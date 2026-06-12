package com.sm.instagram.platform.notification.event;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Domain event published when an AppliedOpportunity status changes.
 * <p>
 * Published by: AppliedOpportunityService
 * Handled by: NotificationEventListener
 * <p>
 * This event carries all the context needed to:
 * 1. Determine which notification type to create
 * 2. Identify the recipient(s)
 * 3. Build the notification snapshot
 * 4. Create translation parameters
 * <p>
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
     * @param source             the object that published this event (usually the service)
     * @param appliedOpportunity the opportunity that changed
     * @param previousStatus     status before the change (null for initial)
     * @param newStatus          status after the change
     * @param triggeredBy        user who made the change
     * @param note               optional note/reason
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