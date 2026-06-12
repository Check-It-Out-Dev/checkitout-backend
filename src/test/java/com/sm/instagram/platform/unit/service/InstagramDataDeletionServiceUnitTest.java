package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.dto.DataDeletionResponse;
import com.sm.instagram.platform.auth.dto.MetaCallbackPayload;
import com.sm.instagram.platform.auth.entity.DeletionRequestStatus;
import com.sm.instagram.platform.auth.entity.PendingDataDeletionRequest;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.repository.PendingDataDeletionRequestRepository;
import com.sm.instagram.platform.auth.service.InstagramDataDeletionService;
import com.sm.instagram.platform.auth.service.MetaSignedRequestService;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserAccountOrchestrator;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InstagramDataDeletionService Unit Tests")
class InstagramDataDeletionServiceUnitTest {

    @Mock private MetaSignedRequestService metaSignedRequestService;
    @Mock private UserSocialConnectionRepository socialConnectionRepository;
    @Mock private UserAccountOrchestrator userAccountOrchestrator;
    @Mock private FirestoreService firestoreService;
    @Mock private PendingDataDeletionRequestRepository deletionRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.sm.instagram.platform.auth.cache.UserCacheService userCacheService;

    @InjectMocks
    private InstagramDataDeletionService service;

    private static final String SIGNED_REQUEST = "test-signed-request";
    private static final String INSTAGRAM_USER_ID = "17841400123456789";
    private static final String FIREBASE_UID = "firebase-uid-123";

    private User user;
    private UserSocialConnection connection;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "https://checkitout.app");

        user = new User();
        user.setId(1L);
        user.setFirebaseUserId(FIREBASE_UID);
        user.setAccountStatus(AccountStatus.ACTIVE);

        connection = new UserSocialConnection();
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setSocialUserId(INSTAGRAM_USER_ID);
        connection.setUser(user);

        when(metaSignedRequestService.parseSignedRequest(SIGNED_REQUEST))
                .thenReturn(new MetaCallbackPayload(INSTAGRAM_USER_ID, "HMAC-SHA256", 1700000000L));
        when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", INSTAGRAM_USER_ID))
                .thenReturn(Optional.of(connection));
    }

    @Nested
    @DisplayName("Case A: Immediate deletion (no active collaborations)")
    class ImmediateDeletion {

        @BeforeEach
        void setUp() {
            DeletionEligibilityDto eligibility = DeletionEligibilityDto.builder()
                    .canSoftDelete(true)
                    .softDeleteBlockers(Collections.emptyList())
                    .build();
            when(userAccountOrchestrator.checkDeletionEligibilityForUser(eq(user), any(Locale.class)))
                    .thenReturn(eligibility);
        }

        @Test
        @DisplayName("should archive user immediately")
        void should_archive_user() {
            service.processDataDeletion(SIGNED_REQUEST);

            verify(userAccountOrchestrator).archiveUser(user);
        }

        @Test
        @DisplayName("should create COMPLETED deletion request record")
        void should_create_completed_record() {
            service.processDataDeletion(SIGNED_REQUEST);

            ArgumentCaptor<PendingDataDeletionRequest> captor =
                    ArgumentCaptor.forClass(PendingDataDeletionRequest.class);
            verify(deletionRequestRepository).save(captor.capture());

            PendingDataDeletionRequest saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(DeletionRequestStatus.COMPLETED);
            assertThat(saved.getCompletedAt()).isNotNull();
            assertThat(saved.getConfirmationCode()).isNotNull();
        }

        @Test
        @DisplayName("should return valid response with url and confirmation code")
        void should_return_valid_response() {
            DataDeletionResponse response = service.processDataDeletion(SIGNED_REQUEST);

            assertThat(response.confirmationCode()).isNotNull();
            assertThat(response.url()).contains("deletion-status?code=");
            assertThat(response.url()).startsWith("https://checkitout.app");
        }

        @Test
        @DisplayName("should mark connection as DISCONNECTED")
        void should_disconnect_connection() {
            service.processDataDeletion(SIGNED_REQUEST);

            verify(socialConnectionRepository).save(argThat(c ->
                    c.getConnectionStatus() == ConnectionStatus.DISCONNECTED));
        }

        @Test
        @DisplayName("should delete Firestore data")
        void should_delete_firestore_data() {
            service.processDataDeletion(SIGNED_REQUEST);

            verify(firestoreService).deleteInstagramUserData(FIREBASE_UID);
        }
    }

    @Nested
    @DisplayName("Case B: Deferred deletion (active collaborations)")
    class DeferredDeletion {

        @BeforeEach
        void setUp() {
            DeletionEligibilityDto eligibility = DeletionEligibilityDto.builder()
                    .canSoftDelete(false)
                    .softDeleteBlockers(Collections.emptyList())
                    .build();
            when(userAccountOrchestrator.checkDeletionEligibilityForUser(eq(user), any(Locale.class)))
                    .thenReturn(eligibility);
        }

        @Test
        @DisplayName("should evict the user cache so TO_BE_DELETED blocks access before the 5-min TTL")
        void should_evict_user_cache() {
            // TO_BE_DELETED is excluded from isUserActive, so it blocks the user -- but only once the
            // cache reflects it. Case A evicts via archiveUser; the deferred path must evict too,
            // otherwise a stale "active" cache entry lets the user keep acting after requesting deletion.
            service.processDataDeletion(SIGNED_REQUEST);

            verify(userCacheService).evict(FIREBASE_UID);
        }

        @Test
        @DisplayName("should NOT archive user")
        void should_not_archive_user() {
            service.processDataDeletion(SIGNED_REQUEST);

            verify(userAccountOrchestrator, never()).archiveUser(any());
        }

        @Test
        @DisplayName("should set user status to TO_BE_DELETED")
        void should_set_status_to_be_deleted() {
            service.processDataDeletion(SIGNED_REQUEST);

            verify(userRepository).save(argThat(u ->
                    u.getAccountStatus() == AccountStatus.TO_BE_DELETED));
        }

        @Test
        @DisplayName("should create PENDING deletion request record")
        void should_create_pending_record() {
            service.processDataDeletion(SIGNED_REQUEST);

            ArgumentCaptor<PendingDataDeletionRequest> captor =
                    ArgumentCaptor.forClass(PendingDataDeletionRequest.class);
            verify(deletionRequestRepository).save(captor.capture());

            PendingDataDeletionRequest saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(DeletionRequestStatus.PENDING);
            assertThat(saved.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("should return valid response")
        void should_return_valid_response() {
            DataDeletionResponse response = service.processDataDeletion(SIGNED_REQUEST);

            assertThat(response.confirmationCode()).isNotNull();
            assertThat(response.url()).contains("deletion-status?code=");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should return dummy response when user not found")
        void should_handle_user_not_found() {
            when(socialConnectionRepository.findByPlatform_NameAndSocialUserId("Instagram", INSTAGRAM_USER_ID))
                    .thenReturn(Optional.empty());

            DataDeletionResponse response = service.processDataDeletion(SIGNED_REQUEST);

            assertThat(response.confirmationCode()).isNotNull();
            assertThat(response.url()).isNotNull();
            verify(userAccountOrchestrator, never()).archiveUser(any());
        }

        @Test
        @DisplayName("should handle already deleted user")
        void should_handle_already_deleted_user() {
            user.setAccountStatus(AccountStatus.TO_BE_DELETED);
            PendingDataDeletionRequest existingRequest = PendingDataDeletionRequest.builder()
                    .confirmationCode("existing-code")
                    .build();
            when(deletionRequestRepository.findByUserId(user.getId()))
                    .thenReturn(Optional.of(existingRequest));

            DataDeletionResponse response = service.processDataDeletion(SIGNED_REQUEST);

            assertThat(response.confirmationCode()).isEqualTo("existing-code");
        }

        @Test
        @DisplayName("should return existing code when connection already DISCONNECTED")
        void should_handle_already_disconnected_connection() {
            connection.setConnectionStatus(ConnectionStatus.DISCONNECTED);
            PendingDataDeletionRequest existingRequest = PendingDataDeletionRequest.builder()
                    .confirmationCode("existing-code-disc")
                    .build();
            when(deletionRequestRepository.findByUserId(user.getId()))
                    .thenReturn(Optional.of(existingRequest));

            DataDeletionResponse response = service.processDataDeletion(SIGNED_REQUEST);

            assertThat(response.confirmationCode()).isEqualTo("existing-code-disc");
            verify(userAccountOrchestrator, never()).archiveUser(any());
            verify(userAccountOrchestrator, never()).checkDeletionEligibilityForUser(any(), any());
        }
    }
}
