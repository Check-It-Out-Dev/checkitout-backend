package com.sm.instagram.platform.e2e.hooks;

import io.cucumber.java.Before;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

/**
 * Cucumber hooks for rate limiting E2E tests.
 * Clears Redis rate limit keys before each @rate-limiting scenario
 * to ensure test isolation.
 */
@Slf4j
public class RateLimitingHooks {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Clears all rate limit keys in Redis before each rate-limiting scenario.
     * This ensures each scenario starts with a fresh rate limit bucket.
     *
     * <p>Rate limit keys follow the pattern: rate_limit:* and rate_limit_ip:*
     */
    @Before("@rate-limiting")
    public void clearRateLimitKeys() {
        try {
            // Find and delete all rate limit keys
            Set<String> rateLimitKeys = redisTemplate.keys("rate_limit*");
            if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
                Long deleted = redisTemplate.delete(rateLimitKeys);
                log.info("[E2E] Cleared {} rate limit keys from Redis before scenario", deleted);
            } else {
                log.info("[E2E] No rate limit keys found in Redis to clear");
            }
        } catch (Exception e) {
            log.warn("[E2E] Failed to clear rate limit keys from Redis: {}", e.getMessage());
            // Don't fail the test - rate limiting might still work with fresh user keys
        }
    }
}
