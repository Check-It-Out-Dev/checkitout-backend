package com.sm.instagram.platform.subscription.dto;

import com.sm.instagram.platform.subscription.entity.SubscriptionStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class SubscriptionStatusDtoOut {

    private String currentPlanName;
    private BigDecimal currentPlanPrice;
    private int campaignLimit;
    private long campaignsUsedThisPeriod;
    private SubscriptionStatus status;

    private LocalDateTime billingPeriodStart;
    private LocalDateTime billingPeriodEnd;

    private boolean trialEligible;
    private boolean trialUsed;
    private LocalDateTime trialEndDate;

    private String targetPlanName;
    private boolean hasStripeSubscription;
}
