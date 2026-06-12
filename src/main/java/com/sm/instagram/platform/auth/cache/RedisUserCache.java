package com.sm.instagram.platform.auth.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.common.utils.HashingUtil;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based user cache implementation for production.
 * Provides distributed caching for authentication data.
 */
@Component
@ConditionalOnProperty(name = "storage.mode", havingValue = "redis", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class RedisUserCache implements UserCacheService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    
    // IMPORTANT: This prefix must match the key format used in HashingUtil.generateRedisKey()
    // HashingUtil generates: "user_cache:" + hash, so we use "user_cache:" for evictAll()
    private static final String USER_PREFIX = "user_cache:";
    private static final long CACHE_TTL = 5; // minutes
    
    @Override
    public void cacheUser(String userId, User user) {
        // GDPR: Log cache operation without PII
        log.info("GDPR: Operation=cacheUser, DataCached=user.status,user.role, Purpose=performance_optimization, ThirdParty=redis");
        
        // Hash Firebase UID for Redis key
        String key = HashingUtil.generateRedisKey("user_cache", userId);
        // Authentication check: Can user log in?
        // INACTIVE, DELETED, TO_BE_DELETED = cannot authenticate at all
        // BANNED = CAN authenticate (to see why they're banned) but cannot perform actions
        // IN_VALIDATION, ACTIVE = normal access
        boolean canAuthenticate = user.getAccountStatus() != AccountStatus.INACTIVE
            && user.getAccountStatus() != AccountStatus.DELETED
            && user.getAccountStatus() != AccountStatus.TO_BE_DELETED;
        Map<String, String> userData = Map.of(
            "active", String.valueOf(canAuthenticate),
            "role", user.getUserType().name(),
            "status", user.getAccountStatus().name(),  // Stored for authorization checks (BANNED, etc.)
            "tokenVersion", String.valueOf(user.getTokenVersion()),  // For session invalidation
            "emailVerified", String.valueOf(Boolean.TRUE.equals(user.getEmailVerified()))  // For email enforcement filter
        );
        
        try {
            String json = objectMapper.writeValueAsString(userData);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL, TimeUnit.MINUTES);
            log.debug("Cached user in Redis");  // No PII in logs
            // GDPR: Log successful cache without PII
            log.info("GDPR: Operation=cacheUser_success, CacheDuration={}minutes, Purpose=authentication_cache", CACHE_TTL);
        } catch (Exception e) {
            log.error("GDPR: Operation=cacheUser_failed, Error={}, Purpose=error_logging", e.getMessage());
            log.error("Failed to cache user: {}", e.getMessage());  // No PII in logs
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public boolean isUserActive(String userId) {
        // GDPR: Log user status check without PII
        log.info("GDPR: Operation=isUserActive_check, DataAccessed=user.status, Purpose=authorization");
        // Hash Firebase UID for Redis key
        String key = HashingUtil.generateRedisKey("user_cache", userId);
        String cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            try {
                Map<String, String> data = objectMapper.readValue(cached, Map.class);
                log.debug("Redis cache hit");  // No PII in logs
                // GDPR: Log cache hit without PII
                log.info("GDPR: Operation=isUserActive_cacheHit, DataSource=redis_cache, Purpose=authorization");
                return "true".equals(data.get("active"));
            } catch (Exception e) {
                log.error("Failed to parse cached data: {}", e.getMessage());
            }
        }
        
        // Cache miss - check DB
        log.debug("Redis cache miss, checking DB");  // No PII in logs
        // GDPR: Log database query without PII
        log.info("GDPR: DatabaseQuery=findByFirebaseUserId, Table=users, DataAccessed=user.accountStatus, Purpose=authorization");
        
        // The userId here is actually Firebase UID, not database ID
        // Need to use findByFirebaseUserId instead of findById
        User user = userRepository.findByFirebaseUserId(userId).orElse(null);
        
        if (user != null) {
            cacheUser(userId, user);
            // Authentication check: can user log in?
            // BANNED users CAN log in (to see why they're banned, contact support)
            // Only truly deactivated accounts are blocked from authentication
            return user.getAccountStatus() != AccountStatus.INACTIVE
                && user.getAccountStatus() != AccountStatus.DELETED
                && user.getAccountStatus() != AccountStatus.TO_BE_DELETED;
        }
        
        log.debug("User not found");  // No PII in logs
        return false;
    }
    
    @Override
    public void evict(String userId) {
        // GDPR: Log cache eviction without PII
        log.info("GDPR: Operation=evictUser, DataDeleted=cached_user_data, Purpose=cache_management");
        
        // Hash Firebase UID for Redis key
        String key = HashingUtil.generateRedisKey("user_cache", userId);
        redisTemplate.delete(key);
        log.debug("Evicted user from Redis");  // No PII in logs
    }
    
    @Override
    public void evictAll() {
        // GDPR: Log bulk cache eviction
        log.warn("GDPR: Operation=evictAll, DataDeleted=all_cached_users, Purpose=cache_reset");
        
        Set<String> keys = redisTemplate.keys(USER_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} entries from Redis cache", keys.size());
            // GDPR: Log successful eviction
            log.warn("GDPR: Operation=evictAll_success, EntriesDeleted={}, Purpose=cache_cleared", keys.size());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Long getTokenVersion(String userId) {
        // Hash Firebase UID for Redis key
        String key = HashingUtil.generateRedisKey("user_cache", userId);
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            try {
                Map<String, String> data = objectMapper.readValue(cached, Map.class);
                String versionStr = data.get("tokenVersion");
                if (versionStr != null) {
                    return Long.parseLong(versionStr);
                }
            } catch (Exception e) {
                log.error("Failed to parse tokenVersion from cache: {}", e.getMessage());
            }
        }

        // Cache miss - query DB directly for current token version
        User user = userRepository.findByFirebaseUserId(userId).orElse(null);
        if (user != null) {
            // Re-cache the user with current data
            cacheUser(userId, user);
            return user.getTokenVersion();
        }

        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Boolean getEmailVerified(String userId) {
        String key = HashingUtil.generateRedisKey("user_cache", userId);
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            try {
                Map<String, String> data = objectMapper.readValue(cached, Map.class);
                String value = data.get("emailVerified");
                if (value != null) {
                    return Boolean.parseBoolean(value);
                }
            } catch (Exception e) {
                log.error("Failed to parse emailVerified from cache: {}", e.getMessage());
            }
        }

        // Cache miss - query DB
        User user = userRepository.findByFirebaseUserId(userId).orElse(null);
        if (user != null) {
            cacheUser(userId, user);
            return Boolean.TRUE.equals(user.getEmailVerified());
        }

        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getAccountStatus(String userId) {
        // Hash Firebase UID for Redis key
        String key = HashingUtil.generateRedisKey("user_cache", userId);
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            try {
                Map<String, String> data = objectMapper.readValue(cached, Map.class);
                return data.get("status");  // "status" key stores AccountStatus.name()
            } catch (Exception e) {
                log.error("Failed to parse accountStatus from cache: {}", e.getMessage());
            }
        }

        // Cache miss - query DB
        User user = userRepository.findByFirebaseUserId(userId).orElse(null);
        if (user != null) {
            cacheUser(userId, user);
            return user.getAccountStatus().name();
        }

        return null;
    }
}
