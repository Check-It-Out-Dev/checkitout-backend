package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for exchanging Firebase ID token for backend JWT.
 * Supports both field names for compatibility.
 */
@Data
public class ExchangeTokenRequest {
    
    @NotBlank(message = "{validation.auth.firebaseToken.required}")
    @JsonAlias({"idToken", "firebaseToken"})
    private String idToken;
    
    @JsonAlias({"expirationDays", "expirationHours"})
    private Integer expirationDays = 7; // Default 7 days
    
    /**
     * Get expiration in hours (convert days to hours)
     */
    public Integer getExpirationHours() {
        return expirationDays != null ? expirationDays * 24 : 168; // Default 7 days = 168 hours
    }
}
