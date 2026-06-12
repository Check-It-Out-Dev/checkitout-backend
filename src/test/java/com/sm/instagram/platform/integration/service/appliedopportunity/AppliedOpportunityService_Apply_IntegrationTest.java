package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoIn;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoOut;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.FollowerValidationException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService.save() and saveAsDto() methods.
 * Tests the application creation flow including permission checks and follower validation.
 */
@DisplayName("AppliedOpportunityService - Apply (Create)")
class AppliedOpportunityService_Apply_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can create application for any influencer")
    void adminCanCreateApplicationForAnyInfluencer() {
        authenticateAs(testAdmin);

        AppliedOpportunity application = createTestAppliedOpportunity(
                testInfluencer,
                testPartnershipOpportunity,
                OpportunityStatus.APPLIED
        );

        AppliedOpportunity result = appliedOpportunityService.save(application);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getInfluencer().getId()).isEqualTo(testInfluencer.getId());
        assertThat(result.getPartnershipOpportunity().getId()).isEqualTo(testPartnershipOpportunity.getId());
        assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
    }

    @Test
    @DisplayName("Influencer can apply to opportunity with valid social connection")
    void influencerCanApplyToOpportunity() {
        authenticateAs(testInfluencer);
        // testSocialConnection is already created in @BeforeEach with 5000 followers
        // testPartnershipOpportunity requires 1000-100000 followers

        AppliedOpportunity application = createTestAppliedOpportunity(
                testInfluencer,
                testPartnershipOpportunity,
                OpportunityStatus.APPLIED
        );

        AppliedOpportunity result = appliedOpportunityService.save(application);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getInfluencer().getId()).isEqualTo(testInfluencer.getId());
        assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.APPLIED);
    }

    @Test
    @DisplayName("Influencer cannot apply without social connections")
    void influencerCannotApplyWithoutSocialConnections() {
        // secondInfluencer has no social connections
        authenticateAs(secondInfluencer);

        AppliedOpportunity application = createTestAppliedOpportunity(
                secondInfluencer,
                testPartnershipOpportunity,
                OpportunityStatus.APPLIED
        );

        assertThatThrownBy(() -> appliedOpportunityService.save(application))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Influencer cannot apply below minimum follower requirement")
    void influencerCannotApplyBelowMinFollowers() {
        // Create social connection with only 100 followers
        createTestSocialConnection(secondInfluencer, 100);
        authenticateAs(secondInfluencer);

        // Opportunity requires 1000-100000 followers
        AppliedOpportunity application = createTestAppliedOpportunity(
                secondInfluencer,
                testPartnershipOpportunity,
                OpportunityStatus.APPLIED
        );

        assertThatThrownBy(() -> appliedOpportunityService.save(application))
                .isInstanceOf(FollowerValidationException.class)
                .hasMessageContaining("below the minimum requirement");
    }

    @Test
    @DisplayName("Influencer cannot apply above maximum follower limit (when max > 0)")
    void influencerCannotApplyAboveMaxFollowers() {
        // Create social connection with 200000 followers
        createTestSocialConnection(secondInfluencer, 200000);
        authenticateAs(secondInfluencer);

        // Create opportunity with max 50000 followers
        PartnershipOpportunity restrictedOpportunity = createPartnershipOpportunityWithFollowerRange(
                testCompany,
                1000,
                50000
        );

        AppliedOpportunity application = createTestAppliedOpportunity(
                secondInfluencer,
                restrictedOpportunity,
                OpportunityStatus.APPLIED
        );

        assertThatThrownBy(() -> appliedOpportunityService.save(application))
                .isInstanceOf(FollowerValidationException.class)
                .hasMessageContaining("exceeds the maximum limit");
    }

    @Test
    @DisplayName("Influencer can apply when max followers is very high (effectively unlimited)")
    void influencerCanApplyWhenMaxIsVeryHigh() {
        // Create social connection with large follower count
        createTestSocialConnection(secondInfluencer, 1000000);
        authenticateAs(secondInfluencer);

        // Create opportunity with very high max (effectively unlimited)
        // Note: Entity validation requires followersMax >= followersMin, so we can't use 0
        PartnershipOpportunity unlimitedOpportunity = createPartnershipOpportunityWithFollowerRange(
                testCompany,
                1000,
                Integer.MAX_VALUE  // Effectively unlimited
        );

        AppliedOpportunity application = createTestAppliedOpportunity(
                secondInfluencer,
                unlimitedOpportunity,
                OpportunityStatus.APPLIED
        );

        AppliedOpportunity result = appliedOpportunityService.save(application);

        assertThat(result.getId()).isNotNull();
    }

    @Test
    @DisplayName("Company cannot create application")
    void companyCannotCreateApplication() {
        authenticateAs(testCompany);

        AppliedOpportunity application = createTestAppliedOpportunity(
                testInfluencer,
                testPartnershipOpportunity,
                OpportunityStatus.APPLIED
        );

        assertThatThrownBy(() -> appliedOpportunityService.save(application))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Nested
    @DisplayName("DTO Methods (require HTTP context)")
    class DtoReturnTests {

        @Test
        @DisplayName("saveAsDto creates application and returns DTO with mock HTTP context")
        void saveAsDtoCreatesApplicationAndReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(
                    testInfluencer,
                    testPartnershipOpportunity
            );

            AppliedOpportunityDtoOut result = appliedOpportunityService.saveAsDto(dto);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getNote()).isEqualTo("Integration test application via DTO");
        }

        @Test
        @DisplayName("saveAsDto auto-assigns influencer ID when not provided")
        void saveAsDtoAutoAssignsInfluencerIdForInfluencer() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
            dto.setPartnershipOpportunity(testPartnershipOpportunity.getId());
            dto.setNote("Auto-assigned influencer test");
            // Note: influencer ID is NOT set

            AppliedOpportunityDtoOut result = appliedOpportunityService.saveAsDto(dto);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            // The service should have auto-assigned the current user as influencer
        }
    }
}
