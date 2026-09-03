package com.sm.instagram.platform.storage.service;

import com.sm.instagram.platform.storage.entity.FileUpload;
import com.sm.instagram.platform.storage.repository.FileUploadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class FileTrackingService {

    public static final String DESCRIPTION = "description";
    public static final String ALT_TEXT = "altText";
    private final FileUploadRepository uploadRepository;
    private final StorageRateLimitService rateLimiterService;

    @Autowired
    public FileTrackingService(FileUploadRepository uploadRepository,
                               @Autowired(required = false) StorageRateLimitService rateLimiterService) {
        this.uploadRepository = uploadRepository;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Records initial upload request when signed URL is generated.
     */
    public FileUpload recordUploadRequest(String userId, String filePath,
                                          String filename, String contentType,
                                          Long estimatedSize) {
        log.info("GDPR: Service=recordUploadRequest, Operation=RECORD_UPLOAD, FirebaseUID={}, FileName={}, ContentType={}, Purpose=upload_tracking", 
            userId, filename, contentType);
        
        FileUpload upload = new FileUpload(userId, filePath, filename,
                contentType, estimatedSize);
        upload.setStatus(FileUpload.UploadStatus.PENDING);

        return uploadRepository.save(upload);
    }

    /**
     * Looks up a tracked upload by its id (the {@code uploadId} handed to the
     * client by {@code POST /upload/signed-url}). Read-only accessor for the
     * BE-minted-URL round trip — see {@code SignedUrlService.resolveOwnedUpload}.
     */
    @Transactional(readOnly = true)
    public Optional<FileUpload> getUpload(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            return Optional.empty();
        }
        return uploadRepository.findById(uploadId.trim());
    }

    /**
     * Confirms upload via direct API call from frontend.
     */
    public void confirmUploadViaApi(String filePath, Long actualSize) {
        log.info("GDPR: Service=confirmUploadViaApi, Operation=CONFIRM_UPLOAD, FilePath={}, Size={}, Purpose=upload_confirmation", 
            filePath, actualSize);
        
        Optional<FileUpload> uploadOpt = uploadRepository.findByFilePath(filePath);

        if (uploadOpt.isPresent()) {
            FileUpload upload = uploadOpt.get();
            upload.setStatus(FileUpload.UploadStatus.CONFIRMED);
            upload.setConfirmedAt(Instant.now());

            // Update size if different
            if (!upload.getFileSize().equals(actualSize)) {
                log.info("GDPR: Service=updateFileSize, Operation=SIZE_UPDATE, FirebaseUID={}, FilePath={}, OldSize={}, NewSize={}, Purpose=storage_quota",
                        upload.getUserId(), filePath, upload.getFileSize(), actualSize);

                // Adjust storage quota if service is available
                if (rateLimiterService != null) {
                    long difference = actualSize - upload.getFileSize();
                    rateLimiterService.updateUserStorage(upload.getUserId(), difference);
                } else {
                    log.warn("StorageRateLimitService not available - cannot update storage quota");
                }

                upload.setFileSize(actualSize);
            }

            uploadRepository.save(upload);
        }
    }

    /**
     * Confirms upload via Firebase webhook.
     * This is more reliable as it comes directly from Firebase.
     */
    public void confirmUploadViaWebhook(String filePath, String userId,
                                        Long size, String contentType,
                                        Map<String, String> metadata) {
        Optional<FileUpload> uploadOpt = uploadRepository.findByFilePath(filePath);

        if (uploadOpt.isPresent()) {
            // Update existing record
            FileUpload upload = uploadOpt.get();
            upload.setStatus(FileUpload.UploadStatus.WEBHOOK);
            upload.setConfirmedAt(Instant.now());
            upload.setFileSize(size);
            uploadRepository.save(upload);

        } else {
            // Create new record (in case API confirmation was missed)
            FileUpload upload = new FileUpload(userId, filePath,
                    extractFilename(filePath),
                    contentType, size);
            upload.setStatus(FileUpload.UploadStatus.WEBHOOK);
            upload.setConfirmedAt(Instant.now());

            // Set metadata if available
            if (metadata != null) {
                upload.setDescription(metadata.get(DESCRIPTION));
                upload.setAltText(metadata.get(ALT_TEXT));
            }

            uploadRepository.save(upload);

            // Update user storage if service is available
            if (rateLimiterService != null) {
                rateLimiterService.updateUserStorage(userId, size);
            } else {
                log.warn("StorageRateLimitService not available - cannot update user storage");
            }
        }
    }

    /**
     * Marks a file as deleted.
     */
    public void markFileAsDeleted(String filePath) {
        log.warn("GDPR: DELETION Service=markFileAsDeleted, Operation=MARK_DELETED, FilePath={}, Purpose=file_deletion", filePath);
        
        Optional<FileUpload> uploadOpt = uploadRepository.findByFilePath(filePath);

        if (uploadOpt.isPresent()) {
            FileUpload upload = uploadOpt.get();
            upload.setStatus(FileUpload.UploadStatus.DELETED);
            uploadRepository.save(upload);
            
            log.info("GDPR: DELETION_COMPLETE FirebaseUID={}, FilePath={}, Status=deleted", 
                upload.getUserId(), filePath);

            // Reduce user's storage quota if service is available
            if (rateLimiterService != null) {
                rateLimiterService.updateUserStorage(
                        upload.getUserId(),
                        -upload.getFileSize()
                );
            } else {
            log.warn("StorageRateLimitService not available - cannot update storage quota for deletion");
            }
        }
    }

    /**
     * Updates file metadata.
     */
    public void updateFileMetadata(String filePath, Map<String, String> metadata) {
        Optional<FileUpload> uploadOpt = uploadRepository.findByFilePath(filePath);

        if (uploadOpt.isPresent() && metadata != null) {
            FileUpload upload = uploadOpt.get();

            if (metadata.containsKey(DESCRIPTION)) {
                upload.setDescription(metadata.get(DESCRIPTION));
            }
            if (metadata.containsKey(ALT_TEXT)) {
                upload.setAltText(metadata.get(ALT_TEXT));
            }

            uploadRepository.save(upload);
        }
    }

    /**
     * Gets user's upload statistics.
     */
    public UserUploadStats getUserStats(String userId) {
        List<FileUpload> uploads = uploadRepository.findByUserIdAndStatusIn(
                userId,
                List.of(FileUpload.UploadStatus.CONFIRMED, FileUpload.UploadStatus.WEBHOOK)
        );

        long totalSize = uploads.stream()
                .mapToLong(FileUpload::getFileSize)
                .sum();

        long uploadedToday = uploads.stream()
                .filter(u -> u.getUploadTime().isAfter(Instant.now().minus(1, ChronoUnit.DAYS)))
                .count();

        long uploadedThisHour = uploads.stream()
                .filter(u -> u.getUploadTime().isAfter(Instant.now().minus(1, ChronoUnit.HOURS)))
                .count();

        return new UserUploadStats(
                uploads.size(),
                totalSize,
                uploadedToday,
                uploadedThisHour
        );
    }

    /**
     * Scheduled task to clean up orphaned uploads.
     * Runs every hour to mark old pending uploads as failed.
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    public void cleanupOrphanedUploads() {
        log.info("GDPR: Service=cleanupOrphanedUploads, Operation=CLEANUP, Purpose=maintenance");

        // Find uploads that have been pending for more than 1 hour
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        List<FileUpload> orphaned = uploadRepository.findByStatusAndCreatedAtBefore(
                FileUpload.UploadStatus.PENDING,
                cutoff
        );

        orphaned.forEach(upload -> {
            upload.setStatus(FileUpload.UploadStatus.FAILED);
            log.info("GDPR: Service=cleanupOrphanedUploads, Operation=MARK_FAILED, FirebaseUID={}, FilePath={}, Reason=timeout, Purpose=cleanup", 
                upload.getUserId(), upload.getFilePath());
        });

        uploadRepository.saveAll(orphaned);
        log.info("GDPR: Service=cleanupOrphanedUploads_complete, CleanedCount={}, Purpose=maintenance", orphaned.size());
    }

    /**
     * Extracts filename from full path.
     */
    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    // Inner class for statistics
    public static class UserUploadStats {
        private final long totalFiles;
        private final long totalSize;
        private final long filesUploadedToday;
        private final long filesUploadedThisHour;

        public UserUploadStats(long totalFiles, long totalSize,
                               long filesUploadedToday, long filesUploadedThisHour) {
            this.totalFiles = totalFiles;
            this.totalSize = totalSize;
            this.filesUploadedToday = filesUploadedToday;
            this.filesUploadedThisHour = filesUploadedThisHour;
        }

        // Getters
        public long getTotalFiles() {
            return totalFiles;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public long getFilesUploadedToday() {
            return filesUploadedToday;
        }

        public long getFilesUploadedThisHour() {
            return filesUploadedThisHour;
        }
    }
}
