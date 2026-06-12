package com.sm.instagram.platform.appliedopportunities;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class AppliedOpportunitySimpleDtoOut {
    private Long id;
    private OpportunityStatusDtoOut opportunityStatus;
    private RateStatusDtoOut rateStatus;
    private RateStatusDtoOut companyRateStatus;
    private LocalDateTime executionDate;
    private LocalDateTime createdTime;
    private String note;
    private LocalDateTime lastUpdateTime;
    private String updater;
    private com.sm.instagram.platform.user.InfluencerPublicProfileDto influencer;
    // No partnershipOpportunity field to avoid circular reference
    
    // Content submissions - simplified version, can be null
    private List<AppliedOpportunityContentSimpleDtoOut> contentSubmissions;
}
