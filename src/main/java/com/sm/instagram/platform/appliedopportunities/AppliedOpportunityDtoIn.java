package com.sm.instagram.platform.appliedopportunities;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AppliedOpportunityDtoIn {

    private Long influencer;
    @NotNull(message = "{validation.applied.partnershipOpportunity.required}")
    private Long partnershipOpportunity;
    @Size(max = 500, message = "{validation.applied.note.size}")
    private String note;
    private OpportunityStatus opportunityStatus;
    private LocalDateTime executionDate;
    private RateStatus rateStatus;
    private RateStatus companyRateStatus;
    private Long version;
}