package com.sm.instagram.platform.notification.email;

import com.sm.instagram.platform.notification.Notification;
import com.sm.instagram.platform.notification.NotificationService;
import com.sm.instagram.platform.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cron job for processing notification email queue.
 * <p>
 * Runs every 15 minutes to:
 * 1. Fetch pending emails (email_enabled=true, email_sent=false, retry<3)
 * 2. Send each email via NotificationEmailService
 * 3. Update email_sent=true on success
 * 4. Increment retry_count on failure (max 3 attempts)
 * <p>
 * Why 15 minutes?
 * - Frequent enough for timely delivery
 * - Infrequent enough to batch naturally
 * - Aligns with common email patterns (Gmail, LinkedIn)
 * <p>
 * Pattern used by: Airbnb, Netflix, Uber for notification delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailCronJob {

    private final NotificationService notificationService;
    private final NotificationEmailService emailService;

    /**
     * Batch size per cron run (default 100).
     */
    @Value("${notification.email.batch-size:100}")
    private int batchSize;

    /**
     * Enable/disable email cron job (for testing/staging).
     */
    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    /**
     * Process pending notification emails.
     * <p>
     * Schedule: Every 15 minutes (configurable via notification.email.cron)
     * <p>
     * Default cron expression: "0 0/15 * * * *"
     * - Second: 0 (at the start of the minute)
     * - Minute: 0/15 (every 15 minutes starting from 0)
     * - Hour: * (every hour)
     * - Day of Month: * (every day)
     * - Month: * (every month)
     * - Day of Week: * (every day)
     * <p>
     * Transaction Strategy:
     * - No @Transactional on this method (each email processed independently)
     * - Individual email operations are transactional via service methods
     * - Failure of one email does NOT rollback others
     */
    @Scheduled(cron = "${notification.email.cron:0 0/15 * * * *}")
    @SchedulerLock(
            name = "notifications:emailQueueProcessor",
            lockAtMostFor = "14m",
            lockAtLeastFor = "1m"
    )
    public void processEmailQueue() {
        if (!emailEnabled) {
            log.debug("Email cron job disabled via configuration");
            return;
        }

        log.info("Starting email queue processing, batchSize={}", batchSize);

        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        try {
            // Fetch pending emails
            List<Notification> pendingEmails = notificationService.findPendingEmails(batchSize);

            log.info("Found {} pending emails to process", pendingEmails.size());

            for (Notification notification : pendingEmails) {
                try {
                    // IDEMPOTENCY CHECK: Skip if already sent (prevents duplicates on retry)
                    if (notification.getEmailSent()) {
                        log.warn("Skipping already-sent notification: {} (idempotency check)", notification.getId());
                        skippedCount++;
                        continue;
                    }

                    // Get recipient email
                    String recipientEmail = getRecipientEmail(notification);

                    if (recipientEmail == null || recipientEmail.isBlank()) {
                        log.warn("Skipping notification {}: no recipient email", notification.getId());
                        skippedCount++;
                        // Mark as failed so we don't retry indefinitely
                        notificationService.recordEmailFailure(notification, "No recipient email address");
                        continue;
                    }

                    // Send email
                    emailService.sendNotificationEmail(notification, recipientEmail);

                    // Mark as sent
                    notificationService.markEmailSent(notification);
                    successCount++;

                } catch (MailException e) {
                    // Record failure for retry
                    notificationService.recordEmailFailure(notification, e.getMessage());
                    failureCount++;

                    log.warn("Failed to send email for notification {}: {} (retry {})",
                            notification.getId(),
                            e.getMessage(),
                            notification.getEmailRetryCount());

                } catch (Exception e) {
                    // Unexpected error - log and continue with next
                    notificationService.recordEmailFailure(notification, e.getMessage());
                    failureCount++;

                    log.error("Unexpected error processing notification {}: {}",
                            notification.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Email queue processing failed: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;

        log.info("Email queue processing complete: success={}, failed={}, skipped={}, duration={}ms",
                successCount, failureCount, skippedCount, duration);
    }

    /**
     * Get recipient email address from notification.
     *
     * @param notification the notification
     * @return email address or null
     */
    private String getRecipientEmail(Notification notification) {
        User user = notification.getUser();
        if (user == null) {
            return null;
        }
        return user.getEmail();
    }
}