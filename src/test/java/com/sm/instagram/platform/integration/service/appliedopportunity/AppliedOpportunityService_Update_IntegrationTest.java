package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoIn;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoOut;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService.update() method.
 * Tests permission logic and field-level access control.
 */
@DisplayName("AppliedOpportunityService - update")
class AppliedOpportunityService_Update_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can update all fields including status")
    void adminCanUpdateAllFields() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );
        Long id = application.getId();

        authenticateAs(testAdmin);
        AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(testInfluencer, testPartnershipOpportunity);
        dto.setNote("Updated by admin");
        dto.setOpportunityStatus(OpportunityStatus.ACCEPTED_BY_COMPANY);
        dto.setRateStatus(RateStatus.POSITIVE);
        dto.setCompanyRateStatus(RateStatus.POSITIVE);

        AppliedOpportunity result = appliedOpportunityService.update(id, dto);

        assertThat(result.getNote()).isEqualTo("Updated by admin");
        assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
        assertThat(result.getRateStatus()).isEqualTo(RateStatus.POSITIVE);
        assertThat(result.getCompanyRateStatus()).isEqualTo(RateStatus.POSITIVE);
    }

    @Test
    @DisplayName("Influencer can update note field but rateStatus is skipped in mapping")
    void influencerCanUpdateNoteButRateStatusIsSkipped() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(testInfluencer, testPartnershipOpportunity);
        dto.setNote("Influencer note update");
        dto.setRateStatus(RateStatus.POSITIVE);  // This will be skipped by modelMapper config

        AppliedOpportunity result = appliedOpportunityService.update(id, dto);

        // Note should be updated, but rateStatus is skipped in modelMapper (see AppliedOpportunityMapping)
        // Influencers should use updateInfluencerRating() to update their rating
        assertThat(result.getNote()).isEqualTo("Influencer note update");
        assertThat(result.getRateStatus()).isEqualTo(RateStatus.DEFAULT); // Unchanged due to mapper.skip()
    }

    @Test
    @DisplayName("Influencer cannot update opportunityStatus (reverted silently)")
    void influencerCannotUpdateOpportunityStatus() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(testInfluencer, testPartnershipOpportunity);
        dto.setOpportunityStatus(OpportunityStatus.APPLIED);  // Try to change status

        AppliedOpportunity result = appliedOpportunityService.update(id, dto);

        // Status should be reverted to original (DONE)
        assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.DONE);
    }

    @Test
    @DisplayName("Company cannot use general update method (must use updateCompanyRating)")
    void companyCannotUseGeneralUpdateMethod() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        authenticateAs(testCompany);
        AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(testInfluencer, testPartnershipOpportunity);
        dto.setCompanyRateStatus(RateStatus.POSITIVE);

        // Companies must use the dedicated updateCompanyRating method, not the general update
        assertThatThrownBy(() -> appliedOpportunityService.update(id, dto))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Company cannot access update method at all")
    void companyCannotAccessUpdateMethod() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        authenticateAs(testCompany);
        AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(testInfluencer, testPartnershipOpportunity);
        dto.setRateStatus(RateStatus.POSITIVE);  // Try to change influencer's rating

        // Companies cannot use the general update method
        assertThatThrownBy(() -> appliedOpportunityService.update(id, dto))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Non-owner cannot update application")
    void nonOwnerCannotUpdate() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        // secondInfluencer tries to update testInfluencer's application
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(testInfluencer, testPartnershipOpportunity);

        assertThatThrownBy(() -> appliedOpportunityService.update(id, dto))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Test
    @DisplayName("Update with missing influencer field throws ValidationException")
    void updateThrowsOnMissingInfluencer() {
        authenticateAs(testInfluencer);
        AppliedOpportunity application = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
        );
        Long id = application.getId();

        authenticateAs(testAdmin);
        AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
        dto.setPartnershipOpportunity(testPartnershipOpportunity.getId());
        dto.setInfluencer(null);  // Missing required field

        assertThatThrownBy(() -> appliedOpportunityService.update(id, dto))
                .isInstanceOf(ValidationTranslatableException.class);
    }

    @Test
    @DisplayName("Update non-existent application throws ResourceNotFoundException")
    void updateNonExistentThrows() {
        authenticateAs(testAdmin);
        AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(testInfluencer, testPartnershipOpportunity);

        assertThatThrownBy(() -> appliedOpportunityService.update(999999L, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Nested
    @DisplayName("DTO Methods (require HTTP context)")
    class DtoMethods {

        @Test
        @DisplayName("updateAsDto updates and returns DTO")
        void updateAsDtoReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );

            AppliedOpportunityDtoIn dto = createTestAppliedOpportunityDto(testInfluencer, testPartnershipOpportunity);
            dto.setNote("Updated via DTO method");

            AppliedOpportunityDtoOut result = appliedOpportunityService.updateAsDto(application.getId(), dto);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(application.getId());
            assertThat(result.getNote()).isEqualTo("Updated via DTO method");
        }
    }
}
