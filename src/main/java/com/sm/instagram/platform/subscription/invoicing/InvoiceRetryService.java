package com.sm.instagram.platform.subscription.invoicing;

import com.sm.instagram.platform.subscription.entity.InvoiceRecord;
import com.sm.instagram.platform.subscription.entity.InvoiceStatus;
import com.sm.instagram.platform.subscription.repository.InvoiceRecordRepository;
import com.sm.instagram.platform.registry.CompanyData;
import com.sm.instagram.platform.registry.CompanyDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceRetryService {

    private final InvoiceRecordRepository invoiceRecordRepo;
    private final CompanyDataRepository companyDataRepo;
    private final InvoicingPort invoicingPort;

    @Transactional
    public void retryFailedInvoices() {
        var cutoff = LocalDateTime.now().minusMinutes(15);
        var retryable = invoiceRecordRepo.findRetryable(cutoff);

        if (retryable.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failureCount = 0;
        int deadLetterCount = 0;

        for (var invoice : retryable) {
            try {
                processInvoice(invoice);
                successCount++;
            } catch (Exception e) {
                log.error("Invoice retry failed: invoiceId={}, error={}", invoice.getId(), e.getMessage());
                failureCount++;
            }
        }

        log.info("Invoice retry batch: total={}, success={}, failure={}, deadLetter={}",
                retryable.size(), successCount, failureCount, deadLetterCount);
    }

    /**
     * Process a single invoice by ID — re-fetches from DB to avoid detached entity issues
     * when called from @TransactionalEventListener(AFTER_COMMIT).
     * Uses REQUIRES_NEW because AFTER_COMMIT runs outside the original transaction.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void processOneInvoice(InvoiceRecord staleInvoice) {
        var invoice = invoiceRecordRepo.findById(staleInvoice.getId()).orElse(null);
        if (invoice == null) {
            log.warn("Invoice not found for immediate send: id={}", staleInvoice.getId());
            return;
        }
        processInvoice(invoice);
    }

    private void processInvoice(InvoiceRecord invoice) {
        // Skip zero-amount invoices (FREE plan — no Fakturownia invoice needed)
        if (invoice.getAmountPln().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            invoice.setStatus(InvoiceStatus.SENT);
            invoice.setErrorMessage("Skipped: zero amount");
            invoice.setLastAttemptAt(LocalDateTime.now());
            invoiceRecordRepo.save(invoice);
            log.info("Skipped zero-amount invoice: invoiceId={}", invoice.getId());
            return;
        }

        var companyData = companyDataRepo.findByUserId(invoice.getUser().getId()).orElse(null);
        if (companyData == null) {
            markFailed(invoice, "No company data found for user");
            return;
        }

        var address = companyData.getRegisteredAddress();
        String street = buildStreet(address);

        var request = new InvoicingPort.InvoiceRequest(
                companyData.getCompanyName(),
                companyData.getNip(),
                street,
                address != null ? address.get("city") : null,
                address != null ? address.get("postalCode") : null,
                "PL",
                resolvePlanName(invoice),
                invoice.getAmountPln(),
                null // stripeInvoiceId — could be enriched later
        );

        var result = invoicingPort.createInvoice(request);

        if (result.success()) {
            invoice.setStatus(InvoiceStatus.SENT);
            invoice.setFakturowniaInvoiceId(result.externalInvoiceId());
            invoice.setLastAttemptAt(LocalDateTime.now());
            invoice.setErrorMessage(null);
            invoiceRecordRepo.save(invoice);
            log.info("Invoice sent: invoiceId={}, fakturowniaId={}", invoice.getId(), result.externalInvoiceId());
        } else {
            markFailed(invoice, result.errorMessage());
        }
    }

    private void markFailed(InvoiceRecord invoice, String errorMessage) {
        invoice.setRetryCount(invoice.getRetryCount() + 1);
        invoice.setLastAttemptAt(LocalDateTime.now());
        invoice.setErrorMessage(errorMessage);

        if (invoice.getRetryCount() >= invoice.getMaxRetries()) {
            invoice.setStatus(InvoiceStatus.DEAD_LETTER);
            log.error("Invoice exhausted max retries → DEAD_LETTER: invoiceId={}, attempts={}",
                    invoice.getId(), invoice.getRetryCount());
        } else {
            invoice.setStatus(InvoiceStatus.FAILED);
            log.warn("Invoice retry failed: invoiceId={}, attempt={}/{}, error={}",
                    invoice.getId(), invoice.getRetryCount(), invoice.getMaxRetries(), errorMessage);
        }

        invoiceRecordRepo.save(invoice);
    }

    private String buildStreet(Map<String, String> address) {
        if (address == null) return null;
        String street = address.get("street");
        String building = address.get("building");
        String apartment = address.get("apartment");
        if (street == null) return null;
        return street
                + (building != null ? " " + building : "")
                + (apartment != null ? "/" + apartment : "");
    }

    private String resolvePlanName(InvoiceRecord invoice) {
        if (invoice.getBillingPeriod() != null && invoice.getBillingPeriod().getPlan() != null) {
            return invoice.getBillingPeriod().getPlan().getName();
        }
        return "Subscription";
    }
}
