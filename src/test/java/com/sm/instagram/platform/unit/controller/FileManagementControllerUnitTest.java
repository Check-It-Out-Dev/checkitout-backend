package com.sm.instagram.platform.unit.controller;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.storage.controller.FileManagementController;
import com.sm.instagram.platform.storage.controller.UploadAdminController;
import com.sm.instagram.platform.storage.controller.WebhookController;
import com.sm.instagram.platform.storage.entity.FileUpload;
import com.sm.instagram.platform.storage.model.DeleteFilesRequest;
import com.sm.instagram.platform.storage.model.FileOperationResponse;
import com.sm.instagram.platform.storage.repository.FileUploadRepository;
import com.sm.instagram.platform.storage.service.FileTrackingService;
import com.sm.instagram.platform.storage.service.FirebaseStorageService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import com.sm.instagram.platform.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for storage controllers:
 * - FileManagementController
 * - UploadAdminController
 * - WebhookController
 *
 * Pure Mockito tests without Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Storage Controllers Unit Tests")
class FileManagementControllerUnitTest {

    // ========================================================================
    // FileManagementController Tests
    // ========================================================================

    @Nested
    @DisplayName("FileManagementController")
    class FileManagementControllerTests {

        @Mock
        private Storage storage;

        @Mock
        private UserRepository userRepository;

        @Mock
        private FirebaseStorageService storageService;

        @Mock
        private StorageRateLimitService storageRateLimitService;

        @Mock
        private HttpServletRequest servletRequest;

        @Mock
        private SecurityContext securityContext;

        @Mock
        private Authentication authentication;

        @Mock
        private Claims claims;

        @InjectMocks
        private FileManagementController controller;

        private static final String BUCKET_NAME = "test-bucket";
        private static final String USER_ID = "firebase-user-123";
        private static final String USER_EMAIL = "test@example.com";

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(controller, "bucketName", BUCKET_NAME);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(USER_ID);
            when(authentication.getDetails()).thenReturn(claims);
            when(claims.get("email", String.class)).thenReturn(USER_EMAIL);
        }

        @Nested
        @DisplayName("DELETE /files/delete - Single File Deletion")
        class DeleteFileTests {

            @Test
            @DisplayName("should delete file successfully when user owns it")
            void shouldDeleteFileSuccessfully() {
                // Given
                String fileUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/users%2F" + USER_ID + "%2Ffile.jpg?alt=media";
                String blobPath = "users/" + USER_ID + "/file.jpg";
                BlobId blobId = BlobId.of(BUCKET_NAME, blobPath);
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(1024L);
                when(storage.get(any(BlobId.class))).thenReturn(blob);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFile(fileUrl, servletRequest);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().isSuccess()).isTrue();
                assertThat(response.getBody().getMessage()).isEqualTo("File deleted successfully");
                verify(storageRateLimitService).decreaseUserStorage(eq(USER_ID), eq(1024L));
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when not authenticated")
            void shouldThrowWhenNotAuthenticated() {
                // Given
                when(authentication.isAuthenticated()).thenReturn(false);
                String fileUrl = "users/" + USER_ID + "/file.jpg";

                // When/Then
                assertThatThrownBy(() -> controller.deleteFile(fileUrl, servletRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when auth is null")
            void shouldThrowWhenAuthIsNull() {
                // Given
                when(securityContext.getAuthentication()).thenReturn(null);
                String fileUrl = "users/" + USER_ID + "/file.jpg";

                // When/Then
                assertThatThrownBy(() -> controller.deleteFile(fileUrl, servletRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
            }

            @Test
            @DisplayName("should throw BusinessRuleException when user does not own file")
            void shouldThrowWhenUserDoesNotOwnFile() {
                // Given
                String otherUserId = "other-user-456";
                String fileUrl = "users/" + otherUserId + "/file.jpg";

                // When/Then
                assertThatThrownBy(() -> controller.deleteFile(fileUrl, servletRequest))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when file not found")
            void shouldThrowWhenFileNotFound() {
                // Given
                String fileUrl = "users/" + USER_ID + "/file.jpg";
                when(storage.get(any(BlobId.class))).thenReturn(null);
                when(storage.delete(any(BlobId.class))).thenReturn(false);

                // When/Then
                assertThatThrownBy(() -> controller.deleteFile(fileUrl, servletRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
            }

            @Test
            @DisplayName("should throw StorageException when storage operation fails")
            void shouldThrowWhenStorageOperationFails() {
                // Given
                String fileUrl = "users/" + USER_ID + "/file.jpg";
                when(storage.get(any(BlobId.class))).thenThrow(new RuntimeException("Storage error"));

                // When/Then
                assertThatThrownBy(() -> controller.deleteFile(fileUrl, servletRequest))
                    .isInstanceOf(StorageTranslatableException.class);
            }

            @Test
            @DisplayName("should handle Firebase Storage URL correctly")
            void shouldHandleFirebaseStorageUrl() {
                // Given
                String fileUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/users%2F" + USER_ID + "%2Fimage.jpg?alt=media";
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(2048L);
                when(storage.get(any(BlobId.class))).thenReturn(blob);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFile(fileUrl, servletRequest);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().isSuccess()).isTrue();
            }

            @Test
            @DisplayName("should not reclaim storage when file size is null")
            void shouldNotReclaimStorageWhenFileSizeIsNull() {
                // Given
                String fileUrl = "users/" + USER_ID + "/file.jpg";
                when(storage.get(any(BlobId.class))).thenReturn(null);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFile(fileUrl, servletRequest);

                // Then
                assertThat(response.getBody().isSuccess()).isTrue();
                verify(storageRateLimitService, never()).decreaseUserStorage(anyString(), anyLong());
            }

            @Test
            @DisplayName("should not reclaim storage when file size is zero")
            void shouldNotReclaimStorageWhenFileSizeIsZero() {
                // Given
                String fileUrl = "users/" + USER_ID + "/file.jpg";
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(0L);
                when(storage.get(any(BlobId.class))).thenReturn(blob);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFile(fileUrl, servletRequest);

                // Then
                assertThat(response.getBody().isSuccess()).isTrue();
                verify(storageRateLimitService, never()).decreaseUserStorage(anyString(), anyLong());
            }

            @Test
            @DisplayName("should include correlation ID in response")
            void shouldIncludeCorrelationIdInResponse() {
                // Given
                String fileUrl = "users/" + USER_ID + "/file.jpg";
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(1024L);
                when(storage.get(any(BlobId.class))).thenReturn(blob);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFile(fileUrl, servletRequest);

                // Then
                assertThat(response.getBody().getData()).containsKey("correlationId");
            }

            @Test
            @DisplayName("should handle email without Claims details")
            void shouldHandleEmailWithoutClaimsDetails() {
                // Given
                when(authentication.getDetails()).thenReturn("not-claims-object");
                String fileUrl = "users/" + USER_ID + "/file.jpg";
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(1024L);
                when(storage.get(any(BlobId.class))).thenReturn(blob);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFile(fileUrl, servletRequest);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Nested
        @DisplayName("DELETE /files/batch-delete - Batch File Deletion")
        class BatchDeleteFilesTests {

            @Test
            @DisplayName("should delete multiple files successfully")
            void shouldDeleteMultipleFilesSuccessfully() {
                // Given
                DeleteFilesRequest request = new DeleteFilesRequest();
                request.setFileUrls(Arrays.asList(
                    "users/" + USER_ID + "/file1.jpg",
                    "users/" + USER_ID + "/file2.jpg"
                ));
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(1024L);
                when(storage.get(any(BlobId.class))).thenReturn(blob);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFiles(request, servletRequest);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().isSuccess()).isTrue();
                @SuppressWarnings("unchecked")
                Map<String, Object> summary = (Map<String, Object>) response.getBody().getData().get("summary");
                assertThat(summary.get("total")).isEqualTo(2);
                assertThat(summary.get("success")).isEqualTo(2);
                assertThat(summary.get("failed")).isEqualTo(0);
            }

            @Test
            @DisplayName("should throw ValidationException when file URLs is null")
            void shouldThrowWhenFileUrlsIsNull() {
                // Given
                DeleteFilesRequest request = new DeleteFilesRequest();
                request.setFileUrls(null);

                // When/Then
                assertThatThrownBy(() -> controller.deleteFiles(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when file URLs is empty")
            void shouldThrowWhenFileUrlsIsEmpty() {
                // Given
                DeleteFilesRequest request = new DeleteFilesRequest();
                request.setFileUrls(Collections.emptyList());

                // When/Then
                assertThatThrownBy(() -> controller.deleteFiles(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when file URLs exceeds 100")
            void shouldThrowWhenFileUrlsExceeds100() {
                // Given
                DeleteFilesRequest request = new DeleteFilesRequest();
                List<String> urls = new ArrayList<>();
                for (int i = 0; i < 101; i++) {
                    urls.add("users/" + USER_ID + "/file" + i + ".jpg");
                }
                request.setFileUrls(urls);

                // When/Then
                assertThatThrownBy(() -> controller.deleteFiles(request, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should handle mixed success and failure")
            void shouldHandleMixedSuccessAndFailure() {
                // Given
                DeleteFilesRequest request = new DeleteFilesRequest();
                request.setFileUrls(Arrays.asList(
                    "users/" + USER_ID + "/file1.jpg",
                    "users/other-user/file2.jpg",
                    "users/" + USER_ID + "/file3.jpg"
                ));
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(1024L);
                when(storage.get(any(BlobId.class))).thenReturn(blob);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFiles(request, servletRequest);

                // Then
                @SuppressWarnings("unchecked")
                Map<String, Object> summary = (Map<String, Object>) response.getBody().getData().get("summary");
                assertThat(summary.get("total")).isEqualTo(3);
                assertThat(summary.get("success")).isEqualTo(2);
                assertThat(summary.get("failed")).isEqualTo(1);
            }

            @Test
            @DisplayName("should handle file not found in batch")
            void shouldHandleFileNotFoundInBatch() {
                // Given
                DeleteFilesRequest request = new DeleteFilesRequest();
                request.setFileUrls(Arrays.asList("users/" + USER_ID + "/file1.jpg"));
                when(storage.get(any(BlobId.class))).thenReturn(null);
                when(storage.delete(any(BlobId.class))).thenReturn(false);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFiles(request, servletRequest);

                // Then
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().getData().get("results");
                assertThat(results.get(0).get("status")).isEqualTo("NOT_FOUND");
            }

            @Test
            @DisplayName("should handle exception for individual file in batch")
            void shouldHandleExceptionForIndividualFileInBatch() {
                // Given
                DeleteFilesRequest request = new DeleteFilesRequest();
                request.setFileUrls(Arrays.asList("users/" + USER_ID + "/file1.jpg"));
                when(storage.get(any(BlobId.class))).thenThrow(new RuntimeException("Error"));

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFiles(request, servletRequest);

                // Then
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().getData().get("results");
                assertThat(results.get(0).get("status")).isEqualTo("ERROR");
            }

            @Test
            @DisplayName("should reclaim storage for each successful deletion")
            void shouldReclaimStorageForEachSuccessfulDeletion() {
                // Given
                DeleteFilesRequest request = new DeleteFilesRequest();
                request.setFileUrls(Arrays.asList(
                    "users/" + USER_ID + "/file1.jpg",
                    "users/" + USER_ID + "/file2.jpg"
                ));
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(1024L);
                when(storage.get(any(BlobId.class))).thenReturn(blob);
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                controller.deleteFiles(request, servletRequest);

                // Then
                verify(storageRateLimitService, times(2)).decreaseUserStorage(eq(USER_ID), eq(1024L));
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when not authenticated")
            void shouldThrowWhenNotAuthenticatedForBatch() {
                // Given
                when(securityContext.getAuthentication()).thenReturn(null);
                DeleteFilesRequest request = new DeleteFilesRequest();
                request.setFileUrls(Arrays.asList("users/" + USER_ID + "/file1.jpg"));

                // When/Then
                assertThatThrownBy(() -> controller.deleteFiles(request, servletRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Nested
        @DisplayName("DELETE /files/folder - Folder Deletion")
        class DeleteFolderTests {

            @Test
            @DisplayName("should delete folder successfully when user owns it")
            @SuppressWarnings("unchecked")
            void shouldDeleteFolderSuccessfully() {
                // Given
                String folderPath = "users/" + USER_ID + "/content";
                when(storageService.deleteFolder(eq(BUCKET_NAME), eq(folderPath))).thenReturn(5);

                Page<Blob> page = mock(Page.class);
                Blob blob = mock(Blob.class);
                when(blob.getSize()).thenReturn(1024L);
                when(page.iterateAll()).thenReturn(Arrays.asList(blob, blob, blob, blob, blob));
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class))).thenReturn(page);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFolder(folderPath, servletRequest);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().isSuccess()).isTrue();
                assertThat(response.getBody().getData().get("filesDeleted")).isEqualTo(5);
            }

            @Test
            @DisplayName("should throw ValidationException when path is null")
            void shouldThrowWhenPathIsNull() {
                // When/Then
                assertThatThrownBy(() -> controller.deleteFolder(null, servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when path is empty")
            void shouldThrowWhenPathIsEmpty() {
                // When/Then
                assertThatThrownBy(() -> controller.deleteFolder("", servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when path is whitespace")
            void shouldThrowWhenPathIsWhitespace() {
                // When/Then
                assertThatThrownBy(() -> controller.deleteFolder("   ", servletRequest))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw BusinessRuleException when user does not own folder")
            void shouldThrowWhenUserDoesNotOwnFolder() {
                // Given
                String otherUserPath = "users/other-user-456/content";

                // When/Then
                assertThatThrownBy(() -> controller.deleteFolder(otherUserPath, servletRequest))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
            }

            @Test
            @DisplayName("should reclaim storage after folder deletion")
            @SuppressWarnings("unchecked")
            void shouldReclaimStorageAfterFolderDeletion() {
                // Given
                String folderPath = "users/" + USER_ID + "/content";
                when(storageService.deleteFolder(eq(BUCKET_NAME), eq(folderPath))).thenReturn(2);

                Page<Blob> page = mock(Page.class);
                Blob blob1 = mock(Blob.class);
                Blob blob2 = mock(Blob.class);
                when(blob1.getSize()).thenReturn(1024L);
                when(blob2.getSize()).thenReturn(2048L);
                when(page.iterateAll()).thenReturn(Arrays.asList(blob1, blob2));
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class))).thenReturn(page);

                // When
                controller.deleteFolder(folderPath, servletRequest);

                // Then
                verify(storageRateLimitService).decreaseUserStorage(eq(USER_ID), eq(3072L));
            }

            @Test
            @DisplayName("should not reclaim storage when no files deleted")
            @SuppressWarnings("unchecked")
            void shouldNotReclaimStorageWhenNoFilesDeleted() {
                // Given
                String folderPath = "users/" + USER_ID + "/content";
                when(storageService.deleteFolder(eq(BUCKET_NAME), eq(folderPath))).thenReturn(0);

                Page<Blob> page = mock(Page.class);
                when(page.iterateAll()).thenReturn(Collections.emptyList());
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class))).thenReturn(page);

                // When
                controller.deleteFolder(folderPath, servletRequest);

                // Then
                verify(storageRateLimitService, never()).decreaseUserStorage(anyString(), anyLong());
            }

            @Test
            @DisplayName("should throw StorageException when storageService.deleteFolder throws exception")
            @SuppressWarnings("unchecked")
            void shouldThrowWhenStorageOperationFailsForFolder() {
                // Given
                String folderPath = "users/" + USER_ID + "/content";
                // Setup folder size calculation to succeed
                Page<Blob> page = mock(Page.class);
                when(page.iterateAll()).thenReturn(Collections.emptyList());
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class))).thenReturn(page);
                // Make the deleteFolder call fail
                when(storageService.deleteFolder(eq(BUCKET_NAME), eq(folderPath)))
                    .thenThrow(new StorageTranslatableException("error.storage.delete_failed"));

                // When/Then
                assertThatThrownBy(() -> controller.deleteFolder(folderPath, servletRequest))
                    .isInstanceOf(StorageTranslatableException.class);
            }

            @Test
            @DisplayName("should handle folder size calculation failure gracefully")
            @SuppressWarnings("unchecked")
            void shouldHandleFolderSizeCalculationFailure() {
                // Given
                String folderPath = "users/" + USER_ID + "/content";

                Page<Blob> page = mock(Page.class);
                when(page.iterateAll()).thenThrow(new RuntimeException("Size calc error"));
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class))).thenReturn(page);
                when(storageService.deleteFolder(eq(BUCKET_NAME), eq(folderPath))).thenReturn(3);

                // When
                ResponseEntity<FileOperationResponse> response = controller.deleteFolder(folderPath, servletRequest);

                // Then - should still succeed
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Nested
        @DisplayName("Security - Path Traversal Prevention")
        class PathTraversalTests {

            @Test
            @DisplayName("should reject path with null byte injection")
            void shouldRejectNullByteInjection() {
                // Given - null byte in path causes verification to fail
                // The controller checks ownership first, and the null byte causes path issues
                String maliciousUrl = "users/" + USER_ID + "/\0malicious.jpg";

                // When/Then - The malicious path is rejected (security behavior verified)
                assertThatThrownBy(() -> controller.deleteFile(maliciousUrl, servletRequest))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
            }

            @Test
            @DisplayName("should reject path traversal attempts")
            void shouldRejectPathTraversalAttempts() {
                // Given
                String maliciousUrl = "users/" + USER_ID + "/../other-user/secret.jpg";

                // When/Then - Should fail ownership check after normalization
                assertThatThrownBy(() -> controller.deleteFile(maliciousUrl, servletRequest))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
            }
        }
    }

    // ========================================================================
    // UploadAdminController Tests
    // ========================================================================

    @Nested
    @DisplayName("UploadAdminController")
    class UploadAdminControllerTests {

        @Mock
        private FileUploadRepository uploadRepository;

        @Mock
        private FileTrackingService trackingService;

        @Mock
        private StorageRateLimitService rateLimiterService;

        @Mock
        private SecurityContext securityContext;

        @Mock
        private Authentication authentication;

        @InjectMocks
        private UploadAdminController controller;

        private static final String ADMIN_UID = "admin-firebase-uid";

        @BeforeEach
        void setUp() {
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn(ADMIN_UID);
        }

        @Nested
        @DisplayName("GET /admin/uploads/stats/system")
        class GetSystemStatsTests {

            @Test
            @DisplayName("should return system statistics successfully")
            void shouldReturnSystemStats() {
                // Given
                when(uploadRepository.count()).thenReturn(100L);
                when(uploadRepository.findByStatusAndCreatedAtBefore(eq(FileUpload.UploadStatus.PENDING), any(Instant.class)))
                    .thenReturn(Arrays.asList(new FileUpload(), new FileUpload()));
                when(uploadRepository.findByUserIdAndStatusIn(eq(""), anyList()))
                    .thenReturn(createMockUploads(5));
                when(uploadRepository.getTotalStorageByUser(eq(""))).thenReturn(1024000L);

                // When
                ResponseEntity<?> response = controller.getSystemStats();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) response.getBody();
                assertThat(stats.get("totalUploads")).isEqualTo(100L);
                assertThat(stats.get("pendingUploads")).isEqualTo(2L);
            }

            @Test
            @DisplayName("should handle null total storage")
            void shouldHandleNullTotalStorage() {
                // Given
                when(uploadRepository.count()).thenReturn(0L);
                when(uploadRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
                when(uploadRepository.findByUserIdAndStatusIn(anyString(), anyList())).thenReturn(Collections.emptyList());
                when(uploadRepository.getTotalStorageByUser(anyString())).thenReturn(null);

                // When
                ResponseEntity<?> response = controller.getSystemStats();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) response.getBody();
                assertThat(stats.get("totalStorageBytes")).isEqualTo(0L);
            }
        }

        @Nested
        @DisplayName("GET /admin/uploads/user/{userId}")
        class GetUserUploadsTests {

            @Test
            @DisplayName("should return user uploads successfully")
            void shouldReturnUserUploads() {
                // Given
                String userId = "user-123";
                List<FileUpload> uploads = createMockUploads(3);
                when(uploadRepository.findByUserId(eq(userId))).thenReturn(uploads);
                when(trackingService.getUserStats(eq(userId))).thenReturn(
                    new FileTrackingService.UserUploadStats(3, 1024L, 2, 1)
                );
                when(rateLimiterService.getUserStatus(eq(userId))).thenReturn(
                    new StorageRateLimitService.RateLimitStatus(5, 10, 20, 50, 1024L, 10240L)
                );

                // When
                ResponseEntity<?> response = controller.getUserUploads(userId, 0, 20);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }

            @Test
            @DisplayName("should throw ValidationException when userId is null")
            void shouldThrowWhenUserIdIsNull() {
                // When/Then
                assertThatThrownBy(() -> controller.getUserUploads(null, 0, 20))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when userId is empty")
            void shouldThrowWhenUserIdIsEmpty() {
                // When/Then
                assertThatThrownBy(() -> controller.getUserUploads("", 0, 20))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when userId is whitespace")
            void shouldThrowWhenUserIdIsWhitespace() {
                // When/Then
                assertThatThrownBy(() -> controller.getUserUploads("   ", 0, 20))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when page is negative")
            void shouldThrowWhenPageIsNegative() {
                // When/Then
                assertThatThrownBy(() -> controller.getUserUploads("user-123", -1, 20))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when size is zero")
            void shouldThrowWhenSizeIsZero() {
                // When/Then
                assertThatThrownBy(() -> controller.getUserUploads("user-123", 0, 0))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when size exceeds 100")
            void shouldThrowWhenSizeExceeds100() {
                // When/Then
                assertThatThrownBy(() -> controller.getUserUploads("user-123", 0, 101))
                    .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("POST /admin/uploads/cleanup/orphaned")
        class CleanupOrphanedTests {

            @Test
            @DisplayName("should cleanup orphaned uploads successfully")
            void shouldCleanupOrphanedUploads() {
                // Given
                doNothing().when(trackingService).cleanupOrphanedUploads();

                // When
                ResponseEntity<?> response = controller.cleanupOrphanedUploads();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(trackingService).cleanupOrphanedUploads();
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) response.getBody();
                assertThat(body.get("status")).isEqualTo("success");
            }
        }

        @Nested
        @DisplayName("GET /admin/uploads/status/{status}")
        class GetUploadsByStatusTests {

            @Test
            @DisplayName("should return uploads by status successfully")
            void shouldReturnUploadsByStatus() {
                // Given
                List<FileUpload> uploads = createMockUploads(5);
                when(uploadRepository.findByStatusAndCreatedAtBefore(eq(FileUpload.UploadStatus.PENDING), any(Instant.class)))
                    .thenReturn(uploads);

                // When
                ResponseEntity<?> response = controller.getUploadsByStatus(FileUpload.UploadStatus.PENDING, 0, 50);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) response.getBody();
                assertThat(body.get("totalCount")).isEqualTo(5);
            }

            @Test
            @DisplayName("should throw ValidationException when status is null")
            void shouldThrowWhenStatusIsNull() {
                // When/Then
                assertThatThrownBy(() -> controller.getUploadsByStatus(null, 0, 50))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when page is negative")
            void shouldThrowWhenPageIsNegativeForStatus() {
                // When/Then
                assertThatThrownBy(() -> controller.getUploadsByStatus(FileUpload.UploadStatus.CONFIRMED, -1, 50))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when size is invalid")
            void shouldThrowWhenSizeIsInvalidForStatus() {
                // When/Then
                assertThatThrownBy(() -> controller.getUploadsByStatus(FileUpload.UploadStatus.CONFIRMED, 0, 0))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when size exceeds limit")
            void shouldThrowWhenSizeExceedsLimitForStatus() {
                // When/Then
                assertThatThrownBy(() -> controller.getUploadsByStatus(FileUpload.UploadStatus.CONFIRMED, 0, 101))
                    .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("GET /admin/uploads/reports/weekly")
        class GenerateWeeklyReportTests {

            @Test
            @DisplayName("should generate weekly report successfully")
            void shouldGenerateWeeklyReport() {
                // Given
                List<FileUpload> uploads = createMockUploads(10);
                when(uploadRepository.findByUserIdAndStatusIn(eq(""), anyList())).thenReturn(uploads);

                // When
                ResponseEntity<?> response = controller.generateWeeklyReport();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                @SuppressWarnings("unchecked")
                Map<String, Object> report = (Map<String, Object>) response.getBody();
                assertThat(report).containsKeys("period", "summary", "byContentType", "generatedAt");
            }

            @Test
            @DisplayName("should handle empty uploads for weekly report")
            void shouldHandleEmptyUploadsForWeeklyReport() {
                // Given
                when(uploadRepository.findByUserIdAndStatusIn(eq(""), anyList())).thenReturn(Collections.emptyList());

                // When
                ResponseEntity<?> response = controller.generateWeeklyReport();

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        private List<FileUpload> createMockUploads(int count) {
            List<FileUpload> uploads = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                FileUpload upload = new FileUpload();
                upload.setId(UUID.randomUUID().toString());
                upload.setUserId("user-" + i);
                upload.setFilePath("path/file" + i + ".jpg");
                upload.setFilename("file" + i + ".jpg");
                upload.setContentType("image/jpeg");
                upload.setFileSize(1024L * (i + 1));
                upload.setUploadTime(Instant.now().minusSeconds(3600 * i));
                upload.setStatus(FileUpload.UploadStatus.CONFIRMED);
                upload.setCreatedAt(Instant.now());
                uploads.add(upload);
            }
            return uploads;
        }
    }

    // ========================================================================
    // WebhookController Tests
    // ========================================================================

    @Nested
    @DisplayName("WebhookController")
    class WebhookControllerTests {

        @Mock
        private FileTrackingService trackingService;

        @InjectMocks
        private WebhookController controller;

        private static final String WEBHOOK_SECRET = "test-secret-123";

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(controller, "webhookSecret", "");
        }

        @Nested
        @DisplayName("POST /webhooks/firebase/storage - Object Finalized")
        class ObjectFinalizedTests {

            @Test
            @DisplayName("should handle object.finalize event successfully")
            void shouldHandleObjectFinalizeEvent() {
                // Given
                Map<String, Object> payload = createFinalizePayload("content/user123/image.jpg", 1024L);
                doNothing().when(trackingService).confirmUploadViaWebhook(anyString(), anyString(), anyLong(), anyString(), anyMap());

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(trackingService).confirmUploadViaWebhook(
                    eq("content/user123/image.jpg"),
                    eq("user123"),
                    eq(1024L),
                    eq("image/jpeg"),
                    anyMap()
                );
            }

            @Test
            @DisplayName("should throw ValidationException when data is missing")
            void shouldThrowWhenDataIsMissing() {
                // Given
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.finalize");
                payload.put("data", null);

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when name is missing")
            void shouldThrowWhenNameIsMissing() {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("size", 1024L);
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.finalize");
                payload.put("data", data);

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when size is missing")
            void shouldThrowWhenSizeIsMissing() {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("name", "content/user123/image.jpg");
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.finalize");
                payload.put("data", data);

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should not process when userId is null")
            void shouldNotProcessWhenUserIdIsNull() {
                // Given - path that doesn't start with content/
                Map<String, Object> data = new HashMap<>();
                data.put("name", "other/path/image.jpg");
                data.put("size", "1024");
                data.put("contentType", "image/jpeg");
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.finalize");
                payload.put("data", data);

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(trackingService, never()).confirmUploadViaWebhook(anyString(), anyString(), anyLong(), anyString(), anyMap());
            }

            @Test
            @DisplayName("should not process when metadata is null")
            void shouldNotProcessWhenMetadataIsNull() {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("name", "content/user123/image.jpg");
                data.put("size", "1024");
                data.put("contentType", "image/jpeg");
                data.put("metadata", null);
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.finalize");
                payload.put("data", data);

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(trackingService, never()).confirmUploadViaWebhook(anyString(), anyString(), anyLong(), anyString(), anyMap());
            }
        }

        @Nested
        @DisplayName("POST /webhooks/firebase/storage - Object Deleted")
        class ObjectDeletedTests {

            @Test
            @DisplayName("should handle object.delete event successfully")
            void shouldHandleObjectDeleteEvent() {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("name", "content/user123/image.jpg");
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.delete");
                payload.put("data", data);
                doNothing().when(trackingService).markFileAsDeleted(anyString());

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(trackingService).markFileAsDeleted(eq("content/user123/image.jpg"));
            }

            @Test
            @DisplayName("should throw ValidationException when data is missing for delete")
            void shouldThrowWhenDataIsMissingForDelete() {
                // Given
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.delete");
                payload.put("data", null);

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when name is missing for delete")
            void shouldThrowWhenNameIsMissingForDelete() {
                // Given
                Map<String, Object> data = new HashMap<>();
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.delete");
                payload.put("data", data);

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("POST /webhooks/firebase/storage - Metadata Update")
        class MetadataUpdateTests {

            @Test
            @DisplayName("should handle metadataUpdate event successfully")
            void shouldHandleMetadataUpdateEvent() {
                // Given
                Map<String, String> metadata = new HashMap<>();
                metadata.put("description", "Updated description");
                Map<String, Object> data = new HashMap<>();
                data.put("name", "content/user123/image.jpg");
                data.put("metadata", metadata);
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.metadataUpdate");
                payload.put("data", data);
                doNothing().when(trackingService).updateFileMetadata(anyString(), anyMap());

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(trackingService).updateFileMetadata(eq("content/user123/image.jpg"), eq(metadata));
            }

            @Test
            @DisplayName("should throw ValidationException when data is missing for metadata update")
            void shouldThrowWhenDataIsMissingForMetadataUpdate() {
                // Given
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.metadataUpdate");
                payload.put("data", null);

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException when name is missing for metadata update")
            void shouldThrowWhenNameIsMissingForMetadataUpdate() {
                // Given
                Map<String, Object> data = new HashMap<>();
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.metadataUpdate");
                payload.put("data", data);

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("POST /webhooks/firebase/storage - Event Type Validation")
        class EventTypeValidationTests {

            @Test
            @DisplayName("should throw ValidationException when eventType is missing")
            void shouldThrowWhenEventTypeIsMissing() {
                // Given
                Map<String, Object> payload = new HashMap<>();
                payload.put("data", new HashMap<>());

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should ignore unknown event types")
            void shouldIgnoreUnknownEventTypes() {
                // Given
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.unknown");

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verifyNoInteractions(trackingService);
            }
        }

        @Nested
        @DisplayName("POST /webhooks/firebase/storage - Signature Verification")
        class SignatureVerificationTests {

            @Test
            @DisplayName("should reject invalid signature when secret is configured")
            void shouldRejectInvalidSignature() {
                // Given
                ReflectionTestUtils.setField(controller, "webhookSecret", WEBHOOK_SECRET);
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.finalize");
                payload.put("data", new HashMap<>());

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook("invalid-signature", payload))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
            }

            @Test
            @DisplayName("should skip signature verification when secret is empty")
            void shouldSkipVerificationWhenSecretIsEmpty() {
                // Given
                ReflectionTestUtils.setField(controller, "webhookSecret", "");
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.unknown");

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook("any-signature", payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }

            @Test
            @DisplayName("should reject when signature is null but secret is configured")
            void shouldRejectWhenSignatureIsNull() {
                // Given
                ReflectionTestUtils.setField(controller, "webhookSecret", WEBHOOK_SECRET);
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.finalize");

                // When/Then
                assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("User ID Extraction")
        class UserIdExtractionTests {

            @Test
            @DisplayName("should extract userId from content path")
            void shouldExtractUserIdFromContentPath() {
                // Given
                Map<String, Object> payload = createFinalizePayload("content/user-abc-123/file.jpg", 1024L);
                doNothing().when(trackingService).confirmUploadViaWebhook(anyString(), anyString(), anyLong(), anyString(), anyMap());

                // When
                controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                verify(trackingService).confirmUploadViaWebhook(
                    anyString(),
                    eq("user-abc-123"),
                    anyLong(),
                    anyString(),
                    anyMap()
                );
            }

            @Test
            @DisplayName("should return null for non-content paths")
            void shouldReturnNullForNonContentPaths() {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("name", "other/user123/image.jpg");
                data.put("size", "1024");
                data.put("contentType", "image/jpeg");
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.finalize");
                payload.put("data", data);

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                verify(trackingService, never()).confirmUploadViaWebhook(anyString(), anyString(), anyLong(), anyString(), anyMap());
            }

            @Test
            @DisplayName("should handle path with only content prefix")
            void shouldHandlePathWithOnlyContentPrefix() {
                // Given
                Map<String, Object> data = new HashMap<>();
                data.put("name", "content/");
                Map<String, Object> payload = new HashMap<>();
                payload.put("eventType", "google.storage.object.delete");
                payload.put("data", data);

                // When
                ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        private Map<String, Object> createFinalizePayload(String objectName, Long size) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("uploadId", "upload-123");
            Map<String, Object> data = new HashMap<>();
            data.put("name", objectName);
            data.put("size", String.valueOf(size));
            data.put("contentType", "image/jpeg");
            data.put("metadata", metadata);
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "google.storage.object.finalize");
            payload.put("data", data);
            return payload;
        }
    }

    // ========================================================================
    // FileOperationResponse Tests
    // ========================================================================

    @Nested
    @DisplayName("FileOperationResponse Model")
    class FileOperationResponseTests {

        @Test
        @DisplayName("should create success response correctly")
        void shouldCreateSuccessResponse() {
            // When
            FileOperationResponse response = FileOperationResponse.success("Test message", Map.of("key", "value"));

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Test message");
            assertThat(response.getData()).containsEntry("key", "value");
            assertThat(response.getTimestamp()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should create error response correctly")
        void shouldCreateErrorResponse() {
            // When
            FileOperationResponse response = FileOperationResponse.error("Error message");

            // Then
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Error message");
            assertThat(response.getTimestamp()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should use builder correctly")
        void shouldUseBuilderCorrectly() {
            // When
            FileOperationResponse response = FileOperationResponse.builder()
                .success(true)
                .message("Built message")
                .correlationId("correlation-123")
                .build();

            // Then
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Built message");
            assertThat(response.getCorrelationId()).isEqualTo("correlation-123");
        }
    }

    // ========================================================================
    // DeleteFilesRequest Tests
    // ========================================================================

    @Nested
    @DisplayName("DeleteFilesRequest Model")
    class DeleteFilesRequestTests {

        @Test
        @DisplayName("should set and get file URLs correctly")
        void shouldSetAndGetFileUrls() {
            // Given
            DeleteFilesRequest request = new DeleteFilesRequest();
            List<String> urls = Arrays.asList("url1", "url2");

            // When
            request.setFileUrls(urls);

            // Then
            assertThat(request.getFileUrls()).containsExactly("url1", "url2");
        }

        @Test
        @DisplayName("should handle empty list")
        void shouldHandleEmptyList() {
            // Given
            DeleteFilesRequest request = new DeleteFilesRequest();
            request.setFileUrls(Collections.emptyList());

            // Then
            assertThat(request.getFileUrls()).isEmpty();
        }

        @Test
        @DisplayName("should handle null list")
        void shouldHandleNullList() {
            // Given
            DeleteFilesRequest request = new DeleteFilesRequest();
            request.setFileUrls(null);

            // Then
            assertThat(request.getFileUrls()).isNull();
        }
    }

    // ========================================================================
    // Additional FileManagementController Edge Case Tests
    // ========================================================================

    @Nested
    @DisplayName("FileManagementController Edge Cases")
    class FileManagementControllerEdgeCaseTests {

        @Mock
        private Storage storage;

        @Mock
        private UserRepository userRepository;

        @Mock
        private FirebaseStorageService storageService;

        @Mock
        private StorageRateLimitService storageRateLimitService;

        @Mock
        private HttpServletRequest servletRequest;

        @Mock
        private SecurityContext securityContext;

        @Mock
        private Authentication authentication;

        @Mock
        private Claims claims;

        @InjectMocks
        private FileManagementController controller;

        private static final String BUCKET_NAME = "test-bucket";
        private static final String USER_ID = "firebase-user-123";

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(controller, "bucketName", BUCKET_NAME);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(USER_ID);
            when(authentication.getDetails()).thenReturn(claims);
            when(claims.get("email", String.class)).thenReturn("test@example.com");
        }

        @Test
        @DisplayName("should handle URL encoded path correctly")
        void shouldHandleUrlEncodedPath() {
            // Given
            String encodedUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/users%2F" + USER_ID + "%2Ffile%20with%20space.jpg?alt=media";
            Blob blob = mock(Blob.class);
            when(blob.getSize()).thenReturn(1024L);
            when(storage.get(any(BlobId.class))).thenReturn(blob);
            when(storage.delete(any(BlobId.class))).thenReturn(true);

            // When
            ResponseEntity<FileOperationResponse> response = controller.deleteFile(encodedUrl, servletRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle very long file paths")
        void shouldHandleVeryLongFilePaths() {
            // Given
            String longPath = "users/" + USER_ID + "/" + "a".repeat(200) + ".jpg";
            Blob blob = mock(Blob.class);
            when(blob.getSize()).thenReturn(1024L);
            when(storage.get(any(BlobId.class))).thenReturn(blob);
            when(storage.delete(any(BlobId.class))).thenReturn(true);

            // When
            ResponseEntity<FileOperationResponse> response = controller.deleteFile(longPath, servletRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle path with special characters")
        void shouldHandlePathWithSpecialCharacters() {
            // Given
            String pathWithSpecialChars = "users/" + USER_ID + "/file-name_123.jpg";
            Blob blob = mock(Blob.class);
            when(blob.getSize()).thenReturn(1024L);
            when(storage.get(any(BlobId.class))).thenReturn(blob);
            when(storage.delete(any(BlobId.class))).thenReturn(true);

            // When
            ResponseEntity<FileOperationResponse> response = controller.deleteFile(pathWithSpecialChars, servletRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle nested folder paths")
        void shouldHandleNestedFolderPaths() {
            // Given
            String nestedPath = "users/" + USER_ID + "/content/images/profile/avatar.jpg";
            Blob blob = mock(Blob.class);
            when(blob.getSize()).thenReturn(2048L);
            when(storage.get(any(BlobId.class))).thenReturn(blob);
            when(storage.delete(any(BlobId.class))).thenReturn(true);

            // When
            ResponseEntity<FileOperationResponse> response = controller.deleteFile(nestedPath, servletRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            verify(storageRateLimitService).decreaseUserStorage(eq(USER_ID), eq(2048L));
        }

        @Test
        @DisplayName("should reject double dot in path")
        void shouldRejectDoubleDotsInPath() {
            // Given
            String maliciousPath = "users/" + USER_ID + "/content/../../../etc/passwd";

            // When/Then
            assertThatThrownBy(() -> controller.deleteFile(maliciousPath, servletRequest))
                .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should handle multiple files in batch delete with varying statuses")
        void shouldHandleMultipleFilesWithVaryingStatuses() {
            // Given
            DeleteFilesRequest request = new DeleteFilesRequest();
            request.setFileUrls(Arrays.asList(
                "users/" + USER_ID + "/file1.jpg",  // success
                "users/other-user/file2.jpg",        // unauthorized
                "users/" + USER_ID + "/file3.jpg"   // not found
            ));
            Blob blob = mock(Blob.class);
            when(blob.getSize()).thenReturn(1024L);
            when(storage.get(any(BlobId.class))).thenReturn(blob);
            when(storage.delete(any(BlobId.class)))
                .thenReturn(true)
                .thenReturn(false);

            // When
            ResponseEntity<FileOperationResponse> response = controller.deleteFiles(request, servletRequest);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().getData().get("results");
            assertThat(results).hasSize(3);
        }
    }

    // ========================================================================
    // Additional UploadAdminController Edge Case Tests
    // ========================================================================

    @Nested
    @DisplayName("UploadAdminController Edge Cases")
    class UploadAdminControllerEdgeCaseTests {

        @Mock
        private FileUploadRepository uploadRepository;

        @Mock
        private FileTrackingService trackingService;

        @Mock
        private StorageRateLimitService rateLimiterService;

        @Mock
        private SecurityContext securityContext;

        @Mock
        private Authentication authentication;

        @InjectMocks
        private UploadAdminController controller;

        @BeforeEach
        void setUp() {
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getPrincipal()).thenReturn("admin-uid");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 10, 50, 100})
        @DisplayName("should handle various page sizes")
        void shouldHandleVariousPageSizes(int size) {
            // Given (size 0 should throw, others pass)
            if (size == 0) {
                // When/Then
                assertThatThrownBy(() -> controller.getUserUploads("user-123", 0, size))
                    .isInstanceOf(ValidationTranslatableException.class);
            } else {
                // Given
                when(uploadRepository.findByUserId(eq("user-123"))).thenReturn(Collections.emptyList());
                when(trackingService.getUserStats(eq("user-123"))).thenReturn(
                    new FileTrackingService.UserUploadStats(0, 0L, 0, 0)
                );
                when(rateLimiterService.getUserStatus(eq("user-123"))).thenReturn(
                    new StorageRateLimitService.RateLimitStatus(0, 10, 0, 50, 0L, 10240L)
                );

                // When
                ResponseEntity<?> response = controller.getUserUploads("user-123", 0, size);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        @Test
        @DisplayName("should handle large page numbers")
        void shouldHandleLargePageNumbers() {
            // Given
            when(uploadRepository.findByUserId(eq("user-123"))).thenReturn(Collections.emptyList());
            when(trackingService.getUserStats(eq("user-123"))).thenReturn(
                new FileTrackingService.UserUploadStats(0, 0L, 0, 0)
            );
            when(rateLimiterService.getUserStatus(eq("user-123"))).thenReturn(
                new StorageRateLimitService.RateLimitStatus(0, 10, 0, 50, 0L, 10240L)
            );

            // When
            ResponseEntity<?> response = controller.getUserUploads("user-123", 1000, 20);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ========================================================================
    // Additional WebhookController Edge Case Tests
    // ========================================================================

    @Nested
    @DisplayName("WebhookController Edge Cases")
    class WebhookControllerEdgeCaseTests {

        @Mock
        private FileTrackingService trackingService;

        @InjectMocks
        private WebhookController controller;

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(controller, "webhookSecret", "");
        }

        @Test
        @DisplayName("should handle size as integer type")
        void shouldHandleSizeAsIntegerType() {
            // Given
            Map<String, Object> data = new HashMap<>();
            data.put("name", "content/user123/file.jpg");
            data.put("size", 1024);  // Integer instead of String
            data.put("contentType", "image/jpeg");
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "google.storage.object.finalize");
            payload.put("data", data);

            // When
            ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle size as Long type")
        void shouldHandleSizeAsLongType() {
            // Given
            Map<String, Object> data = new HashMap<>();
            data.put("name", "content/user123/file.jpg");
            data.put("size", 1024L);  // Long type
            data.put("contentType", "image/jpeg");
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "google.storage.object.finalize");
            payload.put("data", data);

            // When
            ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle empty metadata map")
        void shouldHandleEmptyMetadataMap() {
            // Given
            Map<String, Object> data = new HashMap<>();
            data.put("name", "content/user123/file.jpg");
            data.put("size", "1024");
            data.put("contentType", "image/jpeg");
            data.put("metadata", new HashMap<>());  // Empty metadata
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "google.storage.object.finalize");
            payload.put("data", data);

            // When
            ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle missing contentType")
        void shouldHandleMissingContentType() {
            // Given
            Map<String, Object> data = new HashMap<>();
            data.put("name", "content/user123/file.jpg");
            data.put("size", "1024");
            // No contentType
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "google.storage.object.finalize");
            payload.put("data", data);

            // When
            ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle deeply nested content paths")
        void shouldHandleDeeplyNestedContentPaths() {
            // Given
            Map<String, Object> data = new HashMap<>();
            data.put("name", "content/user123/folder1/folder2/folder3/file.jpg");
            data.put("size", "2048");
            data.put("contentType", "image/jpeg");
            Map<String, String> metadata = new HashMap<>();
            metadata.put("uploadId", "upload-456");
            data.put("metadata", metadata);
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "google.storage.object.finalize");
            payload.put("data", data);
            doNothing().when(trackingService).confirmUploadViaWebhook(anyString(), anyString(), anyLong(), anyString(), anyMap());

            // When
            ResponseEntity<?> response = controller.handleFirebaseStorageWebhook(null, payload);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(trackingService).confirmUploadViaWebhook(
                eq("content/user123/folder1/folder2/folder3/file.jpg"),
                eq("user123"),
                eq(2048L),
                eq("image/jpeg"),
                anyMap()
            );
        }
    }
}
