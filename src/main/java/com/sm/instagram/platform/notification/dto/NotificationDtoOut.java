package com.sm.instagram.platform.notification.dto;

import com.sm.instagram.platform.notification.Notification;
import com.sm.instagram.platform.notification.NotificationPriority;
import com.sm.instagram.platform.notification.NotificationSnapshot;
import com.sm.instagram.platform.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for notification responses to frontend.
 * Follows existing *DtoOut naming convention.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDtoOut {

    /**
     * Notification ID.
     */
    private Long id;

    /**
     * Notification type for frontend display logic.
     */
    @Schema(implementation = NotificationType.class)
    private String type;

    /**
     * Category for grouping/filtering.
     */
    @Schema(allowableValues = {"PARTNERSHIP", "ACCOUNT", "SUPPORT", "SYSTEM"}, description = "NotificationCategory enum name")
    private String category;

    /**
     * Priority for visual styling.
     */
    @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}, description = "NotificationPriority enum name")
    private String priority;

    /**
     * Priority color theme (e.g., "primary", "danger").
     */
    private String colorTheme;

    /**
     * Icon identifier for display.
     */
    private String icon;

    /**
     * Notification title (translated).
     */
    private String title;

    /**
     * Notification message (translated).
     */
    private String message;

    /**
     * URL to navigate on click.
     */
    private String actionUrl;

    /**
     * Action button label (translated).
     */
    private String actionLabel;

    /**
     * Whether notification has been read.
     */
    private Boolean isRead;

    /**
     * When notification was read.
     */
    private LocalDateTime readAt;

    /**
     * Context snapshot for rich display.
     */
    private NotificationSnapshot snapshot;

    /**
     * When notification was created.
     */
    private LocalDateTime createdAt;

    /**
     * Applied opportunity ID (for linking).
     */
    private Long appliedOpportunityId;

    /**
     * Group key for related notifications.
     */
    private String groupKey;

    /**
     * Create DTO from entity.
     */
    public static NotificationDtoOut fromEntity(Notification notification) {
        if (notification == null) {
            return null;
        }

        NotificationPriority priority = notification.getPriority();

        return NotificationDtoOut.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .category(notification.getCategory().name())
                .priority(priority.name())
                .colorTheme(priority.getColorTheme())
                .icon(priority.getIcon())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .actionUrl(notification.getActionUrl())
                .actionLabel(notification.getActionLabel())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .snapshot(notification.getSnapshot())
                .createdAt(notification.getCreatedAt())
                .appliedOpportunityId(notification.getAppliedOpportunityId())
                .groupKey(notification.getGroupKey())
                .build();
    }
}