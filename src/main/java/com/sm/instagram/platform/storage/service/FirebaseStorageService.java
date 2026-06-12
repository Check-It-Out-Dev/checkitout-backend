package com.sm.instagram.platform.storage.service;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for Firebase Storage operations using Admin SDK.
 */
@Slf4j
@Service
public class FirebaseStorageService {

    private final Storage storage;
    private final String bucketName;

    public FirebaseStorageService(
            Storage storage,
            @Value("${gcp.bucket-name:check-it-out-47c50.firebasestorage.app}") String bucketName) {
        this.storage = storage;
        this.bucketName = bucketName;
    }

    // ========== User Directory Operations (for Cascade Delete) ==========

    /**
     * Delete all files for a user.
     * Files are stored under users/{firebaseUid}/ path.
     *
     * @param firebaseUid User's Firebase UID
     * @return Number of files deleted
     */
    public int deleteUserDirectory(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            log.warn("Cannot delete user directory: firebaseUid is null or empty");
            return 0;
        }

        String userPath = "users/" + firebaseUid + "/";
        log.warn("GDPR: CASCADE_DELETE Service=deleteUserDirectory, Operation=USER_FILES_DELETE, " +
                "FirebaseUid={}, Path={}, Purpose=cascade_delete", firebaseUid, userPath);

        return deleteFolder(bucketName, userPath);
    }

    /**
     * Count files in a user's directory.
     * Used for cascade delete preview.
     *
     * @param firebaseUid User's Firebase UID
     * @return Number of files
     */
    public int countUserFiles(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            return 0;
        }

        String userPath = "users/" + firebaseUid + "/";
        try {
            Page<Blob> blobs = storage.list(bucketName,
                    Storage.BlobListOption.prefix(userPath));

            int count = 0;
            for (Blob blob : blobs.iterateAll()) {
                count++;
            }
            return count;
        } catch (Exception e) {
            log.error("Failed to count user files for: {}", firebaseUid, e);
            return 0;
        }
    }

    /**
     * Check if a user has any files in storage.
     *
     * @param firebaseUid User's Firebase UID
     * @return true if user has files, false otherwise
     */
    public boolean userDirectoryExists(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            return false;
        }

        String userPath = "users/" + firebaseUid + "/";
        try {
            Page<Blob> blobs = storage.list(bucketName,
                    Storage.BlobListOption.prefix(userPath),
                    Storage.BlobListOption.pageSize(1));

            return blobs.getValues().iterator().hasNext();
        } catch (Exception e) {
            log.error("Failed to check user directory existence for: {}", firebaseUid, e);
            return false;
        }
    }

    // ========== Archive Operations (for Cascade Delete) ==========

    /**
     * Upload archive data to GCS for GDPR retention.
     *
     * @param archivePath Path in the archive bucket (e.g., archives/gdpr-deletion/2026/01/uuid.json)
     * @param content Archive content as JSON bytes
     * @return Full GCS URL of the archived file
     */
    public String uploadArchive(String archivePath, byte[] content) {
        if (archivePath == null || archivePath.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "archivePath");
        }

        try {
            BlobId blobId = BlobId.of(bucketName, archivePath);
            Blob blob = storage.create(
                    com.google.cloud.storage.BlobInfo.newBuilder(blobId)
                            .setContentType("application/json")
                            .build(),
                    content);

            String archiveUrl = "gs://" + bucketName + "/" + archivePath;
            log.info("GDPR: ARCHIVE_CREATED Path={}, Size={} bytes", archiveUrl, content.length);
            return archiveUrl;
        } catch (Exception e) {
            log.error("Failed to upload archive: {}", archivePath, e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    // ========== General File/Folder Operations ==========

    /**
     * Delete all files in a folder.
     *
     * @param bucketName Name of the storage bucket
     * @param folderPath Path to the folder
     * @return Number of files deleted
     */
    public int deleteFolder(String bucketName, String folderPath) {
        log.warn("GDPR: DELETION Service=deleteFolder, Operation=BULK_DELETE, FolderPath={}, Purpose=storage_management", folderPath);

        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "bucketName");
        }

        if (folderPath == null || folderPath.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "folderPath");
        }

        try {
            // Ensure folder path ends with /
            if (!folderPath.endsWith("/")) {
                folderPath = folderPath + "/";
            }

            // List all blobs with the folder prefix
            Page<Blob> blobs = storage.list(bucketName,
                    Storage.BlobListOption.prefix(folderPath));

            List<BlobId> blobIds = new ArrayList<>();
            int count = 0;

            // Collect all blob IDs
            for (Blob blob : blobs.iterateAll()) {
                blobIds.add(blob.getBlobId());
                count++;

                // Batch delete in groups of 100
                if (blobIds.size() >= 100) {
                    List<Boolean> results = storage.delete(blobIds);
                    logDeletionResults(results, blobIds);
                    blobIds.clear();
                }
            }

            // Delete remaining blobs
            if (!blobIds.isEmpty()) {
                List<Boolean> results = storage.delete(blobIds);
                logDeletionResults(results, blobIds);
            }

            log.info("GDPR: DELETION_COMPLETE Operation=deleteFolder, FolderPath={}, FilesDeleted={}, Purpose=storage_management", folderPath, count);
            return count;

        } catch (Exception e) {
            log.error("Failed to delete folder: {}", folderPath, e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Delete a single file.
     *
     * @param bucketName Name of the storage bucket
     * @param blobPath   Path to the file
     * @return true if deleted, false if not found
     */
    public boolean deleteFile(String bucketName, String blobPath) {
        log.warn("GDPR: DELETION Service=deleteFile, Operation=DELETE, FilePath={}, Purpose=storage_management", blobPath);

        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "bucketName");
        }

        if (blobPath == null || blobPath.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "blobPath");
        }

        try {
            BlobId blobId = BlobId.of(bucketName, blobPath);
            boolean deleted = storage.delete(blobId);

            if (deleted) {
                log.info("GDPR: DELETION_COMPLETE FilePath={}, Status=deleted", blobPath);
            } else {
                log.warn("File not found for deletion: {}", blobPath);
            }

            return deleted;

        } catch (Exception e) {
            log.error("Failed to delete file: {}", blobPath, e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Check if a file exists.
     *
     * @param bucketName Name of the storage bucket
     * @param blobPath   Path to the file
     * @return true if exists, false otherwise
     */
    public boolean fileExists(String bucketName, String blobPath) {
        log.debug("GDPR: Service=fileExists, Operation=CHECK_EXISTS, FilePath={}, Purpose=validation", blobPath);

        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "bucketName");
        }

        if (blobPath == null || blobPath.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "blobPath");
        }

        try {
            BlobId blobId = BlobId.of(bucketName, blobPath);
            Blob blob = storage.get(blobId);
            return blob != null && blob.exists();
        } catch (Exception e) {
            log.error("Failed to check file existence: {}", blobPath, e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Get file metadata.
     *
     * @param bucketName Name of the storage bucket
     * @param blobPath   Path to the file
     * @return Blob object with metadata, or null if not found
     */
    public Blob getFileMetadata(String bucketName, String blobPath) {
        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "bucketName");
        }

        if (blobPath == null || blobPath.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "blobPath");
        }

        try {
            BlobId blobId = BlobId.of(bucketName, blobPath);
            return storage.get(blobId);
        } catch (Exception e) {
            log.error("Failed to get file metadata: {}", blobPath, e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Log deletion results for batch operations.
     */
    private void logDeletionResults(List<Boolean> results, List<BlobId> blobIds) {
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i)) {
                log.warn("Failed to delete blob: {}", blobIds.get(i).getName());
            }
        }
    }
}
