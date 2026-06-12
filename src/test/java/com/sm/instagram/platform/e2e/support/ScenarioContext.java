package com.sm.instagram.platform.e2e.support;

import io.cucumber.spring.ScenarioScope;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Scenario context for sharing state between Cucumber step definitions.
 * This class is scoped to a single scenario, meaning it's reset between scenarios.
 *
 * <p>Use this to store:
 * <ul>
 *   <li>Current user context (email, role)</li>
 *   <li>Session cookies</li>
 *   <li>Response from last HTTP request</li>
 *   <li>Any scenario-specific state</li>
 * </ul>
 */
@Data
@Component
@ScenarioScope
public class ScenarioContext {

    // Current user context
    private String currentEmail;
    private String currentRole;
    private String currentFirebaseUid;
    private Long currentUserId;

    // Firebase auth tokens (from /auth/firebase/login)
    private String firebaseIdToken;
    private String firebaseIdTokenSig;

    // Session cookies
    private String sessionCookie;
    private String sessionSigCookie;
    private String partialSessionCookie;
    private String partialSessionSigCookie;

    // TOTP
    private String totpCode;
    private boolean totpVerified;

    // Last response
    private ResponseEntity<?> lastResponse;

    // Generic storage for custom data
    private final Map<String, Object> customData = new HashMap<>();

    /**
     * Gets a cookie by name.
     *
     * @param cookieName Name of the cookie
     * @return Cookie value or null
     */
    public String getCookie(String cookieName) {
        return switch (cookieName) {
            case "session" -> sessionCookie;
            case "session_sig" -> sessionSigCookie;
            case "partialSession" -> partialSessionCookie;
            case "partialSessionSig" -> partialSessionSigCookie;
            default -> null;
        };
    }

    /**
     * Sets a cookie by name.
     *
     * @param cookieName Name of the cookie
     * @param value Cookie value
     */
    public void setCookie(String cookieName, String value) {
        switch (cookieName) {
            case "session" -> sessionCookie = value;
            case "session_sig" -> sessionSigCookie = value;
            case "partialSession" -> partialSessionCookie = value;
            case "partialSessionSig" -> partialSessionSigCookie = value;
        }
    }

    /**
     * Clears all session cookies.
     */
    public void clearCookies() {
        sessionCookie = null;
        sessionSigCookie = null;
        partialSessionCookie = null;
        partialSessionSigCookie = null;
        firebaseIdToken = null;
        firebaseIdTokenSig = null;
    }

    /**
     * Stores custom data in the context.
     *
     * @param key Data key
     * @param value Data value
     */
    public void put(String key, Object value) {
        customData.put(key, value);
    }

    /**
     * Retrieves custom data from the context.
     *
     * @param key Data key
     * @param <T> Expected type
     * @return Data value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) customData.get(key);
    }

    /**
     * Retrieves custom data with a default value.
     *
     * @param key Data key
     * @param defaultValue Default value if key not found
     * @param <T> Expected type
     * @return Data value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) customData.getOrDefault(key, defaultValue);
    }

    /**
     * Checks if the context has a valid session.
     *
     * @return true if session cookies are set
     */
    public boolean hasSession() {
        return sessionCookie != null && sessionSigCookie != null;
    }

    /**
     * Checks if the context has a partial session (2FA pending).
     *
     * @return true if partial session cookies are set
     */
    public boolean hasPartialSession() {
        return partialSessionCookie != null && partialSessionSigCookie != null;
    }

    /**
     * Resets all context state.
     * Called automatically between scenarios by Cucumber.
     */
    public void reset() {
        currentEmail = null;
        currentRole = null;
        currentFirebaseUid = null;
        currentUserId = null;
        firebaseIdToken = null;
        firebaseIdTokenSig = null;
        clearCookies();
        totpCode = null;
        totpVerified = false;
        lastResponse = null;
        customData.clear();
    }
}
