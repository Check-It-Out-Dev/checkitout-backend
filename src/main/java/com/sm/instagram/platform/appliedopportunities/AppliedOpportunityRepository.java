package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface AppliedOpportunityRepository extends BaseRepository<AppliedOpportunity, Long>, AppliedOpportunityRepositoryCustom {
    List<AppliedOpportunity> findAllByInfluencer_FirebaseUserId(String firebaseUserId);

    List<AppliedOpportunity> findByPartnershipOpportunity(PartnershipOpportunity partnershipOpportunity);

    List<AppliedOpportunity> findByPartnershipOpportunityAndOpportunityStatus(PartnershipOpportunity partnershipOpportunity, OpportunityStatus opportunityStatus);

    boolean existsByPartnershipOpportunity_IdAndOpportunityStatusIn(Long opportunityId, Set<OpportunityStatus> statuses);

    boolean existsByIdAndOpportunityStatusIn(Long id, Set<OpportunityStatus> statuses);
    
    List<AppliedOpportunity> findAllByPartnershipOpportunity_Company_FirebaseUserId(String firebaseUserId);

    // Note: Complex fetching methods have been moved to AppliedOpportunityRepositoryCustom
    // to handle MultipleBagFetchException using multiple queries

    /**
     * Find all applied opportunities for a specific influencer.
     * Used by cascade delete to identify all applications by an influencer.
     */
    List<AppliedOpportunity> findByInfluencerId(Long influencerId);

    /**
     * Delete all applied opportunities for a specific influencer.
     * Used by cascade delete when deleting an influencer account.
     */
    void deleteByInfluencerId(Long influencerId);

    /**
     * Find all applied opportunities for a specific partnership opportunity.
     * Used by cascade delete to identify all applications to a company's opportunity.
     */
    List<AppliedOpportunity> findByPartnershipOpportunityId(Long partnershipOpportunityId);

    /**
     * Delete all applied opportunities for a specific partnership opportunity.
     * Used by cascade delete when deleting a partnership opportunity.
     */
    void deleteByPartnershipOpportunityId(Long partnershipOpportunityId);

    /**
     * Count applied opportunities for a specific influencer.
     * Used by cascade delete preview.
     */
    int countByInfluencerId(Long influencerId);

    /**
     * Count applied opportunities for a specific partnership opportunity.
     * Used by cascade delete preview.
     */
    int countByPartnershipOpportunityId(Long partnershipOpportunityId);

    boolean existsByInfluencerIdAndPartnershipOpportunityId(Long influencerId, Long partnershipOpportunityId);
}
