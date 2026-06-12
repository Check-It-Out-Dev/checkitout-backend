package com.sm.instagram.platform.subscription.event;

import com.sm.instagram.platform.notification.NotificationType;
import com.sm.instagram.platform.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * Published by SubscriptionService when a subscription lifecycle change should trigger a notification.
 * Single event class with NotificationType discriminator — all subscription notifications share
 * the same shape: userId + plan-related translation parameters.
 */
@Getter
public class SubscriptionNotificationEvent extends ApplicationEvent {

    private final User user;
    private final NotificationType notificationType;
    private final Map<String, String> parameters;

    public SubscriptionNotificationEvent(Object source, User user,
                                          NotificationType notificationType,
                                          Map<String, String> parameters) {
        super(source);
        this.user = user;
        this.notificationType = notificationType;
        this.parameters = parameters != null ? parameters : Map.of();
    }
}
