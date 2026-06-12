package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.subscription.invoicing.FakturowniaAdapter;
import com.sm.instagram.platform.subscription.invoicing.FakturowniaProperties;
import com.sm.instagram.platform.subscription.invoicing.InvoicingPort;
import com.sm.instagram.platform.subscription.invoicing.dto.FakturowniaInvoiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FakturowniaAdapterUnitTest {

    @Mock private RestTemplate restTemplate;
    private FakturowniaProperties properties;
    private FakturowniaAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new FakturowniaProperties();
        properties.setEnabled(true);
        properties.setApiKey("test_api_key");
        properties.setDomain("checkitout");
        properties.setDepartmentId(1878648);
        adapter = new FakturowniaAdapter(restTemplate, properties);
    }

    private InvoicingPort.InvoiceRequest testRequest() {
        return new InvoicingPort.InvoiceRequest(
                "Test Company Sp. z o.o.", "1234567890",
                "Marszalkowska 10/5", "Warszawa", "00-001", "PL",
                "BUSINESS", new BigDecimal("29.00"), "stripe_inv_123"
        );
    }

    @Nested
    @DisplayName("createInvoice — kill switch")
    class KillSwitch {

        @Test
        @DisplayName("should return failure when adapter is disabled")
        void shouldReturnFailureWhenDisabled() {
            properties.setEnabled(false);

            var result = adapter.createInvoice(testRequest());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("disabled");
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("createInvoice — happy path")
    class HappyPath {

        @Test
        @DisplayName("should return success with invoice ID and number")
        void shouldReturnSuccess() {
            var response = new FakturowniaInvoiceResponse();
            response.setId(476301568L);
            response.setNumber("1/03/2026");

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(FakturowniaInvoiceResponse.class)))
                    .thenReturn(new ResponseEntity<>(response, HttpStatus.CREATED));

            var result = adapter.createInvoice(testRequest());

            assertThat(result.success()).isTrue();
            assertThat(result.externalInvoiceId()).isEqualTo(476301568L);
            assertThat(result.invoiceNumber()).isEqualTo("1/03/2026");
        }

        @Test
        @DisplayName("should use default country PL when buyerCountry is null")
        void shouldDefaultCountryToPL() {
            var request = new InvoicingPort.InvoiceRequest(
                    "Test Co", "1234567890", "Street 1", "City", "00-001",
                    null, "BUSINESS", new BigDecimal("29.00"), null);

            var response = new FakturowniaInvoiceResponse();
            response.setId(1L);
            response.setNumber("1/04/2026");

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(FakturowniaInvoiceResponse.class)))
                    .thenReturn(new ResponseEntity<>(response, HttpStatus.CREATED));

            var result = adapter.createInvoice(request);

            assertThat(result.success()).isTrue();
            var captor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(FakturowniaInvoiceResponse.class));
            assertThat(captor.getValue().getBody().toString()).contains("PL");
        }
    }

    @Nested
    @DisplayName("createInvoice — error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return failure when response body is null")
        void shouldFailOnNullBody() {
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(FakturowniaInvoiceResponse.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.CREATED));

            var result = adapter.createInvoice(testRequest());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Empty response");
        }

        @Test
        @DisplayName("should return failure when response body has null ID")
        void shouldFailOnNullId() {
            var response = new FakturowniaInvoiceResponse();
            response.setId(null);

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(FakturowniaInvoiceResponse.class)))
                    .thenReturn(new ResponseEntity<>(response, HttpStatus.CREATED));

            var result = adapter.createInvoice(testRequest());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Empty response");
        }

        @Test
        @DisplayName("should return failure on HttpClientErrorException")
        void shouldFailOnHttpClientError() {
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(FakturowniaInvoiceResponse.class)))
                    .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "Validation error"));

            var result = adapter.createInvoice(testRequest());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("HTTP 422");
        }

        @Test
        @DisplayName("should return failure on RestClientException (timeout, connection)")
        void shouldFailOnRestClientException() {
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(FakturowniaInvoiceResponse.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            var result = adapter.createInvoice(testRequest());

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Connection refused");
        }
    }

    @Nested
    @DisplayName("InvoiceResult factory methods")
    class ResultFactories {

        @Test
        @DisplayName("success() should have correct fields")
        void successResult() {
            var result = InvoicingPort.InvoiceResult.success(123L, "INV-001");
            assertThat(result.success()).isTrue();
            assertThat(result.externalInvoiceId()).isEqualTo(123L);
            assertThat(result.invoiceNumber()).isEqualTo("INV-001");
            assertThat(result.errorMessage()).isNull();
        }

        @Test
        @DisplayName("failure() should have correct fields")
        void failureResult() {
            var result = InvoicingPort.InvoiceResult.failure("some error");
            assertThat(result.success()).isFalse();
            assertThat(result.externalInvoiceId()).isNull();
            assertThat(result.invoiceNumber()).isNull();
            assertThat(result.errorMessage()).isEqualTo("some error");
        }
    }
}
