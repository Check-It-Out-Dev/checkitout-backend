package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.cron.DeferredDeletionCronJob;
import com.sm.instagram.platform.auth.entity.DeletionRequestStatus;
import com.sm.instagram.platform.auth.entity.PendingDataDeletionRequest;
import com.sm.instagram.platform.auth.repository.PendingDataDeletionRequestRepository;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserAccountOrchestrator;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DeferredDeletionCronJob Unit Tests")
class DeferredDeletionCronJobUnitTest {

    @Mock private PendingDataDeletionRequestRepository deletionRequestRepository;
    @Mock private UserAccountOrchestrator userAccountOrchestrator;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private DeferredDeletionCronJob cronJob;

    private User user;
    private PendingDataDeletionRequest pendingRequest;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cronJob, "enabled", true);

        // Make TransactionTemplate.executeWithoutResult() actually run the callback
        doAnswer(invocation -> {
            Consumer<Object> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        user = new User();
        user.setId(1L);
        user.setFirebaseUserId("firebase-uid-123");

        pendingRequest = PendingDataDeletionRequest.builder()
                .id(100L)
                .user(user)
                .confirmationCode("test-code-123")
                .status(DeletionRequestStatus.PENDING)
                .requestedAt(LocalDateTime.now().minusDays(5))
                .blockers("[{\"category\":\"ACTIVE_OPPORTUNITIES\"}]")
                .build();
    }

    @Nested
    @DisplayName("processDeferredDeletions")
    class ProcessDeferredDeletions {

        @Test
        @DisplayName("should skip when disabled")
        void should_skip_when_disabled() {
            ReflectionTestUtils.setField(cronJob, "enabled", false);

            cronJob.processDeferredDeletions();

            verifyNoInteractions(deletionRequestRepository);
        }

        @Test
        @DisplayName("should do nothing when no pending requests")
        void should_do_nothing_when_empty() {
            when(deletionRequestRepository.findAllByStatus(DeletionRequestStatus.PENDING))
                    .thenReturn(Collections.emptyList());

            cronJob.processDeferredDeletions();

            verify(userAccountOrchestrator, never()).archiveUser(any());
        }

        @Test
        @DisplayName("should archive user when collaborations are cleared")
        void should_archive_when_eligible() {
            when(deletionRequestRepository.findAllByStatus(DeletionRequestStatus.PENDING))
                    .thenReturn(List.of(pendingRequest));
            DeletionEligibilityDto eligible = DeletionEligibilityDto.builder()
                    .canSoftDelete(true)
                    .softDeleteBlockers(Collections.emptyList())
                    .build();
            when(userAccountOrchestrator.checkDeletionEligibilityForUser(eq(user), any(Locale.class)))
                    .thenReturn(eligible);

            cronJob.processDeferredDeletions();

            verify(userAccountOrchestrator).archiveUser(user);
            verify(deletionRequestRepository).save(argThat(req -> {
                assertThat(req.getStatus()).isEqualTo(DeletionRequestStatus.COMPLETED);
                assertThat(req.getCompletedAt()).isNotNull();
                assertThat(req.getBlockers()).isNull();
                return true;
            }));
        }

        @Test
        @DisplayName("should leave PENDING when collaborations still active")
        void should_leave_pending_when_blocked() {
            when(deletionRequestRepository.findAllByStatus(DeletionRequestStatus.PENDING))
                    .thenReturn(List.of(pendingRequest));
            DeletionEligibilityDto blocked = DeletionEligibilityDto.builder()
                    .canSoftDelete(false)
                    .softDeleteBlockers(Collections.emptyList())
                    .build();
            when(userAccountOrchestrator.checkDeletionEligibilityForUser(eq(user), any(Locale.class)))
                    .thenReturn(blocked);

            cronJob.processDeferredDeletions();

            verify(userAccountOrchestrator, never()).archiveUser(any());
            verify(deletionRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("should continue processing other requests when one fails")
        void should_continue_on_error() {
            User user2 = new User();
            user2.setId(2L);
            user2.setFirebaseUserId("firebase-uid-456");

            PendingDataDeletionRequest request2 = PendingDataDeletionRequest.builder()
                    .id(101L)
                    .user(user2)
                    .confirmationCode("test-code-456")
                    .status(DeletionRequestStatus.PENDING)
                    .requestedAt(LocalDateTime.now().minusDays(3))
                    .build();

            when(deletionRequestRepository.findAllByStatus(DeletionRequestStatus.PENDING))
                    .thenReturn(List.of(pendingRequest, request2));

            // First request fails
            when(userAccountOrchestrator.checkDeletionEligibilityForUser(eq(user), any(Locale.class)))
                    .thenThrow(new RuntimeException("DB error"));

            // Second request succeeds
            DeletionEligibilityDto eligible = DeletionEligibilityDto.builder()
                    .canSoftDelete(true)
                    .softDeleteBlockers(Collections.emptyList())
                    .build();
            when(userAccountOrchestrator.checkDeletionEligibilityForUser(eq(user2), any(Locale.class)))
                    .thenReturn(eligible);

            cronJob.processDeferredDeletions();

            // Second request should still be processed
            verify(userAccountOrchestrator).archiveUser(user2);
        }
    }
}
