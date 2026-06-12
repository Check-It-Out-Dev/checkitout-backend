package com.sm.instagram.platform.consent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for Consent Type with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentTypeDtoOut {
    private String value;           // Original value: "marketing", "analytics", "cookies"
    private String label;           // Translated label: "Marketing Communications", "Analytics and Performance"
    private String description;     // Translated description
    private String originalLabel;   // Fallback to original value if translation missing
}
