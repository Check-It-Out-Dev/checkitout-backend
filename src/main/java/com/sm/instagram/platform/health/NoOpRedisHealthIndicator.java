package com.sm.instagram.platform.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback health indicator when Redis is disabled.
 * Reports Redis as disabled/not configured.
 */
@Component("noOpRedisHealthIndicator")
@ConditionalOnProperty(name = "storage.mode", havingValue = "in-memory", matchIfMissing = true)
@Slf4j
public class NoOpRedisHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up()
                .withDetail("redis", "Disabled")
                .withDetail("mode", "no-redis")
                .withDetail("cache", "In-memory caching active")
                .build();
    }
}