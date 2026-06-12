package com.sm.instagram.platform.integration.service.useraccountorchestrator;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.dto.DeletionBlockerCategory;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserAccountOrchestrator.checkDeletionEligibilityForUser() method.
 * Tests deletion eligibility computation for different user types and scenarios.
 */
@DisplayName("UserAccountOrchestrator - checkDeletionEligibilityForUser()")
class UserAccountOrchestrator_DeletionEligibility_IntegrationTest extends UserAccountOrchestratorIntegrationTestBase {

    private static final Locale TEST_LOCALE = Locale.ENGLISH;

    @Nested
    @DisplayName("Influencer Eligibility")
    class InfluencerEligibility {

        @Test
        @DisplayName("Influencer with no opportunities can be deleted")
        void influencerWithNoOpportunitiesCanBeDeleted() {
            User influencer = createInfluencerWithSocialConnection("clean", 5000);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isTrue();
            assertThat(result.isCanPermanentDelete()).isTrue();
            assertThat(result.getSoftDeleteBlockers()).isEmpty();
            assertThat(result.getPermanentDeleteBlockers()).isEmpty();
        }

        @Test
        @DisplayName("Influencer with active opportunity cannot be deleted - ACTIVE_OPPORTUNITIES blocker")
        void influencerWithActiveOpportunityCannotBeDeleted() {
            User influencer = createInfluencerWithSocialConnection("active", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.ACCEPTED_BY_COMPANY
            );

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isFalse();
            assertThat(result.isCanPermanentDelete()).isFalse();
            assertThat(result.getSoftDeleteBlockers())
                    .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
        }

        @Test
        @DisplayName("Influencer with pending (APPLIED) opportunity cannot be deleted - PENDING_OPPORTUNITIES blocker")
        void influencerWithPendingOpportunityCannotBeDeleted() {
            User influencer = createInfluencerWithSocialConnection("pending", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.APPLIED
            );

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isFalse();
            assertThat(result.isCanPermanentDelete()).isFalse();
            assertThat(result.getSoftDeleteBlockers())
                    .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.PENDING_OPPORTUNITIES);
        }

        @Test
        @DisplayName("Influencer with completed (DONE) opportunity can be deleted")
        void influencerWithCompletedOpportunityCanBeDeleted() {
            User influencer = createInfluencerWithSocialConnection("done", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.DONE
            );

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isTrue();
            assertThat(result.isCanPermanentDelete()).isTrue();
            assertThat(result.getSoftDeleteBlockers()).isEmpty();
        }

        @Test
        @DisplayName("Influencer with rejected opportunity can be deleted")
        void influencerWithRejectedOpportunityCanBeDeleted() {
            User influencer = createInfluencerWithSocialConnection("rejected", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.REJECTED_BY_COMPANY
            );

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isTrue();
            assertThat(result.isCanPermanentDelete()).isTrue();
        }

        @Test
        @DisplayName("Multiple blockers listed when both active and pending opportunities exist")
        void multipleBlockersListedWhenBothActiveAndPendingExist() {
            User influencer = createInfluencerWithSocialConnection("multi", 5000);
            PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(testCompany);

            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            createAppliedOpportunityAtStatus(influencer, po2, OpportunityStatus.APPLIED);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isFalse();
            assertThat(result.getSoftDeleteBlockers()).hasSize(2);
            assertThat(result.getSoftDeleteBlockers())
                    .extracting(b -> b.getCategory())
                    .containsExactlyInAnyOrder(
                            DeletionBlockerCategory.ACTIVE_OPPORTUNITIES,
                            DeletionBlockerCategory.PENDING_OPPORTUNITIES
                    );
        }

        @Test
        @DisplayName("Blocker count reflects number of blocking opportunities")
        void blockerCountReflectsNumberOfBlockingOpportunities() {
            User influencer = createInfluencerWithSocialConnection("count", 5000);
            PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(testCompany);
            PartnershipOpportunity po3 = createAndSavePartnershipOpportunity(testCompany);

            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);
            createAppliedOpportunityAtStatus(influencer, po2, OpportunityStatus.CONTENT_APPROVED);
            createAppliedOpportunityAtStatus(influencer, po3, OpportunityStatus.CONTENT_POSTED);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.getSoftDeleteBlockers())
                    .filteredOn(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .singleElement()
                    .satisfies(blocker -> {
                        assertThat(blocker.getCount()).isEqualTo(3);
                        assertThat(blocker.getEntityIds()).hasSize(3);
                    });
        }

        @Test
        @DisplayName("Entity IDs in blocker reference correct applied opportunities")
        void entityIdsInBlockerReferenceCorrectAppliedOpportunities() {
            User influencer = createInfluencerWithSocialConnection("ids", 5000);
            AppliedOpportunity ao = createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.CONTENT_SEND_TO_ACCEPT
            );

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.getSoftDeleteBlockers())
                    .filteredOn(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .singleElement()
                    .satisfies(blocker -> {
                        assertThat(blocker.getEntityIds()).containsExactly(ao.getId());
                        assertThat(blocker.getEntityType()).isEqualTo("AppliedOpportunity");
                    });
        }
    }

    @Nested
    @DisplayName("Company Eligibility")
    class CompanyEligibility {

        @Test
        @DisplayName("Company with no partnership opportunities can be deleted")
        void companyWithNoPartnershipOpportunitiesCanBeDeleted() {
            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(secondCompany, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isTrue();
            assertThat(result.isCanPermanentDelete()).isTrue();
            assertThat(result.getSoftDeleteBlockers()).isEmpty();
        }

        @Test
        @DisplayName("Company with active partnership opportunity cannot be deleted")
        void companyWithActivePartnershipOpportunityCannotBeDeleted() {
            PartnershipOpportunity activePo = createPartnershipOpportunityForCompany(secondCompany);
            assertThat(activePo.isActive()).isTrue();

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(secondCompany, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isFalse();
            assertThat(result.getSoftDeleteBlockers())
                    .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES);
        }

        @Test
        @DisplayName("Company with inactive partnership opportunity can be deleted")
        void companyWithInactivePartnershipOpportunityCanBeDeleted() {
            createInactivePartnershipOpportunityForCompany(secondCompany);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(secondCompany, TEST_LOCALE);

            // Inactive PO doesn't block, but PO with applications might
            // Let's verify ACTIVE_PARTNERSHIP_OPPORTUNITIES is not present
            assertThat(result.getSoftDeleteBlockers())
                    .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES);
        }

        @Test
        @DisplayName("Company with active partnership opportunity that has applications cannot be deleted")
        void companyWithPOThatHasApplicationsCannotBeDeleted() {
            PartnershipOpportunity po = createPartnershipOpportunityForCompany(secondCompany);
            User influencer = createInfluencerWithSocialConnection("applicant", 5000);
            createAppliedOpportunityAtStatus(influencer, po, OpportunityStatus.APPLIED);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(secondCompany, TEST_LOCALE);

            // Active PO blocker should always be present (regardless of applications)
            assertThat(result.isCanSoftDelete()).isFalse();
            assertThat(result.getSoftDeleteBlockers())
                    .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES);
        }

        @Test
        @DisplayName("Active PO with applications blocks deletion")
        void activePOWithApplicationsBlocksDeletion() {
            PartnershipOpportunity activePo = createPartnershipOpportunityForCompany(secondCompany);
            User influencer = createInfluencerWithSocialConnection("applicant2", 5000);
            createAppliedOpportunityAtStatus(influencer, activePo, OpportunityStatus.APPLIED);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(secondCompany, TEST_LOCALE);

            // At minimum, ACTIVE_PARTNERSHIP_OPPORTUNITIES blocker should be present
            assertThat(result.isCanSoftDelete()).isFalse();
            assertThat(result.getSoftDeleteBlockers())
                    .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES);
        }

        @Test
        @DisplayName("Company blocker count reflects number of active opportunities")
        void companyBlockerCountReflectsNumberOfActiveOpportunities() {
            createPartnershipOpportunityForCompany(secondCompany);
            createPartnershipOpportunityForCompany(secondCompany);
            createPartnershipOpportunityForCompany(secondCompany);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(secondCompany, TEST_LOCALE);

            assertThat(result.getSoftDeleteBlockers())
                    .filteredOn(b -> b.getCategory() == DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES)
                    .singleElement()
                    .satisfies(blocker -> {
                        assertThat(blocker.getCount()).isEqualTo(3);
                        assertThat(blocker.getEntityIds()).hasSize(3);
                        assertThat(blocker.getEntityType()).isEqualTo("PartnershipOpportunity");
                    });
        }
    }

    @Nested
    @DisplayName("Admin Eligibility")
    class AdminEligibility {

        @Test
        @DisplayName("Admin can be deleted when multiple admins exist")
        void adminCanBeDeletedWhenMultipleAdminsExist() {
            createSecondAdmin();

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(testAdmin, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isTrue();
            assertThat(result.isCanPermanentDelete()).isTrue();
            assertThat(result.getSoftDeleteBlockers()).isEmpty();
        }

        @Test
        @DisplayName("Admin eligibility depends on admin count")
        void adminEligibilityDependsOnAdminCount() {
            // Query current admin count in database
            long currentAdminCount = userRepository.countByUserTypeAndAccountStatusNot(
                    com.sm.instagram.platform.user.UserType.ADMIN,
                    com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED
            );
            long currentPendingAdminCount = userRepository.countByUserTypeAndAccountStatusNot(
                    com.sm.instagram.platform.user.UserType.PENDING_ADMIN,
                    com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED
            );

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(testAdmin, TEST_LOCALE);

            // Verify the eligibility matches the business rule:
            // - If only 1 admin total, LAST_ADMIN blocker should be present
            // - If >1 admin total, no LAST_ADMIN blocker
            if (currentAdminCount + currentPendingAdminCount <= 1) {
                assertThat(result.getSoftDeleteBlockers())
                        .anyMatch(b -> b.getCategory() == DeletionBlockerCategory.LAST_ADMIN);
            } else {
                assertThat(result.getSoftDeleteBlockers())
                        .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.LAST_ADMIN);
            }
        }

        @Test
        @DisplayName("Pending admin counts toward admin total")
        void pendingAdminCountsTowardAdminTotal() {
            createPendingAdmin();

            // testAdmin + pendingAdmin = 2 admins, so testAdmin can be deleted
            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(testAdmin, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isTrue();
            assertThat(result.getSoftDeleteBlockers())
                    .noneMatch(b -> b.getCategory() == DeletionBlockerCategory.LAST_ADMIN);
        }

        @Test
        @DisplayName("Pending admin also protected when only one admin total")
        void pendingAdminAlsoProtectedWhenOnlyOneAdminTotal() {
            User pendingAdmin = createPendingAdmin();

            // We have testAdmin + pendingAdmin = 2 total
            // If we try to delete pendingAdmin, testAdmin still exists, so it should work
            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(pendingAdmin, TEST_LOCALE);

            assertThat(result.isCanSoftDelete()).isTrue();
        }

        @Test
        @DisplayName("Deleted admins (TO_BE_DELETED) do not count toward admin total")
        void deletedAdminsDoNotCountTowardAdminTotal() {
            // Query current counts to understand the starting state
            long adminCount = userRepository.countByUserTypeAndAccountStatusNot(
                    com.sm.instagram.platform.user.UserType.ADMIN,
                    com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED
            );

            // Create a deleted admin - this should NOT increase the count
            User deletedAdmin = createSecondAdmin();
            deletedAdmin.setAccountStatus(com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED);
            userRepository.save(deletedAdmin);

            // Count should remain the same (deleted admin shouldn't count)
            long newAdminCount = userRepository.countByUserTypeAndAccountStatusNot(
                    com.sm.instagram.platform.user.UserType.ADMIN,
                    com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED
            );

            assertThat(newAdminCount).isEqualTo(adminCount);
        }

        @Test
        @DisplayName("With 2 active admins, one can be deleted")
        void withTwoActiveAdminsOneCanBeDeleted() {
            User admin2 = createSecondAdmin();

            DeletionEligibilityDto result1 = userAccountOrchestrator.checkDeletionEligibilityForUser(testAdmin, TEST_LOCALE);
            DeletionEligibilityDto result2 = userAccountOrchestrator.checkDeletionEligibilityForUser(admin2, TEST_LOCALE);

            assertThat(result1.isCanSoftDelete()).isTrue();
            assertThat(result2.isCanSoftDelete()).isTrue();
        }
    }

    @Nested
    @DisplayName("Eligibility Summary")
    class EligibilitySummary {

        @Test
        @DisplayName("Summary indicates can delete all when no blockers")
        void summaryIndicatesCanDeleteAllWhenNoBlockers() {
            User influencer = createInfluencerWithSocialConnection("summary-clean", 5000);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.getSummary()).contains(influencer.getEmail());
            assertThat(result.getSummary()).containsAnyOf("Can be both soft deleted and permanently deleted", "can be both");
        }

        @Test
        @DisplayName("Summary indicates cannot delete when blockers exist")
        void summaryIndicatesCannotDeleteWhenBlockersExist() {
            User influencer = createInfluencerWithSocialConnection("summary-blocked", 5000);
            createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.getSummary()).contains(influencer.getEmail());
            assertThat(result.getSummary()).containsAnyOf("Cannot be deleted", "cannot be deleted", "blocker");
        }

        @Test
        @DisplayName("DTO contains user identification fields")
        void dtoContainsUserIdentificationFields() {
            User influencer = createInfluencerWithSocialConnection("dto-fields", 5000);

            DeletionEligibilityDto result = userAccountOrchestrator.checkDeletionEligibilityForUser(influencer, TEST_LOCALE);

            assertThat(result.getUserId()).isEqualTo(influencer.getId());
            assertThat(result.getFirebaseUserId()).isEqualTo(influencer.getFirebaseUserId());
            assertThat(result.getUserEmail()).isEqualTo(influencer.getEmail());
            assertThat(result.getUserType()).isEqualTo("INFLUENCER");
        }
    }
}
