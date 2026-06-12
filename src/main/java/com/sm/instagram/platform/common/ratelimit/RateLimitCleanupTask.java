package com.sm.instagram.platform.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to clean up expired rate limit entries
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitCleanupTask {
    
    private final RateLimiterService rateLimiterService;
    
    @Scheduled(fixedDelay = 300000, initialDelay = 300000) // Every 5 minutes
    public void cleanup() {
        try {
            int removed = rateLimiterService.cleanupExpiredEntries();
            if (removed > 0) {
                log.info("Rate limit cleanup: removed {} expired entries", removed);
            }
        } catch (Exception e) {
            log.error("Error during rate limit cleanup", e);
        }
    }
}
