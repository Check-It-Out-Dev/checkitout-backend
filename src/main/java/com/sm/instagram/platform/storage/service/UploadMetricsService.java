package com.sm.instagram.platform.storage.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.monitoring.enabled", havingValue = "true", matchIfMissing = false)
public class UploadMetricsService {

    private final MeterRegistry meterRegistry;

    // Counters for different events
    private final Counter uploadRequests;
    private final Counter uploadSuccess;
    private final Counter uploadFailures;
    private final Counter rateLimitHits;
    private final Counter storageQuotaExceeded;

    // Gauges for current state
    private final AtomicInteger activeUploads = new AtomicInteger(0);
    private final AtomicLong totalStorageUsed = new AtomicLong(0);

    // Timers for performance
    private final Timer signedUrlGenerationTime;
    private final Timer uploadConfirmationTime;

    @Autowired
    public UploadMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.uploadRequests = Counter.builder("uploads.requests.total")
                .description("Total number of upload requests")
                .tag("type", "signed_url")
                .register(meterRegistry);

        this.uploadSuccess = Counter.builder("uploads.success.total")
                .description("Total number of successful uploads")
                .register(meterRegistry);

        this.uploadFailures = Counter.builder("uploads.failures.total")
                .description("Total number of failed uploads")
                .register(meterRegistry);

        this.rateLimitHits = Counter.builder("uploads.rate_limit.hits")
                .description("Number of rate limit violations")
                .register(meterRegistry);

        this.storageQuotaExceeded = Counter.builder("uploads.storage_quota.exceeded")
                .description("Number of storage quota violations")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("uploads.active", activeUploads, AtomicInteger::get)
                .description("Number of uploads currently in progress")
                .register(meterRegistry);

        Gauge.builder("storage.total.bytes", totalStorageUsed, AtomicLong::get)
                .description("Total storage used across all users")
                .register(meterRegistry);

        // Initialize timers
        this.signedUrlGenerationTime = Timer.builder("uploads.signed_url.generation.time")
                .description("Time taken to generate signed URLs")
                .register(meterRegistry);

        this.uploadConfirmationTime = Timer.builder("uploads.confirmation.time")
                .description("Time taken to confirm uploads")
                .register(meterRegistry);
    }

    // Counter increment methods
    public void recordUploadRequest() {
        uploadRequests.increment();
    }

    public void recordUploadSuccess(String userId, long fileSize) {
        log.info("GDPR: Operation=recordUploadSuccess, FirebaseUID={}, DataAccessed=upload_metrics, Purpose=performance_monitoring", userId);
        uploadSuccess.increment();

        // Add custom tags for detailed metrics
        Counter.builder("uploads.by_user")
                .tag("user_id", userId)
                .register(meterRegistry)
                .increment();

        // Record file size distribution
        meterRegistry.summary("uploads.file_size.bytes")
                .record(fileSize);
    }

    public void recordUploadFailure(String reason) {
        uploadFailures.increment();

        // Track failure reasons
        Counter.builder("uploads.failures.by_reason")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordRateLimitHit(String limitType) {
        rateLimitHits.increment();

        Counter.builder("uploads.rate_limit.by_type")
                .tag("limit_type", limitType) // hourly, daily, global
                .register(meterRegistry)
                .increment();
    }

    public void recordStorageQuotaExceeded(String userId) {
        log.warn("GDPR: Operation=recordStorageQuotaExceeded, FirebaseUID={}, DataAccessed=storage_quota, Purpose=quota_enforcement", userId);
        storageQuotaExceeded.increment();
    }

    public void recordAlert(String severity) {
        Counter.builder("alerts.total")
                .tag("severity", severity)
                .register(meterRegistry)
                .increment();
    }

    // Gauge update methods
    public void incrementActiveUploads() {
        activeUploads.incrementAndGet();
    }

    public void decrementActiveUploads() {
        activeUploads.decrementAndGet();
    }

    public void updateTotalStorage(long bytes) {
        totalStorageUsed.set(bytes);
    }

    // Timer methods
    public Timer.Sample startSignedUrlTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordSignedUrlTime(Timer.Sample sample) {
        sample.stop(signedUrlGenerationTime);
    }

    public Timer.Sample startConfirmationTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordConfirmationTime(Timer.Sample sample) {
        sample.stop(uploadConfirmationTime);
    }

    /**
     * Records detailed upload metrics for monitoring dashboards.
     */
    public void recordDetailedUploadMetrics(String userId, String contentType,
                                            long fileSize, long uploadDuration) {
        log.debug("GDPR: Operation=recordDetailedMetrics, FirebaseUID={}, DataAccessed=upload_performance_data, Purpose=analytics", userId);
        // Record by content type
        Counter.builder("uploads.by_content_type")
                .tag("content_type", contentType)
                .register(meterRegistry)
                .increment();

        // Record upload speed (bytes per second)
        if (uploadDuration > 0) {
            double speed = (double) fileSize / uploadDuration * 1000; // Convert to bytes/sec
            meterRegistry.gauge("uploads.speed.bytes_per_second", speed);
        }

        // Record time of day pattern
        int hour = java.time.LocalDateTime.now().getHour();
        Counter.builder("uploads.by_hour")
                .tag("hour", String.valueOf(hour))
                .register(meterRegistry)
                .increment();
    }
}
