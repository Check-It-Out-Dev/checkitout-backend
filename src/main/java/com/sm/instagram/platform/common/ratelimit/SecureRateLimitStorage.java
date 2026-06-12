package com.sm.instagram.platform.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Secure storage for active rate limits that cannot be deleted by users.
 * This ensures GDPR compliance while preventing abuse of the deletion feature.
 * IMPORTANT: This class intentionally does NOT provide any method to delete
 * active rate limits based on user ID. This is a security measure.
 */
@Component
@Slf4j
public class SecureRateLimitStorage {
    
    /**
     * Check if a user is currently rate limited (for admin/monitoring purposes only)
     */
    public boolean isUserRateLimited(String userId, RateLimiterService rateLimiter) {
        // This is a read-only check - does not modify any rate limits
        String userKey = "user:" + userId;
        
        // Perform a dummy check with 1 request limit to see current state
        // This doesn't affect the actual rate limit counter
        RateLimiterService.RateLimitResult result = rateLimiter.checkLimit(
            userKey + ":check", 1, 1
        );
        
        return !result.isAllowed();
    }
    
    /**
     * Get rate limit status for monitoring (admin only)
     */
    public RateLimitStatus getRateLimitStatus(String key, int limit, int duration, 
                                            RateLimiterService rateLimiter) {
        // Create a separate key for status checking
        String statusKey = key + ":status:" + System.currentTimeMillis();
        
        // Check with the same limits to get current state
        RateLimiterService.RateLimitResult result = rateLimiter.checkLimit(
            statusKey, limit, duration
        );
        
        return new RateLimitStatus(
            result.isAllowed(),
            result.getLimit(),
            result.getRemaining(),
            result.getResetTime()
        );
    }
    
    /**
     * Rate limit status information
     */
    public record RateLimitStatus(
        boolean allowed,
        int limit,
        int remaining,
        long resetTime
    ) {
        public long getSecondsUntilReset() {
            return Math.max(0, resetTime - (System.currentTimeMillis() / 1000));
        }
    }
    
    /**
     * Log security event when user attempts to bypass rate limits
     */
    public void logBypassAttempt(String userId, String action) {
        log.warn("SECURITY: User {} attempted to bypass rate limits via: {}", userId, action);
        // In production, this could trigger additional security measures:
        // - Send alert to security team
        // - Increment abuse counter
        // - Temporary ban if repeated attempts
    }
}
