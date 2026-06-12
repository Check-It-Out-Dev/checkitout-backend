package com.sm.instagram.platform.common.ratelimit;

import lombok.Getter;

/**
 * Pre-defined rate limit profiles for common use cases
 */
@Getter
public enum RateLimitProfile {
    /**
     * Custom profile - use value and duration from annotation
     */
    CUSTOM(0, 0, 0, "custom"),
    
    /**
     * Authentication endpoints - 50 requests per minute
     * Increased from 20 to handle:
     * - Complex registration flows (3-4 API calls)
     * - Token exchanges, JWT refreshes, OAuth callbacks
     * - 2FA verification flows, session management
     * - Network retries in distributed auth systems
     * - Multiple tabs/windows during registration
     * - NAT/corporate users sharing IP-based buckets
     */
    AUTH(50, 60, 300, "auth"),
    
    /**
     * Admin account authentication - 3 attempts per 15 minutes, 5 minute block
     * Reduced from 1 hour block - excessive block times frustrate legitimate users
     * 5 minutes is still long enough to prevent brute force attacks
     */
    ADMIN_AUTH(3, 900, 300, "admin_auth"),

    /**
     * Company account authentication - 5 attempts per 5 minutes, 3 minute block
     * Reduced from 15 minute block - companies need quick access for business operations
     */
    COMPANY_AUTH(5, 300, 180, "company_auth"),

    /**
     * Influencer account authentication - 10 attempts per 5 minutes, 2 minute block
     * Reduced from 5 minute block - influencers post frequently
     */
    INFLUENCER_AUTH(10, 300, 120, "influencer_auth"),

    /**
     * Unknown/default account authentication - 5 attempts per 5 minutes, 5 minute block
     * Reduced from 10 minute block
     */
    UNKNOWN_AUTH(5, 300, 300, "unknown_auth"),
    
    /**
     * Strict limit for sensitive operations - 10 requests per minute.
     * Use for: Password reset, email change, 2FA enrollment, API key generation,
     * account deletion, payment method updates, and other security-critical operations.
     * Block duration: 5 minutes to prevent abuse while not over-punishing legitimate users.
     */
    STRICT(10, 60, 300, "strict"),

    /**
     * Standard API usage - 60 requests per minute.
     * Use for: Most authenticated API endpoints including CRUD operations,
     * file uploads, form submissions, and general user interactions.
     * This is the default profile for typical authenticated endpoints.
     */
    STANDARD(60, 60, 300, "standard"),

    /**
     * Relaxed limit for read-heavy operations - 120 requests per minute.
     * Use for: Search endpoints, listing pages, filtering, autocomplete,
     * dashboard data retrieval, and other read-only operations that users
     * may invoke frequently during normal browsing.
     */
    RELAXED(120, 60, 300, "relaxed"),

    /**
     * High frequency operations - 300 requests per minute.
     * Use for: Health checks, metrics endpoints, static resource metadata,
     * real-time status polling, WebSocket fallback polling, and other
     * endpoints designed to handle very high request volumes.
     * WARNING: Only use for endpoints that truly need this throughput.
     */
    HIGH(300, 60, 300, "high");
    
    private final int maxAttempts;
    private final int windowSeconds;
    private final int blockDurationSeconds;
    private final String keyPrefix;
    
    RateLimitProfile(int maxAttempts, int windowSeconds, int blockDurationSeconds, String keyPrefix) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.blockDurationSeconds = blockDurationSeconds;
        this.keyPrefix = keyPrefix;
    }
    
    // Backward compatibility getters
    public int getRequests() {
        return maxAttempts;
    }
    
    public int getDurationSeconds() {
        return windowSeconds;
    }

}
