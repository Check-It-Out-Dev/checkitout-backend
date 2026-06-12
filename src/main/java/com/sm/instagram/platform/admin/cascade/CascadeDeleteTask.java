package com.sm.instagram.platform.admin.cascade;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for tracking cascade delete operations across multiple systems.
 * Used for retry logic when partial failures occur during user/entity deletion.
 *
 * <p>Deletion order (safest):
 * <ol>
 *   <li>Archive to GCS (recovery option)</li>
 *   <li>PostgreSQL (transactional, main data)</li>
 *   <li>Firestore instagramUsers collection</li>
 *   <li>Firestore totpSecrets collection</li>
 *   <li>Firebase Storage (user files)</li>
 *   <li>Firebase Auth (last, invalidates sessions)</li>
 * </ol>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "cascade_delete_task", indexes = {
    @Index(name = "idx_cascade_delete_task_completed", columnList = "completed_at"),
    @Index(name = "idx_cascade_delete_task_user", columnList = "user_id"),
    @Index(name = "idx_cascade_delete_task_firebase", columnList = "firebase_user_id")
})
public class CascadeDeleteTask {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cascade_delete_task_generator")
    @SequenceGenerator(
        name = "cascade_delete_task_generator",
        sequenceName = "cascade_delete_task_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    /**
     * The PostgreSQL user ID being deleted.
     * May be null for orphaned Firebase user cleanup.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * The Firebase UID of the user being deleted.
     * Required for Firestore and Firebase Auth cleanup.
     */
    @Column(name = "firebase_user_id")
    @Size(max = 255)
    private String firebaseUserId;

    /**
     * The type of entity being deleted (USER, PARTNERSHIP_OPPORTUNITY, APPLIED_OPPORTUNITY).
     */
    @NotBlank
    @Column(name = "entity_type", nullable = false)
    @Size(max = 50)
    private String entityType;

    /**
     * Reason for deletion (GDPR request, admin cleanup, spam, test data, etc.).
     */
    @NotBlank
    @Column(name = "reason", nullable = false)
    @Size(max = 500)
    private String reason;

    /**
     * Firebase UID of the admin who initiated the deletion.
     */
    @NotBlank
    @Column(name = "admin_firebase_id", nullable = false)
    @Size(max = 255)
    private String adminFirebaseId;

    /**
     * GCS path to the archived data (if archival succeeded).
     */
    @Column(name = "archive_url")
    @Size(max = 2048)
    private String archiveUrl;

    // ========== Per-System Status Tracking ==========

    /**
     * Status of PostgreSQL deletion.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "postgresql_status", nullable = false)
    @Builder.Default
    private SystemStatus postgresqlStatus = SystemStatus.PENDING;

    /**
     * Status of Firestore instagramUsers collection cleanup.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "firestore_instagram_status", nullable = false)
    @Builder.Default
    private SystemStatus firestoreInstagramStatus = SystemStatus.PENDING;

    /**
     * Status of Firestore totpSecrets collection cleanup.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "firestore_totp_status", nullable = false)
    @Builder.Default
    private SystemStatus firestoreTotpStatus = SystemStatus.PENDING;

    /**
     * Status of Firebase Storage cleanup (user files).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "firebase_storage_status", nullable = false)
    @Builder.Default
    private SystemStatus firebaseStorageStatus = SystemStatus.PENDING;

    /**
     * Status of Firebase Auth user deletion.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "firebase_auth_status", nullable = false)
    @Builder.Default
    private SystemStatus firebaseAuthStatus = SystemStatus.PENDING;

    // ========== Error Tracking ==========

    /**
     * Detailed error information if any system failed.
     * JSON format: {"postgresql": "error msg", "firestoreInstagram": "error msg", ...}
     */
    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    /**
     * Number of retry attempts for failed systems.
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    // ========== Timestamps ==========

    /**
     * When the cascade delete task was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * When the last attempt (initial or retry) was made.
     */
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    /**
     * When all systems completed successfully (or all remaining were skipped).
     * Null if any system is still PENDING or FAILED with retries remaining.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ========== Helper Methods ==========

    /**
     * Returns true if all systems have completed (SUCCESS or SKIPPED).
     */
    public boolean isFullyCompleted() {
        return (postgresqlStatus == SystemStatus.SUCCESS || postgresqlStatus == SystemStatus.SKIPPED)
            && (firestoreInstagramStatus == SystemStatus.SUCCESS || firestoreInstagramStatus == SystemStatus.SKIPPED)
            && (firestoreTotpStatus == SystemStatus.SUCCESS || firestoreTotpStatus == SystemStatus.SKIPPED)
            && (firebaseStorageStatus == SystemStatus.SUCCESS || firebaseStorageStatus == SystemStatus.SKIPPED)
            && (firebaseAuthStatus == SystemStatus.SUCCESS || firebaseAuthStatus == SystemStatus.SKIPPED);
    }

    /**
     * Returns true if any system has a FAILED status that needs retry.
     */
    public boolean hasFailedSystems() {
        return postgresqlStatus == SystemStatus.FAILED
            || firestoreInstagramStatus == SystemStatus.FAILED
            || firestoreTotpStatus == SystemStatus.FAILED
            || firebaseStorageStatus == SystemStatus.FAILED
            || firebaseAuthStatus == SystemStatus.FAILED;
    }

    /**
     * Returns true if this task can be retried (has failed systems and retry count not exceeded).
     */
    public boolean canRetry(int maxRetries) {
        return hasFailedSystems() && retryCount < maxRetries;
    }

    /**
     * Increment retry count and update last attempt time.
     */
    public void incrementRetry() {
        this.retryCount++;
        this.lastAttemptAt = LocalDateTime.now();
    }

    /**
     * Mark the task as completed if all systems are done.
     */
    public void markCompletedIfDone() {
        if (isFullyCompleted() && completedAt == null) {
            this.completedAt = LocalDateTime.now();
        }
    }
}
