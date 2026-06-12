package com.sm.instagram.platform.integration.service.registry;

import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.registry.CompanyData;
import com.sm.instagram.platform.registry.CompanyType;
import com.sm.instagram.platform.registry.dto.CompanyDataConfirmRequest;
import com.sm.instagram.platform.registry.dto.CompanyDataConfirmResponse;
import com.sm.instagram.platform.registry.exception.NipAlreadyRegisteredException;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Integration tests for RegistryLookupService.confirmCompanyData().
 * Tests real DB persistence (JSONB round-trips), auto-activation logic,
 * and unique constraint enforcement.
 */
@DisplayName("RegistryLookupService - Confirm")
class RegistryLookupService_Confirm_IntegrationTest extends RegistryServiceIntegrationTestBase {

    @Nested
    @DisplayName("Successful Confirmation — KRS Company")
    class SuccessfulKrsConfirm {

        @Test
        @DisplayName("Persists CompanyData with all fields including JSONB columns")
        void persistsCompanyDataWithAllFields() {
            User user = createCompanyUserEmailVerified();
            authenticateAs(user);

            CompanyDataConfirmResponse response = registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP));

            assertThat(response).isNotNull();
            assertThat(response.getCompanyDataId()).isNotNull();

            // Verify DB persistence with JSONB round-trip
            flushAndClear();
            Optional<CompanyData> saved = companyDataRepository.findByNip(TEST_NIP);
            assertThat(saved).isPresent();

            CompanyData cd = saved.get();
            assertThat(cd.getNip()).isEqualTo(TEST_NIP);
            assertThat(cd.getRegon()).isEqualTo("012345678");
            assertThat(cd.getKrs()).isEqualTo("0000123456");
            assertThat(cd.getCompanyName()).isEqualTo("Testowa Firma Sp. z o.o.");
            assertThat(cd.getCompanyType()).isEqualTo(CompanyType.SP_ZOO);

            // JSONB: registeredAddress
            assertThat(cd.getRegisteredAddress()).isNotNull();
            assertThat(cd.getRegisteredAddress()).containsEntry("city", "Warszawa");
            assertThat(cd.getRegisteredAddress()).containsEntry("postalCode", "00-001");
            assertThat(cd.getRegisteredAddress()).containsEntry("street", "Marszałkowska");

            // JSONB: pkdCodes
            assertThat(cd.getPkdCodes()).hasSize(2);

            // JSONB: bankAccounts
            assertThat(cd.getBankAccounts()).hasSize(2);
            assertThat(cd.getBankAccounts()).contains("PL61109010140000071219812874");

            // JSONB: rawGusResponse (nested maps)
            assertThat(cd.getRawGusResponse()).isNotNull();
            assertThat(cd.getRawGusResponse()).containsKey("searchResult");

            assertThat(cd.getVatStatus()).isEqualTo("ACTIVE");
            assertThat(cd.getSourceGus()).isTrue();
            assertThat(cd.getSourceVat()).isTrue();
        }

        @Test
        @DisplayName("Auto-activates when emailVerified is true")
        void autoActivatesWhenEmailVerified() {
            User user = createCompanyUserEmailVerified();
            authenticateAs(user);

            CompanyDataConfirmResponse response = registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP));

            assertThat(response.isActivated()).isTrue();
            assertThat(response.getAccountStatus()).isEqualTo("ACTIVE");

            // Verify user status in DB
            flushAndClear();
            User refreshed = userRepository.findByFirebaseUserId(user.getFirebaseUserId()).orElseThrow();
            assertThat(refreshed.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("Does NOT auto-activate when emailVerified is false")
        void doesNotActivateWhenEmailNotVerified() {
            User user = createCompanyUserInValidation();
            user.setEmailVerified(false);
            userRepository.save(user);
            authenticateAs(user);

            CompanyDataConfirmResponse response = registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP));

            assertThat(response.isActivated()).isFalse();
            assertThat(response.getAccountStatus()).isEqualTo("IN_VALIDATION");
        }

        @Test
        @DisplayName("Does NOT auto-activate when emailVerified is null")
        void doesNotActivateWhenEmailNull() {
            User user = createCompanyUserInValidation();
            // emailVerified is null by default
            authenticateAs(user);

            CompanyDataConfirmResponse response = registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP));

            assertThat(response.isActivated()).isFalse();
        }

        @Test
        @DisplayName("NIP is denormalized to user.nip")
        void nipDenormalizedToUser() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP));

            flushAndClear();
            User refreshed = userRepository.findByFirebaseUserId(user.getFirebaseUserId()).orElseThrow();
            assertThat(refreshed.getNip()).isEqualTo(TEST_NIP);
        }

        @Test
        @DisplayName("dataVerified is true after confirm")
        void dataVerifiedIsTrue() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP));

            flushAndClear();
            CompanyData cd = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
            assertThat(cd.getDataVerified()).isTrue();
        }

        @Test
        @DisplayName("@CreationTimestamp and @UpdateTimestamp are populated")
        void timestampsPopulated() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP));

            flushAndClear();
            CompanyData cd = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
            assertThat(cd.getCreatedTime()).isNotNull();
            assertThat(cd.getLastUpdateTime()).isNotNull();
        }

        @Test
        @DisplayName("@Version starts at 0")
        void versionStartsAtZero() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP));

            flushAndClear();
            CompanyData cd = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
            assertThat(cd.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Correspondence address persists as JSONB when provided")
        void correspondenceAddressPersists() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            CompanyDataConfirmRequest request = createConfirmRequest(TEST_NIP);
            request.setCorrespondenceAddress(Map.of(
                    "street", "Inna Ulica",
                    "city", "Kraków",
                    "postalCode", "30-001"
            ));

            registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), request);

            flushAndClear();
            CompanyData cd = companyDataRepository.findByNip(TEST_NIP).orElseThrow();
            assertThat(cd.getCorrespondenceAddress()).isNotNull();
            assertThat(cd.getCorrespondenceAddress()).containsEntry("city", "Kraków");
        }
    }

    @Nested
    @DisplayName("Uniqueness & Existence Checks")
    class UniquenessChecks {

        @Test
        @DisplayName("Duplicate NIP in DB throws NipAlreadyRegisteredException")
        void duplicateNipThrows() {
            User existingUser = createCompanyUserInValidation();
            createAndSaveCompanyData(existingUser, TEST_NIP);

            User newUser = createCompanyUserInValidation();
            authenticateAs(newUser);

            assertThatThrownBy(() -> registryLookupService.confirmCompanyData(
                    newUser.getFirebaseUserId(), createConfirmRequest(TEST_NIP)))
                    .isInstanceOf(NipAlreadyRegisteredException.class);
        }

        @Test
        @DisplayName("User already has company data throws BusinessRuleTranslatableException")
        void userAlreadyHasCompanyDataThrows() {
            User user = createCompanyUserInValidation();
            createAndSaveCompanyData(user, TEST_NIP);
            authenticateAs(user);

            // Try to confirm with a different NIP
            when(companyRegistryPort.lookupByNip(TEST_NIP_2)).thenReturn(createMockGusData(TEST_NIP_2));
            when(vatRegistryPort.lookupVatStatus(TEST_NIP_2)).thenReturn(createMockVatData(TEST_NIP_2));

            assertThatThrownBy(() -> registryLookupService.confirmCompanyData(
                    user.getFirebaseUserId(), createConfirmRequest(TEST_NIP_2)))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }
    }
}
