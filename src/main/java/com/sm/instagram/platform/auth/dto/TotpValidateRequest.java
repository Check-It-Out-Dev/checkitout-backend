package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request to validate TOTP during login
 */
@Data
public class TotpValidateRequest {
    @NotBlank(message = "{validation.auth.userId.required}")
    private String userId;

    @NotBlank(message = "{validation.auth.totp.code.required}")
    @Pattern(regexp = "\\d{6}", message = "{validation.auth.totp.code.format}")
    private String code;
}
