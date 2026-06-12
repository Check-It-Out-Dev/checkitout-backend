package com.sm.instagram.platform.e2e.multiuser.actor;

import lombok.Data;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds real session state for a single actor.
 * Contains actual cookies from real Firebase/backend authentication.
 *
 * <p>This class mirrors the session fields from ScenarioContext but is
 * designed to be held per-actor in the ActorRegistry, enabling multiple
 * users to be logged in simultaneously with isolated sessions.
 *
 * <p>Supports all three user types:
 * <ul>
 *   <li>COMPANY - Email/Password → Full Session</li>
 *   <li>INFLUENCER - OAuth via Instagram token → Full Session</li>
 *   <li>ADMIN - Email/Password → Partial Session → 2FA → Full Session</li>
 * </ul>
 */
@Data
public class UserSession {

    // Identity (from step parameters)
    private String alias;
    private String firebaseUid;
    private String email;
    private String role;  // COMPANY, INFLUENCER, ADMIN
    private Long userId;

    // Firebase auth tokens (from real /auth/firebase/login)
    private String firebaseIdToken;
    private String firebaseIdTokenSig;

    // Session cookies (from real /auth/exchange-token)
    private String sessionCookie;
    private String sessionSigCookie;

    // Partial session for 2FA (Admin only)
    private String partialSessionCookie;
    private String partialSessionSigCookie;

    // TOTP state (Admin only)
    private boolean totpVerified;
    private String totpCode;  // Stored generated TOTP code

    // OAuth-specific fields (Influencer)
    private String instagramUserId;
    private String instagramUsername;
    private Long socialConnectionId;
    private boolean oauth;
    private String provider;  // "instagram" for influencers

    // Custom data storage for scenario-specific data
    private final Map<String, Object> customData = new HashMap<>();

    // Last response for assertions
    private ResponseEntity<?> lastResponse;

    /**
     * Builds HTTP headers with real session cookies for authenticated requests.
     *
     * @return HttpHeaders with Content-Type and session cookies
     */
    public HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add("Cookie", "session=" + sessionCookie);
        }
        if (sessionSigCookie != null) {
            headers.add("Cookie", "session_sig=" + sessionSigCookie);
        }
        return headers;
    }

    /**
     * Builds HTTP headers with Firebase cookies (for token exchange).
     *
     * @return HttpHeaders with Firebase token cookies
     */
    public HttpHeaders buildFirebaseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (firebaseIdToken != null) {
            headers.add("Cookie", "FirebaseIdToken=" + firebaseIdToken);
        }
        if (firebaseIdTokenSig != null) {
            headers.add("Cookie", "FirebaseIdToken_sig=" + firebaseIdTokenSig);
        }
        return headers;
    }

    /**
     * Builds HTTP headers with partial session cookies (for 2FA verification).
     *
     * @return HttpHeaders with partial session cookies
     */
    public HttpHeaders buildPartialSessionHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (partialSessionCookie != null) {
            headers.add("Cookie", "partialSession=" + partialSessionCookie);
        }
        if (partialSessionSigCookie != null) {
            headers.add("Cookie", "partialSessionSig=" + partialSessionSigCookie);
        }
        return headers;
    }

    /**
     * Checks if this session has valid full authentication.
     *
     * @return true if both session and session_sig cookies are present
     */
    public boolean isAuthenticated() {
        return sessionCookie != null && sessionSigCookie != null;
    }

    /**
     * Checks if this session has partial authentication (2FA pending).
     *
     * @return true if partial session cookies are present
     */
    public boolean hasPartialSession() {
        return partialSessionCookie != null && partialSessionSigCookie != null;
    }

    /**
     * Checks if Firebase authentication succeeded.
     *
     * @return true if Firebase ID token is present
     */
    public boolean hasFirebaseToken() {
        return firebaseIdToken != null;
    }

    /**
     * Clears all session state.
     */
    public void clearSession() {
        sessionCookie = null;
        sessionSigCookie = null;
        partialSessionCookie = null;
        partialSessionSigCookie = null;
        firebaseIdToken = null;
        firebaseIdTokenSig = null;
        totpVerified = false;
        totpCode = null;
        instagramUserId = null;
        instagramUsername = null;
        socialConnectionId = null;
        oauth = false;
        provider = null;
        customData.clear();
    }

    /**
     * Stores custom data for this actor's scenario.
     *
     * @param key the data key
     * @param value the data value
     */
    public void put(String key, Object value) {
        customData.put(key, value);
    }

    /**
     * Retrieves custom data for this actor's scenario.
     *
     * @param key the data key
     * @param <T> the expected type
     * @return the stored value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) customData.get(key);
    }

    /**
     * Retrieves custom data with a default value.
     *
     * @param key the data key
     * @param defaultValue default if key not found
     * @param <T> the expected type
     * @return the stored value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) customData.getOrDefault(key, defaultValue);
    }
}
