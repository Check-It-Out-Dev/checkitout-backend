package com.sm.instagram.platform.appliedopportunities;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for applied opportunity statistics grouped by status categories
 */
@Data
@Builder
public class AppliedOpportunityStatisticsDto {
    
    /**
     * Number of opportunities in progress: ACCEPTED_BY_INFLUENCER, CONTENT_SEND_TO_ACCEPT, 
     * CONTENT_APPROVED, CONTENT_REJECTED, CONTENT_POSTED, CONTENT_POSTED_REJECTED, TO_BE_PAID
     */
    private Long inProgress;
    
    /**
     * Number of new opportunities: APPLIED, ACCEPTED_BY_COMPANY
     */
    private Long newOpportunities;
    
    /**
     * Number of done opportunities: DONE, REJECTED_BY_COMPANY, REJECTED_BY_INFLUENCER
     */
    private Long done;
    
    /**
     * Total number of applied opportunities
     */
    private Long total;
}