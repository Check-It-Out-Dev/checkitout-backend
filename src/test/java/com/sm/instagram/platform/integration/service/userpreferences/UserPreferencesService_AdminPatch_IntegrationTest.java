package com.sm.instagram.platform.integration.service.userpreferences;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.notification.EmailFrequency;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserPreferencesService admin patch operations.
 * Tests patchUserPreferences(Long userId, Map<String, Object> updates) method.
 */
@DisplayName("UserPreferencesService - Admin Patch Operations")
class UserPreferencesService_AdminPatch_IntegrationTest extends UserPreferencesServiceIntegrationTestBase {

    @Nested
    @DisplayName("patchUserPreferences() - Admin Access")
    class AdminPatchUserPreferences {

        @Test
        @DisplayName("Admin can patch any user's preferences")
        void adminCanPatchAnyUserPreferences() {
            // Given
            UserPreferences influencerPrefs = createPreferencesForUser(testInfluencer);
            assertThat(influencerPrefs.getDarkModeEnabled()).isFalse();

            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", true);

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDarkModeEnabled()).isTrue();
            assertThat(result.getUser().getId()).isEqualTo(testInfluencer.getId());
        }

        @Test
        @DisplayName("Admin can patch company user's preferences")
        void adminCanPatchCompanyUserPreferences() {
            // Given
            createPreferencesForUser(testCompany);
            authenticateAs(testAdmin);
            Map<String, Object> updates = createPatchMap(
                    "language", "pl",
                    "darkModeEnabled", true
            );

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testCompany.getId(), updates);

            // Then
            assertThat(result.getLanguage()).isEqualTo("pl");
            assertThat(result.getDarkModeEnabled()).isTrue();
        }

        @Test
        @DisplayName("Admin patch creates default preferences if none exist")
        void adminPatchCreatesDefaultsIfNoneExist() {
            // Given
            authenticateAs(testAdmin);
            LocaleContextHolder.setLocale(Locale.ENGLISH);

            try {
                assertThat(userPreferencesRepository.findByUser(testInfluencer)).isNull();

                Map<String, Object> updates = singleFieldPatch("notificationEmailEnabled", true);

                // When
                UserPreferences result = userPreferencesService.patchUserPreferences(
                        testInfluencer.getId(), updates);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isNotNull();
                assertThat(result.getNotificationEmailEnabled()).isTrue();
                // Other fields have defaults
                assertThat(result.getDarkModeEnabled()).isFalse();
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }
        }

        @Test
        @DisplayName("Admin can patch multiple fields at once")
        void adminCanPatchMultipleFieldsAtOnce() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);

            Map<String, Object> updates = createPatchMap(
                    "notificationEmailEnabled", true,
                    "notificationPushEnabled", true,
                    "darkModeEnabled", true,
                    "language", "pl",
                    "timezone", "Europe/London",
                    "communicationFrequency", "DAILY_DIGEST",
                    "gdprMarketingConsent", true
            );

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates);

            // Then
            assertThat(result.getNotificationEmailEnabled()).isTrue();
            assertThat(result.getNotificationPushEnabled()).isTrue();
            assertThat(result.getDarkModeEnabled()).isTrue();
            assertThat(result.getLanguage()).isEqualTo("pl");
            assertThat(result.getTimezone()).isEqualTo("Europe/London");
            assertThat(result.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.DAILY_DIGEST);
            assertThat(result.getGdprMarketingConsent()).isTrue();
        }

        @Test
        @DisplayName("Admin can override GDPR consent")
        void adminCanOverrideGdprConsent() {
            // Given
            UserPreferences prefs = createPreferencesForUser(testInfluencer);
            prefs.setGdprMarketingConsent(false);
            userPreferencesRepository.save(prefs);

            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("gdprMarketingConsent", true);

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates);

            // Then
            assertThat(result.getGdprMarketingConsent()).isTrue();
        }
    }

    @Nested
    @DisplayName("patchUserPreferences() - Permission Denied")
    class PatchUserPreferencesPermissionDenied {

        @Test
        @DisplayName("Non-admin cannot patch other user's preferences")
        void nonAdminCannotPatchOtherUserPreferences() {
            // Given
            createPreferencesForUser(testCompany);
            authenticateAs(testInfluencer);
            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", true);

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    testCompany.getId(), updates))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Company user cannot use admin patch method")
        void companyUserCannotUseAdminPatchMethod() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testCompany);
            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", true);

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Influencer cannot use admin patch method even for own preferences")
        void influencerCannotUseAdminPatchMethodForOwnPreferences() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testInfluencer);
            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", true);

            // When/Then - Should use patchCurrentUserPreferences instead
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("patchUserPreferences() - Validation")
    class PatchUserPreferencesValidation {

        @Test
        @DisplayName("Throws ResourceNotFoundException when user doesn't exist")
        void throwsWhenUserNotFound() {
            // Given
            authenticateAs(testAdmin);
            Long nonExistentUserId = 999999L;
            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", true);

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    nonExistentUserId, updates))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ValidationTranslatableException for invalid field name")
        void throwsForInvalidFieldName() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("invalidFieldName", "value");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws ValidationTranslatableException for type mismatch")
        void throwsForTypeMismatch() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", "not-a-boolean");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws ValidationTranslatableException for language exceeding max length")
        void throwsForLanguageExceedingMaxLength() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("language", "this-is-way-too-long");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws ValidationTranslatableException for timezone exceeding max length")
        void throwsForTimezoneExceedingMaxLength() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            String longTimezone = "T".repeat(51);
            Map<String, Object> updates = singleFieldPatch("timezone", longTimezone);

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws ValidationTranslatableException for invalid communication frequency")
        void throwsForInvalidCommunicationFrequency() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("communicationFrequency", "INVALID_VALUE");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("patchUserPreferences() - All Patchable Fields")
    class AdminPatchAllFields {

        @Test
        @DisplayName("Admin can patch notificationEmailEnabled")
        void adminCanPatchNotificationEmailEnabled() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("notificationEmailEnabled", false);

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates);

            // Then
            assertThat(result.getNotificationEmailEnabled()).isFalse();
        }

        @Test
        @DisplayName("Admin can patch notificationPushEnabled")
        void adminCanPatchNotificationPushEnabled() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("notificationPushEnabled", true);

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates);

            // Then
            assertThat(result.getNotificationPushEnabled()).isTrue();
        }

        @Test
        @DisplayName("Admin can patch notificationSmsEnabled")
        void adminCanPatchNotificationSmsEnabled() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("notificationSmsEnabled", true);

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates);

            // Then
            assertThat(result.getNotificationSmsEnabled()).isTrue();
        }

        @Test
        @DisplayName("Admin can patch sharePhoneForPayments")
        void adminCanPatchSharePhoneForPayments() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("sharePhoneForPayments", false);

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates);

            // Then
            assertThat(result.getSharePhoneForPayments()).isFalse();
        }

        @Test
        @DisplayName("Admin can patch twoFactorAuthenticationEnabled")
        void adminCanPatchTwoFactorAuthenticationEnabled() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);
            Map<String, Object> updates = singleFieldPatch("twoFactorAuthenticationEnabled", true);

            // When
            UserPreferences result = userPreferencesService.patchUserPreferences(
                    testInfluencer.getId(), updates);

            // Then
            assertThat(result.getTwoFactorAuthenticationEnabled()).isTrue();
        }

        @Test
        @DisplayName("Admin can patch all communication frequencies")
        void adminCanPatchAllCommunicationFrequencies() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);

            // Test each frequency
            for (EmailFrequency freq : EmailFrequency.values()) {
                Map<String, Object> updates = singleFieldPatch("communicationFrequency", freq.name());

                // When
                UserPreferences result = userPreferencesService.patchUserPreferences(
                        testInfluencer.getId(), updates);

                // Then
                assertThat(result.getCommunicationFrequency()).isEqualTo(freq);
            }
        }
    }
}
