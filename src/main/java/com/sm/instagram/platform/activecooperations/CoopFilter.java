package com.sm.instagram.platform.activecooperations;

import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.appliedopportunities.RateStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Mutable query parameters used by {@code ActiveCooperationService} to drive
 * paged active-cooperation lookups. All fields are optional; null means
 * "do not filter on this dimension".
 *
 * <p>The fields fall into three groups:
 * <ul>
 *   <li><b>Scope</b> — {@code companyId}, {@code influencerId},
 *       {@code partnershipOpportunityId} narrow to a single owner / campaign.</li>
 *   <li><b>Status</b> — {@code opportunityStatuses}, {@code filterRateStatus}
 *       restrict by lifecycle phase.</li>
 *   <li><b>Audience</b> — {@code minFollowers}, {@code maxFollowers},
 *       {@code minPositiveRates} apply influencer-side thresholds.</li>
 * </ul>
 */
@Getter
@Setter
public class CoopFilter {

    private Long companyId;
    private Long influencerId;
    private Long partnershipOpportunityId;

    private List<OpportunityStatus> opportunityStatuses;
    private RateStatus filterRateStatus;

    private Integer minFollowers;
    private Integer maxFollowers;
    private Long minPositiveRates;
}
