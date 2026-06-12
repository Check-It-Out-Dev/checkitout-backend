package com.sm.instagram.platform.legal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.filter.HmacUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HMAC-signed cookie management for legal consent proof.
 * Uses a DEDICATED HMAC secret separate from the session cookie HMAC secret
 * to prevent cross-contamination and allow independent rotation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentCookieService {

    private final ObjectMapper objectMapper;

    @Value("${consent.hmac-secret}")
    private String consentHmacSecret;

    @Value("${jwt.cookie.domain:localhost}")
    private String cookieDomain;

    @Value("${jwt.cookie.secure:true}")
    private boolean secureCookies;

    /** Cookie names for each consent type */
    public static final String COOKIE_CONSENT_COOKIE_POLICY = "consent_cookie_policy";
    public static final String COOKIE_CONSENT_TERMS_OF_SERVICE = "consent_terms_of_service";
    public static final String COOKIE_CONSENT_PRIVACY_POLICY = "consent_privacy_policy";
    private static final String SIG_SUFFIX = "_sig";
    private static final int MAX_AGE_SECONDS = 3600; // 1 hour for registration cookies

    /** Category banner cookies (1 year TTL) */
    public static final String CATEGORY_COOKIE_PREFIX = "consent_cat_";
    private static final int CATEGORY_MAX_AGE_SECONDS = 31536000; // 1 year

    private static final List<String> ALL_CONSENT_COOKIES = List.of(
            COOKIE_CONSENT_COOKIE_POLICY,
            COOKIE_CONSENT_TERMS_OF_SERVICE,
            COOKIE_CONSENT_PRIVACY_POLICY
    );

    /**
     * Set an HMAC-signed consent cookie.
     * Uses SameSite=Lax so cookies survive OAuth redirects from Instagram.
     */
    public void setConsentCookie(HttpServletResponse response, String name, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String encoded = URLEncoder.encode(json, StandardCharsets.UTF_8);
            String hmac = HmacUtils.generateHMAC(encoded, consentHmacSecret);

            addCookieHeader(response, name, encoded, MAX_AGE_SECONDS);
            addCookieHeader(response, name + SIG_SUFFIX, hmac, MAX_AGE_SECONDS);

            log.debug("Set consent cookie: {}", name);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize consent cookie payload for {}", name, e);
            throw new IllegalStateException("Cannot serialize consent cookie payload", e);
        }
    }

    /**
     * Read and validate an HMAC-signed consent cookie.
     * Returns the deserialized payload or null if missing/invalid.
     */
    public <T> T readConsentCookie(HttpServletRequest request, String name, Class<T> type) {
        String value = getCookieValue(request, name);
        String signature = getCookieValue(request, name + SIG_SUFFIX);

        if (value == null || signature == null) {
            log.debug("Consent cookie '{}' not found or missing signature", name);
            return null;
        }

        if (!HmacUtils.validateHMAC(value, signature, consentHmacSecret)) {
            log.warn("SECURITY: Invalid HMAC signature for consent cookie '{}'", name);
            return null;
        }

        try {
            String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
            return objectMapper.readValue(decoded, type);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize consent cookie '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Validates that all required consent cookies are present and HMAC-valid.
     * Called before registration to fail fast on missing consents.
     * @throws ValidationTranslatableException if any required cookie is missing/invalid
     */
    public void validateConsentCookiesPresent(HttpServletRequest request) {
        String[] requiredCookies = {
            COOKIE_CONSENT_COOKIE_POLICY,
            COOKIE_CONSENT_TERMS_OF_SERVICE,
            COOKIE_CONSENT_PRIVACY_POLICY
        };

        List<String> missing = new ArrayList<>();
        for (String cookieName : requiredCookies) {
            ConsentProofPayload payload = readConsentCookie(request, cookieName, ConsentProofPayload.class);
            if (payload == null) missing.add(cookieName);
        }

        if (!missing.isEmpty()) {
            log.warn("Missing consent cookies during registration: {}", missing);
            throw new ValidationTranslatableException("error.consent.missing_consents");
        }
    }

    /**
     * Clear a single consent cookie (both value and signature) from the response.
     */
    public void clearConsentCookie(HttpServletResponse response, String cookieName) {
        clearCookie(response, cookieName);
        clearCookie(response, cookieName + SIG_SUFFIX);
        log.debug("Cleared consent cookie: {}", cookieName);
    }

    /**
     * Clear all consent cookies from the response.
     */
    public void clearAllConsentCookies(HttpServletResponse response) {
        for (String cookieName : ALL_CONSENT_COOKIES) {
            clearCookie(response, cookieName);
            clearCookie(response, cookieName + SIG_SUFFIX);
        }
        log.debug("Cleared all consent cookies");
    }

    /**
     * Get the cookie name for a banner category (e.g., "consent_cat_ANALYTICS").
     */
    public static String categoryBannerCookieName(String categoryType) {
        return CATEGORY_COOKIE_PREFIX + categoryType;
    }

    /**
     * Set a category consent cookie (1 year TTL, HMAC-signed).
     */
    public void setCategoryCookie(HttpServletResponse response, String name, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String encoded = URLEncoder.encode(json, StandardCharsets.UTF_8);
            String hmac = HmacUtils.generateHMAC(encoded, consentHmacSecret);

            addCookieHeader(response, name, encoded, CATEGORY_MAX_AGE_SECONDS);
            addCookieHeader(response, name + SIG_SUFFIX, hmac, CATEGORY_MAX_AGE_SECONDS);

            log.debug("Set category cookie: {}", name);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize category cookie payload for {}", name, e);
            throw new IllegalStateException("Cannot serialize category cookie payload", e);
        }
    }

    /**
     * Map a LegalDocumentType to its corresponding cookie name.
     */
    public static String cookieNameForType(LegalDocumentType type) {
        return switch (type) {
            case COOKIE_POLICY -> COOKIE_CONSENT_COOKIE_POLICY;
            case TERMS_OF_SERVICE -> COOKIE_CONSENT_TERMS_OF_SERVICE;
            case PRIVACY_POLICY -> COOKIE_CONSENT_PRIVACY_POLICY;
            case SUBSCRIPTION_ACTIVATION_CONSENT -> throw new IllegalArgumentException(
                    "SUBSCRIPTION_ACTIVATION_CONSENT is not cookie-based; collected at subscription purchase time");
            case DATA_RETENTION_POLICY -> throw new IllegalArgumentException(
                    "DATA_RETENTION_POLICY is not cookie-based; informational document only");
        };
    }

    private void addCookieHeader(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        boolean isLocalDev = "localhost".equals(cookieDomain) && !secureCookies;
        String secureFlag = isLocalDev ? "" : "; Secure";

        String domain = "";
        if (!"localhost".equals(cookieDomain) && cookieDomain != null && !cookieDomain.isEmpty()) {
            domain = String.format("; Domain=%s", cookieDomain);
        }

        // SameSite=Lax is critical: Strict cookies are not sent on the redirect back from Instagram OAuth
        String cookieString = String.format(
                "%s=%s; Max-Age=%d; Path=/; HttpOnly%s; SameSite=Lax%s",
                name, value, maxAgeSeconds, secureFlag, domain
        );

        response.addHeader("Set-Cookie", cookieString);
    }

    private void clearCookie(HttpServletResponse response, String name) {
        boolean isLocalDev = "localhost".equals(cookieDomain) && !secureCookies;
        String secureFlag = isLocalDev ? "" : "; Secure";

        String domain = "";
        if (!"localhost".equals(cookieDomain) && cookieDomain != null && !cookieDomain.isEmpty()) {
            domain = String.format("; Domain=%s", cookieDomain);
        }

        String cookieString = String.format(
                "%s=; Max-Age=0; Path=/; HttpOnly%s; SameSite=Lax%s",
                name, secureFlag, domain
        );

        response.addHeader("Set-Cookie", cookieString);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
