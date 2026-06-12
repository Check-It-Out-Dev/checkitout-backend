package com.sm.instagram.platform.admin.cascade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for batch cascade delete preview.
 * Contains individual previews and aggregated totals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCascadeDeletePreview {
    /**
     * Individual previews for each user.
     */
    private List<CascadeDeletePreview> userPreviews;

    /**
     * Total entity count across all users.
     */
    private int totalEntityCount;

    /**
     * Total breakdown by entity type across all users.
     */
    private List<EntityTypeCount> totalBreakdown;

    /**
     * All systems that will be cleaned across all users.
     */
    private List<String> allSystemsToClean;

    /**
     * All warnings aggregated from all users.
     */
    private List<String> allWarnings;

    /**
     * Number of users that can be deleted.
     */
    private int deletableUserCount;

    /**
     * Number of users that cannot be deleted (e.g., last admin).
     */
    private int blockedUserCount;

    /**
     * IDs of users that cannot be deleted.
     */
    private List<Long> blockedUserIds;

    /**
     * Reasons why blocked users cannot be deleted.
     */
    private List<String> blockedReasons;
}
