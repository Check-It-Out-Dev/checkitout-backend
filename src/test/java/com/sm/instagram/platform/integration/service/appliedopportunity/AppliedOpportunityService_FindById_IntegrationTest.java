package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoOut;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService.findById() method.
 * Tests permission logic for viewing applications.
 */
@DisplayName("AppliedOpportunityService - findById")
class AppliedOpportunityService_FindById_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can find any application")
    void adminCanFindAnyApplication() {
        // Create application as influencer
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long applicationId = application.getId();

        // Admin should be able to find it
        authenticateAs(testAdmin);
        AppliedOpportunity result = appliedOpportunityService.findById(applicationId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(applicationId);
    }

    @Test
    @DisplayName("Company can find application to own opportunity")
    void companyCanFindApplicationToOwnOpportunity() {
        // Create application as influencer
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long applicationId = application.getId();

        // Company (owner of testPartnershipOpportunity) should be able to find it
        authenticateAs(testCompany);
        AppliedOpportunity result = appliedOpportunityService.findById(applicationId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(applicationId);
    }

    @Test
    @DisplayName("Company cannot find application to other company's opportunity")
    void companyCannotFindApplicationToOtherCompanyOpportunity() {
        // Create opportunity for a different company
        PartnershipOpportunity otherCompanyOpportunity = createAndSavePartnershipOpportunity(secondCompany);

        // Create social connection for secondInfluencer and create application
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(secondInfluencer, otherCompanyOpportunity, OpportunityStatus.APPLIED)
        );
        Long applicationId = application.getId();

        // testCompany should NOT be able to view this (belongs to secondCompany)
        authenticateAs(testCompany);

        assertThatThrownBy(() -> appliedOpportunityService.findById(applicationId))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Influencer can find own application")
    void influencerCanFindOwnApplication() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long applicationId = application.getId();

        AppliedOpportunity result = appliedOpportunityService.findById(applicationId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(applicationId);
        assertThat(result.getInfluencer().getId()).isEqualTo(testInfluencer.getId());
    }

    @Test
    @DisplayName("Influencer cannot find other influencer's application")
    void influencerCannotFindOtherInfluencerApplication() {
        // Create application for testInfluencer
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long applicationId = application.getId();

        // secondInfluencer should NOT be able to view testInfluencer's application
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);

        assertThatThrownBy(() -> appliedOpportunityService.findById(applicationId))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Non-existent application throws ResourceNotFoundException")
    void nonExistentApplicationThrows() {
        authenticateAs(testAdmin);

        assertThatThrownBy(() -> appliedOpportunityService.findById(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Nested
    @DisplayName("DTO Conversion Methods (require HTTP context)")
    class DtoConversionTests {

        @Test
        @DisplayName("findByIdAsDto returns DTO with mock HTTP context")
        void findByIdAsDtoReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );

            AppliedOpportunityDtoOut result = appliedOpportunityService.findByIdAsDto(application.getId());

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(application.getId());
            assertThat(result.getNote()).isEqualTo("Integration test application");
        }
    }
}
