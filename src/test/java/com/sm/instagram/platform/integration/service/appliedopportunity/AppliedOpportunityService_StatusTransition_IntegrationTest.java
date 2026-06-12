package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoOut;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppliedOpportunityService.updateOpportunityStatus() method.
 * Tests the complete state machine for opportunity status transitions.
 *
 * <p>State Machine:
 * <pre>
 * APPLIED → ACCEPTED_BY_COMPANY → ACCEPTED_BY_INFLUENCER → CONTENT_SEND_TO_ACCEPT
 *     ↓            ↓                      ↓
 * REJECTED_BY_   REJECTED_BY_         CONTENT_APPROVED → CONTENT_POSTED → TO_BE_PAID → DONE
 * COMPANY        INFLUENCER                 ↓                ↓
 *                                    CONTENT_REJECTED   CONTENT_POSTED_REJECTED
 *
 * Special transitions:
 * - CONTENT_REJECTED → CONTENT_SEND_TO_ACCEPT (resubmit) or REJECTED_BY_INFLUENCER (resign)
 * - CONTENT_POSTED_REJECTED → CONTENT_POSTED (repost)
 * </pre>
 */
@DisplayName("AppliedOpportunityService - Status Transitions")
class AppliedOpportunityService_StatusTransition_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    // =========================================================================
    // Company Transitions
    // =========================================================================

    @Nested
    @DisplayName("Company Transitions")
    class CompanyTransitions {

        @Test
        @DisplayName("Company can accept application (APPLIED → ACCEPTED_BY_COMPANY)")
        void companyCanAcceptApplication() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
        }

        @Test
        @DisplayName("Company can reject application (APPLIED → REJECTED_BY_COMPANY)")
        void companyCanRejectApplication() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, false);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.REJECTED_BY_COMPANY);
        }

        @Test
        @DisplayName("Company can approve content (CONTENT_SEND_TO_ACCEPT → CONTENT_APPROVED)")
        void companyCanApproveContent() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_SEND_TO_ACCEPT)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_APPROVED);
        }

        @Test
        @DisplayName("Company can reject content (CONTENT_SEND_TO_ACCEPT → CONTENT_REJECTED)")
        void companyCanRejectContent() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_SEND_TO_ACCEPT)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, false);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_REJECTED);
        }

        @Test
        @DisplayName("Company can verify posted content (CONTENT_POSTED → TO_BE_PAID)")
        void companyCanVerifyPostedContent() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_POSTED)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.TO_BE_PAID);
        }

        @Test
        @DisplayName("Company can reject posted content (CONTENT_POSTED → CONTENT_POSTED_REJECTED)")
        void companyCanRejectPostedContent() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_POSTED)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, false);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_POSTED_REJECTED);
        }

        @Test
        @DisplayName("Company can mark as done (TO_BE_PAID → DONE)")
        void companyCanMarkAsDone() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.TO_BE_PAID)
            );
            Long id = application.getId();

            authenticateAs(testCompany);
            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.DONE);
        }

        @Test
        @DisplayName("Company cannot transition from ACCEPTED_BY_COMPANY (influencer's turn)")
        void companyCannotTransitionFromAcceptedByCompany() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY)
            );
            Long id = application.getId();

            authenticateAs(testCompany);

            assertThatThrownBy(() -> appliedOpportunityService.updateOpportunityStatus(id, true))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Company cannot transition from ACCEPTED_BY_INFLUENCER (influencer's turn)")
        void companyCannotTransitionFromAcceptedByInfluencer() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_INFLUENCER)
            );
            Long id = application.getId();

            authenticateAs(testCompany);

            assertThatThrownBy(() -> appliedOpportunityService.updateOpportunityStatus(id, true))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =========================================================================
    // Influencer Transitions
    // =========================================================================

    @Nested
    @DisplayName("Influencer Transitions")
    class InfluencerTransitions {

        @Test
        @DisplayName("Influencer can accept company offer (ACCEPTED_BY_COMPANY → ACCEPTED_BY_INFLUENCER)")
        void influencerCanAcceptCompanyOffer() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("Influencer can reject company offer (ACCEPTED_BY_COMPANY → REJECTED_BY_INFLUENCER)")
        void influencerCanRejectCompanyOffer() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, false);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("Influencer can submit content (ACCEPTED_BY_INFLUENCER → CONTENT_SEND_TO_ACCEPT)")
        void influencerCanSubmitContent() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_INFLUENCER)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
        }

        @Test
        @DisplayName("Influencer can resubmit after rejection (CONTENT_REJECTED → CONTENT_SEND_TO_ACCEPT)")
        void influencerCanResubmitAfterRejection() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_REJECTED)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
        }

        @Test
        @DisplayName("Influencer can resign after content rejected (CONTENT_REJECTED → REJECTED_BY_INFLUENCER)")
        void influencerCanResignAfterContentRejected() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_REJECTED)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, false);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.REJECTED_BY_INFLUENCER);
        }

        @Test
        @DisplayName("Influencer can post approved content (CONTENT_APPROVED → CONTENT_POSTED)")
        void influencerCanPostApprovedContent() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_APPROVED)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("Influencer can repost rejected content (CONTENT_POSTED_REJECTED → CONTENT_POSTED)")
        void influencerCanRepostRejectedContent() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.CONTENT_POSTED_REJECTED)
            );
            Long id = application.getId();

            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.CONTENT_POSTED);
        }

        @Test
        @DisplayName("Influencer cannot transition from APPLIED (company's turn)")
        void influencerCannotTransitionFromApplied() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );
            Long id = application.getId();

            assertThatThrownBy(() -> appliedOpportunityService.updateOpportunityStatus(id, true))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // =========================================================================
    // Invalid Transitions and Permission Checks
    // =========================================================================

    @Nested
    @DisplayName("Invalid Transitions and Permissions")
    class InvalidTransitionsAndPermissions {

        @Test
        @DisplayName("Cannot transition from terminal status (DONE)")
        void cannotTransitionFromTerminalStatus() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.DONE)
            );
            Long id = application.getId();

            authenticateAs(testAdmin);

            assertThatThrownBy(() -> appliedOpportunityService.updateOpportunityStatus(id, true))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Non-owner influencer cannot update status")
        void nonOwnerInfluencerCannotUpdateStatus() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY)
            );
            Long id = application.getId();

            // secondInfluencer tries to update testInfluencer's application
            createTestSocialConnection(secondInfluencer, 5000);
            authenticateAs(secondInfluencer);

            assertThatThrownBy(() -> appliedOpportunityService.updateOpportunityStatus(id, true))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Non-owner company cannot update status")
        void nonOwnerCompanyCannotUpdateStatus() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );
            Long id = application.getId();

            // secondCompany tries to update application to testCompany's opportunity
            authenticateAs(secondCompany);

            assertThatThrownBy(() -> appliedOpportunityService.updateOpportunityStatus(id, true))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("Admin can update status at any state")
        void adminCanUpdateStatusAtAnyState() {
            authenticateAs(testInfluencer);
            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );
            Long id = application.getId();

            authenticateAs(testAdmin);
            AppliedOpportunity result = appliedOpportunityService.updateOpportunityStatus(id, true);

            assertThat(result.getOpportunityStatus()).isEqualTo(OpportunityStatus.ACCEPTED_BY_COMPANY);
        }
    }

    @Nested
    @DisplayName("DTO Methods (require HTTP context)")
    class DtoMethods {

        @Test
        @DisplayName("updateOpportunityStatusAsDto updates and returns DTO")
        void updateOpportunityStatusAsDtoReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY)
            );

            AppliedOpportunityDtoOut result = appliedOpportunityService.updateOpportunityStatusAsDto(
                    application.getId(), true
            );

            assertThat(result).isNotNull();
            assertThat(result.getOpportunityStatus().getValue()).isEqualTo("ACCEPTED_BY_INFLUENCER");
        }
    }
}
