package com.sm.instagram.platform.integration.service.activecooperation;

import com.sm.instagram.platform.activecooperations.ActiveCooperationService;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import com.sm.instagram.platform.integration.service.appliedopportunity.AppliedOpportunityServiceIntegrationTestBase;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.User;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base class for ActiveCooperationService integration tests.
 * Extends AppliedOpportunityServiceIntegrationTestBase to reuse fixtures for
 * AppliedOpportunity, PartnershipOpportunity, and UserSocialConnection.
 *
 * <p>Provides:
 * <ul>
 *   <li>ActiveCooperationService for testing</li>
 *   <li>Helper methods for creating cooperations at various states</li>
 *   <li>All fixtures from AppliedOpportunityServiceIntegrationTestBase</li>
 * </ul>
 */
public abstract class ActiveCooperationServiceIntegrationTestBase extends AppliedOpportunityServiceIntegrationTestBase {

    @Autowired
    protected ActiveCooperationService activeCooperationService;

    // =========================================================================
    // Helper Methods - Cooperation States
    // =========================================================================

    /**
     * Creates an AppliedOpportunity at DONE status, ready for rating.
     * This is the typical state for cooperations that need rating.
     *
     * @param influencer  The influencer
     * @param opportunity The partnership opportunity
     * @return The saved AppliedOpportunity at DONE status
     */
    protected AppliedOpportunity createDoneCooperation(User influencer, PartnershipOpportunity opportunity) {
        return createAppliedOpportunityAtStatus(influencer, opportunity, OpportunityStatus.DONE);
    }

    /**
     * Creates an AppliedOpportunity at APPLIED status, ready for acceptance.
     *
     * @param influencer  The influencer
     * @param opportunity The partnership opportunity
     * @return The saved AppliedOpportunity at APPLIED status
     */
    protected AppliedOpportunity createAppliedCooperation(User influencer, PartnershipOpportunity opportunity) {
        return createAppliedOpportunityAtStatus(influencer, opportunity, OpportunityStatus.APPLIED);
    }

    /**
     * Creates an AppliedOpportunity at a specific in-progress status.
     *
     * @param influencer  The influencer
     * @param opportunity The partnership opportunity
     * @param status      The desired in-progress status
     * @return The saved AppliedOpportunity
     */
    protected AppliedOpportunity createInProgressCooperation(
            User influencer,
            PartnershipOpportunity opportunity,
            OpportunityStatus status) {
        return createAppliedOpportunityAtStatus(influencer, opportunity, status);
    }

    /**
     * Creates an AppliedOpportunity with specific ratings already set.
     *
     * @param influencer        The influencer
     * @param opportunity       The partnership opportunity
     * @param influencerRating  Rating given by influencer (can be null for DEFAULT)
     * @param companyRating     Rating given by company (can be null for DEFAULT)
     * @return The saved AppliedOpportunity with ratings
     */
    protected AppliedOpportunity createRatedCooperation(
            User influencer,
            PartnershipOpportunity opportunity,
            RateStatus influencerRating,
            RateStatus companyRating) {
        AppliedOpportunity ao = createTestAppliedOpportunity(influencer, opportunity, OpportunityStatus.DONE);
        if (influencerRating != null) {
            ao.setRateStatus(influencerRating);
        }
        if (companyRating != null) {
            ao.setCompanyRateStatus(companyRating);
        }
        return saveAppliedOpportunity(ao);
    }

    /**
     * Creates multiple cooperations for pagination/filtering tests.
     *
     * @param influencer  The influencer
     * @param company     The company (owner of opportunities)
     * @param count       Number of cooperations to create
     * @param status      Status for all cooperations
     * @return Array of saved AppliedOpportunities
     */
    protected AppliedOpportunity[] createMultipleCooperations(
            User influencer,
            User company,
            int count,
            OpportunityStatus status) {
        AppliedOpportunity[] result = new AppliedOpportunity[count];
        for (int i = 0; i < count; i++) {
            PartnershipOpportunity po = createAndSavePartnershipOpportunity(company);
            result[i] = createAppliedOpportunityAtStatus(influencer, po, status);
        }
        return result;
    }
}
