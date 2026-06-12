package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for completing social registration.
 * Used when a new OAuth user provides their email to complete registration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteSocialRegistrationRequest {
    
    @NotBlank(message = "{validation.auth.sessionId.required}")
    private String sessionId;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.format}")
    private String email;
}
