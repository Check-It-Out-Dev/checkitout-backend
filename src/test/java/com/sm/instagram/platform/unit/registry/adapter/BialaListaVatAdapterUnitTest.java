package com.sm.instagram.platform.unit.registry.adapter;

import com.sm.instagram.platform.registry.adapter.bialista.BialaListaResponse;
import com.sm.instagram.platform.registry.adapter.bialista.BialaListaVatAdapter;
import com.sm.instagram.platform.registry.config.RegistryProperties;
import com.sm.instagram.platform.registry.port.VatStatusData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BialaListaVatAdapter.
 * Tests REST call, error handling, rate limiting, and disabled adapter behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BialaListaVatAdapter")
class BialaListaVatAdapterUnitTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RegistryProperties registryProperties;

    private BialaListaVatAdapter adapter;

    private static final String TEST_NIP = "5261040828";

    private RegistryProperties.VatConfig vatConfig;

    @BeforeEach
    void setUp() {
        vatConfig = new RegistryProperties.VatConfig();
        vatConfig.setEnabled(true);
        vatConfig.setBaseUrl("https://wl-api.mf.gov.pl");

        adapter = new BialaListaVatAdapter(restTemplate, registryProperties);
    }

    @Nested
    @DisplayName("Disabled adapter")
    class DisabledAdapter {

        @Test
        @DisplayName("should return empty result when adapter is disabled")
        void shouldReturnEmptyWhenDisabled() {
            vatConfig.setEnabled(false);
            when(registryProperties.getVat()).thenReturn(vatConfig);

            VatStatusData result = adapter.lookupVatStatus(TEST_NIP);

            assertThat(result).isNotNull();
            assertThat(result.isFound()).isFalse();
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("Successful lookup")
    class SuccessfulLookup {

        @Test
        @DisplayName("should return VAT status for active taxpayer")
        void shouldReturnVatStatusForActiveTaxpayer() {
            when(registryProperties.getVat()).thenReturn(vatConfig);

            BialaListaResponse response = buildBialaListaResponse("Czynny", "Test Company", List.of("PL12345678901234567890123456"));
            when(restTemplate.getForEntity(anyString(), eq(BialaListaResponse.class)))
                    .thenReturn(ResponseEntity.ok(response));

            VatStatusData result = adapter.lookupVatStatus(TEST_NIP);

            assertThat(result.isFound()).isTrue();
            assertThat(result.getStatusVat()).isEqualTo("Czynny");
            assertThat(result.getNormalizedStatus()).isEqualTo("ACTIVE");
            assertThat(result.getName()).isEqualTo("Test Company");
            assertThat(result.getAccountNumbers()).hasSize(1);
            assertThat(result.getCheckedAt()).isNotNull();
        }

        @Test
        @DisplayName("should handle exempt taxpayer status")
        void shouldHandleExemptTaxpayerStatus() {
            when(registryProperties.getVat()).thenReturn(vatConfig);

            BialaListaResponse response = buildBialaListaResponse("Zwolniony", "Exempt Corp", List.of());
            when(restTemplate.getForEntity(anyString(), eq(BialaListaResponse.class)))
                    .thenReturn(ResponseEntity.ok(response));

            VatStatusData result = adapter.lookupVatStatus(TEST_NIP);

            assertThat(result.isFound()).isTrue();
            assertThat(result.getNormalizedStatus()).isEqualTo("EXEMPT");
        }
    }

    @Nested
    @DisplayName("Empty/null responses")
    class EmptyResponses {

        @Test
        @DisplayName("should return not found when response body is null")
        void shouldReturnNotFoundWhenBodyNull() {
            when(registryProperties.getVat()).thenReturn(vatConfig);
            when(restTemplate.getForEntity(anyString(), eq(BialaListaResponse.class)))
                    .thenReturn(ResponseEntity.ok(null));

            VatStatusData result = adapter.lookupVatStatus(TEST_NIP);

            assertThat(result.isFound()).isFalse();
            assertThat(result.getCheckedAt()).isNotNull();
        }

        @Test
        @DisplayName("should return not found when subject is null")
        void shouldReturnNotFoundWhenSubjectNull() {
            when(registryProperties.getVat()).thenReturn(vatConfig);

            BialaListaResponse response = new BialaListaResponse();
            BialaListaResponse.Result resultObj = new BialaListaResponse.Result();
            resultObj.setSubject(null);
            response.setResult(resultObj);

            when(restTemplate.getForEntity(anyString(), eq(BialaListaResponse.class)))
                    .thenReturn(ResponseEntity.ok(response));

            VatStatusData result = adapter.lookupVatStatus(TEST_NIP);

            assertThat(result.isFound()).isFalse();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return not found for 404 response")
        void shouldReturnNotFoundFor404() {
            when(registryProperties.getVat()).thenReturn(vatConfig);
            when(restTemplate.getForEntity(anyString(), eq(BialaListaResponse.class)))
                    .thenThrow(HttpClientErrorException.NotFound.class);

            VatStatusData result = adapter.lookupVatStatus(TEST_NIP);

            assertThat(result.isFound()).isFalse();
            assertThat(result.getCheckedAt()).isNotNull();
        }

        @Test
        @DisplayName("should return not found for 429 rate limit")
        void shouldReturnNotFoundFor429RateLimit() {
            when(registryProperties.getVat()).thenReturn(vatConfig);
            when(restTemplate.getForEntity(anyString(), eq(BialaListaResponse.class)))
                    .thenThrow(HttpClientErrorException.TooManyRequests.class);

            VatStatusData result = adapter.lookupVatStatus(TEST_NIP);

            assertThat(result.isFound()).isFalse();
            assertThat(result.getCheckedAt()).isNotNull();
        }

        @Test
        @DisplayName("should return not found for generic REST error")
        void shouldReturnNotFoundForGenericError() {
            when(registryProperties.getVat()).thenReturn(vatConfig);
            when(restTemplate.getForEntity(anyString(), eq(BialaListaResponse.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            VatStatusData result = adapter.lookupVatStatus(TEST_NIP);

            assertThat(result.isFound()).isFalse();
            assertThat(result.getCheckedAt()).isNotNull();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private BialaListaResponse buildBialaListaResponse(String statusVat, String name, List<String> accountNumbers) {
        BialaListaResponse.Subject subject = new BialaListaResponse.Subject();
        subject.setStatusVat(statusVat);
        subject.setName(name);
        subject.setNip(TEST_NIP);
        subject.setRegon("012345678");
        subject.setAccountNumbers(accountNumbers);
        subject.setRegistrationLegalDate("2020-01-15");

        BialaListaResponse.Result result = new BialaListaResponse.Result();
        result.setSubject(subject);

        BialaListaResponse response = new BialaListaResponse();
        response.setResult(result);
        return response;
    }
}
