package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.utils.HashingUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HashingUtil.
 * These are pure unit tests - no Spring context needed.
 */
@DisplayName("HashingUtil")
class HashingUtilUnitTest {

    @Nested
    @DisplayName("hashIdentifier")
    class HashIdentifier {

        @Test
        @DisplayName("should generate consistent SHA-256 hash for same input")
        void shouldGenerateConsistentHash() {
            // Given
            String identifier = "test-firebase-uid-12345";

            // When
            String hash1 = HashingUtil.hashIdentifier(identifier);
            String hash2 = HashingUtil.hashIdentifier(identifier);

            // Then
            assertThat(hash1)
                    .isNotNull()
                    .isEqualTo(hash2)
                    .hasSize(64); // SHA-256 produces 64 hex characters
        }

        @Test
        @DisplayName("should generate different hashes for different inputs")
        void shouldGenerateDifferentHashesForDifferentInputs() {
            // Given
            String identifier1 = "user-123";
            String identifier2 = "user-456";

            // When
            String hash1 = HashingUtil.hashIdentifier(identifier1);
            String hash2 = HashingUtil.hashIdentifier(identifier2);

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should return null for null or empty input")
        void shouldReturnNullForNullOrEmptyInput(String input) {
            // When
            String result = HashingUtil.hashIdentifier(input);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should produce valid hex string")
        void shouldProduceValidHexString() {
            // Given
            String identifier = "test-identifier";

            // When
            String hash = HashingUtil.hashIdentifier(identifier);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .matches("^[a-f0-9]{64}$"); // Valid lowercase hex
        }

        @ParameterizedTest
        @ValueSource(strings = {"simple", "with spaces", "special!@#$%", "unicode-\u00e9\u00e8\u00ea"})
        @DisplayName("should handle various input formats")
        void shouldHandleVariousInputFormats(String input) {
            // When
            String hash = HashingUtil.hashIdentifier(input);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64);
        }
    }

    @Nested
    @DisplayName("generateRedisKey")
    class GenerateRedisKey {

        @Test
        @DisplayName("should generate properly formatted Redis key")
        void shouldGenerateProperlyFormattedRedisKey() {
            // Given
            String namespace = "rate_limit";
            String identifier = "user-123";

            // When
            String redisKey = HashingUtil.generateRedisKey(namespace, identifier);

            // Then
            assertThat(redisKey)
                    .isNotNull()
                    .startsWith("rate_limit:")
                    .hasSize("rate_limit:".length() + 64); // namespace + colon + 64-char hash
        }

        @Test
        @DisplayName("should return null for null identifier")
        void shouldReturnNullForNullIdentifier() {
            // When
            String result = HashingUtil.generateRedisKey("namespace", null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for empty identifier")
        void shouldReturnNullForEmptyIdentifier() {
            // When
            String result = HashingUtil.generateRedisKey("namespace", "");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should generate consistent keys for same inputs")
        void shouldGenerateConsistentKeys() {
            // Given
            String namespace = "session";
            String identifier = "firebase-uid-xyz";

            // When
            String key1 = HashingUtil.generateRedisKey(namespace, identifier);
            String key2 = HashingUtil.generateRedisKey(namespace, identifier);

            // Then
            assertThat(key1).isEqualTo(key2);
        }

        @Test
        @DisplayName("should generate different keys for different namespaces")
        void shouldGenerateDifferentKeysForDifferentNamespaces() {
            // Given
            String identifier = "same-user";

            // When
            String sessionKey = HashingUtil.generateRedisKey("session", identifier);
            String cacheKey = HashingUtil.generateRedisKey("user_cache", identifier);

            // Then
            assertThat(sessionKey).isNotEqualTo(cacheKey);
        }
    }

    @Nested
    @DisplayName("hashIpAddress")
    class HashIpAddress {

        @Test
        @DisplayName("should hash IPv4 address")
        void shouldHashIpv4Address() {
            // Given
            String ipAddress = "192.168.1.100";

            // When
            String hash = HashingUtil.hashIpAddress(ipAddress);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64);
        }

        @Test
        @DisplayName("should hash IPv6 address")
        void shouldHashIpv6Address() {
            // Given
            String ipAddress = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";

            // When
            String hash = HashingUtil.hashIpAddress(ipAddress);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64);
        }

        @Test
        @DisplayName("should return null for null IP")
        void shouldReturnNullForNullIp() {
            // When
            String result = HashingUtil.hashIpAddress(null);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("hashFirebaseUid")
    class HashFirebaseUid {

        @Test
        @DisplayName("should hash Firebase UID")
        void shouldHashFirebaseUid() {
            // Given
            String firebaseUid = "abc123XYZ789";

            // When
            String hash = HashingUtil.hashFirebaseUid(firebaseUid);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64);
        }

        @Test
        @DisplayName("should produce consistent hash for same Firebase UID")
        void shouldProduceConsistentHashForSameFirebaseUid() {
            // Given
            String firebaseUid = "firebase-user-id-12345";

            // When
            String hash1 = HashingUtil.hashFirebaseUid(firebaseUid);
            String hash2 = HashingUtil.hashFirebaseUid(firebaseUid);

            // Then
            assertThat(hash1).isEqualTo(hash2);
        }
    }
}
