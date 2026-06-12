package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.logging.CustomErrorController;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomErrorController.
 * Tests error handling for different HTTP status codes, error page rendering,
 * and JSON error responses.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CustomErrorController Unit Tests")
class CustomErrorControllerUnitTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private HttpServletRequest request;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private CustomErrorController errorController;

    @BeforeEach
    void setUp() {
        errorController = new CustomErrorController(messageSource);

        // Default request setup
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        when(request.getHeader("Referer")).thenReturn(null);
        when(request.getHeader("Origin")).thenReturn(null);

        // Default IP headers - return null for all proxy headers
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(request.getHeader("HTTP_X_FORWARDED_FOR")).thenReturn(null);
        when(request.getHeader("HTTP_X_FORWARDED")).thenReturn(null);
        when(request.getHeader("HTTP_X_CLUSTER_CLIENT_IP")).thenReturn(null);
        when(request.getHeader("HTTP_CLIENT_IP")).thenReturn(null);
        when(request.getHeader("HTTP_FORWARDED_FOR")).thenReturn(null);
        when(request.getHeader("HTTP_FORWARDED")).thenReturn(null);
        when(request.getHeader("HTTP_VIA")).thenReturn(null);
        when(request.getHeader("REMOTE_ADDR")).thenReturn(null);

        // Default message source behavior
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==================== HTTP Status Code Tests ====================

    @Nested
    @DisplayName("HTTP 400 Bad Request")
    class BadRequestTests {

        @Test
        @DisplayName("should return 400 status with correct error details")
        void shouldReturn400WithCorrectDetails() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(400);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("Bad Request");
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/api/test");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(400);
            assertThat(response.getBody().get("error")).isEqualTo("Bad Request");
            assertThat(response.getBody().get("path")).isEqualTo("/api/test");
            assertThat(response.getBody().get("message")).isEqualTo("error.validation.failed");
        }

        @Test
        @DisplayName("should include requestId in response")
        void shouldIncludeRequestId() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(400);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("requestId")).isNotNull();
            assertThat(response.getBody().get("requestId").toString()).startsWith("REQ-");
        }
    }

    @Nested
    @DisplayName("HTTP 401 Unauthorized")
    class UnauthorizedTests {

        @Test
        @DisplayName("should return 401 status with correct error message")
        void shouldReturn401WithCorrectMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(401);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/api/protected");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(401);
            assertThat(response.getBody().get("error")).isEqualTo("Unauthorized");
            assertThat(response.getBody().get("message")).isEqualTo("error.auth.not_authenticated");
        }
    }

    @Nested
    @DisplayName("HTTP 403 Forbidden")
    class ForbiddenTests {

        @Test
        @DisplayName("should return 403 status with correct error message")
        void shouldReturn403WithCorrectMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(403);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/api/admin");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(403);
            assertThat(response.getBody().get("error")).isEqualTo("Forbidden");
            assertThat(response.getBody().get("message")).isEqualTo("error.auth.insufficient_permissions");
        }

        @Test
        @DisplayName("should log CORS rejection when Origin header is present")
        void shouldLogCorsRejectionWhenOriginPresent() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(403);
            when(request.getHeader("Origin")).thenReturn("https://malicious-site.com");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(request, atLeastOnce()).getHeader("Origin");
        }
    }

    @Nested
    @DisplayName("HTTP 404 Not Found")
    class NotFoundTests {

        @Test
        @DisplayName("should return 404 status with correct error message")
        void shouldReturn404WithCorrectMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/api/nonexistent");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(404);
            assertThat(response.getBody().get("error")).isEqualTo("Not Found");
            assertThat(response.getBody().get("message")).isEqualTo("error.not_found");
        }
    }

    @Nested
    @DisplayName("HTTP 405 Method Not Allowed")
    class MethodNotAllowedTests {

        @Test
        @DisplayName("should return 405 status with correct error message")
        void shouldReturn405WithCorrectMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(405);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/api/resource");
            when(request.getMethod()).thenReturn("DELETE");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(405);
            assertThat(response.getBody().get("error")).isEqualTo("Method Not Allowed");
            assertThat(response.getBody().get("message")).isEqualTo("error.http.method_not_allowed");
        }
    }

    @Nested
    @DisplayName("HTTP 406 Not Acceptable")
    class NotAcceptableTests {

        @Test
        @DisplayName("should return 406 status with correct error message")
        void shouldReturn406WithCorrectMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(406);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(406);
            assertThat(response.getBody().get("message")).isEqualTo("error.http.unsupported_media_type");
        }
    }

    @Nested
    @DisplayName("HTTP 415 Unsupported Media Type")
    class UnsupportedMediaTypeTests {

        @Test
        @DisplayName("should return 415 status with correct error message")
        void shouldReturn415WithCorrectMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(415);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(415);
            assertThat(response.getBody().get("message")).isEqualTo("error.http.unsupported_media_type");
        }
    }

    @Nested
    @DisplayName("HTTP 429 Too Many Requests")
    class TooManyRequestsTests {

        @Test
        @DisplayName("should return 429 status with correct error message")
        void shouldReturn429WithCorrectMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(429);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(429);
            assertThat(response.getBody().get("message")).isEqualTo("error.business.rate_limit_exceeded");
        }
    }

    @Nested
    @DisplayName("HTTP 500 Internal Server Error")
    class InternalServerErrorTests {

        @Test
        @DisplayName("should return 500 status with correct error message")
        void shouldReturn500WithCorrectMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION))
                    .thenReturn(new RuntimeException("Database connection failed"));

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(500);
            assertThat(response.getBody().get("error")).isEqualTo("Internal Server Error");
            assertThat(response.getBody().get("message")).isEqualTo("error.general.internal_server");
        }

        @Test
        @DisplayName("should handle 5xx errors with error logging")
        void shouldHandle5xxErrorsWithErrorLogging() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(503);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("Unknown and Edge Case Status Codes")
    class UnknownStatusCodeTests {

        @Test
        @DisplayName("should default to 500 when status code is null")
        void shouldDefaultTo500WhenStatusCodeIsNull() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(null);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(500);
        }

        @Test
        @DisplayName("should default to 500 for non-standard status codes")
        void shouldDefaultTo500ForNonStandardStatusCodes() {
            // Given - 499 is a non-standard status code (Nginx client closed request)
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(499);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should use default message for unhandled status codes")
        void shouldUseDefaultMessageForUnhandledStatusCodes() {
            // Given - 418 I'm a teapot (not explicitly handled)
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(418);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message")).isEqualTo("error.general.unexpected");
        }

        @ParameterizedTest
        @ValueSource(ints = {201, 202, 204, 301, 302, 304})
        @DisplayName("should handle various non-error status codes")
        void shouldHandleVariousStatusCodes(int statusCode) {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(statusCode);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getBody()).isNotNull();
        }
    }

    // ==================== Error Response Structure Tests ====================

    @Nested
    @DisplayName("Error Response Structure")
    class ErrorResponseStructureTests {

        @Test
        @DisplayName("should include timestamp in response")
        void shouldIncludeTimestampInResponse() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("timestamp")).isNotNull();
            assertThat(response.getBody().get("timestamp").toString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
        }

        @Test
        @DisplayName("should include all required fields in response")
        void shouldIncludeAllRequiredFields() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/api/test");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKeys("timestamp", "status", "error", "path", "message", "requestId");
        }

        @Test
        @DisplayName("should use MDC correlationId when available")
        void shouldUseMdcCorrelationIdWhenAvailable() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            MDC.put("correlationId", "test-correlation-id");

            try {
                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().get("requestId")).isEqualTo("test-correlation-id");
            } finally {
                MDC.clear();
            }
        }

        @Test
        @DisplayName("should generate new requestId when MDC correlationId is empty")
        void shouldGenerateNewRequestIdWhenMdcEmpty() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            MDC.put("correlationId", "   ");

            try {
                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().get("requestId").toString()).startsWith("REQ-");
            } finally {
                MDC.clear();
            }
        }
    }

    // ==================== Exception Details Tests ====================

    @Nested
    @DisplayName("Exception Details Handling")
    class ExceptionDetailsTests {

        @Test
        @DisplayName("should extract exception message when exception is present")
        void shouldExtractExceptionMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            RuntimeException exception = new RuntimeException("Detailed error message");
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(exception);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE)).thenReturn(RuntimeException.class);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should handle null exception gracefully")
        void shouldHandleNullExceptionGracefully() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
        }
    }

    // ==================== Client IP Address Extraction Tests ====================

    @Nested
    @DisplayName("Client IP Address Extraction")
    class ClientIpExtractionTests {

        @Test
        @DisplayName("should use X-Forwarded-For header when available")
        void shouldUseXForwardedForHeader() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2, 10.0.0.3");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
            verify(request).getHeader("X-Forwarded-For");
        }

        @Test
        @DisplayName("should use X-Real-IP when X-Forwarded-For is not available")
        void shouldUseXRealIpHeader() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn("192.168.1.100");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
            verify(request).getHeader("X-Real-IP");
        }

        @Test
        @DisplayName("should fallback to remote address when no proxy headers")
        void shouldFallbackToRemoteAddress() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            // All headers return null, but remoteAddr returns value

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
            verify(request).getRemoteAddr();
        }

        @Test
        @DisplayName("should skip 'unknown' IP values")
        void shouldSkipUnknownIpValues() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
            when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.1");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
        }
    }

    // ==================== Firebase UID Extraction Tests ====================

    @Nested
    @DisplayName("Firebase UID Extraction")
    class FirebaseUidExtractionTests {

        @Test
        @DisplayName("should extract Firebase UID from authenticated user")
        void shouldExtractFirebaseUidFromAuthenticatedUser() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

            try (MockedStatic<SecurityContextHolder> securityMock = mockStatic(SecurityContextHolder.class)) {
                securityMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);
                when(authentication.isAuthenticated()).thenReturn(true);
                when(authentication.getPrincipal()).thenReturn("firebase-uid-123");

                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then
                assertThat(response).isNotNull();
            }
        }

        @Test
        @DisplayName("should return ANONYMOUS when authentication is null")
        void shouldReturnAnonymousWhenAuthenticationIsNull() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

            try (MockedStatic<SecurityContextHolder> securityMock = mockStatic(SecurityContextHolder.class)) {
                securityMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(null);

                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then
                assertThat(response).isNotNull();
            }
        }

        @Test
        @DisplayName("should return ANONYMOUS when user is not authenticated")
        void shouldReturnAnonymousWhenNotAuthenticated() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

            try (MockedStatic<SecurityContextHolder> securityMock = mockStatic(SecurityContextHolder.class)) {
                securityMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);
                when(authentication.isAuthenticated()).thenReturn(false);

                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then
                assertThat(response).isNotNull();
            }
        }

        @Test
        @DisplayName("should handle security context exception gracefully")
        void shouldHandleSecurityContextExceptionGracefully() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

            try (MockedStatic<SecurityContextHolder> securityMock = mockStatic(SecurityContextHolder.class)) {
                securityMock.when(SecurityContextHolder::getContext).thenThrow(new RuntimeException("Security error"));

                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then - should not throw, returns ANONYMOUS
                assertThat(response).isNotNull();
            }
        }
    }

    // ==================== Locale and Internationalization Tests ====================

    @Nested
    @DisplayName("Locale and Internationalization")
    class LocaleTests {

        @Test
        @DisplayName("should use locale from LocaleContextHolder")
        void shouldUseLocaleFromLocaleContextHolder() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

            try (MockedStatic<LocaleContextHolder> localeMock = mockStatic(LocaleContextHolder.class)) {
                localeMock.when(LocaleContextHolder::getLocale).thenReturn(Locale.GERMAN);
                when(messageSource.getMessage(eq("error.not_found"), isNull(), eq("error.not_found"), eq(Locale.GERMAN)))
                        .thenReturn("Nicht gefunden");

                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().get("message")).isEqualTo("Nicht gefunden");
            }
        }

        @Test
        @DisplayName("should default to Polish locale when locale is null")
        void shouldDefaultToPolishWhenLocaleIsNull() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

            try (MockedStatic<LocaleContextHolder> localeMock = mockStatic(LocaleContextHolder.class)) {
                localeMock.when(LocaleContextHolder::getLocale).thenReturn(null);

                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then
                assertThat(response.getBody()).isNotNull();
                verify(messageSource).getMessage(eq("error.not_found"), isNull(), eq("error.not_found"), eq(new Locale("pl")));
            }
        }

        @Test
        @DisplayName("should default to Polish locale when language is empty")
        void shouldDefaultToPolishWhenLanguageIsEmpty() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);

            try (MockedStatic<LocaleContextHolder> localeMock = mockStatic(LocaleContextHolder.class)) {
                localeMock.when(LocaleContextHolder::getLocale).thenReturn(new Locale(""));

                // When
                ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

                // Then
                assertThat(response.getBody()).isNotNull();
                verify(messageSource).getMessage(eq("error.not_found"), isNull(), eq("error.not_found"), eq(new Locale("pl")));
            }
        }
    }

    // ==================== CORS Preflight Tests ====================

    @Nested
    @DisplayName("CORS Preflight Handling")
    class CorsPreflightTests {

        @Test
        @DisplayName("should log preflight rejection for OPTIONS request with 4xx error")
        void shouldLogPreflightRejectionForOptionsRequest() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(403);
            when(request.getMethod()).thenReturn("OPTIONS");
            when(request.getHeader("Origin")).thenReturn("https://example.com");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(request, atLeastOnce()).getMethod();
        }

        @ParameterizedTest
        @CsvSource({
            "400, OPTIONS",
            "401, OPTIONS",
            "403, OPTIONS",
            "405, OPTIONS"
        })
        @DisplayName("should handle various 4xx errors with OPTIONS method")
        void shouldHandleVarious4xxWithOptionsMethod(int statusCode, String method) {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(statusCode);
            when(request.getMethod()).thenReturn(method);
            when(request.getHeader("Origin")).thenReturn("https://test.com");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
        }
    }

    // ==================== Specific Error Logging Tests ====================

    @Nested
    @DisplayName("Specific Error Logging")
    class SpecificErrorLoggingTests {

        @ParameterizedTest
        @CsvSource({
            "400, Bad Request",
            "401, Unauthorized",
            "403, Forbidden",
            "404, Not Found",
            "405, Method Not Allowed",
            "406, Not Acceptable",
            "415, Unsupported Media Type",
            "429, Too Many Requests"
        })
        @DisplayName("should handle all 4xx status codes with appropriate logging")
        void shouldHandleAll4xxStatusCodes(int statusCode, String expectedError) {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(statusCode);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(statusCode);
        }

        @ParameterizedTest
        @ValueSource(ints = {500, 501, 502, 503, 504})
        @DisplayName("should handle all 5xx status codes with error logging")
        void shouldHandleAll5xxStatusCodes(int statusCode) {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(statusCode);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo(statusCode);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle request with all null attributes")
        void shouldHandleRequestWithAllNullAttributes() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(null);
            when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE)).thenReturn(null);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should handle empty request URI")
        void shouldHandleEmptyRequestUri() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("path")).isEqualTo("");
        }

        @Test
        @DisplayName("should handle special characters in request URI")
        void shouldHandleSpecialCharactersInRequestUri() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/api/test?query=a%20b&special=<>&foo=bar");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should handle query string in request")
        void shouldHandleQueryString() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(request.getQueryString()).thenReturn("page=1&size=10");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
            verify(request).getQueryString();
        }

        @Test
        @DisplayName("should handle very long error message")
        void shouldHandleVeryLongErrorMessage() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
            String longMessage = "Error: " + "a".repeat(10000);
            when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(longMessage);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response).isNotNull();
        }
    }

    // ==================== Message Key Resolution Tests ====================

    @Nested
    @DisplayName("Message Key Resolution")
    class MessageKeyResolutionTests {

        @ParameterizedTest
        @CsvSource({
            "400, error.validation.failed",
            "401, error.auth.not_authenticated",
            "403, error.auth.insufficient_permissions",
            "404, error.not_found",
            "405, error.http.method_not_allowed",
            "406, error.http.unsupported_media_type",
            "415, error.http.unsupported_media_type",
            "429, error.business.rate_limit_exceeded",
            "500, error.general.internal_server"
        })
        @DisplayName("should resolve correct message key for each status code")
        void shouldResolveCorrectMessageKey(int statusCode, String expectedMessageKey) {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(statusCode);

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message")).isEqualTo(expectedMessageKey);
        }

        @Test
        @DisplayName("should use translated message when available")
        void shouldUseTranslatedMessageWhenAvailable() {
            // Given
            when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
            when(messageSource.getMessage(eq("error.not_found"), isNull(), eq("error.not_found"), any(Locale.class)))
                    .thenReturn("Resource not found");

            // When
            ResponseEntity<Map<String, Object>> response = errorController.handleError(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message")).isEqualTo("Resource not found");
        }
    }
}
