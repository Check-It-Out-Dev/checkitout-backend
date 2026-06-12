package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.logging.EarlyRequestLoggingFilter;
import com.sm.instagram.platform.common.logging.RequestBodyCachingFilter;
import com.sm.instagram.platform.common.logging.RequestBodyCachingFilter.CachedBodyHttpServletRequest;
import com.sm.instagram.platform.common.logging.RequestBodyCachingFilter.CachedBodyServletInputStream;
import com.sm.instagram.platform.config.CorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestBodyCachingFilter Unit Tests")
class RequestBodyCachingFilterUnitTest {

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RequestBodyCachingFilter filter;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filter = new RequestBodyCachingFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ==================== doFilter Tests ====================

    @Nested
    @DisplayName("doFilter - MDC Context Setup")
    class MdcContextSetupTests {

        @Test
        @DisplayName("Should set REQUEST_ID in MDC for any request")
        void doFilter_SetsRequestIdInMdc() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/test");

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
        @DisplayName("Should set requestMethod in MDC")
        void doFilter_SetsRequestMethodInMdc() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.setContentType("text/plain");

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
        @DisplayName("Should set requestUri in MDC")
        void doFilter_SetsRequestUriInMdc() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/health");

            doAnswer(invocation -> {
                assertThat(MDC.get("requestUri")).isEqualTo("/api/health");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("REQUEST_ID should have correct format REQ-timestamp-threadId")
        void doFilter_RequestIdHasCorrectFormat() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/test");

            doAnswer(invocation -> {
                String requestId = MDC.get("REQUEST_ID");
                assertThat(requestId).isNotNull();
                assertThat(requestId).startsWith("REQ-");
                assertThat(requestId).matches("REQ-\\d+-\\d+");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("doFilter - Request Body Caching Decision")
    class RequestBodyCachingDecisionTests {

        @Test
        @DisplayName("Should cache POST request with JSON content")
        void doFilter_CachesPostRequestWithJsonContent() throws ServletException, IOException {
            // Given
            String jsonBody = "{\"name\":\"test\"}";
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.setContentType("application/json");
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            HttpServletRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest).isInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should cache PUT request with JSON content")
        void doFilter_CachesPutRequestWithJsonContent() throws ServletException, IOException {
            // Given
            String jsonBody = "{\"id\":1,\"name\":\"updated\"}";
            request.setMethod("PUT");
            request.setRequestURI("/api/users/1");
            request.setContentType("application/json");
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should cache PATCH request with JSON content")
        void doFilter_CachesPatchRequestWithJsonContent() throws ServletException, IOException {
            // Given
            String jsonBody = "{\"name\":\"patched\"}";
            request.setMethod("PATCH");
            request.setRequestURI("/api/users/1");
            request.setContentType("application/json");
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should NOT cache GET request")
        void doFilter_DoesNotCacheGetRequest() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/users");

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isNotInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should NOT cache DELETE request")
        void doFilter_DoesNotCacheDeleteRequest() throws ServletException, IOException {
            // Given
            request.setMethod("DELETE");
            request.setRequestURI("/api/users/1");

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isNotInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should NOT cache OPTIONS request")
        void doFilter_DoesNotCacheOptionsRequest() throws ServletException, IOException {
            // Given
            request.setMethod("OPTIONS");
            request.setRequestURI("/api/users");

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isNotInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should NOT cache POST with non-JSON content type")
        void doFilter_DoesNotCacheNonJsonContent() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/upload");
            request.setContentType("multipart/form-data");

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isNotInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should NOT cache POST with null content type")
        void doFilter_DoesNotCacheNullContentType() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/test");
            // No content type set

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isNotInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should NOT cache POST with empty body")
        void doFilter_DoesNotCacheEmptyBody() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/test");
            request.setContentType("application/json");
            // Empty content - contentLength will be 0

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isNotInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should cache POST with application/json;charset=utf-8 content type")
        void doFilter_CachesJsonWithCharset() throws ServletException, IOException {
            // Given
            String jsonBody = "{\"name\":\"test\"}";
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.setContentType("application/json;charset=utf-8");
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isInstanceOf(CachedBodyHttpServletRequest.class);
        }
    }

    @Nested
    @DisplayName("Actuator Endpoint Filtering")
    class ActuatorEndpointFilteringTests {

        @Test
        @DisplayName("Should skip filtering for /actuator/health")
        void doFilter_SkipsActuatorHealth() throws ServletException, IOException {
            // Given
            request.setRequestURI("/actuator/health");
            request.setMethod("GET");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should skip filtering for /actuator/info")
        void doFilter_SkipsActuatorInfo() throws ServletException, IOException {
            // Given
            request.setRequestURI("/actuator/info");
            request.setMethod("GET");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should skip filtering for /actuator/prometheus")
        void doFilter_SkipsActuatorPrometheus() throws ServletException, IOException {
            // Given
            request.setRequestURI("/actuator/prometheus");
            request.setMethod("GET");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"/api/test", "/users", "/health", "/auth/login"})
        @DisplayName("Should NOT skip filtering for non-actuator endpoints")
        void doFilter_DoesNotSkipNonActuatorEndpoints(String uri) throws ServletException, IOException {
            // Given
            request.setRequestURI(uri);
            request.setMethod("GET");

            doAnswer(invocation -> {
                assertThat(MDC.get("REQUEST_ID")).isNotNull();
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    // ==================== CachedBodyHttpServletRequest Tests ====================

    @Nested
    @DisplayName("CachedBodyHttpServletRequest - Body Caching")
    class CachedBodyHttpServletRequestTests {

        @Test
        @DisplayName("Should cache request body and make it accessible via getCachedBody()")
        void cachedRequest_CachesBodyString() throws IOException {
            // Given
            String jsonBody = "{\"username\":\"testuser\",\"email\":\"test@example.com\"}";
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/users");

            // When
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

            // Then
            assertThat(cachedRequest.getCachedBody()).isEqualTo(jsonBody);
        }

        @Test
        @DisplayName("Should cache request body bytes and make it accessible via getCachedBodyBytes()")
        void cachedRequest_CachesBodyBytes() throws IOException {
            // Given
            String jsonBody = "{\"name\":\"test\"}";
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/test");

            // When
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

            // Then
            byte[] cachedBytes = cachedRequest.getCachedBodyBytes();
            assertThat(cachedBytes).isEqualTo(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("getCachedBodyBytes should return a clone (defensive copy)")
        void cachedRequest_GetCachedBodyBytesReturnsClone() throws IOException {
            // Given
            String jsonBody = "{\"name\":\"test\"}";
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/test");

            // When
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
            byte[] firstCall = cachedRequest.getCachedBodyBytes();
            byte[] secondCall = cachedRequest.getCachedBodyBytes();

            // Then
            assertThat(firstCall).isNotSameAs(secondCall);
            assertThat(firstCall).isEqualTo(secondCall);
        }

        @Test
        @DisplayName("Should store body in request attribute CACHED_REQUEST_BODY")
        void cachedRequest_StoresBodyInAttribute() throws IOException {
            // Given
            String jsonBody = "{\"key\":\"value\"}";
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/test");

            // When
            new CachedBodyHttpServletRequest(request);

            // Then
            assertThat(request.getAttribute("CACHED_REQUEST_BODY")).isEqualTo(jsonBody);
        }

        @Test
        @DisplayName("Should store body bytes in request attribute CACHED_REQUEST_BODY_BYTES")
        void cachedRequest_StoresBytesInAttribute() throws IOException {
            // Given
            String jsonBody = "{\"key\":\"value\"}";
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/test");

            // When
            new CachedBodyHttpServletRequest(request);

            // Then
            assertThat(request.getAttribute("CACHED_REQUEST_BODY_BYTES")).isNotNull();
        }

        @Test
        @DisplayName("Should allow reading body multiple times via getReader()")
        void cachedRequest_AllowsMultipleReadsViaReader() throws IOException {
            // Given
            String jsonBody = "{\"message\":\"hello\"}";
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/test");

            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

            // When
            BufferedReader reader1 = cachedRequest.getReader();
            String body1 = reader1.lines().reduce("", (a, b) -> a + b);

            BufferedReader reader2 = cachedRequest.getReader();
            String body2 = reader2.lines().reduce("", (a, b) -> a + b);

            // Then
            assertThat(body1).isEqualTo(jsonBody);
            assertThat(body2).isEqualTo(jsonBody);
        }

        @Test
        @DisplayName("Should allow reading body multiple times via getInputStream()")
        void cachedRequest_AllowsMultipleReadsViaInputStream() throws IOException {
            // Given
            String jsonBody = "{\"data\":\"test\"}";
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/test");

            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

            // When
            ServletInputStream is1 = cachedRequest.getInputStream();
            byte[] bytes1 = is1.readAllBytes();

            ServletInputStream is2 = cachedRequest.getInputStream();
            byte[] bytes2 = is2.readAllBytes();

            // Then
            assertThat(new String(bytes1, StandardCharsets.UTF_8)).isEqualTo(jsonBody);
            assertThat(new String(bytes2, StandardCharsets.UTF_8)).isEqualTo(jsonBody);
        }

        @Test
        @DisplayName("Should handle empty body")
        void cachedRequest_HandlesEmptyBody() throws IOException {
            // Given
            request.setContent(new byte[0]);
            request.setRequestURI("/api/test");

            // When
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

            // Then
            assertThat(cachedRequest.getCachedBody()).isEmpty();
            assertThat(cachedRequest.getCachedBodyBytes()).isEmpty();
        }

        @Test
        @DisplayName("Should handle multiline JSON body")
        void cachedRequest_HandlesMultilineBody() throws IOException {
            // Given
            String multilineBody = "{\n  \"name\": \"test\",\n  \"value\": 123\n}";
            request.setContent(multilineBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/test");

            // When
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

            // Then
            String cachedBody = cachedRequest.getCachedBody();
            assertThat(cachedBody).contains("\"name\": \"test\"");
            assertThat(cachedBody).contains("\"value\": 123");
        }

        @Test
        @DisplayName("Should handle Unicode content in body")
        void cachedRequest_HandlesUnicodeContent() throws IOException {
            // Given
            String unicodeBody = "{\"message\":\"Hello, World! Bonjour, Monde!\"}";
            request.setContent(unicodeBody.getBytes(StandardCharsets.UTF_8));
            request.setRequestURI("/api/test");

            // When
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

            // Then
            assertThat(cachedRequest.getCachedBody()).isEqualTo(unicodeBody);
        }
    }

    // ==================== CachedBodyServletInputStream Tests ====================

    @Nested
    @DisplayName("CachedBodyServletInputStream - Stream Operations")
    class CachedBodyServletInputStreamTests {

        @Test
        @DisplayName("Should read single byte correctly")
        void cachedInputStream_ReadsSingleByteCorrectly() throws IOException {
            // Given
            byte[] data = "test".getBytes(StandardCharsets.UTF_8);
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);

            // When/Then
            assertThat(stream.read()).isEqualTo('t');
            assertThat(stream.read()).isEqualTo('e');
            assertThat(stream.read()).isEqualTo('s');
            assertThat(stream.read()).isEqualTo('t');
            assertThat(stream.read()).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should read into byte array correctly")
        void cachedInputStream_ReadsIntoByteArray() throws IOException {
            // Given
            byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);
            byte[] buffer = new byte[5];

            // When
            int bytesRead = stream.read(buffer);

            // Then
            assertThat(bytesRead).isEqualTo(5);
            assertThat(new String(buffer, StandardCharsets.UTF_8)).isEqualTo("hello");
        }

        @Test
        @DisplayName("Should read into byte array with offset and length")
        void cachedInputStream_ReadsWithOffsetAndLength() throws IOException {
            // Given
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);
            byte[] buffer = new byte[10];

            // When
            int bytesRead = stream.read(buffer, 2, 5);

            // Then
            assertThat(bytesRead).isEqualTo(5);
            assertThat(buffer[2]).isEqualTo((byte) 'h');
            assertThat(buffer[3]).isEqualTo((byte) 'e');
        }

        @Test
        @DisplayName("isFinished should return false when data available")
        void cachedInputStream_IsFinishedReturnsFalseWhenDataAvailable() {
            // Given
            byte[] data = "test".getBytes(StandardCharsets.UTF_8);
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);

            // Then
            assertThat(stream.isFinished()).isFalse();
        }

        @Test
        @DisplayName("isFinished should return true when stream exhausted")
        void cachedInputStream_IsFinishedReturnsTrueWhenExhausted() throws IOException {
            // Given
            byte[] data = "ab".getBytes(StandardCharsets.UTF_8);
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);

            // When
            stream.read();
            stream.read();

            // Then
            assertThat(stream.isFinished()).isTrue();
        }

        @Test
        @DisplayName("isFinished should return true for empty stream")
        void cachedInputStream_IsFinishedReturnsTrueForEmptyStream() {
            // Given
            byte[] data = new byte[0];
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);

            // Then
            assertThat(stream.isFinished()).isTrue();
        }

        @Test
        @DisplayName("isReady should always return true")
        void cachedInputStream_IsReadyAlwaysReturnsTrue() {
            // Given
            byte[] data = "test".getBytes(StandardCharsets.UTF_8);
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);

            // Then
            assertThat(stream.isReady()).isTrue();
        }

        @Test
        @DisplayName("isReady should return true even for empty stream")
        void cachedInputStream_IsReadyReturnsTrueForEmptyStream() {
            // Given
            byte[] data = new byte[0];
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);

            // Then
            assertThat(stream.isReady()).isTrue();
        }

        @Test
        @DisplayName("setReadListener should throw RuntimeException")
        void cachedInputStream_SetReadListenerThrowsException() {
            // Given
            byte[] data = "test".getBytes(StandardCharsets.UTF_8);
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);
            ReadListener mockListener = mock(ReadListener.class);

            // When/Then
            assertThatThrownBy(() -> stream.setReadListener(mockListener))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Not implemented");
        }

        @Test
        @DisplayName("Should handle reading all bytes at once")
        void cachedInputStream_ReadsAllBytes() throws IOException {
            // Given
            String content = "complete content";
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            CachedBodyServletInputStream stream = new CachedBodyServletInputStream(data);

            // When
            byte[] result = stream.readAllBytes();

            // Then
            assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    // ==================== EarlyRequestLoggingFilter Tests ====================

    @Nested
    @DisplayName("EarlyRequestLoggingFilter - Request Logging")
    class EarlyRequestLoggingFilterTests {

        private EarlyRequestLoggingFilter earlyFilter;

        @BeforeEach
        void setUpEarlyFilter() {
            earlyFilter = new EarlyRequestLoggingFilter(testCorsProperties());
        }

        @Test
        @DisplayName("Should log incoming request and continue filter chain")
        void doFilter_LogsAndContinuesChain() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/test");
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(200);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should set MDC context for REQUEST_ID")
        void doFilter_SetsMdcRequestId() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(201);

            doAnswer(invocation -> {
                String requestId = MDC.get("REQUEST_ID");
                assertThat(requestId).isNotNull().startsWith("REQ-");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should set correlationId in MDC")
        void doFilter_SetsMdcCorrelationId() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/test");
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(200);

            doAnswer(invocation -> {
                String correlationId = MDC.get("correlationId");
                assertThat(correlationId).isNotNull().startsWith("REQ-");
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle OPTIONS request (CORS preflight)")
        void doFilter_HandlesOptionsRequest() throws ServletException, IOException {
            // Given
            request.setMethod("OPTIONS");
            request.setRequestURI("/api/users");
            request.addHeader("Origin", "http://localhost:4200");
            request.addHeader("User-Agent", "Test-Agent");
            request.addHeader("Access-Control-Request-Method", "POST");
            request.addHeader("Access-Control-Request-Headers", "Content-Type");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(200);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle exception during filter chain and rethrow")
        void doFilter_HandlesExceptionAndRethrows() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/error");
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");

            ServletException expectedException = new ServletException("Test exception");
            doThrow(expectedException).when(filterChain).doFilter(any(), any());

            // When/Then
            assertThatThrownBy(() -> earlyFilter.doFilter(request, response, filterChain))
                    .isSameAs(expectedException);
        }

        @Test
        @DisplayName("Should extract client IP from X-Forwarded-For header")
        void doFilter_ExtractsIpFromXForwardedFor() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/test");
            request.addHeader("User-Agent", "Test-Agent");
            request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
            response.setStatus(200);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should extract client IP from X-Real-IP header")
        void doFilter_ExtractsIpFromXRealIp() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/test");
            request.addHeader("User-Agent", "Test-Agent");
            request.addHeader("X-Real-IP", "10.0.0.5");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(200);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("EarlyRequestLoggingFilter - Origin Authorization")
    class OriginAuthorizationTests {

        private EarlyRequestLoggingFilter earlyFilter;

        @BeforeEach
        void setUpEarlyFilter() {
            earlyFilter = new EarlyRequestLoggingFilter(testCorsProperties());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://localhost:4200",
                "https://localhost:4200",
                "http://localhost:80",
                "https://localhost:80",
                "http://bs-local.com:4200",
                "https://bs-local.com:4200",
                "https://app.check-it-out.pl",
                "https://checkitout.app"
        })
        @DisplayName("Should allow authorized origins")
        void doFilter_AllowsAuthorizedOrigins(String origin) throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.addHeader("Origin", origin);
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(200);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://evil.com",
                "https://attacker.io",
                "http://localhost:3000",
                "https://malicious-site.example"
        })
        @DisplayName("Should log unauthorized origins but continue processing")
        void doFilter_LogsUnauthorizedOrigins(String origin) throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.addHeader("Origin", origin);
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(403);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should allow request with null origin (non-CORS)")
        void doFilter_AllowsNullOrigin() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/health");
            request.addHeader("User-Agent", "curl/7.68.0");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(200);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should detect suspicious pattern - long origin")
        void doFilter_DetectsLongOrigin() throws ServletException, IOException {
            // Given
            String longOrigin = "http://evil.com/" + "a".repeat(150);
            request.setMethod("POST");
            request.setRequestURI("/api/test");
            request.addHeader("Origin", longOrigin);
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(403);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should detect suspicious pattern - path traversal attempt")
        void doFilter_DetectsPathTraversalAttempt() throws ServletException, IOException {
            // Given
            String suspiciousOrigin = "http://evil.com/../../../etc";
            request.setMethod("POST");
            request.setRequestURI("/api/test");
            request.addHeader("Origin", suspiciousOrigin);
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(403);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("EarlyRequestLoggingFilter - Response Status Logging")
    class ResponseStatusLoggingTests {

        private EarlyRequestLoggingFilter earlyFilter;

        @BeforeEach
        void setUpEarlyFilter() {
            earlyFilter = new EarlyRequestLoggingFilter(testCorsProperties());
        }

        @Test
        @DisplayName("Should log success for 2xx status")
        void doFilter_LogsSuccessFor2xxStatus() throws ServletException, IOException {
            // Given
            request.setMethod("GET");
            request.setRequestURI("/api/users");
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");
            response.setStatus(200);

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should log warning for 4xx status")
        void doFilter_LogsWarningFor4xxStatus() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");

            doAnswer(invocation -> {
                response.setStatus(400);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should log warning for 5xx status")
        void doFilter_LogsWarningFor5xxStatus() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");

            doAnswer(invocation -> {
                response.setStatus(500);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should log CORS rejection for 4xx with unauthorized origin")
        void doFilter_LogsCorsRejection() throws ServletException, IOException {
            // Given
            request.setMethod("POST");
            request.setRequestURI("/api/users");
            request.addHeader("Origin", "http://evil.com");
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");

            doAnswer(invocation -> {
                response.setStatus(403);
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            earlyFilter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Filter Integration Tests")
    class FilterIntegrationTests {

        @Test
        @DisplayName("Should work with both filters in sequence")
        void shouldWorkWithBothFiltersInSequence() throws ServletException, IOException {
            // Given
            EarlyRequestLoggingFilter earlyFilter = new EarlyRequestLoggingFilter(testCorsProperties());
            RequestBodyCachingFilter cachingFilter = new RequestBodyCachingFilter();

            request.setRequestURI("/api/test");
            request.setMethod("POST");
            request.setContentType("application/json");
            request.setContent("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));
            request.addHeader("User-Agent", "Test-Agent");
            request.setRemoteAddr("127.0.0.1");

            FilterChain innerChain = mock(FilterChain.class);
            doAnswer(invocation -> {
                assertThat(MDC.get("REQUEST_ID")).isNotNull();
                assertThat(MDC.get("correlationId")).isNotNull();
                cachingFilter.doFilter(request, response, filterChain);
                return null;
            }).when(innerChain).doFilter(any(), any());

            // When
            earlyFilter.doFilter(request, response, innerChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should cache body and allow multiple reads through filter chain")
        void shouldCacheBodyAndAllowMultipleReads() throws ServletException, IOException {
            // Given
            String jsonBody = "{\"username\":\"testuser\"}";
            request.setRequestURI("/api/users");
            request.setMethod("POST");
            request.setContentType("application/json");
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            doAnswer(invocation -> {
                HttpServletRequest capturedReq = invocation.getArgument(0);
                if (capturedReq instanceof CachedBodyHttpServletRequest) {
                    CachedBodyHttpServletRequest cachedReq = (CachedBodyHttpServletRequest) capturedReq;
                    String body1 = cachedReq.getCachedBody();
                    String body2 = new BufferedReader(cachedReq.getReader()).lines()
                            .reduce("", (a, b) -> a + b);
                    assertThat(body1).isEqualTo(jsonBody);
                    assertThat(body2).isEqualTo(jsonBody);
                }
                return null;
            }).when(filterChain).doFilter(requestCaptor.capture(), any());

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
            assertThat(requestCaptor.getValue()).isInstanceOf(CachedBodyHttpServletRequest.class);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle request with very long URI")
        void shouldHandleRequestWithVeryLongUri() throws ServletException, IOException {
            // Given
            String longUri = "/api/" + "a".repeat(1000);
            request.setRequestURI(longUri);
            request.setMethod("GET");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle request with special characters in URI")
        void shouldHandleRequestWithSpecialCharactersInUri() throws ServletException, IOException {
            // Given
            request.setRequestURI("/api/test?query=hello%20world&name=%E2%9C%93");
            request.setMethod("GET");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle large request body within limits")
        void shouldHandleLargeRequestBodyWithinLimits() throws ServletException, IOException {
            // Given
            byte[] largeBody = new byte[4000];
            java.util.Arrays.fill(largeBody, (byte) 'a');
            String jsonBody = "{\"data\":\"" + new String(largeBody, StandardCharsets.UTF_8) + "\"}";

            request.setRequestURI("/api/test");
            request.setMethod("POST");
            request.setContentType("application/json");
            request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));

            ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(requestCaptor.capture(), any());
            assertThat(requestCaptor.getValue()).isInstanceOf(CachedBodyHttpServletRequest.class);
        }

        @Test
        @DisplayName("Should handle concurrent requests")
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

                        RequestBodyCachingFilter threadFilter = new RequestBodyCachingFilter();
                        threadFilter.doFilter(threadRequest, threadResponse, threadFilterChain);

                        verify(threadFilterChain).doFilter(any(), any());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }
        }
    }

    private static CorsProperties testCorsProperties() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins(List.of(
                "http://localhost:4200", "https://localhost:4200",
                "http://localhost:80", "https://localhost:80",
                "http://bs-local.com:4200", "https://bs-local.com:4200",
                "https://app.check-it-out.pl", "https://checkitout.app"
        ));
        return props;
    }
}
