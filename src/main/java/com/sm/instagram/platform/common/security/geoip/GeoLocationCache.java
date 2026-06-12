package com.sm.instagram.platform.common.security.geoip;

import com.sm.instagram.platform.common.security.GeoLocation;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for GeoLocation caching.
 * Implementations can use Redis, in-memory, or any other storage.
 * 
 * This abstraction allows the business logic to be independent of storage mechanism.
 */
public interface GeoLocationCache {
    
    /**
     * Get location from cache.
     * 
     * @param ip IP address
     * @return GeoLocation or null if not cached
     */
    GeoLocation get(String ip);
    
    /**
     * Get location from cache asynchronously.
     * 
     * @param ip IP address
     * @return CompletableFuture with GeoLocation or null
     */
    CompletableFuture<GeoLocation> getAsync(String ip);
    
    /**
     * Store location in cache.
     * 
     * @param ip IP address
     * @param location GeoLocation data
     */
    void put(String ip, GeoLocation location);
    
    /**
     * Store location in cache asynchronously.
     * 
     * @param ip IP address
     * @param location GeoLocation data
     * @return CompletableFuture for async operation
     */
    CompletableFuture<Void> putAsync(String ip, GeoLocation location);
    
    /**
     * Remove location from cache.
     * 
     * @param ip IP address
     */
    void evict(String ip);
    
    /**
     * Clear all cached locations.
     */
    void clear();
    
    /**
     * Store travel pattern event.
     * 
     * @param userId User ID
     * @param event Travel event data
     */
    void recordTravelPattern(Long userId, Map<String, Object> event);
    
    /**
     * Get cache metrics.
     * 
     * @return Map with metrics (hits, misses, size, etc.)
     */
    Map<String, Object> getMetrics();
    
    /**
     * Check if cache is available and working.
     * 
     * @return true if cache is operational
     */
    boolean isAvailable();
    
    /**
     * Get cache type identifier.
     * 
     * @return "redis" or "in-memory"
     */
    String getCacheType();
    
    /**
     * Try to acquire a distributed lock (for Redis) or local lock (for in-memory).
     * 
     * @param lockKey Lock key
     * @param ttlSeconds Time to live in seconds
     * @return true if lock acquired
     */
    boolean tryLock(String lockKey, int ttlSeconds);
    
    /**
     * Release a lock.
     * 
     * @param lockKey Lock key
     */
    void releaseLock(String lockKey);
}
