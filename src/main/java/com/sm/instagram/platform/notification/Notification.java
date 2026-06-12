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
 * <p>
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