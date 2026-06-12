package com.sm.instagram.platform.unit.service;

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
 * Unit tests for RateLimiterService.
 * Tests in-memory rate limiting logic.
 * No Spring context needed - testing pure Java logic.
 */
@DisplayName("RateLimiterService Unit Tests")
class RateLimiterServiceUnitTest {

    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new RateLimiterService();
    }

    @Nested
    @DisplayName("checkLimit")
    class CheckLimitTests {

        @Test
        @DisplayName("should allow first request within limit")
        void shouldAllowFirstRequest() {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit("user:123", 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(9);
            assertThat(result.getLimit()).isEqualTo(10);
        }

        @Test
        @DisplayName("should allow requests up to limit")
        void shouldAllowRequestsUpToLimit() {
            // Given
            String key = "user:456";
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
            String key = "user:789";
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
            String key1 = "user:aaa";
            String key2 = "user:bbb";
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
            String key = "user:counting";
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
            RateLimiterService.RateLimitResult result = service.checkLimit("user:time", 10, 60);

            // Then
            assertThat(result.getResetTime()).isGreaterThanOrEqualTo(beforeRequest + 60);
        }

        @ParameterizedTest
        @CsvSource({
                "1, 60",
                "10, 60",
                "100, 3600",
                "1000, 1"
        })
        @DisplayName("should handle various limit and duration combinations")
        void shouldHandleVariousLimitAndDurationCombinations(int limit, int duration) {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit("key:" + limit, limit, duration);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getLimit()).isEqualTo(limit);
            assertThat(result.getRemaining()).isEqualTo(limit - 1);
        }
    }

    @Nested
    @DisplayName("isAllowed (legacy)")
    class IsAllowedTests {

        @Test
        @DisplayName("should return true when within limit")
        void shouldReturnTrueWhenWithinLimit() {
            // When
            boolean allowed = service.isAllowed("legacy:123", 5, 60);

            // Then
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("should return false when limit exceeded")
        void shouldReturnFalseWhenLimitExceeded() {
            // Given
            String key = "legacy:456";

            // When - exhaust limit
            service.isAllowed(key, 1, 60);

            // Then
            boolean allowed = service.isAllowed(key, 1, 60);
            assertThat(allowed).isFalse();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredEntries")
    class CleanupExpiredEntriesTests {

        @Test
        @DisplayName("should return zero when no entries to clean")
        void shouldReturnZeroWhenNoEntriesToClean() {
            // Given - fresh service with no entries

            // When
            int removed = service.cleanupExpiredEntries();

            // Then
            assertThat(removed).isEqualTo(0);
        }

        @Test
        @DisplayName("should not remove recent entries")
        void shouldNotRemoveRecentEntries() {
            // Given
            service.checkLimit("recent:1", 10, 60);
            service.checkLimit("recent:2", 10, 60);

            // When
            int removed = service.cleanupExpiredEntries();

            // Then
            assertThat(removed).isEqualTo(0);
            assertThat(service.getTrackedEntriesCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getTrackedEntriesCount")
    class GetTrackedEntriesCountTests {

        @Test
        @DisplayName("should return zero initially")
        void shouldReturnZeroInitially() {
            assertThat(service.getTrackedEntriesCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should increment as entries are added")
        void shouldIncrementAsEntriesAreAdded() {
            // When
            service.checkLimit("entry:1", 10, 60);
            assertThat(service.getTrackedEntriesCount()).isEqualTo(1);

            service.checkLimit("entry:2", 10, 60);
            assertThat(service.getTrackedEntriesCount()).isEqualTo(2);

            service.checkLimit("entry:3", 10, 60);
            assertThat(service.getTrackedEntriesCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should not increment for same key")
        void shouldNotIncrementForSameKey() {
            // When
            service.checkLimit("same:key", 10, 60);
            service.checkLimit("same:key", 10, 60);
            service.checkLimit("same:key", 10, 60);

            // Then
            assertThat(service.getTrackedEntriesCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("RateLimitResult")
    class RateLimitResultTests {

        @Test
        @DisplayName("should create result with correct values")
        void shouldCreateResultWithCorrectValues() {
            // Given
            RateLimiterService.RateLimitResult result =
                    new RateLimiterService.RateLimitResult(true, 100, 50, 1234567890L);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getLimit()).isEqualTo(100);
            assertThat(result.getRemaining()).isEqualTo(50);
            assertThat(result.getResetTime()).isEqualTo(1234567890L);
        }

        @Test
        @DisplayName("should create denied result")
        void shouldCreateDeniedResult() {
            // Given
            RateLimiterService.RateLimitResult result =
                    new RateLimiterService.RateLimitResult(false, 10, 0, 1234567890L);

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemaining()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle limit of 1")
        void shouldHandleLimitOfOne() {
            // When
            RateLimiterService.RateLimitResult first = service.checkLimit("limit:1", 1, 60);
            RateLimiterService.RateLimitResult second = service.checkLimit("limit:1", 1, 60);

            // Then
            assertThat(first.isAllowed()).isTrue();
            assertThat(first.getRemaining()).isEqualTo(0);
            assertThat(second.isAllowed()).isFalse();
        }

        @Test
        @DisplayName("should handle very high limit")
        void shouldHandleVeryHighLimit() {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit("high:limit", 1_000_000, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(999999);
        }

        @Test
        @DisplayName("should handle short duration")
        void shouldHandleShortDuration() {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit("short:duration", 10, 1);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "special:key:with:colons", "key-with-dashes", "key_with_underscores"})
        @DisplayName("should handle various key formats")
        void shouldHandleVariousKeyFormats(String key) {
            // When
            RateLimiterService.RateLimitResult result = service.checkLimit(key, 10, 60);

            // Then
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should throw exception for null key")
        void shouldThrowExceptionForNullKey() {
            // ConcurrentHashMap does not support null keys
            // When/Then
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> {
                service.checkLimit(null, 10, 60);
            });
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("should handle concurrent requests to same key")
        void shouldHandleConcurrentRequestsToSameKey() throws InterruptedException {
            // Given
            String key = "concurrent:key";
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
    }

    @Nested
    @DisplayName("Window Reset Tests")
    class WindowResetTests {

        @Test
        @DisplayName("should track requests within same window")
        void shouldTrackRequestsWithinSameWindow() {
            // Given
            String key = "window:test";
            int limit = 5;

            // When - make multiple requests
            for (int i = 0; i < 3; i++) {
                service.checkLimit(key, limit, 3600); // 1 hour window
            }

            // Then
            RateLimiterService.RateLimitResult result = service.checkLimit(key, limit, 3600);
            assertThat(result.getRemaining()).isEqualTo(1); // 5 - 4 = 1
        }
    }
}
