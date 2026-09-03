package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.base.BaseRepository;
import com.sm.instagram.platform.contenttype.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppliedOpportunityContentRepository extends BaseRepository<AppliedOpportunityContent, Long> {

    /**
     * Per-application content listing (the FE review + submissions pages).
     * Fetch-joins contentType because the DTO mapping reads its name OUTSIDE
     * any transaction, so the plain derived query returned a lazy proxy and
     * GET /applied-opportunity/content/applied-opportunity/{id} 500ed with
     * LazyInitializationException for the reviewing COMPANY — caught by the
     * live Chrome click-through 2026-06-12. Same fix class as
     * findByApprovalStatus below (2026-06-10). appliedOpportunity stays lazy
     * on purpose: the mapper only reads its id, which never initializes a
     * proxy; urls is a jsonb column, not a JPA collection.
     */
    @Query("""
        SELECT c FROM AppliedOpportunityContent c
        LEFT JOIN FETCH c.contentType
        WHERE c.appliedOpportunity.id = :appliedOpportunityId
        """)
    List<AppliedOpportunityContent> findByAppliedOpportunityId(@Param("appliedOpportunityId") Long appliedOpportunityId);

    List<AppliedOpportunityContent> findByAppliedOpportunityIdAndContentType(Long appliedOpportunityId, ContentType contentType);

    /**
     * Admin/status listing. Fetch-joins the FULL object graph the service's
     * permission filter and the DTO mapping navigate (contentType name,
     * appliedOpportunity -> partnershipOpportunity -> company, -> influencer):
     * the controller maps OUTSIDE any transaction, so a plain derived query
     * returned lazy proxies and GET /applied-opportunity/content/status/{s}
     * 500ed with LazyInitializationException — caught live by the FE BDD
     * oracle 2026-06-10. LEFT JOIN FETCH keeps rows even if a relation is
     * ever null; DISTINCT collapses the fetch-join row expansion.
     */
    @Query("""
        SELECT DISTINCT c FROM AppliedOpportunityContent c
        LEFT JOIN FETCH c.contentType
        LEFT JOIN FETCH c.appliedOpportunity ao
        LEFT JOIN FETCH ao.partnershipOpportunity po
        LEFT JOIN FETCH po.company
        LEFT JOIN FETCH ao.influencer
        WHERE c.approvalStatus = :approvalStatus
        """)
    List<AppliedOpportunityContent> findByApprovalStatus(@Param("approvalStatus") ContentApprovalStatus approvalStatus);

    /** Same DTO mapping path as findByAppliedOpportunityId — same fetch-join (see above). */
    @Query("SELECT c FROM AppliedOpportunityContent c LEFT JOIN FETCH c.contentType WHERE c.appliedOpportunity.id = :appliedOpportunityId AND c.approvalStatus = :status")
    List<AppliedOpportunityContent> findByAppliedOpportunityIdAndApprovalStatus(@Param("appliedOpportunityId") Long appliedOpportunityId, @Param("status") ContentApprovalStatus status);

    @Query("SELECT COUNT(c) FROM AppliedOpportunityContent c WHERE c.appliedOpportunity.id = :appliedOpportunityId")
    Long countByAppliedOpportunityId(@Param("appliedOpportunityId") Long appliedOpportunityId);
    
    /**
     * Fetches content with the relationships needed for permission checking AND
     * DTO mapping. Eagerly loads appliedOpportunity -> partnershipOpportunity ->
     * company (permission validation) PLUS contentType — callers that map to a DTO
     * in the CONTROLLER (outside any tx) read contentType.getName(), so without this
     * join GET /applied-opportunity/content/{contentId} 500ed with
     * LazyInitializationException. Same fix class as findByAppliedOpportunityId /
     * findByApprovalStatus. All fetch-joins are to-one, so no row expansion.
     */
    @Query("SELECT c FROM AppliedOpportunityContent c " +
           "LEFT JOIN FETCH c.contentType " +
           "LEFT JOIN FETCH c.appliedOpportunity ao " +
           "LEFT JOIN FETCH ao.partnershipOpportunity po " +
           "LEFT JOIN FETCH po.company " +
           "WHERE c.id = :contentId")
    Optional<AppliedOpportunityContent> findByIdWithRelationships(@Param("contentId") Long contentId);

    /**
     * Paged content Specification query used by BaseService.getDataPagedAndFiltered.
     * GET /applied-opportunity/content/applied-opportunity/{id}/paged maps each row
     * to a DTO in the CONTROLLER, outside any tx, and reads contentType.getName() —
     * so contentType must be fetched eagerly here or the endpoint 500s with
     * LazyInitializationException. @EntityGraph is pagination-safe because contentType
     * is @ManyToOne (to-one): no in-memory pagination, unlike a fetched collection.
     */
    @Override
    @EntityGraph(attributePaths = "contentType")
    Page<AppliedOpportunityContent> findAll(Specification<AppliedOpportunityContent> spec, Pageable pageable);

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
