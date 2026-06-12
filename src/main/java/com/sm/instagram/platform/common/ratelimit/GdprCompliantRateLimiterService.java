package com.sm.instagram.platform.common.ratelimit;

import com.sm.instagram.platform.config.StorageModeConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.sm.instagram.platform.common.utils.HashingUtil;
import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * GDPR-compliant wrapper for rate limiting services.
 * Provides data anonymization, retention policies, and audit capabilities.
 */
@Service
@Primary
@Slf4j
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"}) // Spring @Value fields
public class GdprCompliantRateLimiterService extends RateLimiterService {
    
    // Constants
    private static final String USER_KEY_PREFIX = "user:";
    private static final String IP_KEY_PREFIX = "ip:";
    private static final String ANONYMOUS_KEY_PREFIX = "anonymous:";
    private static final String ANON_KEY_PREFIX = "anon:";
    private static final String USER_KEY_TYPE = "user";
    private static final String IP_KEY_TYPE = "ip";
    private static final String ANONYMOUS_KEY_TYPE = "anonymous";
    private static final String UNKNOWN_KEY_TYPE = "unknown";
    private static final long SECONDS_PER_HOUR = 3600L;
    
    // Delegate to the actual rate limiter (Redis or In-Memory based on storage.mode)
    private final RateLimiterService delegateRateLimiter;
    private final StorageModeConfiguration storageModeConfiguration;
    
    @Value("${rate-limit.gdpr.enabled:true}")
    private boolean gdprEnabled;
    
    @Value("${rate-limit.gdpr.data-retention-hours:24}")
    private int dataRetentionHours;
    
    @Value("${rate-limit.gdpr.anonymize-keys:true}")
    private boolean anonymizeKeys;
    
    @Value("${rate-limit.gdpr.audit-violations:true}")
    private boolean auditViolations;
    
    // GDPR: Store minimal audit data with automatic expiration
    private final Map<String, RateLimitAuditEvent> auditEvents = new ConcurrentHashMap<>();
    
    @Autowired
    public GdprCompliantRateLimiterService(
            @Autowired(required = false) RedisRateLimiterService redisRateLimiter,
            @Autowired(required = false) InMemoryRateLimiterService inMemoryRateLimiter,
            StorageModeConfiguration storageModeConfiguration) {
        
        this.storageModeConfiguration = storageModeConfiguration;
        
        // Select based on storage mode configuration
        if (storageModeConfiguration.isRedisMode() && redisRateLimiter != null) {
            this.delegateRateLimiter = redisRateLimiter;
            log.info("GdprCompliantRateLimiterService using RedisRateLimiterService");
        } else if (storageModeConfiguration.isInMemoryMode() && inMemoryRateLimiter != null) {
            this.delegateRateLimiter = inMemoryRateLimiter;
            log.info("GdprCompliantRateLimiterService using InMemoryRateLimiterService");
        } else {
            // Fallback to base in-memory if nothing else is available
            this.delegateRateLimiter = new RateLimiterService();
            log.warn("GdprCompliantRateLimiterService using fallback RateLimiterService");
        }
    }
    
    @PostConstruct
    public void init() {
        log.info("GDPR Rate Limiting Configuration:");
        log.info("  - Storage mode: {}", storageModeConfiguration.getCurrentStorageMode());
        log.info("  - Active implementation: {}", delegateRateLimiter.getClass().getSimpleName());
        log.info("  - GDPR enabled: {}", gdprEnabled);
        log.info("  - Data retention: {} hours", dataRetentionHours);
        log.info("  - Anonymize keys: {}", anonymizeKeys);
        log.info("  - Audit violations: {}", auditViolations);
    }
    
    /**
     * Check rate limit with GDPR compliance
     */
    @Override
    public RateLimitResult checkLimit(String key, int limit, int durationSeconds) {
        // CRITICAL FIX: Keys from RateLimitInterceptor are ALREADY anonymized via HashingUtil
        // Do NOT re-hash them - double-hashing was causing key inconsistencies and audit lookup failures
        // The anonymization happens at the source (RateLimitInterceptor) for these key types:
        // - rate_limit:{hash} (authenticated user)
        // - rate_limit_ip:{hash} (anonymous by IP)
        // - rate_limit_device:{hash} (anonymous by device)
        // - user:{hash}:endpoint:... (user + endpoint)
        // - ip:{hash}:endpoint:... (IP + endpoint)
        String processedKey = key;

        // Extract user ID if this is a user key
        String userId = key.startsWith(USER_KEY_PREFIX) ? key.substring(USER_KEY_PREFIX.length()) : "anonymous";

        log.debug("GDPR: Service=checkRateLimit, Operation=CHECK_LIMIT, UserKey={}, Limit={}, Duration={}, Purpose=rate_limiting",
            key, limit, durationSeconds);

        // Delegate to actual rate limiter
        RateLimitResult result = delegateRateLimiter.checkLimit(
            processedKey, limit, durationSeconds
        );

        // GDPR: Audit violations if enabled
        if (gdprEnabled && auditViolations && !result.isAllowed()) {
            log.warn("GDPR: Service=rateLimitViolation, Operation=LIMIT_EXCEEDED, UserKey={}, Limit={}, Purpose=security",
                key, limit);
            auditRateLimitViolation(key, processedKey, limit, durationSeconds);
        }

        return result;
    }
    
    /**
     * Anonymize keys to protect PII using SHA-256 hashing
     */
    private String anonymizeKey(String key) {
        // GDPR-compliant SHA-256 hashing for all identifiers
        return HashingUtil.hashIdentifier(key);
    }
    
    /**
     * Create one-way hash of sensitive data
     */
    private String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // Return first 16 chars of hex representation
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to hash string", e);
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }
    
    /**
     * Anonymize IP address by removing last octet
     */
    private String anonymizeIpAddress(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".xxx";
        } else if (parts.length > 0 && ip.contains(":")) {
            // IPv6 - anonymize last 64 bits
            int lastColon = ip.lastIndexOf(":");
            if (lastColon > 0) {
                return ip.substring(0, lastColon) + ":xxxx";
            }
        }
        return "xxx.xxx.xxx.xxx";
    }
    
    /**
     * Audit rate limit violations for security monitoring
     */
    private void auditRateLimitViolation(String originalKey, String anonymizedKey, 
                                        int limit, int duration) {
        String eventId = UUID.randomUUID().toString();
        String keyType = extractKeyType(originalKey);
        
        RateLimitAuditEvent event = new RateLimitAuditEvent(
            eventId,
            keyType,
            anonymizedKey,
            LocalDateTime.now(),
            limit,
            duration
        );
        
        auditEvents.put(eventId, event);
        
        // Log for security monitoring (no PII)
        log.warn("Rate limit exceeded - ID: {}, Type: {}, Limit: {}/{}", 
            eventId, keyType, limit, duration);
    }
    
    private String extractKeyType(String key) {
        // Handle all key prefixes from RateLimitInterceptor
        // User keys: rate_limit:{hash}, rate_limit_user:{hash}, user:{hash}:endpoint:...
        if (key.startsWith("rate_limit_user:") || key.startsWith("user:")) {
            return USER_KEY_TYPE;
        }
        // IP keys: rate_limit_ip:{hash}, ip:{hash}:endpoint:...
        if (key.startsWith("rate_limit_ip:") || key.startsWith(IP_KEY_PREFIX)) {
            return IP_KEY_TYPE;
        }
        // Device keys: rate_limit_device:{hash}
        if (key.startsWith("rate_limit_device:")) {
            return "device";
        }
        // Default authenticated user key from generateUserOrIpKey()
        if (key.startsWith("rate_limit:")) {
            return USER_KEY_TYPE;
        }
        // Legacy anonymous keys
        if (key.startsWith(ANONYMOUS_KEY_PREFIX) || key.startsWith(ANON_KEY_PREFIX)) {
            return ANONYMOUS_KEY_TYPE;
        }
        // Endpoint-only or custom keys
        if (key.startsWith("endpoint:") || key.startsWith("custom:")) {
            return "endpoint";
        }
        return UNKNOWN_KEY_TYPE;
    }
    
    /**
     * Clean up expired data for GDPR compliance
     */
    public int cleanupExpiredData() {
        // Clean rate limiter data
        int cleaned = delegateRateLimiter.cleanupExpiredEntries();
        
        // Clean audit events older than retention period
        if (gdprEnabled) {
            long cutoffTime = Instant.now().minusSeconds(dataRetentionHours * SECONDS_PER_HOUR).toEpochMilli();
            int auditCleaned = 0;
            
            var iterator = auditEvents.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getValue().getTimestamp() < cutoffTime) {
                    iterator.remove();
                    auditCleaned++;
                }
            }
            
            if (auditCleaned > 0) {
                log.debug("Cleaned {} expired audit events", auditCleaned);
            }
            
            cleaned += auditCleaned;
        }
        
        return cleaned;
    }
    
    /**
     * Delegate method for backward compatibility
     */
    @Override
    public int cleanupExpiredEntries() {
        return cleanupExpiredData();
    }
    
    /**
     * Get tracked entries count from delegate rate limiter
     */
    @Override
    public int getTrackedEntriesCount() {
        return delegateRateLimiter.getTrackedEntriesCount();
    }
    
    /**
     * Export rate limit data for a user (GDPR right to access).
     * Returns sanitized audit data without affecting active rate limits.
     */
    public Map<String, Object> exportUserRateLimitData(String userId) {
        log.info("GDPR: Service=exportUserData, Operation=DATA_EXPORT, UserID={}, Purpose=gdpr_data_portability", 
            hashString(userId));
        
        if (!gdprEnabled) {
            return Map.of("error", "GDPR features not enabled");
        }
        
        String hashedUserId = hashString(userId);
        Map<String, Object> exportData = new HashMap<>();
        
        // Find audit events for this user (read-only operation)
        Map<String, Object> userAudits = auditEvents.entrySet().stream()
            .filter(entry -> {
                RateLimitAuditEvent event = entry.getValue();
                return USER_KEY_TYPE.equals(event.getKeyType()) && 
                       event.getAnonymizedKey().endsWith(hashedUserId);
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().toExportFormat()
            ));
        
        exportData.put("rate_limit_violations", userAudits);
        exportData.put("data_retention_hours", dataRetentionHours);
        exportData.put("exported_at", LocalDateTime.now().toString());
        exportData.put("note", "Active rate limits are not included for security reasons");
        
        return exportData;
    }
    
    /**
     * Delete rate limit AUDIT data for a user (GDPR right to erasure).
     * IMPORTANT: This only deletes audit logs, NOT active rate limits.
     * Active rate limits remain in effect to prevent abuse.
     */
    public void deleteUserRateLimitData(String userId) {
        if (!gdprEnabled) {
            return;
        }
        
        String hashedUserId = hashString(userId);
        log.warn("GDPR: DELETION Service=deleteUserRateLimitData, Operation=DELETE_AUDIT_DATA, UserID={}, Purpose=gdpr_erasure", 
            hashedUserId);
        
        // Only remove AUDIT events, not active rate limits
        // Use an array to make it effectively final for lambda
        final int[] removedCount = {0};
        auditEvents.entrySet().removeIf(entry -> {
            RateLimitAuditEvent event = entry.getValue();
            boolean shouldRemove = USER_KEY_TYPE.equals(event.getKeyType()) && 
                                 event.getAnonymizedKey().endsWith(hashedUserId);
            if (shouldRemove) removedCount[0]++;
            return shouldRemove;
        });
        
        log.info("GDPR: DELETION_COMPLETE Operation=deleteUserRateLimitData, UserID={}, RecordsDeleted={}, Note=active_limits_retained", 
                hashedUserId, removedCount[0]);
    }
    
    /**
     * Get current metrics for monitoring
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("tracked_entries", delegateRateLimiter.getTrackedEntriesCount());
        metrics.put("audit_events", auditEvents.size());
        metrics.put("gdpr_enabled", gdprEnabled);
        metrics.put("data_retention_hours", dataRetentionHours);
        return metrics;
    }
    
    /**
     * Audit event for rate limit violations
     */
    @Getter
    private static class RateLimitAuditEvent {
        private final String id;
        private final String keyType;
        private final String anonymizedKey;
        private final long timestamp;
        private final int limit;
        private final int duration;
        
        public RateLimitAuditEvent(String id, String keyType, String anonymizedKey,
                                  LocalDateTime dateTime, int limit, int duration) {
            this.id = id;
            this.keyType = keyType;
            this.anonymizedKey = anonymizedKey;
            this.timestamp = dateTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            this.limit = limit;
            this.duration = duration;
        }
        
        public Map<String, Object> toExportFormat() {
            return Map.of(
                "id", id,
                "type", keyType,
                "timestamp", LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), 
                    java.time.ZoneOffset.UTC
                ).toString(),
                "limit", limit,
                "duration_seconds", duration
            );
        }
    }
}
