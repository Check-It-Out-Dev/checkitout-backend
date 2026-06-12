package com.sm.instagram.platform.auth.dto;

import lombok.Data;

import java.util.Map;

/**
 * Response DTO for social media sign-in.
 */
@Data
public class SocialSignInResponse {
    private boolean success;
    private boolean userExists;
    private String firebaseUserId;
    private String customToken;
    private Long userId;
    private String errorMessage;
    private Map<String, Object> socialUserData;
}