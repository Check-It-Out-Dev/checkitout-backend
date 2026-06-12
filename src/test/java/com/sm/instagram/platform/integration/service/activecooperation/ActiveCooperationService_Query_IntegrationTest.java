package com.sm.instagram.platform.integration.service.activecooperation;

import com.sm.instagram.platform.activecooperations.CoopDto;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ActiveCooperationService query methods.
 * Tests getInfluencersToRate(), getInfluencersToAccept(), and findAll().
 */
@DisplayName("ActiveCooperationService - Query")
class ActiveCooperationService_Query_IntegrationTest extends ActiveCooperationServiceIntegrationTestBase {

    private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 10);

    @Nested
    @DisplayName("getInfluencersToRate()")
    class GetInfluencersToRate {

        @Test
        @DisplayName("Returns DONE cooperations for rating")
        void returnsDoneCooperationsForRating() {
            // Given - Create a cooperation at DONE status
            createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When
            Page<CoopDto> result = activeCooperationService.getInfluencersToRate("ALL", DEFAULT_PAGEABLE);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent().get(0).getAppliedOpportunityStatus()).isEqualTo(OpportunityStatus.DONE);
        }

        @Test
        @DisplayName("Filters by POSITIVE rate status")
        void filtersByPositiveRateStatus() {
            // Given
            AppliedOpportunity rated = createRatedCooperation(
                    testInfluencer, testPartnershipOpportunity, RateStatus.POSITIVE, RateStatus.DEFAULT);
            createDoneCooperation(secondInfluencer, testPartnershipOpportunity);
            createTestSocialConnection(secondInfluencer, 5000);
            authenticateAs(testCompany);

            // When
            Page<CoopDto> result = activeCooperationService.getInfluencersToRate("POSITIVE", DEFAULT_PAGEABLE);

            // Then
            assertThat(result.getContent()).allMatch(dto -> dto.getInfluencerRateStatus() == RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("Filters by NEGATIVE rate status")
        void filtersByNegativeRateStatus() {
            // Given
            createRatedCooperation(testInfluencer, testPartnershipOpportunity, RateStatus.NEGATIVE, RateStatus.DEFAULT);
            authenticateAs(testCompany);

            // When
            Page<CoopDto> result = activeCooperationService.getInfluencersToRate("NEGATIVE", DEFAULT_PAGEABLE);

            // Then
            assertThat(result.getContent()).allMatch(dto -> dto.getInfluencerRateStatus() == RateStatus.NEGATIVE);
        }

        @Test
        @DisplayName("Company only sees own opportunities")
        void companyOnlySeesOwnOpportunities() {
            // Given - Create cooperation for testCompany
            createDoneCooperation(testInfluencer, testPartnershipOpportunity);

            // Create cooperation for secondCompany
            PartnershipOpportunity otherOpportunity = createAndSavePartnershipOpportunity(secondCompany);
            createTestSocialConnection(secondInfluencer, 5000);
            createDoneCooperation(secondInfluencer, otherOpportunity);

            authenticateAs(testCompany);

            // When
            Page<CoopDto> result = activeCooperationService.getInfluencersToRate("ALL", DEFAULT_PAGEABLE);

            // Then - Only sees testCompany's opportunities
            assertThat(result.getContent()).allMatch(dto ->
                    dto.getPartnershipOpportunityTitle().equals(testPartnershipOpportunity.getTitle()));
        }

        @Test
        @DisplayName("Invalid rate status throws ValidationException")
        void invalidRateStatusThrows() {
            // Given
            authenticateAs(testCompany);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.getInfluencersToRate("INVALID", DEFAULT_PAGEABLE))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("getInfluencersToRateWithPermission()")
    class GetInfluencersToRateWithPermission {

        @Test
        @DisplayName("Admin can access all cooperations")
        void adminCanAccessAllCooperations() {
            // Given
            createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testAdmin);

            // When
            Page<CoopDto> result = activeCooperationService.getInfluencersToRateWithPermission("ALL", DEFAULT_PAGEABLE);

            // Then
            assertThat(result.getContent()).isNotEmpty();
        }

        @Test
        @DisplayName("Non-admin is denied access")
        void nonAdminDeniedAccess() {
            // Given
            authenticateAs(testCompany);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.getInfluencersToRateWithPermission("ALL", DEFAULT_PAGEABLE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Influencer is denied access")
        void influencerDeniedAccess() {
            // Given
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.getInfluencersToRateWithPermission("ALL", DEFAULT_PAGEABLE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("getInfluencersToAccept()")
    class GetInfluencersToAccept {

        @Test
        @DisplayName("Returns APPLIED cooperations")
        void returnsAppliedCooperations() {
            // Given
            createAppliedCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When
            Page<CoopDto> result = activeCooperationService.getInfluencersToAccept(null, null, null, DEFAULT_PAGEABLE);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent().get(0).getAppliedOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
        }

        @Test
        @DisplayName("Filters by minimum followers")
        void filtersByMinFollowers() {
            // Given - influencer with 5000 followers
            createAppliedCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When - filter for min 3000 followers
            Page<CoopDto> result = activeCooperationService.getInfluencersToAccept(3000, null, null, DEFAULT_PAGEABLE);

            // Then - should include influencer with 5000 followers
            assertThat(result.getContent()).isNotEmpty();
        }

        @Test
        @DisplayName("Filters by maximum followers")
        void filtersByMaxFollowers() {
            // Given - influencer with 5000 followers
            createAppliedCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When - filter for max 10000 followers
            Page<CoopDto> result = activeCooperationService.getInfluencersToAccept(null, 10000, null, DEFAULT_PAGEABLE);

            // Then - should include influencer with 5000 followers
            assertThat(result.getContent()).isNotEmpty();
        }

        @Test
        @DisplayName("Filters by follower range")
        void filtersByFollowerRange() {
            // Given - influencer with 5000 followers
            createAppliedCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When - filter for 1000-10000 followers
            Page<CoopDto> result = activeCooperationService.getInfluencersToAccept(1000, 10000, null, DEFAULT_PAGEABLE);

            // Then
            assertThat(result.getContent()).isNotEmpty();
        }

        @Test
        @DisplayName("Excludes influencers below min followers")
        void excludesInfluencersBelowMinFollowers() {
            // Given - influencer with 5000 followers
            createAppliedCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When - filter for min 10000 followers
            Page<CoopDto> result = activeCooperationService.getInfluencersToAccept(10000, null, null, DEFAULT_PAGEABLE);

            // Then - should exclude influencer with 5000 followers
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Invalid follower range throws ValidationException")
        void invalidFollowerRangeThrows() {
            // Given
            authenticateAs(testCompany);

            // When/Then - minFollowers > maxFollowers
            assertThatThrownBy(() -> activeCooperationService.getInfluencersToAccept(10000, 5000, null, DEFAULT_PAGEABLE))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("Company only sees applications to own opportunities")
        void companyOnlySeesOwnApplications() {
            // Given
            createAppliedCooperation(testInfluencer, testPartnershipOpportunity);

            PartnershipOpportunity otherOpportunity = createAndSavePartnershipOpportunity(secondCompany);
            createTestSocialConnection(secondInfluencer, 5000);
            createAppliedCooperation(secondInfluencer, otherOpportunity);

            authenticateAs(testCompany);

            // When
            Page<CoopDto> result = activeCooperationService.getInfluencersToAccept(null, null, null, DEFAULT_PAGEABLE);

            // Then
            assertThat(result.getContent()).allMatch(dto ->
                    dto.getPartnershipOpportunityTitle().equals(testPartnershipOpportunity.getTitle()));
        }
    }

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("Pagination works correctly for getInfluencersToRate")
        void paginationWorksForGetInfluencersToRate() {
            // Given - Create 5 cooperations
            createMultipleCooperations(testInfluencer, testCompany, 5, OpportunityStatus.DONE);
            authenticateAs(testCompany);

            // When - Request page 0 with size 2
            Page<CoopDto> page0 = activeCooperationService.getInfluencersToRate("ALL", PageRequest.of(0, 2));

            // Then
            assertThat(page0.getContent()).hasSize(2);
            assertThat(page0.getTotalElements()).isGreaterThanOrEqualTo(5);
            assertThat(page0.getTotalPages()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Empty page returned when no matching cooperations")
        void emptyPageReturnedWhenNoMatch() {
            // Given - No cooperations created
            authenticateAs(testCompany);

            // When
            Page<CoopDto> result = activeCooperationService.getInfluencersToRate("ALL", DEFAULT_PAGEABLE);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }
}
