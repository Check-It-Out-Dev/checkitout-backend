package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for registration endpoints.
 * Contains custom token and user info for new registrations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegistrationResponse {
    
    private boolean success;
    private String customToken;
    private Long userId;
    private String firebaseUid;
    @Schema(allowableValues = {"ADMIN", "PENDING_ADMIN", "INFLUENCER", "COMPANY"}, description = "UserType enum name")
    private String userType;
    private String error;
    
    /**
     * Check if registration was successful.
     * 
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Create an error response.
     * 
     * @param errorMessage Error message
     * @return Error response
     */
    public static RegistrationResponse error(String errorMessage) {
        return RegistrationResponse.builder()
            .success(false)
            .error(errorMessage)
            .build();
    }
}
