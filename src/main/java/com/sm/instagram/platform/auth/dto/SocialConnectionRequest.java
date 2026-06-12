package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for connecting a user to a social media platform.
 * The Firebase user ID is obtained from the security context.
 */
@Data
public class SocialConnectionRequest {
    @NotBlank(message = "{validation.auth.platformName.required}")
    private String platformName;

    @NotBlank(message = "{validation.auth.authCode.required}")
    private String authCode;

    private Boolean setPrimary = true;
}