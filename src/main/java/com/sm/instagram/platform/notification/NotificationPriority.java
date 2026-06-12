package com.sm.instagram.platform.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Notification priority levels.
 * Affects visual presentation and potential delivery timing.
 */
@Getter
@AllArgsConstructor
public enum NotificationPriority {
    /**
     * Informational notifications that don't require action.
     * Example: Collaboration complete
     */
    LOW("blue", "heroicons_outline:information-circle"),

    /**
     * Standard notifications about workflow progress.
     * Example: Content posted
     */
    MEDIUM("primary", "heroicons_outline:bell"),

    /**
     * Important notifications requiring attention.
     * Example: Application accepted, content approved
     */
    HIGH("amber", "heroicons_outline:exclamation-circle"),

    /**
     * Critical notifications that cannot be disabled.
     * Example: Account suspended, account banned
     */
    CRITICAL("red", "heroicons_outline:exclamation-triangle");

    /**
     * Color theme key for frontend styling.
     * Maps to Tailwind/Material color palette.
     */
    private final String colorTheme;

    /**
     * Icon identifier for frontend display.
     * Uses Heroicons naming convention.
     */
    private final String icon;

    @JsonCreator
    public static NotificationPriority fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    @JsonValue
    public String getValue() {
        return name();
    }
}