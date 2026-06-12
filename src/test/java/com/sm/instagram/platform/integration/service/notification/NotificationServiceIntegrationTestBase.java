package com.sm.instagram.platform.integration.service.notification;

import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.notification.*;
import com.sm.instagram.platform.notification.dto.NotificationRequest;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base class for NotificationService integration tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>Common notification test fixtures and helpers</li>
 *   <li>Methods to create user preferences with specific notification settings</li>
 *   <li>Methods to build NotificationRequest for various notification types</li>
 * </ul>
 *
 * <p>These tests focus on verifying that:
 * <ul>
 *   <li>shouldNotify() correctly gates notifications by category and preference</li>
 *   <li>shouldSendEmail() correctly gates emails by global + category preferences</li>
 *   <li>createNotification() persists notifications when preferences allow</li>
 *   <li>All notification types and categories are handled correctly</li>
 * </ul>
 */
public abstract class NotificationServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected NotificationService notificationService;

    @Autowired
    protected NotificationRepository notificationRepository;

    @Autowired
    protected UserPreferencesRepository userPreferencesRepository;

    // ========================================================================
    // PREFERENCE HELPERS
    // ========================================================================

    /**
     * Creates user preferences with ALL notification channels enabled.
     * This is the "full opt-in" scenario.
     */
    protected UserPreferences createPreferencesAllEnabled(User user) {
        UserPreferences prefs = new UserPreferences();
        prefs.setUser(user);
        prefs.setNotificationPartnershipEnabled(true);
        prefs.setNotificationSupportEnabled(true);
        prefs.setNotificationSystemEnabled(true);
        prefs.setNotificationEmailEnabled(true);
        prefs.setNotificationEmailPartnershipEnabled(true);
        prefs.setNotificationEmailSupportEnabled(true);
        prefs.setNotificationPushEnabled(false);
        prefs.setNotificationSmsEnabled(false);
        prefs.setDarkModeEnabled(false);
        prefs.setLanguage("en");
        prefs.setTimezone("UTC");
        prefs.setCommunicationFrequency(com.sm.instagram.platform.notification.EmailFrequency.WEEKLY_DIGEST);
        prefs.setGdprMarketingConsent(false);
        prefs.setSharePhoneForPayments(true);
        prefs.setTwoFactorAuthenticationEnabled(false);
        return userPreferencesRepository.save(prefs);
    }

    /**
     * Creates user preferences with ALL notification channels disabled.
     * This is the "full opt-out" / default scenario.
     */
    protected UserPreferences createPreferencesAllDisabled(User user) {
        UserPreferences prefs = new UserPreferences();
        prefs.setUser(user);
        prefs.setNotificationPartnershipEnabled(false);
        prefs.setNotificationSupportEnabled(false);
        prefs.setNotificationSystemEnabled(false);
        prefs.setNotificationEmailEnabled(false);
        prefs.setNotificationEmailPartnershipEnabled(false);
        prefs.setNotificationEmailSupportEnabled(false);
        prefs.setNotificationPushEnabled(false);
        prefs.setNotificationSmsEnabled(false);
        prefs.setDarkModeEnabled(false);
        prefs.setLanguage("en");
        prefs.setTimezone("UTC");
        prefs.setCommunicationFrequency(EmailFrequency.WEEKLY_DIGEST);
        prefs.setGdprMarketingConsent(false);
        prefs.setSharePhoneForPayments(true);
        prefs.setTwoFactorAuthenticationEnabled(false);
        return userPreferencesRepository.save(prefs);
    }

    /**
     * Creates user preferences with in-app partnership notifications enabled
     * but email disabled.
     */
    protected UserPreferences createPreferencesPartnershipInAppOnly(User user) {
        UserPreferences prefs = new UserPreferences();
        prefs.setUser(user);
        prefs.setNotificationPartnershipEnabled(true);
        prefs.setNotificationSupportEnabled(false);
        prefs.setNotificationSystemEnabled(false);
        prefs.setNotificationEmailEnabled(false);
        prefs.setNotificationEmailPartnershipEnabled(false);
        prefs.setNotificationEmailSupportEnabled(false);
        prefs.setNotificationPushEnabled(false);
        prefs.setNotificationSmsEnabled(false);
        prefs.setDarkModeEnabled(false);
        prefs.setLanguage("en");
        prefs.setTimezone("UTC");
        prefs.setCommunicationFrequency(EmailFrequency.WEEKLY_DIGEST);
        prefs.setGdprMarketingConsent(false);
        prefs.setSharePhoneForPayments(true);
        prefs.setTwoFactorAuthenticationEnabled(false);
        return userPreferencesRepository.save(prefs);
    }

    /**
     * Creates preferences with global email ON but category-specific email OFF.
     * Tests the double-gating scenario.
     */
    protected UserPreferences createPreferencesGlobalEmailOnlyCategoryOff(User user) {
        UserPreferences prefs = new UserPreferences();
        prefs.setUser(user);
        prefs.setNotificationPartnershipEnabled(true);
        prefs.setNotificationSupportEnabled(true);
        prefs.setNotificationSystemEnabled(true);
        prefs.setNotificationEmailEnabled(true);
        prefs.setNotificationEmailPartnershipEnabled(false);
        prefs.setNotificationEmailSupportEnabled(false);
        prefs.setNotificationPushEnabled(false);
        prefs.setNotificationSmsEnabled(false);
        prefs.setDarkModeEnabled(false);
        prefs.setLanguage("en");
        prefs.setTimezone("UTC");
        prefs.setCommunicationFrequency(EmailFrequency.WEEKLY_DIGEST);
        prefs.setGdprMarketingConsent(false);
        prefs.setSharePhoneForPayments(true);
        prefs.setTwoFactorAuthenticationEnabled(false);
        return userPreferencesRepository.save(prefs);
    }

    // ========================================================================
    // REQUEST BUILDERS
    // ========================================================================

    /**
     * Builds a simple NotificationRequest for any notification type.
     */
    protected NotificationRequest buildRequest(Long userId, NotificationType type) {
        return NotificationRequest.builder()
                .userId(userId)
                .type(type)
                .parameters(java.util.Map.of(
                        "influencerName", "Test Influencer",
                        "companyName", "Test Company",
                        "opportunityName", "Test Campaign"
                ))
                .actionUrl("/collaborations/registrations")
                .groupKey("collab:test-" + System.nanoTime())
                .workflowStep(type.name())
                .build();
    }

    // ========================================================================
    // NOTIFICATION BUILDERS (direct repository insert, avoids REQUIRES_NEW)
    // ========================================================================

    /**
     * Creates a notification directly via the repository.
     * <p>
     * This bypasses {@code NotificationService.createNotification()} which uses
     * {@code @Transactional(propagation = REQUIRES_NEW)} and therefore cannot
     * see uncommitted test data. The preference-gating logic is tested
     * separately in {@link NotificationService_ShouldNotify_IntegrationTest}.
     */
    protected Notification createTestNotification(User user, NotificationType type, String groupKey) {
        return createTestNotification(user, type, groupKey, true);
    }

    /**
     * Creates a notification directly via the repository with configurable emailEnabled.
     */
    protected Notification createTestNotification(User user, NotificationType type,
                                                   String groupKey, boolean emailEnabled) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .category(type.getCategory())
                .priority(type.getPriority())
                .title("Test " + type.name())
                .message("Test message for " + type.name())
                .actionUrl("/collaborations/registrations")
                .actionLabel("View")
                .translationKey(type.getTranslationKeyPrefix())
                .languageCode("en")
                .groupKey(groupKey)
                .workflowStep(type.name())
                .emailEnabled(emailEnabled)
                .build();
        return notificationRepository.save(notification);
    }
}
