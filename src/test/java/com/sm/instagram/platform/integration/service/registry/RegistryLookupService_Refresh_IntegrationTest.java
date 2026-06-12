package com.sm.instagram.platform.integration.service.registry;

import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.registry.CompanyData;
import com.sm.instagram.platform.registry.dto.NipLookupResponse;
import com.sm.instagram.platform.registry.port.CompanyRegistryData;
import com.sm.instagram.platform.registry.port.VatStatusData;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Integration tests for RegistryLookupService.refreshCompanyData().
 * Tests update of existing CompanyData entity with fresh registry data,
 * including JSONB field updates and timestamp advancement.
 */
@DisplayName("RegistryLookupService - Refresh")
class RegistryLookupService_Refresh_IntegrationTest extends RegistryServiceIntegrationTestBase {

    @Test
    @DisplayName("Refresh updates existing CompanyData with fresh registry data")
    void refreshUpdatesExistingCompanyData() {
        User user = createCompanyUserInValidation();
        CompanyData existing = createAndSaveCompanyData(user, TEST_NIP);
        authenticateAs(user);

        // Set up fresh data from GUS with updated company name
        CompanyRegistryData freshGusData = createMockGusData(TEST_NIP);
        freshGusData.setCompanyName("Updated Firma Sp. z o.o.");
        when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(freshGusData);

        NipLookupResponse response = registryLookupService.refreshCompanyData(user.getFirebaseUserId());

        assertThat(response).isNotNull();

        flushAndClear();
        CompanyData refreshed = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
        assertThat(refreshed.getCompanyName()).isEqualTo("Updated Firma Sp. z o.o.");
        assertThat(refreshed.getRegon()).isEqualTo("012345678");
    }

    @Test
    @DisplayName("Refresh for non-existent company data throws BusinessRuleTranslatableException")
    void refreshWithoutCompanyDataThrows() {
        User user = createCompanyUserInValidation();
        authenticateAs(user);
        // No company data saved for this user

        assertThatThrownBy(() -> registryLookupService.refreshCompanyData(user.getFirebaseUserId()))
                .isInstanceOf(BusinessRuleTranslatableException.class);
    }

    @Test
    @DisplayName("Refresh for non-COMPANY user throws BusinessRuleTranslatableException")
    void refreshForInfluencerThrows() {
        authenticateAs(testInfluencer);

        assertThatThrownBy(() -> registryLookupService.refreshCompanyData(testInfluencer.getFirebaseUserId()))
                .isInstanceOf(BusinessRuleTranslatableException.class);
    }

    @Test
    @DisplayName("Refresh updates JSONB fields (pkdCodes, bankAccounts, rawGusResponse)")
    void refreshUpdatesJsonbFields() {
        User user = createCompanyUserInValidation();
        createAndSaveCompanyData(user, TEST_NIP);
        authenticateAs(user);

        // Fresh GUS data with updated PKD codes
        CompanyRegistryData freshGusData = createMockGusData(TEST_NIP);
        freshGusData.setPkdCodes(List.of(
                Map.of("code", "63.11.Z", "description", "Przetwarzanie danych", "isPrimary", true)
        ));
        when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(freshGusData);

        // Fresh VAT data with new bank accounts
        VatStatusData freshVatData = VatStatusData.builder()
                .found(true)
                .normalizedStatus("ACTIVE")
                .accountNumbers(List.of("PL99999999999999999999999999"))
                .build();
        when(vatRegistryPort.lookupVatStatus(TEST_NIP)).thenReturn(freshVatData);

        registryLookupService.refreshCompanyData(user.getFirebaseUserId());

        flushAndClear();
        CompanyData refreshed = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
        assertThat(refreshed.getPkdCodes()).hasSize(1);
        assertThat(refreshed.getBankAccounts()).containsExactly("PL99999999999999999999999999");
        assertThat(refreshed.getRawGusResponse()).isNotNull();
    }

    @Test
    @DisplayName("@UpdateTimestamp advances after refresh")
    void updateTimestampAdvancesAfterRefresh() {
        User user = createCompanyUserInValidation();
        CompanyData existing = createAndSaveCompanyData(user, TEST_NIP);
        flushAndClear();

        CompanyData before = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
        LocalDateTime beforeTimestamp = before.getLastUpdateTime();

        authenticateAs(user);
        registryLookupService.refreshCompanyData(user.getFirebaseUserId());

        flushAndClear();
        CompanyData after = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
        assertThat(after.getLastUpdateTime()).isAfterOrEqualTo(beforeTimestamp);
    }
}
