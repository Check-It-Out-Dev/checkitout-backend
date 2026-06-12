package com.sm.instagram.platform.auth.social;

import com.sm.instagram.platform.platform.Platform;
import java.util.Map;

/**
 * Interface for social platform integrations.
 * All social platform services (Instagram, TikTok, etc.) must implement this interface.
 */
public interface SocialPlatformService {

    /**
     * Exchange an authorization code for user profile information.
     *
     * @param authCode The authorization code from OAuth flow
     * @return Map of user profile data
     */
    Map<String, Object> exchangeAuthCodeForProfile(String authCode);

    /**
     * Refresh social data for a user.
     *
     * @param socialUserId The user's ID on the social platform
     * @return Updated social data
     */
    Map<String, Object> refreshSocialData(String socialUserId);

    /**
     * Get the Platform entity for this social platform.
     *
     * @return Platform entity
     */
    Platform getPlatformEntity();

    /**
     * Get the name of this social platform.
     *
     * @return Platform name (e.g., "Instagram", "TikTok")
     */
    String getPlatformName();
}