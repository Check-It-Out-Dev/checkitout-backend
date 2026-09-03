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
    // implementation = OpportunityStatus.class makes the spec REFERENCE the
    // shared enum component instead of inlining a per-DTO copy — the FE
    // generator then reuses the single OpportunityStatus TS enum rather than
    // minting an incompatible OpportunityStatusDtoOutValueEnum (docs-only;
    // the runtime type stays String).
    @Schema(implementation = OpportunityStatus.class, description = "OpportunityStatus enum name")
    private String value;        // Original enum value: "APPLIED", "CONTENT_APPROVED", etc.
    private String label;        // Translated label: "Applied", "Content Approved", etc.
    private String description;  // Translated description
    private String originalLabel; // Fallback to enum name if translation missing
    private String colorTheme;   // UI color theme
    private String icon;         // UI icon
    private List<String> aliases; // Alternative names
    @ArraySchema(schema = @Schema(implementation = OpportunityStatus.class,
            description = "OpportunityStatus enum name"))
    private List<String> possibleTransitions; // Possible next states
    private boolean isTerminal;  // Is terminal state
    private boolean isSuccessful; // Is successful completion
}
