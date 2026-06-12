package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request to verify a TOTP code.
 * The Firebase user ID is extracted from the JWT token/principal for security.
 * This prevents brute force attacks by requiring valid authentication.
 *
 * Code is a String to preserve leading zeros (e.g., "012345").
 * Pattern allows 1-6 digits for backward compatibility with older frontends
 * that may send numeric values (e.g., 12345 instead of "012345").
 */
@Data
public class TotpVerifyRequest {
    @NotBlank(message = "{validation.auth.totp.code.required}")
    @Pattern(regexp = "\\d{6}", message = "{validation.auth.totp.code.format}")
    private String code;
}
