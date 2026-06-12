package com.sm.instagram.platform.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
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
    private Instant snapshotTime;

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
                .snapshotTime(Instant.now())
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
                .snapshotTime(Instant.now())
                .build();
    }
}