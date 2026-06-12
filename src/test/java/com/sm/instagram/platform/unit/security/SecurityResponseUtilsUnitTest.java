package com.sm.instagram.platform.unit.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.util.SecurityResponseUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SecurityResponseUtils.
 * Uses light mocking with MockHttpServletRequest/Response from spring-test.
 * No Spring context needed - tests pure servlet manipulation logic.
 */
@DisplayName("SecurityResponseUtils Unit Tests")
class SecurityResponseUtilsUnitTest {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for LocalDateTime
    }

    @Nested
    @DisplayName("writeAuthenticationFailureResponse Tests")
    class WriteUnauthorizedResponseTests {

        @Test
        @DisplayName("should set correct status 401 for unauthorized response")
        void writeUnauthorizedResponse_setsCorrectStatus() throws IOException {
            // Given
            request.setRequestURI("/api/protected/resource");
            Exception authException = new RuntimeException("Invalid credentials");

            // When
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, authException, objectMapper);

            // Then
            assertThat(response.getStatus())
                    .isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("should set correct content type application/json for unauthorized response")
        void writeUnauthorizedResponse_setsCorrectContentType() throws IOException {
            // Given
            request.setRequestURI("/api/protected/resource");
            Exception authException = new RuntimeException("Token expired");

            // When
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, authException, objectMapper);

            // Then
            assertThat(response.getContentType())
                    .isEqualTo("application/json");
        }

        @Test
        @DisplayName("should write JSON body with correct structure for unauthorized response")
        void writeUnauthorizedResponse_writesJsonBody() throws IOException {
            // Given
            request.setRequestURI("/api/users/profile");
            Exception authException = new RuntimeException("No token provided");

            // When
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, authException, objectMapper);

            // Then
            String responseContent = response.getContentAsString();
            assertThat(responseContent).isNotEmpty();

            JsonNode jsonNode = objectMapper.readTree(responseContent);
            assertThat(jsonNode.has("status")).isTrue();
            assertThat(jsonNode.get("status").asInt()).isEqualTo(401);
            assertThat(jsonNode.has("error")).isTrue();
            assertThat(jsonNode.get("error").asText()).isEqualTo("Unauthorized");
            assertThat(jsonNode.has("message")).isTrue();
            assertThat(jsonNode.get("message").asText()).isEqualTo("Authentication required");
            assertThat(jsonNode.has("path")).isTrue();
            assertThat(jsonNode.get("path").asText()).isEqualTo("/api/users/profile");
        }
    }

    @Nested
    @DisplayName("writeAccessDeniedResponse Tests")
    class WriteForbiddenResponseTests {

        @Test
        @DisplayName("should set 403 status for forbidden response")
        void writeForbiddenResponse_sets403Status() throws IOException {
            // Given
            request.setRequestURI("/api/admin/settings");
            Exception accessDeniedException = new RuntimeException("Insufficient permissions");

            // When
            SecurityResponseUtils.writeAccessDeniedResponse(request, response, accessDeniedException, objectMapper);

            // Then
            assertThat(response.getStatus())
                    .isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("writeErrorResponse Custom Status Tests")
    class WriteErrorResponseCustomStatusTests {

        @Test
        @DisplayName("should set custom status correctly for authentication failure (401)")
        void writeErrorResponse_customStatus_setsCorrectly() throws IOException {
            // Given
            request.setRequestURI("/api/custom/endpoint");
            Exception exception = new RuntimeException("Custom error");

            // When - Using authentication failure for 401
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, exception, objectMapper);

            // Then
            assertThat(response.getStatus()).isEqualTo(401);

            // Also test 403 with access denied
            MockHttpServletResponse response403 = new MockHttpServletResponse();
            SecurityResponseUtils.writeAccessDeniedResponse(request, response403, exception, objectMapper);
            assertThat(response403.getStatus()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("Error Response Content Tests")
    class ErrorResponseContentTests {

        @Test
        @DisplayName("should include timestamp in error response")
        void writeErrorResponse_includesTimestamp() throws IOException {
            // Given
            request.setRequestURI("/api/test");
            Exception exception = new RuntimeException("Test error");

            // When
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, exception, objectMapper);

            // Then
            String responseContent = response.getContentAsString();
            JsonNode jsonNode = objectMapper.readTree(responseContent);

            assertThat(jsonNode.has("timestamp")).isTrue();
            String timestamp = jsonNode.get("timestamp").asText();
            // Timestamp should be in ISO format: yyyy-MM-dd'T'HH:mm:ss.SSS
            assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
        }

        @Test
        @DisplayName("should include request path in error response")
        void writeErrorResponse_includesRequestPath() throws IOException {
            // Given
            String expectedPath = "/api/v1/users/123/profile";
            request.setRequestURI(expectedPath);
            Exception exception = new RuntimeException("Path test error");

            // When
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, exception, objectMapper);

            // Then
            String responseContent = response.getContentAsString();
            JsonNode jsonNode = objectMapper.readTree(responseContent);

            assertThat(jsonNode.has("path")).isTrue();
            assertThat(jsonNode.get("path").asText()).isEqualTo(expectedPath);
        }

        @Test
        @DisplayName("should include requestId in error response")
        void writeErrorResponse_includesRequestId() throws IOException {
            // Given
            request.setRequestURI("/api/test");
            Exception exception = new RuntimeException("Request ID test");

            // When
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, exception, objectMapper);

            // Then
            String responseContent = response.getContentAsString();
            JsonNode jsonNode = objectMapper.readTree(responseContent);

            assertThat(jsonNode.has("requestId")).isTrue();
            String requestId = jsonNode.get("requestId").asText();
            // RequestId should be either from MDC correlationId or generated with SEC- prefix
            assertThat(requestId).isNotEmpty();
            // The format is either correlationId from MDC or SEC-xxxxxxxx
            assertThat(requestId).matches("(SEC-[a-f0-9]{8}|[a-zA-Z0-9-]+)");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Additional Scenarios")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle null exception gracefully")
        void shouldHandleNullExceptionGracefully() throws IOException {
            // Given
            request.setRequestURI("/api/test");

            // When
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, null, objectMapper);

            // Then
            assertThat(response.getStatus()).isEqualTo(401);
            String responseContent = response.getContentAsString();
            assertThat(responseContent).isNotEmpty();
        }

        @Test
        @DisplayName("should handle various request paths correctly")
        void shouldHandleVariousRequestPathsCorrectly() throws IOException {
            // Given - test with query params in URI (though they shouldn't be there typically)
            request.setRequestURI("/api/users");
            request.setQueryString("filter=active&page=1");
            Exception exception = new RuntimeException("Test");

            // When
            SecurityResponseUtils.writeAuthenticationFailureResponse(request, response, exception, objectMapper);

            // Then
            String responseContent = response.getContentAsString();
            JsonNode jsonNode = objectMapper.readTree(responseContent);
            assertThat(jsonNode.get("path").asText()).isEqualTo("/api/users");
        }

        @Test
        @DisplayName("access denied response should include correct error message")
        void accessDeniedResponseShouldIncludeCorrectErrorMessage() throws IOException {
            // Given
            request.setRequestURI("/api/admin/users");
            Exception exception = new RuntimeException("User is not admin");

            // When
            SecurityResponseUtils.writeAccessDeniedResponse(request, response, exception, objectMapper);

            // Then
            String responseContent = response.getContentAsString();
            JsonNode jsonNode = objectMapper.readTree(responseContent);

            assertThat(jsonNode.get("error").asText()).isEqualTo("Forbidden");
            assertThat(jsonNode.get("message").asText()).isEqualTo("Access denied - insufficient permissions");
            assertThat(jsonNode.get("status").asInt()).isEqualTo(403);
        }
    }
}
