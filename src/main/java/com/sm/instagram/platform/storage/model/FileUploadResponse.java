package com.sm.instagram.platform.storage.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class FileUploadResponse {

    private String uploadUrl;      // The signed URL for uploading
    private String publicUrl;      // The public URL for viewing (after upload)
    private String filePath;       // The path in storage
    private Instant expiresAt;     // When the upload URL expires
    private String uploadId;       // Unique ID for tracking this upload
    private RateLimitInfo rateLimitInfo;  // Current rate limit status
    private String error;
    private UploadInstructions uploadInstructions;  // Instructions for frontend

    // Success constructor
    public FileUploadResponse(String uploadUrl, String publicUrl,
                              String filePath, Instant expiresAt, String uploadId) {
        this.uploadUrl = uploadUrl;
        this.publicUrl = publicUrl;
        this.filePath = filePath;
        this.expiresAt = expiresAt;
        this.uploadId = uploadId;
    }

    // Error constructor
    public FileUploadResponse(String error) {
        this.error = error;
    }

    // All getters and setters
    public String getUploadUrl() {
        return uploadUrl;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getFilePath() {
        return filePath;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getUploadId() {
        return uploadId;
    }

    public RateLimitInfo getRateLimitInfo() {
        return rateLimitInfo;
    }

    public void setRateLimitInfo(RateLimitInfo rateLimitInfo) {
        this.rateLimitInfo = rateLimitInfo;
    }

    public String getError() {
        return error;
    }

    // Nested class for rate limit info
    public static class RateLimitInfo {
        private Integer remainingHourly;
        private Integer remainingDaily;
        private Long storageUsedMB;
        private Long storageLimitMB;

        public RateLimitInfo(Integer remainingHourly, Integer remainingDaily,
                             Long storageUsedMB, Long storageLimitMB) {
            this.remainingHourly = remainingHourly;
            this.remainingDaily = remainingDaily;
            this.storageUsedMB = storageUsedMB;
            this.storageLimitMB = storageLimitMB;
        }

        // Getters
        public Integer getRemainingHourly() {
            return remainingHourly;
        }

        public Integer getRemainingDaily() {
            return remainingDaily;
        }

        public Long getStorageUsedMB() {
            return storageUsedMB;
        }

        public Long getStorageLimitMB() {
            return storageLimitMB;
        }
    }
    
    // Instructions for frontend upload
    public static class UploadInstructions {
        private String method = "PUT";
        private String[] requiredHeaders;
        private String[] doNotSetHeaders;
        private String signatureVersion;
        private String note;
        
        public UploadInstructions() {
            // Default V4 signature instructions
            this.requiredHeaders = new String[]{"Content-Type: <file.type>"};
            this.doNotSetHeaders = new String[]{"Authorization", "Cookie", "Content-Length (browser sets automatically)"};
            this.signatureVersion = "V4";
            this.note = "Use XMLHttpRequest or fetch. Do NOT double-encode the URL. Send raw file as body.";
        }
        
        // Getters
        public String getMethod() { return method; }
        public String[] getRequiredHeaders() { return requiredHeaders; }
        public String[] getDoNotSetHeaders() { return doNotSetHeaders; }
        public String getSignatureVersion() { return signatureVersion; }
        public String getNote() { return note; }
    }
    
    public UploadInstructions getUploadInstructions() {
        return uploadInstructions;
    }
    
    public void setUploadInstructions(UploadInstructions uploadInstructions) {
        this.uploadInstructions = uploadInstructions;
    }
}
