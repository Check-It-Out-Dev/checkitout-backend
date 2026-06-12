package com.sm.instagram.platform.admin.cascade;

import com.sm.instagram.platform.admin.cascade.dto.CascadeDeleteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled task to retry failed cascade delete operations.
 *
 * Runs daily at 3 AM to:
 * 1. Find cascade delete tasks that have partial failures (not completed)
 * 2. Retry the failed system cleanup operations
 * 3. Alert on tasks that have exceeded max retry attempts
 *
 * This ensures eventual consistency across PostgreSQL, Firestore, and Firebase Auth
 * even when individual systems experience temporary failures.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrphanCleanupTask {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String SYSTEM_ADMIN_ID = "SYSTEM_SCHEDULER";

    private final CascadeDeleteTaskRepository taskRepository;
    private final AdminCascadeDeleteService cascadeDeleteService;

    /**
     * Retry failed cascade delete tasks daily at 3 AM.
     * Uses cron expression: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void retryFailedDeletions() {
        log.info("Starting scheduled cascade delete retry job");

        try {
            // Find tasks that need retry (not completed and below max retries)
            List<CascadeDeleteTask> failedTasks = taskRepository
                    .findByCompletedAtIsNullAndRetryCountLessThan(MAX_RETRY_ATTEMPTS);

            if (failedTasks.isEmpty()) {
                log.info("No failed cascade delete tasks to retry");
                return;
            }

            log.info("Found {} cascade delete tasks to retry", failedTasks.size());

            int successCount = 0;
            int failureCount = 0;
            int partialCount = 0;

            for (CascadeDeleteTask task : failedTasks) {
                try {
                    CascadeDeleteResult result = cascadeDeleteService.retryTask(task.getId());

                    if (result.isSuccess()) {
                        successCount++;
                        log.info("Successfully retried cascade delete task {} for user {}",
                                task.getId(), task.getUserId());
                    } else if (result.isPartialSuccess()) {
                        partialCount++;
                        log.warn("Partial success retrying cascade delete task {} for user {}: {}",
                                task.getId(), task.getUserId(), result.getWarnings());
                    } else {
                        failureCount++;
                        log.error("Failed to retry cascade delete task {} for user {}: {}",
                                task.getId(), task.getUserId(), result.getWarnings());
                    }
                } catch (Exception e) {
                    failureCount++;
                    log.error("Exception retrying cascade delete task {} for user {}: {}",
                            task.getId(), task.getUserId(), e.getMessage(), e);
                }
            }

            log.info("Cascade delete retry job completed: {} success, {} partial, {} failed",
                    successCount, partialCount, failureCount);

            // Check for tasks that have exceeded max retries
            alertOnStuckTasks();

        } catch (Exception e) {
            log.error("Error during cascade delete retry job", e);
        }
    }

    /**
     * Alert on tasks that have exceeded maximum retry attempts.
     * These require manual intervention.
     */
    private void alertOnStuckTasks() {
        List<CascadeDeleteTask> stuckTasks = taskRepository
                .findByCompletedAtIsNullAndRetryCountGreaterThanEqual(MAX_RETRY_ATTEMPTS);

        if (!stuckTasks.isEmpty()) {
            log.error("ALERT: {} cascade delete tasks have exceeded max retry attempts and require manual intervention",
                    stuckTasks.size());

            for (CascadeDeleteTask task : stuckTasks) {
                log.error("Stuck task: id={}, userId={}, firebaseUid={}, retryCount={}, " +
                                "postgresqlStatus={}, firestoreInstagramStatus={}, firestoreTotpStatus={}, " +
                                "firebaseStorageStatus={}, firebaseAuthStatus={}, lastAttempt={}, errors={}",
                        task.getId(),
                        task.getUserId(),
                        task.getFirebaseUserId(),
                        task.getRetryCount(),
                        task.getPostgresqlStatus(),
                        task.getFirestoreInstagramStatus(),
                        task.getFirestoreTotpStatus(),
                        task.getFirebaseStorageStatus(),
                        task.getFirebaseAuthStatus(),
                        task.getLastAttemptAt(),
                        task.getErrorDetails());
            }
        }
    }

    /**
     * Get count of pending retry tasks (for monitoring/metrics).
     */
    public long getPendingRetryCount() {
        return taskRepository.countByCompletedAtIsNull();
    }

    /**
     * Get count of stuck tasks (exceeded max retries).
     */
    public long getStuckTaskCount() {
        return taskRepository.findByCompletedAtIsNullAndRetryCountGreaterThanEqual(MAX_RETRY_ATTEMPTS).size();
    }
}
