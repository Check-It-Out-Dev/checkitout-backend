package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.InMemoryUserCache;
import com.sm.instagram.platform.auth.cache.RedisUserCache;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserCacheService implementations.
 * Tests cache delegation logic, fallback behavior, and cache miss scenarios.
 * Covers both InMemoryUserCache and RedisUserCache implementations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserCacheService Unit Tests")
class UserCacheServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    private InMemoryUserCache inMemoryUserCache;
    private RedisUserCache redisUserCache;

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
        inMemoryUserCache = new InMemoryUserCache(userRepository);
        redisUserCache = new RedisUserCache(redisTemplate, userRepository, objectMapper);

        // Setup Redis mock to return value operations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== InMemoryUserCache Tests ====================

    @Nested
    @DisplayName("InMemoryUserCache")
    class InMemoryUserCacheTests {

        @Nested
        @DisplayName("cacheUser")
        class CacheUserTests {

            @Test
            @DisplayName("should cache ACTIVE user as active (can authenticate)")
            void shouldCacheActiveUserAsActive() {
                // Given
                User user = createUser("firebase-uid-1", UserType.INFLUENCER, AccountStatus.ACTIVE);

                // When
                inMemoryUserCache.cacheUser("firebase-uid-1", user);

                // Then - verify by checking isUserActive (should hit cache)
                boolean result = inMemoryUserCache.isUserActive("firebase-uid-1");
                assertThat(result).isTrue();
                // Repository should NOT be called since we have a cache hit
                verify(userRepository, never()).findByFirebaseUserId(anyString());
            }

            @Test
            @DisplayName("should cache IN_VALIDATION user as active (can authenticate)")
            void shouldCacheInValidationUserAsActive() {
                // Given
                User user = createUser("firebase-uid-2", UserType.COMPANY, AccountStatus.IN_VALIDATION);

                // When
                inMemoryUserCache.cacheUser("firebase-uid-2", user);

                // Then
                boolean result = inMemoryUserCache.isUserActive("firebase-uid-2");
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should cache BANNED user as active (can authenticate to see ban reason)")
            void shouldCacheBannedUserAsActive() {
                // Given - BANNED users CAN authenticate (to see why they're banned)
                User user = createUser("firebase-uid-3", UserType.INFLUENCER, AccountStatus.BANNED);

                // When
                inMemoryUserCache.cacheUser("firebase-uid-3", user);

                // Then
                boolean result = inMemoryUserCache.isUserActive("firebase-uid-3");
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should cache INACTIVE user as not active (cannot authenticate)")
            void shouldCacheInactiveUserAsNotActive() {
                // Given
                User user = createUser("firebase-uid-4", UserType.INFLUENCER, AccountStatus.INACTIVE);

                // When
                inMemoryUserCache.cacheUser("firebase-uid-4", user);

                // Then
                boolean result = inMemoryUserCache.isUserActive("firebase-uid-4");
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should cache DELETED user as not active (cannot authenticate)")
            void shouldCacheDeletedUserAsNotActive() {
                // Given
                User user = createUser("firebase-uid-5", UserType.COMPANY, AccountStatus.DELETED);

                // When
                inMemoryUserCache.cacheUser("firebase-uid-5", user);

                // Then
                boolean result = inMemoryUserCache.isUserActive("firebase-uid-5");
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should cache TO_BE_DELETED user as not active (cannot authenticate)")
            void shouldCacheToBeDeletedUserAsNotActive() {
                // Given
                User user = createUser("firebase-uid-6", UserType.INFLUENCER, AccountStatus.TO_BE_DELETED);

                // When
                inMemoryUserCache.cacheUser("firebase-uid-6", user);

                // Then
                boolean result = inMemoryUserCache.isUserActive("firebase-uid-6");
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should cache token version")
            void shouldCacheTokenVersion() {
                // Given
                User user = createUser("firebase-uid-7", UserType.ADMIN, AccountStatus.ACTIVE);
                user.setTokenVersion(5L);

                // When
                inMemoryUserCache.cacheUser("firebase-uid-7", user);

                // Then
                Long tokenVersion = inMemoryUserCache.getTokenVersion("firebase-uid-7");
                assertThat(tokenVersion).isEqualTo(5L);
            }

            @Test
            @DisplayName("should cache account status string")
            void shouldCacheAccountStatusString() {
                // Given
                User user = createUser("firebase-uid-8", UserType.COMPANY, AccountStatus.BANNED);

                // When
                inMemoryUserCache.cacheUser("firebase-uid-8", user);

                // Then
                String status = inMemoryUserCache.getAccountStatus("firebase-uid-8");
                assertThat(status).isEqualTo("BANNED");
            }
        }

        @Nested
        @DisplayName("isUserActive")
        class IsUserActiveTests {

            @Test
            @DisplayName("should return true from cache for active user")
            void shouldReturnTrueFromCacheForActiveUser() {
                // Given
                User user = createUser("cached-user", UserType.INFLUENCER, AccountStatus.ACTIVE);
                inMemoryUserCache.cacheUser("cached-user", user);

                // When
                boolean result = inMemoryUserCache.isUserActive("cached-user");

                // Then
                assertThat(result).isTrue();
                verify(userRepository, never()).findByFirebaseUserId(anyString());
            }

            @Test
            @DisplayName("should query database on cache miss")
            void shouldQueryDatabaseOnCacheMiss() {
                // Given
                User user = createUser("db-user", UserType.COMPANY, AccountStatus.ACTIVE);
                when(userRepository.findByFirebaseUserId("db-user")).thenReturn(Optional.of(user));

                // When
                boolean result = inMemoryUserCache.isUserActive("db-user");

                // Then
                assertThat(result).isTrue();
                verify(userRepository).findByFirebaseUserId("db-user");
            }

            @Test
            @DisplayName("should return false when user not found in database")
            void shouldReturnFalseWhenUserNotFoundInDatabase() {
                // Given
                when(userRepository.findByFirebaseUserId("unknown-user")).thenReturn(Optional.empty());

                // When
                boolean result = inMemoryUserCache.isUserActive("unknown-user");

                // Then
                assertThat(result).isFalse();
                verify(userRepository).findByFirebaseUserId("unknown-user");
            }

            @Test
            @DisplayName("should cache user after database query")
            void shouldCacheUserAfterDatabaseQuery() {
                // Given
                User user = createUser("to-be-cached", UserType.INFLUENCER, AccountStatus.ACTIVE);
                when(userRepository.findByFirebaseUserId("to-be-cached")).thenReturn(Optional.of(user));

                // When - first call triggers DB query
                inMemoryUserCache.isUserActive("to-be-cached");

                // Then - second call should hit cache
                inMemoryUserCache.isUserActive("to-be-cached");
                verify(userRepository, times(1)).findByFirebaseUserId("to-be-cached");
            }

            @ParameterizedTest
            @EnumSource(value = AccountStatus.class, names = {"INACTIVE", "DELETED", "TO_BE_DELETED"})
            @DisplayName("should return false for non-authenticable statuses from database")
            void shouldReturnFalseForNonAuthenticableStatusesFromDatabase(AccountStatus status) {
                // Given
                User user = createUser("status-test", UserType.INFLUENCER, status);
                when(userRepository.findByFirebaseUserId("status-test")).thenReturn(Optional.of(user));

                // When
                boolean result = inMemoryUserCache.isUserActive("status-test");

                // Then
                assertThat(result).isFalse();
            }

            @ParameterizedTest
            @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "IN_VALIDATION", "BANNED"})
            @DisplayName("should return true for authenticable statuses from database")
            void shouldReturnTrueForAuthenticableStatusesFromDatabase(AccountStatus status) {
                // Given
                User user = createUser("auth-status-test", UserType.COMPANY, status);
                when(userRepository.findByFirebaseUserId("auth-status-test")).thenReturn(Optional.of(user));

                // When
                boolean result = inMemoryUserCache.isUserActive("auth-status-test");

                // Then
                assertThat(result).isTrue();
            }
        }

        @Nested
        @DisplayName("evict")
        class EvictTests {

            @Test
            @DisplayName("should remove user from cache")
            void shouldRemoveUserFromCache() {
                // Given
                User user = createUser("evict-test", UserType.INFLUENCER, AccountStatus.ACTIVE);
                inMemoryUserCache.cacheUser("evict-test", user);
                when(userRepository.findByFirebaseUserId("evict-test")).thenReturn(Optional.of(user));

                // When
                inMemoryUserCache.evict("evict-test");

                // Then - next call should hit database
                inMemoryUserCache.isUserActive("evict-test");
                verify(userRepository).findByFirebaseUserId("evict-test");
            }

            @Test
            @DisplayName("should handle evicting non-existent user gracefully")
            void shouldHandleEvictingNonExistentUserGracefully() {
                // When/Then - no exception should be thrown
                inMemoryUserCache.evict("non-existent-user");
            }
        }

        @Nested
        @DisplayName("evictAll")
        class EvictAllTests {

            @Test
            @DisplayName("should clear all cached users")
            void shouldClearAllCachedUsers() {
                // Given
                User user1 = createUser("user-1", UserType.INFLUENCER, AccountStatus.ACTIVE);
                User user2 = createUser("user-2", UserType.COMPANY, AccountStatus.ACTIVE);
                inMemoryUserCache.cacheUser("user-1", user1);
                inMemoryUserCache.cacheUser("user-2", user2);

                when(userRepository.findByFirebaseUserId("user-1")).thenReturn(Optional.of(user1));
                when(userRepository.findByFirebaseUserId("user-2")).thenReturn(Optional.of(user2));

                // When
                inMemoryUserCache.evictAll();

                // Then - both should trigger database queries
                inMemoryUserCache.isUserActive("user-1");
                inMemoryUserCache.isUserActive("user-2");
                verify(userRepository).findByFirebaseUserId("user-1");
                verify(userRepository).findByFirebaseUserId("user-2");
            }

            @Test
            @DisplayName("should handle evictAll on empty cache gracefully")
            void shouldHandleEvictAllOnEmptyCacheGracefully() {
                // When/Then - no exception should be thrown
                inMemoryUserCache.evictAll();
            }
        }

        @Nested
        @DisplayName("getTokenVersion")
        class GetTokenVersionTests {

            @Test
            @DisplayName("should return token version from cache")
            void shouldReturnTokenVersionFromCache() {
                // Given
                User user = createUser("token-user", UserType.ADMIN, AccountStatus.ACTIVE);
                user.setTokenVersion(10L);
                inMemoryUserCache.cacheUser("token-user", user);

                // When
                Long tokenVersion = inMemoryUserCache.getTokenVersion("token-user");

                // Then
                assertThat(tokenVersion).isEqualTo(10L);
            }

            @Test
            @DisplayName("should query database on cache miss for token version")
            void shouldQueryDatabaseOnCacheMissForTokenVersion() {
                // Given
                User user = createUser("token-db-user", UserType.COMPANY, AccountStatus.ACTIVE);
                user.setTokenVersion(15L);
                when(userRepository.findByFirebaseUserId("token-db-user")).thenReturn(Optional.of(user));

                // When
                Long tokenVersion = inMemoryUserCache.getTokenVersion("token-db-user");

                // Then
                assertThat(tokenVersion).isEqualTo(15L);
                verify(userRepository).findByFirebaseUserId("token-db-user");
            }

            @Test
            @DisplayName("should return null when user not found")
            void shouldReturnNullWhenUserNotFound() {
                // Given
                when(userRepository.findByFirebaseUserId("unknown")).thenReturn(Optional.empty());

                // When
                Long tokenVersion = inMemoryUserCache.getTokenVersion("unknown");

                // Then
                assertThat(tokenVersion).isNull();
            }
        }

        @Nested
        @DisplayName("getAccountStatus")
        class GetAccountStatusTests {

            @Test
            @DisplayName("should return account status from cache")
            void shouldReturnAccountStatusFromCache() {
                // Given
                User user = createUser("status-user", UserType.INFLUENCER, AccountStatus.BANNED);
                inMemoryUserCache.cacheUser("status-user", user);

                // When
                String status = inMemoryUserCache.getAccountStatus("status-user");

                // Then
                assertThat(status).isEqualTo("BANNED");
            }

            @Test
            @DisplayName("should query database on cache miss for account status")
            void shouldQueryDatabaseOnCacheMissForAccountStatus() {
                // Given
                User user = createUser("status-db-user", UserType.COMPANY, AccountStatus.IN_VALIDATION);
                when(userRepository.findByFirebaseUserId("status-db-user")).thenReturn(Optional.of(user));

                // When
                String status = inMemoryUserCache.getAccountStatus("status-db-user");

                // Then
                assertThat(status).isEqualTo("IN_VALIDATION");
                verify(userRepository).findByFirebaseUserId("status-db-user");
            }

            @Test
            @DisplayName("should return null when user not found for account status")
            void shouldReturnNullWhenUserNotFoundForAccountStatus() {
                // Given
                when(userRepository.findByFirebaseUserId("unknown")).thenReturn(Optional.empty());

                // When
                String status = inMemoryUserCache.getAccountStatus("unknown");

                // Then
                assertThat(status).isNull();
            }

            @ParameterizedTest
            @EnumSource(AccountStatus.class)
            @DisplayName("should correctly cache and retrieve all account statuses")
            void shouldCorrectlyCacheAndRetrieveAllAccountStatuses(AccountStatus accountStatus) {
                // Given
                String userId = "status-" + accountStatus.name();
                User user = createUser(userId, UserType.INFLUENCER, accountStatus);
                inMemoryUserCache.cacheUser(userId, user);

                // When
                String status = inMemoryUserCache.getAccountStatus(userId);

                // Then
                assertThat(status).isEqualTo(accountStatus.name());
            }
        }

        @Nested
        @DisplayName("cleanExpired")
        class CleanExpiredTests {

            @Test
            @DisplayName("should not remove fresh entries")
            void shouldNotRemoveFreshEntries() {
                // Given
                User user = createUser("fresh-user", UserType.INFLUENCER, AccountStatus.ACTIVE);
                inMemoryUserCache.cacheUser("fresh-user", user);

                // When
                inMemoryUserCache.cleanExpired();

                // Then - entry should still be in cache
                boolean result = inMemoryUserCache.isUserActive("fresh-user");
                assertThat(result).isTrue();
                verify(userRepository, never()).findByFirebaseUserId(anyString());
            }
        }
    }

    // ==================== RedisUserCache Tests ====================

    @Nested
    @DisplayName("RedisUserCache")
    class RedisUserCacheTests {

        @Nested
        @DisplayName("cacheUser")
        class CacheUserTests {

            @Test
            @DisplayName("should cache user data in Redis")
            void shouldCacheUserDataInRedis() throws JsonProcessingException {
                // Given
                User user = createUser("redis-user", UserType.INFLUENCER, AccountStatus.ACTIVE);
                user.setTokenVersion(5L);
                when(objectMapper.writeValueAsString(anyMap())).thenReturn("{\"active\":\"true\",\"role\":\"INFLUENCER\",\"status\":\"ACTIVE\",\"tokenVersion\":\"5\"}");

                // When
                redisUserCache.cacheUser("redis-user", user);

                // Then
                verify(valueOperations).set(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES));
            }

            @Test
            @DisplayName("should handle JsonProcessingException gracefully")
            void shouldHandleJsonProcessingExceptionGracefully() throws JsonProcessingException {
                // Given
                User user = createUser("error-user", UserType.COMPANY, AccountStatus.ACTIVE);
                when(objectMapper.writeValueAsString(anyMap())).thenThrow(mock(JsonProcessingException.class));

                // When/Then - no exception should propagate
                redisUserCache.cacheUser("error-user", user);
            }

            @Test
            @DisplayName("should cache BANNED user as active for authentication")
            void shouldCacheBannedUserAsActiveForAuthentication() throws JsonProcessingException {
                // Given
                User user = createUser("banned-user", UserType.INFLUENCER, AccountStatus.BANNED);
                when(objectMapper.writeValueAsString(anyMap())).thenReturn("{\"active\":\"true\"}");

                // When
                redisUserCache.cacheUser("banned-user", user);

                // Then - verify it was cached (BANNED can authenticate)
                verify(valueOperations).set(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES));
            }
        }

        @Nested
        @DisplayName("isUserActive")
        class IsUserActiveTests {

            @Test
            @DisplayName("should return true from Redis cache for active user")
            void shouldReturnTrueFromRedisCacheForActiveUser() throws JsonProcessingException {
                // Given
                String cachedJson = "{\"active\":\"true\",\"role\":\"INFLUENCER\",\"status\":\"ACTIVE\"}";
                when(valueOperations.get(anyString())).thenReturn(cachedJson);
                when(objectMapper.readValue(eq(cachedJson), eq(java.util.Map.class)))
                        .thenReturn(java.util.Map.of("active", "true", "role", "INFLUENCER", "status", "ACTIVE"));

                // When
                boolean result = redisUserCache.isUserActive("cached-redis-user");

                // Then
                assertThat(result).isTrue();
                verify(userRepository, never()).findByFirebaseUserId(anyString());
            }

            @Test
            @DisplayName("should return false from Redis cache for inactive user")
            void shouldReturnFalseFromRedisCacheForInactiveUser() throws JsonProcessingException {
                // Given
                String cachedJson = "{\"active\":\"false\",\"role\":\"INFLUENCER\",\"status\":\"INACTIVE\"}";
                when(valueOperations.get(anyString())).thenReturn(cachedJson);
                when(objectMapper.readValue(eq(cachedJson), eq(java.util.Map.class)))
                        .thenReturn(java.util.Map.of("active", "false", "role", "INFLUENCER", "status", "INACTIVE"));

                // When
                boolean result = redisUserCache.isUserActive("inactive-redis-user");

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should query database on cache miss")
            void shouldQueryDatabaseOnCacheMiss() throws JsonProcessingException {
                // Given
                when(valueOperations.get(anyString())).thenReturn(null);
                User user = createUser("db-redis-user", UserType.COMPANY, AccountStatus.ACTIVE);
                when(userRepository.findByFirebaseUserId("db-redis-user")).thenReturn(Optional.of(user));
                when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

                // When
                boolean result = redisUserCache.isUserActive("db-redis-user");

                // Then
                assertThat(result).isTrue();
                verify(userRepository).findByFirebaseUserId("db-redis-user");
            }

            @Test
            @DisplayName("should return false when user not found in database")
            void shouldReturnFalseWhenUserNotFoundInDatabase() {
                // Given
                when(valueOperations.get(anyString())).thenReturn(null);
                when(userRepository.findByFirebaseUserId("unknown-redis")).thenReturn(Optional.empty());

                // When
                boolean result = redisUserCache.isUserActive("unknown-redis");

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should handle parse exception gracefully and query database")
            void shouldHandleParseExceptionGracefullyAndQueryDatabase() throws JsonProcessingException {
                // Given
                String invalidJson = "invalid-json";
                when(valueOperations.get(anyString())).thenReturn(invalidJson);
                when(objectMapper.readValue(eq(invalidJson), eq(java.util.Map.class)))
                        .thenThrow(mock(JsonProcessingException.class));
                User user = createUser("parse-error-user", UserType.INFLUENCER, AccountStatus.ACTIVE);
                when(userRepository.findByFirebaseUserId("parse-error-user")).thenReturn(Optional.of(user));
                when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

                // When
                boolean result = redisUserCache.isUserActive("parse-error-user");

                // Then - should fall back to database
                assertThat(result).isTrue();
                verify(userRepository).findByFirebaseUserId("parse-error-user");
            }
        }

        @Nested
        @DisplayName("evict")
        class EvictTests {

            @Test
            @DisplayName("should delete key from Redis")
            void shouldDeleteKeyFromRedis() {
                // When
                redisUserCache.evict("evict-redis-user");

                // Then
                verify(redisTemplate).delete(anyString());
            }
        }

        @Nested
        @DisplayName("evictAll")
        class EvictAllTests {

            @Test
            @DisplayName("should delete all user cache keys from Redis")
            void shouldDeleteAllUserCacheKeysFromRedis() {
                // Given
                Set<String> keys = new HashSet<>();
                keys.add("user_cache:hash1");
                keys.add("user_cache:hash2");
                when(redisTemplate.keys("user_cache:*")).thenReturn(keys);

                // When
                redisUserCache.evictAll();

                // Then
                verify(redisTemplate).delete(keys);
            }

            @Test
            @DisplayName("should handle empty keys gracefully")
            void shouldHandleEmptyKeysGracefully() {
                // Given
                when(redisTemplate.keys("user_cache:*")).thenReturn(new HashSet<>());

                // When
                redisUserCache.evictAll();

                // Then
                verify(redisTemplate, never()).delete(anySet());
            }

            @Test
            @DisplayName("should handle null keys gracefully")
            void shouldHandleNullKeysGracefully() {
                // Given
                when(redisTemplate.keys("user_cache:*")).thenReturn(null);

                // When/Then - no exception
                redisUserCache.evictAll();
                verify(redisTemplate, never()).delete(anySet());
            }
        }

        @Nested
        @DisplayName("getTokenVersion")
        class GetTokenVersionTests {

            @Test
            @DisplayName("should return token version from Redis cache")
            void shouldReturnTokenVersionFromRedisCache() throws JsonProcessingException {
                // Given
                String cachedJson = "{\"tokenVersion\":\"10\"}";
                when(valueOperations.get(anyString())).thenReturn(cachedJson);
                when(objectMapper.readValue(eq(cachedJson), eq(java.util.Map.class)))
                        .thenReturn(java.util.Map.of("tokenVersion", "10"));

                // When
                Long tokenVersion = redisUserCache.getTokenVersion("token-redis-user");

                // Then
                assertThat(tokenVersion).isEqualTo(10L);
            }

            @Test
            @DisplayName("should query database on cache miss for token version")
            void shouldQueryDatabaseOnCacheMissForTokenVersion() throws JsonProcessingException {
                // Given
                when(valueOperations.get(anyString())).thenReturn(null);
                User user = createUser("token-db-redis", UserType.ADMIN, AccountStatus.ACTIVE);
                user.setTokenVersion(25L);
                when(userRepository.findByFirebaseUserId("token-db-redis")).thenReturn(Optional.of(user));
                when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

                // When
                Long tokenVersion = redisUserCache.getTokenVersion("token-db-redis");

                // Then
                assertThat(tokenVersion).isEqualTo(25L);
                verify(userRepository).findByFirebaseUserId("token-db-redis");
            }

            @Test
            @DisplayName("should return null when token version not in cache and user not found")
            void shouldReturnNullWhenTokenVersionNotInCacheAndUserNotFound() {
                // Given
                when(valueOperations.get(anyString())).thenReturn(null);
                when(userRepository.findByFirebaseUserId("unknown")).thenReturn(Optional.empty());

                // When
                Long tokenVersion = redisUserCache.getTokenVersion("unknown");

                // Then
                assertThat(tokenVersion).isNull();
            }

            @Test
            @DisplayName("should handle parse exception and query database")
            void shouldHandleParseExceptionAndQueryDatabase() throws JsonProcessingException {
                // Given
                String invalidJson = "not-json";
                when(valueOperations.get(anyString())).thenReturn(invalidJson);
                when(objectMapper.readValue(eq(invalidJson), eq(java.util.Map.class)))
                        .thenThrow(mock(JsonProcessingException.class));
                User user = createUser("parse-token", UserType.COMPANY, AccountStatus.ACTIVE);
                user.setTokenVersion(30L);
                when(userRepository.findByFirebaseUserId("parse-token")).thenReturn(Optional.of(user));
                when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

                // When
                Long tokenVersion = redisUserCache.getTokenVersion("parse-token");

                // Then
                assertThat(tokenVersion).isEqualTo(30L);
            }

            @Test
            @DisplayName("should return null when tokenVersion key is missing from cached data")
            void shouldReturnNullWhenTokenVersionKeyMissingFromCachedData() throws JsonProcessingException {
                // Given
                String cachedJson = "{\"active\":\"true\"}";
                when(valueOperations.get(anyString())).thenReturn(cachedJson);
                when(objectMapper.readValue(eq(cachedJson), eq(java.util.Map.class)))
                        .thenReturn(java.util.Map.of("active", "true"));
                when(userRepository.findByFirebaseUserId("missing-token")).thenReturn(Optional.empty());

                // When
                Long tokenVersion = redisUserCache.getTokenVersion("missing-token");

                // Then - falls through to DB which returns empty
                assertThat(tokenVersion).isNull();
            }
        }

        @Nested
        @DisplayName("getAccountStatus")
        class GetAccountStatusTests {

            @Test
            @DisplayName("should return account status from Redis cache")
            void shouldReturnAccountStatusFromRedisCache() throws JsonProcessingException {
                // Given
                String cachedJson = "{\"status\":\"BANNED\"}";
                when(valueOperations.get(anyString())).thenReturn(cachedJson);
                when(objectMapper.readValue(eq(cachedJson), eq(java.util.Map.class)))
                        .thenReturn(java.util.Map.of("status", "BANNED"));

                // When
                String status = redisUserCache.getAccountStatus("status-redis-user");

                // Then
                assertThat(status).isEqualTo("BANNED");
            }

            @Test
            @DisplayName("should query database on cache miss for account status")
            void shouldQueryDatabaseOnCacheMissForAccountStatus() throws JsonProcessingException {
                // Given
                when(valueOperations.get(anyString())).thenReturn(null);
                User user = createUser("status-db-redis", UserType.INFLUENCER, AccountStatus.TO_BE_DELETED);
                when(userRepository.findByFirebaseUserId("status-db-redis")).thenReturn(Optional.of(user));
                when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

                // When
                String status = redisUserCache.getAccountStatus("status-db-redis");

                // Then
                assertThat(status).isEqualTo("TO_BE_DELETED");
                verify(userRepository).findByFirebaseUserId("status-db-redis");
            }

            @Test
            @DisplayName("should return null when user not found for account status")
            void shouldReturnNullWhenUserNotFoundForAccountStatus() {
                // Given
                when(valueOperations.get(anyString())).thenReturn(null);
                when(userRepository.findByFirebaseUserId("unknown")).thenReturn(Optional.empty());

                // When
                String status = redisUserCache.getAccountStatus("unknown");

                // Then
                assertThat(status).isNull();
            }

            @Test
            @DisplayName("should handle parse exception and query database for status")
            void shouldHandleParseExceptionAndQueryDatabaseForStatus() throws JsonProcessingException {
                // Given
                String invalidJson = "invalid";
                when(valueOperations.get(anyString())).thenReturn(invalidJson);
                when(objectMapper.readValue(eq(invalidJson), eq(java.util.Map.class)))
                        .thenThrow(mock(JsonProcessingException.class));
                User user = createUser("parse-status", UserType.COMPANY, AccountStatus.DELETED);
                when(userRepository.findByFirebaseUserId("parse-status")).thenReturn(Optional.of(user));
                when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

                // When
                String status = redisUserCache.getAccountStatus("parse-status");

                // Then
                assertThat(status).isEqualTo("DELETED");
            }
        }
    }

    // ==================== Cross-Implementation Tests ====================

    @Nested
    @DisplayName("Cross-Implementation Behavior")
    class CrossImplementationTests {

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"INACTIVE", "DELETED", "TO_BE_DELETED"})
        @DisplayName("both implementations should return false for non-authenticable statuses")
        void bothImplementationsShouldReturnFalseForNonAuthenticableStatuses(AccountStatus status) throws JsonProcessingException {
            // Given
            User user = createUser("cross-test", UserType.INFLUENCER, status);

            // Test InMemory
            inMemoryUserCache.cacheUser("cross-test-memory", user);
            assertThat(inMemoryUserCache.isUserActive("cross-test-memory")).isFalse();

            // Test Redis - cache hit scenario
            String cachedJson = "{\"active\":\"false\",\"status\":\"" + status.name() + "\"}";
            when(valueOperations.get(anyString())).thenReturn(cachedJson);
            when(objectMapper.readValue(eq(cachedJson), eq(java.util.Map.class)))
                    .thenReturn(java.util.Map.of("active", "false", "status", status.name()));
            assertThat(redisUserCache.isUserActive("cross-test-redis")).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "IN_VALIDATION", "BANNED"})
        @DisplayName("both implementations should return true for authenticable statuses")
        void bothImplementationsShouldReturnTrueForAuthenticableStatuses(AccountStatus status) throws JsonProcessingException {
            // Given
            User user = createUser("cross-auth-test", UserType.COMPANY, status);

            // Test InMemory
            inMemoryUserCache.cacheUser("cross-auth-memory", user);
            assertThat(inMemoryUserCache.isUserActive("cross-auth-memory")).isTrue();

            // Test Redis - cache hit scenario
            String cachedJson = "{\"active\":\"true\",\"status\":\"" + status.name() + "\"}";
            when(valueOperations.get(anyString())).thenReturn(cachedJson);
            when(objectMapper.readValue(eq(cachedJson), eq(java.util.Map.class)))
                    .thenReturn(java.util.Map.of("active", "true", "status", status.name()));
            assertThat(redisUserCache.isUserActive("cross-auth-redis")).isTrue();
        }

        @Test
        @DisplayName("both implementations should cache user after database query")
        void bothImplementationsShouldCacheUserAfterDatabaseQuery() throws JsonProcessingException {
            // Given
            User user = createUser("cache-after-db", UserType.ADMIN, AccountStatus.ACTIVE);
            user.setTokenVersion(100L);
            when(userRepository.findByFirebaseUserId("cache-after-db")).thenReturn(Optional.of(user));
            when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

            // When - InMemory
            inMemoryUserCache.isUserActive("cache-after-db");
            inMemoryUserCache.isUserActive("cache-after-db");

            // Then - only one DB call for InMemory
            verify(userRepository, times(1)).findByFirebaseUserId("cache-after-db");

            // Reset for Redis test
            reset(userRepository);
            when(userRepository.findByFirebaseUserId("cache-after-db-redis")).thenReturn(Optional.of(user));
            when(valueOperations.get(anyString())).thenReturn(null);

            // When - Redis (first call)
            redisUserCache.isUserActive("cache-after-db-redis");

            // Then - DB was called and user was cached
            verify(userRepository).findByFirebaseUserId("cache-after-db-redis");
            verify(valueOperations).set(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    @DisplayName("UserType Handling")
    class UserTypeHandlingTests {

        @ParameterizedTest
        @EnumSource(UserType.class)
        @DisplayName("should correctly cache all user types")
        void shouldCorrectlyCacheAllUserTypes(UserType userType) {
            // Given
            String userId = "type-" + userType.name();
            User user = createUser(userId, userType, AccountStatus.ACTIVE);

            // When
            inMemoryUserCache.cacheUser(userId, user);

            // Then
            assertThat(inMemoryUserCache.isUserActive(userId)).isTrue();
        }
    }

    @Nested
    @DisplayName("Token Version Behavior")
    class TokenVersionBehaviorTests {

        @Test
        @DisplayName("should handle default token version of 1")
        void shouldHandleDefaultTokenVersionOfOne() {
            // Given
            User user = createUser("default-token", UserType.INFLUENCER, AccountStatus.ACTIVE);
            // Default token version is 1

            // When
            inMemoryUserCache.cacheUser("default-token", user);

            // Then
            Long tokenVersion = inMemoryUserCache.getTokenVersion("default-token");
            assertThat(tokenVersion).isEqualTo(1L);
        }

        @Test
        @DisplayName("should handle large token version values")
        void shouldHandleLargeTokenVersionValues() {
            // Given
            User user = createUser("large-token", UserType.ADMIN, AccountStatus.ACTIVE);
            user.setTokenVersion(Long.MAX_VALUE);

            // When
            inMemoryUserCache.cacheUser("large-token", user);

            // Then
            Long tokenVersion = inMemoryUserCache.getTokenVersion("large-token");
            assertThat(tokenVersion).isEqualTo(Long.MAX_VALUE);
        }
    }
}
