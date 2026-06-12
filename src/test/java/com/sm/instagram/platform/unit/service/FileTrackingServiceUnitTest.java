package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.storage.entity.FileUpload;
import com.sm.instagram.platform.storage.repository.FileUploadRepository;
import com.sm.instagram.platform.storage.service.FileTrackingService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileTrackingService Unit Tests")
class FileTrackingServiceUnitTest {

    @Mock
    private FileUploadRepository uploadRepository;

    @Mock
    private StorageRateLimitService rateLimiterService;

    @InjectMocks
    private FileTrackingService service;

    private static final String USER_ID = "firebase-uid-123";
    private static final String FILE_PATH = "content/user123/image.jpg";
    private static final String FILENAME = "image.jpg";
    private static final String CONTENT_TYPE = "image/jpeg";
    private static final Long FILE_SIZE = 1024L;

    @Nested
    @DisplayName("recordUploadRequest")
    class RecordUploadRequestTests {

        @Test
        @DisplayName("should create new FileUpload with PENDING status")
        void shouldCreateNewFileUpload() {
            // Given
            when(uploadRepository.save(any(FileUpload.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // When
            FileUpload result = service.recordUploadRequest(
                USER_ID, FILE_PATH, FILENAME, CONTENT_TYPE, FILE_SIZE
            );

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getFilePath()).isEqualTo(FILE_PATH);
            assertThat(result.getFilename()).isEqualTo(FILENAME);
            assertThat(result.getContentType()).isEqualTo(CONTENT_TYPE);
            assertThat(result.getFileSize()).isEqualTo(FILE_SIZE);
            assertThat(result.getStatus()).isEqualTo(FileUpload.UploadStatus.PENDING);
        }

        @Test
        @DisplayName("should save the file upload to repository")
        void shouldSaveToRepository() {
            // Given
            ArgumentCaptor<FileUpload> captor = ArgumentCaptor.forClass(FileUpload.class);
            when(uploadRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.recordUploadRequest(USER_ID, FILE_PATH, FILENAME, CONTENT_TYPE, FILE_SIZE);

            // Then
            verify(uploadRepository).save(any(FileUpload.class));
            FileUpload saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(FileUpload.UploadStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("confirmUploadViaApi")
    class ConfirmUploadViaApiTests {

        @Test
        @DisplayName("should confirm upload when file exists")
        void shouldConfirmUploadWhenExists() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            existingUpload.setFileSize(500L);
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.confirmUploadViaApi(FILE_PATH, 1024L);

            // Then
            verify(uploadRepository).save(argThat(upload ->
                upload.getStatus() == FileUpload.UploadStatus.CONFIRMED &&
                upload.getConfirmedAt() != null &&
                upload.getFileSize().equals(1024L)
            ));
        }

        @Test
        @DisplayName("should update storage quota when size differs")
        void shouldUpdateStorageQuotaWhenSizeDiffers() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            existingUpload.setFileSize(500L);
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.confirmUploadViaApi(FILE_PATH, 1024L);

            // Then
            verify(rateLimiterService).updateUserStorage(USER_ID, 524L); // 1024 - 500
        }

        @Test
        @DisplayName("should not update storage quota when size is same")
        void shouldNotUpdateStorageQuotaWhenSizeIsSame() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            existingUpload.setFileSize(1024L);
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.confirmUploadViaApi(FILE_PATH, 1024L);

            // Then
            verify(rateLimiterService, never()).updateUserStorage(anyString(), anyLong());
        }

        @Test
        @DisplayName("should do nothing when file not found")
        void shouldDoNothingWhenFileNotFound() {
            // Given
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.empty());

            // When
            service.confirmUploadViaApi(FILE_PATH, 1024L);

            // Then
            verify(uploadRepository, never()).save(any());
            verify(rateLimiterService, never()).updateUserStorage(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("confirmUploadViaWebhook")
    class ConfirmUploadViaWebhookTests {

        @Test
        @DisplayName("should update existing upload via webhook")
        void shouldUpdateExistingUpload() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.confirmUploadViaWebhook(FILE_PATH, USER_ID, FILE_SIZE, CONTENT_TYPE, null);

            // Then
            verify(uploadRepository).save(argThat(upload ->
                upload.getStatus() == FileUpload.UploadStatus.WEBHOOK &&
                upload.getConfirmedAt() != null
            ));
        }

        @Test
        @DisplayName("should create new upload if not exists")
        void shouldCreateNewUploadIfNotExists() {
            // Given
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.empty());
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.confirmUploadViaWebhook(FILE_PATH, USER_ID, FILE_SIZE, CONTENT_TYPE, null);

            // Then
            verify(uploadRepository).save(argThat(upload ->
                upload.getStatus() == FileUpload.UploadStatus.WEBHOOK &&
                upload.getUserId().equals(USER_ID)
            ));
        }

        @Test
        @DisplayName("should set metadata from webhook")
        void shouldSetMetadataFromWebhook() {
            // Given
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.empty());
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));
            Map<String, String> metadata = Map.of(
                "description", "Test description",
                "altText", "Alt text"
            );

            // When
            service.confirmUploadViaWebhook(FILE_PATH, USER_ID, FILE_SIZE, CONTENT_TYPE, metadata);

            // Then
            verify(uploadRepository).save(argThat(upload ->
                "Test description".equals(upload.getDescription()) &&
                "Alt text".equals(upload.getAltText())
            ));
        }

        @Test
        @DisplayName("should update user storage for new webhook upload")
        void shouldUpdateUserStorageForNewWebhookUpload() {
            // Given
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.empty());
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.confirmUploadViaWebhook(FILE_PATH, USER_ID, FILE_SIZE, CONTENT_TYPE, null);

            // Then
            verify(rateLimiterService).updateUserStorage(USER_ID, FILE_SIZE);
        }
    }

    @Nested
    @DisplayName("markFileAsDeleted")
    class MarkFileAsDeletedTests {

        @Test
        @DisplayName("should mark file as deleted")
        void shouldMarkFileAsDeleted() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.markFileAsDeleted(FILE_PATH);

            // Then
            verify(uploadRepository).save(argThat(upload ->
                upload.getStatus() == FileUpload.UploadStatus.DELETED
            ));
        }

        @Test
        @DisplayName("should reduce storage quota on deletion")
        void shouldReduceStorageQuotaOnDeletion() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            existingUpload.setFileSize(2048L);
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.markFileAsDeleted(FILE_PATH);

            // Then
            verify(rateLimiterService).updateUserStorage(USER_ID, -2048L);
        }

        @Test
        @DisplayName("should do nothing when file not found")
        void shouldDoNothingWhenFileNotFound() {
            // Given
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.empty());

            // When
            service.markFileAsDeleted(FILE_PATH);

            // Then
            verify(uploadRepository, never()).save(any());
            verify(rateLimiterService, never()).updateUserStorage(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("updateFileMetadata")
    class UpdateFileMetadataTests {

        @Test
        @DisplayName("should update description and altText")
        void shouldUpdateDescriptionAndAltText() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));
            Map<String, String> metadata = Map.of(
                "description", "New description",
                "altText", "New alt text"
            );

            // When
            service.updateFileMetadata(FILE_PATH, metadata);

            // Then
            verify(uploadRepository).save(argThat(upload ->
                "New description".equals(upload.getDescription()) &&
                "New alt text".equals(upload.getAltText())
            ));
        }

        @Test
        @DisplayName("should update only description when altText not provided")
        void shouldUpdateOnlyDescription() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            existingUpload.setAltText("Original alt");
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));
            Map<String, String> metadata = Map.of("description", "New description");

            // When
            service.updateFileMetadata(FILE_PATH, metadata);

            // Then
            verify(uploadRepository).save(argThat(upload ->
                "New description".equals(upload.getDescription()) &&
                "Original alt".equals(upload.getAltText())
            ));
        }

        @Test
        @DisplayName("should do nothing when file not found")
        void shouldDoNothingWhenFileNotFound() {
            // Given
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.empty());

            // When
            service.updateFileMetadata(FILE_PATH, Map.of("description", "Test"));

            // Then
            verify(uploadRepository, never()).save(any());
        }

        @Test
        @DisplayName("should do nothing when metadata is null")
        void shouldDoNothingWhenMetadataIsNull() {
            // Given
            FileUpload existingUpload = createTestFileUpload();
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));

            // When
            service.updateFileMetadata(FILE_PATH, null);

            // Then
            verify(uploadRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getUserStats")
    class GetUserStatsTests {

        @Test
        @DisplayName("should calculate correct statistics")
        void shouldCalculateCorrectStatistics() {
            // Given
            Instant now = Instant.now();
            List<FileUpload> uploads = List.of(
                createTestFileUploadWithSize(100L, now.minus(30, ChronoUnit.MINUTES)),  // last hour, today
                createTestFileUploadWithSize(200L, now.minus(2, ChronoUnit.HOURS)),     // today
                createTestFileUploadWithSize(300L, now.minus(12, ChronoUnit.HOURS)),    // today
                createTestFileUploadWithSize(400L, now.minus(2, ChronoUnit.DAYS))       // not today
            );
            when(uploadRepository.findByUserIdAndStatusIn(
                eq(USER_ID),
                eq(List.of(FileUpload.UploadStatus.CONFIRMED, FileUpload.UploadStatus.WEBHOOK))
            )).thenReturn(uploads);

            // When
            FileTrackingService.UserUploadStats stats = service.getUserStats(USER_ID);

            // Then
            assertThat(stats.getTotalFiles()).isEqualTo(4);
            assertThat(stats.getTotalSize()).isEqualTo(1000L);
            assertThat(stats.getFilesUploadedToday()).isEqualTo(3); // Files within 24 hours
            assertThat(stats.getFilesUploadedThisHour()).isEqualTo(1); // Files within 1 hour
        }

        @Test
        @DisplayName("should return zero stats for no uploads")
        void shouldReturnZeroStatsForNoUploads() {
            // Given
            when(uploadRepository.findByUserIdAndStatusIn(
                eq(USER_ID),
                eq(List.of(FileUpload.UploadStatus.CONFIRMED, FileUpload.UploadStatus.WEBHOOK))
            )).thenReturn(List.of());

            // When
            FileTrackingService.UserUploadStats stats = service.getUserStats(USER_ID);

            // Then
            assertThat(stats.getTotalFiles()).isZero();
            assertThat(stats.getTotalSize()).isZero();
            assertThat(stats.getFilesUploadedToday()).isZero();
            assertThat(stats.getFilesUploadedThisHour()).isZero();
        }
    }

    @Nested
    @DisplayName("cleanupOrphanedUploads")
    class CleanupOrphanedUploadsTests {

        @Test
        @DisplayName("should mark orphaned uploads as failed")
        void shouldMarkOrphanedUploadsAsFailed() {
            // Given
            FileUpload orphaned1 = createTestFileUpload();
            FileUpload orphaned2 = createTestFileUpload();
            List<FileUpload> orphanedUploads = List.of(orphaned1, orphaned2);

            when(uploadRepository.findByStatusAndCreatedAtBefore(
                eq(FileUpload.UploadStatus.PENDING),
                any(Instant.class)
            )).thenReturn(orphanedUploads);

            // When
            service.cleanupOrphanedUploads();

            // Then
            assertThat(orphaned1.getStatus()).isEqualTo(FileUpload.UploadStatus.FAILED);
            assertThat(orphaned2.getStatus()).isEqualTo(FileUpload.UploadStatus.FAILED);
            verify(uploadRepository).saveAll(orphanedUploads);
        }

        @Test
        @DisplayName("should handle empty orphaned list")
        void shouldHandleEmptyOrphanedList() {
            // Given
            when(uploadRepository.findByStatusAndCreatedAtBefore(
                eq(FileUpload.UploadStatus.PENDING),
                any(Instant.class)
            )).thenReturn(List.of());

            // When
            service.cleanupOrphanedUploads();

            // Then
            verify(uploadRepository).saveAll(List.of());
        }
    }

    @Nested
    @DisplayName("Service without rate limiter")
    class ServiceWithoutRateLimiterTests {

        @Test
        @DisplayName("should work without rate limiter service")
        void shouldWorkWithoutRateLimiterService() {
            // Given - create service without rate limiter
            FileTrackingService serviceWithoutRateLimiter =
                new FileTrackingService(uploadRepository, null);

            FileUpload existingUpload = createTestFileUpload();
            existingUpload.setFileSize(500L);
            when(uploadRepository.findByFilePath(FILE_PATH)).thenReturn(Optional.of(existingUpload));
            when(uploadRepository.save(any(FileUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When - should not throw exception
            serviceWithoutRateLimiter.confirmUploadViaApi(FILE_PATH, 1024L);

            // Then
            verify(uploadRepository).save(any(FileUpload.class));
            // rateLimiterService should NOT be called since it's null
        }
    }

    // Helper methods

    private FileUpload createTestFileUpload() {
        FileUpload upload = new FileUpload(USER_ID, FILE_PATH, FILENAME, CONTENT_TYPE, FILE_SIZE);
        upload.setStatus(FileUpload.UploadStatus.PENDING);
        return upload;
    }

    private FileUpload createTestFileUploadWithSize(Long size, Instant uploadTime) {
        FileUpload upload = new FileUpload(USER_ID, FILE_PATH + size, FILENAME, CONTENT_TYPE, size);
        upload.setStatus(FileUpload.UploadStatus.CONFIRMED);
        // Use reflection to set uploadTime since constructor sets it to now
        try {
            java.lang.reflect.Field field = FileUpload.class.getDeclaredField("uploadTime");
            field.setAccessible(true);
            field.set(upload, uploadTime);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return upload;
    }
}
