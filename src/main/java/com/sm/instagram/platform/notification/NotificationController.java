package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.notification.dto.NotificationDtoOut;
import com.sm.instagram.platform.notification.dto.UnreadCountDto;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for notification operations.
 * <p>
 * All endpoints require authentication and operate on the current user's notifications.
 * Users can only access their own notifications (enforced at service layer).
 * <p>
 * Follows existing controller patterns in the codebase:
 * - Uses @RequiredArgsConstructor for dependency injection
 * - GDPR-compliant logging via service layer
 * - OpenAPI/Swagger documentation
 */
@Slf4j
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/notifications")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification management endpoints")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final PermissionUtils permissionUtils;

    // ========================================================================
    // GET NOTIFICATIONS (Paginated)
    // ========================================================================

    /**
     * Get paginated notifications for the current user.
     * <p>
     * Default: 20 notifications per page, sorted by creation date descending.
     * Frontend uses this for the notification dropdown/list.
     *
     * @param page page number (0-indexed, default 0)
     * @param size page size (default 20, max 100)
     * @return page of notification DTOs
     */
    @GetMapping
    @Operation(
            summary = "Get user notifications",
            description = "Returns paginated notifications for the current user, excluding archived. " +
                    "Sorted by creation date descending (newest first)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated")
    })
    public ResponseEntity<Page<NotificationDtoOut>> getNotifications(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=getNotifications, UserId={}, Page={}, Size={}, Purpose=notification_retrieval",
                userId, page, size);

        // Enforce max page size
        size = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> notifications = notificationService.getUserNotifications(userId, pageable);

        Page<NotificationDtoOut> dtoPage = notifications.map(NotificationDtoOut::fromEntity);

        log.debug("GDPR: Operation=getNotifications, UserId={}, Page={}/{}, Count={}, DataReturned=notifications.list",
                userId, page, dtoPage.getTotalPages(), dtoPage.getNumberOfElements());

        return ResponseEntity.ok(dtoPage);
    }

    // ========================================================================
    // GET UNREAD COUNT (For badge)
    // ========================================================================

    /**
     * Get count of unread notifications for badge display.
     * <p>
     * Frontend polls this endpoint every 30 seconds to update the bell icon badge.
     *
     * @return count of unread notifications
     */
    @GetMapping("/unread/count")
    @RateLimit(profile = RateLimitProfile.HIGH, keyType = RateLimitKeyType.USER_ENDPOINT)
    @Operation(
            summary = "Get unread notification count",
            description = "Returns the count of unread, non-archived notifications. " +
                    "Used for displaying the badge on the notification bell icon."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<UnreadCountDto> getUnreadCount() {
        Long userId = getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);

        log.debug("GDPR: Operation=getUnreadCount, UserId={}, Count={}, Purpose=badge_display", userId, count);

        return ResponseEntity.ok(new UnreadCountDto(count));
    }

    // ========================================================================
    // GET SINGLE NOTIFICATION
    // ========================================================================

    /**
     * Get a single notification by ID.
     *
     * @param id notification ID
     * @return notification DTO
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get notification by ID",
            description = "Returns a single notification. User can only access their own notifications."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Notification not found or belongs to different user")
    })
    public ResponseEntity<NotificationDtoOut> getNotification(
            @Parameter(description = "Notification ID")
            @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=getNotification, UserId={}, NotificationId={}, Purpose=single_notification_access",
                userId, id);

        Notification notification = notificationService.getNotification(id, userId);

        return ResponseEntity.ok(NotificationDtoOut.fromEntity(notification));
    }

    // ========================================================================
    // MARK AS READ (Single)
    // ========================================================================

    /**
     * Mark a single notification as read.
     * <p>
     * Called when user clicks on a notification in the list.
     *
     * @param id notification ID
     * @return updated notification DTO
     */
    @PatchMapping("/{id}/read")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    @Operation(
            summary = "Mark notification as read",
            description = "Marks a single notification as read. Returns the updated notification."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<NotificationDtoOut> markAsRead(
            @Parameter(description = "Notification ID")
            @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=markAsRead, UserId={}, NotificationId={}, Purpose=mark_notification_read",
                userId, id);

        Notification notification = notificationService.markAsRead(id, userId);

        log.debug("GDPR: Operation=markAsRead, UserId={}, NotificationId={}, Result=success", userId, id);

        return ResponseEntity.ok(NotificationDtoOut.fromEntity(notification));
    }

    // ========================================================================
    // MARK ALL AS READ (Bulk)
    // ========================================================================

    /**
     * Mark all unread notifications as read.
     * <p>
     * Called when user clicks "Mark all as read" button.
     *
     * @return count of notifications marked as read
     */
    @PostMapping("/read-all")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    @Operation(
            summary = "Mark all notifications as read",
            description = "Marks all unread, non-archived notifications as read for the current user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications marked as read"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<MarkAllReadResponse> markAllAsRead() {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=markAllAsRead, UserId={}, Purpose=bulk_mark_read", userId);

        int count = notificationService.markAllAsRead(userId);

        log.info("GDPR: Operation=markAllAsRead, UserId={}, Count={}, Result=success", userId, count);

        return ResponseEntity.ok(new MarkAllReadResponse(count));
    }

    // ========================================================================
    // ARCHIVE NOTIFICATION
    // ========================================================================

    /**
     * Archive (hide) a notification.
     * <p>
     * Archived notifications are excluded from the main list but remain in database.
     * Uses DELETE method but doesn't actually delete - follows "soft delete" pattern.
     *
     * @param id notification ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @RateLimit(profile = RateLimitProfile.STRICT, keyType = RateLimitKeyType.USER_ENDPOINT)
    @Operation(
            summary = "Archive notification",
            description = "Archives a notification (soft delete). " +
                    "Archived notifications are hidden from the main list but preserved in database."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notification archived"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Void> archiveNotification(
            @Parameter(description = "Notification ID")
            @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=archiveNotification, UserId={}, NotificationId={}, Purpose=archive_notification",
                userId, id);

        notificationService.archiveNotification(id, userId);

        log.debug("GDPR: Operation=archiveNotification, UserId={}, NotificationId={}, Result=success", userId, id);

        return ResponseEntity.noContent().build();
    }

    // ========================================================================
    // GET BY GROUP KEY
    // ========================================================================

    /**
     * Get all notifications for a specific workflow/collaboration.
     * <p>
     * Useful for showing notification history on a collaboration details page.
     *
     * @param groupKey group key (e.g., "collab:123")
     * @return list of notifications for this group
     */
    @GetMapping("/group/{groupKey}")
    @Operation(
            summary = "Get notifications by group",
            description = "Returns all notifications for a specific workflow group (e.g., collaboration). " +
                    "Useful for showing notification history on detail pages."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<NotificationDtoOut>> getByGroupKey(
            @Parameter(description = "Group key (e.g., 'collab:123')")
            @PathVariable String groupKey
    ) {
        Long userId = getCurrentUserId();
        log.info("GDPR: Operation=getByGroupKey, UserId={}, GroupKey={}, Purpose=group_notification_access",
                userId, groupKey);

        List<Notification> notifications = notificationService.getNotificationsByGroup(userId, groupKey);

        List<NotificationDtoOut> dtos = notifications.stream()
                .map(NotificationDtoOut::fromEntity)
                .toList();

        log.debug("GDPR: Operation=getByGroupKey, UserId={}, GroupKey={}, Count={}, DataReturned=notifications.list",
                userId, groupKey, dtos.size());

        return ResponseEntity.ok(dtos);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get the database ID of the current authenticated user.
     * <p>
     * Uses PermissionUtils to get Firebase UID, then looks up the User entity.
     *
     * @return user's database ID
     * @throws ResourceNotFoundException if user not found
     */
    private Long getCurrentUserId() {
        String firebaseUid = permissionUtils.getUserId();
        log.debug("GDPR: Operation=getCurrentUserId, FirebaseUID={}, Purpose=notification_access", firebaseUid);

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> {
                    log.error("GDPR: Operation=getCurrentUserId, FirebaseUID={}, Result=user_not_found", firebaseUid);
                    return new ResourceNotFoundException("error.user.not_found");
                });

        return user.getId();
    }
}