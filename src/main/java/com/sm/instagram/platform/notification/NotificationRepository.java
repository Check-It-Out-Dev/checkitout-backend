package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Notification entity.
 * <p>
 * Query naming conventions:
 * - findBy* : Standard JPA derived queries
 * - count* : Counting queries
 * - findPending* : Queue processing queries
 */
@Repository
public interface NotificationRepository extends BaseRepository<Notification, Long> {

    // ========================================================================
    // USER NOTIFICATION QUERIES (Frontend API)
    // ========================================================================

    /**
     * Get paginated notifications for a user (excluding archived).
     * Used by: GET /notifications
     *
     * @param userId   the user's ID
     * @param pageable pagination parameters
     * @return page of notifications, newest first
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.user.id = :userId
              AND n.isArchived = false
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findByUserIdAndNotArchived(
            @Param("userId") Long userId,
            Pageable pageable
    );

    /**
     * Get unread notification count for a user.
     * Used by: GET /notifications/unread/count
     *
     * @param userId the user's ID
     * @return count of unread, non-archived notifications
     */
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.user.id = :userId
              AND n.isRead = false
              AND n.isArchived = false
            """)
    long countUnreadByUserId(@Param("userId") Long userId);

    /**
     * Find notification by ID and user ID (for security).
     * Ensures user can only access their own notifications.
     *
     * @param id     notification ID
     * @param userId user ID
     * @return notification if found and belongs to user
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.id = :id
              AND n.user.id = :userId
            """)
    java.util.Optional<Notification> findByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    // ========================================================================
    // BULK UPDATE QUERIES
    // ========================================================================

    /**
     * Mark all unread notifications as read for a user.
     * Used by: POST /notifications/read-all
     *
     * @param userId user ID
     * @param readAt timestamp when marked as read
     * @return number of notifications updated
     */
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.isRead = true, n.readAt = :readAt
            WHERE n.user.id = :userId
              AND n.isRead = false
              AND n.isArchived = false
            """)
    int markAllAsReadByUserId(
            @Param("userId") Long userId,
            @Param("readAt") LocalDateTime readAt
    );
//    @Modifying
//    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.user.id = :userId AND n.isRead = false")
//    int markAllAsReadByUserId(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    // ========================================================================
    // EMAIL QUEUE QUERIES (Cron Job)
    // ========================================================================

    /**
     * Find notifications pending email delivery.
     * Used by: EmailCronJob every 15 minutes
     * <p>
     * Criteria:
     * - email_enabled = true (user wants email)
     * - email_sent = false (not yet sent)
     * - email_retry_count < 3 (not exhausted retries)
     *
     * @param pageable pagination (batch size)
     * @return list of notifications needing email
     */
    @Query("""
            SELECT n FROM Notification  n JOIN FETCH n.user
            WHERE n.emailEnabled = true
              AND n.emailSent = false
              AND n.emailRetryCount < 3
            ORDER BY n.createdAt ASC
            """)
    List<Notification> findPendingEmails(Pageable pageable);

    // ========================================================================
    // GROUPING QUERIES
    // ========================================================================

    /**
     * Find notifications for a specific collaboration/workflow.
     * Used for showing related notification history.
     *
     * @param userId   user ID
     * @param groupKey group key (e.g., "collab:123")
     * @return list of notifications in the group
     */
    List<Notification> findByUserIdAndGroupKeyOrderByCreatedAtDesc(
            Long userId,
            String groupKey
    );

    /**
     * Find notifications for a specific applied opportunity.
     *
     * @param userId               user ID
     * @param appliedOpportunityId applied opportunity ID
     * @return list of notifications for this opportunity
     */
    List<Notification> findByUserIdAndAppliedOpportunityIdOrderByCreatedAtDesc(
            Long userId,
            Long appliedOpportunityId
    );

    // ========================================================================
    // CLEANUP QUERIES (Scheduled Maintenance)
    // ========================================================================

    /**
     * Archive old read notifications.
     * Run weekly to keep notification list manageable.
     *
     * @param userId     user ID
     * @param cutoffDate archive notifications older than this
     * @param archivedAt timestamp for archival
     * @return number archived
     */
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.isArchived = true, n.archivedAt = :archivedAt
            WHERE n.user.id = :userId
              AND n.isRead = true
              AND n.isArchived = false
              AND n.createdAt < :cutoffDate
            """)
    int archiveOldReadNotifications(
            @Param("userId") Long userId,
            @Param("cutoffDate") LocalDateTime cutoffDate,
            @Param("archivedAt") LocalDateTime archivedAt
    );

    /**
     * Delete very old archived notifications.
     * Run monthly to free database space.
     *
     * @param cutoffDate delete archived notifications older than this
     * @return number deleted
     */
    @Modifying
    @Query("""
            DELETE FROM Notification n
            WHERE n.isArchived = true
              AND n.archivedAt < :cutoffDate
            """)
    int deleteOldArchivedNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========================================================================
    // STATISTICS QUERIES (Admin/Monitoring)
    // ========================================================================

    /**
     * Count notifications by type in a time range.
     * For monitoring/analytics.
     */
    @Query("""
            SELECT n.type, COUNT(n) FROM Notification n
            WHERE n.createdAt >= :startDate
              AND n.createdAt < :endDate
            GROUP BY n.type
            """)
    List<Object[]> countByTypeInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    Page<Notification> findByUserIdAndIsArchivedFalse(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalseAndIsArchivedFalse(Long userId);


//    @Modifying
//    @Query("DELETE FROM Notification n WHERE n.expiresAt < :now")
//    int deleteByExpiresAtBefore(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.readAt < :cutoff")
    int deleteByIsReadTrueAndReadAtBefore(@Param("cutoff") LocalDateTime cutoff);
}