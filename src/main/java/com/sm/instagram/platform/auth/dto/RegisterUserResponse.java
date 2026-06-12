package com.sm.instagram.platform.auth.dto;

import lombok.Data;

/**
 * Response DTO for user registration with authentication tokens and status.
 */
@Data
public class RegisterUserResponse {
    private boolean success;
    private String firebaseUserId;
    private Long userId;
    private String customToken; // Firebase custom token for authentication
    private SocialConnectionResponse socialConnection; // Optional, only if social connection was established
    private String socialConnectionError; // Only set if social connection failed
    private String error; // General error message for registration failures
}