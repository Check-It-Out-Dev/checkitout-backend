package com.sm.instagram.platform.storage.config;

import com.sm.instagram.platform.common.ratelimit.RateLimitProperties;
import com.sm.instagram.platform.storage.service.InMemoryStorageRateLimitService;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for Storage services based on profile.
 * Ensures proper instantiation of rate limiting services.
 */
@Configuration
@EnableScheduling  // Enable scheduled tasks for cleanup
@Slf4j
public class StorageConfiguration {

    /**
     * Fallback bean for when neither Redis nor no-redis profile is active.
     * This can happen in test scenarios or misconfigured environments.
     */
    @Bean
    @ConditionalOnMissingBean(StorageRateLimitService.class)
    public StorageRateLimitService fallbackStorageRateLimitService(RateLimitProperties rateLimitProperties) {
        log.warn("No StorageRateLimitService bean found - creating in-memory fallback");
        return new InMemoryStorageRateLimitService(rateLimitProperties);
    }
}
