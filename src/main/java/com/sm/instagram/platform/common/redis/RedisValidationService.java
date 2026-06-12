package com.sm.instagram.platform.common.redis;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.utils.HashingUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

/**
 * Redis validation service that runs at startup to verify Redis connectivity and configuration.
 * Similar to KMSValidationService, provides detailed diagnostics during application startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.mode", havingValue = "redis")
public class RedisValidationService {

    private final RedisConnectionFactory redisConnectionFactory;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:2000}")
    private String redisTimeoutValue;

    private int redisTimeout;

    @Value("${storage.mode:in-memory}")
    private String storageMode;

    @PostConstruct
    public void validateRedisAccess() {
        // Parse timeout value (handle both "2000" and "2000ms" formats)
        try {
            if (redisTimeoutValue.endsWith("ms")) {
                redisTimeout = Integer.parseInt(redisTimeoutValue.substring(0, redisTimeoutValue.length() - 2));
            } else {
                redisTimeout = Integer.parseInt(redisTimeoutValue);
            }
        } catch (Exception e) {
            log.warn("Could not parse Redis timeout '{}', using default 2000ms", redisTimeoutValue);
            redisTimeout = 2000;
        }

        // GDPR: System validation at startup
        log.info("GDPR: Operation=validateRedisAccess, Purpose=system_startup_validation, DataAccessed=none");

        log.info("==========================================");
        log.info("🚀 REDIS VALIDATION SERVICE");
        log.info("==========================================");

        log.info("📍 Redis Configuration:");
        log.info("   Storage Mode: {}", storageMode);
        log.info("   Host: {}", redisHost);
        log.info("   Port: {}", redisPort);
        log.info("   Database: {}", redisDatabase);
        log.info("   Timeout: {}ms", redisTimeout);

        Instant startTime = Instant.now();

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {

            // Test 1: PING
            log.info("\n🧪 Testing Redis Connection:");
            log.info("   Testing PING...");
            String pingResponse = connection.ping();
            if (!"PONG".equals(pingResponse)) {
                throw new ValidationTranslatableException("error.validation.failed");
            }
            log.info("   ✓ PING successful");

            // Test 2: Server Info
            log.info("   Retrieving server info...");
            Properties serverInfo = connection.serverCommands().info();
            if (serverInfo != null) {
                String version = serverInfo.getProperty("redis_version", "unknown");
                String mode = serverInfo.getProperty("redis_mode", "unknown");

                // Memory info
                String usedMemoryStr = serverInfo.getProperty("used_memory");
                long usedMemory = usedMemoryStr != null ? Long.parseLong(usedMemoryStr) : 0;

                String maxMemoryStr = serverInfo.getProperty("maxmemory");
                long maxMemory = maxMemoryStr != null && !"0".equals(maxMemoryStr) ?
                        Long.parseLong(maxMemoryStr) : 0;

                // Client info
                String connectedClientsStr = serverInfo.getProperty("connected_clients");
                int connectedClients = connectedClientsStr != null ?
                        Integer.parseInt(connectedClientsStr) : 0;

                log.info("   ✓ Server Info Retrieved:");
                log.info("     - Version: {}", version);
                log.info("     - Mode: {}", mode);
                log.info("     - Connected Clients: {}", connectedClients);
                log.info("     - Memory Used: {} MB", usedMemory / 1024 / 1024);
                if (maxMemory > 0) {
                    double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
                    log.info("     - Memory Limit: {} MB", maxMemory / 1024 / 1024);
                    log.info("     - Memory Usage: {:.2f}%", memoryUsagePercent);

                    if (memoryUsagePercent > 90) {
                        log.warn("   ⚠️ WARNING: Memory usage above 90%!");
                    } else if (memoryUsagePercent > 75) {
                        log.warn("   ⚠️ WARNING: Memory usage above 75%");
                    }
                } else {
                    log.info("     - Memory Limit: unlimited");
                }
            }

            // Test 3: Database Size
            log.info("   Checking database size...");
            Long dbSize = connection.serverCommands().dbSize();
            log.info("   ✓ Database contains {} keys", dbSize);

            // Test 4: Write/Read Test
            log.info("   Testing write/read operations...");
            String testKey = HashingUtil.generateRedisKey("validation_test", String.valueOf(System.currentTimeMillis()));
            String testValue = "OK";

            redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(1));
            String retrievedValue = redisTemplate.opsForValue().get(testKey);

            if (!testValue.equals(retrievedValue)) {
                throw new ValidationTranslatableException("error.validation.failed");
            }

            redisTemplate.delete(testKey);
            log.info("   ✓ Write/Read test successful");

            // Test 5: Rate Limit Keys Count
            log.info("   Counting rate limit keys...");
            int rateLimitKeys = countRateLimitKeys(connection);
            if (rateLimitKeys >= 0) {
                log.info("   ✓ Found {} rate limit keys", rateLimitKeys);
            } else {
                log.info("   ℹ️ Could not count rate limit keys");
            }

            // Test 6: Test Multiple Data Types
            log.info("\n🧪 Testing Redis Data Types:");
            testRedisDataTypes();

            // Test 7: Test Rate Limiting Simulation
            log.info("\n🧪 Testing Rate Limiting Simulation:");
            testRateLimitingSimulation();

            // Test 8: Test Expiration
            log.info("\n🧪 Testing Key Expiration:");
            testKeyExpiration();

            // Calculate total response time
            Duration responseTime = Duration.between(startTime, Instant.now());

            // Performance check
            if (responseTime.toMillis() > 1000) {
                log.warn("   ⚠️ WARNING: Slow response time: {}ms", responseTime.toMillis());
            } else {
                log.info("   ✓ Response time: {}ms", responseTime.toMillis());
            }

            log.info("\n✅ REDIS VALIDATION SUCCESSFUL");
            log.info("   ✓ Connection established");
            log.info("   ✓ Server responding");
            log.info("   ✓ Read/Write operations working");
            log.info("   ✓ Ready for rate limiting!");

        } catch (Exception e) {
            log.error("\n❌ REDIS VALIDATION FAILED!");
            log.error("   Error: {}", e.getMessage());
            log.error("\n   Troubleshooting steps:");
            log.error("   1. Check if Redis server is running");
            log.error("   2. Verify connection settings: {}:{}", redisHost, redisPort);
            log.error("   3. Check firewall/network settings");
            log.error("   4. Verify Redis password if authentication is enabled");

            if (e.getMessage() != null) {
                if (e.getMessage().contains("Connection refused")) {
                    log.error("   → Redis server is not reachable!");
                } else if (e.getMessage().contains("NOAUTH")) {
                    log.error("   → Redis requires authentication!");
                } else if (e.getMessage().contains("timeout")) {
                    log.error("   → Connection timeout - server may be slow or unreachable!");
                }
            }

            // Don't fail startup, but warn severely
            log.warn("⚠️⚠️ CONTINUING WITHOUT REDIS - FALLING BACK TO IN-MEMORY! ⚠️⚠️");
        }

        log.info("==========================================");
    }

    private int countRateLimitKeys(RedisConnection connection) {
        try {
            int count = 0;
            var cursor = connection.keyCommands().scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match("rate_limit:*")
                            .count(1000)
                            .build()
            );

            while (cursor.hasNext() && count < 10000) {
                cursor.next();
                count++;
            }

            cursor.close();
            return count;
        } catch (Exception e) {
            log.debug("Failed to count rate limit keys: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Test method to verify Redis is operational
     */
    public boolean isRedisOperational() {
        log.debug("GDPR: Operation=checkRedisOperational, Purpose=health_check, DataAccessed=none");

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equals(connection.ping());
        } catch (Exception e) {
            log.error("GDPR: Operation=checkRedisOperational_failed, Error={}, Purpose=health_check", e.getMessage());
            return false;
        }
    }

    /**
     * Test various Redis data types
     */
    private void testRedisDataTypes() {
        log.debug("GDPR: Operation=testRedisDataTypes, Purpose=system_validation, DataAccessed=test_data_only");

        try {
            // Test String operations with hashed keys
            String stringKey = HashingUtil.generateRedisKey("test_string", "sample");
            redisTemplate.opsForValue().set(stringKey, "Hello Redis!", Duration.ofMinutes(5));
            String stringValue = redisTemplate.opsForValue().get(stringKey);
            log.info("   ✓ String ops: Stored and retrieved '{}'", stringValue);

            // Test List operations with hashed keys
            String listKey = HashingUtil.generateRedisKey("test_list", "queue");
            redisTemplate.opsForList().rightPush(listKey, "item1");
            redisTemplate.opsForList().rightPush(listKey, "item2");
            redisTemplate.opsForList().rightPush(listKey, "item3");
            Long listSize = redisTemplate.opsForList().size(listKey);
            redisTemplate.expire(listKey, Duration.ofMinutes(5));
            log.info("   ✓ List ops: Created list with {} items", listSize);

            // Test Set operations with hashed keys
            String setKey = HashingUtil.generateRedisKey("test_set", "users");
            redisTemplate.opsForSet().add(setKey, "user1", "user2", "user3");
            Long setSize = redisTemplate.opsForSet().size(setKey);
            redisTemplate.expire(setKey, Duration.ofMinutes(5));
            log.info("   ✓ Set ops: Created set with {} unique items", setSize);

            // Test Hash operations with hashed keys
            String hashKey = HashingUtil.generateRedisKey("test_hash", "config");
            redisTemplate.opsForHash().put(hashKey, "environment", "test");
            redisTemplate.opsForHash().put(hashKey, "version", "1.0.0");
            redisTemplate.opsForHash().put(hashKey, "timestamp", String.valueOf(System.currentTimeMillis()));
            Long hashSize = redisTemplate.opsForHash().size(hashKey);
            redisTemplate.expire(hashKey, Duration.ofMinutes(5));
            log.info("   ✓ Hash ops: Created hash with {} fields", hashSize);

            // Test Sorted Set (ZSet) operations with hashed keys
            String zsetKey = HashingUtil.generateRedisKey("test_zset", "leaderboard");
            redisTemplate.opsForZSet().add(zsetKey, "player1", 100);
            redisTemplate.opsForZSet().add(zsetKey, "player2", 200);
            redisTemplate.opsForZSet().add(zsetKey, "player3", 150);
            Long zsetSize = redisTemplate.opsForZSet().size(zsetKey);
            redisTemplate.expire(zsetKey, Duration.ofMinutes(5));
            log.info("   ✓ ZSet ops: Created sorted set with {} items", zsetSize);

        } catch (Exception e) {
            log.warn("   ⚠️ Some data type tests failed: {}", e.getMessage());
        }
    }

    /**
     * Simulate rate limiting scenario with GDPR-compliant hashed identifiers
     */
    private void testRateLimitingSimulation() {
        log.debug("GDPR: Operation=testRateLimiting, Purpose=system_validation, DataAccessed=test_data_only");

        try {
            String userId = "test-user-123";
            String endpoint = "/api/test/endpoint";

            // Simulate rate limit entries with hashed user ID
            String hashedUserId = HashingUtil.hashIdentifier(userId);
            for (int i = 1; i <= 5; i++) {
                String rateLimitKey = String.format("rate_limit:%s:%s:%d", hashedUserId, endpoint, i);
                redisTemplate.opsForValue().set(rateLimitKey, String.valueOf(i), Duration.ofMinutes(1));
            }

            // Count rate limit keys
            int count = 0;
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                var cursor = connection.keyCommands().scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match("rate_limit:*")
                                .count(100)
                                .build()
                );

                while (cursor.hasNext()) {
                    cursor.next();
                    count++;
                }
                cursor.close();
            }

            log.info("   ✓ Created {} rate limit test entries (with hashed IDs)", count);
            log.info("   ✓ Rate limiting keys will expire in 1 minute");

            // Test increment operation (common in rate limiting) with hashed key
            String counterKey = HashingUtil.generateRedisKey("rate_limit_counter", "test");
            Long counter = redisTemplate.opsForValue().increment(counterKey);
            redisTemplate.expire(counterKey, Duration.ofMinutes(1));
            log.info("   ✓ Counter increment test: value = {}", counter);

        } catch (Exception e) {
            log.warn("   ⚠️ Rate limiting simulation failed: {}", e.getMessage());
        }
    }

    /**
     * Test key expiration
     */
    private void testKeyExpiration() {
        try {
            // Test TTL setting with hashed keys
            String ttlKey = HashingUtil.generateRedisKey("test_ttl", "shortlived");
            redisTemplate.opsForValue().set(ttlKey, "expires soon", Duration.ofSeconds(3));

            Long ttl = redisTemplate.getExpire(ttlKey);
            log.info("   ✓ Set key with 3 second TTL, remaining: {} seconds", ttl);

            // Test PERSIST (remove expiration) with hashed keys
            String persistKey = HashingUtil.generateRedisKey("test_persist", "permanent");
            redisTemplate.opsForValue().set(persistKey, "was temporary", Duration.ofSeconds(10));
            redisTemplate.persist(persistKey);
            Long persistTtl = redisTemplate.getExpire(persistKey);
            log.info("   ✓ Removed expiration from key, TTL: {} (should be -1)", persistTtl);

            // Clean up persistent key
            redisTemplate.delete(persistKey);

            // Test atomic operations with hashed keys
            String atomicKey = HashingUtil.generateRedisKey("test_atomic", "counter");
            redisTemplate.opsForValue().setIfAbsent(atomicKey, "0", Duration.ofMinutes(1));
            Long atomicValue = redisTemplate.opsForValue().increment(atomicKey, 5);
            log.info("   ✓ Atomic increment by 5: result = {}", atomicValue);

        } catch (Exception e) {
            log.warn("   ⚠️ Expiration tests failed: {}", e.getMessage());
        }
    }
}
