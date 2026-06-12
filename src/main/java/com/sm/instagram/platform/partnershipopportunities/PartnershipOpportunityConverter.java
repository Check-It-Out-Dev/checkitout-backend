package com.sm.instagram.platform.partnershipopportunities;

import org.modelmapper.Converter;
import org.springframework.stereotype.Component;

@Component
public class PartnershipOpportunityConverter {
    private final PartnershipOpportunityRepository partnershipOpportunityRepository;

    public PartnershipOpportunityConverter(PartnershipOpportunityRepository partnershipOpportunityRepository) {
        this.partnershipOpportunityRepository = partnershipOpportunityRepository;
    }

    public Converter<Long, PartnershipOpportunity> toPartnershipOpportunityConverter() {
        return ctx -> {
            Long opportunityId = ctx.getSource();
            if (opportunityId == null) {
                throw new IllegalArgumentException("Partnership opportunity ID cannot be null");
            }
            return partnershipOpportunityRepository.findById(opportunityId)
                    .orElseThrow(() -> new com.sm.instagram.platform.common.exceptions.ResourceNotFoundException(
                            "error.business.item_not_found", opportunityId));
        };
    }

    public Converter<PartnershipOpportunity, Long> fromPartnershipOpportunityConverter() {
        return ctx -> {
            PartnershipOpportunity opportunity = ctx.getSource();
            return opportunity != null ? opportunity.getId() : null;
        };
    }

}
