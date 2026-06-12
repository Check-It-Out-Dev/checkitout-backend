package com.sm.instagram.platform.admin.cascade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for confirming a cascade delete operation.
 * Requires a confirmation code and expected entity count as safety measures.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CascadeDeleteConfirmationRequest {
    /**
     * Confirmation code that must match CASCADE-DELETE-{userId}.
     */
    @NotBlank(message = "Confirmation code is required")
    @Size(max = 100, message = "Confirmation code cannot exceed 100 characters")
    private String confirmationCode;

    /**
     * Expected number of entities to be deleted (from preview).
     * Used as a safety check to ensure admin saw the correct preview.
     */
    @NotNull(message = "Expected entity count is required")
    @Min(value = 0, message = "Expected entity count cannot be negative")
    private Integer expectedEntityCount;

    /**
     * Reason for deletion (required for audit trail).
     */
    @NotBlank(message = "Deletion reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
