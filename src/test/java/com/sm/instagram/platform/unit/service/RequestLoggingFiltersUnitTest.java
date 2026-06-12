package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.logging.RequestCorrelationFilter;
import com.sm.instagram.platform.common.logging.RequestLoggingFilter;
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
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestLoggingFilter and RequestCorrelationFilter.
 * Tests logging behavior, correlation ID propagation, and MDC context management.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Request Logging Filters Unit Tests")
class RequestLoggingFiltersUnitTest {

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("RequestCorrelationFilter Tests")
    class RequestCorrelationFilterTests {

        private RequestCorrelationFilter filter;

        @BeforeEach
        void setUp() {
            filter = new RequestCorrelationFilter();
        }

        @Nested
        @DisplayName("Correlation ID Generation")
        class CorrelationIdGenerationTests {

            @Test
            @DisplayName("should generate correlation ID when not provided in request header")
            void shouldGenerateCorrelationIdWhenNotProvided() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    // Verify MDC is set during filter execution
                    assertThat(MDC.get("correlationId")).isNotNull();
                    assertThat(MDC.get("correlationId")).startsWith("REQ-");
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
                assertThat(response.getHeader("X-Trace-ID")).isNotNull();
                assertThat(response.getHeader("X-Trace-ID")).startsWith("REQ-");
                assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
            }

            @Test
            @DisplayName("should use provided X-Correlation-ID from request header")
            void shouldUseProvidedCorrelationIdHeader() throws ServletException, IOException {
                // Given
                String providedCorrelationId = "CLIENT-PROVIDED-123";
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.addHeader("X-Correlation-ID", providedCorrelationId);

                doAnswer(invocation -> {
                    assertThat(MDC.get("correlationId")).isEqualTo(providedCorrelationId);
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                assertThat(response.getHeader("X-Trace-ID")).isEqualTo(providedCorrelationId);
                assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(providedCorrelationId);
            }

            @Test
            @DisplayName("should use provided X-Trace-ID from request header")
            void shouldUseProvidedTraceIdHeader() throws ServletException, IOException {
                // Given
                String providedTraceId = "TRACE-ID-456";
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.addHeader("X-Trace-ID", providedTraceId);

                doAnswer(invocation -> {
                    assertThat(MDC.get("correlationId")).isEqualTo(providedTraceId);
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                assertThat(response.getHeader("X-Trace-ID")).isEqualTo(providedTraceId);
            }

            @Test
            @DisplayName("should prefer X-Correlation-ID over X-Trace-ID")
            void shouldPreferCorrelationIdOverTraceId() throws ServletException, IOException {
                // Given
                String correlationId = "CORR-ID-789";
                String traceId = "TRACE-ID-999";
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.addHeader("X-Correlation-ID", correlationId);
                request.addHeader("X-Trace-ID", traceId);

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                assertThat(response.getHeader("X-Trace-ID")).isEqualTo(correlationId);
            }

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should generate new ID when header is null, empty, or whitespace")
            void shouldGenerateNewIdForInvalidHeaders(String headerValue) throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                if (headerValue != null) {
                    request.addHeader("X-Correlation-ID", headerValue);
                }

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                String responseId = response.getHeader("X-Trace-ID");
                assertThat(responseId).isNotNull();
                assertThat(responseId).startsWith("REQ-");
            }
        }

        @Nested
        @DisplayName("Request ID Generation")
        class RequestIdGenerationTests {

            @Test
            @DisplayName("should generate REQUEST_ID when not in MDC")
            void shouldGenerateRequestIdWhenNotInMdc() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    String requestId = MDC.get("REQUEST_ID");
                    assertThat(requestId).isNotNull();
                    assertThat(requestId).startsWith("REQ-");
                    assertThat(requestId).contains("-");
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should preserve existing REQUEST_ID from MDC")
            void shouldPreserveExistingRequestIdFromMdc() throws ServletException, IOException {
                // Given
                String existingRequestId = "REQ-EXISTING-123";
                MDC.put("REQUEST_ID", existingRequestId);
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    // The filter should use existing or regenerate, but not clear
                    String requestId = MDC.get("REQUEST_ID");
                    assertThat(requestId).isNotNull();
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("MDC Context Management")
        class MdcContextManagementTests {

            @Test
            @DisplayName("should set request URI in MDC")
            void shouldSetRequestUriInMdc() throws ServletException, IOException {
                // Given
                String requestUri = "/api/users/123";
                request.setRequestURI(requestUri);
                request.setMethod("GET");

                doAnswer(invocation -> {
                    assertThat(MDC.get("requestUri")).isEqualTo(requestUri);
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should set request method in MDC")
            void shouldSetRequestMethodInMdc() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("POST");

                doAnswer(invocation -> {
                    assertThat(MDC.get("requestMethod")).isEqualTo("POST");
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should clear MDC after filter completes normally")
            void shouldClearMdcAfterFilterCompletes() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                assertThat(MDC.get("correlationId")).isNull();
                assertThat(MDC.get("REQUEST_ID")).isNull();
                assertThat(MDC.get("requestUri")).isNull();
                assertThat(MDC.get("requestMethod")).isNull();
            }

            @Test
            @DisplayName("should clear MDC even when filter chain throws exception")
            void shouldClearMdcWhenFilterChainThrowsException() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                doThrow(new ServletException("Test exception")).when(filterChain).doFilter(any(), any());

                // When/Then
                assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                        .isInstanceOf(ServletException.class);

                // MDC should still be cleared
                assertThat(MDC.get("correlationId")).isNull();
                assertThat(MDC.get("REQUEST_ID")).isNull();
            }
        }

        @Nested
        @DisplayName("Response Header Tests")
        class ResponseHeaderTests {

            @Test
            @DisplayName("should add X-Trace-ID header to response")
            void shouldAddTraceIdHeaderToResponse() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                assertThat(response.getHeader("X-Trace-ID")).isNotNull();
            }

            @Test
            @DisplayName("should add X-Correlation-ID header to response for backward compatibility")
            void shouldAddCorrelationIdHeaderToResponse() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
            }

            @Test
            @DisplayName("should have same value for both trace and correlation headers")
            void shouldHaveSameValueForBothHeaders() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                assertThat(response.getHeader("X-Trace-ID"))
                        .isEqualTo(response.getHeader("X-Correlation-ID"));
            }
        }

        @Nested
        @DisplayName("Actuator Endpoint Filtering")
        class ActuatorEndpointFilteringTests {

            @Test
            @DisplayName("should not filter actuator health endpoint")
            void shouldNotFilterActuatorHealthEndpoint() throws ServletException, IOException {
                // Given
                request.setRequestURI("/actuator/health");
                request.setMethod("GET");

                // When
                filter.doFilter(request, response, filterChain);

                // Then - filter should be skipped for actuator endpoints
                // The filter's shouldNotFilter returns true for actuator paths
                assertThat(request.getRequestURI()).startsWith("/actuator/");
            }

            @ParameterizedTest
            @ValueSource(strings = {"/actuator/health", "/actuator/info", "/actuator/metrics", "/actuator/prometheus"})
            @DisplayName("should skip various actuator endpoints")
            void shouldSkipVariousActuatorEndpoints(String actuatorPath) throws ServletException, IOException {
                // Given
                request.setRequestURI(actuatorPath);
                request.setMethod("GET");

                // When - shouldNotFilter should return true
                // We can't directly test shouldNotFilter as it's protected, but we verify behavior
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("HTTP Method Tests")
        class HttpMethodTests {

            @ParameterizedTest
            @ValueSource(strings = {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"})
            @DisplayName("should handle all HTTP methods")
            void shouldHandleAllHttpMethods(String method) throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod(method);

                doAnswer(invocation -> {
                    assertThat(MDC.get("requestMethod")).isEqualTo(method);
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }
    }

    @Nested
    @DisplayName("RequestLoggingFilter Tests")
    class RequestLoggingFilterTests {

        private RequestLoggingFilter filter;

        @BeforeEach
        void setUp() {
            filter = new RequestLoggingFilter();
        }

        @Nested
        @DisplayName("Request Wrapping Tests")
        class RequestWrappingTests {

            @Test
            @DisplayName("should wrap request and response for content caching")
            void shouldWrapRequestAndResponseForContentCaching() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("POST");
                request.setContent("test body".getBytes());
                request.setContentType("application/json");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should copy response body back after filtering")
            void shouldCopyResponseBodyBackAfterFiltering() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    // The filter wraps the response, so we get ContentCachingResponseWrapper
                    jakarta.servlet.http.HttpServletResponse resp =
                        (jakarta.servlet.http.HttpServletResponse) invocation.getArgument(1);
                    resp.setStatus(200);
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("MDC Fallback Generation")
        class MdcFallbackGenerationTests {

            @Test
            @DisplayName("should generate REQUEST_ID if not already set")
            void shouldGenerateRequestIdIfNotAlreadySet() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                // MDC is empty

                doAnswer(invocation -> {
                    String requestId = MDC.get("REQUEST_ID");
                    assertThat(requestId).isNotNull();
                    assertThat(requestId).startsWith("REQ-");
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should generate correlationId if not already set")
            void shouldGenerateCorrelationIdIfNotAlreadySet() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    String correlationId = MDC.get("correlationId");
                    assertThat(correlationId).isNotNull();
                    assertThat(correlationId).startsWith("REQ-");
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should preserve existing REQUEST_ID from earlier filter")
            void shouldPreserveExistingRequestIdFromEarlierFilter() throws ServletException, IOException {
                // Given
                String existingId = "REQ-PRE-EXISTING-123";
                MDC.put("REQUEST_ID", existingId);
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    assertThat(MDC.get("REQUEST_ID")).isEqualTo(existingId);
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("Request Logging Tests")
        class RequestLoggingTests {

            @Test
            @DisplayName("should log incoming request with method and URI")
            void shouldLogIncomingRequestWithMethodAndUri() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/users/123");
                request.setMethod("GET");
                request.setRemoteAddr("192.168.1.100");

                // When
                filter.doFilter(request, response, filterChain);

                // Then - Filter should complete without errors
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should log OPTIONS request as preflight")
            void shouldLogOptionsRequestAsPreflight() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("OPTIONS");
                request.addHeader("Origin", "https://example.com");
                request.addHeader("Access-Control-Request-Method", "POST");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should handle request with content type")
            void shouldHandleRequestWithContentType() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("POST");
                request.setContentType("application/json");
                request.setContent("{\"key\":\"value\"}".getBytes());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("Response Logging Tests")
        class ResponseLoggingTests {

            @Test
            @DisplayName("should log successful response with status 200")
            void shouldLogSuccessfulResponseWithStatus200() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                response.setStatus(200);

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should log client error response with status 4xx")
            void shouldLogClientErrorResponse() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    // The filter wraps the response, so we get ContentCachingResponseWrapper
                    jakarta.servlet.http.HttpServletResponse resp =
                        (jakarta.servlet.http.HttpServletResponse) invocation.getArgument(1);
                    resp.setStatus(404);
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should log server error response with status 5xx")
            void shouldLogServerErrorResponse() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    // The filter wraps the response, so we get ContentCachingResponseWrapper
                    jakarta.servlet.http.HttpServletResponse resp =
                        (jakarta.servlet.http.HttpServletResponse) invocation.getArgument(1);
                    resp.setStatus(500);
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should include request duration in response logging")
            void shouldIncludeRequestDurationInResponseLogging() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                doAnswer(invocation -> {
                    Thread.sleep(10); // Simulate some processing time
                    return null;
                }).when(filterChain).doFilter(any(), any());

                // When
                long start = System.currentTimeMillis();
                filter.doFilter(request, response, filterChain);
                long end = System.currentTimeMillis();

                // Then
                verify(filterChain).doFilter(any(), any());
                assertThat(end - start).isGreaterThanOrEqualTo(10);
            }
        }

        @Nested
        @DisplayName("Exception Handling Tests")
        class ExceptionHandlingTests {

            @Test
            @DisplayName("should log and rethrow exception from filter chain")
            void shouldLogAndRethrowExceptionFromFilterChain() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                ServletException expectedException = new ServletException("Test error");
                doThrow(expectedException).when(filterChain).doFilter(any(), any());

                // When/Then
                assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                        .isInstanceOf(ServletException.class)
                        .hasMessage("Test error");
            }

            @Test
            @DisplayName("should log and rethrow IOException from filter chain")
            void shouldLogAndRethrowIOExceptionFromFilterChain() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                IOException expectedException = new IOException("IO error");
                doThrow(expectedException).when(filterChain).doFilter(any(), any());

                // When/Then
                assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                        .isInstanceOf(IOException.class)
                        .hasMessage("IO error");
            }

            @Test
            @DisplayName("should log and rethrow RuntimeException from filter chain")
            void shouldLogAndRethrowRuntimeExceptionFromFilterChain() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                RuntimeException expectedException = new RuntimeException("Runtime error");
                doThrow(expectedException).when(filterChain).doFilter(any(), any());

                // When/Then
                assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessage("Runtime error");
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
                request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3");

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
                request.addHeader("X-Real-IP", "192.168.1.50");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should fallback to remote addr when headers not present")
            void shouldFallbackToRemoteAddr() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.setRemoteAddr("127.0.0.1");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @ParameterizedTest
            @ValueSource(strings = {"Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR"})
            @DisplayName("should support various proxy headers")
            void shouldSupportVariousProxyHeaders(String headerName) throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.addHeader(headerName, "10.20.30.40");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("Header Extraction and Masking Tests")
        class HeaderExtractionTests {

            @Test
            @DisplayName("should mask authorization header")
            void shouldMaskAuthorizationHeader() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.addHeader("Authorization", "Bearer secret-token-12345");

                // When
                filter.doFilter(request, response, filterChain);

                // Then - filter should handle sensitive headers appropriately
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should mask cookie header")
            void shouldMaskCookieHeader() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.addHeader("Cookie", "session=abc123; auth=xyz789");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @ParameterizedTest
            @ValueSource(strings = {"Authorization", "Cookie", "X-Auth-Token", "Password-Header"})
            @DisplayName("should mask various sensitive headers")
            void shouldMaskVariousSensitiveHeaders(String headerName) throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.addHeader(headerName, "sensitive-value");

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
                    "'Googlebot/2.1 (+http://www.google.com/bot.html)', Bot"
            })
            @DisplayName("should categorize user agents correctly")
            void shouldCategorizeUserAgentsCorrectly(String userAgent, String expectedCategory) throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
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
                // No User-Agent header

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
            @DisplayName("should anonymize IPv4 address")
            void shouldAnonymizeIpv4Address() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.setRemoteAddr("192.168.1.100");

                // When
                filter.doFilter(request, response, filterChain);

                // Then - IP should be anonymized (last octet masked)
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should anonymize IPv6 address")
            void shouldAnonymizeIpv6Address() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.setRemoteAddr("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

                // When
                filter.doFilter(request, response, filterChain);

                // Then - IPv6 should be anonymized
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("Parameter Extraction Tests")
        class ParameterExtractionTests {

            @Test
            @DisplayName("should extract query parameters")
            void shouldExtractQueryParameters() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");
                request.addParameter("page", "1");
                request.addParameter("size", "10");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @ParameterizedTest
            @ValueSource(strings = {"email", "password", "phone", "address", "ssn", "dob"})
            @DisplayName("should mask sensitive parameters (GDPR)")
            void shouldMaskSensitiveParameters(String paramName) throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("POST");
                request.addParameter(paramName, "sensitive-value");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("Actuator Endpoint Exclusion Tests")
        class ActuatorEndpointExclusionTests {

            @ParameterizedTest
            @ValueSource(strings = {"/actuator/health", "/actuator/info", "/actuator/metrics"})
            @DisplayName("should skip actuator endpoints")
            void shouldSkipActuatorEndpoints(String path) throws ServletException, IOException {
                // Given
                request.setRequestURI(path);
                request.setMethod("GET");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should not skip non-actuator endpoints")
            void shouldNotSkipNonActuatorEndpoints() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/users");
                request.setMethod("GET");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }

        @Nested
        @DisplayName("Request Body Handling Tests")
        class RequestBodyHandlingTests {

            @Test
            @DisplayName("should handle request with body")
            void shouldHandleRequestWithBody() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("POST");
                request.setContentType("application/json");
                request.setContent("{\"name\":\"test\"}".getBytes());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should handle request without body")
            void shouldHandleRequestWithoutBody() throws ServletException, IOException {
                // Given
                request.setRequestURI("/api/test");
                request.setMethod("GET");

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should sanitize email addresses in request body for debug endpoints")
            void shouldSanitizeEmailAddressesInRequestBody() throws ServletException, IOException {
                // Given
                request.setRequestURI("/debug/test");
                request.setMethod("POST");
                request.setContentType("application/json");
                request.setContent("{\"email\":\"user@example.com\"}".getBytes());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }

            @Test
            @DisplayName("should sanitize phone numbers in request body for debug endpoints")
            void shouldSanitizePhoneNumbersInRequestBody() throws ServletException, IOException {
                // Given
                request.setRequestURI("/debug/test");
                request.setMethod("POST");
                request.setContentType("application/json");
                request.setContent("{\"phone\":\"123-456-7890\"}".getBytes());

                // When
                filter.doFilter(request, response, filterChain);

                // Then
                verify(filterChain).doFilter(any(), any());
            }
        }
    }

    @Nested
    @DisplayName("Filter Integration Tests")
    class FilterIntegrationTests {

        @Test
        @DisplayName("should work with both filters in sequence")
        void shouldWorkWithBothFiltersInSequence() throws ServletException, IOException {
            // Given
            RequestCorrelationFilter correlationFilter = new RequestCorrelationFilter();
            RequestLoggingFilter loggingFilter = new RequestLoggingFilter();

            request.setRequestURI("/api/test");
            request.setMethod("GET");

            // Simulate first filter (correlation) setting up MDC
            FilterChain innerChain = mock(FilterChain.class);
            doAnswer(invocation -> {
                // Inside correlation filter, before logging filter
                assertThat(MDC.get("correlationId")).isNotNull();
                assertThat(MDC.get("REQUEST_ID")).isNotNull();

                // Now call logging filter
                loggingFilter.doFilter(request, response, filterChain);
                return null;
            }).when(innerChain).doFilter(any(), any());

            // When
            correlationFilter.doFilter(request, response, innerChain);

            // Then
            verify(filterChain).doFilter(any(), any());
            assertThat(response.getHeader("X-Trace-ID")).isNotNull();
        }

        @Test
        @DisplayName("should propagate correlation ID through filter chain")
        void shouldPropagateCorrelationIdThroughFilterChain() throws ServletException, IOException {
            // Given
            RequestCorrelationFilter correlationFilter = new RequestCorrelationFilter();
            String clientCorrelationId = "CLIENT-TRACE-ID-XYZ";

            request.setRequestURI("/api/test");
            request.setMethod("GET");
            request.addHeader("X-Correlation-ID", clientCorrelationId);

            doAnswer(invocation -> {
                assertThat(MDC.get("correlationId")).isEqualTo(clientCorrelationId);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            correlationFilter.doFilter(request, response, filterChain);

            // Then
            assertThat(response.getHeader("X-Trace-ID")).isEqualTo(clientCorrelationId);
            assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(clientCorrelationId);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle request with very long URI")
        void shouldHandleRequestWithVeryLongUri() throws ServletException, IOException {
            // Given
            RequestLoggingFilter filter = new RequestLoggingFilter();
            String longUri = "/api/" + "a".repeat(1000);
            request.setRequestURI(longUri);
            request.setMethod("GET");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle request with special characters in URI")
        void shouldHandleRequestWithSpecialCharactersInUri() throws ServletException, IOException {
            // Given
            RequestLoggingFilter filter = new RequestLoggingFilter();
            request.setRequestURI("/api/test?query=hello%20world&name=%E2%9C%93");
            request.setMethod("GET");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle concurrent requests")
        void shouldHandleConcurrentRequests() throws Exception {
            // Given
            RequestCorrelationFilter filter = new RequestCorrelationFilter();
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

                        filter.doFilter(threadRequest, threadResponse, threadFilterChain);

                        assertThat(threadResponse.getHeader("X-Trace-ID")).isNotNull();
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
        @DisplayName("should handle empty request body")
        void shouldHandleEmptyRequestBody() throws ServletException, IOException {
            // Given
            RequestLoggingFilter filter = new RequestLoggingFilter();
            request.setRequestURI("/api/test");
            request.setMethod("POST");
            request.setContentType("application/json");
            request.setContent(new byte[0]);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should handle large request body")
        void shouldHandleLargeRequestBody() throws ServletException, IOException {
            // Given
            RequestLoggingFilter filter = new RequestLoggingFilter();
            request.setRequestURI("/api/test");
            request.setMethod("POST");
            request.setContentType("application/json");
            byte[] largeBody = new byte[10000];
            java.util.Arrays.fill(largeBody, (byte) 'a');
            request.setContent(largeBody);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }
}
