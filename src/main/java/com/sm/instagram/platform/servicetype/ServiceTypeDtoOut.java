package com.sm.instagram.platform.servicetype;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for ServiceType with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTypeDtoOut {
    private Long id;
    private String name;                // Translated name
    private String originalName;        // Original name from DB
    private String description;         // Translated description
    private String originalDescription; // Original description from DB
    private String category;            // Translated category
    private String originalCategory;    // Original category from DB
}
