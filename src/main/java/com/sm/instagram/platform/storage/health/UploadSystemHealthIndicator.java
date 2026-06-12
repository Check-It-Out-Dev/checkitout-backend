package com.sm.instagram.platform.storage.health;

import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
// Remove the conditional property - let it work with all profiles
public class UploadSystemHealthIndicator implements HealthIndicator {

    private final Storage storage;
    private final RedisTemplate<String, String> redisTemplate;
    private final StorageRateLimitService rateLimiterService;

    @Value("${gcp.bucket-name:}")
    private String bucketName;

    @Autowired
    public UploadSystemHealthIndicator(@Autowired(required = false) @Qualifier("fileUploadStorage") Storage storage,
                                       @Autowired(required = false) RedisTemplate<String, String> redisTemplate,
                                       @Autowired(required = false) StorageRateLimitService rateLimiterService) {
        this.storage = storage;
        this.redisTemplate = redisTemplate;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        // Check Firebase Storage connectivity
        boolean storageHealthy = checkStorageHealth();
        String storageStatus = storage != null ? (storageHealthy ? "UP" : "DOWN") : "NOT_CONFIGURED";
        details.put("firebase_storage", storageStatus);

        // Check Redis connectivity
        boolean redisHealthy = checkRedisHealth();
        String redisStatus = redisTemplate != null ? (redisHealthy ? "UP" : "DOWN") : "NOT_CONFIGURED";
        details.put("redis", redisStatus);

        // Check rate limiter
        boolean rateLimiterHealthy = checkRateLimiterHealth();
        String rateLimiterStatus = rateLimiterService != null ? (rateLimiterHealthy ? "UP" : "DOWN") : "NOT_CONFIGURED";
        details.put("rate_limiter", rateLimiterStatus);

        // Get current system load
        Map<String, Object> systemLoad = getSystemLoad();
        details.put("system_load", systemLoad);

        // Check if any critical component that IS configured is failing
        boolean hasFailure = false;
        StringBuilder statusNote = new StringBuilder();
        
        if (storage != null && !storageHealthy) {
            hasFailure = true;
            statusNote.append("Storage is configured but not accessible. ");
        }
        
        if (redisTemplate != null && !redisHealthy) {
            hasFailure = true;
            statusNote.append("Redis is configured but not accessible. ");
        }
        
        if (rateLimiterService != null && !rateLimiterHealthy) {
            hasFailure = true;
            statusNote.append("Rate limiter is configured but not working. ");
        }
        
        // Determine overall health
        if (!hasFailure) {
            // Nothing is failing
            if (storage == null && redisTemplate == null && rateLimiterService == null) {
                details.put("status_note", "Upload system not configured - basic mode without rate limiting or cloud storage");
            } else {
                details.put("status_note", "All configured upload system components are healthy");
            }
            return Health.up().withDetails(details).build();
        } else {
            // Something that was configured is failing
            details.put("status_note", statusNote.toString().trim());
            return Health.down().withDetails(details).build();
        }
    }

    private boolean checkStorageHealth() {
        try {
            // If storage is not configured, return false but don't fail the app
            if (storage == null || bucketName == null || bucketName.isEmpty()) {
                return false;
            }
            return storage.get(bucketName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRedisHealth() {
        try {
            // If Redis is not configured, return false but don't fail the app
            if (redisTemplate == null) {
                return false;
            }

            // Try a simple Redis operation
            String key = "health:check:" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(key, "OK", 1, TimeUnit.SECONDS);
            String value = redisTemplate.opsForValue().get(key);
            return "OK".equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRateLimiterHealth() {
        try {
            // If rate limiter service is not configured, return false but don't fail the app
            if (rateLimiterService == null) {
                return false;
            }

            // Try to get status for a test user
            rateLimiterService.getUserStatus("health-check-user");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> getSystemLoad() {
        Map<String, Object> load = new HashMap<>();

        try {
            // Get JVM metrics
            Runtime runtime = Runtime.getRuntime();
            load.put("memory_used_mb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
            load.put("memory_total_mb", runtime.totalMemory() / 1024 / 1024);
            load.put("memory_free_mb", runtime.freeMemory() / 1024 / 1024);
            load.put("memory_max_mb", runtime.maxMemory() / 1024 / 1024);

            // Get thread count
            load.put("active_threads", Thread.activeCount());

            // Get system properties
            load.put("available_processors", Runtime.getRuntime().availableProcessors());

        } catch (Exception e) {
            load.put("error", "Failed to collect system metrics: " + e.getMessage());
        }

        return load;
    }
}
