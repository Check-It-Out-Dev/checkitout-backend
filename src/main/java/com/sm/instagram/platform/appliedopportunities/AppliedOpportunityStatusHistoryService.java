package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppliedOpportunityStatusHistoryService {

    public static final String APPLIED_OPPORTUNITY_NOT_FOUND = "Applied opportunity not found";
    private final AppliedOpportunityStatusHistoryRepository statusHistoryRepository;
    private final AppliedOpportunityRepository appliedOpportunityRepository;
    private final UserRepository userRepository;
    private final PermissionUtils permissionUtils;

    /**
     * Log a status change for an applied opportunity
     */
    @Transactional
    public AppliedOpportunityStatusHistory logStatusChange(AppliedOpportunity appliedOpportunity,
                                                           OpportunityStatus previousStatus,
                                                           OpportunityStatus newStatus,
                                                           String changeReason) {
        return logStatusChange(appliedOpportunity, previousStatus, newStatus, changeReason, null);
    }

    /**
     * Log a status change for an applied opportunity with additional notes
     */
    @Transactional
    public AppliedOpportunityStatusHistory logStatusChange(AppliedOpportunity appliedOpportunity,
                                                           OpportunityStatus previousStatus,
                                                           OpportunityStatus newStatus,
                                                           String changeReason,
                                                           String notes) {
        try {
            String currentUserFirebaseId = permissionUtils.getUserId();
            log.info("GDPR: Operation=logStatusChange, FirebaseUID={}, AppliedOpportunityID={}, StatusChange={}→{}, Purpose=status_tracking",
                    currentUserFirebaseId, appliedOpportunity.getId(), previousStatus, newStatus);

            // Try to get the full user entity for better referential integrity
            Optional<User> currentUser = userRepository.findByFirebaseUserId(currentUserFirebaseId);

            AppliedOpportunityStatusHistory historyEntry;
            if (currentUser.isPresent()) {
                historyEntry = new AppliedOpportunityStatusHistory(
                        appliedOpportunity,
                        previousStatus,
                        newStatus,
                        currentUser.get(),
                        currentUserFirebaseId,
                        changeReason
                );
            } else {
                // Fallback to Firebase ID only if user not found in database
                historyEntry = new AppliedOpportunityStatusHistory(
                        appliedOpportunity,
                        previousStatus,
                        newStatus,
                        currentUserFirebaseId,
                        changeReason
                );
                log.warn("User not found in database for Firebase ID: {}, using Firebase ID only", currentUserFirebaseId);
            }

            if (notes != null && !notes.trim().isEmpty()) {
                historyEntry.setNotes(notes.trim());
            }

            AppliedOpportunityStatusHistory saved = statusHistoryRepository.save(historyEntry);

            log.info("GDPR: DataCreated=status_history, FirebaseUID={}, AppliedOpportunityID={}, DataAccessed=status,changedBy,reason, Purpose=audit_trail",
                    currentUserFirebaseId, appliedOpportunity.getId());
            log.info("Status change logged: Applied Opportunity ID={}, {} -> {}, Changed by={}, Reason={}",
                    appliedOpportunity.getId(),
                    previousStatus,
                    newStatus,
                    currentUserFirebaseId,
                    changeReason);

            return saved;

        } catch (Exception e) {
            log.error("Failed to log status change for Applied Opportunity ID={}: {}",
                    appliedOpportunity.getId(), e.getMessage(), e);
            // Don't throw exception - logging should not break the main operation
            return null;
        }
    }

    /**
     * Log status change for system operations (no current user context)
     */
    @Transactional
    public AppliedOpportunityStatusHistory logSystemStatusChange(AppliedOpportunity appliedOpportunity,
                                                                 OpportunityStatus previousStatus,
                                                                 OpportunityStatus newStatus,
                                                                 String changeReason) {
        try {
            log.info("GDPR: Operation=logSystemStatusChange, FirebaseUID=SYSTEM, AppliedOpportunityID={}, StatusChange={}→{}, Purpose=automated_workflow",
                    appliedOpportunity.getId(), previousStatus, newStatus);
            AppliedOpportunityStatusHistory historyEntry = new AppliedOpportunityStatusHistory(
                    appliedOpportunity,
                    previousStatus,
                    newStatus,
                    "SYSTEM",
                    changeReason
            );

            AppliedOpportunityStatusHistory saved = statusHistoryRepository.save(historyEntry);

            log.info("GDPR: DataCreated=system_status_history, FirebaseUID=SYSTEM, AppliedOpportunityID={}, Purpose=system_audit",
                    appliedOpportunity.getId());
            log.info("System status change logged: Applied Opportunity ID={}, {} -> {}, Reason={}",
                    appliedOpportunity.getId(), previousStatus, newStatus, changeReason);

            return saved;

        } catch (Exception e) {
            log.error("Failed to log system status change for Applied Opportunity ID={}: {}",
                    appliedOpportunity.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get status history for a specific applied opportunity
     */
    public List<AppliedOpportunityStatusHistory> getStatusHistory(Long appliedOpportunityId) {
        String currentUserFirebaseId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getStatusHistory, FirebaseUID={}, AppliedOpportunityID={}, Purpose=audit_review",
                currentUserFirebaseId, appliedOpportunityId);

        // Validate access to the applied opportunity
        AppliedOpportunity opportunity = appliedOpportunityRepository.findById(appliedOpportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

        if (!permissionUtils.canViewAppliedOpportunity(opportunity)) {
            log.warn("GDPR: AccessDenied Operation=getStatusHistory, FirebaseUID={}, AppliedOpportunityID={}, Reason=no_view_permission",
                    currentUserFirebaseId, appliedOpportunityId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserFirebaseId,
                    "getStatusHistory",
                    "AppliedOpportunity#" + appliedOpportunityId);
        }

        List<AppliedOpportunityStatusHistory> history = statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(appliedOpportunityId);
        log.info("GDPR: DataAccessed=status_history, FirebaseUID={}, RecordCount={}, Purpose=history_retrieval",
                currentUserFirebaseId, history.size());
        return history;
    }

    /**
     * Get status history for a specific applied opportunity with pagination
     */
    public Page<AppliedOpportunityStatusHistory> getStatusHistory(Long appliedOpportunityId, Pageable pageable) {
        String currentUserFirebaseId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getStatusHistoryPaged, FirebaseUID={}, AppliedOpportunityID={}, Purpose=paginated_audit_review",
                currentUserFirebaseId, appliedOpportunityId);

        // Validate access to the applied opportunity
        AppliedOpportunity opportunity = appliedOpportunityRepository.findById(appliedOpportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

        if (!permissionUtils.canViewAppliedOpportunity(opportunity)) {
            log.warn("GDPR: AccessDenied Operation=getStatusHistoryPaged, FirebaseUID={}, AppliedOpportunityID={}, Reason=no_view_permission",
                    currentUserFirebaseId, appliedOpportunityId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserFirebaseId,
                    "getStatusHistoryPaged",
                    "AppliedOpportunity#" + appliedOpportunityId);
        }

        Page<AppliedOpportunityStatusHistory> historyPage = statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(appliedOpportunityId, pageable);
        log.info("GDPR: DataAccessed=status_history_page, FirebaseUID={}, PageSize={}, TotalElements={}, Purpose=paginated_history",
                currentUserFirebaseId, historyPage.getSize(), historyPage.getTotalElements());
        return historyPage;
    }

    /**
     * Get status changes made by a specific user - requires admin or self
     */
    public List<AppliedOpportunityStatusHistory> getStatusChangesByUser(String firebaseUserId) {
        String currentUserFirebaseId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getStatusChangesByUser, FirebaseUID={}, TargetFirebaseUID={}, Purpose=user_activity_audit",
                currentUserFirebaseId, firebaseUserId);

        // Only admin or the user themselves can view their change history
        if (!permissionUtils.isAdmin() && !currentUserFirebaseId.equals(firebaseUserId)) {
            log.warn("GDPR: AccessDenied Operation=getStatusChangesByUser, FirebaseUID={}, TargetFirebaseUID={}, Reason=not_authorized",
                    currentUserFirebaseId, firebaseUserId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserFirebaseId,
                    "getStatusChangesByUser",
                    "User#" + firebaseUserId);
        }

        List<AppliedOpportunityStatusHistory> userHistory = statusHistoryRepository.findByChangedByFirebaseIdOrderByChangedAtDesc(firebaseUserId);
        log.info("GDPR: DataAccessed=user_status_changes, FirebaseUID={}, TargetFirebaseUID={}, RecordCount={}, Purpose=user_audit",
                currentUserFirebaseId, firebaseUserId, userHistory.size());
        return userHistory;
    }

    /**
     * Get status changes within a date range - admin only
     */
    public List<AppliedOpportunityStatusHistory> getStatusChangesInDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        String currentUserFirebaseId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getStatusChangesInDateRange, FirebaseUID={}, DateRange={} to {}, Purpose=temporal_audit",
                currentUserFirebaseId, startDate, endDate);

        if (!permissionUtils.isAdmin()) {
            log.warn("GDPR: AccessDenied Operation=getStatusChangesInDateRange, FirebaseUID={}, Reason=admin_only",
                    currentUserFirebaseId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserFirebaseId,
                    "getStatusChangesInDateRange",
                    "StatusHistory");
        }

        List<AppliedOpportunityStatusHistory> rangeHistory = statusHistoryRepository.findByChangedAtBetweenOrderByChangedAtDesc(startDate, endDate);
        log.info("GDPR: DataAccessed=date_range_status_changes, FirebaseUID={}, RecordCount={}, Purpose=temporal_analysis",
                currentUserFirebaseId, rangeHistory.size());
        return rangeHistory;
    }

    /**
     * Get the most recent status change for an applied opportunity
     */
    public Optional<AppliedOpportunityStatusHistory> getLastStatusChange(Long appliedOpportunityId) {
        String currentUserFirebaseId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getLastStatusChange, FirebaseUID={}, AppliedOpportunityID={}, Purpose=latest_status_check",
                currentUserFirebaseId, appliedOpportunityId);

        // Validate access to the applied opportunity
        AppliedOpportunity opportunity = appliedOpportunityRepository.findById(appliedOpportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

        if (!permissionUtils.canViewAppliedOpportunity(opportunity)) {
            log.warn("GDPR: AccessDenied Operation=getLastStatusChange, FirebaseUID={}, AppliedOpportunityID={}, Reason=no_view_permission",
                    currentUserFirebaseId, appliedOpportunityId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserFirebaseId,
                    "getLastStatusChange",
                    "AppliedOpportunity#" + appliedOpportunityId);
        }

        AppliedOpportunityStatusHistory lastChange = statusHistoryRepository
                .findFirstByAppliedOpportunityIdOrderByChangedAtDesc(appliedOpportunityId);
        log.info("GDPR: DataAccessed=last_status_change, FirebaseUID={}, Found={}, Purpose=status_verification",
                currentUserFirebaseId, lastChange != null);
        return Optional.ofNullable(lastChange);
    }

    /**
     * Count total status changes for an applied opportunity
     */
    public long countStatusChanges(Long appliedOpportunityId) {
        String currentUserFirebaseId = permissionUtils.getUserId();
        log.info("GDPR: Operation=countStatusChanges, FirebaseUID={}, AppliedOpportunityID={}, Purpose=statistics_query",
                currentUserFirebaseId, appliedOpportunityId);

        // Validate access to the applied opportunity
        AppliedOpportunity opportunity = appliedOpportunityRepository.findById(appliedOpportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

        if (!permissionUtils.canViewAppliedOpportunity(opportunity)) {
            log.warn("GDPR: AccessDenied Operation=countStatusChanges, FirebaseUID={}, AppliedOpportunityID={}, Reason=no_view_permission",
                    currentUserFirebaseId, appliedOpportunityId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserFirebaseId,
                    "countStatusChanges",
                    "AppliedOpportunity#" + appliedOpportunityId);
        }

        long count = statusHistoryRepository.countByAppliedOpportunityId(appliedOpportunityId);
        log.info("GDPR: DataAccessed=status_change_count, FirebaseUID={}, Count={}, Purpose=statistics_retrieval",
                currentUserFirebaseId, count);
        return count;
    }
}
