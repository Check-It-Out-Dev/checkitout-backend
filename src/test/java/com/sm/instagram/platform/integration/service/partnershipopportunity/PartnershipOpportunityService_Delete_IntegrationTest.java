package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for PartnershipOpportunityService.delete() method.
 */
@DisplayName("PartnershipOpportunityService - delete")
class PartnershipOpportunityService_Delete_IntegrationTest extends PartnershipOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can delete any opportunity")
    void adminCanDeleteAnyOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(testAdmin);
        partnershipOpportunityService.delete(opportunityId);

        PartnershipOpportunity result = partnershipOpportunityRepository.findById(opportunityId).orElseThrow();
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("Company can delete own opportunity")
    void companyCanDeleteOwnOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        partnershipOpportunityService.delete(opportunityId);

        PartnershipOpportunity result = partnershipOpportunityRepository.findById(opportunityId).orElseThrow();
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("Cannot delete already inactive opportunity")
    void cannotDeleteAlreadyInactiveOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
        opportunity.setActive(false);
        opportunity = saveOpportunity(opportunity);
        Long opportunityId = opportunity.getId();

        assertThatThrownBy(() -> partnershipOpportunityService.delete(opportunityId))
                .isInstanceOf(BusinessRuleTranslatableException.class);
    }

    @Test
    @DisplayName("Cannot delete opportunity with active applications")
    void cannotDeleteOpportunityWithActiveApplications() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        createAppliedOpportunity(opportunity, testInfluencer, OpportunityStatus.ACCEPTED_BY_COMPANY);
        Long opportunityId = opportunity.getId();

        assertThatThrownBy(() -> partnershipOpportunityService.delete(opportunityId))
                .isInstanceOf(BusinessRuleTranslatableException.class);
    }

    @Test
    @DisplayName("Delete non-existent opportunity throws ResourceNotFoundException")
    void deleteNonExistentOpportunityThrows() {
        authenticateAs(testAdmin);

        assertThatThrownBy(() -> partnershipOpportunityService.delete(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Company cannot delete other company's opportunity (silent - no exception)")
    void companyCannotDeleteOtherCompanyOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(secondCompany);
        partnershipOpportunityService.delete(opportunityId);

        // Opportunity remains active (silent failure - potential bug)
        PartnershipOpportunity result = partnershipOpportunityRepository.findById(opportunityId).orElseThrow();
        assertThat(result.isActive()).isTrue();
    }
}
