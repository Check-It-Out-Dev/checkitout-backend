package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Firebase Authentication Response DTO.
 * 
 * Contains tokens and user info returned from Firebase Auth API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FirebaseAuthResponse {
    
    // Success response fields
    private String idToken;
    private String refreshToken;
    private String expiresIn;
    private String localId;
    private String email;
    private boolean emailVerified;
    private String displayName;
    private boolean registered;
    
    // Error response fields
    private FirebaseError error;
    
    // Metadata (added by proxy)
    private Long processingTime;
    private String correlationId;
    
    // 2FA fields (added for admin users)
    private boolean requires2FA;
    private boolean requires2FASetup;
    private String message;
    
    // Role field (from Firebase custom claims)
    @Schema(allowableValues = {"ADMIN", "PENDING_ADMIN", "INFLUENCER", "COMPANY"}, description = "User role from Firebase custom claims")
    private String role;
    
    // Success flag (explicitly set for clarity)
    private boolean success;
    
    /**
     * Create error response.
     */
    public static FirebaseAuthResponse error(String message) {
        return FirebaseAuthResponse.builder()
            .error(FirebaseError.builder()
                .message(message)
                .code(400)
                .build())
            .build();
    }
    
    /**
     * Check if response is successful.
     */
    public boolean isSuccess() {
        // Use explicit success flag if set, otherwise check for error and idToken
        if (success) {
            return true;
        }
        return error == null && idToken != null;
    }
    
    /**
     * Firebase error structure.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FirebaseError {
        private Integer code;
        private String message;
        private Object errors;
    }
}
