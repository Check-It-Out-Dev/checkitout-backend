package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.util.OtpAuthUrls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The redaction rule for otpauth URLs.
 *
 * <p>The seed in one of these is the entire second factor and it does not expire, so "the secret is
 * gone" is the only assertion that matters here; everything else is about not making the redacted
 * line useless. Written because CodeQL found the same URL being logged in two places, one of them
 * the production enrolment path.
 */
@DisplayName("OtpAuthUrls.redactSecret")
class OtpAuthUrlsUnitTest {

    private static final String SEED = "JBSWY3DPEHPK3PXP";

    @Test
    @DisplayName("the seed is gone and the rest of the URL survives")
    void redactsTheSeedAndKeepsTheRest() {
        String url = "otpauth://totp/CheckItOut:user%40example.com?secret=" + SEED
                + "&issuer=CheckItOut&algorithm=SHA1&digits=6&period=30";

        String redacted = OtpAuthUrls.redactSecret(url);

        assertThat(redacted).doesNotContain(SEED);
        assertThat(redacted).contains("secret=REDACTED");
        assertThat(redacted)
                .as("what is left has to still be worth logging")
                .contains("otpauth://totp/CheckItOut:user%40example.com")
                .contains("issuer=CheckItOut")
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    @DisplayName("the seed is gone when it is the last parameter")
    void redactsATrailingSeed() {
        String url = "otpauth://totp/CheckItOut:user%40example.com?issuer=CheckItOut&secret=" + SEED;

        assertThat(OtpAuthUrls.redactSecret(url))
                .doesNotContain(SEED)
                .isEqualTo("otpauth://totp/CheckItOut:user%40example.com?issuer=CheckItOut&secret=REDACTED");
    }

    @Test
    @DisplayName("a URL quoted inside a sentence still loses its seed")
    void redactsInsideSurroundingText() {
        String line = "URL doesn't contain email. URL: otpauth://totp/X?secret=" + SEED
                + "&issuer=Y, looking for a@b.c";

        assertThat(OtpAuthUrls.redactSecret(line)).doesNotContain(SEED);
    }

    @Test
    @DisplayName("more than one URL on the line: every seed goes")
    void redactsEveryOccurrence() {
        String line = "before secret=" + SEED + "&x=1 and after secret=SECONDSEED2222&y=2";

        String redacted = OtpAuthUrls.redactSecret(line);

        assertThat(redacted).doesNotContain(SEED).doesNotContain("SECONDSEED2222");
        assertThat(redacted).isEqualTo("before secret=REDACTED&x=1 and after secret=REDACTED&y=2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SECRET=", "Secret=", "sEcReT="})
    @DisplayName("the parameter name is matched however it is cased")
    void redactsWhateverTheCase(String key) {
        assertThat(OtpAuthUrls.redactSecret("otpauth://totp/X?" + key + SEED)).doesNotContain(SEED);
    }

    @Test
    @DisplayName("truncating a redacted URL cannot re-expose the seed")
    void truncationStaysSafe() {
        // The validator quotes the first 80 characters of its URL into a result that is printed at
        // INFO. On the raw URL that window reaches past `secret=`; on the redacted one it cannot.
        String url = "otpauth://totp/CheckItOut:validator%40test.com?secret=" + SEED
                + "&issuer=CheckItOut&algorithm=SHA1&digits=6&period=30";
        String redacted = OtpAuthUrls.redactSecret(url);

        assertThat(url.substring(0, 80)).as("the raw URL leaks in 80 characters").contains(SEED);
        assertThat(redacted.substring(0, Math.min(80, redacted.length()))).doesNotContain(SEED);
    }

    @Test
    @DisplayName("null in, null out — a redactor that throws would be worse than the leak")
    void nullIsPassedThrough() {
        assertThat(OtpAuthUrls.redactSecret(null)).isNull();
    }

    @Test
    @DisplayName("a URL with no secret parameter is returned unchanged")
    void leavesUnrelatedTextAlone() {
        String url = "otpauth://totp/CheckItOut:user%40example.com?issuer=CheckItOut&digits=6";

        assertThat(OtpAuthUrls.redactSecret(url)).isEqualTo(url);
    }
}
