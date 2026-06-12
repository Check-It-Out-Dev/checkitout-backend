package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AppliedOpportunityStatusHistoryService.
 * Tests cover:
 * - Status change logging (user context and system context)
 * - Status history retrieval (list and paginated)
 * - Permission validation for all operations
 * - Entity and DTO tests
 * - Edge cases and error handling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppliedOpportunityStatusHistoryService Unit Tests")
class AppliedOpportunityStatusHistoryServiceUnitTest {

    @Mock
    private AppliedOpportunityStatusHistoryRepository statusHistoryRepository;

    @Mock
    private AppliedOpportunityRepository appliedOpportunityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionUtils permissionUtils;

    @InjectMocks
    private AppliedOpportunityStatusHistoryService statusHistoryService;

    // Test fixtures
    private User testInfluencer;
    private User testCompany;
    private User testAdmin;
    private PartnershipOpportunity testPartnershipOpportunity;
    private AppliedOpportunity testAppliedOpportunity;
    private AppliedOpportunityStatusHistory testStatusHistory;
    private static final String INFLUENCER_FIREBASE_ID = "influencer-firebase-uid";
    private static final String COMPANY_FIREBASE_ID = "company-firebase-uid";
    private static final String ADMIN_FIREBASE_ID = "admin-firebase-uid";
    private static final String SYSTEM_FIREBASE_ID = "SYSTEM";

    @BeforeEach
    void setUp() {
        // Create test influencer
        testInfluencer = new User();
        testInfluencer.setId(1L);
        testInfluencer.setFirebaseUserId(INFLUENCER_FIREBASE_ID);
        testInfluencer.setUserType(UserType.INFLUENCER);
        testInfluencer.setFirstName("Test");
        testInfluencer.setLastName("Influencer");
        testInfluencer.setEmail("influencer@test.com");
        testInfluencer.setAccountStatus(AccountStatus.ACTIVE);

        // Create test company
        testCompany = new User();
        testCompany.setId(2L);
        testCompany.setFirebaseUserId(COMPANY_FIREBASE_ID);
        testCompany.setUserType(UserType.COMPANY);
        testCompany.setFirstName("Test");
        testCompany.setLastName("Company");
        testCompany.setEmail("company@test.com");
        testCompany.setAccountStatus(AccountStatus.ACTIVE);

        // Create test admin
        testAdmin = new User();
        testAdmin.setId(3L);
        testAdmin.setFirebaseUserId(ADMIN_FIREBASE_ID);
        testAdmin.setUserType(UserType.ADMIN);
        testAdmin.setFirstName("Test");
        testAdmin.setLastName("Admin");
        testAdmin.setEmail("admin@test.com");
        testAdmin.setAccountStatus(AccountStatus.ACTIVE);

        // Create test partnership opportunity
        testPartnershipOpportunity = new PartnershipOpportunity();
        testPartnershipOpportunity.setId(100L);
        testPartnershipOpportunity.setCompany(testCompany);
        testPartnershipOpportunity.setName("Test Partnership");
        testPartnershipOpportunity.setActive(true);

        // Create test applied opportunity
        testAppliedOpportunity = new AppliedOpportunity();
        testAppliedOpportunity.setId(10L);
        testAppliedOpportunity.setInfluencer(testInfluencer);
        testAppliedOpportunity.setPartnershipOpportunity(testPartnershipOpportunity);
        testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.APPLIED);

        // Create test status history entry
        testStatusHistory = new AppliedOpportunityStatusHistory(
                testAppliedOpportunity,
                OpportunityStatus.APPLIED,
                OpportunityStatus.ACCEPTED_BY_COMPANY,
                testCompany,
                COMPANY_FIREBASE_ID,
                "Company accepted the application"
        );
        testStatusHistory.setId(1L);
    }

    // =====================================================
    // logStatusChange Tests (User Context)
    // =====================================================
    @Nested
    @DisplayName("logStatusChange - User Context")
    class LogStatusChangeUserContext {

        @Test
        @DisplayName("should log status change with user entity when user exists in database")
        void shouldLogStatusChangeWithUserEntity() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> {
                        AppliedOpportunityStatusHistory saved = invocation.getArgument(0);
                        saved.setId(1L);
                        return saved;
                    });

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted"
            );

            assertThat(result).isNotNull();
            assertThat(result.getChangedByUser()).isEqualTo(testCompany);
            assertThat(result.getChangedByFirebaseId()).isEqualTo(COMPANY_FIREBASE_ID);
            assertThat(result.getPreviousStatus()).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(result.getNewStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
            assertThat(result.getChangeReason()).isEqualTo("Company accepted");
            verify(statusHistoryRepository).save(any(AppliedOpportunityStatusHistory.class));
        }

        @Test
        @DisplayName("should log status change with Firebase ID only when user not in database")
        void shouldLogStatusChangeWithFirebaseIdOnly() {
            when(permissionUtils.getUserId()).thenReturn("unknown-firebase-uid");
            when(userRepository.findByFirebaseUserId("unknown-firebase-uid")).thenReturn(Optional.empty());
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> {
                        AppliedOpportunityStatusHistory saved = invocation.getArgument(0);
                        saved.setId(1L);
                        return saved;
                    });

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted"
            );

            assertThat(result).isNotNull();
            assertThat(result.getChangedByUser()).isNull();
            assertThat(result.getChangedByFirebaseId()).isEqualTo("unknown-firebase-uid");
            verify(statusHistoryRepository).save(any(AppliedOpportunityStatusHistory.class));
        }

        @Test
        @DisplayName("should log status change with notes when provided")
        void shouldLogStatusChangeWithNotes() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted",
                    "Additional notes about the decision"
            );

            assertThat(result).isNotNull();
            assertThat(result.getNotes()).isEqualTo("Additional notes about the decision");
        }

        @Test
        @DisplayName("should trim notes when provided with whitespace")
        void shouldTrimNotesWithWhitespace() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted",
                    "   Trimmed notes   "
            );

            assertThat(result).isNotNull();
            assertThat(result.getNotes()).isEqualTo("Trimmed notes");
        }

        @Test
        @DisplayName("should not set notes when empty string provided")
        void shouldNotSetNotesWhenEmptyString() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted",
                    "   "
            );

            assertThat(result).isNotNull();
            assertThat(result.getNotes()).isNull();
        }

        @Test
        @DisplayName("should return null when exception occurs during logging")
        void shouldReturnNullWhenExceptionOccurs() {
            when(permissionUtils.getUserId()).thenThrow(new RuntimeException("Test exception"));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted"
            );

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when repository save fails")
        void shouldReturnNullWhenRepositorySaveFails() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenThrow(new RuntimeException("Database error"));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted"
            );

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should call overloaded method without notes")
        void shouldCallOverloadedMethodWithoutNotes() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted"
            );

            assertThat(result).isNotNull();
            assertThat(result.getNotes()).isNull();
        }

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should handle all status transitions as previous status")
        void shouldHandleAllStatusTransitionsAsPreviousStatus(OpportunityStatus previousStatus) {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    previousStatus,
                    OpportunityStatus.DONE,
                    "Status changed"
            );

            assertThat(result).isNotNull();
            assertThat(result.getPreviousStatus()).isEqualTo(previousStatus);
        }

        @ParameterizedTest
        @EnumSource(OpportunityStatus.class)
        @DisplayName("should handle all status transitions as new status")
        void shouldHandleAllStatusTransitionsAsNewStatus(OpportunityStatus newStatus) {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    newStatus,
                    "Status changed"
            );

            assertThat(result).isNotNull();
            assertThat(result.getNewStatus()).isEqualTo(newStatus);
        }
    }

    // =====================================================
    // logSystemStatusChange Tests
    // =====================================================
    @Nested
    @DisplayName("logSystemStatusChange - System Context")
    class LogSystemStatusChange {

        @Test
        @DisplayName("should log system status change with SYSTEM as changedByFirebaseId")
        void shouldLogSystemStatusChange() {
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> {
                        AppliedOpportunityStatusHistory saved = invocation.getArgument(0);
                        saved.setId(1L);
                        return saved;
                    });

            AppliedOpportunityStatusHistory result = statusHistoryService.logSystemStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.TO_BE_PAID,
                    "Automated payment processing"
            );

            assertThat(result).isNotNull();
            assertThat(result.getChangedByFirebaseId()).isEqualTo(SYSTEM_FIREBASE_ID);
            assertThat(result.getChangedByUser()).isNull();
            assertThat(result.getPreviousStatus()).isEqualTo(OpportunityStatus.CONTENT_POSTED);
            assertThat(result.getNewStatus()).isEqualTo(OpportunityStatus.TO_BE_PAID);
            assertThat(result.getChangeReason()).isEqualTo("Automated payment processing");
        }

        @Test
        @DisplayName("should return null when system status change fails")
        void shouldReturnNullWhenSystemStatusChangeFails() {
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenThrow(new RuntimeException("Database error"));

            AppliedOpportunityStatusHistory result = statusHistoryService.logSystemStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.TO_BE_PAID,
                    "Automated payment processing"
            );

            assertThat(result).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Auto-approved by system", "Scheduled status update", "Batch processing"})
        @DisplayName("should handle various system change reasons")
        void shouldHandleVariousSystemChangeReasons(String reason) {
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logSystemStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.TO_BE_PAID,
                    reason
            );

            assertThat(result).isNotNull();
            assertThat(result.getChangeReason()).isEqualTo(reason);
        }
    }

    // =====================================================
    // getStatusHistory Tests (List)
    // =====================================================
    @Nested
    @DisplayName("getStatusHistory - List")
    class GetStatusHistoryList {

        @Test
        @DisplayName("should return status history when user has permission")
        void shouldReturnStatusHistoryWhenUserHasPermission() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L))
                    .thenReturn(List.of(testStatusHistory));

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(testStatusHistory);
            verify(statusHistoryRepository).findByAppliedOpportunityIdOrderByChangedAtDesc(10L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when applied opportunity not found")
        void shouldThrowResourceNotFoundWhenOpportunityNotFound() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> statusHistoryService.getStatusHistory(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user lacks permission")
        void shouldThrowInsufficientPermissionsWhenNoViewPermission() {
            when(permissionUtils.getUserId()).thenReturn("unauthorized-user");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            assertThatThrownBy(() -> statusHistoryService.getStatusHistory(10L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should return empty list when no history exists")
        void shouldReturnEmptyListWhenNoHistoryExists() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L))
                    .thenReturn(Collections.emptyList());

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return multiple history entries in descending order")
        void shouldReturnMultipleHistoryEntriesInDescendingOrder() {
            AppliedOpportunityStatusHistory history1 = new AppliedOpportunityStatusHistory(
                    testAppliedOpportunity, OpportunityStatus.APPLIED, OpportunityStatus.ACCEPTED_BY_COMPANY,
                    COMPANY_FIREBASE_ID, "First change");
            AppliedOpportunityStatusHistory history2 = new AppliedOpportunityStatusHistory(
                    testAppliedOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY, OpportunityStatus.ACCEPTED_BY_INFLUENCER,
                    INFLUENCER_FIREBASE_ID, "Second change");

            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L))
                    .thenReturn(List.of(history2, history1));

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getNewStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);
            assertThat(result.get(1).getNewStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
        }
    }

    // =====================================================
    // getStatusHistory Tests (Paginated)
    // =====================================================
    @Nested
    @DisplayName("getStatusHistory - Paginated")
    class GetStatusHistoryPaginated {

        @Test
        @DisplayName("should return paginated status history when user has permission")
        void shouldReturnPaginatedStatusHistoryWhenUserHasPermission() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AppliedOpportunityStatusHistory> historyPage = new PageImpl<>(
                    List.of(testStatusHistory), pageable, 1);

            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L, pageable))
                    .thenReturn(historyPage);

            Page<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0)).isEqualTo(testStatusHistory);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for paginated when opportunity not found")
        void shouldThrowResourceNotFoundForPaginatedWhenOpportunityNotFound() {
            Pageable pageable = PageRequest.of(0, 10);
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> statusHistoryService.getStatusHistory(999L, pageable))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException for paginated when user lacks permission")
        void shouldThrowInsufficientPermissionsForPaginatedWhenNoViewPermission() {
            Pageable pageable = PageRequest.of(0, 10);
            when(permissionUtils.getUserId()).thenReturn("unauthorized-user");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            assertThatThrownBy(() -> statusHistoryService.getStatusHistory(10L, pageable))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should return empty page when no history exists")
        void shouldReturnEmptyPageWhenNoHistoryExists() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AppliedOpportunityStatusHistory> emptyPage = new PageImpl<>(
                    Collections.emptyList(), pageable, 0);

            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L, pageable))
                    .thenReturn(emptyPage);

            Page<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @ParameterizedTest
        @CsvSource({"0, 10", "1, 20", "2, 5", "0, 100"})
        @DisplayName("should handle various page configurations")
        void shouldHandleVariousPageConfigurations(int page, int size) {
            Pageable pageable = PageRequest.of(page, size);
            Page<AppliedOpportunityStatusHistory> historyPage = new PageImpl<>(
                    List.of(testStatusHistory), pageable, 1);

            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L, pageable))
                    .thenReturn(historyPage);

            Page<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L, pageable);

            assertThat(result.getSize()).isEqualTo(size);
            assertThat(result.getNumber()).isEqualTo(page);
        }
    }

    // =====================================================
    // getStatusChangesByUser Tests
    // =====================================================
    @Nested
    @DisplayName("getStatusChangesByUser")
    class GetStatusChangesByUser {

        @Test
        @DisplayName("should return status changes when admin queries any user")
        void shouldReturnStatusChangesWhenAdminQueriesAnyUser() {
            when(permissionUtils.getUserId()).thenReturn(ADMIN_FIREBASE_ID);
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(statusHistoryRepository.findByChangedByFirebaseIdOrderByChangedAtDesc(COMPANY_FIREBASE_ID))
                    .thenReturn(List.of(testStatusHistory));

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusChangesByUser(COMPANY_FIREBASE_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(testStatusHistory);
        }

        @Test
        @DisplayName("should return status changes when user queries themselves")
        void shouldReturnStatusChangesWhenUserQueriesThemselves() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(statusHistoryRepository.findByChangedByFirebaseIdOrderByChangedAtDesc(COMPANY_FIREBASE_ID))
                    .thenReturn(List.of(testStatusHistory));

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusChangesByUser(COMPANY_FIREBASE_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-admin queries another user")
        void shouldThrowInsufficientPermissionsWhenNonAdminQueriesOtherUser() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(permissionUtils.isAdmin()).thenReturn(false);

            assertThatThrownBy(() -> statusHistoryService.getStatusChangesByUser(COMPANY_FIREBASE_ID))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should return empty list when user has no status changes")
        void shouldReturnEmptyListWhenUserHasNoStatusChanges() {
            when(permissionUtils.getUserId()).thenReturn(ADMIN_FIREBASE_ID);
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(statusHistoryRepository.findByChangedByFirebaseIdOrderByChangedAtDesc("new-user"))
                    .thenReturn(Collections.emptyList());

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusChangesByUser("new-user");

            assertThat(result).isEmpty();
        }
    }

    // =====================================================
    // getStatusChangesInDateRange Tests
    // =====================================================
    @Nested
    @DisplayName("getStatusChangesInDateRange")
    class GetStatusChangesInDateRange {

        @Test
        @DisplayName("should return status changes in date range when admin")
        void shouldReturnStatusChangesInDateRangeWhenAdmin() {
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);
            LocalDateTime endDate = LocalDateTime.now();

            when(permissionUtils.getUserId()).thenReturn(ADMIN_FIREBASE_ID);
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(statusHistoryRepository.findByChangedAtBetweenOrderByChangedAtDesc(startDate, endDate))
                    .thenReturn(List.of(testStatusHistory));

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusChangesInDateRange(startDate, endDate);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-admin queries date range")
        void shouldThrowInsufficientPermissionsWhenNonAdminQueriesDateRange() {
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);
            LocalDateTime endDate = LocalDateTime.now();

            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(permissionUtils.isAdmin()).thenReturn(false);

            assertThatThrownBy(() -> statusHistoryService.getStatusChangesInDateRange(startDate, endDate))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should return empty list when no changes in date range")
        void shouldReturnEmptyListWhenNoChangesInDateRange() {
            LocalDateTime startDate = LocalDateTime.now().minusDays(1);
            LocalDateTime endDate = LocalDateTime.now();

            when(permissionUtils.getUserId()).thenReturn(ADMIN_FIREBASE_ID);
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(statusHistoryRepository.findByChangedAtBetweenOrderByChangedAtDesc(startDate, endDate))
                    .thenReturn(Collections.emptyList());

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusChangesInDateRange(startDate, endDate);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should handle same start and end date")
        void shouldHandleSameStartAndEndDate() {
            LocalDateTime sameDate = LocalDateTime.now();

            when(permissionUtils.getUserId()).thenReturn(ADMIN_FIREBASE_ID);
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(statusHistoryRepository.findByChangedAtBetweenOrderByChangedAtDesc(sameDate, sameDate))
                    .thenReturn(Collections.emptyList());

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusChangesInDateRange(sameDate, sameDate);

            assertThat(result).isEmpty();
        }
    }

    // =====================================================
    // getLastStatusChange Tests
    // =====================================================
    @Nested
    @DisplayName("getLastStatusChange")
    class GetLastStatusChange {

        @Test
        @DisplayName("should return last status change when exists and user has permission")
        void shouldReturnLastStatusChangeWhenExistsAndUserHasPermission() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findFirstByAppliedOpportunityIdOrderByChangedAtDesc(10L))
                    .thenReturn(testStatusHistory);

            Optional<AppliedOpportunityStatusHistory> result = statusHistoryService.getLastStatusChange(10L);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(testStatusHistory);
        }

        @Test
        @DisplayName("should return empty Optional when no history exists")
        void shouldReturnEmptyOptionalWhenNoHistoryExists() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findFirstByAppliedOpportunityIdOrderByChangedAtDesc(10L))
                    .thenReturn(null);

            Optional<AppliedOpportunityStatusHistory> result = statusHistoryService.getLastStatusChange(10L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when opportunity not found")
        void shouldThrowResourceNotFoundWhenOpportunityNotFound() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> statusHistoryService.getLastStatusChange(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user lacks permission")
        void shouldThrowInsufficientPermissionsWhenNoViewPermission() {
            when(permissionUtils.getUserId()).thenReturn("unauthorized-user");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            assertThatThrownBy(() -> statusHistoryService.getLastStatusChange(10L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =====================================================
    // countStatusChanges Tests
    // =====================================================
    @Nested
    @DisplayName("countStatusChanges")
    class CountStatusChanges {

        @Test
        @DisplayName("should return count when user has permission")
        void shouldReturnCountWhenUserHasPermission() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.countByAppliedOpportunityId(10L)).thenReturn(5L);

            long result = statusHistoryService.countStatusChanges(10L);

            assertThat(result).isEqualTo(5L);
        }

        @Test
        @DisplayName("should return zero when no history exists")
        void shouldReturnZeroWhenNoHistoryExists() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.countByAppliedOpportunityId(10L)).thenReturn(0L);

            long result = statusHistoryService.countStatusChanges(10L);

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when opportunity not found")
        void shouldThrowResourceNotFoundWhenOpportunityNotFound() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> statusHistoryService.countStatusChanges(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user lacks permission")
        void shouldThrowInsufficientPermissionsWhenNoViewPermission() {
            when(permissionUtils.getUserId()).thenReturn("unauthorized-user");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            assertThatThrownBy(() -> statusHistoryService.countStatusChanges(10L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 10L, 100L, 1000L})
        @DisplayName("should return various count values")
        void shouldReturnVariousCountValues(long count) {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.countByAppliedOpportunityId(10L)).thenReturn(count);

            long result = statusHistoryService.countStatusChanges(10L);

            assertThat(result).isEqualTo(count);
        }
    }

    // =====================================================
    // AppliedOpportunityStatusHistory Entity Tests
    // =====================================================
    @Nested
    @DisplayName("AppliedOpportunityStatusHistory Entity")
    class AppliedOpportunityStatusHistoryEntityTests {

        @Test
        @DisplayName("should create entity with all args constructor")
        void shouldCreateEntityWithAllArgsConstructor() {
            LocalDateTime changedAt = LocalDateTime.now();
            AppliedOpportunityStatusHistory entity = new AppliedOpportunityStatusHistory(
                    1L, testAppliedOpportunity, OpportunityStatus.APPLIED, OpportunityStatus.ACCEPTED_BY_COMPANY,
                    testCompany, COMPANY_FIREBASE_ID, changedAt, "Test reason", "Test notes"
            );

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getAppliedOpportunity()).isEqualTo(testAppliedOpportunity);
            assertThat(entity.getPreviousStatus()).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(entity.getNewStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
            assertThat(entity.getChangedByUser()).isEqualTo(testCompany);
            assertThat(entity.getChangedByFirebaseId()).isEqualTo(COMPANY_FIREBASE_ID);
            assertThat(entity.getChangedAt()).isEqualTo(changedAt);
            assertThat(entity.getChangeReason()).isEqualTo("Test reason");
            assertThat(entity.getNotes()).isEqualTo("Test notes");
        }

        @Test
        @DisplayName("should create entity with no args constructor")
        void shouldCreateEntityWithNoArgsConstructor() {
            AppliedOpportunityStatusHistory entity = new AppliedOpportunityStatusHistory();

            assertThat(entity.getId()).isNull();
            assertThat(entity.getAppliedOpportunity()).isNull();
            assertThat(entity.getPreviousStatus()).isNull();
            assertThat(entity.getNewStatus()).isNull();
            assertThat(entity.getChangedByUser()).isNull();
            assertThat(entity.getChangedByFirebaseId()).isNull();
        }

        @Test
        @DisplayName("should create entity with user convenience constructor")
        void shouldCreateEntityWithUserConvenienceConstructor() {
            AppliedOpportunityStatusHistory entity = new AppliedOpportunityStatusHistory(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    testCompany,
                    COMPANY_FIREBASE_ID,
                    "Company accepted"
            );

            assertThat(entity.getAppliedOpportunity()).isEqualTo(testAppliedOpportunity);
            assertThat(entity.getPreviousStatus()).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(entity.getNewStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
            assertThat(entity.getChangedByUser()).isEqualTo(testCompany);
            assertThat(entity.getChangedByFirebaseId()).isEqualTo(COMPANY_FIREBASE_ID);
            assertThat(entity.getChangeReason()).isEqualTo("Company accepted");
            assertThat(entity.getChangedAt()).isNotNull();
        }

        @Test
        @DisplayName("should create entity with firebase-only convenience constructor")
        void shouldCreateEntityWithFirebaseOnlyConvenienceConstructor() {
            AppliedOpportunityStatusHistory entity = new AppliedOpportunityStatusHistory(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    COMPANY_FIREBASE_ID,
                    "Company accepted"
            );

            assertThat(entity.getAppliedOpportunity()).isEqualTo(testAppliedOpportunity);
            assertThat(entity.getPreviousStatus()).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(entity.getNewStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
            assertThat(entity.getChangedByUser()).isNull();
            assertThat(entity.getChangedByFirebaseId()).isEqualTo(COMPANY_FIREBASE_ID);
            assertThat(entity.getChangeReason()).isEqualTo("Company accepted");
            assertThat(entity.getChangedAt()).isNotNull();
        }

        @Test
        @DisplayName("should set and get all fields via setters")
        void shouldSetAndGetAllFieldsViaSetters() {
            AppliedOpportunityStatusHistory entity = new AppliedOpportunityStatusHistory();
            LocalDateTime now = LocalDateTime.now();

            entity.setId(100L);
            entity.setAppliedOpportunity(testAppliedOpportunity);
            entity.setPreviousStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            entity.setNewStatus(OpportunityStatus.CONTENT_APPROVED);
            entity.setChangedByUser(testCompany);
            entity.setChangedByFirebaseId(COMPANY_FIREBASE_ID);
            entity.setChangedAt(now);
            entity.setChangeReason("Updated reason");
            entity.setNotes("Updated notes");

            assertThat(entity.getId()).isEqualTo(100L);
            assertThat(entity.getAppliedOpportunity()).isEqualTo(testAppliedOpportunity);
            assertThat(entity.getPreviousStatus()).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            assertThat(entity.getNewStatus()).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
            assertThat(entity.getChangedByUser()).isEqualTo(testCompany);
            assertThat(entity.getChangedByFirebaseId()).isEqualTo(COMPANY_FIREBASE_ID);
            assertThat(entity.getChangedAt()).isEqualTo(now);
            assertThat(entity.getChangeReason()).isEqualTo("Updated reason");
            assertThat(entity.getNotes()).isEqualTo("Updated notes");
        }

        @Test
        @DisplayName("should allow null previous status")
        void shouldAllowNullPreviousStatus() {
            AppliedOpportunityStatusHistory entity = new AppliedOpportunityStatusHistory(
                    testAppliedOpportunity,
                    null,
                    OpportunityStatus.APPLIED,
                    COMPANY_FIREBASE_ID,
                    "Initial application"
            );

            assertThat(entity.getPreviousStatus()).isNull();
            assertThat(entity.getNewStatus()).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("should handle long change reason")
        void shouldHandleLongChangeReason() {
            String longReason = "a".repeat(500);
            AppliedOpportunityStatusHistory entity = new AppliedOpportunityStatusHistory();
            entity.setChangeReason(longReason);

            assertThat(entity.getChangeReason()).hasSize(500);
        }

        @Test
        @DisplayName("should handle long notes")
        void shouldHandleLongNotes() {
            String longNotes = "n".repeat(1000);
            AppliedOpportunityStatusHistory entity = new AppliedOpportunityStatusHistory();
            entity.setNotes(longNotes);

            assertThat(entity.getNotes()).hasSize(1000);
        }
    }

    // =====================================================
    // AppliedOpportunityStatusHistoryDtoOut Tests
    // =====================================================
    @Nested
    @DisplayName("AppliedOpportunityStatusHistoryDtoOut")
    class AppliedOpportunityStatusHistoryDtoOutTests {

        @Test
        @DisplayName("should create DTO from entity with user")
        void shouldCreateDtoFromEntityWithUser() {
            testStatusHistory.setId(1L);
            testStatusHistory.setNotes("Test notes");

            AppliedOpportunityStatusHistoryDtoOut dto = AppliedOpportunityStatusHistoryDtoOut.fromEntity(testStatusHistory);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getAppliedOpportunityId()).isEqualTo(10L);
            assertThat(dto.getPreviousStatus()).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(dto.getNewStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
            assertThat(dto.getChangedByUserId()).isEqualTo(testCompany.getId());
            assertThat(dto.getChangedByFirebaseId()).isEqualTo(COMPANY_FIREBASE_ID);
            assertThat(dto.getChangedByUserName()).isEqualTo("Test Company");
            assertThat(dto.getChangeReason()).isEqualTo("Company accepted the application");
            assertThat(dto.getNotes()).isEqualTo("Test notes");
        }

        @Test
        @DisplayName("should create DTO from entity without user as System")
        void shouldCreateDtoFromEntityWithoutUserAsSystem() {
            AppliedOpportunityStatusHistory systemHistory = new AppliedOpportunityStatusHistory(
                    testAppliedOpportunity,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.TO_BE_PAID,
                    SYSTEM_FIREBASE_ID,
                    "Automated processing"
            );
            systemHistory.setId(2L);

            AppliedOpportunityStatusHistoryDtoOut dto = AppliedOpportunityStatusHistoryDtoOut.fromEntity(systemHistory);

            assertThat(dto.getChangedByUserId()).isNull();
            assertThat(dto.getChangedByFirebaseId()).isEqualTo(SYSTEM_FIREBASE_ID);
            assertThat(dto.getChangedByUserName()).isEqualTo("System");
        }

        @Test
        @DisplayName("should create DTO from entity without user as Unknown User")
        void shouldCreateDtoFromEntityWithoutUserAsUnknownUser() {
            AppliedOpportunityStatusHistory unknownHistory = new AppliedOpportunityStatusHistory(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "unknown-firebase-id",
                    "Unknown user action"
            );
            unknownHistory.setId(3L);

            AppliedOpportunityStatusHistoryDtoOut dto = AppliedOpportunityStatusHistoryDtoOut.fromEntity(unknownHistory);

            assertThat(dto.getChangedByUserId()).isNull();
            assertThat(dto.getChangedByFirebaseId()).isEqualTo("unknown-firebase-id");
            assertThat(dto.getChangedByUserName()).isEqualTo("Unknown User");
        }

        @Test
        @DisplayName("should create DTO with all args constructor")
        void shouldCreateDtoWithAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();
            AppliedOpportunityStatusHistoryDtoOut dto = new AppliedOpportunityStatusHistoryDtoOut(
                    1L, 10L, OpportunityStatus.APPLIED, OpportunityStatus.ACCEPTED_BY_COMPANY,
                    2L, COMPANY_FIREBASE_ID, "Test Company", now, "Test reason", "Test notes"
            );

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getAppliedOpportunityId()).isEqualTo(10L);
            assertThat(dto.getPreviousStatus()).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(dto.getNewStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
            assertThat(dto.getChangedByUserId()).isEqualTo(2L);
            assertThat(dto.getChangedByFirebaseId()).isEqualTo(COMPANY_FIREBASE_ID);
            assertThat(dto.getChangedByUserName()).isEqualTo("Test Company");
            assertThat(dto.getChangedAt()).isEqualTo(now);
            assertThat(dto.getChangeReason()).isEqualTo("Test reason");
            assertThat(dto.getNotes()).isEqualTo("Test notes");
        }

        @Test
        @DisplayName("should create DTO with no args constructor")
        void shouldCreateDtoWithNoArgsConstructor() {
            AppliedOpportunityStatusHistoryDtoOut dto = new AppliedOpportunityStatusHistoryDtoOut();

            assertThat(dto.getId()).isNull();
            assertThat(dto.getAppliedOpportunityId()).isNull();
            assertThat(dto.getPreviousStatus()).isNull();
            assertThat(dto.getNewStatus()).isNull();
        }

        @Test
        @DisplayName("should set and get all fields via setters")
        void shouldSetAndGetAllFieldsViaSetters() {
            AppliedOpportunityStatusHistoryDtoOut dto = new AppliedOpportunityStatusHistoryDtoOut();
            LocalDateTime now = LocalDateTime.now();

            dto.setId(5L);
            dto.setAppliedOpportunityId(20L);
            dto.setPreviousStatus(OpportunityStatus.CONTENT_APPROVED);
            dto.setNewStatus(OpportunityStatus.CONTENT_POSTED);
            dto.setChangedByUserId(3L);
            dto.setChangedByFirebaseId("test-firebase-id");
            dto.setChangedByUserName("Test User");
            dto.setChangedAt(now);
            dto.setChangeReason("Test reason");
            dto.setNotes("Test notes");

            assertThat(dto.getId()).isEqualTo(5L);
            assertThat(dto.getAppliedOpportunityId()).isEqualTo(20L);
            assertThat(dto.getPreviousStatus()).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
            assertThat(dto.getNewStatus()).isEqualTo(OpportunityStatus.CONTENT_POSTED);
            assertThat(dto.getChangedByUserId()).isEqualTo(3L);
            assertThat(dto.getChangedByFirebaseId()).isEqualTo("test-firebase-id");
            assertThat(dto.getChangedByUserName()).isEqualTo("Test User");
            assertThat(dto.getChangedAt()).isEqualTo(now);
            assertThat(dto.getChangeReason()).isEqualTo("Test reason");
            assertThat(dto.getNotes()).isEqualTo("Test notes");
        }

        @Test
        @DisplayName("should handle null notes in DTO")
        void shouldHandleNullNotesInDto() {
            testStatusHistory.setNotes(null);
            AppliedOpportunityStatusHistoryDtoOut dto = AppliedOpportunityStatusHistoryDtoOut.fromEntity(testStatusHistory);

            assertThat(dto.getNotes()).isNull();
        }

        @Test
        @DisplayName("should handle null change reason in DTO")
        void shouldHandleNullChangeReasonInDto() {
            testStatusHistory.setChangeReason(null);
            AppliedOpportunityStatusHistoryDtoOut dto = AppliedOpportunityStatusHistoryDtoOut.fromEntity(testStatusHistory);

            assertThat(dto.getChangeReason()).isNull();
        }
    }

    // =====================================================
    // Service Constants Tests
    // =====================================================
    @Nested
    @DisplayName("Service Constants")
    class ServiceConstantsTests {

        @Test
        @DisplayName("should have correct constant for applied opportunity not found message")
        void shouldHaveCorrectConstantForNotFoundMessage() {
            assertThat(AppliedOpportunityStatusHistoryService.APPLIED_OPPORTUNITY_NOT_FOUND)
                    .isEqualTo("Applied opportunity not found");
        }
    }

    // =====================================================
    // Edge Cases and Error Handling Tests
    // =====================================================
    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("should handle null notes parameter gracefully")
        void shouldHandleNullNotesParameterGracefully() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted",
                    null
            );

            assertThat(result).isNotNull();
            assertThat(result.getNotes()).isNull();
        }

        @Test
        @DisplayName("should handle concurrent access scenarios")
        void shouldHandleConcurrentAccessScenarios() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> {
                        AppliedOpportunityStatusHistory saved = invocation.getArgument(0);
                        saved.setId(System.nanoTime());
                        return saved;
                    });

            // Simulate multiple concurrent calls
            List<AppliedOpportunityStatusHistory> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                        testAppliedOpportunity,
                        OpportunityStatus.APPLIED,
                        OpportunityStatus.ACCEPTED_BY_COMPANY,
                        "Concurrent change " + i
                );
                results.add(result);
            }

            assertThat(results).hasSize(5);
            assertThat(results).allMatch(r -> r != null && r.getId() != null);
        }

        @Test
        @DisplayName("should properly handle special characters in change reason")
        void shouldHandleSpecialCharactersInChangeReason() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            String specialReason = "Reason with special chars: <>&\"'{}[]|\\`~!@#$%^&*()";
            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    specialReason
            );

            assertThat(result).isNotNull();
            assertThat(result.getChangeReason()).isEqualTo(specialReason);
        }

        @Test
        @DisplayName("should handle unicode characters in notes")
        void shouldHandleUnicodeCharactersInNotes() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            String unicodeNotes = "Unicode notes: Zdrowy tekst po polsku";
            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted",
                    unicodeNotes
            );

            assertThat(result).isNotNull();
            assertThat(result.getNotes()).isEqualTo(unicodeNotes);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should handle blank notes variations")
        void shouldHandleBlankNotesVariations(String notes) {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(COMPANY_FIREBASE_ID)).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    "Company accepted",
                    notes
            );

            assertThat(result).isNotNull();
            assertThat(result.getNotes()).isNull();
        }
    }

    // =====================================================
    // Permission Integration Tests
    // =====================================================
    @Nested
    @DisplayName("Permission Integration")
    class PermissionIntegration {

        @Test
        @DisplayName("should allow influencer to view their own opportunity history")
        void shouldAllowInfluencerToViewOwnOpportunityHistory() {
            when(permissionUtils.getUserId()).thenReturn(INFLUENCER_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L))
                    .thenReturn(List.of(testStatusHistory));

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L);

            assertThat(result).isNotEmpty();
            verify(permissionUtils).canViewAppliedOpportunity(testAppliedOpportunity);
        }

        @Test
        @DisplayName("should allow company to view opportunity history for their partnership")
        void shouldAllowCompanyToViewOpportunityHistoryForTheirPartnership() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L))
                    .thenReturn(List.of(testStatusHistory));

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should allow admin to view any opportunity history")
        void shouldAllowAdminToViewAnyOpportunityHistory() {
            when(permissionUtils.getUserId()).thenReturn(ADMIN_FIREBASE_ID);
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(true);
            when(statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(10L))
                    .thenReturn(List.of(testStatusHistory));

            List<AppliedOpportunityStatusHistory> result = statusHistoryService.getStatusHistory(10L);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should deny access when user not authorized")
        void shouldDenyAccessWhenUserNotAuthorized() {
            when(permissionUtils.getUserId()).thenReturn("random-unauthorized-user");
            when(appliedOpportunityRepository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));
            when(permissionUtils.canViewAppliedOpportunity(testAppliedOpportunity)).thenReturn(false);

            assertThatThrownBy(() -> statusHistoryService.getStatusHistory(10L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =====================================================
    // Status Transition Workflow Tests
    // =====================================================
    @Nested
    @DisplayName("Status Transition Workflow")
    class StatusTransitionWorkflow {

        @Test
        @DisplayName("should log complete happy path workflow")
        void shouldLogCompleteHappyPathWorkflow() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(anyString())).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Simulate complete workflow
            OpportunityStatus[] statuses = {
                    OpportunityStatus.APPLIED,
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.ACCEPTED_BY_INFLUENCER,
                    OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
                    OpportunityStatus.CONTENT_APPROVED,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.TO_BE_PAID,
                    OpportunityStatus.DONE
            };

            for (int i = 0; i < statuses.length - 1; i++) {
                AppliedOpportunityStatusHistory result = statusHistoryService.logStatusChange(
                        testAppliedOpportunity,
                        statuses[i],
                        statuses[i + 1],
                        "Transition " + (i + 1)
                );
                assertThat(result).isNotNull();
                assertThat(result.getPreviousStatus()).isEqualTo(statuses[i]);
                assertThat(result.getNewStatus()).isEqualTo(statuses[i + 1]);
            }

            verify(statusHistoryRepository, times(7)).save(any(AppliedOpportunityStatusHistory.class));
        }

        @Test
        @DisplayName("should log content rejection and resubmission workflow")
        void shouldLogContentRejectionAndResubmissionWorkflow() {
            when(permissionUtils.getUserId()).thenReturn(COMPANY_FIREBASE_ID);
            when(userRepository.findByFirebaseUserId(anyString())).thenReturn(Optional.of(testCompany));
            when(statusHistoryRepository.save(any(AppliedOpportunityStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Content rejected
            AppliedOpportunityStatusHistory rejection = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
                    OpportunityStatus.CONTENT_REJECTED,
                    "Content needs revision"
            );

            // Content resubmitted
            AppliedOpportunityStatusHistory resubmission = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.CONTENT_REJECTED,
                    OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
                    "Content resubmitted"
            );

            // Content approved
            AppliedOpportunityStatusHistory approval = statusHistoryService.logStatusChange(
                    testAppliedOpportunity,
                    OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
                    OpportunityStatus.CONTENT_APPROVED,
                    "Content approved"
            );

            assertThat(rejection.getNewStatus()).isEqualTo(OpportunityStatus.CONTENT_REJECTED);
            assertThat(resubmission.getNewStatus()).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            assertThat(approval.getNewStatus()).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
        }
    }
}
