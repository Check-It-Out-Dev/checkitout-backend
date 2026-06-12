package com.sm.instagram.platform.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Dynamic rate limiting annotation that supports different limits based on user tier
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DynamicRateLimit {
    
    /**
     * Base rate limit for anonymous users
     */
    int baseValue() default 10;
    
    /**
     * Duration in seconds
     */
    int duration() default 60;
    
    /**
     * Rate limit multipliers for different tiers
     */
    TierLimit[] tiers() default {};
    
    /**
     * SpEL expression to determine user tier
     * Example: "@userService.getUserTier(authentication.name)"
     */
    String tierExpression() default "";
    
    @interface TierLimit {
        String tier();
        int multiplier() default 1;
        int absoluteLimit() default -1; // If set, overrides multiplier
    }
}

// Example usage:
/*
@GetMapping("/api/data")
@DynamicRateLimit(
    baseValue = 10,
    duration = 60,
    tierExpression = "@userService.getUserTier(authentication.name)",
    tiers = {
        @TierLimit(tier = "FREE", multiplier = 1),      // 10 requests/min
        @TierLimit(tier = "BASIC", multiplier = 5),     // 50 requests/min
        @TierLimit(tier = "PRO", multiplier = 10),      // 100 requests/min
        @TierLimit(tier = "ENTERPRISE", absoluteLimit = 1000)  // 1000 requests/min
    }
)
public ResponseEntity<?> getData() {
    // Implementation
}
*/
