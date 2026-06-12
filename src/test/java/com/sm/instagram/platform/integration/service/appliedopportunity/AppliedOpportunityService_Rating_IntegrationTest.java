package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoOut;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService rating methods.
 * Tests: updateRating(), updateCompanyRating(), updateInfluencerRating() and their DTO variants.
 */
@DisplayName("AppliedOpportunityService - Rating")
class AppliedOpportunityService_Rating_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    // =========================================================================
    // Admin Rating Tests
    // =========================================================================

    @Test
    @DisplayName("Admin can update any rating using generic updateRating method")
    void adminCanUpdateAnyRating() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        authenticateAs(testAdmin);
        AppliedOpportunity result = appliedOpportunityService.updateRating(id, "influencer", RateStatus.POSITIVE);

        assertThat(result.getRateStatus()).isEqualTo(RateStatus.POSITIVE);
    }

    @Test
    @DisplayName("Admin can update company rating")
    void adminCanUpdateCompanyRating() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        authenticateAs(testAdmin);
        AppliedOpportunity result = appliedOpportunityService.updateCompanyRating(id, RateStatus.POSITIVE);

        assertThat(result.getCompanyRateStatus()).isEqualTo(RateStatus.POSITIVE);
    }

    // =========================================================================
    // Influencer Rating Tests (Influencer rates Company)
    // =========================================================================

    @Nested
    @DisplayName("Influencer Rating (rates Company)")
    class InfluencerRatingTests {

        @Test
        @DisplayName("Influencer can rate company positively")
        void influencerCanRateCompanyPositively() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateInfluencerRating(id, RateStatus.POSITIVE);

            assertThat(result.getRateStatus()).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("Influencer can rate company negatively")
        void influencerCanRateCompanyNegatively() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateInfluencerRating(id, RateStatus.NEGATIVE);

            assertThat(result.getRateStatus()).isEqualTo(RateStatus.NEGATIVE);
        }

        @Test
        @DisplayName("Influencer cannot re-rate after already rating")
        void influencerCannotReRate() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = createTestAppliedOpportunity(
                    testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE
            );
            application.setRateStatus(RateStatus.POSITIVE);  // Already rated
            application = saveAppliedOpportunity(application);
            Long id = application.getId();

            assertThatThrownBy(() -> appliedOpportunityService.updateInfluencerRating(id, RateStatus.NEGATIVE))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("Influencer cannot use company rating type")
        void influencerCannotRateAsCompany() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            // Using "company" ratingType as influencer should fail
            assertThatThrownBy(() -> appliedOpportunityService.updateRating(id, "company", RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Influencer cannot rate other influencer's application")
        void influencerCannotRateOtherInfluencerApplication() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            // secondInfluencer tries to rate testInfluencer's application
            createTestSocialConnection(secondInfluencer, 5000);
            authenticateAs(secondInfluencer);

            assertThatThrownBy(() -> appliedOpportunityService.updateInfluencerRating(id, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =========================================================================
    // Company Rating Tests (Company rates Influencer)
    // =========================================================================

    @Nested
    @DisplayName("Company Rating (rates Influencer)")
    class CompanyRatingTests {

        @Test
        @DisplayName("Company can rate influencer positively")
        void companyCanRateInfluencerPositively() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateCompanyRating(id, RateStatus.POSITIVE);

            assertThat(result.getCompanyRateStatus()).isEqualTo(RateStatus.POSITIVE);
        }

        @Test
        @DisplayName("Company can rate influencer negatively")
        void companyCanRateInfluencerNegatively() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateCompanyRating(id, RateStatus.NEGATIVE);

            assertThat(result.getCompanyRateStatus()).isEqualTo(RateStatus.NEGATIVE);
        }

        @Test
        @DisplayName("Company cannot re-rate after already rating")
        void companyCannotReRate() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = createTestAppliedOpportunity(
                    testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE
            );
            application.setCompanyRateStatus(RateStatus.POSITIVE);  // Already rated
            application = saveAppliedOpportunity(application);
            Long id = application.getId();

            authenticateAs(testCompany);

            assertThatThrownBy(() -> appliedOpportunityService.updateCompanyRating(id, RateStatus.NEGATIVE))
                    .isInstanceOf(BusinessRuleTranslatableException.class);
        }

        @Test
        @DisplayName("Company cannot use influencer rating type")
        void companyCannotRateAsInfluencer() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            authenticateAs(testCompany);

            // Using "influencer" ratingType as company should fail
            assertThatThrownBy(() -> appliedOpportunityService.updateRating(id, "influencer", RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Company cannot rate applications to other company's opportunity")
        void companyCannotRateOtherCompanyApplications() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            // secondCompany tries to rate application to testCompany's opportunity
            authenticateAs(secondCompany);

            assertThatThrownBy(() -> appliedOpportunityService.updateCompanyRating(id, RateStatus.POSITIVE))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Test
    @DisplayName("Invalid rating type throws ValidationException")
    void invalidRatingTypeThrows() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        authenticateAs(testAdmin);

        assertThatThrownBy(() -> appliedOpportunityService.updateRating(id, "invalid", RateStatus.POSITIVE))
                .isInstanceOf(ValidationTranslatableException.class);
    }

    // =========================================================================
    // DTO Methods (require HTTP context)
    // =========================================================================

    @Nested
    @DisplayName("DTO Methods (require HTTP context)")
    class DtoMethods {

        @Test
        @DisplayName("updateCompanyRatingAsDto returns DTO")
        void updateCompanyRatingAsDtoReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );

            authenticateAs(testCompany);
            setUpMockHttpContext();

            AppliedOpportunityDtoOut result = appliedOpportunityService.updateCompanyRatingAsDto(
                    application.getId(), RateStatus.POSITIVE
            );

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(application.getId());
        }

        @Test
        @DisplayName("updateInfluencerRatingAsDto returns DTO")
        void updateInfluencerRatingAsDtoReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );

            AppliedOpportunityDtoOut result = appliedOpportunityService.updateInfluencerRatingAsDto(
                    application.getId(), RateStatus.POSITIVE
            );

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(application.getId());
        }
    }
}
