package com.sm.instagram.platform.common.security.geoip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.utils.HashingUtil;
import com.sm.instagram.platform.common.security.GeoLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis implementation of GeoLocationCache.
 * 
 * Responsibilities:
 * - Cache GeoLocation data in Redis with TTL
 * - Track cache metrics
 * - Store travel patterns
 * - Handle distributed locking
 * 
 * Single Responsibility: Redis cache operations only
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.mode", havingValue = "redis")
@RequiredArgsConstructor
public class RedisGeoLocationCache implements GeoLocationCache {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    
    // Cache key prefixes
    private static final String GEO_PREFIX = "geo:ip:";
    private static final String TRAVEL_PREFIX = "geo:travel:";
    private static final String METRICS_PREFIX = "geo:metrics:";
    private static final String LOCK_PREFIX = "geo:lock:";
    
    @Value("${geoip.cache.ttl-days:7}")
    private int cacheTtlDays;
    
    // Metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalLookups = new AtomicLong(0);
    
    @Override
    public GeoLocation get(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        
        totalLookups.incrementAndGet();
        // Hash IP address for Redis key
        String key = HashingUtil.generateRedisKey("geo", ip);
        
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                cacheHits.incrementAndGet();
                GeoLocation location = objectMapper.readValue(cached, GeoLocation.class);
                log.debug("Redis cache hit for geo location");  // No PII in logs
                return location;
            } else {
                cacheMisses.incrementAndGet();
                log.debug("Redis cache miss for geo location");  // No PII in logs
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to get from Redis cache", e);
            cacheMisses.incrementAndGet();
            return null;
        }
    }
    
    @Override
    public CompletableFuture<GeoLocation> getAsync(String ip) {
        return CompletableFuture.supplyAsync(() -> get(ip), executorService);
    }
    
    @Override
    public void put(String ip, GeoLocation location) {
        if (ip == null || location == null) {
            return;
        }
        
        // Hash IP address for Redis key
        String key = HashingUtil.generateRedisKey("geo", ip);
        
        try {
            String json = objectMapper.writeValueAsString(location);
            // GDPR: Auto-expire after TTL
            redisTemplate.opsForValue().set(key, json, cacheTtlDays, TimeUnit.DAYS);
            log.debug("Cached location with TTL: {} days", cacheTtlDays);  // No PII in logs
        } catch (Exception e) {
            log.error("Failed to cache location in Redis", e);
        }
    }
    
    @Override
    public CompletableFuture<Void> putAsync(String ip, GeoLocation location) {
        return CompletableFuture.runAsync(() -> put(ip, location), executorService);
    }
    
    @Override
    public void evict(String ip) {
        if (ip == null) {
            return;
        }
        
        // Hash IP address for Redis key
        String key = HashingUtil.generateRedisKey("geo", ip);
        redisTemplate.delete(key);
        log.debug("Evicted location from cache");  // No PII in logs
    }
    
    @Override
    public void clear() {
        try {
            // Clear all geo:ip:* keys using pattern delete
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                // Use scan instead of keys for production safety
                var keys = connection.keyCommands().keys((GEO_PREFIX + "*").getBytes());
                if (keys != null && !keys.isEmpty()) {
                    connection.keyCommands().del(keys.toArray(new byte[0][]));
                }
                return null;
            });
            log.info("Cleared all GeoLocation cache entries");
        } catch (Exception e) {
            log.error("Failed to clear cache", e);
        }
    }
    
    @Override
    public void recordTravelPattern(Long userId, Map<String, Object> event) {
        if (userId == null || event == null) {
            return;
        }
        
        String key = TRAVEL_PREFIX + userId + ":" + LocalDate.now();
        
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, 30, TimeUnit.DAYS);  // GDPR: 30 days retention
            
            // Update daily metrics if it was impossible travel
            Boolean impossible = (Boolean) event.get("impossible");
            if (Boolean.TRUE.equals(impossible)) {
                String metricsKey = METRICS_PREFIX + "impossible:" + LocalDate.now();
                redisTemplate.opsForValue().increment(metricsKey);
                redisTemplate.expire(metricsKey, 90, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("Failed to record travel pattern", e);
        }
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = totalLookups.get();
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("cacheHits", hits);
        metrics.put("cacheMisses", misses);
        metrics.put("totalLookups", total);
        metrics.put("hitRate", total > 0 ? (double) hits / total : 0.0);
        metrics.put("cacheType", getCacheType());
        metrics.put("ttlDays", cacheTtlDays);
        
        // Get Redis-specific metrics
        try {
            Long dbSize = redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.serverCommands().dbSize());
            metrics.put("redisKeys", dbSize != null ? dbSize : 0);
            
            // Count geo keys
            Long geoKeys = redisTemplate.execute((RedisCallback<Long>) connection -> {
                var keys = connection.keyCommands().keys((GEO_PREFIX + "*").getBytes());
                return keys != null ? (long) keys.size() : 0L;
            });
            metrics.put("geoLocationKeys", geoKeys != null ? geoKeys : 0);
        } catch (Exception e) {
            log.debug("Could not get Redis metrics", e);
        }
        
        return metrics;
    }
    
    @Override
    public boolean isAvailable() {
        try {
            redisTemplate.execute((RedisCallback<String>) connection -> {
                connection.ping();
                return "PONG";
            });
            return true;
        } catch (Exception e) {
            log.warn("Redis is not available: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getCacheType() {
        return "redis";
    }
    
    @Override
    public boolean tryLock(String lockKey, int ttlSeconds) {
        String fullKey = LOCK_PREFIX + lockKey;
        String lockValue = Thread.currentThread().getName() + "_" + System.currentTimeMillis();
        
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(fullKey, lockValue, ttlSeconds, TimeUnit.SECONDS);
        
        return Boolean.TRUE.equals(acquired);
    }
    
    @Override
    public void releaseLock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        redisTemplate.delete(fullKey);
    }
    
    /**
     * GDPR compliant IP masking.
     */
    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        
        if (ip.contains(".")) {
            // IPv4: mask last octet
            int lastDot = ip.lastIndexOf('.');
            return lastDot > 0 ? ip.substring(0, lastDot) + ".xxx" : "masked";
        } else if (ip.contains(":")) {
            // IPv6: mask last 4 segments
            int lastColon = ip.lastIndexOf(':');
            return lastColon > 0 ? ip.substring(0, lastColon) + ":xxxx" : "masked";
        }
        return "masked";
    }
}
