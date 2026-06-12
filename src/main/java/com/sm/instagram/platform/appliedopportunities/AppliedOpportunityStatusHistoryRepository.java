package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppliedOpportunityStatusHistoryRepository extends BaseRepository<AppliedOpportunityStatusHistory, Long> {
    
    /**
     * Find all status history entries for a specific applied opportunity, ordered by change time (newest first)
     */
    List<AppliedOpportunityStatusHistory> findByAppliedOpportunityIdOrderByChangedAtDesc(Long appliedOpportunityId);
    
    /**
     * Find status history entries for a specific applied opportunity with pagination
     */
    Page<AppliedOpportunityStatusHistory> findByAppliedOpportunityIdOrderByChangedAtDesc(Long appliedOpportunityId, Pageable pageable);
    
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
