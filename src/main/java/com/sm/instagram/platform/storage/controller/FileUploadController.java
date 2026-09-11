package com.sm.instagram.platform.storage.controller;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.TranslatableException;
import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.storage.model.FileUploadRequest;
import com.sm.instagram.platform.storage.model.UploadResponses;
import com.sm.instagram.platform.storage.model.FileUploadResponse;
import com.sm.instagram.platform.storage.service.FileTrackingService;
import com.sm.instagram.platform.storage.service.SignedUrlService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import com.sm.instagram.platform.storage.service.UploadMetricsService;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import com.sm.instagram.platform.common.util.LogSafe;

@Slf4j
@RestController
@RequestMapping("upload")
@Tag(name = "File Upload", description = "Endpoints for file upload management")
@Validated
@ConditionalOnBean(SignedUrlService.class)
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT, companyLimitMultiplier = 3.0, influencerLimitMultiplier = 2.0)
public class FileUploadController {

    private final SignedUrlService signedUrlService;
    private final StorageRateLimitService rateLimiterService;
    private final UploadMetricsService metricsService;
    private final FileTrackingService trackingService;
    private final PermissionUtils permissionUtils;

    public FileUploadController(SignedUrlService signedUrlService,
                                StorageRateLimitService rateLimiterService,
                                UploadMetricsService metricsService,
                                FileTrackingService trackingService,
                                PermissionUtils permissionUtils) {
        this.signedUrlService = signedUrlService;
        this.rateLimiterService = rateLimiterService;
        this.metricsService = metricsService;
        this.trackingService = trackingService;
        this.permissionUtils = permissionUtils;
    }

    /**
     * Generates a signed URL for file upload.
     * Think of this endpoint as the ticket booth where users get their upload passes.
     */
    @PostMapping("/signed-url")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Generate signed URL for file upload",
            description = "Creates a temporary signed URL that allows direct upload to Firebase Storage")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signed URL generated successfully",
                    content = @Content(schema = @Schema(implementation = FileUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "507", description = "Storage quota exceeded")
    })
    public ResponseEntity<?> generateSignedUrl(@Valid @RequestBody FileUploadRequest request) {

        String userId = permissionUtils.getUserId();
        String firebaseUid = userId; // userId is already the Firebase UID from PermissionUtils

        log.info("GDPR: Operation=generateSignedUrl, FirebaseUID={}, Filename={}, ContentType={}, Purpose=file_upload",
                firebaseUid, request.getFilename(), request.getContentType());

        // Record metrics
        if (metricsService != null) {
            metricsService.recordUploadRequest();
            metricsService.incrementActiveUploads();
        }

        Timer.Sample timerSample = null;
        if (metricsService != null) {
            timerSample = metricsService.startSignedUrlTimer();
        }

        try {
            // Generate signed URL
            FileUploadResponse response = signedUrlService.generateSignedUrl(userId, request);

            // Check if there was an error (rate limit or storage)
            if (response.getError() != null) {
                log.warn("GDPR: Operation=generateSignedUrl_denied, FirebaseUID={}, Error={}, Purpose=rate_limit_enforcement",
                        firebaseUid, response.getError());

                // Record metrics for failures
                if (metricsService != null) {
                    String errorLowerCase = response.getError().toLowerCase();
                    if (errorLowerCase.contains("rate limit")) {
                        metricsService.recordRateLimitHit("upload");
                    } else if (errorLowerCase.contains("storage")) {
                        metricsService.recordStorageQuotaExceeded(userId);
                    }
                    metricsService.recordUploadFailure(response.getError());
                    metricsService.decrementActiveUploads();
                }

                // Determine appropriate exception type (case-insensitive check)
                String errorLowerCase = response.getError().toLowerCase();
                if (errorLowerCase.contains("rate limit")) {
                    throw new RateLimitTranslatableException("error.storage.rate_limit_exceeded", "uploads");
                } else if (errorLowerCase.contains("storage")) {
                    throw new StorageTranslatableException("error.storage.quota_exceeded");
                }

                throw new StorageTranslatableException("error.storage.upload_failed", "Upload operation failed");
            }

            // Record successful signed URL generation
            if (metricsService != null && timerSample != null) {
                metricsService.recordSignedUrlTime(timerSample);
            }

            log.info("GDPR: Operation=generateSignedUrl_success, FirebaseUID={}, UploadID={}, Purpose=file_upload",
                    firebaseUid, response.getUploadId());
            return ResponseEntity.ok(response);

        } catch (TranslatableException e) {
            // Every domain exception in this project already carries the status it means, and the
            // handlers already map it. Naming three of them here left the rest to the catch-all
            // below, which relabels anything at all as 507 Insufficient Storage -- a specific claim
            // about the server's disk, made about a request the server had simply refused.
            throw e;
        } catch (Exception e) {
            log.error("GDPR: Operation=generateSignedUrl_failed, FirebaseUID={}, Error={}",
                    firebaseUid, e.getMessage(), e);

            // Record metrics for unexpected errors
            if (metricsService != null) {
                metricsService.recordUploadFailure("INTERNAL_ERROR");
                metricsService.decrementActiveUploads();
                if (timerSample != null) {
                    metricsService.recordSignedUrlTime(timerSample);
                }
            }

            throw new StorageTranslatableException("error.storage.upload_failed", "Upload operation failed");
        }
    }

    /**
     * Confirms a successful upload.
     * The frontend should call this after successfully uploading to Firebase.
     */
    @PostMapping("/confirm/{uploadId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Confirm successful upload",
            description = "Notifies the backend that a file was successfully uploaded")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upload confirmed"),
            @ApiResponse(responseCode = "404", description = "Upload ID not found"),
            @ApiResponse(responseCode = "409", description = "Upload already confirmed")
    })
    public ResponseEntity<UploadResponses.UploadConfirmation> confirmUpload(
            @PathVariable String uploadId, @RequestParam String filePath) {

        String userId = permissionUtils.getUserId();
        String firebaseUid = userId;

        log.info("GDPR: Operation=confirmUpload, FirebaseUID={}, UploadID={}, FilePath={}, Purpose=upload_confirmation",
                firebaseUid, uploadId, filePath);

        Timer.Sample confirmationTimer = null;
        if (metricsService != null) {
            confirmationTimer = metricsService.startConfirmationTimer();
        }

        try {
            // Validate that the file actually exists
            boolean exists = signedUrlService.validateUploadSuccess(filePath);

            if (!exists) {
                log.warn("GDPR: Operation=confirmUpload_notFound, FirebaseUID={}, FilePath={}, Purpose=validation",
                        firebaseUid, filePath);
                throw new ResourceNotFoundException("error.business.item_not_found", "File");
            }

            // Update user's storage quota
            signedUrlService.confirmUpload(userId, filePath);

            // Record success metrics
            if (metricsService != null) {
                // Get file size for metrics (this is a simplified approach)
                try {
                    metricsService.recordUploadSuccess(userId, 0); // Size will be updated by tracking service
                    metricsService.decrementActiveUploads();
                    if (confirmationTimer != null) {
                        metricsService.recordConfirmationTime(confirmationTimer);
                    }
                } catch (Exception metricsError) {
                    log.warn("Failed to record success metrics for user: {}", userId, metricsError);
                }
            }

            return ResponseEntity.ok(new UploadResponses.UploadConfirmation(
                    "confirmed", uploadId, filePath, "Upload confirmed successfully"));

        } catch (TranslatableException | IllegalArgumentException e) {
            // What the caller typed. 507 Insufficient Storage is a specific claim -- the server
            // cannot store the representation -- and saying it about a malformed filePath sends the
            // caller looking for a quota problem that does not exist. `?filePath=` came back 507
            // even after IllegalArgumentException was let through here, because the service refuses
            // an empty path with ValidationTranslatableException, which is a 400 and was being
            // caught below and relabelled.
            log.warn("GDPR: Operation=confirmUpload_rejected, FirebaseUID={}, UploadID={}, Reason={}",
                    firebaseUid, LogSafe.value(uploadId), LogSafe.value(e.getMessage()));
            throw e;
        } catch (Exception e) {
            log.error("GDPR: Operation=confirmUpload_failed, FirebaseUID={}, UploadID={}, Error={}",
                    firebaseUid, LogSafe.value(uploadId), LogSafe.value(e.getMessage()), e);

            // Record failure metrics
            if (metricsService != null) {
                metricsService.recordUploadFailure("CONFIRMATION_ERROR");
                if (confirmationTimer != null) {
                    metricsService.recordConfirmationTime(confirmationTimer);
                }
            }

            throw new StorageTranslatableException("error.storage.upload_failed", "Upload confirmation failed");
        }
    }

    /**
     * Gets the current rate limit status for the authenticated user.
     * Useful for showing upload limits in the UI.
     */
    @GetMapping("/limits")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user's rate limit status",
            description = "Returns current upload limits and usage for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rate limit status retrieved")
    })
    public ResponseEntity<UploadResponses.RateLimits> getRateLimitStatus() {
        String userId = permissionUtils.getUserId();
        log.debug("Fetching rate limit status for user: {}", userId);

        var status = rateLimiterService.getUserStatus(userId);

        return ResponseEntity.ok(UploadResponses.RateLimits.from(status));
    }

    /**
     * Health check endpoint for monitoring.
     * Operations teams can use this to ensure the upload service is running.
     */
    @GetMapping("/health")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Health check", description = "Check if upload service is healthy")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "file-upload");
        health.put("timestamp", String.valueOf(System.currentTimeMillis()));

        return ResponseEntity.ok(health);
    }

    /**
     * Gets detailed upload statistics for the authenticated user.
     * Shows file counts, storage usage, and recent activity.
     */
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user's upload statistics",
            description = "Returns detailed upload statistics and file history for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    public ResponseEntity<UploadResponses.UploadStats> getUserUploadStats() {
        String userId = permissionUtils.getUserId();
        log.debug("Fetching upload statistics for user: {}", userId);

        UploadResponses.RateLimits rateLimits =
                UploadResponses.RateLimits.from(rateLimiterService.getUserStatus(userId));

        // Absent rather than empty when the tracking service is not configured; the serializer
        // drops the null, which is what the map did by never putting the key.
        UploadResponses.UploadHistory history = null;
        if (trackingService != null) {
            var uploadStats = trackingService.getUserStats(userId);
            history = new UploadResponses.UploadHistory(
                    uploadStats.getTotalFiles(),
                    uploadStats.getTotalSize(),
                    uploadStats.getFilesUploadedToday(),
                    uploadStats.getFilesUploadedThisHour());
        }

        return ResponseEntity.ok(new UploadResponses.UploadStats(rateLimits, history));
    }
}
