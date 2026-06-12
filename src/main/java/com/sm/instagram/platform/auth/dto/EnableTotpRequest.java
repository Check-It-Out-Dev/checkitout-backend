package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request to enable TOTP after verification
 */
@Data
public class EnableTotpRequest {
    @NotBlank(message = "{validation.auth.verificationCode.required}")
    @Pattern(regexp = "\\d{6}", message = "{validation.auth.totp.code.format}")
    private String verificationCode;
}
