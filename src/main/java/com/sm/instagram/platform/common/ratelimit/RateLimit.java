package com.sm.instagram.platform.common.ratelimit;

import java.lang.annotation.*;

/**
 * Annotation to apply rate limiting to controller methods or classes.
 * Method-level annotations take precedence over class-level annotations.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    
    /**
     * Number of requests allowed within the duration.
     * Default is 0, which means use the value from the profile.
     */
    int value() default 0;
    
    /**
     * Duration in seconds for the rate limit window.
     * Default is 60 seconds (1 minute).
     */
    int duration() default 60;
    
    /**
     * Pre-defined rate limit profile to use.
     * Default is CUSTOM, which uses the value and duration fields.
     */
    RateLimitProfile profile() default RateLimitProfile.CUSTOM;
    
    /**
     * Type of key to use for rate limiting.
     * Default is USER_OR_IP.
     */
    RateLimitKeyType keyType() default RateLimitKeyType.USER_OR_IP;
    
    /**
     * Whether to enable rate limiting.
     * Default is true.
     */
    boolean enabled() default true;
    
    /**
     * Custom error message to return when rate limit is exceeded.
     * If empty, a default message will be used.
     */
    String errorMessage() default "Rate limit exceeded. Please try again later.";
    
    /**
     * Whether to skip rate limiting for admin users.
     * Default is true.
     */
    boolean skipForAdmin() default true;

    /**
     * Multiplier for rate limits when user has COMPANY role.
     * Example: 3.0 means companies get 3x the normal limit.
     * Default is 1.0 (no bonus - same limits as regular users).
     *
     * This provides higher throughput for business operations like:
     * - Campaign creation with multiple photos
     * - Bulk product uploads
     * - Managing multiple collaborations
     *
     * Unlike skipForCompany (which bypasses entirely), this maintains
     * rate limiting for security while allowing legitimate business use.
     */
    double companyLimitMultiplier() default 1.0;

    /**
     * Multiplier for rate limits when user has INFLUENCER role.
     * Example: 2.0 means influencers get 2x the normal limit.
     * Default is 1.0 (no bonus).
     */
    double influencerLimitMultiplier() default 1.0;

    /**
     * Custom key for rate limiting (used when keyType is CUSTOM).
     * Can use SpEL expressions.
     */
    String customKey() default "";
}
