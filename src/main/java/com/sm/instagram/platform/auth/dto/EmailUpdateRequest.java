package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for email update operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailUpdateRequest {
    
    @NotBlank(message = "{validation.auth.newEmail.required}")
    @Email(message = "{validation.email.invalid}")
    private String newEmail;

    @NotBlank(message = "{validation.auth.password.emailUpdate}")
    private String password;
}
