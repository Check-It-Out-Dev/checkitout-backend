package com.sm.instagram.platform.unit.registry.adapter;

import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.registry.adapter.gus.GusBir1RegistryAdapter;
import com.sm.instagram.platform.registry.config.RegistryProperties;
import com.sm.instagram.platform.registry.port.CompanyRegistryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GusBir1RegistryAdapter.
 * Tests the 4-step SOAP flow, error handling, and disabled adapter behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GusBir1RegistryAdapter")
class GusBir1RegistryAdapterUnitTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RegistryProperties registryProperties;

    private GusBir1RegistryAdapter adapter;

    private static final String TEST_NIP = "5261040828";
    private static final String TEST_SESSION_ID = "abc123session";
    private static final String TEST_SERVICE_URL = "https://wyszukiwarkaregontest.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc";
    private static final String TEST_API_KEY = "testApiKey123";

    private RegistryProperties.GusConfig gusConfig;

    @BeforeEach
    void setUp() {
        gusConfig = new RegistryProperties.GusConfig();
        gusConfig.setApiKey(TEST_API_KEY);
        gusConfig.setEnvironment("test");
        gusConfig.setEnabled(true);

        adapter = new GusBir1RegistryAdapter(restTemplate, registryProperties);
    }

    @Nested
    @DisplayName("Disabled adapter")
    class DisabledAdapter {

        @Test
        @DisplayName("should return empty result when adapter is disabled")
        void shouldReturnEmptyWhenDisabled() {
            gusConfig.setEnabled(false);
            when(registryProperties.getGus()).thenReturn(gusConfig);

            CompanyRegistryData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result).isNotNull();
            assertThat(result.isFound()).isFalse();
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("Login step")
    class LoginStep {

        @Test
        @DisplayName("should throw when API key is missing")
        void shouldThrowWhenApiKeyMissing() {
            gusConfig.setApiKey(null);
            when(registryProperties.getGus()).thenReturn(gusConfig);

            assertThatThrownBy(() -> adapter.lookupByNip(TEST_NIP))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("gus_key_missing");
        }

        @Test
        @DisplayName("should throw when API key is blank")
        void shouldThrowWhenApiKeyBlank() {
            gusConfig.setApiKey("  ");
            when(registryProperties.getGus()).thenReturn(gusConfig);

            assertThatThrownBy(() -> adapter.lookupByNip(TEST_NIP))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("gus_key_missing");
        }

        @Test
        @DisplayName("should throw when login returns null session ID")
        void shouldThrowWhenLoginReturnsNullSession() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            // Login returns empty session
            mockSoapCall(buildEmptyLoginResponse());

            assertThatThrownBy(() -> adapter.lookupByNip(TEST_NIP))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("gus_login_failed");
        }
    }

    @Nested
    @DisplayName("Full SOAP flow")
    class FullSoapFlow {

        @Test
        @DisplayName("should complete 4-step flow for legal person (P)")
        void shouldComplete4StepFlowForLegalPerson() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            // 4 SOAP calls: login, search, full report, PKD report + logout
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))     // login
                    .thenReturn(ResponseEntity.ok(buildSearchResponse("P")))                // search
                    .thenReturn(ResponseEntity.ok(buildFullReportResponse("praw_")))        // full report
                    .thenReturn(ResponseEntity.ok(buildPkdReportResponse("pkd_")))          // PKD report
                    .thenReturn(ResponseEntity.ok("<WylogujResult>true</WylogujResult>"));  // logout

            CompanyRegistryData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result).isNotNull();
            assertThat(result.isFound()).isTrue();
            assertThat(result.getNip()).isEqualTo(TEST_NIP);
            assertThat(result.getRegon()).isEqualTo("012345678");
            assertThat(result.getCompanyName()).isEqualTo("Firma Testowa Sp. z o.o.");
            assertThat(result.getCity()).isEqualTo("Warszawa");

            // 5 calls: login + search + full report + PKD + logout
            verify(restTemplate, times(5)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @DisplayName("should complete 4-step flow for sole proprietor (F)")
        void shouldComplete4StepFlowForSoleProprietor() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))     // login
                    .thenReturn(ResponseEntity.ok(buildSearchResponse("F")))                // search
                    .thenReturn(ResponseEntity.ok(buildFullReportResponse("fiz_")))         // full report
                    .thenReturn(ResponseEntity.ok(buildPkdReportResponse("fiz_pkd_")))      // PKD report
                    .thenReturn(ResponseEntity.ok("<WylogujResult>true</WylogujResult>"));  // logout

            CompanyRegistryData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result).isNotNull();
            assertThat(result.isFound()).isTrue();
            assertThat(result.getBasicLegalFormCode()).isEqualTo("9");
            assertThat(result.getSpecificLegalFormCode()).isEqualTo("099");
            assertThat(result.getKrs()).isNull();
        }

        @Test
        @DisplayName("should return not found when search returns empty")
        void shouldReturnNotFoundWhenSearchEmpty() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))     // login
                    .thenReturn(ResponseEntity.ok(buildEmptySearchResponse()))               // search — empty
                    .thenReturn(ResponseEntity.ok("<WylogujResult>true</WylogujResult>"));  // logout

            CompanyRegistryData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result).isNotNull();
            assertThat(result.isFound()).isFalse();
        }

        @Test
        @DisplayName("should set session ID in sid header for authenticated calls")
        void shouldSetSessionIdInSidHeader() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))
                    .thenReturn(ResponseEntity.ok(buildEmptySearchResponse()))
                    .thenReturn(ResponseEntity.ok("<WylogujResult>true</WylogujResult>"));

            adapter.lookupByNip(TEST_NIP);

            // Capture the search call (2nd call)
            ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate, atLeast(2)).postForEntity(anyString(), captor.capture(), eq(String.class));

            // The search call should have sid header
            HttpEntity<String> searchRequest = captor.getAllValues().get(1);
            assertThat(searchRequest.getHeaders().getFirst("sid")).isEqualTo(TEST_SESSION_ID);
        }
    }

    @Nested
    @DisplayName("PKD codes")
    class PkdCodes {

        @Test
        @DisplayName("should populate PKD main code from primary PKD entry")
        void shouldPopulatePkdMainCodeFromPrimaryEntry() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))
                    .thenReturn(ResponseEntity.ok(buildSearchResponse("P")))
                    .thenReturn(ResponseEntity.ok(buildFullReportResponse("praw_")))
                    .thenReturn(ResponseEntity.ok(buildPkdReportResponse("pkd_")))
                    .thenReturn(ResponseEntity.ok("<WylogujResult>true</WylogujResult>"));

            CompanyRegistryData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.getPkdMainCode()).isEqualTo("62.01.Z");
            assertThat(result.getPkdMainDescription()).isEqualTo("Software development");
            assertThat(result.getPkdCodes()).isNotNull().hasSize(2);
        }

        @Test
        @DisplayName("should handle PKD fetch failure gracefully")
        void shouldHandlePkdFetchFailureGracefully() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))
                    .thenReturn(ResponseEntity.ok(buildSearchResponse("P")))
                    .thenReturn(ResponseEntity.ok(buildFullReportResponse("praw_")))
                    .thenThrow(new RestClientException("PKD fetch timeout"))                 // PKD fails
                    .thenReturn(ResponseEntity.ok("<WylogujResult>true</WylogujResult>"));  // logout still works

            CompanyRegistryData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result).isNotNull();
            assertThat(result.isFound()).isTrue();
            assertThat(result.getPkdMainCode()).isNull();
            assertThat(result.getPkdCodes()).isNull();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw with status 503 when SOAP call fails")
        void shouldThrowWith503WhenSoapCallFails() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))
                    .thenThrow(new RestClientException("Connection refused"));

            assertThatThrownBy(() -> adapter.lookupByNip(TEST_NIP))
                    .isInstanceOf(ExternalServiceException.class)
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("GUS_BIR1");
                    });
        }

        @Test
        @DisplayName("should always attempt logout even on failure")
        void shouldAlwaysAttemptLogout() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))
                    .thenReturn(ResponseEntity.ok(buildEmptySearchResponse()))
                    .thenReturn(ResponseEntity.ok("<WylogujResult>true</WylogujResult>"));

            adapter.lookupByNip(TEST_NIP);

            // 3 calls: login + search + logout
            verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        }

        @Test
        @DisplayName("should handle search SOAP fault gracefully")
        void shouldHandleSearchSoapFaultGracefully() {
            when(registryProperties.getGus()).thenReturn(gusConfig);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(buildLoginResponse(TEST_SESSION_ID)))
                    .thenReturn(ResponseEntity.ok(buildSoapFaultResponse()))
                    .thenReturn(ResponseEntity.ok("<WylogujResult>true</WylogujResult>"));

            CompanyRegistryData result = adapter.lookupByNip(TEST_NIP);

            assertThat(result.isFound()).isFalse();
        }
    }

    // =========================================================================
    // SOAP Response Builders
    // =========================================================================

    private String buildLoginResponse(String sessionId) {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                    <s:Body>
                        <ZalogujResponse xmlns="http://CIS/BIR/PUBL/2014/07">
                            <ZalogujResult>%s</ZalogujResult>
                        </ZalogujResponse>
                    </s:Body>
                </s:Envelope>""".formatted(sessionId);
    }

    private String buildEmptyLoginResponse() {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                    <s:Body>
                        <ZalogujResponse xmlns="http://CIS/BIR/PUBL/2014/07">
                            <ZalogujResult></ZalogujResult>
                        </ZalogujResponse>
                    </s:Body>
                </s:Envelope>""";
    }

    private String buildSearchResponse(String entityType) {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                    <s:Body>
                        <DaneSzukajPodmiotyResponse xmlns="http://CIS/BIR/PUBL/2014/07">
                            <DaneSzukajPodmiotyResult>&lt;root&gt;&lt;dane&gt;&lt;Regon&gt;012345678&lt;/Regon&gt;&lt;Nip&gt;5261040828&lt;/Nip&gt;&lt;Nazwa&gt;Firma Testowa Sp. z o.o.&lt;/Nazwa&gt;&lt;Typ&gt;%s&lt;/Typ&gt;&lt;/dane&gt;&lt;/root&gt;</DaneSzukajPodmiotyResult>
                        </DaneSzukajPodmiotyResponse>
                    </s:Body>
                </s:Envelope>""".formatted(entityType);
    }

    private String buildEmptySearchResponse() {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                    <s:Body>
                        <DaneSzukajPodmiotyResponse xmlns="http://CIS/BIR/PUBL/2014/07">
                            <DaneSzukajPodmiotyResult></DaneSzukajPodmiotyResult>
                        </DaneSzukajPodmiotyResponse>
                    </s:Body>
                </s:Envelope>""";
    }

    private String buildFullReportResponse(String prefix) {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                    <s:Body>
                        <DanePobierzPelnyRaportResponse xmlns="http://CIS/BIR/PUBL/2014/07">
                            <DanePobierzPelnyRaportResult>&lt;root&gt;&lt;dane&gt;&lt;%1$snazwa&gt;Firma Testowa Sp. z o.o.&lt;/%1$snazwa&gt;&lt;%1$sadSiedzMiejscowosc_Nazwa&gt;Warszawa&lt;/%1$sadSiedzMiejscowosc_Nazwa&gt;&lt;%1$sadSiedzKodPocztowy&gt;00-001&lt;/%1$sadSiedzKodPocztowy&gt;&lt;%1$sadSiedzUlica_Nazwa&gt;Marszałkowska&lt;/%1$sadSiedzUlica_Nazwa&gt;&lt;%1$sadSiedzNumerNieruchomosci&gt;10&lt;/%1$sadSiedzNumerNieruchomosci&gt;&lt;/dane&gt;&lt;/root&gt;</DanePobierzPelnyRaportResult>
                        </DanePobierzPelnyRaportResponse>
                    </s:Body>
                </s:Envelope>""".formatted(prefix);
    }

    private String buildPkdReportResponse(String prefix) {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                    <s:Body>
                        <DanePobierzPelnyRaportResponse xmlns="http://CIS/BIR/PUBL/2014/07">
                            <DanePobierzPelnyRaportResult>&lt;root&gt;&lt;dane&gt;&lt;%1$sKod&gt;62.01.Z&lt;/%1$sKod&gt;&lt;%1$sNazwa&gt;Software development&lt;/%1$sNazwa&gt;&lt;%1$sPrzewazajace&gt;1&lt;/%1$sPrzewazajace&gt;&lt;/dane&gt;&lt;dane&gt;&lt;%1$sKod&gt;62.02.Z&lt;/%1$sKod&gt;&lt;%1$sNazwa&gt;IT consulting&lt;/%1$sNazwa&gt;&lt;%1$sPrzewazajace&gt;0&lt;/%1$sPrzewazajace&gt;&lt;/dane&gt;&lt;/root&gt;</DanePobierzPelnyRaportResult>
                        </DanePobierzPelnyRaportResponse>
                    </s:Body>
                </s:Envelope>""".formatted(prefix);
    }

    private String buildSoapFaultResponse() {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                    <s:Body>
                        <s:Fault>
                            <s:Code><s:Value>s:Receiver</s:Value></s:Code>
                            <s:Reason><s:Text>Internal error</s:Text></s:Reason>
                        </s:Fault>
                    </s:Body>
                </s:Envelope>""";
    }

    private void mockSoapCall(String response) {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(response));
    }
}
