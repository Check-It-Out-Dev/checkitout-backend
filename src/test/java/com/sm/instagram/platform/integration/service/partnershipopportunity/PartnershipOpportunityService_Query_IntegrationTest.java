package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PartnershipOpportunityService query methods.
 */
@DisplayName("PartnershipOpportunityService - Query")
class PartnershipOpportunityService_Query_IntegrationTest extends PartnershipOpportunityServiceIntegrationTestBase {

    @Nested
    @DisplayName("getDataPagedAndFiltered")
    class GetDataPagedAndFilteredTests {

        @Test
        @DisplayName("Admin sees all opportunities including inactive")
        void adminSeesAllOpportunities() {
            authenticateAs(testCompany);
            saveOpportunity(createTestOpportunity(testCompany));
            PartnershipOpportunity inactiveOpp = createTestOpportunity(testCompany);
            inactiveOpp.setActive(false);
            saveOpportunity(inactiveOpp);

            authenticateAs(testAdmin);
            Page<PartnershipOpportunity> result = partnershipOpportunityService.getDataPagedAndFiltered(
                    PageRequest.of(0, 10), new HashMap<>()
            );

            assertThat(result.getContent()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Company sees all opportunities")
        void companySeesAllOpportunities() {
            authenticateAs(testCompany);
            saveOpportunity(createTestOpportunity(testCompany));

            authenticateAs(secondCompany);
            saveOpportunity(createTestOpportunity(secondCompany));

            authenticateAs(testCompany);
            Page<PartnershipOpportunity> result = partnershipOpportunityService.getDataPagedAndFiltered(
                    PageRequest.of(0, 10), new HashMap<>()
            );

            assertThat(result.getContent()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Influencer sees only active opportunities")
        void influencerSeesOnlyActiveOpportunities() {
            authenticateAs(testCompany);
            saveOpportunity(createTestOpportunity(testCompany));
            PartnershipOpportunity inactiveOpp = createTestOpportunity(testCompany);
            inactiveOpp.setActive(false);
            inactiveOpp.setName("Inactive Opp");
            saveOpportunity(inactiveOpp);

            authenticateAs(testInfluencer);
            Page<PartnershipOpportunity> result = partnershipOpportunityService.getDataPagedAndFiltered(
                    PageRequest.of(0, 10), new HashMap<>()
            );

            assertThat(result.getContent()).allMatch(PartnershipOpportunity::isActive);
        }

        @Test
        @DisplayName("Unknown user role sees no opportunities")
        void unknownUserRoleSeesNoOpportunities() {
            authenticateAs(testCompany);
            saveOpportunity(createTestOpportunity(testCompany));

            authenticateAs("UNKNOWN-" + UUID.randomUUID(), Permission.PENDING_ADMIN);
            Page<PartnershipOpportunity> result = partnershipOpportunityService.getDataPagedAndFiltered(
                    PageRequest.of(0, 10), new HashMap<>()
            );

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Pagination works correctly")
        void paginationWorksCorrectly() {
            authenticateAs(testCompany);
            for (int i = 0; i < 5; i++) {
                PartnershipOpportunity opp = createTestOpportunity(testCompany);
                opp.setName("Opportunity " + i);
                saveOpportunity(opp);
            }

            authenticateAs(testAdmin);
            Page<PartnershipOpportunity> result = partnershipOpportunityService.getDataPagedAndFiltered(
                    PageRequest.of(0, 2), new HashMap<>()
            );

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(5);
        }
    }

    @Nested
    @DisplayName("findByCompany")
    class FindByCompanyTests {

        @Test
        @DisplayName("Returns opportunities for specific company")
        void returnsOpportunitiesForSpecificCompany() {
            authenticateAs(testCompany);
            saveOpportunity(createTestOpportunity(testCompany));
            saveOpportunity(createTestOpportunity(testCompany));

            authenticateAs(secondCompany);
            saveOpportunity(createTestOpportunity(secondCompany));

            List<PartnershipOpportunity> result = partnershipOpportunityService.findByCompany(testCompany);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(o -> o.getCompany().getId().equals(testCompany.getId()));
        }

        @Test
        @DisplayName("Returns empty list for company with no opportunities")
        void returnsEmptyListForCompanyWithNoOpportunities() {
            List<PartnershipOpportunity> result = partnershipOpportunityService.findByCompany(secondCompany);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findOpportunitiesByStatus")
    class FindOpportunitiesByStatusTests {

        @Test
        @DisplayName("Returns only active opportunities when active=true")
        void returnsOnlyActiveOpportunities() {
            authenticateAs(testCompany);
            saveOpportunity(createTestOpportunity(testCompany));
            PartnershipOpportunity inactiveOpp = createTestOpportunity(testCompany);
            inactiveOpp.setActive(false);
            saveOpportunity(inactiveOpp);

            List<PartnershipOpportunity> result = partnershipOpportunityService.findOpportunitiesByStatus(true);

            assertThat(result).allMatch(PartnershipOpportunity::isActive);
        }

        @Test
        @DisplayName("Returns only inactive opportunities when active=false")
        void returnsOnlyInactiveOpportunities() {
            authenticateAs(testCompany);
            saveOpportunity(createTestOpportunity(testCompany));
            PartnershipOpportunity inactiveOpp = createTestOpportunity(testCompany);
            inactiveOpp.setActive(false);
            saveOpportunity(inactiveOpp);

            List<PartnershipOpportunity> result = partnershipOpportunityService.findOpportunitiesByStatus(false);

            assertThat(result).allMatch(o -> !o.isActive());
        }
    }

    @Nested
    @DisplayName("hasActiveApplications")
    class HasActiveApplicationsTests {

        @Test
        @DisplayName("Returns true when opportunity has ACCEPTED_BY_COMPANY application")
        void returnsTrueForAcceptedByCompany() {
            authenticateAs(testCompany);
            PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
            createAppliedOpportunity(opportunity, testInfluencer, com.sm.instagram.platform.appliedopportunities.OpportunityStatus.ACCEPTED_BY_COMPANY);

            boolean result = partnershipOpportunityService.hasActiveApplications(opportunity);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns true when opportunity has ACCEPTED_BY_INFLUENCER application")
        void returnsTrueForAcceptedByInfluencer() {
            authenticateAs(testCompany);
            PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
            createAppliedOpportunity(opportunity, testInfluencer, com.sm.instagram.platform.appliedopportunities.OpportunityStatus.ACCEPTED_BY_INFLUENCER);

            boolean result = partnershipOpportunityService.hasActiveApplications(opportunity);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns true when opportunity has CONTENT_APPROVED application")
        void returnsTrueForContentApproved() {
            authenticateAs(testCompany);
            PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
            createAppliedOpportunity(opportunity, testInfluencer, com.sm.instagram.platform.appliedopportunities.OpportunityStatus.CONTENT_APPROVED);

            boolean result = partnershipOpportunityService.hasActiveApplications(opportunity);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when opportunity has only APPLIED (pending) application")
        void returnsFalseForAppliedOnly() {
            authenticateAs(testCompany);
            PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));
            createAppliedOpportunity(opportunity, testInfluencer, com.sm.instagram.platform.appliedopportunities.OpportunityStatus.APPLIED);

            boolean result = partnershipOpportunityService.hasActiveApplications(opportunity);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Returns false when opportunity has no applications")
        void returnsFalseForNoApplications() {
            authenticateAs(testCompany);
            PartnershipOpportunity opportunity = saveOpportunity(createTestOpportunity(testCompany));

            boolean result = partnershipOpportunityService.hasActiveApplications(opportunity);

            assertThat(result).isFalse();
        }
    }
}
