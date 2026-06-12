package com.sm.instagram.platform.integration.service.notification;

import com.sm.instagram.platform.notification.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Integration tests for notification persistence, retrieval, and operations.
 *
 * <p><b>Why we create notifications via repository instead of service:</b>
 * {@code NotificationService.createNotification()} uses
 * {@code @Transactional(propagation = REQUIRES_NEW)} which opens a separate
 * transaction that cannot see the test's uncommitted data (users, preferences).
 * The preference-gating logic is fully covered by
 * {@link NotificationService_ShouldNotify_IntegrationTest}.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Notification entity is correctly persisted and retrievable</li>
 *   <li>getUserNotifications(), getUnreadCount(), getNotificationsByGroup() work correctly</li>
 *   <li>markAsRead(), markAllAsRead(), archiveNotification() update state properly</li>
 *   <li>Cross-user isolation ensures users only see their own notifications</li>
 * </ul>
 */
@DisplayName("NotificationService - notification operations")
class NotificationService_CreateNotification_IntegrationTest extends NotificationServiceIntegrationTestBase {

    // ========================================================================
    // PERSISTENCE & RETRIEVAL
    // ========================================================================

    @Nested
    @DisplayName("Notification persistence and retrieval")
    class PersistenceAndRetrieval {

        @Test
        @DisplayName("Persisted notification should have correct fields")
        void persistedNotificationShouldHaveCorrectFields() {
            authenticateAs(testCompany);

            Notification saved = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime());

            assertThat(saved).isNotNull();
            assertSoftly(softly -> {
                softly.assertThat(saved.getId()).isNotNull();
                softly.assertThat(saved.getUser().getId()).isEqualTo(testCompany.getId());
                softly.assertThat(saved.getType()).isEqualTo(NotificationType.APPLICATION_RECEIVED);
                softly.assertThat(saved.getCategory()).isEqualTo(NotificationCategory.PARTNERSHIP);
                softly.assertThat(saved.getPriority()).isEqualTo(NotificationPriority.HIGH);
                softly.assertThat(saved.getTitle()).isNotBlank();
                softly.assertThat(saved.getMessage()).isNotBlank();
                softly.assertThat(saved.getIsRead()).isFalse();
                softly.assertThat(saved.getIsArchived()).isFalse();
                softly.assertThat(saved.getGroupKey()).isNotBlank();
                softly.assertThat(saved.getWorkflowStep()).isEqualTo("APPLICATION_RECEIVED");
            });
        }

        @Test
        @DisplayName("Notification should be retrievable via getUserNotifications")
        void notificationShouldBeRetrievableViaGetUserNotifications() {
            authenticateAs(testCompany);

            Notification saved = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime());

            Page<Notification> page = notificationService.getUserNotifications(
                    testCompany.getId(),
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

            assertThat(page.getContent())
                    .extracting(Notification::getId)
                    .contains(saved.getId());
        }

        @Test
        @DisplayName("Notification should increment unread count")
        void notificationShouldIncrementUnreadCount() {
            authenticateAs(testCompany);

            long countBefore = notificationService.getUnreadCount(testCompany.getId());

            createTestNotification(testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime());

            long countAfter = notificationService.getUnreadCount(testCompany.getId());

            assertThat(countAfter).isEqualTo(countBefore + 1);
        }

        @Test
        @DisplayName("Multiple notifications should all be retrievable")
        void multipleNotificationsShouldAllBeRetrievable() {
            authenticateAs(testInfluencer);

            createTestNotification(testInfluencer, NotificationType.APPLICATION_ACCEPTED,
                    "collab:test1-" + System.nanoTime());
            createTestNotification(testInfluencer, NotificationType.CONTENT_APPROVED,
                    "collab:test2-" + System.nanoTime());
            createTestNotification(testInfluencer, NotificationType.POST_VERIFIED,
                    "collab:test3-" + System.nanoTime());

            long count = notificationService.getUnreadCount(testInfluencer.getId());

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("Notification should be retrievable by group key")
        void notificationShouldBeRetrievableByGroupKey() {
            authenticateAs(testCompany);

            String groupKey = "collab:integration-test-" + System.nanoTime();
            createTestNotification(testCompany, NotificationType.APPLICATION_RECEIVED, groupKey);

            var grouped = notificationService.getNotificationsByGroup(
                    testCompany.getId(), groupKey);

            assertThat(grouped).hasSize(1);
            assertThat(grouped.get(0).getGroupKey()).isEqualTo(groupKey);
        }

        @Test
        @DisplayName("Notification with emailEnabled=true should have correct email fields")
        void notificationWithEmailEnabledShouldHaveCorrectFields() {
            authenticateAs(testCompany);

            Notification saved = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime(), true);

            assertSoftly(softly -> {
                softly.assertThat(saved.getEmailEnabled()).isTrue();
                softly.assertThat(saved.getEmailSent()).isFalse();
                softly.assertThat(saved.getEmailRetryCount()).isZero();
                softly.assertThat(saved.shouldAttemptEmail()).isTrue();
            });
        }

        @Test
        @DisplayName("Notification with emailEnabled=false should not attempt email")
        void notificationWithEmailDisabledShouldNotAttemptEmail() {
            authenticateAs(testCompany);

            Notification saved = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime(), false);

            assertThat(saved.getEmailEnabled()).isFalse();
            assertThat(saved.shouldAttemptEmail()).isFalse();
        }
    }

    // ========================================================================
    // MARK AS READ / ARCHIVE
    // ========================================================================

    @Nested
    @DisplayName("Mark as read and archive operations")
    class MarkAsReadAndArchive {

        @Test
        @DisplayName("Mark as read should update isRead flag and readAt")
        void markAsReadShouldUpdateFlags() {
            authenticateAs(testCompany);

            Notification created = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime());
            assertThat(created.getIsRead()).isFalse();

            Notification read = notificationService.markAsRead(
                    created.getId(), testCompany.getId());

            assertThat(read.getIsRead()).isTrue();
            assertThat(read.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("Mark as read should decrement unread count")
        void markAsReadShouldDecrementUnreadCount() {
            authenticateAs(testCompany);

            Notification created = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime());

            long countBefore = notificationService.getUnreadCount(testCompany.getId());

            notificationService.markAsRead(created.getId(), testCompany.getId());

            long countAfter = notificationService.getUnreadCount(testCompany.getId());
            assertThat(countAfter).isEqualTo(countBefore - 1);
        }

        @Test
        @DisplayName("Archive should exclude notification from getUserNotifications")
        void archiveShouldExcludeFromGetUserNotifications() {
            authenticateAs(testCompany);

            Notification created = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime());

            notificationService.archiveNotification(created.getId(), testCompany.getId());

            Page<Notification> page = notificationService.getUserNotifications(
                    testCompany.getId(),
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

            assertThat(page.getContent())
                    .extracting(Notification::getId)
                    .doesNotContain(created.getId());
        }

        @Test
        @DisplayName("markAllAsRead should mark all unread notifications")
        void markAllAsReadShouldMarkAllUnread() {
            authenticateAs(testInfluencer);

            createTestNotification(testInfluencer, NotificationType.APPLICATION_ACCEPTED,
                    "collab:test1-" + System.nanoTime());
            createTestNotification(testInfluencer, NotificationType.CONTENT_APPROVED,
                    "collab:test2-" + System.nanoTime());

            assertThat(notificationService.getUnreadCount(testInfluencer.getId())).isEqualTo(2);

            int markedCount = notificationService.markAllAsRead(testInfluencer.getId());

            assertThat(markedCount).isEqualTo(2);
            assertThat(notificationService.getUnreadCount(testInfluencer.getId())).isZero();
        }
    }

    // ========================================================================
    // EMAIL TRACKING
    // ========================================================================

    @Nested
    @DisplayName("Email delivery tracking")
    class EmailDeliveryTracking {

        @Test
        @DisplayName("markEmailSent should update email tracking fields")
        void markEmailSentShouldUpdateFields() {
            authenticateAs(testCompany);

            Notification notification = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime(), true);

            notificationService.markEmailSent(notification);

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertSoftly(softly -> {
                softly.assertThat(updated.getEmailSent()).isTrue();
                softly.assertThat(updated.getEmailSentAt()).isNotNull();
                softly.assertThat(updated.shouldAttemptEmail()).isFalse();
            });
        }

        @Test
        @DisplayName("recordEmailFailure should increment retry count")
        void recordEmailFailureShouldIncrementRetryCount() {
            authenticateAs(testCompany);

            Notification notification = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime(), true);

            notificationService.recordEmailFailure(notification, "SMTP timeout");

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(updated.getEmailRetryCount()).isEqualTo(1);
            assertThat(updated.getEmailError()).isEqualTo("SMTP timeout");
            assertThat(updated.shouldAttemptEmail()).isTrue();
        }

        @Test
        @DisplayName("Email should be disabled after 3 failures")
        void emailShouldBeDisabledAfterMaxRetries() {
            authenticateAs(testCompany);

            Notification notification = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test-" + System.nanoTime(), true);

            notificationService.recordEmailFailure(notification, "Attempt 1");
            notificationService.recordEmailFailure(notification, "Attempt 2");
            notificationService.recordEmailFailure(notification, "Attempt 3");

            Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
            assertThat(updated.getEmailRetryCount()).isEqualTo(3);
            assertThat(updated.getEmailEnabled()).isFalse();
            assertThat(updated.shouldAttemptEmail()).isFalse();
        }

        @Test
        @DisplayName("findPendingEmails should return email-enabled unread notifications")
        void findPendingEmailsShouldReturnCorrectNotifications() {
            authenticateAs(testCompany);

            Notification emailEnabled = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:test1-" + System.nanoTime(), true);
            Notification emailDisabled = createTestNotification(
                    testCompany, NotificationType.APPLICATION_ACCEPTED,
                    "collab:test2-" + System.nanoTime(), false);

            var pending = notificationService.findPendingEmails(100);

            assertThat(pending)
                    .extracting(Notification::getId)
                    .contains(emailEnabled.getId())
                    .doesNotContain(emailDisabled.getId());
        }
    }

    // ========================================================================
    // CROSS-USER ISOLATION
    // ========================================================================

    @Nested
    @DisplayName("Cross-user isolation")
    class CrossUserIsolation {

        @Test
        @DisplayName("User should only see own notifications")
        void userShouldOnlySeeOwnNotifications() {
            // Create notification for company
            authenticateAs(testCompany);
            createTestNotification(testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:company-" + System.nanoTime());

            // Create notification for influencer
            authenticateAs(testInfluencer);
            createTestNotification(testInfluencer, NotificationType.APPLICATION_ACCEPTED,
                    "collab:influencer-" + System.nanoTime());

            // Verify each user only sees their own
            long companyCount = notificationService.getUnreadCount(testCompany.getId());
            long influencerCount = notificationService.getUnreadCount(testInfluencer.getId());

            assertThat(companyCount).isEqualTo(1);
            assertThat(influencerCount).isEqualTo(1);
        }

        @Test
        @DisplayName("getUserNotifications should not leak across users")
        void getUserNotificationsShouldNotLeakAcrossUsers() {
            authenticateAs(testCompany);
            Notification companyNotif = createTestNotification(
                    testCompany, NotificationType.APPLICATION_RECEIVED,
                    "collab:company-" + System.nanoTime());

            authenticateAs(testInfluencer);
            Notification influencerNotif = createTestNotification(
                    testInfluencer, NotificationType.APPLICATION_ACCEPTED,
                    "collab:influencer-" + System.nanoTime());

            // Check company's list
            authenticateAs(testCompany);
            Page<Notification> companyPage = notificationService.getUserNotifications(
                    testCompany.getId(),
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

            assertThat(companyPage.getContent())
                    .extracting(Notification::getId)
                    .contains(companyNotif.getId())
                    .doesNotContain(influencerNotif.getId());
        }
    }
}
