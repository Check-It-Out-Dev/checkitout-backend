package com.sm.instagram.platform.partnershipopportunities;

import com.sm.instagram.platform.common.repository.MultiBagFetchRepository;

/**
 * Custom repository methods for PartnershipOpportunity to handle complex fetching scenarios
 * and avoid MultipleBagFetchException by using multiple queries.
 */
public interface PartnershipOpportunityRepositoryCustom extends MultiBagFetchRepository<PartnershipOpportunity, Long> {
    // Additional custom methods specific to PartnershipOpportunity can be added here
}
