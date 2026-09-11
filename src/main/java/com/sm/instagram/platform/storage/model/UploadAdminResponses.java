package com.sm.instagram.platform.storage.model;

import com.sm.instagram.platform.storage.entity.FileUpload;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The bodies {@code /admin/uploads/**} actually sends.
 *
 * <p>All five handlers returned {@code ResponseEntity<?>} carrying a {@code HashMap}, so springdoc
 * could publish nothing but {@code type: object} and the generated TypeScript client typed every one
 * of them {@code any}. The keys were never in doubt -- each map is built literally, a few lines
 * above its return -- so the document was uninformative for no reason at all.
 *
 * <p>Records, because the wire shape is the whole content: Jackson serialises a record by its
 * component names, so every key below is byte-for-byte the one the map had. Grouped in one file
 * because they exist to describe one controller and are meaningless apart from it.
 */
public final class UploadAdminResponses {

    private UploadAdminResponses() {
    }

    @Schema(description = "Counts and storage totals across every user")
    public record SystemStats(
            long totalUploads,
            long pendingUploads,
            long uploadsLast24Hours,
            long uploadsLastHour,
            long totalStorageBytes,
            double totalStorageMB,
            Instant timestamp) {
    }

    @Schema(description = "One user's uploads, with the quota they count against")
    public record UserUploads(
            List<FileUpload> uploads,
            UserStatistics statistics,
            UserRateLimits rateLimits) {
    }

    @Schema(description = "Upload counts for one user")
    public record UserStatistics(
            long totalFiles,
            long totalSizeBytes,
            long filesUploadedToday,
            long filesUploadedThisHour) {
    }

    @Schema(description = "How much of the user's hourly, daily and storage quota is spent")
    public record UserRateLimits(
            int hourlyUsed,
            int hourlyLimit,
            int dailyUsed,
            int dailyLimit,
            double storageUsedMB,
            double storageLimitMB) {
    }

    @Schema(description = "The outcome of a manual orphaned-upload cleanup")
    public record CleanupOutcome(
            String status,
            String message,
            Instant timestamp) {
    }

    @Schema(description = "A page of uploads in one status")
    public record UploadsByStatus(
            List<FileUpload> uploads,
            int totalCount,
            FileUpload.UploadStatus status,
            int page,
            int size) {
    }

    @Schema(description = "Seven days of upload activity")
    public record WeeklyReport(
            ReportPeriod period,
            WeeklySummary summary,
            @Schema(description = "Uploads in the period, counted per content type")
            Map<String, Long> byContentType,
            Instant generatedAt) {
    }

    /**
     * {@code periodStart} and {@code periodEnd} rather than {@code start} and {@code end}: the
     * document is checked for fields that claim {@code format: date-time} without an offset to
     * back it, and that check reads property names across the whole document. Two words as common
     * as start and end would have to be allowed everywhere to allow them here.
     */
    @Schema(description = "The window a report covers")
    public record ReportPeriod(Instant periodStart, Instant periodEnd, int days) {
    }

    @Schema(description = "Totals over the reporting window")
    public record WeeklySummary(
            long totalFiles,
            long totalSizeBytes,
            double totalSizeMB,
            double averageFilesPerDay) {
    }
}
