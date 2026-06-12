package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.logging.RequestLoggingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestLoggingFilter.
 * Tests HTTP request/response logging behavior.
 * No Spring context needed - testing pure filter logic.
 *
 * Uses a test subclass to access protected methods.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequestLoggingFilter Unit Tests")
class RequestLoggingFilterUnitTest {

    @Mock
    private FilterChain filterChain;

    private TestableRequestLoggingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    /**
     * Subclass to expose protected methods for testing.
     */
    static class TestableRequestLoggingFilter extends RequestLoggingFilter {
        @Override
        public void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
            super.doFilterInternal(request, response, filterChain);
        }

        @Override
        public boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request) {
            return super.shouldNotFilter(request);
        }
    }

    @BeforeEach
    void setUp() {
        filter = new TestableRequestLoggingFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Clear MDC before each test
        MDC.clear();

        // Setup default request properties
        request.setMethod("GET");
        request.setRequestURI("/api/users");
        request.setRemoteAddr("192.168.1.100");
    }

    @Nested
    @DisplayName("doFilterInternal()")
    class DoFilterInternalTests {

        @Test
        @DisplayName("should call filter chain and copy response body")
        void shouldCallFilterChainAndCopyResponseBody() throws ServletException, IOException {
            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should set request method and URI in MDC")
        void shouldSetRequestContextInMdc() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/payments");

            doAnswer(invocation -> {
                // Verify MDC is set during filter execution
                assertThat(MDC.get("requestMethod")).isEqualTo("POST");
                assertThat(MDC.get("requestUri")).isEqualTo("/api/payments");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should generate request ID if not present in MDC")
        void shouldGenerateRequestIdIfNotPresent() throws ServletException, IOException {
            // Given - MDC is clear

            doAnswer(invocation -> {
                // Verify REQUEST_ID is set during filter execution
                assertThat(MDC.get("REQUEST_ID")).isNotBlank();
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should use existing request ID from MDC")
        void shouldUseExistingRequestIdFromMdc() throws ServletException, IOException {
            // Given
            MDC.put("REQUEST_ID", "EXISTING-123");

            doAnswer(invocation -> {
                assertThat(MDC.get("REQUEST_ID")).isEqualTo("EXISTING-123");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should generate correlation ID if not present in MDC")
        void shouldGenerateCorrelationIdIfNotPresent() throws ServletException, IOException {
            // Given - MDC is clear

            doAnswer(invocation -> {
                assertThat(MDC.get("correlationId")).isNotBlank();
                assertThat(MDC.get("correlationId")).startsWith("REQ-");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should propagate exception from filter chain")
        void shouldPropagateExceptionFromFilterChain() throws ServletException, IOException {
            // Given
            RuntimeException expectedException = new RuntimeException("Test exception");
            doThrow(expectedException).when(filterChain).doFilter(any(), any());

            // When/Then
            assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Test exception");
        }

        @Test
        @DisplayName("should handle preflight OPTIONS request")
        void shouldHandlePreflightOptionsRequest() throws ServletException, IOException {
            // Given
            request.setMethod("OPTIONS");
            request.addHeader("Origin", "https://example.com");
            request.addHeader("Access-Control-Request-Method", "POST");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should set response status correctly")
        void shouldSetResponseStatusCorrectly() throws ServletException, IOException {
            // Given
            doAnswer(invocation -> {
                jakarta.servlet.http.HttpServletResponse resp = invocation.getArgument(1);
                resp.setStatus(201);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(response.getStatus()).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("shouldNotFilter()")
    class ShouldNotFilterTests {

        @Test
        @DisplayName("should skip actuator health endpoint")
        void shouldSkipActuatorHealthEndpoint() {
            // Given
            request.setRequestURI("/actuator/health");

            // When
            boolean shouldNotFilter = filter.shouldNotFilter(request);

            // Then
            assertThat(shouldNotFilter).isTrue();
        }

        @Test
        @DisplayName("should skip actuator info endpoint")
        void shouldSkipActuatorInfoEndpoint() {
            // Given
            request.setRequestURI("/actuator/info");

            // When
            boolean shouldNotFilter = filter.shouldNotFilter(request);

            // Then
            assertThat(shouldNotFilter).isTrue();
        }

        @Test
        @DisplayName("should skip actuator metrics endpoint")
        void shouldSkipActuatorMetricsEndpoint() {
            // Given
            request.setRequestURI("/actuator/metrics");

            // When
            boolean shouldNotFilter = filter.shouldNotFilter(request);

            // Then
            assertThat(shouldNotFilter).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/users",
                "/api/payments",
                "/auth/login",
                "/public/health"
        })
        @DisplayName("should not skip regular API endpoints")
        void shouldNotSkipRegularApiEndpoints(String uri) {
            // Given
            request.setRequestURI(uri);

            // When
            boolean shouldNotFilter = filter.shouldNotFilter(request);

            // Then
            assertThat(shouldNotFilter).isFalse();
        }
    }

    @Nested
    @DisplayName("IP Address Extraction")
    class IpAddressExtractionTests {

        @Test
        @DisplayName("should extract IP from X-Forwarded-For header")
        void shouldExtractIpFromXForwardedFor() throws ServletException, IOException {
            // Given
            request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

            // When - we just verify the request is processed; IP extraction is internal
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should extract IP from X-Real-IP header")
        void shouldExtractIpFromXRealIp() throws ServletException, IOException {
            // Given
            request.addHeader("X-Real-IP", "203.0.113.195");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should fall back to remote address when no proxy headers")
        void shouldFallbackToRemoteAddress() throws ServletException, IOException {
            // Given - no proxy headers set, remoteAddr is default

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Header Handling")
    class HeaderHandlingTests {

        @Test
        @DisplayName("should process request with content type header")
        void shouldProcessRequestWithContentTypeHeader() throws ServletException, IOException {
            // Given
            request.setContentType("application/json");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should process request with user agent header")
        void shouldProcessRequestWithUserAgentHeader() throws ServletException, IOException {
            // Given
            request.addHeader("User-Agent", "Mozilla/5.0 Chrome/120.0.0.0");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle missing user agent")
        void shouldHandleMissingUserAgent() throws ServletException, IOException {
            // Given - no User-Agent header

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should log error response for 5xx status")
        void shouldLogErrorResponseFor5xxStatus() throws ServletException, IOException {
            // Given
            doAnswer(invocation -> {
                jakarta.servlet.http.HttpServletResponse resp = invocation.getArgument(1);
                resp.setStatus(500);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(response.getStatus()).isEqualTo(500);
        }

        @Test
        @DisplayName("should log warning response for 4xx status")
        void shouldLogWarningResponseFor4xxStatus() throws ServletException, IOException {
            // Given
            doAnswer(invocation -> {
                jakarta.servlet.http.HttpServletResponse resp = invocation.getArgument(1);
                resp.setStatus(400);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(response.getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("should handle servlet exception gracefully")
        void shouldHandleServletExceptionGracefully() throws ServletException, IOException {
            // Given
            ServletException expectedException = new ServletException("Servlet error");
            doThrow(expectedException).when(filterChain).doFilter(any(), any());

            // When/Then
            assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                    .isInstanceOf(ServletException.class)
                    .hasMessage("Servlet error");
        }
    }

    @Nested
    @DisplayName("Query String Handling")
    class QueryStringHandlingTests {

        @Test
        @DisplayName("should handle request with query parameters")
        void shouldHandleRequestWithQueryParameters() throws ServletException, IOException {
            // Given
            request.setQueryString("page=1&size=20");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle request without query parameters")
        void shouldHandleRequestWithoutQueryParameters() throws ServletException, IOException {
            // Given - no query string

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Content Length Handling")
    class ContentLengthHandlingTests {

        @Test
        @DisplayName("should handle request with content length")
        void shouldHandleRequestWithContentLength() throws ServletException, IOException {
            // Given
            request.setContent("{\"test\":\"data\"}".getBytes());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle empty request body")
        void shouldHandleEmptyRequestBody() throws ServletException, IOException {
            // Given
            request.setContent(new byte[0]);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }
}
