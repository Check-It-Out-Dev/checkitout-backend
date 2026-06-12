package com.sm.instagram.platform.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Test environment health configuration
 * Provides simplified health checks for containerized test environment
 */
@Configuration
@Profile("test")
public class TestHealthConfiguration {

    /**
     * Simplified ping health indicator for test environment
     * Always returns UP if the application is running
     */
    @Bean
    @ConditionalOnProperty(
        value = "management.health.custom-ping.enabled",
        havingValue = "true",
        matchIfMissing = false
    )
    public HealthIndicator customPingHealthIndicator() {
        return () -> Health.up()
            .withDetail("description", "Test environment ping check")
            .build();
    }

    /**
     * Lenient disk space health indicator for test
     * Test environment has limited resources in containers
     */
    @Bean
    @ConditionalOnProperty(
        value = "management.health.custom-disk.enabled",
        havingValue = "true",
        matchIfMissing = false
    )
    public HealthIndicator customDiskHealthIndicator() {
        return () -> {
            // In test environment, disk space is managed by container orchestration
            // We don't want health checks to fail due to container disk limits
            return Health.up()
                .withDetail("description", "Disk space check simplified for test environment")
                .withDetail("note", "Container disk space managed by orchestration")
                .build();
        };
    }
}
