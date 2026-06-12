package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for universal token exchange endpoint.
 * Supports two authentication flows:
 * 1. Email/password: ID token in request body
 * 2. OAuth: Custom token in HttpOnly cookie (idToken can be null)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenExchangeRequest {
    
    // Optional: For OAuth flow, token is in HttpOnly cookie
    // Required: For email/password flow, token must be in body
    private String idToken;
    
    @Min(value = 1, message = "{validation.auth.expirationDays.min}")
    @Max(value = 30, message = "{validation.auth.expirationDays.max}")
    @Builder.Default
    private Integer expirationDays = 7;
}
