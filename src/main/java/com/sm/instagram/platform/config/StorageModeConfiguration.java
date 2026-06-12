package com.sm.instagram.platform.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import jakarta.annotation.PostConstruct;

/**
 * Unified storage mode configuration.
 * This single property controls ALL storage/caching decisions in the application.
 * 
 * storage.mode = redis (default) - Use Redis for everything
 * storage.mode = in-memory - Use in-memory for everything
 */
@Slf4j
@Configuration
@Getter
public class StorageModeConfiguration {
    
    public enum StorageMode {
        REDIS("redis", "Redis-based storage (distributed, persistent)"),
        IN_MEMORY("in-memory", "In-memory storage (local, non-persistent)");
        
        private final String value;
        private final String description;
        
        StorageMode(String value, String description) {
            this.value = value;
            this.description = description;
        }
        
        public static StorageMode fromString(String value) {
            for (StorageMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return REDIS; // Default to Redis
        }
    }
    
    @Value("${storage.mode:redis}")
    private String storageModeValue;
    
    private StorageMode storageMode;
    
    @PostConstruct
    public void init() {
        this.storageMode = StorageMode.fromString(storageModeValue);
        
        log.info("==========================================");
        log.info("🗄️  STORAGE MODE CONFIGURATION");
        log.info("==========================================");
        log.info("Storage Mode: {} - {}", storageMode.value, storageMode.description);
        log.info("");
        log.info("This affects:");
        log.info("  • User cache (authentication)");
        log.info("  • Rate limiting");
        log.info("  • Session storage");
        log.info("  • Application cache");
        log.info("  • Upload tracking");
        
        if (storageMode == StorageMode.IN_MEMORY) {
            log.warn("⚠️  IN-MEMORY MODE ACTIVE");
            log.warn("  • Data will be lost on restart");
            log.warn("  • Not suitable for production");
            log.warn("  • Cannot be distributed across instances");
        } else {
            log.info("✅ REDIS MODE ACTIVE");
            log.info("  • Data persists across restarts");
            log.info("  • Supports distributed deployments");
            log.info("  • Production ready");
        }
        log.info("==========================================");
    }
    
    public boolean isRedisMode() {
        return storageMode == StorageMode.REDIS;
    }
    
    public boolean isInMemoryMode() {
        return storageMode == StorageMode.IN_MEMORY;
    }
    
    /**
     * Bean to make storage mode available for SpEL expressions
     */
    @Bean
    public String currentStorageMode() {
        return storageMode.value;
    }
    
    /**
     * Get the current storage mode value as string
     */
    public String getCurrentStorageMode() {
        return storageMode.value;
    }
}
