package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.notification.*;
import com.sm.instagram.platform.notification.dto.NotificationDtoOut;
import com.sm.instagram.platform.notification.dto.UnreadCountDto;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
//TODO
/**
 * Słabe strony / ograniczenia:
 *
 * Tylko mock serwisu
 *
 * Kontroler testuje w zasadzie tylko to, że wywołuje serwis i zamienia DTO.
 *
 * Nie wykryje, jeśli logika serwisu przestanie działać (np. markAsRead nie ustawia flagi).
 *
 * Brak testów DTO / mappingu z Notification na NotificationDtoOut
 *
 * Jeżeli w DTO zmienisz mapowanie, test tego nie wychwyci.
 *
 * Nie testuje autoryzacji ani wyjątków z serwisu w pełnej skali
 *
 * Masz kilka testów „user not found”, ale np. InsufficientPermissionsException w kontrolerze nie jest przetestowane.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController Unit Tests")
class NotificationControllerUnitTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionUtils permissionUtils;

    @InjectMocks
    private NotificationController controller;

    private User testUser;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testUser = createUser(1L, "firebase-uid", UserType.INFLUENCER);
//        User company = createUser(2L, "uid-2", UserType.COMPANY);

        testNotification = Notification.builder()
                .id(100L)
                .user(testUser)
                .title("Test Notification")
                .message("Test message")
                .type(NotificationType.APPLICATION_RECEIVED)
                .category(NotificationCategory.ACCOUNT)
                .priority(NotificationPriority.MEDIUM)
                .isRead(false)
                .isArchived(false)
                .emailEnabled(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
    private User createUser(Long id, String firebaseUid, UserType userType) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPhoneNumber("+48123456789");
        user.setCreatedTime(LocalDateTime.now());
        user.setLastUpdateTime(LocalDateTime.now());
        return user;
    }

    private void mockUserLookup() {
        when(permissionUtils.getUserId()).thenReturn("firebase-uid");
        when(userRepository.findByFirebaseUserId("firebase-uid")).thenReturn(Optional.of(testUser));
    }

    // ==================== GET /notifications ====================

    @Test
    @DisplayName("getNotifications should return paginated notifications with default size")
    void getNotifications_returnsPaginatedNotifications() {
        mockUserLookup();

        Page<Notification> page = new PageImpl<>(List.of(testNotification));
        when(notificationService.getUserNotifications(eq(testUser.getId()), any(Pageable.class)))
                .thenReturn(page);

        ResponseEntity<Page<NotificationDtoOut>> response = controller.getNotifications(0, 20);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getContent().get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getNotifications should cap page size at 100")
    void getNotifications_capsPageSizeAt100() {
        mockUserLookup();

        Page<Notification> page = new PageImpl<>(List.of(testNotification));
        when(notificationService.getUserNotifications(eq(testUser.getId()), any(Pageable.class)))
                .thenReturn(page);

        controller.getNotifications(0, 500);

        verify(notificationService).getUserNotifications(eq(testUser.getId()),
                argThat(p -> p.getPageSize() == 100));
    }

    @Test
    @DisplayName("getNotifications should throw when user not found")
    void getNotifications_throwsWhenUserNotFound() {
        when(permissionUtils.getUserId()).thenReturn("unknown-uid");
        when(userRepository.findByFirebaseUserId("unknown-uid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getNotifications(0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== GET /notifications/unread/count ====================

    @Test
    @DisplayName("getUnreadCount returns unread count")
    void getUnreadCount_returnsCount() {
        mockUserLookup();

        when(notificationService.getUnreadCount(testUser.getId())).thenReturn(5L);

        ResponseEntity<UnreadCountDto> response = controller.getUnreadCount();

        assertThat(response.getBody().getCount()).isEqualTo(5L);
    }

    // ==================== GET /notifications/{id} ====================

    @Test
    @DisplayName("getNotification returns notification DTO")
    void getNotification_returnsNotification() {
        mockUserLookup();

        when(notificationService.getNotification(testNotification.getId(), testUser.getId()))
                .thenReturn(testNotification);

        ResponseEntity<NotificationDtoOut> response = controller.getNotification(100L);

        assertThat(response.getBody().getId()).isEqualTo(100L);
    }

    // ==================== PATCH /notifications/{id}/read ====================

    @Test
    @DisplayName("markAsRead marks notification as read")
    void markAsRead_marksNotification() {
        mockUserLookup();

        when(notificationService.markAsRead(testNotification.getId(), testUser.getId()))
                .thenReturn(testNotification);

        ResponseEntity<NotificationDtoOut> response = controller.markAsRead(100L);

        assertThat(response.getBody().getId()).isEqualTo(100L);
    }

    // ==================== POST /notifications/read-all ====================

    @Test
    @DisplayName("markAllAsRead returns count of notifications marked")
    void markAllAsRead_returnsCount() {
        mockUserLookup();

        when(notificationService.markAllAsRead(testUser.getId())).thenReturn(3);

        ResponseEntity<MarkAllReadResponse> response = controller.markAllAsRead();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCount()).isEqualTo(3);
    }

    // ==================== DELETE /notifications/{id} ====================

    @Test
    @DisplayName("archiveNotification returns 204 No Content")
    void archiveNotification_returnsNoContent() {
        mockUserLookup();

        ResponseEntity<Void> response = controller.archiveNotification(100L);

        assertThat(response.getStatusCodeValue()).isEqualTo(204);
        verify(notificationService).archiveNotification(100L, testUser.getId());
    }

    // ==================== GET /notifications/group/{groupKey} ====================

    @Test
    @DisplayName("getByGroupKey returns notifications for group")
    void getByGroupKey_returnsNotifications() {
        mockUserLookup();

        when(notificationService.getNotificationsByGroup(testUser.getId(), "group1"))
                .thenReturn(List.of(testNotification));

        ResponseEntity<List<NotificationDtoOut>> response = controller.getByGroupKey("group1");

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getId()).isEqualTo(100L);
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("methods throw when permissionUtils returns unknown user")
    void methods_throwWhenUserNotFound() {
        when(permissionUtils.getUserId()).thenReturn("unknown");
        when(userRepository.findByFirebaseUserId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getNotifications(0, 20)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> controller.getUnreadCount()).isInstanceOf(ResourceNotFoundException.class);
    }
}
