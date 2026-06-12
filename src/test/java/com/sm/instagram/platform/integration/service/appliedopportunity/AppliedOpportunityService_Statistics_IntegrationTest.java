package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityStatisticsDto;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AppliedOpportunityService.getAppliedOpportunityStatistics() method.
 * Tests statistics calculation and filtering by user role.
 *
 * <p>Status categorization:
 * <ul>
 *   <li>NEW: APPLIED, ACCEPTED_BY_COMPANY</li>
 *   <li>IN_PROGRESS: ACCEPTED_BY_INFLUENCER, CONTENT_SEND_TO_ACCEPT, CONTENT_APPROVED,
 *       CONTENT_REJECTED, CONTENT_POSTED, CONTENT_POSTED_REJECTED, TO_BE_PAID</li>
 *   <li>DONE: DONE, REJECTED_BY_COMPANY, REJECTED_BY_INFLUENCER</li>
 * </ul>
 *
 * <p>Filtering by user role:
 * <ul>
 *   <li>Influencer: sees only their own applications</li>
 *   <li>Company: sees applications to their opportunities</li>
 *   <li>Admin: sees all applications</li>
 * </ul>
 */
@DisplayName("AppliedOpportunityService - Statistics")
class AppliedOpportunityService_Statistics_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    @Test
    @DisplayName("Influencer sees statistics for own applications only")
    void influencerSeesOwnStatistics() {
        // Create applications for testInfluencer
        authenticateAs(testInfluencer);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED));

        PartnershipOpportunity secondOpportunity = createAndSavePartnershipOpportunity(secondCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, secondOpportunity, OpportunityStatus.DONE));

        // Create application for secondInfluencer (should NOT be included)
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        saveAppliedOpportunity(createTestAppliedOpportunity(secondInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED));

        // Get statistics for testInfluencer
        authenticateAs(testInfluencer);
        AppliedOpportunityStatisticsDto stats = appliedOpportunityService.getAppliedOpportunityStatistics();

        // Should only see testInfluencer's 2 applications
        assertThat(stats.getTotal()).isEqualTo(2L);
        assertThat(stats.getNewOpportunities()).isEqualTo(1L); // APPLIED
        assertThat(stats.getDone()).isEqualTo(1L); // DONE
    }

    @Test
    @DisplayName("Company sees statistics for applications to their opportunities")
    void companySeesStatisticsForOwnOpportunities() {
        // Create applications to testCompany's opportunity
        authenticateAs(testInfluencer);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED));

        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        saveAppliedOpportunity(createTestAppliedOpportunity(secondInfluencer, testPartnershipOpportunity, OpportunityStatus.ACCEPTED_BY_COMPANY));

        // Create application to secondCompany's opportunity (should NOT be included for testCompany)
        PartnershipOpportunity secondOpportunity = createAndSavePartnershipOpportunity(secondCompany);
        authenticateAs(testInfluencer);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, secondOpportunity, OpportunityStatus.APPLIED));

        // Get statistics for testCompany
        authenticateAs(testCompany);
        AppliedOpportunityStatisticsDto stats = appliedOpportunityService.getAppliedOpportunityStatistics();

        // Should only see applications to testCompany's opportunities (2)
        assertThat(stats.getTotal()).isEqualTo(2L);
        assertThat(stats.getNewOpportunities()).isEqualTo(2L); // APPLIED + ACCEPTED_BY_COMPANY
    }

    @Test
    @DisplayName("Admin sees statistics for all applications")
    void adminSeesAllStatistics() {
        // Create applications from multiple influencers to multiple opportunities
        authenticateAs(testInfluencer);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED));

        PartnershipOpportunity secondOpportunity = createAndSavePartnershipOpportunity(secondCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, secondOpportunity, OpportunityStatus.DONE));

        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        saveAppliedOpportunity(createTestAppliedOpportunity(secondInfluencer, testPartnershipOpportunity, OpportunityStatus.TO_BE_PAID));

        // Get statistics as admin
        authenticateAs(testAdmin);
        AppliedOpportunityStatisticsDto stats = appliedOpportunityService.getAppliedOpportunityStatistics();

        // Should see all 3 applications
        assertThat(stats.getTotal()).isGreaterThanOrEqualTo(3L);
    }

    @Test
    @DisplayName("Statistics correctly categorize status values")
    void statisticsCategorizeStatusCorrectly() {
        // Create applications with different statuses to test categorization
        authenticateAs(testInfluencer);

        // NEW category: APPLIED, ACCEPTED_BY_COMPANY
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED));

        PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(secondCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po2, OpportunityStatus.ACCEPTED_BY_COMPANY));

        // IN_PROGRESS category: ACCEPTED_BY_INFLUENCER, CONTENT_SEND_TO_ACCEPT, etc.
        PartnershipOpportunity po3 = createAndSavePartnershipOpportunity(testCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po3, OpportunityStatus.ACCEPTED_BY_INFLUENCER));

        PartnershipOpportunity po4 = createAndSavePartnershipOpportunity(secondCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po4, OpportunityStatus.CONTENT_SEND_TO_ACCEPT));

        PartnershipOpportunity po5 = createAndSavePartnershipOpportunity(testCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po5, OpportunityStatus.TO_BE_PAID));

        // DONE category: DONE, REJECTED_BY_COMPANY, REJECTED_BY_INFLUENCER
        PartnershipOpportunity po6 = createAndSavePartnershipOpportunity(secondCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po6, OpportunityStatus.DONE));

        PartnershipOpportunity po7 = createAndSavePartnershipOpportunity(testCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po7, OpportunityStatus.REJECTED_BY_COMPANY));

        PartnershipOpportunity po8 = createAndSavePartnershipOpportunity(secondCompany);
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po8, OpportunityStatus.REJECTED_BY_INFLUENCER));

        // Get statistics as testInfluencer
        AppliedOpportunityStatisticsDto stats = appliedOpportunityService.getAppliedOpportunityStatistics();

        assertThat(stats.getNewOpportunities()).isEqualTo(2L);    // APPLIED + ACCEPTED_BY_COMPANY
        assertThat(stats.getInProgress()).isEqualTo(3L);           // ACCEPTED_BY_INFLUENCER + CONTENT_SEND_TO_ACCEPT + TO_BE_PAID
        assertThat(stats.getDone()).isEqualTo(3L);                 // DONE + REJECTED_BY_COMPANY + REJECTED_BY_INFLUENCER
        assertThat(stats.getTotal()).isEqualTo(8L);
    }

    @Test
    @DisplayName("Empty statistics when no applications exist")
    void emptyStatisticsWhenNoApplications() {
        // New influencer with no applications
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);

        AppliedOpportunityStatisticsDto stats = appliedOpportunityService.getAppliedOpportunityStatistics();

        assertThat(stats.getTotal()).isEqualTo(0L);
        assertThat(stats.getNewOpportunities()).isEqualTo(0L);
        assertThat(stats.getInProgress()).isEqualTo(0L);
        assertThat(stats.getDone()).isEqualTo(0L);
    }
}
