package com.sm.instagram.platform.user;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityService;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityService;
import com.sm.instagram.platform.user.dto.DeletionBlocker;
import com.sm.instagram.platform.user.dto.DeletionBlockerCategory;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates user lifecycle operations that span multiple aggregates/services.
 * This decouples UserService from AppliedOpportunityService and PartnershipOpportunityService
 * to avoid circular dependencies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountOrchestrator {

    private final AppliedOpportunityService appliedOpportunityService;
    private final PartnershipOpportunityService partnershipOpportunityService;
    private final DictionaryService dictionaryService;
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final com.sm.instagram.platform.subscription.SubscriptionService subscriptionService;

    /**
     * Get the proxied instance for transactional method calls.
     * Uses ApplicationContext to avoid circular dependency issues.
     */
    private UserAccountOrchestrator getSelf() {
        return applicationContext.getBean(UserAccountOrchestrator.class);
    }

    // ===== Public API =====

    /**
     * Perform archival (soft-delete) cleanup by user type and mark account as TO_BE_DELETED.
     * This method persists the user entity.
     */
    @Transactional
    public void archiveUser(User user) {
        switch (user.getUserType()) {
            case ADMIN -> {
                // No specific cleanup for admin
            }
            case INFLUENCER -> handleInfluencerArchival(user);
            case COMPANY -> {
                handleCompanyArchival(user);
                subscriptionService.deactivateForAccountDeletion(user.getId());
            }
            case PENDING_ADMIN -> {
                // Treat similar to admin for now
            }
        }

        AccountStatus oldStatus = user.getAccountStatus();
        user.setAccountStatus(AccountStatus.TO_BE_DELETED);

        // Increment token version to invalidate all existing sessions
        // This ensures the user is immediately logged out after archival
        user.incrementTokenVersion();

        log.info("User {} status changed from {} to {} for archival",
                user.getId(), oldStatus, AccountStatus.TO_BE_DELETED);

        userRepository.save(user);

        // Evict user from cache to force fresh lookup on next request
        userCacheService.evict(user.getFirebaseUserId());
        log.info("GDPR: Operation=cacheEvict_onArchival, UserId={}, NewStatus={}, Purpose=immediate_session_invalidation",
                user.getId(), AccountStatus.TO_BE_DELETED);
    }

    /**
     * Compute deletion eligibility and blockers for a user.
     */
    @Transactional(readOnly = true)
    public DeletionEligibilityDto checkDeletionEligibilityForUser(User user, Locale locale) {
        List<DeletionBlocker> soft = getSelf().checkSoftDeleteBlockers(user, locale);
        List<DeletionBlocker> permanent = new ArrayList<>(soft);
        checkPermanentDeleteSpecificBlockers(user, permanent, locale);

        boolean canSoft = soft.isEmpty();
        boolean canPermanent = permanent.isEmpty();
        String summary = generateDeletionSummary(user, canSoft, canPermanent, soft, permanent, locale);

        return DeletionEligibilityDto.builder()
                .userId(user.getId())
                .firebaseUserId(user.getFirebaseUserId())
                .userEmail(user.getEmail())
                .userType(user.getUserType().name())
                .canSoftDelete(canSoft)
                .canPermanentDelete(canPermanent)
                .softDeleteBlockers(soft)
                .permanentDeleteBlockers(permanent)
                .summary(summary)
                .build();
    }

    // ===== Soft-delete blockers =====

    @Transactional(readOnly = true)
    public List<DeletionBlocker> checkSoftDeleteBlockers(User user, Locale locale) {
        List<DeletionBlocker> blockers = new ArrayList<>();

        switch (user.getUserType()) {
            case INFLUENCER -> checkInfluencerSoftDeleteBlockers(user, blockers, locale);
            case COMPANY -> checkCompanySoftDeleteBlockers(user, blockers, locale);
            case ADMIN, PENDING_ADMIN -> checkAdminSoftDeleteBlockers(user, blockers, locale);
        }

        checkCommonSoftDeleteBlockers(user, blockers, locale);
        return blockers;
    }

    private void checkInfluencerSoftDeleteBlockers(User user, List<DeletionBlocker> blockers, Locale locale) {
        List<AppliedOpportunity> opportunities =
                appliedOpportunityService.findByInfluencerUserId(user.getFirebaseUserId());

        List<AppliedOpportunity> active = opportunities.stream()
                .filter(appliedOpportunityService::isAppliedOpportunityActive)
                .toList();

        if (!active.isEmpty()) {
            blockers.add(DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES.getLabel(dictionaryService, locale))
                    .description(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES.getDescription(dictionaryService, locale))
                    .count(active.size())
                    .entityIds(active.stream().map(AppliedOpportunity::getId).toList())
                    .entityType("AppliedOpportunity")
                    .entityDescription("Active applied opportunities")
                    .build());
        }

        List<AppliedOpportunity> pending = opportunities.stream()
                .filter(ao -> ao.getOpportunityStatus() == OpportunityStatus.APPLIED)
                .toList();

        if (!pending.isEmpty()) {
            blockers.add(DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.PENDING_OPPORTUNITIES)
                    .reason(DeletionBlockerCategory.PENDING_OPPORTUNITIES.getLabel(dictionaryService, locale))
                    .description(DeletionBlockerCategory.PENDING_OPPORTUNITIES.getDescription(dictionaryService, locale))
                    .count(pending.size())
                    .entityIds(pending.stream().map(AppliedOpportunity::getId).toList())
                    .entityType("AppliedOpportunity")
                    .entityDescription("Pending opportunity applications")
                    .build());
        }
    }

    private void checkCompanySoftDeleteBlockers(User user, List<DeletionBlocker> blockers, Locale locale) {
        List<PartnershipOpportunity> opportunities = partnershipOpportunityService.findByCompany(user);

        List<PartnershipOpportunity> activeOpportunities = opportunities.stream()
                .filter(PartnershipOpportunity::isActive)
                .toList();

        if (!activeOpportunities.isEmpty()) {
            blockers.add(DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES)
                    .reason(DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES.getLabel(dictionaryService, locale))
                    .description(DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES.getDescription(dictionaryService, locale))
                    .count(activeOpportunities.size())
                    .entityIds(activeOpportunities.stream().map(PartnershipOpportunity::getId).toList())
                    .entityType("PartnershipOpportunity")
                    .entityDescription("Active partnership opportunities")
                    .build());
        }

        List<PartnershipOpportunity> withApplications = opportunities.stream()
                .filter(po -> po.getAppliedOpportunities() != null && !po.getAppliedOpportunities().isEmpty())
                .toList();

        if (!withApplications.isEmpty()) {
            blockers.add(DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.OPPORTUNITIES_WITH_APPLICATIONS)
                    .reason(DeletionBlockerCategory.OPPORTUNITIES_WITH_APPLICATIONS.getLabel(dictionaryService, locale))
                    .description(DeletionBlockerCategory.OPPORTUNITIES_WITH_APPLICATIONS.getDescription(dictionaryService, locale))
                    .count(withApplications.size())
                    .entityIds(withApplications.stream().map(PartnershipOpportunity::getId).toList())
                    .entityType("PartnershipOpportunity")
                    .entityDescription("Partnership opportunities with applications")
                    .build());
        }
    }

    private void checkAdminSoftDeleteBlockers(User user, List<DeletionBlocker> blockers, Locale locale) {
        long adminCount = userRepository.countByUserTypeAndAccountStatusNot(UserType.ADMIN, AccountStatus.TO_BE_DELETED);
        long pendingAdminCount = userRepository.countByUserTypeAndAccountStatusNot(UserType.PENDING_ADMIN, AccountStatus.TO_BE_DELETED);

        if (adminCount + pendingAdminCount <= 1) {
            blockers.add(DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.LAST_ADMIN)
                    .reason(DeletionBlockerCategory.LAST_ADMIN.getLabel(dictionaryService, locale))
                    .description(DeletionBlockerCategory.LAST_ADMIN.getDescription(dictionaryService, locale))
                    .count(1)
                    .entityIds(List.of(user.getId()))
                    .entityType("User")
                    .entityDescription("Last admin protection")
                    .build());
        }
    }

    private void checkCommonSoftDeleteBlockers(User user, List<DeletionBlocker> blockers, Locale locale) {
        // Placeholder for support tickets; can be implemented once SupportTicketService is available.
        log.debug("Support ticket check placeholder for user {}", user.getId());
    }

    // ===== Permanent-delete extra blockers =====

    private void checkPermanentDeleteSpecificBlockers(User user, List<DeletionBlocker> blockers, Locale locale) {
        // Support tickets history blocker placeholder would go here.
        
        // Note: Removed "recent activity" blocker as it was preventing legitimate admin deletions
        // and confusing account creation with actual user activity. Administrators should have
        // the authority to permanently delete accounts regardless of recent activity.
        // 
        // Real permanent deletion blockers should be:
        // - Legal/compliance holds
        // - Active business relationships (handled in soft delete blockers)
        // - Technical data dependencies
        // - Regulatory retention requirements
    }

    // ===== Archival helpers =====

    private void handleInfluencerArchival(User user) {
        List<AppliedOpportunity> opportunities =
                appliedOpportunityService.findByInfluencerUserId(user.getFirebaseUserId());

        boolean hasActive = opportunities.stream()
                .anyMatch(appliedOpportunityService::isAppliedOpportunityActive);

        if (hasActive) {
            throw new IllegalArgumentException(
                    "You have unfinished opportunities. Please close all before closing your account");
        }

        // Remove pending applications
        opportunities.stream()
                .filter(ao -> ao.getOpportunityStatus().equals(OpportunityStatus.APPLIED))
                .forEach(ao -> appliedOpportunityService.delete(ao.getId()));

        // Clear email verification timestamps (GDPR right to erasure)
        user.setEmailVerificationSentAt(null);
        user.setEmailVerifiedAt(null);
    }

    private void handleCompanyArchival(User user) {
        // Delete active partnership opportunities
        for (PartnershipOpportunity po : partnershipOpportunityService.findByCompany(user)) {
            if (po.isActive()) {
                partnershipOpportunityService.delete(po.getId());
            }
        }

        // Anonymize user data (GDPR right to erasure)
        user.setFirstName("N/A");
        user.setLastName("N/A");
        user.setPhoneNumber(null);
        // Clear email verification timestamps
        user.setEmailVerificationSentAt(null);
        user.setEmailVerifiedAt(null);
    }

    // ===== Summary =====

    private String generateDeletionSummary(User user,
                                           boolean canSoftDelete,
                                           boolean canPermanentDelete,
                                           List<DeletionBlocker> softBlockers,
                                           List<DeletionBlocker> permanentBlockers,
                                           Locale locale) {
        StringBuilder summary = new StringBuilder();

        summary.append(String.format("User %s (%s): ", user.getEmail(), user.getUserType()));

        if (canSoftDelete && canPermanentDelete) {
            String message = dictionaryService.getTranslation("DELETION_SUMMARY_CAN_DELETE_ALL", locale.getLanguage())
                    .orElse("✅ Can be both soft deleted and permanently deleted");
            summary.append("✅ ").append(message);
        } else if (canSoftDelete) {
            String canSoftDeleteMsg = dictionaryService.getTranslation("DELETION_SUMMARY_CAN_SOFT_DELETE_ONLY", locale.getLanguage())
                    .orElse("Can be soft deleted but not permanently deleted");
            String blockersCountMsg = dictionaryService.getTranslation("DELETION_SUMMARY_PERMANENT_BLOCKERS_COUNT", locale.getLanguage())
                    .orElse("blocker(s) for permanent deletion");

            summary.append("⚠️ ").append(canSoftDeleteMsg);
            summary.append(" (").append(permanentBlockers.size()).append(" ").append(blockersCountMsg).append(")");
        } else {
            String cannotDeleteMsg = dictionaryService.getTranslation("DELETION_SUMMARY_CANNOT_DELETE", locale.getLanguage())
                    .orElse("Cannot be deleted");
            String blockersCountMsg = dictionaryService.getTranslation("DELETION_SUMMARY_BLOCKERS_COUNT", locale.getLanguage())
                    .orElse("blocker(s)");

            summary.append("❌ ").append(cannotDeleteMsg);
            summary.append(" (").append(softBlockers.size()).append(" ").append(blockersCountMsg).append(")");
        }

        return summary.toString();
    }
}