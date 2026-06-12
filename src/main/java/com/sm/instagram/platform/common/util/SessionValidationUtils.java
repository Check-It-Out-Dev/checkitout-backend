package com.sm.instagram.platform.common.util;

import java.time.Duration;
import java.time.Instant;

/**
 * Utility class for session validation operations.
 *
 * Extracted from SessionSecurityService for testability and reuse.
 * All methods are pure functions with no external dependencies.
 *
 * @see com.sm.instagram.platform.auth.service.SessionSecurityService
 */
public final class SessionValidationUtils {

    private SessionValidationUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Check if a session has expired based on creation time and max age.
     *
     * @param createdAt Session creation timestamp
     * @param maxAge Maximum session duration
     * @return true if session is expired or createdAt is null, false otherwise
     */
    public static boolean isSessionExpired(Instant createdAt, Duration maxAge) {
        if (createdAt == null) {
            return true;
        }
        return Instant.now().isAfter(createdAt.plus(maxAge));
    }

    /**
     * Compare stored User-Agent with current request User-Agent.
     * Requires exact match for security.
     *
     * @param stored Stored User-Agent from session
     * @param current Current User-Agent from request
     * @return true if User-Agents match exactly, false if null or different
     */
    public static boolean isSameUserAgent(String stored, String current) {
        if (stored == null || current == null) {
            return false;
        }
        return stored.equals(current);
    }

    /**
     * Compare country codes (case-insensitive).
     * Both null values are considered equal (unknown countries).
     *
     * @param stored Stored country code from session
     * @param current Current country code from request
     * @return true if countries match (case-insensitive) or both are null
     */
    public static boolean isSameCountry(String stored, String current) {
        if (stored == null && current == null) {
            return true;
        }
        if (stored == null || current == null) {
            return false;
        }
        return stored.equalsIgnoreCase(current);
    }

    /**
     * Convenience method to check session expiration using seconds.
     *
     * @param createdAt Session creation timestamp
     * @param maxAgeSeconds Maximum session age in seconds
     * @return true if session is expired
     */
    public static boolean isSessionTooOld(Instant createdAt, long maxAgeSeconds) {
        return isSessionExpired(createdAt, Duration.ofSeconds(maxAgeSeconds));
    }
}
