package com.sm.instagram.platform.usersocialconnection;

import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSocialConnectionRepository extends BaseRepository<UserSocialConnection, Long> {
    /**
     * Find all social connections for a specific user
     */
    List<UserSocialConnection> findByUserId(Long userId);

    /**
     * Find all active connections for a specific user
     */
    List<UserSocialConnection> findByUserIdAndConnectionStatus(Long userId, ConnectionStatus status);

    /**
     * Find a user's connection to a specific platform
     */
    Optional<UserSocialConnection> findByUserIdAndPlatformId(Long userId, Long platformId);

    /**
     * Find a connection by user ID, platform name, and social user ID
     */
    Optional<UserSocialConnection> findByUserIdAndPlatformNameAndSocialUserId(
            Long userId, String platformName, String socialUserId);

    /**
     * Find connections by platform name and social user ID
     */
    Optional<UserSocialConnection> findByPlatform_NameAndSocialUserId(
            String platformName, String socialUserId);

    /**
     * Find all connections with a specific social user ID
     */
    List<UserSocialConnection> findBySocialUserId(String socialUserId);

    /**
     * Find a user's primary connection
     */
    Optional<UserSocialConnection> findByUserIdAndIsPrimaryTrue(Long userId);

    /**
     * Find a connection by platform name and social user ID that belongs to a different user
     * This helps detect if a social account is already connected to another user
     */
    Optional<UserSocialConnection> findByPlatformNameAndSocialUserIdAndUserIdNot(
            String platformName, String socialUserId, Long userId);
}