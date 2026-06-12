package com.sm.instagram.platform.admin.cascade;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for cascade delete task tracking.
 * Used for managing retry queue and audit trail of multi-system deletions.
 */
@Repository
public interface CascadeDeleteTaskRepository extends BaseRepository<CascadeDeleteTask, Long> {

    /**
     * Find tasks that need retry (not completed and retry count below threshold).
     */
    List<CascadeDeleteTask> findByCompletedAtIsNullAndRetryCountLessThan(int maxRetries);

    /**
     * Find tasks that are stuck (not completed, max retries exceeded).
     */
    List<CascadeDeleteTask> findByCompletedAtIsNullAndRetryCountGreaterThanEqual(int maxRetries);

    /**
     * Find all incomplete tasks for a specific user.
     */
    List<CascadeDeleteTask> findByUserIdAndCompletedAtIsNull(Long userId);

    /**
     * Find all tasks for a specific user (for audit trail).
     */
    List<CascadeDeleteTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find task by Firebase UID (for orphan cleanup tracking).
     */
    Optional<CascadeDeleteTask> findByFirebaseUserIdAndCompletedAtIsNull(String firebaseUserId);

    /**
     * Find all incomplete tasks (paginated, for admin dashboard).
     */
    Page<CascadeDeleteTask> findByCompletedAtIsNull(Pageable pageable);

    /**
     * Find completed tasks within a date range (for reporting).
     */
    List<CascadeDeleteTask> findByCompletedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find tasks by admin who initiated them.
     */
    Page<CascadeDeleteTask> findByAdminFirebaseIdOrderByCreatedAtDesc(String adminFirebaseId, Pageable pageable);

    /**
     * Count incomplete tasks (for dashboard metrics).
     */
    long countByCompletedAtIsNull();

    /**
     * Count tasks with specific status (for monitoring).
     */
    long countByPostgresqlStatus(SystemStatus status);
    long countByFirestoreInstagramStatus(SystemStatus status);
    long countByFirestoreTotpStatus(SystemStatus status);
    long countByFirebaseStorageStatus(SystemStatus status);
    long countByFirebaseAuthStatus(SystemStatus status);

    /**
     * Check if a deletion task already exists for this user.
     */
    boolean existsByUserIdAndCompletedAtIsNull(Long userId);

    /**
     * Check if a deletion task already exists for this Firebase UID.
     */
    boolean existsByFirebaseUserIdAndCompletedAtIsNull(String firebaseUserId);
}
