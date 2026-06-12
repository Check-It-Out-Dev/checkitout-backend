package com.sm.instagram.platform.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a specific reason why deletion is blocked.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletionBlocker {
    private DeletionBlockerCategory category;
    private String reason;
    private String description;
    private Integer count;
    private List<Long> entityIds;
    private String entityType;
    private String entityDescription;
}
