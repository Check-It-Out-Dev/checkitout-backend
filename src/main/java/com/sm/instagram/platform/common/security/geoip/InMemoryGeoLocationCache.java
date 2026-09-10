package com.sm.instagram.platform.common.security.geoip;

import com.sm.instagram.platform.common.security.GeoLocation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of GeoLocationCache using Caffeine.
 * 
 * Responsibilities:
 * - Cache GeoLocation data in memory with expiration
 * - Track cache metrics
 * - Store travel patterns in memory
 * - Handle local locking
 * 
 * Single Responsibility: In-memory cache operations only
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.mode", havingValue = "in-memory")
public class InMemoryGeoLocationCache implements GeoLocationCache {
    
    private final Map<String, CachedLocation> locationCache = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> travelPatterns = new ConcurrentHashMap<>();
    /**
     * Lock key -> the nanoTime at which the holder's claim lapses.
     *
     * <p>Not a {@link java.util.concurrent.locks.ReentrantLock}, which cannot express this. A
     * ReentrantLock belongs to the thread that took it, so only that thread may unlock, and the TTL
     * safety net that used to sit here scheduled its unlock on an executor thread where
     * {@code isHeldByCurrentThread()} is false by construction -- it could never fire once. Worse,
     * the TTL was being passed to {@code tryLock(timeout, unit)} as an acquisition timeout, so a
     * second instance finding the lock taken waited ten minutes for it instead of taking the
     * caller's "another instance is updating" branch.
     *
     * <p>An expiry map is what the Redis implementation of this same interface already does --
     * {@code SET key value NX EX ttl} -- so the two now behave alike: acquire without waiting, lapse
     * on their own if a holder dies, and release from any thread.
     */
    private final Map<String, Long> lockExpiry = new ConcurrentHashMap<>();
    
    @Data
    @AllArgsConstructor
    private static class CachedLocation {
        private GeoLocation location;
        private long timestamp;
        private long lastAccessed;
        
        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
        
        void touch() {
            lastAccessed = System.currentTimeMillis();
        }
    }
    
    @Value("${geoip.cache.ttl-days:7}")
    private int cacheTtlDays;
    
    @Value("${geoip.cache.max-entries:10000}")
    private int maxEntries;
    
    // Metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalLookups = new AtomicLong(0);
    private final AtomicLong impossibleTravelCount = new AtomicLong(0);
    
    @PostConstruct
    public void init() {
        log.info("Initialized in-memory GeoLocation cache with max {} entries and {} days TTL", 
            maxEntries, cacheTtlDays);
    }
    
    @Override
    public GeoLocation get(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        
        totalLookups.incrementAndGet();
        String key = normalizeKey(ip);
        
        CachedLocation cached = locationCache.get(key);
        if (cached != null) {
            long ttlMillis = TimeUnit.DAYS.toMillis(cacheTtlDays);
            if (!cached.isExpired(ttlMillis)) {
                cached.touch();
                cacheHits.incrementAndGet();
                log.debug("In-memory cache hit for IP: {}", maskIp(ip));
                return cached.getLocation();
            } else {
                // Remove expired entry
                locationCache.remove(key);
            }
        }
        
        cacheMisses.incrementAndGet();
        log.debug("In-memory cache miss for IP: {}", maskIp(ip));
        return null;
    }
    
    @Override
    public CompletableFuture<GeoLocation> getAsync(String ip) {
        return CompletableFuture.completedFuture(get(ip));
    }
    
    @Override
    public void put(String ip, GeoLocation location) {
        if (ip == null || location == null) {
            return;
        }
        
        String key = normalizeKey(ip);
        
        // Check cache size and evict if necessary
        if (locationCache.size() >= maxEntries) {
            evictOldestEntry();
        }
        
        CachedLocation cached = new CachedLocation(
            location,
            System.currentTimeMillis(),
            System.currentTimeMillis()
        );
        
        locationCache.put(key, cached);
        log.debug("Cached location for IP: {} in memory (cache size: {})", maskIp(ip), locationCache.size());
    }
    
    @Override
    public CompletableFuture<Void> putAsync(String ip, GeoLocation location) {
        put(ip, location);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void evict(String ip) {
        if (ip == null) {
            return;
        }
        
        String key = normalizeKey(ip);
        locationCache.remove(key);
        log.debug("Evicted location for IP: {}", maskIp(ip));
    }
    
    @Override
    public void clear() {
        locationCache.clear();
        travelPatterns.clear();
        log.info("Cleared all in-memory cache entries");
    }
    
    /**
     * Evict oldest or least recently used entry.
     */
    private void evictOldestEntry() {
        String oldestKey = null;
        long oldestTime = System.currentTimeMillis();
        
        for (Map.Entry<String, CachedLocation> entry : locationCache.entrySet()) {
            CachedLocation cached = entry.getValue();
            if (cached.getLastAccessed() < oldestTime) {
                oldestTime = cached.getLastAccessed();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            locationCache.remove(oldestKey);
            log.debug("Evicted oldest cache entry to maintain size limit");
        }
    }
    
    @Override
    public void recordTravelPattern(Long userId, Map<String, Object> event) {
        if (userId == null || event == null) {
            return;
        }
        
        String key = userId + ":" + LocalDate.now();
        
        travelPatterns.computeIfAbsent(key, k -> 
            Collections.synchronizedList(new ArrayList<>())
        ).add(event);
        
        // Update metrics if it was impossible travel
        Boolean impossible = (Boolean) event.get("impossible");
        if (Boolean.TRUE.equals(impossible)) {
            impossibleTravelCount.incrementAndGet();
        }
        
        // Keep only last 1000 patterns per user-day to prevent memory issues
        List<Map<String, Object>> patterns = travelPatterns.get(key);
        if (patterns.size() > 1000) {
            patterns.subList(0, patterns.size() - 1000).clear();
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
        metrics.put("maxEntries", maxEntries);
        metrics.put("ttlDays", cacheTtlDays);
        
        // In-memory specific metrics
        metrics.put("currentSize", locationCache.size());
        metrics.put("travelPatternCount", travelPatterns.size());
        metrics.put("impossibleTravelCount", impossibleTravelCount.get());
        
        return metrics;
    }
    
    @Override
    public boolean isAvailable() {
        // In-memory cache is always available if the service is running
        return true;
    }
    
    @Override
    public String getCacheType() {
        return "in-memory";
    }
    
    @Override
    public boolean tryLock(String lockKey, int ttlSeconds) {
        long now = System.nanoTime();
        long expiresAt = now + TimeUnit.SECONDS.toNanos(Math.max(ttlSeconds, 0));
        // compute() holds the bin lock, so read-decide-write is atomic against another thread doing
        // the same thing. A claim whose deadline has passed is treated as free, which is what makes
        // this survive a holder that died without releasing.
        boolean[] acquired = { false };
        lockExpiry.compute(lockKey, (key, current) -> {
            if (current == null || current - now <= 0) {
                acquired[0] = true;
                return expiresAt;
            }
            return current;
        });
        return acquired[0];
    }

    @Override
    public void releaseLock(String lockKey) {
        // Any thread may release, exactly as deleting the Redis key does. The caller releases in a
        // finally block, so this is the path that actually runs; the TTL is only the net under it.
        lockExpiry.remove(lockKey);
    }
    
    /**
     * Clean up expired cache entries periodically.
     * Runs every hour.
     */
    @Scheduled(fixedDelay = 3600000)  // 1 hour
    public void cleanupExpiredEntries() {
        long ttlMillis = TimeUnit.DAYS.toMillis(cacheTtlDays);
        int removed = 0;
        
        Iterator<Map.Entry<String, CachedLocation>> iter = locationCache.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, CachedLocation> entry = iter.next();
            if (entry.getValue().isExpired(ttlMillis)) {
                iter.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleaned {} expired cache entries", removed);
        }
    }
    
    /**
     * Clean up old travel patterns periodically.
     * Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldPatterns() {
        LocalDate cutoffDate = LocalDate.now().minusDays(30);
        
        travelPatterns.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            // Extract date from key format: "userId:date"
            String[] parts = key.split(":");
            if (parts.length >= 2) {
                try {
                    LocalDate date = LocalDate.parse(parts[1]);
                    return date.isBefore(cutoffDate);
                } catch (Exception e) {
                    // Invalid format, remove it
                    return true;
                }
            }
            return true;
        });
        
        log.info("Cleaned up travel patterns older than {}", cutoffDate);
    }
    
    /**
     * Normalize cache key.
     */
    private String normalizeKey(String ip) {
        return ip.replace(".", "_").replace(":", "_");
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
