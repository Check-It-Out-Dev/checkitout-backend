package com.sm.instagram.platform.notification;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.notification.dto.NotificationRequest;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for managing notifications.
 * <p>
 * Responsibilities:
 * - Create notifications from NotificationRequest
 * - Retrieve user notifications (paginated)
 * - Mark notifications as read (single and bulk)
 * - Archive notifications
 * - Check user preferences for notification eligibility
 * <p>
 * All operations are GDPR-compliant with full audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTranslationService translationService;
    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PermissionUtils permissionUtils;

    // ========================================================================
    // NOTIFICATION CREATION
    // ========================================================================

    /**
     * Create a new notification from a request.
     * <p>
     * This method:
     * 1. Checks if user should receive this notification type (preferences)
     * 2. Fetches user's language for translation
     * 3. Translates title and message with placeholder replacement
     * 4. Determines if email should be sent (preferences + notification type)
     * 5. Saves notification to database
     *
     * @param request the notification creation request
     * @return created notification, or null if user has disabled this category
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification createNotification(NotificationRequest request) {
        // Validate required fields
        if (request.getUserId() == null) {
            log.error("Cannot create notification: userId is null");
            throw new ValidationTranslatableException("error.validation.required_field", "userId");
        }
        if (request.getType() == null) {
            log.error("Cannot create notification: type is null");
            throw new ValidationTranslatableException("error.validation.required_field", "type");
        }

        Long userId = request.getUserId();
        NotificationType type = request.getType();

        // Get user with preferences
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Cannot create notification: user not found, userId={}", userId);
                    return new ResourceNotFoundException("error.business.item_not_found", "User");
                });

        // Check if user wants this notification category
        if (!shouldNotify(userId, type)) {
            log.info("GDPR: Notification suppressed, userId={}, type={}, reason=user_preference",
                    userId, type);
            return null;
        }

        // Get user's language preference
        String languageCode = getUserLanguage(user);

        // Build translation parameters
        Map<String, String> params = request.getParameters() != null
                ? request.getParameters()
                : Map.of();

        // Translate content
        String title = translationService.getTitle(type, languageCode, params);
        String message = translationService.getMessage(type, languageCode, params);
        String actionLabel = translationService.getActionLabel(type, languageCode);

        // Determine email eligibility
        boolean emailEnabled = shouldSendEmail(userId, type);

        // Build notification entity
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .category(type.getCategory())
                .priority(type.getPriority())
                .title(title)
                .message(message)
                .actionUrl(request.getActionUrl())
                .actionLabel(actionLabel)
                .translationKey(type.getTranslationKeyPrefix())
                .languageCode(languageCode)
                .snapshot(request.getSnapshot())
                .appliedOpportunityId(request.getAppliedOpportunityId())
                .partnershipOpportunityId(request.getPartnershipOpportunityId())
                .influencerId(request.getInfluencerId())
                .companyId(request.getCompanyId())
                .supportTicketId(request.getSupportTicketId())
                .groupKey(request.getGroupKey())
                .workflowStep(request.getWorkflowStep())
                .emailEnabled(emailEnabled)
                .build();

        Notification saved = notificationRepository.save(notification);

        log.info("GDPR: Operation=createNotification, UserId={}, NotificationId={}, Type={}, " +
                        "Category={}, EmailEnabled={}, Purpose=user_notification",
                userId, saved.getId(), type, type.getCategory(), emailEnabled);

        return saved;
    }

    // ========================================================================
    // NOTIFICATION RETRIEVAL
    // ========================================================================

    /**
     * Get paginated notifications for a user.
     * Excludes archived notifications.
     *
     * @param userId   the user's ID
     * @param pageable pagination parameters
     * @return page of notifications
     */
    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        String accessorUid = permissionUtils.getUserId();

        log.info("GDPR: Operation=getUserNotifications, AccessorFirebaseUID={}, TargetUserId={}, " +
                        "Page={}, Size={}, Purpose=notification_retrieval",
                accessorUid, userId, pageable.getPageNumber(), pageable.getPageSize());

        return notificationRepository.findByUserIdAndNotArchived(userId, pageable);
    }

    /**
     * Get unread notification count for a user.
     * Used for bell icon badge.
     *
     * @param userId the user's ID
     * @return count of unread notifications
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Get a single notification by ID.
     * Validates that the notification belongs to the specified user.
     *
     * @param notificationId notification ID
     * @param userId         user ID (for security)
     * @return the notification
     * @throws ResourceNotFoundException        if not found
     * @throws InsufficientPermissionsException if notification doesn't belong to user
     */
    @Transactional(readOnly = true)
    public Notification getNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.business.item_not_found", "Notification"));

        // Verify ownership (admin bypass or owner check)
        validateNotificationAccess(notification, userId);

        return notification;
    }

    /**
     * Validates that the current user can access the given notification.
     * Admins can access any notification; regular users can only access their own.
     *
     * @param notification the notification to check
     * @param userId       the user ID requesting access
     * @throws InsufficientPermissionsException if access is denied
     */
    private void validateNotificationAccess(Notification notification, Long userId) {
        // Admins can access any notification
        if (permissionUtils.isAdmin()) {
            return;
        }

        // Regular users can only access their own notifications
        if (!notification.getUser().getId().equals(userId)) {
            log.warn("GDPR: AccessDenied Operation=validateNotificationAccess, AttemptedUserId={}, NotificationOwnerId={}, NotificationId={}, Reason=insufficient_permissions",
                    userId, notification.getUser().getId(), notification.getId());

            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    permissionUtils.getUserId(),
                    "accessNotification",
                    "Notification");
        }
    }

    /**
     * Get all notifications for a specific collaboration/workflow.
     *
     * @param userId   user ID
     * @param groupKey group key (e.g., "collab:123")
     * @return list of notifications in chronological order
     */
    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByGroup(Long userId, String groupKey) {
        return notificationRepository.findByUserIdAndGroupKeyOrderByCreatedAtDesc(userId, groupKey);
    }

    // ========================================================================
    // MARK AS READ
    // ========================================================================

    /**
     * Mark a single notification as read.
     *
     * @param notificationId notification ID
     * @param userId         user ID (for security)
     * @return updated notification
     */
    @Transactional
    public Notification markAsRead(Long notificationId, Long userId) {
        Notification notification = getNotification(notificationId, userId);

        if (!notification.getIsRead()) {
            notification.markAsRead();
            notification = notificationRepository.save(notification);

            log.info("GDPR: Operation=markAsRead, UserId={}, NotificationId={}, Purpose=read_status_update",
                    userId, notificationId);
        }

        return notification;
    }

    /**
     * Mark all unread notifications as read for a user.
     *
     * @param userId user ID
     * @return number of notifications marked as read
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int count = notificationRepository.markAllAsReadByUserId(userId, now);

        log.info("GDPR: Operation=markAllAsRead, UserId={}, Count={}, Purpose=bulk_read_status_update",
                userId, count);

        return count;
    }

    // ========================================================================
    // ARCHIVE
    // ========================================================================

    /**
     * Archive a notification (hide from main list).
     *
     * @param notificationId notification ID
     * @param userId         user ID (for security)
     * @return updated notification
     */
    @Transactional
    public Notification archiveNotification(Long notificationId, Long userId) {
        Notification notification = getNotification(notificationId, userId);

        if (!notification.getIsArchived()) {
            notification.archive();
            notification = notificationRepository.save(notification);

            log.info("GDPR: Operation=archiveNotification, UserId={}, NotificationId={}, Purpose=notification_archive",
                    userId, notificationId);
        }

        return notification;
    }

    // ========================================================================
    // EMAIL QUEUE (for EmailCronJob)
    // ========================================================================

    /**
     * Get notifications pending email delivery.
     * Used by EmailCronJob.
     *
     * @param batchSize maximum notifications to fetch
     * @return list of notifications needing email
     */
    @Transactional(readOnly = true)
    public List<Notification> findPendingEmails(int batchSize) {
        return notificationRepository.findPendingEmails(PageRequest.of(0, batchSize));
    }

    /**
     * Record successful email send.
     *
     * @param notification the notification
     */
    @Transactional
    public void markEmailSent(Notification notification) {
        notification.markEmailSent();
        notificationRepository.save(notification);

        log.info("GDPR: Operation=markEmailSent, UserId={}, NotificationId={}, Purpose=email_delivery_confirmation",
                notification.getUser().getId(), notification.getId());
    }

    /**
     * Record failed email send attempt.
     *
     * @param notification the notification
     * @param error        error message
     */
    @Transactional
    public void recordEmailFailure(Notification notification, String error) {
        notification.recordEmailFailure(error);
        notificationRepository.save(notification);

        log.warn("GDPR: Operation=recordEmailFailure, UserId={}, NotificationId={}, " +
                        "RetryCount={}, Error={}, Purpose=email_delivery_failure",
                notification.getUser().getId(), notification.getId(),
                notification.getEmailRetryCount(), error);
    }

    // ========================================================================
    // PREFERENCE CHECKING
    // ========================================================================

    /**
     * Check if user should receive a notification of this type.
     * <p>
     * Decision tree:
     * 1. If notification category is SYSTEM → always true (system notifications cannot be disabled)
     * 2. Check user's category-specific preference
     *
     * @param userId user ID
     * @param type   notification type
     * @return true if notification should be created
     */
    public boolean shouldNotify(Long userId, NotificationType type) {

        // Hard rule: SYSTEM & ACCOUNT notifications are always visible
        if (type.getCategory() == NotificationCategory.SYSTEM
                || type.getCategory() == NotificationCategory.ACCOUNT) {
            return true;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn(
                    "GDPR: Notification suppressed, reason=user_not_found, userId={}, type={}",
                    userId, type
            );
            return false;
        }
        // Get user preferences
        UserPreferences prefs = userPreferencesRepository.findByUser(user);

        // No preferences → opt-in design: suppress non-system notifications
        if (prefs == null) {
            log.info(
                    "GDPR: No preferences found, suppressing notification (opt-in), userId={}, type={}",
                    userId, type
            );
            return false;
        }

        boolean allowed = switch (type.getCategory()) {
            case PARTNERSHIP -> Boolean.TRUE.equals(prefs.getNotificationPartnershipEnabled());

            case SUPPORT -> Boolean.TRUE.equals(prefs.getNotificationSupportEnabled());

            case SYSTEM, ACCOUNT -> true;
        };

        if (!allowed) {
            log.info(
                    "GDPR: Notification suppressed, userId={}, type={}, category={}, reason=user_preference",
                    userId, type, type.getCategory()
            );
        }

        return allowed;
    }

    /**
     * Check if email should be sent for this notification.
     * <p>
     * Decision tree:
     * 1. If user has email notifications disabled globally → false
     * 2. If notification type emailDefault is DISABLED → false
     * 3. If notification type emailDefault is ALWAYS → true
     * 4. Otherwise → true (ENABLED or DIGEST)
     *
     * @param userId user ID
     * @param type   notification type
     * @return true if email should be sent
     */
    public boolean shouldSendEmail(Long userId, NotificationType type) {

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Email suppressed: user not found, userId={}, type={}", userId, type);
            return false;
        }

        if (type.getEmailDefault() == NotificationType.EmailDefault.ALWAYS) {
            return true;
        }

        UserPreferences prefs = userPreferencesRepository.findByUser(user);

        // No preferences → opt-in design: only ALWAYS types bypass (already handled above)
        if (prefs == null) {
            return false;
        }

        // Global email toggle
        if (!Boolean.TRUE.equals(prefs.getNotificationEmailEnabled())) {
            log.info("Email suppressed: global email disabled, userId={}, type={}", userId, type);
            return false;
        }

        boolean categoryAllowed = switch (type.getCategory()) {
            case PARTNERSHIP -> Boolean.TRUE.equals(prefs.getNotificationEmailPartnershipEnabled());

            case SUPPORT -> Boolean.TRUE.equals(prefs.getNotificationEmailSupportEnabled());

            case ACCOUNT, SYSTEM -> true;
        };

        if (!categoryAllowed) {
            log.info(
                    "Email suppressed: category disabled, userId={}, type={}, category={}",
                    userId, type, type.getCategory()
            );
            return false;
        }

        return switch (type.getEmailDefault()) {
            case DISABLED -> false;
            case ENABLED, DIGEST -> true;
            case ALWAYS -> true; // unreachable, but required by compiler
        };
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Get user's preferred language.
     *
     * @param user the user
     * @return language code (default: "pl")
     */
    private String getUserLanguage(User user) {
        UserPreferences prefs = userPreferencesRepository.findByUser(user);
        if (prefs != null && prefs.getLanguage() != null && !prefs.getLanguage().isBlank()) {
            return prefs.getLanguage();
        }
        return "pl";
    }
}