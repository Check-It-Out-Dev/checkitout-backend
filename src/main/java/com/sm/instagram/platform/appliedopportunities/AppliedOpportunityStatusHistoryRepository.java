package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppliedOpportunityStatusHistoryRepository extends BaseRepository<AppliedOpportunityStatusHistory, Long> {
    
    /**
     * Find all status history entries for a specific applied opportunity, ordered by change time (newest first).
     * LEFT JOIN FETCH changedByUser: the controller maps each row via
     * {@code AppliedOpportunityStatusHistoryDtoOut.fromEntity} OUTSIDE the service
     * transaction, so the lazy changedByUser must be initialized in the query
     * (else LazyInitializationException — caught by the FE parity sweep).
     */
    @Query("SELECT h FROM AppliedOpportunityStatusHistory h "
            + "LEFT JOIN FETCH h.changedByUser "
            + "WHERE h.appliedOpportunity.id = :appliedOpportunityId "
            + "ORDER BY h.changedAt DESC")
    List<AppliedOpportunityStatusHistory> findByAppliedOpportunityIdOrderByChangedAtDesc(@Param("appliedOpportunityId") Long appliedOpportunityId);

    /**
     * Find status history entries for a specific applied opportunity with pagination.
     * changedByUser is a to-one association, so JOIN FETCH is pagination-safe
     * (no in-memory paging). Same fromEntity-outside-transaction reason as above.
     */
    @Query(value = "SELECT h FROM AppliedOpportunityStatusHistory h "
            + "LEFT JOIN FETCH h.changedByUser "
            + "WHERE h.appliedOpportunity.id = :appliedOpportunityId "
            + "ORDER BY h.changedAt DESC",
            countQuery = "SELECT COUNT(h) FROM AppliedOpportunityStatusHistory h "
                    + "WHERE h.appliedOpportunity.id = :appliedOpportunityId")
    Page<AppliedOpportunityStatusHistory> findByAppliedOpportunityIdOrderByChangedAtDesc(@Param("appliedOpportunityId") Long appliedOpportunityId, Pageable pageable);
    
    /**
     * Find all status changes made by a specific user (using Firebase ID)
     */
    List<AppliedOpportunityStatusHistory> findByChangedByFirebaseIdOrderByChangedAtDesc(String firebaseUserId);
    
    /**
     * Find all status changes made by a specific user (using database user ID)
     */
    List<AppliedOpportunityStatusHistory> findByChangedByUserIdOrderByChangedAtDesc(Long userId);
    
    /**
     * Find status changes within a date range
     */
    List<AppliedOpportunityStatusHistory> findByChangedAtBetweenOrderByChangedAtDesc(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find all status changes to a specific status
     */
    List<AppliedOpportunityStatusHistory> findByNewStatusOrderByChangedAtDesc(OpportunityStatus status);
    
    /**
     * Find all status changes from a specific status
     */
    List<AppliedOpportunityStatusHistory> findByPreviousStatusOrderByChangedAtDesc(OpportunityStatus status);
    
    /**
     * Count total status changes for a specific applied opportunity
     */
    long countByAppliedOpportunityId(Long appliedOpportunityId);
    
    /**
     * Find the most recent status change for a specific applied opportunity
     */
    AppliedOpportunityStatusHistory findFirstByAppliedOpportunityIdOrderByChangedAtDesc(Long appliedOpportunityId);

    /**
     * Delete all status history entries for a specific applied opportunity.
     * Used by cascade delete to remove history before deleting the parent AppliedOpportunity.
     */
    void deleteByAppliedOpportunityId(Long appliedOpportunityId);

    /**
     * Delete all status history entries for multiple applied opportunities.
     * Used by batch cascade delete operations.
     */
    void deleteByAppliedOpportunityIdIn(List<Long> appliedOpportunityIds);

    /**
     * Count status history entries for a specific applied opportunity.
     * Used by cascade delete preview.
     */
    int countByAppliedOpportunity_Id(Long appliedOpportunityId);
}
