package com.sm.instagram.platform.storage.config;

import com.sm.instagram.platform.storage.service.UploadMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "storage.monitoring.alerts.enabled", havingValue = "true")
public class AlertConfiguration {
    private static final int ALERT_INTERVAL_MS = 300_000; // 5 minutes
    private static final int MIN_VOLUME_FOR_ALERT = 10;
    private static final String HIGH_FAILURE_RATE_TITLE = "High Upload Failure Rate";
    private static final String ERROR_SEVERITY = "ERROR";

    private final int rateLimitThreshold;
    private final double failureRateThreshold;
    private final String alertWebhookUrl;
    private final UploadMetricsService metricsService;

    // Tracking for rate calculations
    private final AtomicInteger recentUploads = new AtomicInteger(0);
    private final AtomicInteger recentFailures = new AtomicInteger(0);
    private final AtomicLong lastRateLimitAlert = new AtomicLong(0);

    public AlertConfiguration(
            @Value("${alerts.rate-limit.threshold:100}") int rateLimitThreshold,
            @Value("${alerts.failure-rate.threshold:0.1}") double failureRateThreshold,
            @Value("${alerts.webhook.url:}") String alertWebhookUrl,
            UploadMetricsService metricsService) {
        this.rateLimitThreshold = rateLimitThreshold;
        this.failureRateThreshold = failureRateThreshold;
        this.alertWebhookUrl = alertWebhookUrl;
        this.metricsService = metricsService;
    }

    /**
     * Monitors metrics and triggers alerts.
     * Runs every minute.
     */
    @Scheduled(fixedDelay = 60000)
    public void checkMetricsAndAlert() {
        if (metricsService == null) {
            return;
        }

        try {
            checkFailureRate();
            checkRateLimitViolations();
            checkStorageUsage();
            checkSystemHealth();
        } catch (Exception e) {
            log.error("Error in alert monitoring", e);
        }
    }

    private void checkFailureRate() {
        // Calculate failure rate over last 5 minutes
        int totalAttempts = recentUploads.get();
        int failures = recentFailures.get();

        if (totalAttempts > MIN_VOLUME_FOR_ALERT) { // Only alert if significant volume
            double failureRate = (double) failures / totalAttempts;

            if (failureRate > failureRateThreshold) {
                String message = String.format("Failure rate is %.1f%% (threshold: %.1f%%). " +
                                "Failed: %d, Total: %d",
                        failureRate * 100, failureRateThreshold * 100,
                        failures, totalAttempts);
                sendAlert(HIGH_FAILURE_RATE_TITLE, message, ERROR_SEVERITY);
                
                // GDPR logging for alerts that might involve user data
                log.warn("GDPR: Operation=alert_triggered, AlertType=high_failure_rate, Purpose=monitoring, LegalBasis=legitimate_interest");
            }
        }
    }

    private void checkRateLimitViolations() {
        // Check if there's been excessive rate limiting
        long now = System.currentTimeMillis();
        if (now - lastRateLimitAlert.get() > ALERT_INTERVAL_MS) {
            // Alert logic would go here when rate limits are being hit frequently
            // For now, we just update the timestamp when called
            log.debug("Checking rate limit violations at {}", now);
        }
    }

    private void checkStorageUsage() {
        // Alert if total storage usage is approaching limits
        // This would query the database for total usage
    }

    private void checkSystemHealth() {
        // Check if key services are responding
        // Alert if any critical service is down
    }

    /**
     * Sends an alert through configured channels.
     * In production, this would integrate with PagerDuty, Slack, etc.
     */
    private void sendAlert(String title, String message, String severity) {
        log.error("ALERT [{}]: {} - {}", severity, title, message);

        // Send to external alerting system
        if (alertWebhookUrl != null && !alertWebhookUrl.isEmpty()) {
            // Send webhook notification
            sendWebhookAlert(title, message, severity);
        }

        // Record alert metric
        if (metricsService != null) {
            metricsService.recordAlert(severity);
        }
    }

    private void sendWebhookAlert(String title, String message, String severity) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> alertPayload = new HashMap<>();
            alertPayload.put("title", title);
            alertPayload.put("message", message);
            alertPayload.put("severity", severity);
            alertPayload.put("timestamp", System.currentTimeMillis());
            alertPayload.put("service", "file-upload-system");

            restTemplate.postForObject(alertWebhookUrl, alertPayload, String.class);
            log.info("Alert sent to webhook: {}", title);
        } catch (Exception e) {
            log.error("Failed to send webhook alert", e);
        }
    }

    // Public methods for updating counters from other services
    public void recordUploadAttempt() {
        recentUploads.incrementAndGet();
    }

    public void recordUploadFailure() {
        recentFailures.incrementAndGet();
    }

    public void recordRateLimitHit() {
        lastRateLimitAlert.set(System.currentTimeMillis());
    }
}
