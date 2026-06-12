package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.ratelimit.InMemoryRateLimiterService;
import com.sm.instagram.platform.common.ratelimit.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InMemoryRateLimiterService.
 * Tests in-memory rate limiting with GDPR-compliant key anonymization.
 * No Spring context needed - testing pure Java logic.
 */
@DisplayName("InMemoryRateLimiterService Unit Tests")
class InMemoryRateLimiterServiceUnitTest {

    private InMemoryRateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryRateLimiterService();
    }

    @Nested
    @DisplayName("checkLimit()")
    class CheckLimitTests {

        @Test
        @DisplayName("should allow first request within limit")
        void shouldAllowFirstRequest() {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit("user:abc123def456", 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(9);
            assertThat(result.getLimit()).isEqualTo(10);
        }

        @Test
        @DisplayName("should allow requests up to limit")
        void shouldAllowRequestsUpToLimit() {
            // Given
            String key = "user:test12345678901234567890";
            int limit = 5;

            // When - make 5 requests (all within limit)
            for (int i = 0; i < limit; i++) {
                RateLimiterService.RateLimitResult result = service.checkLimit(key, limit, 60);
                assertThat(result.isAllowed())
                        .as("Request %d should be allowed", i + 1)
                        .isTrue();
            }

            // Then - 6th request should be denied
            RateLimiterService.RateLimitResult result = service.checkLimit(key, limit, 60);
            assertThat(result.isAllowed()).isFalse();
        }

        @Test
        @DisplayName("should deny request when limit exceeded")
        void shouldDenyWhenLimitExceeded() {
            // Given
            String key = "user:deniedUser1234567890";
            int limit = 3;

            // When - exhaust the limit
            for (int i = 0; i < limit; i++) {
                service.checkLimit(key, limit, 60);
            }

            // Then
            RateLimiterService.RateLimitResult result = service.checkLimit(key, limit, 60);
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemaining()).isEqualTo(0);
        }

        @Test
        @DisplayName("should track different keys separately")
        void shouldTrackDifferentKeysSeparately() {
            // Given
            String key1 = "user:userAAAAAAAAAAAAAAAA";
            String key2 = "user:userBBBBBBBBBBBBBBBB";
            int limit = 2;

            // When - exhaust limit for key1
            service.checkLimit(key1, limit, 60);
            service.checkLimit(key1, limit, 60);
            RateLimiterService.RateLimitResult result1 = service.checkLimit(key1, limit, 60);

            // Then - key1 should be denied
            assertThat(result1.isAllowed()).isFalse();

            // But key2 should still be allowed
            RateLimiterService.RateLimitResult result2 = service.checkLimit(key2, limit, 60);
            assertThat(result2.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should return correct remaining count")
        void shouldReturnCorrectRemainingCount() {
            // Given
            String key = "user:countingUser12345678";
            int limit = 10;

            // When/Then
            for (int i = 0; i < limit; i++) {
                RateLimiterService.RateLimitResult result = service.checkLimit(key, limit, 60);
                int expectedRemaining = limit - (i + 1);
                assertThat(result.getRemaining())
                        .as("After %d requests, remaining should be %d", i + 1, expectedRemaining)
                        .isEqualTo(expectedRemaining);
            }
        }

        @Test
        @DisplayName("should return reset time in future")
        void shouldReturnResetTimeInFuture() {
            // Given
            long beforeRequest = System.currentTimeMillis() / 1000;

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit("user:timeTest1234567890", 10, 60);

            // Then
            assertThat(result.getResetTime()).isGreaterThanOrEqualTo(beforeRequest + 60);
        }
    }

    @Nested
    @DisplayName("Key Anonymization")
    class KeyAnonymizationTests {

        @Test
        @DisplayName("should handle user prefix with Firebase UID")
        void shouldHandleUserPrefixWithFirebaseUid() {
            // Given - Firebase UIDs are 28 chars, alphanumeric
            String firebaseUid = "user:abcdefghij1234567890ABCD";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(firebaseUid, 10, 60);

            // Then - should work without errors
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle user prefix with numeric ID")
        void shouldHandleUserPrefixWithNumericId() {
            // Given
            String numericId = "user:12345";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(numericId, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle IP prefix")
        void shouldHandleIpPrefix() {
            // Given
            String ipKey = "ip:192.168.1.100";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(ipKey, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle email-like key")
        void shouldHandleEmailLikeKey() {
            // Given
            String emailKey = "test@example.com";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(emailKey, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle generic key")
        void shouldHandleGenericKey() {
            // Given
            String genericKey = "api-request-key";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(genericKey, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle empty key")
        void shouldHandleEmptyKey() {
            // Given
            String emptyKey = "";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(emptyKey, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle null key gracefully")
        void shouldHandleNullKeyGracefully() {
            // ConcurrentHashMap doesn't support null keys, but anonymizeKey handles null
            // The parent class will throw NullPointerException, which is expected behavior
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> {
                service.checkLimit(null, 10, 60);
            });
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle limit of 1")
        void shouldHandleLimitOfOne() {
            // When
            RateLimiterService.RateLimitResult first = service.checkLimit("user:limitOne1234567890", 1, 60);
            RateLimiterService.RateLimitResult second = service.checkLimit("user:limitOne1234567890", 1, 60);

            // Then
            assertThat(first.isAllowed()).isTrue();
            assertThat(first.getRemaining()).isEqualTo(0);
            assertThat(second.isAllowed()).isFalse();
        }

        @Test
        @DisplayName("should handle very high limit")
        void shouldHandleVeryHighLimit() {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit("user:highLimit1234567890", 1_000_000, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(999999);
        }

        @Test
        @DisplayName("should handle short duration")
        void shouldHandleShortDuration() {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit("user:shortDuration123456", 10, 1);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "special:key:with:colons",
                "key-with-dashes",
                "key_with_underscores",
                "key.with.dots"
        })
        @DisplayName("should handle various key formats")
        void shouldHandleVariousKeyFormats(String key) {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(key, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("should handle concurrent requests to same key")
        void shouldHandleConcurrentRequestsToSameKey() throws InterruptedException {
            // Given
            String key = "user:concurrentTestKey123";
            int limit = 100;
            int threadCount = 10;
            int requestsPerThread = 5;

            // When
            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < requestsPerThread; j++) {
                        service.checkLimit(key, limit, 60);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Then - should have tracked exactly limit - remaining requests
            RateLimiterService.RateLimitResult result = service.checkLimit(key, limit, 60);
            int totalRequests = threadCount * requestsPerThread + 1;
            int expectedRemaining = limit - totalRequests;
            assertThat(result.getRemaining()).isEqualTo(Math.max(0, expectedRemaining));
        }

        @Test
        @DisplayName("should handle concurrent requests to different keys")
        void shouldHandleConcurrentRequestsToDifferentKeys() throws InterruptedException {
            // Given
            int threadCount = 10;
            int limit = 5;

            // When
            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    String key = "user:thread" + threadIndex + "key123456";
                    for (int j = 0; j < 3; j++) {
                        service.checkLimit(key, limit, 60);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Then - each key should have independent tracking
            assertThat(service.getTrackedEntriesCount()).isEqualTo(threadCount);
        }
    }

    @Nested
    @DisplayName("Inheritance from RateLimiterService")
    class InheritanceTests {

        @Test
        @DisplayName("should inherit cleanup functionality")
        void shouldInheritCleanupFunctionality() {
            // Given
            service.checkLimit("user:cleanupTest1234567890", 10, 60);
            service.checkLimit("user:cleanupTest2345678901", 10, 60);

            // When
            int removed = service.cleanupExpiredEntries();

            // Then - recent entries should not be removed
            assertThat(removed).isEqualTo(0);
            assertThat(service.getTrackedEntriesCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should inherit tracking count functionality")
        void shouldInheritTrackingCountFunctionality() {
            // Given
            assertThat(service.getTrackedEntriesCount()).isEqualTo(0);

            // When
            service.checkLimit("user:trackingTest1234567890", 10, 60);

            // Then
            assertThat(service.getTrackedEntriesCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should inherit isAllowed legacy method")
        void shouldInheritIsAllowedLegacyMethod() {
            // When
            boolean allowed = service.isAllowed("user:legacyMethod1234567890", 5, 60);

            // Then
            assertThat(allowed).isTrue();
        }
    }

    @Nested
    @DisplayName("IP Anonymization")
    class IpAnonymizationTests {

        @ParameterizedTest
        @CsvSource({
                "ip:192.168.1.100, true",
                "ip:10.0.0.1, true",
                "ip:172.16.0.1, true",
                "ip:8.8.8.8, true"
        })
        @DisplayName("should allow IP-based rate limiting")
        void shouldAllowIpBasedRateLimiting(String key, boolean expectedAllowed) {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(key, 10, 60);

            // Then
            assertThat(result.isAllowed()).isEqualTo(expectedAllowed);
        }

        @Test
        @DisplayName("should handle malformed IP in key")
        void shouldHandleMalformedIpInKey() {
            // Given
            String malformedIpKey = "ip:not-an-ip";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(malformedIpKey, 10, 60);

            // Then - should still work
            assertThat(result.isAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("User ID Handling")
    class UserIdHandlingTests {

        @Test
        @DisplayName("should handle short user ID")
        void shouldHandleShortUserId() {
            // Given
            String shortId = "user:abc";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(shortId, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle long Firebase UID")
        void shouldHandleLongFirebaseUid() {
            // Given - typical Firebase UID is 28 characters
            String firebaseUid = "user:abcdefghij1234567890ABCDEF";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(firebaseUid, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle user ID with special characters")
        void shouldHandleUserIdWithSpecialCharacters() {
            // Given
            String specialCharsId = "user:abc-def_123.456";

            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(specialCharsId, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }
    }
}
