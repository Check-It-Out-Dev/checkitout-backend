package com.sm.instagram.platform.unit.exceptions;

import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.NetworkRetryExhaustedException;
import com.sm.instagram.platform.common.exceptions.handlers.BaseExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.NetworkExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NetworkExceptionHandler.
 * Tests exception handling logic for network-related errors.
 * No Spring context needed - testing pure Java logic with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NetworkExceptionHandler Unit Tests")
class NetworkExceptionHandlerUnitTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private WebRequest webRequest;

    private NetworkExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NetworkExceptionHandler(messageSource);

        // Setup common mock behavior
        lenient().when(webRequest.getDescription(false)).thenReturn("uri=/api/test");
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("handleNetworkRetryExhaustedException()")
    class HandleNetworkRetryExhaustedExceptionTests {

        @Test
        @DisplayName("should return SERVICE_UNAVAILABLE for retry exhausted exception")
        void shouldReturnServiceUnavailableForRetryExhausted() {
            // Given
            NetworkRetryExhaustedException ex = new NetworkRetryExhaustedException(
                    "error.network.retry_exhausted", "PaymentAPI", 3);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleNetworkRetryExhaustedException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(503);
            assertThat(response.getBody().getError()).isEqualTo("Service Temporarily Unavailable");
        }

        @Test
        @DisplayName("should use connection_failed message key for SocketException cause")
        void shouldUseConnectionFailedKeyForSocketException() {
            // Given
            SocketException socketException = new SocketException("Connection reset");
            NetworkRetryExhaustedException ex = new NetworkRetryExhaustedException(
                    "error.network.retry_exhausted", "PaymentAPI", 3, socketException);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleNetworkRetryExhaustedException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            verify(messageSource).getMessage(eq("error.network.connection_failed"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should use connection_failed message key for ConnectException cause")
        void shouldUseConnectionFailedKeyForConnectException() {
            // Given
            ConnectException connectException = new ConnectException("Connection refused");
            NetworkRetryExhaustedException ex = new NetworkRetryExhaustedException(
                    "error.network.retry_exhausted", "PaymentAPI", 3, connectException);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleNetworkRetryExhaustedException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            verify(messageSource).getMessage(eq("error.network.connection_failed"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should use timeout message key for SocketTimeoutException cause")
        void shouldUseTimeoutKeyForSocketTimeoutException() {
            // Given
            SocketTimeoutException timeoutException = new SocketTimeoutException("Read timed out");
            NetworkRetryExhaustedException ex = new NetworkRetryExhaustedException(
                    "error.network.retry_exhausted", "PaymentAPI", 3, timeoutException);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleNetworkRetryExhaustedException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            verify(messageSource).getMessage(eq("error.network.timeout"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should use retry_exhausted message key for generic exception cause")
        void shouldUseRetryExhaustedKeyForGenericException() {
            // Given
            RuntimeException genericException = new RuntimeException("Something went wrong");
            NetworkRetryExhaustedException ex = new NetworkRetryExhaustedException(
                    "error.network.retry_exhausted", "PaymentAPI", 3, genericException);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleNetworkRetryExhaustedException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            verify(messageSource).getMessage(eq("error.network.retry_exhausted"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should include request ID in response")
        void shouldIncludeRequestIdInResponse() {
            // Given
            NetworkRetryExhaustedException ex = new NetworkRetryExhaustedException(
                    "error.network.retry_exhausted", "PaymentAPI", 3);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleNetworkRetryExhaustedException(ex, webRequest);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getRequestId()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("handleExternalServiceException()")
    class HandleExternalServiceExceptionTests {

        @Test
        @DisplayName("should return BAD_GATEWAY for 5xx status from non-Instagram service")
        void shouldReturnBadGatewayFor5xxStatus() {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "Service unavailable", "PaymentAPI", "checkout", 500);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            verify(messageSource).getMessage(eq("error.network.external_service_unavailable"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should return UNAUTHORIZED for 401 status")
        void shouldReturnUnauthorizedFor401Status() {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "Unauthorized", "PaymentAPI", "checkout", 401);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(messageSource).getMessage(eq("error.auth.external_service_unauthorized"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should return UNAUTHORIZED for 403 status")
        void shouldReturnUnauthorizedFor403Status() {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "Forbidden", "PaymentAPI", "checkout", 403);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should return NOT_FOUND for 404 status")
        void shouldReturnNotFoundFor404Status() {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "Resource not found", "PaymentAPI", "checkout", 404);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(messageSource).getMessage(eq("error.network.external_service_not_found"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should return TOO_MANY_REQUESTS for 429 status")
        void shouldReturnTooManyRequestsFor429Status() {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "Rate limited", "PaymentAPI", "checkout", 429);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            verify(messageSource).getMessage(eq("error.network.external_service_rate_limited"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should return BAD_REQUEST for 4xx status")
        void shouldReturnBadRequestFor4xxStatus() {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "Bad request", "PaymentAPI", "checkout", 400);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should preserve Instagram user-friendly message")
        void shouldPreserveInstagramUserFriendlyMessage() {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "Your session has expired. Please reconnect your Instagram account.",
                    "Instagram", "getUserProfile", 401);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage())
                    .isEqualTo("Your session has expired. Please reconnect your Instagram account.");
        }

        @Test
        @DisplayName("should not preserve Instagram technical message")
        void shouldNotPreserveInstagramTechnicalMessage() {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "HTTP 401 Exception from Instagram API",
                    "Instagram", "getUserProfile", 401);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            assertThat(response.getBody()).isNotNull();
            // Should use localized message instead of technical message
            verify(messageSource).getMessage(eq("error.auth.external_service_unauthorized"), any(), anyString(), any(Locale.class));
        }

        @ParameterizedTest
        @CsvSource({
                "401, UNAUTHORIZED",
                "403, UNAUTHORIZED",
                "404, NOT_FOUND",
                "429, TOO_MANY_REQUESTS",
                "400, BAD_REQUEST",
                "500, BAD_GATEWAY",
                "502, BAD_GATEWAY",
                "503, BAD_GATEWAY"
        })
        @DisplayName("should map status codes correctly")
        void shouldMapStatusCodesCorrectly(int statusCode, String expectedStatus) {
            // Given
            ExternalServiceException ex = new ExternalServiceException(
                    "Error", "TestService", "operation", statusCode);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleExternalServiceException(ex, webRequest);

            // Then
            HttpStatus expected = HttpStatus.valueOf(expectedStatus);
            assertThat(response.getStatusCode()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("handleWebClientRequestException()")
    class HandleWebClientRequestExceptionTests {

        @Test
        @DisplayName("should return BAD_GATEWAY for timeout exception cause")
        void shouldReturnBadGatewayForTimeoutCause() {
            // Given
            SocketTimeoutException cause = new SocketTimeoutException("Read timed out");
            WebClientRequestException ex = new WebClientRequestException(
                    cause, HttpMethod.GET, URI.create("http://test.com"), new HttpHeaders());

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleWebClientRequestException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            verify(messageSource).getMessage(eq("error.network.external_service_timeout"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should return BAD_GATEWAY for connect exception cause")
        void shouldReturnBadGatewayForConnectCause() {
            // Given
            ConnectException cause = new ConnectException("Connection refused");
            WebClientRequestException ex = new WebClientRequestException(
                    cause, HttpMethod.GET, URI.create("http://test.com"), new HttpHeaders());

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleWebClientRequestException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            verify(messageSource).getMessage(eq("error.network.external_service_connection_failed"), any(), anyString(), any(Locale.class));
        }

        @Test
        @DisplayName("should return BAD_GATEWAY for generic exception cause")
        void shouldReturnBadGatewayForGenericCause() {
            // Given
            RuntimeException cause = new RuntimeException("Unknown error");
            WebClientRequestException ex = new WebClientRequestException(
                    cause, HttpMethod.GET, URI.create("http://test.com"), new HttpHeaders());

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleWebClientRequestException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            verify(messageSource).getMessage(eq("error.network.external_service_connection_error"), any(), anyString(), any(Locale.class));
        }
    }

    @Nested
    @DisplayName("handleWebClientResponseException()")
    class HandleWebClientResponseExceptionTests {

        @Test
        @DisplayName("should return BAD_GATEWAY for 5xx response")
        void shouldReturnBadGatewayFor5xxResponse() {
            // Given
            WebClientResponseException ex = WebClientResponseException.create(
                    500, "Internal Server Error", null, null, null);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleWebClientResponseException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }

        @Test
        @DisplayName("should preserve 4xx status codes")
        void shouldPreserve4xxStatusCodes() {
            // Given
            WebClientResponseException ex = WebClientResponseException.create(
                    400, "Bad Request", null, null, null);

            // When
            ResponseEntity<BaseExceptionHandler.ErrorResponse> response =
                    handler.handleWebClientResponseException(ex, webRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should include HTTP status in message args")
        void shouldIncludeHttpStatusInMessageArgs() {
            // Given
            WebClientResponseException ex = WebClientResponseException.create(
                    404, "Not Found", null, null, null);

            // When
            handler.handleWebClientResponseException(ex, webRequest);

            // Then
            verify(messageSource).getMessage(
                    eq("error.network.external_service_http_error"),
                    argThat(args -> args != null && args.length > 0 && args[0].equals(404)),
                    anyString(),
                    any(Locale.class));
        }
    }
}
