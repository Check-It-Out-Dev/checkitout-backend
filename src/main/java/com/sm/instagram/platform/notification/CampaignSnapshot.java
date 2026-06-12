package com.sm.instagram.platform.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Snapshot of a campaign/opportunity at notification creation time.
 * Stored as part of NotificationSnapshot JSONB.
 *
 * Why this exists:
 * - Campaign title might be edited
 * - Campaign might be deleted
 * - Notification should still show what user applied to
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Partnership opportunity ID.
     * Can be used to link to campaign if still exists.
     */
    private Long id;

    /**
     * Campaign title at notification time.
     */
    private String title;

    /**
     * Applied opportunity ID (the specific application).
     */
    private Long appliedOpportunityId;

    /**
     * Offered compensation at notification time.
     * Might be different from current offer.
     */
    private BigDecimal compensation;

    /**
     * Currency code (e.g., "PLN", "USD", "EUR").
     */
    private String currency;

    /**
     * Create snapshot from AppliedOpportunity entity.
     *
     * @param appliedOpportunity the application to snapshot
     * @return CampaignSnapshot with current data
     */
    public static CampaignSnapshot fromAppliedOpportunity(
            com.sm.instagram.platform.appliedopportunities.AppliedOpportunity appliedOpportunity) {
        if (appliedOpportunity == null) {
            return null;
        }

        var partnership = appliedOpportunity.getPartnershipOpportunity();

        return CampaignSnapshot.builder()
                .id(partnership != null ? partnership.getId() : null)
                .title(partnership != null ? partnership.getTitle() : null)
                .appliedOpportunityId(appliedOpportunity.getId())
                // Note: PartnershipOpportunity uses compensationAmountMin/Max (int), not getBudget()
                .compensation(partnership != null && partnership.getCompensationAmountMax() != null
                        ? BigDecimal.valueOf(partnership.getCompensationAmountMax()) : null)
                .currency("PLN") // Default currency, adjust as needed
                .build();
    }
}