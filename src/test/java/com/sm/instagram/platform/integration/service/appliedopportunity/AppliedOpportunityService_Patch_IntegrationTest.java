package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoOut;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService.patch() method.
 * Tests partial update functionality with field-level permissions.
 */
@DisplayName("AppliedOpportunityService - patch")
class AppliedOpportunityService_Patch_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can patch status field")
    void adminCanPatchStatusField() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long id = application.getId();

        authenticateAs(testAdmin);
        Map<String, Object> updates = new HashMap<>();
        updates.put("opportunityStatus", "ACCEPTED_BY_COMPANY");
        updates.put("influencer", testInfluencer.getId());

        AppliedOpportunity result = appliedOpportunityService.patch(id, updates);

        assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
    }

    @Test
    @DisplayName("Influencer can patch rateStatus")
    void influencerCanPatchRateStatus() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("rateStatus", "POSITIVE");
        updates.put("influencer", testInfluencer.getId());

        AppliedOpportunity result = appliedOpportunityService.patch(id, updates);

        assertThat(result.getRateStatus()).isEqualTo(RateStatus.POSITIVE);
    }

    @Test
    @DisplayName("Influencer must match influencer field in patch")
    void influencerMustMatchInfluencerField() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        // secondInfluencer tries to patch with their own ID
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        Map<String, Object> updates = new HashMap<>();
        updates.put("rateStatus", "POSITIVE");
        updates.put("influencer", secondInfluencer.getId());  // Different influencer

        assertThatThrownBy(() -> appliedOpportunityService.patch(id, updates))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Company cannot use general patch method (must use updateCompanyRating)")
    void companyCannotUseGeneralPatchMethod() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        authenticateAs(testCompany);
        Map<String, Object> updates = new HashMap<>();
        updates.put("companyRateStatus", "POSITIVE");

        // Companies must use updateCompanyRating, not the general patch method
        assertThatThrownBy(() -> appliedOpportunityService.patch(id, updates))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Company must be owner of the opportunity")
    void companyMustBeOwner() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        // secondCompany is not the owner of testPartnershipOpportunity
        authenticateAs(secondCompany);
        Map<String, Object> updates = new HashMap<>();
        updates.put("companyRateStatus", "POSITIVE");

        assertThatThrownBy(() -> appliedOpportunityService.patch(id, updates))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Patch note field")
    void patchNoteField() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        Map<String, Object> updates = new HashMap<>();
        updates.put("note", "Patched note");
        updates.put("influencer", testInfluencer.getId());

        AppliedOpportunity result = appliedOpportunityService.patch(id, updates);

        assertThat(result.getNote()).isEqualTo("Patched note");
    }

    @Nested
    @DisplayName("DTO Methods (require HTTP context)")
    class DtoMethods {

        @Test
        @DisplayName("patchEntityAsDto patches and returns DTO")
        void patchEntityAsDtoReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );

            Map<String, Object> updates = new HashMap<>();
            updates.put("note", "Patched via DTO method");
            updates.put("influencer", testInfluencer.getId());

            AppliedOpportunityDtoOut result = appliedOpportunityService.patchEntityAsDto(application.getId(), updates);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(application.getId());
            assertThat(result.getNote()).isEqualTo("Patched via DTO method");
        }
    }
}
