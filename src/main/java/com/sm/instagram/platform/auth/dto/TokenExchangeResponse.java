package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for token exchange endpoint.
 * Contains user information after successful authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenExchangeResponse {
    
    private boolean success;
    private Long userId;
    private String email;
    @Schema(allowableValues = {"ADMIN", "PENDING_ADMIN", "INFLUENCER", "COMPANY"}, description = "User role (Permission/UserType name)")
    private String role;
    private String firebaseUid;
    private String error;
    
    // 2FA-related fields
    private Boolean requires2FA;  // true if user needs to complete 2FA
    private Boolean requires2FASetup;  // true if user needs to setup 2FA for first time
    private Boolean twoFactorEnabled;  // true if 2FA is enabled for the account
    private Boolean twoFactorVerified;  // true if 2FA has been verified in this session
    
    // Cookie type information
    @Schema(allowableValues = {"PARTIAL", "FULL"}, description = "Session cookie type")
    private String cookieType;  // "PARTIAL" or "FULL"
    private String sessionDuration;  // "10 minutes" or "7 days"
    
    /**
     * Create an error response.
     * 
     * @param errorMessage Error message
     * @return Error response
     */
    public static TokenExchangeResponse error(String errorMessage) {
        return TokenExchangeResponse.builder()
            .success(false)
            .error(errorMessage)
            .build();
    }
    
    /**
     * Create a success response.
     * 
     * @param userId User ID
     * @param email User email
     * @param role User role
     * @param firebaseUid Firebase user ID
     * @return Success response
     */
    public static TokenExchangeResponse success(Long userId, String email, String role, String firebaseUid) {
        return TokenExchangeResponse.builder()
            .success(true)
            .userId(userId)
            .email(email)
            .role(role)
            .firebaseUid(firebaseUid)
            .build();
    }
}
