package com.sm.instagram.platform.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for efficiently tracking rate limit metrics without expensive operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rate-limit.metrics.optimized", havingValue = "true", matchIfMissing = true)
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(org.springframework.data.redis.core.RedisTemplate.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.data.redis.core.RedisTemplate.class)
public class RateLimitMetricsService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final String METRICS_KEY = "rate_limit:metrics:active_keys";
    private static final String METRICS_APPROX_KEY = "rate_limit:metrics:approx_count";
    
    /**
     * Lua script to increment active keys counter when a new rate limit key is created
     */
    private static final String INCREMENT_METRICS_SCRIPT = """
        local metrics_key = KEYS[1]
        local ttl = tonumber(ARGV[1])
        
        -- Increment the counter
        local count = redis.call('INCR', metrics_key)
        
        -- Set expiration if this is the first increment
        if count == 1 then
            redis.call('EXPIRE', metrics_key, ttl)
        end
        
        return count
    """;
    
    private final RedisScript<Long> incrementScript = 
        RedisScript.of(INCREMENT_METRICS_SCRIPT, Long.class);
    
    /**
     * Called when a new rate limit key is created
     */
    public void onKeyCreated() {
        try {
            redisTemplate.execute(
                incrementScript,
                Collections.singletonList(METRICS_KEY),
                "3600" // 1 hour TTL
            );
        } catch (Exception e) {
            log.debug("Failed to update rate limit metrics", e);
        }
    }
    
    /**
     * Get approximate count of active rate limit keys
     * This is much more efficient than scanning all keys
     */
    public int getApproximateActiveKeys() {
        try {
            String count = redisTemplate.opsForValue().get(METRICS_KEY);
            return count != null ? Integer.parseInt(count) : 0;
        } catch (Exception e) {
            log.debug("Failed to get approximate active keys", e);
            return -1;
        }
    }
    
    /**
     * Periodically update the approximate count with actual count
     * This should be called infrequently (e.g., once per hour)
     */
    public void updateApproximateCount(int actualCount) {
        try {
            redisTemplate.opsForValue().set(
                METRICS_APPROX_KEY, 
                String.valueOf(actualCount),
                1, 
                TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.debug("Failed to update approximate count", e);
        }
    }
    
    /**
     * Get cached approximate count from last full scan
     */
    public int getCachedApproximateCount() {
        try {
            String count = redisTemplate.opsForValue().get(METRICS_APPROX_KEY);
            return count != null ? Integer.parseInt(count) : -1;
        } catch (Exception e) {
            log.debug("Failed to get cached approximate count", e);
            return -1;
        }
    }
}
