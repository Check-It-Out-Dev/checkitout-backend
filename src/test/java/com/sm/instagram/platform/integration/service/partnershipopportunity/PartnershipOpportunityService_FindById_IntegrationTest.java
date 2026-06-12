package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityDtoOut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for PartnershipOpportunityService.findById() method.
 */
@DisplayName("PartnershipOpportunityService - findById")
class PartnershipOpportunityService_FindById_IntegrationTest extends PartnershipOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can find any opportunity")
    void adminCanFindAnyOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(testAdmin);
        PartnershipOpportunity result = partnershipOpportunityService.findById(opportunityId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(opportunityId);
    }

    @Test
    @DisplayName("Company can find own opportunity")
    void companyCanFindOwnOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        PartnershipOpportunity result = partnershipOpportunityService.findById(opportunityId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(opportunityId);
    }

    @Test
    @DisplayName("Influencer can find active opportunity")
    void influencerCanFindActiveOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
        Long opportunityId = opportunity.getId();

        authenticateAs(testInfluencer);
        PartnershipOpportunity result = partnershipOpportunityService.findById(opportunityId);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Non-existent opportunity throws ResourceNotFoundException")
    void nonExistentOpportunityThrows() {
        authenticateAs(testAdmin);

        assertThatThrownBy(() -> partnershipOpportunityService.findById(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Influencer cannot find inactive opportunity")
    void influencerCannotFindInactiveOpportunity() {
        authenticateAs(testCompany);
        PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
        opportunity.setActive(false);
        opportunity = saveOpportunity(opportunity);
        Long opportunityId = opportunity.getId();

        authenticateAs(testInfluencer);

        assertThatThrownBy(() -> partnershipOpportunityService.findById(opportunityId))
                .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Nested
    @DisplayName("DTO Conversion Methods (require HTTP context)")
    class DtoConversionTests {

        @Test
        @DisplayName("findByIdAsDto returns DTO with mock HTTP context")
        void findByIdAsDtoReturnsDto() {
            authenticateAs(testCompany);
            setUpMockHttpContext();  // Enable HTTP context for getLocaleFromRequest()

            PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));

            PartnershipOpportunityDtoOut result = partnershipOpportunityService.findByIdAsDto(opportunity.getId());

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(opportunity.getId());
            assertThat(result.getTitle()).isEqualTo(opportunity.getTitle());
        }

        @Test
        @DisplayName("convertToFilteredDto converts entity to DTO with mock HTTP context")
        void convertToFilteredDtoConvertsEntityToDto() {
            authenticateAs(testCompany);
            setUpMockHttpContext();  // Enable HTTP context for getLocaleFromRequest()

            PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));

            PartnershipOpportunityDtoOut result = partnershipOpportunityService.convertToFilteredDto(opportunity);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(opportunity.getId());
            assertThat(result.getTitle()).isEqualTo(opportunity.getTitle());
        }
    }
}
