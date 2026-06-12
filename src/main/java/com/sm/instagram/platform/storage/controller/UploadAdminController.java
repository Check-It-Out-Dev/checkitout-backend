package com.sm.instagram.platform.storage.controller;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.storage.entity.FileUpload;
import com.sm.instagram.platform.storage.repository.FileUploadRepository;
import com.sm.instagram.platform.storage.service.FileTrackingService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("admin/uploads")
@Tag(name = "Upload Administration", description = "Admin endpoints for upload system management")
@PreAuthorize("hasAuthority('ADMIN')")
public class UploadAdminController {

    private final FileUploadRepository uploadRepository;
    private final FileTrackingService trackingService;
    private final StorageRateLimitService rateLimiterService;

    @Autowired
    public UploadAdminController(FileUploadRepository uploadRepository,
                                 FileTrackingService trackingService,
                                 StorageRateLimitService rateLimiterService) {
        this.uploadRepository = uploadRepository;
        this.trackingService = trackingService;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Get system-wide upload statistics.
     */
    @GetMapping("/stats/system")
    @Operation(summary = "Get system upload statistics",
            description = "Returns overall system upload metrics and health")
    public ResponseEntity<?> getSystemStats() {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log admin system statistics access
        log.info("GDPR: Operation=getSystemStats, FirebaseUID={}, Purpose=system_monitoring, DataAccessed=upload.statistics",
                firebaseUid);

        // Get counts by status
        long totalUploads = uploadRepository.count();
        long pendingUploads = uploadRepository.findByStatusAndCreatedAtBefore(
                FileUpload.UploadStatus.PENDING,
                Instant.now()
        ).size();

        // Get recent activity
        Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant hourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        List<FileUpload> recentUploads = uploadRepository.findByUserIdAndStatusIn(
                "", // Will be filtered by query
                List.of(FileUpload.UploadStatus.CONFIRMED, FileUpload.UploadStatus.WEBHOOK)
        );

        long uploadsLast24h = recentUploads.stream()
                .filter(u -> u.getUploadTime().isAfter(dayAgo))
                .count();

        long uploadsLastHour = recentUploads.stream()
                .filter(u -> u.getUploadTime().isAfter(hourAgo))
                .count();

        // Calculate total storage used
        Long totalStorage = uploadRepository.getTotalStorageByUser("");
        if (totalStorage == null) totalStorage = 0L;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUploads", totalUploads);
        stats.put("pendingUploads", pendingUploads);
        stats.put("uploadsLast24Hours", uploadsLast24h);
        stats.put("uploadsLastHour", uploadsLastHour);
        stats.put("totalStorageBytes", totalStorage);
        stats.put("totalStorageMB", totalStorage / (1024.0 * 1024.0));
        stats.put("timestamp", Instant.now());

        return ResponseEntity.ok(stats);
    }

    /**
     * Get uploads for a specific user.
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user uploads",
            description = "Returns all uploads for a specific user")
    public ResponseEntity<?> getUserUploads(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String adminUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "userId");
        }

        if (page < 0) {
            throw new ValidationTranslatableException("error.validation.invalid_id");
        }

        if (size <= 0 || size > 100) {
            throw new ValidationTranslatableException("error.validation.list_too_large", "100");
        }

        // GDPR: Log admin access to user uploads
        log.info("GDPR: Operation=getUserUploads, AdminUID={}, TargetUserID={}, Page={}, Size={}, Purpose=user_upload_review, DataAccessed=user.uploads,user.storage",
                adminUid, userId, page, size);

        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "uploadTime"));

        List<FileUpload> uploads = uploadRepository.findByUserId(userId);

        // Get user statistics
        FileTrackingService.UserUploadStats stats = trackingService.getUserStats(userId);
        StorageRateLimitService.RateLimitStatus rateLimits = rateLimiterService.getUserStatus(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("uploads", uploads);
        response.put("statistics", Map.of(
                "totalFiles", stats.getTotalFiles(),
                "totalSizeBytes", stats.getTotalSize(),
                "filesUploadedToday", stats.getFilesUploadedToday(),
                "filesUploadedThisHour", stats.getFilesUploadedThisHour()
        ));
        response.put("rateLimits", Map.of(
                "hourlyUsed", rateLimits.getHourlyUsed(),
                "hourlyLimit", rateLimits.getHourlyLimit(),
                "dailyUsed", rateLimits.getDailyUsed(),
                "dailyLimit", rateLimits.getDailyLimit(),
                "storageUsedMB", rateLimits.getStorageUsedMB(),
                "storageLimitMB", rateLimits.getStorageLimitMB()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Manual cleanup of orphaned uploads.
     */
    @PostMapping("/cleanup/orphaned")
    @Operation(summary = "Clean up orphaned uploads",
            description = "Manually trigger cleanup of old pending uploads")
    public ResponseEntity<?> cleanupOrphanedUploads() {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log data cleanup operation
        log.warn("GDPR: CLEANUP Operation=cleanupOrphanedUploads, FirebaseUID={}, Purpose=data_maintenance, DataModified=orphaned.uploads",
                firebaseUid);

        trackingService.cleanupOrphanedUploads();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Orphaned upload cleanup completed");
        response.put("timestamp", Instant.now());

        return ResponseEntity.ok(response);
    }

    /**
     * Get uploads by status.
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Get uploads by status",
            description = "Returns all uploads with the specified status")
    public ResponseEntity<?> getUploadsByStatus(
            @PathVariable FileUpload.UploadStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        if (status == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "status");
        }

        if (page < 0) {
            throw new ValidationTranslatableException("error.validation.invalid_id");
        }

        if (size <= 0 || size > 100) {
            throw new ValidationTranslatableException("error.validation.list_too_large", "100");
        }

        // GDPR: Log admin query by status
        log.info("GDPR: Operation=getUploadsByStatus, FirebaseUID={}, Status={}, Page={}, Purpose=upload_monitoring, DataAccessed=uploads.by_status",
                firebaseUid, status, page);

        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // For this simple implementation, we'll get all and filter
        // In production, you'd want to add this query to the repository
        List<FileUpload> uploads = uploadRepository.findByStatusAndCreatedAtBefore(
                status,
                Instant.now().plus(1, ChronoUnit.DAYS) // Get all up to tomorrow
        );

        Map<String, Object> response = new HashMap<>();
        response.put("uploads", uploads);
        response.put("totalCount", uploads.size());
        response.put("status", status);
        response.put("page", page);
        response.put("size", size);

        return ResponseEntity.ok(response);
    }

    /**
     * Generate weekly usage report.
     */
    @GetMapping("/reports/weekly")
    @Operation(summary = "Generate weekly report",
            description = "Creates a weekly summary of upload activity")
    public ResponseEntity<?> generateWeeklyReport() {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log report generation
        log.info("GDPR: Operation=generateWeeklyReport, FirebaseUID={}, Purpose=analytics_reporting, DataAccessed=upload.weekly_statistics",
                firebaseUid);

        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        // This is a simplified version - in production you'd want more sophisticated queries
        List<FileUpload> weeklyUploads = uploadRepository.findByUserIdAndStatusIn(
                        "", // Will get all users
                        List.of(FileUpload.UploadStatus.CONFIRMED, FileUpload.UploadStatus.WEBHOOK)
                ).stream()
                .filter(u -> u.getUploadTime().isAfter(weekAgo))
                .toList();

        long totalFiles = weeklyUploads.size();
        long totalSize = weeklyUploads.stream().mapToLong(FileUpload::getFileSize).sum();

        // Group by content type
        Map<String, Long> byContentType = weeklyUploads.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        FileUpload::getContentType,
                        java.util.stream.Collectors.counting()
                ));

        Map<String, Object> report = new HashMap<>();
        report.put("period", Map.of(
                "start", weekAgo,
                "end", Instant.now(),
                "days", 7
        ));
        report.put("summary", Map.of(
                "totalFiles", totalFiles,
                "totalSizeBytes", totalSize,
                "totalSizeMB", totalSize / (1024.0 * 1024.0),
                "averageFilesPerDay", totalFiles / 7.0
        ));
        report.put("byContentType", byContentType);
        report.put("generatedAt", Instant.now());

        return ResponseEntity.ok(report);
    }
}
