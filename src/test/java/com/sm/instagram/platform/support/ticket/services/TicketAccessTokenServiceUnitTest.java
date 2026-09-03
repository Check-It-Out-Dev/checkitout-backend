package com.sm.instagram.platform.support.ticket.services;

import com.sm.instagram.platform.auth.filter.HmacUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link TicketAccessTokenService} — the signed magic-link
 * tokens for anonymous ticket access. Confirms tokens round-trip, cannot be
 * forged or tampered, and expire.
 */
class TicketAccessTokenServiceUnitTest {

    private static final String SECRET = "test-secret-123";
    private TicketAccessTokenService service;

    @BeforeEach
    void setUp() {
        service = new TicketAccessTokenService(SECRET);
    }

    @Test
    void mintedTokenVerifiesBackToTheSameTicketId() {
        String token = service.mint(4242L);
        assertEquals(Optional.of(4242L), service.verify(token));
    }

    @Test
    void rejectsATamperedToken() {
        String token = service.mint(7L);
        // Flip the last character.
        char[] chars = token.toCharArray();
        chars[chars.length - 1] = chars[chars.length - 1] == 'A' ? 'B' : 'A';
        assertTrue(service.verify(new String(chars)).isEmpty());
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        String foreign = new TicketAccessTokenService("another-secret").mint(7L);
        assertTrue(service.verify(foreign).isEmpty());
    }

    @Test
    void rejectsAnExpiredButOtherwiseValidToken() {
        // Craft a properly-signed token whose expiry is in the past.
        String data = "42:" + (Instant.now().getEpochSecond() - 100);
        String sig = HmacUtils.generateHMAC(data, SECRET + "-ticket-access");
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((data + ":" + sig).getBytes(StandardCharsets.UTF_8));
        assertTrue(service.verify(token).isEmpty());
    }

    @Test
    void rejectsNullBlankAndGarbage() {
        assertTrue(service.verify(null).isEmpty());
        assertTrue(service.verify("   ").isEmpty());
        assertTrue(service.verify("not-a-real-token").isEmpty());
        assertTrue(service.verify("!!!not base64!!!").isEmpty());
    }

    @Test
    void tokenIsUrlSafe() {
        String token = service.mint(999999L);
        // URL-safe base64 (no '+', '/', or padding '=').
        assertFalse(token.contains("+"));
        assertFalse(token.contains("/"));
        assertFalse(token.contains("="));
    }
}
