package com.sm.instagram.platform.integration.service.userpreferences;

import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.notification.EmailFrequency;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesDtoIn;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import com.sm.instagram.platform.userpreferences.UserPreferencesService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for UserPreferencesService integration tests.
 * Provides common fixtures and helper methods for testing user preferences functionality.
 */
public abstract class UserPreferencesServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected UserPreferencesService userPreferencesService;

    @Autowired
    protected UserPreferencesRepository userPreferencesRepository;

    /**
     * Creates and saves user preferences for a given user.
     *
     * @param user The user to create preferences for
     * @return The created UserPreferences entity
     */
    protected UserPreferences createPreferencesForUser(User user) {
        UserPreferences preferences = new UserPreferences();
        preferences.setUser(user);
        preferences.setNotificationEmailEnabled(true);
        preferences.setNotificationPushEnabled(false);
        preferences.setNotificationSmsEnabled(false);
        preferences.setDarkModeEnabled(false);
        preferences.setLanguage("en");
        preferences.setTimezone("Europe/Warsaw");
        preferences.setCommunicationFrequency(EmailFrequency.WEEKLY_DIGEST);
        preferences.setGdprMarketingConsent(false);
        preferences.setSharePhoneForPayments(true);
        preferences.setTwoFactorAuthenticationEnabled(false);
        return userPreferencesRepository.save(preferences);
    }

    /**
     * Creates a fully populated UserPreferencesDtoIn for testing full updates.
     *
     * @return A complete UserPreferencesDtoIn with all fields set
     */
    protected UserPreferencesDtoIn createFullDtoIn() {
        UserPreferencesDtoIn dto = new UserPreferencesDtoIn();
        dto.setNotificationEmailEnabled(true);
        dto.setNotificationPushEnabled(true);
        dto.setNotificationSmsEnabled(true);
        dto.setDarkModeEnabled(true);
        dto.setLanguage("pl");
        dto.setTimezone("America/New_York");
        dto.setCommunicationFrequency("DAILY_DIGEST");
        dto.setGdprMarketingConsent(true);
        dto.setSharePhoneForPayments(false);
        dto.setTwoFactorAuthenticationEnabled(true);
        return dto;
    }

    /**
     * Creates a patch map with specified fields for testing partial updates.
     *
     * @param fieldValues Pairs of field names and values (must be even number)
     * @return Map of field names to values
     */
    protected Map<String, Object> createPatchMap(Object... fieldValues) {
        if (fieldValues.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide field-value pairs");
        }
        Map<String, Object> patchMap = new HashMap<>();
        for (int i = 0; i < fieldValues.length; i += 2) {
            String fieldName = (String) fieldValues[i];
            Object value = fieldValues[i + 1];
            patchMap.put(fieldName, value);
        }
        return patchMap;
    }

    /**
     * Creates a patch map for a single field update.
     *
     * @param fieldName The field to update
     * @param value The new value
     * @return Map containing the single field update
     */
    protected Map<String, Object> singleFieldPatch(String fieldName, Object value) {
        Map<String, Object> patchMap = new HashMap<>();
        patchMap.put(fieldName, value);
        return patchMap;
    }

    /**
     * Verifies that preferences have default values.
     *
     * @param prefs The preferences to check
     * @param expectedLanguage The expected language (from locale)
     */
    protected void assertDefaultPreferences(UserPreferences prefs, String expectedLanguage) {
        org.assertj.core.api.Assertions.assertThat(prefs.getNotificationEmailEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(prefs.getNotificationPushEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(prefs.getNotificationSmsEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(prefs.getNotificationPartnershipEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(prefs.getNotificationSupportEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(prefs.getNotificationSystemEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(prefs.getNotificationEmailPartnershipEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(prefs.getNotificationEmailSupportEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(prefs.getDarkModeEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(prefs.getLanguage()).isEqualTo(expectedLanguage);
        org.assertj.core.api.Assertions.assertThat(prefs.getTimezone()).isEqualTo("UTC");
        org.assertj.core.api.Assertions.assertThat(prefs.getCommunicationFrequency())
                .isEqualTo(EmailFrequency.WEEKLY_DIGEST);
        org.assertj.core.api.Assertions.assertThat(prefs.getGdprMarketingConsent()).isFalse();
        org.assertj.core.api.Assertions.assertThat(prefs.getSharePhoneForPayments()).isFalse();
        org.assertj.core.api.Assertions.assertThat(prefs.getTwoFactorAuthenticationEnabled()).isFalse();
    }
}
