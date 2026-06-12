package com.sm.instagram.platform.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Google Cloud Storage for GeoIP database storage.
 * Creates a Storage bean specifically for GeoIP operations.
 * 
 * @deprecated Use UnifiedStorageConfiguration instead. 
 * This class is disabled by default - only activated if unified config is disabled.
 * 
 * Services that need GeoIP storage should autowire: @Qualifier("geoIpStorage") Storage storage
 * 
 * @author CheckItOut Team
 * @since 2.0.0 - Refactored to use GoogleCredentialsProvider
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.unified.enabled", havingValue = "false", matchIfMissing = false)
@Deprecated
public class GoogleCloudStorageConfig {
    
    private final GoogleCredentialsProvider credentialsProvider;
    
    /**
     * Create Google Cloud Storage client for GeoIP operations.
     * Uses the same credentials as Firebase for consistency.
     * 
     * @return Google Cloud Storage client for GeoIP bucket
     */
    @Bean(name = "geoIpStorage")
    public Storage geoIpStorage() {
        log.info("==========================================");
        log.info("☁️ GOOGLE CLOUD STORAGE (GeoIP) CONFIGURATION");
        log.info("==========================================");
        
        String projectId = credentialsProvider.getProjectId();
        log.info("Project ID: {}", projectId);
        log.info("Instance Type: {}", credentialsProvider.getInstanceType());
        
        if (credentialsProvider.getServiceAccountEmail() != null) {
            log.info("Service Account: {}", credentialsProvider.getServiceAccountEmail());
        }
        
        try {
            // Create Storage client with credentials from provider
            Storage storage = StorageOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(credentialsProvider.getCachedCredentials())
                    .build()
                    .getService();
            
            log.info("✅ Google Cloud Storage client initialized successfully for GeoIP storage");
            log.info("==========================================");
            
            return storage;
            
        } catch (Exception e) {
            log.error("Failed to initialize Google Cloud Storage client", e);
            throw new RuntimeException("Could not initialize Google Cloud Storage for GeoIP", e);
        }
    }
}
