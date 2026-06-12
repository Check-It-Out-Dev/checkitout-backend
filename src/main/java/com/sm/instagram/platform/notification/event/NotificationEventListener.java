package com.sm.instagram.platform.notification.event;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.notification.NotificationService;
import com.sm.instagram.platform.notification.NotificationTranslationService;
import com.sm.instagram.platform.notification.NotificationType;
import com.sm.instagram.platform.notification.dto.NotificationRequest;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * Event listener for domain events that trigger notifications.
 * <p>
 * CRITICAL DESIGN NOTES:
 * 1. Uses TransactionPhase.AFTER_COMMIT - notification creation only happens
 * AFTER the business transaction commits successfully.
 * 2. If notification creation fails, the business operation is NOT rolled back.
 * 3. Each handler method logs extensively for debugging and monitoring.
 * <p>
 * Pattern used by: Shopify, Stripe, GitHub for decoupled notification systems.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    // ========================================================================
    // OPPORTUNITY STATUS CHANGE HANDLER
    // ========================================================================

    /**
     * Handle opportunity status changes by creating appropriate notifications.
     * <p>
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
    // ACCOUNT ACTIVATION HANDLER
    // ========================================================================

    /**
     * Handle account activation by creating an in-app + email notification.
     * <p>
     * Recipient: The activated user
     * Priority: HIGH (important account event)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountActivated(AccountActivatedEvent event) {
        try {
            User user = event.getUser();

            NotificationRequest request = NotificationRequest.forAccount(
                    user.getId(),
                    NotificationType.ACCOUNT_ACTIVATED
            );

            notificationService.createNotification(request);

            log.info("Created ACCOUNT_ACTIVATED notification: userId={}, source={}, previousStatus={}",
                    user.getId(), event.getActivationSource(), event.getPreviousStatus());
        } catch (Exception e) {
            log.error("Failed to create ACCOUNT_ACTIVATED notification: userId={}, error={}",
                    event.getUser().getId(), e.getMessage(), e);
        }

        // Also notify all admins about the activation
        notifyAdmins(
                NotificationType.ADMIN_ACCOUNT_ACTIVATED,
                Map.of(
                        "userName", getDisplayName(event.getUser()),
                        "userEmail", event.getUser().getEmail() != null ? event.getUser().getEmail() : "N/A",
                        "userType", event.getUser().getUserType().name(),
                        "activationSource", event.getActivationSource() != null ? event.getActivationSource() : "UNKNOWN"
                )
        );
    }

    // ========================================================================
    // ADMIN NOTIFICATIONS - New user registered
    // ========================================================================

    /**
     * Handle new user registration by notifying all admins in-app.
     * Supplements the existing admin email (NTF-002 in EmailService).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewUserRegistered(NewUserRegisteredEvent event) {
        try {
            User newUser = event.getUser();

            notifyAdmins(
                    NotificationType.ADMIN_NEW_USER_REGISTERED,
                    Map.of(
                            "userName", getDisplayName(newUser),
                            "userEmail", newUser.getEmail() != null ? newUser.getEmail() : "N/A",
                            "userType", newUser.getUserType().name(),
                            "registrationType", event.getRegistrationType()
                    )
            );

            log.info("Created ADMIN_NEW_USER_REGISTERED notifications for new user: userId={}, type={}",
                    newUser.getId(), newUser.getUserType());
        } catch (Exception e) {
            log.error("Failed to create ADMIN_NEW_USER_REGISTERED notifications: userId={}, error={}",
                    event.getUser().getId(), e.getMessage(), e);
        }
    }

    // ========================================================================
    // STATUS HANDLERS - Each creates notification for appropriate recipient
    // ========================================================================

    /**
     * APPLIED: Influencer applied → Notify Company
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
            try {
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
            } catch (Exception e) {
                log.error("Failed to create COLLABORATION_COMPLETE notification for influencer: recipientId={}, " +
                        "appliedOpportunityId={}, error={}", influencer.getId(), appliedOpp.getId(), e.getMessage(), e);
            }
        } else {
            log.warn("Cannot notify influencer for DONE: no influencer found, " +
                    "appliedOpportunityId={}", appliedOpp.getId());
        }

        // Notify Company
        User company = event.getCompany();
        if (company != null) {
            try {
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
            } catch (Exception e) {
                log.error("Failed to create COLLABORATION_COMPLETE notification for company: recipientId={}, " +
                        "appliedOpportunityId={}, error={}", company.getId(), appliedOpp.getId(), e.getMessage(), e);
            }
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

    /**
     * Send an in-app notification to ALL active admin users.
     * Used for admin-specific events (new registration, account activation).
     *
     * @param type       the admin notification type
     * @param parameters translation parameters
     */
    private void notifyAdmins(NotificationType type, Map<String, String> parameters) {
        List<User> admins = userRepository.findByUserTypeAndAccountStatus(UserType.ADMIN, AccountStatus.ACTIVE);

        if (admins.isEmpty()) {
            log.warn("No active ADMIN users found to notify for {}", type);
            return;
        }

        int created = 0;
        for (User admin : admins) {
            try {
                NotificationRequest request = NotificationRequest.forAdmin(
                        admin.getId(), type, parameters);
                notificationService.createNotification(request);
                created++;
            } catch (Exception e) {
                log.error("Failed to create {} notification for admin userId={}: {}",
                        type, admin.getId(), e.getMessage());
            }
        }

        log.info("Created {} {} notifications for {}/{} admins", created, type, created, admins.size());
    }
}