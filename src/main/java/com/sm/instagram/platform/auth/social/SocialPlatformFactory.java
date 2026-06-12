package com.sm.instagram.platform.auth.social;

import com.sm.instagram.platform.auth.exceptions.SocialConnectionException;
import com.sm.instagram.platform.auth.social.instagram.InstagramService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Factory for obtaining social platform service implementations.
 * Provides a central point for managing all social integrations.
 */
@Component
@RequiredArgsConstructor
public class SocialPlatformFactory {

    private final InstagramService instagramService;
    // Add other social service implementations as they become available
    // private final TikTokService tikTokService;
    // private final SnapchatService snapchatService;
    // private final YouTubeService youTubeService;

    private final Map<String, SocialPlatformService> serviceCache = new HashMap<>();

    /**
     * Get a social platform service implementation by name.
     *
     * @param platformName The name of the platform (case insensitive)
     * @return The appropriate service implementation
     * @throws SocialConnectionException if an unsupported platform is requested
     */
    public SocialPlatformService getSocialService(String platformName) {
        String normalizedName = platformName.toLowerCase();

        // Check if we already have an instance in the cache
        if (serviceCache.containsKey(normalizedName)) {
            return serviceCache.get(normalizedName);
        }

        // Create and cache a new instance
        SocialPlatformService service;

        switch (normalizedName) {
            case "instagram":
                service = instagramService;
                break;
            // Add cases for other platforms as they are implemented
            // case "tiktok":
            //     service = tikTokService;
            //     break;
            // case "snapchat":
            //     service = snapchatService;
            //     break;
            // case "youtube":
            //     service = youTubeService;
            //     break;
            default:
                throw new SocialConnectionException("Unsupported social platform: " + platformName, platformName);
        }

        // Cache the service instance for future use
        serviceCache.put(normalizedName, service);
        return service;
    }

    /**
     * Get a list of all supported social platforms.
     *
     * @return Set of supported platform names
     */
    public Set<String> getSupportedPlatforms() {
        // Currently only Instagram is supported
        // Add other platforms as they are implemented
        return Set.of("instagram");
    }
}