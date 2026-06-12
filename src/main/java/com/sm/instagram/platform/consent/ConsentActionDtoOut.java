package com.sm.instagram.platform.consent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for ConsentAction with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentActionDtoOut {
    private String value;           // Original enum value: "GRANTED", "WITHDRAWN", etc.
    private String label;           // Translated label: "Granted", "Withdrawn", etc.
    private String description;     // Translated description
    private String originalLabel;   // Fallback to enum name if translation missing
    private String colorTheme;      // UI color theme
    private String icon;            // UI icon
    private boolean isPositiveAction;    // Helper methods
    private boolean isNegativeAction;
    private boolean isModificationAction;
}
