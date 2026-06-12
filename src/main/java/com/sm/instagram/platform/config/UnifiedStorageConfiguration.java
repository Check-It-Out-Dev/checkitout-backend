package com.sm.instagram.platform.config;

import com.sm.instagram.platform.common.security.geoip.GeoLocationCache;
import com.sm.instagram.platform.common.security.geoip.InMemoryGeoLocationCache;
import com.sm.instagram.platform.common.security.geoip.RedisGeoLocationCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Unified storage configuration that uses the single storage.mode property
 * to configure ALL storage/caching in the application.
 * 
 * This eliminates the need for multiple properties like:
 * - geoip.cache.type
 * - spring.cache.type
 * - rate-limit.storage
 * 
 * Everything is now controlled by: storage.mode = redis | in-memory
 */
@Slf4j
@Configuration("unifiedCacheStorageConfiguration")  // Renamed to avoid conflict with GCS configuration
@RequiredArgsConstructor
public class UnifiedStorageConfiguration {
    
    private final StorageModeConfiguration storageModeConfig;
    
    /**
     * Configure GeoLocation cache based on storage.mode.
     * This bean is selected automatically based on the storage.mode property.
     * 
     * Note: The actual implementations (RedisGeoLocationCache and InMemoryGeoLocationCache)
     * are already annotated with @ConditionalOnProperty to activate based on storage.mode.
     * This configuration just logs the selection for clarity.
     */
    @Bean
    @Primary
    public String geoLocationCacheType() {
        String mode = storageModeConfig.getCurrentStorageMode();
        
        log.info("==========================================");
        log.info("📍 GEOLOCATION CACHE CONFIGURATION");
        log.info("==========================================");
        log.info("Storage Mode: {}", mode);
        log.info("GeoLocation Cache: {}", mode.equals("redis") ? "RedisGeoLocationCache" : "InMemoryGeoLocationCache");
        
        if (storageModeConfig.isInMemoryMode()) {
            log.info("  • GeoLocation data stored in application memory");
            log.info("  • Limited by max-entries configuration");
            log.info("  • Data lost on restart");
        } else {
            log.info("  • GeoLocation data stored in Redis");
            log.info("  • Distributed across instances");
            log.info("  • Data persists with TTL");
        }
        
        log.info("==========================================");
        
        return mode + "-geolocation-cache";
    }
    
    /**
     * Log the complete storage configuration on startup.
     */
    @jakarta.annotation.PostConstruct
    public void logStorageConfiguration() {
        log.info("");
        log.info("╔════════════════════════════════════════╗");
        log.info("║     UNIFIED STORAGE CONFIGURATION      ║");
        log.info("╠════════════════════════════════════════╣");
        log.info("║ Single Property: storage.mode = {}    ║", 
            String.format("%-7s", storageModeConfig.getCurrentStorageMode()));
        log.info("╠════════════════════════════════════════╣");
        log.info("║ This controls:                         ║");
        log.info("║   ✓ User authentication cache          ║");
        log.info("║   ✓ Rate limiting storage              ║");
        log.info("║   ✓ Session management                 ║");
        log.info("║   ✓ GeoLocation cache                  ║");
        log.info("║   ✓ Application cache                  ║");
        log.info("║   ✓ Upload tracking                    ║");
        log.info("╠════════════════════════════════════════╣");
        
        if (storageModeConfig.isRedisMode()) {
            log.info("║ Mode: REDIS (Production Ready)         ║");
            log.info("║   • Distributed & Persistent           ║");
            log.info("║   • Supports clustering                ║");
            log.info("║   • GDPR compliant with TTL            ║");
        } else {
            log.info("║ Mode: IN-MEMORY (Development)          ║");
            log.info("║   • Local only                         ║");
            log.info("║   • Non-persistent                     ║");
            log.info("║   • Single instance only               ║");
        }
        
        log.info("╚════════════════════════════════════════╝");
        log.info("");
    }
}
