package com.sm.instagram.platform.integration.service.useraccountorchestrator;

import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.integration.service.appliedopportunity.AppliedOpportunityServiceIntegrationTestBase;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserAccountOrchestrator;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * Base class for UserAccountOrchestrator integration tests.
 * Extends AppliedOpportunityServiceIntegrationTestBase to reuse PO, AO, and social connection fixtures.
 *
 * <p>Provides:
 * <ul>
 *   <li>UserAccountOrchestrator service injection</li>
 *   <li>Helper methods for creating test users with social connections</li>
 *   <li>Helper methods for creating applied opportunities at specific statuses</li>
 *   <li>Helper methods for creating additional admin users</li>
 * </ul>
 */
public abstract class UserAccountOrchestratorIntegrationTestBase extends AppliedOpportunityServiceIntegrationTestBase {

    @Autowired
    protected UserAccountOrchestrator userAccountOrchestrator;

    /**
     * Creates an influencer with an active social connection.
     *
     * @param suffix       Unique suffix for user identifiers
     * @param followerCount Number of followers for the social connection
     * @return The created and persisted influencer with social connection
     */
    protected User createInfluencerWithSocialConnection(String suffix, int followerCount) {
        // Clean suffix for email (only lowercase letters and numbers)
        String cleanSuffix = suffix.toLowerCase().replaceAll("[^a-z0-9]", "");
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        User influencer = createTestUser(
                "INFLUENCER-" + suffix + "-" + UUID.randomUUID(),
                UserType.INFLUENCER,
                "influencer" + cleanSuffix + uniqueId + "@integration-test.com"
        );
        createTestSocialConnection(influencer, followerCount);
        return influencer;
    }

    /**
     * Creates a second admin user (in addition to testAdmin from BaseServiceIntegrationTest).
     * Useful for testing last-admin protection scenarios.
     *
     * @return The created and persisted admin user
     */
    protected User createSecondAdmin() {
        return createTestUser(
                "ADMIN2-" + UUID.randomUUID(),
                UserType.ADMIN,
                "admin2." + UUID.randomUUID() + "@integration-test.com"
        );
    }

    /**
     * Creates a pending admin user.
     * Useful for testing admin count scenarios where pending admins are counted.
     *
     * @return The created and persisted pending admin user
     */
    protected User createPendingAdmin() {
        return createTestUser(
                "PENDING-ADMIN-" + UUID.randomUUID(),
                UserType.PENDING_ADMIN,
                "pending.admin." + UUID.randomUUID() + "@integration-test.com"
        );
    }

    /**
     * Creates an admin user marked as TO_BE_DELETED.
     * Useful for testing that deleted admins don't count toward admin totals.
     *
     * @return The created admin user with TO_BE_DELETED status
     */
    protected User createDeletedAdmin() {
        return createTestUser(
                "DELETED-ADMIN-" + UUID.randomUUID(),
                UserType.ADMIN,
                "deleted.admin." + UUID.randomUUID() + "@integration-test.com",
                "Deleted",
                "Admin",
                AccountStatus.TO_BE_DELETED
        );
    }

    /**
     * Creates an AppliedOpportunity for an influencer at a specific status.
     *
     * @param influencer The influencer applying
     * @param po         The partnership opportunity
     * @param status     The opportunity status
     * @return The saved AppliedOpportunity
     */
    protected AppliedOpportunity createAppliedOpportunityAtStatus(
            User influencer,
            PartnershipOpportunity po,
            OpportunityStatus status) {
        AppliedOpportunity ao = createTestAppliedOpportunity(influencer, po, status);
        return saveAppliedOpportunity(ao);
    }

    /**
     * Creates a company user with an active partnership opportunity.
     *
     * @param suffix Unique suffix for user identifiers
     * @return The created company user
     */
    protected User createCompanyWithActiveOpportunity(String suffix) {
        User company = createTestUser(
                "COMPANY-" + suffix + "-" + UUID.randomUUID(),
                UserType.COMPANY,
                "company." + suffix + "." + UUID.randomUUID() + "@integration-test.com"
        );
        // Note: We can't create a PO directly for this company since createAndSavePartnershipOpportunity
        // uses testAddress which is tied to testCompany. The calling test should handle this.
        return company;
    }

    /**
     * Creates a partnership opportunity for a specific company.
     * Sets up required address first.
     *
     * @param company The company owning the opportunity
     * @return The saved PartnershipOpportunity
     */
    protected PartnershipOpportunity createPartnershipOpportunityForCompany(User company) {
        // Create address for this company
        com.sm.instagram.platform.address.Address companyAddress = new com.sm.instagram.platform.address.Address();
        companyAddress.setStreet("Test Street " + UUID.randomUUID().toString().substring(0, 8));
        companyAddress.setCity(testCity.getName());
        companyAddress.setPostalCode("00-002");
        companyAddress.setCountry("Poland");
        companyAddress.setAddressType("MAIN");
        companyAddress.setSourceType(com.sm.instagram.platform.address.AddressSourceType.CUSTOM);
        companyAddress.setUser(company);
        companyAddress = addressRepository.save(companyAddress);

        // Create partnership opportunity
        PartnershipOpportunity po = new PartnershipOpportunity();
        po.setName("Test Partnership " + UUID.randomUUID().toString().substring(0, 8));
        po.setTitle("Integration Test Partnership for " + company.getEmail());
        po.setDetails("Test partnership opportunity");
        po.setCompany(company);
        po.setActive(true);
        po.setCity(testCity);
        po.setAddress(companyAddress);
        po.setFollowersMin(1000);
        po.setFollowersMax(100000);
        po.setCompensationType(com.sm.instagram.platform.partnershipopportunities.CompensationType.CASH);
        po.setCompensationAmountMin(100);
        po.setCompensationAmountMax(500);
        po.setCurrency(testCurrency);
        po.setServiceType(testServiceType);
        return partnershipOpportunityRepository.save(po);
    }

    /**
     * Creates an inactive partnership opportunity for a company.
     *
     * @param company The company owning the opportunity
     * @return The saved inactive PartnershipOpportunity
     */
    protected PartnershipOpportunity createInactivePartnershipOpportunityForCompany(User company) {
        PartnershipOpportunity po = createPartnershipOpportunityForCompany(company);
        po.setActive(false);
        return partnershipOpportunityRepository.save(po);
    }
}
