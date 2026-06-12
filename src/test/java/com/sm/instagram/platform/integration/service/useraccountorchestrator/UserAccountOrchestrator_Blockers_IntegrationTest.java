package com.sm.instagram.platform.integration.service.useraccountorchestrator;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.dto.DeletionBlocker;
import com.sm.instagram.platform.user.dto.DeletionBlockerCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserAccountOrchestrator.checkSoftDeleteBlockers() method.
 * Tests specific blocker details and OpportunityStatus classification.
 */
@DisplayName("UserAccountOrchestrator - checkSoftDeleteBlockers()")
class UserAccountOrchestrator_Blockers_IntegrationTest extends UserAccountOrchestratorIntegrationTestBase {

    private static final Locale TEST_LOCALE = Locale.ENGLISH;

    /**
     * Active statuses that should block influencer deletion.
     * These are statuses where the collaboration is in progress.
     */
    private static final Set<OpportunityStatus> ACTIVE_STATUSES = Set.of(
            OpportunityStatus.ACCEPTED_BY_COMPANY,
            OpportunityStatus.ACCEPTED_BY_INFLUENCER,
            OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
            OpportunityStatus.CONTENT_APPROVED,
            OpportunityStatus.CONTENT_POSTED,
            OpportunityStatus.CONTENT_REJECTED,
            OpportunityStatus.CONTENT_POSTED_REJECTED
    );

    /**
     * Terminal statuses that should NOT block influencer deletion.
     * These are statuses where the collaboration has ended.
     */
    private static final Set<OpportunityStatus> TERMINAL_STATUSES = Set.of(
            OpportunityStatus.DONE,
            OpportunityStatus.REJECTED_BY_COMPANY,
            OpportunityStatus.REJECTED_BY_INFLUENCER
    );

    @Nested
    @DisplayName("Active OpportunityStatus Classification")
    class ActiveOpportunitiesBlocker {

        @ParameterizedTest(name = "Status {0} should block deletion as ACTIVE_OPPORTUNITIES")
        @EnumSource(value = OpportunityStatus.class, names = {
                "ACCEPTED_BY_COMPANY",
                "ACCEPTED_BY_INFLUENCER",
                "CONTENT_SEND_TO_ACCEPT",
                "CONTENT_APPROVED",
                "CONTENT_POSTED",
                "CONTENT_REJECTED",
                "CONTENT_POSTED_REJECTED"
        })
        @DisplayName("Active status blocks deletion")
        void activeStatusBlocksDeletion(OpportunityStatus status) {
            User influencer = createInfluencerWithSocialConnection("active-" + status.name(), 5000);
            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, status);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            assertThat(blockers)
                    .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
        }

        @ParameterizedTest(name = "Status {0} should NOT block deletion as active")
        @EnumSource(value = OpportunityStatus.class, names = {
                "DONE",
                "REJECTED_BY_COMPANY",
                "REJECTED_BY_INFLUENCER"
        })
        @DisplayName("Terminal status does not create ACTIVE_OPPORTUNITIES blocker")
        void terminalStatusDoesNotBlockAsActive(OpportunityStatus status) {
            User influencer = createInfluencerWithSocialConnection("terminal-" + status.name(), 5000);
            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, status);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            assertThat(blockers)
                    .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
        }

        @Test
        @DisplayName("APPLIED status creates PENDING_OPPORTUNITIES blocker, not ACTIVE_OPPORTUNITIES")
        void appliedStatusCreatesPendingBlocker() {
            User influencer = createInfluencerWithSocialConnection("applied", 5000);
            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, OpportunityStatus.APPLIED);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            assertThat(blockers)
                    .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
            assertThat(blockers)
                    .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.PENDING_OPPORTUNITIES);
        }

        @Test
        @DisplayName("TO_BE_PAID status does not block deletion")
        void toBePaidStatusDoesNotBlock() {
            User influencer = createInfluencerWithSocialConnection("to-be-paid", 5000);
            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, OpportunityStatus.TO_BE_PAID);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            assertThat(blockers)
                    .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
            assertThat(blockers)
                    .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.PENDING_OPPORTUNITIES);
        }
    }

    @Nested
    @DisplayName("Blocker Details")
    class BlockerDetails {

        @Test
        @DisplayName("Blocker contains correct entity IDs for applied opportunities")
        void blockerContainsCorrectEntityIdsForAppliedOpportunities() {
            User influencer = createInfluencerWithSocialConnection("entity-ids", 5000);
            PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(testCompany);

            AppliedOpportunity ao1 = createAppliedOpportunityAtStatus(
                    influencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            AppliedOpportunity ao2 = createAppliedOpportunityAtStatus(
                    influencer, po2, OpportunityStatus.CONTENT_APPROVED);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            DeletionBlocker activeBlocker = blockers.stream()
                    .filter(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .findFirst()
                    .orElseThrow();

            assertThat(activeBlocker.getEntityIds())
                    .containsExactlyInAnyOrder(ao1.getId(), ao2.getId());
            assertThat(activeBlocker.getCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Blocker contains correct entity type")
        void blockerContainsCorrectEntityType() {
            User influencer = createInfluencerWithSocialConnection("entity-type", 5000);
            createAppliedOpportunityAtStatus(
                    influencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_POSTED);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            DeletionBlocker activeBlocker = blockers.stream()
                    .filter(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .findFirst()
                    .orElseThrow();

            assertThat(activeBlocker.getEntityType()).isEqualTo("AppliedOpportunity");
            assertThat(activeBlocker.getEntityDescription()).isNotBlank();
        }

        @Test
        @DisplayName("Blocker contains reason from category")
        void blockerContainsReasonFromCategory() {
            User influencer = createInfluencerWithSocialConnection("reason", 5000);
            createAppliedOpportunityAtStatus(
                    influencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_INFLUENCER);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            DeletionBlocker activeBlocker = blockers.stream()
                    .filter(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .findFirst()
                    .orElseThrow();

            assertThat(activeBlocker.getReason()).isNotBlank();
            assertThat(activeBlocker.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("Partnership opportunity blocker contains correct entity IDs")
        void partnershipOpportunityBlockerContainsCorrectEntityIds() {
            PartnershipOpportunity po1 = createPartnershipOpportunityForCompany(secondCompany);
            PartnershipOpportunity po2 = createPartnershipOpportunityForCompany(secondCompany);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(secondCompany, TEST_LOCALE);

            DeletionBlocker poBlocker = blockers.stream()
                    .filter(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES)
                    .findFirst()
                    .orElseThrow();

            assertThat(poBlocker.getEntityIds())
                    .containsExactlyInAnyOrder(po1.getId(), po2.getId());
            assertThat(poBlocker.getEntityType()).isEqualTo("PartnershipOpportunity");
            assertThat(poBlocker.getCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Last admin blocker has correct structure when present")
        void lastAdminBlockerHasCorrectStructure() {
            // Query current admin count to understand if we can test this scenario
            long adminCount = userRepository.countByUserTypeAndAccountStatusNot(
                    com.sm.instagram.platform.user.UserType.ADMIN,
                    com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED
            );
            long pendingAdminCount = userRepository.countByUserTypeAndAccountStatusNot(
                    com.sm.instagram.platform.user.UserType.PENDING_ADMIN,
                    com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED
            );

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(testAdmin, TEST_LOCALE);

            if (adminCount + pendingAdminCount <= 1) {
                // Last admin scenario - verify blocker structure
                DeletionBlocker adminBlocker = blockers.stream()
                        .filter(b -> b.getCategory() == DeletionBlockerCategory.LAST_ADMIN)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("LAST_ADMIN blocker should be present when only 1 admin exists"));

                assertThat(adminBlocker.getEntityIds()).containsExactly(testAdmin.getId());
                assertThat(adminBlocker.getEntityType()).isEqualTo("User");
                assertThat(adminBlocker.getCount()).isEqualTo(1);
            } else {
                // Multiple admins exist - no LAST_ADMIN blocker should be present
                assertThat(blockers)
                        .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.LAST_ADMIN);
            }
        }
    }

    @Nested
    @DisplayName("Multiple Blockers Scenarios")
    class MultipleBlockersScenarios {

        @Test
        @DisplayName("Multiple active statuses create single ACTIVE_OPPORTUNITIES blocker")
        void multipleActiveStatusesCreateSingleBlocker() {
            User influencer = createInfluencerWithSocialConnection("multi-active", 5000);
            PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(testCompany);
            PartnershipOpportunity po3 = createAndSavePartnershipOpportunity(testCompany);

            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            createAppliedOpportunityAtStatus(influencer, po2, OpportunityStatus.CONTENT_APPROVED);
            createAppliedOpportunityAtStatus(influencer, po3, OpportunityStatus.CONTENT_POSTED);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            // Should have exactly one ACTIVE_OPPORTUNITIES blocker, not three
            long activeBlockerCount = blockers.stream()
                    .filter(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .count();
            assertThat(activeBlockerCount).isEqualTo(1);

            DeletionBlocker activeBlocker = blockers.stream()
                    .filter(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .findFirst()
                    .orElseThrow();
            assertThat(activeBlocker.getCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Mix of active and terminal statuses only counts active ones")
        void mixOfActiveAndTerminalStatusesOnlyCountsActiveOnes() {
            User influencer = createInfluencerWithSocialConnection("mix", 5000);
            PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(testCompany);
            PartnershipOpportunity po3 = createAndSavePartnershipOpportunity(testCompany);
            PartnershipOpportunity po4 = createAndSavePartnershipOpportunity(testCompany);

            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            createAppliedOpportunityAtStatus(influencer, po2, OpportunityStatus.DONE);  // Terminal
            createAppliedOpportunityAtStatus(influencer, po3, OpportunityStatus.CONTENT_POSTED);
            createAppliedOpportunityAtStatus(influencer, po4, OpportunityStatus.REJECTED_BY_COMPANY);  // Terminal

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            DeletionBlocker activeBlocker = blockers.stream()
                    .filter(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .findFirst()
                    .orElseThrow();

            // Only 2 active (ACCEPTED_BY_COMPANY, CONTENT_POSTED), not the terminal ones
            assertThat(activeBlocker.getCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Company with active POs gets ACTIVE_PARTNERSHIP_OPPORTUNITIES blocker")
        void companyWithActivePOsGetsActiveBlocker() {
            // Create active POs for the company
            PartnershipOpportunity activePo1 = createPartnershipOpportunityForCompany(secondCompany);
            PartnershipOpportunity activePo2 = createPartnershipOpportunityForCompany(secondCompany);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(secondCompany, TEST_LOCALE);

            // Should have ACTIVE_PARTNERSHIP_OPPORTUNITIES blocker
            assertThat(blockers)
                    .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES);

            DeletionBlocker activeBlocker = blockers.stream()
                    .filter(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES)
                    .findFirst()
                    .orElseThrow();
            assertThat(activeBlocker.getEntityIds()).containsExactlyInAnyOrder(activePo1.getId(), activePo2.getId());
            assertThat(activeBlocker.getCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Empty blockers list when no blocking conditions")
        void emptyBlockersListWhenNoBlockingConditions() {
            User influencer = createInfluencerWithSocialConnection("empty", 5000);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(influencer, TEST_LOCALE);

            assertThat(blockers).isEmpty();
        }

        @Test
        @DisplayName("Company with only inactive POs has no ACTIVE_PARTNERSHIP_OPPORTUNITIES blocker")
        void companyWithOnlyInactivePOsHasNoActiveBlocker() {
            // Create only inactive POs
            createInactivePartnershipOpportunityForCompany(secondCompany);
            createInactivePartnershipOpportunityForCompany(secondCompany);

            List<DeletionBlocker> blockers = userAccountOrchestrator.checkSoftDeleteBlockers(secondCompany, TEST_LOCALE);

            // Should NOT have ACTIVE_PARTNERSHIP_OPPORTUNITIES blocker
            assertThat(blockers)
                    .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES);
        }
    }
}
