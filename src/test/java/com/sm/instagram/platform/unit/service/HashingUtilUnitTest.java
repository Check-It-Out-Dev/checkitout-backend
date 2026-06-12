package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.utils.HashingUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for HashingUtil.
 * Tests all static methods for GDPR-compliant hashing functionality.
 */
@DisplayName("HashingUtil Unit Tests")
class HashingUtilUnitTest {

    // Known SHA-256 hash for verification (pre-computed)
    private static final String KNOWN_INPUT = "test-identifier";
    private static final String KNOWN_HASH = "115ae872eb1d3e23f9de03f7ab344193b21068812ee52eb37e8169e6d093c7ae";

    @Nested
    @DisplayName("hashIdentifier(String) Tests")
    class HashIdentifierTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            // When
            String result = HashingUtil.hashIdentifier(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for empty string input")
        void shouldReturnNullForEmptyStringInput() {
            // When
            String result = HashingUtil.hashIdentifier("");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return consistent hash for same input (deterministic)")
        void shouldReturnConsistentHashForSameInput() {
            // Given
            String identifier = "firebase-uid-abc123";

            // When
            String hash1 = HashingUtil.hashIdentifier(identifier);
            String hash2 = HashingUtil.hashIdentifier(identifier);
            String hash3 = HashingUtil.hashIdentifier(identifier);

            // Then
            assertThat(hash1)
                    .isEqualTo(hash2)
                    .isEqualTo(hash3);
        }

        @Test
        @DisplayName("should return 64-character hex string for valid input")
        void shouldReturn64CharHexStringForValidInput() {
            // Given
            String identifier = "valid-identifier";

            // When
            String hash = HashingUtil.hashIdentifier(identifier);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64)
                    .matches("^[a-f0-9]+$");
        }

        @Test
        @DisplayName("should produce different hashes for different inputs")
        void shouldProduceDifferentHashesForDifferentInputs() {
            // When
            String hash1 = HashingUtil.hashIdentifier("input-one");
            String hash2 = HashingUtil.hashIdentifier("input-two");

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "simple-text",
                "with spaces in between",
                "special!@#$%^&*()chars",
                "unicode-\u00e9\u00e8\u00ea\u00eb",
                "emoji-test-\uD83D\uDE00",
                "very-long-identifier-that-exceeds-typical-lengths-0123456789"
        })
        @DisplayName("should handle various input formats correctly")
        void shouldHandleVariousInputFormats(String input) {
            // When
            String hash = HashingUtil.hashIdentifier(input);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64)
                    .matches("^[a-f0-9]{64}$");
        }

        @Test
        @DisplayName("should produce known hash for known input (SHA-256 verification)")
        void shouldProduceKnownHashForKnownInput() {
            // When
            String hash = HashingUtil.hashIdentifier(KNOWN_INPUT);

            // Then
            assertThat(hash).isEqualTo(KNOWN_HASH);
        }
    }

    @Nested
    @DisplayName("generateRedisKey(String, String) Tests")
    class GenerateRedisKeyTests {

        @Test
        @DisplayName("should return null when namespace is provided but identifier is null")
        void shouldReturnNullWhenIdentifierIsNull() {
            // When
            String result = HashingUtil.generateRedisKey("rate_limit", null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when identifier is empty string")
        void shouldReturnNullWhenIdentifierIsEmpty() {
            // When
            String result = HashingUtil.generateRedisKey("rate_limit", "");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should generate key with correct format: namespace:hash")
        void shouldGenerateKeyWithCorrectFormat() {
            // Given
            String namespace = "rate_limit";
            String identifier = "user-12345";

            // When
            String redisKey = HashingUtil.generateRedisKey(namespace, identifier);

            // Then
            assertThat(redisKey)
                    .isNotNull()
                    .startsWith(namespace + ":")
                    .hasSize(namespace.length() + 1 + 64); // namespace + ":" + 64-char hash
        }

        @Test
        @DisplayName("should generate consistent keys for same namespace and identifier")
        void shouldGenerateConsistentKeys() {
            // Given
            String namespace = "session";
            String identifier = "firebase-uid-xyz789";

            // When
            String key1 = HashingUtil.generateRedisKey(namespace, identifier);
            String key2 = HashingUtil.generateRedisKey(namespace, identifier);

            // Then
            assertThat(key1).isEqualTo(key2);
        }

        @Test
        @DisplayName("should generate different keys for different namespaces with same identifier")
        void shouldGenerateDifferentKeysForDifferentNamespaces() {
            // Given
            String identifier = "same-user-id";

            // When
            String sessionKey = HashingUtil.generateRedisKey("session", identifier);
            String cacheKey = HashingUtil.generateRedisKey("user_cache", identifier);

            // Then
            assertThat(sessionKey).isNotEqualTo(cacheKey);
            assertThat(sessionKey).startsWith("session:");
            assertThat(cacheKey).startsWith("user_cache:");
        }

        @ParameterizedTest
        @CsvSource({
                "rate_limit, user-123",
                "session, firebase-abc",
                "user_cache, long-identifier-value",
                "geo_cache, 192.168.1.1"
        })
        @DisplayName("should work with various namespace and identifier combinations")
        void shouldWorkWithVariousCombinations(String namespace, String identifier) {
            // When
            String redisKey = HashingUtil.generateRedisKey(namespace, identifier);

            // Then
            assertThat(redisKey)
                    .isNotNull()
                    .startsWith(namespace + ":")
                    .contains(":");
        }
    }

    @Nested
    @DisplayName("hashIpAddress(String) Tests")
    class HashIpAddressTests {

        @Test
        @DisplayName("should hash IPv4 address correctly")
        void shouldHashIpv4AddressCorrectly() {
            // Given
            String ipv4 = "192.168.1.100";

            // When
            String hash = HashingUtil.hashIpAddress(ipv4);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64)
                    .matches("^[a-f0-9]{64}$");
        }

        @Test
        @DisplayName("should hash IPv6 address correctly")
        void shouldHashIpv6AddressCorrectly() {
            // Given
            String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";

            // When
            String hash = HashingUtil.hashIpAddress(ipv6);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64)
                    .matches("^[a-f0-9]{64}$");
        }

        @Test
        @DisplayName("should hash compressed IPv6 address correctly")
        void shouldHashCompressedIpv6AddressCorrectly() {
            // Given
            String compressedIpv6 = "2001:db8:85a3::8a2e:370:7334";

            // When
            String hash = HashingUtil.hashIpAddress(compressedIpv6);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64);
        }

        @Test
        @DisplayName("should return null for null IP address")
        void shouldReturnNullForNullIpAddress() {
            // When
            String result = HashingUtil.hashIpAddress(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for empty IP address")
        void shouldReturnNullForEmptyIpAddress() {
            // When
            String result = HashingUtil.hashIpAddress("");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should produce consistent hash for same IP address")
        void shouldProduceConsistentHashForSameIpAddress() {
            // Given
            String ipAddress = "10.0.0.1";

            // When
            String hash1 = HashingUtil.hashIpAddress(ipAddress);
            String hash2 = HashingUtil.hashIpAddress(ipAddress);

            // Then
            assertThat(hash1).isEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("hashFirebaseUid(String) Tests")
    class HashFirebaseUidTests {

        @Test
        @DisplayName("should hash valid Firebase UID correctly")
        void shouldHashValidFirebaseUidCorrectly() {
            // Given
            String firebaseUid = "abc123XYZ789";

            // When
            String hash = HashingUtil.hashFirebaseUid(firebaseUid);

            // Then
            assertThat(hash)
                    .isNotNull()
                    .hasSize(64)
                    .matches("^[a-f0-9]{64}$");
        }

        @Test
        @DisplayName("should return null for null Firebase UID")
        void shouldReturnNullForNullFirebaseUid() {
            // When
            String result = HashingUtil.hashFirebaseUid(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for empty Firebase UID")
        void shouldReturnNullForEmptyFirebaseUid() {
            // When
            String result = HashingUtil.hashFirebaseUid("");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should produce consistent hash for same Firebase UID")
        void shouldProduceConsistentHashForSameFirebaseUid() {
            // Given
            String firebaseUid = "firebase-user-id-unique-12345";

            // When
            String hash1 = HashingUtil.hashFirebaseUid(firebaseUid);
            String hash2 = HashingUtil.hashFirebaseUid(firebaseUid);

            // Then
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("should produce different hashes for different Firebase UIDs")
        void shouldProduceDifferentHashesForDifferentFirebaseUids() {
            // Given
            String uid1 = "firebase-user-001";
            String uid2 = "firebase-user-002";

            // When
            String hash1 = HashingUtil.hashFirebaseUid(uid1);
            String hash2 = HashingUtil.hashFirebaseUid(uid2);

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("Cross-method Consistency Tests")
    class CrossMethodConsistencyTests {

        @Test
        @DisplayName("hashIpAddress should delegate to hashIdentifier and produce same result")
        void hashIpAddressShouldDelegateToHashIdentifier() {
            // Given
            String ipAddress = "172.16.0.1";

            // When
            String fromHashIpAddress = HashingUtil.hashIpAddress(ipAddress);
            String fromHashIdentifier = HashingUtil.hashIdentifier(ipAddress);

            // Then
            assertThat(fromHashIpAddress).isEqualTo(fromHashIdentifier);
        }

        @Test
        @DisplayName("hashFirebaseUid should delegate to hashIdentifier and produce same result")
        void hashFirebaseUidShouldDelegateToHashIdentifier() {
            // Given
            String firebaseUid = "firebase-uid-test-123";

            // When
            String fromHashFirebaseUid = HashingUtil.hashFirebaseUid(firebaseUid);
            String fromHashIdentifier = HashingUtil.hashIdentifier(firebaseUid);

            // Then
            assertThat(fromHashFirebaseUid).isEqualTo(fromHashIdentifier);
        }
    }
}
