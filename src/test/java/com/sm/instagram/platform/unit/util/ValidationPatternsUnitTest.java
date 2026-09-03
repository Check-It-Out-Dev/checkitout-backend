package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.util.ValidationPatterns;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The media-URL pattern guards every persisted image reference. It must accept
 * what the backend actually mints — bucket URLs in production, application
 * paths in the dev-lite simulator — and reject anything that could point a
 * stored link at a host we do not control.
 */
@DisplayName("ValidationPatterns.HTTPS_URL_PATTERN")
class ValidationPatternsUnitTest {

    private static final Pattern URL = Pattern.compile(ValidationPatterns.HTTPS_URL_PATTERN);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://firebasestorage.googleapis.com/v0/b/bucket/o/content%2Fuid%2F1_a.png?alt=media",
            "https://storage.googleapis.com/bucket/content/uid/1.png",
            "https://check-it-out.pl/img/logo.png",
            "/api/dev-lite/files/content/uid/1785_avatar.png",
            "/api/dev-lite/placeholder/user-401",
    })
    void accepts(String url) {
        assertThat(URL.matcher(url).matches()).as(url).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://evil.example.com/x.png",   // plain http
            "//evil.example.com/x.png",        // protocol-relative: resolves to another host
            "javascript:alert(1)",
            "ftp://host/file.png",
            "data:image/png;base64,AAAA",
    })
    void rejects(String url) {
        assertThat(URL.matcher(url).matches()).as(url).isFalse();
    }
}
