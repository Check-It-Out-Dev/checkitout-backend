package com.sm.instagram.platform.admin.cascade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for listing cascade delete tasks in the admin dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CascadeDeleteTaskDto {
    private Long taskId;
    private Long userId;
    private String firebaseUserId;
    private String entityType;
    private String reason;
    private String adminFirebaseId;
    private SystemStatuses systemStatuses;
    private int retryCount;
    private String archiveUrl;
    private String errorDetails;
    private LocalDateTime createdAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime completedAt;
    private boolean canRetry;
    private boolean isFullyCompleted;
}
