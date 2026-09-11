package com.sm.instagram.platform.common.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.paging.Page;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.storage.*;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import com.sm.instagram.platform.common.util.Interrupts;

/**
 * Service for managing GeoIP database storage in Firebase Storage.
 * Provides secure upload and download of MaxMind GeoLite2 database files.
 * <p>
 * All application nodes have admin service accounts, but this service ensures
 * the GeoIP data is protected from external 3rd parties without MaxMind license.
 */
@Service
@Slf4j
public class GeoIpStorageService {

    private static final String LOCK_COLLECTION = "geoip_locks";
    private static final String LOCK_DOCUMENT = "maxmind_download";
    private static final long LOCK_TIMEOUT_MS = 300000; // 5 minutes
    private final Storage storage;
    private final Firestore firestore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${geoip.storage.bucket:check-it-out-47c50-geoip-private}")
    private String bucketName;
    @Value("${geoip.storage.enabled:true}")
    private boolean storageEnabled;
    @Value("${geoip.storage.max-versions:3}")
    private int maxVersionsToKeep;
    @Value("${geoip.storage.metadata-prefix:geoip-metadata}")
    private String metadataPrefix;
    /**
     * The same property MaxMindDatabaseService reads: both write the same database, so both should
     * work beside it rather than in the shared system temp directory (java:S5443). The directory is
     * shared even though the NIO call creates the file owner-only, and a database another local
     * account can watch appear -- or swap between the download and the read -- is not worth leaving
     * to the platform. Requesting POSIX permissions instead would throw on Windows, where this is
     * developed.
     */
    @Value("${maxmind.database.path:./geoip/GeoLite2-City.mmdb}")
    private String databasePath;

    @Value("${geoip.storage.database-prefix:geoip}")
    private String databasePrefix;
    @Value("${geoip.storage.max-age-days:7}")
    private int maxAgeDays;
    public GeoIpStorageService(@Qualifier("geoIpStorage") Storage storage, Firestore firestore) {
        this.storage = storage;
        this.firestore = firestore;
    }

    @PostConstruct
    public void init() {
        if (!storageEnabled) {
            log.info("GeoIP Storage is disabled");
            return;
        }

        // Auto-create bucket if it doesn't exist
        try {
            Bucket bucket = storage.get(bucketName);
            if (bucket == null || !bucket.exists()) {
                log.info("Creating GeoIP storage bucket: {}", bucketName);

                BucketInfo bucketInfo = BucketInfo.newBuilder(bucketName)
                        .setLocation("europe-central2")
                        .setStorageClass(StorageClass.STANDARD)
                        .build();

                bucket = storage.create(bucketInfo);
                log.info("Successfully created bucket: {}", bucketName);

                // Set lifecycle rule
                setLifecyclePolicy();
            } else {
                log.info("GeoIP storage bucket exists: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize storage bucket", e);
        }
    }

    private void setLifecyclePolicy() {
        try {
            // Delete files older than 90 days
            LifecycleRule rule = new LifecycleRule(
                    LifecycleAction.newDeleteAction(),
                    LifecycleCondition.newBuilder()
                            .setAge(90)
                            .setMatchesPrefix(Collections.singletonList(databasePrefix + "/GeoLite2-City-"))
                            .build()
            );

            BucketInfo bucketInfo = BucketInfo.newBuilder(bucketName)
                    .setLifecycleRules(Collections.singletonList(rule))
                    .build();

            storage.update(bucketInfo);
            log.info("Lifecycle policy set: delete files older than 90 days");
        } catch (Exception e) {
            log.error("Failed to set lifecycle policy", e);
        }
    }

    /**
     * Upload GeoIP database to Firebase Storage.
     * Only admin instances should call this after downloading from MaxMind.
     *
     * @param localFile Path to the local GeoLite2 database file
     * @return CompletableFuture with the uploaded file name
     */
    public CompletableFuture<String> uploadDatabase(Path localFile) {
        // GDPR: Log database upload operation (no Firebase UID available in service layer)
        log.info("GDPR: Operation=uploadGeoIpDatabase, Purpose=database_distribution, DataAccessed=geoip.database, LegalBasis=legitimate_interest");

        if (!storageEnabled) {
            log.info("GeoIP Storage is disabled");
            return CompletableFuture.completedFuture("Storage disabled");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate filename with date
                String fileName = String.format("%s/GeoLite2-City-%s.mmdb",
                        databasePrefix,
                        LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                );

                log.info("Uploading GeoIP database to Firebase Storage: {}", fileName);

                // Create blob with metadata
                BlobId blobId = BlobId.of(bucketName, fileName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                        .setContentType("application/octet-stream")
                        .setMetadata(Map.of(
                                "version", LocalDate.now().toString(),
                                "source", "MaxMind",
                                "uploaded", Instant.now().toString(),
                                "uploadedBy", System.getProperty("instance.role", "unknown"),
                                "license", "MaxMind GeoLite2 - Internal Use Only"
                        ))
                        .build();

                // Upload the file
                byte[] fileContent = Files.readAllBytes(localFile);
                Blob blob = storage.create(blobInfo, fileContent);

                log.info("Successfully uploaded GeoIP database: {} (size: {} bytes)",
                        fileName, blob.getSize());

                // GDPR: Log successful upload
                log.info("GDPR: Operation=uploadGeoIpDatabase_SUCCESS, FileName={}, Size={}, DataStored=geoip.database",
                        fileName, blob.getSize());

                // Update the latest pointer
                updateLatestPointer(fileName);

                // Clean up old versions
                cleanupOldVersions();

                return blob.getName();

            } catch (Exception e) {
                log.error("Failed to upload GeoIP database", e);
                throw new StorageTranslatableException("error.storage.upload_failed");
            }
        });
    }

    /** A directory this application owns, for files that are only half a database yet. */
    private Path workDirectory() throws IOException {
        Path parent = Paths.get(databasePath).toAbsolutePath().getParent();
        Path work = (parent == null ? Paths.get(".").toAbsolutePath() : parent).resolve("work");
        Files.createDirectories(work);
        return work;
    }

    /**
     * Download the latest GeoIP database from Firebase Storage.
     * All application instances can call this on startup or update.
     *
     * @return CompletableFuture with the path to the downloaded file
     */
    public CompletableFuture<Path> downloadDatabase() {
        // GDPR: Log database download operation
        log.info("GDPR: Operation=downloadGeoIpDatabase, Purpose=location_service_update, DataAccessed=geoip.database");

        if (!storageEnabled) {
            log.info("GeoIP Storage is disabled, expecting local file");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get the latest database file name
                String latestFile = getLatestDatabaseFile();

                if (latestFile == null) {
                    log.warn("No GeoIP database found in Firebase Storage");
                    return null;
                }

                log.info("Downloading GeoIP database from Firebase Storage: {}", latestFile);

                // Get the blob
                Blob blob = storage.get(BlobId.of(bucketName, latestFile));
                if (blob == null) {
                    throw new ResourceNotFoundException("error.business.item_not_found", "GeoIP database" + latestFile);
                }

                // Create temp file for download
                Path tempFile = Files.createTempFile(workDirectory(), "GeoLite2-City", ".mmdb");

                // Download to temp file
                blob.downloadTo(tempFile);

                log.info("Successfully downloaded GeoIP database: {} (size: {} bytes)",
                        latestFile, blob.getSize());

                // GDPR: Log successful download
                log.info("GDPR: Operation=downloadGeoIpDatabase_SUCCESS, FileName={}, Size={}, DataRetrieved=geoip.database",
                        latestFile, blob.getSize());

                // Log metadata for audit
                if (blob.getMetadata() != null) {
                    log.debug("Database metadata: {}", blob.getMetadata());
                }

                return tempFile;

            } catch (Exception e) {
                log.error("Failed to download GeoIP database from Firebase Storage", e);
                throw new StorageTranslatableException("error.storage.upload_failed");
            }
        });
    }

    /**
     * Get metadata about all available GeoIP databases in storage.
     * Useful for monitoring and management.
     *
     * @return Map containing database information
     */
    public Map<String, Object> getDatabaseMetadata() {
        // GDPR: Log metadata access
        log.info("GDPR: Operation=getGeoIpDatabaseMetadata, Purpose=database_monitoring, DataAccessed=geoip.metadata");

        try {
            if (!storageEnabled) {
                return Map.of("status", "disabled");
            }

            // List all database files
            Page<Blob> blobs = storage.list(bucketName,
                    Storage.BlobListOption.prefix(databasePrefix + "/"),
                    Storage.BlobListOption.pageSize(10));

            List<Map<String, Object>> databases = StreamSupport
                    .stream(blobs.iterateAll().spliterator(), false)
                    .map(blob -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("name", blob.getName());
                        info.put("size", blob.getSize());
                        info.put("created", blob.getCreateTimeOffsetDateTime());
                        info.put("updated", blob.getUpdateTimeOffsetDateTime());
                        info.put("contentType", blob.getContentType());

                        if (blob.getMetadata() != null) {
                            info.put("metadata", blob.getMetadata());
                        }

                        return info;
                    })
                    .collect(Collectors.toList());

            // Get latest pointer
            String latest = null;
            try {
                latest = getLatestDatabaseFile();
            } catch (Exception e) {
                log.debug("Could not get latest pointer: {}", e.getMessage());
            }

            return Map.of(
                    "bucket", bucketName,
                    "databases", databases,
                    "count", databases.size(),
                    "latest", latest != null ? latest : "unknown",
                    "maxVersions", maxVersionsToKeep
            );

        } catch (Exception e) {
            log.error("Failed to get database metadata", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Check if a GeoIP database exists in storage.
     *
     * @return true if at least one database exists
     */
    public boolean hasDatabaseInStorage() {
        if (!storageEnabled) {
            return false;
        }

        try {
            String latest = getLatestDatabaseFile();
            return latest != null;
        } catch (Exception e) {
            log.debug("Error checking for database in storage: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Update the metadata pointer to the latest database version.
     *
     * @param fileName The name of the latest database file
     */
    private void updateLatestPointer(String fileName) {
        try {
            String metaFile = metadataPrefix + "/latest.json";

            Map<String, String> latest = Map.of(
                    "file", fileName,
                    "updated", Instant.now().toString(),
                    "updatedBy", System.getProperty("instance.role", "unknown")
            );

            BlobId metaId = BlobId.of(bucketName, metaFile);
            BlobInfo metaInfo = BlobInfo.newBuilder(metaId)
                    .setContentType("application/json")
                    .build();

            byte[] content = objectMapper.writeValueAsBytes(latest);
            storage.create(metaInfo, content, Storage.BlobTargetOption.doesNotExist());

            log.debug("Updated latest pointer to: {}", fileName);

        } catch (StorageException e) {
            if (e.getCode() == 412) {
                // File already exists, update it
                try {
                    updateExistingLatestPointer(fileName);
                } catch (Exception updateError) {
                    log.error("Failed to update existing latest pointer", updateError);
                }
            } else {
                log.error("Failed to create latest pointer", e);
            }
        } catch (Exception e) {
            log.error("Failed to update latest pointer", e);
        }
    }

    /**
     * Update existing latest pointer file.
     */
    private void updateExistingLatestPointer(String fileName) throws IOException {
        String metaFile = metadataPrefix + "/latest.json";

        Map<String, String> latest = Map.of(
                "file", fileName,
                "updated", Instant.now().toString(),
                "updatedBy", System.getProperty("instance.role", "unknown")
        );

        BlobId metaId = BlobId.of(bucketName, metaFile);
        byte[] content = objectMapper.writeValueAsBytes(latest);

        storage.get(metaId).delete();

        BlobInfo metaInfo = BlobInfo.newBuilder(metaId)
                .setContentType("application/json")
                .build();

        storage.create(metaInfo, content);
    }

    /**
     * Get the filename of the latest database from metadata.
     *
     * @return The filename of the latest database, or null if not found
     */
    private String getLatestDatabaseFile() throws IOException {
        String metaFile = metadataPrefix + "/latest.json";

        Blob metaBlob = storage.get(BlobId.of(bucketName, metaFile));
        if (metaBlob == null) {
            // Fallback: list files and get the newest by name
            return findNewestDatabaseByName();
        }

        byte[] content = metaBlob.getContent();
        Map<String, String> meta = objectMapper.readValue(content, new TypeReference<>() {
        });

        String fileName = meta.get("file");

        // Verify the file actually exists
        if (fileName != null) {
            Blob dbBlob = storage.get(BlobId.of(bucketName, fileName));
            if (dbBlob != null && dbBlob.exists()) {
                return fileName;
            }
        }

        // If pointed file doesn't exist, find newest
        log.warn("Latest pointer points to non-existent file: {}, finding newest", fileName);
        return findNewestDatabaseByName();
    }

    /**
     * Find the newest database file by parsing filenames.
     * Files are named like: geoip/GeoLite2-City-YYYY-MM-DD.mmdb
     *
     * @return The filename of the newest database
     */
    private String findNewestDatabaseByName() {
        Page<Blob> blobs = storage.list(bucketName,
                Storage.BlobListOption.prefix(databasePrefix + "/GeoLite2-City-"),
                Storage.BlobListOption.pageSize(100));

        return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                .map(Blob::getName)
                .filter(name -> name.endsWith(".mmdb"))
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * Clean up old database versions, keeping only the most recent ones.
     */
    private void cleanupOldVersions() {
        try {
            // GDPR: Log cleanup operation
            log.info("GDPR: Operation=cleanupOldGeoIpVersions, Purpose=storage_management, DataRemoved=old.geoip.databases");

            log.debug("Cleaning up old GeoIP database versions");

            // List all database files
            Page<Blob> blobs = storage.list(bucketName,
                    Storage.BlobListOption.prefix(databasePrefix + "/GeoLite2-City-"),
                    Storage.BlobListOption.pageSize(100));

            // Sort by name (which includes date) in descending order
            List<Blob> sortedBlobs = StreamSupport
                    .stream(blobs.iterateAll().spliterator(), false)
                    .filter(blob -> blob.getName().endsWith(".mmdb"))
                    .sorted((a, b) -> b.getName().compareTo(a.getName()))
                    .collect(Collectors.toList());

            // Keep only the configured number of versions
            if (sortedBlobs.size() > maxVersionsToKeep) {
                List<Blob> toDelete = sortedBlobs.subList(maxVersionsToKeep, sortedBlobs.size());

                for (Blob blob : toDelete) {
                    try {
                        blob.delete();
                        log.info("Deleted old GeoIP database version: {}", blob.getName());

                        // GDPR: Log deletion
                        log.info("GDPR: Operation=deleteOldGeoIpDatabase, FileName={}, Purpose=retention_policy, DataRemoved=obsolete.geoip.database",
                                blob.getName());
                    } catch (Exception e) {
                        log.warn("Failed to delete old version: {}", blob.getName(), e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to cleanup old versions", e);
        }
    }

    /**
     * Check if the database in Firebase Storage needs updating.
     * Returns true if database is older than maxAgeDays or doesn't exist.
     *
     * @return true if update is needed
     */
    public boolean isDatabaseUpdateNeeded() {
        try {
            String latestFile = getLatestDatabaseFile();
            if (latestFile == null) {
                log.info("No database found in Firebase Storage - update needed");
                return true;
            }

            // Get the blob metadata
            Blob blob = storage.get(BlobId.of(bucketName, latestFile));
            if (blob == null) {
                return true;
            }

            // Check age
            var createTime = blob.getCreateTimeOffsetDateTime();
            if (createTime == null) {
                return true;
            }

            long ageInDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - createTime.toInstant().toEpochMilli());
            boolean updateNeeded = ageInDays >= maxAgeDays;

            log.info("Database age: {} days, max age: {} days, update needed: {}",
                    ageInDays, maxAgeDays, updateNeeded);

            return updateNeeded;

        } catch (Exception e) {
            log.error("Error checking database age", e);
            return false; // Don't update on error
        }
    }

    /**
     * Try to acquire a distributed lock for MaxMind download.
     * Uses Firestore for distributed locking across instances.
     *
     * @param instanceId Unique identifier for this instance
     * @return true if lock was acquired
     */
    public boolean tryAcquireDownloadLock(String instanceId) {
        // GDPR: Log lock acquisition attempt
        log.debug("GDPR: Operation=tryAcquireGeoIpLock, InstanceID={}, Purpose=distributed_coordination, DataAccessed=lock.status",
                instanceId);

        try {
            DocumentReference lockDoc = firestore.collection(LOCK_COLLECTION).document(LOCK_DOCUMENT);

            // Try to get existing lock
            DocumentSnapshot snapshot = lockDoc.get().get();

            if (snapshot.exists()) {
                // Check if lock is expired
                Long lockedAt = snapshot.getLong("lockedAt");
                String owner = snapshot.getString("owner");

                if (lockedAt != null) {
                    long lockAge = System.currentTimeMillis() - lockedAt;

                    if (lockAge < LOCK_TIMEOUT_MS) {
                        // Lock is still valid
                        if (!instanceId.equals(owner)) {
                            log.info("Download lock held by {}, age: {} ms", owner, lockAge);
                            return false;
                        }
                        // We already own the lock
                        return true;
                    }

                    // Lock is expired, we can take it
                    log.info("Previous lock by {} expired (age: {} ms), acquiring new lock", owner, lockAge);
                }
            }

            // Try to acquire the lock
            Map<String, Object> lockData = Map.of(
                    "owner", instanceId,
                    "lockedAt", System.currentTimeMillis(),
                    "purpose", "MaxMind database download"
            );

            lockDoc.set(lockData, SetOptions.merge()).get();
            log.info("Successfully acquired download lock for instance: {}", instanceId);
            return true;

        } catch (Exception e) {
            if (Interrupts.isInterrupt(e)) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to acquire download lock", e);
            return false;
        }
    }

    /**
     * Release the download lock.
     *
     * @param instanceId Instance that holds the lock
     */
    public void releaseDownloadLock(String instanceId) {
        try {
            DocumentReference lockDoc = firestore.collection(LOCK_COLLECTION).document(LOCK_DOCUMENT);
            DocumentSnapshot snapshot = lockDoc.get().get();

            if (snapshot.exists()) {
                String owner = snapshot.getString("owner");
                if (instanceId.equals(owner)) {
                    lockDoc.delete().get();
                    log.info("Released download lock for instance: {}", instanceId);
                } else {
                    log.warn("Cannot release lock owned by: {}", owner);
                }
            }
        } catch (Exception e) {
            if (Interrupts.isInterrupt(e)) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to release download lock", e);
        }
    }

    /**
     * Get storage statistics for monitoring.
     *
     * @return Map with storage statistics
     */
    public Map<String, Object> getStorageStatistics() {
        try {
            if (!storageEnabled) {
                return Map.of("enabled", false);
            }

            Page<Blob> blobs = storage.list(bucketName,
                    Storage.BlobListOption.prefix(databasePrefix + "/"));

            long totalSize = 0;
            int fileCount = 0;

            for (Blob blob : blobs.iterateAll()) {
                if (blob.getName().endsWith(".mmdb")) {
                    totalSize += blob.getSize();
                    fileCount++;
                }
            }

            return Map.of(
                    "enabled", true,
                    "bucket", bucketName,
                    "fileCount", fileCount,
                    "totalSizeBytes", totalSize,
                    "totalSizeMB", totalSize / (1024.0 * 1024.0),
                    "maxVersions", maxVersionsToKeep
            );

        } catch (Exception e) {
            log.error("Failed to get storage statistics", e);
            return Map.of("error", e.getMessage());
        }
    }
}
