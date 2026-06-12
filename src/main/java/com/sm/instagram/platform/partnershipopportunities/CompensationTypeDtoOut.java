package com.sm.instagram.platform.partnershipopportunities;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for CompensationType with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensationTypeDtoOut {
    @Schema(allowableValues = {"CASH", "BARTER"}, description = "CompensationType enum name")
    private String value;        // Original enum value: "CASH", "BARTER"
    private String label;        // Translated label: "Cash Payment", "Product Exchange"
    private String originalLabel; // Fallback to enum name if translation missing
}
