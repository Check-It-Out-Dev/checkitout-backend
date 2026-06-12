package com.sm.instagram.platform.admin.cascade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch cascade delete operations.
 * Maximum 10 users per batch to prevent accidental mass deletion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCascadeDeleteRequest {
    /**
     * List of user IDs to delete. Maximum 10.
     */
    @Schema(description = "List of user IDs to cascade-delete. Maximum 10 per batch to prevent accidental mass deletion.",
            maxLength = 10)
    @NotEmpty(message = "User IDs list cannot be empty")
    @Size(max = 10, message = "Maximum 10 users can be deleted in a single batch")
    private List<Long> userIds;

    /**
     * Expected total number of entities across all users (from batch preview).
     */
    @NotNull(message = "Expected total entity count is required")
    @Min(value = 0, message = "Expected entity count cannot be negative")
    private Integer expectedTotalEntityCount;

    /**
     * Reason for deletion (required for audit trail).
     */
    @NotBlank(message = "Deletion reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
