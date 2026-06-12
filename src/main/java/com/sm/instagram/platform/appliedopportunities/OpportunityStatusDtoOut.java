package com.sm.instagram.platform.appliedopportunities;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Output DTO for OpportunityStatus with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpportunityStatusDtoOut {
    @Schema(allowableValues = {"APPLIED", "ACCEPTED_BY_COMPANY", "REJECTED_BY_COMPANY", "ACCEPTED_BY_INFLUENCER",
            "REJECTED_BY_INFLUENCER", "CONTENT_SEND_TO_ACCEPT", "CONTENT_APPROVED", "CONTENT_REJECTED",
            "CONTENT_POSTED", "CONTENT_POSTED_REJECTED", "TO_BE_PAID", "DONE"}, description = "OpportunityStatus enum name")
    private String value;        // Original enum value: "APPLIED", "CONTENT_APPROVED", etc.
    private String label;        // Translated label: "Applied", "Content Approved", etc.
    private String description;  // Translated description
    private String originalLabel; // Fallback to enum name if translation missing
    private String colorTheme;   // UI color theme
    private String icon;         // UI icon
    private List<String> aliases; // Alternative names
    @ArraySchema(schema = @Schema(allowableValues = {"APPLIED", "ACCEPTED_BY_COMPANY", "REJECTED_BY_COMPANY",
            "ACCEPTED_BY_INFLUENCER", "REJECTED_BY_INFLUENCER", "CONTENT_SEND_TO_ACCEPT", "CONTENT_APPROVED",
            "CONTENT_REJECTED", "CONTENT_POSTED", "CONTENT_POSTED_REJECTED", "TO_BE_PAID", "DONE"},
            description = "OpportunityStatus enum name"))
    private List<String> possibleTransitions; // Possible next states
    private boolean isTerminal;  // Is terminal state
    private boolean isSuccessful; // Is successful completion
}
