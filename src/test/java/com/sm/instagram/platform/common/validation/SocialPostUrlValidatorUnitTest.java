package com.sm.instagram.platform.common.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link SocialPostUrlValidator} (pentest 3.5 follow-up —
 * the publication link must point at Instagram/TikTok, not arbitrary
 * attacker-controlled hosts; it is rendered as a clickable anchor to the
 * reviewing company).
 */
class SocialPostUrlValidatorUnitTest {

    private SocialPostUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SocialPostUrlValidator();
    }

    private boolean valid(String url) {
        return validator.isValid(url, null);
    }

    @Test
    void acceptsTrustedInstagramHosts() {
        assertTrue(valid("https://instagram.com/p/abc123"));
        assertTrue(valid("https://www.instagram.com/reel/xyz789/"));
    }

    @Test
    void acceptsTrustedTikTokHosts() {
        assertTrue(valid("https://tiktok.com/@user/video/1"));
        assertTrue(valid("https://www.tiktok.com/@user/video/123"));
        assertTrue(valid("https://vm.tiktok.com/ZM123abc/"));
    }

    @Test
    void rejectsArbitraryAndLocalHosts() {
        // Same class as report Listing 3.9 on the sibling urls field.
        assertFalse(valid("http://localhost:8000/malicious_file.html"));
        assertFalse(valid("https://attacker-controlled-server/evil"));
        assertFalse(valid("https://youtube.com/watch?v=abc")); // wrong host
        assertFalse(valid("https://vimeo.com/123"));           // vimeo goes in `urls`, not here
    }

    @Test
    void rejectsNonHttpsAndTricks() {
        assertFalse(valid("http://instagram.com/p/abc"));               // not https
        assertFalse(valid("https://instagram.com.attacker.com/p/abc")); // subdomain trick
        assertFalse(valid("https://evil.com/instagram.com/p/abc"));     // path trick
        assertFalse(valid("javascript:alert(1)"));
        assertFalse(valid("not a url"));
    }

    @Test
    void nullAndBlankAreValidOptionalField() {
        assertTrue(valid(null));
        assertTrue(valid(""));
        assertTrue(valid("   "));
    }
}
