package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimitProperties;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService.RateLimitResult;
import com.sm.instagram.platform.storage.service.StorageRateLimitService.RateLimitStatus;
import com.sm.instagram.platform.storage.service.StorageRateLimitService.UploadType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StorageRateLimitService.
 * Tests rate limiting logic, delegation to Redis, and fail-open behavior.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StorageRateLimitService Unit Tests")
class StorageRateLimitServiceUnitTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RateLimitProperties rateLimitProperties;

    @Mock
    private RateLimitProperties.Upload uploadConfig;

    @Mock
    private RateLimitProperties.GracePeriod gracePeriodConfig;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private StorageRateLimitService service;

    private static final String USER_ID = "firebase-uid-123";
    private static final long FILE_SIZE = 1024L; // 1KB
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024L; // 100MB
    private static final long MAX_USER_STORAGE = 1024 * 1024 * 1024L; // 1GB
    private static final int HOURLY_LIMIT = 30;
    private static final int DAILY_LIMIT = 100;
    private static final int GLOBAL_PER_MINUTE = 100;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(rateLimitProperties.getUpload()).thenReturn(uploadConfig);
        when(rateLimitProperties.getGracePeriod()).thenReturn(gracePeriodConfig);

        // Default upload config
        when(uploadConfig.getMaxFileSize()).thenReturn(MAX_FILE_SIZE);
        when(uploadConfig.getMaxUserStorage()).thenReturn(MAX_USER_STORAGE);
        when(uploadConfig.getPerHour()).thenReturn(HOURLY_LIMIT);
        when(uploadConfig.getPerDay()).thenReturn(DAILY_LIMIT);
        when(uploadConfig.getGlobalPerMinute()).thenReturn(GLOBAL_PER_MINUTE);

        // Default grace period config (disabled)
        when(gracePeriodConfig.isEnabled()).thenReturn(false);
        when(gracePeriodConfig.getDurationHours()).thenReturn(24);
        when(gracePeriodConfig.getLimitMultiplier()).thenReturn(2.0);

        service = new StorageRateLimitService(redisTemplate, rateLimitProperties);
    }

    @Nested
    @DisplayName("Constructor and Initialization")
    class ConstructorTests {

        @Test
        @DisplayName("should initialize with null redisTemplate (fail-open mode)")
        void shouldInitializeWithNullRedisTemplate() {
            // When
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, rateLimitProperties);

            // Then - should not throw
            assertThat(failOpenService).isNotNull();
        }

        @Test
        @DisplayName("should initialize with null rateLimitProperties (fail-open mode)")
        void shouldInitializeWithNullRateLimitProperties() {
            // When
            StorageRateLimitService failOpenService = new StorageRateLimitService(redisTemplate, null);

            // Then - should not throw
            assertThat(failOpenService).isNotNull();
        }

        @Test
        @DisplayName("should initialize with both null (fail-open mode)")
        void shouldInitializeWithBothNull() {
            // When
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, null);

            // Then - should not throw
            assertThat(failOpenService).isNotNull();
        }
    }

    @Nested
    @DisplayName("UploadType Enum")
    class UploadTypeEnumTests {

        @Test
        @DisplayName("should have PROFILE_PHOTO type")
        void shouldHaveProfilePhotoType() {
            assertThat(UploadType.PROFILE_PHOTO).isNotNull();
            assertThat(UploadType.PROFILE_PHOTO.name()).isEqualTo("PROFILE_PHOTO");
        }

        @Test
        @DisplayName("should have CONTENT type")
        void shouldHaveContentType() {
            assertThat(UploadType.CONTENT).isNotNull();
            assertThat(UploadType.CONTENT.name()).isEqualTo("CONTENT");
        }

        @Test
        @DisplayName("should have CAMPAIGN_MEDIA type")
        void shouldHaveCampaignMediaType() {
            assertThat(UploadType.CAMPAIGN_MEDIA).isNotNull();
            assertThat(UploadType.CAMPAIGN_MEDIA.name()).isEqualTo("CAMPAIGN_MEDIA");
        }

        @Test
        @DisplayName("should have exactly 3 upload types")
        void shouldHaveExactlyThreeUploadTypes() {
            assertThat(UploadType.values()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("checkUploadAllowed - Without UploadType")
    class CheckUploadAllowedWithoutTypeTests {

        @Test
        @DisplayName("should default to CONTENT upload type")
        void shouldDefaultToContentUploadType() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should allow upload when within all limits")
        void shouldAllowUploadWhenWithinLimits() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(HOURLY_LIMIT);
            assertThat(result.getRemainingDaily()).isEqualTo(DAILY_LIMIT);
        }
    }

    @Nested
    @DisplayName("checkUploadAllowed - With UploadType")
    class CheckUploadAllowedWithTypeTests {

        @Test
        @DisplayName("should allow upload when dependencies are null (fail-open)")
        void shouldFailOpenWhenDependenciesNull() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, null);

            // When
            RateLimitResult result = failOpenService.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(999);
            assertThat(result.getRemainingDaily()).isEqualTo(999);
        }

        @Test
        @DisplayName("should block when file size exceeds maximum")
        void shouldBlockWhenFileSizeExceedsMaximum() {
            // Given
            long oversizedFile = MAX_FILE_SIZE + 1;

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, oversizedFile, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("File size exceeds maximum");
        }

        @Test
        @DisplayName("should block when storage quota exceeded")
        void shouldBlockWhenStorageQuotaExceeded() {
            // Given
            when(valueOperations.get(anyString())).thenReturn(String.valueOf(MAX_USER_STORAGE));

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Storage limit exceeded");
        }

        @Test
        @DisplayName("should allow PROFILE_PHOTO and skip rate limits")
        void shouldAllowProfilePhotoAndSkipRateLimits() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.PROFILE_PHOTO);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(HOURLY_LIMIT);
            assertThat(result.getRemainingDaily()).isEqualTo(DAILY_LIMIT);
        }

        @Test
        @DisplayName("should block when global rate limit exceeded")
        void shouldBlockWhenGlobalRateLimitExceeded() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(eq("rate_limit:upload:global:minute"), anyDouble(), anyDouble()))
                    .thenReturn((long) GLOBAL_PER_MINUTE);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Global rate limit exceeded");
        }

        @Test
        @DisplayName("should block when hourly limit exceeded")
        void shouldBlockWhenHourlyLimitExceeded() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(eq("rate_limit:upload:global:minute"), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble()))
                    .thenReturn((long) HOURLY_LIMIT);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Hourly limit reached");
        }

        @Test
        @DisplayName("should block when daily limit exceeded")
        void shouldBlockWhenDailyLimitExceeded() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(eq("rate_limit:upload:global:minute"), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.count(contains("daily"), anyDouble(), anyDouble()))
                    .thenReturn((long) DAILY_LIMIT);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Daily limit reached");
        }

        @Test
        @DisplayName("should return correct remaining counts when allowed")
        void shouldReturnCorrectRemainingCounts() {
            // Given
            int hourlyUsed = 5;
            int dailyUsed = 10;
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(eq("rate_limit:upload:global:minute"), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble()))
                    .thenReturn((long) hourlyUsed);
            when(zSetOperations.count(contains("daily"), anyDouble(), anyDouble()))
                    .thenReturn((long) dailyUsed);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(HOURLY_LIMIT - hourlyUsed);
            assertThat(result.getRemainingDaily()).isEqualTo(DAILY_LIMIT - dailyUsed);
        }
    }

    @Nested
    @DisplayName("checkUploadAllowed - CAMPAIGN_MEDIA Type")
    class CampaignMediaTests {

        @Test
        @DisplayName("should apply 2x multiplier for CAMPAIGN_MEDIA")
        void shouldApply2xMultiplierForCampaignMedia() {
            // Given - uses limit that would block CONTENT but not CAMPAIGN_MEDIA
            int hourlyUsed = HOURLY_LIMIT; // At limit for CONTENT, but 2x limit for CAMPAIGN
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(eq("rate_limit:upload:global:minute"), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble()))
                    .thenReturn((long) hourlyUsed);
            when(zSetOperations.count(contains("daily"), anyDouble(), anyDouble()))
                    .thenReturn(0L);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CAMPAIGN_MEDIA);

            // Then - CAMPAIGN_MEDIA has 2x limit so should be allowed
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should block CAMPAIGN_MEDIA when 2x limit exceeded")
        void shouldBlockCampaignMediaWhen2xLimitExceeded() {
            // Given
            int hourlyUsed = HOURLY_LIMIT * 2; // At the 2x limit
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(eq("rate_limit:upload:global:minute"), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble()))
                    .thenReturn((long) hourlyUsed);
            when(zSetOperations.count(contains("daily"), anyDouble(), anyDouble()))
                    .thenReturn(0L);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CAMPAIGN_MEDIA);

            // Then
            assertThat(result.isAllowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Grace Period Tests")
    class GracePeriodTests {

        @Test
        @DisplayName("should apply grace period multiplier for new users")
        void shouldApplyGracePeriodMultiplier() {
            // Given - enable grace period
            when(gracePeriodConfig.isEnabled()).thenReturn(true);
            when(valueOperations.get(contains("first_upload"))).thenReturn(null); // New user

            when(valueOperations.get(contains("storage"))).thenReturn("0");
            when(zSetOperations.count(eq("rate_limit:upload:global:minute"), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble()))
                    .thenReturn(0L);
            when(zSetOperations.count(contains("daily"), anyDouble(), anyDouble()))
                    .thenReturn(0L);

            // Recreate service to pick up the new grace period setting
            service = new StorageRateLimitService(redisTemplate, rateLimitProperties);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
            // First upload timestamp should be set
            verify(valueOperations).set(contains("first_upload"), anyString());
        }

        @Test
        @DisplayName("should not apply grace period when disabled")
        void shouldNotApplyGracePeriodWhenDisabled() {
            // Given - grace period disabled (default)
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
            // First upload timestamp should NOT be set
            verify(valueOperations, never()).set(contains("first_upload"), anyString());
        }
    }

    @Nested
    @DisplayName("recordUpload")
    class RecordUploadTests {

        @Test
        @DisplayName("should record upload in Redis")
        void shouldRecordUploadInRedis() {
            // When
            service.recordUpload(USER_ID, FILE_SIZE);

            // Then
            verify(zSetOperations, times(3)).add(anyString(), anyString(), anyDouble());
            // 3 for rate limit buckets + 1 for storage update = 4 expire calls
            verify(redisTemplate, times(4)).expire(anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("should update user storage")
        void shouldUpdateUserStorage() {
            // When
            service.recordUpload(USER_ID, FILE_SIZE);

            // Then
            verify(valueOperations).increment(contains("storage"), eq(FILE_SIZE));
        }

        @Test
        @DisplayName("should cleanup old entries")
        void shouldCleanupOldEntries() {
            // When
            service.recordUpload(USER_ID, FILE_SIZE);

            // Then
            verify(zSetOperations, times(3)).removeRangeByScore(anyString(), eq(0.0), anyDouble());
        }

        @Test
        @DisplayName("should not throw when Redis unavailable (fail-open)")
        void shouldNotThrowWhenRedisUnavailable() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, null);

            // When/Then - should not throw
            failOpenService.recordUpload(USER_ID, FILE_SIZE);
        }

        @Test
        @DisplayName("should throw StorageTranslatableException on Redis error")
        void shouldThrowOnRedisError() {
            // Given
            doThrow(new RuntimeException("Redis error")).when(zSetOperations).add(anyString(), anyString(), anyDouble());

            // When/Then
            assertThatThrownBy(() -> service.recordUpload(USER_ID, FILE_SIZE))
                    .isInstanceOf(StorageTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("checkAndRecordUpload (Atomic)")
    class CheckAndRecordUploadTests {

        @Test
        @DisplayName("should fail open when dependencies unavailable")
        void shouldFailOpenWhenDependenciesUnavailable() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, null);

            // When
            RateLimitResult result = failOpenService.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(999);
            assertThat(result.getRemainingDaily()).isEqualTo(999);
        }

        @Test
        @DisplayName("should block when file size exceeds maximum")
        void shouldBlockWhenFileSizeExceedsMaximum() {
            // Given
            long oversizedFile = MAX_FILE_SIZE + 1;

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, oversizedFile, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("File size exceeds maximum");
        }

        @Test
        @DisplayName("should handle PROFILE_PHOTO specially")
        void shouldHandleProfilePhotoSpecially() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.PROFILE_PHOTO);

            // Then
            assertThat(result.isAllowed()).isTrue();
            verify(valueOperations).increment(contains("storage"), eq(FILE_SIZE));
        }

        @Test
        @DisplayName("should block PROFILE_PHOTO when storage exceeded")
        void shouldBlockProfilePhotoWhenStorageExceeded() {
            // Given
            when(valueOperations.get(contains("storage"))).thenReturn(String.valueOf(MAX_USER_STORAGE));

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.PROFILE_PHOTO);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Storage limit exceeded");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should execute atomic Lua script for CONTENT type")
        void shouldExecuteAtomicLuaScriptForContentType() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            List<Long> scriptResult = Arrays.asList(1L, 29L, 99L, 0L); // allowed, hourlyRemaining, dailyRemaining, errorCode
            // Use doReturn for varargs matching
            doReturn(scriptResult).when(redisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            );

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(29);
            assertThat(result.getRemainingDaily()).isEqualTo(99);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return blocked for hourly limit exceeded (errorCode 1)")
        void shouldReturnBlockedForHourlyLimitExceeded() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            List<Long> scriptResult = Arrays.asList(0L, 0L, 90L, 1L); // blocked, errorCode 1 = hourly
            doReturn(scriptResult).when(redisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            );

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Hourly limit reached");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return blocked for daily limit exceeded (errorCode 2)")
        void shouldReturnBlockedForDailyLimitExceeded() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            List<Long> scriptResult = Arrays.asList(0L, 25L, 0L, 2L); // blocked, errorCode 2 = daily
            doReturn(scriptResult).when(redisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            );

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Daily limit reached");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return blocked for global limit exceeded (errorCode 3)")
        void shouldReturnBlockedForGlobalLimitExceeded() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            List<Long> scriptResult = Arrays.asList(0L, 20L, 80L, 3L); // blocked, errorCode 3 = global
            doReturn(scriptResult).when(redisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            );

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Global rate limit exceeded");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return blocked for storage exceeded (errorCode 4)")
        void shouldReturnBlockedForStorageExceeded() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            List<Long> scriptResult = Arrays.asList(0L, 20L, 80L, 4L); // blocked, errorCode 4 = storage
            doReturn(scriptResult).when(redisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            );

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Storage limit exceeded");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should fail open on invalid script result")
        void shouldFailOpenOnInvalidScriptResult() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            doReturn(null).when(redisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            );

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(999);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should fail open on script exception")
        void shouldFailOpenOnScriptException() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            doThrow(new RuntimeException("Redis error")).when(redisTemplate).execute(
                    any(DefaultRedisScript.class),
                    anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            );

            // When
            RateLimitResult result = service.checkAndRecordUpload(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(999);
        }
    }

    @Nested
    @DisplayName("updateUserStorage")
    class UpdateUserStorageTests {

        @Test
        @DisplayName("should increment storage in Redis")
        void shouldIncrementStorageInRedis() {
            // When
            service.updateUserStorage(USER_ID, FILE_SIZE);

            // Then
            verify(valueOperations).increment(contains("storage"), eq(FILE_SIZE));
            verify(redisTemplate).expire(contains("storage"), eq(365L), eq(TimeUnit.DAYS));
        }

        @Test
        @DisplayName("should not throw when Redis unavailable")
        void shouldNotThrowWhenRedisUnavailable() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, null);

            // When/Then - should not throw
            failOpenService.updateUserStorage(USER_ID, FILE_SIZE);
        }
    }

    @Nested
    @DisplayName("decreaseUserStorage")
    class DecreaseUserStorageTests {

        @Test
        @DisplayName("should execute atomic Lua script for decrement")
        void shouldExecuteAtomicLuaScript() {
            // Given
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenReturn(1024L);

            // When
            service.decreaseUserStorage(USER_ID, FILE_SIZE);

            // Then
            verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), any(), any());
        }

        @Test
        @DisplayName("should not throw when Redis unavailable")
        void shouldNotThrowWhenRedisUnavailable() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, null);

            // When/Then - should not throw
            failOpenService.decreaseUserStorage(USER_ID, FILE_SIZE);
        }

        @Test
        @DisplayName("should skip for zero or negative file size")
        void shouldSkipForZeroOrNegativeFileSize() {
            // When
            service.decreaseUserStorage(USER_ID, 0);
            service.decreaseUserStorage(USER_ID, -100);

            // Then
            verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), any());
        }

        @Test
        @DisplayName("should throw StorageTranslatableException on Redis error")
        void shouldThrowOnRedisError() {
            // Given
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                    .thenThrow(new RuntimeException("Redis error"));

            // When/Then
            assertThatThrownBy(() -> service.decreaseUserStorage(USER_ID, FILE_SIZE))
                    .isInstanceOf(StorageTranslatableException.class);
        }
    }

    @Nested
    @DisplayName("hasStorageSpace")
    class HasStorageSpaceTests {

        @Test
        @DisplayName("should return true when storage space available")
        void shouldReturnTrueWhenStorageAvailable() {
            // Given
            when(valueOperations.get(contains("storage"))).thenReturn("0");

            // When
            boolean result = service.hasStorageSpace(USER_ID, FILE_SIZE);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when storage quota exceeded")
        void shouldReturnFalseWhenStorageExceeded() {
            // Given
            when(valueOperations.get(contains("storage"))).thenReturn(String.valueOf(MAX_USER_STORAGE));

            // When
            boolean result = service.hasStorageSpace(USER_ID, FILE_SIZE);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when dependencies null (fail-open)")
        void shouldReturnTrueWhenDependenciesNull() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, null);

            // When
            boolean result = failOpenService.hasStorageSpace(USER_ID, FILE_SIZE);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle corrupted storage value gracefully")
        void shouldHandleCorruptedStorageValue() {
            // Given
            when(valueOperations.get(contains("storage"))).thenReturn("not_a_number");

            // When
            boolean result = service.hasStorageSpace(USER_ID, FILE_SIZE);

            // Then
            assertThat(result).isTrue();
            // Should reset corrupted value
            verify(valueOperations).set(contains("storage"), eq("0"), eq(365L), eq(TimeUnit.DAYS));
        }

        @Test
        @DisplayName("should allow and reset negative storage values (treat as corruption)")
        void shouldAllowAndResetNegativeStorageValues() {
            // Given - negative storage value indicates data corruption
            when(valueOperations.get(contains("storage"))).thenReturn("-1000");

            // When
            boolean result = service.hasStorageSpace(USER_ID, FILE_SIZE);

            // Then - allow upload (don't penalize user for data corruption)
            assertThat(result).isTrue();
            // Should reset corrupted value to 0
            verify(valueOperations).set(contains("storage"), eq("0"), eq(365L), eq(TimeUnit.DAYS));
        }

        @Test
        @DisplayName("should reject negative requested size")
        void shouldRejectNegativeRequestedSize() {
            // Given
            when(valueOperations.get(contains("storage"))).thenReturn("0");

            // When
            boolean result = service.hasStorageSpace(USER_ID, -100);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should use overflow-safe comparison")
        void shouldUseOverflowSafeComparison() {
            // Given - storage at max, requesting any size should fail
            when(valueOperations.get(contains("storage"))).thenReturn(String.valueOf(MAX_USER_STORAGE));

            // When
            boolean result = service.hasStorageSpace(USER_ID, 1);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should allow exactly remaining space")
        void shouldAllowExactlyRemainingSpace() {
            // Given
            long usedStorage = MAX_USER_STORAGE - FILE_SIZE;
            when(valueOperations.get(contains("storage"))).thenReturn(String.valueOf(usedStorage));

            // When
            boolean result = service.hasStorageSpace(USER_ID, FILE_SIZE);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getUserStatus")
    class GetUserStatusTests {

        @Test
        @DisplayName("should return correct status")
        void shouldReturnCorrectStatus() {
            // Given
            when(valueOperations.get(contains("storage"))).thenReturn("1048576"); // 1MB
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble())).thenReturn(5L);
            when(zSetOperations.count(contains("daily"), anyDouble(), anyDouble())).thenReturn(10L);

            // When
            RateLimitStatus status = service.getUserStatus(USER_ID);

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(5);
            assertThat(status.getHourlyLimit()).isEqualTo(HOURLY_LIMIT);
            assertThat(status.getDailyUsed()).isEqualTo(10);
            assertThat(status.getDailyLimit()).isEqualTo(DAILY_LIMIT);
            assertThat(status.getStorageUsed()).isEqualTo(1048576L);
            assertThat(status.getStorageLimit()).isEqualTo(MAX_USER_STORAGE);
        }

        @Test
        @DisplayName("should return default status when dependencies null")
        void shouldReturnDefaultStatusWhenDependenciesNull() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(null, null);

            // When
            RateLimitStatus status = failOpenService.getUserStatus(USER_ID);

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(0);
            assertThat(status.getHourlyLimit()).isEqualTo(999);
            assertThat(status.getDailyUsed()).isEqualTo(0);
            assertThat(status.getDailyLimit()).isEqualTo(999);
            assertThat(status.getStorageLimit()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle corrupted storage value")
        void shouldHandleCorruptedStorageValue() {
            // Given
            when(valueOperations.get(contains("storage"))).thenReturn("corrupted");
            when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

            // When
            RateLimitStatus status = service.getUserStatus(USER_ID);

            // Then
            assertThat(status.getStorageUsed()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getRemainingHourlyUploads")
    class GetRemainingHourlyUploadsTests {

        @Test
        @DisplayName("should return correct remaining hourly uploads")
        void shouldReturnCorrectRemainingHourlyUploads() {
            // Given
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble())).thenReturn(10L);

            // When
            int remaining = service.getRemainingHourlyUploads(USER_ID);

            // Then
            assertThat(remaining).isEqualTo(HOURLY_LIMIT - 10);
        }

        @Test
        @DisplayName("should return 999 when dependencies unavailable")
        void shouldReturn999WhenDependenciesUnavailable() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(redisTemplate, null);

            // When
            int remaining = failOpenService.getRemainingHourlyUploads(USER_ID);

            // Then
            assertThat(remaining).isEqualTo(999);
        }

        @Test
        @DisplayName("should return 0 when over limit")
        void shouldReturnZeroWhenOverLimit() {
            // Given
            when(zSetOperations.count(contains("hourly"), anyDouble(), anyDouble())).thenReturn(100L);

            // When
            int remaining = service.getRemainingHourlyUploads(USER_ID);

            // Then
            assertThat(remaining).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getRemainingDailyUploads")
    class GetRemainingDailyUploadsTests {

        @Test
        @DisplayName("should return correct remaining daily uploads")
        void shouldReturnCorrectRemainingDailyUploads() {
            // Given
            when(zSetOperations.count(contains("daily"), anyDouble(), anyDouble())).thenReturn(25L);

            // When
            int remaining = service.getRemainingDailyUploads(USER_ID);

            // Then
            assertThat(remaining).isEqualTo(DAILY_LIMIT - 25);
        }

        @Test
        @DisplayName("should return 999 when dependencies unavailable")
        void shouldReturn999WhenDependenciesUnavailable() {
            // Given
            StorageRateLimitService failOpenService = new StorageRateLimitService(redisTemplate, null);

            // When
            int remaining = failOpenService.getRemainingDailyUploads(USER_ID);

            // Then
            assertThat(remaining).isEqualTo(999);
        }

        @Test
        @DisplayName("should return 0 when over limit")
        void shouldReturnZeroWhenOverLimit() {
            // Given
            when(zSetOperations.count(contains("daily"), anyDouble(), anyDouble())).thenReturn(200L);

            // When
            int remaining = service.getRemainingDailyUploads(USER_ID);

            // Then
            assertThat(remaining).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("RateLimitResult")
    class RateLimitResultTests {

        @Test
        @DisplayName("should create allowed result with remaining counts")
        void shouldCreateAllowedResultWithRemainingCounts() {
            // When
            RateLimitResult result = RateLimitResult.allowed(10, 50);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Upload allowed");
            assertThat(result.getRemainingHourly()).isEqualTo(10);
            assertThat(result.getRemainingDaily()).isEqualTo(50);
        }

        @Test
        @DisplayName("should create blocked result with reason")
        void shouldCreateBlockedResultWithReason() {
            // When
            RateLimitResult result = RateLimitResult.blocked("Test reason");

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Test reason");
            assertThat(result.getRemainingHourly()).isNull();
            assertThat(result.getRemainingDaily()).isNull();
        }
    }

    @Nested
    @DisplayName("RateLimitStatus")
    class RateLimitStatusTests {

        @Test
        @DisplayName("should create status with all fields")
        void shouldCreateStatusWithAllFields() {
            // When
            RateLimitStatus status = new RateLimitStatus(5, 30, 10, 100, 1048576L, 1073741824L);

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(5);
            assertThat(status.getHourlyLimit()).isEqualTo(30);
            assertThat(status.getDailyUsed()).isEqualTo(10);
            assertThat(status.getDailyLimit()).isEqualTo(100);
            assertThat(status.getStorageUsed()).isEqualTo(1048576L);
            assertThat(status.getStorageLimit()).isEqualTo(1073741824L);
        }

        @Test
        @DisplayName("should calculate storage in MB correctly")
        void shouldCalculateStorageInMBCorrectly() {
            // Given
            long usedBytes = 10 * 1024 * 1024; // 10MB
            long limitBytes = 1024 * 1024 * 1024; // 1GB

            // When
            RateLimitStatus status = new RateLimitStatus(0, 0, 0, 0, usedBytes, limitBytes);

            // Then
            assertThat(status.getStorageUsedMB()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.01));
            assertThat(status.getStorageLimitMB()).isCloseTo(1024.0, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle null zSetOps in checkGlobalLimit")
        void shouldHandleNullZSetOpsInCheckGlobalLimit() {
            // Given
            StorageRateLimitService serviceWithNullZSet = new StorageRateLimitService(null, rateLimitProperties);

            // When - checkUploadAllowed internally calls checkGlobalLimit
            RateLimitResult result = serviceWithNullZSet.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then - should fail open
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle null count from zSetOperations")
        void shouldHandleNullCountFromZSetOperations() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(null);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle file size exactly at maximum")
        void shouldHandleFileSizeExactlyAtMaximum() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, MAX_FILE_SIZE, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(UploadType.class)
        @DisplayName("should handle all upload types")
        void shouldHandleAllUploadTypes(UploadType uploadType) {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, FILE_SIZE, uploadType);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 1, 100, 1000, 1024 * 1024, 100 * 1024 * 1024 - 1})
        @DisplayName("should handle various valid file sizes")
        void shouldHandleVariousValidFileSizes(long fileSize) {
            // Given
            when(valueOperations.get(anyString())).thenReturn("0");
            when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

            // When
            RateLimitResult result = service.checkUploadAllowed(USER_ID, fileSize, UploadType.CONTENT);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }
    }
}
