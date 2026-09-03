package com.sm.instagram.platform.common.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link VimeoUrlsValidator} (pentest 3.5 — content video URLs
 * must be Vimeo, not arbitrary attacker-controlled hosts).
 */
class VimeoUrlsValidatorUnitTest {

    private VimeoUrlsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new VimeoUrlsValidator();
    }

    private boolean valid(List<String> urls) {
        return validator.isValid(urls, null);
    }

    @Test
    void acceptsTrustedVimeoHosts() {
        assertTrue(valid(List.of("https://vimeo.com/123456789")));
        assertTrue(valid(List.of("https://www.vimeo.com/123456789")));
        assertTrue(valid(List.of("https://player.vimeo.com/video/123456789")));
        assertTrue(valid(List.of(
                "https://vimeo.com/1",
                "https://player.vimeo.com/video/2")));
    }

    @Test
    void rejectsTheReportedMaliciousUrl() {
        // Exact payload from report Listing 3.9.
        assertFalse(valid(List.of("http://localhost:8000/malicious_file.html")));
    }

    @Test
    void rejectsNonVimeoHostsAndNonHttps() {
        assertFalse(valid(List.of("https://attacker-controlled-server/evil")));
        assertFalse(valid(List.of("http://vimeo.com/123")));            // not https
        assertFalse(valid(List.of("https://youtube.com/watch?v=abc")));  // wrong host
        assertFalse(valid(List.of("https://vimeo.com.attacker.com/1")));  // subdomain trick
        assertFalse(valid(List.of("not a url")));
        assertFalse(valid(List.of("javascript:alert(1)")));
    }

    @Test
    void rejectsWhenAnyEntryIsInvalid() {
        assertFalse(valid(List.of(
                "https://vimeo.com/ok",
                "http://localhost:8000/malicious_file.html")));
    }

    @Test
    void nullAndEmptyAreValidOptionalField() {
        assertTrue(valid(null));
        assertTrue(valid(List.of()));
    }
}
