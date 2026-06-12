package com.sm.instagram.platform.appliedopportunities;

import org.modelmapper.Converter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AppliedOpportunityConverter {

    private final AppliedOpportunityContentMapping contentMapping;

    public AppliedOpportunityConverter(AppliedOpportunityContentMapping contentMapping) {
        this.contentMapping = contentMapping;
    }

    /**
     * Converter for mapping content submissions to full DTOs
     */
    public Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentDtoOut>> toContentDtoListConverter() {
        return context -> {
            List<AppliedOpportunityContent> source = context.getSource();
            if (source == null || source.isEmpty()) {
                return Collections.emptyList();
            }
            return source.stream()
                    .map(contentMapping::toDto)
                    .toList();
        };
    }

    /**
     * Converter for mapping content submissions to simple DTOs
     */
    public Converter<List<AppliedOpportunityContent>, List<AppliedOpportunityContentSimpleDtoOut>> toSimpleContentDtoListConverter() {
        return context -> {
            List<AppliedOpportunityContent> source = context.getSource();
            if (source == null || source.isEmpty()) {
                return Collections.emptyList();
            }
            return source.stream()
                    .map(contentMapping::toSimpleDto)
                    .toList();
        };
    }
}
