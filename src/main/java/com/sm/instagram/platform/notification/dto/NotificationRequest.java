package com.sm.instagram.platform.notification.dto;

import com.sm.instagram.platform.notification.NotificationSnapshot;
import com.sm.instagram.platform.notification.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Request object for creating a notification.
 * Used internally by NotificationService and event listeners.
 * <p>
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
     * <p>
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
     * Create request for account notification (activation, suspension, ban).
     * No snapshot or partnership context needed — message is self-contained from dictionary.
     */
    public static NotificationRequest forAccount(Long recipientUserId, NotificationType type) {
        return NotificationRequest.builder()
                .userId(recipientUserId)
                .type(type)
                .actionUrl("/user/settings")
                .groupKey("account:" + recipientUserId)
                .build();
    }

    /**
     * Create request for admin notification (new user registered, account activated).
     * Uses /admin/users as the action URL.
     *
     * @param adminUserId the admin who should receive the notification
     * @param type        the notification type
     * @param parameters  translation parameters (e.g., userName, userType)
     */
    public static NotificationRequest forAdmin(Long adminUserId, NotificationType type,
                                                Map<String, String> parameters) {
        return NotificationRequest.builder()
                .userId(adminUserId)
                .type(type)
                .parameters(parameters)
                .actionUrl("/admin/users")
                .groupKey("admin:" + type.name())
                .build();
    }

    /**
     * Create request for subscription lifecycle notification.
     * Covers trial, payment, upgrade, downgrade, suspension, reactivation.
     */
    public static NotificationRequest forSubscription(Long recipientUserId, NotificationType type,
                                                       Map<String, String> parameters) {
        return NotificationRequest.builder()
                .userId(recipientUserId)
                .type(type)
                .parameters(parameters)
                .actionUrl("/subscription")
                .groupKey("subscription:" + recipientUserId)
                .workflowStep(type.name())
                .build();
    }

    /**
     * Build action URL based on applied opportunity status.
     * Maps to the correct collaboration tab based on the status.
     * <p>
     * Tabs:
     * - /collaborations/registrations: APPLIED, ACCEPTED_BY_COMPANY
     * - /collaborations/in-progress: ACCEPTED_BY_INFLUENCER, CONTENT_SEND_TO_ACCEPT, CONTENT_APPROVED,
     *                                 CONTENT_REJECTED, CONTENT_POSTED, CONTENT_POSTED_REJECTED, TO_BE_PAID
     * - /collaborations/finished: DONE, REJECTED_BY_COMPANY, REJECTED_BY_INFLUENCER
     */
    private static String buildPartnershipUrl(NotificationType type,
                                              com.sm.instagram.platform.appliedopportunities.AppliedOpportunity appliedOpp) {

        var status = appliedOpp.getOpportunityStatus();

        // Map status to the correct collaboration tab
        return switch (status) {
            // REGISTRATIONS TAB - Applications pending action
            case APPLIED, ACCEPTED_BY_COMPANY -> "/collaborations/registrations";

            // IN-PROGRESS TAB - Active collaborations
            case ACCEPTED_BY_INFLUENCER, CONTENT_SEND_TO_ACCEPT, CONTENT_APPROVED,
                 CONTENT_REJECTED, CONTENT_POSTED, CONTENT_POSTED_REJECTED, TO_BE_PAID ->
                "/collaborations/in-progress";

            // FINISHED TAB - Completed or rejected collaborations
            case DONE, REJECTED_BY_COMPANY, REJECTED_BY_INFLUENCER ->
                "/collaborations/finished";
        };
    }
}