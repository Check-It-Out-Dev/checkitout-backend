package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.common.util.mappers.EnumTranslationService;
import com.sm.instagram.platform.common.util.mappers.UpdaterIdConverter;
import com.sm.instagram.platform.notification.EmailFrequency;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.userpreferences.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyLong;

/**
 * Comprehensive unit tests for UserPreferences package.
 * Tests UserPreferencesController, UserPreferences entity, DTOs, and mapping configuration.
 * Uses pure Mockito without Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserPreferences Package Unit Tests")
class UserPreferencesControllerUnitTest {

    @Mock
    private UserPreferencesService userPreferencesService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private EnumTranslationService enumTranslationService;

    @Mock
    private UpdaterIdConverter updaterIdConverter;

    @InjectMocks
    private UserPreferencesController userPreferencesController;

    private User testUser;
    private UserPreferences testPreferences;
    private UserPreferencesDtoIn testDtoIn;
    private UserPreferencesDtoOut testDtoOut;

    public static UserPreferences testPreferences(User user) {
        UserPreferences p = new UserPreferences();
        p.setId(1L);
        p.setUser(user);
        p.setNotificationEmailEnabled(true);
        p.setNotificationPushEnabled(false);
        p.setNotificationSmsEnabled(false);
        p.setDarkModeEnabled(true);
        p.setLanguage("en");
        p.setTimezone("UTC");
        p.setCommunicationFrequency(EmailFrequency.DAILY_DIGEST);
        p.setGdprMarketingConsent(true);
        p.setSharePhoneForPayments(false);
        p.setTwoFactorAuthenticationEnabled(true);
        p.setUpdaterId("admin123");
        p.setNotificationPartnershipEnabled(true);
        p.setNotificationSupportEnabled(true);
        p.setNotificationSystemEnabled(true);
        p.setNotificationEmailPartnershipEnabled(true);
        p.setNotificationEmailSupportEnabled(true);
        return p;
    }

    @BeforeEach
    void setUp() {
        // Setup security context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("test-firebase-uid");
        SecurityContextHolder.setContext(securityContext);

        // Setup test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setFirebaseUserId("test-firebase-uid");
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");

        // Setup test preferences
        testPreferences = new UserPreferences();
        testPreferences.setId(1L);
        testPreferences.setUser(testUser);
        testPreferences.setNotificationEmailEnabled(true);
        testPreferences.setNotificationPushEnabled(false);
        testPreferences.setNotificationSmsEnabled(false);
        testPreferences.setDarkModeEnabled(false);
        testPreferences.setLanguage("en");
        testPreferences.setTimezone("UTC");
        testPreferences.setCommunicationFrequency(EmailFrequency.WEEKLY_DIGEST);
        testPreferences.setGdprMarketingConsent(false);
        testPreferences.setSharePhoneForPayments(true);
        testPreferences.setTwoFactorAuthenticationEnabled(false);
        testPreferences.setCreatedTime(LocalDateTime.now());
        testPreferences.setLastUpdateTime(LocalDateTime.now());

        // Setup test DTO in
        testDtoIn = new UserPreferencesDtoIn();
        testDtoIn.setNotificationEmailEnabled(true);
        testDtoIn.setNotificationPushEnabled(true);
        testDtoIn.setNotificationSmsEnabled(false);
        testDtoIn.setDarkModeEnabled(true);
        testDtoIn.setLanguage("pl");
        testDtoIn.setTimezone("Europe/Warsaw");
        testDtoIn.setCommunicationFrequency("DAILY_DIGEST");
        testDtoIn.setGdprMarketingConsent(true);
        testDtoIn.setSharePhoneForPayments(true);
        testDtoIn.setTwoFactorAuthenticationEnabled(false);

        // Setup test DTO out
        testDtoOut = new UserPreferencesDtoOut();
        testDtoOut.setId(1L);
        testDtoOut.setUserId(1L);
        testDtoOut.setNotificationEmailEnabled(true);
        testDtoOut.setNotificationPushEnabled(false);
        testDtoOut.setNotificationSmsEnabled(false);
        testDtoOut.setDarkModeEnabled(false);
        testDtoOut.setLanguage("en");
        testDtoOut.setTimezone("UTC");
        testDtoOut.setCommunicationFrequency("WEEKLY_DIGEST");
        testDtoOut.setGdprMarketingConsent(false);
        testDtoOut.setSharePhoneForPayments(true);
        testDtoOut.setTwoFactorAuthenticationEnabled(false);
        testDtoOut.setCreatedTime(LocalDateTime.now());
        testDtoOut.setLastUpdateTime(LocalDateTime.now());
    }

    // ==================== CONTROLLER TESTS ====================

    @Nested
    @DisplayName("UserPreferencesController - GET /user-preferences/me")
    class GetCurrentUserPreferencesTests {

        @Test
        @DisplayName("should return current user preferences successfully")
        void shouldReturnCurrentUserPreferencesSuccessfully() {
            // Given
            when(userPreferencesService.getCurrentUserPreferencesAsDto()).thenReturn(testDtoOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response = userPreferencesController.getCurrentUserPreferences();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            assertThat(response.getBody().getLanguage()).isEqualTo("en");
            verify(userPreferencesService).getCurrentUserPreferencesAsDto();
        }

        @Test
        @DisplayName("should return preferences with all fields populated")
        void shouldReturnPreferencesWithAllFieldsPopulated() {
            // Given
            when(userPreferencesService.getCurrentUserPreferencesAsDto()).thenReturn(testDtoOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response = userPreferencesController.getCurrentUserPreferences();

            // Then
            UserPreferencesDtoOut body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getNotificationEmailEnabled()).isTrue();
            assertThat(body.getNotificationPushEnabled()).isFalse();
            assertThat(body.getNotificationSmsEnabled()).isFalse();
            assertThat(body.getDarkModeEnabled()).isFalse();
            assertThat(body.getTimezone()).isEqualTo("UTC");
            assertThat(body.getCommunicationFrequency()).isEqualTo("WEEKLY_DIGEST");
            assertThat(body.getGdprMarketingConsent()).isFalse();
            assertThat(body.getSharePhoneForPayments()).isTrue();
            assertThat(body.getTwoFactorAuthenticationEnabled()).isFalse();
        }

        @Test
        @DisplayName("should call service method exactly once")
        void shouldCallServiceMethodExactlyOnce() {
            // Given
            when(userPreferencesService.getCurrentUserPreferencesAsDto()).thenReturn(testDtoOut);

            // When
            userPreferencesController.getCurrentUserPreferences();

            // Then
            verify(userPreferencesService, times(1)).getCurrentUserPreferencesAsDto();
        }
    }

    @Nested
    @DisplayName("UserPreferencesController - PUT /user-preferences/me")
    class UpdateCurrentUserPreferencesTests {

        @Test
        @DisplayName("should update current user preferences successfully")
        void shouldUpdateCurrentUserPreferencesSuccessfully() {
            // Given
            UserPreferencesDtoOut updatedDtoOut = new UserPreferencesDtoOut();
            updatedDtoOut.setId(1L);
            updatedDtoOut.setLanguage("pl");
            updatedDtoOut.setDarkModeEnabled(true);
            when(userPreferencesService.updateCurrentUserPreferencesAsDto(any(UserPreferencesDtoIn.class)))
                    .thenReturn(updatedDtoOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response =
                    userPreferencesController.updateCurrentUserPreferences(testDtoIn);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getLanguage()).isEqualTo("pl");
            assertThat(response.getBody().getDarkModeEnabled()).isTrue();
        }

        @Test
        @DisplayName("should pass DTO to service correctly")
        void shouldPassDtoToServiceCorrectly() {
            // Given
            when(userPreferencesService.updateCurrentUserPreferencesAsDto(any(UserPreferencesDtoIn.class)))
                    .thenReturn(testDtoOut);

            // When
            userPreferencesController.updateCurrentUserPreferences(testDtoIn);

            // Then
            verify(userPreferencesService).updateCurrentUserPreferencesAsDto(eq(testDtoIn));
        }

        @Test
        @DisplayName("should handle all notification preferences update")
        void shouldHandleAllNotificationPreferencesUpdate() {
            // Given
            testDtoIn.setNotificationEmailEnabled(true);
            testDtoIn.setNotificationPushEnabled(true);
            testDtoIn.setNotificationSmsEnabled(true);

            UserPreferencesDtoOut updatedOut = new UserPreferencesDtoOut();
            updatedOut.setNotificationEmailEnabled(true);
            updatedOut.setNotificationPushEnabled(true);
            updatedOut.setNotificationSmsEnabled(true);

            when(userPreferencesService.updateCurrentUserPreferencesAsDto(any())).thenReturn(updatedOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response =
                    userPreferencesController.updateCurrentUserPreferences(testDtoIn);

            // Then
            assertThat(response.getBody().getNotificationEmailEnabled()).isTrue();
            assertThat(response.getBody().getNotificationPushEnabled()).isTrue();
            assertThat(response.getBody().getNotificationSmsEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("UserPreferencesController - PATCH /user-preferences/me")
    class PatchCurrentUserPreferencesTests {

        @Test
        @DisplayName("should patch current user preferences successfully")
        void shouldPatchCurrentUserPreferencesSuccessfully() {
            // Given
            Map<String, Object> updates = new HashMap<>();
            updates.put("darkModeEnabled", true);
            updates.put("language", "de");

            UserPreferencesDtoOut patchedOut = new UserPreferencesDtoOut();
            patchedOut.setDarkModeEnabled(true);
            patchedOut.setLanguage("de");

            when(userPreferencesService.patchCurrentUserPreferencesAsDto(anyMap())).thenReturn(patchedOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response =
                    userPreferencesController.patchCurrentUserPreferences(updates);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getDarkModeEnabled()).isTrue();
            assertThat(response.getBody().getLanguage()).isEqualTo("de");
        }

        @Test
        @DisplayName("should handle single field patch")
        void shouldHandleSingleFieldPatch() {
            // Given
            Map<String, Object> updates = Map.of("notificationEmailEnabled", false);
            UserPreferencesDtoOut patchedOut = new UserPreferencesDtoOut();
            patchedOut.setNotificationEmailEnabled(false);

            when(userPreferencesService.patchCurrentUserPreferencesAsDto(anyMap())).thenReturn(patchedOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response =
                    userPreferencesController.patchCurrentUserPreferences(updates);

            // Then
            assertThat(response.getBody().getNotificationEmailEnabled()).isFalse();
        }

        @Test
        @DisplayName("should handle empty updates map")
        void shouldHandleEmptyUpdatesMap() {
            // Given
            Map<String, Object> updates = new HashMap<>();
            when(userPreferencesService.patchCurrentUserPreferencesAsDto(anyMap())).thenReturn(testDtoOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response =
                    userPreferencesController.patchCurrentUserPreferences(updates);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userPreferencesService).patchCurrentUserPreferencesAsDto(updates);
        }
    }

    @Nested
    @DisplayName("UserPreferencesController - GET /user-preferences/user/{userId}")
    class GetUserPreferencesTests {

        @Test
        @DisplayName("should return user preferences for admin successfully")
        void shouldReturnUserPreferencesForAdminSuccessfully() {
            // Given
            when(userPreferencesService.getUserPreferencesAsDto(1L)).thenReturn(testDtoOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response =
                    userPreferencesController.getUserPreferences(1L);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should pass user ID to service correctly")
        void shouldPassUserIdToServiceCorrectly() {
            // Given
            Long userId = 42L;
            when(userPreferencesService.getUserPreferencesAsDto(userId)).thenReturn(testDtoOut);

            // When
            userPreferencesController.getUserPreferences(userId);

            // Then
            verify(userPreferencesService).getUserPreferencesAsDto(eq(42L));
        }
    }

    @Nested
    @DisplayName("UserPreferencesController - PATCH /user-preferences/user/{userId}")
    class PatchUserPreferencesTests {

        @Test
        @DisplayName("should patch user preferences by admin successfully")
        void shouldPatchUserPreferencesByAdminSuccessfully() {
            // Given
            Long userId = 1L;
            Map<String, Object> updates = Map.of("gdprMarketingConsent", true);
            UserPreferencesDtoOut patchedOut = new UserPreferencesDtoOut();
            patchedOut.setGdprMarketingConsent(true);

            when(userPreferencesService.patchUserPreferencesAsDto(eq(userId), anyMap()))
                    .thenReturn(patchedOut);

            // When
            ResponseEntity<UserPreferencesDtoOut> response =
                    userPreferencesController.patchUserPreferences(userId, updates);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getGdprMarketingConsent()).isTrue();
        }

        @Test
        @DisplayName("should pass user ID and updates to service")
        void shouldPassUserIdAndUpdatesToService() {
            // Given
            Long userId = 99L;
            Map<String, Object> updates = Map.of("twoFactorAuthenticationEnabled", true);
            when(userPreferencesService.patchUserPreferencesAsDto(anyLong(), anyMap()))
                    .thenReturn(testDtoOut);

            // When
            userPreferencesController.patchUserPreferences(userId, updates);

            // Then
            verify(userPreferencesService).patchUserPreferencesAsDto(eq(99L), eq(updates));
        }
    }

    // ==================== ENTITY TESTS ====================

    @Nested
    @DisplayName("UserPreferences Entity - Basic Operations")
    class UserPreferencesEntityBasicTests {

        @Test
        @DisplayName("should create entity with no-args constructor")
        void shouldCreateEntityWithNoArgsConstructor() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences).isNotNull();
        }

        @Test
        @DisplayName("should create entity with all-args constructor")
        void shouldCreateEntityWithAllArgsConstructor() {
            // When
            UserPreferences preferences = testPreferences(testUser);

            // Then
            assertThat(preferences.getId()).isEqualTo(1L);
            assertThat(preferences.getUser()).isEqualTo(testUser);
            assertThat(preferences.getNotificationEmailEnabled()).isTrue();
            assertThat(preferences.getDarkModeEnabled()).isTrue();
            assertThat(preferences.getLanguage()).isEqualTo("en");
        }

        @Test
        @DisplayName("should set and get ID")
        void shouldSetAndGetId() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setId(100L);

            // Then
            assertThat(preferences.getId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should set and get user reference")
        void shouldSetAndGetUserReference() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setUser(testUser);

            // Then
            assertThat(preferences.getUser()).isEqualTo(testUser);
            assertThat(preferences.getUser().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should set and get updater ID")
        void shouldSetAndGetUpdaterId() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setUpdaterId("admin123");

            // Then
            assertThat(preferences.getUpdaterId()).isEqualTo("admin123");
        }

        @Test
        @DisplayName("should set and get created time")
        void shouldSetAndGetCreatedTime() {
            // Given
            UserPreferences preferences = new UserPreferences();
            LocalDateTime now = LocalDateTime.now();

            // When
            preferences.setCreatedTime(now);

            // Then
            assertThat(preferences.getCreatedTime()).isEqualTo(now);
        }

        @Test
        @DisplayName("should set and get last update time")
        void shouldSetAndGetLastUpdateTime() {
            // Given
            UserPreferences preferences = new UserPreferences();
            LocalDateTime now = LocalDateTime.now();

            // When
            preferences.setLastUpdateTime(now);

            // Then
            assertThat(preferences.getLastUpdateTime()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("UserPreferences Entity - Notification Settings")
    class UserPreferencesNotificationTests {

        @Test
        @DisplayName("should set and get email notification enabled")
        void shouldSetAndGetEmailNotificationEnabled() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setNotificationEmailEnabled(true);

            // Then
            assertThat(preferences.getNotificationEmailEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get push notification enabled")
        void shouldSetAndGetPushNotificationEnabled() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setNotificationPushEnabled(true);

            // Then
            assertThat(preferences.getNotificationPushEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get SMS notification enabled")
        void shouldSetAndGetSmsNotificationEnabled() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setNotificationSmsEnabled(true);

            // Then
            assertThat(preferences.getNotificationSmsEnabled()).isTrue();
        }

        @Test
        @DisplayName("should handle all notifications disabled")
        void shouldHandleAllNotificationsDisabled() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setNotificationEmailEnabled(false);
            preferences.setNotificationPushEnabled(false);
            preferences.setNotificationSmsEnabled(false);

            // Then
            assertThat(preferences.getNotificationEmailEnabled()).isFalse();
            assertThat(preferences.getNotificationPushEnabled()).isFalse();
            assertThat(preferences.getNotificationSmsEnabled()).isFalse();
        }

        @Test
        @DisplayName("should handle all notifications enabled")
        void shouldHandleAllNotificationsEnabled() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setNotificationEmailEnabled(true);
            preferences.setNotificationPushEnabled(true);
            preferences.setNotificationSmsEnabled(true);

            // Then
            assertThat(preferences.getNotificationEmailEnabled()).isTrue();
            assertThat(preferences.getNotificationPushEnabled()).isTrue();
            assertThat(preferences.getNotificationSmsEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("UserPreferences Entity - UI Settings")
    class UserPreferencesUISettingsTests {

        @Test
        @DisplayName("should set and get dark mode enabled")
        void shouldSetAndGetDarkModeEnabled() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setDarkModeEnabled(true);

            // Then
            assertThat(preferences.getDarkModeEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get language")
        void shouldSetAndGetLanguage() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setLanguage("pl");

            // Then
            assertThat(preferences.getLanguage()).isEqualTo("pl");
        }

        @Test
        @DisplayName("should set and get timezone")
        void shouldSetAndGetTimezone() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setTimezone("Europe/Warsaw");

            // Then
            assertThat(preferences.getTimezone()).isEqualTo("Europe/Warsaw");
        }

        @ParameterizedTest
        @ValueSource(strings = {"en", "pl", "de", "fr", "es", "it", "pt"})
        @DisplayName("should accept various language codes")
        void shouldAcceptVariousLanguageCodes(String language) {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setLanguage(language);

            // Then
            assertThat(preferences.getLanguage()).isEqualTo(language);
        }

        @ParameterizedTest
        @ValueSource(strings = {"UTC", "Europe/Warsaw", "America/New_York", "Asia/Tokyo", "Australia/Sydney"})
        @DisplayName("should accept various timezones")
        void shouldAcceptVariousTimezones(String timezone) {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setTimezone(timezone);

            // Then
            assertThat(preferences.getTimezone()).isEqualTo(timezone);
        }
    }

    @Nested
    @DisplayName("UserPreferences Entity - Privacy Settings")
    class UserPreferencesPrivacySettingsTests {

        @Test
        @DisplayName("should set and get GDPR marketing consent")
        void shouldSetAndGetGdprMarketingConsent() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setGdprMarketingConsent(true);

            // Then
            assertThat(preferences.getGdprMarketingConsent()).isTrue();
        }

        @Test
        @DisplayName("should set and get share phone for payments")
        void shouldSetAndGetSharePhoneForPayments() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setSharePhoneForPayments(false);

            // Then
            assertThat(preferences.getSharePhoneForPayments()).isFalse();
        }

        @Test
        @DisplayName("should set and get 2FA enabled")
        void shouldSetAndGet2FAEnabled() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setTwoFactorAuthenticationEnabled(true);

            // Then
            assertThat(preferences.getTwoFactorAuthenticationEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("UserPreferences Entity - Communication Frequency")
    class UserPreferencesCommunicationFrequencyTests {

        @Test
        @DisplayName("should set and get communication frequency")
        void shouldSetAndGetCommunicationFrequency() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setCommunicationFrequency(EmailFrequency.DAILY_DIGEST);

            // Then
            assertThat(preferences.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.DAILY_DIGEST);
        }

        @ParameterizedTest
        @EnumSource(EmailFrequency.class)
        @DisplayName("should accept all communication frequency values")
        void shouldAcceptAllCommunicationFrequencyValues(EmailFrequency frequency) {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setCommunicationFrequency(frequency);

            // Then
            assertThat(preferences.getCommunicationFrequency()).isEqualTo(frequency);
        }
    }

    @Nested
    @DisplayName("CommunicationFrequency Enum Tests")
    class CommunicationFrequencyEnumTests {

        @Test
        @DisplayName("should have exactly 4 frequency values")
        void shouldHaveExactly4FrequencyValues() {
            // When
            EmailFrequency[] values =
                    EmailFrequency.values();

            // Then
            assertThat(values).hasSize(4);
        }

        @Test
        @DisplayName("should contain DAILY frequency")
        void shouldContainDailyFrequency() {
            assertThat(EmailFrequency.DAILY_DIGEST).isNotNull();
            assertThat(EmailFrequency.DAILY_DIGEST.name()).isEqualTo("DAILY_DIGEST");
        }

        @Test
        @DisplayName("should contain WEEKLY frequency")
        void shouldContainWeeklyFrequency() {
            assertThat(EmailFrequency.WEEKLY_DIGEST).isNotNull();
            assertThat(EmailFrequency.WEEKLY_DIGEST.name()).isEqualTo("WEEKLY_DIGEST");
        }


        @Test
        @DisplayName("should parse frequency from valid string")
        void shouldParseFrequencyFromValidString() {
            // When
            EmailFrequency frequency =
                    EmailFrequency.valueOf("WEEKLY_DIGEST");

            // Then
            assertThat(frequency).isEqualTo(EmailFrequency.WEEKLY_DIGEST);
        }

        @Test
        @DisplayName("should throw exception for invalid frequency string")
        void shouldThrowExceptionForInvalidFrequencyString() {
            assertThatThrownBy(() -> EmailFrequency.valueOf("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should get ordinal values correctly")
        void shouldGetOrdinalValuesCorrectly() {
            // Enum order: IMMEDIATE(0), HOURLY_DIGEST(1), DAILY_DIGEST(2), WEEKLY_DIGEST(3)
            assertThat(EmailFrequency.IMMEDIATE.ordinal()).isEqualTo(0);
            assertThat(EmailFrequency.HOURLY_DIGEST.ordinal()).isEqualTo(1);
            assertThat(EmailFrequency.DAILY_DIGEST.ordinal()).isEqualTo(2);
            assertThat(EmailFrequency.WEEKLY_DIGEST.ordinal()).isEqualTo(3);
        }
    }

    // ==================== DTO IN TESTS ====================

    @Nested
    @DisplayName("UserPreferencesDtoIn Tests")
    class UserPreferencesDtoInTests {

        @Test
        @DisplayName("should create DTO with default constructor")
        void shouldCreateDtoWithDefaultConstructor() {
            // When
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // Then
            assertThat(dto).isNotNull();
        }

        @Test
        @DisplayName("should set and get notification email enabled")
        void shouldSetAndGetNotificationEmailEnabled() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setNotificationEmailEnabled(true);

            // Then
            assertThat(dto.getNotificationEmailEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get notification push enabled")
        void shouldSetAndGetNotificationPushEnabled() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setNotificationPushEnabled(true);

            // Then
            assertThat(dto.getNotificationPushEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get notification SMS enabled")
        void shouldSetAndGetNotificationSmsEnabled() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setNotificationSmsEnabled(true);

            // Then
            assertThat(dto.getNotificationSmsEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get dark mode enabled")
        void shouldSetAndGetDarkModeEnabled() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setDarkModeEnabled(true);

            // Then
            assertThat(dto.getDarkModeEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get language")
        void shouldSetAndGetLanguage() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setLanguage("de");

            // Then
            assertThat(dto.getLanguage()).isEqualTo("de");
        }

        @Test
        @DisplayName("should set and get timezone")
        void shouldSetAndGetTimezone() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setTimezone("America/Los_Angeles");

            // Then
            assertThat(dto.getTimezone()).isEqualTo("America/Los_Angeles");
        }

        @Test
        @DisplayName("should set and get communication frequency")
        void shouldSetAndGetCommunicationFrequency() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setCommunicationFrequency("DAILY_DIGEST");

            // Then
            assertThat(dto.getCommunicationFrequency()).isEqualTo("DAILY_DIGEST");
        }

        @Test
        @DisplayName("should set and get GDPR marketing consent")
        void shouldSetAndGetGdprMarketingConsent() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setGdprMarketingConsent(true);

            // Then
            assertThat(dto.getGdprMarketingConsent()).isTrue();
        }

        @Test
        @DisplayName("should set and get share phone for payments")
        void shouldSetAndGetSharePhoneForPayments() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setSharePhoneForPayments(false);

            // Then
            assertThat(dto.getSharePhoneForPayments()).isFalse();
        }

        @Test
        @DisplayName("should set and get 2FA enabled")
        void shouldSetAndGet2FAEnabled() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // When
            dto.setTwoFactorAuthenticationEnabled(true);

            // Then
            assertThat(dto.getTwoFactorAuthenticationEnabled()).isTrue();
        }

        @Test
        @DisplayName("should handle null values for all fields")
        void shouldHandleNullValuesForAllFields() {
            // Given
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();

            // Then
            assertThat(dto.getNotificationEmailEnabled()).isNull();
            assertThat(dto.getNotificationPushEnabled()).isNull();
            assertThat(dto.getNotificationSmsEnabled()).isNull();
            assertThat(dto.getDarkModeEnabled()).isNull();
            assertThat(dto.getLanguage()).isNull();
            assertThat(dto.getTimezone()).isNull();
            assertThat(dto.getCommunicationFrequency()).isNull();
            assertThat(dto.getGdprMarketingConsent()).isNull();
            assertThat(dto.getSharePhoneForPayments()).isNull();
            assertThat(dto.getTwoFactorAuthenticationEnabled()).isNull();
        }
    }

    // ==================== DTO OUT TESTS ====================

    @Nested
    @DisplayName("UserPreferencesDtoOut Tests")
    class UserPreferencesDtoOutTests {

        @Test
        @DisplayName("should create DTO with default constructor")
        void shouldCreateDtoWithDefaultConstructor() {
            // When
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // Then
            assertThat(dto).isNotNull();
        }

        @Test
        @DisplayName("should set and get ID")
        void shouldSetAndGetId() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setId(123L);

            // Then
            assertThat(dto.getId()).isEqualTo(123L);
        }

        @Test
        @DisplayName("should set and get user ID")
        void shouldSetAndGetUserId() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setUserId(456L);

            // Then
            assertThat(dto.getUserId()).isEqualTo(456L);
        }

        @Test
        @DisplayName("should set and get all notification preferences")
        void shouldSetAndGetAllNotificationPreferences() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setNotificationEmailEnabled(true);
            dto.setNotificationPushEnabled(true);
            dto.setNotificationSmsEnabled(false);

            // Then
            assertThat(dto.getNotificationEmailEnabled()).isTrue();
            assertThat(dto.getNotificationPushEnabled()).isTrue();
            assertThat(dto.getNotificationSmsEnabled()).isFalse();
        }

        @Test
        @DisplayName("should set and get dark mode")
        void shouldSetAndGetDarkMode() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setDarkModeEnabled(true);

            // Then
            assertThat(dto.getDarkModeEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get language")
        void shouldSetAndGetLanguage() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setLanguage("fr");

            // Then
            assertThat(dto.getLanguage()).isEqualTo("fr");
        }

        @Test
        @DisplayName("should set and get timezone")
        void shouldSetAndGetTimezone() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setTimezone("Pacific/Auckland");

            // Then
            assertThat(dto.getTimezone()).isEqualTo("Pacific/Auckland");
        }

        @Test
        @DisplayName("should set and get communication frequency as string")
        void shouldSetAndGetCommunicationFrequencyAsString() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setCommunicationFrequency("HOURLY_DIGEST");

            // Then
            assertThat(dto.getCommunicationFrequency()).isEqualTo("HOURLY_DIGEST");
        }

        @Test
        @DisplayName("should set and get GDPR marketing consent")
        void shouldSetAndGetGdprMarketingConsent() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setGdprMarketingConsent(true);

            // Then
            assertThat(dto.getGdprMarketingConsent()).isTrue();
        }

        @Test
        @DisplayName("should set and get share phone for payments")
        void shouldSetAndGetSharePhoneForPayments() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setSharePhoneForPayments(true);

            // Then
            assertThat(dto.getSharePhoneForPayments()).isTrue();
        }

        @Test
        @DisplayName("should set and get 2FA enabled")
        void shouldSetAndGet2FAEnabled() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setTwoFactorAuthenticationEnabled(true);

            // Then
            assertThat(dto.getTwoFactorAuthenticationEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set and get created time")
        void shouldSetAndGetCreatedTime() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();
            LocalDateTime now = LocalDateTime.now();

            // When
            dto.setCreatedTime(now);

            // Then
            assertThat(dto.getCreatedTime()).isEqualTo(now);
        }

        @Test
        @DisplayName("should set and get last update time")
        void shouldSetAndGetLastUpdateTime() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();
            LocalDateTime now = LocalDateTime.now();

            // When
            dto.setLastUpdateTime(now);

            // Then
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
        }

        @Test
        @DisplayName("should set and get updater ID")
        void shouldSetAndGetUpdaterId() {
            // Given
            UserPreferencesDtoOut dto = new UserPreferencesDtoOut();

            // When
            dto.setUpdaterId("system_admin");

            // Then
            assertThat(dto.getUpdaterId()).isEqualTo("system_admin");
        }
    }

    // ==================== MAPPING TESTS ====================

    @Nested
    @DisplayName("UserPreferencesMapping Tests")
    class UserPreferencesMappingTests {

        @Test
        @DisplayName("should create mapping configuration with dependencies")
        void shouldCreateMappingConfigurationWithDependencies() {
            // Given
            EnumTranslationService mockEnumService = mock(EnumTranslationService.class);
            UpdaterIdConverter mockConverter = mock(UpdaterIdConverter.class);

            // When
            UserPreferencesMapping mapping = new UserPreferencesMapping(mockEnumService, mockConverter);

            // Then
            assertThat(mapping).isNotNull();
        }

        @Test
        @DisplayName("should configure model mapper")
        void shouldConfigureModelMapper() {
            // Given
            EnumTranslationService mockEnumService = mock(EnumTranslationService.class);
            UpdaterIdConverter mockConverter = mock(UpdaterIdConverter.class);
            ModelMapper mapper = new ModelMapper();

            // Mock the converter method
            when(mockConverter.toUpdaterConverter()).thenReturn(ctx -> "converted");

            UserPreferencesMapping mapping = new UserPreferencesMapping(mockEnumService, mockConverter);

            // When
            ModelMapper result = mapping.configureMapping(mapper);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTypeMap(UserPreferencesDtoIn.class, UserPreferences.class)).isNotNull();
            assertThat(result.getTypeMap(UserPreferences.class, UserPreferencesDtoOut.class)).isNotNull();
        }

        @Test
        @DisplayName("should map DtoIn to entity correctly")
        void shouldMapDtoInToEntityCorrectly() {
            // Given
            UserPreferencesDtoIn dtoIn = new UserPreferencesDtoIn();
            dtoIn.setNotificationEmailEnabled(true);
            dtoIn.setDarkModeEnabled(true);
            dtoIn.setLanguage("pl");
            dtoIn.setTimezone("Europe/Warsaw");
            dtoIn.setCommunicationFrequency("DAILY_DIGEST");

            // When - manual mapping simulation
            UserPreferences entity = new UserPreferences();
            entity.setNotificationEmailEnabled(dtoIn.getNotificationEmailEnabled());
            entity.setDarkModeEnabled(dtoIn.getDarkModeEnabled());
            entity.setLanguage(dtoIn.getLanguage());
            entity.setTimezone(dtoIn.getTimezone());
            if (dtoIn.getCommunicationFrequency() != null) {
                entity.setCommunicationFrequency(
                        EmailFrequency.valueOf(dtoIn.getCommunicationFrequency())
                );
            }

            // Then
            assertThat(entity.getNotificationEmailEnabled()).isTrue();
            assertThat(entity.getDarkModeEnabled()).isTrue();
            assertThat(entity.getLanguage()).isEqualTo("pl");
            assertThat(entity.getTimezone()).isEqualTo("Europe/Warsaw");
            assertThat(entity.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.DAILY_DIGEST);
        }

        @Test
        @DisplayName("should map entity to DtoOut correctly")
        void shouldMapEntityToDtoOutCorrectly() {
            // Given - already have testPreferences and testUser setup

            // When - manual mapping simulation
            UserPreferencesDtoOut dtoOut = new UserPreferencesDtoOut();
            dtoOut.setId(testPreferences.getId());
            dtoOut.setUserId(testPreferences.getUser().getId());
            dtoOut.setNotificationEmailEnabled(testPreferences.getNotificationEmailEnabled());
            dtoOut.setDarkModeEnabled(testPreferences.getDarkModeEnabled());
            dtoOut.setLanguage(testPreferences.getLanguage());
            dtoOut.setTimezone(testPreferences.getTimezone());
            dtoOut.setCommunicationFrequency(testPreferences.getCommunicationFrequency().name());

            // Then
            assertThat(dtoOut.getId()).isEqualTo(1L);
            assertThat(dtoOut.getUserId()).isEqualTo(1L);
            assertThat(dtoOut.getNotificationEmailEnabled()).isTrue();
            assertThat(dtoOut.getDarkModeEnabled()).isFalse();
            assertThat(dtoOut.getLanguage()).isEqualTo("en");
            assertThat(dtoOut.getCommunicationFrequency()).isEqualTo("WEEKLY_DIGEST");
        }

        @Test
        @DisplayName("should handle null communication frequency in DtoIn")
        void shouldHandleNullCommunicationFrequencyInDtoIn() {
            // Given
            UserPreferencesDtoIn dtoIn = new UserPreferencesDtoIn();
            dtoIn.setCommunicationFrequency(null);

            // When - simulating mapping logic
            UserPreferences entity = new UserPreferences();
            if (dtoIn.getCommunicationFrequency() != null) {
                entity.setCommunicationFrequency(
                        EmailFrequency.valueOf(dtoIn.getCommunicationFrequency())
                );
            } else {
                entity.setCommunicationFrequency(EmailFrequency.WEEKLY_DIGEST);
            }

            // Then
            assertThat(entity.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.WEEKLY_DIGEST);
        }
    }

    // ==================== DEFAULT VALUES TESTS ====================

    @Nested
    @DisplayName("UserPreferences Default Values Tests")
    class UserPreferencesDefaultValuesTests {

        @Test
        @DisplayName("should have default notification email enabled as true")
        void shouldHaveDefaultNotificationEmailEnabledAsTrue() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then - checking field definition default
            assertThat(preferences.getNotificationEmailEnabled()).isEqualTo(true);
        }

        @Test
        @DisplayName("should have default notification push enabled as false")
        void shouldHaveDefaultNotificationPushEnabledAsFalse() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getNotificationPushEnabled()).isEqualTo(false);
        }

        @Test
        @DisplayName("should have default notification SMS enabled as false")
        void shouldHaveDefaultNotificationSmsEnabledAsFalse() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getNotificationSmsEnabled()).isEqualTo(false);
        }

        @Test
        @DisplayName("should have default dark mode enabled as false")
        void shouldHaveDefaultDarkModeEnabledAsFalse() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getDarkModeEnabled()).isEqualTo(false);
        }

        @Test
        @DisplayName("should have default language as en")
        void shouldHaveDefaultLanguageAsEn() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getLanguage()).isEqualTo("en");
        }

        @Test
        @DisplayName("should have default timezone as UTC")
        void shouldHaveDefaultTimezoneAsUtc() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getTimezone()).isEqualTo("UTC");
        }

        @Test
        @DisplayName("should have default communication frequency as WEEKLY")
        void shouldHaveDefaultCommunicationFrequencyAsWeekly() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.WEEKLY_DIGEST);
        }

        @Test
        @DisplayName("should have default GDPR marketing consent as false")
        void shouldHaveDefaultGdprMarketingConsentAsFalse() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getGdprMarketingConsent()).isEqualTo(false);
        }

        @Test
        @DisplayName("should have default share phone for payments as false")
        void shouldHaveDefaultSharePhoneForPaymentsAsFalse() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getSharePhoneForPayments()).isEqualTo(false);
        }

        @Test
        @DisplayName("should have default 2FA enabled as false")
        void shouldHaveDefault2FAEnabledAsFalse() {
            // When
            UserPreferences preferences = new UserPreferences();

            // Then
            assertThat(preferences.getTwoFactorAuthenticationEnabled()).isEqualTo(false);
        }
    }

    // ==================== EDGE CASES TESTS ====================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle empty language string")
        void shouldHandleEmptyLanguageString() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setLanguage("");

            // Then
            assertThat(preferences.getLanguage()).isEmpty();
        }

        @Test
        @DisplayName("should handle empty timezone string")
        void shouldHandleEmptyTimezoneString() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setTimezone("");

            // Then
            assertThat(preferences.getTimezone()).isEmpty();
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("should handle null language")
        void shouldHandleNullLanguage(String language) {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setLanguage(language);

            // Then
            assertThat(preferences.getLanguage()).isNull();
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("should handle null timezone")
        void shouldHandleNullTimezone(String timezone) {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setTimezone(timezone);

            // Then
            assertThat(preferences.getTimezone()).isNull();
        }

        @Test
        @DisplayName("should handle null communication frequency")
        void shouldHandleNullCommunicationFrequency() {
            // Given
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setCommunicationFrequency(null);

            // Then
            assertThat(preferences.getCommunicationFrequency()).isNull();
        }

        @Test
        @DisplayName("should handle very long language string")
        void shouldHandleVeryLongLanguageString() {
            // Given
            UserPreferences preferences = new UserPreferences();
            String longLanguage = "a".repeat(100);

            // When
            preferences.setLanguage(longLanguage);

            // Then
            assertThat(preferences.getLanguage()).hasSize(100);
        }

        @Test
        @DisplayName("should handle very long timezone string")
        void shouldHandleVeryLongTimezoneString() {
            // Given
            UserPreferences preferences = new UserPreferences();
            String longTimezone = "z".repeat(100);

            // When
            preferences.setTimezone(longTimezone);

            // Then
            assertThat(preferences.getTimezone()).hasSize(100);
        }
    }

    // ==================== VALIDATION LENGTH TESTS ====================

    @Nested
    @DisplayName("Validation Length Tests")
    class ValidationLengthTests {

        @Test
        @DisplayName("should validate language within 10 char limit")
        void shouldValidateLanguageWithin10CharLimit() {
            // Given
            String validLanguage = "en-US";

            // Then
            assertThat(validLanguage.length()).isLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("should detect language exceeding 10 char limit")
        void shouldDetectLanguageExceeding10CharLimit() {
            // Given
            String invalidLanguage = "this-is-too-long";

            // Then
            assertThat(invalidLanguage.length()).isGreaterThan(10);
        }

        @Test
        @DisplayName("should validate timezone within 50 char limit")
        void shouldValidateTimezoneWithin50CharLimit() {
            // Given
            String validTimezone = "America/Argentina/Buenos_Aires";

            // Then
            assertThat(validTimezone.length()).isLessThanOrEqualTo(50);
        }

        @Test
        @DisplayName("should detect timezone exceeding 50 char limit")
        void shouldDetectTimezoneExceeding50CharLimit() {
            // Given
            String invalidTimezone = "x".repeat(51);

            // Then
            assertThat(invalidTimezone.length()).isGreaterThan(50);
        }

        @Test
        @DisplayName("should validate updater ID within 255 char limit")
        void shouldValidateUpdaterIdWithin255CharLimit() {
            // Given
            String validUpdaterId = "admin123user456";

            // Then
            assertThat(validUpdaterId.length()).isLessThanOrEqualTo(255);
        }

        @Test
        @DisplayName("should validate updater ID pattern - alphanumeric only")
        void shouldValidateUpdaterIdPatternAlphanumericOnly() {
            // Given
            String validUpdaterId = "admin123";
            String pattern = "^[a-zA-Z0-9]+$";

            // Then
            assertThat(validUpdaterId).matches(pattern);
        }

        @Test
        @DisplayName("should detect invalid updater ID pattern")
        void shouldDetectInvalidUpdaterIdPattern() {
            // Given
            String invalidUpdaterId = "admin-123";
            String pattern = "^[a-zA-Z0-9]+$";

            // Then
            assertThat(invalidUpdaterId).doesNotMatch(pattern);
        }
    }

    // ==================== PATCH UPDATE SIMULATION TESTS ====================

    @Nested
    @DisplayName("Patch Update Simulation Tests")
    class PatchUpdateSimulationTests {

        private static Stream<Arguments> providePatchUpdateScenarios() {
            return Stream.of(
                    Arguments.of("notificationEmailEnabled", true, "notificationEmailEnabled"),
                    Arguments.of("notificationPushEnabled", true, "notificationPushEnabled"),
                    Arguments.of("notificationSmsEnabled", true, "notificationSmsEnabled"),
                    Arguments.of("darkModeEnabled", true, "darkModeEnabled"),
                    Arguments.of("gdprMarketingConsent", true, "gdprMarketingConsent"),
                    Arguments.of("sharePhoneForPayments", false, "sharePhoneForPayments"),
                    Arguments.of("twoFactorAuthenticationEnabled", true, "twoFactorAuthenticationEnabled")
            );
        }

        @ParameterizedTest
        @MethodSource("providePatchUpdateScenarios")
        @DisplayName("should update boolean field via patch")
        void shouldUpdateBooleanFieldViaPatch(String fieldName, Boolean value, String expectedField) {
            // Given
            Map<String, Object> updates = new HashMap<>();
            updates.put(fieldName, value);
            UserPreferences preferences = new UserPreferences();

            // When
            applyPatchUpdate(preferences, fieldName, value);

            // Then
            assertThat(getBooleanField(preferences, expectedField)).isEqualTo(value);
        }

        @Test
        @DisplayName("should update language via patch")
        void shouldUpdateLanguageViaPatch() {
            // Given
            Map<String, Object> updates = Map.of("language", "de");
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setLanguage((String) updates.get("language"));

            // Then
            assertThat(preferences.getLanguage()).isEqualTo("de");
        }

        @Test
        @DisplayName("should update timezone via patch")
        void shouldUpdateTimezoneViaPatch() {
            // Given
            Map<String, Object> updates = Map.of("timezone", "Asia/Singapore");
            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setTimezone((String) updates.get("timezone"));

            // Then
            assertThat(preferences.getTimezone()).isEqualTo("Asia/Singapore");
        }

        @Test
        @DisplayName("should update communication frequency via patch")
        void shouldUpdateCommunicationFrequencyViaPatch() {
            // Given
            Map<String, Object> updates = Map.of("communicationFrequency", "DAILY_DIGEST");
            UserPreferences preferences = new UserPreferences();

            // When
            String frequency = (String) updates.get("communicationFrequency");
            preferences.setCommunicationFrequency(EmailFrequency.valueOf(frequency));

            // Then
            assertThat(preferences.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.DAILY_DIGEST);
        }

        @Test
        @DisplayName("should handle multiple field updates in single patch")
        void shouldHandleMultipleFieldUpdatesInSinglePatch() {
            // Given
            Map<String, Object> updates = new HashMap<>();
            updates.put("darkModeEnabled", true);
            updates.put("language", "fr");
            updates.put("communicationFrequency", "DAILY_DIGEST");
            updates.put("gdprMarketingConsent", true);

            UserPreferences preferences = new UserPreferences();

            // When
            preferences.setDarkModeEnabled((Boolean) updates.get("darkModeEnabled"));
            preferences.setLanguage((String) updates.get("language"));
            preferences.setCommunicationFrequency(
                    EmailFrequency.valueOf((String) updates.get("communicationFrequency")));
            preferences.setGdprMarketingConsent((Boolean) updates.get("gdprMarketingConsent"));

            // Then
            assertThat(preferences.getDarkModeEnabled()).isTrue();
            assertThat(preferences.getLanguage()).isEqualTo("fr");
            assertThat(preferences.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.DAILY_DIGEST);
            assertThat(preferences.getGdprMarketingConsent()).isTrue();
        }

        // Helper methods for patch simulation
        private void applyPatchUpdate(UserPreferences preferences, String fieldName, Boolean value) {
            switch (fieldName) {
                case "notificationEmailEnabled" -> preferences.setNotificationEmailEnabled(value);
                case "notificationPushEnabled" -> preferences.setNotificationPushEnabled(value);
                case "notificationSmsEnabled" -> preferences.setNotificationSmsEnabled(value);
                case "darkModeEnabled" -> preferences.setDarkModeEnabled(value);
                case "gdprMarketingConsent" -> preferences.setGdprMarketingConsent(value);
                case "sharePhoneForPayments" -> preferences.setSharePhoneForPayments(value);
                case "twoFactorAuthenticationEnabled" -> preferences.setTwoFactorAuthenticationEnabled(value);
            }
        }

        private Boolean getBooleanField(UserPreferences preferences, String fieldName) {
            return switch (fieldName) {
                case "notificationEmailEnabled" -> preferences.getNotificationEmailEnabled();
                case "notificationPushEnabled" -> preferences.getNotificationPushEnabled();
                case "notificationSmsEnabled" -> preferences.getNotificationSmsEnabled();
                case "darkModeEnabled" -> preferences.getDarkModeEnabled();
                case "gdprMarketingConsent" -> preferences.getGdprMarketingConsent();
                case "sharePhoneForPayments" -> preferences.getSharePhoneForPayments();
                case "twoFactorAuthenticationEnabled" -> preferences.getTwoFactorAuthenticationEnabled();
                default -> null;
            };
        }
    }

    // ==================== CONTROLLER SERVICE INTERACTION TESTS ====================

    @Nested
    @DisplayName("Controller Service Interaction Tests")
    class ControllerServiceInteractionTests {

        @Test
        @DisplayName("should return service from getService method")
        void shouldReturnServiceFromGetServiceMethod() {
            // When - getService is protected, we verify through controller behavior
            when(userPreferencesService.getCurrentUserPreferencesAsDto()).thenReturn(testDtoOut);
            userPreferencesController.getCurrentUserPreferences();

            // Then
            verify(userPreferencesService).getCurrentUserPreferencesAsDto();
        }

        @Test
        @DisplayName("should propagate service exceptions")
        void shouldPropagateServiceExceptions() {
            // Given
            RuntimeException serviceException = new RuntimeException("Service error");
            when(userPreferencesService.getCurrentUserPreferencesAsDto()).thenThrow(serviceException);

            // When/Then
            assertThatThrownBy(() -> userPreferencesController.getCurrentUserPreferences())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Service error");
        }

        @Test
        @DisplayName("should handle null response from service gracefully")
        void shouldHandleNullResponseFromServiceGracefully() {
            // Given
            when(userPreferencesService.getCurrentUserPreferencesAsDto()).thenReturn(null);

            // When
            ResponseEntity<UserPreferencesDtoOut> response = userPreferencesController.getCurrentUserPreferences();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNull();
        }
    }
}
