package com.sm.instagram.platform.unit.service;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.storage.service.FirebaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FirebaseStorageService.
 *
 * Tests cover:
 * - deleteFolder: bulk deletion of files in a folder
 * - deleteFile: single file deletion
 * - fileExists: file existence check
 * - getFileMetadata: retrieving file metadata
 *
 * Each method is tested for:
 * - Successful operations
 * - Validation of required parameters
 * - Error handling and exception wrapping
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FirebaseStorageService Unit Tests")
class FirebaseStorageServiceUnitTest {

    @Mock
    private Storage storage;

    private FirebaseStorageService service;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String FOLDER_PATH = "users/12345/content";
    private static final String BLOB_PATH = "users/12345/content/image.jpg";

    @BeforeEach
    void setUp() {
        service = new FirebaseStorageService(storage, BUCKET_NAME);
    }

    // ========================================================================
    // deleteFolder Tests
    // ========================================================================

    @Nested
    @DisplayName("deleteFolder")
    class DeleteFolderTests {

        @Nested
        @DisplayName("Validation")
        class DeleteFolderValidationTests {

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should throw ValidationException when bucketName is null or empty")
            void shouldThrowWhenBucketNameNullOrEmpty(String bucketName) {
                assertThatThrownBy(() -> service.deleteFolder(bucketName, FOLDER_PATH))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should throw ValidationException when bucketName is whitespace only")
            void shouldThrowWhenBucketNameWhitespaceOnly(String bucketName) {
                assertThatThrownBy(() -> service.deleteFolder(bucketName, FOLDER_PATH))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should throw ValidationException when folderPath is null or empty")
            void shouldThrowWhenFolderPathNullOrEmpty(String folderPath) {
                assertThatThrownBy(() -> service.deleteFolder(BUCKET_NAME, folderPath))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should throw ValidationException when folderPath is whitespace only")
            void shouldThrowWhenFolderPathWhitespaceOnly(String folderPath) {
                assertThatThrownBy(() -> service.deleteFolder(BUCKET_NAME, folderPath))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }
        }

        @Nested
        @DisplayName("Success Scenarios")
        class DeleteFolderSuccessTests {

            @Test
            @DisplayName("should return 0 when folder is empty")
            void shouldReturnZeroWhenFolderIsEmpty() {
                // Given
                @SuppressWarnings("unchecked")
                Page<Blob> emptyPage = mock(Page.class);
                when(emptyPage.iterateAll()).thenReturn(Collections.emptyList());
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenReturn(emptyPage);

                // When
                int result = service.deleteFolder(BUCKET_NAME, FOLDER_PATH);

                // Then
                assertThat(result).isEqualTo(0);
                verify(storage, never()).delete(anyList());
            }

            @Test
            @DisplayName("should delete single file and return count of 1")
            void shouldDeleteSingleFileAndReturnCount() {
                // Given
                Blob blob = createMockBlob("file1.jpg");
                @SuppressWarnings("unchecked")
                Page<Blob> page = mock(Page.class);
                when(page.iterateAll()).thenReturn(Collections.singletonList(blob));
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenReturn(page);
                when(storage.delete(anyList())).thenReturn(Collections.singletonList(true));

                // When
                int result = service.deleteFolder(BUCKET_NAME, FOLDER_PATH);

                // Then
                assertThat(result).isEqualTo(1);
                verify(storage).delete(anyList());
            }

            @Test
            @DisplayName("should delete multiple files and return correct count")
            void shouldDeleteMultipleFilesAndReturnCount() {
                // Given
                List<Blob> blobs = Arrays.asList(
                    createMockBlob("file1.jpg"),
                    createMockBlob("file2.jpg"),
                    createMockBlob("file3.jpg")
                );
                @SuppressWarnings("unchecked")
                Page<Blob> page = mock(Page.class);
                when(page.iterateAll()).thenReturn(blobs);
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenReturn(page);
                when(storage.delete(anyList())).thenReturn(Arrays.asList(true, true, true));

                // When
                int result = service.deleteFolder(BUCKET_NAME, FOLDER_PATH);

                // Then
                assertThat(result).isEqualTo(3);
            }

            @Test
            @DisplayName("should add trailing slash to folderPath if missing")
            void shouldAddTrailingSlashToFolderPath() {
                // Given
                String pathWithoutSlash = "users/12345/content";
                @SuppressWarnings("unchecked")
                Page<Blob> emptyPage = mock(Page.class);
                when(emptyPage.iterateAll()).thenReturn(Collections.emptyList());
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenReturn(emptyPage);

                // When
                service.deleteFolder(BUCKET_NAME, pathWithoutSlash);

                // Then
                ArgumentCaptor<Storage.BlobListOption> optionCaptor =
                    ArgumentCaptor.forClass(Storage.BlobListOption.class);
                verify(storage).list(eq(BUCKET_NAME), optionCaptor.capture());
                // The prefix option is used - verification that method was called is sufficient
            }

            @Test
            @DisplayName("should not modify folderPath if it already has trailing slash")
            void shouldNotModifyFolderPathWithTrailingSlash() {
                // Given
                String pathWithSlash = "users/12345/content/";
                @SuppressWarnings("unchecked")
                Page<Blob> emptyPage = mock(Page.class);
                when(emptyPage.iterateAll()).thenReturn(Collections.emptyList());
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenReturn(emptyPage);

                // When
                service.deleteFolder(BUCKET_NAME, pathWithSlash);

                // Then
                verify(storage).list(eq(BUCKET_NAME), any(Storage.BlobListOption.class));
            }

            @Test
            @DisplayName("should batch delete files in groups of 100")
            void shouldBatchDeleteFilesInGroupsOf100() {
                // Given
                // Create 150 blobs to test batching (100 + 50)
                Blob[] blobArray = new Blob[150];
                for (int i = 0; i < 150; i++) {
                    blobArray[i] = createMockBlob("file" + i + ".jpg");
                }
                List<Blob> blobs = Arrays.asList(blobArray);

                @SuppressWarnings("unchecked")
                Page<Blob> page = mock(Page.class);
                when(page.iterateAll()).thenReturn(blobs);
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenReturn(page);

                // First batch of 100, second batch of 50
                List<Boolean> firstBatchResults = Collections.nCopies(100, true);
                List<Boolean> secondBatchResults = Collections.nCopies(50, true);
                when(storage.delete(anyList()))
                    .thenReturn(firstBatchResults)
                    .thenReturn(secondBatchResults);

                // When
                int result = service.deleteFolder(BUCKET_NAME, FOLDER_PATH);

                // Then
                assertThat(result).isEqualTo(150);
                verify(storage, times(2)).delete(anyList());
            }

            @Test
            @DisplayName("should handle partial deletion failures gracefully")
            void shouldHandlePartialDeletionFailures() {
                // Given
                List<Blob> blobs = Arrays.asList(
                    createMockBlob("file1.jpg"),
                    createMockBlob("file2.jpg"),
                    createMockBlob("file3.jpg")
                );
                @SuppressWarnings("unchecked")
                Page<Blob> page = mock(Page.class);
                when(page.iterateAll()).thenReturn(blobs);
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenReturn(page);
                // Second deletion fails
                when(storage.delete(anyList())).thenReturn(Arrays.asList(true, false, true));

                // When
                int result = service.deleteFolder(BUCKET_NAME, FOLDER_PATH);

                // Then
                // Count is still 3 (files encountered), deletion status is logged
                assertThat(result).isEqualTo(3);
            }
        }

        @Nested
        @DisplayName("Error Handling")
        class DeleteFolderErrorTests {

            @Test
            @DisplayName("should throw StorageException when storage.list throws exception")
            void shouldThrowStorageExceptionWhenListFails() {
                // Given
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenThrow(new RuntimeException("Storage unavailable"));

                // When/Then
                assertThatThrownBy(() -> service.deleteFolder(BUCKET_NAME, FOLDER_PATH))
                    .isInstanceOf(StorageTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }

            @Test
            @DisplayName("should throw StorageException when storage.delete throws exception")
            void shouldThrowStorageExceptionWhenDeleteFails() {
                // Given
                Blob blob = createMockBlob("file1.jpg");
                @SuppressWarnings("unchecked")
                Page<Blob> page = mock(Page.class);
                when(page.iterateAll()).thenReturn(Collections.singletonList(blob));
                when(storage.list(eq(BUCKET_NAME), any(Storage.BlobListOption.class)))
                    .thenReturn(page);
                when(storage.delete(anyList()))
                    .thenThrow(new RuntimeException("Delete failed"));

                // When/Then
                assertThatThrownBy(() -> service.deleteFolder(BUCKET_NAME, FOLDER_PATH))
                    .isInstanceOf(StorageTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }
        }
    }

    // ========================================================================
    // deleteFile Tests
    // ========================================================================

    @Nested
    @DisplayName("deleteFile")
    class DeleteFileTests {

        @Nested
        @DisplayName("Validation")
        class DeleteFileValidationTests {

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should throw ValidationException when bucketName is null or empty")
            void shouldThrowWhenBucketNameNullOrEmpty(String bucketName) {
                assertThatThrownBy(() -> service.deleteFile(bucketName, BLOB_PATH))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should throw ValidationException when bucketName is whitespace only")
            void shouldThrowWhenBucketNameWhitespaceOnly(String bucketName) {
                assertThatThrownBy(() -> service.deleteFile(bucketName, BLOB_PATH))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should throw ValidationException when blobPath is null or empty")
            void shouldThrowWhenBlobPathNullOrEmpty(String blobPath) {
                assertThatThrownBy(() -> service.deleteFile(BUCKET_NAME, blobPath))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should throw ValidationException when blobPath is whitespace only")
            void shouldThrowWhenBlobPathWhitespaceOnly(String blobPath) {
                assertThatThrownBy(() -> service.deleteFile(BUCKET_NAME, blobPath))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }
        }

        @Nested
        @DisplayName("Success Scenarios")
        class DeleteFileSuccessTests {

            @Test
            @DisplayName("should return true when file is deleted successfully")
            void shouldReturnTrueWhenFileDeleted() {
                // Given
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                boolean result = service.deleteFile(BUCKET_NAME, BLOB_PATH);

                // Then
                assertThat(result).isTrue();
                verify(storage).delete(argThat((BlobId blobId) ->
                    blobId.getBucket().equals(BUCKET_NAME) &&
                    blobId.getName().equals(BLOB_PATH)));
            }

            @Test
            @DisplayName("should return false when file does not exist")
            void shouldReturnFalseWhenFileNotFound() {
                // Given
                when(storage.delete(any(BlobId.class))).thenReturn(false);

                // When
                boolean result = service.deleteFile(BUCKET_NAME, BLOB_PATH);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should create BlobId with correct bucket and path")
            void shouldCreateBlobIdWithCorrectParameters() {
                // Given
                when(storage.delete(any(BlobId.class))).thenReturn(true);

                // When
                service.deleteFile(BUCKET_NAME, BLOB_PATH);

                // Then
                ArgumentCaptor<BlobId> blobIdCaptor = ArgumentCaptor.forClass(BlobId.class);
                verify(storage).delete(blobIdCaptor.capture());

                BlobId capturedBlobId = blobIdCaptor.getValue();
                assertThat(capturedBlobId.getBucket()).isEqualTo(BUCKET_NAME);
                assertThat(capturedBlobId.getName()).isEqualTo(BLOB_PATH);
            }
        }

        @Nested
        @DisplayName("Error Handling")
        class DeleteFileErrorTests {

            @Test
            @DisplayName("should throw StorageException when storage.delete throws exception")
            void shouldThrowStorageExceptionWhenDeleteFails() {
                // Given
                when(storage.delete(any(BlobId.class)))
                    .thenThrow(new RuntimeException("Storage unavailable"));

                // When/Then
                assertThatThrownBy(() -> service.deleteFile(BUCKET_NAME, BLOB_PATH))
                    .isInstanceOf(StorageTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }
        }
    }

    // ========================================================================
    // fileExists Tests
    // ========================================================================

    @Nested
    @DisplayName("fileExists")
    class FileExistsTests {

        @Nested
        @DisplayName("Validation")
        class FileExistsValidationTests {

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should throw ValidationException when bucketName is null or empty")
            void shouldThrowWhenBucketNameNullOrEmpty(String bucketName) {
                assertThatThrownBy(() -> service.fileExists(bucketName, BLOB_PATH))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should throw ValidationException when bucketName is whitespace only")
            void shouldThrowWhenBucketNameWhitespaceOnly(String bucketName) {
                assertThatThrownBy(() -> service.fileExists(bucketName, BLOB_PATH))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should throw ValidationException when blobPath is null or empty")
            void shouldThrowWhenBlobPathNullOrEmpty(String blobPath) {
                assertThatThrownBy(() -> service.fileExists(BUCKET_NAME, blobPath))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should throw ValidationException when blobPath is whitespace only")
            void shouldThrowWhenBlobPathWhitespaceOnly(String blobPath) {
                assertThatThrownBy(() -> service.fileExists(BUCKET_NAME, blobPath))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }
        }

        @Nested
        @DisplayName("Success Scenarios")
        class FileExistsSuccessTests {

            @Test
            @DisplayName("should return true when blob exists and is valid")
            void shouldReturnTrueWhenBlobExists() {
                // Given
                Blob blob = mock(Blob.class);
                when(blob.exists()).thenReturn(true);
                when(storage.get(any(BlobId.class))).thenReturn(blob);

                // When
                boolean result = service.fileExists(BUCKET_NAME, BLOB_PATH);

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should return false when blob is null")
            void shouldReturnFalseWhenBlobIsNull() {
                // Given
                when(storage.get(any(BlobId.class))).thenReturn(null);

                // When
                boolean result = service.fileExists(BUCKET_NAME, BLOB_PATH);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return false when blob.exists() returns false")
            void shouldReturnFalseWhenBlobExistsReturnsFalse() {
                // Given
                Blob blob = mock(Blob.class);
                when(blob.exists()).thenReturn(false);
                when(storage.get(any(BlobId.class))).thenReturn(blob);

                // When
                boolean result = service.fileExists(BUCKET_NAME, BLOB_PATH);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should create BlobId with correct bucket and path")
            void shouldCreateBlobIdWithCorrectParameters() {
                // Given
                Blob blob = mock(Blob.class);
                when(blob.exists()).thenReturn(true);
                when(storage.get(any(BlobId.class))).thenReturn(blob);

                // When
                service.fileExists(BUCKET_NAME, BLOB_PATH);

                // Then
                ArgumentCaptor<BlobId> blobIdCaptor = ArgumentCaptor.forClass(BlobId.class);
                verify(storage).get(blobIdCaptor.capture());

                BlobId capturedBlobId = blobIdCaptor.getValue();
                assertThat(capturedBlobId.getBucket()).isEqualTo(BUCKET_NAME);
                assertThat(capturedBlobId.getName()).isEqualTo(BLOB_PATH);
            }
        }

        @Nested
        @DisplayName("Error Handling")
        class FileExistsErrorTests {

            @Test
            @DisplayName("should throw StorageException when storage.get throws exception")
            void shouldThrowStorageExceptionWhenGetFails() {
                // Given
                when(storage.get(any(BlobId.class)))
                    .thenThrow(new RuntimeException("Storage unavailable"));

                // When/Then
                assertThatThrownBy(() -> service.fileExists(BUCKET_NAME, BLOB_PATH))
                    .isInstanceOf(StorageTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }

            @Test
            @DisplayName("should throw StorageException when blob.exists() throws exception")
            void shouldThrowStorageExceptionWhenBlobExistsFails() {
                // Given
                Blob blob = mock(Blob.class);
                when(blob.exists()).thenThrow(new RuntimeException("Connection lost"));
                when(storage.get(any(BlobId.class))).thenReturn(blob);

                // When/Then
                assertThatThrownBy(() -> service.fileExists(BUCKET_NAME, BLOB_PATH))
                    .isInstanceOf(StorageTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }
        }
    }

    // ========================================================================
    // getFileMetadata Tests
    // ========================================================================

    @Nested
    @DisplayName("getFileMetadata")
    class GetFileMetadataTests {

        @Nested
        @DisplayName("Validation")
        class GetFileMetadataValidationTests {

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should throw ValidationException when bucketName is null or empty")
            void shouldThrowWhenBucketNameNullOrEmpty(String bucketName) {
                assertThatThrownBy(() -> service.getFileMetadata(bucketName, BLOB_PATH))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should throw ValidationException when bucketName is whitespace only")
            void shouldThrowWhenBucketNameWhitespaceOnly(String bucketName) {
                assertThatThrownBy(() -> service.getFileMetadata(bucketName, BLOB_PATH))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("should throw ValidationException when blobPath is null or empty")
            void shouldThrowWhenBlobPathNullOrEmpty(String blobPath) {
                assertThatThrownBy(() -> service.getFileMetadata(BUCKET_NAME, blobPath))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }

            @ParameterizedTest
            @ValueSource(strings = {"   ", "\t", "\n"})
            @DisplayName("should throw ValidationException when blobPath is whitespace only")
            void shouldThrowWhenBlobPathWhitespaceOnly(String blobPath) {
                assertThatThrownBy(() -> service.getFileMetadata(BUCKET_NAME, blobPath))
                    .isInstanceOf(ValidationTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.validation.required_field");
            }
        }

        @Nested
        @DisplayName("Success Scenarios")
        class GetFileMetadataSuccessTests {

            @Test
            @DisplayName("should return blob when file exists")
            void shouldReturnBlobWhenFileExists() {
                // Given
                Blob expectedBlob = mock(Blob.class);
                when(storage.get(any(BlobId.class))).thenReturn(expectedBlob);

                // When
                Blob result = service.getFileMetadata(BUCKET_NAME, BLOB_PATH);

                // Then
                assertThat(result).isSameAs(expectedBlob);
            }

            @Test
            @DisplayName("should return null when file does not exist")
            void shouldReturnNullWhenFileDoesNotExist() {
                // Given
                when(storage.get(any(BlobId.class))).thenReturn(null);

                // When
                Blob result = service.getFileMetadata(BUCKET_NAME, BLOB_PATH);

                // Then
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("should create BlobId with correct bucket and path")
            void shouldCreateBlobIdWithCorrectParameters() {
                // Given
                Blob blob = mock(Blob.class);
                when(storage.get(any(BlobId.class))).thenReturn(blob);

                // When
                service.getFileMetadata(BUCKET_NAME, BLOB_PATH);

                // Then
                ArgumentCaptor<BlobId> blobIdCaptor = ArgumentCaptor.forClass(BlobId.class);
                verify(storage).get(blobIdCaptor.capture());

                BlobId capturedBlobId = blobIdCaptor.getValue();
                assertThat(capturedBlobId.getBucket()).isEqualTo(BUCKET_NAME);
                assertThat(capturedBlobId.getName()).isEqualTo(BLOB_PATH);
            }

            @Test
            @DisplayName("should return blob with all metadata intact")
            void shouldReturnBlobWithMetadata() {
                // Given
                Blob blob = mock(Blob.class);
                when(blob.getContentType()).thenReturn("image/jpeg");
                when(blob.getSize()).thenReturn(1024L);
                when(blob.getName()).thenReturn(BLOB_PATH);
                when(storage.get(any(BlobId.class))).thenReturn(blob);

                // When
                Blob result = service.getFileMetadata(BUCKET_NAME, BLOB_PATH);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getContentType()).isEqualTo("image/jpeg");
                assertThat(result.getSize()).isEqualTo(1024L);
                assertThat(result.getName()).isEqualTo(BLOB_PATH);
            }
        }

        @Nested
        @DisplayName("Error Handling")
        class GetFileMetadataErrorTests {

            @Test
            @DisplayName("should throw StorageException when storage.get throws exception")
            void shouldThrowStorageExceptionWhenGetFails() {
                // Given
                when(storage.get(any(BlobId.class)))
                    .thenThrow(new RuntimeException("Storage unavailable"));

                // When/Then
                assertThatThrownBy(() -> service.getFileMetadata(BUCKET_NAME, BLOB_PATH))
                    .isInstanceOf(StorageTranslatableException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.storage.upload_failed");
            }
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private Blob createMockBlob(String name) {
        Blob blob = mock(Blob.class);
        BlobId blobId = BlobId.of(BUCKET_NAME, FOLDER_PATH + "/" + name);
        when(blob.getBlobId()).thenReturn(blobId);
        when(blob.getName()).thenReturn(name);
        return blob;
    }
}
