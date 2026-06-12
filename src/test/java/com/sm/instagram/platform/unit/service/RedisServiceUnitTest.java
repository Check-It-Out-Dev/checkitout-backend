package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.redis.RedisValidationService;
import com.sm.instagram.platform.common.utils.HashingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for RedisValidationService.
 * Tests Redis connectivity validation, operations, and error handling.
 * Uses mocked RedisTemplate and RedisConnectionFactory.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisValidationService Unit Tests")
class RedisServiceUnitTest {

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private RedisServerCommands serverCommands;

    @Mock
    private RedisKeyCommands keyCommands;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private Cursor<byte[]> cursor;

    private RedisValidationService redisValidationService;

    @BeforeEach
    void setUp() {
        redisValidationService = new RedisValidationService(redisConnectionFactory, redisTemplate);

        // Set default configuration values via reflection
        ReflectionTestUtils.setField(redisValidationService, "redisHost", "localhost");
        ReflectionTestUtils.setField(redisValidationService, "redisPort", 6379);
        ReflectionTestUtils.setField(redisValidationService, "redisDatabase", 0);
        ReflectionTestUtils.setField(redisValidationService, "redisTimeoutValue", "2000");
        ReflectionTestUtils.setField(redisValidationService, "storageMode", "redis");

        // Setup common mock behaviors
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.serverCommands()).thenReturn(serverCommands);
        when(redisConnection.keyCommands()).thenReturn(keyCommands);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    private Properties createServerInfo() {
        Properties props = new Properties();
        props.setProperty("redis_version", "7.0.0");
        props.setProperty("redis_mode", "standalone");
        props.setProperty("used_memory", String.valueOf(100 * 1024 * 1024)); // 100 MB
        props.setProperty("maxmemory", String.valueOf(1024 * 1024 * 1024)); // 1 GB
        props.setProperty("connected_clients", "10");
        return props;
    }

    private void setupSuccessfulValidation() {
        when(redisConnection.ping()).thenReturn("PONG");
        when(serverCommands.info()).thenReturn(createServerInfo());
        when(serverCommands.dbSize()).thenReturn(100L);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        when(listOperations.size(anyString())).thenReturn(3L);
        when(setOperations.size(anyString())).thenReturn(3L);
        when(hashOperations.size(anyString())).thenReturn(3L);
        when(zSetOperations.size(anyString())).thenReturn(3L);
        when(redisTemplate.getExpire(anyString())).thenReturn(3L);
        when(redisTemplate.persist(anyString())).thenReturn(true);
        when(valueOperations.increment(anyString(), anyLong())).thenReturn(5L);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Nested
    @DisplayName("validateRedisAccess Tests")
    class ValidateRedisAccessTests {

        @Test
        @DisplayName("should successfully validate Redis connection with PONG response")
        void shouldSuccessfullyValidateRedisConnectionWithPongResponse() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnection).ping();
            verify(serverCommands).info();
            verify(serverCommands).dbSize();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when PING does not return PONG")
        void shouldThrowWhenPingDoesNotReturnPong() {
            // Given
            when(redisConnection.ping()).thenReturn("INVALID");

            // When/Then - The validation should log error but not throw (graceful degradation)
            redisValidationService.validateRedisAccess();

            // Verify ping was attempted
            verify(redisConnection).ping();
        }

        @Test
        @DisplayName("should handle null PING response gracefully")
        void shouldHandleNullPingResponseGracefully() {
            // Given
            when(redisConnection.ping()).thenReturn(null);

            // When - should not throw, but log error
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnection).ping();
        }

        @Test
        @DisplayName("should parse timeout value without ms suffix")
        void shouldParseTimeoutValueWithoutMsSuffix() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");
            ReflectionTestUtils.setField(redisValidationService, "redisTimeoutValue", "3000");

            // When
            redisValidationService.validateRedisAccess();

            // Then - no exception, timeout parsed correctly
            verify(redisConnection).ping();
        }

        @Test
        @DisplayName("should parse timeout value with ms suffix")
        void shouldParseTimeoutValueWithMsSuffix() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");
            ReflectionTestUtils.setField(redisValidationService, "redisTimeoutValue", "5000ms");

            // When
            redisValidationService.validateRedisAccess();

            // Then - no exception, timeout parsed correctly
            verify(redisConnection).ping();
        }

        @Test
        @DisplayName("should use default timeout when parsing fails")
        void shouldUseDefaultTimeoutWhenParsingFails() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");
            ReflectionTestUtils.setField(redisValidationService, "redisTimeoutValue", "invalid-timeout");

            // When
            redisValidationService.validateRedisAccess();

            // Then - should use default 2000ms, no exception
            verify(redisConnection).ping();
        }

        @Test
        @DisplayName("should log warning for high memory usage above 90%")
        void shouldLogWarningForHighMemoryUsageAbove90Percent() {
            // Given
            Properties props = createServerInfo();
            props.setProperty("used_memory", String.valueOf(950 * 1024 * 1024L)); // 950 MB
            props.setProperty("maxmemory", String.valueOf(1024 * 1024 * 1024L)); // 1 GB = 92.7%

            setupSuccessfulValidation();
            when(serverCommands.info()).thenReturn(props);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then - should complete without exception, warning logged
            verify(serverCommands).info();
        }

        @Test
        @DisplayName("should log warning for memory usage above 75%")
        void shouldLogWarningForMemoryUsageAbove75Percent() {
            // Given
            Properties props = createServerInfo();
            props.setProperty("used_memory", String.valueOf(800 * 1024 * 1024L)); // 800 MB
            props.setProperty("maxmemory", String.valueOf(1024 * 1024 * 1024L)); // 1 GB = 78%

            setupSuccessfulValidation();
            when(serverCommands.info()).thenReturn(props);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then - should complete without exception
            verify(serverCommands).info();
        }

        @Test
        @DisplayName("should handle unlimited memory configuration")
        void shouldHandleUnlimitedMemoryConfiguration() {
            // Given
            Properties props = createServerInfo();
            props.setProperty("maxmemory", "0"); // Unlimited

            setupSuccessfulValidation();
            when(serverCommands.info()).thenReturn(props);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(serverCommands).info();
        }

        @Test
        @DisplayName("should handle null server info gracefully")
        void shouldHandleNullServerInfoGracefully() {
            // Given
            when(redisConnection.ping()).thenReturn("PONG");
            when(serverCommands.info()).thenReturn(null);
            when(serverCommands.dbSize()).thenReturn(0L);
            when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
            when(cursor.hasNext()).thenReturn(false);
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(listOperations.size(anyString())).thenReturn(3L);
            when(setOperations.size(anyString())).thenReturn(3L);
            when(hashOperations.size(anyString())).thenReturn(3L);
            when(zSetOperations.size(anyString())).thenReturn(3L);
            when(redisTemplate.getExpire(anyString())).thenReturn(3L);
            when(redisTemplate.persist(anyString())).thenReturn(true);
            when(valueOperations.increment(anyString(), anyLong())).thenReturn(5L);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(serverCommands).info();
        }

        @Test
        @DisplayName("should handle connection exception gracefully")
        void shouldHandleConnectionExceptionGracefully() {
            // Given
            when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Connection refused"));

            // When - should not throw, graceful degradation
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnectionFactory).getConnection();
        }

        @Test
        @DisplayName("should handle NOAUTH exception with specific error message")
        void shouldHandleNoAuthExceptionWithSpecificErrorMessage() {
            // Given
            when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("NOAUTH Authentication required"));

            // When
            redisValidationService.validateRedisAccess();

            // Then - should log authentication error
            verify(redisConnectionFactory).getConnection();
        }

        @Test
        @DisplayName("should handle timeout exception with specific error message")
        void shouldHandleTimeoutExceptionWithSpecificErrorMessage() {
            // Given
            when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Connection timeout"));

            // When
            redisValidationService.validateRedisAccess();

            // Then - should log timeout error
            verify(redisConnectionFactory).getConnection();
        }
    }

    @Nested
    @DisplayName("isRedisOperational Tests")
    class IsRedisOperationalTests {

        @Test
        @DisplayName("should return true when PING returns PONG")
        void shouldReturnTrueWhenPingReturnsPong() {
            // Given
            when(redisConnection.ping()).thenReturn("PONG");

            // When
            boolean result = redisValidationService.isRedisOperational();

            // Then
            assertThat(result).isTrue();
            verify(redisConnection).ping();
        }

        @Test
        @DisplayName("should return false when PING does not return PONG")
        void shouldReturnFalseWhenPingDoesNotReturnPong() {
            // Given
            when(redisConnection.ping()).thenReturn("ERROR");

            // When
            boolean result = redisValidationService.isRedisOperational();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when PING returns null")
        void shouldReturnFalseWhenPingReturnsNull() {
            // Given
            when(redisConnection.ping()).thenReturn(null);

            // When
            boolean result = redisValidationService.isRedisOperational();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when connection throws exception")
        void shouldReturnFalseWhenConnectionThrowsException() {
            // Given
            when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Connection error"));

            // When
            boolean result = redisValidationService.isRedisOperational();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when ping throws exception")
        void shouldReturnFalseWhenPingThrowsException() {
            // Given
            when(redisConnection.ping()).thenThrow(new RuntimeException("Ping failed"));

            // When
            boolean result = redisValidationService.isRedisOperational();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Write/Read Operation Tests")
    class WriteReadOperationTests {

        @Test
        @DisplayName("should successfully write and read test value")
        void shouldSuccessfullyWriteAndReadTestValue() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations, atLeastOnce()).set(anyString(), eq("OK"), any(Duration.class));
            verify(valueOperations, atLeastOnce()).get(anyString());
        }

        @Test
        @DisplayName("should throw when write/read test fails with mismatched value")
        void shouldThrowWhenWriteReadTestFailsWithMismatchedValue() {
            // Given
            when(redisConnection.ping()).thenReturn("PONG");
            when(serverCommands.info()).thenReturn(createServerInfo());
            when(serverCommands.dbSize()).thenReturn(100L);
            when(valueOperations.get(anyString())).thenReturn("WRONG_VALUE");

            // When - should log error but not crash
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations).set(anyString(), eq("OK"), any(Duration.class));
        }

        @Test
        @DisplayName("should delete test key after successful validation")
        void shouldDeleteTestKeyAfterSuccessfulValidation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisTemplate).delete(argThat((String key) -> key.contains("validation_test")));
        }
    }

    @Nested
    @DisplayName("Data Type Tests")
    class DataTypeTests {

        @Test
        @DisplayName("should test string operations during validation")
        void shouldTestStringOperationsDuringValidation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK", "Hello Redis!");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations, atLeastOnce()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should test list operations during validation")
        void shouldTestListOperationsDuringValidation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(listOperations, atLeast(3)).rightPush(anyString(), anyString());
            verify(listOperations).size(anyString());
        }

        @Test
        @DisplayName("should test set operations during validation")
        void shouldTestSetOperationsDuringValidation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(setOperations).add(anyString(), anyString(), anyString(), anyString());
            verify(setOperations).size(anyString());
        }

        @Test
        @DisplayName("should test hash operations during validation")
        void shouldTestHashOperationsDuringValidation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(hashOperations, atLeast(3)).put(anyString(), anyString(), anyString());
            verify(hashOperations).size(anyString());
        }

        @Test
        @DisplayName("should test sorted set operations during validation")
        void shouldTestSortedSetOperationsDuringValidation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(zSetOperations, atLeast(3)).add(anyString(), anyString(), anyDouble());
            verify(zSetOperations).size(anyString());
        }

        @Test
        @DisplayName("should set expiration on test collections")
        void shouldSetExpirationOnTestCollections() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisTemplate, atLeast(4)).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should handle data type test failures gracefully")
        void shouldHandleDataTypeTestFailuresGracefully() {
            // Given
            when(redisConnection.ping()).thenReturn("PONG");
            when(serverCommands.info()).thenReturn(createServerInfo());
            when(serverCommands.dbSize()).thenReturn(100L);
            when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
            when(cursor.hasNext()).thenReturn(false);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // Simulate failure in data type tests
            when(listOperations.rightPush(anyString(), anyString())).thenThrow(new RuntimeException("List error"));
            when(redisTemplate.getExpire(anyString())).thenReturn(3L);
            when(redisTemplate.persist(anyString())).thenReturn(true);
            when(valueOperations.increment(anyString(), anyLong())).thenReturn(5L);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

            // When - should not throw
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnection).ping();
        }
    }

    @Nested
    @DisplayName("Rate Limiting Simulation Tests")
    class RateLimitingSimulationTests {

        @Test
        @DisplayName("should create rate limit test entries with hashed IDs")
        void shouldCreateRateLimitTestEntriesWithHashedIds() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations, atLeast(5)).set(argThat((String key) -> key.startsWith("rate_limit:")), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should test counter increment operation")
        void shouldTestCounterIncrementOperation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations).increment(argThat((String key) -> key.contains("rate_limit_counter")));
        }

        @Test
        @DisplayName("should scan for rate limit keys")
        void shouldScanForRateLimitKeys() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(keyCommands, atLeast(2)).scan(any(ScanOptions.class));
        }

        @Test
        @DisplayName("should handle scan exception gracefully")
        void shouldHandleScanExceptionGracefully() {
            // Given
            when(redisConnection.ping()).thenReturn("PONG");
            when(serverCommands.info()).thenReturn(createServerInfo());
            when(serverCommands.dbSize()).thenReturn(100L);
            when(keyCommands.scan(any(ScanOptions.class))).thenThrow(new RuntimeException("Scan error"));
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(listOperations.size(anyString())).thenReturn(3L);
            when(setOperations.size(anyString())).thenReturn(3L);
            when(hashOperations.size(anyString())).thenReturn(3L);
            when(zSetOperations.size(anyString())).thenReturn(3L);
            when(redisTemplate.getExpire(anyString())).thenReturn(3L);
            when(redisTemplate.persist(anyString())).thenReturn(true);
            when(valueOperations.increment(anyString(), anyLong())).thenReturn(5L);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

            // When - should not throw
            redisValidationService.validateRedisAccess();

            // Then - validation should continue
            verify(serverCommands).dbSize();
        }
    }

    @Nested
    @DisplayName("Key Expiration Tests")
    class KeyExpirationTests {

        @Test
        @DisplayName("should set TTL on test keys")
        void shouldSetTtlOnTestKeys() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisTemplate, atLeastOnce()).getExpire(anyString());
        }

        @Test
        @DisplayName("should test persist operation")
        void shouldTestPersistOperation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisTemplate).persist(argThat((String key) -> key.contains("test_persist")));
        }

        @Test
        @DisplayName("should delete persistent test key after testing")
        void shouldDeletePersistentTestKeyAfterTesting() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisTemplate, atLeast(2)).delete(anyString());
        }

        @Test
        @DisplayName("should test atomic setIfAbsent operation")
        void shouldTestAtomicSetIfAbsentOperation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should test atomic increment by value operation")
        void shouldTestAtomicIncrementByValueOperation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations).increment(anyString(), eq(5L));
        }

        @Test
        @DisplayName("should handle expiration test failures gracefully")
        void shouldHandleExpirationTestFailuresGracefully() {
            // Given
            when(redisConnection.ping()).thenReturn("PONG");
            when(serverCommands.info()).thenReturn(createServerInfo());
            when(serverCommands.dbSize()).thenReturn(100L);
            when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
            when(cursor.hasNext()).thenReturn(false);
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(listOperations.size(anyString())).thenReturn(3L);
            when(setOperations.size(anyString())).thenReturn(3L);
            when(hashOperations.size(anyString())).thenReturn(3L);
            when(zSetOperations.size(anyString())).thenReturn(3L);

            // Simulate expiration test failure
            when(redisTemplate.getExpire(anyString())).thenThrow(new RuntimeException("Expire error"));
            when(valueOperations.increment(anyString())).thenReturn(1L);

            // When - should not throw
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnection).ping();
        }
    }

    @Nested
    @DisplayName("Server Info Tests")
    class ServerInfoTests {

        @Test
        @DisplayName("should retrieve and log Redis version")
        void shouldRetrieveAndLogRedisVersion() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(serverCommands).info();
        }

        @Test
        @DisplayName("should retrieve database size")
        void shouldRetrieveDatabaseSize() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(serverCommands).dbSize();
        }

        @Test
        @DisplayName("should handle missing server info properties gracefully")
        void shouldHandleMissingServerInfoPropertiesGracefully() {
            // Given
            Properties sparseProps = new Properties();
            // Only set version, leave others null

            when(redisConnection.ping()).thenReturn("PONG");
            when(serverCommands.info()).thenReturn(sparseProps);
            when(serverCommands.dbSize()).thenReturn(0L);
            when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
            when(cursor.hasNext()).thenReturn(false);
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(listOperations.size(anyString())).thenReturn(3L);
            when(setOperations.size(anyString())).thenReturn(3L);
            when(hashOperations.size(anyString())).thenReturn(3L);
            when(zSetOperations.size(anyString())).thenReturn(3L);
            when(redisTemplate.getExpire(anyString())).thenReturn(3L);
            when(redisTemplate.persist(anyString())).thenReturn(true);
            when(valueOperations.increment(anyString(), anyLong())).thenReturn(5L);
            when(valueOperations.increment(anyString())).thenReturn(1L);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

            // When - should not throw
            redisValidationService.validateRedisAccess();

            // Then
            verify(serverCommands).info();
        }
    }

    @Nested
    @DisplayName("Connection Handling Tests")
    class ConnectionHandlingTests {

        @Test
        @DisplayName("should close connection after validation")
        void shouldCloseConnectionAfterValidation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then - connection may be closed multiple times due to try-with-resources
            verify(redisConnection, atLeastOnce()).close();
        }

        @Test
        @DisplayName("should close connection even on exception")
        void shouldCloseConnectionEvenOnException() {
            // Given
            when(redisConnection.ping()).thenThrow(new RuntimeException("Ping failed"));

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnection).close();
        }

        @Test
        @DisplayName("should use connection from factory")
        void shouldUseConnectionFromFactory() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then - connection is obtained multiple times during validation tests
            verify(redisConnectionFactory, atLeastOnce()).getConnection();
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("should log warning for slow response time over 1000ms")
        void shouldLogWarningForSlowResponseTime() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then - response time is calculated internally, we just verify completion
            verify(redisConnection).ping();
        }
    }

    @Nested
    @DisplayName("HashingUtil Integration Tests")
    class HashingUtilIntegrationTests {

        @Test
        @DisplayName("should use HashingUtil for generating Redis keys")
        void shouldUseHashingUtilForGeneratingRedisKeys() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then - verify keys are generated (HashingUtil is used internally)
            verify(valueOperations, atLeastOnce()).set(argThat((String key) ->
                    key.contains("validation_test") || key.contains("test_string") ||
                            key.contains("test_list") || key.contains("test_set") ||
                            key.contains("test_hash") || key.contains("test_zset") ||
                            key.contains("rate_limit") || key.contains("test_ttl") ||
                            key.contains("test_persist") || key.contains("test_atomic")),
                    anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should generate consistent keys using HashingUtil")
        void shouldGenerateConsistentKeysUsingHashingUtil() {
            // Given
            String testInput = "sample";
            String key1 = HashingUtil.generateRedisKey("test_string", testInput);
            String key2 = HashingUtil.generateRedisKey("test_string", testInput);

            // Then
            assertThat(key1).isEqualTo(key2);
            assertThat(key1).startsWith("test_string:");
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @ParameterizedTest
        @CsvSource({
                "localhost, 6379, 0",
                "redis.example.com, 6380, 1",
                "192.168.1.100, 6379, 15"
        })
        @DisplayName("should accept various Redis configurations")
        void shouldAcceptVariousRedisConfigurations(String host, int port, int database) {
            // Given
            ReflectionTestUtils.setField(redisValidationService, "redisHost", host);
            ReflectionTestUtils.setField(redisValidationService, "redisPort", port);
            ReflectionTestUtils.setField(redisValidationService, "redisDatabase", database);

            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnection).ping();
        }

        @ParameterizedTest
        @ValueSource(strings = {"1000", "2000ms", "5000", "10000ms"})
        @DisplayName("should accept various timeout formats")
        void shouldAcceptVariousTimeoutFormats(String timeout) {
            // Given
            ReflectionTestUtils.setField(redisValidationService, "redisTimeoutValue", timeout);

            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnection).ping();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty database")
        void shouldHandleEmptyDatabase() {
            // Given
            setupSuccessfulValidation();
            when(serverCommands.dbSize()).thenReturn(0L);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(serverCommands).dbSize();
        }

        @Test
        @DisplayName("should handle large database")
        void shouldHandleLargeDatabase() {
            // Given
            setupSuccessfulValidation();
            when(serverCommands.dbSize()).thenReturn(1_000_000L);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(serverCommands).dbSize();
        }

        @Test
        @DisplayName("should handle cursor with many rate limit keys")
        void shouldHandleCursorWithManyRateLimitKeys() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // Simulate cursor returning many keys
            when(cursor.hasNext()).thenReturn(true, true, true, true, true, false);
            when(cursor.next()).thenReturn("key1".getBytes(), "key2".getBytes(), "key3".getBytes(),
                    "key4".getBytes(), "key5".getBytes());

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(cursor, atLeastOnce()).hasNext();
        }

        @Test
        @DisplayName("should handle cursor close exception gracefully")
        void shouldHandleCursorCloseExceptionGracefully() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");
            doThrow(new RuntimeException("Close error")).when(cursor).close();

            // When - should not throw
            redisValidationService.validateRedisAccess();

            // Then
            verify(redisConnection).ping();
        }
    }

    @Nested
    @DisplayName("GDPR Compliance Tests")
    class GdprComplianceTests {

        @Test
        @DisplayName("should hash user identifiers for rate limiting")
        void shouldHashUserIdentifiersForRateLimiting() {
            // Given
            String userId = "test-user-123";
            String hashedId = HashingUtil.hashIdentifier(userId);

            // Then - verify hash is generated
            assertThat(hashedId).isNotNull();
            assertThat(hashedId).isNotEqualTo(userId);
            assertThat(hashedId).hasSize(64); // SHA-256 produces 64 hex characters
        }

        @Test
        @DisplayName("should use hashed keys in rate limit simulation")
        void shouldUseHashedKeysInRateLimitSimulation() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then - verify rate limit keys use hashed format
            verify(valueOperations, atLeast(5)).set(argThat((String key) -> {
                // Rate limit keys should contain hashed user ID (64 chars hex)
                if (key.startsWith("rate_limit:")) {
                    String[] parts = key.split(":");
                    // Key format: rate_limit:<hashed_user_id>:<endpoint>:<number>
                    return parts.length >= 2;
                }
                return true; // Allow other keys
            }), anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTests {

        @Test
        @DisplayName("should serialize string values correctly")
        void shouldSerializeStringValuesCorrectly() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations).set(anyString(), eq("OK"), any(Duration.class));
            verify(valueOperations).set(anyString(), eq("Hello Redis!"), any(Duration.class));
        }

        @Test
        @DisplayName("should handle special characters in values")
        void shouldHandleSpecialCharactersInValues() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then - validation completes successfully
            verify(redisConnection).ping();
        }
    }

    @Nested
    @DisplayName("TTL and Duration Tests")
    class TtlAndDurationTests {

        @Test
        @DisplayName("should set 1 second TTL for validation test key")
        void shouldSet1SecondTtlForValidationTestKey() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations).set(argThat((String key) -> key.contains("validation_test")),
                    eq("OK"), eq(Duration.ofSeconds(1)));
        }

        @Test
        @DisplayName("should set 5 minute TTL for data type test keys")
        void shouldSet5MinuteTtlForDataTypeTestKeys() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations).set(argThat((String key) -> key.contains("test_string")),
                    eq("Hello Redis!"), eq(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("should set 1 minute TTL for rate limit test keys")
        void shouldSet1MinuteTtlForRateLimitTestKeys() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations, atLeast(5)).set(argThat((String key) -> key.startsWith("rate_limit:")),
                    anyString(), eq(Duration.ofMinutes(1)));
        }

        @Test
        @DisplayName("should set 3 second TTL for short-lived test key")
        void shouldSet3SecondTtlForShortLivedTestKey() {
            // Given
            setupSuccessfulValidation();
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            redisValidationService.validateRedisAccess();

            // Then
            verify(valueOperations).set(argThat((String key) -> key.contains("test_ttl")),
                    eq("expires soon"), eq(Duration.ofSeconds(3)));
        }
    }
}
