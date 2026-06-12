package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request to use a backup code for 2FA
 */
@Data
public class BackupCodeRequest {
    @NotBlank(message = "{validation.auth.userId.required}")
    private String userId;

    @NotBlank(message = "{validation.auth.backupCode.required}")
    @Pattern(regexp = "^[A-Z0-9]{8}$", message = "{validation.auth.backupCode.pattern}")
    private String backupCode;
}
