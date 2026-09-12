package com.sm.instagram.platform.storage.service;

import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimitProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of StorageRateLimitService for no-redis profile.
 * Used for local development and environments without Redis.
 * <p>
 * This implementation provides the same rate limiting functionality but stores
 * all data in memory instead of Redis. Data is lost on restart.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.mode", havingValue = "in-memory")  // Active when in-memory storage is configured
public class InMemoryStorageRateLimitService extends StorageRateLimitService {


    // In-memory storage structures (all inner collections must be thread-safe for concurrent access)
    private final Map<String, ConcurrentSkipListSet<UploadEntry>> userHourlyUploads = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentSkipListSet<UploadEntry>> userDailyUploads = new ConcurrentHashMap<>();
    private final ConcurrentSkipListSet<UploadEntry> globalMinuteUploads = new ConcurrentSkipListSet<>();
    private final Map<String, AtomicLong> userStorageUsage = new ConcurrentHashMap<>();

    // Signed URL cache for rate limiting signed URL generation
    private final Map<String, CopyOnWriteArrayList<SignedUrlEntry>> signedUrlCache = new ConcurrentHashMap<>();

    private final RateLimitProperties rateLimitProperties;

    public InMemoryStorageRateLimitService(@Autowired(required = false) RateLimitProperties rateLimitProperties) {
        super(null, rateLimitProperties);  // Pass null for RedisTemplate since we don't use it
        this.rateLimitProperties = rateLimitProperties;
        log.info("InMemoryStorageRateLimitService initialized for no-redis profile");
    }

    @Override
    public RateLimitResult checkUploadAllowed(String userId, long fileSize) {
        // Log GDPR information
        log.info("GDPR: Service=checkUploadAllowed, Operation=CHECK_UPLOAD_LIMIT, UserID={}, FileSize={}, Purpose=storage_rate_limiting",
                anonymizeUserId(userId), fileSize);

        // If rate limiting is not configured, allow uploads
        if (rateLimitProperties == null || rateLimitProperties.getUpload() == null) {
            log.warn("Rate limiting not configured - allowing upload for user {}", anonymizeUserId(userId));
            return RateLimitResult.allowed(999, 999);
        }

        long now = Instant.now().toEpochMilli();
        RateLimitProperties.Upload uploadConfig = rateLimitProperties.getUpload();

        // Check file size limit
        if (fileSize > uploadConfig.getMaxFileSize()) {
            return RateLimitResult.blocked(
                    String.format("File size exceeds maximum allowed size of %d MB",
                            uploadConfig.getMaxFileSize() / (1024 * 1024))
            );
        }

        // Check if user has storage space
        if (!hasStorageSpace(userId, fileSize)) {
            return RateLimitResult.blocked(
                    String.format("Storage limit exceeded. Maximum allowed: %d MB",
                            uploadConfig.getMaxUserStorage() / (1024 * 1024))
            );
        }

        // Check global rate limit
        if (!checkGlobalLimitInMemory(now)) {
            return RateLimitResult.blocked("Global rate limit exceeded. Try again in a minute.");
        }

        // Check user's hourly limit
        int hourlyCount = checkUserHourlyLimitInMemory(userId, now);
        if (hourlyCount >= uploadConfig.getPerHour()) {
            return RateLimitResult.blocked(
                    String.format("Hourly limit reached (%d/%d uploads). Try again later.",
                            hourlyCount, uploadConfig.getPerHour())
            );
        }

        // Check user's daily limit
        int dailyCount = checkUserDailyLimitInMemory(userId, now);
        if (dailyCount >= uploadConfig.getPerDay()) {
            return RateLimitResult.blocked(
                    String.format("Daily limit reached (%d/%d uploads). Try again tomorrow.",
                            dailyCount, uploadConfig.getPerDay())
            );
        }

        // Check signed URL generation rate limit (if applicable)
        if (!checkSignedUrlRateLimit(userId, now)) {
            return RateLimitResult.blocked("Too many signed URL requests. Please wait a moment.");
        }

        return RateLimitResult.allowed(
                uploadConfig.getPerHour() - hourlyCount,
                uploadConfig.getPerDay() - dailyCount
        );
    }

    @Override
    public void recordUpload(String userId, long fileSize) {
        log.info("GDPR: Service=recordUpload, Operation=RECORD_UPLOAD, UserID={}, FileSize={}, Purpose=usage_tracking",
                anonymizeUserId(userId), fileSize);

        if (rateLimitProperties == null) {
            log.warn("Rate limiting not configured - cannot record upload for user {}", anonymizeUserId(userId));
            return;
        }

        long now = Instant.now().toEpochMilli();
        String uploadId = userId + ":" + now + ":" + UUID.randomUUID();
        UploadEntry entry = new UploadEntry(uploadId, now);

        try {
            // Record in hourly bucket
            userHourlyUploads.computeIfAbsent(userId, k -> new ConcurrentSkipListSet<>()).add(entry);

            // Record in daily bucket
            userDailyUploads.computeIfAbsent(userId, k -> new ConcurrentSkipListSet<>()).add(entry);

            // Record in global bucket
            globalMinuteUploads.add(entry);

            // Update user storage
            updateUserStorage(userId, fileSize);

            // Clean up old entries
            cleanupOldEntries();

            log.info("Recorded upload for user {} with size {} bytes (in-memory)", anonymizeUserId(userId), fileSize);
        } catch (Exception e) {
            log.error("GDPR: Service=recordUpload, Operation=RECORD_FAILED, UserID={}, Error={}",
                    anonymizeUserId(userId), e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.quota_tracking_failed");
        }
    }

    @Override
    public void updateUserStorage(String userId, long fileSize) {
        userStorageUsage.computeIfAbsent(userId, k -> new AtomicLong(0)).addAndGet(fileSize);
        log.debug("GDPR: Service=updateUserStorage, Operation=UPDATE_STORAGE, UserID={}, SizeChange={}, TotalUsage={}, Purpose=storage_accounting",
                anonymizeUserId(userId), fileSize, userStorageUsage.get(userId).get());
    }

    @Override
    public boolean hasStorageSpace(String userId, long requestedSize) {
        if (rateLimitProperties == null || rateLimitProperties.getUpload() == null) {
            return true; // Fail open
        }

        AtomicLong currentUsage = userStorageUsage.get(userId);
        long used = currentUsage != null ? currentUsage.get() : 0;

        // The same overflow-safe comparison StorageRateLimitService has carried all along, and
        // this implementation of the same interface did not. `used + requestedSize` wraps to a
        // negative for a large declared size, and a negative is <= the limit -- so the quota check
        // passed exactly on the input it exists to refuse. SignedUrlService calls this with a
        // caller-declared fileSize (CodeQL java/tainted-arithmetic).
        if (requestedSize < 0) {
            log.warn("GDPR: Operation=hasStorageSpace_invalidRequest, UserID={}, RequestedSize={}, Purpose=security",
                    anonymizeUserId(userId), requestedSize);
            return false;
        }
        if (used < 0) {
            log.warn("GDPR: Operation=hasStorageSpace_invalid, UserID={}, Used={}, Purpose=security",
                    anonymizeUserId(userId), used);
            used = 0;
        }
        long maxStorage = rateLimitProperties.getUpload().getMaxUserStorage();
        if (used > maxStorage) {
            return false;
        }
        return requestedSize <= maxStorage - used;
    }

    @Override
    public RateLimitStatus getUserStatus(String userId) {
        if (rateLimitProperties == null || rateLimitProperties.getUpload() == null) {
            log.warn("Rate limiting not configured - returning default status for user {}", userId);
            return new RateLimitStatus(0, 999, 0, 999, 0, Long.MAX_VALUE);
        }

        long now = Instant.now().toEpochMilli();
        RateLimitProperties.Upload uploadConfig = rateLimitProperties.getUpload();

        int hourlyCount = checkUserHourlyLimitInMemory(userId, now);
        int dailyCount = checkUserDailyLimitInMemory(userId, now);

        AtomicLong storageUsage = userStorageUsage.get(userId);
        long storageUsed = storageUsage != null ? storageUsage.get() : 0;

        return new RateLimitStatus(
                hourlyCount,
                uploadConfig.getPerHour(),
                dailyCount,
                uploadConfig.getPerDay(),
                storageUsed,
                uploadConfig.getMaxUserStorage()
        );
    }

    private boolean checkGlobalLimitInMemory(long now) {
        if (rateLimitProperties == null || rateLimitProperties.getUpload() == null) {
            return true; // Fail open
        }

        long oneMinuteAgo = now - 60000; // 1 minute in milliseconds

        // Remove old entries
        globalMinuteUploads.removeIf(entry -> entry.getTimestamp() < oneMinuteAgo);

        // Count remaining entries
        return globalMinuteUploads.size() < rateLimitProperties.getUpload().getGlobalPerMinute();
    }

    private int checkUserHourlyLimitInMemory(String userId, long now) {
        ConcurrentSkipListSet<UploadEntry> uploads = userHourlyUploads.get(userId);
        if (uploads == null) {
            return 0;
        }

        long oneHourAgo = now - 3600000; // 1 hour in milliseconds

        // Count entries within the last hour
        return (int) uploads.stream()
                .filter(entry -> entry.getTimestamp() > oneHourAgo)
                .count();
    }

    // Private helper methods for in-memory operations

    private int checkUserDailyLimitInMemory(String userId, long now) {
        ConcurrentSkipListSet<UploadEntry> uploads = userDailyUploads.get(userId);
        if (uploads == null) {
            return 0;
        }

        long oneDayAgo = now - 86400000; // 24 hours in milliseconds

        // Count entries within the last day
        return (int) uploads.stream()
                .filter(entry -> entry.getTimestamp() > oneDayAgo)
                .count();
    }

    /**
     * Check signed URL generation rate limit
     * Prevents abuse of signed URL generation (e.g., 10 requests per minute per user)
     */
    private boolean checkSignedUrlRateLimit(String userId, long now) {
        CopyOnWriteArrayList<SignedUrlEntry> userUrls = signedUrlCache.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());

        // Clean up entries older than 1 minute
        long oneMinuteAgo = now - 60000;
        userUrls.removeIf(entry -> entry.getTimestamp() < oneMinuteAgo);

        // Check if user has exceeded signed URL generation limit (e.g., 10 per minute)
        int maxSignedUrlsPerMinute = 10; // This could be configurable
        return userUrls.size() < maxSignedUrlsPerMinute;
    }

    /**
     * Record a signed URL generation for rate limiting purposes
     */
    public void recordSignedUrlGeneration(String userId, long fileSize) {
        long now = Instant.now().toEpochMilli();
        CopyOnWriteArrayList<SignedUrlEntry> userUrls = signedUrlCache.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        userUrls.add(new SignedUrlEntry(UUID.randomUUID().toString(), now, fileSize));
    }

    /**
     * Clean up old entries from all in-memory stores
     * This is called periodically and also during record operations
     */
    private void cleanupOldEntries() {
        long now = Instant.now().toEpochMilli();
        long oneHourAgo = now - 3600000;
        long oneDayAgo = now - 86400000;
        long oneMinuteAgo = now - 60000;

        // Clean hourly uploads
        userHourlyUploads.forEach((userId, uploads) -> {
            uploads.removeIf(entry -> entry.getTimestamp() < oneHourAgo);
        });

        // Clean daily uploads
        userDailyUploads.forEach((userId, uploads) -> {
            uploads.removeIf(entry -> entry.getTimestamp() < oneDayAgo);
        });

        // Clean global uploads
        globalMinuteUploads.removeIf(entry -> entry.getTimestamp() < oneMinuteAgo);

        // Clean signed URL cache
        signedUrlCache.forEach((userId, urls) -> {
            urls.removeIf(entry -> entry.getTimestamp() < oneMinuteAgo);
        });
    }

    /**
     * Scheduled cleanup task to prevent memory leaks
     * Runs every 5 minutes to clean up expired entries
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void scheduledCleanup() {
        int beforeHourly = userHourlyUploads.values().stream().mapToInt(Set::size).sum();
        int beforeDaily = userDailyUploads.values().stream().mapToInt(Set::size).sum();
        int beforeGlobal = globalMinuteUploads.size();
        int beforeSignedUrls = signedUrlCache.values().stream().mapToInt(List::size).sum();

        cleanupOldEntries();

        // Remove empty user entries
        userHourlyUploads.entrySet().removeIf(e -> e.getValue().isEmpty());
        userDailyUploads.entrySet().removeIf(e -> e.getValue().isEmpty());
        signedUrlCache.entrySet().removeIf(e -> e.getValue().isEmpty());

        int afterHourly = userHourlyUploads.values().stream().mapToInt(Set::size).sum();
        int afterDaily = userDailyUploads.values().stream().mapToInt(Set::size).sum();
        int afterGlobal = globalMinuteUploads.size();
        int afterSignedUrls = signedUrlCache.values().stream().mapToInt(List::size).sum();

        if (beforeHourly != afterHourly || beforeDaily != afterDaily ||
                beforeGlobal != afterGlobal || beforeSignedUrls != afterSignedUrls) {
            log.debug("Cleaned expired entries - Hourly: {} -> {}, Daily: {} -> {}, Global: {} -> {}, SignedUrls: {} -> {}",
                    beforeHourly, afterHourly, beforeDaily, afterDaily,
                    beforeGlobal, afterGlobal, beforeSignedUrls, afterSignedUrls);
        }
    }

    /**
     * Reset all rate limits and storage for a specific user
     * Useful for testing or administrative purposes
     */
    public void resetUserLimits(String userId) {
        log.warn("GDPR: Service=resetUserLimits, Operation=RESET_USER_LIMITS, UserID={}, Purpose=administrative_action",
                anonymizeUserId(userId));

        userHourlyUploads.remove(userId);
        userDailyUploads.remove(userId);
        userStorageUsage.remove(userId);
        signedUrlCache.remove(userId);
        log.info("Reset all limits and storage for user {}", anonymizeUserId(userId));
    }

    /**
     * Anonymize user ID for GDPR-compliant logging
     */
    private String anonymizeUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "unknown";
        }
        // Return first 8 chars of hash
        return "user_" + Integer.toHexString(userId.hashCode()).substring(0, Math.min(8, Integer.toHexString(userId.hashCode()).length()));
    }

    /**
     * Get current memory usage statistics
     * Useful for monitoring the in-memory cache size
     */
    public Map<String, Object> getMemoryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userStorageUsage.size());
        stats.put("hourlyUploadsEntries", userHourlyUploads.values().stream().mapToInt(Set::size).sum());
        stats.put("dailyUploadsEntries", userDailyUploads.values().stream().mapToInt(Set::size).sum());
        stats.put("globalUploadsEntries", globalMinuteUploads.size());
        stats.put("signedUrlCacheEntries", signedUrlCache.values().stream().mapToInt(List::size).sum());

        long totalStorageUsed = userStorageUsage.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
        stats.put("totalStorageUsedBytes", totalStorageUsed);
        stats.put("totalStorageUsedMB", totalStorageUsed / (1024.0 * 1024.0));

        return stats;
    }

    @Data
    private static class UploadEntry implements Comparable<UploadEntry> {
        private final String uploadId;
        private final long timestamp;

        @Override
        public int compareTo(UploadEntry other) {
            int timeCompare = Long.compare(this.timestamp, other.timestamp);
            return timeCompare != 0 ? timeCompare : this.uploadId.compareTo(other.uploadId);
        }
    }

    @Data
    private static class SignedUrlEntry {
        private final String urlId;
        private final long timestamp;
        private final long fileSize;
    }
}
