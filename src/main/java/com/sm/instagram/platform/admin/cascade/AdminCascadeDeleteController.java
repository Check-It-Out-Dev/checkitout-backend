package com.sm.instagram.platform.admin.cascade;

import com.sm.instagram.platform.admin.cascade.dto.*;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for admin cascade delete operations.
 * Provides endpoints for force-deleting users, opportunities, and managing delete tasks.
 *
 * Security: All endpoints require ADMIN authority.
 * Rate limiting: STRICT profile (10 req/min) to prevent abuse.
 */
@Slf4j
@RestController
@RequestMapping("/admin/cascade-delete")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
@RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
public class AdminCascadeDeleteController {

    private static final String REQUEST_ID = "REQUEST_ID";

    private final AdminCascadeDeleteService cascadeDeleteService;

    // ========== User Cascade Delete ==========

    /**
     * Preview what will be deleted when cascade deleting a user.
     * Returns entity counts, systems to clean, and any warnings.
     *
     * @param userId PostgreSQL user ID
     * @return Preview with entity breakdown and systems to clean
     */
    @GetMapping("/users/{userId}/preview")
    public ResponseEntity<CascadeDeletePreview> previewUserDeletion(@PathVariable Long userId) {
        log.info("ADMIN_CASCADE: Preview requested for user {}, admin={}", userId, getAdminUid());

        CascadeDeletePreview preview = cascadeDeleteService.previewUserDeletion(userId);

        log.info("ADMIN_CASCADE: Preview generated for user {}, totalEntities={}, systems={}",
            userId, preview.getTotalEntityCount(), preview.getSystemsToClean());

        return ResponseEntity.ok(preview);
    }

    /**
     * Execute cascade delete for a user across all systems.
     * Deletes from PostgreSQL, Firestore, Firebase Storage, and Firebase Auth.
     *
     * @param userId PostgreSQL user ID
     * @param request Confirmation with reason and expected entity count
     * @return Result with per-system status and deleted entity counts
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<CascadeDeleteResult> forceDeleteUser(
            @PathVariable Long userId,
            @Valid @RequestBody CascadeDeleteConfirmationRequest request) {

        validateConfirmation(request, "CASCADE-DELETE-" + userId);

        String adminUid = getAdminUid();
        log.warn("GDPR: CASCADE_DELETE Admin={}, Action=FORCE_DELETE_USER, UserId={}, Reason={}, RequestId={}",
            adminUid, userId, request.getReason(), MDC.get(REQUEST_ID));

        CascadeDeleteResult result = cascadeDeleteService.forceDeleteUser(userId, request.getReason(), adminUid);

        log.info("ADMIN_CASCADE: User {} deletion completed, success={}, totalDeleted={}, taskId={}",
            userId, result.isSuccess(), result.getTotalDeleted(), result.getTaskId());

        return ResponseEntity.ok(result);
    }

    /**
     * Preview batch cascade delete for multiple users.
     *
     * @param request List of user IDs (max 10)
     * @return Aggregated preview across all users
     */
    @PostMapping("/users/batch/preview")
    public ResponseEntity<BatchCascadeDeletePreview> previewBatchUserDeletion(
            @Valid @RequestBody BatchCascadeDeleteRequest request) {

        validateBatchSize(request.getUserIds());

        log.info("ADMIN_CASCADE: Batch preview requested for {} users, admin={}",
            request.getUserIds().size(), getAdminUid());

        BatchCascadeDeletePreview preview = cascadeDeleteService.previewBatchUserDeletion(request.getUserIds());

        return ResponseEntity.ok(preview);
    }

    /**
     * Execute batch cascade delete for multiple users.
     *
     * @param request User IDs (max 10) with reason
     * @return List of results, one per user
     */
    @DeleteMapping("/users/batch")
    public ResponseEntity<List<CascadeDeleteResult>> forceDeleteUsers(
            @Valid @RequestBody BatchCascadeDeleteRequest request) {

        validateBatchSize(request.getUserIds());

        String adminUid = getAdminUid();
        log.warn("GDPR: CASCADE_DELETE Admin={}, Action=BATCH_FORCE_DELETE_USERS, UserIds={}, Reason={}, RequestId={}",
            adminUid, request.getUserIds(), request.getReason(), MDC.get(REQUEST_ID));

        List<CascadeDeleteResult> results = cascadeDeleteService.forceDeleteUsers(
            request.getUserIds(), request.getReason(), adminUid);

        long successCount = results.stream().filter(CascadeDeleteResult::isSuccess).count();
        log.info("ADMIN_CASCADE: Batch deletion completed, total={}, success={}, failed={}",
            results.size(), successCount, results.size() - successCount);

        return ResponseEntity.ok(results);
    }

    // ========== Partnership Opportunity Cascade Delete ==========

    /**
     * Preview what will be deleted when cascade deleting a partnership opportunity.
     *
     * @param poId Partnership opportunity ID
     * @return Preview with entity breakdown
     */
    @GetMapping("/partnership-opportunities/{poId}/preview")
    public ResponseEntity<CascadeDeletePreview> previewPartnershipOpportunityDeletion(@PathVariable Long poId) {
        log.info("ADMIN_CASCADE: Preview requested for PO {}, admin={}", poId, getAdminUid());

        CascadeDeletePreview preview = cascadeDeleteService.previewPartnershipOpportunityDeletion(poId);

        return ResponseEntity.ok(preview);
    }

    /**
     * Execute cascade delete for a partnership opportunity.
     *
     * @param poId Partnership opportunity ID
     * @param request Confirmation with reason
     * @return Result with deleted entity counts
     */
    @DeleteMapping("/partnership-opportunities/{poId}")
    public ResponseEntity<CascadeDeleteResult> forceDeletePartnershipOpportunity(
            @PathVariable Long poId,
            @Valid @RequestBody CascadeDeleteConfirmationRequest request) {

        validateConfirmation(request, "CASCADE-DELETE-PO-" + poId);

        String adminUid = getAdminUid();
        log.warn("GDPR: CASCADE_DELETE Admin={}, Action=FORCE_DELETE_PO, PoId={}, Reason={}, RequestId={}",
            adminUid, poId, request.getReason(), MDC.get(REQUEST_ID));

        CascadeDeleteResult result = cascadeDeleteService.forceDeletePartnershipOpportunity(
            poId, request.getReason(), adminUid);

        return ResponseEntity.ok(result);
    }

    // ========== Applied Opportunity Cascade Delete ==========

    /**
     * Preview what will be deleted when cascade deleting an applied opportunity.
     *
     * @param aoId Applied opportunity ID
     * @return Preview with entity breakdown
     */
    @GetMapping("/applied-opportunities/{aoId}/preview")
    public ResponseEntity<CascadeDeletePreview> previewAppliedOpportunityDeletion(@PathVariable Long aoId) {
        log.info("ADMIN_CASCADE: Preview requested for AO {}, admin={}", aoId, getAdminUid());

        CascadeDeletePreview preview = cascadeDeleteService.previewAppliedOpportunityDeletion(aoId);

        return ResponseEntity.ok(preview);
    }

    /**
     * Execute cascade delete for an applied opportunity.
     *
     * @param aoId Applied opportunity ID
     * @param request Confirmation with reason
     * @return Result with deleted entity counts
     */
    @DeleteMapping("/applied-opportunities/{aoId}")
    public ResponseEntity<CascadeDeleteResult> forceDeleteAppliedOpportunity(
            @PathVariable Long aoId,
            @Valid @RequestBody CascadeDeleteConfirmationRequest request) {

        validateConfirmation(request, "CASCADE-DELETE-AO-" + aoId);

        String adminUid = getAdminUid();
        log.warn("GDPR: CASCADE_DELETE Admin={}, Action=FORCE_DELETE_AO, AoId={}, Reason={}, RequestId={}",
            adminUid, aoId, request.getReason(), MDC.get(REQUEST_ID));

        CascadeDeleteResult result = cascadeDeleteService.forceDeleteAppliedOpportunity(
            aoId, request.getReason(), adminUid);

        return ResponseEntity.ok(result);
    }

    // ========== Task Management ==========

    /**
     * Get pending/failed cascade delete tasks.
     *
     * @param page Page number (0-based)
     * @param size Page size (max 50)
     * @return Page of pending tasks
     */
    @GetMapping("/tasks")
    public ResponseEntity<Page<CascadeDeleteTaskDto>> getPendingTasks(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CascadeDeleteTaskDto> tasks = cascadeDeleteService.getPendingTasks(pageable);

        return ResponseEntity.ok(tasks);
    }

    /**
     * Get task details by ID.
     *
     * @param taskId Task ID
     * @return Task details with per-system status
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<CascadeDeleteTaskDto> getTask(@PathVariable Long taskId) {
        CascadeDeleteTaskDto task = cascadeDeleteService.getTask(taskId);
        return ResponseEntity.ok(task);
    }

    /**
     * Retry a failed cascade delete task.
     * Only retries systems that failed - already successful systems are skipped.
     *
     * @param taskId Task ID to retry
     * @return Updated result with new status
     */
    @PostMapping("/tasks/{taskId}/retry")
    public ResponseEntity<CascadeDeleteResult> retryTask(@PathVariable Long taskId) {
        String adminUid = getAdminUid();
        log.warn("GDPR: CASCADE_DELETE Admin={}, Action=RETRY_TASK, TaskId={}, RequestId={}",
            adminUid, taskId, MDC.get(REQUEST_ID));

        CascadeDeleteResult result = cascadeDeleteService.retryTask(taskId);

        log.info("ADMIN_CASCADE: Task {} retry completed, success={}, canRetry={}",
            taskId, result.isSuccess(), result.isCanRetry());

        return ResponseEntity.ok(result);
    }

    // ========== Orphan Cleanup ==========

    /**
     * Delete an orphaned Firebase user (exists in Firebase but not PostgreSQL).
     * Use this for cleaning up data inconsistencies.
     *
     * @param firebaseUid Firebase UID to clean up
     * @param reason Reason for deletion
     * @return Result with Firebase cleanup status
     */
    @DeleteMapping("/firebase/{firebaseUid}")
    public ResponseEntity<CascadeDeleteResult> deleteOrphanedFirebaseUser(
            @PathVariable @NotBlank String firebaseUid,
            @RequestParam @NotBlank @Size(min = 5, max = 500) String reason) {

        String adminUid = getAdminUid();
        log.warn("GDPR: CASCADE_DELETE Admin={}, Action=DELETE_ORPHANED_FIREBASE, FirebaseUid={}, Reason={}, RequestId={}",
            adminUid, firebaseUid, reason, MDC.get(REQUEST_ID));

        CascadeDeleteResult result = cascadeDeleteService.deleteOrphanedFirebaseUser(
            firebaseUid, reason, adminUid);

        return ResponseEntity.ok(result);
    }

    /**
     * Find Firebase users that don't exist in PostgreSQL.
     * Limited functionality due to Firebase API constraints.
     *
     * @param limit Maximum number of orphans to return (max 100)
     * @return List of orphaned Firebase UIDs
     */
    @GetMapping("/orphans")
    public ResponseEntity<List<String>> findOrphanedFirebaseUsers(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {

        log.info("ADMIN_CASCADE: Orphan detection requested, limit={}, admin={}", limit, getAdminUid());

        List<String> orphans = cascadeDeleteService.findOrphanedFirebaseUsers(limit);

        return ResponseEntity.ok(orphans);
    }

    // ========== Helper Methods ==========

    private String getAdminUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AuthenticationTranslatableException("error.auth.not_authenticated");
        }
        return auth.getName();
    }

    private void validateConfirmation(CascadeDeleteConfirmationRequest request, String expectedCode) {
        if (request.getConfirmationCode() == null || !request.getConfirmationCode().equals(expectedCode)) {
            throw new ValidationTranslatableException("error.cascade.invalid_confirmation_code");
        }

        if (request.getReason() == null || request.getReason().trim().length() < 5) {
            throw new ValidationTranslatableException("error.cascade.reason_required");
        }
    }

    private void validateBatchSize(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new ValidationTranslatableException("error.cascade.empty_batch");
        }
        if (userIds.size() > 10) {
            throw new ValidationTranslatableException("error.cascade.batch_limit");
        }
    }
}
