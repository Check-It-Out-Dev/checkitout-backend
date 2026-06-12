package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Firebase Authentication Request DTO.
 * 
 * Used for login and registration requests to Firebase Auth API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FirebaseAuthRequest {
    
    @Email(message = "{validation.email.invalid}")
    @NotBlank(message = "{validation.email.required}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    private String password;
    
    // Always true for Firebase Web API
    @Builder.Default
    private boolean returnSecureToken = true;
    
    // For token refresh
    private String refreshToken;
    
    // For custom token sign-in (not used in proxy flow)
    private String token;
}
