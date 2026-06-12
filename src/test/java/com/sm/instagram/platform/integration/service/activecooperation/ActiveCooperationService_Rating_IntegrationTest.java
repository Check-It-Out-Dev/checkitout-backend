package com.sm.instagram.platform.integration.service.activecooperation;

import com.sm.instagram.platform.activecooperations.CoopDto;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ActiveCooperationService rating methods.
 * Tests updateCompanyRating() and updateInfluencerRating() which return CoopDto.
 *
 * Note: These methods differ from AppliedOpportunityService rating methods:
 * - Return CoopDto instead of AppliedOpportunity entity
 * - Allow re-rating (no business rule preventing it)
 */
@DisplayName("ActiveCooperationService - Rating")
class ActiveCooperationService_Rating_IntegrationTest extends ActiveCooperationServiceIntegrationTestBase {

    @Nested
    @DisplayName("updateCompanyRating()")
    class UpdateCompanyRating {

        @Test
        @DisplayName("Company can rate influencer positively")
        void companyCanRateInfluencerPositively() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When
            CoopDto result = activeCooperationService.updateCompanyRating(cooperation.getId(), RateStatus.POSITIVE);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCompanyRateStatus()).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("Company can rate influencer negatively")
        void companyCanRateInfluencerNegatively() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When
            CoopDto result = activeCooperationService.updateCompanyRating(cooperation.getId(), RateStatus.NEGATIVE);

            // Then
            assertThat(result.getCompanyRateStatus()).isEqualTo(RateStatus.NEGATIVE);
        }

        @Test
        @DisplayName("Admin can rate on behalf of any company")
        void adminCanRateForAnyCompany() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testAdmin);

            // When
            CoopDto result = activeCooperationService.updateCompanyRating(cooperation.getId(), RateStatus.POSITIVE);

            // Then
            assertThat(result.getCompanyRateStatus()).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("Company cannot rate applications to other company's opportunity")
        void companyCannotRateOtherCompanyApplications() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            Long cooperationId = cooperation.getId();
            authenticateAs(secondCompany);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.updateCompanyRating(cooperationId, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Influencer cannot use company rating")
        void influencerCannotUseCompanyRating() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            Long cooperationId = cooperation.getId();
            authenticateAs(testInfluencer);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.updateCompanyRating(cooperationId, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Non-existent cooperation throws ResourceNotFoundException")
        void nonExistentCooperationThrows() {
            // Given
            authenticateAs(testCompany);
            Long nonExistentId = 999999L;

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.updateCompanyRating(nonExistentId, RateStatus.POSITIVE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Company can change rating (re-rate allowed)")
        void companyCanChangeRating() {
            // Given - cooperation already rated
            AppliedOpportunity cooperation = createRatedCooperation(
                    testInfluencer, testPartnershipOpportunity, null, RateStatus.POSITIVE);
            authenticateAs(testCompany);

            // When - change rating
            CoopDto result = activeCooperationService.updateCompanyRating(cooperation.getId(), RateStatus.NEGATIVE);

            // Then - rating changed successfully
            assertThat(result.getCompanyRateStatus()).isEqualTo(RateStatus.NEGATIVE);
        }
    }

    @Nested
    @DisplayName("updateInfluencerRating()")
    class UpdateInfluencerRating {

        @Test
        @DisplayName("Influencer can rate company positively")
        void influencerCanRateCompanyPositively() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testInfluencer);

            // When
            CoopDto result = activeCooperationService.updateInfluencerRating(cooperation.getId(), RateStatus.POSITIVE);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getInfluencerRateStatus()).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("Influencer can rate company negatively")
        void influencerCanRateCompanyNegatively() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testInfluencer);

            // When
            CoopDto result = activeCooperationService.updateInfluencerRating(cooperation.getId(), RateStatus.NEGATIVE);

            // Then
            assertThat(result.getInfluencerRateStatus()).isEqualTo(RateStatus.NEGATIVE);
        }

        @Test
        @DisplayName("Admin can rate on behalf of any influencer")
        void adminCanRateForAnyInfluencer() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testAdmin);

            // When
            CoopDto result = activeCooperationService.updateInfluencerRating(cooperation.getId(), RateStatus.POSITIVE);

            // Then
            assertThat(result.getInfluencerRateStatus()).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("Influencer cannot rate other influencer's applications")
        void influencerCannotRateOtherInfluencerApplications() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            Long cooperationId = cooperation.getId();
            createTestSocialConnection(secondInfluencer, 5000);
            authenticateAs(secondInfluencer);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.updateInfluencerRating(cooperationId, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Company cannot use influencer rating")
        void companyCannotUseInfluencerRating() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            Long cooperationId = cooperation.getId();
            authenticateAs(testCompany);

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.updateInfluencerRating(cooperationId, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Non-existent cooperation throws ResourceNotFoundException")
        void nonExistentCooperationThrows() {
            // Given
            authenticateAs(testInfluencer);
            Long nonExistentId = 999999L;

            // When/Then
            assertThatThrownBy(() -> activeCooperationService.updateInfluencerRating(nonExistentId, RateStatus.POSITIVE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Influencer can change rating (re-rate allowed)")
        void influencerCanChangeRating() {
            // Given - cooperation already rated
            AppliedOpportunity cooperation = createRatedCooperation(
                    testInfluencer, testPartnershipOpportunity, RateStatus.POSITIVE, null);
            authenticateAs(testInfluencer);

            // When - change rating
            CoopDto result = activeCooperationService.updateInfluencerRating(cooperation.getId(), RateStatus.NEGATIVE);

            // Then - rating changed successfully
            assertThat(result.getInfluencerRateStatus()).isEqualTo(RateStatus.NEGATIVE);
        }
    }

    @Nested
    @DisplayName("Rating DTO Content")
    class RatingDtoContent {

        @Test
        @DisplayName("CoopDto contains influencer information")
        void coopDtoContainsInfluencerInfo() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When
            CoopDto result = activeCooperationService.updateCompanyRating(cooperation.getId(), RateStatus.POSITIVE);

            // Then
            assertThat(result.getInfluencerFirstName()).isEqualTo(testInfluencer.getFirstName());
            assertThat(result.getInfluencerLastName()).isEqualTo(testInfluencer.getLastName());
            assertThat(result.getInfluencerEmail()).isEqualTo(testInfluencer.getEmail());
        }

        @Test
        @DisplayName("CoopDto contains opportunity information")
        void coopDtoContainsOpportunityInfo() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testCompany);

            // When
            CoopDto result = activeCooperationService.updateCompanyRating(cooperation.getId(), RateStatus.POSITIVE);

            // Then
            assertThat(result.getAppliedOpportunityId()).isEqualTo(cooperation.getId());
            assertThat(result.getAppliedOpportunityStatus()).isEqualTo(OpportunityStatus.DONE);
            assertThat(result.getPartnershipOpportunityTitle()).isEqualTo(testPartnershipOpportunity.getTitle());
        }

        @Test
        @DisplayName("CoopDto contains company information")
        void coopDtoContainsCompanyInfo() {
            // Given
            AppliedOpportunity cooperation = createDoneCooperation(testInfluencer, testPartnershipOpportunity);
            authenticateAs(testInfluencer);

            // When
            CoopDto result = activeCooperationService.updateInfluencerRating(cooperation.getId(), RateStatus.POSITIVE);

            // Then
            assertThat(result.getCompanyName()).isEqualTo(testCompany.getName());
        }
    }
}
