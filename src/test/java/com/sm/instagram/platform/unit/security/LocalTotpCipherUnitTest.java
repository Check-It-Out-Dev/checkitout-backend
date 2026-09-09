package com.sm.instagram.platform.unit.security;

import com.sm.instagram.platform.common.security.LocalTotpCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cipher that stands in for Cloud KMS when a run has no Google credential.
 *
 * <p>It exists so the end-to-end tier can store a TOTP secret on a public runner. What matters is
 * that it is a real AEAD cipher rather than a bypass, that it cannot be confused with a KMS
 * ciphertext in either direction, and that it refuses to work at all without a key - a silent
 * fallback to plaintext would be worse than the failure it replaces.
 */
@DisplayName("LocalTotpCipher")
class LocalTotpCipherUnitTest {

    private static final String KEY = "a-test-key-for-the-local-cipher";

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("returns what was encrypted")
        void returnsWhatWasEncrypted() {
            LocalTotpCipher cipher = new LocalTotpCipher(KEY);
            String plaintext = "JBSWY3DPEHPK3PXP";

            assertThat(cipher.decrypt(cipher.encrypt(plaintext))).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("handles a long value and one with characters outside ASCII")
        void handlesAwkwardValues() {
            LocalTotpCipher cipher = new LocalTotpCipher(KEY);
            String plaintext = "zażółć gęślą jaźń ".repeat(40);

            assertThat(cipher.decrypt(cipher.encrypt(plaintext))).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("gives a different ciphertext every time, because the IV is random")
        void doesNotRepeatItself() {
            LocalTotpCipher cipher = new LocalTotpCipher(KEY);

            String first = cipher.encrypt("same secret");
            String second = cipher.encrypt("same secret");

            assertThat(first).isNotEqualTo(second);
            assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second));
        }
    }

    @Nested
    @DisplayName("telling the two ciphers apart")
    class Provenance {

        @Test
        @DisplayName("stamps its own ciphertexts")
        void stampsItsOwnCiphertexts() {
            assertThat(new LocalTotpCipher(KEY).encrypt("secret")).startsWith("v1:");
        }

        @Test
        @DisplayName("refuses a value it did not write, rather than returning rubbish")
        void refusesAKmsCiphertext() {
            // What a KMS ciphertext looks like here: base64, no prefix.
            assertThatThrownBy(() -> new LocalTotpCipher(KEY).decrypt("CiQAaW5zdGFuY2UtaWQtaGVyZQ=="))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not written by the local cipher");
        }

        @Test
        @DisplayName("fails on a ciphertext from a different key")
        void failsOnADifferentKey() {
            String sealed = new LocalTotpCipher(KEY).encrypt("secret");

            assertThatThrownBy(() -> new LocalTotpCipher("a completely different key").decrypt(sealed))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("without a key")
    class Unconfigured {

        @Test
        @DisplayName("reports that it is not configured")
        void reportsThatItIsNotConfigured() {
            assertThat(new LocalTotpCipher("").isConfigured()).isFalse();
            assertThat(new LocalTotpCipher(null).isConfigured()).isFalse();
            assertThat(new LocalTotpCipher(KEY).isConfigured()).isTrue();
        }

        @Test
        @DisplayName("throws instead of falling back to plaintext")
        void throwsInsteadOfFallingBack() {
            LocalTotpCipher cipher = new LocalTotpCipher("");

            assertThatThrownBy(() -> cipher.encrypt("secret"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("totp.local-key is empty");
        }
    }
}
