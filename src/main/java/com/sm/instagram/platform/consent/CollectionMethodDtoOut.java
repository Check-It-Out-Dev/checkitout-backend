package com.sm.instagram.platform.consent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for Collection Method with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionMethodDtoOut {
    private String value;           // Original value: "web_form", "api", "import"
    private String label;           // Translated label: "Web Form", "API", "Data Import"
    private String description;     // Translated description
    private String originalLabel;   // Fallback to original value if translation missing
}
