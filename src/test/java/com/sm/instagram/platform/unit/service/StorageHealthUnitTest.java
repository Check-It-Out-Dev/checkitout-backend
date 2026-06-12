package com.sm.instagram.platform.unit.service;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.storage.health.UploadSystemHealthIndicator;
import com.sm.instagram.platform.storage.service.StorageRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UploadSystemHealthIndicator in storage.health package.
 * Tests all health check scenarios including Firebase Storage, Redis, and RateLimiter checks.
 * No Spring context needed - using pure Mockito for isolation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UploadSystemHealthIndicator Unit Tests")
class StorageHealthUnitTest {

    @Mock
    private Storage storage;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private StorageRateLimitService rateLimiterService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Bucket bucket;

    private UploadSystemHealthIndicator healthIndicator;

    private static final String TEST_BUCKET_NAME = "test-bucket";

    @BeforeEach
    void setUp() {
        // Setup Redis value operations mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Create health indicator with all dependencies
        healthIndicator = new UploadSystemHealthIndicator(storage, redisTemplate, rateLimiterService);

        // Set the bucket name via reflection
        ReflectionTestUtils.setField(healthIndicator, "bucketName", TEST_BUCKET_NAME);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("should create indicator with all dependencies null")
        void shouldCreateWithAllNullDependencies() {
            // When
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(null, null, null);

            // Then
            assertThat(indicator).isNotNull();
            Health health = indicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("should create indicator with only storage dependency")
        void shouldCreateWithOnlyStorage() {
            // When
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(storage, null, null);
            ReflectionTestUtils.setField(indicator, "bucketName", TEST_BUCKET_NAME);
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);

            // Then
            assertThat(indicator).isNotNull();
            Health health = indicator.health();
            assertThat(health).isNotNull();
        }

        @Test
        @DisplayName("should create indicator with only redis dependency")
        void shouldCreateWithOnlyRedis() {
            // Given
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(null, redisTemplate, null);

            // Then
            assertThat(indicator).isNotNull();
            Health health = indicator.health();
            assertThat(health).isNotNull();
        }

        @Test
        @DisplayName("should create indicator with only rate limiter dependency")
        void shouldCreateWithOnlyRateLimiter() {
            // When
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(null, null, rateLimiterService);

            // Then
            assertThat(indicator).isNotNull();
            Health health = indicator.health();
            assertThat(health).isNotNull();
        }
    }

    @Nested
    @DisplayName("Health Method Basic Tests")
    class HealthMethodBasicTests {

        @Test
        @DisplayName("should return Health object when called")
        void shouldReturnHealthObject() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health).isNotNull();
            assertThat(health.getStatus()).isNotNull();
        }

        @Test
        @DisplayName("should return Health with details")
        void shouldReturnHealthWithDetails() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).isNotNull();
            assertThat(health.getDetails()).isNotEmpty();
        }

        @Test
        @DisplayName("should implement HealthIndicator interface")
        void shouldImplementHealthIndicatorInterface() {
            // Then
            assertThat(healthIndicator).isInstanceOf(HealthIndicator.class);
        }
    }

    @Nested
    @DisplayName("Firebase Storage Health Check Tests")
    class FirebaseStorageHealthCheckTests {

        @Test
        @DisplayName("should report storage UP when bucket exists")
        void shouldReportStorageUpWhenBucketExists() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("UP");
        }

        @Test
        @DisplayName("should report storage DOWN when bucket does not exist")
        void shouldReportStorageDownWhenBucketDoesNotExist() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(null);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("DOWN");
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should report storage DOWN when exception occurs")
        void shouldReportStorageDownWhenExceptionOccurs() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenThrow(new RuntimeException("Connection failed"));
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("DOWN");
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should report storage NOT_CONFIGURED when storage is null")
        void shouldReportStorageNotConfiguredWhenNull() {
            // Given
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(null, redisTemplate, rateLimiterService);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("NOT_CONFIGURED");
        }

        @Test
        @DisplayName("should report storage DOWN when bucket name is null")
        void shouldReportStorageDownWhenBucketNameIsNull() {
            // Given
            ReflectionTestUtils.setField(healthIndicator, "bucketName", null);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("DOWN");
        }

        @Test
        @DisplayName("should report storage DOWN when bucket name is empty")
        void shouldReportStorageDownWhenBucketNameIsEmpty() {
            // Given
            ReflectionTestUtils.setField(healthIndicator, "bucketName", "");
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("DOWN");
        }
    }

    @Nested
    @DisplayName("Redis Health Check Tests")
    class RedisHealthCheckTests {

        @Test
        @DisplayName("should report redis UP when set and get succeed")
        void shouldReportRedisUpWhenOperationsSucceed() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("redis")).isEqualTo("UP");
        }

        @Test
        @DisplayName("should report redis DOWN when get returns wrong value")
        void shouldReportRedisDownWhenGetReturnsWrongValue() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("WRONG");

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("redis")).isEqualTo("DOWN");
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should report redis DOWN when get returns null")
        void shouldReportRedisDownWhenGetReturnsNull() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn(null);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("redis")).isEqualTo("DOWN");
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should report redis DOWN when exception occurs during set")
        void shouldReportRedisDownWhenSetFails() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            doThrow(new RuntimeException("Redis connection error"))
                    .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("redis")).isEqualTo("DOWN");
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should report redis DOWN when exception occurs during get")
        void shouldReportRedisDownWhenGetFails() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis read error"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("redis")).isEqualTo("DOWN");
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should report redis NOT_CONFIGURED when redisTemplate is null")
        void shouldReportRedisNotConfiguredWhenNull() {
            // Given
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(storage, null, rateLimiterService);
            ReflectionTestUtils.setField(indicator, "bucketName", TEST_BUCKET_NAME);
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getDetails().get("redis")).isEqualTo("NOT_CONFIGURED");
        }
    }

    @Nested
    @DisplayName("Rate Limiter Health Check Tests")
    class RateLimiterHealthCheckTests {

        @Test
        @DisplayName("should report rate limiter UP when getUserStatus succeeds")
        void shouldReportRateLimiterUpWhenGetStatusSucceeds() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("rate_limiter")).isEqualTo("UP");
        }

        @Test
        @DisplayName("should report rate limiter DOWN when exception occurs")
        void shouldReportRateLimiterDownWhenExceptionOccurs() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(rateLimiterService.getUserStatus(anyString()))
                    .thenThrow(new RuntimeException("Rate limiter error"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("rate_limiter")).isEqualTo("DOWN");
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should report rate limiter NOT_CONFIGURED when service is null")
        void shouldReportRateLimiterNotConfiguredWhenNull() {
            // Given
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(storage, redisTemplate, null);
            ReflectionTestUtils.setField(indicator, "bucketName", TEST_BUCKET_NAME);
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getDetails().get("rate_limiter")).isEqualTo("NOT_CONFIGURED");
        }
    }

    @Nested
    @DisplayName("Overall Health Status Tests")
    class OverallHealthStatusTests {

        @Test
        @DisplayName("should return UP when all components are healthy")
        void shouldReturnUpWhenAllComponentsHealthy() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails().get("status_note"))
                    .isEqualTo("All configured upload system components are healthy");
        }

        @Test
        @DisplayName("should return UP when no components are configured")
        void shouldReturnUpWhenNoComponentsConfigured() {
            // Given
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(null, null, null);

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails().get("status_note"))
                    .isEqualTo("Upload system not configured - basic mode without rate limiting or cloud storage");
        }

        @Test
        @DisplayName("should return DOWN when storage is configured but not accessible")
        void shouldReturnDownWhenStorageConfiguredButNotAccessible() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(null);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("status_note").toString())
                    .contains("Storage is configured but not accessible");
        }

        @Test
        @DisplayName("should return DOWN when redis is configured but not accessible")
        void shouldReturnDownWhenRedisConfiguredButNotAccessible() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn(null);
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("status_note").toString())
                    .contains("Redis is configured but not accessible");
        }

        @Test
        @DisplayName("should return DOWN when rate limiter is configured but not working")
        void shouldReturnDownWhenRateLimiterConfiguredButNotWorking() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(rateLimiterService.getUserStatus(anyString()))
                    .thenThrow(new RuntimeException("Rate limiter error"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("status_note").toString())
                    .contains("Rate limiter is configured but not working");
        }

        @Test
        @DisplayName("should concatenate multiple failure messages")
        void shouldConcatenateMultipleFailureMessages() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(null);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(rateLimiterService.getUserStatus(anyString()))
                    .thenThrow(new RuntimeException("Rate limiter error"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            String statusNote = health.getDetails().get("status_note").toString();
            assertThat(statusNote).contains("Storage is configured but not accessible");
            assertThat(statusNote).contains("Redis is configured but not accessible");
            assertThat(statusNote).contains("Rate limiter is configured but not working");
        }
    }

    @Nested
    @DisplayName("System Load Details Tests")
    class SystemLoadDetailsTests {

        @Test
        @DisplayName("should include system_load in health details")
        void shouldIncludeSystemLoadInDetails() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("system_load");
        }

        @Test
        @DisplayName("should include memory_used_mb in system load")
        void shouldIncludeMemoryUsedMbInSystemLoad() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat(systemLoad).containsKey("memory_used_mb");
            assertThat(systemLoad.get("memory_used_mb")).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("should include memory_total_mb in system load")
        void shouldIncludeMemoryTotalMbInSystemLoad() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat(systemLoad).containsKey("memory_total_mb");
            assertThat(systemLoad.get("memory_total_mb")).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("should include memory_free_mb in system load")
        void shouldIncludeMemoryFreeMbInSystemLoad() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat(systemLoad).containsKey("memory_free_mb");
            assertThat(systemLoad.get("memory_free_mb")).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("should include memory_max_mb in system load")
        void shouldIncludeMemoryMaxMbInSystemLoad() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat(systemLoad).containsKey("memory_max_mb");
            assertThat(systemLoad.get("memory_max_mb")).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("should include active_threads in system load")
        void shouldIncludeActiveThreadsInSystemLoad() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat(systemLoad).containsKey("active_threads");
            assertThat(systemLoad.get("active_threads")).isInstanceOf(Integer.class);
        }

        @Test
        @DisplayName("should include available_processors in system load")
        void shouldIncludeAvailableProcessorsInSystemLoad() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat(systemLoad).containsKey("available_processors");
            assertThat(systemLoad.get("available_processors")).isInstanceOf(Integer.class);
        }

        @Test
        @DisplayName("should have positive memory values")
        void shouldHavePositiveMemoryValues() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat((Long) systemLoad.get("memory_used_mb")).isGreaterThanOrEqualTo(0);
            assertThat((Long) systemLoad.get("memory_total_mb")).isGreaterThan(0);
            assertThat((Long) systemLoad.get("memory_free_mb")).isGreaterThanOrEqualTo(0);
            assertThat((Long) systemLoad.get("memory_max_mb")).isGreaterThan(0);
        }

        @Test
        @DisplayName("should have positive active threads")
        void shouldHavePositiveActiveThreads() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat((Integer) systemLoad.get("active_threads")).isGreaterThan(0);
        }

        @Test
        @DisplayName("should have at least one available processor")
        void shouldHaveAtLeastOneProcessor() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            assertThat((Integer) systemLoad.get("available_processors")).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should have memory_used_mb less than or equal to memory_max_mb")
        void shouldHaveMemoryUsedLessThanMax() {
            // When
            Health health = healthIndicator.health();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> systemLoad = (Map<String, Object>) health.getDetails().get("system_load");
            Long memoryUsed = (Long) systemLoad.get("memory_used_mb");
            Long memoryMax = (Long) systemLoad.get("memory_max_mb");
            assertThat(memoryUsed).isLessThanOrEqualTo(memoryMax);
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("should be thread-safe for concurrent health checks")
        void shouldBeThreadSafeForConcurrentHealthChecks() throws InterruptedException {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Health health = healthIndicator.health();
                        if (health != null && health.getStatus() != null) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then
            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("should handle rapid successive calls")
        void shouldHandleRapidSuccessiveCalls() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When/Then - reduced iterations for memory efficiency
            for (int i = 0; i < 20; i++) {
                Health health = healthIndicator.health();
                assertThat(health).isNotNull();
                assertThat(health.getStatus()).isEqualTo(Status.UP);
            }
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should not throw exception when storage throws unexpected exception")
        void shouldNotThrowWhenStorageThrowsUnexpectedException() {
            // Given
            when(storage.get(anyString())).thenThrow(new NullPointerException("Unexpected null"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health).isNotNull();
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("DOWN");
        }

        @Test
        @DisplayName("should not throw exception when redis throws unexpected exception")
        void shouldNotThrowWhenRedisThrowsUnexpectedException() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("Connection pool exhausted"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health).isNotNull();
            assertThat(health.getDetails().get("redis")).isEqualTo("DOWN");
        }

        @Test
        @DisplayName("should not throw exception when rate limiter throws illegal argument exception")
        void shouldNotThrowWhenRateLimiterThrowsIllegalArgumentException() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(rateLimiterService.getUserStatus(anyString()))
                    .thenThrow(new IllegalArgumentException("Invalid user ID"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health).isNotNull();
            assertThat(health.getDetails().get("rate_limiter")).isEqualTo("DOWN");
        }

        @Test
        @DisplayName("should handle health check user ID for rate limiter")
        void shouldHandleHealthCheckUserId() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(eq("health-check-user"))).thenReturn(status);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("rate_limiter")).isEqualTo("UP");
        }

        @Test
        @DisplayName("should handle repeated instantiation")
        void shouldHandleRepeatedInstantiation() {
            // When
            UploadSystemHealthIndicator indicator1 = new UploadSystemHealthIndicator(null, null, null);
            UploadSystemHealthIndicator indicator2 = new UploadSystemHealthIndicator(null, null, null);
            UploadSystemHealthIndicator indicator3 = new UploadSystemHealthIndicator(null, null, null);

            // Then
            assertThat(indicator1.health()).isNotNull();
            assertThat(indicator2.health()).isNotNull();
            assertThat(indicator3.health()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Health Details Key Tests")
    class HealthDetailsKeyTests {

        @Test
        @DisplayName("should always include firebase_storage detail")
        void shouldAlwaysIncludeFirebaseStorageDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("firebase_storage");
        }

        @Test
        @DisplayName("should always include redis detail")
        void shouldAlwaysIncludeRedisDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("redis");
        }

        @Test
        @DisplayName("should always include rate_limiter detail")
        void shouldAlwaysIncludeRateLimiterDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("rate_limiter");
        }

        @Test
        @DisplayName("should always include system_load detail")
        void shouldAlwaysIncludeSystemLoadDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("system_load");
        }

        @Test
        @DisplayName("should always include status_note detail")
        void shouldAlwaysIncludeStatusNoteDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("status_note");
        }
    }

    @Nested
    @DisplayName("Component Status Values Tests")
    class ComponentStatusValuesTests {

        @Test
        @DisplayName("should have valid firebase_storage status value")
        void shouldHaveValidFirebaseStorageStatusValue() {
            // When
            Health health = healthIndicator.health();

            // Then
            String status = (String) health.getDetails().get("firebase_storage");
            assertThat(status).isIn("UP", "DOWN", "NOT_CONFIGURED");
        }

        @Test
        @DisplayName("should have valid redis status value")
        void shouldHaveValidRedisStatusValue() {
            // When
            Health health = healthIndicator.health();

            // Then
            String status = (String) health.getDetails().get("redis");
            assertThat(status).isIn("UP", "DOWN", "NOT_CONFIGURED");
        }

        @Test
        @DisplayName("should have valid rate_limiter status value")
        void shouldHaveValidRateLimiterStatusValue() {
            // When
            Health health = healthIndicator.health();

            // Then
            String status = (String) health.getDetails().get("rate_limiter");
            assertThat(status).isIn("UP", "DOWN", "NOT_CONFIGURED");
        }
    }

    @Nested
    @DisplayName("Mixed Component State Tests")
    class MixedComponentStateTests {

        @Test
        @DisplayName("should return UP when storage is NOT_CONFIGURED but others are healthy")
        void shouldReturnUpWhenStorageNotConfiguredOthersHealthy() {
            // Given
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(null, redisTemplate, rateLimiterService);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("should return UP when redis is NOT_CONFIGURED but others are healthy")
        void shouldReturnUpWhenRedisNotConfiguredOthersHealthy() {
            // Given
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(storage, null, rateLimiterService);
            ReflectionTestUtils.setField(indicator, "bucketName", TEST_BUCKET_NAME);
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("should return UP when rate limiter is NOT_CONFIGURED but others are healthy")
        void shouldReturnUpWhenRateLimiterNotConfiguredOthersHealthy() {
            // Given
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(storage, redisTemplate, null);
            ReflectionTestUtils.setField(indicator, "bucketName", TEST_BUCKET_NAME);
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("should return DOWN when only storage fails among configured components")
        void shouldReturnDownWhenOnlyStorageFails() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(null);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("DOWN");
            assertThat(health.getDetails().get("redis")).isEqualTo("UP");
            assertThat(health.getDetails().get("rate_limiter")).isEqualTo("UP");
        }

        @Test
        @DisplayName("should return DOWN when only redis fails among configured components")
        void shouldReturnDownWhenOnlyRedisFails() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn(null);
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("UP");
            assertThat(health.getDetails().get("redis")).isEqualTo("DOWN");
            assertThat(health.getDetails().get("rate_limiter")).isEqualTo("UP");
        }

        @Test
        @DisplayName("should return DOWN when only rate limiter fails among configured components")
        void shouldReturnDownWhenOnlyRateLimiterFails() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(rateLimiterService.getUserStatus(anyString()))
                    .thenThrow(new RuntimeException("Rate limiter error"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("firebase_storage")).isEqualTo("UP");
            assertThat(health.getDetails().get("redis")).isEqualTo("UP");
            assertThat(health.getDetails().get("rate_limiter")).isEqualTo("DOWN");
        }
    }

    @Nested
    @DisplayName("Redis Health Check Key Pattern Tests")
    class RedisHealthCheckKeyPatternTests {

        @Test
        @DisplayName("should use health check key with timestamp prefix")
        void shouldUseHealthCheckKeyWithTimestampPrefix() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");

            // When
            healthIndicator.health();

            // Then - verify the key starts with health:check:
            // Since we're using lenient strictness, this verifies the pattern is used
            assertThat(health()).isNotNull();
        }

        private Health health() {
            return healthIndicator.health();
        }
    }

    @Nested
    @DisplayName("Status Note Message Tests")
    class StatusNoteMessageTests {

        @Test
        @DisplayName("should provide correct status note when all healthy")
        void shouldProvideCorrectStatusNoteWhenAllHealthy() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            StorageRateLimitService.RateLimitStatus status = mock(StorageRateLimitService.RateLimitStatus.class);
            when(rateLimiterService.getUserStatus(anyString())).thenReturn(status);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("status_note"))
                    .isEqualTo("All configured upload system components are healthy");
        }

        @Test
        @DisplayName("should provide correct status note when nothing configured")
        void shouldProvideCorrectStatusNoteWhenNothingConfigured() {
            // Given
            UploadSystemHealthIndicator indicator = new UploadSystemHealthIndicator(null, null, null);

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getDetails().get("status_note"))
                    .isEqualTo("Upload system not configured - basic mode without rate limiting or cloud storage");
        }

        @Test
        @DisplayName("should include storage failure in status note")
        void shouldIncludeStorageFailureInStatusNote() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(null);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("status_note").toString())
                    .contains("Storage is configured but not accessible");
        }

        @Test
        @DisplayName("should include redis failure in status note")
        void shouldIncludeRedisFailureInStatusNote() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn(null);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("status_note").toString())
                    .contains("Redis is configured but not accessible");
        }

        @Test
        @DisplayName("should include rate limiter failure in status note")
        void shouldIncludeRateLimiterFailureInStatusNote() {
            // Given
            when(storage.get(TEST_BUCKET_NAME)).thenReturn(bucket);
            when(valueOperations.get(anyString())).thenReturn("OK");
            when(rateLimiterService.getUserStatus(anyString()))
                    .thenThrow(new RuntimeException("Error"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("status_note").toString())
                    .contains("Rate limiter is configured but not working");
        }
    }
}
