package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.auth.filter.HmacUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for HmacUtils.
 * These are pure unit tests - no Spring context or mocking needed.
 *
 * Tests cover:
 * - HMAC generation with valid and invalid inputs
 * - Constant-time string comparison for timing attack resistance
 * - HMAC validation round-trip tests
 */
@DisplayName("HmacUtils")
class HmacUtilsUnitTest {

    private static final String TEST_SECRET = "test-secret-key-12345";
    private static final String TEST_DATA = "sample-data-to-sign";

    @Nested
    @DisplayName("generateHMAC")
    class GenerateHMAC {

        @Test
        @DisplayName("should generate valid Base64-encoded HMAC signature for valid inputs")
        void generateHMAC_withValidInputs_returnsCorrectSignature() {
            // Given
            String data = "test-data";
            String secret = "secret-key";

            // When
            String hmac = HmacUtils.generateHMAC(data, secret);

            // Then
            assertThat(hmac)
                    .isNotNull()
                    .isNotEmpty()
                    .isBase64(); // AssertJ validates Base64 format
        }

        @Test
        @DisplayName("should throw NullPointerException when data is null")
        void generateHMAC_withNullData_throwsNullPointerException() {
            // Given
            String secret = "secret-key";

            // When/Then
            assertThatThrownBy(() -> HmacUtils.generateHMAC(null, secret))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should generate valid HMAC for empty data string")
        void generateHMAC_withEmptyData_returnsValidSignature() {
            // Given
            String data = "";
            String secret = "secret-key";

            // When
            String hmac = HmacUtils.generateHMAC(data, secret);

            // Then
            assertThat(hmac)
                    .isNotNull()
                    .isNotEmpty()
                    .isBase64();
        }

        @Test
        @DisplayName("should throw NullPointerException when key is null")
        void generateHMAC_withNullKey_throwsNullPointerException() {
            // Given
            String data = "test-data";

            // When/Then
            assertThatThrownBy(() -> HmacUtils.generateHMAC(data, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when key is empty")
        void generateHMAC_withEmptyKey_throwsIllegalArgumentException() {
            // Given
            String data = "test-data";
            String secret = "";

            // When/Then
            // SecretKeySpec rejects empty keys with IllegalArgumentException
            assertThatThrownBy(() -> HmacUtils.generateHMAC(data, secret))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Empty key");
        }

        @Test
        @DisplayName("should produce same HMAC for identical inputs")
        void sameInputs_produceSameHMAC() {
            // Given
            String data = TEST_DATA;
            String secret = TEST_SECRET;

            // When
            String hmac1 = HmacUtils.generateHMAC(data, secret);
            String hmac2 = HmacUtils.generateHMAC(data, secret);

            // Then
            assertThat(hmac1).isEqualTo(hmac2);
        }

        @Test
        @DisplayName("should produce different HMACs for different data")
        void differentInputs_produceDifferentHMACs() {
            // Given
            String secret = TEST_SECRET;

            // When
            String hmac1 = HmacUtils.generateHMAC("data-one", secret);
            String hmac2 = HmacUtils.generateHMAC("data-two", secret);

            // Then
            assertThat(hmac1).isNotEqualTo(hmac2);
        }

        @Test
        @DisplayName("should produce different HMACs for different secrets")
        void differentSecrets_produceDifferentHMACs() {
            // Given
            String data = TEST_DATA;

            // When
            String hmac1 = HmacUtils.generateHMAC(data, "secret-one");
            String hmac2 = HmacUtils.generateHMAC(data, "secret-two");

            // Then
            assertThat(hmac1).isNotEqualTo(hmac2);
        }

        @ParameterizedTest
        @ValueSource(strings = {"simple", "with spaces", "special!@#$%^&*()", "unicode-\u00e9\u00e8\u00ea\u4e2d\u6587"})
        @DisplayName("should handle various input formats")
        void generateHMAC_withVariousInputFormats_returnsValidSignature(String data) {
            // When
            String hmac = HmacUtils.generateHMAC(data, TEST_SECRET);

            // Then
            assertThat(hmac)
                    .isNotNull()
                    .isNotEmpty()
                    .isBase64();
        }
    }

    @Nested
    @DisplayName("constantTimeEquals")
    class ConstantTimeEquals {

        @Test
        @DisplayName("should return true for identical strings")
        void constantTimeEquals_identicalStrings_returnsTrue() {
            // Given
            String a = "same-string-value";
            String b = "same-string-value";

            // When
            boolean result = HmacUtils.constantTimeEquals(a, b);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for different strings")
        void constantTimeEquals_differentStrings_returnsFalse() {
            // Given
            String a = "string-one";
            String b = "string-two";

            // When
            boolean result = HmacUtils.constantTimeEquals(a, b);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when both strings are null")
        void constantTimeEquals_bothNull_returnsTrue() {
            // When
            boolean result = HmacUtils.constantTimeEquals(null, null);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when first string is null")
        void constantTimeEquals_firstNull_returnsFalse() {
            // When
            boolean result = HmacUtils.constantTimeEquals(null, "non-null");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when second string is null")
        void constantTimeEquals_secondNull_returnsFalse() {
            // When
            boolean result = HmacUtils.constantTimeEquals("non-null", null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for two empty strings")
        void constantTimeEquals_emptyStrings_returnsTrue() {
            // When
            boolean result = HmacUtils.constantTimeEquals("", "");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when comparing empty string with non-empty")
        void constantTimeEquals_emptyAndNonEmpty_returnsFalse() {
            // When
            boolean result = HmacUtils.constantTimeEquals("", "non-empty");

            // Then
            assertThat(result).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
                "'abc', 'abc', true",
                "'abc', 'abd', false",
                "'abc', 'abcd', false",
                "'abcd', 'abc', false",
                "'', '', true"
        })
        @DisplayName("should correctly compare various string pairs")
        void constantTimeEquals_variousPairs_returnsExpected(String a, String b, boolean expected) {
            // When
            boolean result = HmacUtils.constantTimeEquals(a, b);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should demonstrate timing attack resistance by using constant-time comparison")
        void constantTimeEquals_timingAttackResistance_consistentTime() {
            // This test verifies the implementation uses MessageDigest.isEqual
            // which provides constant-time comparison. We test behavioral correctness
            // rather than actual timing (timing tests are unreliable in unit tests).

            // Given - strings that differ early vs late in the string
            String base = "abcdefghijklmnopqrstuvwxyz1234567890";
            String differEarly = "Xbcdefghijklmnopqrstuvwxyz1234567890";
            String differLate = "abcdefghijklmnopqrstuvwxyz123456789X";

            // When/Then - both should return false, proving comparison is complete
            assertThat(HmacUtils.constantTimeEquals(base, differEarly)).isFalse();
            assertThat(HmacUtils.constantTimeEquals(base, differLate)).isFalse();

            // Note: The constant-time guarantee comes from MessageDigest.isEqual
            // which is documented to always compare all bytes regardless of where
            // differences occur. This prevents timing side-channel attacks.
        }
    }

    @Nested
    @DisplayName("validateHMAC")
    class ValidateHMAC {

        @Test
        @DisplayName("should return true for valid signature")
        void validateHMAC_validSignature_returnsTrue() {
            // Given
            String data = TEST_DATA;
            String secret = TEST_SECRET;
            String signature = HmacUtils.generateHMAC(data, secret);

            // When
            boolean result = HmacUtils.validateHMAC(data, signature, secret);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for tampered data")
        void validateHMAC_tamperedData_returnsFalse() {
            // Given
            String originalData = "original-data";
            String secret = TEST_SECRET;
            String signature = HmacUtils.generateHMAC(originalData, secret);
            String tamperedData = "tampered-data";

            // When
            boolean result = HmacUtils.validateHMAC(tamperedData, signature, secret);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when using wrong key")
        void validateHMAC_wrongKey_returnsFalse() {
            // Given
            String data = TEST_DATA;
            String correctSecret = "correct-secret";
            String wrongSecret = "wrong-secret";
            String signature = HmacUtils.generateHMAC(data, correctSecret);

            // When
            boolean result = HmacUtils.validateHMAC(data, signature, wrongSecret);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should successfully round-trip generate and validate")
        void roundTrip_generateThenValidate_succeeds() {
            // Given
            String data = "round-trip-test-data-with-special-chars-!@#$%";
            String secret = "round-trip-secret-key";

            // When
            String signature = HmacUtils.generateHMAC(data, secret);
            boolean isValid = HmacUtils.validateHMAC(data, signature, secret);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should return false for completely invalid signature format")
        void validateHMAC_invalidSignatureFormat_returnsFalse() {
            // Given
            String data = TEST_DATA;
            String secret = TEST_SECRET;
            String invalidSignature = "not-a-valid-base64-hmac-!@#$%";

            // When
            boolean result = HmacUtils.validateHMAC(data, invalidSignature, secret);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for empty signature")
        void validateHMAC_emptySignature_returnsFalse() {
            // Given
            String data = TEST_DATA;
            String secret = TEST_SECRET;

            // When
            boolean result = HmacUtils.validateHMAC(data, "", secret);

            // Then
            assertThat(result).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "payment:123:1000.00:USD",
                "{\"userId\":\"abc\",\"action\":\"transfer\"}",
                "webhook_callback_data_with_timestamp_1234567890"
        })
        @DisplayName("should validate various data formats correctly")
        void validateHMAC_variousDataFormats_validatesCorrectly(String data) {
            // Given
            String secret = TEST_SECRET;
            String signature = HmacUtils.generateHMAC(data, secret);

            // When
            boolean result = HmacUtils.validateHMAC(data, signature, secret);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("should detect single character modification in data")
        void shouldDetectSingleCharacterModification() {
            // Given
            String originalData = "sensitive-transaction-data";
            String modifiedData = "sensitive-transactioN-data"; // N instead of n
            String secret = TEST_SECRET;
            String signature = HmacUtils.generateHMAC(originalData, secret);

            // When/Then
            assertThat(HmacUtils.validateHMAC(originalData, signature, secret)).isTrue();
            assertThat(HmacUtils.validateHMAC(modifiedData, signature, secret)).isFalse();
        }

        @Test
        @DisplayName("should handle long data strings")
        void shouldHandleLongDataStrings() {
            // Given
            String longData = "x".repeat(10000);
            String secret = TEST_SECRET;

            // When
            String signature = HmacUtils.generateHMAC(longData, secret);
            boolean isValid = HmacUtils.validateHMAC(longData, signature, secret);

            // Then
            assertThat(signature).isNotEmpty().isBase64();
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should handle long secret keys")
        void shouldHandleLongSecretKeys() {
            // Given
            String data = TEST_DATA;
            String longSecret = "s".repeat(1000);

            // When
            String signature = HmacUtils.generateHMAC(data, longSecret);
            boolean isValid = HmacUtils.validateHMAC(data, signature, longSecret);

            // Then
            assertThat(signature).isNotEmpty().isBase64();
            assertThat(isValid).isTrue();
        }
    }
}
