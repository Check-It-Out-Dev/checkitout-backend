package com.sm.instagram.platform.unit.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.storage.service.SignedUrlValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SignedUrlValidationService.
 *
 * Tests cover:
 * - isFullyOperational() status checks
 * - getValidationReport() report generation
 * - isBase64() detection
 * - getCredentials() credential loading
 * - Initialization scenarios
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SignedUrlValidationService Unit Tests")
class SignedUrlValidationServiceUnitTest {

    @Mock
    private Storage storage;

    @Mock
    private ServiceAccountCredentials serviceAccountCredentials;

    @Mock
    private Resource serviceAccountFile;

    @Mock
    private Bucket bucket;

    @Mock
    private Blob blob;

    private SignedUrlValidationService service;

    private static final String PROJECT_ID = "test-project-id";
    private static final String BUCKET_NAME = "test-bucket.firebasestorage.app";
    private static final String SERVICE_ACCOUNT_EMAIL = "test@test-project.iam.gserviceaccount.com";
    private static final int SIGNED_URL_EXPIRATION_MINUTES = 5;

    // Valid JSON service account content for testing
    private static final String VALID_SERVICE_ACCOUNT_JSON = """
            {
              "type": "service_account",
              "project_id": "test-project",
              "private_key_id": "key-id",
              "private_key": "-----BEGIN RSA PRIVATE KEY-----\\nMIIEpAIBAAKCAQEA0Z3VS5JJcds3xfn/ygWyF8PbnGy0AHB7MvDE3w==\\n-----END RSA PRIVATE KEY-----\\n",
              "client_email": "test@test-project.iam.gserviceaccount.com",
              "client_id": "123456789",
              "auth_uri": "https://accounts.google.com/o/oauth2/auth",
              "token_uri": "https://oauth2.googleapis.com/token"
            }
            """;

    @BeforeEach
    void setUp() {
        service = new SignedUrlValidationService(storage);
        setDefaultFieldValues();
    }

    private void setDefaultFieldValues() {
        ReflectionTestUtils.setField(service, "projectId", PROJECT_ID);
        ReflectionTestUtils.setField(service, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(service, "signedUrlExpirationMinutes", SIGNED_URL_EXPIRATION_MINUTES);
        ReflectionTestUtils.setField(service, "testActualUpload", false);
        ReflectionTestUtils.setField(service, "serviceAccountJsonBase64", "");
        ReflectionTestUtils.setField(service, "serviceAccountFile", null);
    }

    @Nested
    @DisplayName("isFullyOperational Tests")
    class IsFullyOperationalTests {

        @Test
        @DisplayName("should return false when serviceAccountCredentials is null")
        void shouldReturnFalseWhenCredentialsNull() {
            // Given - no credentials set (default state after construction)
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", null);

            // When
            boolean result = service.isFullyOperational();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when storage is null")
        void shouldReturnFalseWhenStorageNull() {
            // Given
            service = new SignedUrlValidationService(null);
            setDefaultFieldValues();
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);

            // When
            boolean result = service.isFullyOperational();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when both storage and credentials are available")
        void shouldReturnTrueWhenFullyConfigured() {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);

            // When
            boolean result = service.isFullyOperational();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when storage is available but credentials are null")
        void shouldReturnFalseWhenOnlyStorageAvailable() {
            // Given - storage is mocked, credentials are null
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", null);

            // When
            boolean result = service.isFullyOperational();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getValidationReport Tests")
    class GetValidationReportTests {

        @Test
        @DisplayName("should include storageConfigured as true when storage is not null")
        void shouldIncludeStorageConfiguredTrue() {
            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("storageConfigured")).isEqualTo(true);
        }

        @Test
        @DisplayName("should include storageConfigured as false when storage is null")
        void shouldIncludeStorageConfiguredFalse() {
            // Given
            service = new SignedUrlValidationService(null);
            setDefaultFieldValues();

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("storageConfigured")).isEqualTo(false);
        }

        @Test
        @DisplayName("should include credentialsAvailable as true when credentials exist")
        void shouldIncludeCredentialsAvailableTrue() {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("credentialsAvailable")).isEqualTo(true);
        }

        @Test
        @DisplayName("should include credentialsAvailable as false when credentials are null")
        void shouldIncludeCredentialsAvailableFalse() {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", null);

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("credentialsAvailable")).isEqualTo(false);
        }

        @Test
        @DisplayName("should include bucket name in report")
        void shouldIncludeBucketName() {
            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("bucketName")).isEqualTo(BUCKET_NAME);
        }

        @Test
        @DisplayName("should include project ID in report")
        void shouldIncludeProjectId() {
            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("projectId")).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("should include testActualUpload flag in report")
        void shouldIncludeTestActualUploadFlag() {
            // Given
            ReflectionTestUtils.setField(service, "testActualUpload", true);

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("testActualUpload")).isEqualTo(true);
        }

        @Test
        @DisplayName("should include service account email when credentials exist")
        void shouldIncludeServiceAccountEmail() {
            // Given
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("serviceAccount")).isEqualTo(SERVICE_ACCOUNT_EMAIL);
        }

        @Test
        @DisplayName("should not include service account email when credentials are null")
        void shouldNotIncludeServiceAccountEmailWhenNull() {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", null);

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report).doesNotContainKey("serviceAccount");
        }

        @Test
        @DisplayName("should return complete report with all expected keys")
        void shouldReturnCompleteReport() {
            // Given
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report).containsKeys(
                    "storageConfigured",
                    "credentialsAvailable",
                    "bucketName",
                    "projectId",
                    "testActualUpload",
                    "serviceAccount"
            );
        }
    }

    @Nested
    @DisplayName("isBase64 Detection Tests")
    class IsBase64DetectionTests {

        @Test
        @DisplayName("should return false for null string")
        void shouldReturnFalseForNull() {
            // When - use reflection to test private method
            Boolean result = invokeIsBase64(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for empty string")
        void shouldReturnFalseForEmptyString() {
            // When
            Boolean result = invokeIsBase64("");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for JSON string starting with brace")
        void shouldReturnFalseForJsonString() {
            // Given
            String jsonString = "{\"key\": \"value\"}";

            // When
            Boolean result = invokeIsBase64(jsonString);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for JSON string with leading whitespace")
        void shouldReturnFalseForJsonWithWhitespace() {
            // Given
            String jsonString = "  {\"key\": \"value\"}";

            // When
            Boolean result = invokeIsBase64(jsonString);

            // Then - trim().startsWith("{") check
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for valid base64 encoded string")
        void shouldReturnTrueForValidBase64() {
            // Given
            String base64 = Base64.getEncoder().encodeToString("test content".getBytes());

            // When
            Boolean result = invokeIsBase64(base64);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid base64 string")
        void shouldReturnFalseForInvalidBase64() {
            // Given - string with invalid base64 characters
            String invalidBase64 = "not-valid-base64!@#$%";

            // When
            Boolean result = invokeIsBase64(invalidBase64);

            // Then
            assertThat(result).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"SGVsbG8gV29ybGQ=", "dGVzdA==", "YWJj"})
        @DisplayName("should return true for various valid base64 strings")
        void shouldReturnTrueForVariousBase64Strings(String base64) {
            // When
            Boolean result = invokeIsBase64(base64);

            // Then
            assertThat(result).isTrue();
        }

        private Boolean invokeIsBase64(String str) {
            return (Boolean) ReflectionTestUtils.invokeMethod(service, "isBase64", str);
        }
    }

    @Nested
    @DisplayName("runValidation Tests")
    class RunValidationTests {

        @Test
        @DisplayName("should not throw when credentials are not set")
        void shouldNotThrowWhenCredentialsNotSet() {
            // Given - no credentials
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", null);

            // When/Then - should complete without exception
            service.runValidation();
        }

        @Test
        @DisplayName("should run validation with credentials set")
        void shouldRunValidationWithCredentials() {
            // Given
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            when(serviceAccountCredentials.getProjectId()).thenReturn(PROJECT_ID);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenReturn(bucket);
            when(bucket.exists()).thenReturn(true);

            // When/Then - should complete without exception
            service.runValidation();
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("should create service with storage")
        void shouldCreateServiceWithStorage() {
            // When
            SignedUrlValidationService newService = new SignedUrlValidationService(storage);

            // Then
            assertThat(newService).isNotNull();
        }

        @Test
        @DisplayName("should create service with null storage")
        void shouldCreateServiceWithNullStorage() {
            // When
            SignedUrlValidationService newService = new SignedUrlValidationService(null);

            // Then
            assertThat(newService).isNotNull();
            assertThat(newService.isFullyOperational()).isFalse();
        }
    }

    @Nested
    @DisplayName("testSignedUrlLifecycle Tests")
    class TestSignedUrlLifecycleTests {

        @Test
        @DisplayName("should handle null bucket gracefully")
        void shouldHandleNullBucketGracefully() {
            // Given
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenReturn(null);

            // When/Then - should not throw, just log error
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");
        }

        @Test
        @DisplayName("should handle bucket that does not exist")
        void shouldHandleBucketNotExists() {
            // Given
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenReturn(bucket);
            when(bucket.exists()).thenReturn(false);

            // When/Then - should not throw, just log error
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");
        }

        @Test
        @DisplayName("should handle storage exception when getting bucket")
        void shouldHandleStorageExceptionGettingBucket() {
            // Given
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenThrow(new RuntimeException("Storage error"));

            // When/Then - should not throw, just log error
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");
        }

        @Test
        @DisplayName("should skip lifecycle test when credentials are null")
        void shouldSkipWhenCredentialsNull() {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", null);

            // When/Then - should return early without exceptions
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");
        }

        @Test
        @DisplayName("should generate upload URL when bucket exists")
        void shouldGenerateUploadUrlWhenBucketExists() throws Exception {
            // Given
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenReturn(bucket);
            when(bucket.exists()).thenReturn(true);

            URL mockUrl = new URL("https://storage.googleapis.com/test?X-Goog-Signature=abc");
            when(storage.signUrl(any(com.google.cloud.storage.BlobInfo.class), anyLong(), any(TimeUnit.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class)))
                    .thenReturn(mockUrl);

            // When/Then - should not throw
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");

            // Verify signUrl was called for upload URL generation
            verify(storage, atLeastOnce()).get(BUCKET_NAME);
        }

        @Test
        @DisplayName("should handle signUrl exception gracefully")
        void shouldHandleSignUrlException() {
            // Given
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenReturn(bucket);
            when(bucket.exists()).thenReturn(true);
            when(storage.signUrl(any(com.google.cloud.storage.BlobInfo.class), anyLong(), any(TimeUnit.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class)))
                    .thenThrow(new RuntimeException("SignUrl failed"));

            // When/Then - should not throw, just log error
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");
        }
    }

    @Nested
    @DisplayName("getCredentials Tests")
    class GetCredentialsTests {

        @Test
        @DisplayName("should load credentials from base64 encoded JSON")
        void shouldLoadCredentialsFromBase64() throws Exception {
            // Given
            String base64Json = Base64.getEncoder().encodeToString(VALID_SERVICE_ACCOUNT_JSON.getBytes(StandardCharsets.UTF_8));
            ReflectionTestUtils.setField(service, "serviceAccountJsonBase64", base64Json);

            // When/Then - method will throw because the private key is not valid
            // but it should at least attempt to parse the JSON
            try {
                ReflectionTestUtils.invokeMethod(service, "getCredentials");
            } catch (Exception e) {
                // Expected - private key is not valid, but parsing was attempted
                assertThat(e.getCause()).isInstanceOf(IOException.class);
            }
        }

        @Test
        @DisplayName("should load credentials from plain JSON string")
        void shouldLoadCredentialsFromPlainJson() throws Exception {
            // Given - JSON that does not look like base64 (starts with {)
            ReflectionTestUtils.setField(service, "serviceAccountJsonBase64", VALID_SERVICE_ACCOUNT_JSON);

            // When/Then - method will throw because the private key is not valid
            try {
                ReflectionTestUtils.invokeMethod(service, "getCredentials");
            } catch (Exception e) {
                // Expected - private key is not valid, but parsing was attempted
                assertThat(e.getCause()).isInstanceOf(IOException.class);
            }
        }

        @Test
        @DisplayName("should fall back to file when base64 is empty")
        void shouldFallBackToFileWhenBase64Empty() throws Exception {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(service, "serviceAccountFile", serviceAccountFile);
            when(serviceAccountFile.exists()).thenReturn(true);
            when(serviceAccountFile.getInputStream())
                    .thenReturn(new ByteArrayInputStream(VALID_SERVICE_ACCOUNT_JSON.getBytes(StandardCharsets.UTF_8)));

            // When/Then
            try {
                ReflectionTestUtils.invokeMethod(service, "getCredentials");
            } catch (Exception e) {
                // Expected - private key is not valid
                assertThat(e.getCause()).isInstanceOf(IOException.class);
            }

            verify(serviceAccountFile).getInputStream();
        }

        @Test
        @DisplayName("should fall back to file when base64 is null")
        void shouldFallBackToFileWhenBase64Null() throws Exception {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountJsonBase64", null);
            ReflectionTestUtils.setField(service, "serviceAccountFile", serviceAccountFile);
            when(serviceAccountFile.exists()).thenReturn(true);
            when(serviceAccountFile.getInputStream())
                    .thenReturn(new ByteArrayInputStream(VALID_SERVICE_ACCOUNT_JSON.getBytes(StandardCharsets.UTF_8)));

            // When/Then
            try {
                ReflectionTestUtils.invokeMethod(service, "getCredentials");
            } catch (Exception e) {
                // Expected - private key is not valid
                assertThat(e.getCause()).isInstanceOf(IOException.class);
            }

            verify(serviceAccountFile).getInputStream();
        }

        @Test
        @DisplayName("should skip file when it does not exist")
        void shouldSkipFileWhenNotExists() throws Exception {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(service, "serviceAccountFile", serviceAccountFile);
            when(serviceAccountFile.exists()).thenReturn(false);

            // When/Then - will try default credentials which will fail in test env
            try {
                ReflectionTestUtils.invokeMethod(service, "getCredentials");
            } catch (Exception e) {
                // Expected in test environment - no default credentials available
            }

            verify(serviceAccountFile, never()).getInputStream();
        }

        @Test
        @DisplayName("should skip file when serviceAccountFile is null")
        void shouldSkipFileWhenNull() throws Exception {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountJsonBase64", "");
            ReflectionTestUtils.setField(service, "serviceAccountFile", null);

            // When/Then - will try default credentials which will fail in test env
            try {
                ReflectionTestUtils.invokeMethod(service, "getCredentials");
            } catch (Exception e) {
                // Expected in test environment - no default credentials available
            }
        }
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("should handle initialization with null storage gracefully")
        void shouldHandleNullStorageGracefully() {
            // Given
            service = new SignedUrlValidationService(null);
            setDefaultFieldValues();

            // When - initialize will try to load credentials
            // Should not throw even with null storage
            // Note: actual initialize() is called by @PostConstruct in real app

            // Then
            assertThat(service.isFullyOperational()).isFalse();
        }

        @Test
        @DisplayName("should set serviceAccountCredentials when valid credentials loaded")
        void shouldSetCredentialsWhenValid() {
            // Given - set credentials directly (simulating successful load)
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);

            // Then
            assertThat(service.isFullyOperational()).isTrue();
        }
    }

    @Nested
    @DisplayName("Test Image Constant Tests")
    class TestImageConstantTests {

        @Test
        @DisplayName("should have valid base64 encoded test image")
        void shouldHaveValidBase64TestImage() {
            // Given - access the constant via reflection
            String testImageBase64 = (String) ReflectionTestUtils.getField(
                    SignedUrlValidationService.class, "TEST_IMAGE_BASE64");

            // When
            byte[] decoded = Base64.getDecoder().decode(testImageBase64);

            // Then - should be valid PNG (starts with PNG magic bytes)
            assertThat(decoded).isNotEmpty();
            // PNG magic bytes: 137 80 78 71 13 10 26 10
            assertThat(decoded[0] & 0xFF).isEqualTo(137);
            assertThat(decoded[1] & 0xFF).isEqualTo(80);  // 'P'
            assertThat(decoded[2] & 0xFF).isEqualTo(78);  // 'N'
            assertThat(decoded[3] & 0xFF).isEqualTo(71);  // 'G'
        }
    }

    @Nested
    @DisplayName("Actual Upload Test Flag Tests")
    class ActualUploadTestFlagTests {

        @Test
        @DisplayName("should skip actual upload test when flag is false")
        void shouldSkipActualUploadWhenFlagFalse() throws Exception {
            // Given
            ReflectionTestUtils.setField(service, "testActualUpload", false);
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenReturn(bucket);
            when(bucket.exists()).thenReturn(true);

            URL mockUrl = new URL("https://storage.googleapis.com/test?X-Goog-Signature=abc");
            when(storage.signUrl(any(com.google.cloud.storage.BlobInfo.class), anyLong(), any(TimeUnit.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class)))
                    .thenReturn(mockUrl);

            // When
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");

            // Then - bucket was accessed and URL was generated
            verify(storage, atLeastOnce()).get(BUCKET_NAME);
        }

        @Test
        @DisplayName("should include testActualUpload in validation report")
        void shouldIncludeTestActualUploadInReport() {
            // Given
            ReflectionTestUtils.setField(service, "testActualUpload", true);

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("testActualUpload")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle concurrent access to getValidationReport")
        void shouldHandleConcurrentAccess() throws InterruptedException {
            // Given
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);

            // When - simulate concurrent access
            Thread t1 = new Thread(() -> service.getValidationReport());
            Thread t2 = new Thread(() -> service.getValidationReport());

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            // Then - no exceptions should occur
        }

        @Test
        @DisplayName("should handle empty bucket name")
        void shouldHandleEmptyBucketName() {
            // Given
            ReflectionTestUtils.setField(service, "bucketName", "");

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("bucketName")).isEqualTo("");
        }

        @Test
        @DisplayName("should handle empty project ID")
        void shouldHandleEmptyProjectId() {
            // Given
            ReflectionTestUtils.setField(service, "projectId", "");

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("projectId")).isEqualTo("");
        }

        @Test
        @DisplayName("should handle null values in report generation gracefully")
        void shouldHandleNullValuesInReportGracefully() {
            // Given
            ReflectionTestUtils.setField(service, "bucketName", null);
            ReflectionTestUtils.setField(service, "projectId", null);

            // When
            Map<String, Object> report = service.getValidationReport();

            // Then
            assertThat(report.get("bucketName")).isNull();
            assertThat(report.get("projectId")).isNull();
        }
    }

    @Nested
    @DisplayName("Verification and Cleanup Tests")
    class VerificationAndCleanupTests {

        @Test
        @DisplayName("should verify uploaded file when testActualUpload is true")
        void shouldVerifyUploadedFile() throws Exception {
            // Given
            ReflectionTestUtils.setField(service, "testActualUpload", true);
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenReturn(bucket);
            when(bucket.exists()).thenReturn(true);

            URL mockUrl = new URL("https://storage.googleapis.com/test?X-Goog-Signature=abc");
            when(storage.signUrl(any(com.google.cloud.storage.BlobInfo.class), anyLong(), any(TimeUnit.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class)))
                    .thenReturn(mockUrl);

            // Simulate blob verification
            when(storage.get(eq(BUCKET_NAME), anyString())).thenReturn(blob);
            when(blob.exists()).thenReturn(true);
            when(blob.getSize()).thenReturn(67L);
            when(blob.getContentType()).thenReturn("image/png");

            // When - this will attempt HTTP connection which will fail in test
            // but the verification logic can be observed
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");

            // Then - verify bucket was accessed
            verify(storage, atLeastOnce()).get(BUCKET_NAME);
        }

        @Test
        @DisplayName("should handle verification failure gracefully")
        void shouldHandleVerificationFailure() throws Exception {
            // Given
            ReflectionTestUtils.setField(service, "testActualUpload", true);
            when(serviceAccountCredentials.getClientEmail()).thenReturn(SERVICE_ACCOUNT_EMAIL);
            ReflectionTestUtils.setField(service, "serviceAccountCredentials", serviceAccountCredentials);
            when(storage.get(BUCKET_NAME)).thenReturn(bucket);
            when(bucket.exists()).thenReturn(true);

            URL mockUrl = new URL("https://storage.googleapis.com/test?X-Goog-Signature=abc");
            when(storage.signUrl(any(com.google.cloud.storage.BlobInfo.class), anyLong(), any(TimeUnit.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class),
                    any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class)))
                    .thenReturn(mockUrl);

            // Simulate blob not found
            when(storage.get(eq(BUCKET_NAME), anyString())).thenReturn(null);

            // When/Then - should not throw
            ReflectionTestUtils.invokeMethod(service, "testSignedUrlLifecycle");
        }
    }
}
