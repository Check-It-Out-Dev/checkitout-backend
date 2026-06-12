package com.sm.instagram.platform.common.security.geoip;

import com.maxmind.geoip2.model.CityResponse;
import com.sm.instagram.platform.common.security.GeoLocation;
import com.sm.instagram.platform.common.security.GeoLocationService;
import com.sm.instagram.platform.common.security.GeoIpStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Facade service that orchestrates GeoLocation operations.
 * This is the main entry point for all GeoLocation functionality.
 * 
 * It delegates to specialized services:
 * - MaxMindDatabaseService for database operations
 * - GeoLocationCache for caching (Redis or in-memory based on configuration)
 * - TravelPatternService for travel analysis
 * - GeoIpStorageService for Firebase storage (optional)
 * 
 * This design allows for:
 * - Single Responsibility Principle compliance
 * - Easy testing of individual components
 * - Storage-agnostic business logic
 * - Quick hotfixes to specific functionality
 */
@Slf4j
@Service("geoLocationService")
@RequiredArgsConstructor
public class GeoLocationFacade implements GeoLocationService {
    
    private final MaxMindDatabaseService databaseService;
    private final GeoLocationCache cache;
    private final TravelPatternService travelPatternService;
    
    @Autowired(required = false)
    private GeoIpStorageService storageService;
    
    /**
     * Get location for an IP address.
     * First checks cache, then looks up in MaxMind database if needed.
     */
    @Override
    public GeoLocation getLocation(String ip, long timeoutMs) {
        try {
            return getLocationAsync(ip).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("GeoIP lookup timed out for IP: {}", maskIp(ip));
            return GeoLocation.unknown(ip);
        } catch (Exception e) {
            log.error("GeoIP lookup failed for IP: {}", maskIp(ip), e);
            return GeoLocation.unknown(ip);
        }
    }
    
    /**
     * Get location asynchronously.
     */
    @Override
    public CompletableFuture<GeoLocation> getLocationAsync(String ip) {
        if (ip == null || ip.isEmpty()) {
            return CompletableFuture.completedFuture(GeoLocation.unknown("unknown"));
        }
        
        // Try cache first
        return cache.getAsync(ip).thenCompose(cached -> {
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            
            // Cache miss - lookup in database
            return lookupAndCache(ip);
        });
    }
    
    /**
     * Check for impossible travel between two IPs.
     */
    @Override
    public CompletableFuture<Boolean> checkImpossibleTravel(
            Long userId, 
            String fromIp, 
            String toIp, 
            long minutesElapsed) {
        
        if (fromIp.equals(toIp)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return getLocationAsync(fromIp)
            .thenCombine(getLocationAsync(toIp), (from, to) -> {
                // Use TravelPatternService for analysis
                boolean impossible = travelPatternService.isImpossibleTravel(from, to, minutesElapsed);
                
                // Record the travel pattern
                if (userId != null) {
                    Map<String, Object> event = travelPatternService.createTravelEvent(
                        userId, from, to, minutesElapsed
                    );
                    cache.recordTravelPattern(userId, event);
                }
                
                return impossible;
            })
            .exceptionally(e -> {
                log.error("Failed to check impossible travel", e);
                return false;  // Fail open
            });
    }
    
    /**
     * Update the GeoIP database.
     */
    @Override
    @Scheduled(cron = "${maxmind.update.cron:0 0 2 ? * WED}")
    public void updateGeoIpDatabase() {
        // Check if we need to download from Firebase first
        if (storageService != null && !databaseService.isDatabaseReady()) {
            try {
                log.info("Checking Firebase Storage for GeoIP database...");
                if (storageService.hasDatabaseInStorage()) {
                    downloadFromStorage();
                    return;
                }
            } catch (Exception e) {
                log.warn("Failed to check Firebase Storage: {}", e.getMessage());
            }
        }
        
        // Use distributed lock for database updates
        String lockKey = "database_update";
        if (cache.tryLock(lockKey, 600)) {  // 10 minutes lock
            try {
                databaseService.updateDatabase();
                
                // Upload to Firebase if available
                if (storageService != null) {
                    uploadToStorage();
                }
            } finally {
                cache.releaseLock(lockKey);
            }
        } else {
            log.info("Another instance is updating the database");
        }
    }
    
    /**
     * Clean expired cache entries.
     */
    @Override
    @Scheduled(cron = "${geoip.cleanup.cron:0 0 4 * * SUN}")
    public void cleanExpiredCache() {
        log.info("Running cache cleanup");
        
        // For Redis, TTL handles expiration automatically
        // For in-memory, Caffeine handles expiration
        // This is mainly for metrics tracking
        
        Map<String, Object> metrics = getMetrics();
        log.info("Cache cleanup complete. Current metrics: {}", metrics);
    }
    
    /**
     * Get service metrics.
     */
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = cache.getMetrics();
        
        // Add database info
        MaxMindDatabaseService.DatabaseInfo dbInfo = databaseService.getDatabaseInfo();
        metrics.put("databaseReady", dbInfo.isReady());
        metrics.put("databasePath", dbInfo.getPath());
        metrics.put("databaseSize", dbInfo.getSizeBytes());
        
        // Add storage info if available
        if (storageService != null) {
            metrics.put("storageEnabled", true);
            metrics.put("storageType", "Firebase");
        } else {
            metrics.put("storageEnabled", false);
        }
        
        // Add cache availability
        metrics.put("cacheAvailable", cache.isAvailable());
        
        return metrics;
    }
    
    /**
     * Lookup IP in MaxMind database and cache the result.
     */
    private CompletableFuture<GeoLocation> lookupAndCache(String ip) {
        return databaseService.lookupCityAsync(ip).thenCompose(cityResponse -> {
            GeoLocation location = convertToGeoLocation(ip, cityResponse);
            
            if (location.isKnown()) {
                // Cache the result asynchronously
                cache.putAsync(ip, location);
            }
            
            return CompletableFuture.completedFuture(location);
        });
    }
    
    /**
     * Convert MaxMind CityResponse to GeoLocation.
     */
    private GeoLocation convertToGeoLocation(String ip, CityResponse response) {
        if (response == null) {
            return GeoLocation.unknown(ip);
        }
        
        GeoLocation.GeoLocationBuilder builder = GeoLocation.builder()
            .ip(ip)
            .country(response.getCountry().getName())
            .countryCode(response.getCountry().getIsoCode())
            .city(response.getCity().getName())
            .region(response.getMostSpecificSubdivision().getName())
            .latitude(response.getLocation().getLatitude())
            .longitude(response.getLocation().getLongitude())
            .postalCode(response.getPostal().getCode())
            .timezone(response.getLocation().getTimeZone());
        
        // Check for proxy/VPN
        if (response.getTraits() != null) {
            builder.isVpn(response.getTraits().isAnonymousVpn())
                   .isProxy(response.getTraits().isPublicProxy())
                   .isTor(response.getTraits().isTorExitNode())
                   .ispName(response.getTraits().getIsp());
        }
        
        return builder.build();
    }
    
    /**
     * Download database from Firebase Storage.
     */
    private void downloadFromStorage() {
        if (storageService == null) {
            return;
        }
        
        try {
            log.info("Downloading GeoIP database from Firebase Storage...");
            storageService.downloadDatabase()
                .thenAccept(path -> {
                    if (path != null) {
                        log.info("Database downloaded from Firebase Storage");
                        // Database service will reload automatically
                    }
                })
                .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to download from Firebase Storage", e);
        }
    }
    
    /**
     * Upload database to Firebase Storage.
     */
    private void uploadToStorage() {
        if (storageService == null) {
            return;
        }
        
        try {
            MaxMindDatabaseService.DatabaseInfo dbInfo = databaseService.getDatabaseInfo();
            if (dbInfo.isReady()) {
                storageService.uploadDatabase(java.nio.file.Paths.get(dbInfo.getPath()))
                    .thenAccept(result -> log.info("Database uploaded to Firebase Storage"))
                    .get(30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("Failed to upload to Firebase Storage", e);
        }
    }
    
    /**
     * GDPR compliant IP masking.
     */
    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        
        if (ip.contains(".")) {
            int lastDot = ip.lastIndexOf('.');
            return lastDot > 0 ? ip.substring(0, lastDot) + ".xxx" : "masked";
        } else if (ip.contains(":")) {
            int lastColon = ip.lastIndexOf(':');
            return lastColon > 0 ? ip.substring(0, lastColon) + ":xxxx" : "masked";
        }
        return "masked";
    }
}
