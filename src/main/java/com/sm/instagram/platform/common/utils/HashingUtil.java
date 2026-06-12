package com.sm.instagram.platform.common.utils;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/**
 * GDPR-compliant hashing utility for anonymizing identifiers.
 * Ensures consistent SHA-256 hashing across all application instances.
 * 
 * This utility is critical for GDPR compliance as it prevents storage
 * of personally identifiable information (PII) in Redis keys and logs.
 */
@Component
public class HashingUtil {
    
    /**
     * Hash any identifier using SHA-256 algorithm.
     * 
     * @param identifier The raw identifier (Firebase UID, IP address, etc.)
     * @return SHA-256 hex string (64 characters) or null if input is null
     */
    public static String hashIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }
        return DigestUtils.sha256Hex(identifier);
    }
    
    /**
     * Generate a Redis key with hashed identifier.
     * 
     * @param namespace The Redis namespace (e.g., "rate_limit", "session", "user_cache")
     * @param identifier The raw identifier to hash
     * @return Formatted Redis key with hashed identifier
     */
    public static String generateRedisKey(String namespace, String identifier) {
        String hashed = hashIdentifier(identifier);
        if (hashed == null) {
            return null;
        }
        return namespace + ":" + hashed;
    }
    
    /**
     * Hash an IP address for geo-location caching.
     * 
     * @param ipAddress The raw IP address
     * @return Hashed IP address
     */
    public static String hashIpAddress(String ipAddress) {
        return hashIdentifier(ipAddress);
    }
    
    /**
     * Hash a Firebase UID for user operations.
     * 
     * @param firebaseUid The raw Firebase UID
     * @return Hashed Firebase UID
     */
    public static String hashFirebaseUid(String firebaseUid) {
        return hashIdentifier(firebaseUid);
    }
}
