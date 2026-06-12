package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for Firebase authentication login.
 * Contains the Firebase ID token to be validated and exchanged for backend JWT.
 */
@Data
public class FirebaseLoginRequest {
    
    @NotBlank(message = "{validation.auth.firebaseToken.required}")
    private String firebaseToken;
    
    private Integer expirationHours = 8; // Default 8 hours
}
