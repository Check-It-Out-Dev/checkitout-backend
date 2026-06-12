package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.util.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestContextUtils.
 * Tests utility methods for request context extraction.
 * No Spring context needed - testing pure Java logic with mocked requests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequestContextUtils Unit Tests")
class RequestContextUtilsUnitTest {

    @Mock
    private HttpServletRequest mockRequest;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("generateTraceId()")
    class GenerateTraceIdTests {

        @Test
        @DisplayName("should return correlation ID from MDC when available")
        void shouldReturnCorrelationIdFromMdc() {
            // Given
            MDC.put("correlationId", "REQ-abc12345");

            // When
            String traceId = RequestContextUtils.generateTraceId();

            // Then
            assertThat(traceId).isEqualTo("REQ-abc12345");
        }

        @Test
        @DisplayName("should generate new trace ID with default prefix when MDC is empty")
        void shouldGenerateNewTraceIdWhenMdcEmpty() {
            // Given - MDC is empty

            // When
            String traceId = RequestContextUtils.generateTraceId();

            // Then
            assertThat(traceId).startsWith("ERR-");
            assertThat(traceId).hasSize(12); // ERR- + 8 chars
        }

        @Test
        @DisplayName("should generate new trace ID with custom prefix")
        void shouldGenerateNewTraceIdWithCustomPrefix() {
            // Given - MDC is empty

            // When
            String traceId = RequestContextUtils.generateTraceId("CUSTOM");

            // Then
            assertThat(traceId).startsWith("CUSTOM-");
        }

        @Test
        @DisplayName("should not use empty correlation ID from MDC")
        void shouldNotUseEmptyCorrelationIdFromMdc() {
            // Given
            MDC.put("correlationId", "   ");

            // When
            String traceId = RequestContextUtils.generateTraceId();

            // Then
            assertThat(traceId).startsWith("ERR-");
        }
    }

    @Nested
    @DisplayName("getClientIpAddress()")
    class GetClientIpAddressTests {

        @Test
        @DisplayName("should extract IP from X-Forwarded-For header")
        void shouldExtractIpFromXForwardedFor() {
            // Given
            request.addHeader("X-Forwarded-For", "203.0.113.195");

            // When
            String ip = RequestContextUtils.getClientIpAddress(request);

            // Then
            assertThat(ip).isEqualTo("203.0.113.195");
        }

        @Test
        @DisplayName("should extract first IP from X-Forwarded-For with multiple proxies")
        void shouldExtractFirstIpFromXForwardedForWithMultipleProxies() {
            // Given
            request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

            // When
            String ip = RequestContextUtils.getClientIpAddress(request);

            // Then
            assertThat(ip).isEqualTo("203.0.113.195");
        }

        @Test
        @DisplayName("should extract IP from X-Real-IP header")
        void shouldExtractIpFromXRealIp() {
            // Given
            request.addHeader("X-Real-IP", "10.0.0.1");

            // When
            String ip = RequestContextUtils.getClientIpAddress(request);

            // Then
            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("should fallback to remote address when no proxy headers")
        void shouldFallbackToRemoteAddress() {
            // Given
            request.setRemoteAddr("192.168.1.100");

            // When
            String ip = RequestContextUtils.getClientIpAddress(request);

            // Then
            assertThat(ip).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should skip unknown header value")
        void shouldSkipUnknownHeaderValue() {
            // Given
            request.addHeader("X-Forwarded-For", "unknown");
            request.addHeader("X-Real-IP", "10.0.0.1");

            // When
            String ip = RequestContextUtils.getClientIpAddress(request);

            // Then
            assertThat(ip).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("should skip empty header value")
        void shouldSkipEmptyHeaderValue() {
            // Given
            request.addHeader("X-Forwarded-For", "");
            request.setRemoteAddr("127.0.0.1");

            // When
            String ip = RequestContextUtils.getClientIpAddress(request);

            // Then
            assertThat(ip).isEqualTo("127.0.0.1");
        }

        @ParameterizedTest
        @CsvSource({
                "X-Forwarded-For, 1.2.3.4",
                "X-Real-IP, 5.6.7.8",
                "Proxy-Client-IP, 9.10.11.12",
                "WL-Proxy-Client-IP, 13.14.15.16"
        })
        @DisplayName("should check various proxy headers in order")
        void shouldCheckVariousProxyHeaders(String headerName, String expectedIp) {
            // Given
            request.addHeader(headerName, expectedIp);

            // When
            String ip = RequestContextUtils.getClientIpAddress(request);

            // Then
            assertThat(ip).isEqualTo(expectedIp);
        }
    }

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return user from security context")
        void shouldReturnUserFromSecurityContext() {
            // Given
            Authentication auth = mock(Authentication.class);
            when(auth.getName()).thenReturn("testUser");
            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(securityContext);

            // When
            String user = RequestContextUtils.getCurrentUser(request);

            // Then
            assertThat(user).isEqualTo("testUser");
        }

        @Test
        @DisplayName("should fallback to request principal when security context is null")
        void shouldFallbackToRequestPrincipal() {
            // Given
            request.setUserPrincipal(() -> "requestPrincipalUser");

            // When
            String user = RequestContextUtils.getCurrentUser(request);

            // Then
            assertThat(user).isEqualTo("requestPrincipalUser");
        }

        @Test
        @DisplayName("should return anonymous when no user principal")
        void shouldReturnAnonymousWhenNoUserPrincipal() {
            // Given - no principal set

            // When
            String user = RequestContextUtils.getCurrentUser(request);

            // Then
            assertThat(user).isEqualTo("anonymous");
        }
    }

    @Nested
    @DisplayName("buildRequestContext()")
    class BuildRequestContextTests {

        @Test
        @DisplayName("should build complete request context string")
        void shouldBuildCompleteRequestContextString() {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.setRemoteAddr("192.168.1.100");

            // When
            String context = RequestContextUtils.buildRequestContext(request, "trace123");

            // Then
            assertThat(context).contains("trace=trace123");
            assertThat(context).contains("path=/api/users");
            assertThat(context).contains("method=POST");
            assertThat(context).contains("user=anonymous");
            assertThat(context).contains("ip=192.168.1.100");
        }
    }

    @Nested
    @DisplayName("sanitizeSensitiveData()")
    class SanitizeSensitiveDataTests {

        @Test
        @DisplayName("should mask password field in JSON")
        void shouldMaskPasswordFieldInJson() {
            // Given
            String body = "{\"username\":\"john\",\"password\":\"secret123\"}";

            // When
            String sanitized = RequestContextUtils.sanitizeSensitiveData(body);

            // Then
            assertThat(sanitized).contains("\"password\":\"***MASKED***\"");
            assertThat(sanitized).doesNotContain("secret123");
            assertThat(sanitized).contains("\"username\":\"john\"");
        }

        @Test
        @DisplayName("should mask token field in JSON")
        void shouldMaskTokenFieldInJson() {
            // Given
            String body = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9.xyz\"}";

            // When
            String sanitized = RequestContextUtils.sanitizeSensitiveData(body);

            // Then
            assertThat(sanitized).contains("\"token\":\"***MASKED***\"");
            assertThat(sanitized).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        }

        @Test
        @DisplayName("should mask apiKey field in JSON")
        void shouldMaskApiKeyFieldInJson() {
            // Given
            String body = "{\"apiKey\":\"sk-1234567890abcdef\"}";

            // When
            String sanitized = RequestContextUtils.sanitizeSensitiveData(body);

            // Then
            assertThat(sanitized).contains("\"apiKey\":\"***MASKED***\"");
            assertThat(sanitized).doesNotContain("sk-1234567890abcdef");
        }

        @Test
        @DisplayName("should mask secret field in JSON")
        void shouldMaskSecretFieldInJson() {
            // Given
            String body = "{\"secret\":\"very-secret-value\"}";

            // When
            String sanitized = RequestContextUtils.sanitizeSensitiveData(body);

            // Then
            assertThat(sanitized).contains("\"secret\":\"***MASKED***\"");
        }

        @Test
        @DisplayName("should preserve non-sensitive fields")
        void shouldPreserveNonSensitiveFields() {
            // Given
            String body = "{\"email\":\"test@example.com\",\"name\":\"John\",\"password\":\"secret\"}";

            // When
            String sanitized = RequestContextUtils.sanitizeSensitiveData(body);

            // Then
            assertThat(sanitized).contains("\"email\":\"test@example.com\"");
            assertThat(sanitized).contains("\"name\":\"John\"");
            assertThat(sanitized).contains("\"password\":\"***MASKED***\"");
        }

        @Test
        @DisplayName("should handle null body")
        void shouldHandleNullBody() {
            // When
            String sanitized = RequestContextUtils.sanitizeSensitiveData(null);

            // Then
            assertThat(sanitized).isNull();
        }

        @Test
        @DisplayName("should handle empty body")
        void shouldHandleEmptyBody() {
            // When
            String sanitized = RequestContextUtils.sanitizeSensitiveData("");

            // Then
            assertThat(sanitized).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "password", "passwd", "pwd",
                "secret", "apiSecret", "clientSecret",
                "token", "accessToken", "refreshToken",
                "key", "apiKey", "privateKey"
        })
        @DisplayName("should mask various sensitive field names")
        void shouldMaskVariousSensitiveFieldNames(String fieldName) {
            // Given
            String body = "{\"" + fieldName + "\":\"sensitiveValue\"}";

            // When
            String sanitized = RequestContextUtils.sanitizeSensitiveData(body);

            // Then
            assertThat(sanitized).contains("\"" + fieldName + "\":\"***MASKED***\"");
            assertThat(sanitized).doesNotContain("sensitiveValue");
        }
    }

    @Nested
    @DisplayName("sanitizeParameters()")
    class SanitizeParametersTests {

        @Test
        @DisplayName("should mask password parameter")
        void shouldMaskPasswordParameter() {
            // Given
            Map<String, String[]> params = new HashMap<>();
            params.put("password", new String[]{"secret123"});
            params.put("username", new String[]{"john"});

            // When
            Map<String, String[]> sanitized = RequestContextUtils.sanitizeParameters(params);

            // Then
            assertThat(sanitized.get("password")[0]).isEqualTo("***MASKED***");
            assertThat(sanitized.get("username")[0]).isEqualTo("john");
        }

        @Test
        @DisplayName("should handle null parameters")
        void shouldHandleNullParameters() {
            // When
            Map<String, String[]> sanitized = RequestContextUtils.sanitizeParameters(null);

            // Then
            assertThat(sanitized).isNull();
        }

        @Test
        @DisplayName("should handle empty parameters")
        void shouldHandleEmptyParameters() {
            // When
            Map<String, String[]> sanitized = RequestContextUtils.sanitizeParameters(Collections.emptyMap());

            // Then
            assertThat(sanitized).isEmpty();
        }
    }

    @Nested
    @DisplayName("getHttpMethod()")
    class GetHttpMethodTests {

        @Test
        @DisplayName("should return HTTP method")
        void shouldReturnHttpMethod() {
            // Given
            request.setMethod("DELETE");

            // When
            String method = RequestContextUtils.getHttpMethod(request);

            // Then
            assertThat(method).isEqualTo("DELETE");
        }
    }

    @Nested
    @DisplayName("getRequestPath()")
    class GetRequestPathTests {

        @Test
        @DisplayName("should return request path")
        void shouldReturnRequestPath() {
            // Given
            request.setRequestURI("/api/v1/users/123");

            // When
            String path = RequestContextUtils.getRequestPath(request);

            // Then
            assertThat(path).isEqualTo("/api/v1/users/123");
        }
    }

    @Nested
    @DisplayName("buildFullRequestDetails()")
    class BuildFullRequestDetailsTests {

        @Test
        @DisplayName("should build full request details map")
        void shouldBuildFullRequestDetailsMap() {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.setQueryString("sort=name");
            request.setContentType("application/json");
            request.addHeader("Accept", "application/json");

            // When
            Map<String, Object> details = RequestContextUtils.buildFullRequestDetails(request);

            // Then
            assertThat(details).containsKey("method");
            assertThat(details).containsKey("path");
            assertThat(details).containsKey("queryString");
            assertThat(details).containsKey("contentType");
            assertThat(details.get("method")).isEqualTo("POST");
            assertThat(details.get("path")).isEqualTo("/api/users");
        }

        @Test
        @DisplayName("should mask sensitive headers")
        void shouldMaskSensitiveHeaders() {
            // Given
            request.addHeader("Authorization", "Bearer secret-token");
            request.addHeader("Content-Type", "application/json");

            // When
            Map<String, Object> details = RequestContextUtils.buildFullRequestDetails(request);

            // Then
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) details.get("headers");
            assertThat(headers.get("Authorization")).isEqualTo("***MASKED***");
            assertThat(headers.get("Content-Type")).isEqualTo("application/json");
        }
    }
}
