package com.sm.instagram.platform.unit.exceptions;

import com.sm.instagram.platform.common.exceptions.handlers.BaseExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.ValidationExceptionHandler;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * One API, one error shape.
 *
 * <p>A constraint written on a controller method parameter — {@code @RequestParam @Min(1) int limit}
 * — raises {@link HandlerMethodValidationException} in Spring 6.1 and later, and
 * {@code ResponseEntityExceptionHandler} renders that as an RFC 7807 {@code problem+json}.
 * Everything else this application returns is the envelope in {@code BaseExceptionHandler}. So the
 * API had two error shapes, and the document — which now points every error response at one schema
 * — could only be right about one of them. Schemathesis reported it as an undocumented content type
 * on {@code /admin/cascade-delete/orphans?limit=0}.
 *
 * <p>The shape a caller parses must not depend on which annotation the constraint was written on.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("A bad method parameter returns the same envelope as a bad body")
class OneErrorShapeUnitTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private WebRequest webRequest;

    private ValidationExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ValidationExceptionHandler(messageSource);
        lenient().when(webRequest.getDescription(false)).thenReturn("uri=/api/admin/cascade-delete/orphans");
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** A controller-shaped method, so the MethodParameter under test is a real one. */
    @SuppressWarnings("unused")
    static void findOrphans(@Min(1) int limit) {
        // signature only
    }

    private static HandlerMethodValidationException violationOn(String message) throws Exception {
        Method method = OneErrorShapeUnitTest.class.getDeclaredMethod("findOrphans", int.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        ParameterValidationResult result = new ParameterValidationResult(
                parameter, 0,
                List.of(new DefaultMessageSourceResolvable(new String[]{"Min"}, null, message)),
                null, null, null, (error, sourceType) -> {
            throw new IllegalArgumentException("no container expected");
        });

        MethodValidationResult validation = mock(MethodValidationResult.class);
        lenient().when(validation.getAllValidationResults()).thenReturn(List.of(result));
        lenient().when(validation.getParameterValidationResults()).thenReturn(List.of(result));
        return new HandlerMethodValidationException(validation);
    }

    @Test
    @DisplayName("it is the envelope, with a 400 and the parameter that failed")
    void returnsTheEnvelope() throws Exception {
        ResponseEntity<Object> response = handler.handleHandlerMethodValidationException(
                violationOn("must be greater than or equal to 1"),
                new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .as("not a ProblemDetail: the same class every other error in this API returns")
                .isInstanceOf(BaseExceptionHandler.ErrorResponse.class);

        BaseExceptionHandler.ErrorResponse body = (BaseExceptionHandler.ErrorResponse) response.getBody();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getRequestId()).isNotBlank();
        assertThat(body.getValidationErrors())
                .containsValue("must be greater than or equal to 1");
    }

    @Test
    @DisplayName("a hostile constraint message is encoded before it is echoed")
    void encodesTheMessage() throws Exception {
        ResponseEntity<Object> response = handler.handleHandlerMethodValidationException(
                violationOn("<script>alert(1)</script>"),
                new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

        BaseExceptionHandler.ErrorResponse body = (BaseExceptionHandler.ErrorResponse) response.getBody();
        assertThat(body.getValidationErrors().values())
                .noneMatch(value -> value.contains("<script>"));
    }
}
