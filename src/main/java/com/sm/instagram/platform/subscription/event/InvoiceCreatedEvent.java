package com.sm.instagram.platform.subscription.event;

import com.sm.instagram.platform.subscription.entity.InvoiceRecord;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when an InvoiceRecord is created and needs to be sent to Fakturownia.
 * Handled by InvoiceCreatedEventListener AFTER_COMMIT — so the invoice exists in DB before send attempt.
 * If the send fails, InvoiceRetryCronJob (every 15min) is the safety net.
 */
@Getter
public class InvoiceCreatedEvent extends ApplicationEvent {

    private final InvoiceRecord invoice;

    public InvoiceCreatedEvent(Object source, InvoiceRecord invoice) {
        super(source);
        this.invoice = invoice;
    }
}
