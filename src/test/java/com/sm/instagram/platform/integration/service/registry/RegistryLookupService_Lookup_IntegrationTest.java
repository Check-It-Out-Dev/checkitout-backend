package com.sm.instagram.platform.integration.service.registry;

import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.registry.CompanyType;
import com.sm.instagram.platform.registry.dto.NipLookupResponse;
import com.sm.instagram.platform.registry.exception.NipAlreadyRegisteredException;
import com.sm.instagram.platform.registry.exception.NipNotFoundException;
import com.sm.instagram.platform.registry.port.CompanyRegistryData;
import com.sm.instagram.platform.registry.port.SoleProprietorData;
import com.sm.instagram.platform.registry.port.VatStatusData;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for RegistryLookupService.lookupByNip().
 * Tests real Spring wiring, NipValidator bean, DB-level NIP uniqueness checks,
 * and permission enforcement with SecurityContext.
 */
@DisplayName("RegistryLookupService - Lookup")
class RegistryLookupService_Lookup_IntegrationTest extends RegistryServiceIntegrationTestBase {

    @Nested
    @DisplayName("Successful Lookups")
    class SuccessfulLookups {

        @Test
        @DisplayName("KRS company lookup returns full response with all fields")
        void krsCompanyLookupReturnsFullResponse() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            NipLookupResponse response = registryLookupService.lookupByNip(TEST_NIP, user.getFirebaseUserId());

            assertThat(response).isNotNull();
            assertThat(response.getNip()).isEqualTo(TEST_NIP);
            assertThat(response.getRegon()).isEqualTo("012345678");
            assertThat(response.getKrs()).isEqualTo("0000123456");
            assertThat(response.getCompanyName()).isEqualTo("Testowa Firma Sp. z o.o.");
            assertThat(response.getCompanyType()).isEqualTo(CompanyType.SP_ZOO);
            assertThat(response.getCity()).isEqualTo("Warszawa");
            assertThat(response.getVatStatus()).isEqualTo("ACTIVE");
            assertThat(response.getBankAccounts()).hasSize(2);
            assertThat(response.getPkdCodes()).hasSize(2);
            assertThat(response.isSourceGus()).isTrue();
            assertThat(response.isSourceVat()).isTrue();
            assertThat(response.isCompanyActive()).isTrue();
        }

        @Test
        @DisplayName("JDG lookup triggers CEIDG call and returns owner name")
        void jdgLookupTriggersCeidgCall() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(createMockJdgGusData(TEST_NIP));
            when(soleProprietorRegistryPort.lookupByNip(TEST_NIP)).thenReturn(createMockCeidgData());

            NipLookupResponse response = registryLookupService.lookupByNip(TEST_NIP, user.getFirebaseUserId());

            assertThat(response.getCompanyType()).isEqualTo(CompanyType.JDG);
            assertThat(response.getOwnerName()).isEqualTo("Jan Kowalski");
            assertThat(response.isSourceCeidg()).isTrue();
            verify(soleProprietorRegistryPort).lookupByNip(TEST_NIP);
        }

        @Test
        @DisplayName("Non-JDG lookup does NOT call CEIDG")
        void nonJdgLookupDoesNotCallCeidg() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            registryLookupService.lookupByNip(TEST_NIP, user.getFirebaseUserId());

            verify(soleProprietorRegistryPort, never()).lookupByNip(anyString());
        }
    }

    @Nested
    @DisplayName("Validation Errors")
    class ValidationErrors {

        @Test
        @DisplayName("Invalid NIP format throws ValidationTranslatableException")
        void invalidNipThrows() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            assertThatThrownBy(() -> registryLookupService.lookupByNip("1234567890", user.getFirebaseUserId()))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("NIP already registered in DB throws NipAlreadyRegisteredException")
        void nipAlreadyRegisteredThrows() {
            User existingUser = createCompanyUserInValidation();
            createAndSaveCompanyData(existingUser, TEST_NIP);

            User newUser = createCompanyUserInValidation();
            authenticateAs(newUser);

            assertThatThrownBy(() -> registryLookupService.lookupByNip(TEST_NIP, newUser.getFirebaseUserId()))
                    .isInstanceOf(NipAlreadyRegisteredException.class);
        }
    }

    @Nested
    @DisplayName("Permission Enforcement")
    class PermissionEnforcement {

        @Test
        @DisplayName("Influencer user type is rejected")
        void influencerRejected() {
            authenticateAs(testInfluencer);

            assertThatThrownBy(() -> registryLookupService.lookupByNip(TEST_NIP, testInfluencer.getFirebaseUserId()))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("Admin user type is rejected")
        void adminRejected() {
            authenticateAs(testAdmin);

            assertThatThrownBy(() -> registryLookupService.lookupByNip(TEST_NIP, testAdmin.getFirebaseUserId()))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("Registry Failures")
    class RegistryFailures {

        @Test
        @DisplayName("GUS returns not found throws NipNotFoundException")
        void gusNotFoundThrows() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            when(companyRegistryPort.lookupByNip(TEST_NIP))
                    .thenReturn(CompanyRegistryData.builder().found(false).build());

            assertThatThrownBy(() -> registryLookupService.lookupByNip(TEST_NIP, user.getFirebaseUserId()))
                    .isInstanceOf(NipNotFoundException.class);
        }

        @Test
        @DisplayName("Inactive company throws BusinessRuleTranslatableException")
        void inactiveCompanyThrows() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(createMockInactiveGusData(TEST_NIP));

            assertThatThrownBy(() -> registryLookupService.lookupByNip(TEST_NIP, user.getFirebaseUserId()))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("Biala Lista failure is non-blocking")
        void bialaListaFailureNonBlocking() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            when(vatRegistryPort.lookupVatStatus(anyString()))
                    .thenThrow(new RuntimeException("API down"));

            NipLookupResponse response = registryLookupService.lookupByNip(TEST_NIP, user.getFirebaseUserId());

            assertThat(response).isNotNull();
            assertThat(response.isSourceVat()).isFalse();
            assertThat(response.getVatStatus()).isNull();
        }

        @Test
        @DisplayName("CEIDG failure is non-blocking for JDG")
        void ceidgFailureNonBlockingForJdg() {
            User user = createCompanyUserInValidation();
            authenticateAs(user);

            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(createMockJdgGusData(TEST_NIP));
            when(soleProprietorRegistryPort.lookupByNip(anyString()))
                    .thenThrow(new RuntimeException("CEIDG API down"));

            NipLookupResponse response = registryLookupService.lookupByNip(TEST_NIP, user.getFirebaseUserId());

            assertThat(response).isNotNull();
            assertThat(response.getCompanyType()).isEqualTo(CompanyType.JDG);
            assertThat(response.isSourceCeidg()).isFalse();
            assertThat(response.getOwnerName()).isNull();
        }
    }
}
