package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.cache.InMemoryUserCache;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InMemoryUserCache.
 * Tests cache operations: get, put, evict, expiration, and concurrent access.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InMemoryUserCache Unit Tests")
class InMemoryUserCacheUnitTest {

    @Mock
    private UserRepository userRepository;

    private InMemoryUserCache cache;

    private User createUser(String firebaseUid, UserType userType, AccountStatus accountStatus) {
        User user = new User();
        user.setId(1L);
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setAccountStatus(accountStatus);
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        return user;
    }

    @BeforeEach
    void setUp() {
        cache = new InMemoryUserCache(userRepository);
    }

    @Nested
    @DisplayName("cacheUser")
    class CacheUserTests {

        @Test
        @DisplayName("should cache active user successfully")
        void shouldCacheActiveUserSuccessfully() {
            // Given
            User user = createUser("firebase-uid-1", UserType.INFLUENCER, AccountStatus.ACTIVE);

            // When
            cache.cacheUser("firebase-uid-1", user);

            // Then
            assertThat(cache.isUserActive("firebase-uid-1")).isTrue();
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should cache user in validation status as active")
        void shouldCacheUserInValidationStatusAsActive() {
            // Given
            User user = createUser("firebase-uid-2", UserType.COMPANY, AccountStatus.IN_VALIDATION);

            // When
            cache.cacheUser("firebase-uid-2", user);

            // Then
            assertThat(cache.isUserActive("firebase-uid-2")).isTrue();
        }

        @Test
        @DisplayName("should cache banned user as active for authentication")
        void shouldCacheBannedUserAsActiveForAuthentication() {
            // Given
            User user = createUser("firebase-uid-3", UserType.INFLUENCER, AccountStatus.BANNED);

            // When
            cache.cacheUser("firebase-uid-3", user);

            // Then - BANNED users can authenticate (to see why they're banned)
            assertThat(cache.isUserActive("firebase-uid-3")).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"INACTIVE", "DELETED", "TO_BE_DELETED"})
        @DisplayName("should cache non-authenticatable user statuses as inactive")
        void shouldCacheNonAuthenticatableUserStatusesAsInactive(AccountStatus status) {
            // Given
            User user = createUser("firebase-uid-inactive", UserType.INFLUENCER, status);

            // When
            cache.cacheUser("firebase-uid-inactive", user);

            // Then
            assertThat(cache.isUserActive("firebase-uid-inactive")).isFalse();
        }

        @Test
        @DisplayName("should cache user role correctly")
        void shouldCacheUserRoleCorrectly() {
            // Given
            User user = createUser("firebase-uid-role", UserType.ADMIN, AccountStatus.ACTIVE);

            // When
            cache.cacheUser("firebase-uid-role", user);

            // Then - verify role is stored (indirectly by checking account status)
            String accountStatus = cache.getAccountStatus("firebase-uid-role");
            assertThat(accountStatus).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should cache token version correctly")
        void shouldCacheTokenVersionCorrectly() {
            // Given
            User user = createUser("firebase-uid-token", UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.incrementTokenVersion(); // Now version is 2

            // When
            cache.cacheUser("firebase-uid-token", user);

            // Then
            Long tokenVersion = cache.getTokenVersion("firebase-uid-token");
            assertThat(tokenVersion).isEqualTo(2L);
        }

        @Test
        @DisplayName("should overwrite existing cache entry")
        void shouldOverwriteExistingCacheEntry() {
            // Given
            User user1 = createUser("firebase-uid-overwrite", UserType.INFLUENCER, AccountStatus.ACTIVE);
            User user2 = createUser("firebase-uid-overwrite", UserType.INFLUENCER, AccountStatus.BANNED);

            // When
            cache.cacheUser("firebase-uid-overwrite", user1);
            cache.cacheUser("firebase-uid-overwrite", user2);

            // Then
            String accountStatus = cache.getAccountStatus("firebase-uid-overwrite");
            assertThat(accountStatus).isEqualTo("BANNED");
        }
    }

    @Nested
    @DisplayName("isUserActive")
    class IsUserActiveTests {

        @Test
        @DisplayName("should return true for cached active user")
        void shouldReturnTrueForCachedActiveUser() {
            // Given
            User user = createUser("firebase-uid-active", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-active", user);

            // When
            boolean result = cache.isUserActive("firebase-uid-active");

            // Then
            assertThat(result).isTrue();
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should return false for cached inactive user")
        void shouldReturnFalseForCachedInactiveUser() {
            // Given
            User user = createUser("firebase-uid-inactive", UserType.INFLUENCER, AccountStatus.INACTIVE);
            cache.cacheUser("firebase-uid-inactive", user);

            // When
            boolean result = cache.isUserActive("firebase-uid-inactive");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should query database on cache miss and cache result")
        void shouldQueryDatabaseOnCacheMissAndCacheResult() {
            // Given
            User user = createUser("firebase-uid-miss", UserType.COMPANY, AccountStatus.ACTIVE);
            when(userRepository.findByFirebaseUserId("firebase-uid-miss")).thenReturn(Optional.of(user));

            // When
            boolean result = cache.isUserActive("firebase-uid-miss");

            // Then
            assertThat(result).isTrue();
            verify(userRepository).findByFirebaseUserId("firebase-uid-miss");

            // Second call should hit cache
            cache.isUserActive("firebase-uid-miss");
            verify(userRepository, times(1)).findByFirebaseUserId("firebase-uid-miss");
        }

        @Test
        @DisplayName("should return false when user not found in database")
        void shouldReturnFalseWhenUserNotFoundInDatabase() {
            // Given
            when(userRepository.findByFirebaseUserId("non-existent")).thenReturn(Optional.empty());

            // When
            boolean result = cache.isUserActive("non-existent");

            // Then
            assertThat(result).isFalse();
            verify(userRepository).findByFirebaseUserId("non-existent");
        }

        @Test
        @DisplayName("should return false for deleted user from database")
        void shouldReturnFalseForDeletedUserFromDatabase() {
            // Given
            User user = createUser("firebase-uid-deleted", UserType.INFLUENCER, AccountStatus.DELETED);
            when(userRepository.findByFirebaseUserId("firebase-uid-deleted")).thenReturn(Optional.of(user));

            // When
            boolean result = cache.isUserActive("firebase-uid-deleted");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for to-be-deleted user from database")
        void shouldReturnFalseForToBeDeletedUserFromDatabase() {
            // Given
            User user = createUser("firebase-uid-tbd", UserType.INFLUENCER, AccountStatus.TO_BE_DELETED);
            when(userRepository.findByFirebaseUserId("firebase-uid-tbd")).thenReturn(Optional.of(user));

            // When
            boolean result = cache.isUserActive("firebase-uid-tbd");

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("evict")
    class EvictTests {

        @Test
        @DisplayName("should remove user from cache")
        void shouldRemoveUserFromCache() {
            // Given
            User user = createUser("firebase-uid-evict", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-evict", user);
            assertThat(cache.isUserActive("firebase-uid-evict")).isTrue();

            // Setup for cache miss after eviction
            when(userRepository.findByFirebaseUserId("firebase-uid-evict")).thenReturn(Optional.empty());

            // When
            cache.evict("firebase-uid-evict");

            // Then - should query DB again after eviction
            boolean result = cache.isUserActive("firebase-uid-evict");
            assertThat(result).isFalse();
            verify(userRepository).findByFirebaseUserId("firebase-uid-evict");
        }

        @Test
        @DisplayName("should handle eviction of non-existent user gracefully")
        void shouldHandleEvictionOfNonExistentUserGracefully() {
            // When/Then - should not throw
            cache.evict("non-existent-uid");
        }

        @Test
        @DisplayName("should allow re-caching after eviction")
        void shouldAllowReCachingAfterEviction() {
            // Given
            User user = createUser("firebase-uid-recache", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-recache", user);
            cache.evict("firebase-uid-recache");

            // When
            cache.cacheUser("firebase-uid-recache", user);

            // Then
            assertThat(cache.isUserActive("firebase-uid-recache")).isTrue();
        }
    }

    @Nested
    @DisplayName("evictAll")
    class EvictAllTests {

        @Test
        @DisplayName("should clear entire cache")
        void shouldClearEntireCache() {
            // Given
            User user1 = createUser("firebase-uid-1", UserType.INFLUENCER, AccountStatus.ACTIVE);
            User user2 = createUser("firebase-uid-2", UserType.COMPANY, AccountStatus.ACTIVE);
            User user3 = createUser("firebase-uid-3", UserType.ADMIN, AccountStatus.ACTIVE);

            cache.cacheUser("firebase-uid-1", user1);
            cache.cacheUser("firebase-uid-2", user2);
            cache.cacheUser("firebase-uid-3", user3);

            // Setup for cache misses after eviction
            when(userRepository.findByFirebaseUserId(anyString())).thenReturn(Optional.empty());

            // When
            cache.evictAll();

            // Then - all should query DB
            assertThat(cache.isUserActive("firebase-uid-1")).isFalse();
            assertThat(cache.isUserActive("firebase-uid-2")).isFalse();
            assertThat(cache.isUserActive("firebase-uid-3")).isFalse();
            verify(userRepository, times(3)).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should handle evictAll on empty cache gracefully")
        void shouldHandleEvictAllOnEmptyCacheGracefully() {
            // When/Then - should not throw
            cache.evictAll();
        }
    }

    @Nested
    @DisplayName("cleanExpired")
    class CleanExpiredTests {

        @Test
        @DisplayName("should not remove non-expired entries")
        void shouldNotRemoveNonExpiredEntries() {
            // Given
            User user = createUser("firebase-uid-fresh", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-fresh", user);

            // When
            cache.cleanExpired();

            // Then - entry should still be in cache
            assertThat(cache.isUserActive("firebase-uid-fresh")).isTrue();
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should remove expired entries")
        void shouldRemoveExpiredEntries() throws Exception {
            // Given
            User user = createUser("firebase-uid-expired", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-expired", user);

            // Force expiration by manipulating the cache entry timestamp
            forceExpireEntry("firebase-uid-expired");

            // Setup for cache miss after expiration
            when(userRepository.findByFirebaseUserId("firebase-uid-expired")).thenReturn(Optional.empty());

            // When
            cache.cleanExpired();

            // Then - entry should be removed
            boolean result = cache.isUserActive("firebase-uid-expired");
            assertThat(result).isFalse();
            verify(userRepository).findByFirebaseUserId("firebase-uid-expired");
        }

        @Test
        @DisplayName("should only remove expired entries keeping fresh ones")
        void shouldOnlyRemoveExpiredEntriesKeepingFreshOnes() throws Exception {
            // Given
            User expiredUser = createUser("firebase-uid-old", UserType.INFLUENCER, AccountStatus.ACTIVE);
            User freshUser = createUser("firebase-uid-new", UserType.COMPANY, AccountStatus.ACTIVE);

            cache.cacheUser("firebase-uid-old", expiredUser);
            forceExpireEntry("firebase-uid-old");
            cache.cacheUser("firebase-uid-new", freshUser);

            when(userRepository.findByFirebaseUserId("firebase-uid-old")).thenReturn(Optional.empty());

            // When
            cache.cleanExpired();

            // Then
            assertThat(cache.isUserActive("firebase-uid-new")).isTrue();
            verify(userRepository, never()).findByFirebaseUserId("firebase-uid-new");

            boolean oldUserResult = cache.isUserActive("firebase-uid-old");
            assertThat(oldUserResult).isFalse();
            verify(userRepository).findByFirebaseUserId("firebase-uid-old");
        }
    }

    @Nested
    @DisplayName("getTokenVersion")
    class GetTokenVersionTests {

        @Test
        @DisplayName("should return cached token version")
        void shouldReturnCachedTokenVersion() {
            // Given
            User user = createUser("firebase-uid-token", UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.incrementTokenVersion();
            user.incrementTokenVersion(); // Now version is 3
            cache.cacheUser("firebase-uid-token", user);

            // When
            Long tokenVersion = cache.getTokenVersion("firebase-uid-token");

            // Then
            assertThat(tokenVersion).isEqualTo(3L);
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should query database on cache miss")
        void shouldQueryDatabaseOnCacheMiss() {
            // Given
            User user = createUser("firebase-uid-miss", UserType.INFLUENCER, AccountStatus.ACTIVE);
            when(userRepository.findByFirebaseUserId("firebase-uid-miss")).thenReturn(Optional.of(user));

            // When
            Long tokenVersion = cache.getTokenVersion("firebase-uid-miss");

            // Then
            assertThat(tokenVersion).isEqualTo(1L);
            verify(userRepository).findByFirebaseUserId("firebase-uid-miss");
        }

        @Test
        @DisplayName("should return null when user not found")
        void shouldReturnNullWhenUserNotFound() {
            // Given
            when(userRepository.findByFirebaseUserId("non-existent")).thenReturn(Optional.empty());

            // When
            Long tokenVersion = cache.getTokenVersion("non-existent");

            // Then
            assertThat(tokenVersion).isNull();
        }

        @Test
        @DisplayName("should query database when cache entry expired")
        void shouldQueryDatabaseWhenCacheEntryExpired() throws Exception {
            // Given
            User user = createUser("firebase-uid-expired", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-expired", user);
            forceExpireEntry("firebase-uid-expired");

            User freshUser = createUser("firebase-uid-expired", UserType.INFLUENCER, AccountStatus.ACTIVE);
            freshUser.incrementTokenVersion(); // Now version is 2
            when(userRepository.findByFirebaseUserId("firebase-uid-expired")).thenReturn(Optional.of(freshUser));

            // When
            Long tokenVersion = cache.getTokenVersion("firebase-uid-expired");

            // Then
            assertThat(tokenVersion).isEqualTo(2L);
            verify(userRepository).findByFirebaseUserId("firebase-uid-expired");
        }
    }

    @Nested
    @DisplayName("getAccountStatus")
    class GetAccountStatusTests {

        @Test
        @DisplayName("should return cached account status")
        void shouldReturnCachedAccountStatus() {
            // Given
            User user = createUser("firebase-uid-status", UserType.INFLUENCER, AccountStatus.BANNED);
            cache.cacheUser("firebase-uid-status", user);

            // When
            String accountStatus = cache.getAccountStatus("firebase-uid-status");

            // Then
            assertThat(accountStatus).isEqualTo("BANNED");
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should query database on cache miss")
        void shouldQueryDatabaseOnCacheMiss() {
            // Given
            User user = createUser("firebase-uid-miss", UserType.COMPANY, AccountStatus.IN_VALIDATION);
            when(userRepository.findByFirebaseUserId("firebase-uid-miss")).thenReturn(Optional.of(user));

            // When
            String accountStatus = cache.getAccountStatus("firebase-uid-miss");

            // Then
            assertThat(accountStatus).isEqualTo("IN_VALIDATION");
            verify(userRepository).findByFirebaseUserId("firebase-uid-miss");
        }

        @Test
        @DisplayName("should return null when user not found")
        void shouldReturnNullWhenUserNotFound() {
            // Given
            when(userRepository.findByFirebaseUserId("non-existent")).thenReturn(Optional.empty());

            // When
            String accountStatus = cache.getAccountStatus("non-existent");

            // Then
            assertThat(accountStatus).isNull();
        }

        @ParameterizedTest
        @EnumSource(AccountStatus.class)
        @DisplayName("should correctly cache all account status types")
        void shouldCorrectlyCacheAllAccountStatusTypes(AccountStatus status) {
            // Given
            User user = createUser("firebase-uid-" + status.name(), UserType.INFLUENCER, status);
            cache.cacheUser("firebase-uid-" + status.name(), user);

            // When
            String cachedStatus = cache.getAccountStatus("firebase-uid-" + status.name());

            // Then
            assertThat(cachedStatus).isEqualTo(status.name());
        }

        @Test
        @DisplayName("should query database when cache entry expired")
        void shouldQueryDatabaseWhenCacheEntryExpired() throws Exception {
            // Given
            User user = createUser("firebase-uid-expired", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-expired", user);
            forceExpireEntry("firebase-uid-expired");

            User freshUser = createUser("firebase-uid-expired", UserType.INFLUENCER, AccountStatus.BANNED);
            when(userRepository.findByFirebaseUserId("firebase-uid-expired")).thenReturn(Optional.of(freshUser));

            // When
            String accountStatus = cache.getAccountStatus("firebase-uid-expired");

            // Then
            assertThat(accountStatus).isEqualTo("BANNED");
            verify(userRepository).findByFirebaseUserId("firebase-uid-expired");
        }
    }

    @Nested
    @DisplayName("Cache Expiration")
    class CacheExpirationTests {

        @Test
        @DisplayName("should query database when cached entry is expired")
        void shouldQueryDatabaseWhenCachedEntryIsExpired() throws Exception {
            // Given
            User user = createUser("firebase-uid-expire", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-expire", user);

            // Force expiration
            forceExpireEntry("firebase-uid-expire");

            User freshUser = createUser("firebase-uid-expire", UserType.INFLUENCER, AccountStatus.BANNED);
            when(userRepository.findByFirebaseUserId("firebase-uid-expire")).thenReturn(Optional.of(freshUser));

            // When
            boolean result = cache.isUserActive("firebase-uid-expire");

            // Then - BANNED users can still authenticate
            assertThat(result).isTrue();
            verify(userRepository).findByFirebaseUserId("firebase-uid-expire");
        }

        @Test
        @DisplayName("should not query database when cached entry is not expired")
        void shouldNotQueryDatabaseWhenCachedEntryIsNotExpired() {
            // Given
            User user = createUser("firebase-uid-fresh", UserType.INFLUENCER, AccountStatus.ACTIVE);
            cache.cacheUser("firebase-uid-fresh", user);

            // When
            boolean result = cache.isUserActive("firebase-uid-fresh");

            // Then
            assertThat(result).isTrue();
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }
    }

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("should handle concurrent cache operations safely")
        void shouldHandleConcurrentCacheOperationsSafely() throws InterruptedException {
            // Given
            int threadCount = 10;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            String userId = "user-" + threadId + "-" + j;
                            User user = createUser(userId, UserType.INFLUENCER, AccountStatus.ACTIVE);

                            // Mix of operations
                            cache.cacheUser(userId, user);
                            cache.isUserActive(userId);
                            cache.getTokenVersion(userId);
                            cache.getAccountStatus(userId);

                            if (j % 5 == 0) {
                                cache.evict(userId);
                            }

                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Log failure but don't fail test prematurely
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(threadCount * operationsPerThread);
        }

        @Test
        @DisplayName("should handle concurrent evictAll safely")
        void shouldHandleConcurrentEvictAllSafely() throws InterruptedException {
            // Given
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            // Pre-populate cache
            for (int i = 0; i < 100; i++) {
                User user = createUser("user-" + i, UserType.INFLUENCER, AccountStatus.ACTIVE);
                cache.cacheUser("user-" + i, user);
            }

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        cache.evictAll();
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - should complete without deadlock or exception
            assertThat(completed).isTrue();
        }

        @Test
        @DisplayName("should handle concurrent read and write operations")
        void shouldHandleConcurrentReadAndWriteOperations() throws InterruptedException {
            // Given
            int threadCount = 8;
            int operationsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger writeCount = new AtomicInteger(0);
            AtomicInteger readCount = new AtomicInteger(0);

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            String userId = "shared-user-" + (j % 10); // Shared keys to force contention

                            if (threadId % 2 == 0) {
                                // Writer thread
                                User user = createUser(userId, UserType.INFLUENCER, AccountStatus.ACTIVE);
                                cache.cacheUser(userId, user);
                                writeCount.incrementAndGet();
                            } else {
                                // Reader thread
                                cache.isUserActive(userId);
                                cache.getAccountStatus(userId);
                                readCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(writeCount.get()).isGreaterThan(0);
            assertThat(readCount.get()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should throw NullPointerException for null userId in isUserActive")
        void shouldThrowNullPointerExceptionForNullUserIdInIsUserActive() {
            // ConcurrentHashMap does not allow null keys
            // This is expected behavior - null userIds should never be passed to the cache

            // When/Then
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> {
                cache.isUserActive(null);
            });
        }

        @Test
        @DisplayName("should handle empty userId gracefully")
        void shouldHandleEmptyUserIdGracefully() {
            // Given
            when(userRepository.findByFirebaseUserId("")).thenReturn(Optional.empty());

            // When
            boolean result = cache.isUserActive("");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle very long userId")
        void shouldHandleVeryLongUserId() {
            // Given
            String longUserId = "a".repeat(1000);
            User user = createUser(longUserId, UserType.INFLUENCER, AccountStatus.ACTIVE);

            // When
            cache.cacheUser(longUserId, user);

            // Then
            assertThat(cache.isUserActive(longUserId)).isTrue();
        }

        @Test
        @DisplayName("should handle special characters in userId")
        void shouldHandleSpecialCharactersInUserId() {
            // Given
            String specialUserId = "user_123-test.account";
            User user = createUser(specialUserId, UserType.INFLUENCER, AccountStatus.ACTIVE);

            // When
            cache.cacheUser(specialUserId, user);

            // Then
            assertThat(cache.isUserActive(specialUserId)).isTrue();
            assertThat(cache.getAccountStatus(specialUserId)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should handle default token version for new user")
        void shouldHandleDefaultTokenVersionForNewUser() {
            // Given
            User user = createUser("firebase-uid-new", UserType.INFLUENCER, AccountStatus.ACTIVE);
            // Default token version is 1

            // When
            cache.cacheUser("firebase-uid-new", user);
            Long tokenVersion = cache.getTokenVersion("firebase-uid-new");

            // Then
            assertThat(tokenVersion).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("User Type Handling")
    class UserTypeHandlingTests {

        @ParameterizedTest
        @EnumSource(UserType.class)
        @DisplayName("should cache all user types correctly")
        void shouldCacheAllUserTypesCorrectly(UserType userType) {
            // Given
            User user = createUser("firebase-uid-" + userType.name(), userType, AccountStatus.ACTIVE);

            // When
            cache.cacheUser("firebase-uid-" + userType.name(), user);

            // Then
            assertThat(cache.isUserActive("firebase-uid-" + userType.name())).isTrue();
        }

        @Test
        @DisplayName("should cache admin user correctly")
        void shouldCacheAdminUserCorrectly() {
            // Given
            User adminUser = createUser("firebase-uid-admin", UserType.ADMIN, AccountStatus.ACTIVE);

            // When
            cache.cacheUser("firebase-uid-admin", adminUser);

            // Then
            assertThat(cache.isUserActive("firebase-uid-admin")).isTrue();
            assertThat(cache.getAccountStatus("firebase-uid-admin")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should cache pending admin user correctly")
        void shouldCachePendingAdminUserCorrectly() {
            // Given
            User pendingAdmin = createUser("firebase-uid-pending-admin", UserType.PENDING_ADMIN, AccountStatus.IN_VALIDATION);

            // When
            cache.cacheUser("firebase-uid-pending-admin", pendingAdmin);

            // Then
            assertThat(cache.isUserActive("firebase-uid-pending-admin")).isTrue();
        }
    }

    // Helper method to force cache entry expiration for testing
    @SuppressWarnings("unchecked")
    private void forceExpireEntry(String userId) throws Exception {
        // Access the private cache field via reflection
        Field cacheField = InMemoryUserCache.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        Map<String, Object> internalCache = (Map<String, Object>) cacheField.get(cache);

        Object cachedUser = internalCache.get(userId);
        if (cachedUser != null) {
            // Access the timestamp field in CachedUser inner class
            Class<?> cachedUserClass = cachedUser.getClass();
            Method setTimestampMethod = null;

            // Try to find setTimestamp method
            for (Method method : cachedUserClass.getDeclaredMethods()) {
                if (method.getName().equals("setTimestamp")) {
                    setTimestampMethod = method;
                    break;
                }
            }

            if (setTimestampMethod != null) {
                setTimestampMethod.setAccessible(true);
                // Set timestamp to 10 minutes ago (beyond 5 minute TTL)
                setTimestampMethod.invoke(cachedUser, System.currentTimeMillis() - (10 * 60 * 1000));
            }
        }
    }
}
