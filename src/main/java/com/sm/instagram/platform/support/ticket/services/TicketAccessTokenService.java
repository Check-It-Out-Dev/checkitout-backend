package com.sm.instagram.platform.support.ticket.services;

import com.sm.instagram.platform.auth.filter.HmacUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Mints and verifies signed, expiring access tokens for anonymous ticket
 * access ("magic links").
 *
 * <p>A token is an HMAC-SHA256 signature over {@code ticketId:expiry}, so it
 * is <strong>unforgeable</strong> — knowing a ticket id (or the human-readable
 * reference) is not enough to access a ticket. This is strictly stronger than
 * a random reference in a URL: enumeration/brute-force is impossible, and the
 * token <strong>expires</strong>, bounding the damage of a leaked link. Tokens
 * are reusable until expiry (bookmark / multi-device friendly).
 *
 * <p>Security context (pentest 3.2/3.3): intended to replace the leak-prone
 * reference-in-URL as the primary anonymous access credential.
 */
@Slf4j
@Service
public class TicketAccessTokenService {

    /** Domain separation — never reuse the cookie-signing key verbatim. */
    private static final String KEY_CONTEXT = "-ticket-access";
    private static final long TOKEN_TTL_SECONDS = 14L * 24 * 60 * 60; // 14 days

    private final String signingKey;

    public TicketAccessTokenService(@Value("${cookie.hmac.secret}") String cookieHmacSecret) {
        this.signingKey = cookieHmacSecret + KEY_CONTEXT;
    }

    /**
     * Mint a token granting access to {@code ticketId}, valid for 14 days.
     */
    public String mint(Long ticketId) {
        long exp = Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS;
        String data = ticketId + ":" + exp;
        String sig = HmacUtils.generateHMAC(data, signingKey);
        String raw = data + ":" + sig;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verify a token and return the ticket id it grants.
     *
     * @return the ticket id, or empty if the token is malformed, tampered
     *         with, or expired
     */
    public Optional<Long> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        final String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(token.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // data is "ticketId:exp"; sig (base64) contains no ':' so a 3-way
        // split is unambiguous.
        String[] parts = raw.split(":", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        String data = parts[0] + ":" + parts[1];
        if (!HmacUtils.validateHMAC(data, parts[2], signingKey)) {
            log.warn("SECURITY: Rejected ticket access token with an invalid signature");
            return Optional.empty();
        }
        final long ticketId;
        final long exp;
        try {
            ticketId = Long.parseLong(parts[0]);
            exp = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (exp < Instant.now().getEpochSecond()) {
            return Optional.empty(); // expired
        }
        return Optional.of(ticketId);
    }
}
