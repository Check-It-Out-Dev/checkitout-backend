package com.sm.instagram.platform.auth.dto;

import lombok.Data;

/**
 * Response DTO for social media connection operations.
 */
@Data
public class SocialConnectionResponse {
    private boolean success;
    private Long connectionId;
    private String platformName;
    private String socialUserId;
    private String displayName;
    private Integer followersCount;
    private String errorMessage;
}