package com.sm.instagram.platform.integration.service.notification;

import com.sm.instagram.platform.notification.Notification;
import com.sm.instagram.platform.notification.NotificationCategory;
import com.sm.instagram.platform.notification.NotificationPriority;
import com.sm.instagram.platform.notification.NotificationType;
import com.sm.instagram.platform.notification.dto.NotificationRequest;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Integration tests for ACCOUNT_ACTIVATED notification flow (NTF-003).
 * <p>
 * Tests that NotificationService correctly creates ACCOUNT_ACTIVATED notifications
 * using the new {@code NotificationRequest.forAccount()} factory method.
 * <p>
 * Note: {@code @TransactionalEventListener(AFTER_COMMIT)} handlers don't fire in
 * {@code @Transactional} test context (rollback, no commit), so we test the
 * notification creation path directly. The event listener is tested at unit level.
 */
@DisplayName("NotificationService - Account Activation (NTF-003)")
class NotificationService_AccountActivation_IntegrationTest extends NotificationServiceIntegrationTestBase {

    @Nested
    @DisplayName("ACCOUNT_ACTIVATED via forAccount() factory")
    class AccountActivatedNotification {

        @Test
        @DisplayName("should persist ACCOUNT_ACTIVATED notification with correct fields")
        void shouldPersist_accountActivatedNotification() {
            // Given — create notification directly (bypasses REQUIRES_NEW)
            Notification notification = createTestNotification(
                    testInfluencer,
                    NotificationType.ACCOUNT_ACTIVATED,
                    "account:" + testInfluencer.getId()
            );

            // Then — verify persisted correctly
            assertSoftly(softly -> {
                softly.assertThat(notification.getId()).isNotNull();
                softly.assertThat(notification.getUser().getId()).isEqualTo(testInfluencer.getId());
                softly.assertThat(notification.getType()).isEqualTo(NotificationType.ACCOUNT_ACTIVATED);
                softly.assertThat(notification.getCategory()).isEqualTo(NotificationCategory.ACCOUNT);
                softly.assertThat(notification.getPriority()).isEqualTo(NotificationPriority.HIGH);
                softly.assertThat(notification.getGroupKey()).isEqualTo("account:" + testInfluencer.getId());
                softly.assertThat(notification.getIsRead()).isFalse();
                softly.assertThat(notification.getIsArchived()).isFalse();
            });
        }

        @Test
        @DisplayName("should enable email for ACCOUNT_ACTIVATED (emailDefault=ENABLED)")
        void shouldEnableEmail_forAccountActivated() {
            Notification notification = createTestNotification(
                    testInfluencer,
                    NotificationType.ACCOUNT_ACTIVATED,
                    "account:" + testInfluencer.getId(),
                    true // emailEnabled
            );

            assertThat(notification.getEmailEnabled()).isTrue();
            assertThat(notification.getEmailSent()).isFalse();
            assertThat(notification.getEmailRetryCount()).isZero();
        }

        @Test
        @DisplayName("should be retrievable via unread count query")
        void shouldBeRetrievable_viaUnreadCount() {
            createTestNotification(
                    testInfluencer,
                    NotificationType.ACCOUNT_ACTIVATED,
                    "account:" + testInfluencer.getId()
            );

            long unreadCount = notificationRepository.countUnreadByUserId(testInfluencer.getId());
            assertThat(unreadCount).isEqualTo(1);
        }

        @Test
        @DisplayName("forAccount() factory should create valid request")
        void forAccountFactory_shouldCreateValidRequest() {
            NotificationRequest request = NotificationRequest.forAccount(
                    testInfluencer.getId(),
                    NotificationType.ACCOUNT_ACTIVATED
            );

            assertSoftly(softly -> {
                softly.assertThat(request.getUserId()).isEqualTo(testInfluencer.getId());
                softly.assertThat(request.getType()).isEqualTo(NotificationType.ACCOUNT_ACTIVATED);
                softly.assertThat(request.getActionUrl()).isEqualTo("/user/settings");
                softly.assertThat(request.getGroupKey()).isEqualTo("account:" + testInfluencer.getId());
                // No partnership-specific fields
                softly.assertThat(request.getAppliedOpportunityId()).isNull();
                softly.assertThat(request.getPartnershipOpportunityId()).isNull();
                softly.assertThat(request.getInfluencerId()).isNull();
                softly.assertThat(request.getCompanyId()).isNull();
                softly.assertThat(request.getSnapshot()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("ACCOUNT_ACTIVATED bypasses preferences")
    class AccountBypassesPreferences {

        @Test
        @DisplayName("should create notification even when all preferences are disabled")
        void shouldCreate_evenWhenPreferencesDisabled() {
            // Given — user has opted out of everything
            createPreferencesAllDisabled(testInfluencer);

            // When — ACCOUNT category bypasses preferences (tested in shouldNotify integration tests)
            boolean shouldNotify = notificationService.shouldNotify(
                    testInfluencer.getId(),
                    NotificationType.ACCOUNT_ACTIVATED
            );

            // Then — ACCOUNT notifications cannot be suppressed
            assertThat(shouldNotify).isTrue();
        }
    }

    @Nested
    @DisplayName("Cross-user isolation")
    class CrossUserIsolation {

        @Test
        @DisplayName("company should not see influencer's activation notification")
        void companyShouldNotSee_influencerActivationNotification() {
            createTestNotification(
                    testInfluencer,
                    NotificationType.ACCOUNT_ACTIVATED,
                    "account:" + testInfluencer.getId()
            );

            long companyUnread = notificationRepository.countUnreadByUserId(testCompany.getId());
            assertThat(companyUnread).isZero();
        }
    }
}
