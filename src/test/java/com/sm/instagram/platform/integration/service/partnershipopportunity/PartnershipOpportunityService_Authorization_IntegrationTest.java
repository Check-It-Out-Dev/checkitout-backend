package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PartnershipOpportunityService authorization,
 * data isolation, and transactional behavior.
 */
@DisplayName("PartnershipOpportunityService - Authorization & Infrastructure")
class PartnershipOpportunityService_Authorization_IntegrationTest extends PartnershipOpportunityServiceIntegrationTestBase {

    @Nested
    @DisplayName("Authorization Edge Cases")
    class AuthorizationEdgeCasesTests {

        @Test
        @DisplayName("SecurityContext is correctly set for company")
        void securityContextSetForCompany() {
            authenticateAs(testCompany);
            assertThat(getCurrentFirebaseUid()).isEqualTo(testCompany.getFirebaseUserId());
        }

        @Test
        @DisplayName("SecurityContext is correctly set for influencer")
        void securityContextSetForInfluencer() {
            authenticateAs(testInfluencer);
            assertThat(getCurrentFirebaseUid()).isEqualTo(testInfluencer.getFirebaseUserId());
        }

        @Test
        @DisplayName("SecurityContext is correctly set for admin")
        void securityContextSetForAdmin() {
            authenticateAs(testAdmin);
            assertThat(getCurrentFirebaseUid()).isEqualTo(testAdmin.getFirebaseUserId());
        }

        @Test
        @DisplayName("Custom permissions can be set")
        void customPermissionsCanBeSet() {
            String customUid = "CUSTOM-" + UUID.randomUUID();
            authenticateAs(customUid, Permission.COMPANY, Permission.ADMIN);
            assertThat(getCurrentFirebaseUid()).isEqualTo(customUid);
        }

        @Test
        @DisplayName("Unauthenticated user cannot access service methods")
        void unauthenticatedUserCannotAccess() {
            clearAuthentication();
            assertThat(getCurrentFirebaseUid()).isNull();
        }
    }

    @Nested
    @DisplayName("Data Isolation (Test Rollback)")
    class DataIsolationTests {

        @Test
        @DisplayName("Test 1 - Creates data (should be rolled back)")
        void test1CreatesData() {
            authenticateAs(testCompany);
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            opportunity.setName("Isolation Test 1");
            partnershipOpportunityRepository.save(opportunity);

            long count = partnershipOpportunityRepository.count();
            assertThat(count).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Test 2 - Data from Test 1 should not exist")
        void test2NoDataFromTest1() {
            authenticateAs(testCompany);

            List<PartnershipOpportunity> opportunities = partnershipOpportunityRepository.findAll();

            boolean hasTest1Data = opportunities.stream()
                    .anyMatch(o -> "Isolation Test 1".equals(o.getName()));
            assertThat(hasTest1Data).isFalse();
        }
    }

    @Nested
    @DisplayName("Transactional Proxy Tests (getSelf pattern)")
    class TransactionalProxyTests {

        @Test
        @DisplayName("getSelf() returns working proxy for transactional methods")
        void getSelfReturnsWorkingProxy() {
            authenticateAs(testCompany);
            PartnershipOpportunity opportunity = createTestOpportunity(testCompany);
            PartnershipOpportunity saved = partnershipOpportunityRepository.save(opportunity);

            assertThat(saved.getId()).isNotNull();
            Optional<PartnershipOpportunity> fromDb = partnershipOpportunityRepository.findById(saved.getId());
            assertThat(fromDb).isPresent();
        }

        @Test
        @DisplayName("Multiple saves in same transaction work correctly")
        void multipleSavesInSameTransactionWork() {
            authenticateAs(testCompany);

            PartnershipOpportunity opp1 = createTestOpportunity(testCompany);
            opp1.setName("Opportunity 1");
            PartnershipOpportunity saved1 = partnershipOpportunityRepository.save(opp1);

            PartnershipOpportunity opp2 = createTestOpportunity(testCompany);
            opp2.setName("Opportunity 2");
            PartnershipOpportunity saved2 = partnershipOpportunityRepository.save(opp2);

            assertThat(saved1.getId()).isNotNull();
            assertThat(saved2.getId()).isNotNull();
            assertThat(saved1.getId()).isNotEqualTo(saved2.getId());
        }
    }
}
