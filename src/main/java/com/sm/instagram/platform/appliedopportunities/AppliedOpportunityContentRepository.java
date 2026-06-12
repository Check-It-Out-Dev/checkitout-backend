package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.contenttype.ContentType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppliedOpportunityContentRepository extends BaseRepository<AppliedOpportunityContent, Long> {

    List<AppliedOpportunityContent> findByAppliedOpportunityId(Long appliedOpportunityId);

    List<AppliedOpportunityContent> findByAppliedOpportunityIdAndContentType(Long appliedOpportunityId, ContentType contentType);

    List<AppliedOpportunityContent> findByApprovalStatus(ContentApprovalStatus approvalStatus);

    @Query("SELECT c FROM AppliedOpportunityContent c WHERE c.appliedOpportunity.id = :appliedOpportunityId AND c.approvalStatus = :status")
    List<AppliedOpportunityContent> findByAppliedOpportunityIdAndApprovalStatus(@Param("appliedOpportunityId") Long appliedOpportunityId, @Param("status") ContentApprovalStatus status);

    @Query("SELECT COUNT(c) FROM AppliedOpportunityContent c WHERE c.appliedOpportunity.id = :appliedOpportunityId")
    Long countByAppliedOpportunityId(@Param("appliedOpportunityId") Long appliedOpportunityId);
    
    /**
     * Fetches content with all relationships needed for permission checking.
     * This eagerly loads: applied opportunity -> partnership opportunity -> company
     * to avoid lazy initialization exceptions during permission validation.
     */
    @Query("SELECT c FROM AppliedOpportunityContent c " +
           "LEFT JOIN FETCH c.appliedOpportunity ao " +
           "LEFT JOIN FETCH ao.partnershipOpportunity po " +
           "LEFT JOIN FETCH po.company " +
           "WHERE c.id = :contentId")
    Optional<AppliedOpportunityContent> findByIdWithRelationships(@Param("contentId") Long contentId);

    /**
     * Delete all content submissions for a specific applied opportunity.
     * Used by cascade delete to remove content before deleting the parent AppliedOpportunity.
     */
    void deleteByAppliedOpportunityId(Long appliedOpportunityId);

    /**
     * Delete all content submissions for multiple applied opportunities.
     * Used by batch cascade delete operations.
     */
    void deleteByAppliedOpportunityIdIn(List<Long> appliedOpportunityIds);
}
