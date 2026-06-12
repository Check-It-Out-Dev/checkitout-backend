package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityDtoOut;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AppliedOpportunityService query methods.
 * Tests: getDataPagedAndFiltered(), getDataPagedAndFilteredAsDtos(), findByInfluencerUserId()
 *
 * <p>Query behavior by user type:
 * <ul>
 *   <li>Admin: sees all applications (no filtering)</li>
 *   <li>Company: sees only applications to their own opportunities</li>
 *   <li>Influencer: sees only their own applications</li>
 *   <li>Unknown: sees empty results (security fallback)</li>
 * </ul>
 */
@DisplayName("AppliedOpportunityService - Query")
class AppliedOpportunityService_Query_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase {

    // =========================================================================
    // Admin Query Tests
    // =========================================================================

    @Test
    @DisplayName("Admin sees all applications without filtering")
    void adminSeesAllApplications() {
        // Create application from testInfluencer to testCompany's opportunity
        authenticateAs(testInfluencer);
        AppliedOpportunity app1 = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );

        // Create another opportunity from secondCompany
        PartnershipOpportunity secondOpportunity = createAndSavePartnershipOpportunity(secondCompany);

        // Create application from secondInfluencer to secondCompany's opportunity
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        AppliedOpportunity app2 = saveAppliedOpportunity(
                createTestAppliedOpportunity(secondInfluencer, secondOpportunity, OpportunityStatus.APPLIED)
        );

        // Admin should see both applications
        authenticateAs(testAdmin);
        Pageable pageable = PageRequest.of(0, 50);
        Map<String, String> filters = new HashMap<>();

        Page<AppliedOpportunity> result = appliedOpportunityService.getDataPagedAndFiltered(pageable, filters);

        assertThat(result.getContent())
                .extracting(AppliedOpportunity::getId)
                .contains(app1.getId(), app2.getId());
    }

    // =========================================================================
    // Company Query Tests
    // =========================================================================

    @Test
    @DisplayName("Company sees only applications to their own opportunities")
    void companySeesOnlyApplicationsToOwnOpportunities() {
        // Create application to testCompany's opportunity
        authenticateAs(testInfluencer);
        AppliedOpportunity appToTestCompany = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );

        // Create another opportunity from secondCompany
        PartnershipOpportunity secondOpportunity = createAndSavePartnershipOpportunity(secondCompany);

        // Create application to secondCompany's opportunity
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        AppliedOpportunity appToSecondCompany = saveAppliedOpportunity(
                createTestAppliedOpportunity(secondInfluencer, secondOpportunity, OpportunityStatus.APPLIED)
        );

        // testCompany should only see applications to their opportunity
        authenticateAs(testCompany);
        Pageable pageable = PageRequest.of(0, 50);
        Map<String, String> filters = new HashMap<>();

        Page<AppliedOpportunity> result = appliedOpportunityService.getDataPagedAndFiltered(pageable, filters);

        assertThat(result.getContent())
                .extracting(AppliedOpportunity::getId)
                .contains(appToTestCompany.getId())
                .doesNotContain(appToSecondCompany.getId());
    }

    // =========================================================================
    // Influencer Query Tests
    // =========================================================================

    @Test
    @DisplayName("Influencer sees only their own applications")
    void influencerSeesOnlyOwnApplications() {
        // Create application from testInfluencer
        authenticateAs(testInfluencer);
        AppliedOpportunity myApp = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );

        // Create application from secondInfluencer
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        AppliedOpportunity otherApp = saveAppliedOpportunity(
                createTestAppliedOpportunity(secondInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );

        // testInfluencer should only see their own application
        authenticateAs(testInfluencer);
        Pageable pageable = PageRequest.of(0, 50);
        Map<String, String> filters = new HashMap<>();

        Page<AppliedOpportunity> result = appliedOpportunityService.getDataPagedAndFiltered(pageable, filters);

        assertThat(result.getContent())
                .extracting(AppliedOpportunity::getId)
                .contains(myApp.getId())
                .doesNotContain(otherApp.getId());
    }

    @Test
    @DisplayName("findByInfluencerUserId returns applications for specific influencer")
    void findByInfluencerUserIdReturnsCorrectApplications() {
        // Create multiple applications for testInfluencer
        authenticateAs(testInfluencer);
        AppliedOpportunity app1 = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );

        PartnershipOpportunity secondOpportunity = createAndSavePartnershipOpportunity(secondCompany);
        AppliedOpportunity app2 = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, secondOpportunity, OpportunityStatus.DONE)
        );

        // Create application for secondInfluencer (should not be returned)
        createTestSocialConnection(secondInfluencer, 5000);
        authenticateAs(secondInfluencer);
        AppliedOpportunity otherApp = saveAppliedOpportunity(
                createTestAppliedOpportunity(secondInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );

        // Query by testInfluencer's firebaseUserId
        List<AppliedOpportunity> result = appliedOpportunityService.findByInfluencerUserId(testInfluencer.getFirebaseUserId());

        assertThat(result)
                .extracting(AppliedOpportunity::getId)
                .contains(app1.getId(), app2.getId())
                .doesNotContain(otherApp.getId());
    }

    // =========================================================================
    // Pagination Tests
    // =========================================================================

    @Test
    @DisplayName("Pagination works correctly")
    void paginationWorksCorrectly() {
        // Create 5 opportunities from different companies for variety
        authenticateAs(testInfluencer);

        // Create multiple partnership opportunities to apply to
        PartnershipOpportunity po1 = createAndSavePartnershipOpportunity(testCompany);
        PartnershipOpportunity po2 = createAndSavePartnershipOpportunity(testCompany);
        PartnershipOpportunity po3 = createAndSavePartnershipOpportunity(secondCompany);
        PartnershipOpportunity po4 = createAndSavePartnershipOpportunity(secondCompany);

        // Create applications to different opportunities
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED));
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po1, OpportunityStatus.APPLIED));
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po2, OpportunityStatus.DONE));
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po3, OpportunityStatus.APPLIED));
        saveAppliedOpportunity(createTestAppliedOpportunity(testInfluencer, po4, OpportunityStatus.DONE));

        authenticateAs(testAdmin);
        Map<String, String> filters = new HashMap<>();

        // Page 1 with page size 2
        Page<AppliedOpportunity> page1 = appliedOpportunityService.getDataPagedAndFiltered(
                PageRequest.of(0, 2), filters
        );

        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isGreaterThanOrEqualTo(5);
        assertThat(page1.getTotalPages()).isGreaterThanOrEqualTo(3);

        // Page 2 with page size 2
        Page<AppliedOpportunity> page2 = appliedOpportunityService.getDataPagedAndFiltered(
                PageRequest.of(1, 2), filters
        );

        assertThat(page2.getContent()).hasSize(2);

        // Verify pages have different content
        assertThat(page1.getContent())
                .extracting(AppliedOpportunity::getId)
                .doesNotContainAnyElementsOf(
                        page2.getContent().stream()
                                .map(AppliedOpportunity::getId)
                                .toList()
                );
    }

    // =========================================================================
    // Filter Tests
    // =========================================================================

    @Test
    @DisplayName("Filtering by status works")
    void filteringByStatusWorks() {
        // Create applications with different statuses
        authenticateAs(testInfluencer);
        AppliedOpportunity appliedApp = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
        );

        PartnershipOpportunity secondOpportunity = createAndSavePartnershipOpportunity(secondCompany);
        AppliedOpportunity doneApp = saveAppliedOpportunity(
                createTestAppliedOpportunity(testInfluencer, secondOpportunity, OpportunityStatus.DONE)
        );

        authenticateAs(testAdmin);
        Pageable pageable = PageRequest.of(0, 50);

        // Filter by APPLIED status
        Map<String, String> appliedFilter = new HashMap<>();
        appliedFilter.put("opportunityStatus", "APPLIED");

        Page<AppliedOpportunity> appliedResults = appliedOpportunityService.getDataPagedAndFiltered(pageable, appliedFilter);

        assertThat(appliedResults.getContent())
                .extracting(AppliedOpportunity::getId)
                .contains(appliedApp.getId())
                .doesNotContain(doneApp.getId());

        // Filter by DONE status
        Map<String, String> doneFilter = new HashMap<>();
        doneFilter.put("opportunityStatus", "DONE");

        Page<AppliedOpportunity> doneResults = appliedOpportunityService.getDataPagedAndFiltered(pageable, doneFilter);

        assertThat(doneResults.getContent())
                .extracting(AppliedOpportunity::getId)
                .contains(doneApp.getId())
                .doesNotContain(appliedApp.getId());
    }

    // =========================================================================
    // DTO Methods (require HTTP context)
    // =========================================================================

    @Nested
    @DisplayName("DTO Methods (require HTTP context)")
    class DtoMethods {

        @Test
        @DisplayName("getDataPagedAndFilteredAsDtos returns DTOs")
        void getDataPagedAndFilteredAsDtosReturnsDtos() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            AppliedOpportunity application = saveAppliedOpportunity(
                    createTestAppliedOpportunity(testInfluencer, testPartnershipOpportunity, OpportunityStatus.APPLIED)
            );

            authenticateAs(testAdmin);
            setUpMockHttpContext();

            Pageable pageable = PageRequest.of(0, 50);
            Map<String, String> filters = new HashMap<>();

            Page<AppliedOpportunityDtoOut> result = appliedOpportunityService.getDataPagedAndFilteredAsDtos(pageable, filters);

            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent())
                    .extracting(AppliedOpportunityDtoOut::getId)
                    .contains(application.getId());
        }
    }
}
