package com.sm.instagram.platform.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for base RateLimiterService (in-memory implementation)
 */
class InMemoryRateLimiterUnitTest {

    private RateLimiterService rateLimiterService;
    
    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }
    
    @Test
    void testBasicRateLimiting() {
        String testKey = "test_key_" + System.currentTimeMillis();
        int limit = 5;
        int duration = 60;
        
        System.out.println("Testing in-memory rate limiter with key: " + testKey);
        
        // Should allow first 5 requests
        for (int i = 0; i < limit; i++) {
            RateLimiterService.RateLimitResult result = 
                rateLimiterService.checkLimit(testKey, limit, duration);
            
            System.out.println("Request " + (i + 1) + 
                              ": allowed=" + result.isAllowed() + 
                              ", remaining=" + result.getRemaining() + 
                              ", expected=" + (limit - i - 1));
            
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
            assertEquals(limit - i - 1, result.getRemaining(), 
                        "Remaining count mismatch at request " + (i + 1));
        }
        
        // 6th request should be denied
        RateLimiterService.RateLimitResult result = 
            rateLimiterService.checkLimit(testKey, limit, duration);
        assertFalse(result.isAllowed(), "6th request should be denied");
        assertEquals(0, result.getRemaining());
    }
}
