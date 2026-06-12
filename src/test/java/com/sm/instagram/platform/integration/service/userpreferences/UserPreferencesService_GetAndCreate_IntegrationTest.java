package com.sm.instagram.platform.integration.service.userpreferences;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserPreferencesService get and create operations.
 * Tests getCurrentUserPreferences() and getUserPreferences() methods.
 */
@DisplayName("UserPreferencesService - Get and Create Operations")
class UserPreferencesService_GetAndCreate_IntegrationTest extends UserPreferencesServiceIntegrationTestBase {

    @Nested
    @DisplayName("getCurrentUserPreferences()")
    class GetCurrentUserPreferences {

        @Test
        @DisplayName("Returns existing preferences for authenticated user")
        void returnsExistingPreferences() {
            // Given
            authenticateAs(testInfluencer);
            UserPreferences existingPrefs = createPreferencesForUser(testInfluencer);

            // When
            UserPreferences result = userPreferencesService.getCurrentUserPreferences();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(existingPrefs.getId());
            assertThat(result.getUser().getId()).isEqualTo(testInfluencer.getId());
            assertThat(result.getNotificationEmailEnabled()).isTrue();
            assertThat(result.getLanguage()).isEqualTo("en");
        }

        @Test
        @DisplayName("Creates default preferences when none exist")
        void createsDefaultPreferencesWhenNoneExist() {
            // Given
            authenticateAs(testInfluencer);
            setUpMockHttpContext(Locale.ENGLISH);

            // Verify no preferences exist
            assertThat(userPreferencesRepository.findByUser(testInfluencer)).isNull();

            // When
            UserPreferences result = userPreferencesService.getCurrentUserPreferences();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(testInfluencer.getId());
            assertDefaultPreferences(result, "en");
        }

        @Test
        @DisplayName("Creates preferences with Polish language when locale is Polish")
        void createsPreferencesWithPolishLanguageFromLocale() {
            // Given
            authenticateAs(testCompany);
            LocaleContextHolder.setLocale(Locale.forLanguageTag("pl"));

            try {
                // Verify no preferences exist
                assertThat(userPreferencesRepository.findByUser(testCompany)).isNull();

                // When
                UserPreferences result = userPreferencesService.getCurrentUserPreferences();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getLanguage()).isEqualTo("pl");
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }
        }

        @Test
        @DisplayName("Creates preferences with English language for unsupported locales")
        void createsPreferencesWithEnglishForUnsupportedLocales() {
            // Given
            authenticateAs(testInfluencer);
            LocaleContextHolder.setLocale(Locale.GERMAN);

            try {
                // Verify no preferences exist
                assertThat(userPreferencesRepository.findByUser(testInfluencer)).isNull();

                // When
                UserPreferences result = userPreferencesService.getCurrentUserPreferences();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getLanguage()).isEqualTo("en");
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }
        }

        @Test
        @DisplayName("Works for company users")
        void worksForCompanyUsers() {
            // Given
            authenticateAs(testCompany);
            setUpMockHttpContext(Locale.ENGLISH);

            // When
            UserPreferences result = userPreferencesService.getCurrentUserPreferences();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(testCompany.getId());
        }

        @Test
        @DisplayName("Works for admin users")
        void worksForAdminUsers() {
            // Given
            authenticateAs(testAdmin);
            setUpMockHttpContext(Locale.ENGLISH);

            // When
            UserPreferences result = userPreferencesService.getCurrentUserPreferences();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(testAdmin.getId());
        }
    }

    @Nested
    @DisplayName("getUserPreferences(Long userId)")
    class GetUserPreferences {

        @Test
        @DisplayName("Admin can get any user's preferences")
        void adminCanGetAnyUserPreferences() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testAdmin);

            // When
            UserPreferences result = userPreferencesService.getUserPreferences(testInfluencer.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(testInfluencer.getId());
        }

        @Test
        @DisplayName("Owner can get their own preferences")
        void ownerCanGetOwnPreferences() {
            // Given
            createPreferencesForUser(testInfluencer);
            authenticateAs(testInfluencer);

            // When
            UserPreferences result = userPreferencesService.getUserPreferences(testInfluencer.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUser().getId()).isEqualTo(testInfluencer.getId());
        }

        @Test
        @DisplayName("Non-owner, non-admin cannot access other user's preferences")
        void nonOwnerNonAdminCannotAccessOtherUserPreferences() {
            // Given
            createPreferencesForUser(testCompany);
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.getUserPreferences(testCompany.getId()))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when preferences don't exist")
        void throwsWhenPreferencesNotFound() {
            // Given
            authenticateAs(testAdmin);
            // Ensure no preferences exist for testInfluencer

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.getUserPreferences(testInfluencer.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when user doesn't exist")
        void throwsWhenUserNotFound() {
            // Given
            authenticateAs(testAdmin);
            Long nonExistentUserId = 999999L;

            // When/Then
            assertThatThrownBy(() -> userPreferencesService.getUserPreferences(nonExistentUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Admin can access company user's preferences")
        void adminCanAccessCompanyUserPreferences() {
            // Given
            UserPreferences companyPrefs = createPreferencesForUser(testCompany);
            companyPrefs.setDarkModeEnabled(true);
            userPreferencesRepository.save(companyPrefs);
            authenticateAs(testAdmin);

            // When
            UserPreferences result = userPreferencesService.getUserPreferences(testCompany.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDarkModeEnabled()).isTrue();
        }
    }
}
