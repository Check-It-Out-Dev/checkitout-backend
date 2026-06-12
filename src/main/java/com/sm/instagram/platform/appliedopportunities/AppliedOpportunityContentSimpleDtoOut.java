package com.sm.instagram.platform.appliedopportunities;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Simplified content DTO for use in AppliedOpportunitySimpleDtoOut
 * Contains only essential fields to avoid data overload
 */
@Getter
@Setter
public class AppliedOpportunityContentSimpleDtoOut {
    private Long id;
    private String contentTypeName;
    private Integer contentCount;
    private List<String> urls;
    private ContentApprovalStatus approvalStatus;
    private String socialMediaLink;
    private LocalDateTime submissionDate;
    
    // Basic engagement metrics
    private Long likesCount;
    private Long viewsCount;
}
