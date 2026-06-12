package com.sm.instagram.platform.integration.service.notification;

import com.sm.instagram.platform.notification.*;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for NotificationService.shouldNotify() and shouldSendEmail().
 *
 * <p>Tests the full preference-checking decision tree with a real database,
 * verifying all notification categories (PARTNERSHIP, SUPPORT, SYSTEM, ACCOUNT)
 * behave correctly with various preference configurations.
 *
 * <p>These tests would have caught the "all preferences default to false" bug
 * because they verify the actual behavior against different preference states.
 */
@DisplayName("NotificationService - shouldNotify() & shouldSendEmail()")
class NotificationService_ShouldNotify_IntegrationTest extends NotificationServiceIntegrationTestBase {

    // ========================================================================
    // PARTNERSHIP CATEGORY - shouldNotify()
    // ========================================================================

    @Nested
    @DisplayName("PARTNERSHIP notifications - shouldNotify()")
    class PartnershipShouldNotify {

        @Test
        @DisplayName("Should allow when partnership notifications enabled")
        void shouldAllowWhenPartnershipEnabled() {
            authenticateAs(testCompany);
            createPreferencesAllEnabled(testCompany);

            boolean result = notificationService.shouldNotify(
                    testCompany.getId(), NotificationType.APPLICATION_RECEIVED);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should suppress when partnership notifications disabled")
        void shouldSuppressWhenPartnershipDisabled() {
            authenticateAs(testCompany);
            createPreferencesAllDisabled(testCompany);

            boolean result = notificationService.shouldNotify(
                    testCompany.getId(), NotificationType.APPLICATION_RECEIVED);

            assertThat(result).isFalse();
        }

        @ParameterizedTest(name = "Type: {0}")
        @EnumSource(value = NotificationType.class, names = {
                "APPLICATION_RECEIVED", "APPLICATION_ACCEPTED", "APPLICATION_REJECTED",
                "OFFER_ACCEPTED", "OFFER_REJECTED",
                "CONTENT_SUBMITTED", "CONTENT_APPROVED", "CONTENT_REJECTED",
                "CONTENT_POSTED", "POST_VERIFIED", "POST_REJECTED",
                "COLLABORATION_COMPLETE"
        })
        @DisplayName("All PARTNERSHIP types should be allowed when enabled")
        void allPartnershipTypesShouldBeAllowedWhenEnabled(NotificationType type) {
            authenticateAs(testCompany);
            createPreferencesAllEnabled(testCompany);

            boolean result = notificationService.shouldNotify(testCompany.getId(), type);

            assertThat(result)
                    .as("shouldNotify for %s with partnership enabled", type)
                    .isTrue();
        }

        @ParameterizedTest(name = "Type: {0}")
        @EnumSource(value = NotificationType.class, names = {
                "APPLICATION_RECEIVED", "APPLICATION_ACCEPTED", "APPLICATION_REJECTED",
                "OFFER_ACCEPTED", "OFFER_REJECTED",
                "CONTENT_SUBMITTED", "CONTENT_APPROVED", "CONTENT_REJECTED",
                "CONTENT_POSTED", "POST_VERIFIED", "POST_REJECTED",
                "COLLABORATION_COMPLETE"
        })
        @DisplayName("All PARTNERSHIP types should be suppressed when disabled")
        void allPartnershipTypesShouldBeSuppressedWhenDisabled(NotificationType type) {
            authenticateAs(testCompany);
            createPreferencesAllDisabled(testCompany);

            boolean result = notificationService.shouldNotify(testCompany.getId(), type);

            assertThat(result)
                    .as("shouldNotify for %s with partnership disabled", type)
                    .isFalse();
        }
    }

    // ========================================================================
    // SUPPORT CATEGORY - shouldNotify()
    // ========================================================================

    @Nested
    @DisplayName("SUPPORT notifications - shouldNotify()")
    class SupportShouldNotify {

        @ParameterizedTest(name = "Type: {0}")
        @EnumSource(value = NotificationType.class, names = {
                "TICKET_RESPONSE", "TICKET_RESOLVED", "TICKET_CLOSED"
        })
        @DisplayName("Should allow SUPPORT types when support enabled")
        void shouldAllowWhenSupportEnabled(NotificationType type) {
            authenticateAs(testInfluencer);
            createPreferencesAllEnabled(testInfluencer);

            boolean result = notificationService.shouldNotify(testInfluencer.getId(), type);

            assertThat(result).isTrue();
        }

        @ParameterizedTest(name = "Type: {0}")
        @EnumSource(value = NotificationType.class, names = {
                "TICKET_RESPONSE", "TICKET_RESOLVED", "TICKET_CLOSED"
        })
        @DisplayName("Should suppress SUPPORT types when support disabled")
        void shouldSuppressWhenSupportDisabled(NotificationType type) {
            authenticateAs(testInfluencer);
            createPreferencesAllDisabled(testInfluencer);

            boolean result = notificationService.shouldNotify(testInfluencer.getId(), type);

            assertThat(result).isFalse();
        }
    }

    // ========================================================================
    // SYSTEM & ACCOUNT CATEGORY - shouldNotify()
    // ========================================================================

    @Nested
    @DisplayName("SYSTEM & ACCOUNT notifications - shouldNotify()")
    class SystemAccountShouldNotify {

        @ParameterizedTest(name = "Type: {0}")
        @EnumSource(value = NotificationType.class, names = {
                "ACCOUNT_ACTIVATED", "ACCOUNT_SUSPENDED", "ACCOUNT_BANNED"
        })
        @DisplayName("ACCOUNT notifications always bypass preferences")
        void accountNotificationsAlwaysBypassPreferences(NotificationType type) {
            authenticateAs(testInfluencer);
            createPreferencesAllDisabled(testInfluencer);

            boolean result = notificationService.shouldNotify(testInfluencer.getId(), type);

            assertThat(result)
                    .as("ACCOUNT type %s should always pass regardless of preferences", type)
                    .isTrue();
        }

        @Test
        @DisplayName("ACCOUNT notifications bypass even when no preferences exist")
        void accountNotificationsBypassEvenWithNoPreferences() {
            authenticateAs(testInfluencer);
            // No preferences created at all

            boolean result = notificationService.shouldNotify(
                    testInfluencer.getId(), NotificationType.ACCOUNT_SUSPENDED);

            assertThat(result).isTrue();
        }
    }

    // ========================================================================
    // NULL / MISSING PREFERENCES - shouldNotify()
    // ========================================================================

    @Nested
    @DisplayName("Edge cases - shouldNotify()")
    class EdgeCases {

        @Test
        @DisplayName("Should suppress PARTNERSHIP when no preferences exist (opt-in)")
        void shouldSuppressPartnershipWhenNoPreferencesExist() {
            authenticateAs(testCompany);
            // No preferences created

            boolean result = notificationService.shouldNotify(
                    testCompany.getId(), NotificationType.APPLICATION_RECEIVED);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should suppress SUPPORT when no preferences exist (opt-in)")
        void shouldSuppressSupportWhenNoPreferencesExist() {
            authenticateAs(testInfluencer);
            // No preferences created

            boolean result = notificationService.shouldNotify(
                    testInfluencer.getId(), NotificationType.TICKET_RESPONSE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should suppress when user ID does not exist")
        void shouldSuppressWhenUserIdDoesNotExist() {
            authenticateAs(testAdmin);

            boolean result = notificationService.shouldNotify(999999L, NotificationType.APPLICATION_RECEIVED);

            assertThat(result).isFalse();
        }
    }

    // ========================================================================
    // shouldSendEmail() - EMAIL DECISION TREE
    // ========================================================================

    @Nested
    @DisplayName("shouldSendEmail() - email decision tree")
    class ShouldSendEmail {

        @Test
        @DisplayName("Should allow email when both global and category email enabled")
        void shouldAllowEmailWhenBothGlobalAndCategoryEnabled() {
            authenticateAs(testCompany);
            createPreferencesAllEnabled(testCompany);

            boolean result = notificationService.shouldSendEmail(
                    testCompany.getId(), NotificationType.APPLICATION_RECEIVED);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should suppress email when global email disabled")
        void shouldSuppressEmailWhenGlobalDisabled() {
            authenticateAs(testCompany);
            UserPreferences prefs = createPreferencesAllEnabled(testCompany);
            prefs.setNotificationEmailEnabled(false);
            userPreferencesRepository.save(prefs);

            boolean result = notificationService.shouldSendEmail(
                    testCompany.getId(), NotificationType.APPLICATION_RECEIVED);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should suppress email when category email disabled (global on)")
        void shouldSuppressEmailWhenCategoryDisabledButGlobalOn() {
            authenticateAs(testCompany);
            createPreferencesGlobalEmailOnlyCategoryOff(testCompany);

            boolean result = notificationService.shouldSendEmail(
                    testCompany.getId(), NotificationType.APPLICATION_RECEIVED);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("ALWAYS email type bypasses all preferences")
        void alwaysEmailTypeBypassesAllPreferences() {
            authenticateAs(testInfluencer);
            createPreferencesAllDisabled(testInfluencer);

            // ACCOUNT_SUSPENDED has EmailDefault.ALWAYS
            boolean result = notificationService.shouldSendEmail(
                    testInfluencer.getId(), NotificationType.ACCOUNT_SUSPENDED);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("ALWAYS email type bypasses even when no preferences exist")
        void alwaysEmailTypeBypassesNoPreferences() {
            authenticateAs(testInfluencer);
            // No preferences

            boolean result = notificationService.shouldSendEmail(
                    testInfluencer.getId(), NotificationType.ACCOUNT_SUSPENDED);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("DISABLED email type returns false even when all enabled")
        void disabledEmailTypeReturnsFalseEvenWhenAllEnabled() {
            authenticateAs(testCompany);
            createPreferencesAllEnabled(testCompany);

            // COLLABORATION_COMPLETE has EmailDefault.DISABLED
            boolean result = notificationService.shouldSendEmail(
                    testCompany.getId(), NotificationType.COLLABORATION_COMPLETE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Support email requires both global and support category enabled")
        void supportEmailRequiresBothGlobalAndCategory() {
            authenticateAs(testInfluencer);
            createPreferencesAllEnabled(testInfluencer);

            boolean result = notificationService.shouldSendEmail(
                    testInfluencer.getId(), NotificationType.TICKET_RESPONSE);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Support email suppressed when support category email disabled")
        void supportEmailSuppressedWhenCategoryDisabled() {
            authenticateAs(testInfluencer);
            UserPreferences prefs = createPreferencesAllEnabled(testInfluencer);
            prefs.setNotificationEmailSupportEnabled(false);
            userPreferencesRepository.save(prefs);

            boolean result = notificationService.shouldSendEmail(
                    testInfluencer.getId(), NotificationType.TICKET_RESPONSE);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("No preferences suppresses non-ALWAYS email types")
        void noPreferencesSuppressesNonAlwaysEmail() {
            authenticateAs(testCompany);
            // No preferences

            boolean result = notificationService.shouldSendEmail(
                    testCompany.getId(), NotificationType.APPLICATION_RECEIVED);

            assertThat(result).isFalse();
        }
    }
}
