package com.sm.instagram.platform.appliedopportunities;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for RateStatus with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateStatusDtoOut {
    @Schema(allowableValues = {"DEFAULT", "POSITIVE", "NEGATIVE"}, description = "RateStatus enum name")
    private String value;        // Original enum value: "DEFAULT", "POSITIVE", "NEGATIVE"
    private String label;        // Translated label: "Default", "Positive", "Negative"
    private String originalLabel; // Fallback to enum name if translation missing
    private String colorTheme;   // UI color theme for the status
    private String icon;         // UI icon representation

}
