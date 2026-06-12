package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.dto.MetaCallbackPayload;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.service.InstagramDeauthorizationService;
import com.sm.instagram.platform.auth.service.MetaSignedRequestService;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserAccountOrchestrator;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InstagramDeauthorizationService Unit Tests")
class InstagramDeauthorizationServiceUnitTest {

    @Mock private MetaSignedRequestService metaSignedRequestService;
    @Mock private UserSocialConnectionRepository socialConnectionRepository;
    @Mock private UserAccountOrchestrator userAccountOrchestrator;
    @Mock private FirestoreService firestoreService;
    @Mock private EmailService emailService;
    @Mock private UserPreferencesRepository userPreferencesRepository;

    @InjectMocks
    private InstagramDeauthorizationService service;

    private static final String SIGNED_REQUEST = "test-signed-request";
    private static final String INSTAGRAM_USER_ID = "17841400123456789";
    private static final String FIREBASE_UID = "firebase-uid-123";

    private User activeUser;
    private User inactiveUser;
    private UserSocialConnection connection;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setFirebaseUserId(FIREBASE_UID);
        activeUser.setEmailVerified(true);
        activeUser.setEmail("user@example.com");
        activeUser.setFirstName("John");

        inactiveUser = new User();
        inactiveUser.setId(2L);
        inactiveUser.setFirebaseUserId("firebase-uid-456");
        inactiveUser.setEmailVerified(false);

        connection = new UserSocialConnection();
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setSocialUserId(INSTAGRAM_USER_ID);

        when(metaSignedRequestService.parseSignedRequest(SIGNED_REQUEST))
                .thenReturn(new MetaCallbackPayload(INSTAGRAM_USER_ID, "HMAC-SHA256", 1700000000L));
    }

    @Nested
    @DisplayName("Branch B: Active user (verified email)")
    class ActiveUserBranch {

        @BeforeEach
        void setUp() {
            connection.setUser(activeUser);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", INSTAGRAM_USER_ID))
                    .thenReturn(Optional.of(connection));
        }

        @Test
        @DisplayName("should mark connection as DISCONNECTED")
        void should_mark_connection_disconnected() {
            service.processDeauthorization(SIGNED_REQUEST);

            verify(socialConnectionRepository).save(argThat(c ->
                    c.getConnectionStatus() == ConnectionStatus.DISCONNECTED));
        }

        @Test
        @DisplayName("should delete Firestore data")
        void should_delete_firestore_data() {
            service.processDeauthorization(SIGNED_REQUEST);

            verify(firestoreService).deleteInstagramUserData(FIREBASE_UID);
        }

        @Test
        @DisplayName("should send deauthorization notice email")
        void should_send_notification_email() {
            service.processDeauthorization(SIGNED_REQUEST);

            verify(emailService).sendDeauthorizationNotice(
                    eq("user@example.com"), eq("John"), anyString());
        }

        @Test
        @DisplayName("should NOT archive the user")
        void should_not_archive_user() {
            service.processDeauthorization(SIGNED_REQUEST);

            verify(userAccountOrchestrator, never()).archiveUser(any());
        }

        @Test
        @DisplayName("should continue even if Firestore delete fails")
        void should_continue_on_firestore_failure() {
            doThrow(new RuntimeException("Firestore error"))
                    .when(firestoreService).deleteInstagramUserData(any());

            service.processDeauthorization(SIGNED_REQUEST);

            verify(socialConnectionRepository).save(any());
            verify(emailService).sendDeauthorizationNotice(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Branch A: Inactive user (no verified email)")
    class InactiveUserBranch {

        @BeforeEach
        void setUp() {
            connection.setUser(inactiveUser);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", INSTAGRAM_USER_ID))
                    .thenReturn(Optional.of(connection));
        }

        @Test
        @DisplayName("should archive inactive user")
        void should_archive_user() {
            service.processDeauthorization(SIGNED_REQUEST);

            verify(userAccountOrchestrator).archiveUser(inactiveUser);
        }

        @Test
        @DisplayName("should mark connection as DISCONNECTED")
        void should_mark_connection_disconnected() {
            service.processDeauthorization(SIGNED_REQUEST);

            verify(socialConnectionRepository).save(argThat(c ->
                    c.getConnectionStatus() == ConnectionStatus.DISCONNECTED));
        }

        @Test
        @DisplayName("should delete Firestore data")
        void should_delete_firestore_data() {
            service.processDeauthorization(SIGNED_REQUEST);

            verify(firestoreService).deleteInstagramUserData("firebase-uid-456");
        }

        @Test
        @DisplayName("should NOT send email")
        void should_not_send_email() {
            service.processDeauthorization(SIGNED_REQUEST);

            verify(emailService, never()).sendDeauthorizationNotice(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should continue even if archiveUser fails")
        void should_continue_on_archive_failure() {
            doThrow(new RuntimeException("Archive error"))
                    .when(userAccountOrchestrator).archiveUser(any());

            service.processDeauthorization(SIGNED_REQUEST);

            verify(firestoreService).deleteInstagramUserData("firebase-uid-456");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should do nothing when user not found")
        void should_handle_user_not_found() {
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", INSTAGRAM_USER_ID))
                    .thenReturn(Optional.empty());

            service.processDeauthorization(SIGNED_REQUEST);

            verify(userAccountOrchestrator, never()).archiveUser(any());
            verify(emailService, never()).sendDeauthorizationNotice(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should skip already disconnected connection")
        void should_skip_already_disconnected() {
            connection.setConnectionStatus(ConnectionStatus.DISCONNECTED);
            connection.setUser(activeUser);
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", INSTAGRAM_USER_ID))
                    .thenReturn(Optional.of(connection));

            service.processDeauthorization(SIGNED_REQUEST);

            verify(socialConnectionRepository, never()).save(any());
            verify(emailService, never()).sendDeauthorizationNotice(anyString(), anyString(), anyString());
        }
    }
}
