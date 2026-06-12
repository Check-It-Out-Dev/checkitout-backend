package com.sm.instagram.platform.subscription.invoicing;

import java.math.BigDecimal;

/**
 * Port interface for invoice generation.
 * Primary implementation: Fakturownia API.
 * Abstracted to allow swapping invoicing provider without changing business logic.
 */
public interface InvoicingPort {

    /**
     * Creates an invoice in the external invoicing system.
     *
     * @param request invoice creation data (buyer, amount, plan name)
     * @return result with external invoice ID, or failure info
     */
    InvoiceResult createInvoice(InvoiceRequest request);

    record InvoiceRequest(
            String buyerName,
            String buyerTaxNo,
            String buyerStreet,
            String buyerCity,
            String buyerPostCode,
            String buyerCountry,
            String planName,
            BigDecimal amountPln,
            String stripeInvoiceId
    ) {}

    record InvoiceResult(
            boolean success,
            Long externalInvoiceId,
            String invoiceNumber,
            String errorMessage
    ) {
        public static InvoiceResult success(Long externalInvoiceId, String invoiceNumber) {
            return new InvoiceResult(true, externalInvoiceId, invoiceNumber, null);
        }

        public static InvoiceResult failure(String errorMessage) {
            return new InvoiceResult(false, null, null, errorMessage);
        }
    }
}
