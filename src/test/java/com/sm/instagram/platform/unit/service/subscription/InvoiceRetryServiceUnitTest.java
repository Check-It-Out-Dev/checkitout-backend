package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.registry.CompanyData;
import com.sm.instagram.platform.registry.CompanyDataRepository;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.invoicing.InvoiceRetryService;
import com.sm.instagram.platform.subscription.invoicing.InvoicingPort;
import com.sm.instagram.platform.subscription.repository.InvoiceRecordRepository;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceRetryServiceUnitTest {

    @Mock private InvoiceRecordRepository invoiceRecordRepo;
    @Mock private CompanyDataRepository companyDataRepo;
    @Mock private InvoicingPort invoicingPort;

    @InjectMocks private InvoiceRetryService retryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
    }

    // ========================================================================
    // retryFailedInvoices
    // ========================================================================

    @Nested
    @DisplayName("retryFailedInvoices")
    class RetryBatch {

        @Test
        @DisplayName("should return early when no retryable invoices")
        void shouldReturnEarlyWhenEmpty() {
            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of());

            retryService.retryFailedInvoices();

            verifyNoInteractions(companyDataRepo, invoicingPort);
        }

        @Test
        @DisplayName("should process each invoice in batch")
        void shouldProcessBatch() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(999L, "INV-001"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(invoice.getFakturowniaInvoiceId()).isEqualTo(999L);
        }

        @Test
        @DisplayName("should continue batch when one invoice fails")
        void shouldContinueOnFailure() {
            var invoice1 = createInvoice(InvoiceStatus.PENDING, 0);
            var invoice2 = createInvoice(InvoiceStatus.FAILED, 1);
            invoice2.setId(2L);
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice1, invoice2));
            when(companyDataRepo.findByUserId(1L))
                    .thenReturn(Optional.empty())  // first fails — no company data
                    .thenReturn(Optional.of(companyData)); // second succeeds
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(999L, "INV-002"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            // First invoice failed (no company data), second succeeded
            assertThat(invoice1.getStatus()).isEqualTo(InvoiceStatus.FAILED);
            assertThat(invoice2.getStatus()).isEqualTo(InvoiceStatus.SENT);
        }
    }

    // ========================================================================
    // processInvoice (via retryFailedInvoices)
    // ========================================================================

    @Nested
    @DisplayName("processInvoice — company data")
    class ProcessInvoice {

        @Test
        @DisplayName("should mark failed when no company data found")
        void shouldMarkFailedWhenNoCompanyData() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.empty());
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
            assertThat(invoice.getErrorMessage()).contains("No company data");
        }

        @Test
        @DisplayName("should handle null registered address")
        void shouldHandleNullAddress() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            var companyData = new CompanyData();
            companyData.setCompanyName("Test Co");
            companyData.setNip("1234567890");
            companyData.setRegisteredAddress(null);

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(100L, "INV-003"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            var captor = ArgumentCaptor.forClass(InvoicingPort.InvoiceRequest.class);
            verify(invoicingPort).createInvoice(captor.capture());
            assertThat(captor.getValue().buyerStreet()).isNull();
            assertThat(captor.getValue().buyerCity()).isNull();
        }

        @Test
        @DisplayName("should pass a deterministic per-record idempotency key (cio-{id}) — stable across retries")
        void shouldPassDeterministicIdempotencyKey() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(100L, "INV-777"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            var captor = ArgumentCaptor.forClass(InvoicingPort.InvoiceRequest.class);
            verify(invoicingPort).createInvoice(captor.capture());
            // The key ties Fakturownia's oid_unique dedup to THIS InvoiceRecord:
            // an immediate send and any later cron retry carry the same key, so a
            // success-with-lost-response can never mint a second real VAT invoice.
            assertThat(captor.getValue().idempotencyKey()).isEqualTo("cio-1");
        }

        @Test
        @DisplayName("retries of the SAME record carry the SAME idempotency key — the dedup contract")
        void shouldKeepTheSameKeyAcrossRetries() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            // first attempt: transient failure (e.g. timeout after remote create)
            when(invoicingPort.createInvoice(any()))
                    .thenReturn(InvoicingPort.InvoiceResult.failure("read timeout"))
                    .thenReturn(InvoicingPort.InvoiceResult.success(100L, "INV-778"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices(); // attempt 1 → FAILED
            retryService.retryFailedInvoices(); // attempt 2 → SENT

            var captor = ArgumentCaptor.forClass(InvoicingPort.InvoiceRequest.class);
            verify(invoicingPort, times(2)).createInvoice(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(InvoicingPort.InvoiceRequest::idempotencyKey)
                    .containsExactly("cio-1", "cio-1");
        }

        @Test
        @DisplayName("should mark failed when invoicing port returns failure")
        void shouldMarkFailedOnPortFailure() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.failure("API error 422"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
            assertThat(invoice.getErrorMessage()).isEqualTo("API error 422");
            assertThat(invoice.getRetryCount()).isEqualTo(1);
        }
    }

    // ========================================================================
    // markFailed — DEAD_LETTER escalation
    // ========================================================================

    @Nested
    @DisplayName("markFailed — retry exhaustion")
    class MarkFailed {

        @Test
        @DisplayName("should transition to DEAD_LETTER when max retries reached")
        void shouldDeadLetterOnMaxRetries() {
            var invoice = createInvoice(InvoiceStatus.FAILED, 4); // maxRetries=5, so next fail = exhausted
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.failure("Still failing"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DEAD_LETTER);
            assertThat(invoice.getRetryCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("should stay FAILED when retries remaining")
        void shouldStayFailedWhenRetriesRemaining() {
            var invoice = createInvoice(InvoiceStatus.FAILED, 1);
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.failure("Temporary error"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FAILED);
            assertThat(invoice.getRetryCount()).isEqualTo(2);
        }
    }

    // ========================================================================
    // buildStreet — address assembly
    // ========================================================================

    @Nested
    @DisplayName("buildStreet — address assembly")
    class BuildStreet {

        @Test
        @DisplayName("should return full street with building and apartment")
        void fullStreet() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            var companyData = createCompanyDataWithAddress("Marszalkowska", "10", "5");

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(1L, "INV"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            var captor = ArgumentCaptor.forClass(InvoicingPort.InvoiceRequest.class);
            verify(invoicingPort).createInvoice(captor.capture());
            assertThat(captor.getValue().buyerStreet()).isEqualTo("Marszalkowska 10/5");
        }

        @Test
        @DisplayName("should return street with building only (no apartment)")
        void streetWithBuildingOnly() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            var companyData = createCompanyDataWithAddress("Nowa", "44", null);

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(1L, "INV"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            var captor = ArgumentCaptor.forClass(InvoicingPort.InvoiceRequest.class);
            verify(invoicingPort).createInvoice(captor.capture());
            assertThat(captor.getValue().buyerStreet()).isEqualTo("Nowa 44");
        }

        @Test
        @DisplayName("should return street only (no building, no apartment)")
        void streetOnly() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            var companyData = createCompanyDataWithAddress("Dluga", null, null);

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(1L, "INV"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            var captor = ArgumentCaptor.forClass(InvoicingPort.InvoiceRequest.class);
            verify(invoicingPort).createInvoice(captor.capture());
            assertThat(captor.getValue().buyerStreet()).isEqualTo("Dluga");
        }
    }

    // ========================================================================
    // resolvePlanName
    // ========================================================================

    @Nested
    @DisplayName("resolvePlanName")
    class ResolvePlanName {

        @Test
        @DisplayName("should return plan name from billing period")
        void shouldReturnPlanName() {
            var plan = new SubscriptionPlan();
            plan.setName("ENTERPRISE");
            var period = new BillingPeriod();
            period.setPlan(plan);
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            invoice.setBillingPeriod(period);
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(1L, "INV"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            var captor = ArgumentCaptor.forClass(InvoicingPort.InvoiceRequest.class);
            verify(invoicingPort).createInvoice(captor.capture());
            assertThat(captor.getValue().planName()).isEqualTo("ENTERPRISE");
        }

        @Test
        @DisplayName("should return fallback when no billing period")
        void shouldReturnFallback() {
            var invoice = createInvoice(InvoiceStatus.PENDING, 0);
            invoice.setBillingPeriod(null);
            var companyData = createCompanyData();

            when(invoiceRecordRepo.findRetryable(any())).thenReturn(List.of(invoice));
            when(companyDataRepo.findByUserId(1L)).thenReturn(Optional.of(companyData));
            when(invoicingPort.createInvoice(any())).thenReturn(
                    InvoicingPort.InvoiceResult.success(1L, "INV"));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            retryService.retryFailedInvoices();

            var captor = ArgumentCaptor.forClass(InvoicingPort.InvoiceRequest.class);
            verify(invoicingPort).createInvoice(captor.capture());
            assertThat(captor.getValue().planName()).isEqualTo("Subscription");
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private InvoiceRecord createInvoice(InvoiceStatus status, int retryCount) {
        var invoice = new InvoiceRecord();
        invoice.setId(1L);
        invoice.setUser(testUser);
        invoice.setAmountPln(new BigDecimal("29.00"));
        invoice.setStatus(status);
        invoice.setRetryCount(retryCount);
        invoice.setMaxRetries(5);
        return invoice;
    }

    private CompanyData createCompanyData() {
        return createCompanyDataWithAddress("Marszalkowska", "10", "5");
    }

    private CompanyData createCompanyDataWithAddress(String street, String building, String apartment) {
        var data = new CompanyData();
        data.setCompanyName("Test Company Sp. z o.o.");
        data.setNip("1234567890");
        var address = new java.util.HashMap<String, String>();
        address.put("street", street);
        if (building != null) address.put("building", building);
        if (apartment != null) address.put("apartment", apartment);
        address.put("city", "Warszawa");
        address.put("postalCode", "00-001");
        data.setRegisteredAddress(address);
        return data;
    }
}
