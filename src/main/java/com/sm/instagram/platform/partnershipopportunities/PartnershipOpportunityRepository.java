package com.sm.instagram.platform.partnershipopportunities;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.user.User;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnershipOpportunityRepository extends BaseRepository<PartnershipOpportunity,Long>, PartnershipOpportunityRepositoryCustom {
    List<PartnershipOpportunity> findByCompany(User company);
    List<PartnershipOpportunity> findByActive(boolean active);

    /**
     * Find all partnership opportunities by company ID.
     * Used by cascade delete to identify all opportunities for a company.
     */
    List<PartnershipOpportunity> findByCompanyId(Long companyId);

    /**
     * Delete all partnership opportunities by company ID.
     * Used by cascade delete when deleting a company account.
     */
    void deleteByCompanyId(Long companyId);

    /**
     * Count partnership opportunities for a specific company.
     * Used by cascade delete preview.
     */
    int countByCompanyId(Long companyId);
}
