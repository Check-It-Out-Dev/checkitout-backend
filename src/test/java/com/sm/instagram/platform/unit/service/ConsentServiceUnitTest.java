package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.consent.*;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConsentService Unit Tests")
class ConsentServiceUnitTest {

    @Mock
    private ConsentDefinitionRepository consentDefinitionRepository;

    @Mock
    private ConsentVersionRepository consentVersionRepository;

    @Mock
    private UserConsentRepository userConsentRepository;

    @Mock
    private UserCurrentConsentRepository userCurrentConsentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionUtils permissionUtils;

    @Mock
    private HttpServletRequest request;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private DictionaryService dictionaryService;

    @Mock
    private TranslationService translationService;

    private ConsentService service;

    @BeforeEach
    void setUp() {
        service = new ConsentService(
                consentDefinitionRepository,
                consentVersionRepository,
                userConsentRepository,
                userCurrentConsentRepository,
                userRepository,
                permissionUtils,
                request,
                applicationContext,
                dictionaryService,
                translationService
        );

        // Default setup for getSelf() pattern
        when(applicationContext.getBean(ConsentService.class)).thenReturn(service);
    }

    @Nested
    @DisplayName("createConsentDefinition")
    class CreateConsentDefinitionTests {

        @Test
        @DisplayName("should create consent definition when admin")
        void shouldCreateConsentDefinitionWhenAdmin() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(consentDefinitionRepository.existsByConsentType("MARKETING")).thenReturn(false);

            ConsentDefinitionDtoIn dtoIn = new ConsentDefinitionDtoIn();
            dtoIn.setConsentType("MARKETING");
            dtoIn.setName("Marketing Consent");
            dtoIn.setDescription("Consent for marketing emails");
            dtoIn.setIsActive(true);

            ConsentDefinition savedDefinition = new ConsentDefinition();
            savedDefinition.setId(1L);
            savedDefinition.setConsentType("MARKETING");
            savedDefinition.setName("Marketing Consent");
            savedDefinition.setIsActive(true);

            when(consentDefinitionRepository.save(any(ConsentDefinition.class))).thenReturn(savedDefinition);

            // When
            ConsentDefinitionDtoOut result = service.createConsentDefinition(dtoIn);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getConsentType()).isEqualTo("MARKETING");
            verify(consentDefinitionRepository).save(any(ConsentDefinition.class));
        }

        @Test
        @DisplayName("should throw exception when non-admin tries to create")
        void shouldThrowExceptionWhenNonAdminTriesToCreate() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn("user-uid");

            ConsentDefinitionDtoIn dtoIn = new ConsentDefinitionDtoIn();
            dtoIn.setConsentType("MARKETING");

            // When/Then
            assertThatThrownBy(() -> service.createConsentDefinition(dtoIn))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw exception when consent type already exists")
        void shouldThrowExceptionWhenConsentTypeExists() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(consentDefinitionRepository.existsByConsentType("MARKETING")).thenReturn(true);

            ConsentDefinitionDtoIn dtoIn = new ConsentDefinitionDtoIn();
            dtoIn.setConsentType("MARKETING");

            // When/Then
            assertThatThrownBy(() -> service.createConsentDefinition(dtoIn))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("createConsentVersion")
    class CreateConsentVersionTests {

        @Test
        @DisplayName("should create consent version when admin")
        void shouldCreateConsentVersionWhenAdmin() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("admin-uid");

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");

            when(consentDefinitionRepository.findById(1L)).thenReturn(Optional.of(definition));
            when(consentVersionRepository.findCurrentVersionByDefinitionId(anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Optional.empty());

            ConsentVersionDtoIn dtoIn = new ConsentVersionDtoIn();
            dtoIn.setConsentDefinitionId(1L);
            dtoIn.setVersion("1.0");
            dtoIn.setConsentText("I agree to receive marketing emails");
            dtoIn.setPolicyUrl("https://example.com/policy");
            dtoIn.setEffectiveFrom(LocalDateTime.now().plusDays(1));

            ConsentVersion savedVersion = new ConsentVersion();
            savedVersion.setId(1L);
            savedVersion.setConsentDefinition(definition);
            savedVersion.setVersion("1.0");
            savedVersion.setConsentText("I agree to receive marketing emails");

            when(consentVersionRepository.save(any(ConsentVersion.class))).thenReturn(savedVersion);

            // When
            ConsentVersionDtoOut result = service.createConsentVersion(dtoIn);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo("1.0");
            verify(consentVersionRepository).save(any(ConsentVersion.class));
        }

        @Test
        @DisplayName("should throw exception when non-admin tries to create version")
        void shouldThrowExceptionWhenNonAdminTriesToCreateVersion() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn("user-uid");

            ConsentVersionDtoIn dtoIn = new ConsentVersionDtoIn();
            dtoIn.setConsentDefinitionId(1L);

            // When/Then
            assertThatThrownBy(() -> service.createConsentVersion(dtoIn))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw exception when consent definition not found")
        void shouldThrowExceptionWhenDefinitionNotFound() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(consentDefinitionRepository.findById(999L)).thenReturn(Optional.empty());

            ConsentVersionDtoIn dtoIn = new ConsentVersionDtoIn();
            dtoIn.setConsentDefinitionId(999L);

            // When/Then
            assertThatThrownBy(() -> service.createConsentVersion(dtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getAllConsentDefinitions")
    class GetAllConsentDefinitionsTests {

        @Test
        @DisplayName("should return all active consent definitions for admin")
        void shouldReturnAllActiveDefinitionsForAdmin() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);

            ConsentDefinition def1 = new ConsentDefinition();
            def1.setId(1L);
            def1.setConsentType("MARKETING");
            def1.setIsActive(true);

            ConsentDefinition def2 = new ConsentDefinition();
            def2.setId(2L);
            def2.setConsentType("ANALYTICS");
            def2.setIsActive(true);

            when(consentDefinitionRepository.findByIsActiveTrueOrderByConsentType())
                    .thenReturn(List.of(def1, def2));

            // When
            List<ConsentDefinitionDtoOut> result = service.getAllConsentDefinitions();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getConsentType()).isEqualTo("MARKETING");
            assertThat(result.get(1).getConsentType()).isEqualTo("ANALYTICS");
        }

        @Test
        @DisplayName("should throw exception when non-admin tries to get all definitions")
        void shouldThrowExceptionWhenNonAdminTriesToGetAll() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn("user-uid");

            // When/Then
            assertThatThrownBy(() -> service.getAllConsentDefinitions())
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("getMyAvailableConsents")
    class GetMyAvailableConsentsTests {

        @Test
        @DisplayName("should return available consents for current user")
        void shouldReturnAvailableConsentsForCurrentUser() {
            // Given
            String firebaseUid = "user-uid";
            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);

            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(firebaseUid);

            // Both findByFirebaseUserId and findById are called (findById by validateUserAccess)
            when(userRepository.findByFirebaseUserId(firebaseUid)).thenReturn(Optional.of(user));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");
            definition.setName("Marketing Consent");
            definition.setIsActive(true);

            when(consentDefinitionRepository.findByIsActiveTrueOrderByConsentType())
                    .thenReturn(List.of(definition));

            when(consentVersionRepository.findCurrentVersionByDefinitionId(anyLong(), any(LocalDateTime.class)))
                    .thenReturn(Optional.empty());

            when(userCurrentConsentRepository.findByUserIdAndConsentDefinitionId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());

            // When
            List<UserCurrentConsentDtoOut> result = service.getMyAvailableConsents();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getConsentGiven()).isFalse();
        }

        @Test
        @DisplayName("should throw exception when user not found")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("unknown-uid");
            when(userRepository.findByFirebaseUserId("unknown-uid")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getMyAvailableConsents())
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("recordUserConsentForUser")
    class RecordUserConsentForUserTests {

        @Test
        @DisplayName("should record user consent when user grants consent")
        void shouldRecordConsentWhenUserGrants() {
            // Given
            String firebaseUid = "user-uid";
            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);

            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(firebaseUid);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");

            ConsentVersion version = new ConsentVersion();
            version.setId(1L);
            version.setConsentDefinition(definition);
            version.setVersion("1.0");

            when(consentVersionRepository.findCurrentVersionByConsentType(eq("MARKETING"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(version));

            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("MARKETING");
            dtoIn.setConsentGiven(true);
            dtoIn.setCollectionMethod("WEB_FORM");

            UserConsent savedConsent = new UserConsent();
            savedConsent.setId(1L);
            savedConsent.setUser(user);
            savedConsent.setConsentVersion(version);
            savedConsent.setConsentGiven(true);
            savedConsent.setAction(ConsentAction.GRANTED);

            when(userConsentRepository.save(any(UserConsent.class))).thenReturn(savedConsent);
            when(userCurrentConsentRepository.findByUserIdAndConsentDefinitionId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());

            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            // When
            UserConsentDtoOut result = service.recordUserConsentForUser(1L, dtoIn);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getConsentGiven()).isTrue();
            verify(userConsentRepository).save(any(UserConsent.class));
            verify(userCurrentConsentRepository).save(any(UserCurrentConsent.class));
        }

        @Test
        @DisplayName("should throw exception when consent version not found")
        void shouldThrowExceptionWhenVersionNotFound() {
            // Given
            String firebaseUid = "user-uid";
            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);

            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(firebaseUid);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(consentVersionRepository.findCurrentVersionByConsentType(eq("UNKNOWN"), any(LocalDateTime.class)))
                    .thenReturn(Optional.empty());

            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("UNKNOWN");
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> service.recordUserConsentForUser(1L, dtoIn))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw exception when non-owner tries to record consent")
        void shouldThrowExceptionWhenNonOwnerTriesToRecord() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("other-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);

            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId("owner-uid");

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("MARKETING");
            dtoIn.setConsentGiven(true);

            // When/Then
            assertThatThrownBy(() -> service.recordUserConsentForUser(1L, dtoIn))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("getUserConsentHistoryForUser")
    class GetUserConsentHistoryTests {

        @Test
        @DisplayName("should return consent history for user")
        void shouldReturnConsentHistoryForUser() {
            // Given
            String firebaseUid = "user-uid";
            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);

            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(firebaseUid);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");

            ConsentVersion version = new ConsentVersion();
            version.setId(1L);
            version.setConsentDefinition(definition);

            UserConsent consent1 = new UserConsent();
            consent1.setId(1L);
            consent1.setUser(user);
            consent1.setConsentVersion(version);
            consent1.setConsentGiven(true);
            consent1.setAction(ConsentAction.GRANTED);

            UserConsent consent2 = new UserConsent();
            consent2.setId(2L);
            consent2.setUser(user);
            consent2.setConsentVersion(version);
            consent2.setConsentGiven(false);
            consent2.setAction(ConsentAction.WITHDRAWN);

            when(userConsentRepository.findByUserIdAndConsentType(1L, "MARKETING"))
                    .thenReturn(List.of(consent1, consent2));

            // When
            List<UserConsentDtoOut> result = service.getUserConsentHistoryForUser(1L, "MARKETING");

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no consent history")
        void shouldReturnEmptyListWhenNoHistory() {
            // Given
            String firebaseUid = "user-uid";
            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);

            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(firebaseUid);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userConsentRepository.findByUserIdAndConsentType(1L, "MARKETING"))
                    .thenReturn(List.of());

            // When
            List<UserConsentDtoOut> result = service.getUserConsentHistoryForUser(1L, "MARKETING");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("admin should access any user consent history")
        void adminShouldAccessAnyUserConsentHistory() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);

            when(userConsentRepository.findByUserIdAndConsentType(1L, "MARKETING"))
                    .thenReturn(List.of());

            // When
            List<UserConsentDtoOut> result = service.getUserConsentHistoryForUser(1L, "MARKETING");

            // Then
            assertThat(result).isEmpty();
            verify(userRepository, never()).findById(anyLong()); // Admin doesn't need ownership check
        }
    }

    @Nested
    @DisplayName("createConsentVersion - version expiration")
    class CreateConsentVersionExpirationTests {

        @Test
        @DisplayName("should expire current version when new version is effective now")
        void shouldExpireCurrentVersionWhenNewVersionEffectiveNow() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("admin-uid");

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");

            ConsentVersion existingVersion = new ConsentVersion();
            existingVersion.setId(1L);
            existingVersion.setConsentDefinition(definition);
            existingVersion.setVersion("1.0");
            existingVersion.setEffectiveFrom(LocalDateTime.now().minusDays(30));
            existingVersion.setEffectiveUntil(null); // Currently active

            when(consentDefinitionRepository.findById(1L)).thenReturn(Optional.of(definition));
            when(consentVersionRepository.findCurrentVersionByDefinitionId(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(existingVersion));

            ConsentVersionDtoIn dtoIn = new ConsentVersionDtoIn();
            dtoIn.setConsentDefinitionId(1L);
            dtoIn.setVersion("2.0");
            dtoIn.setConsentText("Updated consent text");
            dtoIn.setEffectiveFrom(LocalDateTime.now().minusMinutes(1)); // Effective now/past

            ConsentVersion savedVersion = new ConsentVersion();
            savedVersion.setId(2L);
            savedVersion.setConsentDefinition(definition);
            savedVersion.setVersion("2.0");

            when(consentVersionRepository.save(any(ConsentVersion.class)))
                    .thenAnswer(inv -> {
                        ConsentVersion v = inv.getArgument(0);
                        if (v.getId() == null) {
                            v.setId(2L);
                        }
                        return v;
                    });

            // When
            ConsentVersionDtoOut result = service.createConsentVersion(dtoIn);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo("2.0");
            // Verify that existing version was updated (expired)
            verify(consentVersionRepository, times(2)).save(any(ConsentVersion.class));
        }

        @Test
        @DisplayName("should not expire current version when new version is effective in future")
        void shouldNotExpireCurrentVersionWhenNewVersionEffectiveInFuture() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("admin-uid");

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");

            when(consentDefinitionRepository.findById(1L)).thenReturn(Optional.of(definition));
            when(consentVersionRepository.findCurrentVersionByDefinitionId(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Optional.empty());

            ConsentVersionDtoIn dtoIn = new ConsentVersionDtoIn();
            dtoIn.setConsentDefinitionId(1L);
            dtoIn.setVersion("1.0");
            dtoIn.setConsentText("Consent text");
            dtoIn.setEffectiveFrom(LocalDateTime.now().plusDays(30)); // Future date

            ConsentVersion savedVersion = new ConsentVersion();
            savedVersion.setId(1L);
            savedVersion.setConsentDefinition(definition);
            savedVersion.setVersion("1.0");

            when(consentVersionRepository.save(any(ConsentVersion.class))).thenReturn(savedVersion);

            // When
            ConsentVersionDtoOut result = service.createConsentVersion(dtoIn);

            // Then
            assertThat(result).isNotNull();
            verify(consentVersionRepository, times(1)).save(any(ConsentVersion.class)); // Only new version saved
        }
    }

    @Nested
    @DisplayName("getAvailableConsentsForUser - existing consent")
    class GetAvailableConsentsWithExistingConsentTests {

        @Test
        @DisplayName("should return consents with existing consent status")
        void shouldReturnConsentsWithExistingConsentStatus() {
            // Given
            String firebaseUid = "user-uid";
            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);

            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(firebaseUid);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");
            definition.setName("Marketing Consent");
            definition.setDescription("Consent for marketing");
            definition.setIsActive(true);

            ConsentVersion currentVersion = new ConsentVersion();
            currentVersion.setId(1L);
            currentVersion.setConsentDefinition(definition);
            currentVersion.setVersion("1.0");
            currentVersion.setConsentText("I agree to marketing emails");
            currentVersion.setPolicyUrl("https://example.com/policy");

            UserCurrentConsent existingConsent = new UserCurrentConsent();
            existingConsent.setUserId(1L);
            existingConsent.setConsentDefinitionId(1L);
            existingConsent.setConsentGiven(true);
            existingConsent.setGrantedAt(LocalDateTime.now().minusDays(7));
            existingConsent.setLastUpdated(LocalDateTime.now().minusDays(7));

            when(consentDefinitionRepository.findByIsActiveTrueOrderByConsentType())
                    .thenReturn(List.of(definition));
            when(consentVersionRepository.findCurrentVersionByDefinitionId(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(currentVersion));
            when(userCurrentConsentRepository.findByUserIdAndConsentDefinitionId(1L, 1L))
                    .thenReturn(Optional.of(existingConsent));

            // When
            List<UserCurrentConsentDtoOut> result = service.getAvailableConsentsForUser(1L);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getConsentGiven()).isTrue();
            assertThat(result.get(0).getCurrentVersion()).isEqualTo("1.0");
            assertThat(result.get(0).getGrantedAt()).isNotNull();
        }

        @Test
        @DisplayName("should return empty list when no active definitions")
        void shouldReturnEmptyListWhenNoActiveDefinitions() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));

            when(consentDefinitionRepository.findByIsActiveTrueOrderByConsentType())
                    .thenReturn(List.of());

            // When
            List<UserCurrentConsentDtoOut> result = service.getAvailableConsentsForUser(1L);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("recordUserConsentForUser - withdraw consent")
    class RecordUserConsentWithdrawTests {

        @Test
        @DisplayName("should record consent withdrawal")
        void shouldRecordConsentWithdrawal() {
            // Given
            String firebaseUid = "user-uid";
            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);

            User user = new User();
            user.setId(1L);
            user.setFirebaseUserId(firebaseUid);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");

            ConsentVersion version = new ConsentVersion();
            version.setId(1L);
            version.setConsentDefinition(definition);
            version.setVersion("1.0");

            when(consentVersionRepository.findCurrentVersionByConsentType(eq("MARKETING"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(version));

            UserCurrentConsent existingConsent = new UserCurrentConsent();
            existingConsent.setUserId(1L);
            existingConsent.setConsentDefinitionId(1L);
            existingConsent.setConsentGiven(true);

            when(userCurrentConsentRepository.findByUserIdAndConsentDefinitionId(1L, 1L))
                    .thenReturn(Optional.of(existingConsent));

            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("MARKETING");
            dtoIn.setConsentGiven(false); // Withdrawing
            dtoIn.setCollectionMethod("WEB_FORM");

            UserConsent savedConsent = new UserConsent();
            savedConsent.setId(1L);
            savedConsent.setUser(user);
            savedConsent.setConsentVersion(version);
            savedConsent.setConsentGiven(false);
            savedConsent.setAction(ConsentAction.WITHDRAWN);

            when(userConsentRepository.save(any(UserConsent.class))).thenReturn(savedConsent);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            // When
            UserConsentDtoOut result = service.recordUserConsentForUser(1L, dtoIn);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getConsentGiven()).isFalse();
            assertThat(result.getAction().getValue()).isEqualTo("WITHDRAWN");
            verify(userCurrentConsentRepository).save(any(UserCurrentConsent.class));
        }

        @Test
        @DisplayName("admin should record consent for any user")
        void adminShouldRecordConsentForAnyUser() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);

            User targetUser = new User();
            targetUser.setId(99L);
            targetUser.setFirebaseUserId("target-uid");

            when(userRepository.findById(99L)).thenReturn(Optional.of(targetUser));

            ConsentDefinition definition = new ConsentDefinition();
            definition.setId(1L);
            definition.setConsentType("MARKETING");

            ConsentVersion version = new ConsentVersion();
            version.setId(1L);
            version.setConsentDefinition(definition);
            version.setVersion("1.0");

            when(consentVersionRepository.findCurrentVersionByConsentType(eq("MARKETING"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(version));
            when(userCurrentConsentRepository.findByUserIdAndConsentDefinitionId(anyLong(), anyLong()))
                    .thenReturn(Optional.empty());

            UserConsentDtoIn dtoIn = new UserConsentDtoIn();
            dtoIn.setConsentType("MARKETING");
            dtoIn.setConsentGiven(true);

            UserConsent savedConsent = new UserConsent();
            savedConsent.setId(1L);
            savedConsent.setUser(targetUser);
            savedConsent.setConsentVersion(version);
            savedConsent.setConsentGiven(true);
            savedConsent.setAction(ConsentAction.GRANTED);

            when(userConsentRepository.save(any(UserConsent.class))).thenReturn(savedConsent);
            when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

            // When
            UserConsentDtoOut result = service.recordUserConsentForUser(99L, dtoIn);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getConsentGiven()).isTrue();
        }
    }

    @Nested
    @DisplayName("createConsentDefinition - defaults")
    class CreateConsentDefinitionDefaultsTests {

        @Test
        @DisplayName("should set isActive to true by default when null")
        void shouldSetIsActiveTrueByDefault() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(consentDefinitionRepository.existsByConsentType("NEW_TYPE")).thenReturn(false);

            ConsentDefinitionDtoIn dtoIn = new ConsentDefinitionDtoIn();
            dtoIn.setConsentType("NEW_TYPE");
            dtoIn.setName("New Consent");
            dtoIn.setIsActive(null); // Null should default to true

            ConsentDefinition savedDefinition = new ConsentDefinition();
            savedDefinition.setId(1L);
            savedDefinition.setConsentType("NEW_TYPE");
            savedDefinition.setIsActive(true);

            when(consentDefinitionRepository.save(any(ConsentDefinition.class)))
                    .thenAnswer(inv -> {
                        ConsentDefinition def = inv.getArgument(0);
                        assertThat(def.getIsActive()).isTrue(); // Verify default was set
                        def.setId(1L);
                        return def;
                    });

            // When
            ConsentDefinitionDtoOut result = service.createConsentDefinition(dtoIn);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("should use provided isActive value when not null")
        void shouldUseProvidedIsActiveValue() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(consentDefinitionRepository.existsByConsentType("INACTIVE_TYPE")).thenReturn(false);

            ConsentDefinitionDtoIn dtoIn = new ConsentDefinitionDtoIn();
            dtoIn.setConsentType("INACTIVE_TYPE");
            dtoIn.setName("Inactive Consent");
            dtoIn.setIsActive(false); // Explicitly set to false

            ConsentDefinition savedDefinition = new ConsentDefinition();
            savedDefinition.setId(1L);
            savedDefinition.setConsentType("INACTIVE_TYPE");
            savedDefinition.setIsActive(false);

            when(consentDefinitionRepository.save(any(ConsentDefinition.class)))
                    .thenAnswer(inv -> {
                        ConsentDefinition def = inv.getArgument(0);
                        assertThat(def.getIsActive()).isFalse(); // Verify value was preserved
                        def.setId(1L);
                        return def;
                    });

            // When
            ConsentDefinitionDtoOut result = service.createConsentDefinition(dtoIn);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("ConsentAction enum")
    class ConsentActionEnumTests {

        @Test
        @DisplayName("should have GRANTED action")
        void shouldHaveGrantedAction() {
            assertThat(ConsentAction.GRANTED).isNotNull();
            assertThat(ConsentAction.GRANTED.isPositiveAction()).isTrue();
        }

        @Test
        @DisplayName("should have WITHDRAWN action")
        void shouldHaveWithdrawnAction() {
            assertThat(ConsentAction.WITHDRAWN).isNotNull();
            assertThat(ConsentAction.WITHDRAWN.isNegativeAction()).isTrue();
        }
    }
}
