package com.sm.instagram.platform.common.security;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for GeoLocation services.
 * Supports multiple implementations: Redis, InMemory, Firestore (deprecated).
 * 
 * This abstraction allows switching between different caching strategies
 * based on deployment environment and compliance requirements.
 */
public interface GeoLocationService {
    
    /**
     * Get location information asynchronously for an IP address.
     * 
     * @param ip IP address to lookup
     * @return CompletableFuture with GeoLocation data
     */
    CompletableFuture<GeoLocation> getLocationAsync(String ip);
    
    /**
     * Get location information synchronously with timeout.
     * 
     * @param ip IP address to lookup
     * @param timeoutMs Timeout in milliseconds
     * @return GeoLocation data or unknown location on timeout
     */
    GeoLocation getLocation(String ip, long timeoutMs);
    
    /**
     * Check for impossible travel between two IPs.
     * 
     * @param userId User ID for travel pattern tracking (nullable)
     * @param fromIp Original IP address
     * @param toIp New IP address
     * @param minutesElapsed Time elapsed between IP changes
     * @return CompletableFuture<Boolean> true if travel is impossible
     */
    CompletableFuture<Boolean> checkImpossibleTravel(
            Long userId, 
            String fromIp, 
            String toIp, 
            long minutesElapsed);
    
    /**
     * Get service metrics for monitoring.
     * 
     * @return Map of metric name to value
     */
    Map<String, Object> getMetrics();
    
    /**
     * Force update of GeoIP database (if applicable).
     * Not all implementations may support this.
     */
    default void updateGeoIpDatabase() {
        // Optional operation - implementations may override
    }
    
    /**
     * Clean expired cache entries (if applicable).
     * Not all implementations may support this.
     */
    default void cleanExpiredCache() {
        // Optional operation - implementations may override
    }
}
