package com.sm.instagram.platform.storage.service;

import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.mode", havingValue = "redis", matchIfMissing = true)  // Default to Redis
public class StorageRateLimitService {

    // Redis key patterns
    private static final String USER_HOURLY_KEY = "rate_limit:upload:user:%s:hourly";
    private static final String USER_DAILY_KEY = "rate_limit:upload:user:%s:daily";
    private static final String GLOBAL_MINUTE_KEY = "rate_limit:upload:global:minute";
    private static final String USER_STORAGE_KEY = "user:storage:%s";
    private static final String USER_FIRST_UPLOAD_KEY = "user:first_upload:%s";

    /**
     * An hour, as a literal, so the divisor below is provably non-zero.
     *
     * <p>{@code TimeUnit.HOURS.toMillis(1)} is the same number and always was, but it is a method
     * call, and the dataflow analyser cannot see through it -- it reported the division as a possible
     * ArithmeticException (javabugs:S3518). Nothing about the arithmetic changes; the constant just
     * says out loud what the call already guaranteed.
     */
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    /**
     * Lua script for atomic storage decrement with bounds checking.
     * Prevents TOCTOU race condition by atomically:
     * 1. Reading current value
     * 2. Calculating safe decrement
     * 3. Performing decrement (or reset to 0 if result would be negative)
     * 4. Setting TTL
     *
     * Returns the new storage value after decrement.
     */
    private static final String DECREASE_STORAGE_SCRIPT =
        "local current = tonumber(redis.call('GET', KEYS[1]) or '0') " +
        "if current <= 0 then return 0 end " +
        "local decrement = math.min(tonumber(ARGV[1]), current) " +
        "local new_value = current - decrement " +
        "if new_value < 0 then new_value = 0 end " +
        "redis.call('SET', KEYS[1], tostring(new_value)) " +
        "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) " +
        "return new_value";

    /**
     * Lua script for ATOMIC check-and-record of upload rate limits.
     * CRITICAL: This prevents race conditions where 10 concurrent requests could all
     * pass the check before any recording happens.
     *
     * Keys: [hourlyKey, dailyKey, globalKey, storageKey]
     * Args: [now, hourlyLimit, dailyLimit, globalLimit, fileSize, maxStorage,
     *        oneHourMs, oneDayMs, oneMinuteMs, uploadId, storageTtlDays]
     *
     * Returns: [allowed, hourlyRemaining, dailyRemaining, errorCode]
     * errorCode: 0=allowed, 1=hourly exceeded, 2=daily exceeded, 3=global exceeded, 4=storage exceeded
     */
    private static final String CHECK_AND_RECORD_UPLOAD_SCRIPT =
        "local hourlyKey = KEYS[1] " +
        "local dailyKey = KEYS[2] " +
        "local globalKey = KEYS[3] " +
        "local storageKey = KEYS[4] " +
        "local now = tonumber(ARGV[1]) " +
        "local hourlyLimit = tonumber(ARGV[2]) " +
        "local dailyLimit = tonumber(ARGV[3]) " +
        "local globalLimit = tonumber(ARGV[4]) " +
        "local fileSize = tonumber(ARGV[5]) " +
        "local maxStorage = tonumber(ARGV[6]) " +
        "local oneHourMs = tonumber(ARGV[7]) " +
        "local oneDayMs = tonumber(ARGV[8]) " +
        "local oneMinuteMs = tonumber(ARGV[9]) " +
        "local uploadId = ARGV[10] " +
        "local storageTtlDays = tonumber(ARGV[11]) " +
        // Clean up old entries (sliding window maintenance)
        "redis.call('ZREMRANGEBYSCORE', hourlyKey, '-inf', now - oneHourMs) " +
        "redis.call('ZREMRANGEBYSCORE', dailyKey, '-inf', now - oneDayMs) " +
        "redis.call('ZREMRANGEBYSCORE', globalKey, '-inf', now - oneMinuteMs) " +
        // Check counts AFTER cleanup
        "local hourlyCount = redis.call('ZCARD', hourlyKey) " +
        "local dailyCount = redis.call('ZCARD', dailyKey) " +
        "local globalCount = redis.call('ZCARD', globalKey) " +
        // Check storage space (overflow-safe)
        "local currentStorage = tonumber(redis.call('GET', storageKey) or '0') " +
        "if currentStorage < 0 then currentStorage = 0 end " +
        "if currentStorage > maxStorage then " +
        "  return {0, hourlyLimit - hourlyCount, dailyLimit - dailyCount, 4} " +
        "end " +
        "local remainingStorage = maxStorage - currentStorage " +
        "if fileSize > remainingStorage then " +
        "  return {0, hourlyLimit - hourlyCount, dailyLimit - dailyCount, 4} " +
        "end " +
        // Check rate limits - return immediately if exceeded (don't record)
        "if globalCount >= globalLimit then " +
        "  return {0, hourlyLimit - hourlyCount, dailyLimit - dailyCount, 3} " +
        "end " +
        "if hourlyCount >= hourlyLimit then " +
        "  return {0, 0, dailyLimit - dailyCount, 1} " +
        "end " +
        "if dailyCount >= dailyLimit then " +
        "  return {0, hourlyLimit - hourlyCount, 0, 2} " +
        "end " +
        // All checks passed - ATOMICALLY record the upload
        "redis.call('ZADD', hourlyKey, now, uploadId) " +
        "redis.call('EXPIRE', hourlyKey, 3900) " +  // 65 minutes
        "redis.call('ZADD', dailyKey, now, uploadId) " +
        "redis.call('EXPIRE', dailyKey, 90000) " +  // 25 hours
        "redis.call('ZADD', globalKey, now, uploadId) " +
        "redis.call('EXPIRE', globalKey, 120) " +  // 2 minutes
        // Update storage
        "redis.call('INCRBY', storageKey, fileSize) " +
        "redis.call('EXPIRE', storageKey, storageTtlDays * 86400) " +
        // Return success with remaining counts
        "return {1, hourlyLimit - hourlyCount - 1, dailyLimit - dailyCount - 1, 0}";

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitProperties rateLimitProperties;
    private final ZSetOperations<String, String> zSetOps;
    private final DefaultRedisScript<Long> decreaseStorageScript;
    private final DefaultRedisScript<java.util.List> checkAndRecordScript;

    /**
     * Upload types for differentiated rate limiting.
     * Profile photos get special treatment to ensure new users can always upload.
     */
    public enum UploadType {
        /** Profile photos - exempted from hourly/daily limits, only file size/storage quota apply */
        PROFILE_PHOTO,
        /** Regular content uploads - full rate limiting applies */
        CONTENT,
        /**
         * Campaign media uploads - relaxed limits for companies creating campaigns.
         * Uses 2x normal hourly/daily limits to support bulk campaign creation.
         */
        CAMPAIGN_MEDIA
    }

    public StorageRateLimitService(@Autowired(required = false) RedisTemplate<String, String> redisTemplate,
                                   @Autowired(required = false) RateLimitProperties rateLimitProperties) {
        this.redisTemplate = redisTemplate;
        this.rateLimitProperties = rateLimitProperties;
        this.zSetOps = redisTemplate != null ? redisTemplate.opsForZSet() : null;

        // Initialize Lua script for atomic storage operations
        this.decreaseStorageScript = new DefaultRedisScript<>();
        this.decreaseStorageScript.setScriptText(DECREASE_STORAGE_SCRIPT);
        this.decreaseStorageScript.setResultType(Long.class);

        // Initialize Lua script for atomic check-and-record operations
        this.checkAndRecordScript = new DefaultRedisScript<>();
        this.checkAndRecordScript.setScriptText(CHECK_AND_RECORD_UPLOAD_SCRIPT);
        this.checkAndRecordScript.setResultType(java.util.List.class);
    }

    /**
     * Checks if a user can upload based on all rate limits.
     * Think of this as checking multiple ID cards at different checkpoints.
     *
     * @param userId   The user attempting to upload
     * @param fileSize Size of the file to upload in bytes
     * @return RateLimitResult with details about which limit was hit (if any)
     */
    public RateLimitResult checkUploadAllowed(String userId, long fileSize) {
        return checkUploadAllowed(userId, fileSize, UploadType.CONTENT);
    }

    /**
     * Checks if a user can upload based on rate limits with upload type consideration.
     * Profile photos are exempted from hourly/daily limits to ensure new users can always
     * upload their profile picture.
     *
     * @param userId     The user attempting to upload
     * @param fileSize   Size of the file to upload in bytes
     * @param uploadType Type of upload (PROFILE_PHOTO or CONTENT)
     * @return RateLimitResult with details about which limit was hit (if any)
     */
    public RateLimitResult checkUploadAllowed(String userId, long fileSize, UploadType uploadType) {
        log.debug("GDPR: Operation=checkUploadRateLimit, FirebaseUID={}, FileSize={}, UploadType={}, Purpose=rate_limit_enforcement",
                userId, fileSize, uploadType);

        // If dependencies are not available, allow uploads (fail open for resilience)
        if (redisTemplate == null || rateLimitProperties == null) {
            log.warn("GDPR: Operation=checkUploadRateLimit_noDependencies, FirebaseUID={}, Purpose=rate_limit_bypass", userId);
            return RateLimitResult.allowed(999, 999);
        }

        long now = Instant.now().toEpochMilli();
        RateLimitProperties.Upload uploadConfig = rateLimitProperties.getUpload();

        // Check file size limit first - applies to ALL upload types
        if (fileSize > uploadConfig.getMaxFileSize()) {
            log.warn("GDPR: Operation=uploadBlocked_fileSize, FirebaseUID={}, FileSize={}, MaxAllowed={}, Purpose=file_size_enforcement",
                    userId, fileSize, uploadConfig.getMaxFileSize());
            return RateLimitResult.blocked(
                    String.format("File size exceeds maximum allowed size of %d MB",
                            uploadConfig.getMaxFileSize() / (1024 * 1024))
            );
        }

        // Check if user has storage space - applies to ALL upload types
        if (!hasStorageSpace(userId, fileSize)) {
            log.warn("GDPR: Operation=uploadBlocked_storageQuota, FirebaseUID={}, RequestedSize={}, Purpose=storage_quota_enforcement",
                    userId, fileSize);
            return RateLimitResult.blocked(
                    String.format("Storage limit exceeded. Maximum allowed: %d MB",
                            uploadConfig.getMaxUserStorage() / (1024 * 1024))
            );
        }

        // PROFILE_PHOTO exemption: Skip rate limits for profile photos
        // This ensures new users can ALWAYS upload their profile picture
        if (uploadType == UploadType.PROFILE_PHOTO) {
            log.info("GDPR: Operation=profilePhotoUpload_allowed, FirebaseUID={}, FileSize={}, Purpose=new_user_experience",
                    userId, fileSize);
            return RateLimitResult.allowed(
                    uploadConfig.getPerHour(),  // Full hourly quota still available
                    uploadConfig.getPerDay()    // Full daily quota still available
            );
        }

        // Calculate effective limits based on upload type and grace period
        int effectiveHourlyLimit = uploadConfig.getPerHour();
        int effectiveDailyLimit = uploadConfig.getPerDay();

        // Check if user is in grace period (first 24 hours)
        double gracePeriodMultiplier = getGracePeriodMultiplier(userId);
        if (gracePeriodMultiplier > 1.0) {
            effectiveHourlyLimit = (int) (effectiveHourlyLimit * gracePeriodMultiplier);
            effectiveDailyLimit = (int) (effectiveDailyLimit * gracePeriodMultiplier);
            log.info("GDPR: Operation=gracePeriodApplied, FirebaseUID={}, Multiplier={}, HourlyLimit={}, DailyLimit={}, Purpose=new_user_experience",
                    userId, gracePeriodMultiplier, effectiveHourlyLimit, effectiveDailyLimit);
        }

        // CAMPAIGN_MEDIA gets additional 2x on top of any grace period bonus
        if (uploadType == UploadType.CAMPAIGN_MEDIA) {
            effectiveHourlyLimit = effectiveHourlyLimit * 2;  // 2x hourly limit
            effectiveDailyLimit = effectiveDailyLimit * 2;    // 2x daily limit
            log.info("GDPR: Operation=campaignMediaUpload, FirebaseUID={}, FileSize={}, HourlyLimit={}, DailyLimit={}, Purpose=campaign_creation",
                    userId, fileSize, effectiveHourlyLimit, effectiveDailyLimit);
        }

        // Check global rate limit first (it's the broadest limit)
        if (!checkGlobalLimit(now)) {
            log.warn("GDPR: Operation=uploadBlocked_globalLimit, FirebaseUID={}, Purpose=global_rate_limit_enforcement", userId);
            return RateLimitResult.blocked("Global rate limit exceeded. Try again in a minute.");
        }

        // Check user's hourly limit
        int hourlyCount = checkUserHourlyLimit(userId, now);
        if (hourlyCount >= effectiveHourlyLimit) {
            log.warn("GDPR: Operation=uploadBlocked_hourlyLimit, FirebaseUID={}, HourlyCount={}, Limit={}, Purpose=hourly_rate_limit_enforcement",
                    userId, hourlyCount, effectiveHourlyLimit);
            return RateLimitResult.blocked(
                    String.format("Hourly limit reached (%d/%d uploads). Try again later.",
                            hourlyCount, effectiveHourlyLimit)
            );
        }

        // Check user's daily limit
        int dailyCount = checkUserDailyLimit(userId, now);
        if (dailyCount >= effectiveDailyLimit) {
            log.warn("GDPR: Operation=uploadBlocked_dailyLimit, FirebaseUID={}, DailyCount={}, Limit={}, Purpose=daily_rate_limit_enforcement",
                    userId, dailyCount, effectiveDailyLimit);
            return RateLimitResult.blocked(
                    String.format("Daily limit reached (%d/%d uploads). Try again tomorrow.",
                            dailyCount, effectiveDailyLimit)
            );
        }

        return RateLimitResult.allowed(
                effectiveHourlyLimit - hourlyCount,
                effectiveDailyLimit - dailyCount
        );
    }

    /**
     * Records an upload attempt. Call this AFTER checking limits.
     * This is like stamping a timecard when someone enters.
     */
    public void recordUpload(String userId, long fileSize) {
        log.info("GDPR: Operation=recordUpload, FirebaseUID={}, FileSize={}, DataModified=rate_limit_counters, Purpose=usage_tracking", userId, fileSize);

        if (redisTemplate == null || zSetOps == null) {
            log.warn("GDPR: Operation=recordUpload_failed, FirebaseUID={}, Error=redis_not_available, Purpose=usage_tracking", userId);
            return;
        }

        long now = Instant.now().toEpochMilli();
        String uploadId = userId + ":" + now;

        try {
            // Record in hourly bucket
            // TTL reduced from 2 hours to 65 minutes (1 hour + 5 min buffer for clock skew)
            // This ensures stale data doesn't accumulate and users' limits reset properly
            String hourlyKey = String.format(USER_HOURLY_KEY, userId);
            zSetOps.add(hourlyKey, uploadId, now);
            redisTemplate.expire(hourlyKey, 65, TimeUnit.MINUTES);

            // Record in daily bucket (24 hours + 1 hour buffer)
            String dailyKey = String.format(USER_DAILY_KEY, userId);
            zSetOps.add(dailyKey, uploadId, now);
            redisTemplate.expire(dailyKey, 25, TimeUnit.HOURS);

            // Record in global bucket
            zSetOps.add(GLOBAL_MINUTE_KEY, uploadId, now);
            redisTemplate.expire(GLOBAL_MINUTE_KEY, 2, TimeUnit.MINUTES);

            // Update user storage
            updateUserStorage(userId, fileSize);

            // Clean up old entries (sliding window maintenance)
            cleanupOldEntries(hourlyKey, now - MILLIS_PER_HOUR);
            cleanupOldEntries(dailyKey, now - TimeUnit.DAYS.toMillis(1));
            cleanupOldEntries(GLOBAL_MINUTE_KEY, now - TimeUnit.MINUTES.toMillis(1));

            log.debug("GDPR: Operation=recordUpload_success, FirebaseUID={}, FileSize={}, Purpose=usage_tracking", userId, fileSize);
        } catch (Exception e) {
            log.error("GDPR: Operation=recordUpload_error, FirebaseUID={}, Error={}, Purpose=usage_tracking", userId, e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.quota_tracking_failed");
        }
    }

    /**
     * ATOMIC check and record operation for uploads.
     * CRITICAL: This method prevents race conditions where multiple concurrent requests
     * could all pass the rate limit check before any recording happens.
     *
     * Unlike the separate checkUploadAllowed() + recordUpload() pattern, this method
     * performs both operations in a single atomic Lua script execution.
     *
     * Use this method for all production upload flows to guarantee rate limit accuracy.
     *
     * @param userId     The user attempting to upload
     * @param fileSize   Size of the file to upload in bytes
     * @param uploadType Type of upload (PROFILE_PHOTO, CONTENT, CAMPAIGN_MEDIA)
     * @return RateLimitResult with details about success or which limit was hit
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult checkAndRecordUpload(String userId, long fileSize, UploadType uploadType) {
        log.debug("GDPR: Operation=checkAndRecordUpload_atomic, FirebaseUID={}, FileSize={}, UploadType={}, Purpose=atomic_rate_limit",
                userId, fileSize, uploadType);

        // If dependencies are not available, allow uploads (fail open for resilience)
        if (redisTemplate == null || rateLimitProperties == null) {
            log.warn("GDPR: Operation=checkAndRecordUpload_noDependencies, FirebaseUID={}, Purpose=rate_limit_bypass", userId);
            return RateLimitResult.allowed(999, 999);
        }

        RateLimitProperties.Upload uploadConfig = rateLimitProperties.getUpload();

        // Check file size limit first - this doesn't need atomic operation
        if (fileSize > uploadConfig.getMaxFileSize()) {
            log.warn("GDPR: Operation=uploadBlocked_fileSize, FirebaseUID={}, FileSize={}, MaxAllowed={}, Purpose=file_size_enforcement",
                    userId, fileSize, uploadConfig.getMaxFileSize());
            return RateLimitResult.blocked(
                    String.format("File size exceeds maximum allowed size of %d MB",
                            uploadConfig.getMaxFileSize() / (1024 * 1024))
            );
        }

        // PROFILE_PHOTO exemption: Skip rate limits, only record storage
        if (uploadType == UploadType.PROFILE_PHOTO) {
            // Still need to check storage space
            if (!hasStorageSpace(userId, fileSize)) {
                log.warn("GDPR: Operation=profilePhotoBlocked_storageQuota, FirebaseUID={}, RequestedSize={}, Purpose=storage_quota_enforcement",
                        userId, fileSize);
                return RateLimitResult.blocked(
                        String.format("Storage limit exceeded. Maximum allowed: %d MB",
                                uploadConfig.getMaxUserStorage() / (1024 * 1024))
                );
            }
            // Only update storage (profile photos bypass rate count)
            updateUserStorage(userId, fileSize);
            log.info("GDPR: Operation=profilePhotoUpload_allowed, FirebaseUID={}, FileSize={}, Purpose=new_user_experience",
                    userId, fileSize);
            return RateLimitResult.allowed(uploadConfig.getPerHour(), uploadConfig.getPerDay());
        }

        // Calculate effective limits based on upload type and grace period
        int effectiveHourlyLimit = uploadConfig.getPerHour();
        int effectiveDailyLimit = uploadConfig.getPerDay();

        // Check if user is in grace period (first 24 hours)
        double gracePeriodMultiplier = getGracePeriodMultiplier(userId);
        if (gracePeriodMultiplier > 1.0) {
            effectiveHourlyLimit = (int) (effectiveHourlyLimit * gracePeriodMultiplier);
            effectiveDailyLimit = (int) (effectiveDailyLimit * gracePeriodMultiplier);
            log.info("GDPR: Operation=gracePeriodApplied_atomic, FirebaseUID={}, Multiplier={}, HourlyLimit={}, DailyLimit={}, Purpose=new_user_experience",
                    userId, gracePeriodMultiplier, effectiveHourlyLimit, effectiveDailyLimit);
        }

        // CAMPAIGN_MEDIA gets additional 2x on top of any grace period bonus
        if (uploadType == UploadType.CAMPAIGN_MEDIA) {
            effectiveHourlyLimit = effectiveHourlyLimit * 2;
            effectiveDailyLimit = effectiveDailyLimit * 2;
            log.info("GDPR: Operation=campaignMediaUpload_atomic, FirebaseUID={}, FileSize={}, HourlyLimit={}, DailyLimit={}, Purpose=campaign_creation",
                    userId, fileSize, effectiveHourlyLimit, effectiveDailyLimit);
        }

        // Prepare keys for Lua script
        String hourlyKey = String.format(USER_HOURLY_KEY, userId);
        String dailyKey = String.format(USER_DAILY_KEY, userId);
        String storageKey = String.format(USER_STORAGE_KEY, userId);
        java.util.List<String> keys = java.util.Arrays.asList(hourlyKey, dailyKey, GLOBAL_MINUTE_KEY, storageKey);

        long now = Instant.now().toEpochMilli();
        String uploadId = userId + ":" + now;

        try {
            // Execute atomic Lua script
            java.util.List<Long> result = redisTemplate.execute(
                checkAndRecordScript,
                keys,
                String.valueOf(now),                                    // ARGV[1] now
                String.valueOf(effectiveHourlyLimit),                   // ARGV[2] hourlyLimit
                String.valueOf(effectiveDailyLimit),                    // ARGV[3] dailyLimit
                String.valueOf(uploadConfig.getGlobalPerMinute()),      // ARGV[4] globalLimit
                String.valueOf(fileSize),                               // ARGV[5] fileSize
                String.valueOf(uploadConfig.getMaxUserStorage()),       // ARGV[6] maxStorage
                String.valueOf(TimeUnit.HOURS.toMillis(1)),             // ARGV[7] oneHourMs
                String.valueOf(TimeUnit.DAYS.toMillis(1)),              // ARGV[8] oneDayMs
                String.valueOf(TimeUnit.MINUTES.toMillis(1)),           // ARGV[9] oneMinuteMs
                uploadId,                                               // ARGV[10] uploadId
                String.valueOf(365)                                     // ARGV[11] storageTtlDays
            );

            if (result == null || result.size() < 4) {
                log.error("GDPR: Operation=checkAndRecordUpload_invalidResult, FirebaseUID={}, Purpose=error_recovery", userId);
                // Fail open - allow but log for investigation
                return RateLimitResult.allowed(999, 999);
            }

            long allowed = result.get(0);
            int remainingHourly = result.get(1).intValue();
            int remainingDaily = result.get(2).intValue();
            int errorCode = result.get(3).intValue();

            if (allowed == 1) {
                log.debug("GDPR: Operation=checkAndRecordUpload_success, FirebaseUID={}, RemainingHourly={}, RemainingDaily={}, Purpose=atomic_rate_limit",
                        userId, remainingHourly, remainingDaily);
                return RateLimitResult.allowed(remainingHourly, remainingDaily);
            }

            // Upload blocked - return appropriate error message based on error code
            String blockReason = switch (errorCode) {
                case 1 -> String.format("Hourly limit reached (0/%d uploads). Try again later.", effectiveHourlyLimit);
                case 2 -> String.format("Daily limit reached (0/%d uploads). Try again tomorrow.", effectiveDailyLimit);
                case 3 -> "Global rate limit exceeded. Try again in a minute.";
                case 4 -> String.format("Storage limit exceeded. Maximum allowed: %d MB",
                        uploadConfig.getMaxUserStorage() / (1024 * 1024));
                default -> "Rate limit exceeded. Please try again later.";
            };

            log.warn("GDPR: Operation=uploadBlocked_atomic, FirebaseUID={}, ErrorCode={}, Reason={}, Purpose=rate_limit_enforcement",
                    userId, errorCode, blockReason);
            return RateLimitResult.blocked(blockReason);

        } catch (Exception e) {
            log.error("GDPR: Operation=checkAndRecordUpload_error, FirebaseUID={}, Error={}, Purpose=error_recovery",
                    userId, e.getMessage(), e);
            // Fail open on error - allow upload but log for investigation
            return RateLimitResult.allowed(999, 999);
        }
    }

    /**
     * Updates user's storage usage after successful upload.
     * Think of this as updating their storage locker inventory.
     * Includes TTL for GDPR compliance - storage tracking expires after 1 year.
     */
    public void updateUserStorage(String userId, long fileSize) {
        log.debug("GDPR: Operation=updateUserStorage, FirebaseUID={}, FileSize={}, DataModified=storage_quota, Purpose=storage_accounting", userId, fileSize);

        if (redisTemplate == null) {
            log.warn("GDPR: Operation=updateUserStorage_failed, FirebaseUID={}, Error=redis_not_available, Purpose=storage_accounting", userId);
            return;
        }

        String key = String.format(USER_STORAGE_KEY, userId);
        redisTemplate.opsForValue().increment(key, fileSize);

        // GDPR: Set TTL on storage tracking key (1 year)
        // This ensures inactive users' data is automatically cleaned up
        // Active users will have their TTL refreshed on each upload
        redisTemplate.expire(key, 365, TimeUnit.DAYS);
    }

    /**
     * Decreases user's storage usage after file deletion.
     * This ensures storage quota is properly reclaimed when files are deleted.
     * CRITICAL: Without this, users would run out of quota permanently.
     *
     * Uses atomic Lua script to prevent TOCTOU race conditions.
     * The script atomically:
     * 1. Reads current value
     * 2. Calculates safe decrement (won't go below 0)
     * 3. Updates value
     * 4. Sets TTL for GDPR compliance
     *
     * @param userId   The user whose storage to decrease
     * @param fileSize Size of the deleted file in bytes
     */
    public void decreaseUserStorage(String userId, long fileSize) {
        log.info("GDPR: Operation=decreaseUserStorage, FirebaseUID={}, FileSize={}, DataModified=storage_quota, Purpose=storage_reclamation", userId, fileSize);

        if (redisTemplate == null) {
            log.warn("GDPR: Operation=decreaseUserStorage_failed, FirebaseUID={}, Error=redis_not_available, Purpose=storage_reclamation", userId);
            return;
        }

        if (fileSize <= 0) {
            log.warn("GDPR: Operation=decreaseUserStorage_skipped, FirebaseUID={}, FileSize={}, Reason=invalid_size, Purpose=storage_reclamation", userId, fileSize);
            return;
        }

        String key = String.format(USER_STORAGE_KEY, userId);

        try {
            // Use atomic Lua script to prevent TOCTOU race condition
            // The script atomically reads, calculates safe decrement, updates, and sets TTL
            long ttlSeconds = TimeUnit.DAYS.toSeconds(365); // 1 year TTL for GDPR compliance

            Long newValue = redisTemplate.execute(
                decreaseStorageScript,
                Collections.singletonList(key),
                String.valueOf(fileSize),
                String.valueOf(ttlSeconds)
            );

            log.debug("GDPR: Operation=decreaseUserStorage_success, FirebaseUID={}, RequestedDecrease={}, NewQuota={}, Purpose=storage_reclamation",
                userId, fileSize, newValue);
        } catch (Exception e) {
            log.error("GDPR: Operation=decreaseUserStorage_error, FirebaseUID={}, FileSize={}, Error={}, Purpose=storage_reclamation",
                userId, fileSize, e.getMessage(), e);
            throw new StorageTranslatableException("error.storage.quota_tracking_failed");
        }
    }

    /**
     * Checks if user has storage space available.
     * Handles corrupted storage values gracefully by failing open.
     *
     * SECURITY: Uses overflow-safe comparison to prevent integer overflow attacks.
     */
    public boolean hasStorageSpace(String userId, long requestedSize) {
        if (redisTemplate == null || rateLimitProperties == null) {
            log.warn("Dependencies not available - allowing storage check for user {}", userId);
            return true; // Fail open
        }

        String key = String.format(USER_STORAGE_KEY, userId);
        String currentUsage = redisTemplate.opsForValue().get(key);

        long used;
        try {
            used = currentUsage != null ? Long.parseLong(currentUsage) : 0;
        } catch (NumberFormatException e) {
            // Corrupted storage value - log warning and fail open
            log.error("GDPR: Operation=hasStorageSpace_corrupted, FirebaseUID={}, CorruptedValue={}, Purpose=storage_check_fallback",
                    userId, currentUsage);
            // Reset corrupted value to 0 for future operations WITH TTL to prevent memory leak
            redisTemplate.opsForValue().set(key, "0", 365, TimeUnit.DAYS);
            used = 0;
        }

        // SECURITY: Validate inputs to prevent overflow attacks
        // If storage value is negative (corrupted data), reset it to 0 and allow
        if (used < 0) {
            log.warn("GDPR: Operation=hasStorageSpace_invalid, FirebaseUID={}, Used={}, RequestedSize={}, Purpose=security",
                    userId, used, requestedSize);
            // Reset corrupted negative value to 0 to allow uploads to proceed
            redisTemplate.opsForValue().set(key, "0", 365, TimeUnit.DAYS);
            used = 0;
        }
        // Reject if requestedSize is negative (invalid input)
        if (requestedSize < 0) {
            log.warn("GDPR: Operation=hasStorageSpace_invalidRequest, FirebaseUID={}, RequestedSize={}, Purpose=security",
                    userId, requestedSize);
            return false;
        }

        // SECURITY: Overflow-safe comparison
        // Instead of (used + requestedSize) <= maxStorage which can overflow,
        // use: requestedSize <= (maxStorage - used) which is safe when used <= maxStorage
        long maxStorage = rateLimitProperties.getUpload().getMaxUserStorage();
        if (used > maxStorage) {
            // Already over quota (shouldn't happen, but handle defensively)
            return false;
        }
        long remaining = maxStorage - used;
        return requestedSize <= remaining;
    }

    /**
     * Gets detailed rate limit status for a user.
     * Useful for showing users their current limits in the UI.
     */
    public RateLimitStatus getUserStatus(String userId) {
        log.debug("GDPR: Operation=getUserRateLimitStatus, FirebaseUID={}, DataAccessed=rate_limit_counters,storage_quota, Purpose=status_retrieval", userId);

        if (redisTemplate == null || rateLimitProperties == null) {
            log.warn("GDPR: Operation=getUserStatus_noDependencies, FirebaseUID={}, Purpose=status_retrieval", userId);
            return new RateLimitStatus(0, 999, 0, 999, 0, Long.MAX_VALUE);
        }

        long now = Instant.now().toEpochMilli();
        RateLimitProperties.Upload uploadConfig = rateLimitProperties.getUpload();

        int hourlyCount = checkUserHourlyLimit(userId, now);
        int dailyCount = checkUserDailyLimit(userId, now);

        String storageKey = String.format(USER_STORAGE_KEY, userId);
        String storageStr = redisTemplate.opsForValue().get(storageKey);

        long storageUsed;
        try {
            storageUsed = storageStr != null ? Long.parseLong(storageStr) : 0;
        } catch (NumberFormatException e) {
            log.error("GDPR: Operation=getUserStatus_corrupted, FirebaseUID={}, CorruptedValue={}, Purpose=status_retrieval",
                    userId, storageStr);
            storageUsed = 0;
        }

        return new RateLimitStatus(
                hourlyCount,
                uploadConfig.getPerHour(),
                dailyCount,
                uploadConfig.getPerDay(),
                storageUsed,
                uploadConfig.getMaxUserStorage()
        );
    }

    /**
     * Get remaining uploads for the current hour
     *
     * @param userId User ID
     * @return Number of remaining uploads allowed this hour
     */
    public int getRemainingHourlyUploads(String userId) {
        if (rateLimitProperties == null) {
            return 999; // Fail open
        }

        long now = Instant.now().toEpochMilli();
        int used = checkUserHourlyLimit(userId, now);
        return Math.max(0, rateLimitProperties.getUpload().getPerHour() - used);
    }

    /**
     * Get remaining uploads for the current day
     *
     * @param userId User ID
     * @return Number of remaining uploads allowed today
     */
    public int getRemainingDailyUploads(String userId) {
        if (rateLimitProperties == null) {
            return 999; // Fail open
        }

        long now = Instant.now().toEpochMilli();
        int used = checkUserDailyLimit(userId, now);
        return Math.max(0, rateLimitProperties.getUpload().getPerDay() - used);
    }

    // Private helper methods

    private boolean checkGlobalLimit(long now) {
        if (zSetOps == null || rateLimitProperties == null) {
            return true; // Fail open
        }

        long oneMinuteAgo = now - TimeUnit.MINUTES.toMillis(1);
        Long count = zSetOps.count(GLOBAL_MINUTE_KEY, oneMinuteAgo, now);
        return count == null || count < rateLimitProperties.getUpload().getGlobalPerMinute();
    }

    private int checkUserHourlyLimit(String userId, long now) {
        if (zSetOps == null) {
            return 0; // Fail open
        }

        String key = String.format(USER_HOURLY_KEY, userId);
        long oneHourAgo = now - TimeUnit.HOURS.toMillis(1);
        Long count = zSetOps.count(key, oneHourAgo, now);
        return count != null ? count.intValue() : 0;
    }

    private int checkUserDailyLimit(String userId, long now) {
        if (zSetOps == null) {
            return 0; // Fail open
        }

        String key = String.format(USER_DAILY_KEY, userId);
        long oneDayAgo = now - TimeUnit.DAYS.toMillis(1);
        Long count = zSetOps.count(key, oneDayAgo, now);
        return count != null ? count.intValue() : 0;
    }

    private void cleanupOldEntries(String key, long beforeTimestamp) {
        if (zSetOps == null) {
            return;
        }

        zSetOps.removeRangeByScore(key, 0, beforeTimestamp);
    }

    /**
     * Gets the grace period multiplier for a user based on when they first uploaded.
     * New users (within grace period) get relaxed rate limits.
     *
     * @param userId The user to check
     * @return Multiplier (1.0 = no bonus, > 1.0 = grace period bonus)
     */
    private double getGracePeriodMultiplier(String userId) {
        if (redisTemplate == null || rateLimitProperties == null) {
            return 1.0; // No bonus if dependencies unavailable
        }

        RateLimitProperties.GracePeriod gracePeriod = rateLimitProperties.getGracePeriod();
        if (gracePeriod == null || !gracePeriod.isEnabled()) {
            return 1.0; // Grace period disabled
        }

        String key = String.format(USER_FIRST_UPLOAD_KEY, userId);
        String firstUploadStr = redisTemplate.opsForValue().get(key);

        if (firstUploadStr == null) {
            // First time user - record their first upload timestamp
            long now = Instant.now().toEpochMilli();
            redisTemplate.opsForValue().set(key, String.valueOf(now));
            // Set TTL to grace period duration + 1 day buffer
            redisTemplate.expire(key, gracePeriod.getDurationHours() + 24, TimeUnit.HOURS);
            log.info("GDPR: Operation=newUserDetected, FirebaseUID={}, Purpose=grace_period_tracking", userId);
            return gracePeriod.getLimitMultiplier();
        }

        try {
            long firstUploadTime = Long.parseLong(firstUploadStr);
            long gracePeriodMs = TimeUnit.HOURS.toMillis(gracePeriod.getDurationHours());
            long now = Instant.now().toEpochMilli();

            if (now - firstUploadTime < gracePeriodMs) {
                // Still within grace period
                long hoursRemaining = (gracePeriodMs - (now - firstUploadTime)) / MILLIS_PER_HOUR;
                log.debug("GDPR: Operation=gracePeriodActive, FirebaseUID={}, HoursRemaining={}, Purpose=new_user_experience",
                        userId, hoursRemaining);
                return gracePeriod.getLimitMultiplier();
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid first upload timestamp for user {}", userId);
        }

        return 1.0; // Grace period expired or invalid
    }

    // Result classes

    public static class RateLimitResult {
        private final boolean allowed;
        private final String message;
        private final Integer remainingHourly;
        private final Integer remainingDaily;

        private RateLimitResult(boolean allowed, String message,
                                Integer remainingHourly, Integer remainingDaily) {
            this.allowed = allowed;
            this.message = message;
            this.remainingHourly = remainingHourly;
            this.remainingDaily = remainingDaily;
        }

        public static RateLimitResult allowed(int remainingHourly, int remainingDaily) {
            return new RateLimitResult(true, "Upload allowed",
                    remainingHourly, remainingDaily);
        }

        public static RateLimitResult blocked(String reason) {
            return new RateLimitResult(false, reason, null, null);
        }

        // Getters
        public boolean isAllowed() {
            return allowed;
        }

        public String getMessage() {
            return message;
        }

        public Integer getRemainingHourly() {
            return remainingHourly;
        }

        public Integer getRemainingDaily() {
            return remainingDaily;
        }
    }

    public static class RateLimitStatus {
        private final int hourlyUsed;
        private final int hourlyLimit;
        private final int dailyUsed;
        private final int dailyLimit;
        private final long storageUsed;
        private final long storageLimit;

        public RateLimitStatus(int hourlyUsed, int hourlyLimit,
                               int dailyUsed, int dailyLimit,
                               long storageUsed, long storageLimit) {
            this.hourlyUsed = hourlyUsed;
            this.hourlyLimit = hourlyLimit;
            this.dailyUsed = dailyUsed;
            this.dailyLimit = dailyLimit;
            this.storageUsed = storageUsed;
            this.storageLimit = storageLimit;
        }

        // Getters for all fields
        public int getHourlyUsed() {
            return hourlyUsed;
        }

        public int getHourlyLimit() {
            return hourlyLimit;
        }

        public int getDailyUsed() {
            return dailyUsed;
        }

        public int getDailyLimit() {
            return dailyLimit;
        }

        public long getStorageUsed() {
            return storageUsed;
        }

        public long getStorageLimit() {
            return storageLimit;
        }

        public double getStorageUsedMB() {
            return storageUsed / (1024.0 * 1024.0);
        }

        public double getStorageLimitMB() {
            return storageLimit / (1024.0 * 1024.0);
        }
    }
}
