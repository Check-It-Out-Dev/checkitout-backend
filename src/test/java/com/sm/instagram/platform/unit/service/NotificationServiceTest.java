package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.notification.*;
import com.sm.instagram.platform.notification.dto.NotificationRequest;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
//TODO
/**
 * Słabe strony / ograniczenia:
 *
 * Brak integracji z rzeczywistym encjami i DB
 *
 * W teście wszystko jest mockowane. JPA (@ManyToOne, @Enumerated, @Version) nie jest testowane.
 *
 * Jeżeli ktoś zepsuje mapowanie JPA (np. user_id nie jest nullable = false, lub @Enumerated(EnumType.STRING) się zmieni), testy nie wykryją tego w ogóle.
 *
 * Nie testują pełnej logiki shouldNotify i shouldSendEmail
 *
 * Są tylko testy pozytywne (prefs = true), brak testów dla SYSTEM/ACCOUNT, dla null prefs, dla różnych EmailDefault.
 *
 * Nie testują „drzewka decyzyjnego” w 100%.
 *
 * createNotification nie testuje emailEnabled i snapshot
 *
 * Nie weryfikujesz w testach, czy powiadomienie zostało poprawnie zbudowane pod kątem emailEnabled, snapshot, groupKey, workflowStep.
 *
 * getUserNotifications testuje tylko mocka repozytorium
 *
 * Testuje dokładnie to, co ustawisz w when(). Jeśli ktoś zepsuje metodę repo, test nadal przejdzie, bo repo jest mockowane.
 *
 * markAllAsRead nie sprawdza, czy JPA aktualizuje pola readAt
 *
 * Znowu mock repo – nie ma realnego odzwierciedlenia w bazie danych.
 *
 * Nie ma testów concurrency / edge case dla markAsRead lub archiveNotification
 *
 * W serwisie masz @Version i optimistic locking – testy nie wykrywają problemów w aktualizacji równoległej.
 */
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTranslationService translationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private PermissionUtils permissionUtils;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;
    private NotificationRequest request;
    private NotificationType testType;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testUser = new User();
        testUser.setId(1L);

        testType = NotificationType.APPLICATION_RECEIVED; // example type
        request = NotificationRequest.builder()
                .userId(testUser.getId())
                .type(testType)
                .parameters(Map.of("name", "John"))
                .build();
    }

    // ===========================================
    // CREATE NOTIFICATION
    // ===========================================
    @Test
    @DisplayName("Should create notification when preferences allow")
    void testCreateNotification_Success() {
        UserPreferences prefs = new UserPreferences();
        prefs.setNotificationPartnershipEnabled(true);
        prefs.setNotificationEmailEnabled(true);
        prefs.setNotificationEmailPartnershipEnabled(true);

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userPreferencesRepository.findByUser(testUser)).thenReturn(prefs);
        when(translationService.getTitle(any(), any(), any())).thenReturn("Title");
        when(translationService.getMessage(any(), any(), any())).thenReturn("Message");
        when(translationService.getActionLabel(any(), any())).thenReturn("Action");
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification created = notificationService.createNotification(request);

        assertNotNull(created);
        assertEquals(testUser, created.getUser());
        assertEquals("Title", created.getTitle());
        assertEquals("Message", created.getMessage());
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should suppress notification when no preferences exist (opt-in design)")
    void testCreateNotification_NullPreferences_Suppressed() {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userPreferencesRepository.findByUser(testUser)).thenReturn(null);

        Notification result = notificationService.createNotification(request);

        assertNull(result);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception if userId is null")
    void testCreateNotification_UserIdNull() {
        request.setUserId(null);
        assertThrows(ValidationTranslatableException.class,
                () -> notificationService.createNotification(request));
    }

    @Test
    @DisplayName("Should return null if user preference disables notification")
    void testCreateNotification_DisabledByPreference() {
        UserPreferences prefs = new UserPreferences();
        prefs.setNotificationPartnershipEnabled(false);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userPreferencesRepository.findByUser(testUser)).thenReturn(prefs);

        Notification result = notificationService.createNotification(request);
        assertNull(result);
        verify(notificationRepository, never()).save(any());
    }

    // ===========================================
    // GET USER NOTIFICATIONS
    // ===========================================
    @Test
    @DisplayName("Should get paginated notifications")
    void testGetUserNotifications() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(new Notification(), new Notification()));
        when(permissionUtils.getUserId()).thenReturn("firebaseUid");
        when(notificationRepository.findByUserIdAndNotArchived(testUser.getId(), pageable))
                .thenReturn(page);

        Page<Notification> result = notificationService.getUserNotifications(testUser.getId(), pageable);
        assertEquals(2, result.getContent().size());
    }

    // ===========================================
    // GET SINGLE NOTIFICATION
    // ===========================================
    @Test
    @DisplayName("Should return notification if user owns it")
    void testGetNotification_Success() {
        Notification notification = new Notification();
        notification.setId(100L);
        notification.setUser(testUser);
        when(notificationRepository.findById(100L)).thenReturn(Optional.of(notification));
        when(permissionUtils.isAdmin()).thenReturn(false);

        Notification result = notificationService.getNotification(100L, testUser.getId());
        assertEquals(notification, result);
    }

    @Test
    @DisplayName("Should throw exception if notification belongs to another user")
    void testGetNotification_InsufficientPermissions() {
        Notification notification = new Notification();
        notification.setId(100L);
        User otherUser = new User();
        otherUser.setId(999L);
        notification.setUser(otherUser);

        when(notificationRepository.findById(100L)).thenReturn(Optional.of(notification));
        when(permissionUtils.isAdmin()).thenReturn(false);

        assertThrows(InsufficientPermissionsException.class,
                () -> notificationService.getNotification(100L, testUser.getId()));
    }

    // ===========================================
    // MARK AS READ
    // ===========================================
    @Test
    @DisplayName("Should mark notification as read")
    void testMarkAsRead() {
        Notification notification = new Notification();
        notification.setId(100L);
        notification.setUser(testUser);
        notification.setIsRead(false);

        when(notificationRepository.findById(100L)).thenReturn(Optional.of(notification));
        when(permissionUtils.isAdmin()).thenReturn(false);
        when(notificationRepository.save(notification)).thenReturn(notification);

        Notification result = notificationService.markAsRead(100L, testUser.getId());
        assertTrue(result.getIsRead());
    }

    // ===========================================
    // ARCHIVE NOTIFICATION
    // ===========================================
    @Test
    @DisplayName("Should archive notification")
    void testArchiveNotification() {
        Notification notification = new Notification();
        notification.setId(100L);
        notification.setUser(testUser);
        notification.setIsArchived(false);

        when(notificationRepository.findById(100L)).thenReturn(Optional.of(notification));
        when(permissionUtils.isAdmin()).thenReturn(false);
        when(notificationRepository.save(notification)).thenReturn(notification);

        Notification result = notificationService.archiveNotification(100L, testUser.getId());
        assertTrue(result.getIsArchived());
    }

    // ===========================================
    // SHOULD NOTIFY
    // ===========================================
    @Test
    @DisplayName("Should respect user preferences in shouldNotify")
    void testShouldNotify() {
        UserPreferences prefs = new UserPreferences();
        prefs.setNotificationPartnershipEnabled(true);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userPreferencesRepository.findByUser(testUser)).thenReturn(prefs);

        boolean allowed = notificationService.shouldNotify(testUser.getId(), testType);
        assertTrue(allowed);
    }

    // ===========================================
    // SHOULD SEND EMAIL
    // ===========================================
    @Test
    @DisplayName("Should respect user preferences in shouldSendEmail")
    void testShouldSendEmail() {
        UserPreferences prefs = new UserPreferences();
        prefs.setNotificationEmailEnabled(true);
        prefs.setNotificationEmailPartnershipEnabled(true);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userPreferencesRepository.findByUser(testUser)).thenReturn(prefs);

        boolean allowed = notificationService.shouldSendEmail(testUser.getId(), testType);
        assertTrue(allowed);
    }
}
