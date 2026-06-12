package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for forgot password endpoint.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "validation.email.required")
    @Email(message = "validation.email.invalid")
    @Size(max = 254, message = "validation.email.too_long")  // BUG FIX #12: RFC 5321 max email length
    private String email;
}
