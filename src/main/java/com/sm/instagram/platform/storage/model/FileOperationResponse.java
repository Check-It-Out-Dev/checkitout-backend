package com.sm.instagram.platform.storage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for file operation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileOperationResponse {
    
    private boolean success;
    private String message;
    private Map<String, Object> data;
    private String error;
    private String correlationId;
    private long timestamp;
    
    /**
     * Create a success response.
     */
    public static FileOperationResponse success(String message, Map<String, Object> data) {
        return FileOperationResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * Create an error response.
     */
    public static FileOperationResponse error(String error) {
        return FileOperationResponse.builder()
                .success(false)
                .error(error)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
