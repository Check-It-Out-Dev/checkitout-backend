package com.sm.instagram.platform.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Complete enumeration of all notification types in the system.
 * Each type defines:
 * - Category (for grouping and preferences)
 * - Priority (for visual styling)
 * - Email default behavior
 * - Description for documentation
 *
 * @see NotificationCategory
 * @see NotificationPriority
 */
@Getter
@AllArgsConstructor
public enum NotificationType {

    // ========================================================================
    // PARTNERSHIP WORKFLOW - Company receives these
    // ========================================================================

    /**
     * Sent to COMPANY when an influencer applies to their opportunity.
     * Triggered by: OpportunityStatus.APPLIED (new application)
     */
    APPLICATION_RECEIVED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.HIGH,
            EmailDefault.DIGEST,
            "Influencer has applied to opportunity"
    ),

    /**
     * Sent to COMPANY when influencer confirms the collaboration.
     * Triggered by: ACCEPTED_BY_COMPANY -> ACCEPTED_BY_INFLUENCER
     */
    OFFER_ACCEPTED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Influencer confirmed collaboration"
    ),

    /**
     * Sent to COMPANY when influencer declines the opportunity.
     * Triggered by: ACCEPTED_BY_COMPANY -> REJECTED_BY_INFLUENCER
     */
    OFFER_REJECTED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.MEDIUM,
            EmailDefault.DIGEST,
            "Influencer declined collaboration"
    ),

    /**
     * Sent to COMPANY when influencer submits content for review.
     * Triggered by: ACCEPTED_BY_INFLUENCER -> CONTENT_SEND_TO_ACCEPT
     */
    CONTENT_SUBMITTED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Content ready for review"
    ),

    /**
     * Sent to COMPANY when influencer posts approved content.
     * Triggered by: CONTENT_APPROVED -> CONTENT_POSTED
     */
    CONTENT_POSTED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.MEDIUM,
            EmailDefault.DIGEST,
            "Content has been published"
    ),

    // ========================================================================
    // PARTNERSHIP WORKFLOW - Influencer receives these
    // ========================================================================

    /**
     * Sent to INFLUENCER when company accepts their application.
     * Triggered by: APPLIED -> ACCEPTED_BY_COMPANY
     */
    APPLICATION_ACCEPTED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Application was accepted"
    ),

    /**
     * Sent to INFLUENCER when company rejects their application.
     * Triggered by: APPLIED -> REJECTED_BY_COMPANY
     */
    APPLICATION_REJECTED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.MEDIUM,
            EmailDefault.DIGEST,
            "Application was not selected"
    ),

    /**
     * Sent to INFLUENCER when company approves their content.
     * Triggered by: CONTENT_SEND_TO_ACCEPT -> CONTENT_APPROVED
     */
    CONTENT_APPROVED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Content was approved - ready to publish"
    ),

    /**
     * Sent to INFLUENCER when company requests content revision.
     * Triggered by: CONTENT_SEND_TO_ACCEPT -> CONTENT_REJECTED
     */
    CONTENT_REJECTED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Content needs revision"
    ),

    /**
     * Sent to INFLUENCER when company verifies their post.
     * Triggered by: CONTENT_POSTED -> TO_BE_PAID
     */
    POST_VERIFIED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Post verified - payment processing"
    ),

    /**
     * Sent to INFLUENCER when company rejects their post.
     * Triggered by: CONTENT_POSTED -> CONTENT_POSTED_REJECTED
     */
    POST_REJECTED(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Post needs correction"
    ),

    // ========================================================================
    // PARTNERSHIP WORKFLOW - Both parties receive
    // ========================================================================

    /**
     * Sent to BOTH parties when collaboration is complete.
     * Triggered by: TO_BE_PAID -> DONE
     */
    COLLABORATION_COMPLETE(
            NotificationCategory.PARTNERSHIP,
            NotificationPriority.LOW,
            EmailDefault.DISABLED,
            "Collaboration successfully completed"
    ),

    // ========================================================================
    // ACCOUNT NOTIFICATIONS
    // ========================================================================

    /**
     * Sent when user's account is activated.
     * Triggered by: AccountStatus change to ACTIVE (admin, email verification, company data confirmation)
     * Published via: AccountActivatedEvent → NotificationEventListener
     */
    ACCOUNT_ACTIVATED(
            NotificationCategory.ACCOUNT,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Account has been activated"
    ),

    /**
     * Sent when user's account is suspended.
     * Triggered by: AccountStatus change to SUSPENDED
     * CANNOT be disabled by user (ALWAYS).
     * <p><b>NOT YET IMPLEMENTED</b> - no service currently triggers this notification.</p>
     */
    ACCOUNT_SUSPENDED(
            NotificationCategory.ACCOUNT,
            NotificationPriority.CRITICAL,
            EmailDefault.ALWAYS,
            "Account has been suspended"
    ),

    /**
     * Sent when user's account is permanently banned.
     * Triggered by: AccountStatus change to BANNED
     * CANNOT be disabled by user (ALWAYS).
     * <p><b>NOT YET IMPLEMENTED</b> - no service currently triggers this notification.</p>
     */
    ACCOUNT_BANNED(
            NotificationCategory.ACCOUNT,
            NotificationPriority.CRITICAL,
            EmailDefault.ALWAYS,
            "Account has been permanently disabled"
    ),

    // ========================================================================
    // ADMIN NOTIFICATIONS
    // ========================================================================

    /**
     * Sent to ALL ADMIN users when a new user registers on the platform.
     * Triggered by: NewUserRegisteredEvent (from RegistrationService)
     * Supplements the existing admin email (NTF-002).
     */
    ADMIN_NEW_USER_REGISTERED(
            NotificationCategory.SYSTEM,
            NotificationPriority.MEDIUM,
            EmailDefault.DISABLED,
            "New user has registered on the platform"
    ),

    /**
     * Sent to ALL ADMIN users when a user's account is activated.
     * Triggered by: AccountActivatedEvent (alongside the user's own ACCOUNT_ACTIVATED notification)
     */
    ADMIN_ACCOUNT_ACTIVATED(
            NotificationCategory.SYSTEM,
            NotificationPriority.MEDIUM,
            EmailDefault.DISABLED,
            "A user account has been activated"
    ),

    // ========================================================================
    // SUBSCRIPTION NOTIFICATIONS
    // ========================================================================

    SUBSCRIPTION_TRIAL_ENDING(NotificationCategory.ACCOUNT, NotificationPriority.HIGH, EmailDefault.ALWAYS, "Trial ending reminder"),
    SUBSCRIPTION_TRIAL_EXPIRED(NotificationCategory.ACCOUNT, NotificationPriority.HIGH, EmailDefault.ALWAYS, "Trial has expired"),
    SUBSCRIPTION_PAYMENT_FAILED(NotificationCategory.ACCOUNT, NotificationPriority.CRITICAL, EmailDefault.ALWAYS, "Payment failed"),
    SUBSCRIPTION_PAYMENT_RECOVERED(NotificationCategory.ACCOUNT, NotificationPriority.MEDIUM, EmailDefault.ENABLED, "Payment recovered"),
    SUBSCRIPTION_PAYMENT_EXHAUSTED(NotificationCategory.ACCOUNT, NotificationPriority.CRITICAL, EmailDefault.ALWAYS, "All payment retries failed"),
    SUBSCRIPTION_UPGRADED(NotificationCategory.ACCOUNT, NotificationPriority.MEDIUM, EmailDefault.ENABLED, "Plan upgraded"),
    SUBSCRIPTION_DOWNGRADE_SCHEDULED(NotificationCategory.ACCOUNT, NotificationPriority.MEDIUM, EmailDefault.ENABLED, "Downgrade scheduled"),
    SUBSCRIPTION_DOWNGRADED(NotificationCategory.ACCOUNT, NotificationPriority.MEDIUM, EmailDefault.ENABLED, "Plan downgraded"),
    SUBSCRIPTION_SUSPENDED(NotificationCategory.ACCOUNT, NotificationPriority.CRITICAL, EmailDefault.ALWAYS, "Account suspended due to terms"),
    SUBSCRIPTION_REACTIVATED(NotificationCategory.ACCOUNT, NotificationPriority.MEDIUM, EmailDefault.ENABLED, "Account reactivated"),

    // ========================================================================
    // SUPPORT NOTIFICATIONS
    // Status: NOT YET IMPLEMENTED - no service triggers these notifications.
    // Kept as placeholders for planned support ticket notifications.
    // ========================================================================

    /**
     * Sent when support team responds to ticket.
     * Triggered by: New SupportTicketResponse created
     * <p><b>NOT YET IMPLEMENTED</b> - no service currently triggers this notification.</p>
     */
    TICKET_RESPONSE(
            NotificationCategory.SUPPORT,
            NotificationPriority.HIGH,
            EmailDefault.ENABLED,
            "Support team has responded"
    ),

    /**
     * Sent when support ticket is marked as resolved.
     * Triggered by: SupportTicket status -> RESOLVED
     * <p><b>NOT YET IMPLEMENTED</b> - no service currently triggers this notification.</p>
     */
    TICKET_RESOLVED(
            NotificationCategory.SUPPORT,
            NotificationPriority.MEDIUM,
            EmailDefault.DIGEST,
            "Support ticket has been resolved"
    ),

    /**
     * Sent when support ticket is closed.
     * Triggered by: SupportTicket status -> CLOSED
     * <p><b>NOT YET IMPLEMENTED</b> - no service currently triggers this notification.</p>
     */
    TICKET_CLOSED(
            NotificationCategory.SUPPORT,
            NotificationPriority.LOW,
            EmailDefault.DISABLED,
            "Support ticket has been closed"
    );

    // ========================================================================
    // FIELDS
    // ========================================================================

    private final NotificationCategory category;
    private final NotificationPriority priority;
    private final EmailDefault emailDefault;
    private final String description;

    // ========================================================================
    // JSON SERIALIZATION
    // ========================================================================

    /**
     * Creates NotificationType from JSON string value.
     * Handles case-insensitive parsing.
     */
    @JsonCreator
    public static NotificationType fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    /**
     * Returns the enum name for JSON serialization.
     */
    @JsonValue
    public String getValue() {
        return name();
    }

    // ========================================================================
    // EMAIL DEFAULT BEHAVIOR
    // ========================================================================

    /**
     * Defines default email behavior for each notification type.
     */
    public enum EmailDefault {
        /**
         * Email is ALWAYS sent, user CANNOT disable.
         * Use only for critical security/account notifications.
         */
        ALWAYS,

        /**
         * Email is enabled by default, user CAN disable.
         * Use for important workflow notifications.
         */
        ENABLED,

        /**
         * Email will be batched in digest (future feature).
         * For MVP: treated same as ENABLED.
         */
        DIGEST,

        /**
         * Email is disabled by default, user CAN enable.
         * Use for low-priority informational notifications.
         */
        DISABLED
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if this notification type allows user to disable email.
     *
     * @return false only for CRITICAL notifications (ALWAYS)
     */
    public boolean isEmailConfigurable() {
        return emailDefault != EmailDefault.ALWAYS;
    }

    /**
     * Get the dictionary key prefix for this notification type.
     * Used for looking up translations.
     *
     * @return key prefix like "NOTIFICATION_APPLICATION_RECEIVED"
     */
    public String getTranslationKeyPrefix() {
        return "NOTIFICATION_" + name();
    }
}