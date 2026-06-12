package com.sm.instagram.platform.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * In-memory rate limiter service that is explicitly enabled when storage.mode=in-memory
 * This ensures proper bean selection when Redis is not available or not desired.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "storage.mode", havingValue = "in-memory")
public class InMemoryRateLimiterService extends RateLimiterService {
    
    public InMemoryRateLimiterService() {
        super();
        log.info("InMemoryRateLimiterService initialized - using local in-memory storage for rate limiting");
    }
    
    @Override
    public RateLimitResult checkLimit(String key, int limit, int durationSeconds) {
        // Anonymize key for logging
        String anonymizedKey = anonymizeKey(key);
        
        log.trace("In-memory rate limit check for key: {}", anonymizedKey);
        
        // Perform the rate limit check
        RateLimitResult result = super.checkLimit(key, limit, durationSeconds);
        
        // Log GDPR information
        if (result.isAllowed()) {
            log.debug("GDPR: Service=InMemoryRateLimit, Operation=CHECK_ALLOWED, Key={}, Remaining={}/{}, Duration={}s, Purpose=rate_limiting", 
                anonymizedKey, result.getRemaining(), limit, durationSeconds);
        } else {
            log.warn("GDPR: Service=InMemoryRateLimit, Operation=LIMIT_EXCEEDED, Key={}, Limit={}/{}, Purpose=security", 
                anonymizedKey, limit, durationSeconds);
        }
        
        return result;
    }
    
    /**
     * Anonymize key for GDPR-compliant logging.
     * Preserves key type but anonymizes identifying information.
     * Firebase UIDs are already pseudonymous, so we can use them directly.
     */
    private String anonymizeKey(String key) {
        if (key == null || key.isEmpty()) {
            return "unknown";
        }
        
        // Check for common prefixes
        if (key.startsWith("user:")) {
            // Extract the user ID part
            String userId = key.substring(5);
            
            // Check if this looks like a Firebase UID (20+ chars, alphanumeric)
            // Firebase UIDs are already pseudonymous identifiers
            if (userId.length() >= 20 && userId.matches("[a-zA-Z0-9]+")) {
                // Use first 8 and last 4 chars of Firebase UID for logging
                if (userId.length() > 12) {
                    return "user:" + userId.substring(0, 8) + "..." + userId.substring(userId.length() - 4);
                } else {
                    // Short ID, just use as is
                    return "user:" + userId;
                }
            } else if (userId.matches("\\d+")) {
                // Numeric user ID (database ID), hash it
                String hashedId = hashString(userId);
                return "user:" + (hashedId.length() >= 8 ? hashedId.substring(0, 8) : hashedId);
            } else {
                // Other format, hash it for safety
                String hashedId = hashString(userId);
                return "user:" + (hashedId.length() >= 8 ? hashedId.substring(0, 8) : hashedId);
            }
        } else if (key.startsWith("ip:")) {
            // Anonymize IP address
            String ip = key.substring(3);
            return "ip:" + anonymizeIp(ip);
        } else if (key.contains("@")) {
            // Likely an email, mask it
            return "email:***";
        } else {
            // Generic key, return first few chars + hash
            String prefix = key.length() > 3 ? key.substring(0, 3) : key;
            String hashedKey = hashString(key);
            // Ensure we have at least 4 characters for substring
            return prefix + "***" + (hashedKey.length() >= 4 ? hashedKey.substring(0, 4) : hashedKey);
        }
    }
    
    /**
     * Simple hash function for anonymization.
     * Ensures minimum length by padding with zeros if needed.
     */
    private String hashString(String input) {
        // Use Long.toHexString for better distribution and pad to ensure minimum 8 chars
        long hash = input.hashCode() & 0xFFFFFFFFL; // Convert to unsigned
        String hexString = Long.toHexString(hash);
        // Pad with zeros to ensure at least 8 characters
        while (hexString.length() < 8) {
            hexString = "0" + hexString;
        }
        return hexString;
    }
    
    /**
     * Anonymize IP address by masking last octets.
     */
    private String anonymizeIp(String ip) {
        if (ip == null) return "unknown";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".xxx.xxx";
        }
        return "xxx.xxx.xxx.xxx";
    }
}
