package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.util.PiiMaskingUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PiiMaskingUtils}.
 *
 * <p>Tests verify correct masking of PII data including emails, IP addresses,
 * and phone numbers for GDPR-compliant logging.</p>
 */
@DisplayName("PiiMaskingUtils Unit Tests")
class PiiMaskingUtilsUnitTest {

    @Nested
    @DisplayName("maskEmail tests")
    class MaskEmailTests {

        @Test
        @DisplayName("masks valid email correctly - shows first char and domain")
        void maskEmail_validEmail_masksCorrectly() {
            // Given
            String email = "john.doe@example.com";

            // When
            String result = PiiMaskingUtils.maskEmail(email);

            // Then
            assertThat(result).isEqualTo("j***@example.com");
        }

        @Test
        @DisplayName("masks short local part email correctly")
        void maskEmail_shortLocalPart_masksCorrectly() {
            // Given
            String email = "a@test.org";

            // When
            String result = PiiMaskingUtils.maskEmail(email);

            // Then
            assertThat(result).isEqualTo("a***@test.org");
        }

        @Test
        @DisplayName("returns null when email is null")
        void maskEmail_nullEmail_returnsNull() {
            // When
            String result = PiiMaskingUtils.maskEmail(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns original string when email has no @ symbol")
        void maskEmail_noAtSymbol_returnsOriginal() {
            // Given
            String invalidEmail = "not-an-email";

            // When
            String result = PiiMaskingUtils.maskEmail(invalidEmail);

            // Then
            assertThat(result).isEqualTo(invalidEmail);
        }

        @Test
        @DisplayName("returns original string when local part is empty")
        void maskEmail_emptyLocalPart_returnsOriginal() {
            // Given
            String invalidEmail = "@example.com";

            // When
            String result = PiiMaskingUtils.maskEmail(invalidEmail);

            // Then
            assertThat(result).isEqualTo(invalidEmail);
        }

        @ParameterizedTest(name = "masks ''{0}'' to ''{1}''")
        @CsvSource({
                "user@domain.com, u***@domain.com",
                "test.user@company.org, t***@company.org",
                "x@y.z, x***@y.z",
                "admin+filter@gmail.com, a***@gmail.com"
        })
        @DisplayName("masks various valid emails correctly")
        void maskEmail_variousValidEmails_masksCorrectly(String input, String expected) {
            // When
            String result = PiiMaskingUtils.maskEmail(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("maskIp tests")
    class MaskIpTests {

        @Test
        @DisplayName("masks valid IPv4 address - hides last octet")
        void maskIp_validIpv4_masksLastOctet() {
            // Given
            String ip = "192.168.1.100";

            // When
            String result = PiiMaskingUtils.maskIp(ip);

            // Then
            assertThat(result).isEqualTo("192.168.1.***");
        }

        @Test
        @DisplayName("returns null when IP is null")
        void maskIp_null_returnsNull() {
            // When
            String result = PiiMaskingUtils.maskIp(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("masks localhost IP correctly")
        void maskIp_localhost_masksLastOctet() {
            // Given
            String ip = "127.0.0.1";

            // When
            String result = PiiMaskingUtils.maskIp(ip);

            // Then
            assertThat(result).isEqualTo("127.0.0.***");
        }

        @ParameterizedTest(name = "masks ''{0}'' to ''{1}''")
        @CsvSource({
                "10.0.0.1, 10.0.0.***",
                "172.16.0.255, 172.16.0.***",
                "8.8.8.8, 8.8.8.***",
                "255.255.255.0, 255.255.255.***"
        })
        @DisplayName("masks various IPv4 addresses correctly")
        void maskIp_variousIpv4Addresses_masksCorrectly(String input, String expected) {
            // When
            String result = PiiMaskingUtils.maskIp(input);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns original string when no dot present")
        void maskIp_noDot_returnsOriginal() {
            // Given
            String invalidIp = "localhost";

            // When
            String result = PiiMaskingUtils.maskIp(invalidIp);

            // Then
            assertThat(result).isEqualTo(invalidIp);
        }
    }

    @Nested
    @DisplayName("maskPhoneNumber tests")
    class MaskPhoneNumberTests {

        @Test
        @DisplayName("masks valid phone number - shows last 4 digits")
        void maskPhoneNumber_validPhone_masksCorrectly() {
            // Given
            String phone = "555-123-4567";

            // When
            String result = PiiMaskingUtils.maskPhoneNumber(phone);

            // Then
            assertThat(result).isEqualTo("***-***-4567");
        }

        @Test
        @DisplayName("returns null when phone is null")
        void maskPhoneNumber_null_returnsNull() {
            // When
            String result = PiiMaskingUtils.maskPhoneNumber(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns original when phone is too short")
        void maskPhoneNumber_tooShort_returnsOriginal() {
            // Given
            String shortPhone = "123";

            // When
            String result = PiiMaskingUtils.maskPhoneNumber(shortPhone);

            // Then
            assertThat(result).isEqualTo(shortPhone);
        }

        @Test
        @DisplayName("masks phone number with different formats correctly")
        void maskPhoneNumber_differentFormats_masksCorrectly() {
            // Given
            String phone1 = "+1 (555) 123-4567";
            String phone2 = "5551234567";
            String phone3 = "555.123.4567";

            // When
            String result1 = PiiMaskingUtils.maskPhoneNumber(phone1);
            String result2 = PiiMaskingUtils.maskPhoneNumber(phone2);
            String result3 = PiiMaskingUtils.maskPhoneNumber(phone3);

            // Then
            assertThat(result1).isEqualTo("***-***-4567");
            assertThat(result2).isEqualTo("***-***-4567");
            assertThat(result3).isEqualTo("***-***-4567");
        }

        @Test
        @DisplayName("handles phone with exactly 4 digits")
        void maskPhoneNumber_exactly4Digits_masksCorrectly() {
            // Given
            String phone = "1234";

            // When
            String result = PiiMaskingUtils.maskPhoneNumber(phone);

            // Then
            assertThat(result).isEqualTo("***-***-1234");
        }

        @Test
        @DisplayName("returns original when phone has less than 4 digits")
        void maskPhoneNumber_lessThan4Digits_returnsOriginal() {
            // Given
            String phone = "abc-12";

            // When
            String result = PiiMaskingUtils.maskPhoneNumber(phone);

            // Then
            assertThat(result).isEqualTo(phone);
        }
    }
    @Nested
    @DisplayName("maskUsername")
    class MaskUsernameTests {

        @Test
        @DisplayName("shows the first two characters and hides the rest")
        void maskUsername_long_showsTwoCharacters() {
            assertThat(PiiMaskingUtils.maskUsername("testuser123")).isEqualTo("te***");
        }

        @Test
        @DisplayName("hides a short handle entirely, because two of three characters is not a mask")
        void maskUsername_short_hidesEverything() {
            assertThat(PiiMaskingUtils.maskUsername("abc")).isEqualTo("***");
            assertThat(PiiMaskingUtils.maskUsername("a")).isEqualTo("***");
        }

        @Test
        @DisplayName("null and empty read as unknown")
        void maskUsername_nullOrEmpty_isUnknown() {
            assertThat(PiiMaskingUtils.maskUsername(null)).isEqualTo("unknown");
            assertThat(PiiMaskingUtils.maskUsername("")).isEqualTo("unknown");
        }
    }

    /**
     * A pseudonym has two jobs that pull against each other: the same account must produce the same
     * string on every line, or the log cannot be followed, and the string must not hand the account
     * id to whoever reads it. These tests hold both ends.
     */
    @Nested
    @DisplayName("pseudonymousId")
    class PseudonymousIdTests {

        /** SHA-256("12345678") = ef797c8118f0... - pinned, so a change of algorithm fails here. */
        private static final String ID = "12345678";

        @Test
        @DisplayName("is SHA-256 truncated to twelve hex characters behind the prefix")
        void pseudonymousId_isTruncatedSha256() {
            assertThat(PiiMaskingUtils.pseudonymousId(ID, "ig")).isEqualTo("ig_ef797c8118f0");
        }

        @Test
        @DisplayName("is not the 32-bit String.hashCode it replaced")
        void pseudonymousId_isNotTheOldHash() {
            String oldFormula = "ig_" + Integer.toHexString(ID.hashCode());

            assertThat(PiiMaskingUtils.pseudonymousId(ID, "ig")).isNotEqualTo(oldFormula);
        }

        @Test
        @DisplayName("the same id always produces the same pseudonym, which is the point of logging it")
        void pseudonymousId_isStable() {
            assertThat(PiiMaskingUtils.pseudonymousId(ID, "ig"))
                    .isEqualTo(PiiMaskingUtils.pseudonymousId(ID, "ig"));
        }

        @Test
        @DisplayName("two accounts do not collide")
        void pseudonymousId_differentIdsDiffer() {
            assertThat(PiiMaskingUtils.pseudonymousId("17841400000000001", "ig"))
                    .isNotEqualTo(PiiMaskingUtils.pseudonymousId("17841400000000002", "ig"));
        }

        @Test
        @DisplayName("the id itself does not survive into the output")
        void pseudonymousId_doesNotContainTheId() {
            assertThat(PiiMaskingUtils.pseudonymousId("17841400000000001", "ig"))
                    .doesNotContain("17841400000000001")
                    .doesNotContain("178414");
        }

        @Test
        @DisplayName("the prefix keeps two id spaces apart")
        void pseudonymousId_prefixSeparatesIdSpaces() {
            assertThat(PiiMaskingUtils.pseudonymousId(ID, "ig"))
                    .isNotEqualTo(PiiMaskingUtils.pseudonymousId(ID, "fb"));
        }

        @Test
        @DisplayName("null and empty read as unknown rather than as a hash of nothing")
        void pseudonymousId_nullOrEmpty_isUnknown() {
            assertThat(PiiMaskingUtils.pseudonymousId(null, "ig")).isEqualTo("unknown");
            assertThat(PiiMaskingUtils.pseudonymousId("", "ig")).isEqualTo("unknown");
        }
    }
}
