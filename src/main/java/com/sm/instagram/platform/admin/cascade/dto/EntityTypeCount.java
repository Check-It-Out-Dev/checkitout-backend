package com.sm.instagram.platform.admin.cascade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for counting entities by type in cascade delete preview.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityTypeCount {
    private String entityType;
    private int count;
    private String description;
}
