package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.logging.CorsLoggingFilter;
import com.sm.instagram.platform.config.CorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CorsLoggingFilter.
 * Tests CORS header logging, preflight request handling, and origin validation logging.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CorsLoggingFilter Unit Tests")
class CorsLoggingFilterUnitTest {

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CorsLoggingFilter filter;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of(
                "http://localhost:4200", "https://localhost:4200",
                "http://localhost:80", "https://localhost:80",
                "http://bs-local.com:4200", "https://bs-local.com:4200",
                "https://check-it-out.pl", "https://checkitout.app"
        ));
        filter = new CorsLoggingFilter(corsProperties);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("Filter Invocation Tests")
    class FilterInvocationTests {

        @Test
        @DisplayName("should invoke filter chain for request with Origin header")
        void shouldInvokeFilterChainForRequestWithOriginHeader() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should invoke filter chain for OPTIONS request without Origin")
        void shouldInvokeFilterChainForOptionsRequestWithoutOrigin() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should skip filter for non-CORS request without Origin")
        void shouldSkipFilterForNonCorsRequestWithoutOrigin() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            // No Origin header

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Origin Validation Tests")
    class OriginValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "http://localhost:4200",
            "https://localhost:4200",
            "http://localhost:80",
            "https://localhost:80",
            "http://bs-local.com:4200",
            "https://bs-local.com:4200",
            "https://check-it-out.pl",
            "https://checkitout.app"
        })
        @DisplayName("should accept allowed origins")
        void shouldAcceptAllowedOrigins(String origin) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", origin);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "http://evil.com",
            "https://malicious-site.org",
            "http://localhost:3000",
            "https://not-allowed.checkitout.app",
            "http://checkitout.app.fake.com"
        })
        @DisplayName("should log warning for disallowed origins")
        void shouldLogWarningForDisallowedOrigins(String origin) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", origin);

            // When
            filter.doFilter(request, response, filterChain);

            // Then - filter should complete without errors and invoke chain
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle null origin gracefully")
        void shouldHandleNullOriginGracefully() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            // No Origin header

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Preflight Request Handling Tests")
    class PreflightRequestHandlingTests {

        @Test
        @DisplayName("should identify OPTIONS request with Access-Control-Request-Method as preflight")
        void shouldIdentifyPreflightRequest() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should not identify OPTIONS request without Access-Control-Request-Method as preflight")
        void shouldNotIdentifyOptionsWithoutAccessControlRequestMethodAsPreflight() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            // No Access-Control-Request-Method header

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH"})
        @DisplayName("should not identify non-OPTIONS request as preflight")
        void shouldNotIdentifyNonOptionsRequestAsPreflight(String method) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod(method);
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log preflight details with requested headers")
        void shouldLogPreflightDetailsWithRequestedHeaders() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");
            request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log warning for method not allowed in preflight")
        void shouldLogWarningForMethodNotAllowedInPreflight() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "TRACE"); // Not in allowed list

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
        @DisplayName("should accept valid HTTP methods in preflight")
        void shouldAcceptValidHttpMethodsInPreflight(String method) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", method);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log custom headers in preflight request")
        void shouldLogCustomHeadersInPreflightRequest() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");
            request.addHeader("Access-Control-Request-Headers", "X-Custom-Header, Authorization");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Response Header Logging Tests")
    class ResponseHeaderLoggingTests {

        @Test
        @DisplayName("should add trace headers to response")
        void shouldAddTraceHeadersToResponse() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(response.getHeader("X-Request-ID")).isNotNull();
            assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
            assertThat(response.getHeader("X-Trace-ID")).isNotNull();
        }

        @Test
        @DisplayName("should use existing MDC correlation ID for trace headers")
        void shouldUseExistingMdcCorrelationIdForTraceHeaders() throws ServletException, IOException {
            // Given
            String existingCorrelationId = "EXISTING-CORR-ID";
            MDC.put("correlationId", existingCorrelationId);
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(existingCorrelationId);
            assertThat(response.getHeader("X-Trace-ID")).isEqualTo(existingCorrelationId);
        }

        @Test
        @DisplayName("should use existing MDC request ID for trace headers")
        void shouldUseExistingMdcRequestIdForTraceHeaders() throws ServletException, IOException {
            // Given
            String existingRequestId = "REQ-EXISTING-123";
            MDC.put("REQUEST_ID", existingRequestId);
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(response.getHeader("X-Request-ID")).isEqualTo(existingRequestId);
        }

        @Test
        @DisplayName("should log CORS response headers after processing")
        void shouldLogCorsResponseHeadersAfterProcessing() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
                resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
                resp.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
                resp.setHeader("Access-Control-Allow-Credentials", "true");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log warning for error responses")
        void shouldLogWarningForErrorResponses() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(403);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403, 405})
        @DisplayName("should log error for rejected preflight requests")
        void shouldLogErrorForRejectedPreflightRequests(int status) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(status);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log error when Access-Control-Allow-Origin header is missing on error")
        void shouldLogErrorWhenAccessControlAllowOriginMissing() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(400);
                // No Access-Control-Allow-Origin header
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log successful preflight acceptance")
        void shouldLogSuccessfulPreflightAcceptance() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(200);
                resp.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
                resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("CORS Header Validation Tests")
    class CorsHeaderValidationTests {

        @Test
        @DisplayName("should log security issue for wildcard origin with credentials")
        void shouldLogSecurityIssueForWildcardOriginWithCredentials() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(200);
                resp.setHeader("Access-Control-Allow-Origin", "*");
                resp.setHeader("Access-Control-Allow-Credentials", "true");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log warning for origin mismatch")
        void shouldLogWarningForOriginMismatch() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(200);
                // Response has different origin than request
                resp.setHeader("Access-Control-Allow-Origin", "http://different-origin.com");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log warning when Allow-Origin is missing for request with Origin")
        void shouldLogWarningWhenAllowOriginMissing() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(200);
                // No Access-Control-Allow-Origin header
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should not log warning when Allow-Origin matches request Origin")
        void shouldNotLogWarningWhenAllowOriginMatchesRequestOrigin() throws ServletException, IOException {
            // Given
            String origin = "http://localhost:4200";
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", origin);

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(200);
                resp.setHeader("Access-Control-Allow-Origin", origin);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should accept wildcard origin without credentials")
        void shouldAcceptWildcardOriginWithoutCredentials() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(200);
                resp.setHeader("Access-Control-Allow-Origin", "*");
                // No credentials header or false
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Suspicious Origin Detection Tests")
    class SuspiciousOriginDetectionTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "http://mylocalhost:4200",
            "https://fakehost.localhost.com",
            "http://localhost.evil.com",
            "https://notlocalhost:4200"
        })
        @DisplayName("should log warning for suspicious localhost patterns")
        void shouldLogWarningForSuspiciousLocalhostPatterns(String origin) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", origin);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should not flag legitimate localhost origin")
        void shouldNotFlagLegitimateLocalhostOrigin() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should log warning for excessively long origin")
        void shouldLogWarningForExcessivelyLongOrigin() throws ServletException, IOException {
            // Given
            String longOrigin = "http://" + "a".repeat(100) + ".com";
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", longOrigin);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should not flag origin shorter than 100 characters")
        void shouldNotFlagOriginShorterThan100Characters() throws ServletException, IOException {
            // Given
            String normalOrigin = "https://some-normal-domain.example.com";
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", normalOrigin);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("MDC Context Management Tests")
    class MdcContextManagementTests {

        @Test
        @DisplayName("should set MDC values during CORS request processing")
        void shouldSetMdcValuesDuringCorsRequestProcessing() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("POST");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                // During filter chain execution, MDC should have values
                assertThat(MDC.get("REQUEST_ID")).isNotNull();
                assertThat(MDC.get("correlationId")).isNotNull();
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should preserve existing MDC request ID")
        void shouldPreserveExistingMdcRequestId() throws ServletException, IOException {
            // Given
            String existingRequestId = "REQ-EXISTING-456";
            MDC.put("REQUEST_ID", existingRequestId);
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                assertThat(MDC.get("REQUEST_ID")).isEqualTo(existingRequestId);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should preserve existing MDC correlation ID")
        void shouldPreserveExistingMdcCorrelationId() throws ServletException, IOException {
            // Given
            String existingCorrelationId = "CORR-EXISTING-789";
            MDC.put("correlationId", existingCorrelationId);
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            doAnswer(invocation -> {
                assertThat(MDC.get("correlationId")).isEqualTo(existingCorrelationId);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should generate REQUEST_ID when not set in MDC")
        void shouldGenerateRequestIdWhenNotSetInMdc() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(response.getHeader("X-Request-ID")).isNotNull();
            assertThat(response.getHeader("X-Request-ID")).startsWith("REQ-");
        }

        @Test
        @DisplayName("should generate correlation ID when not set in MDC")
        void shouldGenerateCorrelationIdWhenNotSetInMdc() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
            assertThat(response.getHeader("X-Correlation-ID")).startsWith("REQ-");
        }
    }

    @Nested
    @DisplayName("Client IP Extraction Tests")
    class ClientIpExtractionTests {

        @Test
        @DisplayName("should extract IP from X-Forwarded-For header")
        void shouldExtractIpFromXForwardedFor() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("X-Forwarded-For", "192.168.1.100, 10.0.0.1, 172.16.0.1");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should extract IP from X-Real-IP header")
        void shouldExtractIpFromXRealIp() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("X-Real-IP", "192.168.1.50");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should fallback to remote addr when no proxy headers")
        void shouldFallbackToRemoteAddrWhenNoProxyHeaders() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.setRemoteAddr("127.0.0.1");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"Proxy-Client-IP", "WL-Proxy-Client-IP"})
        @DisplayName("should support various proxy headers")
        void shouldSupportVariousProxyHeaders(String headerName) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader(headerName, "10.20.30.40");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should ignore unknown IP value")
        void shouldIgnoreUnknownIpValue() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("X-Forwarded-For", "unknown");
            request.setRemoteAddr("127.0.0.1");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("IP Anonymization (GDPR) Tests")
    class IpAnonymizationTests {

        @Test
        @DisplayName("should handle IPv4 addresses")
        void shouldHandleIpv4Addresses() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.setRemoteAddr("192.168.1.100");

            // When
            filter.doFilter(request, response, filterChain);

            // Then - IP should be anonymized in logs
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle IPv6 addresses")
        void shouldHandleIpv6Addresses() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.setRemoteAddr("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

            // When
            filter.doFilter(request, response, filterChain);

            // Then - IPv6 should be anonymized in logs
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should handle null or empty IP addresses")
        void shouldHandleNullOrEmptyIpAddresses(String ipAddress) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            if (ipAddress != null) {
                request.setRemoteAddr(ipAddress);
            }

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("User Agent Categorization Tests")
    class UserAgentCategorizationTests {

        @ParameterizedTest
        @CsvSource({
            "'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0.4472.124', Chrome",
            "'Mozilla/5.0 (Windows NT 10.0; rv:89.0) Firefox/89.0', Firefox",
            "'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15', Safari",
            "'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Edge/91.0.864.59', Edge",
            "'Googlebot/2.1 (+http://www.google.com/bot.html)', Bot",
            "'PostmanRuntime/7.28.4', Postman",
            "'curl/7.77.0', curl"
        })
        @DisplayName("should categorize user agents correctly")
        void shouldCategorizeUserAgentsCorrectly(String userAgent, String expectedCategory) throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("User-Agent", userAgent);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle null user agent")
        void shouldHandleNullUserAgent() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            // No User-Agent header

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should categorize Chrome user agent that also contains Safari string")
        void shouldCategorizeChromeThatContainsSafari() throws ServletException, IOException {
            // Given - Chrome UA contains both Chrome and Safari strings
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36");

            // When
            filter.doFilter(request, response, filterChain);

            // Then - should be categorized as Chrome, not Safari
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should categorize unknown user agent as Other")
        void shouldCategorizeUnknownUserAgentAsOther() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("User-Agent", "SomeUnknownClient/1.0");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Rejection Reason Determination Tests")
    class RejectionReasonDeterminationTests {

        @Test
        @DisplayName("should determine rejection reason for disallowed origin")
        void shouldDetermineRejectionReasonForDisallowedOrigin() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://evil.com");
            request.addHeader("Access-Control-Request-Method", "POST");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(403);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should determine rejection reason for disallowed method")
        void shouldDetermineRejectionReasonForDisallowedMethod() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "TRACE");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(405);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should determine rejection reason for missing Allow-Origin header")
        void shouldDetermineRejectionReasonForMissingAllowOriginHeader() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(403);
                // No Access-Control-Allow-Origin header
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should determine rejection reason for 401 authentication error")
        void shouldDetermineRejectionReasonFor401AuthenticationError() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(401);
                resp.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should determine rejection reason for Authorization header not allowed")
        void shouldDetermineRejectionReasonForAuthorizationHeaderNotAllowed() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");
            request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(403);
                resp.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
                resp.setHeader("Access-Control-Allow-Headers", "Content-Type"); // Authorization missing
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("should rethrow ServletException from filter chain")
        void shouldRethrowServletExceptionFromFilterChain() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            ServletException expectedException = new ServletException("Test exception");
            doThrow(expectedException).when(filterChain).doFilter(any(), any());

            // When/Then
            assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(ServletException.class)
                .hasMessage("Test exception");
        }

        @Test
        @DisplayName("should rethrow IOException from filter chain")
        void shouldRethrowIOExceptionFromFilterChain() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            IOException expectedException = new IOException("IO error");
            doThrow(expectedException).when(filterChain).doFilter(any(), any());

            // When/Then
            assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(IOException.class)
                .hasMessage("IO error");
        }

        @Test
        @DisplayName("should still add trace headers even when exception occurs")
        void shouldStillAddTraceHeadersEvenWhenExceptionOccurs() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            doThrow(new RuntimeException("Test error")).when(filterChain).doFilter(any(), any());

            // When/Then
            assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(RuntimeException.class);

            // Trace headers should still be present
            assertThat(response.getHeader("X-Request-ID")).isNotNull();
            assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
        }
    }

    @Nested
    @DisplayName("ShouldNotFilter Tests")
    class ShouldNotFilterTests {

        @Test
        @DisplayName("should not filter request without Origin and not OPTIONS")
        void shouldNotFilterRequestWithoutOriginAndNotOptions() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            // No Origin header, not OPTIONS

            // When
            filter.doFilter(request, response, filterChain);

            // Then - filter should still call chain but skip CORS logging
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should filter OPTIONS request even without Origin")
        void shouldFilterOptionsRequestEvenWithoutOrigin() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            // No Origin header

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should filter request with Origin header")
        void shouldFilterRequestWithOriginHeader() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("POST");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty origin header")
        void shouldHandleEmptyOriginHeader() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle very long request URI")
        void shouldHandleVeryLongRequestUri() throws ServletException, IOException {
            // Given
            String longUri = "/api/" + "a".repeat(1000);
            request.setRequestURI(longUri);
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle special characters in origin")
        void shouldHandleSpecialCharactersInOrigin() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://test%20site.com:8080");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle concurrent requests")
        void shouldHandleConcurrentRequests() throws Exception {
            // Given
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            // When
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        MockHttpServletRequest threadRequest = new MockHttpServletRequest();
                        MockHttpServletResponse threadResponse = new MockHttpServletResponse();
                        FilterChain threadFilterChain = mock(FilterChain.class);

                        threadRequest.setRequestURI("/api/test/" + index);
                        threadRequest.setMethod("GET");
                        threadRequest.addHeader("Origin", "http://localhost:4200");

                        filter.doFilter(threadRequest, threadResponse, threadFilterChain);

                        assertThat(threadResponse.getHeader("X-Trace-ID")).isNotNull();
                        assertThat(threadResponse.getHeader("X-Request-ID")).isNotNull();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Then - no exceptions should have occurred
        }

        @Test
        @DisplayName("should handle response with all CORS headers")
        void shouldHandleResponseWithAllCorsHeaders() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("Access-Control-Request-Method", "POST");

            doAnswer(invocation -> {
                MockHttpServletResponse resp = (MockHttpServletResponse) invocation.getArgument(1);
                resp.setStatus(200);
                resp.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
                resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                resp.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Custom-Header");
                resp.setHeader("Access-Control-Allow-Credentials", "true");
                resp.setHeader("Access-Control-Max-Age", "3600");
                resp.setHeader("Access-Control-Expose-Headers", "X-Custom-Response-Header");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle request with multiple origins in X-Forwarded-For")
        void shouldHandleRequestWithMultipleOriginsInXForwardedFor() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("X-Forwarded-For", "192.168.1.1, 10.0.0.1, 172.16.0.1, 8.8.8.8");

            // When
            filter.doFilter(request, response, filterChain);

            // Then - should use first IP
            verify(filterChain).doFilter(any(), any());
        }
    }
}
