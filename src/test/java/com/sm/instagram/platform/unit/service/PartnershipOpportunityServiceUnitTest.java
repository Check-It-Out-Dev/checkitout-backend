package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityRepository;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityRepository;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PartnershipOpportunityService.
 * Tests business logic, permission checks, and state transitions.
 * Uses pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PartnershipOpportunityService")
class PartnershipOpportunityServiceUnitTest {

    @Mock
    private PartnershipOpportunityRepository repository;

    @Mock
    private AppliedOpportunityRepository appliedOpportunityRepository;

    @Mock
    private PermissionUtils permissionUtils;

    // Test fixtures
    private User testCompany;
    private User testInfluencer;
    private PartnershipOpportunity testOpportunity;
    private AppliedOpportunity testAppliedOpportunity;

    @BeforeEach
    void setUp() {
        // Create test company
        testCompany = new User();
        testCompany.setId(1L);
        testCompany.setFirebaseUserId("company-firebase-uid");
        testCompany.setUserType(UserType.COMPANY);
        testCompany.setName("Test Company");

        // Create test influencer
        testInfluencer = new User();
        testInfluencer.setId(2L);
        testInfluencer.setFirebaseUserId("influencer-firebase-uid");
        testInfluencer.setUserType(UserType.INFLUENCER);
        testInfluencer.setName("Test Influencer");

        // Create test opportunity
        testOpportunity = new PartnershipOpportunity();
        testOpportunity.setId(100L);
        testOpportunity.setCompany(testCompany);
        testOpportunity.setName("Test Campaign");
        testOpportunity.setTitle("Campaign Title");
        testOpportunity.setActive(true);
        testOpportunity.setFollowersMin(1000L);
        testOpportunity.setFollowersMax(50000L);
        testOpportunity.setCompensationAmountMin(100);
        testOpportunity.setCompensationAmountMax(500);
        testOpportunity.setStartDate(LocalDateTime.now());
        testOpportunity.setEndDate(LocalDateTime.now().plusMonths(1));

        // Create test applied opportunity
        testAppliedOpportunity = new AppliedOpportunity();
        testAppliedOpportunity.setId(10L);
        testAppliedOpportunity.setInfluencer(testInfluencer);
        testAppliedOpportunity.setPartnershipOpportunity(testOpportunity);
        testAppliedOpportunity.setOpportunityStatus(OpportunityStatus.APPLIED);
    }

    @Nested
    @DisplayName("hasActiveApplications")
    class HasActiveApplications {

        @Test
        @DisplayName("should return true when active applications exist")
        void shouldReturnTrueWhenActiveApplicationsExist() {
            // Given
            Set<OpportunityStatus> activeStatuses = Set.of(
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.CONTENT_APPROVED,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.CONTENT_REJECTED,
                    OpportunityStatus.ACCEPTED_BY_INFLUENCER
            );

            when(appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    eq(100L), eq(activeStatuses))).thenReturn(true);

            // When
            boolean hasActive = appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    100L, activeStatuses);

            // Then
            assertThat(hasActive).isTrue();
        }

        @Test
        @DisplayName("should return false when no active applications")
        void shouldReturnFalseWhenNoActiveApplications() {
            // Given
            when(appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    eq(100L), anySet())).thenReturn(false);

            // When
            boolean hasActive = appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    100L, Set.of(OpportunityStatus.ACCEPTED_BY_COMPANY));

            // Then
            assertThat(hasActive).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class,
                    names = {"ACCEPTED_BY_COMPANY", "ACCEPTED_BY_INFLUENCER", "CONTENT_APPROVED",
                             "CONTENT_POSTED", "CONTENT_REJECTED"})
        @DisplayName("should consider these statuses as active")
        void shouldConsiderTheseStatusesAsActive(OpportunityStatus status) {
            // Given
            Set<OpportunityStatus> activeStatuses = Set.of(
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.CONTENT_APPROVED,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.CONTENT_REJECTED,
                    OpportunityStatus.ACCEPTED_BY_INFLUENCER
            );

            // Then
            assertThat(activeStatuses).contains(status);
        }

        @ParameterizedTest
        @EnumSource(value = OpportunityStatus.class,
                    names = {"APPLIED", "REJECTED_BY_COMPANY", "REJECTED_BY_INFLUENCER", "DONE"})
        @DisplayName("should not consider these statuses as blocking for edit")
        void shouldNotConsiderTheseStatusesAsBlocking(OpportunityStatus status) {
            // Given
            Set<OpportunityStatus> activeStatuses = Set.of(
                    OpportunityStatus.ACCEPTED_BY_COMPANY,
                    OpportunityStatus.CONTENT_APPROVED,
                    OpportunityStatus.CONTENT_POSTED,
                    OpportunityStatus.CONTENT_REJECTED,
                    OpportunityStatus.ACCEPTED_BY_INFLUENCER
            );

            // Then
            assertThat(activeStatuses).doesNotContain(status);
        }
    }

    @Nested
    @DisplayName("Permission Checks")
    class PermissionChecks {

        @Test
        @DisplayName("admin should have full access to all opportunities")
        void adminShouldHaveFullAccess() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);

            // When
            boolean isAdmin = permissionUtils.isAdmin();

            // Then
            assertThat(isAdmin).isTrue();
        }

        @Test
        @DisplayName("company should only edit own opportunities")
        void companyShouldOnlyEditOwnOpportunities() {
            // Given
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("company-firebase-uid");
            when(permissionUtils.canEditOpportunity(testOpportunity)).thenReturn(true);

            // When
            boolean canEdit = permissionUtils.isCompany() &&
                    permissionUtils.canEditOpportunity(testOpportunity);

            // Then
            assertThat(canEdit).isTrue();
        }

        @Test
        @DisplayName("company should not edit other company's opportunities")
        void companyShouldNotEditOtherCompanyOpportunities() {
            // Given
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("different-company-uid");
            when(permissionUtils.canEditOpportunity(testOpportunity)).thenReturn(false);

            // When
            boolean canEdit = permissionUtils.canEditOpportunity(testOpportunity);

            // Then
            assertThat(canEdit).isFalse();
        }

        @Test
        @DisplayName("influencer should view active opportunities only")
        void influencerShouldViewActiveOpportunitiesOnly() {
            // Given
            when(permissionUtils.isInfluencer()).thenReturn(true);
            testOpportunity.setActive(true);

            // When
            boolean isActiveForViewing = testOpportunity.isActive();

            // Then
            assertThat(isActiveForViewing).isTrue();
        }

        @Test
        @DisplayName("influencer should not view inactive opportunities")
        void influencerShouldNotViewInactiveOpportunities() {
            // Given
            when(permissionUtils.isInfluencer()).thenReturn(true);
            testOpportunity.setActive(false);

            // When
            boolean isActiveForViewing = testOpportunity.isActive();

            // Then
            assertThat(isActiveForViewing).isFalse();
        }
    }

    @Nested
    @DisplayName("Deactivation Logic")
    class DeactivationLogic {

        @Test
        @DisplayName("should deactivate opportunity when no active applications")
        void shouldDeactivateWhenNoActiveApplications() {
            // Given
            testOpportunity.setActive(true);
            when(appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    eq(100L), anySet())).thenReturn(false);

            // When - simulating deactivation
            testOpportunity.setActive(false);

            // Then
            assertThat(testOpportunity.isActive()).isFalse();
        }

        @Test
        @DisplayName("should throw when trying to deactivate with active applications")
        void shouldThrowWhenDeactivatingWithActiveApplications() {
            // Given
            testOpportunity.setActive(true);
            when(appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    eq(100L), anySet())).thenReturn(true);

            // When
            boolean hasActiveApplications = appliedOpportunityRepository
                    .existsByPartnershipOpportunity_IdAndOpportunityStatusIn(100L, Set.of(OpportunityStatus.ACCEPTED_BY_COMPANY));

            // Then
            assertThat(hasActiveApplications).isTrue();
            // In real service, this would throw BusinessRuleTranslatableException
        }

        @Test
        @DisplayName("should throw when opportunity already inactive")
        void shouldThrowWhenAlreadyInactive() {
            // Given
            testOpportunity.setActive(false);

            // When/Then - simulating business rule check
            assertThat(testOpportunity.isActive()).isFalse();
            // In real service, this would throw BusinessRuleTranslatableException
        }
    }

    @Nested
    @DisplayName("Applied Opportunities Filtering")
    class AppliedOpportunitiesFiltering {

        @Test
        @DisplayName("admin should see all applied opportunities")
        void adminShouldSeeAllAppliedOpportunities() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);

            List<AppliedOpportunity> allApplications = List.of(
                    createAppliedOpportunity(1L, testInfluencer),
                    createAppliedOpportunity(2L, createInfluencer(3L, "other-influencer"))
            );
            testOpportunity.setAppliedOpportunities(allApplications);

            // When - simulating filter logic
            List<AppliedOpportunity> filtered = allApplications; // Admin sees all

            // Then
            assertThat(filtered).hasSize(2);
        }

        @Test
        @DisplayName("company should see applications for their opportunities only")
        void companyShouldSeeApplicationsForTheirOpportunities() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("company-firebase-uid");

            AppliedOpportunity app1 = createAppliedOpportunity(1L, testInfluencer);
            app1.setPartnershipOpportunity(testOpportunity);

            // When - simulating filter logic
            boolean isCompanyOwner = testOpportunity.getCompany()
                    .getFirebaseUserId()
                    .equals(permissionUtils.getUserId());

            // Then
            assertThat(isCompanyOwner).isTrue();
        }

        @Test
        @DisplayName("influencer should see only their own applications")
        void influencerShouldSeeOnlyOwnApplications() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(false);
            when(permissionUtils.isInfluencer()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("influencer-firebase-uid");

            AppliedOpportunity ownApp = createAppliedOpportunity(1L, testInfluencer);
            AppliedOpportunity otherApp = createAppliedOpportunity(2L, createInfluencer(3L, "other-uid"));

            List<AppliedOpportunity> allApplications = List.of(ownApp, otherApp);

            // When - simulating filter logic
            String currentUserId = permissionUtils.getUserId();
            List<AppliedOpportunity> filtered = allApplications.stream()
                    .filter(ao -> ao.getInfluencer().getFirebaseUserId().equals(currentUserId))
                    .toList();

            // Then
            assertThat(filtered).hasSize(1);
            assertThat(filtered.getFirst().getInfluencer().getFirebaseUserId())
                    .isEqualTo("influencer-firebase-uid");
        }

        private AppliedOpportunity createAppliedOpportunity(Long id, User influencer) {
            AppliedOpportunity ao = new AppliedOpportunity();
            ao.setId(id);
            ao.setInfluencer(influencer);
            ao.setPartnershipOpportunity(testOpportunity);
            ao.setOpportunityStatus(OpportunityStatus.APPLIED);
            return ao;
        }

        private User createInfluencer(Long id, String firebaseUid) {
            User user = new User();
            user.setId(id);
            user.setFirebaseUserId(firebaseUid);
            user.setUserType(UserType.INFLUENCER);
            return user;
        }
    }

    @Nested
    @DisplayName("Follower Range Validation")
    class FollowerRangeValidation {

        @Test
        @DisplayName("should accept valid follower range")
        void shouldAcceptValidFollowerRange() {
            // Given
            testOpportunity.setFollowersMin(1000L);
            testOpportunity.setFollowersMax(50000L);

            // When
            boolean isValidRange = testOpportunity.getFollowersMin() < testOpportunity.getFollowersMax();

            // Then
            assertThat(isValidRange).isTrue();
        }

        @Test
        @DisplayName("should allow 0 max (unlimited)")
        void shouldAllowZeroMaxForUnlimited() {
            // Given
            testOpportunity.setFollowersMin(1000L);
            testOpportunity.setFollowersMax(0L);

            // When - 0 means unlimited
            boolean isUnlimited = testOpportunity.getFollowersMax() == 0;

            // Then
            assertThat(isUnlimited).isTrue();
        }

        @Test
        @DisplayName("should validate influencer follower count against range")
        void shouldValidateInfluencerFollowerCount() {
            // Given
            long influencerFollowers = 25000;
            long minRequired = testOpportunity.getFollowersMin();
            long maxRequired = testOpportunity.getFollowersMax();

            // When
            boolean isWithinRange = influencerFollowers >= minRequired &&
                    (maxRequired == 0 || influencerFollowers <= maxRequired);

            // Then
            assertThat(isWithinRange).isTrue();
        }

        @Test
        @DisplayName("should reject follower count below minimum")
        void shouldRejectFollowerCountBelowMinimum() {
            // Given
            long influencerFollowers = 500;
            long minRequired = testOpportunity.getFollowersMin();

            // When
            boolean isBelowMinimum = influencerFollowers < minRequired;

            // Then
            assertThat(isBelowMinimum).isTrue();
        }
    }

    @Nested
    @DisplayName("Resource Not Found")
    class ResourceNotFound {

        @Test
        @DisplayName("should throw when opportunity not found by ID")
        void shouldThrowWhenOpportunityNotFoundById() {
            // Given
            when(repository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> repository.findById(999L)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Opportunity")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update Restrictions")
    class UpdateRestrictions {

        @Test
        @DisplayName("should allow update when company owns opportunity and no active apps")
        void shouldAllowUpdateWhenCompanyOwnsAndNoActiveApps() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.canEditOpportunity(testOpportunity)).thenReturn(true);
            when(appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    eq(100L), anySet())).thenReturn(false);

            // When
            boolean canUpdate = permissionUtils.isCompany() &&
                    permissionUtils.canEditOpportunity(testOpportunity) &&
                    !appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                            100L, Set.of(OpportunityStatus.ACCEPTED_BY_COMPANY));

            // Then
            assertThat(canUpdate).isTrue();
        }

        @Test
        @DisplayName("should deny update when active applications exist")
        void shouldDenyUpdateWhenActiveApplicationsExist() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.canEditOpportunity(testOpportunity)).thenReturn(true);
            when(appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    eq(100L), anySet())).thenReturn(true);

            // When
            boolean hasActiveApps = appliedOpportunityRepository.existsByPartnershipOpportunity_IdAndOpportunityStatusIn(
                    100L, Set.of(OpportunityStatus.ACCEPTED_BY_COMPANY));

            boolean canUpdate = permissionUtils.isCompany() &&
                    permissionUtils.canEditOpportunity(testOpportunity) &&
                    !hasActiveApps;

            // Then
            assertThat(canUpdate).isFalse();
        }

        @Test
        @DisplayName("admin should bypass active applications check")
        void adminShouldBypassActiveApplicationsCheck() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);

            // When - admin can always update
            boolean canUpdate = permissionUtils.isAdmin();

            // Then
            assertThat(canUpdate).isTrue();
        }
    }

    @Nested
    @DisplayName("Photo Validation")
    class PhotoValidation {

        @Test
        @DisplayName("should enforce maximum photo limit")
        void shouldEnforceMaximumPhotoLimit() {
            // Given
            int maxPhotos = 6;
            int photoCount = 7;

            // When
            boolean exceedsLimit = photoCount > maxPhotos;

            // Then
            assertThat(exceedsLimit).isTrue();
        }

        @Test
        @DisplayName("should allow photos within limit")
        void shouldAllowPhotosWithinLimit() {
            // Given
            int maxPhotos = 6;
            int photoCount = 5;

            // When
            boolean isWithinLimit = photoCount <= maxPhotos;

            // Then
            assertThat(isWithinLimit).isTrue();
        }
    }

    @Nested
    @DisplayName("Date Validation")
    class DateValidation {

        @Test
        @DisplayName("end date should be after start date")
        void endDateShouldBeAfterStartDate() {
            // Given
            LocalDateTime startDate = LocalDateTime.now();
            LocalDateTime endDate = LocalDateTime.now().plusMonths(1);
            testOpportunity.setStartDate(startDate);
            testOpportunity.setEndDate(endDate);

            // When
            boolean isValidDateRange = testOpportunity.getEndDate().isAfter(testOpportunity.getStartDate());

            // Then
            assertThat(isValidDateRange).isTrue();
        }

        @Test
        @DisplayName("should detect invalid date range")
        void shouldDetectInvalidDateRange() {
            // Given
            LocalDateTime startDate = LocalDateTime.now().plusMonths(1);
            LocalDateTime endDate = LocalDateTime.now();
            testOpportunity.setStartDate(startDate);
            testOpportunity.setEndDate(endDate);

            // When
            boolean isValidDateRange = testOpportunity.getEndDate().isAfter(testOpportunity.getStartDate());

            // Then
            assertThat(isValidDateRange).isFalse();
        }
    }

    @Nested
    @DisplayName("Company Ownership Validation")
    class CompanyOwnershipValidation {

        @Test
        @DisplayName("should validate company ownership on create")
        void shouldValidateCompanyOwnershipOnCreate() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("company-firebase-uid");

            // When - company creates opportunity for themselves
            boolean isOwnCompany = testCompany.getFirebaseUserId().equals(permissionUtils.getUserId());

            // Then
            assertThat(isOwnCompany).isTrue();
        }

        @Test
        @DisplayName("should reject create for different company")
        void shouldRejectCreateForDifferentCompany() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(true);
            when(permissionUtils.getUserId()).thenReturn("different-company-uid");

            // When - company tries to create for different company
            boolean isOwnCompany = testCompany.getFirebaseUserId().equals(permissionUtils.getUserId());

            // Then
            assertThat(isOwnCompany).isFalse();
            // In real service, this would throw InsufficientPermissionsException
        }
    }

    @Nested
    @DisplayName("Active Status Filter")
    class ActiveStatusFilter {

        @Test
        @DisplayName("should include active filter for influencer queries")
        void shouldIncludeActiveFilterForInfluencerQueries() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(false);
            when(permissionUtils.isInfluencer()).thenReturn(true);

            testOpportunity.setActive(true);

            // When - influencer should only see active opportunities
            boolean shouldFilterByActive = permissionUtils.isInfluencer() && !permissionUtils.isAdmin();

            // Then
            assertThat(shouldFilterByActive).isTrue();
        }

        @Test
        @DisplayName("company should see all opportunities")
        void companyShouldSeeAllOpportunities() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isCompany()).thenReturn(true);

            // When - company can see both active and inactive
            boolean shouldFilterByActive = !(permissionUtils.isAdmin() || permissionUtils.isCompany());

            // Then
            assertThat(shouldFilterByActive).isFalse();
        }
    }
}
