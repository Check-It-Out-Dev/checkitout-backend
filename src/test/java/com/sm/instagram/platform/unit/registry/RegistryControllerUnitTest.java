package com.sm.instagram.platform.unit.registry;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.registry.CompanyType;
import com.sm.instagram.platform.registry.RegistryController;
import com.sm.instagram.platform.registry.RegistryLookupService;
import com.sm.instagram.platform.registry.dto.*;
import com.sm.instagram.platform.registry.exception.NipAlreadyRegisteredException;
import com.sm.instagram.platform.registry.exception.NipNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegistryController.
 * Tests delegation to RegistryLookupService and response wrapping.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistryController")
class RegistryControllerUnitTest {

    @Mock
    private RegistryLookupService registryLookupService;

    @Mock
    private PermissionUtils permissionUtils;

    @InjectMocks
    private RegistryController controller;

    private static final String TEST_NIP = "5261040828";
    private static final String TEST_FIREBASE_UID = "firebase-uid-123";

    @BeforeEach
    void setUp() {
        when(permissionUtils.getUserId()).thenReturn(TEST_FIREBASE_UID);
    }

    @Nested
    @DisplayName("lookupByNip")
    class LookupByNip {

        @Test
        @DisplayName("should delegate to service and return 200")
        void shouldDelegateToServiceAndReturn200() {
            NipLookupRequest request = new NipLookupRequest(TEST_NIP);
            NipLookupResponse expected = NipLookupResponse.builder()
                    .nip(TEST_NIP)
                    .companyType(CompanyType.SP_ZOO)
                    .sourceGus(true)
                    .build();

            when(registryLookupService.lookupByNip(TEST_NIP, TEST_FIREBASE_UID)).thenReturn(expected);

            ResponseEntity<NipLookupResponse> response = controller.lookupByNip(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(expected);
            verify(registryLookupService).lookupByNip(TEST_NIP, TEST_FIREBASE_UID);
        }

        @Test
        @DisplayName("should propagate NipNotFoundException from service")
        void shouldPropagateNipNotFoundException() {
            NipLookupRequest request = new NipLookupRequest(TEST_NIP);
            when(registryLookupService.lookupByNip(TEST_NIP, TEST_FIREBASE_UID))
                    .thenThrow(new NipNotFoundException(TEST_NIP));

            assertThatThrownBy(() -> controller.lookupByNip(request))
                    .isInstanceOf(NipNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("confirmCompanyData")
    class ConfirmCompanyData {

        @Test
        @DisplayName("should delegate to service and return 200")
        void shouldDelegateToServiceAndReturn200() {
            CompanyDataConfirmRequest request = new CompanyDataConfirmRequest();
            request.setNip(TEST_NIP);

            CompanyDataConfirmResponse expected = CompanyDataConfirmResponse.builder()
                    .companyDataId(100L)
                    .nip(TEST_NIP)
                    .activated(true)
                    .accountStatus("ACTIVE")
                    .build();

            when(registryLookupService.confirmCompanyData(TEST_FIREBASE_UID, request))
                    .thenReturn(expected);

            ResponseEntity<CompanyDataConfirmResponse> response =
                    controller.confirmCompanyData(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getCompanyDataId()).isEqualTo(100L);
            assertThat(response.getBody().isActivated()).isTrue();
        }
    }

    @Nested
    @DisplayName("getCompanyData")
    class GetCompanyData {

        @Test
        @DisplayName("should return company data when present")
        void shouldReturnCompanyDataWhenPresent() {
            CompanyDataDtoOut expected = CompanyDataDtoOut.builder()
                    .id(1L)
                    .nip(TEST_NIP)
                    .companyName("Test Corp")
                    .build();

            when(registryLookupService.getCompanyDataForUser(TEST_FIREBASE_UID)).thenReturn(expected);

            ResponseEntity<CompanyDataDtoOut> response = controller.getCompanyData();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getNip()).isEqualTo(TEST_NIP);
        }

        /**
         * A 200 with a null body is a 200 with no body and no Content-Type, and a client that
         * reads it as JSON fails at character zero -- which is how the fuzzer found it. 204 is the
         * status for "nothing to send", and it reaches a consumer as the same absence.
         */
        @Test
        @DisplayName("should answer 204 when the user has confirmed no company data")
        void shouldAnswerNoContentWhenNoCompanyData() {
            when(registryLookupService.getCompanyDataForUser(TEST_FIREBASE_UID)).thenReturn(null);

            ResponseEntity<CompanyDataDtoOut> response = controller.getCompanyData();

            assertThat(response.getStatusCode().value()).isEqualTo(204);
            assertThat(response.getBody()).isNull();
        }
    }

    @Nested
    @DisplayName("refreshCompanyData")
    class RefreshCompanyData {

        @Test
        @DisplayName("should delegate to service and return 200")
        void shouldDelegateToServiceAndReturn200() {
            NipLookupResponse expected = NipLookupResponse.builder()
                    .nip(TEST_NIP)
                    .companyType(CompanyType.SP_ZOO)
                    .sourceGus(true)
                    .build();

            when(registryLookupService.refreshCompanyData(TEST_FIREBASE_UID)).thenReturn(expected);

            ResponseEntity<NipLookupResponse> response = controller.refreshCompanyData();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(expected);
        }
    }
}
