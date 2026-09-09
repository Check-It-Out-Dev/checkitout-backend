package com.sm.instagram.platform.storage.service;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.storage.entity.FileUpload;
import com.sm.instagram.platform.storage.model.FileUploadRequest;
import com.sm.instagram.platform.storage.model.FileUploadResponse;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SignedUrlService {

    private static final int MAX_FILENAME_LENGTH = 100;
    private static final int FILENAME_BUFFER = 4; // Buffer for truncating long filenames
    private static final String FILENAME_UNDERSCORE = "_";

    /**
     * Maximum file size: 5MB (5,242,880 bytes)
     */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * Allowed content types for uploads: JPEG, PNG, WebP, GIF
     */
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final Storage storage;
    private final String bucketName;
    private final StorageRateLimitService rateLimiter;
    private final FileTrackingService trackingService;
    private final UploadMetricsService metricsService;
    private final ServiceAccountCredentials signingCredentials;
    /** dev-lite only: local byte transport replacing GCS (null in every other profile). */
    private final LocalUploadSink localSink;

    @Value("${file-upload.signed-url-expiration-minutes:5}")
    private int signedUrlExpirationMinutes;

    @Value("${file-upload.storage-path-pattern:content/{userId}/{timestamp}_{filename}}")
    private String storagePathPattern;

    public SignedUrlService(@Autowired(required = false) @Qualifier("fileUploadStorage") Storage storage,
                            @Value("${gcp.bucket-name:check-it-out-47c50.firebasestorage.app}") String bucketName,
                            StorageRateLimitService rateLimiter,
                            @Autowired(required = false) FileTrackingService trackingService,
                            @Autowired(required = false) UploadMetricsService metricsService,
                            @Autowired(required = false) GoogleCredentialsProvider credentialsProvider,
                            @Autowired(required = false) LocalUploadSink localSink) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.rateLimiter = rateLimiter;
        this.trackingService = trackingService;
        this.metricsService = metricsService;
        this.localSink = localSink;
        if (localSink != null) {
            log.info("SignedUrlService routes uploads to the local sink instead of GCS (dev-lite or e2e)");
        }

        // Extract ServiceAccountCredentials for URL signing
        if (credentialsProvider != null && credentialsProvider.getCachedCredentials() instanceof ServiceAccountCredentials) {
            this.signingCredentials = (ServiceAccountCredentials) credentialsProvider.getCachedCredentials();
            log.info("SignedUrlService initialized with service account credentials for URL signing");
        } else {
            this.signingCredentials = null;
            log.warn("Service account credentials not available - signed URLs may not work properly");
        }

        if (storage == null) {
            log.warn("Google Cloud Storage is not available. Signed URL generation will not work.");
        }
    }

    /**
     * Generates a signed URL for file upload.
     * Think of this as creating a temporary, secure mailbox where someone can drop off a package.
     *
     * @param userId  The user requesting upload
     * @param request Upload request details
     * @return Response with signed URL and metadata
     */
    public FileUploadResponse generateSignedUrl(String userId, FileUploadRequest request) {
        log.info("GDPR: Operation=generateSignedUrl, FirebaseUID={}, FileName={}, FileSize={}, ContentType={}, Purpose=file_upload_preparation",
                userId, request.getFilename(), request.getFileSize(), request.getContentType());

        // Validation checks
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "userId");
        }

        if (request == null) {
            throw new ValidationTranslatableException("error.validation.required_field", "request");
        }

        if (request.getFilename() == null || request.getFilename().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "filename");
        }

        if (request.getContentType() == null || request.getContentType().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "contentType");
        }

        if (request.getFileSize() <= 0) {
            throw new ValidationTranslatableException("error.validation.invalid_argument", "fileSize");
        }

        // Validate file size does not exceed maximum
        if (request.getFileSize() > MAX_FILE_SIZE) {
            log.warn("GDPR: Operation=generateSignedUrl_validation_failed, FirebaseUID={}, FileSize={}, MaxSize={}, Reason=file_too_large, Purpose=security",
                    userId, request.getFileSize(), MAX_FILE_SIZE);
            throw new ValidationTranslatableException("error.validation.file_too_large", "fileSize");
        }

        // Validate content type is allowed
        if (!ALLOWED_CONTENT_TYPES.contains(request.getContentType().toLowerCase())) {
            log.warn("GDPR: Operation=generateSignedUrl_validation_failed, FirebaseUID={}, ContentType={}, AllowedTypes={}, Reason=invalid_content_type, Purpose=security",
                    userId, request.getContentType(), ALLOWED_CONTENT_TYPES);
            throw new ValidationTranslatableException("error.validation.invalid_content_type", "contentType");
        }

        // Start metrics timer
        Timer.Sample timerSample = null;
        if (metricsService != null) {
            timerSample = metricsService.startSignedUrlTimer();
            metricsService.recordUploadRequest();
        }

        // Check if a byte transport is available (GCS, or the dev-lite sink)
        if (storage == null && localSink == null) {
            log.error("GDPR: Operation=generateSignedUrl_failed, FirebaseUID={}, Error=storage_not_configured, Purpose=file_upload_preparation", userId);
            if (metricsService != null) {
                metricsService.recordUploadFailure("STORAGE_NOT_CONFIGURED");
            }
            throw new StorageTranslatableException("error.storage.service_unavailable");
        }

        // Step 1: Check rate limits (with upload type for differentiated limits)
        StorageRateLimitService.UploadType uploadType = resolveUploadType(request.getUploadType());
        var rateLimitResult = rateLimiter.checkUploadAllowed(userId, request.getFileSize(), uploadType);
        if (!rateLimitResult.isAllowed()) {
            log.warn("GDPR: Operation=generateSignedUrl_rateLimited, FirebaseUID={}, FileSize={}, Reason={}, Purpose=rate_limit_enforcement",
                    userId, request.getFileSize(), rateLimitResult.getMessage());
            if (metricsService != null) {
                metricsService.recordRateLimitHit("upload");
                metricsService.recordUploadFailure("RATE_LIMIT_EXCEEDED");
            }
            throw new RateLimitTranslatableException("error.storage.rate_limit_exceeded", "uploads");
        }

        // Step 2: Check storage quota
        if (!rateLimiter.hasStorageSpace(userId, request.getFileSize())) {
            log.warn("GDPR: Operation=generateSignedUrl_quotaExceeded, FirebaseUID={}, RequestedSize={}, Purpose=storage_quota_enforcement",
                    userId, request.getFileSize());
            if (metricsService != null) {
                metricsService.recordStorageQuotaExceeded(userId);
                metricsService.recordUploadFailure("STORAGE_QUOTA_EXCEEDED");
            }
            throw new StorageTranslatableException("error.storage.quota_exceeded");
        }

        // Step 3: Generate unique file path
        String filePath = generateFilePath(userId, request);
        log.debug("Generated file path: {}", filePath);

        // Steps 4-6: mint the upload target + public URL. dev-lite swaps the
        // byte transport (local sink instead of a GCS V4 signature); every
        // rule around it — validation, limits, quota, tracking — is shared.
        try {
            String uploadTargetUrl;
            String publicUrl;
            if (localSink != null) {
                String token = localSink.prepareUpload(
                        filePath, request.getContentType(), signedUrlExpirationMinutes);
                uploadTargetUrl = localSink.uploadUrlFor(token);
                publicUrl = localSink.publicUrl(filePath);
            } else {
                // Step 4: Create blob info
                BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, filePath)
                        .setContentType(request.getContentType())
                        .setMetadata(createMetadata(userId, request))
                        .build();

                // Step 5: Generate signed URL — V4 signature for better
                // compatibility and explicit headers, matching the validation
                // service's successful usage.
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", request.getContentType());

                URL signedUrl;
                if (signingCredentials != null) {
                    // Use service account credentials for signing (required for Firebase Storage)
                    signedUrl = storage.signUrl(
                            blobInfo,
                            signedUrlExpirationMinutes,
                            TimeUnit.MINUTES,
                            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                            Storage.SignUrlOption.withExtHeaders(headers),
                            Storage.SignUrlOption.withV4Signature(),
                            Storage.SignUrlOption.signWith(signingCredentials)
                    );
                } else {
                    // Fallback without explicit signing credentials (may not work with Firebase)
                    log.warn("Generating signed URL without service account credentials - this may fail");
                    signedUrl = storage.signUrl(
                            blobInfo,
                            signedUrlExpirationMinutes,
                            TimeUnit.MINUTES,
                            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                            Storage.SignUrlOption.withExtHeaders(headers),
                            Storage.SignUrlOption.withV4Signature()
                    );
                }
                uploadTargetUrl = signedUrl.toString();

                // Step 6: Generate public URL (for viewing after upload)
                publicUrl = generatePublicUrl(filePath);
            }

            // Step 7: Create response
            String uploadId = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plus(signedUrlExpirationMinutes, ChronoUnit.MINUTES);

            FileUploadResponse response = new FileUploadResponse(
                    uploadTargetUrl,
                    publicUrl,
                    filePath,
                    expiresAt,
                    uploadId
            );

            // Step 8: Record upload request in tracking system
            if (trackingService != null) {
                FileUpload upload = trackingService.recordUploadRequest(
                        userId,
                        filePath,
                        request.getFilename(),
                        request.getContentType(),
                        request.getFileSize()
                );
                // Use the database ID as upload ID for better tracking
                response.setUploadId(upload.getId());
            }

            // Add rate limit info
            var status = rateLimiter.getUserStatus(userId);
            response.setRateLimitInfo(new FileUploadResponse.RateLimitInfo(
                    rateLimitResult.getRemainingHourly(),
                    rateLimitResult.getRemainingDaily(),
                    (long) status.getStorageUsedMB(),
                    (long) status.getStorageLimitMB()
            ));

            // Add upload instructions for frontend
            response.setUploadInstructions(new FileUploadResponse.UploadInstructions());

            // Record success metrics
            if (metricsService != null && timerSample != null) {
                metricsService.recordSignedUrlTime(timerSample);
                metricsService.recordDetailedUploadMetrics(
                        userId,
                        request.getContentType(),
                        request.getFileSize(),
                        0 // Duration will be recorded later during confirmation
                );
            }

            log.info("GDPR: Operation=generateSignedUrl_success, FirebaseUID={}, UploadID={}, FilePath={}, DataAccessed=user_storage_quota, Purpose=file_upload_url_generation",
                    userId, response.getUploadId(), filePath);
            return response;

        } catch (Exception e) {
            log.error("GDPR: Operation=generateSignedUrl_error, FirebaseUID={}, Error={}, Purpose=file_upload_preparation",
                    userId, e.getMessage(), e);
            if (metricsService != null) {
                metricsService.recordUploadFailure("SIGNED_URL_GENERATION_ERROR");
                if (timerSample != null) {
                    metricsService.recordSignedUrlTime(timerSample);
                }
            }
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Generates the storage path for a file.
     * Pattern: content/{userId}/{timestamp}_{filename}
     */
    private String generateFilePath(String userId, FileUploadRequest request) {
        long timestamp = System.currentTimeMillis();
        String sanitizedFilename = sanitizeFilename(request.getFilename());

        return storagePathPattern
                .replace("{userId}", userId)
                .replace("{timestamp}", String.valueOf(timestamp))
                .replace("{filename}", sanitizedFilename);
    }

    /**
     * Sanitizes filename to prevent path traversal attacks.
     * Think of this as checking a package label for suspicious characters.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "filename");
        }

        // Remove any path separators
        filename = filename.replaceAll("[/\\\\]", FILENAME_UNDERSCORE);

        // Remove any non-alphanumeric characters except dots, dashes, and underscores
        filename = filename.replaceAll("[^a-zA-Z0-9._-]", FILENAME_UNDERSCORE);

        // Ensure it doesn't start with a dot (hidden files)
        if (filename.startsWith(".")) {
            filename = FILENAME_UNDERSCORE + filename.substring(1);
        }

        // Limit length
        if (filename.length() > MAX_FILENAME_LENGTH) {
            String extension = filename.substring(filename.lastIndexOf('.'));
            filename = filename.substring(0, MAX_FILENAME_LENGTH - FILENAME_BUFFER - extension.length()) + extension;
        }

        return filename;
    }

    /**
     * Creates metadata to attach to the uploaded file.
     * This helps with tracking and auditing.
     */
    private Map<String, String> createMetadata(String userId, FileUploadRequest request) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("uploadedBy", userId);
        metadata.put("uploadTimestamp", String.valueOf(System.currentTimeMillis()));
        metadata.put("originalFilename", request.getFilename());

        if (request.getDescription() != null) {
            metadata.put("description", request.getDescription());
        }
        if (request.getAltText() != null) {
            metadata.put("altText", request.getAltText());
        }

        return metadata;
    }

    /**
     * Creates headers for the signed URL.
     * These headers must match what the client sends during upload.
     */
    private Map<String, String> createHeaders(FileUploadRequest request) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.getContentType());
        headers.put("Content-Length", String.valueOf(request.getFileSize()));
        return headers;
    }

    /**
     * Generates the public URL for viewing the file.
     * Since our files are publicly readable, this is a simple URL construction.
     */
    private String generatePublicUrl(String filePath) {
        if (localSink != null) {
            return localSink.publicUrl(filePath);
        }
        return String.format("https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media",
                bucketName,
                filePath.replace("/", "%2F"));  // URL encode the path
    }

    /**
     * A tracked upload resolved to BE-derived values. Everything here comes
     * from the PostgreSQL tracking row + the bucket — never from the client.
     */
    public record ResolvedUpload(String filePath, String publicUrl,
                                 String filename, String contentType, Long fileSize) {}

    /**
     * Resolves a client-supplied {@code uploadId} to the BE-minted file
     * reference — the ONLY way an uploaded file becomes a persistable
     * URL (pentest 3.1 hardening, owner directive 2026-06-13: never trust
     * a client URL; the server derives it from its own tracking).
     *
     * <p>Checks, in order:
     * <ol>
     *   <li>the uploadId exists in the {@code file_uploads} tracking table,</li>
     *   <li>the upload belongs to the calling user (ownership — an attacker
     *       cannot attach someone else's file; rejected with the SAME error
     *       key as unknown ids so existence never leaks),</li>
     *   <li>the blob actually exists in OUR bucket
     *       ({@link #validateUploadSuccess}).</li>
     * </ol>
     * The public URL, filename, content type and size are all derived from
     * the tracked row — client-supplied copies of any of them are ignored
     * by callers of this method.
     */
    public ResolvedUpload resolveOwnedUpload(String userId, String uploadId) {
        if (userId == null || userId.isBlank()) {
            throw new ValidationTranslatableException("error.attachment.unknown_upload");
        }
        if (trackingService == null) {
            log.error("File tracking unavailable — cannot resolve uploadId");
            throw new StorageTranslatableException("error.storage.service_unavailable");
        }
        FileUpload upload = trackingService.getUpload(uploadId)
                .orElseThrow(() -> new ValidationTranslatableException("error.attachment.unknown_upload"));
        if (!userId.equals(upload.getUserId())) {
            // Do NOT use a distinct error key — that would leak which ids exist.
            log.warn("SECURITY: user {} attempted to use upload {} owned by another user",
                    userId, uploadId);
            throw new ValidationTranslatableException("error.attachment.unknown_upload");
        }
        if (!validateUploadSuccess(upload.getFilePath())) {
            throw new ValidationTranslatableException("error.attachment.upload_incomplete");
        }
        return new ResolvedUpload(
                upload.getFilePath(),
                generatePublicUrl(upload.getFilePath()),
                upload.getFilename(),
                upload.getContentType(),
                upload.getFileSize());
    }

    /**
     * Validates that an upload was successful by checking if the file exists.
     * Call this after receiving upload confirmation from the client.
     */
    public boolean validateUploadSuccess(String filePath) {
        log.debug("GDPR: Operation=validateUpload, FilePath={}, Purpose=upload_verification", filePath);

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "filePath");
        }

        if (localSink != null) {
            return localSink.exists(filePath);
        }

        if (storage == null) {
            log.error("GDPR: Operation=validateUpload_failed, FilePath={}, Error=storage_not_configured, Purpose=upload_verification", filePath);
            throw new StorageTranslatableException("error.storage.service_unavailable");
        }

        try {
            Blob blob = storage.get(bucketName, filePath);
            return blob != null && blob.exists();
        } catch (Exception e) {
            log.error("GDPR: Operation=validateUpload_error, FilePath={}, Error={}, Purpose=upload_verification",
                    filePath, e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Updates user storage quota after successful upload.
     * This should be called after upload is confirmed.
     */
    public void confirmUpload(String userId, String filePath) {
        log.info("GDPR: Operation=confirmUpload, FirebaseUID={}, FilePath={}, Purpose=upload_confirmation", userId, filePath);

        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "userId");
        }

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "filePath");
        }

        if (localSink != null) {
            Long size = localSink.size(filePath);
            if (size != null) {
                rateLimiter.recordUpload(userId, size);
                if (trackingService != null) {
                    trackingService.confirmUploadViaApi(filePath, size);
                }
                if (metricsService != null) {
                    metricsService.recordUploadSuccess(userId, size);
                }
                log.info("GDPR: Operation=confirmUpload_success, FirebaseUID={}, FilePath={}, FileSize={}, DataModified=user_storage_quota, Purpose=storage_accounting",
                        userId, filePath, size);
            }
            return;
        }

        if (storage == null) {
            log.error("GDPR: Operation=confirmUpload_failed, FirebaseUID={}, Error=storage_not_configured, Purpose=upload_confirmation", userId);
            throw new StorageTranslatableException("error.storage.service_unavailable");
        }

        try {
            Blob blob = storage.get(bucketName, filePath);
            if (blob != null && blob.exists()) {
                Long size = blob.getSize();
                if (size != null) {
                    rateLimiter.recordUpload(userId, size);

                    // Also record in tracking system if available
                    if (trackingService != null) {
                        trackingService.confirmUploadViaApi(filePath, size);
                    }

                    // Record success metrics
                    if (metricsService != null) {
                        metricsService.recordUploadSuccess(userId, size);
                    }

                    log.info("GDPR: Operation=confirmUpload_success, FirebaseUID={}, FilePath={}, FileSize={}, DataModified=user_storage_quota, Purpose=storage_accounting",
                            userId, filePath, size);
                }
            }
        } catch (Exception e) {
            log.error("GDPR: Operation=confirmUpload_error, FirebaseUID={}, FilePath={}, Error={}, Purpose=upload_confirmation",
                    userId, filePath, e.getMessage(), e);
            if (metricsService != null) {
                metricsService.recordUploadFailure("CONFIRMATION_ERROR");
            }
            throw new StorageTranslatableException("error.storage.upload_failed");
        }
    }

    /**
     * Resolves the upload type from the request string.
     * Defaults to CONTENT for backwards compatibility and safety.
     *
     * @param uploadTypeStr String representation from request (may be null)
     * @return UploadType enum value
     */
    private StorageRateLimitService.UploadType resolveUploadType(String uploadTypeStr) {
        if (uploadTypeStr == null || uploadTypeStr.trim().isEmpty()) {
            return StorageRateLimitService.UploadType.CONTENT;
        }

        try {
            return StorageRateLimitService.UploadType.valueOf(uploadTypeStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown upload type '{}', defaulting to CONTENT", uploadTypeStr);
            return StorageRateLimitService.UploadType.CONTENT;
        }
    }
}
