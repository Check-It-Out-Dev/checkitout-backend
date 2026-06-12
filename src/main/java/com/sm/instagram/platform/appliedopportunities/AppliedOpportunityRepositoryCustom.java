package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.repository.MultiBagFetchRepository;

/**
 * Custom repository methods for AppliedOpportunity to handle complex fetching scenarios
 * and avoid MultipleBagFetchException by using multiple queries.
 */
public interface AppliedOpportunityRepositoryCustom extends MultiBagFetchRepository<AppliedOpportunity, Long> {
    // Methods inherited from MultiBagFetchRepository:
    // - Page<AppliedOpportunity> findAllWithAssociationsFetched(Specification<AppliedOpportunity> spec, Pageable pageable)
    // - Optional<AppliedOpportunity> findByIdWithAssociationsFetched(Long id)
}
