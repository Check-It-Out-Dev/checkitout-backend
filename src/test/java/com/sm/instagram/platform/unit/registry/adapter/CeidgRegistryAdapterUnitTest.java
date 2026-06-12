package com.sm.instagram.platform.unit.registry.adapter;

import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.registry.adapter.ceidg.CeidgRegistryAdapter;
import com.sm.instagram.platform.registry.adapter.ceidg.CeidgResponse;
import com.sm.instagram.platform.registry.config.RegistryProperties;
import com.sm.instagram.platform.registry.port.SoleProprietorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CeidgRegistryAdapter.
 * Tests REST call, authentication, response mapping, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CeidgRegistryAdapter")
class CeidgRegistryAdapterUnitTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RegistryProperties registryProperties;

    private CeidgRegistryAdapter adapter;

    private static final String TEST_NIP = "5261040828";
    private static final String TEST_API_KEY = "ceidg-bearer-token-123";

    private RegistryProperties.CeidgConfig ceidgConfig;

    @BeforeEach
    void setUp() {
        ceidgConfig = new RegistryProperties.CeidgConfig();
        ceidgConfig.setEnabled(true);
        ceidgConfig.setApiKey(TEST_API_KEY);
        ceidgConfig.setEnvironment("production");

        adapter = new CeidgRegistryAdapter(restTemplate, registryProperties);
    }

    @Nested
    @DisplayName("Disabled adapter")
    class DisabledAdapter {

        @Test
        @DisplayName("should return empty result when adapter is disabled")
        void shouldReturnEmptyWhenDisabled() {
            ceidgConfig.setEnabled(false);
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);

            SoleProprietorData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result).isNotNull();
            assertThat(result.isFound()).isFalse();
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("Missing API key")
    class MissingApiKey {

        @Test
        @DisplayName("should return not found when API key is null")
        void shouldReturnNotFoundWhenApiKeyNull() {
            ceidgConfig.setApiKey(null);
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);

            SoleProprietorData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.isFound()).isFalse();
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("should return not found when API key is blank")
        void shouldReturnNotFoundWhenApiKeyBlank() {
            ceidgConfig.setApiKey("  ");
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);

            SoleProprietorData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.isFound()).isFalse();
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("Successful lookup")
    class SuccessfulLookup {

        @Test
        @DisplayName("should map CEIDG response to SoleProprietorData")
        void shouldMapCeidgResponseToSoleProprietorData() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);

            CeidgResponse response = buildCeidgResponse();
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenReturn(ResponseEntity.ok(response));

            SoleProprietorData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.isFound()).isTrue();
            assertThat(result.getBusinessName()).isEqualTo("Jan Kowalski Consulting");
            assertThat(result.getNip()).isEqualTo(TEST_NIP);
            assertThat(result.getOwnerFirstName()).isEqualTo("Jan");
            assertThat(result.getOwnerLastName()).isEqualTo("Kowalski");
            assertThat(result.getCity()).isEqualTo("Kraków");
            assertThat(result.getStatus()).isEqualTo("AKTYWNY");
        }

        @Test
        @DisplayName("should map PKD codes from CEIDG response")
        void shouldMapPkdCodesFromCeidgResponse() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);

            CeidgResponse response = buildCeidgResponseWithPkd();
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenReturn(ResponseEntity.ok(response));

            SoleProprietorData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.getPkdCodes()).hasSize(2);
            assertThat(result.getPkdCodes().get(0).get("code")).isEqualTo("62.01.Z");
            assertThat(result.getPkdCodes().get(0).get("isPrimary")).isEqualTo(true);
        }

        @Test
        @DisplayName("should set Bearer auth header with API key")
        void shouldSetBearerAuthHeader() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);

            CeidgResponse response = buildCeidgResponse();
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenReturn(ResponseEntity.ok(response));

            adapter.lookupByNip(TEST_NIP);

            ArgumentCaptor<HttpEntity<Void>> captor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), captor.capture(), eq(CeidgResponse.class));

            String authHeader = captor.getValue().getHeaders().getFirst("Authorization");
            assertThat(authHeader).isEqualTo("Bearer " + TEST_API_KEY);
        }
    }

    @Nested
    @DisplayName("Empty responses")
    class EmptyResponses {

        @Test
        @DisplayName("should return not found when response body is null")
        void shouldReturnNotFoundWhenBodyNull() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenReturn(ResponseEntity.ok(null));

            SoleProprietorData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.isFound()).isFalse();
        }

        @Test
        @DisplayName("should return not found when firmy list is empty")
        void shouldReturnNotFoundWhenFirmyEmpty() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);

            CeidgResponse response = new CeidgResponse();
            response.setFirmy(List.of());
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenReturn(ResponseEntity.ok(response));

            SoleProprietorData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.isFound()).isFalse();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should return not found for 404 response")
        void shouldReturnNotFoundFor404() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenThrow(HttpClientErrorException.NotFound.class);

            SoleProprietorData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.isFound()).isFalse();
        }

        @Test
        @DisplayName("should throw ExternalServiceException for 401 Unauthorized")
        void shouldThrowFor401Unauthorized() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenThrow(HttpClientErrorException.Unauthorized.class);

            assertThatThrownBy(() -> adapter.lookupByNip(TEST_NIP))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("ceidg_auth_failed");
        }

        @Test
        @DisplayName("should throw ExternalServiceException for 403 Forbidden")
        void shouldThrowFor403Forbidden() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenThrow(HttpClientErrorException.Forbidden.class);

            assertThatThrownBy(() -> adapter.lookupByNip(TEST_NIP))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("ceidg_auth_failed");
        }

        @Test
        @DisplayName("should throw ExternalServiceException for generic REST error")
        void shouldThrowForGenericRestError() {
            when(registryProperties.getCeidg()).thenReturn(ceidgConfig);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CeidgResponse.class)))
                    .thenThrow(new RestClientException("Connection timeout"));

            assertThatThrownBy(() -> adapter.lookupByNip(TEST_NIP))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("service_unavailable");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CeidgResponse buildCeidgResponse() {
        CeidgResponse.Wlasciciel owner = new CeidgResponse.Wlasciciel();
        owner.setImie("Jan");
        owner.setNazwisko("Kowalski");

        CeidgResponse.Adres adres = new CeidgResponse.Adres();
        adres.setUlica("Floriańska");
        adres.setBudynek("15");
        adres.setLokal("3");
        adres.setMiasto("Kraków");
        adres.setKodPocztowy("31-021");
        adres.setWojewodztwo("małopolskie");

        CeidgResponse.Firma firma = new CeidgResponse.Firma();
        firma.setNazwa("Jan Kowalski Consulting");
        firma.setNip(TEST_NIP);
        firma.setRegon("123456789");
        firma.setStatus("AKTYWNY");
        firma.setStartDate("2020-01-15");
        firma.setWlasciciel(owner);
        firma.setAdresGlownegoMiejscaWykonywaniaDzialalnosci(adres);

        CeidgResponse response = new CeidgResponse();
        response.setFirmy(List.of(firma));
        return response;
    }

    private CeidgResponse buildCeidgResponseWithPkd() {
        CeidgResponse response = buildCeidgResponse();

        CeidgResponse.Pkd pkd1 = new CeidgResponse.Pkd();
        pkd1.setKod("62.01.Z");
        pkd1.setNazwa("Software development");
        pkd1.setPrzewazajace(true);

        CeidgResponse.Pkd pkd2 = new CeidgResponse.Pkd();
        pkd2.setKod("62.02.Z");
        pkd2.setNazwa("IT consulting");
        pkd2.setPrzewazajace(false);

        response.getFirmy().getFirst().setPkd(List.of(pkd1, pkd2));
        return response;
    }
}
