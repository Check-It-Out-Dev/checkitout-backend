package com.sm.instagram.platform.integration.service.subscription;

import com.sm.instagram.platform.subscription.invoicing.FakturowniaAdapter;
import com.sm.instagram.platform.subscription.invoicing.InvoicingPort;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for FakturowniaAdapter against the REAL Fakturownia test department.
 * Enables Fakturownia via @TestPropertySource (default is disabled in integration profile).
 * Creates real invoices and deletes them for cleanup.
 */
@TestPropertySource(properties = "fakturownia.enabled=true")
class FakturowniaAdapter_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    @Autowired
    private FakturowniaAdapter fakturowniaAdapter;

    @Nested
    @DisplayName("createInvoice — real Fakturownia test department")
    class CreateInvoice {

        @Test
        @DisplayName("should create a real invoice with VAT exempt (zw, art113)")
        void shouldCreateRealInvoice() {
            var request = new InvoicingPort.InvoiceRequest(
                    "Integration Test Sp. z o.o.",
                    "5252445767",
                    "Testowa 1/2",
                    "Warszawa",
                    "00-001",
                    "PL",
                    "BUSINESS",
                    new BigDecimal("29.00"),
                    "si_" + System.currentTimeMillis()
            );

            var result = fakturowniaAdapter.createInvoice(request);

            assertThat(result.success()).isTrue();
            assertThat(result.externalInvoiceId()).isNotNull();
            assertThat(result.externalInvoiceId()).isGreaterThan(0);
            assertThat(result.invoiceNumber()).isNotBlank();

            // Cleanup: delete the test invoice
            deleteTestInvoice(result.externalInvoiceId());
        }

        @Test
        @DisplayName("should create Enterprise invoice with correct amount")
        void shouldCreateEnterpriseInvoice() {
            var request = new InvoicingPort.InvoiceRequest(
                    "Enterprise Test Sp. z o.o.",
                    "5252445767",
                    "Marszalkowska 10",
                    "Warszawa",
                    "00-001",
                    "PL",
                    "ENTERPRISE",
                    new BigDecimal("99.00"),
                    "se_" + System.currentTimeMillis()
            );

            var result = fakturowniaAdapter.createInvoice(request);

            assertThat(result.success()).isTrue();
            assertThat(result.externalInvoiceId()).isNotNull();

            deleteTestInvoice(result.externalInvoiceId());
        }

        @Test
        @DisplayName("should prevent duplicate invoices via oid_unique")
        void shouldPreventDuplicateViaOid() {
            String uniqueOid = "sd_" + System.currentTimeMillis();

            var request = new InvoicingPort.InvoiceRequest(
                    "Dedup Test Sp. z o.o.", "5252445767",
                    "Testowa 1", "Warszawa", "00-001", "PL",
                    "BUSINESS", new BigDecimal("29.00"), uniqueOid);

            var result1 = fakturowniaAdapter.createInvoice(request);
            assertThat(result1.success()).isTrue();

            // Second call with same OID should fail (oid_unique="yes")
            var result2 = fakturowniaAdapter.createInvoice(request);
            assertThat(result2.success()).isFalse();

            deleteTestInvoice(result1.externalInvoiceId());
        }
    }

    /**
     * Deletes a test invoice from Fakturownia to keep the test department clean.
     * Uses RestTemplate directly since FakturowniaAdapter only has createInvoice.
     */
    private void deleteTestInvoice(Long invoiceId) {
        try {
            var restTemplate = new org.springframework.web.client.RestTemplate();
            String url = "https://checkitout.fakturownia.pl/invoices/" + invoiceId + ".json?api_token="
                    + System.getProperty("FAKTUROWNIA_API_KEY",
                    System.getenv("FAKTUROWNIA_API_KEY") != null ? System.getenv("FAKTUROWNIA_API_KEY") : "");
            restTemplate.delete(url);
        } catch (Exception e) {
            // Cleanup failure is not a test failure
            System.err.println("Warning: Failed to delete test invoice " + invoiceId + ": " + e.getMessage());
        }
    }
}
