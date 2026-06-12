package com.sm.instagram.platform.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter service using fixed window algorithm
 */
@Service
@Slf4j
public class RateLimiterService {
    
    private final Map<String, RequestInfo> requestCounts = new ConcurrentHashMap<>();
    private static final int MAX_ENTRIES = 100000; // Prevent unlimited growth
    
    /**
     * Check if request is allowed based on rate limit
     * @return RateLimitResult containing allowed status and metadata
     */
    public RateLimitResult checkLimit(String key, int limit, int durationSeconds) {
        long now = Instant.now().getEpochSecond();
        
        // Prevent memory issues
        if (requestCounts.size() > MAX_ENTRIES) {
            log.warn("Rate limiter entries exceeded maximum ({}), forcing cleanup", MAX_ENTRIES);
            cleanupExpiredEntries();
        }
        
        RequestInfo info = requestCounts.compute(key, (k, v) -> {
            if (v == null || now - v.windowStart >= durationSeconds) {
                // New window - start with count 0
                log.debug("New rate limit window for key: {}", key);
                return new RequestInfo(now, new AtomicInteger(0));
            } else {
                // Same window - return existing info
                return v;
            }
        });
        
        // Increment and check if within limit
        int currentCount = info.count.incrementAndGet();
        boolean allowed = currentCount <= limit;
        int remaining = Math.max(0, limit - currentCount);
        long resetTime = info.windowStart + durationSeconds;
        
        log.debug("Rate limit check - Key: {}, Count: {}/{}, Allowed: {}", key, currentCount, limit, allowed);
        
        return new RateLimitResult(allowed, limit, remaining, resetTime);
    }
    
    /**
     * Legacy method for backward compatibility
     */
    public boolean isAllowed(String key, int limit, int durationSeconds) {
        return checkLimit(key, limit, durationSeconds).isAllowed();
    }
    
    /**
     * Clean up expired entries to prevent memory leaks
     * @return number of entries removed
     */
    public int cleanupExpiredEntries() {
        long now = Instant.now().getEpochSecond();
        int[] removed = {0};
        
        requestCounts.entrySet().removeIf(entry -> {
            RequestInfo info = entry.getValue();
            // Remove entries older than 1 hour
            boolean expired = (now - info.windowStart) > 3600;
            if (expired) removed[0]++;
            return expired;
        });
        
        return removed[0];
    }
    
    /**
     * Get current number of tracked entries (for monitoring)
     */
    public int getTrackedEntriesCount() {
        return requestCounts.size();
    }
    
    /**
     * Simple class to hold request count info
     */
    private static class RequestInfo {
        final long windowStart;
        final AtomicInteger count;
        
        RequestInfo(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
    
    /**
     * Result of rate limit check with metadata
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final int limit;
        private final int remaining;
        private final long resetTime;
        
        public RateLimitResult(boolean allowed, int limit, int remaining, long resetTime) {
            this.allowed = allowed;
            this.limit = limit;
            this.remaining = remaining;
            this.resetTime = resetTime;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public int getLimit() {
            return limit;
        }
        
        public int getRemaining() {
            return remaining;
        }
        
        public long getResetTime() {
            return resetTime;
        }
    }
}
