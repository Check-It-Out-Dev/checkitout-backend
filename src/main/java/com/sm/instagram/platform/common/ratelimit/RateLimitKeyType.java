package com.sm.instagram.platform.common.ratelimit;

/**
 * Defines how rate limit keys are generated
 */
public enum RateLimitKeyType {
    /**
     * Use user ID if authenticated, otherwise use IP address (default)
     */
    USER_OR_IP,
    
    /**
     * Use only the authenticated user ID (requires authentication)
     */
    USER_ONLY,
    
    /**
     * Use only the IP address
     */
    IP_ONLY,
    
    /**
     * Use the endpoint path as the key (global limit per endpoint)
     */
    ENDPOINT,
    
    /**
     * Use custom key generation logic
     */
    CUSTOM,
    
    /**
     * Use user ID + endpoint path (per-user-per-endpoint limiting)
     */
    USER_ENDPOINT,
    
    /**
     * Use IP + endpoint path (per-IP-per-endpoint limiting)
     */
    IP_ENDPOINT
}
