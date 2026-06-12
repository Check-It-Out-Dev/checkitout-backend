package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService.delete() method.
 * Tests permission logic and status restrictions for deleting applications.
 */
@DisplayName("AppliedOpportunityService - delete")
class AppliedOpportunityService_Delete_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can delete application at any status")
    void adminCanDeleteAnyStatusApplication() {
        // Create application at ACCEPTED_BY_COMPANY status
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY)
        );
        Long applicationId = application.getId();

        // Admin should be able to delete even at non-APPLIED status
        authenticateAs(testAdmin);
        appliedOpportunityService.delete(applicationId);

        // Verify deletion
        assertThat(appliedOpportunityRepository.findById(applicationId)).isEmpty();
    }

    @Test
    @DisplayName("Influencer can delete own application at APPLIED status")
    void influencerCanDeleteOwnApplicationAtAppliedStatus() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long applicationId = application.getId();

        appliedOpportunityService.delete(applicationId);

        assertThat(appliedOpportunityRepository.findById(applicationId)).isEmpty();
    }

    @Test
    @DisplayName("Influencer cannot delete application after it has been accepted")
    void influencerCannotDeleteApplicationAfterAccepted() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY)
        );
        Long applicationId = application.getId();

        assertThatThrownBy(() -> appliedOpportunityService.delete(applicationId))
                .isInstanceOf(InsufficientPermissionsException.class)
                .satisfies(ex -> {
                    InsufficientPermissionsException e = (InsufficientPermissionsException) ex;
                    assertThat(e.getResource()).contains("status not APPLIED");
                });
    }

    @Test
    @DisplayName("Influencer cannot delete other influencer's application")
    void influencerCannotDeleteOtherInfluencerApplication() {
        // Create application for testInfluencer
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long applicationId = application.getId();

        // secondInfluencer should NOT be able to delete testInfluencer's application
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);

        assertThatThrownBy(() -> appliedOpportunityService.delete(applicationId))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Company cannot delete application")
    void companyCannotDeleteApplication() {
        // Create application as influencer
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long applicationId = application.getId();

        // Company (even the owner of the opportunity) should NOT be able to delete
        authenticateAs(testCompany);

        assertThatThrownBy(() -> appliedOpportunityService.delete(applicationId))
                .isInstanceOf(InsufficientPermissionsException.class);
    }
}
