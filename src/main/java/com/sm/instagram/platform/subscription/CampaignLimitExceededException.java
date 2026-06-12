package com.sm.instagram.platform.subscription;

import com.sm.instagram.platform.common.exceptions.BusinessRuleViolationException;
import lombok.Getter;

@Getter
public class CampaignLimitExceededException extends BusinessRuleViolationException {

    private final int limit;
    private final String planName;

    public CampaignLimitExceededException(int limit, String planName) {
        super("error.subscription.campaign_limit_reached", "CAMPAIGN_LIMIT",
                "limit=" + limit + ", plan=" + planName);
        this.limit = limit;
        this.planName = planName;
    }
}
