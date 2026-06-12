package com.sm.instagram.platform.unit.registry;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.registry.*;
import com.sm.instagram.platform.registry.config.RegistryProperties;
import com.sm.instagram.platform.registry.dto.*;
import com.sm.instagram.platform.registry.exception.NipAlreadyRegisteredException;
import com.sm.instagram.platform.registry.exception.NipNotFoundException;
import com.sm.instagram.platform.registry.port.*;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegistryLookupService.
 * Tests orchestration logic, company classification, and auto-activation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistryLookupService")
class RegistryLookupServiceUnitTest {

    @Mock private CompanyRegistryPort companyRegistryPort;
    @Mock private VatRegistryPort vatRegistryPort;
    @Mock private SoleProprietorRegistryPort soleProprietorRegistryPort;
    @Mock private CompanyDataRepository companyDataRepository;
    @Mock private UserRepository userRepository;
    @Mock private NipValidator nipValidator;
    @Mock private CompanyTypeClassifier companyTypeClassifier;
    @Mock private RegistryProperties registryProperties;
    @Mock private PermissionUtils permissionUtils;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.sm.instagram.platform.auth.cache.UserCacheService userCacheService;

    @InjectMocks
    private RegistryLookupService service;

    private static final String TEST_NIP = "5261040828";
    private static final String TEST_FIREBASE_UID = "firebase-uid-123";

    private User companyUser;

    @BeforeEach
    void setUp() {
        companyUser = new User();
        companyUser.setId(1L);
        companyUser.setFirebaseUserId(TEST_FIREBASE_UID);
        companyUser.setUserType(UserType.COMPANY);
        companyUser.setAccountStatus(AccountStatus.IN_VALIDATION);
    }

    @Nested
    @DisplayName("lookupByNip")
    class LookupByNip {

        @Test
        @DisplayName("should return lookup response for valid NIP with KRS entity")
        void shouldReturnLookupResponseForValidKrsEntity() {
            // Given
            setupValidLookupMocks(CompanyType.SP_ZOO);

            // When
            NipLookupResponse response = service.lookupByNip(TEST_NIP, TEST_FIREBASE_UID);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getNip()).isEqualTo(TEST_NIP);
            assertThat(response.getCompanyType()).isEqualTo(CompanyType.SP_ZOO);
            assertThat(response.isSourceGus()).isTrue();

            verify(companyRegistryPort).lookupByNip(TEST_NIP);
            verify(vatRegistryPort).lookupVatStatus(TEST_NIP);
            verify(soleProprietorRegistryPort, never()).lookupByNip(anyString());
        }

        @Test
        @DisplayName("should call CEIDG for JDG companies")
        void shouldCallCeidgForJdgCompanies() {
            // Given
            setupValidLookupMocks(CompanyType.JDG);
            when(soleProprietorRegistryPort.lookupByNip(TEST_NIP))
                    .thenReturn(SoleProprietorData.builder()
                            .found(true)
                            .ownerFirstName("Jan")
                            .ownerLastName("Kowalski")
                            .build());

            // When
            NipLookupResponse response = service.lookupByNip(TEST_NIP, TEST_FIREBASE_UID);

            // Then
            assertThat(response.getCompanyType()).isEqualTo(CompanyType.JDG);
            assertThat(response.getOwnerName()).isEqualTo("Jan Kowalski");
            assertThat(response.isSourceCeidg()).isTrue();

            verify(soleProprietorRegistryPort).lookupByNip(TEST_NIP);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid NIP")
        void shouldThrowForInvalidNip() {
            // Given
            when(nipValidator.normalize("invalid")).thenReturn("invalid");
            when(nipValidator.isValid("invalid")).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.lookupByNip("invalid", TEST_FIREBASE_UID))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw NipAlreadyRegisteredException for duplicate NIP")
        void shouldThrowForDuplicateNip() {
            // Given
            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> service.lookupByNip(TEST_NIP, TEST_FIREBASE_UID))
                    .isInstanceOf(NipAlreadyRegisteredException.class);
        }

        @Test
        @DisplayName("should throw BusinessRuleTranslatableException for non-COMPANY user")
        void shouldThrowForNonCompanyUser() {
            // Given
            User influencer = new User();
            influencer.setId(2L);
            influencer.setFirebaseUserId(TEST_FIREBASE_UID);
            influencer.setUserType(UserType.INFLUENCER);

            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(influencer));

            // When/Then
            assertThatThrownBy(() -> service.lookupByNip(TEST_NIP, TEST_FIREBASE_UID))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should throw NipNotFoundException when GUS returns not found")
        void shouldThrowWhenGusReturnsNotFound() {
            // Given
            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyRegistryPort.lookupByNip(TEST_NIP))
                    .thenReturn(CompanyRegistryData.builder().found(false).build());

            // When/Then
            assertThatThrownBy(() -> service.lookupByNip(TEST_NIP, TEST_FIREBASE_UID))
                    .isInstanceOf(NipNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when company is inactive and not suspended")
        void shouldThrowWhenCompanyIsInactiveAndNotSuspended() {
            // Given
            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));

            CompanyRegistryData gusData = CompanyRegistryData.builder()
                    .found(true)
                    .nip(TEST_NIP)
                    .companyName("Liquidated Corp")
                    .activityEndDate("2023-12-31")
                    .build();
            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
            when(companyTypeClassifier.classify(gusData)).thenReturn(CompanyType.SP_ZOO);
            when(companyTypeClassifier.isActive(gusData)).thenReturn(false);
            when(companyTypeClassifier.isSuspended(gusData)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.lookupByNip(TEST_NIP, TEST_FIREBASE_UID))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should continue gracefully when Biała Lista fails")
        void shouldContinueWhenBialaListaFails() {
            // Given
            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));

            CompanyRegistryData gusData = buildGusData();
            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
            when(companyTypeClassifier.classify(gusData)).thenReturn(CompanyType.SP_ZOO);
            when(companyTypeClassifier.isActive(gusData)).thenReturn(true);
            when(companyTypeClassifier.isSuspended(gusData)).thenReturn(false);
            when(vatRegistryPort.lookupVatStatus(TEST_NIP)).thenThrow(new RuntimeException("API down"));

            RegistryProperties.CacheConfig cacheConfig = new RegistryProperties.CacheConfig();
            when(registryProperties.getCache()).thenReturn(cacheConfig);

            // When
            NipLookupResponse response = service.lookupByNip(TEST_NIP, TEST_FIREBASE_UID);

            // Then — should succeed despite Biała Lista failure
            assertThat(response).isNotNull();
            assertThat(response.isSourceVat()).isFalse();
        }
    }

    @Nested
    @DisplayName("confirmCompanyData")
    class ConfirmCompanyData {

        @Test
        @DisplayName("should save company data and auto-activate when email verified")
        void shouldSaveAndAutoActivateWhenEmailVerified() {
            // Given
            companyUser.setEmailVerified(true);

            CompanyDataConfirmRequest request = new CompanyDataConfirmRequest();
            request.setNip(TEST_NIP);

            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.existsByUserId(1L)).thenReturn(false);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);

            // GUS data (cache miss → re-fetch)
            CompanyRegistryData gusData = buildGusData();
            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
            when(companyTypeClassifier.classify(gusData)).thenReturn(CompanyType.SP_ZOO);
            when(vatRegistryPort.lookupVatStatus(TEST_NIP))
                    .thenReturn(VatStatusData.builder().found(false).build());

            when(companyDataRepository.save(any(CompanyData.class))).thenAnswer(inv -> {
                CompanyData cd = inv.getArgument(0);
                cd.setId(100L);
                return cd;
            });

            // When
            CompanyDataConfirmResponse response = service.confirmCompanyData(
                    TEST_FIREBASE_UID, request);

            // Then
            assertThat(response.isActivated()).isTrue();
            assertThat(response.getAccountStatus()).isEqualTo("ACTIVE");
            assertThat(response.getCompanyDataId()).isEqualTo(100L);

            verify(companyDataRepository).save(any(CompanyData.class));
            verify(userRepository).save(companyUser);
            verify(permissionUtils).changeUserRole(TEST_FIREBASE_UID, AccountStatus.ACTIVE, UserType.COMPANY);
            verify(eventPublisher).publishEvent(any(com.sm.instagram.platform.notification.event.AccountActivatedEvent.class));
        }

        /**
         * BUG-15 (sibling of BUG-13): the COMPANY activation path inside
         * confirmCompanyData wrote the Firebase role BEFORE userRepository.save —
         * same split-brain hazard BUG-13 fixed for the email-verify path. If the
         * outer @Transactional rolled back after the Firebase write succeeded,
         * Firebase would show ACTIVE while PG stayed IN_VALIDATION, with no
         * tokenVersion bump (no 419) to self-heal. The Firebase role write and
         * AccountActivatedEvent must be deferred until AFTER the PG save commits.
         */
        @Test
        @DisplayName("BUG-15: should defer Firebase role write until AFTER userRepository.save")
        void shouldDeferFirebaseRoleWriteUntilAfterPgSave() {
            // Given
            companyUser.setEmailVerified(true);

            CompanyDataConfirmRequest request = new CompanyDataConfirmRequest();
            request.setNip(TEST_NIP);

            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.existsByUserId(1L)).thenReturn(false);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);

            CompanyRegistryData gusData = buildGusData();
            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
            when(companyTypeClassifier.classify(gusData)).thenReturn(CompanyType.SP_ZOO);
            when(vatRegistryPort.lookupVatStatus(TEST_NIP))
                    .thenReturn(VatStatusData.builder().found(false).build());

            when(companyDataRepository.save(any(CompanyData.class))).thenAnswer(inv -> {
                CompanyData cd = inv.getArgument(0);
                cd.setId(100L);
                return cd;
            });

            // When
            service.confirmCompanyData(TEST_FIREBASE_UID, request);

            // Then: pin the activation save order — PG must be saved BEFORE the Firebase role write
            InOrder inOrder = inOrder(userRepository, permissionUtils);
            inOrder.verify(userRepository).save(companyUser);
            inOrder.verify(permissionUtils).changeUserRole(
                    TEST_FIREBASE_UID, AccountStatus.ACTIVE, UserType.COMPANY);
        }

        @Test
        @DisplayName("should not auto-activate when email not verified")
        void shouldNotAutoActivateWhenEmailNotVerified() {
            // Given
            companyUser.setEmailVerified(false);

            CompanyDataConfirmRequest request = new CompanyDataConfirmRequest();
            request.setNip(TEST_NIP);

            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.existsByUserId(1L)).thenReturn(false);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);

            CompanyRegistryData gusData = buildGusData();
            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
            when(companyTypeClassifier.classify(gusData)).thenReturn(CompanyType.SP_ZOO);
            when(vatRegistryPort.lookupVatStatus(TEST_NIP))
                    .thenReturn(VatStatusData.builder().found(false).build());

            when(companyDataRepository.save(any(CompanyData.class))).thenAnswer(inv -> {
                CompanyData cd = inv.getArgument(0);
                cd.setId(100L);
                return cd;
            });

            // When
            CompanyDataConfirmResponse response = service.confirmCompanyData(
                    TEST_FIREBASE_UID, request);

            // Then
            assertThat(response.isActivated()).isFalse();
            assertThat(response.getAccountStatus()).isEqualTo("IN_VALIDATION");
            verify(permissionUtils, never()).changeUserRole(anyString(), any(), any());
        }

        @Test
        @DisplayName("should confirm JDG company without consent requirement")
        void shouldConfirmJdgWithoutConsentRequirement() {
            // Given
            companyUser.setEmailVerified(true);

            CompanyDataConfirmRequest request = new CompanyDataConfirmRequest();
            request.setNip(TEST_NIP);

            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.existsByUserId(1L)).thenReturn(false);
            when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);

            CompanyRegistryData gusData = buildGusData();
            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
            when(companyTypeClassifier.classify(gusData)).thenReturn(CompanyType.JDG);
            when(vatRegistryPort.lookupVatStatus(TEST_NIP))
                    .thenReturn(VatStatusData.builder().found(false).build());
            when(soleProprietorRegistryPort.lookupByNip(TEST_NIP))
                    .thenReturn(SoleProprietorData.builder().found(true)
                            .ownerFirstName("Jan").ownerLastName("Kowalski").build());

            when(companyDataRepository.save(any(CompanyData.class))).thenAnswer(inv -> {
                CompanyData cd = inv.getArgument(0);
                cd.setId(100L);
                return cd;
            });

            // When
            CompanyDataConfirmResponse response = service.confirmCompanyData(
                    TEST_FIREBASE_UID, request);

            // Then — JDG confirms without any consent step
            assertThat(response).isNotNull();
            assertThat(response.isActivated()).isTrue();
            assertThat(response.getCompanyType()).isEqualTo(CompanyType.JDG);
        }

        @Test
        @DisplayName("should throw when company data already exists for user")
        void shouldThrowWhenCompanyDataAlreadyExists() {
            // Given
            CompanyDataConfirmRequest request = new CompanyDataConfirmRequest();
            request.setNip(TEST_NIP);

            when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
            when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.existsByUserId(1L)).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> service.confirmCompanyData(
                    TEST_FIREBASE_UID, request))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("getCompanyDataForUser")
    class GetCompanyDataForUser {

        @Test
        @DisplayName("should return null when no company data exists")
        void shouldReturnNullWhenNoData() {
            // Given
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.findByUserId(1L)).thenReturn(Optional.empty());

            // When
            CompanyDataDtoOut result = service.getCompanyDataForUser(TEST_FIREBASE_UID);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return DTO when company data exists")
        void shouldReturnDtoWhenDataExists() {
            // Given
            CompanyData data = new CompanyData();
            data.setId(1L);
            data.setNip(TEST_NIP);
            data.setCompanyName("Test Corp");
            data.setCompanyType(CompanyType.SP_ZOO);

            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.findByUserId(1L)).thenReturn(Optional.of(data));

            // When
            CompanyDataDtoOut result = service.getCompanyDataForUser(TEST_FIREBASE_UID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getNip()).isEqualTo(TEST_NIP);
            assertThat(result.getCompanyName()).isEqualTo("Test Corp");
        }
    }

    @Nested
    @DisplayName("refreshCompanyData")
    class RefreshCompanyData {

        @Test
        @DisplayName("should refresh and update existing company data")
        void shouldRefreshAndUpdateExistingCompanyData() {
            // Given
            CompanyData existing = new CompanyData();
            existing.setId(50L);
            existing.setNip(TEST_NIP);
            existing.setCompanyName("Old Name");
            existing.setUser(companyUser);

            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

            CompanyRegistryData gusData = buildGusData();
            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
            when(companyTypeClassifier.classify(gusData)).thenReturn(CompanyType.SP_ZOO);
            when(companyTypeClassifier.isActive(gusData)).thenReturn(true);
            when(companyTypeClassifier.isSuspended(gusData)).thenReturn(false);
            when(vatRegistryPort.lookupVatStatus(TEST_NIP))
                    .thenReturn(VatStatusData.builder().found(true).normalizedStatus("ACTIVE").build());
            when(companyDataRepository.save(any(CompanyData.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            NipLookupResponse response = service.refreshCompanyData(TEST_FIREBASE_UID);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getNip()).isEqualTo(TEST_NIP);
            assertThat(response.getCompanyType()).isEqualTo(CompanyType.SP_ZOO);
            verify(companyDataRepository).save(existing);
            assertThat(existing.getCompanyName()).isEqualTo("Firma Testowa Sp. z o.o.");
        }

        @Test
        @DisplayName("should throw when no existing company data found")
        void shouldThrowWhenNoExistingCompanyData() {
            // Given
            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.findByUserId(1L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.refreshCompanyData(TEST_FIREBASE_UID))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should throw for non-COMPANY user type")
        void shouldThrowForNonCompanyUserType() {
            // Given
            User influencer = new User();
            influencer.setId(2L);
            influencer.setFirebaseUserId(TEST_FIREBASE_UID);
            influencer.setUserType(UserType.INFLUENCER);

            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(influencer));

            // When/Then
            assertThatThrownBy(() -> service.refreshCompanyData(TEST_FIREBASE_UID))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("should throw NipNotFoundException when GUS returns not found on refresh")
        void shouldThrowWhenGusNotFoundOnRefresh() {
            // Given
            CompanyData existing = new CompanyData();
            existing.setId(50L);
            existing.setNip(TEST_NIP);
            existing.setUser(companyUser);

            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
            when(companyRegistryPort.lookupByNip(TEST_NIP))
                    .thenReturn(CompanyRegistryData.builder().found(false).build());

            // When/Then
            assertThatThrownBy(() -> service.refreshCompanyData(TEST_FIREBASE_UID))
                    .isInstanceOf(NipNotFoundException.class);
        }

        @Test
        @DisplayName("should continue gracefully when Biała Lista fails during refresh")
        void shouldContinueWhenBialaListaFailsDuringRefresh() {
            // Given
            CompanyData existing = new CompanyData();
            existing.setId(50L);
            existing.setNip(TEST_NIP);
            existing.setUser(companyUser);

            when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));
            when(companyDataRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

            CompanyRegistryData gusData = buildGusData();
            when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
            when(companyTypeClassifier.classify(gusData)).thenReturn(CompanyType.SP_ZOO);
            when(companyTypeClassifier.isActive(gusData)).thenReturn(true);
            when(companyTypeClassifier.isSuspended(gusData)).thenReturn(false);
            when(vatRegistryPort.lookupVatStatus(TEST_NIP)).thenThrow(new RuntimeException("API down"));
            when(companyDataRepository.save(any(CompanyData.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            NipLookupResponse response = service.refreshCompanyData(TEST_FIREBASE_UID);

            // Then — should succeed despite Biała Lista failure
            assertThat(response).isNotNull();
            assertThat(response.isSourceVat()).isFalse();
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private void setupValidLookupMocks(CompanyType companyType) {
        when(nipValidator.normalize(TEST_NIP)).thenReturn(TEST_NIP);
        when(nipValidator.isValid(TEST_NIP)).thenReturn(true);
        when(companyDataRepository.existsByNip(TEST_NIP)).thenReturn(false);
        when(userRepository.findByFirebaseUserId(TEST_FIREBASE_UID)).thenReturn(Optional.of(companyUser));

        CompanyRegistryData gusData = buildGusData();
        when(companyRegistryPort.lookupByNip(TEST_NIP)).thenReturn(gusData);
        when(companyTypeClassifier.classify(gusData)).thenReturn(companyType);
        when(companyTypeClassifier.isActive(gusData)).thenReturn(true);
        when(companyTypeClassifier.isSuspended(gusData)).thenReturn(false);

        when(vatRegistryPort.lookupVatStatus(TEST_NIP))
                .thenReturn(VatStatusData.builder()
                        .found(true)
                        .normalizedStatus("ACTIVE")
                        .build());

        RegistryProperties.CacheConfig cacheConfig = new RegistryProperties.CacheConfig();
        when(registryProperties.getCache()).thenReturn(cacheConfig);
    }

    private CompanyRegistryData buildGusData() {
        return CompanyRegistryData.builder()
                .found(true)
                .nip(TEST_NIP)
                .regon("012345678")
                .companyName("Firma Testowa Sp. z o.o.")
                .basicLegalFormCode("1")
                .specificLegalFormCode("117")
                .legalFormName("SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ")
                .city("Warszawa")
                .postalCode("00-001")
                .build();
    }
}
