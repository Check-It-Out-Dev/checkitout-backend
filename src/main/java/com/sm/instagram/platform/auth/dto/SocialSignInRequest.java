package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for social media sign-in.
 */
@Data
public class SocialSignInRequest {
    @NotBlank(message = "{validation.auth.platformName.required}")
    private String platformName;

    @NotBlank(message = "{validation.auth.authCode.required}")
    private String authCode;
}