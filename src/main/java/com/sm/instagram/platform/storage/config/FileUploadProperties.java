package com.sm.instagram.platform.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "file-upload")
public class FileUploadProperties {

    /**
     * Maximum file size in bytes
     */
    private long maxFileSize = 5242880; // 5MB default

    /**
     * Allowed file extensions
     */
    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * Signed URL expiration time in minutes
     */
    private int signedUrlExpirationMinutes = 5;

    /**
     * Storage path pattern with placeholders
     * Available placeholders: {userId}, {timestamp}, {filename}
     */
    private String storagePathPattern = "content/{userId}/{timestamp}_{filename}";
}
