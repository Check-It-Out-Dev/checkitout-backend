package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for authentication operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthOperationResponse {
    
    private boolean success;
    private String message;
    private String correlationId;
    private Long processingTime;
    private Map<String, Object> data;
    private String error;
    
    /**
     * Create a success response.
     */
    public static AuthOperationResponse success(String message) {
        return AuthOperationResponse.builder()
                .success(true)
                .message(message)
                .build();
    }
    
    /**
     * Create a success response with data.
     */
    public static AuthOperationResponse success(String message, Map<String, Object> data) {
        return AuthOperationResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }
    
    /**
     * Create an error response.
     */
    public static AuthOperationResponse error(String error) {
        return AuthOperationResponse.builder()
                .success(false)
                .error(error)
                .build();
    }
}
