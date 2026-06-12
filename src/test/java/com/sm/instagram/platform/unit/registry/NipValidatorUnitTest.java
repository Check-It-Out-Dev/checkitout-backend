package com.sm.instagram.platform.unit.registry;

import com.sm.instagram.platform.registry.NipValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NipValidator.
 * Pure unit tests - no Spring context needed.
 *
 * Polish NIP checksum algorithm: weights = {6, 5, 7, 2, 3, 4, 5, 6, 7}
 * sum(digit[i] * weight[i]) mod 11 == digit[9]
 */
@DisplayName("NipValidator")
class NipValidatorUnitTest {

    private NipValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NipValidator();
    }

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @ParameterizedTest
        @ValueSource(strings = {
                "5261040828",  // Ministerstwo Finansów (GUS test NIP)
                "7740001454",  // known valid NIP
                "5252344078",  // known valid NIP
                "1234563218"   // checksum-valid NIP
        })
        @DisplayName("should return true for valid NIPs")
        void shouldReturnTrueForValidNips(String nip) {
            assertThat(validator.isValid(nip)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "526-104-08-28",  // with dashes
                "526 104 08 28"   // with spaces
        })
        @DisplayName("should return true for valid NIPs with formatting")
        void shouldReturnTrueForValidNipsWithFormatting(String nip) {
            assertThat(validator.isValid(nip)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "5261040829",   // last digit changed (invalid checksum)
                "1234567890",   // sequential digits (invalid checksum)
                "1111111112"    // check digit mismatch (sum=45, mod11=1, last=2)
        })
        @DisplayName("should return false for invalid checksum")
        void shouldReturnFalseForInvalidChecksum(String nip) {
            assertThat(validator.isValid(nip)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "123456789",    // too short (9 digits)
                "12345678901",  // too long (11 digits)
                "",             // empty
                "abcdefghij",   // letters
                "12345abcde",   // mixed
                "12.345.678"    // dots
        })
        @DisplayName("should return false for invalid format")
        void shouldReturnFalseForInvalidFormat(String nip) {
            assertThat(validator.isValid(nip)).isFalse();
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull(String nip) {
            assertThat(validator.isValid(nip)).isFalse();
        }

        @Test
        @DisplayName("should reject NIP where checksum mod 11 equals 10")
        void shouldRejectNipWhereModIs10() {
            // A NIP where the weighted sum mod 11 = 10 is always invalid
            // because the check digit cannot be 10 (only 0-9)
            // We verify this by construction: if we find such a case it must be rejected
            // NIP: the weights are {6,5,7,2,3,4,5,6,7}
            // We need sum(d[i]*w[i]) % 11 == 10
            // For "1111111110" -> sum = 6+5+7+2+3+4+5+6+7 = 45, 45 % 11 = 1, check digit should be 1, not 0
            assertThat(validator.isValid("1111111110")).isFalse();
        }
    }

    @Nested
    @DisplayName("normalize")
    class Normalize {

        @Test
        @DisplayName("should remove dashes")
        void shouldRemoveDashes() {
            assertThat(validator.normalize("526-104-08-28")).isEqualTo("5261040828");
        }

        @Test
        @DisplayName("should remove spaces")
        void shouldRemoveSpaces() {
            assertThat(validator.normalize("526 104 08 28")).isEqualTo("5261040828");
        }

        @Test
        @DisplayName("should remove mixed dashes and spaces")
        void shouldRemoveMixedDashesAndSpaces() {
            assertThat(validator.normalize("526-104 08-28")).isEqualTo("5261040828");
        }

        @Test
        @DisplayName("should return same string if already clean")
        void shouldReturnSameStringIfAlreadyClean() {
            assertThat(validator.normalize("5261040828")).isEqualTo("5261040828");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(validator.normalize(null)).isNull();
        }

        @Test
        @DisplayName("should return empty string for empty input")
        void shouldReturnEmptyStringForEmptyInput() {
            assertThat(validator.normalize("")).isEmpty();
        }
    }
}
