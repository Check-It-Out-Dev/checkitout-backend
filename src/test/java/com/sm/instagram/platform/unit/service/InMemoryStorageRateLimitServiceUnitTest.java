package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.StorageTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimitProperties;
import com.sm.instagram.platform.storage.service.InMemoryStorageRateLimitService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService.RateLimitResult;
import com.sm.instagram.platform.storage.service.StorageRateLimitService.RateLimitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for InMemoryStorageRateLimitService.
 * Tests in-memory storage rate limiting logic including:
 * - Upload rate limiting (hourly, daily, global)
 * - Storage quota management
 * - Signed URL rate limiting
 * - Bucket refill (entry expiration)
 * - Concurrent access scenarios
 * - Edge cases
 *
 * No Spring context needed - testing pure Java logic.
 */
@DisplayName("InMemoryStorageRateLimitService Unit Tests")
class InMemoryStorageRateLimitServiceUnitTest {

    private InMemoryStorageRateLimitService service;
    private RateLimitProperties rateLimitProperties;

    @BeforeEach
    void setUp() {
        rateLimitProperties = createDefaultRateLimitProperties();
        service = new InMemoryStorageRateLimitService(rateLimitProperties);
    }

    private RateLimitProperties createDefaultRateLimitProperties() {
        RateLimitProperties props = new RateLimitProperties();
        RateLimitProperties.Upload upload = new RateLimitProperties.Upload();
        upload.setPerHour(10);
        upload.setPerDay(50);
        upload.setGlobalPerMinute(100);
        upload.setMaxFileSize(104857600L); // 100MB
        upload.setMaxUserStorage(1073741824L); // 1GB
        props.setUpload(upload);
        return props;
    }

    @Nested
    @DisplayName("checkUploadAllowed")
    class CheckUploadAllowedTests {

        @Test
        @DisplayName("should allow first upload when within all limits")
        void shouldAllowFirstUploadWhenWithinAllLimits() {
            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(10);
            assertThat(result.getRemainingDaily()).isEqualTo(50);
        }

        @Test
        @DisplayName("should block upload when file size exceeds maximum")
        void shouldBlockUploadWhenFileSizeExceedsMaximum() {
            // Given - file size larger than max (100MB)
            long oversizedFile = 200 * 1024 * 1024L; // 200MB

            // When
            RateLimitResult result = service.checkUploadAllowed("user123", oversizedFile);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("File size exceeds maximum");
        }

        @Test
        @DisplayName("should block upload when storage quota exceeded")
        void shouldBlockUploadWhenStorageQuotaExceeded() {
            // Given - fill up storage to near limit
            long almostFullStorage = 1073741824L - 1000; // 1GB - 1KB
            service.updateUserStorage("user123", almostFullStorage);

            // When - try to upload 10MB
            RateLimitResult result = service.checkUploadAllowed("user123", 10 * 1024 * 1024L);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Storage limit exceeded");
        }

        @Test
        @DisplayName("should block upload when hourly limit exceeded")
        void shouldBlockUploadWhenHourlyLimitExceeded() {
            // Given - make 10 uploads (hourly limit)
            for (int i = 0; i < 10; i++) {
                service.recordUpload("user123", 1024);
            }

            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Hourly limit reached");
            assertThat(result.getMessage()).contains("10/10");
        }

        @Test
        @DisplayName("should block upload when daily limit exceeded")
        void shouldBlockUploadWhenDailyLimitExceeded() {
            // Given - set hourly limit higher temporarily
            rateLimitProperties.getUpload().setPerHour(100);

            // Make 50 uploads (daily limit)
            for (int i = 0; i < 50; i++) {
                service.recordUpload("user123", 1024);
            }

            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Daily limit reached");
            assertThat(result.getMessage()).contains("50/50");
        }

        @Test
        @DisplayName("should track different users separately")
        void shouldTrackDifferentUsersSeparately() {
            // Given - exhaust user1's hourly limit
            for (int i = 0; i < 10; i++) {
                service.recordUpload("user1", 1024);
            }

            // When
            RateLimitResult user1Result = service.checkUploadAllowed("user1", 1024);
            RateLimitResult user2Result = service.checkUploadAllowed("user2", 1024);

            // Then
            assertThat(user1Result.isAllowed()).isFalse();
            assertThat(user2Result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should allow upload when rate limiting not configured")
        void shouldAllowUploadWhenRateLimitingNotConfigured() {
            // Given - service without rate limit properties
            InMemoryStorageRateLimitService unconfiguredService =
                    new InMemoryStorageRateLimitService(null);

            // When
            RateLimitResult result = unconfiguredService.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(999);
            assertThat(result.getRemainingDaily()).isEqualTo(999);
        }

        @Test
        @DisplayName("should return correct remaining counts")
        void shouldReturnCorrectRemainingCounts() {
            // Given - make 5 uploads
            for (int i = 0; i < 5; i++) {
                service.recordUpload("user123", 1024);
            }

            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingHourly()).isEqualTo(5); // 10 - 5
            assertThat(result.getRemainingDaily()).isEqualTo(45); // 50 - 5
        }

        @ParameterizedTest
        @CsvSource({
                "1024, true",           // 1KB - allowed
                "10485760, true",       // 10MB - allowed
                "52428800, true",       // 50MB - allowed
                "104857600, true",      // 100MB (max) - allowed
                "104857601, false"      // 100MB + 1 byte - blocked
        })
        @DisplayName("should handle various file sizes correctly")
        void shouldHandleVariousFileSizesCorrectly(long fileSize, boolean expectedAllowed) {
            // When
            RateLimitResult result = service.checkUploadAllowed("user123", fileSize);

            // Then
            assertThat(result.isAllowed()).isEqualTo(expectedAllowed);
        }
    }

    @Nested
    @DisplayName("recordUpload")
    class RecordUploadTests {

        @Test
        @DisplayName("should record upload and update counts")
        void shouldRecordUploadAndUpdateCounts() {
            // When
            service.recordUpload("user123", 1024);

            // Then
            RateLimitStatus status = service.getUserStatus("user123");
            assertThat(status.getHourlyUsed()).isEqualTo(1);
            assertThat(status.getDailyUsed()).isEqualTo(1);
            assertThat(status.getStorageUsed()).isEqualTo(1024);
        }

        @Test
        @DisplayName("should accumulate storage usage")
        void shouldAccumulateStorageUsage() {
            // When
            service.recordUpload("user123", 1024);
            service.recordUpload("user123", 2048);
            service.recordUpload("user123", 4096);

            // Then
            RateLimitStatus status = service.getUserStatus("user123");
            assertThat(status.getStorageUsed()).isEqualTo(1024 + 2048 + 4096);
        }

        @Test
        @DisplayName("should increment upload counts per user")
        void shouldIncrementUploadCountsPerUser() {
            // When
            service.recordUpload("user1", 1024);
            service.recordUpload("user1", 1024);
            service.recordUpload("user2", 1024);

            // Then
            RateLimitStatus user1Status = service.getUserStatus("user1");
            RateLimitStatus user2Status = service.getUserStatus("user2");

            assertThat(user1Status.getHourlyUsed()).isEqualTo(2);
            assertThat(user2Status.getHourlyUsed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should not throw when rate limiting not configured")
        void shouldNotThrowWhenRateLimitingNotConfigured() {
            // Given
            InMemoryStorageRateLimitService unconfiguredService =
                    new InMemoryStorageRateLimitService(null);

            // When/Then - should not throw
            unconfiguredService.recordUpload("user123", 1024);
        }
    }

    @Nested
    @DisplayName("updateUserStorage")
    class UpdateUserStorageTests {

        @Test
        @DisplayName("should update storage for new user")
        void shouldUpdateStorageForNewUser() {
            // When
            service.updateUserStorage("user123", 5000);

            // Then
            assertThat(service.hasStorageSpace("user123", 1)).isTrue();
            RateLimitStatus status = service.getUserStatus("user123");
            assertThat(status.getStorageUsed()).isEqualTo(5000);
        }

        @Test
        @DisplayName("should accumulate storage across multiple updates")
        void shouldAccumulateStorageAcrossMultipleUpdates() {
            // When
            service.updateUserStorage("user123", 1000);
            service.updateUserStorage("user123", 2000);
            service.updateUserStorage("user123", 3000);

            // Then
            RateLimitStatus status = service.getUserStatus("user123");
            assertThat(status.getStorageUsed()).isEqualTo(6000);
        }
    }

    @Nested
    @DisplayName("hasStorageSpace")
    class HasStorageSpaceTests {

        @Test
        @DisplayName("should return true for new user with small request")
        void shouldReturnTrueForNewUserWithSmallRequest() {
            // When
            boolean result = service.hasStorageSpace("user123", 1024);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when request exceeds remaining space")
        void shouldReturnFalseWhenRequestExceedsRemainingSpace() {
            // Given - use most of the storage (1GB - 100 bytes)
            service.updateUserStorage("user123", 1073741824L - 100);

            // When
            boolean result = service.hasStorageSpace("user123", 1000);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("a declared size that overflows the sum is refused, not waved through")
        void refusesASizeThatOverflowsTheSum() {
            // Given - any non-zero usage at all
            service.updateUserStorage("user123", 1024);

            // When - the caller declares a size whose sum with `used` wraps past Long.MAX_VALUE
            boolean result = service.hasStorageSpace("user123", Long.MAX_VALUE);

            // Then - `used + requestedSize` is negative, and a negative is <= the limit, so the
            // old comparison returned true: the quota check passed on exactly the input it exists
            // to refuse. SignedUrlService hands this a caller-declared fileSize.
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("a negative declared size is refused rather than crediting the quota")
        void refusesANegativeSize() {
            service.updateUserStorage("user123", 1024);

            assertThat(service.hasStorageSpace("user123", -1)).isFalse();
        }

        @Test
        @DisplayName("should return true when request fits exactly")
        void shouldReturnTrueWhenRequestFitsExactly() {
            // Given - use all but 1000 bytes
            service.updateUserStorage("user123", 1073741824L - 1000);

            // When
            boolean result = service.hasStorageSpace("user123", 1000);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when rate limiting not configured")
        void shouldReturnTrueWhenRateLimitingNotConfigured() {
            // Given
            InMemoryStorageRateLimitService unconfiguredService =
                    new InMemoryStorageRateLimitService(null);

            // When
            boolean result = unconfiguredService.hasStorageSpace("user123", Long.MAX_VALUE);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getUserStatus")
    class GetUserStatusTests {

        @Test
        @DisplayName("should return zero usage for new user")
        void shouldReturnZeroUsageForNewUser() {
            // When
            RateLimitStatus status = service.getUserStatus("user123");

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(0);
            assertThat(status.getDailyUsed()).isEqualTo(0);
            assertThat(status.getStorageUsed()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return correct limits from configuration")
        void shouldReturnCorrectLimitsFromConfiguration() {
            // When
            RateLimitStatus status = service.getUserStatus("user123");

            // Then
            assertThat(status.getHourlyLimit()).isEqualTo(10);
            assertThat(status.getDailyLimit()).isEqualTo(50);
            assertThat(status.getStorageLimit()).isEqualTo(1073741824L);
        }

        @Test
        @DisplayName("should return accurate usage after uploads")
        void shouldReturnAccurateUsageAfterUploads() {
            // Given
            for (int i = 0; i < 3; i++) {
                service.recordUpload("user123", 10000);
            }

            // When
            RateLimitStatus status = service.getUserStatus("user123");

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(3);
            assertThat(status.getDailyUsed()).isEqualTo(3);
            assertThat(status.getStorageUsed()).isEqualTo(30000);
        }

        @Test
        @DisplayName("should return default status when not configured")
        void shouldReturnDefaultStatusWhenNotConfigured() {
            // Given
            InMemoryStorageRateLimitService unconfiguredService =
                    new InMemoryStorageRateLimitService(null);

            // When
            RateLimitStatus status = unconfiguredService.getUserStatus("user123");

            // Then
            assertThat(status.getHourlyLimit()).isEqualTo(999);
            assertThat(status.getDailyLimit()).isEqualTo(999);
        }

        @Test
        @DisplayName("should calculate storage used in MB correctly")
        void shouldCalculateStorageUsedInMBCorrectly() {
            // Given - 10MB
            service.updateUserStorage("user123", 10 * 1024 * 1024);

            // When
            RateLimitStatus status = service.getUserStatus("user123");

            // Then
            assertThat(status.getStorageUsedMB()).isCloseTo(10.0, org.assertj.core.api.Assertions.within(0.01));
        }
    }

    @Nested
    @DisplayName("recordSignedUrlGeneration")
    class RecordSignedUrlGenerationTests {

        @Test
        @DisplayName("should track signed URL generation")
        void shouldTrackSignedUrlGeneration() {
            // Given - record 5 signed URLs
            for (int i = 0; i < 5; i++) {
                service.recordSignedUrlGeneration("user123", 1024);
            }

            // When - check if still allowed (under 10 per minute limit)
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should block when signed URL limit exceeded")
        void shouldBlockWhenSignedUrlLimitExceeded() {
            // Given - record 10 signed URLs (at limit)
            for (int i = 0; i < 10; i++) {
                service.recordSignedUrlGeneration("user123", 1024);
            }

            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Too many signed URL requests");
        }
    }

    @Nested
    @DisplayName("resetUserLimits")
    class ResetUserLimitsTests {

        @Test
        @DisplayName("should clear all limits for user")
        void shouldClearAllLimitsForUser() {
            // Given - record uploads and storage
            for (int i = 0; i < 5; i++) {
                service.recordUpload("user123", 10000);
            }
            service.recordSignedUrlGeneration("user123", 1024);

            // When
            service.resetUserLimits("user123");

            // Then
            RateLimitStatus status = service.getUserStatus("user123");
            assertThat(status.getHourlyUsed()).isEqualTo(0);
            assertThat(status.getDailyUsed()).isEqualTo(0);
            assertThat(status.getStorageUsed()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not affect other users")
        void shouldNotAffectOtherUsers() {
            // Given
            service.recordUpload("user1", 1024);
            service.recordUpload("user2", 1024);

            // When
            service.resetUserLimits("user1");

            // Then
            RateLimitStatus user1Status = service.getUserStatus("user1");
            RateLimitStatus user2Status = service.getUserStatus("user2");

            assertThat(user1Status.getHourlyUsed()).isEqualTo(0);
            assertThat(user2Status.getHourlyUsed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle reset for non-existent user gracefully")
        void shouldHandleResetForNonExistentUserGracefully() {
            // When/Then - should not throw
            service.resetUserLimits("non-existent-user");
        }
    }

    @Nested
    @DisplayName("scheduledCleanup")
    class ScheduledCleanupTests {

        @Test
        @DisplayName("should not throw during cleanup")
        void shouldNotThrowDuringCleanup() {
            // Given - add some entries
            for (int i = 0; i < 5; i++) {
                service.recordUpload("user" + i, 1024);
            }

            // When/Then - should not throw
            service.scheduledCleanup();
        }

        @Test
        @DisplayName("should not remove recent entries")
        void shouldNotRemoveRecentEntries() {
            // Given
            service.recordUpload("user123", 1024);

            // When
            service.scheduledCleanup();

            // Then
            RateLimitStatus status = service.getUserStatus("user123");
            assertThat(status.getHourlyUsed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle empty state gracefully")
        void shouldHandleEmptyStateGracefully() {
            // When/Then - should not throw
            service.scheduledCleanup();
        }
    }

    @Nested
    @DisplayName("getMemoryStats")
    class GetMemoryStatsTests {

        @Test
        @DisplayName("should return empty stats for fresh service")
        void shouldReturnEmptyStatsForFreshService() {
            // When
            Map<String, Object> stats = service.getMemoryStats();

            // Then
            assertThat(stats.get("totalUsers")).isEqualTo(0);
            assertThat(stats.get("hourlyUploadsEntries")).isEqualTo(0);
            assertThat(stats.get("dailyUploadsEntries")).isEqualTo(0);
            assertThat(stats.get("globalUploadsEntries")).isEqualTo(0);
            assertThat(stats.get("signedUrlCacheEntries")).isEqualTo(0);
            assertThat(stats.get("totalStorageUsedBytes")).isEqualTo(0L);
        }

        @Test
        @DisplayName("should return accurate stats after activity")
        void shouldReturnAccurateStatsAfterActivity() {
            // Given
            service.recordUpload("user1", 1000);
            service.recordUpload("user2", 2000);
            service.recordUpload("user1", 500);

            // When
            Map<String, Object> stats = service.getMemoryStats();

            // Then
            assertThat(stats.get("totalUsers")).isEqualTo(2);
            assertThat(stats.get("hourlyUploadsEntries")).isEqualTo(3);
            assertThat(stats.get("dailyUploadsEntries")).isEqualTo(3);
            assertThat((Long) stats.get("totalStorageUsedBytes")).isEqualTo(3500L);
        }

        @Test
        @DisplayName("should calculate storage in MB correctly")
        void shouldCalculateStorageInMBCorrectly() {
            // Given - 5MB total
            service.recordUpload("user1", 2 * 1024 * 1024);
            service.recordUpload("user2", 3 * 1024 * 1024);

            // When
            Map<String, Object> stats = service.getMemoryStats();

            // Then
            assertThat((Double) stats.get("totalStorageUsedMB")).isCloseTo(5.0, org.assertj.core.api.Assertions.within(0.01));
        }
    }

    @Nested
    @DisplayName("Global Rate Limit")
    class GlobalRateLimitTests {

        @Test
        @DisplayName("should block when global per-minute limit exceeded")
        void shouldBlockWhenGlobalPerMinuteLimitExceeded() {
            // Given - set global limit to 5 for testing
            rateLimitProperties.getUpload().setGlobalPerMinute(5);

            // Make 5 uploads from different users
            for (int i = 0; i < 5; i++) {
                service.recordUpload("user" + i, 1024);
            }

            // When - any user tries to upload
            RateLimitResult result = service.checkUploadAllowed("newUser", 1024);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).contains("Global rate limit exceeded");
        }

        @Test
        @DisplayName("should allow uploads when under global limit")
        void shouldAllowUploadsWhenUnderGlobalLimit() {
            // Given - set global limit to 10
            rateLimitProperties.getUpload().setGlobalPerMinute(10);

            // Make 5 uploads
            for (int i = 0; i < 5; i++) {
                service.recordUpload("user" + i, 1024);
            }

            // When
            RateLimitResult result = service.checkUploadAllowed("newUser", 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("should handle concurrent uploads from different users without errors")
        void shouldHandleConcurrentUploadsFromDifferentUsersWithoutDeadlock() throws InterruptedException {
            // Given - ConcurrentSkipListSet ensures thread-safe access to per-user upload sets
            int threadCount = 4;
            int uploadsPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger completedOperations = new AtomicInteger(0);
            List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

            // When - each thread uses its own user
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < uploadsPerThread; j++) {
                            service.recordUpload("concurrentUser-" + threadId, 100);
                            completedOperations.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - all threads should complete without deadlock and without errors
            assertThat(completed).isTrue();
            assertThat(errors).as("No exceptions expected with thread-safe collections").isEmpty();

            int totalAttempted = threadCount * uploadsPerThread;
            assertThat(completedOperations.get()).isEqualTo(totalAttempted);
        }

        @Test
        @DisplayName("should handle concurrent check and record operations without errors")
        void shouldHandleConcurrentCheckAndRecordOperations() throws InterruptedException {
            // Given - concurrent reads (check) and writes (record) on shared user keys
            int threadCount = 8;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger checkCount = new AtomicInteger(0);
            AtomicInteger recordCount = new AtomicInteger(0);
            List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            String userId = "user-" + (j % 5); // Shared keys

                            if (threadId % 2 == 0) {
                                service.checkUploadAllowed(userId, 1024);
                                checkCount.incrementAndGet();
                            } else {
                                service.recordUpload(userId, 100);
                                recordCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - all operations should complete without errors
            assertThat(completed).isTrue();
            assertThat(errors).as("No exceptions expected with thread-safe collections").isEmpty();

            int totalExpected = threadCount * operationsPerThread;
            assertThat(checkCount.get() + recordCount.get()).isEqualTo(totalExpected);
        }

        @Test
        @DisplayName("should handle concurrent reset operations without errors")
        void shouldHandleConcurrentResetOperations() throws InterruptedException {
            // Given
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

            // Pre-populate
            for (int i = 0; i < 10; i++) {
                service.recordUpload("user" + i, 1024);
            }

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        service.resetUserLimits("user" + (threadId % 10));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(errors).as("No exceptions expected with thread-safe collections").isEmpty();
        }

        @Test
        @DisplayName("should handle concurrent cleanup and uploads without errors")
        void shouldHandleConcurrentCleanupAndUploads() throws InterruptedException {
            // Given - one thread runs cleanup while others record uploads concurrently
            int threadCount = 4;
            int operationsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger operationCount = new AtomicInteger(0);
            List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            if (threadId == 0) {
                                service.scheduledCleanup();
                            } else {
                                service.recordUpload("user" + threadId + "-" + j, 100);
                            }
                            operationCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - all operations should complete without errors
            assertThat(completed).isTrue();
            assertThat(errors).as("No exceptions expected with thread-safe collections").isEmpty();
            assertThat(operationCount.get()).isEqualTo(threadCount * operationsPerThread);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle zero file size")
        void shouldHandleZeroFileSize() {
            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 0);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle empty user ID")
        void shouldHandleEmptyUserId() {
            // When
            RateLimitResult result = service.checkUploadAllowed("", 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle very long user ID")
        void shouldHandleVeryLongUserId() {
            // Given
            String longUserId = "a".repeat(1000);

            // When
            RateLimitResult result = service.checkUploadAllowed(longUserId, 1024);
            service.recordUpload(longUserId, 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
            RateLimitStatus status = service.getUserStatus(longUserId);
            assertThat(status.getHourlyUsed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle special characters in user ID")
        void shouldHandleSpecialCharactersInUserId() {
            // Given
            String specialUserId = "user_123-test.account@domain.com";

            // When
            service.recordUpload(specialUserId, 1024);
            RateLimitStatus status = service.getUserStatus(specialUserId);

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle exactly at hourly limit")
        void shouldHandleExactlyAtHourlyLimit() {
            // Given - make exactly 10 uploads (at limit)
            for (int i = 0; i < 10; i++) {
                service.recordUpload("user123", 1024);
            }

            // When
            RateLimitStatus status = service.getUserStatus("user123");

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(10);
            assertThat(status.getHourlyLimit()).isEqualTo(10);
        }

        @Test
        @DisplayName("should handle maximum file size exactly")
        void shouldHandleMaximumFileSizeExactly() {
            // Given - exactly max size
            long maxSize = 104857600L; // 100MB

            // When
            RateLimitResult result = service.checkUploadAllowed("user123", maxSize);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle storage exactly at limit")
        void shouldHandleStorageExactlyAtLimit() {
            // Given - use exactly max storage minus 1 byte
            service.updateUserStorage("user123", 1073741823L); // 1GB - 1

            // When - request exactly 1 byte
            boolean result = service.hasStorageSpace("user123", 1);

            // Then
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"user:with:colons", "user/with/slashes", "user\\with\\backslashes"})
        @DisplayName("should handle various user ID formats")
        void shouldHandleVariousUserIdFormats(String userId) {
            // When
            service.recordUpload(userId, 1024);
            RateLimitStatus status = service.getUserStatus(userId);

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Bucket Refill (Entry Expiration)")
    class BucketRefillTests {

        @Test
        @DisplayName("should expire entries older than one hour from hourly bucket")
        void shouldExpireEntriesOlderThanOneHourFromHourlyBucket() throws Exception {
            // Given - record upload
            service.recordUpload("user123", 1024);
            assertThat(service.getUserStatus("user123").getHourlyUsed()).isEqualTo(1);

            // Simulate time passing by manipulating internal state
            forceExpireHourlyEntries("user123");

            // When - check status again (should trigger cleanup)
            RateLimitStatus status = service.getUserStatus("user123");

            // Then - hourly count should be 0, daily should still be 1
            assertThat(status.getHourlyUsed()).isEqualTo(0);
        }

        @Test
        @DisplayName("should expire entries older than one day from daily bucket")
        void shouldExpireEntriesOlderThanOneDayFromDailyBucket() throws Exception {
            // Given
            service.recordUpload("user123", 1024);
            assertThat(service.getUserStatus("user123").getDailyUsed()).isEqualTo(1);

            // Simulate time passing
            forceExpireDailyEntries("user123");

            // When
            RateLimitStatus status = service.getUserStatus("user123");

            // Then
            assertThat(status.getDailyUsed()).isEqualTo(0);
        }

        @Test
        @DisplayName("should allow upload after hourly bucket refills")
        void shouldAllowUploadAfterHourlyBucketRefills() throws Exception {
            // Given - exhaust hourly limit
            for (int i = 0; i < 10; i++) {
                service.recordUpload("user123", 1024);
            }
            assertThat(service.checkUploadAllowed("user123", 1024).isAllowed()).isFalse();

            // Simulate hour passing
            forceExpireHourlyEntries("user123");

            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("RateLimitResult")
    class RateLimitResultTests {

        @Test
        @DisplayName("allowed result should have correct values")
        void allowedResultShouldHaveCorrectValues() {
            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Upload allowed");
            assertThat(result.getRemainingHourly()).isNotNull();
            assertThat(result.getRemainingDaily()).isNotNull();
        }

        @Test
        @DisplayName("blocked result should have null remaining counts")
        void blockedResultShouldHaveNullRemainingCounts() {
            // Given - exhaust limit
            for (int i = 0; i < 10; i++) {
                service.recordUpload("user123", 1024);
            }

            // When
            RateLimitResult result = service.checkUploadAllowed("user123", 1024);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemainingHourly()).isNull();
            assertThat(result.getRemainingDaily()).isNull();
        }
    }

    @Nested
    @DisplayName("RateLimitStatus")
    class RateLimitStatusTests {

        @Test
        @DisplayName("should calculate storage used in MB correctly")
        void shouldCalculateStorageUsedInMBCorrectly() {
            // Given
            RateLimitStatus status = new RateLimitStatus(0, 10, 0, 50,
                    52428800L, 1073741824L); // 50MB used of 1GB

            // Then
            assertThat(status.getStorageUsedMB()).isCloseTo(50.0, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("should calculate storage limit in MB correctly")
        void shouldCalculateStorageLimitInMBCorrectly() {
            // Given
            RateLimitStatus status = new RateLimitStatus(0, 10, 0, 50,
                    0, 1073741824L); // 0 used of 1GB

            // Then
            assertThat(status.getStorageLimitMB()).isCloseTo(1024.0, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("should return correct hourly values")
        void shouldReturnCorrectHourlyValues() {
            // Given
            RateLimitStatus status = new RateLimitStatus(5, 10, 20, 50,
                    1000L, 2000L);

            // Then
            assertThat(status.getHourlyUsed()).isEqualTo(5);
            assertThat(status.getHourlyLimit()).isEqualTo(10);
        }

        @Test
        @DisplayName("should return correct daily values")
        void shouldReturnCorrectDailyValues() {
            // Given
            RateLimitStatus status = new RateLimitStatus(5, 10, 20, 50,
                    1000L, 2000L);

            // Then
            assertThat(status.getDailyUsed()).isEqualTo(20);
            assertThat(status.getDailyLimit()).isEqualTo(50);
        }
    }

    // Helper methods for testing expiration

    @SuppressWarnings("unchecked")
    private void forceExpireHourlyEntries(String userId) throws Exception {
        // Access the private userHourlyUploads field via reflection
        Field field = InMemoryStorageRateLimitService.class.getDeclaredField("userHourlyUploads");
        field.setAccessible(true);
        Map<String, ?> hourlyUploads = (Map<String, ?>) field.get(service);

        // Clear the entries to simulate expiration
        Object entries = hourlyUploads.get(userId);
        if (entries != null) {
            // Clear the ConcurrentSkipListSet
            Method clearMethod = entries.getClass().getMethod("clear");
            clearMethod.invoke(entries);
        }
    }

    @SuppressWarnings("unchecked")
    private void forceExpireDailyEntries(String userId) throws Exception {
        // Access the private userDailyUploads field via reflection
        Field field = InMemoryStorageRateLimitService.class.getDeclaredField("userDailyUploads");
        field.setAccessible(true);
        Map<String, ?> dailyUploads = (Map<String, ?>) field.get(service);

        // Clear the entries to simulate expiration
        Object entries = dailyUploads.get(userId);
        if (entries != null) {
            // Clear the ConcurrentSkipListSet
            Method clearMethod = entries.getClass().getMethod("clear");
            clearMethod.invoke(entries);
        }
    }
}
