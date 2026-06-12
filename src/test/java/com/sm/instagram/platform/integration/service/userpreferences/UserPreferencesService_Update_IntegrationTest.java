package com.sm.instagram.platform.integration.service.userpreferences;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.notification.EmailFrequency;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesDtoIn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserPreferencesService update operations.
 * Tests updateCurrentUserPreferences() and patchCurrentUserPreferences() methods.
 */
@DisplayName("UserPreferencesService - Update Operations")
class UserPreferencesService_Update_IntegrationTest extends UserPreferencesServiceIntegrationTestBase {

    @Nested
    @DisplayName("updateCurrentUserPreferences()")
    class UpdateCurrentUserPreferences {

        @Test
        @DisplayName("Full update of all preferences")
        void fullUpdateOfAllPreferences() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);
            UserPreferencesDtoIn dto = createFullDtoIn();

            // When
            UserPreferences result = userPreferencesService.updateCurrentUserPreferences(dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getNotificationEmailEnabled()).isTrue();
            assertThat(result.getNotificationPushEnabled()).isTrue();
            assertThat(result.getNotificationSmsEnabled()).isTrue();
            assertThat(result.getDarkModeEnabled()).isTrue();
            assertThat(result.getLanguage()).isEqualTo("pl");
            assertThat(result.getTimezone()).isEqualTo("America/New_York");
            assertThat(result.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.DAILY_DIGEST);
            assertThat(result.getGdprMarketingConsent()).isTrue();
            assertThat(result.getSharePhoneForPayments()).isFalse();
            assertThat(result.getTwoFactorAuthenticationEnabled()).isTrue();
        }

        @Test
        @DisplayName("Creates default preferences if none exist before updating")
        void createsDefaultsIfNoneExistBeforeUpdating() {
            // Given
            authenticateAs(testCompany);
            setUpMockHttpContext(Locale.ENGLISH);
            assertThat(userPreferencesRepository.findByUser(testCompany)).isNull();

            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();
            dto.setDarkModeEnabled(true);

            // When
            UserPreferences result = userPreferencesService.updateCurrentUserPreferences(dto);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getDarkModeEnabled()).isTrue();
        }

        @Test
        @DisplayName("Throws validation exception for language exceeding max length")
        void throwsForLanguageExceedingMaxLength() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();
            dto.setLanguage("this-is-way-too-long-for-language-field");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.updateCurrentUserPreferences(dto))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws validation exception for timezone exceeding max length")
        void throwsForTimezoneExceedingMaxLength() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);
            UserPreferencesDtoIn dto = new UserPreferencesDtoIn();
            dto.setTimezone("This/Is/A/Very/Long/Timezone/String/That/Exceeds/The/Maximum/Length");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.updateCurrentUserPreferences(dto))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Preserves user reference after update")
        void preservesUserReferenceAfterUpdate() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences original = createPreferencesForUser(testInfluencer);
            Long originalId = original.getId();

            UserPreferencesDtoIn dto = createFullDtoIn();

            // When
            UserPreferences result = userPreferencesService.updateCurrentUserPreferences(dto);

            // Then
            assertThat(result.getId()).isEqualTo(originalId);
            assertThat(result.getUser().getId()).isEqualTo(testInfluencer.getId());
        }

        @Test
        @DisplayName("Updates communication frequency correctly")
        void updatesCommunicationFrequencyCorrectly() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);

            // Test each frequency value
            for (EmailFrequency freq : EmailFrequency.values()) {
                UserPreferencesDtoIn dto = new UserPreferencesDtoIn();
                dto.setCommunicationFrequency(freq.name());

                // When
                UserPreferences result = userPreferencesService.updateCurrentUserPreferences(dto);

                // Then
                assertThat(result.getCommunicationFrequency()).isEqualTo(freq);
            }
        }
    }

    @Nested
    @DisplayName("patchCurrentUserPreferences()")
    class PatchCurrentUserPreferences {

        @Test
        @DisplayName("Patches single boolean field")
        void patchesSingleBooleanField() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences original = createPreferencesForUser(testInfluencer);
            assertThat(original.getDarkModeEnabled()).isFalse();

            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", true);

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result.getDarkModeEnabled()).isTrue();
            // Other fields unchanged
            assertThat(result.getLanguage()).isEqualTo("en");
        }

        @Test
        @DisplayName("Patches single string field")
        void patchesSingleStringField() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences original = createPreferencesForUser(testInfluencer);

            Map<String, Object> updates = singleFieldPatch("language", "pl");

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result.getLanguage()).isEqualTo("pl");
        }

        @Test
        @DisplayName("Patches multiple fields at once")
        void patchesMultipleFieldsAtOnce() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);

            Map<String, Object> updates = createPatchMap(
                    "darkModeEnabled", true,
                    "language", "pl",
                    "notificationPushEnabled", true,
                    "timezone", "America/Los_Angeles"
            );

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result.getDarkModeEnabled()).isTrue();
            assertThat(result.getLanguage()).isEqualTo("pl");
            assertThat(result.getNotificationPushEnabled()).isTrue();
            assertThat(result.getTimezone()).isEqualTo("America/Los_Angeles");
        }

        @Test
        @DisplayName("Throws exception for invalid field name")
        void throwsForInvalidFieldName() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);

            Map<String, Object> updates = singleFieldPatch("nonExistentField", "value");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchCurrentUserPreferences(updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws exception for type mismatch (string instead of boolean)")
        void throwsForTypeMismatchStringInsteadOfBoolean() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);

            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", "not-a-boolean");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchCurrentUserPreferences(updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws exception for type mismatch (integer instead of boolean)")
        void throwsForTypeMismatchIntegerInsteadOfBoolean() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);

            Map<String, Object> updates = singleFieldPatch("notificationEmailEnabled", 123);

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchCurrentUserPreferences(updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Patches GDPR marketing consent")
        void patchesGdprMarketingConsent() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences original = createPreferencesForUser(testInfluencer);
            assertThat(original.getGdprMarketingConsent()).isFalse();

            Map<String, Object> updates = singleFieldPatch("gdprMarketingConsent", true);

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result.getGdprMarketingConsent()).isTrue();
        }

        @Test
        @DisplayName("Creates defaults if none exist before patching")
        void createsDefaultsIfNoneExistBeforePatching() {
            // Given
            authenticateAs(testCompany);
            setUpMockHttpContext(Locale.ENGLISH);
            assertThat(userPreferencesRepository.findByUser(testCompany)).isNull();

            Map<String, Object> updates = singleFieldPatch("darkModeEnabled", true);

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getDarkModeEnabled()).isTrue();
            // Other fields have defaults
            assertThat(result.getNotificationEmailEnabled()).isTrue();
        }

        @Test
        @DisplayName("Patches communication frequency")
        void patchesCommunicationFrequency() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences original = createPreferencesForUser(testInfluencer);
            assertThat(original.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.WEEKLY_DIGEST);

            Map<String, Object> updates = singleFieldPatch("communicationFrequency", "IMMEDIATE");

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result.getCommunicationFrequency())
                    .isEqualTo(EmailFrequency.IMMEDIATE);
        }

        @Test
        @DisplayName("Throws exception for invalid communication frequency value")
        void throwsForInvalidCommunicationFrequency() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);

            Map<String, Object> updates = singleFieldPatch("communicationFrequency", "INVALID_FREQ");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchCurrentUserPreferences(updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws exception for language exceeding max length in patch")
        void throwsForLanguageExceedingMaxLengthInPatch() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);

            Map<String, Object> updates = singleFieldPatch("language", "this-language-is-way-too-long");

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchCurrentUserPreferences(updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Throws exception for timezone exceeding max length in patch")
        void throwsForTimezoneExceedingMaxLengthInPatch() {
            // Given
            authenticateAs(testInfluencer);
            createPreferencesForUser(testInfluencer);

            String longTimezone = "A".repeat(51);
            Map<String, Object> updates = singleFieldPatch("timezone", longTimezone);

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.patchCurrentUserPreferences(updates))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Patches all notification settings at once")
        void patchesAllNotificationSettingsAtOnce() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences original = createPreferencesForUser(testInfluencer);

            Map<String, Object> updates = createPatchMap(
                    "notificationEmailEnabled", false,
                    "notificationPushEnabled", true,
                    "notificationSmsEnabled", true
            );

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result.getNotificationEmailEnabled()).isFalse();
            assertThat(result.getNotificationPushEnabled()).isTrue();
            assertThat(result.getNotificationSmsEnabled()).isTrue();
        }

        @Test
        @DisplayName("Patches sharePhoneForPayments")
        void patchesSharePhoneForPayments() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences original = createPreferencesForUser(testInfluencer);
            assertThat(original.getSharePhoneForPayments()).isTrue();

            Map<String, Object> updates = singleFieldPatch("sharePhoneForPayments", false);

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result.getSharePhoneForPayments()).isFalse();
        }

        @Test
        @DisplayName("Patches twoFactorAuthenticationEnabled")
        void patchesTwoFactorAuthenticationEnabled() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences original = createPreferencesForUser(testInfluencer);
            assertThat(original.getTwoFactorAuthenticationEnabled()).isFalse();

            Map<String, Object> updates = singleFieldPatch("twoFactorAuthenticationEnabled", true);

            // When
            UserPreferences result = userPreferencesService.patchCurrentUserPreferences(updates);

            // Then
            assertThat(result.getTwoFactorAuthenticationEnabled()).isTrue();
        }
    }
}
