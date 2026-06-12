package com.sm.instagram.platform.subscription.event;

import com.sm.instagram.platform.notification.NotificationService;
import com.sm.instagram.platform.notification.dto.NotificationRequest;
import com.sm.instagram.platform.notification.event.AccountActivatedEvent;
import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for subscription lifecycle events and creates notifications.
 * Also listens for account activation to auto-create FREE subscription for COMPANY users.
 * AFTER_COMMIT ensures side effects only happen after business transaction succeeds.
 * Failures are logged but never re-thrown — business operation is not affected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionNotificationEventListener {

    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionNotification(SubscriptionNotificationEvent event) {
        try {
            NotificationRequest request = NotificationRequest.forSubscription(
                    event.getUser().getId(),
                    event.getNotificationType(),
                    event.getParameters()
            );
            notificationService.createNotification(request);
            log.info("Subscription notification created: type={}, userId={}",
                    event.getNotificationType(), event.getUser().getId());
        } catch (Exception e) {
            log.error("Failed to create subscription notification: type={}, userId={}",
                    event.getNotificationType(), event.getUser().getId(), e);
        }
    }

    /**
     * Auto-create FREE subscription when a COMPANY user is activated.
     * This ensures every activated company has a subscription + billing period from day one.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountActivated(AccountActivatedEvent event) {
        try {
            var user = event.getUser();
            if (user.getUserType() != UserType.COMPANY) {
                return;
            }
            subscriptionService.getOrCreateSubscription(user.getId());
            log.info("FREE subscription ensured for activated company: userId={}, source={}",
                    user.getId(), event.getActivationSource());
        } catch (Exception e) {
            log.error("Failed to create FREE subscription on activation: userId={}, error={}",
                    event.getUser().getId(), e.getMessage(), e);
        }
    }
}
