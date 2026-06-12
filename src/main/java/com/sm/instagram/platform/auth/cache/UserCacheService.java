package com.sm.instagram.platform.auth.cache;

import com.sm.instagram.platform.user.User;

/**
 * Interface for user caching service with profile-based implementations.
 * Provides methods to cache user authentication data and check user status.
 */
public interface UserCacheService {
    
    /**
     * Cache user data for fast authentication lookup.
     * 
     * @param userId User ID as string
     * @param user User entity to cache
     */
    void cacheUser(String userId, User user);
    
    /**
     * Check if user is allowed to access the system (not inactive).
     * Users with IN_VALIDATION status can login but need to complete their profile.
     * Only INACTIVE users are prevented from accessing the system.
     * 
     * @param userId User ID to check
     * @return true if user is not inactive, false if inactive or not found
     */
    boolean isUserActive(String userId);
    
    /**
     * Evict a specific user from cache.
     * 
     * @param userId User ID to evict
     */
    void evict(String userId);
    
    /**
     * Clear entire cache.
     */
    void evictAll();

    /**
     * Get the current token version for a user.
     * Used to validate JWT tokens haven't been invalidated by admin status changes.
     *
     * @param userId Firebase UID of the user
     * @return Current token version, or null if user not cached/found
     */
    Long getTokenVersion(String userId);

    /**
     * Get the account status for a user.
     * Used for authorization checks (e.g., blocking BANNED users from actions).
     * Authentication (isUserActive) allows BANNED users to log in,
     * but authorization checks should block them from performing actions.
     *
     * @param userId Firebase UID of the user
     * @return Account status as string (e.g., "ACTIVE", "BANNED"), or null if not found
     */
    String getAccountStatus(String userId);

    /**
     * Get the email verification status for a user.
     * Used by EmailVerificationEnforcementFilter to block sensitive actions for unverified users.
     *
     * @param userId Firebase UID of the user
     * @return true if email is verified, false if not verified, null if not found
     */
    Boolean getEmailVerified(String userId);
}
