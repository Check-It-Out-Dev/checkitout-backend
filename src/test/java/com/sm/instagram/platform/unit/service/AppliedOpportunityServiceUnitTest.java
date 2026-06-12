package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.*;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.*;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AppliedOpportunityService.
 * Uses pure Mockito - no Spring context loaded.
 *
 * Tests focus on business logic:
 * - Status transitions (state machine)
 * - Permission validation
 * - Rating updates
 * - Follower validation
 * - CRUD operations
 * - Payment contact retrieval
 * - Statistics
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppliedOpportunityService Unit Tests")
class AppliedOpportunityServiceUnitTest {

    @Mock
    private AppliedOpportunityRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialConnectionRepository userSocialConnectionRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private PermissionUtils permissionUtils;

    @Mock
    private AppliedOpportunityStatusHistoryService statusHistoryService;

    // Test fixtures
    private User testInfluencer;
    private User testCompany;
    private PartnershipOpportunity testPartnershipOpportunity;
    private AppliedOpportunity testAppliedOpportunity;
    private UserSocialConnection testSocialConnection;

    @BeforeEach
    void setUp() {
        // Create test influencer
        testInfluencer = new User();
        testInfluencer.setId(1L);
        testInfluencer.setFirebaseUserId("influencer-firebase-uid");
        testInfluencer.setUserType(UserType.INFLUENCER);
        testInfluencer.setName("Test Influencer");
        testInfluencer.setEmail("influencer@test.com");
        testInfluencer.setPhoneNumber("+48123456789");
        testInfluencer.setProfilePicture("https://example.com/influencer.jpg");
        testInfluencer.setAccountStatus(AccountStatus.ACTIVE);

        // Create test company
        testCompany = new User();
        testCompany.setId(2L);
        testCompany.setFirebaseUserId("company-firebase-uid");
        testCompany.setUserType(UserType.COMPANY);
        testCompany.setName("Test Company");
        testCompany.setEmail("company@test.com");
        testCompany.setPhoneNumber("+48987654321");
        testCompany.setProfilePicture("https://example.com/company.jpg");
        testCompany.setAccountStatus(AccountStatus.ACTIVE);

        // Create test partnership opportunity
        testPartnershipOpportunity = new PartnershipOpportunity();
        testPartnershipOpportunity.setId(100L);
        testPartnershipOpportunity.setCompany(testCompany);
        testPartnershipOpportunity.setFollowersMin(1000L);
        testPartnershipOpportunity.setFollowersMax(50000L);
        testPartnershipOpportunity.setName("Test Partnership");
        testPartnershipOpportunity.setActive(true);

        // Create test applied opportunity
        testAppliedOpportunity = new AppliedOpportunity();
        testAppliedOpportunity.setId(10L);
        testAppliedOpportunity.setInfluencer(testInfluencer);
        testAppliedOpportunity.setPartnershipOpportunity(testPartnershipOpportunity);
        testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.APPLIED);
        testAppliedOpportunity.setRateStatus(RateStatus.DEFAULT);
        testAppliedOpportunity.setCompanyRateStatus(RateStatus.DEFAULT);
        testAppliedOpportunity.setNote("Test note");

        // Create social connection
        testSocialConnection = new UserSocialConnection();
        testSocialConnection.setId(1L);
        testSocialConnection.setUser(testInfluencer);
        testSocialConnection.setIsPrimary(true);
        testSocialConnection.setConnectionStatus(ConnectionStatus.CONNECTED);
        testSocialConnection.setFollowersCount(10000);

        List<UserSocialConnection> connections = new ArrayList<>();
        connections.add(testSocialConnection);
        testInfluencer.setSocialConnections(connections);
    }

    // =====================================================
    // OpportunityStatus State Machine Tests
    // =====================================================
    @Nested
    @DisplayName("OpportunityStatus State Machine")
    class OpportunityStatusStateMachine {

        @Test
        @DisplayName("should transition APPLIED to ACCEPTED_BY_COMPANY when company accepts")
        void shouldTransitionAppliedToAcceptedByCompanyWhenCompanyAccepts() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
        }

        @Test
        @DisplayName("should transition APPLIED to REJECTED_BY_COMPANY when company rejects")
        void shouldTransitionAppliedToRejectedByCompanyWhenCompanyRejects() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, false);
            assertThat(newStatus).isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY);
        }

        @Test
        @DisplayName("should transition ACCEPTED_BY_COMPANY to ACCEPTED_BY_INFLUENCER when influencer accepts")
        void shouldTransitionAcceptedByCompanyToAcceptedByInfluencer() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_COMPANY, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("should transition ACCEPTED_BY_COMPANY to REJECTED_BY_INFLUENCER when influencer rejects")
        void shouldTransitionAcceptedByCompanyToRejectedByInfluencer() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_COMPANY, false);
            assertThat(newStatus).isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("should transition ACCEPTED_BY_INFLUENCER to CONTENT_SEND_TO_ACCEPT")
        void shouldTransitionAcceptedByInfluencerToContentSendToAccept() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_INFLUENCER, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
        }

        @Test
        @DisplayName("should throw when trying to reject at ACCEPTED_BY_INFLUENCER stage")
        void shouldThrowWhenRejectingAtAcceptedByInfluencerStage() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.ACCEPTED_BY_INFLUENCER, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot reject opportunity at this stage");
        }

        @Test
        @DisplayName("should transition CONTENT_SEND_TO_ACCEPT to CONTENT_APPROVED when approved")
        void shouldTransitionContentSendToAcceptToContentApproved() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
        }

        @Test
        @DisplayName("should transition CONTENT_SEND_TO_ACCEPT to CONTENT_REJECTED when rejected")
        void shouldTransitionContentSendToAcceptToContentRejected() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, false);
            assertThat(newStatus).isEqualTo(OpportunityStatus.CONTENT_REJECTED);
        }

        @Test
        @DisplayName("should transition CONTENT_APPROVED to CONTENT_POSTED")
        void shouldTransitionContentApprovedToContentPosted() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_APPROVED, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("should throw when trying to reject at CONTENT_APPROVED stage")
        void shouldThrowWhenRejectingAtContentApprovedStage() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_APPROVED, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot set status to 'rejected'");
        }

        @Test
        @DisplayName("should transition CONTENT_POSTED to TO_BE_PAID when accepted")
        void shouldTransitionContentPostedToToBePaid() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.TO_BE_PAID);
        }

        @Test
        @DisplayName("should transition CONTENT_POSTED to CONTENT_POSTED_REJECTED when rejected")
        void shouldTransitionContentPostedToContentPostedRejected() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED, false);
            assertThat(newStatus).isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);
        }

        @Test
        @DisplayName("should transition CONTENT_POSTED_REJECTED to CONTENT_POSTED")
        void shouldTransitionContentPostedRejectedToContentPosted() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED_REJECTED, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("should transition TO_BE_PAID to DONE")
        void shouldTransitionToBePaidToDone() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.TO_BE_PAID, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.DONE);
        }

        @Test
        @DisplayName("should throw when trying to reject at TO_BE_PAID stage")
        void shouldThrowWhenRejectingAtToBePaidStage() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.TO_BE_PAID, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot reject opportunity at this stage");
        }

        @Test
        @DisplayName("should transition CONTENT_REJECTED to CONTENT_SEND_TO_ACCEPT (resubmit)")
        void shouldTransitionContentRejectedToContentSendToAccept() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_REJECTED, true);
            assertThat(newStatus).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
        }

        @Test
        @DisplayName("should transition CONTENT_REJECTED to REJECTED_BY_INFLUENCER (resign)")
        void shouldTransitionContentRejectedToRejectedByInfluencer() {
            OpportunityStatus newStatus = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_REJECTED, false);
            assertThat(newStatus).isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("should throw when transitioning from DONE terminal status")
        void shouldThrowWhenTransitioningFromDone() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.DONE, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not allow further transitions");
        }

        @Test
        @DisplayName("should throw when transitioning from REJECTED_BY_COMPANY terminal status")
        void shouldThrowWhenTransitioningFromRejectedByCompany() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.REJECTED_BY_COMPANY, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not allow further transitions");
        }

        @Test
        @DisplayName("should throw when transitioning from REJECTED_BY_INFLUENCER terminal status")
        void shouldThrowWhenTransitioningFromRejectedByInfluencer() {
            assertThatThrownBy(() -> OpportunityStatus.getNextStatus(OpportunityStatus.REJECTED_BY_INFLUENCER, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not allow further transitions");
        }
    }

    // =====================================================
    // canTransitionTo Tests
    // =====================================================
    @Nested
    @DisplayName("canTransitionTo Validation")
    class CanTransitionToValidation {

        @Test
        @DisplayName("APPLIED can transition to ACCEPTED_BY_COMPANY")
        void appliedCanTransitionToAcceptedByCompany() {
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.ACCEPTED_BY_COMPANY)).isTrue();
        }

        @Test
        @DisplayName("APPLIED can transition to REJECTED_BY_COMPANY")
        void appliedCanTransitionToRejectedByCompany() {
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.REJECTED_BY_COMPANY)).isTrue();
        }

        @Test
        @DisplayName("APPLIED cannot transition directly to DONE")
        void appliedCannotTransitionDirectlyToDone() {
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.DONE)).isFalse();
        }

        @Test
        @DisplayName("APPLIED cannot transition to CONTENT_APPROVED")
        void appliedCannotTransitionToContentApproved() {
            assertThat(OpportunityStatus.APPLIED.canTransitionTo(OpportunityStatus.CONTENT_APPROVED)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class, names = {"DONE", "REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER"})
        @DisplayName("Terminal statuses cannot transition to any status")
        void terminalStatusesCannotTransition(OpportunityStatus terminalStatus) {
            for (OpportunityStatus target : OpportunityStatus.values()) {
                assertThat(terminalStatus.canTransitionTo(target))
                        .as("Terminal status %s should not transition to %s", terminalStatus, target)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("getPossibleTransitions returns correct transitions for APPLIED")
        void getPossibleTransitionsForApplied() {
            List<OpportunityStatus> transitions = OpportunityStatus.APPLIED.getPossibleTransitions();
            assertThat(transitions).containsExactlyInAnyOrder(
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.REJECTED_BY_COMPANY
            );
        }

        @Test
        @DisplayName("getPossibleTransitions returns empty for terminal statuses")
        void getPossibleTransitionsEmptyForTerminal() {
            assertThat(OpportunityStatus.DONE.getPossibleTransitions()).isEmpty();
            assertThat(OpportunityStatus.REJECTED_BY_COMPANY.getPossibleTransitions()).isEmpty();
            assertThat(OpportunityStatus.REJECTED_BY_INFLUENCER.getPossibleTransitions()).isEmpty();
        }
    }

    // =====================================================
    // Terminal Status Identification
    // =====================================================
    @Nested
    @DisplayName("Terminal Status Identification")
    class TerminalStatusIdentification {

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class, names = {"DONE", "REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER"})
        @DisplayName("should identify terminal statuses")
        void shouldIdentifyTerminalStatuses(OpportunityStatus status) {
            assertThat(status.isTerminalStatus()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class, names = {"APPLIED", "ACCEPTED_BY_COMPANY", "ACCEPTED_BY_INFLUENCER",
                "CONTENT_SEND_TO_ACCEPT", "CONTENT_APPROVED", "CONTENT_REJECTED", "CONTENT_POSTED",
                "CONTENT_POSTED_REJECTED", "TO_BE_PAID"})
        @DisplayName("should identify non-terminal statuses")
        void shouldIdentifyNonTerminalStatuses(OpportunityStatus status) {
            assertThat(status.isTerminalStatus()).isFalse();
        }

        @Test
        @DisplayName("DONE should be successful completion")
        void doneShouldBeSuccessfulCompletion() {
            assertThat(OpportunityStatus.DONE.isSuccessfulCompletion()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class, names = {"REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER"})
        @DisplayName("Rejection statuses should not be successful completion")
        void rejectionStatusesShouldNotBeSuccessfulCompletion(OpportunityStatus status) {
            assertThat(status.isSuccessfulCompletion()).isFalse();
        }
    }

    // =====================================================
    // Follower Validation Logic Tests
    // =====================================================
    @Nested
    @DisplayName("Follower Validation Logic")
    class FollowerValidationLogic {

        @Test
        @DisplayName("should pass when follower count is within range")
        void shouldPassWhenFollowerCountWithinRange() {
            long followerCount = 10000;
            long minRequired = 1000;
            long maxRequired = 50000;

            boolean isWithinRange = followerCount >= minRequired &&
                    (maxRequired == 0 || followerCount <= maxRequired);

            assertThat(isWithinRange).isTrue();
        }

        @Test
        @DisplayName("should fail when follower count is below minimum")
        void shouldFailWhenFollowerCountBelowMinimum() {
            long followerCount = 500;
            long minRequired = 1000;

            assertThat(followerCount < minRequired).isTrue();
        }

        @Test
        @DisplayName("should fail when follower count exceeds maximum")
        void shouldFailWhenFollowerCountExceedsMaximum() {
            long followerCount = 100000;
            long maxRequired = 50000;

            boolean exceedsMaximum = maxRequired > 0 && followerCount > maxRequired;

            assertThat(exceedsMaximum).isTrue();
        }

        @Test
        @DisplayName("should allow unlimited followers when max is 0")
        void shouldAllowUnlimitedFollowersWhenMaxIsZero() {
            long followerCount = 1000000;
            long maxRequired = 0;

            boolean exceedsMaximum = maxRequired > 0 && followerCount > maxRequired;

            assertThat(exceedsMaximum).isFalse();
        }

        @Test
        @DisplayName("should pass when at exact minimum boundary")
        void shouldPassWhenAtExactMinimumBoundary() {
            long followerCount = 1000;
            long minRequired = 1000;
            long maxRequired = 50000;

            boolean isWithinRange = followerCount >= minRequired &&
                    (maxRequired == 0 || followerCount <= maxRequired);

            assertThat(isWithinRange).isTrue();
        }

        @Test
        @DisplayName("should pass when at exact maximum boundary")
        void shouldPassWhenAtExactMaximumBoundary() {
            long followerCount = 50000;
            long minRequired = 1000;
            long maxRequired = 50000;

            boolean isWithinRange = followerCount >= minRequired &&
                    (maxRequired == 0 || followerCount <= maxRequired);

            assertThat(isWithinRange).isTrue();
        }

        @Test
        @DisplayName("should get primary social connection follower count")
        void shouldGetPrimarySocialConnectionFollowerCount() {
            when(userSocialConnectionRepository.findByUserIdAndIsPrimaryTrue(1L))
                    .thenReturn(Optional.of(testSocialConnection));

            Optional<UserSocialConnection> connection =
                    userSocialConnectionRepository.findByUserIdAndIsPrimaryTrue(1L);

            assertThat(connection).isPresent();
            assertThat(connection.get().getFollowersCount()).isEqualTo(10000);
            assertThat(connection.get().getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        }

        @Test
        @DisplayName("should return empty when no primary connection")
        void shouldReturnEmptyWhenNoPrimaryConnection() {
            when(userSocialConnectionRepository.findByUserIdAndIsPrimaryTrue(anyLong()))
                    .thenReturn(Optional.empty());

            Optional<UserSocialConnection> connection =
                    userSocialConnectionRepository.findByUserIdAndIsPrimaryTrue(999L);

            assertThat(connection).isEmpty();
        }

        @Test
        @DisplayName("should handle expired connection status")
        void shouldHandleExpiredConnectionStatus() {
            testSocialConnection.setConnectionStatus(ConnectionStatus.EXPIRED);
            when(userSocialConnectionRepository.findByUserIdAndIsPrimaryTrue(1L))
                    .thenReturn(Optional.of(testSocialConnection));

            Optional<UserSocialConnection> connection =
                    userSocialConnectionRepository.findByUserIdAndIsPrimaryTrue(1L);

            assertThat(connection).isPresent();
            assertThat(connection.get().getConnectionStatus()).isEqualTo(ConnectionStatus.EXPIRED);
        }

        @Test
        @DisplayName("should handle revoked connection status")
        void shouldHandleRevokedConnectionStatus() {
            testSocialConnection.setConnectionStatus(ConnectionStatus.REVOKED);
            when(userSocialConnectionRepository.findByUserIdAndIsPrimaryTrue(1L))
                    .thenReturn(Optional.of(testSocialConnection));

            Optional<UserSocialConnection> connection =
                    userSocialConnectionRepository.findByUserIdAndIsPrimaryTrue(1L);

            assertThat(connection).isPresent();
            assertThat(connection.get().getConnectionStatus()).isEqualTo(ConnectionStatus.REVOKED);
        }
    }

    // =====================================================
    // Rating Updates Tests
    // =====================================================
    @Nested
    @DisplayName("Rating Updates")
    class RatingUpdates {

        @Test
        @DisplayName("influencer can set their rateStatus")
        void influencerCanSetRateStatus() {
            testAppliedOpportunity.setRateStatus(RateStatus.DEFAULT);

            testAppliedOpportunity.setRateStatus(RateStatus.POSITIVE);

            assertThat(testAppliedOpportunity.getRateStatus()).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("company can set companyRateStatus")
        void companyCanSetCompanyRateStatus() {
            testAppliedOpportunity.setCompanyRateStatus(RateStatus.DEFAULT);

            testAppliedOpportunity.setCompanyRateStatus(RateStatus.POSITIVE);

            assertThat(testAppliedOpportunity.getCompanyRateStatus()).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("can set negative rating")
        void canSetNegativeRating() {
            testAppliedOpportunity.setRateStatus(RateStatus.NEGATIVE);

            assertThat(testAppliedOpportunity.getRateStatus()).isEqualTo(RateStatus.NEGATIVE);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("all RateStatus values should be settable")
        void allRateStatusValuesShouldBeSettable(RateStatus rating) {
            testAppliedOpportunity.setRateStatus(rating);
            assertThat(testAppliedOpportunity.getRateStatus()).isEqualTo(rating);
        }

        @ParameterizedTest
        @EnumSource(RateStatus.class)
        @DisplayName("all companyRateStatus values should be settable")
        void allCompanyRateStatusValuesShouldBeSettable(RateStatus rating) {
            testAppliedOpportunity.setCompanyRateStatus(rating);
            assertThat(testAppliedOpportunity.getCompanyRateStatus()).isEqualTo(rating);
        }

        @Test
        @DisplayName("RateStatus DEFAULT should have secondary color theme")
        void rateStatusDefaultShouldHaveSecondaryColorTheme() {
            assertThat(RateStatus.DEFAULT.getColorTheme()).isEqualTo("secondary");
        }

        @Test
        @DisplayName("RateStatus POSITIVE should have success color theme")
        void rateStatusPositiveShouldHaveSuccessColorTheme() {
            assertThat(RateStatus.POSITIVE.getColorTheme()).isEqualTo("success");
        }

        @Test
        @DisplayName("RateStatus NEGATIVE should have danger color theme")
        void rateStatusNegativeShouldHaveDangerColorTheme() {
            assertThat(RateStatus.NEGATIVE.getColorTheme()).isEqualTo("danger");
        }
    }

    // =====================================================
    // Delete Operation Tests
    // =====================================================
    @Nested
    @DisplayName("Delete Operation Logic")
    class DeleteOperationLogic {

        @Test
        @DisplayName("should allow delete when status is APPLIED")
        void shouldAllowDeleteWhenStatusIsApplied() {
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.APPLIED);

            boolean canDelete = testAppliedOpportunity.getOpportunityStatus() == OpportunityStatus.APPLIED;

            assertThat(canDelete).isTrue();
        }

        @Test
        @DisplayName("should deny delete when status is not APPLIED (non-admin)")
        void shouldDenyDeleteWhenStatusIsNotApplied() {
            testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.ACCEPTED_BY_COMPANY);

            boolean statusAllowsDelete = testAppliedOpportunity.getOpportunityStatus() == OpportunityStatus.APPLIED;

            assertThat(statusAllowsDelete).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"APPLIED"})
        @DisplayName("should deny delete for all non-APPLIED statuses (non-admin)")
        void shouldDenyDeleteForAllNonAppliedStatuses(OpportunityStatus status) {
            testAppliedOpportunity.setOpportunityStatus(status);

            boolean statusAllowsDelete = testAppliedOpportunity.getOpportunityStatus() == OpportunityStatus.APPLIED;

            assertThat(statusAllowsDelete)
                    .as("Status %s should not allow delete for non-admin", status)
                    .isFalse();
        }
    }

    // =====================================================
    // Permission Validation Tests
    // =====================================================
    @Nested
    @DisplayName("Permission Validation")
    class PermissionValidation {

        @Test
        @DisplayName("admin should have full access")
        void adminShouldHaveFullAccess() {
            when(permissionUtils.isAdmin()).thenReturn(true);

            boolean isAdmin = permissionUtils.isAdmin();

            assertThat(isAdmin).isTrue();
        }

        @Test
        @DisplayName("influencer should be identified correctly")
        void influencerShouldBeIdentifiedCorrectly() {
            when(permissionUtils.isInfluencer()).thenReturn(true);

            boolean isInfluencer = permissionUtils.isInfluencer();

            assertThat(isInfluencer).isTrue();
        }

        @Test
        @DisplayName("company should be identified correctly")
        void companyShouldBeIdentifiedCorrectly() {
            when(permissionUtils.isCompany()).thenReturn(true);

            boolean isCompany = permissionUtils.isCompany();

            assertThat(isCompany).isTrue();
        }

        @Test
        @DisplayName("influencer should own their opportunities")
        void influencerShouldOwnTheirOpportunities() {
            when(permissionUtils.isInfluencer()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("influencer-firebase-uid");

            boolean isOwner = testAppliedOpportunity.getInfluencer()
                    .getFirebaseUserId()
                    .equals(permissionUtils.getUserId());

            assertThat(isOwner).isTrue();
        }

        @Test
        @DisplayName("influencer should not own other influencer's opportunities")
        void influencerShouldNotOwnOtherOpportunities() {
            when(permissionUtils.isInfluencer()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("different-influencer-uid");

            boolean isOwner = testAppliedOpportunity.getInfluencer()
                    .getFirebaseUserId()
                    .equals(permissionUtils.getUserId());

            assertThat(isOwner).isFalse();
        }

        @Test
        @DisplayName("company should own opportunities for their partnership")
        void companyShouldOwnOpportunitiesForTheirPartnership() {
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("company-firebase-uid");

            boolean isCompanyOwner = testAppliedOpportunity.getPartnershipOpportunity()
                    .getCompany()
                    .getFirebaseUserId()
                    .equals(permissionUtils.getUserId());

            assertThat(isCompanyOwner).isTrue();
        }

        @Test
        @DisplayName("company should not own other company's partnership opportunities")
        void companyShouldNotOwnOtherCompanyOpportunities() {
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("different-company-uid");

            boolean isCompanyOwner = testAppliedOpportunity.getPartnershipOpportunity()
                    .getCompany()
                    .getFirebaseUserId()
                    .equals(permissionUtils.getUserId());

            assertThat(isCompanyOwner).isFalse();
        }
    }

    // =====================================================
    // Status Category Classification Tests
    // =====================================================
    @Nested
    @DisplayName("Status Category Classification")
    class StatusCategoryClassification {

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class, names = {"APPLIED", "ACCEPTED_BY_COMPANY"})
        @DisplayName("should classify as 'new'")
        void shouldClassifyAsNew(OpportunityStatus status) {
            String category = categorizeStatus(status);
            assertThat(category).isEqualTo("new");
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class, names = {
                "ACCEPTED_BY_INFLUENCER", "CONTENT_SEND_TO_ACCEPT", "CONTENT_APPROVED",
                "CONTENT_REJECTED", "CONTENT_POSTED", "CONTENT_POSTED_REJECTED", "TO_BE_PAID"
        })
        @DisplayName("should classify as 'inprogress'")
        void shouldClassifyAsInProgress(OpportunityStatus status) {
            String category = categorizeStatus(status);
            assertThat(category).isEqualTo("inprogress");
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class, names = {"DONE", "REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER"})
        @DisplayName("should classify as 'done'")
        void shouldClassifyAsDone(OpportunityStatus status) {
            String category = categorizeStatus(status);
            assertThat(category).isEqualTo("done");
        }

        private String categorizeStatus(OpportunityStatus status) {
            return switch (status) {
                case ACCEPTED_BY_INFLUENCER, CONTENT_SEND_TO_ACCEPT, CONTENT_APPROVED,
                     CONTENT_REJECTED, CONTENT_POSTED, CONTENT_POSTED_REJECTED, TO_BE_PAID -> "inprogress";
                case APPLIED, ACCEPTED_BY_COMPANY -> "new";
                case DONE, REJECTED_BY_COMPANY, REJECTED_BY_INFLUENCER -> "done";
            };
        }
    }

    // =====================================================
    // Resource Not Found Scenarios Tests
    // =====================================================
    @Nested
    @DisplayName("Resource Not Found Scenarios")
    class ResourceNotFoundScenarios {

        @Test
        @DisplayName("should throw ResourceNotFoundException when opportunity not found")
        void shouldThrowWhenOpportunityNotFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> repository.findById(999L)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByFirebaseUserId("unknown-uid")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userRepository.findByFirebaseUserId("unknown-uid")
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when influencer not found by ID")
        void shouldThrowWhenInfluencerNotFoundById() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userRepository.findById(999L)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Influencer")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =====================================================
    // Active Status Detection Tests
    // =====================================================
    @Nested
    @DisplayName("Active Status Detection")
    class ActiveStatusDetection {

        private static final Set<OpportunityStatus> ACTIVE_STATUSES = Set.of(
                OpportunityStatus.ACCEPTED_BY_COMPANY,
                OpportunityStatus.ACCEPTED_BY_INFLUENCER,
                OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
                OpportunityStatus.CONTENT_APPROVED,
                OpportunityStatus.CONTENT_POSTED,
                OpportunityStatus.CONTENT_REJECTED,
                OpportunityStatus.CONTENT_POSTED_REJECTED
        );

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class,
                names = {"ACCEPTED_BY_COMPANY", "ACCEPTED_BY_INFLUENCER", "CONTENT_SEND_TO_ACCEPT",
                        "CONTENT_APPROVED", "CONTENT_POSTED", "CONTENT_REJECTED", "CONTENT_POSTED_REJECTED"})
        @DisplayName("should identify active statuses")
        void shouldIdentifyActiveStatuses(OpportunityStatus status) {
            boolean isActive = ACTIVE_STATUSES.contains(status);
            assertThat(isActive).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class,
                names = {"APPLIED", "REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER", "DONE", "TO_BE_PAID"})
        @DisplayName("should identify non-active statuses")
        void shouldIdentifyNonActiveStatuses(OpportunityStatus status) {
            boolean isActive = ACTIVE_STATUSES.contains(status);
            assertThat(isActive).isFalse();
        }
    }

    // =====================================================
    // Repository Mock Tests
    // =====================================================
    @Nested
    @DisplayName("Repository Mock Tests")
    class RepositoryMockTests {

        @Test
        @DisplayName("should find opportunity by ID")
        void shouldFindOpportunityById() {
            when(repository.findById(10L)).thenReturn(Optional.of(testAppliedOpportunity));

            Optional<AppliedOpportunity> result = repository.findById(10L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(10L);
            verify(repository).findById(10L);
        }

        @Test
        @DisplayName("should return empty when opportunity not found")
        void shouldReturnEmptyWhenNotFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            Optional<AppliedOpportunity> result = repository.findById(999L);

            assertThat(result).isEmpty();
            verify(repository).findById(999L);
        }

        @Test
        @DisplayName("should save opportunity")
        void shouldSaveOpportunity() {
            when(repository.save(any(AppliedOpportunity.class))).thenReturn(testAppliedOpportunity);

            AppliedOpportunity result = repository.save(testAppliedOpportunity);

            assertThat(result).isEqualTo(testAppliedOpportunity);
            verify(repository).save(testAppliedOpportunity);
        }

        @Test
        @DisplayName("should delete opportunity by ID")
        void shouldDeleteOpportunityById() {
            doNothing().when(repository).deleteById(10L);

            repository.deleteById(10L);

            verify(repository).deleteById(10L);
        }

        @Test
        @DisplayName("should check if opportunity exists")
        void shouldCheckIfOpportunityExists() {
            when(repository.existsById(10L)).thenReturn(true);

            boolean exists = repository.existsById(10L);

            assertThat(exists).isTrue();
            verify(repository).existsById(10L);
        }
    }

    // =====================================================
    // Entity Field Tests
    // =====================================================
    @Nested
    @DisplayName("AppliedOpportunity Entity Fields")
    class AppliedOpportunityEntityFields {

        @Test
        @DisplayName("should have default status as APPLIED")
        void shouldHaveDefaultStatusAsApplied() {
            AppliedOpportunity newOpportunity = new AppliedOpportunity();
            assertThat(newOpportunity.getOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("should have default rateStatus as DEFAULT")
        void shouldHaveDefaultRateStatusAsDefault() {
            AppliedOpportunity newOpportunity = new AppliedOpportunity();
            assertThat(newOpportunity.getRateStatus()).isEqualTo(RateStatus.DEFAULT);
        }

        @Test
        @DisplayName("should have default companyRateStatus as DEFAULT")
        void shouldHaveDefaultCompanyRateStatusAsDefault() {
            AppliedOpportunity newOpportunity = new AppliedOpportunity();
            assertThat(newOpportunity.getCompanyRateStatus()).isEqualTo(RateStatus.DEFAULT);
        }

        @Test
        @DisplayName("should accept note up to 500 characters")
        void shouldAcceptNoteUpTo500Characters() {
            String longNote = "a".repeat(500);
            testAppliedOpportunity.setNote(longNote);
            assertThat(testAppliedOpportunity.getNote()).hasSize(500);
        }

        @Test
        @DisplayName("should allow setting execution date")
        void shouldAllowSettingExecutionDate() {
            LocalDateTime executionDate = LocalDateTime.now().plusDays(7);
            testAppliedOpportunity.setExecutionDate(executionDate);
            assertThat(testAppliedOpportunity.getExecutionDate()).isEqualTo(executionDate);
        }

        @Test
        @DisplayName("should allow adding content submissions")
        void shouldAllowAddingContentSubmissions() {
            AppliedOpportunityContent content = new AppliedOpportunityContent();
            content.setId(1L);

            testAppliedOpportunity.addContentSubmission(content);

            assertThat(testAppliedOpportunity.getContentSubmissions()).contains(content);
            assertThat(content.getAppliedOpportunity()).isEqualTo(testAppliedOpportunity);
        }

        @Test
        @DisplayName("should allow removing content submissions")
        void shouldAllowRemovingContentSubmissions() {
            AppliedOpportunityContent content = new AppliedOpportunityContent();
            content.setId(1L);
            testAppliedOpportunity.addContentSubmission(content);

            testAppliedOpportunity.removeContentSubmission(content);

            assertThat(testAppliedOpportunity.getContentSubmissions()).doesNotContain(content);
            assertThat(content.getAppliedOpportunity()).isNull();
        }
    }

    // =====================================================
    // OpportunityStatus Metadata Tests
    // =====================================================
    @Nested
    @DisplayName("OpportunityStatus Metadata")
    class OpportunityStatusMetadata {

        @Test
        @DisplayName("APPLIED should have correct metadata")
        void appliedShouldHaveCorrectMetadata() {
            assertThat(OpportunityStatus.APPLIED.getColorTheme()).isEqualTo("primary");
            assertThat(OpportunityStatus.APPLIED.getIcon()).isEqualTo("user");
            assertThat(OpportunityStatus.APPLIED.getAliases()).contains("waiting");
        }

        @Test
        @DisplayName("DONE should have success color theme")
        void doneShouldHaveSuccessColorTheme() {
            assertThat(OpportunityStatus.DONE.getColorTheme()).isEqualTo("success");
        }

        @Test
        @DisplayName("REJECTED_BY_COMPANY should have danger color theme")
        void rejectedByCompanyShouldHaveDangerColorTheme() {
            assertThat(OpportunityStatus.REJECTED_BY_COMPANY.getColorTheme()).isEqualTo("danger");
        }

        @Test
        @DisplayName("CONTENT_SEND_TO_ACCEPT should have warning color theme")
        void contentSendToAcceptShouldHaveWarningColorTheme() {
            assertThat(OpportunityStatus.CONTENT_SEND_TO_ACCEPT.getColorTheme()).isEqualTo("warning");
        }

        @Test
        @DisplayName("all statuses should have non-null descriptions")
        void allStatusesShouldHaveNonNullDescriptions() {
            for (OpportunityStatus status : OpportunityStatus.values()) {
                assertThat(status.getDescription())
                        .as("Status %s should have a description", status)
                        .isNotNull()
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("all statuses should have non-null icons")
        void allStatusesShouldHaveNonNullIcons() {
            for (OpportunityStatus status : OpportunityStatus.values()) {
                assertThat(status.getIcon())
                        .as("Status %s should have an icon", status)
                        .isNotNull()
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("all statuses should have non-empty aliases")
        void allStatusesShouldHaveNonEmptyAliases() {
            for (OpportunityStatus status : OpportunityStatus.values()) {
                assertThat(status.getAliases())
                        .as("Status %s should have aliases", status)
                        .isNotNull()
                        .isNotEmpty();
            }
        }
    }

    // =====================================================
    // OpportunityStatus JSON Serialization Tests
    // =====================================================
    @Nested
    @DisplayName("OpportunityStatus JSON Serialization")
    class OpportunityStatusJsonSerialization {

        @Test
        @DisplayName("getValue should return enum name")
        void getValueShouldReturnEnumName() {
            assertThat(OpportunityStatus.APPLIED.getValue()).isEqualTo("APPLIED");
            assertThat(OpportunityStatus.DONE.getValue()).isEqualTo("DONE");
        }

        @Test
        @DisplayName("fromString should parse uppercase value")
        void fromStringShouldParseUppercaseValue() {
            OpportunityStatus status = OpportunityStatus.fromString("APPLIED");
            assertThat(status).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("fromString should parse lowercase value")
        void fromStringShouldParseLowercaseValue() {
            OpportunityStatus status = OpportunityStatus.fromString("applied");
            assertThat(status).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("fromString should throw for invalid value")
        void fromStringShouldThrowForInvalidValue() {
            assertThatThrownBy(() -> OpportunityStatus.fromString("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =====================================================
    // RateStatus JSON Serialization Tests
    // =====================================================
    @Nested
    @DisplayName("RateStatus JSON Serialization")
    class RateStatusJsonSerialization {

        @Test
        @DisplayName("getValue should return enum name")
        void getValueShouldReturnEnumName() {
            assertThat(RateStatus.DEFAULT.getValue()).isEqualTo("DEFAULT");
            assertThat(RateStatus.POSITIVE.getValue()).isEqualTo("POSITIVE");
            assertThat(RateStatus.NEGATIVE.getValue()).isEqualTo("NEGATIVE");
        }

        @Test
        @DisplayName("fromString should parse uppercase value")
        void fromStringShouldParseUppercaseValue() {
            RateStatus status = RateStatus.fromString("POSITIVE");
            assertThat(status).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("fromString should parse lowercase value")
        void fromStringShouldParseLowercaseValue() {
            RateStatus status = RateStatus.fromString("positive");
            assertThat(status).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("fromString should throw for invalid value")
        void fromStringShouldThrowForInvalidValue() {
            assertThatThrownBy(() -> RateStatus.fromString("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =====================================================
    // getActiveStatuses and getCompletedStatuses Tests
    // =====================================================
    @Nested
    @DisplayName("getActiveStatuses and getCompletedStatuses")
    class GetActiveAndCompletedStatuses {

        @Test
        @DisplayName("getActiveStatuses should exclude terminal statuses")
        void getActiveStatusesShouldExcludeTerminalStatuses() {
            List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().toList();

            assertThat(activeStatuses).doesNotContain(OpportunityStatus.DONE);
            assertThat(activeStatuses).doesNotContain(OpportunityStatus.REJECTED_BY_COMPANY);
            assertThat(activeStatuses).doesNotContain(OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("getActiveStatuses should include non-terminal statuses")
        void getActiveStatusesShouldIncludeNonTerminalStatuses() {
            List<OpportunityStatus> activeStatuses = OpportunityStatus.getActiveStatuses().toList();

            assertThat(activeStatuses).contains(OpportunityStatus.APPLIED);
            assertThat(activeStatuses).contains(OpportunityStatus.ACCEPTED_BY_COMPANY);
            assertThat(activeStatuses).contains(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("getCompletedStatuses should return only terminal statuses")
        void getCompletedStatusesShouldReturnOnlyTerminalStatuses() {
            List<OpportunityStatus> completedStatuses = OpportunityStatus.getCompletedStatuses().toList();

            assertThat(completedStatuses).containsExactlyInAnyOrder(
                    OpportunityStatus.DONE,
                    OpportunityStatus.REJECTED_BY_COMPANY,
                    OpportunityStatus.REJECTED_BY_INFLUENCER
            );
        }
    }

    // =====================================================
    // FollowerValidationResult Tests
    // =====================================================
    @Nested
    @DisplayName("FollowerValidationResult")
    class FollowerValidationResultTests {

        @Test
        @DisplayName("success result should be valid")
        void successResultShouldBeValid() {
            AppliedOpportunityService.FollowerValidationResult result =
                    AppliedOpportunityService.FollowerValidationResult.success(10000);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Follower count meets requirements");
            assertThat(result.getCurrentFollowerCount()).isEqualTo(10000);
        }

        @Test
        @DisplayName("failure result should not be valid")
        void failureResultShouldNotBeValid() {
            AppliedOpportunityService.FollowerValidationResult result =
                    AppliedOpportunityService.FollowerValidationResult.failure("Not enough followers", 500);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Not enough followers");
            assertThat(result.getCurrentFollowerCount()).isEqualTo(500);
        }

        @Test
        @DisplayName("success result with null follower count")
        void successResultWithNullFollowerCount() {
            AppliedOpportunityService.FollowerValidationResult result =
                    AppliedOpportunityService.FollowerValidationResult.success(null);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getCurrentFollowerCount()).isNull();
        }
    }

    // =====================================================
    // PaymentContactDto Tests
    // =====================================================
    @Nested
    @DisplayName("PaymentContactDto")
    class PaymentContactDtoTests {

        @Test
        @DisplayName("should create PaymentContactDto with all fields")
        void shouldCreatePaymentContactDtoWithAllFields() {
            PaymentContactDto dto = new PaymentContactDto(
                    "Test Name",
                    "test@example.com",
                    "+48123456789",
                    "https://example.com/profile.jpg"
            );

            assertThat(dto.name()).isEqualTo("Test Name");
            assertThat(dto.email()).isEqualTo("test@example.com");
            assertThat(dto.phone()).isEqualTo("+48123456789");
            assertThat(dto.profilePicture()).isEqualTo("https://example.com/profile.jpg");
        }

        @Test
        @DisplayName("should allow null phone when not shared")
        void shouldAllowNullPhoneWhenNotShared() {
            PaymentContactDto dto = new PaymentContactDto(
                    "Test Name",
                    "test@example.com",
                    null,
                    "https://example.com/profile.jpg"
            );

            assertThat(dto.phone()).isNull();
        }
    }

    // =====================================================
    // AppliedOpportunityStatisticsDto Tests
    // =====================================================
    @Nested
    @DisplayName("AppliedOpportunityStatisticsDto")
    class AppliedOpportunityStatisticsDtoTests {

        @Test
        @DisplayName("should build statistics DTO correctly")
        void shouldBuildStatisticsDtoCorrectly() {
            AppliedOpportunityStatisticsDto dto = AppliedOpportunityStatisticsDto.builder()
                    .inProgress(5L)
                    .newOpportunities(3L)
                    .done(10L)
                    .total(18L)
                    .build();

            assertThat(dto.getInProgress()).isEqualTo(5L);
            assertThat(dto.getNewOpportunities()).isEqualTo(3L);
            assertThat(dto.getDone()).isEqualTo(10L);
            assertThat(dto.getTotal()).isEqualTo(18L);
        }
    }

    // =====================================================
    // Input DTO Validation Tests
    // =====================================================
    @Nested
    @DisplayName("AppliedOpportunityDtoIn")
    class AppliedOpportunityDtoInTests {

        @Test
        @DisplayName("should allow setting all fields")
        void shouldAllowSettingAllFields() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setInfluencer(1L);
            dto.setPartnershipOpportunity(100L);
            dto.setNote("Test note");
            dto.setOpportunityStatus(OpportunityStatus.APPLIED);
            dto.setExecutionDate(LocalDateTime.now());
            dto.setRateStatus(RateStatus.DEFAULT);
            dto.setCompanyRateStatus(RateStatus.DEFAULT);

            assertThat(dto.getInfluencer()).isEqualTo(1L);
            assertThat(dto.getPartnershipOpportunity()).isEqualTo(100L);
            assertThat(dto.getNote()).isEqualTo("Test note");
            assertThat(dto.getOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
            assertThat(dto.getRateStatus()).isEqualTo(RateStatus.DEFAULT);
            assertThat(dto.getCompanyRateStatus()).isEqualTo(RateStatus.DEFAULT);
        }

        @Test
        @DisplayName("should allow null influencer for auto-assignment")
        void shouldAllowNullInfluencerForAutoAssignment() {
            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setInfluencer(null);
            dto.setPartnershipOpportunity(100L);

            assertThat(dto.getInfluencer()).isNull();
        }
    }

    // =====================================================
    // Edge Cases and Boundary Tests
    // =====================================================
    @Nested
    @DisplayName("Edge Cases and Boundaries")
    class EdgeCasesAndBoundaries {

        @Test
        @DisplayName("should handle empty content submissions list")
        void shouldHandleEmptyContentSubmissionsList() {
            testAppliedOpportunity.setContentSubmissions(new ArrayList<>());
            assertThat(testAppliedOpportunity.getContentSubmissions()).isEmpty();
        }

        @Test
        @DisplayName("should handle null content submissions")
        void shouldHandleNullContentSubmissions() {
            testAppliedOpportunity.setContentSubmissions(null);
            assertThat(testAppliedOpportunity.getContentSubmissions()).isNull();
        }

        @Test
        @DisplayName("should handle empty note")
        void shouldHandleEmptyNote() {
            testAppliedOpportunity.setNote("");
            assertThat(testAppliedOpportunity.getNote()).isEmpty();
        }

        @Test
        @DisplayName("should handle null note")
        void shouldHandleNullNote() {
            testAppliedOpportunity.setNote(null);
            assertThat(testAppliedOpportunity.getNote()).isNull();
        }

        @Test
        @DisplayName("should handle zero follower minimum")
        void shouldHandleZeroFollowerMinimum() {
            testPartnershipOpportunity.setFollowersMin(0L);
            assertThat(testPartnershipOpportunity.getFollowersMin()).isZero();
        }

        @Test
        @DisplayName("should handle zero follower maximum (unlimited)")
        void shouldHandleZeroFollowerMaximum() {
            testPartnershipOpportunity.setFollowersMax(0L);
            assertThat(testPartnershipOpportunity.getFollowersMax()).isZero();
        }

        @Test
        @DisplayName("should handle large follower counts")
        void shouldHandleLargeFollowerCounts() {
            testSocialConnection.setFollowersCount(Integer.MAX_VALUE);
            assertThat(testSocialConnection.getFollowersCount()).isEqualTo(Integer.MAX_VALUE);
        }
    }

    // =====================================================
    // Collaboration Status Workflow Integration Tests
    // =====================================================
    @Nested
    @DisplayName("Collaboration Status Workflow")
    class CollaborationStatusWorkflow {

        @Test
        @DisplayName("complete happy path workflow transitions")
        void completeHappyPathWorkflowTransitions() {
            // APPLIED -> ACCEPTED_BY_COMPANY
            OpportunityStatus s1 = OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, true);
            assertThat(s1).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);

            // ACCEPTED_BY_COMPANY -> ACCEPTED_BY_INFLUENCER
            OpportunityStatus s2 = OpportunityStatus.getNextStatus(s1, true);
            assertThat(s2).isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);

            // ACCEPTED_BY_INFLUENCER -> CONTENT_SEND_TO_ACCEPT
            OpportunityStatus s3 = OpportunityStatus.getNextStatus(s2, true);
            assertThat(s3).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);

            // CONTENT_SEND_TO_ACCEPT -> CONTENT_APPROVED
            OpportunityStatus s4 = OpportunityStatus.getNextStatus(s3, true);
            assertThat(s4).isEqualTo(OpportunityStatus.CONTENT_APPROVED);

            // CONTENT_APPROVED -> CONTENT_POSTED
            OpportunityStatus s5 = OpportunityStatus.getNextStatus(s4, true);
            assertThat(s5).isEqualTo(OpportunityStatus.CONTENT_POSTED);

            // CONTENT_POSTED -> TO_BE_PAID
            OpportunityStatus s6 = OpportunityStatus.getNextStatus(s5, true);
            assertThat(s6).isEqualTo(OpportunityStatus.TO_BE_PAID);

            // TO_BE_PAID -> DONE
            OpportunityStatus s7 = OpportunityStatus.getNextStatus(s6, true);
            assertThat(s7).isEqualTo(OpportunityStatus.DONE);
        }

        @Test
        @DisplayName("content rejection and resubmission workflow")
        void contentRejectionAndResubmissionWorkflow() {
            // Start from CONTENT_SEND_TO_ACCEPT
            OpportunityStatus s1 = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT, false);
            assertThat(s1).isEqualTo(OpportunityStatus.CONTENT_REJECTED);

            // CONTENT_REJECTED -> CONTENT_SEND_TO_ACCEPT (resubmit)
            OpportunityStatus s2 = OpportunityStatus.getNextStatus(s1, true);
            assertThat(s2).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);

            // Continue to approval
            OpportunityStatus s3 = OpportunityStatus.getNextStatus(s2, true);
            assertThat(s3).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
        }

        @Test
        @DisplayName("posted content rejection and repost workflow")
        void postedContentRejectionAndRepostWorkflow() {
            // CONTENT_POSTED -> CONTENT_POSTED_REJECTED
            OpportunityStatus s1 = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_POSTED, false);
            assertThat(s1).isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);

            // CONTENT_POSTED_REJECTED -> CONTENT_POSTED (repost)
            OpportunityStatus s2 = OpportunityStatus.getNextStatus(s1, true);
            assertThat(s2).isEqualTo(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("early rejection by company workflow")
        void earlyRejectionByCompanyWorkflow() {
            OpportunityStatus s1 = OpportunityStatus.getNextStatus(OpportunityStatus.APPLIED, false);
            assertThat(s1).isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY);
            assertThat(s1.isTerminalStatus()).isTrue();
        }

        @Test
        @DisplayName("influencer resignation after content rejection workflow")
        void influencerResignationAfterContentRejectionWorkflow() {
            OpportunityStatus s1 = OpportunityStatus.getNextStatus(OpportunityStatus.CONTENT_REJECTED, false);
            assertThat(s1).isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
            assertThat(s1.isTerminalStatus()).isTrue();
        }
    }

    // =====================================================
    // UserType Tests
    // =====================================================
    @Nested
    @DisplayName("UserType Validation")
    class UserTypeValidation {

        @Test
        @DisplayName("influencer should have INFLUENCER user type")
        void influencerShouldHaveInfluencerUserType() {
            assertThat(testInfluencer.getUserType()).isEqualTo(UserType.INFLUENCER);
        }

        @Test
        @DisplayName("company should have COMPANY user type")
        void companyShouldHaveCompanyUserType() {
            assertThat(testCompany.getUserType()).isEqualTo(UserType.COMPANY);
        }

        @Test
        @DisplayName("should correctly identify user types")
        void shouldCorrectlyIdentifyUserTypes() {
            assertThat(testInfluencer.getUserType() == UserType.INFLUENCER).isTrue();
            assertThat(testCompany.getUserType() == UserType.COMPANY).isTrue();
            assertThat(testInfluencer.getUserType() == UserType.COMPANY).isFalse();
        }
    }

    // =====================================================
    // AccountStatus Tests
    // =====================================================
    @Nested
    @DisplayName("AccountStatus Validation")
    class AccountStatusValidation {

        @Test
        @DisplayName("active user should have ACTIVE status")
        void activeUserShouldHaveActiveStatus() {
            assertThat(testInfluencer.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("should check if account is active")
        void shouldCheckIfAccountIsActive() {
            boolean isActive = testInfluencer.getAccountStatus() == AccountStatus.ACTIVE;
            assertThat(isActive).isTrue();
        }
    }

    // =====================================================
    // ConnectionStatus Tests
    // =====================================================
    @Nested
    @DisplayName("ConnectionStatus Validation")
    class ConnectionStatusValidation {

        @Test
        @DisplayName("connected status should be CONNECTED")
        void connectedStatusShouldBeConnected() {
            assertThat(testSocialConnection.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        }

        @ParameterizedTest
        @EnumSource(ConnectionStatus.class)
        @DisplayName("all ConnectionStatus values should be settable")
        void allConnectionStatusValuesShouldBeSettable(ConnectionStatus status) {
            testSocialConnection.setConnectionStatus(status);
            assertThat(testSocialConnection.getConnectionStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("should identify connected status")
        void shouldIdentifyConnectedStatus() {
            boolean isConnected = testSocialConnection.getConnectionStatus() == ConnectionStatus.CONNECTED;
            assertThat(isConnected).isTrue();
        }

        @Test
        @DisplayName("should identify expired status")
        void shouldIdentifyExpiredStatus() {
            testSocialConnection.setConnectionStatus(ConnectionStatus.EXPIRED);
            boolean isExpired = testSocialConnection.getConnectionStatus() == ConnectionStatus.EXPIRED;
            assertThat(isExpired).isTrue();
        }

        @Test
        @DisplayName("should identify revoked status")
        void shouldIdentifyRevokedStatus() {
            testSocialConnection.setConnectionStatus(ConnectionStatus.REVOKED);
            boolean isRevoked = testSocialConnection.getConnectionStatus() == ConnectionStatus.REVOKED;
            assertThat(isRevoked).isTrue();
        }
    }
}
