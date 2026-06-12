package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for re-authentication operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReauthRequest {
    
    @NotBlank(message = "{validation.auth.password.reauth}")
    private String password;
}
