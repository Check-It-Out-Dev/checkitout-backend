package com.sm.instagram.platform.auth.cache;

import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory user cache implementation for no-redis profile.
 * Used for local development and environments without Redis.
 */
@Component
@ConditionalOnProperty(name = "storage.mode", havingValue = "in-memory")
@Slf4j
@RequiredArgsConstructor
public class InMemoryUserCache implements UserCacheService {
    
    private final Map<String, CachedUser> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5 minutes
    
    private final UserRepository userRepository;
    
    @Data
    @AllArgsConstructor
    private static class CachedUser {
        private boolean active;
        private String role;
        private String accountStatus;  // For authorization checks (BANNED, etc.)
        private Long tokenVersion;
        private boolean emailVerified;  // For email verification enforcement filter
        private long timestamp;

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL;
        }
    }
    
    @Override
    public void cacheUser(String userId, User user) {
        // GDPR: Log cache operation
        log.info("GDPR: Operation=cacheUser_memory, FirebaseUID={}, DataCached=user.status,user.role, Purpose=performance_optimization", userId);
        // Authentication check: Can user log in?
        // INACTIVE, DELETED, TO_BE_DELETED = cannot authenticate at all
        // BANNED = CAN authenticate (to see why they're banned) but cannot perform actions
        boolean canAuthenticate = user.getAccountStatus() != AccountStatus.INACTIVE
            && user.getAccountStatus() != AccountStatus.DELETED
            && user.getAccountStatus() != AccountStatus.TO_BE_DELETED;
        cache.put(userId, new CachedUser(
            canAuthenticate,
            user.getUserType().name(),
            user.getAccountStatus().name(),  // Store for authorization checks
            user.getTokenVersion(),
            Boolean.TRUE.equals(user.getEmailVerified()),  // Store for email enforcement filter
            System.currentTimeMillis()
        ));
        log.debug("Cached user {} in memory with status {}", userId, user.getAccountStatus());
        // GDPR: Log successful cache
        log.info("GDPR: Operation=cacheUser_memory_success, FirebaseUID={}, CacheDuration=5minutes, Purpose=authentication_cache", userId);
    }
    
    @Override
    public boolean isUserActive(String userId) {
        // GDPR: Log user status check
        log.info("GDPR: Operation=isUserActive_check_memory, FirebaseUID={}, DataAccessed=user.status, Purpose=authorization", userId);
        CachedUser cached = cache.get(userId);
        
        if (cached != null && !cached.isExpired()) {
            log.debug("Cache hit for user {}", userId);
            // GDPR: Log cache hit
            log.info("GDPR: Operation=isUserActive_cacheHit_memory, FirebaseUID={}, DataSource=memory_cache, Purpose=authorization", userId);
            return cached.active;
        }
        
        // Cache miss - check DB
        log.debug("Cache miss for user {}, checking DB", userId);
        // GDPR: Log database query
        log.info("GDPR: DatabaseQuery=findByFirebaseUserId_memory, FirebaseUID={}, Table=users, DataAccessed=user.accountStatus, Purpose=authorization", userId);
        
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
        
        log.debug("User not found for Firebase UID: {}", userId);
        return false;
    }
    
    @Override
    public void evict(String userId) {
        // GDPR: Log cache eviction
        log.info("GDPR: Operation=evictUser_memory, FirebaseUID={}, DataDeleted=cached_user_data, Purpose=cache_management", userId);
        
        cache.remove(userId);
        log.debug("Evicted user {} from cache", userId);
    }
    
    @Override
    public void evictAll() {
        // GDPR: Log bulk cache eviction
        int size = cache.size();
        log.warn("GDPR: Operation=evictAll_memory, DataDeleted=all_cached_users, EntriesCount={}, Purpose=cache_reset", size);
        
        cache.clear();
        log.info("Cleared entire user cache");
    }
    
    @Scheduled(fixedDelay = 10 * 60 * 1000) // Every 10 minutes
    public void cleanExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
        int after = cache.size();
        if (before != after) {
            log.debug("Cleaned expired entries: {} -> {}", before, after);
            // GDPR: Log automatic cleanup
            log.info("GDPR: Operation=cleanExpired_memory, EntriesRemoved={}, Purpose=cache_maintenance", before - after);
        }
    }

    @Override
    public Long getTokenVersion(String userId) {
        CachedUser cached = cache.get(userId);

        if (cached != null && !cached.isExpired()) {
            return cached.getTokenVersion();
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
    public Boolean getEmailVerified(String userId) {
        CachedUser cached = cache.get(userId);

        if (cached != null && !cached.isExpired()) {
            return cached.isEmailVerified();
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
    public String getAccountStatus(String userId) {
        CachedUser cached = cache.get(userId);

        if (cached != null && !cached.isExpired()) {
            return cached.getAccountStatus();
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
