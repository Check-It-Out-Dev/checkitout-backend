package com.sm.instagram.platform.unit.exceptions;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.handlers.BaseExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.BusinessExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.MappingException;
import org.modelmapper.spi.ErrorMessage;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * A converter refusing the caller's id is the caller's mistake, however ModelMapper wraps it.
 *
 * <p>{@link MappingException} has one constructor and it takes a list of {@link ErrorMessage}: the
 * converter's throwable is inside those, and {@code getCause()} is always null. The handler walked
 * only the cause chain, so its 400 branch could never be reached and every converter failure came
 * back 500 -- {@code POST /user-social-connection} with {@code "platform": 0} among them.
 *
 * <p>Seven converters signal a missing reference this way, so these cases stand for all of them.
 * The last one matters as much as the first: a mapping failure that is genuinely ours must stay a
 * 500, or this fix would hide real faults behind a client error.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("A ModelMapper failure is answered by the converter failure underneath it")
class MappingFailureStatusUnitTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private WebRequest webRequest;

    private BusinessExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BusinessExceptionHandler(messageSource);
        lenient().when(webRequest.getDescription(false)).thenReturn("uri=/api/user-social-connection");
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** How ModelMapper actually reports a converter that threw. */
    private static MappingException aggregating(Throwable converterFailure) {
        return new MappingException(List.of(
                new ErrorMessage("Error mapping com.example.In to com.example.Out", converterFailure)));
    }

    @Test
    @DisplayName("a converter refusing an id is 400, not a server error")
    void converterRefusalIsBadRequest() {
        ResponseEntity<BaseExceptionHandler.ErrorResponse> response = handler.handleMappingException(
                aggregating(new IllegalArgumentException("Platform not found for id: 0")), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("the entity and the id the lookup missed stay out of the body")
    void namesNothingTheCallerDidNotSend() {
        ResponseEntity<BaseExceptionHandler.ErrorResponse> response = handler.handleMappingException(
                aggregating(new IllegalArgumentException("Platform not found for id: 0")), webRequest);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .doesNotContain("Platform")
                .doesNotContain("not found for id");
    }

    @Test
    @DisplayName("it is found however deeply the converter failure is wrapped")
    void findsANestedCause() {
        ResponseEntity<BaseExceptionHandler.ErrorResponse> response = handler.handleMappingException(
                aggregating(new RuntimeException("converter failed",
                        new IllegalStateException("while resolving",
                                new IllegalArgumentException("Currency not found: 99")))), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a translatable not-found underneath is a 404 with its own message")
    void translatableCauseIsNotFound() {
        ResponseEntity<BaseExceptionHandler.ErrorResponse> response = handler.handleMappingException(
                aggregating(new ResourceNotFoundException("error.resource.not_found")), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("error.resource.not_found");
    }

    @Test
    @DisplayName("a mapping failure that is genuinely ours stays a 500")
    void unknownMappingFailureStaysServerError() {
        ResponseEntity<BaseExceptionHandler.ErrorResponse> response = handler.handleMappingException(
                aggregating(new NullPointerException("no converter registered for Instant")), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("a mapping failure reporting no cause at all stays a 500")
    void causelessMappingFailureStaysServerError() {
        MappingException causeless = new MappingException(List.of(new ErrorMessage("unconvertible")));

        ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                handler.handleMappingException(causeless, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("a self-referencing cause chain terminates")
    void selfReferencingCauseTerminates() {
        RuntimeException loop = new RuntimeException("round and round") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                handler.handleMappingException(aggregating(loop), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
