package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for account deletion operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {
    
    @NotBlank(message = "{validation.auth.password.deleteAccount}")
    private String password;
    
    private String reason;  // Optional reason for deletion
}
