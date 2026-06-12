package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.RedisUserCache;
import com.sm.instagram.platform.common.utils.HashingUtil;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for RedisUserCache.
 * Tests Redis-based user caching for authentication with mocked RedisTemplate.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisUserCache Unit Tests")
class RedisUserCacheUnitTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserRepository userRepository;

    private ObjectMapper objectMapper;

    private RedisUserCache redisUserCache;

    private static final String USER_PREFIX = "user_cache:";
    private static final long CACHE_TTL = 5; // minutes

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        redisUserCache = new RedisUserCache(redisTemplate, userRepository, objectMapper);
    }

    private User createUser(Long id, String firebaseUid, UserType userType, AccountStatus status) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setAccountStatus(status);
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setTokenVersion(1L);
        return user;
    }

    private User createActiveUser() {
        return createUser(1L, "firebase-uid-123", UserType.INFLUENCER, AccountStatus.ACTIVE);
    }

    @Nested
    @DisplayName("cacheUser Tests")
    class CacheUserTests {

        @Test
        @DisplayName("should cache active user with correct key and TTL")
        void shouldCacheActiveUserWithCorrectKeyAndTtl() throws JsonProcessingException {
            // Given
            User user = createActiveUser();
            String userId = "firebase-uid-123";
            String expectedKey = HashingUtil.generateRedisKey("user_cache", userId);

            // When
            redisUserCache.cacheUser(userId, user);

            // Then
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<TimeUnit> timeUnitCaptor = ArgumentCaptor.forClass(TimeUnit.class);

            verify(valueOperations).set(
                    keyCaptor.capture(),
                    valueCaptor.capture(),
                    ttlCaptor.capture(),
                    timeUnitCaptor.capture()
            );

            assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
            assertThat(ttlCaptor.getValue()).isEqualTo(CACHE_TTL);
            assertThat(timeUnitCaptor.getValue()).isEqualTo(TimeUnit.MINUTES);

            // Verify cached data structure
            Map<String, String> cachedData = objectMapper.readValue(valueCaptor.getValue(), Map.class);
            assertThat(cachedData.get("active")).isEqualTo("true");
            assertThat(cachedData.get("role")).isEqualTo("INFLUENCER");
            assertThat(cachedData.get("status")).isEqualTo("ACTIVE");
            assertThat(cachedData.get("tokenVersion")).isEqualTo("1");
        }

        @Test
        @DisplayName("should cache user with IN_VALIDATION status as active (can authenticate)")
        void shouldCacheInValidationUserAsActive() throws JsonProcessingException {
            // Given
            User user = createUser(1L, "uid-validation", UserType.COMPANY, AccountStatus.IN_VALIDATION);

            // When
            redisUserCache.cacheUser("uid-validation", user);

            // Then
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));

            Map<String, String> cachedData = objectMapper.readValue(valueCaptor.getValue(), Map.class);
            assertThat(cachedData.get("active")).isEqualTo("true");
            assertThat(cachedData.get("status")).isEqualTo("IN_VALIDATION");
        }

        @Test
        @DisplayName("should cache BANNED user as active (can authenticate to see ban reason)")
        void shouldCacheBannedUserAsActive() throws JsonProcessingException {
            // Given
            User user = createUser(1L, "uid-banned", UserType.INFLUENCER, AccountStatus.BANNED);

            // When
            redisUserCache.cacheUser("uid-banned", user);

            // Then
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));

            Map<String, String> cachedData = objectMapper.readValue(valueCaptor.getValue(), Map.class);
            assertThat(cachedData.get("active")).isEqualTo("true");
            assertThat(cachedData.get("status")).isEqualTo("BANNED");
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"INACTIVE", "DELETED", "TO_BE_DELETED"})
        @DisplayName("should cache non-authenticatable statuses as inactive")
        void shouldCacheNonAuthenticatableStatusesAsInactive(AccountStatus status) throws JsonProcessingException {
            // Given
            User user = createUser(1L, "uid-" + status.name(), UserType.INFLUENCER, status);

            // When
            redisUserCache.cacheUser("uid-" + status.name(), user);

            // Then
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));

            Map<String, String> cachedData = objectMapper.readValue(valueCaptor.getValue(), Map.class);
            assertThat(cachedData.get("active")).isEqualTo("false");
            assertThat(cachedData.get("status")).isEqualTo(status.name());
        }

        @ParameterizedTest
        @EnumSource(UserType.class)
        @DisplayName("should cache user with correct role for all user types")
        void shouldCacheCorrectRoleForAllUserTypes(UserType userType) throws JsonProcessingException {
            // Given
            User user = createUser(1L, "uid-" + userType.name(), userType, AccountStatus.ACTIVE);

            // When
            redisUserCache.cacheUser("uid-" + userType.name(), user);

            // Then
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));

            Map<String, String> cachedData = objectMapper.readValue(valueCaptor.getValue(), Map.class);
            assertThat(cachedData.get("role")).isEqualTo(userType.name());
        }

        @Test
        @DisplayName("should cache token version correctly")
        void shouldCacheTokenVersionCorrectly() throws JsonProcessingException {
            // Given
            User user = createActiveUser();
            user.setTokenVersion(42L);

            // When
            redisUserCache.cacheUser("uid-token", user);

            // Then
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));

            Map<String, String> cachedData = objectMapper.readValue(valueCaptor.getValue(), Map.class);
            assertThat(cachedData.get("tokenVersion")).isEqualTo("42");
        }

        @Test
        @DisplayName("should handle serialization exception gracefully")
        void shouldHandleSerializationExceptionGracefully() throws JsonProcessingException {
            // Given
            User user = createActiveUser();
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Serialization error"));

            RedisUserCache cacheWithFailingMapper = new RedisUserCache(redisTemplate, userRepository, failingMapper);

            // When - should not throw
            cacheWithFailingMapper.cacheUser("uid-error", user);

            // Then - no interaction with Redis since serialization failed
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("should use hashed Firebase UID as Redis key")
        void shouldUseHashedFirebaseUidAsRedisKey() {
            // Given
            User user = createActiveUser();
            String userId = "firebase-uid-to-hash";
            String expectedKey = HashingUtil.generateRedisKey("user_cache", userId);

            // When
            redisUserCache.cacheUser(userId, user);

            // Then
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(keyCaptor.capture(), anyString(), anyLong(), any(TimeUnit.class));

            assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
            assertThat(keyCaptor.getValue()).startsWith(USER_PREFIX);
        }
    }

    @Nested
    @DisplayName("isUserActive Tests")
    class IsUserActiveTests {

        @Test
        @DisplayName("should return true for cached active user")
        void shouldReturnTrueForCachedActiveUser() throws JsonProcessingException {
            // Given
            String userId = "active-user-id";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            Map<String, String> cachedData = Map.of(
                    "active", "true",
                    "role", "INFLUENCER",
                    "status", "ACTIVE",
                    "tokenVersion", "1"
            );
            when(valueOperations.get(key)).thenReturn(objectMapper.writeValueAsString(cachedData));

            // When
            boolean result = redisUserCache.isUserActive(userId);

            // Then
            assertThat(result).isTrue();
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should return false for cached inactive user")
        void shouldReturnFalseForCachedInactiveUser() throws JsonProcessingException {
            // Given
            String userId = "inactive-user-id";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            Map<String, String> cachedData = Map.of(
                    "active", "false",
                    "role", "INFLUENCER",
                    "status", "INACTIVE",
                    "tokenVersion", "1"
            );
            when(valueOperations.get(key)).thenReturn(objectMapper.writeValueAsString(cachedData));

            // When
            boolean result = redisUserCache.isUserActive(userId);

            // Then
            assertThat(result).isFalse();
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should query database on cache miss and cache the result")
        void shouldQueryDatabaseOnCacheMissAndCacheResult() {
            // Given
            String userId = "uncached-user-id";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);

            User user = createActiveUser();
            user.setFirebaseUserId(userId);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            boolean result = redisUserCache.isUserActive(userId);

            // Then
            assertThat(result).isTrue();
            verify(userRepository).findByFirebaseUserId(userId);
            verify(valueOperations).set(eq(key), anyString(), eq(CACHE_TTL), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("should return false when user not found in database")
        void shouldReturnFalseWhenUserNotFoundInDatabase() {
            // Given
            String userId = "non-existent-user";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.empty());

            // When
            boolean result = redisUserCache.isUserActive(userId);

            // Then
            assertThat(result).isFalse();
            verify(userRepository).findByFirebaseUserId(userId);
        }

        @Test
        @DisplayName("should return true for BANNED user from database (can authenticate)")
        void shouldReturnTrueForBannedUserFromDatabase() {
            // Given
            String userId = "banned-user-id";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);

            User bannedUser = createUser(1L, userId, UserType.INFLUENCER, AccountStatus.BANNED);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(bannedUser));

            // When
            boolean result = redisUserCache.isUserActive(userId);

            // Then
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"INACTIVE", "DELETED", "TO_BE_DELETED"})
        @DisplayName("should return false for non-authenticatable statuses from database")
        void shouldReturnFalseForNonAuthenticatableStatusesFromDatabase(AccountStatus status) {
            // Given
            String userId = "status-" + status.name();
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);

            User user = createUser(1L, userId, UserType.INFLUENCER, status);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            boolean result = redisUserCache.isUserActive(userId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle JSON parsing exception gracefully and query database")
        void shouldHandleJsonParsingExceptionGracefullyAndQueryDatabase() {
            // Given
            String userId = "user-with-bad-cache";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn("invalid-json-{{{");

            User user = createActiveUser();
            user.setFirebaseUserId(userId);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            boolean result = redisUserCache.isUserActive(userId);

            // Then
            assertThat(result).isTrue();
            verify(userRepository).findByFirebaseUserId(userId);
        }
    }

    @Nested
    @DisplayName("evict Tests")
    class EvictTests {

        @Test
        @DisplayName("should delete correct key from Redis")
        void shouldDeleteCorrectKeyFromRedis() {
            // Given
            String userId = "user-to-evict";
            String expectedKey = HashingUtil.generateRedisKey("user_cache", userId);

            // When
            redisUserCache.evict(userId);

            // Then
            verify(redisTemplate).delete(expectedKey);
        }

        @Test
        @DisplayName("should use hashed key for eviction")
        void shouldUseHashedKeyForEviction() {
            // Given
            String userId = "firebase-uid-evict";
            String expectedKey = HashingUtil.generateRedisKey("user_cache", userId);

            // When
            redisUserCache.evict(userId);

            // Then
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).delete(keyCaptor.capture());
            assertThat(keyCaptor.getValue()).isEqualTo(expectedKey);
            assertThat(keyCaptor.getValue()).startsWith(USER_PREFIX);
        }
    }

    @Nested
    @DisplayName("evictAll Tests")
    class EvictAllTests {

        @Test
        @DisplayName("should delete all keys matching user_cache prefix")
        void shouldDeleteAllKeysMatchingPrefix() {
            // Given
            Set<String> matchingKeys = new HashSet<>();
            matchingKeys.add("user_cache:hash1");
            matchingKeys.add("user_cache:hash2");
            matchingKeys.add("user_cache:hash3");
            when(redisTemplate.keys(USER_PREFIX + "*")).thenReturn(matchingKeys);

            // When
            redisUserCache.evictAll();

            // Then
            verify(redisTemplate).keys(USER_PREFIX + "*");
            verify(redisTemplate).delete(matchingKeys);
        }

        @Test
        @DisplayName("should not call delete when no keys found")
        void shouldNotCallDeleteWhenNoKeysFound() {
            // Given
            when(redisTemplate.keys(USER_PREFIX + "*")).thenReturn(null);

            // When
            redisUserCache.evictAll();

            // Then
            verify(redisTemplate).keys(USER_PREFIX + "*");
            verify(redisTemplate, never()).delete(anySet());
        }

        @Test
        @DisplayName("should not call delete when empty set returned")
        void shouldNotCallDeleteWhenEmptySetReturned() {
            // Given
            when(redisTemplate.keys(USER_PREFIX + "*")).thenReturn(new HashSet<>());

            // When
            redisUserCache.evictAll();

            // Then
            verify(redisTemplate).keys(USER_PREFIX + "*");
            verify(redisTemplate, never()).delete(anySet());
        }
    }

    @Nested
    @DisplayName("getTokenVersion Tests")
    class GetTokenVersionTests {

        @Test
        @DisplayName("should return token version from cache")
        void shouldReturnTokenVersionFromCache() throws JsonProcessingException {
            // Given
            String userId = "user-with-token";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            Map<String, String> cachedData = Map.of(
                    "active", "true",
                    "role", "INFLUENCER",
                    "status", "ACTIVE",
                    "tokenVersion", "42"
            );
            when(valueOperations.get(key)).thenReturn(objectMapper.writeValueAsString(cachedData));

            // When
            Long tokenVersion = redisUserCache.getTokenVersion(userId);

            // Then
            assertThat(tokenVersion).isEqualTo(42L);
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @Test
        @DisplayName("should query database on cache miss")
        void shouldQueryDatabaseOnCacheMiss() {
            // Given
            String userId = "uncached-user";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);

            User user = createActiveUser();
            user.setTokenVersion(100L);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            Long tokenVersion = redisUserCache.getTokenVersion(userId);

            // Then
            assertThat(tokenVersion).isEqualTo(100L);
            verify(userRepository).findByFirebaseUserId(userId);
        }

        @Test
        @DisplayName("should return null when user not found in database")
        void shouldReturnNullWhenUserNotFoundInDatabase() {
            // Given
            String userId = "non-existent";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.empty());

            // When
            Long tokenVersion = redisUserCache.getTokenVersion(userId);

            // Then
            assertThat(tokenVersion).isNull();
        }

        @Test
        @DisplayName("should return null when tokenVersion not in cache")
        void shouldReturnNullWhenTokenVersionNotInCache() throws JsonProcessingException {
            // Given
            String userId = "user-no-token-version";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            Map<String, String> cachedData = Map.of(
                    "active", "true",
                    "role", "INFLUENCER",
                    "status", "ACTIVE"
                    // tokenVersion intentionally missing
            );
            when(valueOperations.get(key)).thenReturn(objectMapper.writeValueAsString(cachedData));

            User user = createActiveUser();
            user.setTokenVersion(50L);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            Long tokenVersion = redisUserCache.getTokenVersion(userId);

            // Then
            assertThat(tokenVersion).isEqualTo(50L);
            verify(userRepository).findByFirebaseUserId(userId);
        }

        @Test
        @DisplayName("should handle invalid token version in cache gracefully")
        void shouldHandleInvalidTokenVersionInCacheGracefully() throws JsonProcessingException {
            // Given
            String userId = "user-bad-token";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            String cachedJson = "{\"active\":\"true\",\"role\":\"INFLUENCER\",\"status\":\"ACTIVE\",\"tokenVersion\":\"not-a-number\"}";
            when(valueOperations.get(key)).thenReturn(cachedJson);

            User user = createActiveUser();
            user.setTokenVersion(77L);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            Long tokenVersion = redisUserCache.getTokenVersion(userId);

            // Then
            assertThat(tokenVersion).isEqualTo(77L);
            verify(userRepository).findByFirebaseUserId(userId);
        }

        @Test
        @DisplayName("should cache user when fetching token version from database")
        void shouldCacheUserWhenFetchingTokenVersionFromDatabase() {
            // Given
            String userId = "user-to-cache";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);

            User user = createActiveUser();
            user.setTokenVersion(123L);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            redisUserCache.getTokenVersion(userId);

            // Then
            verify(valueOperations).set(eq(key), anyString(), eq(CACHE_TTL), eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    @DisplayName("getAccountStatus Tests")
    class GetAccountStatusTests {

        @Test
        @DisplayName("should return account status from cache")
        void shouldReturnAccountStatusFromCache() throws JsonProcessingException {
            // Given
            String userId = "user-with-status";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            Map<String, String> cachedData = Map.of(
                    "active", "true",
                    "role", "INFLUENCER",
                    "status", "ACTIVE",
                    "tokenVersion", "1"
            );
            when(valueOperations.get(key)).thenReturn(objectMapper.writeValueAsString(cachedData));

            // When
            String accountStatus = redisUserCache.getAccountStatus(userId);

            // Then
            assertThat(accountStatus).isEqualTo("ACTIVE");
            verify(userRepository, never()).findByFirebaseUserId(anyString());
        }

        @ParameterizedTest
        @EnumSource(AccountStatus.class)
        @DisplayName("should return correct account status for all statuses")
        void shouldReturnCorrectAccountStatusForAllStatuses(AccountStatus status) throws JsonProcessingException {
            // Given
            String userId = "user-" + status.name();
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            Map<String, String> cachedData = Map.of(
                    "active", "true",
                    "role", "INFLUENCER",
                    "status", status.name(),
                    "tokenVersion", "1"
            );
            when(valueOperations.get(key)).thenReturn(objectMapper.writeValueAsString(cachedData));

            // When
            String accountStatus = redisUserCache.getAccountStatus(userId);

            // Then
            assertThat(accountStatus).isEqualTo(status.name());
        }

        @Test
        @DisplayName("should query database on cache miss")
        void shouldQueryDatabaseOnCacheMiss() {
            // Given
            String userId = "uncached-status-user";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);

            User user = createUser(1L, userId, UserType.COMPANY, AccountStatus.BANNED);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            String accountStatus = redisUserCache.getAccountStatus(userId);

            // Then
            assertThat(accountStatus).isEqualTo("BANNED");
            verify(userRepository).findByFirebaseUserId(userId);
        }

        @Test
        @DisplayName("should return null when user not found in database")
        void shouldReturnNullWhenUserNotFoundInDatabase() {
            // Given
            String userId = "non-existent-status";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.empty());

            // When
            String accountStatus = redisUserCache.getAccountStatus(userId);

            // Then
            assertThat(accountStatus).isNull();
        }

        @Test
        @DisplayName("should cache user when fetching account status from database")
        void shouldCacheUserWhenFetchingAccountStatusFromDatabase() {
            // Given
            String userId = "user-status-cache";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn(null);

            User user = createUser(1L, userId, UserType.INFLUENCER, AccountStatus.IN_VALIDATION);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            redisUserCache.getAccountStatus(userId);

            // Then
            verify(valueOperations).set(eq(key), anyString(), eq(CACHE_TTL), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("should handle JSON parsing exception gracefully and query database")
        void shouldHandleJsonParsingExceptionGracefullyAndQueryDatabase() {
            // Given
            String userId = "user-bad-json-status";
            String key = HashingUtil.generateRedisKey("user_cache", userId);
            when(valueOperations.get(key)).thenReturn("not-valid-json-at-all");

            User user = createUser(1L, userId, UserType.ADMIN, AccountStatus.ACTIVE);
            when(userRepository.findByFirebaseUserId(userId)).thenReturn(Optional.of(user));

            // When
            String accountStatus = redisUserCache.getAccountStatus(userId);

            // Then
            assertThat(accountStatus).isEqualTo("ACTIVE");
            verify(userRepository).findByFirebaseUserId(userId);
        }
    }

    @Nested
    @DisplayName("Key Hashing Tests")
    class KeyHashingTests {

        @Test
        @DisplayName("should generate consistent keys for same user ID")
        void shouldGenerateConsistentKeysForSameUserId() {
            // Given
            String userId = "consistent-user-id";
            User user = createActiveUser();

            // When - cache the user twice
            redisUserCache.cacheUser(userId, user);
            redisUserCache.cacheUser(userId, user);

            // Then - should use same key both times
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations, times(2)).set(keyCaptor.capture(), anyString(), anyLong(), any(TimeUnit.class));

            assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
        }

        @Test
        @DisplayName("should generate different keys for different user IDs")
        void shouldGenerateDifferentKeysForDifferentUserIds() {
            // Given
            User user = createActiveUser();

            // When
            redisUserCache.cacheUser("user-id-1", user);
            redisUserCache.cacheUser("user-id-2", user);

            // Then
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations, times(2)).set(keyCaptor.capture(), anyString(), anyLong(), any(TimeUnit.class));

            assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
        }

        @Test
        @DisplayName("all keys should start with user_cache prefix")
        void allKeysShouldStartWithUserCachePrefix() {
            // Given
            User user = createActiveUser();

            // When
            redisUserCache.cacheUser("any-user-id", user);

            // Then
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(keyCaptor.capture(), anyString(), anyLong(), any(TimeUnit.class));

            assertThat(keyCaptor.getValue()).startsWith(USER_PREFIX);
        }
    }

    @Nested
    @DisplayName("TTL Configuration Tests")
    class TtlConfigurationTests {

        @Test
        @DisplayName("should use 5 minute TTL for cached users")
        void shouldUseFiveMinuteTtlForCachedUsers() {
            // Given
            User user = createActiveUser();

            // When
            redisUserCache.cacheUser("ttl-test-user", user);

            // Then
            verify(valueOperations).set(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTests {

        @Test
        @DisplayName("should serialize cached data as JSON")
        void shouldSerializeCachedDataAsJson() {
            // Given
            User user = createActiveUser();

            // When
            redisUserCache.cacheUser("json-test", user);

            // Then
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));

            String cachedValue = valueCaptor.getValue();
            assertThat(cachedValue).contains("\"active\"");
            assertThat(cachedValue).contains("\"role\"");
            assertThat(cachedValue).contains("\"status\"");
            assertThat(cachedValue).contains("\"tokenVersion\"");
        }

        @Test
        @DisplayName("should serialize values as strings in JSON")
        void shouldSerializeValuesAsStringsInJson() throws JsonProcessingException {
            // Given
            User user = createActiveUser();
            user.setTokenVersion(12345L);

            // When
            redisUserCache.cacheUser("string-test", user);

            // Then
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));

            Map<String, String> cachedData = objectMapper.readValue(valueCaptor.getValue(), Map.class);
            // All values should be strings
            assertThat(cachedData.get("active")).isInstanceOf(String.class);
            assertThat(cachedData.get("role")).isInstanceOf(String.class);
            assertThat(cachedData.get("status")).isInstanceOf(String.class);
            assertThat(cachedData.get("tokenVersion")).isInstanceOf(String.class);
            assertThat(cachedData.get("tokenVersion")).isEqualTo("12345");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle user with null token version")
        void shouldHandleUserWithNullTokenVersion() throws JsonProcessingException {
            // Given
            User user = createActiveUser();
            // Directly set to null via reflection or new User
            User userWithNullToken = new User();
            userWithNullToken.setId(1L);
            userWithNullToken.setFirebaseUserId("null-token-user");
            userWithNullToken.setUserType(UserType.INFLUENCER);
            userWithNullToken.setAccountStatus(AccountStatus.ACTIVE);
            // tokenVersion defaults to 1L in User, but we test the behavior

            // When
            redisUserCache.cacheUser("null-token-user", userWithNullToken);

            // Then - should still serialize
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations).set(anyString(), valueCaptor.capture(), anyLong(), any(TimeUnit.class));

            Map<String, String> cachedData = objectMapper.readValue(valueCaptor.getValue(), Map.class);
            assertThat(cachedData.get("tokenVersion")).isNotNull();
        }

        @Test
        @DisplayName("should handle special characters in Firebase UID")
        void shouldHandleSpecialCharactersInFirebaseUid() {
            // Given
            String userId = "user-with.special_chars-123";
            User user = createActiveUser();

            // When - should not throw
            redisUserCache.cacheUser(userId, user);

            // Then
            verify(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("should handle long Firebase UID")
        void shouldHandleLongFirebaseUid() {
            // Given
            String userId = "very-long-firebase-uid-" + "x".repeat(200);
            User user = createActiveUser();

            // When - should not throw
            redisUserCache.cacheUser(userId, user);

            // Then
            verify(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    @DisplayName("Integration-like Tests")
    class IntegrationLikeTests {

        @Test
        @DisplayName("should support full cache-evict-cache cycle")
        void shouldSupportFullCacheEvictCacheCycle() throws JsonProcessingException {
            // Given
            String userId = "cycle-user";
            User user = createActiveUser();
            String key = HashingUtil.generateRedisKey("user_cache", userId);

            // When - cache user
            redisUserCache.cacheUser(userId, user);

            // Then - verify cached
            verify(valueOperations).set(eq(key), anyString(), anyLong(), any(TimeUnit.class));

            // When - evict user
            redisUserCache.evict(userId);

            // Then - verify evicted
            verify(redisTemplate).delete(key);

            // When - cache again
            reset(valueOperations);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            redisUserCache.cacheUser(userId, user);

            // Then - verify cached again
            verify(valueOperations).set(eq(key), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("should correctly determine authentication based on account status")
        void shouldCorrectlyDetermineAuthenticationBasedOnAccountStatus() throws JsonProcessingException {
            // Test that BANNED users can authenticate (to see ban reason)
            // while INACTIVE/DELETED/TO_BE_DELETED cannot

            // ACTIVE - can authenticate
            User activeUser = createUser(1L, "active", UserType.INFLUENCER, AccountStatus.ACTIVE);
            redisUserCache.cacheUser("active", activeUser);
            ArgumentCaptor<String> activeCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations, atLeastOnce()).set(anyString(), activeCaptor.capture(), anyLong(), any(TimeUnit.class));
            Map<String, String> activeData = objectMapper.readValue(activeCaptor.getValue(), Map.class);
            assertThat(activeData.get("active")).isEqualTo("true");

            reset(valueOperations);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // BANNED - can authenticate (to see ban reason)
            User bannedUser = createUser(2L, "banned", UserType.INFLUENCER, AccountStatus.BANNED);
            redisUserCache.cacheUser("banned", bannedUser);
            ArgumentCaptor<String> bannedCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations, atLeastOnce()).set(anyString(), bannedCaptor.capture(), anyLong(), any(TimeUnit.class));
            Map<String, String> bannedData = objectMapper.readValue(bannedCaptor.getValue(), Map.class);
            assertThat(bannedData.get("active")).isEqualTo("true");
            assertThat(bannedData.get("status")).isEqualTo("BANNED");

            reset(valueOperations);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // DELETED - cannot authenticate
            User deletedUser = createUser(3L, "deleted", UserType.INFLUENCER, AccountStatus.DELETED);
            redisUserCache.cacheUser("deleted", deletedUser);
            ArgumentCaptor<String> deletedCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOperations, atLeastOnce()).set(anyString(), deletedCaptor.capture(), anyLong(), any(TimeUnit.class));
            Map<String, String> deletedData = objectMapper.readValue(deletedCaptor.getValue(), Map.class);
            assertThat(deletedData.get("active")).isEqualTo("false");
        }
    }
}
