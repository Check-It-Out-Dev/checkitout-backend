package com.sm.instagram.platform.integration.service.useraccountorchestrator;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserAccountOrchestrator.archiveUser() method.
 * Tests archival behavior for different user types.
 */
@DisplayName("UserAccountOrchestrator - archiveUser()")
class UserAccountOrchestrator_Archive_IntegrationTest extends UserAccountOrchestratorIntegrationTestBase {

    @Nested
    @DisplayName("Influencer Archival")
    class InfluencerArchival {

        @Test
        @DisplayName("Archives clean influencer with no opportunities")
        void archivesCleanInfluencerWithNoOpportunities() {
            User influencer = createInfluencerWithSocialConnection("clean", 5000);
            Long initialTokenVersion = influencer.getTokenVersion();

            userAccountOrchestrator.archiveUser(influencer);

            User archived = userRepository.findById(influencer.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
            assertThat(archived.getTokenVersion()).isEqualTo(initialTokenVersion + 1);
        }

        @Test
        @DisplayName("Archives influencer and deletes pending (APPLIED) applications")
        void archivesInfluencerAndDeletesPendingApplications() {
            User influencer = createInfluencerWithSocialConnection("pending", 5000);
            AppliedOpportunity pendingAo = createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.APPLIED
            );
            Long aoId = pendingAo.getId();

            // Authenticate as admin since internal delete() calls require SecurityContext
            authenticateAs(testAdmin);
            userAccountOrchestrator.archiveUser(influencer);

            // Verify influencer is archived
            User archived = userRepository.findById(influencer.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);

            // Verify pending application was deleted
            assertThat(appliedOpportunityRepository.findById(aoId)).isEmpty();
        }

        @Test
        @DisplayName("Throws when influencer has active opportunity (ACCEPTED_BY_COMPANY)")
        void throwsWhenInfluencerHasActiveOpportunityAcceptedByCompany() {
            User influencer = createInfluencerWithSocialConnection("active-abc", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.ACCEPTED_BY_COMPANY
            );

            assertThatThrownBy(() -> userAccountOrchestrator.archiveUser(influencer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unfinished opportunities");
        }

        @Test
        @DisplayName("Throws when influencer has active opportunity (ACCEPTED_BY_INFLUENCER)")
        void throwsWhenInfluencerHasActiveOpportunityAcceptedByInfluencer() {
            User influencer = createInfluencerWithSocialConnection("active-abi", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.ACCEPTED_BY_INFLUENCER
            );

            assertThatThrownBy(() -> userAccountOrchestrator.archiveUser(influencer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unfinished opportunities");
        }

        @Test
        @DisplayName("Throws when influencer has active opportunity (CONTENT_SEND_TO_ACCEPT)")
        void throwsWhenInfluencerHasActiveOpportunityContentSent() {
            User influencer = createInfluencerWithSocialConnection("active-csta", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.CONTENT_SEND_TO_ACCEPT
            );

            assertThatThrownBy(() -> userAccountOrchestrator.archiveUser(influencer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unfinished opportunities");
        }

        @Test
        @DisplayName("Throws when influencer has active opportunity (CONTENT_APPROVED)")
        void throwsWhenInfluencerHasActiveOpportunityContentApproved() {
            User influencer = createInfluencerWithSocialConnection("active-ca", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.CONTENT_APPROVED
            );

            assertThatThrownBy(() -> userAccountOrchestrator.archiveUser(influencer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unfinished opportunities");
        }

        @Test
        @DisplayName("Throws when influencer has active opportunity (CONTENT_POSTED)")
        void throwsWhenInfluencerHasActiveOpportunityContentPosted() {
            User influencer = createInfluencerWithSocialConnection("active-cp", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.CONTENT_POSTED
            );

            assertThatThrownBy(() -> userAccountOrchestrator.archiveUser(influencer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unfinished opportunities");
        }

        @Test
        @DisplayName("Throws when influencer has active opportunity (CONTENT_REJECTED)")
        void throwsWhenInfluencerHasActiveOpportunityContentRejected() {
            User influencer = createInfluencerWithSocialConnection("active-cr", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.CONTENT_REJECTED
            );

            assertThatThrownBy(() -> userAccountOrchestrator.archiveUser(influencer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unfinished opportunities");
        }

        @Test
        @DisplayName("Throws when influencer has active opportunity (CONTENT_POSTED_REJECTED)")
        void throwsWhenInfluencerHasActiveOpportunityContentPostedRejected() {
            User influencer = createInfluencerWithSocialConnection("active-cpr", 5000);
            createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.CONTENT_POSTED_REJECTED
            );

            assertThatThrownBy(() -> userAccountOrchestrator.archiveUser(influencer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unfinished opportunities");
        }

        @Test
        @DisplayName("Archives influencer with completed (DONE) opportunity")
        void archivesInfluencerWithCompletedOpportunity() {
            User influencer = createInfluencerWithSocialConnection("done", 5000);
            AppliedOpportunity doneAo = createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.DONE
            );

            userAccountOrchestrator.archiveUser(influencer);

            User archived = userRepository.findById(influencer.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);

            // DONE opportunity should NOT be deleted (historical record)
            assertThat(appliedOpportunityRepository.findById(doneAo.getId())).isPresent();
        }

        @Test
        @DisplayName("Archives influencer with rejected by company opportunity")
        void archivesInfluencerWithRejectedByCompanyOpportunity() {
            User influencer = createInfluencerWithSocialConnection("rbc", 5000);
            AppliedOpportunity rejectedAo = createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.REJECTED_BY_COMPANY
            );

            userAccountOrchestrator.archiveUser(influencer);

            User archived = userRepository.findById(influencer.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);

            // Rejected opportunity should NOT be deleted
            assertThat(appliedOpportunityRepository.findById(rejectedAo.getId())).isPresent();
        }

        @Test
        @DisplayName("Archives influencer with rejected by influencer opportunity")
        void archivesInfluencerWithRejectedByInfluencerOpportunity() {
            User influencer = createInfluencerWithSocialConnection("rbi", 5000);
            AppliedOpportunity rejectedAo = createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.REJECTED_BY_INFLUENCER
            );

            userAccountOrchestrator.archiveUser(influencer);

            User archived = userRepository.findById(influencer.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);

            // Rejected opportunity should NOT be deleted
            assertThat(appliedOpportunityRepository.findById(rejectedAo.getId())).isPresent();
        }

        @Test
        @DisplayName("Archives influencer with TO_BE_PAID opportunity (not considered active)")
        void archivesInfluencerWithToBePaidOpportunity() {
            User influencer = createInfluencerWithSocialConnection("tbp", 5000);
            AppliedOpportunity toBePaidAo = createAppliedOpportunityAtStatus(
                    influencer,
                    testPartnershipOpportunity,
                    OpportunityStatus.TO_BE_PAID
            );

            // TO_BE_PAID is not in the "active" status set, so archival should succeed
            userAccountOrchestrator.archiveUser(influencer);

            User archived = userRepository.findById(influencer.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
            assertThat(appliedOpportunityRepository.findById(toBePaidAo.getId())).isPresent();
        }

        @Test
        @DisplayName("Deletes multiple pending applications on archival")
        void deletesMultiplePendingApplicationsOnArchival() {
            User influencer = createInfluencerWithSocialConnection("multi-pending", 5000);

            PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(testCompany);
            PartnershipOpportunity po3 = createAndSavePartnershipOpportunity(testCompany);

            AppliedOpportunity ao1 = createAppliedOpportunityAtStatus(influencer, testPartnershipOpportunity, OpportunityStatus.APPLIED);
            AppliedOpportunity ao2 = createAppliedOpportunityAtStatus(influencer, po2, OpportunityStatus.APPLIED);
            AppliedOpportunity ao3 = createAppliedOpportunityAtStatus(influencer, po3, OpportunityStatus.APPLIED);

            // Authenticate as admin since internal delete() calls require SecurityContext
            authenticateAs(testAdmin);
            userAccountOrchestrator.archiveUser(influencer);

            assertThat(appliedOpportunityRepository.findById(ao1.getId())).isEmpty();
            assertThat(appliedOpportunityRepository.findById(ao2.getId())).isEmpty();
            assertThat(appliedOpportunityRepository.findById(ao3.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Company Archival")
    class CompanyArchival {

        @Test
        @DisplayName("Archives company with no partnership opportunities")
        void archivesCompanyWithNoPartnershipOpportunities() {
            // Use secondCompany which has no POs (testCompany has testPartnershipOpportunity from base)
            Long initialTokenVersion = secondCompany.getTokenVersion();

            userAccountOrchestrator.archiveUser(secondCompany);

            User archived = userRepository.findById(secondCompany.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
            assertThat(archived.getTokenVersion()).isEqualTo(initialTokenVersion + 1);
            assertThat(archived.getFirstName()).isEqualTo("N/A");
            assertThat(archived.getLastName()).isEqualTo("N/A");
            assertThat(archived.getPhoneNumber()).isNull();
        }

        @Test
        @DisplayName("Deactivates active partnership opportunities on archival")
        void deactivatesActivePartnershipOpportunitiesOnArchival() {
            PartnershipOpportunity activePo = createPartnershipOpportunityForCompany(secondCompany);
            Long poId = activePo.getId();
            assertThat(activePo.isActive()).isTrue();

            // Authenticate as admin since internal delete() calls require SecurityContext
            authenticateAs(testAdmin);
            userAccountOrchestrator.archiveUser(secondCompany);

            User archived = userRepository.findById(secondCompany.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);

            // PO should be deactivated (delete() in PartnershipOpportunityService deactivates rather than hard deletes)
            PartnershipOpportunity deactivatedPo = partnershipOpportunityRepository.findById(poId).orElseThrow();
            assertThat(deactivatedPo.isActive()).isFalse();
        }

        @Test
        @DisplayName("Preserves inactive partnership opportunities on archival")
        void preservesInactivePartnershipOpportunitiesOnArchival() {
            PartnershipOpportunity inactivePo = createInactivePartnershipOpportunityForCompany(secondCompany);
            Long poId = inactivePo.getId();

            userAccountOrchestrator.archiveUser(secondCompany);

            // Inactive PO should NOT be deleted
            assertThat(partnershipOpportunityRepository.findById(poId)).isPresent();
        }

        @Test
        @DisplayName("Company archival completes successfully with both active and inactive POs")
        void companyArchivalCompletesWithBothActiveAndInactivePOs() {
            PartnershipOpportunity activePo = createPartnershipOpportunityForCompany(secondCompany);
            PartnershipOpportunity inactivePo = createInactivePartnershipOpportunityForCompany(secondCompany);
            Long inactivePoId = inactivePo.getId();

            // Authenticate as admin since internal delete() calls require SecurityContext
            authenticateAs(testAdmin);
            userAccountOrchestrator.archiveUser(secondCompany);

            // Verify company is archived
            User archived = userRepository.findById(secondCompany.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);

            // Inactive PO should be preserved
            assertThat(partnershipOpportunityRepository.findById(inactivePoId)).isPresent();
        }

        @Test
        @DisplayName("Anonymizes user PII on archival")
        void anonymizesUserPiiOnArchival() {
            secondCompany.setFirstName("John");
            secondCompany.setLastName("Doe");
            secondCompany.setPhoneNumber("+1234567890");
            userRepository.save(secondCompany);

            userAccountOrchestrator.archiveUser(secondCompany);

            User archived = userRepository.findById(secondCompany.getId()).orElseThrow();
            assertThat(archived.getFirstName()).isEqualTo("N/A");
            assertThat(archived.getLastName()).isEqualTo("N/A");
            assertThat(archived.getPhoneNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("Admin Archival")
    class AdminArchival {

        @Test
        @DisplayName("Archives admin user")
        void archivesAdminUser() {
            // Create second admin to ensure we're not the last admin
            createSecondAdmin();
            Long initialTokenVersion = testAdmin.getTokenVersion();

            userAccountOrchestrator.archiveUser(testAdmin);

            User archived = userRepository.findById(testAdmin.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
            assertThat(archived.getTokenVersion()).isEqualTo(initialTokenVersion + 1);
        }

        @Test
        @DisplayName("Archives pending admin user")
        void archivesPendingAdminUser() {
            User pendingAdmin = createPendingAdmin();
            Long initialTokenVersion = pendingAdmin.getTokenVersion();

            userAccountOrchestrator.archiveUser(pendingAdmin);

            User archived = userRepository.findById(pendingAdmin.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
            assertThat(archived.getTokenVersion()).isEqualTo(initialTokenVersion + 1);
        }

        @Test
        @DisplayName("Preserves admin PII for audit trail")
        void preservesAdminPiiForAuditTrail() {
            createSecondAdmin();
            testAdmin.setFirstName("Admin");
            testAdmin.setLastName("User");
            testAdmin.setPhoneNumber("+0987654321");
            userRepository.save(testAdmin);

            userAccountOrchestrator.archiveUser(testAdmin);

            User archived = userRepository.findById(testAdmin.getId()).orElseThrow();
            // Admin PII should NOT be anonymized (unlike company)
            assertThat(archived.getFirstName()).isEqualTo("Admin");
            assertThat(archived.getLastName()).isEqualTo("User");
            assertThat(archived.getPhoneNumber()).isEqualTo("+0987654321");
        }
    }

    @Nested
    @DisplayName("Common Archival Behavior")
    class CommonArchivalBehavior {

        @Test
        @DisplayName("Sets account status to TO_BE_DELETED")
        void setsAccountStatusToBeDeleted() {
            User influencer = createInfluencerWithSocialConnection("status", 5000);
            assertThat(influencer.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

            userAccountOrchestrator.archiveUser(influencer);

            User archived = userRepository.findById(influencer.getId()).orElseThrow();
            assertThat(archived.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
        }

        @Test
        @DisplayName("Increments token version for session invalidation")
        void incrementsTokenVersionForSessionInvalidation() {
            User influencer = createInfluencerWithSocialConnection("token", 5000);
            Long initialVersion = influencer.getTokenVersion();

            userAccountOrchestrator.archiveUser(influencer);

            User archived = userRepository.findById(influencer.getId()).orElseThrow();
            assertThat(archived.getTokenVersion()).isEqualTo(initialVersion + 1);
        }

        @Test
        @DisplayName("Persists changes to database")
        void persistsChangesToDatabase() {
            User influencer = createInfluencerWithSocialConnection("persist", 5000);
            Long userId = influencer.getId();

            userAccountOrchestrator.archiveUser(influencer);

            // Fetch fresh from DB to verify persistence
            User fetched = userRepository.findById(userId).orElseThrow();
            assertThat(fetched.getAccountStatus()).isEqualTo(AccountStatus.TO_BE_DELETED);
        }
    }
}
