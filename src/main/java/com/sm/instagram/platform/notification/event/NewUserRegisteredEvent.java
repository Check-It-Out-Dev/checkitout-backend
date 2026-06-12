package com.sm.instagram.platform.notification.event;

import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Domain event published when a new user registers on the platform.
 * <p>
 * Published by: RegistrationService (both email/password and social OAuth registration)
 * Handled by: NotificationEventListener (creates ADMIN_NEW_USER_REGISTERED for all admins)
 * <p>
 * This supplements the existing admin email notification (NTF-002).
 * The in-app notification appears in the admin's notification bell.
 */
@Getter
public class NewUserRegisteredEvent extends ApplicationEvent {

    private final User user;
    private final String registrationType;
    private final LocalDateTime occurredAt;

    /**
     * @param source           the object that published this event
     * @param user             the newly registered user
     * @param registrationType how the user registered (EMAIL_PASSWORD, SOCIAL_OAUTH)
     */
    public NewUserRegisteredEvent(Object source, User user, String registrationType) {
        super(source);
        this.user = user;
        this.registrationType = registrationType;
        this.occurredAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return String.format("NewUserRegisteredEvent{userId=%d, type=%s, registration=%s}",
                user.getId(), user.getUserType(), registrationType);
    }
}
