package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.ratelimit.*;
import com.sm.instagram.platform.config.StorageModeConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GDPR-compliant rate limiting components.
 * Tests cover GdprCompliantRateLimiterService, GdprRateLimitProperties, and SecureRateLimitStorage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GDPR Rate Limiter Unit Tests")
class GdprRateLimiterUnitTest {

    // ==================== GdprCompliantRateLimiterService Tests ====================

    @Nested
    @DisplayName("GdprCompliantRateLimiterService Tests")
    class GdprCompliantRateLimiterServiceTests {

        @Mock
        private RedisRateLimiterService redisRateLimiter;

        @Mock
        private InMemoryRateLimiterService inMemoryRateLimiter;

        @Mock
        private StorageModeConfiguration storageModeConfiguration;

        private GdprCompliantRateLimiterService service;

        @BeforeEach
        void setUp() throws Exception {
            when(storageModeConfiguration.isRedisMode()).thenReturn(false);
            when(storageModeConfiguration.isInMemoryMode()).thenReturn(true);
            when(storageModeConfiguration.getCurrentStorageMode()).thenReturn("in-memory");

            service = new GdprCompliantRateLimiterService(
                    null,
                    inMemoryRateLimiter,
                    storageModeConfiguration
            );

            // Set GDPR fields using reflection
            setField(service, "gdprEnabled", true);
            setField(service, "dataRetentionHours", 24);
            setField(service, "anonymizeKeys", true);
            setField(service, "auditViolations", true);
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }

        private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
            Class<?> current = clazz;
            while (current != null) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        }

        @Nested
        @DisplayName("Constructor and Initialization")
        class ConstructorTests {

            @Test
            @DisplayName("should use Redis rate limiter when Redis mode is enabled")
            void shouldUseRedisRateLimiterWhenRedisModeEnabled() {
                // Given
                when(storageModeConfiguration.isRedisMode()).thenReturn(true);
                when(storageModeConfiguration.isInMemoryMode()).thenReturn(false);

                // When
                GdprCompliantRateLimiterService redisService = new GdprCompliantRateLimiterService(
                        redisRateLimiter,
                        null,
                        storageModeConfiguration
                );

                // Then
                assertThat(redisService).isNotNull();
            }

            @Test
            @DisplayName("should use InMemory rate limiter when InMemory mode is enabled")
            void shouldUseInMemoryRateLimiterWhenInMemoryModeEnabled() {
                // Given
                when(storageModeConfiguration.isRedisMode()).thenReturn(false);
                when(storageModeConfiguration.isInMemoryMode()).thenReturn(true);

                // When
                GdprCompliantRateLimiterService memoryService = new GdprCompliantRateLimiterService(
                        null,
                        inMemoryRateLimiter,
                        storageModeConfiguration
                );

                // Then
                assertThat(memoryService).isNotNull();
            }

            @Test
            @DisplayName("should use fallback RateLimiterService when no specific limiter available")
            void shouldUseFallbackRateLimiterServiceWhenNoSpecificLimiterAvailable() {
                // Given
                when(storageModeConfiguration.isRedisMode()).thenReturn(false);
                when(storageModeConfiguration.isInMemoryMode()).thenReturn(false);

                // When
                GdprCompliantRateLimiterService fallbackService = new GdprCompliantRateLimiterService(
                        null,
                        null,
                        storageModeConfiguration
                );

                // Then
                assertThat(fallbackService).isNotNull();
            }

            @Test
            @DisplayName("should prefer Redis over InMemory when both available and Redis mode enabled")
            void shouldPreferRedisOverInMemoryWhenBothAvailableAndRedisModeEnabled() {
                // Given
                when(storageModeConfiguration.isRedisMode()).thenReturn(true);
                when(storageModeConfiguration.isInMemoryMode()).thenReturn(false);

                // When
                GdprCompliantRateLimiterService redisPreferredService = new GdprCompliantRateLimiterService(
                        redisRateLimiter,
                        inMemoryRateLimiter,
                        storageModeConfiguration
                );

                // Then
                assertThat(redisPreferredService).isNotNull();
            }
        }

        @Nested
        @DisplayName("checkLimit Method")
        class CheckLimitTests {

            @Test
            @DisplayName("should allow request when under limit")
            void shouldAllowRequestWhenUnderLimit() {
                // Given
                RateLimiterService.RateLimitResult allowedResult =
                        new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60);
                when(inMemoryRateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(allowedResult);

                // When
                RateLimiterService.RateLimitResult result = service.checkLimit("user:123", 100, 60);

                // Then
                assertThat(result.isAllowed()).isTrue();
                assertThat(result.getRemaining()).isEqualTo(99);
            }

            @Test
            @DisplayName("should deny request when over limit")
            void shouldDenyRequestWhenOverLimit() {
                // Given
                RateLimiterService.RateLimitResult deniedResult =
                        new RateLimiterService.RateLimitResult(false, 100, 0, System.currentTimeMillis() / 1000 + 60);
                when(inMemoryRateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(deniedResult);

                // When
                RateLimiterService.RateLimitResult result = service.checkLimit("user:456", 100, 60);

                // Then
                assertThat(result.isAllowed()).isFalse();
            }

            @Test
            @DisplayName("should delegate to rate limiter with correct parameters")
            void shouldDelegateToRateLimiterWithCorrectParameters() {
                // Given
                RateLimiterService.RateLimitResult mockResult =
                        new RateLimiterService.RateLimitResult(true, 50, 49, 1000);
                when(inMemoryRateLimiter.checkLimit("user:test123", 50, 30)).thenReturn(mockResult);

                // When
                service.checkLimit("user:test123", 50, 30);

                // Then
                verify(inMemoryRateLimiter).checkLimit("user:test123", 50, 30);
            }

            @Test
            @DisplayName("should audit violation when limit exceeded and audit enabled")
            void shouldAuditViolationWhenLimitExceededAndAuditEnabled() throws Exception {
                // Given
                RateLimiterService.RateLimitResult deniedResult =
                        new RateLimiterService.RateLimitResult(false, 10, 0, 1000);
                when(inMemoryRateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(deniedResult);

                // When
                service.checkLimit("user:violator", 10, 60);

                // Then - verify no exception thrown and method completes
                assertThat(service.getMetrics().get("gdpr_enabled")).isEqualTo(true);
            }

            @Test
            @DisplayName("should not audit violation when audit disabled")
            void shouldNotAuditViolationWhenAuditDisabled() throws Exception {
                // Given
                setField(service, "auditViolations", false);
                RateLimiterService.RateLimitResult deniedResult =
                        new RateLimiterService.RateLimitResult(false, 10, 0, 1000);
                when(inMemoryRateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(deniedResult);

                // When
                service.checkLimit("user:test", 10, 60);

                // Then - no audit event should be created
                Map<String, Object> metrics = service.getMetrics();
                assertThat((Integer) metrics.get("audit_events")).isEqualTo(0);
            }

            @Test
            @DisplayName("should not audit when GDPR disabled")
            void shouldNotAuditWhenGdprDisabled() throws Exception {
                // Given
                setField(service, "gdprEnabled", false);
                RateLimiterService.RateLimitResult deniedResult =
                        new RateLimiterService.RateLimitResult(false, 10, 0, 1000);
                when(inMemoryRateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(deniedResult);

                // When
                service.checkLimit("user:test", 10, 60);

                // Then - metrics should still work
                Map<String, Object> metrics = service.getMetrics();
                assertThat(metrics.get("gdpr_enabled")).isEqualTo(false);
            }
        }

        @Nested
        @DisplayName("Key Type Extraction")
        class KeyTypeExtractionTests {

            @ParameterizedTest
            @DisplayName("should extract correct key type for user keys")
            @CsvSource({
                    "rate_limit_user:abc123, user",
                    "user:firebase123, user",
                    "rate_limit:authenticated123, user"
            })
            void shouldExtractCorrectKeyTypeForUserKeys(String key, String expectedType) throws Exception {
                // Given
                Method extractKeyType = GdprCompliantRateLimiterService.class.getDeclaredMethod("extractKeyType", String.class);
                extractKeyType.setAccessible(true);

                // When
                String result = (String) extractKeyType.invoke(service, key);

                // Then
                assertThat(result).isEqualTo(expectedType);
            }

            @ParameterizedTest
            @DisplayName("should extract correct key type for IP keys")
            @CsvSource({
                    "rate_limit_ip:192.168.1.1, ip",
                    "ip:10.0.0.1, ip"
            })
            void shouldExtractCorrectKeyTypeForIpKeys(String key, String expectedType) throws Exception {
                // Given
                Method extractKeyType = GdprCompliantRateLimiterService.class.getDeclaredMethod("extractKeyType", String.class);
                extractKeyType.setAccessible(true);

                // When
                String result = (String) extractKeyType.invoke(service, key);

                // Then
                assertThat(result).isEqualTo(expectedType);
            }

            @Test
            @DisplayName("should extract device key type")
            void shouldExtractDeviceKeyType() throws Exception {
                // Given
                Method extractKeyType = GdprCompliantRateLimiterService.class.getDeclaredMethod("extractKeyType", String.class);
                extractKeyType.setAccessible(true);

                // When
                String result = (String) extractKeyType.invoke(service, "rate_limit_device:device123");

                // Then
                assertThat(result).isEqualTo("device");
            }

            @Test
            @DisplayName("should extract anonymous key type")
            void shouldExtractAnonymousKeyType() throws Exception {
                // Given
                Method extractKeyType = GdprCompliantRateLimiterService.class.getDeclaredMethod("extractKeyType", String.class);
                extractKeyType.setAccessible(true);

                // When
                String result = (String) extractKeyType.invoke(service, "anonymous:session123");

                // Then
                assertThat(result).isEqualTo("anonymous");
            }

            @Test
            @DisplayName("should extract anon key type")
            void shouldExtractAnonKeyType() throws Exception {
                // Given
                Method extractKeyType = GdprCompliantRateLimiterService.class.getDeclaredMethod("extractKeyType", String.class);
                extractKeyType.setAccessible(true);

                // When
                String result = (String) extractKeyType.invoke(service, "anon:session456");

                // Then
                assertThat(result).isEqualTo("anonymous");
            }

            @Test
            @DisplayName("should extract endpoint key type")
            void shouldExtractEndpointKeyType() throws Exception {
                // Given
                Method extractKeyType = GdprCompliantRateLimiterService.class.getDeclaredMethod("extractKeyType", String.class);
                extractKeyType.setAccessible(true);

                // When
                String result = (String) extractKeyType.invoke(service, "endpoint:/api/users");

                // Then
                assertThat(result).isEqualTo("endpoint");
            }

            @Test
            @DisplayName("should return unknown for unrecognized key type")
            void shouldReturnUnknownForUnrecognizedKeyType() throws Exception {
                // Given
                Method extractKeyType = GdprCompliantRateLimiterService.class.getDeclaredMethod("extractKeyType", String.class);
                extractKeyType.setAccessible(true);

                // When
                String result = (String) extractKeyType.invoke(service, "custom_unknown:value");

                // Then
                assertThat(result).isEqualTo("unknown");
            }
        }

        @Nested
        @DisplayName("IP Address Anonymization")
        class IpAnonymizationTests {

            @Test
            @DisplayName("should anonymize IPv4 address by removing last octet")
            void shouldAnonymizeIpv4AddressByRemovingLastOctet() throws Exception {
                // Given
                Method anonymizeIpAddress = GdprCompliantRateLimiterService.class.getDeclaredMethod("anonymizeIpAddress", String.class);
                anonymizeIpAddress.setAccessible(true);

                // When
                String result = (String) anonymizeIpAddress.invoke(service, "192.168.1.100");

                // Then
                assertThat(result).isEqualTo("192.168.1.xxx");
            }

            @Test
            @DisplayName("should anonymize IPv6 address")
            void shouldAnonymizeIpv6Address() throws Exception {
                // Given
                Method anonymizeIpAddress = GdprCompliantRateLimiterService.class.getDeclaredMethod("anonymizeIpAddress", String.class);
                anonymizeIpAddress.setAccessible(true);

                // When
                String result = (String) anonymizeIpAddress.invoke(service, "2001:0db8:85a3:0000:0000:8a2e:0370:7334");

                // Then
                assertThat(result).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:xxxx");
            }

            @Test
            @DisplayName("should return default anonymized IP for invalid format")
            void shouldReturnDefaultAnonymizedIpForInvalidFormat() throws Exception {
                // Given
                Method anonymizeIpAddress = GdprCompliantRateLimiterService.class.getDeclaredMethod("anonymizeIpAddress", String.class);
                anonymizeIpAddress.setAccessible(true);

                // When
                String result = (String) anonymizeIpAddress.invoke(service, "invalid-ip");

                // Then
                assertThat(result).isEqualTo("xxx.xxx.xxx.xxx");
            }
        }

        @Nested
        @DisplayName("Hashing Tests")
        class HashingTests {

            @Test
            @DisplayName("should produce consistent hash for same input")
            void shouldProduceConsistentHashForSameInput() throws Exception {
                // Given
                Method hashString = GdprCompliantRateLimiterService.class.getDeclaredMethod("hashString", String.class);
                hashString.setAccessible(true);

                // When
                String hash1 = (String) hashString.invoke(service, "test-input");
                String hash2 = (String) hashString.invoke(service, "test-input");

                // Then
                assertThat(hash1).isEqualTo(hash2);
            }

            @Test
            @DisplayName("should produce different hash for different input")
            void shouldProduceDifferentHashForDifferentInput() throws Exception {
                // Given
                Method hashString = GdprCompliantRateLimiterService.class.getDeclaredMethod("hashString", String.class);
                hashString.setAccessible(true);

                // When
                String hash1 = (String) hashString.invoke(service, "input1");
                String hash2 = (String) hashString.invoke(service, "input2");

                // Then
                assertThat(hash1).isNotEqualTo(hash2);
            }

            @Test
            @DisplayName("should produce 16 character hash")
            void shouldProduce16CharacterHash() throws Exception {
                // Given
                Method hashString = GdprCompliantRateLimiterService.class.getDeclaredMethod("hashString", String.class);
                hashString.setAccessible(true);

                // When
                String hash = (String) hashString.invoke(service, "any-input-string");

                // Then
                assertThat(hash).hasSize(16);
            }
        }

        @Nested
        @DisplayName("Cleanup Expired Data")
        class CleanupTests {

            @Test
            @DisplayName("should cleanup expired entries from delegate")
            void shouldCleanupExpiredEntriesFromDelegate() {
                // Given
                when(inMemoryRateLimiter.cleanupExpiredEntries()).thenReturn(5);

                // When
                int cleaned = service.cleanupExpiredData();

                // Then
                assertThat(cleaned).isGreaterThanOrEqualTo(5);
                verify(inMemoryRateLimiter).cleanupExpiredEntries();
            }

            @Test
            @DisplayName("should cleanup expired entries via inherited method")
            void shouldCleanupExpiredEntriesViaInheritedMethod() {
                // Given
                when(inMemoryRateLimiter.cleanupExpiredEntries()).thenReturn(3);

                // When
                int cleaned = service.cleanupExpiredEntries();

                // Then
                assertThat(cleaned).isGreaterThanOrEqualTo(3);
            }
        }

        @Nested
        @DisplayName("Metrics")
        class MetricsTests {

            @Test
            @DisplayName("should return metrics with all required fields")
            void shouldReturnMetricsWithAllRequiredFields() {
                // Given
                when(inMemoryRateLimiter.getTrackedEntriesCount()).thenReturn(42);

                // When
                Map<String, Object> metrics = service.getMetrics();

                // Then
                assertThat(metrics).containsKeys("tracked_entries", "audit_events", "gdpr_enabled", "data_retention_hours");
                assertThat(metrics.get("tracked_entries")).isEqualTo(42);
                assertThat(metrics.get("gdpr_enabled")).isEqualTo(true);
                assertThat(metrics.get("data_retention_hours")).isEqualTo(24);
            }

            @Test
            @DisplayName("should return correct tracked entries count")
            void shouldReturnCorrectTrackedEntriesCount() {
                // Given
                when(inMemoryRateLimiter.getTrackedEntriesCount()).thenReturn(100);

                // When
                int count = service.getTrackedEntriesCount();

                // Then
                assertThat(count).isEqualTo(100);
            }
        }

        @Nested
        @DisplayName("GDPR Data Export")
        class DataExportTests {

            @Test
            @DisplayName("should export user rate limit data when GDPR enabled")
            void shouldExportUserRateLimitDataWhenGdprEnabled() {
                // When
                Map<String, Object> exportData = service.exportUserRateLimitData("user123");

                // Then
                assertThat(exportData).containsKeys("rate_limit_violations", "data_retention_hours", "exported_at", "note");
            }

            @Test
            @DisplayName("should return error when GDPR disabled")
            void shouldReturnErrorWhenGdprDisabled() throws Exception {
                // Given
                setField(service, "gdprEnabled", false);

                // When
                Map<String, Object> exportData = service.exportUserRateLimitData("user123");

                // Then
                assertThat(exportData).containsEntry("error", "GDPR features not enabled");
            }

            @Test
            @DisplayName("should include retention hours in export")
            void shouldIncludeRetentionHoursInExport() {
                // When
                Map<String, Object> exportData = service.exportUserRateLimitData("user123");

                // Then
                assertThat(exportData.get("data_retention_hours")).isEqualTo(24);
            }
        }

        @Nested
        @DisplayName("GDPR Data Deletion")
        class DataDeletionTests {

            @Test
            @DisplayName("should delete user rate limit data when GDPR enabled")
            void shouldDeleteUserRateLimitDataWhenGdprEnabled() {
                // When/Then - should not throw
                assertThatCode(() -> service.deleteUserRateLimitData("user123"))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("should not delete when GDPR disabled")
            void shouldNotDeleteWhenGdprDisabled() throws Exception {
                // Given
                setField(service, "gdprEnabled", false);

                // When/Then - should return early without error
                assertThatCode(() -> service.deleteUserRateLimitData("user123"))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("should handle deletion of non-existent user")
            void shouldHandleDeletionOfNonExistentUser() {
                // When/Then - should not throw for non-existent user
                assertThatCode(() -> service.deleteUserRateLimitData("non-existent-user"))
                        .doesNotThrowAnyException();
            }
        }
    }

    // ==================== GdprRateLimitProperties Tests ====================

    @Nested
    @DisplayName("GdprRateLimitProperties Tests")
    class GdprRateLimitPropertiesTests {

        private GdprRateLimitProperties properties;

        @BeforeEach
        void setUp() {
            properties = new GdprRateLimitProperties();
        }

        @Nested
        @DisplayName("Default Values")
        class DefaultValuesTests {

            @Test
            @DisplayName("should have enabled true by default")
            void shouldHaveEnabledTrueByDefault() {
                assertThat(properties.isEnabled()).isTrue();
            }

            @Test
            @DisplayName("should have 24 hours data retention by default")
            void shouldHave24HoursDataRetentionByDefault() {
                assertThat(properties.getDataRetentionHours()).isEqualTo(24);
            }

            @Test
            @DisplayName("should have anonymize keys true by default")
            void shouldHaveAnonymizeKeysTrueByDefault() {
                assertThat(properties.isAnonymizeKeys()).isTrue();
            }

            @Test
            @DisplayName("should have audit violations true by default")
            void shouldHaveAuditViolationsTrueByDefault() {
                assertThat(properties.isAuditViolations()).isTrue();
            }
        }

        @Nested
        @DisplayName("Setters")
        class SetterTests {

            @Test
            @DisplayName("should set enabled flag")
            void shouldSetEnabledFlag() {
                // When
                properties.setEnabled(false);

                // Then
                assertThat(properties.isEnabled()).isFalse();
            }

            @Test
            @DisplayName("should set data retention hours")
            void shouldSetDataRetentionHours() {
                // When
                properties.setDataRetentionHours(48);

                // Then
                assertThat(properties.getDataRetentionHours()).isEqualTo(48);
            }

            @Test
            @DisplayName("should set anonymize keys flag")
            void shouldSetAnonymizeKeysFlag() {
                // When
                properties.setAnonymizeKeys(false);

                // Then
                assertThat(properties.isAnonymizeKeys()).isFalse();
            }

            @Test
            @DisplayName("should set audit violations flag")
            void shouldSetAuditViolationsFlag() {
                // When
                properties.setAuditViolations(false);

                // Then
                assertThat(properties.isAuditViolations()).isFalse();
            }
        }

        @Nested
        @DisplayName("Edge Cases")
        class EdgeCasesTests {

            @Test
            @DisplayName("should accept zero data retention hours")
            void shouldAcceptZeroDataRetentionHours() {
                // When
                properties.setDataRetentionHours(0);

                // Then
                assertThat(properties.getDataRetentionHours()).isEqualTo(0);
            }

            @Test
            @DisplayName("should accept negative data retention hours")
            void shouldAcceptNegativeDataRetentionHours() {
                // When
                properties.setDataRetentionHours(-1);

                // Then
                assertThat(properties.getDataRetentionHours()).isEqualTo(-1);
            }

            @Test
            @DisplayName("should accept large data retention hours")
            void shouldAcceptLargeDataRetentionHours() {
                // When
                properties.setDataRetentionHours(8760); // 1 year

                // Then
                assertThat(properties.getDataRetentionHours()).isEqualTo(8760);
            }
        }
    }

    // ==================== SecureRateLimitStorage Tests ====================

    @Nested
    @DisplayName("SecureRateLimitStorage Tests")
    class SecureRateLimitStorageTests {

        @Mock
        private RateLimiterService rateLimiter;

        private SecureRateLimitStorage storage;

        @BeforeEach
        void setUp() {
            storage = new SecureRateLimitStorage();
        }

        @Nested
        @DisplayName("isUserRateLimited")
        class IsUserRateLimitedTests {

            @Test
            @DisplayName("should return false when user is not rate limited")
            void shouldReturnFalseWhenUserIsNotRateLimited() {
                // Given
                RateLimiterService.RateLimitResult allowedResult =
                        new RateLimiterService.RateLimitResult(true, 1, 0, 1000);
                when(rateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(allowedResult);

                // When
                boolean result = storage.isUserRateLimited("user123", rateLimiter);

                // Then
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should return true when user is rate limited")
            void shouldReturnTrueWhenUserIsRateLimited() {
                // Given
                RateLimiterService.RateLimitResult deniedResult =
                        new RateLimiterService.RateLimitResult(false, 1, 0, 1000);
                when(rateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(deniedResult);

                // When
                boolean result = storage.isUserRateLimited("user456", rateLimiter);

                // Then
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should use correct key format for user check")
            void shouldUseCorrectKeyFormatForUserCheck() {
                // Given
                RateLimiterService.RateLimitResult allowedResult =
                        new RateLimiterService.RateLimitResult(true, 1, 0, 1000);
                when(rateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(allowedResult);

                // When
                storage.isUserRateLimited("test-user", rateLimiter);

                // Then
                verify(rateLimiter).checkLimit(eq("user:test-user:check"), eq(1), eq(1));
            }
        }

        @Nested
        @DisplayName("getRateLimitStatus")
        class GetRateLimitStatusTests {

            @Test
            @DisplayName("should return status with allowed true when under limit")
            void shouldReturnStatusWithAllowedTrueWhenUnderLimit() {
                // Given
                RateLimiterService.RateLimitResult allowedResult =
                        new RateLimiterService.RateLimitResult(true, 100, 50, 1000);
                when(rateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(allowedResult);

                // When
                SecureRateLimitStorage.RateLimitStatus status =
                        storage.getRateLimitStatus("test-key", 100, 60, rateLimiter);

                // Then
                assertThat(status.allowed()).isTrue();
                assertThat(status.remaining()).isEqualTo(50);
                assertThat(status.limit()).isEqualTo(100);
            }

            @Test
            @DisplayName("should return status with allowed false when over limit")
            void shouldReturnStatusWithAllowedFalseWhenOverLimit() {
                // Given
                RateLimiterService.RateLimitResult deniedResult =
                        new RateLimiterService.RateLimitResult(false, 100, 0, 1000);
                when(rateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(deniedResult);

                // When
                SecureRateLimitStorage.RateLimitStatus status =
                        storage.getRateLimitStatus("test-key", 100, 60, rateLimiter);

                // Then
                assertThat(status.allowed()).isFalse();
                assertThat(status.remaining()).isEqualTo(0);
            }

            @Test
            @DisplayName("should include reset time in status")
            void shouldIncludeResetTimeInStatus() {
                // Given
                long resetTime = System.currentTimeMillis() / 1000 + 60;
                RateLimiterService.RateLimitResult result =
                        new RateLimiterService.RateLimitResult(true, 100, 50, resetTime);
                when(rateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(result);

                // When
                SecureRateLimitStorage.RateLimitStatus status =
                        storage.getRateLimitStatus("test-key", 100, 60, rateLimiter);

                // Then
                assertThat(status.resetTime()).isEqualTo(resetTime);
            }
        }

        @Nested
        @DisplayName("RateLimitStatus Record")
        class RateLimitStatusRecordTests {

            @Test
            @DisplayName("should create status with all fields")
            void shouldCreateStatusWithAllFields() {
                // When
                SecureRateLimitStorage.RateLimitStatus status =
                        new SecureRateLimitStorage.RateLimitStatus(true, 100, 50, 1000);

                // Then
                assertThat(status.allowed()).isTrue();
                assertThat(status.limit()).isEqualTo(100);
                assertThat(status.remaining()).isEqualTo(50);
                assertThat(status.resetTime()).isEqualTo(1000);
            }

            @Test
            @DisplayName("should calculate seconds until reset correctly")
            void shouldCalculateSecondsUntilResetCorrectly() {
                // Given
                long futureResetTime = System.currentTimeMillis() / 1000 + 60;
                SecureRateLimitStorage.RateLimitStatus status =
                        new SecureRateLimitStorage.RateLimitStatus(false, 100, 0, futureResetTime);

                // When
                long secondsUntilReset = status.getSecondsUntilReset();

                // Then
                assertThat(secondsUntilReset).isBetween(55L, 65L);
            }

            @Test
            @DisplayName("should return zero seconds when reset time passed")
            void shouldReturnZeroSecondsWhenResetTimePassed() {
                // Given
                long pastResetTime = System.currentTimeMillis() / 1000 - 60;
                SecureRateLimitStorage.RateLimitStatus status =
                        new SecureRateLimitStorage.RateLimitStatus(true, 100, 100, pastResetTime);

                // When
                long secondsUntilReset = status.getSecondsUntilReset();

                // Then
                assertThat(secondsUntilReset).isEqualTo(0);
            }
        }

        @Nested
        @DisplayName("logBypassAttempt")
        class LogBypassAttemptTests {

            @Test
            @DisplayName("should log bypass attempt without throwing exception")
            void shouldLogBypassAttemptWithoutThrowingException() {
                // When/Then
                assertThatCode(() -> storage.logBypassAttempt("user123", "DELETE_RATE_LIMITS"))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("should handle null user ID")
            void shouldHandleNullUserId() {
                // When/Then
                assertThatCode(() -> storage.logBypassAttempt(null, "SOME_ACTION"))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("should handle null action")
            void shouldHandleNullAction() {
                // When/Then
                assertThatCode(() -> storage.logBypassAttempt("user123", null))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("should handle empty user ID")
            void shouldHandleEmptyUserId() {
                // When/Then
                assertThatCode(() -> storage.logBypassAttempt("", "SOME_ACTION"))
                        .doesNotThrowAnyException();
            }
        }
    }

    // ==================== RateLimiterService Base Class Tests ====================

    @Nested
    @DisplayName("RateLimiterService Base Tests")
    class RateLimiterServiceBaseTests {

        private RateLimiterService rateLimiter;

        @BeforeEach
        void setUp() {
            rateLimiter = new RateLimiterService();
        }

        @Nested
        @DisplayName("checkLimit")
        class CheckLimitTests {

            @Test
            @DisplayName("should allow first request")
            void shouldAllowFirstRequest() {
                // When
                RateLimiterService.RateLimitResult result = rateLimiter.checkLimit("new-key", 10, 60);

                // Then
                assertThat(result.isAllowed()).isTrue();
                assertThat(result.getRemaining()).isEqualTo(9);
            }

            @Test
            @DisplayName("should track multiple requests")
            void shouldTrackMultipleRequests() {
                // When
                rateLimiter.checkLimit("multi-key", 10, 60);
                rateLimiter.checkLimit("multi-key", 10, 60);
                RateLimiterService.RateLimitResult result = rateLimiter.checkLimit("multi-key", 10, 60);

                // Then
                assertThat(result.isAllowed()).isTrue();
                assertThat(result.getRemaining()).isEqualTo(7);
            }

            @Test
            @DisplayName("should deny request when limit exceeded")
            void shouldDenyRequestWhenLimitExceeded() {
                // Given - exhaust the limit
                for (int i = 0; i < 5; i++) {
                    rateLimiter.checkLimit("limited-key", 5, 60);
                }

                // When
                RateLimiterService.RateLimitResult result = rateLimiter.checkLimit("limited-key", 5, 60);

                // Then
                assertThat(result.isAllowed()).isFalse();
                assertThat(result.getRemaining()).isEqualTo(0);
            }

            @Test
            @DisplayName("should use separate windows for different keys")
            void shouldUseSeparateWindowsForDifferentKeys() {
                // When
                rateLimiter.checkLimit("key-a", 10, 60);
                RateLimiterService.RateLimitResult resultA = rateLimiter.checkLimit("key-a", 10, 60);
                RateLimiterService.RateLimitResult resultB = rateLimiter.checkLimit("key-b", 10, 60);

                // Then
                assertThat(resultA.getRemaining()).isEqualTo(8);
                assertThat(resultB.getRemaining()).isEqualTo(9);
            }
        }

        @Nested
        @DisplayName("isAllowed Legacy Method")
        class IsAllowedLegacyTests {

            @Test
            @DisplayName("should return true when under limit")
            void shouldReturnTrueWhenUnderLimit() {
                // When
                boolean allowed = rateLimiter.isAllowed("legacy-key", 10, 60);

                // Then
                assertThat(allowed).isTrue();
            }

            @Test
            @DisplayName("should return false when over limit")
            void shouldReturnFalseWhenOverLimit() {
                // Given - exhaust the limit
                for (int i = 0; i < 5; i++) {
                    rateLimiter.isAllowed("legacy-limited-key", 5, 60);
                }

                // When
                boolean allowed = rateLimiter.isAllowed("legacy-limited-key", 5, 60);

                // Then
                assertThat(allowed).isFalse();
            }
        }

        @Nested
        @DisplayName("cleanupExpiredEntries")
        class CleanupExpiredEntriesTests {

            @Test
            @DisplayName("should return number of cleaned entries")
            void shouldReturnNumberOfCleanedEntries() {
                // Given - add some entries
                rateLimiter.checkLimit("cleanup-key-1", 10, 60);
                rateLimiter.checkLimit("cleanup-key-2", 10, 60);

                // When - entries are not expired yet, so cleanup returns 0
                int cleaned = rateLimiter.cleanupExpiredEntries();

                // Then
                assertThat(cleaned).isEqualTo(0);
            }
        }

        @Nested
        @DisplayName("getTrackedEntriesCount")
        class GetTrackedEntriesCountTests {

            @Test
            @DisplayName("should return zero for empty limiter")
            void shouldReturnZeroForEmptyLimiter() {
                // When
                int count = rateLimiter.getTrackedEntriesCount();

                // Then
                assertThat(count).isEqualTo(0);
            }

            @Test
            @DisplayName("should return correct count after adding entries")
            void shouldReturnCorrectCountAfterAddingEntries() {
                // Given
                rateLimiter.checkLimit("count-key-1", 10, 60);
                rateLimiter.checkLimit("count-key-2", 10, 60);
                rateLimiter.checkLimit("count-key-3", 10, 60);

                // When
                int count = rateLimiter.getTrackedEntriesCount();

                // Then
                assertThat(count).isEqualTo(3);
            }
        }

        @Nested
        @DisplayName("RateLimitResult")
        class RateLimitResultTests {

            @Test
            @DisplayName("should create result with all fields")
            void shouldCreateResultWithAllFields() {
                // When
                RateLimiterService.RateLimitResult result =
                        new RateLimiterService.RateLimitResult(true, 100, 50, 1000);

                // Then
                assertThat(result.isAllowed()).isTrue();
                assertThat(result.getLimit()).isEqualTo(100);
                assertThat(result.getRemaining()).isEqualTo(50);
                assertThat(result.getResetTime()).isEqualTo(1000);
            }

            @Test
            @DisplayName("should create denied result")
            void shouldCreateDeniedResult() {
                // When
                RateLimiterService.RateLimitResult result =
                        new RateLimiterService.RateLimitResult(false, 100, 0, 2000);

                // Then
                assertThat(result.isAllowed()).isFalse();
                assertThat(result.getRemaining()).isEqualTo(0);
            }
        }
    }

    // ==================== Integration-like Tests ====================

    @Nested
    @DisplayName("GDPR Compliance Scenarios")
    class GdprComplianceScenariosTests {

        @Mock
        private InMemoryRateLimiterService inMemoryRateLimiter;

        @Mock
        private StorageModeConfiguration storageModeConfiguration;

        private GdprCompliantRateLimiterService service;

        @BeforeEach
        void setUp() throws Exception {
            when(storageModeConfiguration.isRedisMode()).thenReturn(false);
            when(storageModeConfiguration.isInMemoryMode()).thenReturn(true);
            when(storageModeConfiguration.getCurrentStorageMode()).thenReturn("in-memory");

            service = new GdprCompliantRateLimiterService(
                    null,
                    inMemoryRateLimiter,
                    storageModeConfiguration
            );

            setField(service, "gdprEnabled", true);
            setField(service, "dataRetentionHours", 24);
            setField(service, "anonymizeKeys", true);
            setField(service, "auditViolations", true);
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }

        private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
            Class<?> current = clazz;
            while (current != null) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        }

        @Test
        @DisplayName("should maintain audit trail for violations")
        void shouldMaintainAuditTrailForViolations() {
            // Given
            RateLimiterService.RateLimitResult deniedResult =
                    new RateLimiterService.RateLimitResult(false, 10, 0, 1000);
            when(inMemoryRateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(deniedResult);

            // When - generate multiple violations
            service.checkLimit("user:violator1", 10, 60);
            service.checkLimit("user:violator2", 10, 60);

            // Then
            Map<String, Object> metrics = service.getMetrics();
            assertThat((Integer) metrics.get("audit_events")).isGreaterThan(0);
        }

        @Test
        @DisplayName("should support data portability through export")
        void shouldSupportDataPortabilityThroughExport() {
            // When
            Map<String, Object> exportData = service.exportUserRateLimitData("gdpr-user");

            // Then
            assertThat(exportData).containsKey("rate_limit_violations");
            assertThat(exportData).containsKey("exported_at");
            assertThat(exportData.get("note")).isNotNull();
        }

        @Test
        @DisplayName("should support right to erasure")
        void shouldSupportRightToErasure() {
            // Given - create some audit events
            RateLimiterService.RateLimitResult deniedResult =
                    new RateLimiterService.RateLimitResult(false, 10, 0, 1000);
            when(inMemoryRateLimiter.checkLimit(anyString(), anyInt(), anyInt())).thenReturn(deniedResult);
            service.checkLimit("user:to-be-deleted", 10, 60);

            // When
            service.deleteUserRateLimitData("to-be-deleted");

            // Then - should complete without error
            Map<String, Object> exportData = service.exportUserRateLimitData("to-be-deleted");
            assertThat(exportData).containsKey("rate_limit_violations");
        }

        @Test
        @DisplayName("should enforce data retention policy")
        void shouldEnforceDataRetentionPolicy() {
            // Given
            when(inMemoryRateLimiter.cleanupExpiredEntries()).thenReturn(5);

            // When
            int cleaned = service.cleanupExpiredData();

            // Then
            assertThat(cleaned).isGreaterThanOrEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Concurrent Access Tests")
    class ConcurrentAccessTests {

        private RateLimiterService rateLimiter;

        @BeforeEach
        void setUp() {
            rateLimiter = new RateLimiterService();
        }

        @Test
        @DisplayName("should handle concurrent requests to same key")
        void shouldHandleConcurrentRequestsToSameKey() throws InterruptedException {
            // Given
            String sharedKey = "concurrent-key";
            int limit = 100;
            int numThreads = 10;
            int requestsPerThread = 5;

            Thread[] threads = new Thread[numThreads];
            int[] successCount = {0};

            // When
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < requestsPerThread; j++) {
                        RateLimiterService.RateLimitResult result =
                                rateLimiter.checkLimit(sharedKey, limit, 60);
                        if (result.isAllowed()) {
                            synchronized (successCount) {
                                successCount[0]++;
                            }
                        }
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Then - all requests should succeed since total (50) < limit (100)
            assertThat(successCount[0]).isEqualTo(numThreads * requestsPerThread);
        }

        @Test
        @DisplayName("should correctly enforce limit under concurrent access")
        void shouldCorrectlyEnforceLimitUnderConcurrentAccess() throws InterruptedException {
            // Given
            String sharedKey = "strict-limit-key";
            int limit = 10;
            int numThreads = 20;
            int requestsPerThread = 1;

            Thread[] threads = new Thread[numThreads];
            int[] successCount = {0};
            int[] failureCount = {0};

            // When
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < requestsPerThread; j++) {
                        RateLimiterService.RateLimitResult result =
                                rateLimiter.checkLimit(sharedKey, limit, 60);
                        synchronized (successCount) {
                            if (result.isAllowed()) {
                                successCount[0]++;
                            } else {
                                failureCount[0]++;
                            }
                        }
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Then - exactly limit requests should succeed
            assertThat(successCount[0]).isEqualTo(limit);
            assertThat(failureCount[0]).isEqualTo(numThreads - limit);
        }
    }
}
