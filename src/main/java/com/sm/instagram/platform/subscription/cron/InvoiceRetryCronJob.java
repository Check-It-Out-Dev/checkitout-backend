package com.sm.instagram.platform.subscription.cron;

import com.sm.instagram.platform.subscription.invoicing.InvoiceRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron job that retries failed Fakturownia invoice creation.
 * Runs every 15 minutes with ShedLock (14m lock) to prevent concurrent execution.
 * Follows EmailCronJob pattern: feature toggle + delegate to service + per-item error handling.
 *
 * <p>Bean-gated by {@code app.payments.enabled}: invoicing only happens when payments are ON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payments.enabled", havingValue = "true", matchIfMissing = false)
public class InvoiceRetryCronJob {

    private final InvoiceRetryService invoiceRetryService;

    @Value("${subscription.invoice-retry.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${subscription.invoice-retry.cron:0 */15 * * * *}")
    @SchedulerLock(name = "invoiceRetryCron", lockAtMostFor = "14m", lockAtLeastFor = "1m")
    public void retryFailedInvoices() {
        if (!enabled) {
            return;
        }

        try {
            invoiceRetryService.retryFailedInvoices();
        } catch (Exception e) {
            log.error("Invoice retry cron failed unexpectedly", e);
        }
    }
}
