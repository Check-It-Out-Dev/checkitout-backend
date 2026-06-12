package com.sm.instagram.platform.consent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for Legal Basis with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalBasisDtoOut {
    private String value;           // Original value: "consent", "legitimate_interest"
    private String label;           // Translated label: "Consent", "Legitimate Interest" 
    private String description;     // Translated description
    private String originalLabel;   // Fallback to original value if translation missing
}
