package com.sm.instagram.platform.storage.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The bodies {@code /upload/**} actually sends.
 *
 * <p>Same story as {@link UploadAdminResponses}: handlers typed {@code ResponseEntity<?>} carrying a
 * {@code HashMap}, which springdoc can only publish as {@code type: object}, which the generated
 * TypeScript client turns into {@code any}. The quota numbers a client needs in order to show an
 * upload button at all were among the things the contract did not describe.
 */
public final class UploadResponses {

    private UploadResponses() {
    }

    @Schema(description = "How much of one quota window is spent")
    public record Window(int used, int limit, int remaining) {

        static Window of(int used, int limit) {
            return new Window(used, limit, limit - used);
        }
    }

    @Schema(description = "Storage against the account's allowance, in megabytes")
    public record Storage(double usedMB, double limitMB, double usedPercentage) {

        static Storage of(double usedMB, double limitMB) {
            // A zero allowance is not a caller's problem to divide by.
            return new Storage(usedMB, limitMB, limitMB == 0 ? 0 : (usedMB * 100.0) / limitMB);
        }
    }

    @Schema(description = "Every limit an upload counts against")
    public record RateLimits(Window hourly, Window daily, Storage storage) {

        public static RateLimits from(
                com.sm.instagram.platform.storage.service.StorageRateLimitService.RateLimitStatus status) {
            return new RateLimits(
                    Window.of(status.getHourlyUsed(), status.getHourlyLimit()),
                    Window.of(status.getDailyUsed(), status.getDailyLimit()),
                    Storage.of(status.getStorageUsedMB(), status.getStorageLimitMB()));
        }
    }

    @Schema(description = "What this user has uploaded")
    public record UploadHistory(
            long totalFiles,
            long totalSizeBytes,
            long filesUploadedToday,
            long filesUploadedThisHour) {
    }

    /**
     * {@code uploadHistory} is absent when the tracking service is not configured, which is why it
     * is nullable rather than a second endpoint.
     */
    @Schema(description = "Quotas, and the upload history behind them")
    public record UploadStats(RateLimits rateLimits, UploadHistory uploadHistory) {
    }

    @Schema(description = "An upload the server has verified and counted against the quota")
    public record UploadConfirmation(
            String status,
            String uploadId,
            String filePath,
            String message) {
    }
}
