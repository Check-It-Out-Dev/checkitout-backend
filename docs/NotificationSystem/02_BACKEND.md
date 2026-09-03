# Notification System MVP - Backend Implementation

**Version:** 3.0
**Date:** January 2025
**Package:** `com.sm.instagram.platform.notification`

---

## Table of Contents

1. [Package Structure](#package-structure)
2. [Enums](#enums)
3. [Embeddable Classes](#embeddable-classes)
4. [Entity](#entity)
5. [DTOs](#dtos)
6. [Repository](#repository)
7. [Services](#services)
8. [Domain Events](#domain-events)
9. [Event Listener](#event-listener)
10. [REST Controller](#rest-controller)
11. [Email Infrastructure](#email-infrastructure)
12. [Modifications to Existing Files](#modifications-to-existing-files)

---

## Package Structure

Create this folder structure under `src/main/java/com/sm/instagram/platform/`:

```
notification/
├── Notification.java
├── NotificationType.java
├── NotificationCategory.java
├── NotificationPriority.java
├── NotificationSnapshot.java
├── ActorSnapshot.java
├── CampaignSnapshot.java
├── NotificationRepository.java
├── NotificationService.java
├── NotificationController.java
├── NotificationDtoOut.java
├── NotificationRequest.java
├── NotificationTranslationService.java
├── event/
│   ├── OpportunityStatusChangedEvent.java
│   └── NotificationEventListener.java
└── email/
    ├── NotificationEmailService.java
    └── EmailCronJob.java
```

---

## Enums

### NotificationCategory.java

**Purpose:** Groups notifications into broad categories for user preference management.

```java
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
```

---

### NotificationPriority.java

**Purpose:** Determines visual styling and potential delivery urgency.

```java
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
    LOW("info", "heroicons_outline:information-circle"),

    /**
     * Standard notifications about workflow progress.
     * Example: Content posted
     */
    MEDIUM("primary", "heroicons_outline:bell"),

    /**
     * Important notifications requiring attention.
     * Example: Application accepted, content approved
     */
    HIGH("warning", "heroicons_outline:exclamation-circle"),

    /**
     * Critical notifications that cannot be disabled.
     * Example: Account suspended, account banned
     */
    CRITICAL("danger", "heroicons_outline:exclamation-triangle");

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
```

---

### NotificationType.java

**Purpose:** Defines all notification types with metadata for email defaults and routing.

**IMPORTANT:** This is the central configuration for all notification behavior.

```java
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
     * Triggered by: AccountStatus change to ACTIVE
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
     */
    ACCOUNT_BANNED(
            NotificationCategory.ACCOUNT,
            NotificationPriority.CRITICAL,
            EmailDefault.ALWAYS,
            "Account has been permanently disabled"
    ),

    // ========================================================================
    // SUPPORT NOTIFICATIONS
    // ========================================================================

    /**
     * Sent when support team responds to ticket.
     * Triggered by: New SupportTicketResponse created
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
```

---

## Embeddable Classes

### Why Snapshots?

**Problem:** If an influencer changes their name or a campaign is deleted, old notifications would show incorrect or missing data.

**Solution:** "Freeze" relevant data at notification creation time in a JSONB column. The notification always displays correctly regardless of future changes.

### Business Value of Snapshots

| Benefit | Without Snapshots | With Snapshots |
|---------|-------------------|----------------|
| **Audit Trail** | "You approved application from [DELETED USER]" | "You approved application from Anna Kowalska (@anna_style)" |
| **Legal Disputes** | Cannot prove what was communicated | Full historical record of notification context |
| **User Trust** | Confusing notifications with missing data | Clear, consistent notification history |
| **Support Tickets** | "I don't remember what this notification was about" | Support can see exact context at notification time |
| **GDPR Compliance** | Cannot demonstrate what user was informed about | Immutable record of user communication |

**Real Business Scenarios:**

1. **Payment Dispute (Influencer claims they weren't told about rejection)**
   - Snapshot proves: "Your content for 'Summer Campaign' was rejected on 2025-01-15"
   - Contains: campaign name, rejection reason, company name at that moment

2. **Brand Safety (Company deleted, but notifications remain)**
   - Without snapshot: "Application from [ERROR: Company not found]"
   - With snapshot: "Application from FashionBrand Inc. for 'Winter Collection 2025'"

3. **Name Change (Influencer rebrands)**
   - Without snapshot: Old notifications show new name (confusing timeline)
   - With snapshot: "MariaOld applied on Jan 1" stays accurate even after rebrand to "MariaNew"

4. **Account Deletion (GDPR right to be forgotten)**
   - User data deleted from main tables
   - Notification snapshots remain (anonymized if needed) for audit purposes
   - Business can still see: "User applied to campaign X on date Y"

**Cost-Benefit Analysis:**

| Cost | Value |
|------|-------|
| ~500 bytes extra per notification (JSONB) | Eliminates "broken notification" support tickets |
| Slightly slower write (snapshot creation) | Prevents legal exposure in disputes |
| Minor storage increase (~1GB per 2M notifications) | Complete audit trail for compliance |

**When Snapshots Pay Off:**
- First user complaint about "weird notification" → Already prevented
- First payment dispute → Snapshot proves communication
- First GDPR audit → Immutable notification history available
- First deleted account → Notifications still make sense

---

### ActorSnapshot.java

**Purpose:** Captures influencer OR company identity at notification time.

```java
package com.sm.instagram.platform.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Snapshot of an actor (influencer or company) at notification creation time.
 * Stored as part of NotificationSnapshot JSONB.
 *
 * Why this exists:
 * - User might change their name after notification is created
 * - User might delete their account
 * - Notification should still display original context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User ID of the actor.
     * Can be used to link to profile if still exists.
     */
    private Long id;

    /**
     * Display name at notification time.
     * For influencer: firstName + lastName or Instagram handle
     * For company: company name
     */
    private String name;

    /**
     * Profile picture URL at notification time.
     * Format: /api/users/{id}/avatar or full HTTPS URL
     */
    private String avatarUrl;

    /**
     * User type for display purposes.
     * Values: "INFLUENCER" or "COMPANY"
     */
    private String userType;

    /**
     * Create snapshot from User entity.
     *
     * @param user the user to snapshot
     * @return ActorSnapshot with current user data
     */
    public static ActorSnapshot fromUser(com.sm.instagram.platform.user.User user) {
        if (user == null) {
            return null;
        }

        String displayName = user.getName();
        if (displayName == null || displayName.isBlank()) {
            displayName = user.getFirstName() + " " + user.getLastName();
        }

        return ActorSnapshot.builder()
                .id(user.getId())
                .name(displayName.trim())
                .avatarUrl(user.getProfilePicture())
                .userType(user.getUserType() != null ? user.getUserType().name() : null)
                .build();
    }
}
```

---

### CampaignSnapshot.java

**Purpose:** Captures campaign/opportunity details at notification time.

```java
package com.sm.instagram.platform.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Snapshot of a campaign/opportunity at notification creation time.
 * Stored as part of NotificationSnapshot JSONB.
 *
 * Why this exists:
 * - Campaign title might be edited
 * - Campaign might be deleted
 * - Notification should still show what user applied to
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Partnership opportunity ID.
     * Can be used to link to campaign if still exists.
     */
    private Long id;

    /**
     * Campaign title at notification time.
     */
    private String title;

    /**
     * Applied opportunity ID (the specific application).
     */
    private Long appliedOpportunityId;

    /**
     * Offered compensation at notification time.
     * Might be different from current offer.
     */
    private BigDecimal compensation;

    /**
     * Currency code (e.g., "PLN", "USD", "EUR").
     */
    private String currency;

    /**
     * Create snapshot from AppliedOpportunity entity.
     *
     * @param appliedOpportunity the application to snapshot
     * @return CampaignSnapshot with current data
     */
    public static CampaignSnapshot fromAppliedOpportunity(
            com.sm.instagram.platform.appliedopportunities.AppliedOpportunity appliedOpportunity) {
        if (appliedOpportunity == null) {
            return null;
        }

        var partnership = appliedOpportunity.getPartnershipOpportunity();

        return CampaignSnapshot.builder()
                .id(partnership != null ? partnership.getId() : null)
                .title(partnership != null ? partnership.getTitle() : null)
                .appliedOpportunityId(appliedOpportunity.getId())
                // Note: PartnershipOpportunity uses compensationAmountMin/Max (int), not getBudget()
                .compensation(partnership != null ? BigDecimal.valueOf(partnership.getCompensationAmountMax()) : null)
                .currency("PLN") // Default currency, adjust as needed
                .build();
    }
}
```

---

### NotificationSnapshot.java

**Purpose:** Root container for all snapshot data, stored as JSONB.

```java
package com.sm.instagram.platform.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Complete snapshot of context at notification creation time.
 * Stored as JSONB in the notifications table.
 *
 * This ensures notifications display correctly even if:
 * - Referenced users change their names
 * - Referenced users delete their accounts
 * - Campaigns are modified or deleted
 * - Any referenced entity changes
 *
 * Usage in entity:
 * <pre>
 * {@literal @}JdbcTypeCode(SqlTypes.JSON)
 * {@literal @}Column(columnDefinition = "jsonb")
 * private NotificationSnapshot snapshot;
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The influencer involved in this notification.
     * Present for all partnership notifications.
     */
    private ActorSnapshot influencer;

    /**
     * The company involved in this notification.
     * Present for all partnership notifications.
     */
    private ActorSnapshot company;

    /**
     * The campaign/opportunity details.
     * Present for all partnership notifications.
     */
    private CampaignSnapshot campaign;

    /**
     * The actor who triggered this notification.
     * Useful for showing "John accepted your application".
     */
    private ActorSnapshot triggeredBy;

    /**
     * Support ticket reference (for support notifications).
     */
    private String ticketReference;

    /**
     * Support ticket subject (for support notifications).
     */
    private String ticketSubject;

    /**
     * Timestamp when snapshot was created.
     * For debugging/auditing.
     */
    private LocalDateTime snapshotTime;

    /**
     * Additional context as key-value pairs.
     * For future extensibility without schema changes.
     */
    private Map<String, String> metadata;

    /**
     * Builder helper for partnership notifications.
     */
    public static NotificationSnapshot forPartnership(
            com.sm.instagram.platform.appliedopportunities.AppliedOpportunity appliedOpp,
            com.sm.instagram.platform.user.User triggeredByUser) {

        var partnership = appliedOpp.getPartnershipOpportunity();

        return NotificationSnapshot.builder()
                .influencer(ActorSnapshot.fromUser(appliedOpp.getInfluencer()))
                .company(partnership != null && partnership.getCompany() != null
                        ? ActorSnapshot.fromUser(partnership.getCompany())
                        : null)
                .campaign(CampaignSnapshot.fromAppliedOpportunity(appliedOpp))
                .triggeredBy(ActorSnapshot.fromUser(triggeredByUser))
                .snapshotTime(LocalDateTime.now())
                .build();
    }

    /**
     * Builder helper for support ticket notifications.
     */
    public static NotificationSnapshot forSupportTicket(
            String ticketReference,
            String ticketSubject,
            com.sm.instagram.platform.user.User triggeredByUser) {

        return NotificationSnapshot.builder()
                .ticketReference(ticketReference)
                .ticketSubject(ticketSubject)
                .triggeredBy(ActorSnapshot.fromUser(triggeredByUser))
                .snapshotTime(LocalDateTime.now())
                .build();
    }
}
```

---

## Entity

### Notification.java

**Purpose:** JPA entity for the `notifications` table.

```java
package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Notification entity representing a single notification for a user.
 *
 * Design notes:
 * - Uses JSONB for snapshot to avoid joins and handle deleted entities
 * - Email fields track delivery status for cron job processing
 * - Relationship IDs stored separately for querying without parsing JSONB
 */
@Entity
@Table(name = "notifications", indexes = {
        // Note: JPA @Index does NOT support DESC ordering - use SQL migration for that
        // The Liquibase migration creates the actual index with DESC and WHERE clause
        @Index(name = "idx_notifications_user_unread",
                columnList = "user_id, is_read, created_at"),
        @Index(name = "idx_notifications_email_queue",
                columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_generator")
    @SequenceGenerator(
            name = "notification_generator",
            sequenceName = "notification_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    /**
     * JPA version for optimistic locking.
     * Automatically incremented by JPA on each update to prevent lost updates
     * from concurrent modifications (e.g., user marks as read while archiving).
     * Triggers OptimisticLockingFailureException when concurrent update detected.
     */
    @Version
    @Column(name = "version")
    private Long version;

    // ========================================================================
    // RECIPIENT
    // ========================================================================

    /**
     * The user who should receive this notification.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ========================================================================
    // CLASSIFICATION
    // ========================================================================

    /**
     * Specific notification type (e.g., APPLICATION_RECEIVED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    /**
     * Broad category (PARTNERSHIP, ACCOUNT, SUPPORT).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private NotificationCategory category;

    /**
     * Visual priority level (LOW, MEDIUM, HIGH, CRITICAL).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private NotificationPriority priority;

    // ========================================================================
    // CONTENT (Translated at creation time)
    // ========================================================================

    /**
     * Notification title in user's language.
     * Example: "New Application Received" or "Nowa aplikacja"
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * Notification message in user's language.
     * Example: "John has applied to your opportunity: Summer Campaign"
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /**
     * URL to navigate when notification is clicked.
     * Example: "/collaborations/123" or "/settings/notifications"
     */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    /**
     * Label for action button in user's language.
     * Example: "Review Application" or "Przejrzyj aplikację"
     */
    @Column(name = "action_label", length = 100)
    private String actionLabel;

    // ========================================================================
    // TRANSLATION METADATA
    // ========================================================================

    /**
     * Dictionary key used for translation (for debugging/re-translation).
     * Example: "NOTIFICATION_APPLICATION_RECEIVED"
     */
    @Column(name = "translation_key", length = 100)
    private String translationKey;

    /**
     * Language code used for this notification.
     * Example: "en" or "pl"
     */
    @Column(name = "language_code", length = 10)
    @Builder.Default
    private String languageCode = "en";

    // ========================================================================
    // CONTEXT SNAPSHOT (JSONB)
    // ========================================================================

    /**
     * Frozen context data at notification creation time.
     * Contains influencer, company, campaign info.
     * Ensures notification displays correctly even if entities change/delete.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "jsonb")
    private NotificationSnapshot snapshot;

    // ========================================================================
    // RELATIONSHIP IDs (for querying without parsing JSONB)
    // ========================================================================

    /**
     * Applied opportunity ID for partnership notifications.
     */
    @Column(name = "applied_opportunity_id")
    private Long appliedOpportunityId;

    /**
     * Partnership opportunity ID for campaign-related notifications.
     */
    @Column(name = "partnership_opportunity_id")
    private Long partnershipOpportunityId;

    /**
     * Influencer user ID for partnership notifications.
     */
    @Column(name = "influencer_id")
    private Long influencerId;

    /**
     * Company user ID for partnership notifications.
     */
    @Column(name = "company_id")
    private Long companyId;

    /**
     * Support ticket ID for support notifications.
     */
    @Column(name = "support_ticket_id")
    private Long supportTicketId;

    // ========================================================================
    // WORKFLOW TRACKING
    // ========================================================================

    /**
     * Group key for related notifications.
     * Example: "collab:123" groups all notifications for one collaboration.
     */
    @Column(name = "group_key", length = 100)
    private String groupKey;

    /**
     * Current workflow step when notification was created.
     * Example: "CONTENT_APPROVED"
     */
    @Column(name = "workflow_step", length = 50)
    private String workflowStep;

    // ========================================================================
    // READ STATUS
    // ========================================================================

    /**
     * Whether user has seen this notification.
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    /**
     * When notification was marked as read.
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * Whether notification is archived (hidden from main list).
     */
    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private Boolean isArchived = false;

    /**
     * When notification was archived.
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    // ========================================================================
    // EMAIL DELIVERY
    // ========================================================================

    /**
     * Whether email should be sent for this notification.
     * Set based on user preferences at creation time.
     */
    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private Boolean emailEnabled = true;

    /**
     * Whether email was successfully sent.
     */
    @Column(name = "email_sent", nullable = false)
    @Builder.Default
    private Boolean emailSent = false;

    /**
     * When email was sent.
     */
    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    /**
     * Number of failed email send attempts.
     * Max 3 attempts before giving up.
     */
    @Column(name = "email_retry_count", nullable = false)
    @Builder.Default
    private Integer emailRetryCount = 0;

    /**
     * Error message from last failed email attempt.
     */
    @Column(name = "email_error", length = 500)
    private String emailError;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * When notification was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Mark notification as read.
     */
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }

    /**
     * Archive notification.
     */
    public void archive() {
        if (!this.isArchived) {
            this.isArchived = true;
            this.archivedAt = LocalDateTime.now();
        }
    }

    /**
     * Record email send success.
     */
    public void markEmailSent() {
        this.emailSent = true;
        this.emailSentAt = LocalDateTime.now();
        this.emailError = null;
    }

    /**
     * Record email send failure.
     */
    public void recordEmailFailure(String error) {
        this.emailRetryCount++;
        this.emailError = error != null && error.length() > 500
                ? error.substring(0, 500)
                : error;

        // Disable email after max retries
        if (this.emailRetryCount >= 3) {
            this.emailEnabled = false;
        }
    }

    /**
     * Check if email should still be attempted.
     */
    public boolean shouldAttemptEmail() {
        return this.emailEnabled
                && !this.emailSent
                && this.emailRetryCount < 3;
    }
}
```

---

## DTOs

### NotificationRequest.java

**Purpose:** Internal DTO for creating notifications. Used by NotificationService.

```java
package com.sm.instagram.platform.notification;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Request object for creating a notification.
 * Used internally by NotificationService and event listeners.
 *
 * This is NOT exposed via REST API - it's for internal use only.
 */
@Data
@Builder
public class NotificationRequest {

    // ========================================================================
    // REQUIRED FIELDS
    // ========================================================================

    /**
     * ID of the user who should receive the notification.
     * Required.
     */
    private Long userId;

    /**
     * Type of notification to create.
     * Required.
     */
    private NotificationType type;

    // ========================================================================
    // TRANSLATION PARAMETERS
    // ========================================================================

    /**
     * Parameters for message template placeholders.
     * Example: {"influencerName": "John", "opportunityName": "Summer Campaign"}
     *
     * These replace {placeholderName} in dictionary entries.
     */
    private Map<String, String> parameters;

    // ========================================================================
    // CONTEXT DATA
    // ========================================================================

    /**
     * Snapshot of context at notification time.
     * Contains frozen actor/campaign data.
     */
    private NotificationSnapshot snapshot;

    /**
     * URL to navigate when notification is clicked.
     * Example: "/collaborations/123"
     */
    private String actionUrl;

    // ========================================================================
    // RELATIONSHIP IDs
    // ========================================================================

    /**
     * Applied opportunity ID (for partnership notifications).
     */
    private Long appliedOpportunityId;

    /**
     * Partnership opportunity ID (for campaign-related notifications).
     */
    private Long partnershipOpportunityId;

    /**
     * Influencer user ID (for partnership notifications).
     */
    private Long influencerId;

    /**
     * Company user ID (for partnership notifications).
     */
    private Long companyId;

    /**
     * Support ticket ID (for support notifications).
     */
    private Long supportTicketId;

    // ========================================================================
    // WORKFLOW TRACKING
    // ========================================================================

    /**
     * Group key for related notifications.
     * Example: "collab:123"
     */
    private String groupKey;

    /**
     * Workflow step when notification was created.
     * Example: "CONTENT_APPROVED"
     */
    private String workflowStep;

    // ========================================================================
    // BUILDER HELPERS
    // ========================================================================

    /**
     * Create request for partnership notification.
     */
    public static NotificationRequest forPartnership(
            Long recipientUserId,
            NotificationType type,
            com.sm.instagram.platform.appliedopportunities.AppliedOpportunity appliedOpp,
            com.sm.instagram.platform.user.User triggeredBy,
            Map<String, String> params) {

        var partnership = appliedOpp.getPartnershipOpportunity();

        return NotificationRequest.builder()
                .userId(recipientUserId)
                .type(type)
                .parameters(params)
                .snapshot(NotificationSnapshot.forPartnership(appliedOpp, triggeredBy))
                .actionUrl(buildPartnershipUrl(type, appliedOpp))
                .appliedOpportunityId(appliedOpp.getId())
                .partnershipOpportunityId(partnership != null ? partnership.getId() : null)
                .influencerId(appliedOpp.getInfluencer() != null ? appliedOpp.getInfluencer().getId() : null)
                .companyId(partnership != null && partnership.getCompany() != null
                        ? partnership.getCompany().getId() : null)
                .groupKey("collab:" + appliedOpp.getId())
                .workflowStep(appliedOpp.getOpportunityStatus().name())
                .build();
    }

    /**
     * Build action URL based on notification type.
     */
    private static String buildPartnershipUrl(NotificationType type,
            com.sm.instagram.platform.appliedopportunities.AppliedOpportunity appliedOpp) {

        Long id = appliedOpp.getId();
        Long opportunityId = appliedOpp.getPartnershipOpportunity() != null
                ? appliedOpp.getPartnershipOpportunity().getId()
                : null;

        return switch (type) {
            case APPLICATION_RECEIVED -> "/collaborations/applications/" + id;
            case APPLICATION_ACCEPTED, APPLICATION_REJECTED -> "/collaborations/" + id;
            case OFFER_ACCEPTED, OFFER_REJECTED -> "/opportunities/" + opportunityId + "/applications";
            case CONTENT_SUBMITTED -> "/collaborations/" + id + "/review";
            case CONTENT_APPROVED, CONTENT_REJECTED -> "/collaborations/" + id + "/content";
            case CONTENT_POSTED -> "/collaborations/" + id + "/verify";
            case POST_VERIFIED, POST_REJECTED -> "/collaborations/" + id;
            case COLLABORATION_COMPLETE -> "/collaborations/" + id + "/summary";
            default -> "/collaborations/" + id;
        };
    }
}
```

---

### NotificationDtoOut.java

**Purpose:** Response DTO sent to frontend via REST API.

```java
package com.sm.instagram.platform.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for notification responses to frontend.
 * Follows existing *DtoOut naming convention.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDtoOut {

    /**
     * Notification ID.
     */
    private Long id;

    /**
     * Notification type for frontend display logic.
     */
    private String type;

    /**
     * Category for grouping/filtering.
     */
    private String category;

    /**
     * Priority for visual styling.
     */
    private String priority;

    /**
     * Priority color theme (e.g., "primary", "danger").
     */
    private String colorTheme;

    /**
     * Icon identifier for display.
     */
    private String icon;

    /**
     * Notification title (translated).
     */
    private String title;

    /**
     * Notification message (translated).
     */
    private String message;

    /**
     * URL to navigate on click.
     */
    private String actionUrl;

    /**
     * Action button label (translated).
     */
    private String actionLabel;

    /**
     * Whether notification has been read.
     */
    private Boolean isRead;

    /**
     * When notification was read.
     */
    private LocalDateTime readAt;

    /**
     * Context snapshot for rich display.
     */
    private NotificationSnapshot snapshot;

    /**
     * When notification was created.
     */
    private LocalDateTime createdAt;

    /**
     * Applied opportunity ID (for linking).
     */
    private Long appliedOpportunityId;

    /**
     * Group key for related notifications.
     */
    private String groupKey;

    /**
     * Create DTO from entity.
     */
    public static NotificationDtoOut fromEntity(Notification notification) {
        if (notification == null) {
            return null;
        }

        NotificationPriority priority = notification.getPriority();

        return NotificationDtoOut.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .category(notification.getCategory().name())
                .priority(priority.name())
                .colorTheme(priority.getColorTheme())
                .icon(priority.getIcon())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .actionUrl(notification.getActionUrl())
                .actionLabel(notification.getActionLabel())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .snapshot(notification.getSnapshot())
                .createdAt(notification.getCreatedAt())
                .appliedOpportunityId(notification.getAppliedOpportunityId())
                .groupKey(notification.getGroupKey())
                .build();
    }
}
```

---

## Repository

### NotificationRepository.java

**Purpose:** JPA repository for notification data access.

```java
package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Notification entity.
 *
 * Query naming conventions:
 * - findBy* : Standard JPA derived queries
 * - count* : Counting queries
 * - findPending* : Queue processing queries
 */
@Repository
public interface NotificationRepository extends BaseRepository<Notification, Long> {

    // ========================================================================
    // USER NOTIFICATION QUERIES (Frontend API)
    // ========================================================================

    /**
     * Get paginated notifications for a user (excluding archived).
     * Used by: GET /notifications
     *
     * @param userId   the user's ID
     * @param pageable pagination parameters
     * @return page of notifications, newest first
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.user.id = :userId
              AND n.isArchived = false
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findByUserIdAndNotArchived(
            @Param("userId") Long userId,
            Pageable pageable
    );

    /**
     * Get unread notification count for a user.
     * Used by: GET /notifications/unread/count
     *
     * @param userId the user's ID
     * @return count of unread, non-archived notifications
     */
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.user.id = :userId
              AND n.isRead = false
              AND n.isArchived = false
            """)
    long countUnreadByUserId(@Param("userId") Long userId);

    /**
     * Find notification by ID and user ID (for security).
     * Ensures user can only access their own notifications.
     *
     * @param id     notification ID
     * @param userId user ID
     * @return notification if found and belongs to user
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.id = :id
              AND n.user.id = :userId
            """)
    java.util.Optional<Notification> findByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    // ========================================================================
    // BULK UPDATE QUERIES
    // ========================================================================

    /**
     * Mark all unread notifications as read for a user.
     * Used by: POST /notifications/read-all
     *
     * @param userId user ID
     * @param readAt timestamp when marked as read
     * @return number of notifications updated
     */
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.isRead = true, n.readAt = :readAt
            WHERE n.user.id = :userId
              AND n.isRead = false
              AND n.isArchived = false
            """)
    int markAllAsReadByUserId(
            @Param("userId") Long userId,
            @Param("readAt") LocalDateTime readAt
    );

    // ========================================================================
    // EMAIL QUEUE QUERIES (Cron Job)
    // ========================================================================

    /**
     * Find notifications pending email delivery.
     * Used by: EmailCronJob every 15 minutes
     *
     * Criteria:
     * - email_enabled = true (user wants email)
     * - email_sent = false (not yet sent)
     * - email_retry_count < 3 (not exhausted retries)
     *
     * @param pageable pagination (batch size)
     * @return list of notifications needing email
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.emailEnabled = true
              AND n.emailSent = false
              AND n.emailRetryCount < 3
            ORDER BY n.createdAt ASC
            """)
    List<Notification> findPendingEmails(Pageable pageable);

    // ========================================================================
    // GROUPING QUERIES
    // ========================================================================

    /**
     * Find notifications for a specific collaboration/workflow.
     * Used for showing related notification history.
     *
     * @param userId   user ID
     * @param groupKey group key (e.g., "collab:123")
     * @return list of notifications in the group
     */
    List<Notification> findByUserIdAndGroupKeyOrderByCreatedAtDesc(
            Long userId,
            String groupKey
    );

    /**
     * Find notifications for a specific applied opportunity.
     *
     * @param userId                user ID
     * @param appliedOpportunityId  applied opportunity ID
     * @return list of notifications for this opportunity
     */
    List<Notification> findByUserIdAndAppliedOpportunityIdOrderByCreatedAtDesc(
            Long userId,
            Long appliedOpportunityId
    );

    // ========================================================================
    // CLEANUP QUERIES (Scheduled Maintenance)
    // ========================================================================

    /**
     * Archive old read notifications.
     * Run weekly to keep notification list manageable.
     *
     * @param userId     user ID
     * @param cutoffDate archive notifications older than this
     * @param archivedAt timestamp for archival
     * @return number archived
     */
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.isArchived = true, n.archivedAt = :archivedAt
            WHERE n.user.id = :userId
              AND n.isRead = true
              AND n.isArchived = false
              AND n.createdAt < :cutoffDate
            """)
    int archiveOldReadNotifications(
            @Param("userId") Long userId,
            @Param("cutoffDate") LocalDateTime cutoffDate,
            @Param("archivedAt") LocalDateTime archivedAt
    );

    /**
     * Delete very old archived notifications.
     * Run monthly to free database space.
     *
     * @param cutoffDate delete archived notifications older than this
     * @return number deleted
     */
    @Modifying
    @Query("""
            DELETE FROM Notification n
            WHERE n.isArchived = true
              AND n.archivedAt < :cutoffDate
            """)
    int deleteOldArchivedNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========================================================================
    // STATISTICS QUERIES (Admin/Monitoring)
    // ========================================================================

    /**
     * Count notifications by type in a time range.
     * For monitoring/analytics.
     */
    @Query("""
            SELECT n.type, COUNT(n) FROM Notification n
            WHERE n.createdAt >= :startDate
              AND n.createdAt < :endDate
            GROUP BY n.type
            """)
    List<Object[]> countByTypeInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
```

---

## Services

### NotificationTranslationService.java

**Purpose:** Handles translation lookup and placeholder replacement for notification messages. Uses the existing `DictionaryService` to fetch translations from the `dictionary_entries` table.

**Why this exists:**
- Centralizes all notification translation logic
- Handles placeholder replacement (`{influencerName}` → actual name)
- Provides fallback to English if user's language not available
- Follows existing GDPR logging patterns

```java
package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.dictionary.DictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for translating notification content.
 *
 * Uses DictionaryService to fetch translations and performs placeholder replacement.
 *
 * Dictionary key format for notifications:
 * - Title: NOTIFICATION_{TYPE}_TITLE (e.g., NOTIFICATION_APPLICATION_RECEIVED_TITLE)
 * - Message: NOTIFICATION_{TYPE}_MESSAGE (e.g., NOTIFICATION_APPLICATION_RECEIVED_MESSAGE)
 * - Action: NOTIFICATION_{TYPE}_ACTION (e.g., NOTIFICATION_APPLICATION_RECEIVED_ACTION)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTranslationService {

    private final DictionaryService dictionaryService;

    /**
     * Default language fallback.
     */
    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * Pattern for matching placeholders like {influencerName}.
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Get translated title for a notification type.
     *
     * @param type         notification type
     * @param languageCode user's language (e.g., "en", "pl")
     * @param parameters   values for placeholder replacement
     * @return translated title with placeholders replaced
     */
    public String getTitle(NotificationType type, String languageCode, Map<String, String> parameters) {
        String key = type.getTranslationKeyPrefix() + "_TITLE";
        return getTranslatedText(key, languageCode, parameters);
    }

    /**
     * Get translated message for a notification type.
     *
     * @param type         notification type
     * @param languageCode user's language (e.g., "en", "pl")
     * @param parameters   values for placeholder replacement
     * @return translated message with placeholders replaced
     */
    public String getMessage(NotificationType type, String languageCode, Map<String, String> parameters) {
        String key = type.getTranslationKeyPrefix() + "_MESSAGE";
        return getTranslatedText(key, languageCode, parameters);
    }

    /**
     * Get translated action label for a notification type.
     *
     * @param type         notification type
     * @param languageCode user's language (e.g., "en", "pl")
     * @return translated action label (no placeholders needed)
     */
    public String getActionLabel(NotificationType type, String languageCode) {
        String key = type.getTranslationKeyPrefix() + "_ACTION";
        return getTranslatedText(key, languageCode, null);
    }

    // ========================================================================
    // TRANSLATION CORE
    // ========================================================================

    /**
     * Get translated text with placeholder replacement.
     *
     * @param key          dictionary key
     * @param languageCode user's language
     * @param parameters   placeholder values (can be null)
     * @return translated text with placeholders replaced
     */
    private String getTranslatedText(String key, String languageCode, Map<String, String> parameters) {
        // Try user's language first
        String text = dictionaryService.getTranslation(key, languageCode)
                .orElse(null);

        // Fallback to English if not found
        if (text == null && !DEFAULT_LANGUAGE.equals(languageCode)) {
            text = dictionaryService.getTranslation(key, DEFAULT_LANGUAGE)
                    .orElse(null);

            if (text != null) {
                log.debug("Translation fallback: key={}, requested={}, fallback={}",
                        key, languageCode, DEFAULT_LANGUAGE);
            }
        }

        // Ultimate fallback: return key itself
        if (text == null) {
            log.warn("Missing translation: key={}, language={}", key, languageCode);
            return key; // At least show the key so developers know what's missing
        }

        // Replace placeholders if parameters provided
        if (parameters != null && !parameters.isEmpty()) {
            text = replacePlaceholders(text, parameters);
        }

        return text;
    }

    /**
     * Replace {placeholder} patterns with actual values.
     *
     * @param template   text with placeholders like {influencerName}
     * @param parameters map of placeholder name → value
     * @return text with placeholders replaced
     */
    private String replacePlaceholders(String template, Map<String, String> parameters) {
        if (template == null || parameters == null || parameters.isEmpty()) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            String replacement = parameters.getOrDefault(placeholderName, "{" + placeholderName + "}");

            // Escape special regex characters in replacement
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    // ========================================================================
    // HELPER: BUILD PARAMETERS MAP
    // ========================================================================

    /**
     * Build parameters map for partnership notifications.
     *
     * @param influencerName  name of the influencer
     * @param companyName     name of the company
     * @param opportunityName name of the opportunity/campaign
     * @return parameters map for translation
     */
    public static Map<String, String> buildPartnershipParams(
            String influencerName,
            String companyName,
            String opportunityName) {

        return Map.of(
                "influencerName", nullSafe(influencerName, "Influencer"),
                "companyName", nullSafe(companyName, "Company"),
                "opportunityName", nullSafe(opportunityName, "Campaign")
        );
    }

    /**
     * Build parameters map for support ticket notifications.
     *
     * @param ticketNumber ticket number/ID
     * @return parameters map for translation
     */
    public static Map<String, String> buildSupportParams(String ticketNumber) {
        return Map.of("ticketNumber", nullSafe(ticketNumber, "N/A"));
    }

    /**
     * Null-safe string helper.
     */
    private static String nullSafe(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
```

---

**Usage Example:**

```java
// In NotificationService
String title = translationService.getTitle(
    NotificationType.APPLICATION_RECEIVED,
    "pl",
    Map.of(
        "influencerName", "Jan Kowalski",
        "opportunityName", "Summer Campaign"
    )
);
// Result: "Nowe zgłoszenie od Jan Kowalski"
```

---

### NotificationService.java

**Purpose:** Core business logic for notifications. Handles creation, retrieval, marking as read, archiving, and preference checking.

```java
package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for managing notifications.
 *
 * Responsibilities:
 * - Create notifications from NotificationRequest
 * - Retrieve user notifications (paginated)
 * - Mark notifications as read (single and bulk)
 * - Archive notifications
 * - Check user preferences for notification eligibility
 *
 * All operations are GDPR-compliant with full audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTranslationService translationService;
    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PermissionUtils permissionUtils;

    // ========================================================================
    // NOTIFICATION CREATION
    // ========================================================================

    /**
     * Create a new notification from a request.
     *
     * This method:
     * 1. Checks if user should receive this notification type (preferences)
     * 2. Fetches user's language for translation
     * 3. Translates title and message with placeholder replacement
     * 4. Determines if email should be sent (preferences + notification type)
     * 5. Saves notification to database
     *
     * @param request the notification creation request
     * @return created notification, or null if user has disabled this category
     */
    @Transactional
    public Notification createNotification(NotificationRequest request) {
        // Validate required fields
        if (request.getUserId() == null) {
            log.error("Cannot create notification: userId is null");
            throw new ValidationTranslatableException("error.validation.required_field", "userId");
        }
        if (request.getType() == null) {
            log.error("Cannot create notification: type is null");
            throw new ValidationTranslatableException("error.validation.required_field", "type");
        }

        Long userId = request.getUserId();
        NotificationType type = request.getType();

        // Get user with preferences
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Cannot create notification: user not found, userId={}", userId);
                    return new ResourceNotFoundException("error.business.item_not_found", "User");
                });

        // Check if user wants this notification category
        if (!shouldNotify(userId, type)) {
            log.info("GDPR: Notification suppressed, userId={}, type={}, reason=user_preference",
                    userId, type);
            return null;
        }

        // Get user's language preference
        String languageCode = getUserLanguage(user);

        // Build translation parameters
        Map<String, String> params = request.getParameters() != null
                ? request.getParameters()
                : Map.of();

        // Translate content
        String title = translationService.getTitle(type, languageCode, params);
        String message = translationService.getMessage(type, languageCode, params);
        String actionLabel = translationService.getActionLabel(type, languageCode);

        // Determine email eligibility
        boolean emailEnabled = shouldSendEmail(userId, type);

        // Build notification entity
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .category(type.getCategory())
                .priority(type.getPriority())
                .title(title)
                .message(message)
                .actionUrl(request.getActionUrl())
                .actionLabel(actionLabel)
                .translationKey(type.getTranslationKeyPrefix())
                .languageCode(languageCode)
                .snapshot(request.getSnapshot())
                .appliedOpportunityId(request.getAppliedOpportunityId())
                .partnershipOpportunityId(request.getPartnershipOpportunityId())
                .influencerId(request.getInfluencerId())
                .companyId(request.getCompanyId())
                .supportTicketId(request.getSupportTicketId())
                .groupKey(request.getGroupKey())
                .workflowStep(request.getWorkflowStep())
                .emailEnabled(emailEnabled)
                .build();

        Notification saved = notificationRepository.save(notification);

        log.info("GDPR: Operation=createNotification, UserId={}, NotificationId={}, Type={}, " +
                        "Category={}, EmailEnabled={}, Purpose=user_notification",
                userId, saved.getId(), type, type.getCategory(), emailEnabled);

        return saved;
    }

    // ========================================================================
    // NOTIFICATION RETRIEVAL
    // ========================================================================

    /**
     * Get paginated notifications for a user.
     * Excludes archived notifications.
     *
     * @param userId   the user's ID
     * @param pageable pagination parameters
     * @return page of notifications
     */
    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        String accessorUid = permissionUtils.getUserId();

        log.info("GDPR: Operation=getUserNotifications, AccessorFirebaseUID={}, TargetUserId={}, " +
                        "Page={}, Size={}, Purpose=notification_retrieval",
                accessorUid, userId, pageable.getPageNumber(), pageable.getPageSize());

        return notificationRepository.findByUserIdAndNotArchived(userId, pageable);
    }

    /**
     * Get unread notification count for a user.
     * Used for bell icon badge.
     *
     * @param userId the user's ID
     * @return count of unread notifications
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Get a single notification by ID.
     * Validates that the notification belongs to the specified user.
     *
     * @param notificationId notification ID
     * @param userId         user ID (for security)
     * @return the notification
     * @throws ResourceNotFoundException if not found
     * @throws InsufficientPermissionsException if notification doesn't belong to user
     */
    @Transactional(readOnly = true)
    public Notification getNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.business.item_not_found", "Notification"));

        // Verify ownership (admin bypass or owner check)
        validateNotificationAccess(notification, userId);

        return notification;
    }

    /**
     * Validates that the current user can access the given notification.
     * Admins can access any notification; regular users can only access their own.
     *
     * @param notification the notification to check
     * @param userId the user ID requesting access
     * @throws InsufficientPermissionsException if access is denied
     */
    private void validateNotificationAccess(Notification notification, Long userId) {
        // Admins can access any notification
        if (permissionUtils.isAdmin()) {
            return;
        }

        // Regular users can only access their own notifications
        if (!notification.getUser().getId().equals(userId)) {
            log.warn("GDPR: AccessDenied Operation=validateNotificationAccess, AttemptedUserId={}, NotificationOwnerId={}, NotificationId={}, Reason=insufficient_permissions",
                    userId, notification.getUser().getId(), notification.getId());

            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "accessNotification",
                    "Notification");
        }
    }

    /**
     * Get all notifications for a specific collaboration/workflow.
     *
     * @param userId   user ID
     * @param groupKey group key (e.g., "collab:123")
     * @return list of notifications in chronological order
     */
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByGroup(Long userId, String groupKey) {
        return notificationRepository.findByUserIdAndGroupKeyOrderByCreatedAtDesc(userId, groupKey);
    }

    // ========================================================================
    // MARK AS READ
    // ========================================================================

    /**
     * Mark a single notification as read.
     *
     * @param notificationId notification ID
     * @param userId         user ID (for security)
     * @return updated notification
     */
    @Transactional
    public Notification markAsRead(Long notificationId, Long userId) {
        Notification notification = getNotification(notificationId, userId);

        if (!notification.getIsRead()) {
            notification.markAsRead();
            notification = notificationRepository.save(notification);

            log.info("GDPR: Operation=markAsRead, UserId={}, NotificationId={}, Purpose=read_status_update",
                    userId, notificationId);
        }

        return notification;
    }

    /**
     * Mark all unread notifications as read for a user.
     *
     * @param userId user ID
     * @return number of notifications marked as read
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int count = notificationRepository.markAllAsReadByUserId(userId, now);

        log.info("GDPR: Operation=markAllAsRead, UserId={}, Count={}, Purpose=bulk_read_status_update",
                userId, count);

        return count;
    }

    // ========================================================================
    // ARCHIVE
    // ========================================================================

    /**
     * Archive a notification (hide from main list).
     *
     * @param notificationId notification ID
     * @param userId         user ID (for security)
     * @return updated notification
     */
    @Transactional
    public Notification archiveNotification(Long notificationId, Long userId) {
        Notification notification = getNotification(notificationId, userId);

        if (!notification.getIsArchived()) {
            notification.archive();
            notification = notificationRepository.save(notification);

            log.info("GDPR: Operation=archiveNotification, UserId={}, NotificationId={}, Purpose=notification_archive",
                    userId, notificationId);
        }

        return notification;
    }

    // ========================================================================
    // EMAIL QUEUE (for EmailCronJob)
    // ========================================================================

    /**
     * Get notifications pending email delivery.
     * Used by EmailCronJob.
     *
     * @param batchSize maximum notifications to fetch
     * @return list of notifications needing email
     */
    @Transactional(readOnly = true)
    public List<Notification> findPendingEmails(int batchSize) {
        return notificationRepository.findPendingEmails(PageRequest.of(0, batchSize));
    }

    /**
     * Record successful email send.
     *
     * @param notification the notification
     */
    @Transactional
    public void markEmailSent(Notification notification) {
        notification.markEmailSent();
        notificationRepository.save(notification);

        log.info("GDPR: Operation=markEmailSent, UserId={}, NotificationId={}, Purpose=email_delivery_confirmation",
                notification.getUser().getId(), notification.getId());
    }

    /**
     * Record failed email send attempt.
     *
     * @param notification the notification
     * @param error        error message
     */
    @Transactional
    public void recordEmailFailure(Notification notification, String error) {
        notification.recordEmailFailure(error);
        notificationRepository.save(notification);

        log.warn("GDPR: Operation=recordEmailFailure, UserId={}, NotificationId={}, " +
                        "RetryCount={}, Error={}, Purpose=email_delivery_failure",
                notification.getUser().getId(), notification.getId(),
                notification.getEmailRetryCount(), error);
    }

    // ========================================================================
    // PREFERENCE CHECKING
    // ========================================================================

    /**
     * Check if user should receive a notification of this type.
     *
     * Decision tree:
     * 1. If notification category is SYSTEM → always true (system notifications cannot be disabled)
     * 2. Check user's category-specific preference
     *
     * @param userId user ID
     * @param type   notification type
     * @return true if notification should be created
     */
    public boolean shouldNotify(Long userId, NotificationType type) {
        // System notifications are always shown
        if (type.getCategory() == NotificationCategory.SYSTEM) {
            return true;
        }

        // Get user preferences
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        UserPreferences prefs = userPreferencesRepository.findByUser(user);
        if (prefs == null) {
            // No preferences set = use defaults (show all)
            return true;
        }

        // Check category-specific preferences
        // Note: If granular category preferences are added to UserPreferences,
        // check them here. For MVP, we show all in-app notifications.
        return true;
    }

    /**
     * Check if email should be sent for this notification.
     *
     * Decision tree:
     * 1. If user has email notifications disabled globally → false
     * 2. If notification type emailDefault is DISABLED → false
     * 3. If notification type emailDefault is ALWAYS → true
     * 4. Otherwise → true (ENABLED or DIGEST)
     *
     * @param userId user ID
     * @param type   notification type
     * @return true if email should be sent
     */
    public boolean shouldSendEmail(Long userId, NotificationType type) {
        // Get user preferences
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        UserPreferences prefs = userPreferencesRepository.findByUser(user);
        if (prefs == null) {
            // No preferences = use notification type default
            return type.getEmailDefault() != NotificationType.EmailDefault.DISABLED;
        }

        // Check global email preference
        if (prefs.getNotificationEmailEnabled() == null || !prefs.getNotificationEmailEnabled()) {
            // User has disabled all email notifications
            return false;
        }

        // Check notification type default
        return switch (type.getEmailDefault()) {
            case DISABLED -> false;
            case ALWAYS -> true;  // Critical notifications always send email
            case ENABLED, DIGEST -> true;  // Note: DIGEST sends immediately for MVP
        };
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Get user's preferred language.
     *
     * @param user the user
     * @return language code (default: "en")
     */
    private String getUserLanguage(User user) {
        UserPreferences prefs = userPreferencesRepository.findByUser(user);
        if (prefs != null && prefs.getLanguage() != null && !prefs.getLanguage().isBlank()) {
            return prefs.getLanguage();
        }
        return "en";
    }
}
```

---

## Domain Events

**What is a Domain Event?**
A domain event represents something that happened in the business domain. In our case, when an opportunity status changes, we publish an event that the notification system listens to.

**Why Spring Event Bus (not @Async)?**
- Runs synchronously within the same thread
- Uses `@TransactionalEventListener(phase = AFTER_COMMIT)` → notification creation only happens AFTER the business transaction commits
- If notification creation fails, the status change is NOT rolled back (business logic protected)

---

### OpportunityStatusChangedEvent.java

**Purpose:** Domain event published when an opportunity status changes. Contains all context needed to create appropriate notifications.

**Location:** `src/main/java/com/sm/instagram/platform/notification/event/OpportunityStatusChangedEvent.java`

```java
package com.sm.instagram.platform.notification.event;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Domain event published when an AppliedOpportunity status changes.
 *
 * Published by: AppliedOpportunityService
 * Handled by: NotificationEventListener
 *
 * This event carries all the context needed to:
 * 1. Determine which notification type to create
 * 2. Identify the recipient(s)
 * 3. Build the notification snapshot
 * 4. Create translation parameters
 *
 * IMPORTANT: This event is published BEFORE the transaction commits.
 * The listener uses @TransactionalEventListener(phase = AFTER_COMMIT) to ensure
 * notification creation only happens after the business operation succeeds.
 */
@Getter
public class OpportunityStatusChangedEvent extends ApplicationEvent {

    /**
     * The applied opportunity that changed.
     * Contains relationships to: influencer, partnershipOpportunity, company.
     */
    private final AppliedOpportunity appliedOpportunity;

    /**
     * Status before the change.
     * Can be null for initial APPLIED status.
     */
    private final OpportunityStatus previousStatus;

    /**
     * Status after the change.
     * Never null.
     */
    private final OpportunityStatus newStatus;

    /**
     * User who triggered the status change.
     * This is the "actor" for the notification.
     * Can be null for system-triggered changes.
     */
    private final User triggeredBy;

    /**
     * Timestamp when the event occurred.
     */
    private final LocalDateTime occurredAt;

    /**
     * Optional note/reason for the status change.
     * Example: rejection reason for CONTENT_REJECTED.
     */
    private final String note;

    /**
     * Create a new status change event.
     *
     * @param source            the object that published this event (usually the service)
     * @param appliedOpportunity the opportunity that changed
     * @param previousStatus    status before the change (null for initial)
     * @param newStatus         status after the change
     * @param triggeredBy       user who made the change
     * @param note              optional note/reason
     */
    public OpportunityStatusChangedEvent(
            Object source,
            AppliedOpportunity appliedOpportunity,
            OpportunityStatus previousStatus,
            OpportunityStatus newStatus,
            User triggeredBy,
            String note) {

        super(source);
        this.appliedOpportunity = appliedOpportunity;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.triggeredBy = triggeredBy;
        this.note = note;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * Create event without note.
     */
    public OpportunityStatusChangedEvent(
            Object source,
            AppliedOpportunity appliedOpportunity,
            OpportunityStatus previousStatus,
            OpportunityStatus newStatus,
            User triggeredBy) {

        this(source, appliedOpportunity, previousStatus, newStatus, triggeredBy, null);
    }

    // ========================================================================
    // CONVENIENCE GETTERS
    // ========================================================================

    /**
     * Get the influencer involved in this opportunity.
     */
    public User getInfluencer() {
        return appliedOpportunity.getInfluencer();
    }

    /**
     * Get the company involved in this opportunity.
     */
    public User getCompany() {
        var partnership = appliedOpportunity.getPartnershipOpportunity();
        return partnership != null ? partnership.getCompany() : null;
    }

    /**
     * Get the campaign/opportunity title.
     */
    public String getCampaignTitle() {
        var partnership = appliedOpportunity.getPartnershipOpportunity();
        return partnership != null ? partnership.getTitle() : null;
    }

    /**
     * Check if this is a terminal status (collaboration ended).
     */
    public boolean isTerminalStatus() {
        return newStatus.isTerminalStatus();
    }

    /**
     * Check if this is a successful completion.
     */
    public boolean isSuccessfulCompletion() {
        return newStatus.isSuccessfulCompletion();
    }

    @Override
    public String toString() {
        return String.format(
                "OpportunityStatusChangedEvent{appliedOpportunityId=%d, %s→%s, triggeredBy=%s}",
                appliedOpportunity.getId(),
                previousStatus,
                newStatus,
                triggeredBy != null ? triggeredBy.getId() : "SYSTEM"
        );
    }
}
```

---

**How to Publish This Event:**

Add to `AppliedOpportunityService.java` (shown in "Modifications to Existing Files" section):

```java
// In AppliedOpportunityService, inject ApplicationEventPublisher:
private final ApplicationEventPublisher eventPublisher;

// After saving the status change, publish the event:
@Transactional
public AppliedOpportunity updateOpportunityStatus(Long id, OpportunityStatus newStatus, String note) {
    AppliedOpportunity opportunity = findById(id);
    OpportunityStatus previousStatus = opportunity.getOpportunityStatus();

    // Update status
    opportunity.setOpportunityStatus(newStatus);
    AppliedOpportunity saved = repository.save(opportunity);

    // Publish event (will be handled AFTER_COMMIT by NotificationEventListener)
    eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
            this,
            saved,
            previousStatus,
            newStatus,
            getCurrentUser(),
            note
    ));

    return saved;
}
```

---

### NotificationEventListener.java

**Purpose:** Listens to domain events and creates appropriate notifications. Contains the complete mapping from OpportunityStatus → NotificationType → Recipient.

**Location:** `src/main/java/com/sm/instagram/platform/notification/event/NotificationEventListener.java`

**Key Design Decisions:**
1. Uses `@TransactionalEventListener(phase = AFTER_COMMIT)` → Notifications only created after business transaction succeeds
2. If notification creation fails, it does NOT rollback the status change
3. Each status maps to exactly one notification type and one recipient (except DONE which notifies both parties)

**Status → NotificationType → Recipient Mapping (Complete Table):**

| OpportunityStatus | NotificationType | Recipient | Reason |
|-------------------|------------------|-----------|--------|
| APPLIED | APPLICATION_RECEIVED | Company | Company needs to review |
| ACCEPTED_BY_COMPANY | APPLICATION_ACCEPTED | Influencer | Influencer needs to confirm |
| REJECTED_BY_COMPANY | APPLICATION_REJECTED | Influencer | Influencer should know |
| ACCEPTED_BY_INFLUENCER | OFFER_ACCEPTED | Company | Company should know influencer confirmed |
| REJECTED_BY_INFLUENCER | OFFER_REJECTED | Company | Company should know influencer declined |
| CONTENT_SEND_TO_ACCEPT | CONTENT_SUBMITTED | Company | Company needs to review content |
| CONTENT_APPROVED | CONTENT_APPROVED | Influencer | Influencer can post now |
| CONTENT_REJECTED | CONTENT_REJECTED | Influencer | Influencer needs to revise |
| CONTENT_POSTED | CONTENT_POSTED | Company | Company needs to verify |
| CONTENT_POSTED_REJECTED | POST_REJECTED | Influencer | Influencer needs to fix |
| TO_BE_PAID | POST_VERIFIED | Influencer | Influencer knows payment coming |
| DONE | COLLABORATION_COMPLETE | Both | Both parties get completion notification |

```java
package com.sm.instagram.platform.notification.event;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.notification.*;
import com.sm.instagram.platform.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Event listener for domain events that trigger notifications.
 *
 * CRITICAL DESIGN NOTES:
 * 1. Uses TransactionPhase.AFTER_COMMIT - notification creation only happens
 *    AFTER the business transaction commits successfully.
 * 2. If notification creation fails, the business operation is NOT rolled back.
 * 3. Each handler method logs extensively for debugging and monitoring.
 *
 * Pattern used by: Shopify, Stripe, GitHub for decoupled notification systems.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    // ========================================================================
    // OPPORTUNITY STATUS CHANGE HANDLER
    // ========================================================================

    /**
     * Handle opportunity status changes by creating appropriate notifications.
     *
     * This runs AFTER the business transaction commits, so:
     * - The status change is already persisted
     * - If this fails, the status change is NOT rolled back
     * - Errors are logged but don't affect the user's operation
     *
     * @param event the status change event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOpportunityStatusChanged(OpportunityStatusChangedEvent event) {
        OpportunityStatus newStatus = event.getNewStatus();
        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();

        log.info("Processing notification for status change: appliedOpportunityId={}, " +
                        "previousStatus={}, newStatus={}, triggeredBy={}",
                appliedOpp.getId(),
                event.getPreviousStatus(),
                newStatus,
                event.getTriggeredBy() != null ? event.getTriggeredBy().getId() : "SYSTEM");

        try {
            // Dispatch to appropriate handler based on new status
            switch (newStatus) {
                case APPLIED -> handleApplied(event);
                case ACCEPTED_BY_COMPANY -> handleAcceptedByCompany(event);
                case REJECTED_BY_COMPANY -> handleRejectedByCompany(event);
                case ACCEPTED_BY_INFLUENCER -> handleAcceptedByInfluencer(event);
                case REJECTED_BY_INFLUENCER -> handleRejectedByInfluencer(event);
                case CONTENT_SEND_TO_ACCEPT -> handleContentSubmitted(event);
                case CONTENT_APPROVED -> handleContentApproved(event);
                case CONTENT_REJECTED -> handleContentRejected(event);
                case CONTENT_POSTED -> handleContentPosted(event);
                case CONTENT_POSTED_REJECTED -> handlePostRejected(event);
                case TO_BE_PAID -> handlePostVerified(event);
                case DONE -> handleCollaborationComplete(event);
                default -> log.debug("No notification handler for status transition: {} -> {}",
                        event.getPreviousStatus(), newStatus);
            }
        } catch (Exception e) {
            // Log error but DON'T re-throw - business operation already succeeded
            log.error("Failed to create notification for status change: " +
                            "appliedOpportunityId={}, newStatus={}, error={}",
                    appliedOpp.getId(), newStatus, e.getMessage(), e);
        }
    }

    // ========================================================================
    // STATUS HANDLERS - Each creates notification for appropriate recipient
    // ========================================================================

    /**
     * APPLIED: Influencer applied → Notify Company
     *
     * Recipient: Company (needs to review application)
     * Priority: MEDIUM (routine business event)
     */
    private void handleApplied(OpportunityStatusChangedEvent event) {
        User company = event.getCompany();
        if (company == null) {
            log.warn("Cannot notify company for APPLIED: no company found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        User influencer = event.getInfluencer();
        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();

        NotificationRequest request = NotificationRequest.forPartnership(
                company.getId(),
                NotificationType.APPLICATION_RECEIVED,
                appliedOpp,
                influencer,
                buildTranslationParams(event)
        );

        notificationService.createNotification(request);

        log.debug("Created APPLICATION_RECEIVED notification: recipientId={}, appliedOpportunityId={}",
                company.getId(), appliedOpp.getId());
    }

    /**
     * ACCEPTED_BY_COMPANY: Company accepted application → Notify Influencer
     *
     * Recipient: Influencer (needs to confirm participation)
     * Priority: HIGH (time-sensitive action needed)
     */
    private void handleAcceptedByCompany(OpportunityStatusChangedEvent event) {
        User influencer = event.getInfluencer();
        if (influencer == null) {
            log.warn("Cannot notify influencer for ACCEPTED_BY_COMPANY: no influencer found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy(); // Company representative

        NotificationRequest request = NotificationRequest.forPartnership(
                influencer.getId(),
                NotificationType.APPLICATION_ACCEPTED,
                appliedOpp,
                triggeredBy,
                buildTranslationParams(event)
        );

        notificationService.createNotification(request);

        log.debug("Created APPLICATION_ACCEPTED notification: recipientId={}, appliedOpportunityId={}",
                influencer.getId(), appliedOpp.getId());
    }

    /**
     * REJECTED_BY_COMPANY: Company rejected application → Notify Influencer
     *
     * Recipient: Influencer (should know application was declined)
     * Priority: MEDIUM (informational, no action needed)
     */
    private void handleRejectedByCompany(OpportunityStatusChangedEvent event) {
        User influencer = event.getInfluencer();
        if (influencer == null) {
            log.warn("Cannot notify influencer for REJECTED_BY_COMPANY: no influencer found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy();

        // Add rejection reason to parameters if provided
        Map<String, String> params = buildTranslationParams(event);
        if (event.getNote() != null && !event.getNote().isBlank()) {
            params = new java.util.HashMap<>(params);
            params.put("rejectionReason", event.getNote());
        }

        NotificationRequest request = NotificationRequest.forPartnership(
                influencer.getId(),
                NotificationType.APPLICATION_REJECTED,
                appliedOpp,
                triggeredBy,
                params
        );

        notificationService.createNotification(request);

        log.debug("Created APPLICATION_REJECTED notification: recipientId={}, appliedOpportunityId={}",
                influencer.getId(), appliedOpp.getId());
    }

    /**
     * ACCEPTED_BY_INFLUENCER: Influencer confirmed participation → Notify Company
     *
     * Recipient: Company (knows influencer is committed)
     * Priority: HIGH (workflow can proceed)
     */
    private void handleAcceptedByInfluencer(OpportunityStatusChangedEvent event) {
        User company = event.getCompany();
        if (company == null) {
            log.warn("Cannot notify company for ACCEPTED_BY_INFLUENCER: no company found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy(); // Influencer

        NotificationRequest request = NotificationRequest.forPartnership(
                company.getId(),
                NotificationType.OFFER_ACCEPTED,
                appliedOpp,
                triggeredBy,
                buildTranslationParams(event)
        );

        notificationService.createNotification(request);

        log.debug("Created OFFER_ACCEPTED notification: recipientId={}, appliedOpportunityId={}",
                company.getId(), appliedOpp.getId());
    }

    /**
     * REJECTED_BY_INFLUENCER: Influencer declined participation → Notify Company
     *
     * Recipient: Company (should know influencer declined)
     * Priority: MEDIUM (informational, collaboration ended)
     */
    private void handleRejectedByInfluencer(OpportunityStatusChangedEvent event) {
        User company = event.getCompany();
        if (company == null) {
            log.warn("Cannot notify company for REJECTED_BY_INFLUENCER: no company found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy();

        NotificationRequest request = NotificationRequest.forPartnership(
                company.getId(),
                NotificationType.OFFER_REJECTED,
                appliedOpp,
                triggeredBy,
                buildTranslationParams(event)
        );

        notificationService.createNotification(request);

        log.debug("Created OFFER_REJECTED notification: recipientId={}, appliedOpportunityId={}",
                company.getId(), appliedOpp.getId());
    }

    /**
     * CONTENT_SEND_TO_ACCEPT: Influencer submitted content → Notify Company
     *
     * Recipient: Company (needs to review and approve content)
     * Priority: HIGH (time-sensitive review needed)
     */
    private void handleContentSubmitted(OpportunityStatusChangedEvent event) {
        User company = event.getCompany();
        if (company == null) {
            log.warn("Cannot notify company for CONTENT_SEND_TO_ACCEPT: no company found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy(); // Influencer

        NotificationRequest request = NotificationRequest.forPartnership(
                company.getId(),
                NotificationType.CONTENT_SUBMITTED,
                appliedOpp,
                triggeredBy,
                buildTranslationParams(event)
        );

        notificationService.createNotification(request);

        log.debug("Created CONTENT_SUBMITTED notification: recipientId={}, appliedOpportunityId={}",
                company.getId(), appliedOpp.getId());
    }

    /**
     * CONTENT_APPROVED: Company approved content → Notify Influencer
     *
     * Recipient: Influencer (can now post the content)
     * Priority: HIGH (action needed - post content)
     */
    private void handleContentApproved(OpportunityStatusChangedEvent event) {
        User influencer = event.getInfluencer();
        if (influencer == null) {
            log.warn("Cannot notify influencer for CONTENT_APPROVED: no influencer found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy(); // Company

        NotificationRequest request = NotificationRequest.forPartnership(
                influencer.getId(),
                NotificationType.CONTENT_APPROVED,
                appliedOpp,
                triggeredBy,
                buildTranslationParams(event)
        );

        notificationService.createNotification(request);

        log.debug("Created CONTENT_APPROVED notification: recipientId={}, appliedOpportunityId={}",
                influencer.getId(), appliedOpp.getId());
    }

    /**
     * CONTENT_REJECTED: Company rejected content → Notify Influencer
     *
     * Recipient: Influencer (needs to revise and resubmit)
     * Priority: HIGH (action needed - revise content)
     */
    private void handleContentRejected(OpportunityStatusChangedEvent event) {
        User influencer = event.getInfluencer();
        if (influencer == null) {
            log.warn("Cannot notify influencer for CONTENT_REJECTED: no influencer found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy();

        // Add rejection reason to parameters if provided
        Map<String, String> params = buildTranslationParams(event);
        if (event.getNote() != null && !event.getNote().isBlank()) {
            params = new java.util.HashMap<>(params);
            params.put("rejectionReason", event.getNote());
        }

        NotificationRequest request = NotificationRequest.forPartnership(
                influencer.getId(),
                NotificationType.CONTENT_REJECTED,
                appliedOpp,
                triggeredBy,
                params
        );

        notificationService.createNotification(request);

        log.debug("Created CONTENT_REJECTED notification: recipientId={}, appliedOpportunityId={}",
                influencer.getId(), appliedOpp.getId());
    }

    /**
     * CONTENT_POSTED: Influencer posted content → Notify Company
     *
     * Recipient: Company (needs to verify posted content)
     * Priority: MEDIUM (verification needed but not urgent)
     */
    private void handleContentPosted(OpportunityStatusChangedEvent event) {
        User company = event.getCompany();
        if (company == null) {
            log.warn("Cannot notify company for CONTENT_POSTED: no company found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy(); // Influencer

        NotificationRequest request = NotificationRequest.forPartnership(
                company.getId(),
                NotificationType.CONTENT_POSTED,
                appliedOpp,
                triggeredBy,
                buildTranslationParams(event)
        );

        notificationService.createNotification(request);

        log.debug("Created CONTENT_POSTED notification: recipientId={}, appliedOpportunityId={}",
                company.getId(), appliedOpp.getId());
    }

    /**
     * CONTENT_POSTED_REJECTED: Company rejected posted content → Notify Influencer
     *
     * Recipient: Influencer (needs to fix the post)
     * Priority: HIGH (action needed - correct the post)
     */
    private void handlePostRejected(OpportunityStatusChangedEvent event) {
        User influencer = event.getInfluencer();
        if (influencer == null) {
            log.warn("Cannot notify influencer for CONTENT_POSTED_REJECTED: no influencer found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy();

        // Add rejection reason to parameters if provided
        Map<String, String> params = buildTranslationParams(event);
        if (event.getNote() != null && !event.getNote().isBlank()) {
            params = new java.util.HashMap<>(params);
            params.put("rejectionReason", event.getNote());
        }

        NotificationRequest request = NotificationRequest.forPartnership(
                influencer.getId(),
                NotificationType.POST_REJECTED,
                appliedOpp,
                triggeredBy,
                params
        );

        notificationService.createNotification(request);

        log.debug("Created POST_REJECTED notification: recipientId={}, appliedOpportunityId={}",
                influencer.getId(), appliedOpp.getId());
    }

    /**
     * TO_BE_PAID: Company verified post → Notify Influencer
     *
     * Recipient: Influencer (payment is coming)
     * Priority: MEDIUM (good news, no action needed)
     */
    private void handlePostVerified(OpportunityStatusChangedEvent event) {
        User influencer = event.getInfluencer();
        if (influencer == null) {
            log.warn("Cannot notify influencer for TO_BE_PAID: no influencer found, " +
                    "appliedOpportunityId={}", event.getAppliedOpportunity().getId());
            return;
        }

        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy(); // Company

        NotificationRequest request = NotificationRequest.forPartnership(
                influencer.getId(),
                NotificationType.POST_VERIFIED,
                appliedOpp,
                triggeredBy,
                buildTranslationParams(event)
        );

        notificationService.createNotification(request);

        log.debug("Created POST_VERIFIED notification: recipientId={}, appliedOpportunityId={}",
                influencer.getId(), appliedOpp.getId());
    }

    /**
     * DONE: Collaboration completed → Notify BOTH parties
     *
     * Recipients: Both Influencer and Company (celebration!)
     * Priority: LOW (informational, no action needed)
     */
    private void handleCollaborationComplete(OpportunityStatusChangedEvent event) {
        AppliedOpportunity appliedOpp = event.getAppliedOpportunity();
        User triggeredBy = event.getTriggeredBy();
        Map<String, String> params = buildTranslationParams(event);

        // Notify Influencer
        User influencer = event.getInfluencer();
        if (influencer != null) {
            NotificationRequest influencerRequest = NotificationRequest.forPartnership(
                    influencer.getId(),
                    NotificationType.COLLABORATION_COMPLETE,
                    appliedOpp,
                    triggeredBy,
                    params
            );
            notificationService.createNotification(influencerRequest);

            log.debug("Created COLLABORATION_COMPLETE notification for influencer: recipientId={}, " +
                    "appliedOpportunityId={}", influencer.getId(), appliedOpp.getId());
        } else {
            log.warn("Cannot notify influencer for DONE: no influencer found, " +
                    "appliedOpportunityId={}", appliedOpp.getId());
        }

        // Notify Company
        User company = event.getCompany();
        if (company != null) {
            NotificationRequest companyRequest = NotificationRequest.forPartnership(
                    company.getId(),
                    NotificationType.COLLABORATION_COMPLETE,
                    appliedOpp,
                    triggeredBy,
                    params
            );
            notificationService.createNotification(companyRequest);

            log.debug("Created COLLABORATION_COMPLETE notification for company: recipientId={}, " +
                    "appliedOpportunityId={}", company.getId(), appliedOpp.getId());
        } else {
            log.warn("Cannot notify company for DONE: no company found, " +
                    "appliedOpportunityId={}", appliedOpp.getId());
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build translation parameters from event context.
     *
     * @param event the status change event
     * @return map of placeholder name → value
     */
    private Map<String, String> buildTranslationParams(OpportunityStatusChangedEvent event) {
        String influencerName = "Influencer";
        String companyName = "Company";
        String opportunityName = "Campaign";

        User influencer = event.getInfluencer();
        if (influencer != null) {
            influencerName = getDisplayName(influencer);
        }

        User company = event.getCompany();
        if (company != null) {
            companyName = getDisplayName(company);
        }

        String campaignTitle = event.getCampaignTitle();
        if (campaignTitle != null && !campaignTitle.isBlank()) {
            opportunityName = campaignTitle;
        }

        return NotificationTranslationService.buildPartnershipParams(
                influencerName,
                companyName,
                opportunityName
        );
    }

    /**
     * Get display name for a user.
     *
     * @param user the user
     * @return display name (username or fallback)
     */
    private String getDisplayName(User user) {
        if (user == null) {
            return "User";
        }

        // Try different name fields
        // Note: User entity has getName(), firstName, lastName - NOT getUsername()
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            String fullName = user.getFirstName();
            if (user.getLastName() != null && !user.getLastName().isBlank()) {
                fullName += " " + user.getLastName();
            }
            return fullName;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            // Use email prefix as fallback
            String email = user.getEmail();
            int atIndex = email.indexOf('@');
            return atIndex > 0 ? email.substring(0, atIndex) : email;
        }
        return "User " + user.getId();
    }
}
```

---

**Key Implementation Details:**

1. **AFTER_COMMIT Phase:** The `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` ensures:
   - Status change is already saved to database
   - If notification fails, status change is NOT rolled back
   - Business operations are never affected by notification failures

2. **Error Handling:** All exceptions are caught and logged - they never propagate to affect the calling code.

3. **Null Safety:** Every handler checks if the recipient exists before attempting to create a notification.

4. **Rejection Notes:** For REJECTED_BY_COMPANY, CONTENT_REJECTED, and POST_REJECTED, the optional `note` field is included in translation parameters.

5. **DONE Status:** This is the only status that notifies BOTH parties (influencer and company).

---

## REST Controller

### NotificationController.java

**Purpose:** REST API endpoints for notification operations. Exposes notification data to the Angular frontend.

**Location:** `src/main/java/com/sm/instagram/platform/notification/NotificationController.java`

**Endpoints:**

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| GET | `/notifications` | Get paginated notifications | Page<NotificationDtoOut> |
| GET | `/notifications/unread/count` | Get unread badge count | Long |
| GET | `/notifications/{id}` | Get single notification | NotificationDtoOut |
| PATCH | `/notifications/{id}/read` | Mark as read | NotificationDtoOut |
| POST | `/notifications/read-all` | Mark all as read | { count: N } |
| DELETE | `/notifications/{id}` | Archive notification | 204 No Content |
| GET | `/notifications/group/{groupKey}` | Get by group key | List<NotificationDtoOut> |

```java
package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for notification operations.
 *
 * All endpoints require authentication and operate on the current user's notifications.
 * Users can only access their own notifications (enforced at service layer).
 *
 * Follows existing controller patterns in the codebase:
 * - Uses @RequiredArgsConstructor for dependency injection
 * - GDPR-compliant logging via service layer
 * - OpenAPI/Swagger documentation
 */
@Slf4j
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/notifications")
@RateLimit(profile = RateLimitProfile.STANDARD)
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification management endpoints")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final PermissionUtils permissionUtils;

    // ========================================================================
    // GET NOTIFICATIONS (Paginated)
    // ========================================================================

    /**
     * Get paginated notifications for the current user.
     *
     * Default: 20 notifications per page, sorted by creation date descending.
     * Frontend uses this for the notification dropdown/list.
     *
     * @param page page number (0-indexed, default 0)
     * @param size page size (default 20, max 100)
     * @return page of notification DTOs
     */
    @GetMapping
    @Operation(
            summary = "Get user notifications",
            description = "Returns paginated notifications for the current user, excluding archived. " +
                    "Sorted by creation date descending (newest first)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated")
    })
    public ResponseEntity<Page<NotificationDtoOut>> getNotifications(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=getNotifications, UserId={}, Page={}, Size={}, Purpose=notification_retrieval",
                userId, page, size);

        // Enforce max page size
        size = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> notifications = notificationService.getUserNotifications(userId, pageable);

        Page<NotificationDtoOut> dtoPage = notifications.map(NotificationDtoOut::fromEntity);

        log.debug("GDPR: Operation=getNotifications, UserId={}, Page={}/{}, Count={}, DataReturned=notifications.list",
                userId, page, dtoPage.getTotalPages(), dtoPage.getNumberOfElements());

        return ResponseEntity.ok(dtoPage);
    }

    // ========================================================================
    // GET UNREAD COUNT (For badge)
    // ========================================================================

    /**
     * Get count of unread notifications for badge display.
     *
     * Frontend polls this endpoint every 30 seconds to update the bell icon badge.
     *
     * @return count of unread notifications
     */
    @GetMapping("/unread/count")
    @Operation(
            summary = "Get unread notification count",
            description = "Returns the count of unread, non-archived notifications. " +
                    "Used for displaying the badge on the notification bell icon."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Long> getUnreadCount() {
        Long userId = getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);

        log.debug("GDPR: Operation=getUnreadCount, UserId={}, Count={}, Purpose=badge_display", userId, count);

        return ResponseEntity.ok(count);
    }

    // ========================================================================
    // GET SINGLE NOTIFICATION
    // ========================================================================

    /**
     * Get a single notification by ID.
     *
     * @param id notification ID
     * @return notification DTO
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get notification by ID",
            description = "Returns a single notification. User can only access their own notifications."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Notification not found or belongs to different user")
    })
    public ResponseEntity<NotificationDtoOut> getNotification(
            @Parameter(description = "Notification ID")
            @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=getNotification, UserId={}, NotificationId={}, Purpose=single_notification_access",
                userId, id);

        Notification notification = notificationService.getNotification(id, userId);

        return ResponseEntity.ok(NotificationDtoOut.fromEntity(notification));
    }

    // ========================================================================
    // MARK AS READ (Single)
    // ========================================================================

    /**
     * Mark a single notification as read.
     *
     * Called when user clicks on a notification in the list.
     *
     * @param id notification ID
     * @return updated notification DTO
     */
    @PatchMapping("/{id}/read")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    @Operation(
            summary = "Mark notification as read",
            description = "Marks a single notification as read. Returns the updated notification."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<NotificationDtoOut> markAsRead(
            @Parameter(description = "Notification ID")
            @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=markAsRead, UserId={}, NotificationId={}, Purpose=mark_notification_read",
                userId, id);

        Notification notification = notificationService.markAsRead(id, userId);

        log.debug("GDPR: Operation=markAsRead, UserId={}, NotificationId={}, Result=success", userId, id);

        return ResponseEntity.ok(NotificationDtoOut.fromEntity(notification));
    }

    // ========================================================================
    // MARK ALL AS READ (Bulk)
    // ========================================================================

    /**
     * Mark all unread notifications as read.
     *
     * Called when user clicks "Mark all as read" button.
     *
     * @return count of notifications marked as read
     */
    @PostMapping("/read-all")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    @Operation(
            summary = "Mark all notifications as read",
            description = "Marks all unread, non-archived notifications as read for the current user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications marked as read"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=markAllAsRead, UserId={}, Purpose=bulk_mark_read", userId);

        int count = notificationService.markAllAsRead(userId);

        log.info("GDPR: Operation=markAllAsRead, UserId={}, Count={}, Result=success", userId, count);

        return ResponseEntity.ok(Map.of("count", count));
    }

    // ========================================================================
    // ARCHIVE NOTIFICATION
    // ========================================================================

    /**
     * Archive (hide) a notification.
     *
     * Archived notifications are excluded from the main list but remain in database.
     * Uses DELETE method but doesn't actually delete - follows "soft delete" pattern.
     *
     * @param id notification ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    @Operation(
            summary = "Archive notification",
            description = "Archives a notification (soft delete). " +
                    "Archived notifications are hidden from the main list but preserved in database."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notification archived"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Void> archiveNotification(
            @Parameter(description = "Notification ID")
            @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=archiveNotification, UserId={}, NotificationId={}, Purpose=archive_notification",
                userId, id);

        notificationService.archiveNotification(id, userId);

        log.debug("GDPR: Operation=archiveNotification, UserId={}, NotificationId={}, Result=success", userId, id);

        return ResponseEntity.noContent().build();
    }

    // ========================================================================
    // GET BY GROUP KEY
    // ========================================================================

    /**
     * Get all notifications for a specific workflow/collaboration.
     *
     * Useful for showing notification history on a collaboration details page.
     *
     * @param groupKey group key (e.g., "collab:123")
     * @return list of notifications for this group
     */
    @GetMapping("/group/{groupKey}")
    @Operation(
            summary = "Get notifications by group",
            description = "Returns all notifications for a specific workflow group (e.g., collaboration). " +
                    "Useful for showing notification history on detail pages."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<NotificationDtoOut>> getByGroupKey(
            @Parameter(description = "Group key (e.g., 'collab:123')")
            @PathVariable String groupKey
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=getByGroupKey, UserId={}, GroupKey={}, Purpose=group_notification_access",
                userId, groupKey);

        List<Notification> notifications = notificationService.getNotificationsByGroup(userId, groupKey);

        List<NotificationDtoOut> dtos = notifications.stream()
                .map(NotificationDtoOut::fromEntity)
                .toList();

        log.debug("GDPR: Operation=getByGroupKey, UserId={}, GroupKey={}, Count={}, DataReturned=notifications.list",
                userId, groupKey, dtos.size());

        return ResponseEntity.ok(dtos);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get the database ID of the current authenticated user.
     *
     * Uses PermissionUtils to get Firebase UID, then looks up the User entity.
     *
     * @return user's database ID
     * @throws ResourceNotFoundException if user not found
     */
    private Long getCurrentUserId() {
        String firebaseUid = permissionUtils.getUserId();
        log.debug("GDPR: Operation=getCurrentUserId, FirebaseUID={}, Purpose=notification_access", firebaseUid);

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> {
                    log.error("GDPR: Operation=getCurrentUserId, FirebaseUID={}, Result=user_not_found", firebaseUid);
                    return new ResourceNotFoundException("error.user.not_found");
                });

        return user.getId();
    }
}
```

---

**API Usage Examples:**

```bash
# Get first page of notifications (20 per page)
GET /notifications

# Get page 2 with 50 items
GET /notifications?page=1&size=50

# Get unread count for badge
GET /notifications/unread/count

# Get single notification
GET /notifications/123

# Mark notification as read
PATCH /notifications/123/read

# Mark all as read
POST /notifications/read-all

# Archive notification (soft delete)
DELETE /notifications/123

# Get all notifications for a collaboration
GET /notifications/group/collab:456
```

---

**Response Format:**

```json
// GET /notifications (paginated)
{
  "content": [
    {
      "id": 123,
      "type": "APPLICATION_RECEIVED",
      "category": "PARTNERSHIP",
      "priority": "MEDIUM",
      "colorTheme": "primary",
      "icon": "user",
      "title": "New Application Received",
      "message": "John has applied to your opportunity: Summer Campaign",
      "actionUrl": "/collaborations/applications/123",
      "actionLabel": "Review Application",
      "isRead": false,
      "readAt": null,
      "snapshot": {
        "influencer": { "id": 1, "name": "John", "avatarUrl": "..." },
        "company": { "id": 2, "name": "Acme Corp", "avatarUrl": "..." },
        "campaign": { "id": 3, "title": "Summer Campaign" }
      },
      "createdAt": "2025-01-03T10:30:00",
      "appliedOpportunityId": 456,
      "groupKey": "collab:456"
    }
  ],
  "pageable": { ... },
  "totalElements": 42,
  "totalPages": 3,
  "size": 20,
  "number": 0
}

// GET /notifications/unread/count
5

// POST /notifications/read-all
{ "count": 5 }
```

---

## Email Infrastructure

**Architecture Decision:** Email notifications use a cron-based queue instead of immediate sending.

**Why Queue Pattern (not immediate)?**
- **Reliability:** Retries on failure (max 3 attempts)
- **Rate Limiting:** Natural throttling via batch size
- **Debugging:** Query shows queue health
- **No Thread Exhaustion:** Fixed batch size (100)

Used by: Netflix (1B+ emails/quarter), Airbnb (10M+ notifications/day)

---

### NotificationEmailService.java

**Purpose:** Sends notification emails via JavaMailSender. Generates plain-text emails (HTML templates deferred to Phase 2).

**Location:** `src/main/java/com/sm/instagram/platform/notification/email/NotificationEmailService.java`

```java
package com.sm.instagram.platform.notification.email;

import com.sm.instagram.platform.notification.Notification;
import com.sm.instagram.platform.notification.NotificationSnapshot;
import com.sm.instagram.platform.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service for sending notification emails.
 *
 * MVP Implementation:
 * - Plain text emails only (HTML templates in Phase 2)
 * - Uses Spring's JavaMailSender
 * - Delegates actual sending to infrastructure layer
 *
 * Pattern: Keep email composition logic separate from queue processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEmailService {

    private final JavaMailSender mailSender;

    /**
     * Sender email address (from application.yml).
     */
    @Value("${spring.mail.from:noreply@checkitout.com}")
    private String fromAddress;

    /**
     * Application base URL for links in emails.
     */
    @Value("${app.base-url:https://checkitout.com}")
    private String baseUrl;

    /**
     * Send notification email.
     *
     * @param notification the notification to send
     * @param recipientEmail recipient's email address
     * @throws MailException if sending fails
     */
    public void sendNotificationEmail(Notification notification, String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Cannot send email: no recipient address for notificationId={}",
                    notification.getId());
            throw new ValidationTranslatableException("error.validation.required_field", "recipientEmail");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipientEmail);
            message.setSubject(buildSubject(notification));
            message.setText(buildBody(notification));

            mailSender.send(message);

            log.info("GDPR: Email sent, NotificationId={}, RecipientMasked={}",
                    notification.getId(),
                    maskEmail(recipientEmail));

        } catch (MailException e) {
            log.error("Failed to send email: notificationId={}, error={}",
                    notification.getId(), e.getMessage());
            throw e;
        }
    }

    // ========================================================================
    // EMAIL CONTENT BUILDERS
    // ========================================================================

    /**
     * Build email subject line.
     *
     * Format: [CheckItOut] {notification title}
     *
     * @param notification the notification
     * @return email subject
     */
    private String buildSubject(Notification notification) {
        return String.format("[CheckItOut] %s", notification.getTitle());
    }

    /**
     * Build plain-text email body.
     *
     * Format:
     * - Greeting
     * - Notification message
     * - Action link
     * - Footer
     *
     * @param notification the notification
     * @return email body text
     */
    private String buildBody(Notification notification) {
        StringBuilder body = new StringBuilder();

        // Greeting
        String recipientName = getRecipientName(notification);
        body.append("Hello").append(recipientName.isEmpty() ? "" : " " + recipientName).append(",\n\n");

        // Main message
        body.append(notification.getMessage()).append("\n\n");

        // Action link
        if (notification.getActionUrl() != null && !notification.getActionUrl().isBlank()) {
            String actionLabel = notification.getActionLabel();
            if (actionLabel == null || actionLabel.isBlank()) {
                actionLabel = "View Details";
            }
            body.append(actionLabel).append(": ")
                    .append(baseUrl).append(notification.getActionUrl())
                    .append("\n\n");
        }

        // Context info (if available)
        addContextInfo(body, notification);

        // Footer
        body.append("---\n");
        body.append("This email was sent by CheckItOut.\n");
        body.append("Manage your notification preferences: ")
                .append(baseUrl).append("/settings/preferences\n");

        return body.toString();
    }

    /**
     * Get recipient name from snapshot for personalization.
     *
     * @param notification the notification
     * @return recipient name or empty string
     */
    private String getRecipientName(Notification notification) {
        NotificationSnapshot snapshot = notification.getSnapshot();
        if (snapshot == null) {
            return "";
        }

        NotificationType type = notification.getType();

        // Determine recipient based on notification type
        // Company notifications (company is recipient)
        if (type == NotificationType.APPLICATION_RECEIVED ||
                type == NotificationType.OFFER_ACCEPTED ||
                type == NotificationType.OFFER_REJECTED ||
                type == NotificationType.CONTENT_SUBMITTED ||
                type == NotificationType.CONTENT_POSTED) {

            if (snapshot.getCompany() != null && snapshot.getCompany().getName() != null) {
                return snapshot.getCompany().getName();
            }
        }

        // Influencer notifications (influencer is recipient)
        if (snapshot.getInfluencer() != null && snapshot.getInfluencer().getName() != null) {
            return snapshot.getInfluencer().getName();
        }

        return "";
    }

    /**
     * Add context info from snapshot to email body.
     *
     * @param body         StringBuilder to append to
     * @param notification the notification
     */
    private void addContextInfo(StringBuilder body, Notification notification) {
        NotificationSnapshot snapshot = notification.getSnapshot();
        if (snapshot == null) {
            return;
        }

        boolean hasContext = false;

        // Campaign info
        if (snapshot.getCampaign() != null && snapshot.getCampaign().getTitle() != null) {
            if (!hasContext) {
                body.append("Details:\n");
                hasContext = true;
            }
            body.append("  Campaign: ").append(snapshot.getCampaign().getTitle()).append("\n");
        }

        // Actor info (who triggered the notification)
        if (snapshot.getTriggeredBy() != null && snapshot.getTriggeredBy().getName() != null) {
            if (!hasContext) {
                body.append("Details:\n");
                hasContext = true;
            }
            body.append("  From: ").append(snapshot.getTriggeredBy().getName()).append("\n");
        }

        if (hasContext) {
            body.append("\n");
        }
    }

    /**
     * Mask email for GDPR-compliant logging.
     *
     * Example: john.doe@example.com → j***@e***.com
     *
     * @param email the email address
     * @return masked email
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }

        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);

        String maskedLocal = local.length() > 0
                ? local.charAt(0) + "***"
                : "***";

        int dotIndex = domain.lastIndexOf('.');
        String maskedDomain = dotIndex > 0
                ? domain.charAt(0) + "***" + domain.substring(dotIndex)
                : "***";

        return maskedLocal + "@" + maskedDomain;
    }
}
```

---

### EmailCronJob.java

**Purpose:** Scheduled job that processes the email queue every 15 minutes.

**Location:** `src/main/java/com/sm/instagram/platform/notification/email/EmailCronJob.java`

```java
package com.sm.instagram.platform.notification.email;

import com.sm.instagram.platform.notification.Notification;
import com.sm.instagram.platform.notification.NotificationService;
import com.sm.instagram.platform.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cron job for processing notification email queue.
 *
 * Runs every 15 minutes to:
 * 1. Fetch pending emails (email_enabled=true, email_sent=false, retry<3)
 * 2. Send each email via NotificationEmailService
 * 3. Update email_sent=true on success
 * 4. Increment retry_count on failure (max 3 attempts)
 *
 * Why 15 minutes?
 * - Frequent enough for timely delivery
 * - Infrequent enough to batch naturally
 * - Aligns with common email patterns (Gmail, LinkedIn)
 *
 * Pattern used by: Airbnb, Netflix, Uber for notification delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailCronJob {

    private final NotificationService notificationService;
    private final NotificationEmailService emailService;

    /**
     * Batch size per cron run (default 100).
     */
    @Value("${notification.email.batch-size:100}")
    private int batchSize;

    /**
     * Enable/disable email cron job (for testing/staging).
     */
    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    /**
     * Process pending notification emails.
     *
     * Schedule: Every 15 minutes (configurable via notification.email.cron)
     *
     * Default cron expression: "0 0/15 * * * *"
     * - Second: 0 (at the start of the minute)
     * - Minute: 0/15 (every 15 minutes starting from 0)
     * - Hour: * (every hour)
     * - Day of Month: * (every day)
     * - Month: * (every month)
     * - Day of Week: * (every day)
     *
     * Transaction Strategy:
     * - No @Transactional on this method (each email processed independently)
     * - Individual email operations are transactional via service methods
     * - Failure of one email does NOT rollback others
     */
    @Scheduled(cron = "${notification.email.cron:0 0/15 * * * *}")
    public void processEmailQueue() {
        if (!emailEnabled) {
            log.debug("Email cron job disabled via configuration");
            return;
        }

        log.info("Starting email queue processing, batchSize={}", batchSize);

        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        try {
            // Fetch pending emails
            List<Notification> pendingEmails = notificationService.findPendingEmails(batchSize);

            log.info("Found {} pending emails to process", pendingEmails.size());

            for (Notification notification : pendingEmails) {
                try {
                    // IDEMPOTENCY CHECK: Skip if already sent (prevents duplicates on retry)
                    if (notification.isEmailSent()) {
                        log.warn("Skipping already-sent notification: {} (idempotency check)", notification.getId());
                        skippedCount++;
                        continue;
                    }

                    // Get recipient email
                    String recipientEmail = getRecipientEmail(notification);

                    if (recipientEmail == null || recipientEmail.isBlank()) {
                        log.warn("Skipping notification {}: no recipient email", notification.getId());
                        skippedCount++;
                        // Mark as failed so we don't retry indefinitely
                        notificationService.recordEmailFailure(notification, "No recipient email address");
                        continue;
                    }

                    // Send email
                    emailService.sendNotificationEmail(notification, recipientEmail);

                    // Mark as sent
                    notificationService.markEmailSent(notification);
                    successCount++;

                } catch (MailException e) {
                    // Record failure for retry
                    notificationService.recordEmailFailure(notification, e.getMessage());
                    failureCount++;

                    log.warn("Failed to send email for notification {}: {} (retry {})",
                            notification.getId(),
                            e.getMessage(),
                            notification.getEmailRetryCount());

                } catch (Exception e) {
                    // Unexpected error - log and continue with next
                    notificationService.recordEmailFailure(notification, e.getMessage());
                    failureCount++;

                    log.error("Unexpected error processing notification {}: {}",
                            notification.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Email queue processing failed: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;

        log.info("Email queue processing complete: success={}, failed={}, skipped={}, duration={}ms",
                successCount, failureCount, skippedCount, duration);
    }

    /**
     * Get recipient email address from notification.
     *
     * @param notification the notification
     * @return email address or null
     */
    private String getRecipientEmail(Notification notification) {
        User user = notification.getUser();
        if (user == null) {
            return null;
        }
        return user.getEmail();
    }
}
```

---

### Configuration

Add to `application.yml`:

```yaml
# ========================================================================
# EMAIL CONFIGURATION (for notification delivery)
# ========================================================================
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME:noreply@checkitout.com}
    password: ${MAIL_PASSWORD:}
    from: ${MAIL_FROM:noreply@checkitout.com}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000

# ========================================================================
# NOTIFICATION SETTINGS
# ========================================================================
notification:
  email:
    # Enable/disable email notifications
    enabled: ${NOTIFICATION_EMAIL_ENABLED:true}
    # Cron schedule for email queue processing (default: every 15 minutes)
    cron: "0 0/15 * * * *"
    # Maximum emails to process per cron run
    batch-size: 100
```

Add to `pom.xml`:

```xml
<!-- Email Support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

---

### Email Queue Health Check

Query to monitor email queue health:

```sql
-- Pending emails (should be processed soon)
SELECT COUNT(*) AS pending,
       MIN(created_at) AS oldest_pending
FROM notifications
WHERE email_enabled = TRUE
  AND email_sent = FALSE
  AND email_retry_count < 3;

-- Failed emails (exhausted retries)
SELECT COUNT(*) AS failed,
       MIN(created_at) AS oldest_failed
FROM notifications
WHERE email_enabled = TRUE
  AND email_sent = FALSE
  AND email_retry_count >= 3;

-- Email delivery rate (last 24 hours)
SELECT
    COUNT(*) FILTER (WHERE email_sent = TRUE) AS sent,
    COUNT(*) FILTER (WHERE email_enabled = TRUE AND email_sent = FALSE AND email_retry_count >= 3) AS failed,
    ROUND(
        COUNT(*) FILTER (WHERE email_sent = TRUE)::DECIMAL /
        NULLIF(COUNT(*) FILTER (WHERE email_enabled = TRUE), 0) * 100, 2
    ) AS success_rate_pct
FROM notifications
WHERE created_at >= NOW() - INTERVAL '24 hours';
```

---

## Modifications to Existing Files

This section documents changes required to existing files to integrate the notification system.

---

### AppliedOpportunityService.java

**Location:** `src/main/java/com/sm/instagram/platform/appliedopportunities/AppliedOpportunityService.java`

**Purpose:** Add event publishing when opportunity status changes.

**Changes Required:**

1. Inject `ApplicationEventPublisher`
2. Publish `OpportunityStatusChangedEvent` after status updates

```java
// ========================================================================
// ADD IMPORT
// ========================================================================
import com.sm.instagram.platform.notification.event.OpportunityStatusChangedEvent;
import org.springframework.context.ApplicationEventPublisher;

// ========================================================================
// ADD FIELD (via constructor injection)
// ========================================================================
@RequiredArgsConstructor
public class AppliedOpportunityService {

    private final AppliedOpportunityRepository repository;
    private final UserService userService;
    // ADD THIS LINE:
    private final ApplicationEventPublisher eventPublisher;

    // ... existing fields ...
}

// ========================================================================
// MODIFY STATUS UPDATE METHODS
// ========================================================================

/**
 * Example modification for acceptApplication() method.
 *
 * BEFORE:
 */
@Transactional
public AppliedOpportunity acceptApplication(Long id, boolean accept) {
    AppliedOpportunity opportunity = findById(id);
    OpportunityStatus previousStatus = opportunity.getOpportunityStatus();
    OpportunityStatus newStatus = OpportunityStatus.getNextStatus(previousStatus, accept);

    opportunity.setOpportunityStatus(newStatus);
    return repository.save(opportunity);
}

/**
 * AFTER (with event publishing):
 */
@Transactional
public AppliedOpportunity acceptApplication(Long id, boolean accept) {
    AppliedOpportunity opportunity = findById(id);
    OpportunityStatus previousStatus = opportunity.getOpportunityStatus();
    OpportunityStatus newStatus = OpportunityStatus.getNextStatus(previousStatus, accept);

    opportunity.setOpportunityStatus(newStatus);
    AppliedOpportunity saved = repository.save(opportunity);

    // Publish event for notification system
    // This runs AFTER the transaction commits (via @TransactionalEventListener)
    eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
            this,
            saved,
            previousStatus,
            newStatus,
            getCurrentUser(),  // User who triggered the action
            null               // Optional note (for rejections)
    ));

    return saved;
}

/**
 * For rejection methods that include a reason/note:
 */
@Transactional
public AppliedOpportunity rejectApplication(Long id, String reason) {
    AppliedOpportunity opportunity = findById(id);
    OpportunityStatus previousStatus = opportunity.getOpportunityStatus();
    OpportunityStatus newStatus = OpportunityStatus.getNextStatus(previousStatus, false);

    opportunity.setOpportunityStatus(newStatus);
    AppliedOpportunity saved = repository.save(opportunity);

    // Include rejection reason in event
    eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
            this,
            saved,
            previousStatus,
            newStatus,
            getCurrentUser(),
            reason  // Passed to notification for "Rejection reason: ..." text
    ));

    return saved;
}

/**
 * Helper to get current authenticated user.
 * May already exist - use existing implementation.
 */
private User getCurrentUser() {
    String firebaseUid = permissionUtils.getUserId();
    log.debug("GDPR: Operation=getCurrentUser, FirebaseUID={}, Purpose=event_publishing", firebaseUid);
    return userRepository.findByFirebaseUserId(firebaseUid)
            .orElseThrow(() -> new ResourceNotFoundException("error.user.not_found"));
}
```

**Pattern to Apply:**

For EVERY method that changes `opportunityStatus`, add event publishing after `repository.save()`:

```java
// Pattern for all status-changing methods:
eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
        this,                   // source
        savedOpportunity,       // the updated entity
        previousStatus,         // status before change
        newStatus,              // status after change
        getCurrentUser(),       // who triggered this
        note                    // optional reason (null if not applicable)
));
```

---

### UserPreferences.java

**Location:** `src/main/java/com/sm/instagram/platform/userpreferences/UserPreferences.java`

**Purpose:** Add granular notification category preferences (optional for MVP, but schema supports it).

**Changes Required:**

```java
// ========================================================================
// ADD FIELDS FOR GRANULAR NOTIFICATION PREFERENCES
// ========================================================================

// After existing notification fields (notificationEmailEnabled, etc.):
// Note: UserPreferences uses @Getter @Setter @NoArgsConstructor @AllArgsConstructor (NO @Builder)
// So we use field initialization instead of @Builder.Default

/**
 * Enable/disable partnership notifications (in-app).
 * Controls: application, content, collaboration notifications.
 */
@Column(name = "notification_partnership_enabled")
private Boolean notificationPartnershipEnabled = true;

/**
 * Enable/disable support notifications (in-app).
 * Controls: ticket updates, admin messages.
 */
@Column(name = "notification_support_enabled")
private Boolean notificationSupportEnabled = true;

/**
 * Enable/disable system notifications (in-app).
 * Cannot actually be disabled - field for consistency.
 */
@Column(name = "notification_system_enabled")
private Boolean notificationSystemEnabled = true;

/**
 * Enable email for partnership category.
 * More granular than global notificationEmailEnabled.
 */
@Column(name = "notification_email_partnership_enabled")
private Boolean notificationEmailPartnershipEnabled = true;

/**
 * Enable email for support category.
 */
@Column(name = "notification_email_support_enabled")
private Boolean notificationEmailSupportEnabled = true;
```

**Note:** These fields are optional for MVP. The current implementation uses the existing `notificationEmailEnabled` field. Add these if you want per-category email control.

---

### UserPreferencesRepository.java

**Location:** `src/main/java/com/sm/instagram/platform/userpreferences/UserPreferencesRepository.java`

**Changes Required (if not already present):**

```java
/**
 * Find preferences by user.
 * Used by NotificationService to check notification preferences.
 */
UserPreferences findByUser(User user);
```

---

### pom.xml

**Location:** `checkitout-backend/pom.xml`

**Changes Required:**

```xml
<!-- Add in <dependencies> section -->

<!-- Email Support for Notifications -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

---

### application.yml

**Location:** `src/main/resources/application.yml`

**Changes Required:**

```yaml
# ============================================================================
# MAIL CONFIGURATION
# ============================================================================
# For Google Workspace SMTP:
# 1. Create App-Specific Password: https://myaccount.google.com/apppasswords
# 2. Set MAIL_USERNAME and MAIL_PASSWORD environment variables
# ============================================================================
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME:noreply@checkitout.com}
    password: ${MAIL_PASSWORD:}
    from: ${MAIL_FROM:CheckItOut <noreply@checkitout.com>}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000

# ============================================================================
# APPLICATION SETTINGS
# ============================================================================
app:
  base-url: ${APP_BASE_URL:https://checkitout.com}

# ============================================================================
# NOTIFICATION SETTINGS
# ============================================================================
notification:
  email:
    # Set to false in dev/staging to disable actual email sending
    enabled: ${NOTIFICATION_EMAIL_ENABLED:true}
    # Number of emails to process per cron run
    batch-size: ${NOTIFICATION_EMAIL_BATCH_SIZE:100}
```

**For Local Development (application-local.yml):**

```yaml
# Disable email sending in local development
notification:
  email:
    enabled: false

# Or use MailHog for local email testing:
spring:
  mail:
    host: localhost
    port: 1025
    username:
    password:
```

---

### SecurityConfig.java (if using Spring Security)

**Location:** `src/main/java/com/sm/instagram/platform/common/security/SecurityConfig.java`

**Changes Required:**

Add notification endpoints to permitted patterns (they require authentication but should be accessible to any authenticated user):

```java
// In SecurityFilterChain configuration, ensure notifications endpoint is accessible:
.requestMatchers("/notifications/**").authenticated()
```

---

## File Structure Summary

After implementation, the notification package structure should be:

```
src/main/java/com/sm/instagram/platform/notification/
├── Notification.java                    # JPA Entity
├── NotificationType.java               # Enum: 18 notification types
├── NotificationCategory.java           # Enum: PARTNERSHIP, ACCOUNT, SUPPORT, SYSTEM
├── NotificationPriority.java           # Enum: LOW, MEDIUM, HIGH, CRITICAL
├── NotificationSnapshot.java           # JSONB embeddable
├── ActorSnapshot.java                  # Embeddable: influencer/company data
├── CampaignSnapshot.java               # Embeddable: campaign/opportunity data
├── NotificationRepository.java         # JPA Repository
├── NotificationService.java            # Core business logic
├── NotificationController.java         # REST API endpoints
├── NotificationDtoOut.java             # Response DTO
├── NotificationRequest.java            # Internal creation DTO
├── NotificationTranslationService.java # i18n handling
├── event/
│   ├── OpportunityStatusChangedEvent.java  # Domain event
│   └── NotificationEventListener.java      # Event handler
└── email/
    ├── NotificationEmailService.java   # Email sending
    └── EmailCronJob.java               # Scheduled processor
```

**Total: 17 new files**

---

## Testing Checklist

After implementation, verify:

- [ ] Notification created when opportunity status changes
- [ ] Correct recipient receives notification (influencer vs company)
- [ ] Translation works for EN and PL
- [ ] Snapshot contains frozen actor/campaign data
- [ ] GET /notifications returns paginated results
- [ ] GET /notifications/unread/count returns correct count
- [ ] PATCH /notifications/{id}/read marks as read
- [ ] POST /notifications/read-all marks all as read
- [ ] DELETE /notifications/{id} archives (doesn't delete)
- [ ] Email queue processes every 15 minutes
- [ ] Failed emails retry up to 3 times
- [ ] User preferences respected for email sending

---

*Document Version: 3.0*
*Last Updated: January 2025*
*Status: Ready for Implementation*
