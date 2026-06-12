package com.sm.instagram.platform.integration.service.registry;

import com.sm.instagram.platform.registry.CompanyData;
import com.sm.instagram.platform.registry.CompanyType;
import com.sm.instagram.platform.registry.dto.CompanyDataDtoOut;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RegistryLookupService.getCompanyDataForUser().
 * Tests JSONB round-trips (persist → read → DTO), including nested maps,
 * enum string storage, and null handling for optional fields.
 */
@DisplayName("RegistryLookupService - GetData")
class RegistryLookupService_GetData_IntegrationTest extends RegistryServiceIntegrationTestBase {

    @Test
    @DisplayName("Returns CompanyDataDtoOut with all JSONB fields intact")
    void returnsFullDtoWithJsonbFields() {
        User user = createCompanyUserInValidation();
        authenticateAs(user);

        // Create entity with all JSONB columns populated
        CompanyData cd = new CompanyData();
        cd.setUser(user);
        cd.setNip(TEST_NIP);
        cd.setRegon("012345678");
        cd.setKrs("0000123456");
        cd.setCompanyName("Testowa Firma Sp. z o.o.");
        cd.setCompanyType(CompanyType.SP_ZOO);
        cd.setLegalFormName("SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ");
        cd.setDataVerified(true);
        cd.setSourceGus(true);
        cd.setSourceVat(true);
        cd.setSourceCeidg(false);
        cd.setVatStatus("ACTIVE");
        cd.setRegisteredAddress(Map.of("city", "Warszawa", "postalCode", "00-001", "street", "Marszałkowska"));
        cd.setCorrespondenceAddress(Map.of("city", "Kraków", "postalCode", "30-001"));
        cd.setPkdCodes(List.of(
                Map.of("code", "62.01.Z", "description", "Oprogramowanie", "isPrimary", true)
        ));
        cd.setBankAccounts(List.of("PL61109010140000071219812874", "PL27114020040000300201355387"));
        companyDataRepository.save(cd);
        flushAndClear();

        CompanyDataDtoOut dto = registryLookupService.getCompanyDataForUser(user.getFirebaseUserId());

        assertThat(dto).isNotNull();
        assertThat(dto.getNip()).isEqualTo(TEST_NIP);
        assertThat(dto.getCompanyType()).isEqualTo(CompanyType.SP_ZOO);
        assertThat(dto.getRegisteredAddress()).containsEntry("city", "Warszawa");
        assertThat(dto.getCorrespondenceAddress()).containsEntry("city", "Kraków");
        assertThat(dto.getPkdCodes()).hasSize(1);
        assertThat(dto.getBankAccounts()).hasSize(2);
        assertThat(dto.getDataVerified()).isTrue();
    }

    @Test
    @DisplayName("Returns null when no company data exists")
    void returnsNullWhenNoData() {
        User user = createCompanyUserInValidation();
        authenticateAs(user);

        CompanyDataDtoOut dto = registryLookupService.getCompanyDataForUser(user.getFirebaseUserId());

        assertThat(dto).isNull();
    }

    @Test
    @DisplayName("JSONB with nested maps (rawGusResponse) serializes and deserializes correctly")
    void nestedMapsRoundTrip() {
        User user = createCompanyUserInValidation();
        authenticateAs(user);

        // rawGusResponse contains Map<String, Object> with nested Map<String, String> values
        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("searchResult", Map.of("Regon", "012345678", "Nip", TEST_NIP, "Typ", "P"));
        rawResponse.put("fullReport", Map.of("praw_nazwa", "Test", "praw_adSiedzMiejscowosc_Nazwa", "Warszawa"));

        CompanyData cd = new CompanyData();
        cd.setUser(user);
        cd.setNip(TEST_NIP);
        cd.setCompanyName("Test Sp. z o.o.");
        cd.setCompanyType(CompanyType.SP_ZOO);
        cd.setDataVerified(true);
        cd.setSourceGus(true);
        cd.setSourceVat(false);
        cd.setSourceCeidg(false);
        cd.setRawGusResponse(rawResponse);
        companyDataRepository.save(cd);
        flushAndClear();

        CompanyData reloaded = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
        assertThat(reloaded.getRawGusResponse()).isNotNull();
        assertThat(reloaded.getRawGusResponse()).containsKey("searchResult");
        assertThat(reloaded.getRawGusResponse()).containsKey("fullReport");

        // Verify nested map is deserialized correctly
        @SuppressWarnings("unchecked")
        Map<String, String> searchResult = (Map<String, String>) reloaded.getRawGusResponse().get("searchResult");
        assertThat(searchResult).containsEntry("Regon", "012345678");
        assertThat(searchResult).containsEntry("Nip", TEST_NIP);
    }

    @Test
    @DisplayName("Enum stored as STRING in DB, not ordinal")
    void enumStoredAsString() {
        User user = createCompanyUserInValidation();
        authenticateAs(user);

        CompanyData cd = new CompanyData();
        cd.setUser(user);
        cd.setNip(TEST_NIP);
        cd.setCompanyName("JDG Test");
        cd.setCompanyType(CompanyType.JDG);
        cd.setDataVerified(true);
        cd.setSourceGus(true);
        cd.setSourceVat(false);
        cd.setSourceCeidg(false);
        companyDataRepository.save(cd);
        flushAndClear();

        // Verify via native query that the actual DB value is a string
        String dbValue = (String) entityManager.createNativeQuery(
                "SELECT company_type FROM company_data WHERE nip = :nip")
                .setParameter("nip", TEST_NIP)
                .getSingleResult();
        assertThat(dbValue).isEqualTo("JDG");
    }

    @Test
    @DisplayName("Null optional fields (krs, ownerName) handled correctly")
    void nullOptionalFieldsHandled() {
        User user = createCompanyUserInValidation();
        authenticateAs(user);

        CompanyData cd = new CompanyData();
        cd.setUser(user);
        cd.setNip(TEST_NIP);
        cd.setCompanyName("Minimal Company");
        cd.setCompanyType(CompanyType.SP_ZOO);
        cd.setDataVerified(true);
        cd.setSourceGus(true);
        cd.setSourceVat(false);
        cd.setSourceCeidg(false);
        // krs, ownerName, correspondenceAddress, pkdCodes, bankAccounts all null
        companyDataRepository.save(cd);
        flushAndClear();

        CompanyDataDtoOut dto = registryLookupService.getCompanyDataForUser(user.getFirebaseUserId());

        assertThat(dto).isNotNull();
        assertThat(dto.getKrs()).isNull();
        assertThat(dto.getOwnerName()).isNull();
        assertThat(dto.getCorrespondenceAddress()).isNull();
        assertThat(dto.getPkdCodes()).isNull();
        assertThat(dto.getBankAccounts()).isNull();
    }
}
