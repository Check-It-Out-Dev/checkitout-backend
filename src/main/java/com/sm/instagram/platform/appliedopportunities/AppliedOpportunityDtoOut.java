package com.sm.instagram.platform.appliedopportunities;

import com.fasterxml.jackson.annotation.JsonView;
import com.sm.instagram.platform.activecooperations.Views;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunitySimpleDtoOut;
import com.sm.instagram.platform.user.PublicProfileDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class AppliedOpportunityDtoOut {
    private Long id;
    private OpportunityStatusDtoOut opportunityStatus;

    // Influencer's rating of the company - visible only to influencers and admins
    @JsonView({Views.InProgress_InfluencerView.class, Views.InProgress_AdminView.class})
    private RateStatusDtoOut rateStatus; // Changed from RateStatus to RateStatusDtoOut for translation support

    // Company's rating of the influencer - visible only to companies and admins
    @JsonView({Views.InProgress_CompanyView.class, Views.InProgress_AdminView.class})
    private RateStatusDtoOut companyRateStatus; // Changed from RateStatus to RateStatusDtoOut for translation support

    private LocalDateTime executionDate;
    private LocalDateTime createdTime;
    private String note;
    private LocalDateTime lastUpdateTime;
    private String updater;
    // This can be either InfluencerPublicProfileDto or InfluencerForCompanyProfileDto based on user permissions
    // Using the base interface to avoid LazyInitializationException
    private PublicProfileDto influencer;
    private PartnershipOpportunitySimpleDtoOut partnershipOpportunity;

    // Content submissions - can be null if no content submitted yet
    private List<AppliedOpportunityContentDtoOut> contentSubmissions;
    private Long version;
}
