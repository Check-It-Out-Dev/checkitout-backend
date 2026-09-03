package com.sm.instagram.platform.subscription.invoicing;

import com.sm.instagram.platform.subscription.invoicing.dto.FakturowniaCreateRequest;
import com.sm.instagram.platform.subscription.invoicing.dto.FakturowniaInvoiceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fakturownia API adapter implementing {@link InvoicingPort}.
 * Follows the BialaListaVatAdapter pattern: named RestTemplate, kill switch, non-blocking errors.
 *
 * <p>Creates VAT-exempt invoices (tax: "zw", exempt_tax_kind: "art113") for subscription payments.
 * Uses oid_unique to prevent duplicate invoices for the same Stripe payment.
 */
@Slf4j
@Component
public class FakturowniaAdapter implements InvoicingPort {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;
    private final FakturowniaProperties properties;

    public FakturowniaAdapter(
            @Qualifier("fakturowniaRestTemplate") RestTemplate restTemplate,
            FakturowniaProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public InvoiceResult createInvoice(InvoiceRequest request) {
        if (!properties.isConfigured()) {
            log.debug("Fakturownia adapter is disabled or unconfigured, returning failure");
            return InvoiceResult.failure("Fakturownia adapter is disabled");
        }

        // Invariant: never send an un-keyed invoice. oid_unique only dedups
        // when oid is present — a null oid would let a retry after a
        // success-with-lost-response create a duplicate real VAT invoice.
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            log.error("Refusing Fakturownia invoice without idempotency key: buyer={}",
                    maskNip(request.buyerTaxNo()));
            return InvoiceResult.failure("Missing idempotency key (oid) — refusing to send");
        }

        String today = LocalDate.now().format(DATE_FORMAT);
        String paymentTo = LocalDate.now().plusDays(14).format(DATE_FORMAT);

        var body = FakturowniaCreateRequest.builder()
                .apiToken(properties.getApiKey())
                .invoice(FakturowniaCreateRequest.InvoiceData.builder()
                        .kind("vat")
                        .departmentId(properties.getDepartmentId())
                        .sellDate(today)
                        .issueDate(today)
                        .paymentTo(paymentTo)
                        .paymentType("card")
                        .sellerName(properties.getSellerName())
                        .sellerTaxNo(properties.getSellerTaxNo())
                        .buyerName(request.buyerName())
                        .buyerTaxNo(request.buyerTaxNo())
                        .buyerCompany(true)
                        .buyerStreet(request.buyerStreet())
                        .buyerCity(request.buyerCity())
                        .buyerPostCode(request.buyerPostCode())
                        .buyerCountry(request.buyerCountry() != null ? request.buyerCountry() : "PL")
                        .exemptTaxKind(properties.getExemptTaxKind())
                        .currency("PLN")
                        .lang("pl")
                        .oid(request.idempotencyKey())
                        .oidUnique("yes")
                        .positions(List.of(FakturowniaCreateRequest.Position.builder()
                                .name("checkItOut " + request.planName() + " - subskrypcja miesięczna")
                                .quantity(1)
                                .tax("zw")
                                .totalPriceGross(request.amountPln())
                                .build()))
                        .build())
                .build();

        String url = properties.getBaseUrl() + "/invoices.json";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<FakturowniaInvoiceResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), FakturowniaInvoiceResponse.class);

            if (response.getBody() == null || response.getBody().getId() == null) {
                log.warn("Fakturownia returned empty response for invoice: buyer={}", maskNip(request.buyerTaxNo()));
                return InvoiceResult.failure("Empty response from Fakturownia");
            }

            var invoice = response.getBody();
            log.info("Fakturownia invoice created: id={}, number={}, buyer={}",
                    invoice.getId(), invoice.getNumber(), maskNip(request.buyerTaxNo()));

            return InvoiceResult.success(invoice.getId(), invoice.getNumber());

        } catch (HttpClientErrorException e) {
            log.warn("Fakturownia API error: status={}, body={}, buyer={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), maskNip(request.buyerTaxNo()));
            return InvoiceResult.failure("HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());

        } catch (RestClientException e) {
            log.warn("Fakturownia API call failed: error={}, buyer={}",
                    e.getMessage(), maskNip(request.buyerTaxNo()));
            return InvoiceResult.failure(e.getMessage());
        }
    }

    private String maskNip(String nip) {
        if (nip == null || nip.length() < 4) return "***";
        return nip.substring(0, 3) + "*******";
    }
}
