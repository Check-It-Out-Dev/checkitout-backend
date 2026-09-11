package com.sm.instagram.platform.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.exceptions.GlobalDefaultExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.BusinessExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.RateLimitExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.StorageExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.ValidationExceptionHandler;
import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.storage.controller.FileUploadController;
import com.sm.instagram.platform.storage.model.FileUploadResponse;
import com.sm.instagram.platform.storage.service.FileTrackingService;
import com.sm.instagram.platform.storage.service.SignedUrlService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import com.sm.instagram.platform.storage.service.UploadMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for FileUploadController using @WebMvcTest.
 * Tests HTTP endpoints, request validation, and response formatting.
 * Does NOT load full Spring context - only web layer with mocked services.
 *
 * Note: FileUploadController has @ConditionalOnBean(SignedUrlService.class),
 * so we need to manually instantiate it in a test configuration.
 */
@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {
        FileUploadControllerUnitTest.TestConfig.class,
        TestControllerSecurityConfig.class,
        BusinessExceptionHandler.class,
        StorageExceptionHandler.class,
        ValidationExceptionHandler.class,
        RateLimitExceptionHandler.class,
        GlobalDefaultExceptionHandler.class
})
@DisplayName("FileUploadController")
class FileUploadControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SignedUrlService signedUrlService;

    @MockBean
    private StorageRateLimitService rateLimiterService;

    @MockBean
    private UploadMetricsService metricsService;

    @MockBean
    private FileTrackingService trackingService;

    @MockBean
    private PermissionUtils permissionUtils;

    @MockBean
    private TranslationService translationService;

    @MockBean
    private org.springframework.context.MessageSource messageSource;

    // Test fixtures - using raw JSON to avoid getFileExtension() getter serialization issues
    private String validRequestJson;
    private FileUploadResponse successResponse;
    private static final String TEST_USER_ID = "test-firebase-uid-123";
    private static final String TEST_UPLOAD_ID = "upload-uuid-456";

    @BeforeEach
    void setUp() {
        // Setup valid file upload request as JSON (avoiding ObjectMapper serializing getFileExtension())
        validRequestJson = """
            {
                "filename": "test-image.jpg",
                "contentType": "image/jpeg",
                "fileSize": 1024000,
                "description": "Test image description",
                "altText": "Alt text for accessibility"
            }
            """;

        // Setup success response
        successResponse = new FileUploadResponse(
                "https://storage.googleapis.com/signed-url",
                "https://firebasestorage.googleapis.com/public-url",
                "content/test-user/123456_test-image.jpg",
                Instant.now().plus(5, ChronoUnit.MINUTES),
                TEST_UPLOAD_ID
        );
        successResponse.setRateLimitInfo(new FileUploadResponse.RateLimitInfo(9, 49, 10L, 100L));
        successResponse.setUploadInstructions(new FileUploadResponse.UploadInstructions());

        // Default mock for permissionUtils
        when(permissionUtils.getUserId()).thenReturn(TEST_USER_ID);
    }

    /**
     * Helper method to create request JSON with custom values
     */
    private String createRequestJson(String filename, String contentType, Long fileSize, String uploadType) {
        StringBuilder json = new StringBuilder("{");
        if (filename != null) {
            json.append("\"filename\":\"").append(filename).append("\",");
        }
        if (contentType != null) {
            json.append("\"contentType\":\"").append(contentType).append("\",");
        }
        if (fileSize != null) {
            json.append("\"fileSize\":").append(fileSize).append(",");
        }
        if (uploadType != null) {
            json.append("\"uploadType\":\"").append(uploadType).append("\",");
        }
        // Remove trailing comma if present
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
        json.append("}");
        return json.toString();
    }

    @Nested
    @DisplayName("POST /upload/signed-url")
    class GenerateSignedUrl {

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should generate signed URL for valid request")
        void shouldGenerateSignedUrlForValidRequest() throws Exception {
            // Given
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadUrl").value("https://storage.googleapis.com/signed-url"))
                    .andExpect(jsonPath("$.publicUrl").value("https://firebasestorage.googleapis.com/public-url"))
                    .andExpect(jsonPath("$.filePath").value("content/test-user/123456_test-image.jpg"))
                    .andExpect(jsonPath("$.uploadId").value(TEST_UPLOAD_ID))
                    .andExpect(jsonPath("$.rateLimitInfo").exists())
                    .andExpect(jsonPath("$.rateLimitInfo.remainingHourly").value(9))
                    .andExpect(jsonPath("$.rateLimitInfo.remainingDaily").value(49))
                    .andExpect(jsonPath("$.uploadInstructions").exists())
                    .andExpect(jsonPath("$.uploadInstructions.method").value("PUT"));

            verify(signedUrlService).generateSignedUrl(eq(TEST_USER_ID), any());
        }

        @Test
        @WithMockUser(authorities = "COMPANY")
        @DisplayName("should generate signed URL for company user")
        void shouldGenerateSignedUrlForCompanyUser() throws Exception {
            // Given
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadId").value(TEST_UPLOAD_ID));

            verify(signedUrlService).generateSignedUrl(eq(TEST_USER_ID), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should handle rate limit exceeded error from response")
        void shouldHandleRateLimitExceededError() throws Exception {
            // Given
            FileUploadResponse errorResponse = new FileUploadResponse("Rate limit exceeded");
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(errorResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should handle storage quota exceeded error from response")
        void shouldHandleStorageQuotaExceededError() throws Exception {
            // Given
            FileUploadResponse errorResponse = new FileUploadResponse("Storage quota exceeded");
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(errorResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isInsufficientStorage());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should handle generic storage error from response")
        void shouldHandleGenericStorageError() throws Exception {
            // Given
            FileUploadResponse errorResponse = new FileUploadResponse("Unknown error occurred");
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(errorResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isInsufficientStorage());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should accept request with PROFILE_PHOTO upload type")
        void shouldAcceptRequestWithProfilePhotoUploadType() throws Exception {
            // Given
            String requestJson = createRequestJson("profile.jpg", "image/jpeg", 1024000L, "PROFILE_PHOTO");
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(authorities = "COMPANY")
        @DisplayName("should accept request with CAMPAIGN_MEDIA upload type")
        void shouldAcceptRequestWithCampaignMediaUploadType() throws Exception {
            // Given
            String requestJson = createRequestJson("campaign.jpg", "image/jpeg", 1024000L, "CAMPAIGN_MEDIA");
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should accept PNG content type")
        void shouldAcceptPngContentType() throws Exception {
            // Given
            String requestJson = createRequestJson("test-image.png", "image/png", 1024000L, null);
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should accept GIF content type")
        void shouldAcceptGifContentType() throws Exception {
            // Given
            String requestJson = createRequestJson("animation.gif", "image/gif", 1024000L, null);
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should accept WebP content type")
        void shouldAcceptWebpContentType() throws Exception {
            // Given
            String requestJson = createRequestJson("modern-image.webp", "image/webp", 1024000L, null);
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /upload/signed-url - Validation")
    class GenerateSignedUrlValidation {

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should reject request with missing filename")
        void shouldRejectRequestWithMissingFilename() throws Exception {
            // Given - no filename field
            String requestJson = """
                {
                    "contentType": "image/jpeg",
                    "fileSize": 1024000
                }
                """;

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).generateSignedUrl(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should reject request with blank filename")
        void shouldRejectRequestWithBlankFilename() throws Exception {
            // Given
            String requestJson = """
                {
                    "filename": "",
                    "contentType": "image/jpeg",
                    "fileSize": 1024000
                }
                """;

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).generateSignedUrl(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should reject request with missing content type")
        void shouldRejectRequestWithMissingContentType() throws Exception {
            // Given
            String requestJson = """
                {
                    "filename": "test.jpg",
                    "fileSize": 1024000
                }
                """;

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).generateSignedUrl(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should reject request with invalid content type")
        void shouldRejectRequestWithInvalidContentType() throws Exception {
            // Given - PDF is not allowed
            String requestJson = """
                {
                    "filename": "document.pdf",
                    "contentType": "application/pdf",
                    "fileSize": 1024000
                }
                """;

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).generateSignedUrl(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should reject request with missing file size")
        void shouldRejectRequestWithMissingFileSize() throws Exception {
            // Given
            String requestJson = """
                {
                    "filename": "test.jpg",
                    "contentType": "image/jpeg"
                }
                """;

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).generateSignedUrl(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should reject request with zero file size")
        void shouldRejectRequestWithZeroFileSize() throws Exception {
            // Given
            String requestJson = """
                {
                    "filename": "test.jpg",
                    "contentType": "image/jpeg",
                    "fileSize": 0
                }
                """;

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).generateSignedUrl(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should reject request with file size exceeding max")
        void shouldRejectRequestWithFileSizeExceedingMax() throws Exception {
            // Given - Max is 5MB (5242880 bytes)
            String requestJson = """
                {
                    "filename": "test.jpg",
                    "contentType": "image/jpeg",
                    "fileSize": 5242881
                }
                """;

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).generateSignedUrl(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should reject request with invalid filename pattern")
        void shouldRejectRequestWithInvalidFilenamePattern() throws Exception {
            // Given - filename contains special characters not allowed
            String requestJson = """
                {
                    "filename": "file@name#test.jpg",
                    "contentType": "image/jpeg",
                    "fileSize": 1024000
                }
                """;

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).generateSignedUrl(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should accept request at exact max file size limit")
        void shouldAcceptRequestAtExactMaxFileSizeLimit() throws Exception {
            // Given - Max is 5MB (5242880 bytes)
            String requestJson = """
                {
                    "filename": "test.jpg",
                    "contentType": "image/jpeg",
                    "fileSize": 5242880
                }
                """;
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should accept filename with dots, dashes, and underscores")
        void shouldAcceptFilenameWithDotsAndDashes() throws Exception {
            // Given
            String requestJson = """
                {
                    "filename": "my-file_name.2024.jpg",
                    "contentType": "image/jpeg",
                    "fileSize": 1024000
                }
                """;
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /upload/confirm/{uploadId}")
    class ConfirmUpload {

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should confirm successful upload")
        void shouldConfirmSuccessfulUpload() throws Exception {
            // Given
            String filePath = "content/test-user/123456_test-image.jpg";
            when(signedUrlService.validateUploadSuccess(filePath)).thenReturn(true);
            doNothing().when(signedUrlService).confirmUpload(TEST_USER_ID, filePath);

            // When/Then
            mockMvc.perform(post("/upload/confirm/{uploadId}", TEST_UPLOAD_ID)
                            .param("filePath", filePath))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("confirmed"))
                    .andExpect(jsonPath("$.uploadId").value(TEST_UPLOAD_ID))
                    .andExpect(jsonPath("$.filePath").value(filePath))
                    .andExpect(jsonPath("$.message").value("Upload confirmed successfully"));

            verify(signedUrlService).validateUploadSuccess(filePath);
            verify(signedUrlService).confirmUpload(TEST_USER_ID, filePath);
        }

        @Test
        @WithMockUser(authorities = "COMPANY")
        @DisplayName("should confirm upload for company user")
        void shouldConfirmUploadForCompanyUser() throws Exception {
            // Given
            String filePath = "content/company-user/campaign_image.jpg";
            when(signedUrlService.validateUploadSuccess(filePath)).thenReturn(true);
            doNothing().when(signedUrlService).confirmUpload(TEST_USER_ID, filePath);

            // When/Then
            mockMvc.perform(post("/upload/confirm/{uploadId}", TEST_UPLOAD_ID)
                            .param("filePath", filePath))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("confirmed"));
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should return 404 when file not found in storage")
        void shouldReturn404WhenFileNotFoundInStorage() throws Exception {
            // Given
            String filePath = "content/test-user/non-existent.jpg";
            when(signedUrlService.validateUploadSuccess(filePath)).thenReturn(false);

            // When/Then
            mockMvc.perform(post("/upload/confirm/{uploadId}", TEST_UPLOAD_ID)
                            .param("filePath", filePath))
                    .andExpect(status().isNotFound());

            verify(signedUrlService).validateUploadSuccess(filePath);
            verify(signedUrlService, never()).confirmUpload(any(), any());
        }
    }

    @Nested
    @DisplayName("A refusal keeps the status it means, rather than becoming 507")
    class RefusalStatuses {

        /**
         * Both upload handlers end in a catch-all that rethrows everything as
         * {@code error.storage.upload_failed}, which is 507 Insufficient Storage -- a claim about
         * the server's disk. {@code POST /upload/confirm/0?filePath=} came back 507 when the
         * service had simply refused an empty path, and a caller reading that goes looking for a
         * quota problem that does not exist. The catch-all is still there for what is genuinely
         * unexpected; what it may not do is relabel an exception that already knows its own status.
         */
        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("confirm: an empty filePath is a 400")
        void confirmRejectsEmptyPathAsBadRequest() throws Exception {
            when(signedUrlService.validateUploadSuccess(""))
                    .thenThrow(new ValidationTranslatableException("error.validation.required_field", "filePath"));

            mockMvc.perform(post("/upload/confirm/{uploadId}", "0").param("filePath", ""))
                    .andExpect(status().isBadRequest());

            verify(signedUrlService, never()).confirmUpload(any(), any());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("confirm: a storage failure is still a 507")
        void confirmKeepsStorageFailureAsInsufficientStorage() throws Exception {
            String filePath = "content/test-user/whatever.jpg";
            when(signedUrlService.validateUploadSuccess(filePath))
                    .thenThrow(new StorageTranslatableException("error.storage.upload_failed"));

            mockMvc.perform(post("/upload/confirm/{uploadId}", TEST_UPLOAD_ID).param("filePath", filePath))
                    .andExpect(status().isInsufficientStorage());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("confirm: something genuinely unexpected is still a 507")
        void confirmKeepsUnexpectedFailureAsInsufficientStorage() throws Exception {
            String filePath = "content/test-user/whatever.jpg";
            when(signedUrlService.validateUploadSuccess(filePath))
                    .thenThrow(new IllegalStateException("the sink is in an impossible state"));

            mockMvc.perform(post("/upload/confirm/{uploadId}", TEST_UPLOAD_ID).param("filePath", filePath))
                    .andExpect(status().isInsufficientStorage());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("signed-url: a refusal from the service is a 400")
        void signedUrlRejectionIsBadRequest() throws Exception {
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenThrow(new ValidationTranslatableException("error.validation.required_field", "filename"));

            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /upload/limits")
    class GetRateLimitStatus {

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should return rate limit status for authenticated user")
        void shouldReturnRateLimitStatusForAuthenticatedUser() throws Exception {
            // Given
            StorageRateLimitService.RateLimitStatus status =
                    new StorageRateLimitService.RateLimitStatus(5, 10, 20, 50, 50000000L, 100000000L);
            when(rateLimiterService.getUserStatus(TEST_USER_ID)).thenReturn(status);

            // When/Then
            mockMvc.perform(get("/upload/limits"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hourly.used").value(5))
                    .andExpect(jsonPath("$.hourly.limit").value(10))
                    .andExpect(jsonPath("$.hourly.remaining").value(5))
                    .andExpect(jsonPath("$.daily.used").value(20))
                    .andExpect(jsonPath("$.daily.limit").value(50))
                    .andExpect(jsonPath("$.daily.remaining").value(30))
                    .andExpect(jsonPath("$.storage").exists());

            verify(rateLimiterService).getUserStatus(TEST_USER_ID);
        }

        @Test
        @WithMockUser(authorities = "COMPANY")
        @DisplayName("should return rate limit status for company user")
        void shouldReturnRateLimitStatusForCompanyUser() throws Exception {
            // Given
            StorageRateLimitService.RateLimitStatus status =
                    new StorageRateLimitService.RateLimitStatus(2, 30, 10, 150, 100000000L, 500000000L);
            when(rateLimiterService.getUserStatus(TEST_USER_ID)).thenReturn(status);

            // When/Then
            mockMvc.perform(get("/upload/limits"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hourly.limit").value(30))
                    .andExpect(jsonPath("$.daily.limit").value(150));
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should return storage usage percentage")
        void shouldReturnStorageUsagePercentage() throws Exception {
            // Given - 50MB used out of 100MB
            StorageRateLimitService.RateLimitStatus status =
                    new StorageRateLimitService.RateLimitStatus(0, 10, 0, 50, 52428800L, 104857600L);
            when(rateLimiterService.getUserStatus(TEST_USER_ID)).thenReturn(status);

            // When/Then
            mockMvc.perform(get("/upload/limits"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.storage.usedPercentage").exists());
        }
    }

    @Nested
    @DisplayName("GET /upload/health")
    class HealthCheck {

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should return healthy status")
        void shouldReturnHealthyStatus() throws Exception {
            // When/Then
            mockMvc.perform(get("/upload/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.service").value("file-upload"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @WithMockUser(authorities = "COMPANY")
        @DisplayName("should return health for company user")
        void shouldReturnHealthForCompanyUser() throws Exception {
            // When/Then
            mockMvc.perform(get("/upload/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("should return health for admin user")
        void shouldReturnHealthForAdminUser() throws Exception {
            // When/Then
            mockMvc.perform(get("/upload/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Nested
    @DisplayName("GET /upload/stats")
    class GetUserUploadStats {

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should return upload statistics for user")
        void shouldReturnUploadStatisticsForUser() throws Exception {
            // Given
            StorageRateLimitService.RateLimitStatus status =
                    new StorageRateLimitService.RateLimitStatus(3, 10, 15, 50, 25000000L, 100000000L);
            when(rateLimiterService.getUserStatus(TEST_USER_ID)).thenReturn(status);

            FileTrackingService.UserUploadStats uploadStats =
                    new FileTrackingService.UserUploadStats(100, 500000000L, 5, 2);
            when(trackingService.getUserStats(TEST_USER_ID)).thenReturn(uploadStats);

            // When/Then
            mockMvc.perform(get("/upload/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rateLimits").exists())
                    .andExpect(jsonPath("$.rateLimits.hourly.used").value(3))
                    .andExpect(jsonPath("$.rateLimits.hourly.limit").value(10))
                    .andExpect(jsonPath("$.rateLimits.daily.used").value(15))
                    .andExpect(jsonPath("$.rateLimits.daily.limit").value(50))
                    .andExpect(jsonPath("$.uploadHistory").exists())
                    .andExpect(jsonPath("$.uploadHistory.totalFiles").value(100))
                    .andExpect(jsonPath("$.uploadHistory.totalSizeBytes").value(500000000L))
                    .andExpect(jsonPath("$.uploadHistory.filesUploadedToday").value(5))
                    .andExpect(jsonPath("$.uploadHistory.filesUploadedThisHour").value(2));

            verify(rateLimiterService).getUserStatus(TEST_USER_ID);
            verify(trackingService).getUserStats(TEST_USER_ID);
        }

        @Test
        @WithMockUser(authorities = "COMPANY")
        @DisplayName("should return stats for company user")
        void shouldReturnStatsForCompanyUser() throws Exception {
            // Given
            StorageRateLimitService.RateLimitStatus status =
                    new StorageRateLimitService.RateLimitStatus(0, 30, 0, 150, 0L, 500000000L);
            when(rateLimiterService.getUserStatus(TEST_USER_ID)).thenReturn(status);

            FileTrackingService.UserUploadStats uploadStats =
                    new FileTrackingService.UserUploadStats(0, 0L, 0, 0);
            when(trackingService.getUserStats(TEST_USER_ID)).thenReturn(uploadStats);

            // When/Then
            mockMvc.perform(get("/upload/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rateLimits").exists())
                    .andExpect(jsonPath("$.uploadHistory").exists());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should handle RateLimitTranslatableException from service")
        void shouldHandleRateLimitTranslatableException() throws Exception {
            // Given
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenThrow(new RateLimitTranslatableException("error.storage.rate_limit_exceeded", "uploads"));

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should handle StorageTranslatableException from service")
        void shouldHandleStorageTranslatableException() throws Exception {
            // Given
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenThrow(new StorageTranslatableException("error.storage.quota_exceeded"));

            // When/Then
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isInsufficientStorage());
        }
    }

    @Nested
    @DisplayName("Metrics Recording")
    class MetricsRecording {

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should record metrics on successful signed URL generation")
        void shouldRecordMetricsOnSuccessfulSignedUrlGeneration() throws Exception {
            // Given
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(successResponse);

            // When
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isOk());

            // Then - verify metrics service was called
            verify(metricsService).recordUploadRequest();
            verify(metricsService).incrementActiveUploads();
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should record failure metrics on rate limit error from response")
        void shouldRecordFailureMetricsOnRateLimitError() throws Exception {
            // Given
            FileUploadResponse errorResponse = new FileUploadResponse("Rate limit exceeded");
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(errorResponse);

            // When
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isTooManyRequests());

            // Then
            verify(metricsService).recordRateLimitHit("upload");
            verify(metricsService).recordUploadFailure(anyString());
            verify(metricsService).decrementActiveUploads();
        }

        @Test
        @WithMockUser(authorities = "INFLUENCER")
        @DisplayName("should record storage quota exceeded metrics")
        void shouldRecordStorageQuotaExceededMetrics() throws Exception {
            // Given
            FileUploadResponse errorResponse = new FileUploadResponse("Storage quota exceeded");
            when(signedUrlService.generateSignedUrl(eq(TEST_USER_ID), any()))
                    .thenReturn(errorResponse);

            // When
            mockMvc.perform(post("/upload/signed-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson))
                    .andExpect(status().isInsufficientStorage());

            // Then
            verify(metricsService).recordStorageQuotaExceeded(TEST_USER_ID);
        }
    }

    /**
     * Test configuration that manually creates the FileUploadController.
     * This bypasses the @ConditionalOnBean(SignedUrlService.class) annotation.
     */
    @TestConfiguration
    static class TestConfig {

        @Bean
        public FileUploadController fileUploadController(
                SignedUrlService signedUrlService,
                StorageRateLimitService rateLimiterService,
                UploadMetricsService metricsService,
                FileTrackingService trackingService,
                PermissionUtils permissionUtils) {
            return new FileUploadController(
                    signedUrlService,
                    rateLimiterService,
                    metricsService,
                    trackingService,
                    permissionUtils
            );
        }
    }
}
