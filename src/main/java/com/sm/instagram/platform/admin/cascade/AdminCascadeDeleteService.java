package com.sm.instagram.platform.admin.cascade;

import com.sm.instagram.platform.admin.cascade.dto.BatchCascadeDeletePreview;
import com.sm.instagram.platform.admin.cascade.dto.CascadeDeletePreview;
import com.sm.instagram.platform.admin.cascade.dto.CascadeDeleteResult;
import com.sm.instagram.platform.admin.cascade.dto.CascadeDeleteTaskDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for admin cascade delete operations.
 * Provides multi-system deletion with retry support for partial failures.
 */
public interface AdminCascadeDeleteService {

    // ========== User Deletion ==========

    /**
     * Preview what will be deleted when cascade deleting a user.
     * Checks all systems: PostgreSQL, Firestore, Firebase Auth.
     *
     * @param userId The PostgreSQL user ID
     * @return Preview with entity counts and systems to clean
     */
    CascadeDeletePreview previewUserDeletion(Long userId);

    /**
     * Execute cascade delete for a user across all systems.
     * Order: Archive -> PostgreSQL -> Firestore -> Firebase Storage -> Firebase Auth
     *
     * @param userId The PostgreSQL user ID
     * @param reason Reason for deletion (audit trail)
     * @param adminFirebaseId Firebase UID of the admin performing the action
     * @return Result with per-system status
     */
    CascadeDeleteResult forceDeleteUser(Long userId, String reason, String adminFirebaseId);

    /**
     * Preview batch cascade delete for multiple users.
     *
     * @param userIds List of user IDs (max 10)
     * @return Aggregated preview across all users
     */
    BatchCascadeDeletePreview previewBatchUserDeletion(List<Long> userIds);

    /**
     * Execute batch cascade delete for multiple users.
     *
     * @param userIds List of user IDs (max 10)
     * @param reason Reason for deletion
     * @param adminFirebaseId Firebase UID of the admin
     * @return List of results, one per user
     */
    List<CascadeDeleteResult> forceDeleteUsers(List<Long> userIds, String reason, String adminFirebaseId);

    // ========== Partnership Opportunity Deletion ==========

    /**
     * Preview what will be deleted when cascade deleting a partnership opportunity.
     *
     * @param partnershipOpportunityId The partnership opportunity ID
     * @return Preview with entity counts
     */
    CascadeDeletePreview previewPartnershipOpportunityDeletion(Long partnershipOpportunityId);

    /**
     * Execute cascade delete for a partnership opportunity.
     * Deletes: AppliedOpportunityStatusHistory -> AppliedOpportunityContent -> AppliedOpportunity -> Photos -> PO
     *
     * @param partnershipOpportunityId The partnership opportunity ID
     * @param reason Reason for deletion
     * @param adminFirebaseId Firebase UID of the admin
     * @return Result with deletion counts
     */
    CascadeDeleteResult forceDeletePartnershipOpportunity(Long partnershipOpportunityId, String reason, String adminFirebaseId);

    // ========== Applied Opportunity Deletion ==========

    /**
     * Preview what will be deleted when cascade deleting an applied opportunity.
     *
     * @param appliedOpportunityId The applied opportunity ID
     * @return Preview with entity counts
     */
    CascadeDeletePreview previewAppliedOpportunityDeletion(Long appliedOpportunityId);

    /**
     * Execute cascade delete for an applied opportunity.
     * Deletes: AppliedOpportunityStatusHistory -> AppliedOpportunityContent -> AppliedOpportunity
     *
     * @param appliedOpportunityId The applied opportunity ID
     * @param reason Reason for deletion
     * @param adminFirebaseId Firebase UID of the admin
     * @return Result with deletion counts
     */
    CascadeDeleteResult forceDeleteAppliedOpportunity(Long appliedOpportunityId, String reason, String adminFirebaseId);

    // ========== Task Management ==========

    /**
     * Get pending/failed cascade delete tasks.
     *
     * @param pageable Pagination parameters
     * @return Page of tasks
     */
    Page<CascadeDeleteTaskDto> getPendingTasks(Pageable pageable);

    /**
     * Retry a failed cascade delete task.
     *
     * @param taskId The task ID to retry
     * @return Updated task with new status
     */
    CascadeDeleteResult retryTask(Long taskId);

    /**
     * Get task details by ID.
     *
     * @param taskId The task ID
     * @return Task details
     */
    CascadeDeleteTaskDto getTask(Long taskId);

    // ========== Orphan Cleanup ==========

    /**
     * Delete an orphaned Firebase user (exists in Firebase but not PostgreSQL).
     *
     * @param firebaseUid Firebase UID to clean up
     * @param reason Reason for deletion
     * @param adminFirebaseId Firebase UID of the admin
     * @return Result with Firebase cleanup status
     */
    CascadeDeleteResult deleteOrphanedFirebaseUser(String firebaseUid, String reason, String adminFirebaseId);

    /**
     * Find Firebase users that don't exist in PostgreSQL.
     * Note: This is limited and may not find all orphans due to Firebase API limitations.
     *
     * @param limit Maximum number of orphans to return
     * @return List of orphaned Firebase UIDs
     */
    List<String> findOrphanedFirebaseUsers(int limit);
}
