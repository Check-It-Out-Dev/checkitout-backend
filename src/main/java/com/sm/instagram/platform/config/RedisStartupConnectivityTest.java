package com.sm.instagram.platform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

/**
 * Redis connectivity test that runs at application startup.
 * Verifies Redis connection and logs detailed status.
 * Fails fast if Redis is required but not available.
 */
@Slf4j
@Component
@Order(1) // Run early in startup sequence
@RequiredArgsConstructor
@ConditionalOnProperty(name = "redis.startup.test.enabled", havingValue = "true", matchIfMissing = false)
public class RedisStartupConnectivityTest implements ApplicationRunner {

    private final RedisConnectionFactory redisConnectionFactory;
    private final RedisTemplate<String, String> redisTemplate;
    private final StorageModeConfiguration storageModeConfiguration;
    
    @Override
    public void run(ApplicationArguments args) {
        log.info("========================================");
        log.info("🔍 REDIS CONNECTIVITY TEST STARTING");
        log.info("========================================");
        
        Instant startTime = Instant.now();
        boolean isConnected = false;
        String redisVersion = "Unknown";
        String redisMode = "Unknown";
        Long dbSize = 0L;
        
        try {
            // Test 1: Basic connection
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                
                // Test 2: PING command
                String pingResponse = connection.ping();
                if (!"PONG".equals(pingResponse)) {
                    throw new IllegalStateException("Redis PING failed, expected PONG but got: " + pingResponse);
                }
                
                // Test 3: Get Redis server info
                Properties serverInfo = connection.serverCommands().info();
                if (serverInfo != null) {
                    redisVersion = serverInfo.getProperty("redis_version", "Unknown");
                    redisMode = serverInfo.getProperty("redis_mode", "Unknown");
                }
                
                // Test 4: Get database size
                dbSize = connection.serverCommands().dbSize();
                
                // Test 5: Try a simple SET/GET operation
                String testKey = "startup:test:" + System.currentTimeMillis();
                String testValue = "connectivity-check";
                
                redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(10));
                String retrievedValue = redisTemplate.opsForValue().get(testKey);
                
                if (!testValue.equals(retrievedValue)) {
                    throw new IllegalStateException("Redis SET/GET test failed");
                }
                
                // Clean up test key
                redisTemplate.delete(testKey);
                
                isConnected = true;
            }
            
            Duration connectionTime = Duration.between(startTime, Instant.now());
            
            // Success - log details
            log.info("✅ REDIS CONNECTION SUCCESSFUL");
            log.info("  • Version: {}", redisVersion);
            log.info("  • Mode: {}", redisMode);
            log.info("  • Database size: {} keys", dbSize);
            log.info("  • Connection time: {}ms", connectionTime.toMillis());
            log.info("  • Storage mode: {}", storageModeConfiguration.getCurrentStorageMode());
            
            // Test rate limiting key pattern
            testRateLimitingKeys();
            
        } catch (Exception e) {
            Duration failureTime = Duration.between(startTime, Instant.now());
            
            log.error("❌ REDIS CONNECTION FAILED");
            log.error("  • Error: {}", e.getMessage());
            log.error("  • Failure time: {}ms", failureTime.toMillis());
            log.error("  • Storage mode configured: {}", storageModeConfiguration.getCurrentStorageMode());
            
            // Check if Redis is required
            if (isRedisRequired()) {
                log.error("========================================");
                log.error("🚨 FATAL: Redis is required but not available!");
                log.error("🚨 Application cannot start without Redis in {} mode", storageModeConfiguration.getCurrentStorageMode());
                log.error("========================================");
                
                // Fail fast - stop application
                throw new IllegalStateException("Redis connection required but failed: " + e.getMessage(), e);
            } else {
                log.warn("⚠️ Redis not available, but application can continue with fallback");
            }
        }
        
        log.info("========================================");
    }
    
    private void testRateLimitingKeys() {
        try {
            // Check for existing rate limit keys
            String pattern = "rate_limit:*";
            int keyCount = 0;
            
            // Use SCAN to check for rate limit keys (non-blocking)
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                var cursor = connection.keyCommands().scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build()
                );
                
                while (cursor.hasNext() && keyCount < 10) {
                    cursor.next();
                    keyCount++;
                }
                
                cursor.close();
            }
            
            log.info("  • Rate limit keys found: {}", keyCount > 0 ? keyCount + "+" : "none");
            
        } catch (Exception e) {
            log.debug("  • Rate limit key scan skipped: {}", e.getMessage());
        }
    }
    
    private boolean isRedisRequired() {
        // Redis is required in production and test environments when storage.mode=redis
        String profile = System.getProperty("spring.profiles.active", "");
        return storageModeConfiguration.isRedisMode() && 
               (profile.contains("prod") || profile.contains("test"));
    }
}
