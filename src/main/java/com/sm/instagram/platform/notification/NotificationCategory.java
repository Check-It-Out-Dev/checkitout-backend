package com.sm.instagram.platform.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Notification categories for grouping and preference management.
 * Users can enable/disable entire categories in their preferences.
 */
public enum NotificationCategory {
    /**
     * Partnership workflow notifications:
     * Applications, offers, content reviews, collaborations
     */
    PARTNERSHIP,

    /**
     * Account status notifications:
     * Activation, suspension, banning
     */
    ACCOUNT,

    /**
     * Support ticket notifications:
     * Responses, resolution, closure
     */
    SUPPORT,

    /**
     * System notifications:
     * Maintenance, announcements (future use)
     */
    SYSTEM;

    @JsonCreator
    public static NotificationCategory fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    @JsonValue
    public String getValue() {
        return name();
    }
}