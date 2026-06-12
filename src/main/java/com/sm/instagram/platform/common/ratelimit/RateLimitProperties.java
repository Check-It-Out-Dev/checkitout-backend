package com.sm.instagram.platform.common.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for rate limiting.
 *
 * SECURITY: Uses @Getter only on nested classes (not @Data) to prevent runtime
 * modification of security-critical configuration values after application startup.
 * Spring Boot's @ConfigurationProperties requires setters during binding, so we use
 * @Setter on the outer class only, which is called during startup.
 */
@Getter
@Setter  // Required for Spring Boot property binding during startup
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /**
     * Enable or disable rate limiting globally
     */
    private boolean enabled = true;

    /**
     * Algorithm type: fixed-window, sliding-window
     * Note: Storage type is controlled by storage.mode property
     */
    private AlgorithmType algorithm = AlgorithmType.FIXED_WINDOW;

    /**
     * Maximum number of entries to track (in-memory only)
     */
    private int maxEntries = 100_000;

    /**
     * Cleanup interval in milliseconds
     */
    private long cleanupInterval = 300_000; // 5 minutes

    /**
     * Default rate limits by endpoint pattern
     */
    private Map<String, EndpointLimit> endpoints = new HashMap<>();

    /**
     * Global default limits
     */
    private DefaultLimits defaults = new DefaultLimits();

    /**
     * Redis-specific configuration
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * File upload-specific rate limits
     */
    private Upload upload = new Upload();

    /**
     * New user grace period configuration.
     * New users get relaxed rate limits during their first hours on the platform.
     */
    private GracePeriod gracePeriod = new GracePeriod();

    /**
     * Profile-specific rate limit configurations.
     * Allows YAML override of enum defaults for testability.
     * Each profile can be individually configured via application.yml or system properties.
     */
    private Profiles profiles = new Profiles();

    public enum AlgorithmType {
        FIXED_WINDOW,
        SLIDING_WINDOW
    }

    @Getter
    @Setter  // Required for Spring Boot property binding
    public static class EndpointLimit {
        private int requests = 100;
        private int duration = 3600; // seconds
        private boolean perUser = true; // true = per user, false = per IP
    }

    @Getter
    @Setter  // Required for Spring Boot property binding
    public static class DefaultLimits {
        private int requests = 100;
        private int duration = 3600; // 1 hour
        private int authRequests = 5;
        private int authDuration = 300; // 5 minutes
        private int uploadRequests = 10;
        private int uploadDuration = 3600; // 1 hour
    }

    @Getter
    @Setter  // Required for Spring Boot property binding
    public static class RedisConfig {
        private String keyPrefix = "rate_limit:";
        private boolean enableFallback = true; // Fallback to in-memory if Redis fails
        private int connectionTimeout = 2000; // milliseconds
    }

    @Getter
    @Setter  // Required for Spring Boot property binding
    public static class Upload {
        /**
         * Maximum uploads per user per hour.
         * Increased from 10 to 30 to support legitimate workflows like:
         * - Campaign creation with multiple photos
         * - Batch product uploads for businesses
         */
        private int perHour = 30;

        /**
         * Maximum uploads per user per day.
         * Increased from 50 to 100 to support legitimate business use cases.
         */
        private int perDay = 100;

        private int globalPerMinute = 100;
        private long maxUserStorage = 1073741824L; // 1GB in bytes
        private long maxFileSize = 5242880L; // 5MB in bytes
    }

    @Getter
    @Setter  // Required for Spring Boot property binding
    public static class GracePeriod {
        /**
         * Whether to enable grace period for new users.
         */
        private boolean enabled = true;

        /**
         * Duration of the grace period in hours.
         * Default is 24 hours (1 day).
         */
        private int durationHours = 24;

        /**
         * Multiplier for rate limits during grace period.
         * Default is 2x (double the normal limits).
         */
        private double limitMultiplier = 2.0;
    }

    /**
     * Profile configurations that allow YAML override of enum defaults.
     * Defaults match RateLimitProfile enum values for backward compatibility.
     */
    @Getter
    @Setter  // Required for Spring Boot property binding
    public static class Profiles {
        private ProfileConfig standard = new ProfileConfig(60, 60, 300);
        private ProfileConfig strict = new ProfileConfig(10, 60, 300);
        private ProfileConfig relaxed = new ProfileConfig(120, 60, 300);
        private ProfileConfig high = new ProfileConfig(300, 60, 300);
        private ProfileConfig auth = new ProfileConfig(50, 60, 300);
        private ProfileConfig adminAuth = new ProfileConfig(3, 900, 300);
        private ProfileConfig companyAuth = new ProfileConfig(5, 300, 180);
        private ProfileConfig influencerAuth = new ProfileConfig(10, 300, 120);
        private ProfileConfig unknownAuth = new ProfileConfig(5, 300, 300);
    }

    /**
     * Configuration for a single rate limit profile.
     * Includes requests per window, window duration, and block duration.
     */
    @Getter
    @Setter  // Required for Spring Boot property binding
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProfileConfig {
        /**
         * Maximum number of requests allowed in the window.
         */
        private int requests;

        /**
         * Window duration in seconds.
         */
        private int windowSeconds;

        /**
         * Block duration in seconds when limit is exceeded.
         */
        private int blockDurationSeconds;
    }
}
