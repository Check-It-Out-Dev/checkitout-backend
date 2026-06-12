package com.sm.instagram.platform.admin.cascade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for the result of a cascade delete operation.
 * Includes per-system status tracking for partial failure handling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CascadeDeleteResult {
    /**
     * Whether the deletion was fully successful across all systems.
     */
    private boolean success;

    /**
     * The cascade delete task ID for tracking and retry.
     */
    private Long taskId;

    /**
     * User ID that was deleted.
     */
    private Long userId;

    /**
     * Firebase UID that was deleted.
     */
    private String firebaseUserId;

    /**
     * Total count of PostgreSQL entities deleted.
     */
    private int totalDeleted;

    /**
     * Breakdown of deleted entities by type.
     */
    private List<EntityTypeCount> deletedByType;

    /**
     * Per-system status tracking.
     */
    private SystemStatuses systemStatuses;

    /**
     * URL to the archived data in GCS (if archival succeeded).
     */
    private String archiveUrl;

    /**
     * Audit trail ID for GDPR compliance.
     */
    private String auditTrailId;

    /**
     * Warnings about partial failures or skipped systems.
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * When the deletion operation started.
     */
    private LocalDateTime startedAt;

    /**
     * When the deletion operation completed (or partially completed).
     */
    private LocalDateTime completedAt;

    /**
     * Whether retry is available for failed systems.
     */
    private boolean canRetry;

    /**
     * Add a warning message.
     */
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }

    /**
     * Check if this is a partial success (some systems failed but PostgreSQL succeeded).
     */
    public boolean isPartialSuccess() {
        return !success && systemStatuses != null &&
            systemStatuses.getPostgresql() == com.sm.instagram.platform.admin.cascade.SystemStatus.SUCCESS;
    }

    /**
     * Check if there are any warnings.
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
