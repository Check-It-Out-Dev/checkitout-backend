package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for influencer registration that uses cached social data
 * instead of requiring a new auth code exchange.
 */
@Data
public class RegisterInfluencerRequest {
    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.format}")
    private String email;

    @NotBlank(message = "{validation.auth.platformName.required}")
    private String platformName;

    /**
     * Social user data that was returned from the social-sign-in endpoint.
     * This avoids having to exchange the auth code a second time.
     */
    private Map<String, Object> socialUserData;
}