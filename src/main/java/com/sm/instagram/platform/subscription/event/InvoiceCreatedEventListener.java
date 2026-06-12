package com.sm.instagram.platform.subscription.event;

import com.sm.instagram.platform.subscription.invoicing.InvoiceRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for InvoiceCreatedEvent and attempts immediate Fakturownia send.
 * AFTER_COMMIT ensures the InvoiceRecord is persisted before we try to send it.
 * Failures are caught and logged — InvoiceRetryCronJob (every 15min) is the safety net.
 *
 * <p>Bean-gated by {@code app.payments.enabled}: defense-in-depth — InvoiceCreatedEvent
 * is only published from {@link com.sm.instagram.platform.subscription.SubscriptionService#handleInvoicePaid}
 * which is itself guarded, but gating the listener too makes the code obviously safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payments.enabled", havingValue = "true", matchIfMissing = false)
public class InvoiceCreatedEventListener {

    private final InvoiceRetryService invoiceRetryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceCreated(InvoiceCreatedEvent event) {
        var invoice = event.getInvoice();
        try {
            log.info("Attempting immediate Fakturownia send: invoiceId={}, amount={} PLN",
                    invoice.getId(), invoice.getAmountPln());
            invoiceRetryService.processOneInvoice(invoice);
        } catch (Exception e) {
            log.warn("Immediate Fakturownia send failed (cron will retry): invoiceId={}, error={}",
                    invoice.getId(), e.getMessage());
            // Don't re-throw — the invoice is PENDING in DB, cron picks it up
        }
    }
}
