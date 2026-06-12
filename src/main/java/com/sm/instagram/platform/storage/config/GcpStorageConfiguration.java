package com.sm.instagram.platform.storage.config;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Google Cloud Storage for file uploads.
 * Creates a Storage bean specifically for user file uploads to the main app bucket.
 * 
 * @deprecated Use UnifiedStorageConfiguration instead.
 * This class is disabled by default - only activated if unified config is disabled.
 * 
 * Services that need file upload storage should autowire: @Qualifier("fileUploadStorage") Storage storage
 * 
 * @author CheckItOut Team
 * @since 2.0.0 - Refactored to use GoogleCredentialsProvider
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.unified.enabled", havingValue = "false", matchIfMissing = false)
@Deprecated
public class GcpStorageConfiguration {
    
    private final GoogleCredentialsProvider credentialsProvider;
    
    @Value("${gcp.bucket-name:check-it-out-47c50.firebasestorage.app}")
    private String bucketName;
    
    /**
     * Create Google Cloud Storage client for file uploads.
     * Uses the same credentials as Firebase for consistency.
     * 
     * @return Google Cloud Storage client for file upload operations
     */
    @Bean(name = "fileUploadStorage")
    public Storage fileUploadStorage() {
        log.info("==========================================");
        log.info("☁️ GOOGLE CLOUD STORAGE (File Upload) CONFIGURATION");
        log.info("==========================================");
        
        String projectId = credentialsProvider.getProjectId();
        log.info("📍 GCS Configuration:");
        log.info("   Project ID: {}", projectId);
        log.info("   Instance Type: {}", credentialsProvider.getInstanceType());
        log.info("   Bucket Name: {}", bucketName);
        
        // Log service account info if available
        if (credentialsProvider.getServiceAccountEmail() != null) {
            log.info("   Service Account: {}", credentialsProvider.getServiceAccountEmail());
        }
        
        try {
            // Create Storage client with credentials from provider
            Storage storage = StorageOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentialsProvider.getCachedCredentials())
                .build()
                .getService();
            
            // Test connection by checking if bucket exists
            try {
                var bucket = storage.get(bucketName);
                if (bucket != null && bucket.exists()) {
                    log.info("   ✓ Bucket exists and is accessible");
                    log.info("   Bucket location: {}", bucket.getLocation());
                    log.info("   Storage class: {}", bucket.getStorageClass());
                    
                    // Check if it's a Firebase Storage bucket
                    if (bucketName.contains("firebasestorage.app") || bucketName.contains("appspot.com")) {
                        log.info("   ✓ Firebase Storage bucket detected");
                    }
                } else {
                    log.warn("   ⚠️ Bucket '{}' not found or not accessible", bucketName);
                    log.warn("   Creating bucket is typically done via Firebase Console or gcloud CLI");
                }
            } catch (Exception e) {
                log.warn("   ⚠️ Could not verify bucket access: {}", e.getMessage());
                log.warn("   This might be normal if the bucket hasn't been created yet");
            }
            
            log.info("✅ Google Cloud Storage client configured successfully for file uploads");
            log.info("==========================================");
            
            return storage;
            
        } catch (Exception e) {
            log.error("❌ Failed to configure Google Cloud Storage", e);
            log.error("   Troubleshooting steps:");
            log.error("   1. Verify service account has 'Storage Object Admin' role");
            log.error("   2. Check if the bucket exists: {}", bucketName);
            log.error("   3. Ensure project ID is correct: {}", projectId);
            log.error("   4. Verify Cloud Storage API is enabled in GCP Console");
            throw new RuntimeException("Failed to configure Google Cloud Storage for file uploads", e);
        }
    }
}
