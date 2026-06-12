package com.sm.instagram.platform.unit.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.storage.entity.FileUpload;
import com.sm.instagram.platform.storage.model.FileUploadRequest;
import com.sm.instagram.platform.storage.service.FileTrackingService;
import com.sm.instagram.platform.storage.service.SignedUrlService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import com.sm.instagram.platform.storage.service.UploadMetricsService;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SignedUrlService.
 *
 * Note: Tests that require mocking storage.signUrl() with varargs are challenging
 * in Mockito and are better tested as integration tests with actual GCS mock.
 * This test class focuses on validation, rate limiting, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SignedUrlService Unit Tests")
class SignedUrlServiceUnitTest {

    @Mock
    private Storage storage;

    @Mock
    private StorageRateLimitService rateLimiter;

    @Mock
    private FileTrackingService trackingService;

    @Mock
    private UploadMetricsService metricsService;

    private SignedUrlService service;

    private static final String USER_ID = "firebase-uid-123";
    private static final String BUCKET_NAME = "test-bucket";
    private static final String FILENAME = "test-image.jpg";
    private static final String CONTENT_TYPE = "image/jpeg";
    private static final Long FILE_SIZE = 1024L;

    @BeforeEach
    void setUp() throws Exception {
        service = new SignedUrlService(
            storage,
            BUCKET_NAME,
            rateLimiter,
            trackingService,
            metricsService,
            null  // GoogleCredentialsProvider - null for unit tests
        );
        ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
        ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");

        // Setup default mocks for rate limiting
        setupDefaultMocks();
    }

    private void setupDefaultMocks() throws Exception {
        // Rate limiter allows by default
        var allowedResult = StorageRateLimitService.RateLimitResult.allowed(10, 50);
        when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
            .thenReturn(allowedResult);
        when(rateLimiter.hasStorageSpace(anyString(), anyLong())).thenReturn(true);
        when(rateLimiter.getUserStatus(anyString())).thenReturn(
            new StorageRateLimitService.RateLimitStatus(1, 10, 1, 50, 1024L, 100 * 1024 * 1024L)
        );

        // Tracking service returns upload
        FileUpload fileUpload = new FileUpload(USER_ID, "path", FILENAME, CONTENT_TYPE, FILE_SIZE);
        fileUpload.setId("upload-123");
        when(trackingService.recordUploadRequest(anyString(), anyString(), anyString(), anyString(), anyLong()))
            .thenReturn(fileUpload);

        // Metrics
        when(metricsService.startSignedUrlTimer()).thenReturn(Timer.start());
    }

    private FileUploadRequest createValidRequest() {
        FileUploadRequest request = new FileUploadRequest();
        request.setFilename(FILENAME);
        request.setContentType(CONTENT_TYPE);
        request.setFileSize(FILE_SIZE);
        return request;
    }

    @Nested
    @DisplayName("generateSignedUrl - Validation Tests")
    class ValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw ValidationException when userId is null or empty")
        void shouldThrowWhenUserIdNullOrEmpty(String userId) {
            // Given
            FileUploadRequest request = createValidRequest();

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(userId, request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @Test
        @DisplayName("should throw NullPointerException when request is null (logging before validation)")
        void shouldThrowWhenRequestIsNull() {
            // Note: The service logs request.getFilename() before null check
            // This documents current behavior - service could be improved
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, null))
                .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw ValidationException when filename is null or empty")
        void shouldThrowWhenFilenameNullOrEmpty(String filename) {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setFilename(filename);

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw ValidationException when contentType is null or empty")
        void shouldThrowWhenContentTypeNullOrEmpty(String contentType) {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setContentType(contentType);

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1, -100})
        @DisplayName("should throw ValidationException when fileSize is zero or negative")
        void shouldThrowWhenFileSizeInvalid(long fileSize) {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setFileSize(fileSize);

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.invalid_argument");
        }

        @ParameterizedTest
        @ValueSource(longs = {5242881L, 10000000L, 15000000L}) // > 5MB
        @DisplayName("should throw ValidationException when fileSize exceeds 5MB limit")
        void shouldThrowWhenFileSizeExceedsLimit(long fileSize) {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setFileSize(fileSize);

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.file_too_large");
        }

        @Test
        @DisplayName("should accept fileSize at exactly 5MB limit")
        void shouldAcceptFileSizeAtExactLimit() throws Exception {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setFileSize(5 * 1024 * 1024L); // Exactly 5MB

            java.net.URL mockUrl = new java.net.URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When
            var result = service.generateSignedUrl(USER_ID, request);

            // Then
            assertThat(result).isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"application/pdf", "text/plain", "video/mp4", "application/octet-stream", "text/html"})
        @DisplayName("should throw ValidationException when contentType is not allowed")
        void shouldThrowWhenContentTypeNotAllowed(String contentType) {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setContentType(contentType);

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.invalid_content_type");
        }

        @ParameterizedTest
        @ValueSource(strings = {"image/jpeg", "image/png", "image/webp", "image/gif", "IMAGE/JPEG", "IMAGE/PNG"})
        @DisplayName("should accept allowed content types (case-insensitive)")
        void shouldAcceptAllowedContentTypes(String contentType) throws Exception {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setContentType(contentType);

            java.net.URL mockUrl = new java.net.URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When
            var result = service.generateSignedUrl(USER_ID, request);

            // Then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("generateSignedUrl - Storage Not Available")
    class StorageNotAvailableTests {

        @Test
        @DisplayName("should throw StorageException when storage is null")
        void shouldThrowWhenStorageIsNull() {
            // Given
            service = new SignedUrlService(null, BUCKET_NAME, rateLimiter, trackingService, metricsService, null);
            ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
            ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");
            FileUploadRequest request = createValidRequest();

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(StorageTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.storage.service_unavailable");
        }
    }

    @Nested
    @DisplayName("generateSignedUrl - Rate Limiting Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("should throw RateLimitException when rate limit exceeded")
        void shouldThrowWhenRateLimitExceeded() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Hourly limit reached");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);
            FileUploadRequest request = createValidRequest();

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.storage.rate_limit_exceeded");
        }

        @Test
        @DisplayName("should throw StorageException when storage quota exceeded")
        void shouldThrowWhenStorageQuotaExceeded() {
            // Given
            when(rateLimiter.hasStorageSpace(anyString(), anyLong())).thenReturn(false);
            FileUploadRequest request = createValidRequest();

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(StorageTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.storage.quota_exceeded");
        }

        @Test
        @DisplayName("should record rate limit hit metrics when rate limited")
        void shouldRecordRateLimitHitMetrics() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Hourly limit reached");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);
            FileUploadRequest request = createValidRequest();

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class);

            verify(metricsService).recordRateLimitHit("upload");
            verify(metricsService).recordUploadFailure("RATE_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("should verify rate limit check is called with correct upload type for CONTENT")
        void shouldCallRateLimiterWithContentType() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Blocked");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);

            FileUploadRequest request = createValidRequest();
            request.setUploadType(null);  // defaults to CONTENT

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class);

            verify(rateLimiter).checkUploadAllowed(eq(USER_ID), eq(FILE_SIZE), eq(StorageRateLimitService.UploadType.CONTENT));
        }

        @Test
        @DisplayName("should verify rate limit check is called with PROFILE_PHOTO type")
        void shouldCallRateLimiterWithProfilePhotoType() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Blocked");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);

            FileUploadRequest request = createValidRequest();
            request.setUploadType("PROFILE_PHOTO");

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class);

            verify(rateLimiter).checkUploadAllowed(eq(USER_ID), eq(FILE_SIZE), eq(StorageRateLimitService.UploadType.PROFILE_PHOTO));
        }

        @Test
        @DisplayName("should verify rate limit check is called with CAMPAIGN_MEDIA type")
        void shouldCallRateLimiterWithCampaignMediaType() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Blocked");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);

            FileUploadRequest request = createValidRequest();
            request.setUploadType("CAMPAIGN_MEDIA");

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class);

            verify(rateLimiter).checkUploadAllowed(eq(USER_ID), eq(FILE_SIZE), eq(StorageRateLimitService.UploadType.CAMPAIGN_MEDIA));
        }
    }

    @Nested
    @DisplayName("validateUploadSuccess")
    class ValidateUploadSuccessTests {

        @Test
        @DisplayName("should return true when blob exists")
        void shouldReturnTrueWhenBlobExists() {
            // Given
            Blob blob = mock(Blob.class);
            when(blob.exists()).thenReturn(true);
            when(storage.get(BUCKET_NAME, "test/path")).thenReturn(blob);

            // When
            boolean result = service.validateUploadSuccess("test/path");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when blob does not exist")
        void shouldReturnFalseWhenBlobDoesNotExist() {
            // Given
            when(storage.get(BUCKET_NAME, "test/path")).thenReturn(null);

            // When
            boolean result = service.validateUploadSuccess("test/path");

            // Then
            assertThat(result).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw ValidationException when filePath is null or empty")
        void shouldThrowWhenFilePathNullOrEmpty(String filePath) {
            assertThatThrownBy(() -> service.validateUploadSuccess(filePath))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @Test
        @DisplayName("should throw StorageException when storage is null")
        void shouldThrowWhenStorageIsNullForValidation() {
            // Given
            service = new SignedUrlService(null, BUCKET_NAME, rateLimiter, trackingService, metricsService, null);
            ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
            ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");

            // When/Then
            assertThatThrownBy(() -> service.validateUploadSuccess("test/path"))
                .isInstanceOf(StorageTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.storage.service_unavailable");
        }
    }

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUploadTests {

        @BeforeEach
        void setUpConfirmUpload() {
            Blob blob = mock(Blob.class);
            when(blob.exists()).thenReturn(true);
            when(blob.getSize()).thenReturn(2048L);
            when(storage.get(eq(BUCKET_NAME), anyString())).thenReturn(blob);
        }

        @Test
        @DisplayName("should record upload with rate limiter")
        void shouldRecordUploadWithRateLimiter() {
            // When
            service.confirmUpload(USER_ID, "test/path");

            // Then
            verify(rateLimiter).recordUpload(USER_ID, 2048L);
        }

        @Test
        @DisplayName("should confirm via tracking service")
        void shouldConfirmViaTrackingService() {
            // When
            service.confirmUpload(USER_ID, "test/path");

            // Then
            verify(trackingService).confirmUploadViaApi("test/path", 2048L);
        }

        @Test
        @DisplayName("should record success metrics")
        void shouldRecordSuccessMetrics() {
            // When
            service.confirmUpload(USER_ID, "test/path");

            // Then
            verify(metricsService).recordUploadSuccess(USER_ID, 2048L);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw ValidationException when userId is null or empty")
        void shouldThrowWhenUserIdNullOrEmptyForConfirm(String userId) {
            assertThatThrownBy(() -> service.confirmUpload(userId, "test/path"))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw ValidationException when filePath is null or empty")
        void shouldThrowWhenFilePathNullOrEmptyForConfirm(String filePath) {
            assertThatThrownBy(() -> service.confirmUpload(USER_ID, filePath))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @Test
        @DisplayName("should throw StorageException when storage is null")
        void shouldThrowWhenStorageIsNullForConfirm() {
            // Given
            service = new SignedUrlService(null, BUCKET_NAME, rateLimiter, trackingService, metricsService, null);
            ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
            ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");

            // When/Then
            assertThatThrownBy(() -> service.confirmUpload(USER_ID, "test/path"))
                .isInstanceOf(StorageTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.storage.service_unavailable");
        }

        @Test
        @DisplayName("should not record when blob does not exist")
        void shouldNotRecordWhenBlobDoesNotExist() {
            // Given
            when(storage.get(eq(BUCKET_NAME), anyString())).thenReturn(null);

            // When
            service.confirmUpload(USER_ID, "test/path");

            // Then
            verify(rateLimiter, never()).recordUpload(anyString(), anyLong());
            verify(trackingService, never()).confirmUploadViaApi(anyString(), anyLong());
        }

        @Test
        @DisplayName("should not record when blob exists but size is null")
        void shouldNotRecordWhenBlobSizeIsNull() {
            // Given
            Blob blob = mock(Blob.class);
            when(blob.exists()).thenReturn(true);
            when(blob.getSize()).thenReturn(null);
            when(storage.get(eq(BUCKET_NAME), anyString())).thenReturn(blob);

            // When
            service.confirmUpload(USER_ID, "test/path");

            // Then
            verify(rateLimiter, never()).recordUpload(anyString(), anyLong());
        }

        @Test
        @DisplayName("should throw StorageException when storage throws exception")
        void shouldThrowStorageExceptionOnStorageError() {
            // Given
            when(storage.get(eq(BUCKET_NAME), anyString()))
                .thenThrow(new RuntimeException("Storage error"));

            // When/Then
            assertThatThrownBy(() -> service.confirmUpload(USER_ID, "test/path"))
                .isInstanceOf(StorageTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
        }

        @Test
        @DisplayName("should record failure metrics on storage exception")
        void shouldRecordFailureMetricsOnStorageException() {
            // Given
            when(storage.get(eq(BUCKET_NAME), anyString()))
                .thenThrow(new RuntimeException("Storage error"));

            // When/Then
            assertThatThrownBy(() -> service.confirmUpload(USER_ID, "test/path"))
                .isInstanceOf(StorageTranslatableException.class);

            verify(metricsService).recordUploadFailure("CONFIRMATION_ERROR");
        }

        @Test
        @DisplayName("should work without tracking service (null)")
        void shouldWorkWithoutTrackingService() {
            // Given
            service = new SignedUrlService(storage, BUCKET_NAME, rateLimiter, null, metricsService, null);
            ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
            ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");
            Blob blob = mock(Blob.class);
            when(blob.exists()).thenReturn(true);
            when(blob.getSize()).thenReturn(2048L);
            when(storage.get(eq(BUCKET_NAME), anyString())).thenReturn(blob);

            // When
            service.confirmUpload(USER_ID, "test/path");

            // Then
            verify(rateLimiter).recordUpload(USER_ID, 2048L);
        }

        @Test
        @DisplayName("should work without metrics service (null)")
        void shouldWorkWithoutMetricsService() {
            // Given
            service = new SignedUrlService(storage, BUCKET_NAME, rateLimiter, trackingService, null, null);
            ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
            ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");
            Blob blob = mock(Blob.class);
            when(blob.exists()).thenReturn(true);
            when(blob.getSize()).thenReturn(2048L);
            when(storage.get(eq(BUCKET_NAME), anyString())).thenReturn(blob);

            // When - should not throw even without metrics
            service.confirmUpload(USER_ID, "test/path");

            // Then
            verify(rateLimiter).recordUpload(USER_ID, 2048L);
        }
    }

    @Nested
    @DisplayName("validateUploadSuccess - Additional Tests")
    class ValidateUploadSuccessAdditionalTests {

        @Test
        @DisplayName("should return false when blob exists returns false")
        void shouldReturnFalseWhenBlobExistReturnsFalse() {
            // Given
            Blob blob = mock(Blob.class);
            when(blob.exists()).thenReturn(false);
            when(storage.get(BUCKET_NAME, "test/path")).thenReturn(blob);

            // When
            boolean result = service.validateUploadSuccess("test/path");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw StorageException when storage throws exception")
        void shouldThrowStorageExceptionOnStorageError() {
            // Given
            when(storage.get(BUCKET_NAME, "test/path"))
                .thenThrow(new RuntimeException("Storage error"));

            // When/Then
            assertThatThrownBy(() -> service.validateUploadSuccess("test/path"))
                .isInstanceOf(StorageTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
        }
    }

    @Nested
    @DisplayName("Upload Type Resolution Tests")
    class UploadTypeResolutionTests {

        @Test
        @DisplayName("should default to CONTENT for empty upload type")
        void shouldDefaultToContentForEmptyUploadType() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Blocked");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);

            FileUploadRequest request = createValidRequest();
            request.setUploadType("");

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class);

            verify(rateLimiter).checkUploadAllowed(eq(USER_ID), eq(FILE_SIZE), eq(StorageRateLimitService.UploadType.CONTENT));
        }

        @Test
        @DisplayName("should default to CONTENT for whitespace upload type")
        void shouldDefaultToContentForWhitespaceUploadType() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Blocked");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);

            FileUploadRequest request = createValidRequest();
            request.setUploadType("   ");

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class);

            verify(rateLimiter).checkUploadAllowed(eq(USER_ID), eq(FILE_SIZE), eq(StorageRateLimitService.UploadType.CONTENT));
        }

        @Test
        @DisplayName("should default to CONTENT for invalid upload type")
        void shouldDefaultToContentForInvalidUploadType() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Blocked");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);

            FileUploadRequest request = createValidRequest();
            request.setUploadType("INVALID_TYPE");

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class);

            verify(rateLimiter).checkUploadAllowed(eq(USER_ID), eq(FILE_SIZE), eq(StorageRateLimitService.UploadType.CONTENT));
        }

        @Test
        @DisplayName("should handle lowercase upload type")
        void shouldHandleLowercaseUploadType() {
            // Given
            var blockedResult = StorageRateLimitService.RateLimitResult.blocked("Blocked");
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(blockedResult);

            FileUploadRequest request = createValidRequest();
            request.setUploadType("profile_photo");

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(RateLimitTranslatableException.class);

            verify(rateLimiter).checkUploadAllowed(eq(USER_ID), eq(FILE_SIZE), eq(StorageRateLimitService.UploadType.PROFILE_PHOTO));
        }
    }

    @Nested
    @DisplayName("generateSignedUrl - Success Path Tests")
    class GenerateSignedUrlSuccessTests {

        @Test
        @DisplayName("should generate signed URL successfully with all parameters")
        void shouldGenerateSignedUrlSuccessfully() throws Exception {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setDescription("Test description");
            request.setAltText("Test alt text");

            URL mockUrl = new URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When
            var result = service.generateSignedUrl(USER_ID, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUploadUrl()).isEqualTo(mockUrl.toString());
            assertThat(result.getFilePath()).contains(USER_ID);
            assertThat(result.getFilePath()).contains(FILENAME);
            assertThat(result.getPublicUrl()).contains(BUCKET_NAME);
            assertThat(result.getExpiresAt()).isNotNull();
            assertThat(result.getRateLimitInfo()).isNotNull();
            assertThat(result.getUploadInstructions()).isNotNull();
        }

        @Test
        @DisplayName("should set upload ID from tracking service")
        void shouldSetUploadIdFromTrackingService() throws Exception {
            // Given
            FileUploadRequest request = createValidRequest();

            URL mockUrl = new URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When
            var result = service.generateSignedUrl(USER_ID, request);

            // Then
            assertThat(result.getUploadId()).isEqualTo("upload-123");
        }

        @Test
        @DisplayName("should include rate limit info in response")
        void shouldIncludeRateLimitInfoInResponse() throws Exception {
            // Given
            FileUploadRequest request = createValidRequest();

            URL mockUrl = new URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When
            var result = service.generateSignedUrl(USER_ID, request);

            // Then
            assertThat(result.getRateLimitInfo()).isNotNull();
            assertThat(result.getRateLimitInfo().getRemainingHourly()).isEqualTo(10);
            assertThat(result.getRateLimitInfo().getRemainingDaily()).isEqualTo(50);
        }

        @Test
        @DisplayName("should call storage.signUrl with correct parameters")
        void shouldCallStorageSignUrlWithCorrectParams() throws Exception {
            // Given
            FileUploadRequest request = createValidRequest();

            URL mockUrl = new URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When
            service.generateSignedUrl(USER_ID, request);

            // Then
            verify(storage).signUrl(
                argThat(blobInfo ->
                    blobInfo.getBucket().equals(BUCKET_NAME) &&
                    blobInfo.getContentType().equals(CONTENT_TYPE) &&
                    blobInfo.getMetadata().get("uploadedBy").equals(USER_ID) &&
                    blobInfo.getMetadata().get("originalFilename").equals(FILENAME)
                ),
                eq(5L),
                eq(TimeUnit.MINUTES),
                any(Storage.SignUrlOption[].class)
            );
        }

        @Test
        @DisplayName("should record upload request metrics")
        void shouldRecordUploadRequestMetrics() throws Exception {
            // Given
            FileUploadRequest request = createValidRequest();

            URL mockUrl = new URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When
            service.generateSignedUrl(USER_ID, request);

            // Then
            verify(metricsService).recordUploadRequest();
            verify(metricsService).recordSignedUrlTime(any());
            verify(metricsService).recordDetailedUploadMetrics(eq(USER_ID), eq(CONTENT_TYPE), eq(FILE_SIZE), eq(0L));
        }

        @Test
        @DisplayName("should throw StorageException when signUrl throws exception")
        void shouldThrowStorageExceptionWhenSignUrlFails() {
            // Given
            FileUploadRequest request = createValidRequest();

            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenThrow(new RuntimeException("Signing failed"));

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(StorageTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
        }

        @Test
        @DisplayName("should record failure metrics when signUrl fails")
        void shouldRecordFailureMetricsWhenSignUrlFails() {
            // Given
            FileUploadRequest request = createValidRequest();

            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenThrow(new RuntimeException("Signing failed"));

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(StorageTranslatableException.class);

            verify(metricsService).recordUploadFailure("SIGNED_URL_GENERATION_ERROR");
        }

        @Test
        @DisplayName("should work without tracking service")
        void shouldWorkWithoutTrackingService() throws Exception {
            // Given
            service = new SignedUrlService(storage, BUCKET_NAME, rateLimiter, null, metricsService, null);
            ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
            ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");
            setupDefaultMocks();

            FileUploadRequest request = createValidRequest();

            URL mockUrl = new URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When
            var result = service.generateSignedUrl(USER_ID, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUploadId()).isNotNull(); // UUID generated
        }

        @Test
        @DisplayName("should work without metrics service")
        void shouldWorkWithoutMetricsService() throws Exception {
            // Given
            service = new SignedUrlService(storage, BUCKET_NAME, rateLimiter, trackingService, null, null);
            ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", 5);
            ReflectionTestUtils.setField(service, "storagePathPattern", "content/{userId}/{timestamp}_{filename}");

            var allowedResult = StorageRateLimitService.RateLimitResult.allowed(10, 50);
            when(rateLimiter.checkUploadAllowed(anyString(), anyLong(), any(StorageRateLimitService.UploadType.class)))
                .thenReturn(allowedResult);
            when(rateLimiter.hasStorageSpace(anyString(), anyLong())).thenReturn(true);
            when(rateLimiter.getUserStatus(anyString())).thenReturn(
                new StorageRateLimitService.RateLimitStatus(1, 10, 1, 50, 1024L, 100 * 1024 * 1024L)
            );

            FileUpload fileUpload = new FileUpload(USER_ID, "path", FILENAME, CONTENT_TYPE, FILE_SIZE);
            fileUpload.setId("upload-123");
            when(trackingService.recordUploadRequest(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(fileUpload);

            FileUploadRequest request = createValidRequest();

            URL mockUrl = new URL("https://storage.googleapis.com/test-bucket/test-file?signature=abc123");
            when(storage.signUrl(any(BlobInfo.class), anyLong(), any(TimeUnit.class), any(Storage.SignUrlOption[].class)))
                .thenReturn(mockUrl);

            // When - should not throw
            var result = service.generateSignedUrl(USER_ID, request);

            // Then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("Validation - Edge Cases")
    class ValidationEdgeCaseTests {

        @Test
        @DisplayName("should throw for whitespace-only userId")
        void shouldThrowForWhitespaceOnlyUserId() {
            // Given
            FileUploadRequest request = createValidRequest();

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl("   ", request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @Test
        @DisplayName("should throw for whitespace-only filename")
        void shouldThrowForWhitespaceOnlyFilename() {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setFilename("   ");

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @Test
        @DisplayName("should throw for whitespace-only contentType")
        void shouldThrowForWhitespaceOnlyContentType() {
            // Given
            FileUploadRequest request = createValidRequest();
            request.setContentType("   ");

            // When/Then
            assertThatThrownBy(() -> service.generateSignedUrl(USER_ID, request))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @Test
        @DisplayName("should throw for whitespace-only filePath in validateUploadSuccess")
        void shouldThrowForWhitespaceOnlyFilePath() {
            assertThatThrownBy(() -> service.validateUploadSuccess("   "))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @Test
        @DisplayName("should throw for whitespace-only userId in confirmUpload")
        void shouldThrowForWhitespaceOnlyUserIdInConfirm() {
            assertThatThrownBy(() -> service.confirmUpload("   ", "test/path"))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }

        @Test
        @DisplayName("should throw for whitespace-only filePath in confirmUpload")
        void shouldThrowForWhitespaceOnlyFilePathInConfirm() {
            assertThatThrownBy(() -> service.confirmUpload(USER_ID, "   "))
                .isInstanceOf(ValidationTranslatableException.class)
                .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
        }
    }
}
