package com.sm.instagram.platform.e2e.multiuser.auth;

import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.e2e.multiuser.actor.UserSession;
import com.sm.instagram.platform.e2e.support.TotpCodeGenerator;
import io.cucumber.spring.ScenarioScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the real authentication flow for multi-user scenarios.
 * Performs actual Firebase login + token exchange to get real session cookies.
 *
 * <p>This service replicates the authentication flow from FullAuthSteps but
 * returns a standalone UserSession that can be associated with a named Actor.
 *
 * <p>Supports all three user types with their distinct flows:
 * <ul>
 *   <li>COMPANY: Email/Password → Firebase → Token Exchange → Full Session</li>
 *   <li>INFLUENCER: OAuth via Instagram token (from Firestore, KMS decrypted) → Full Session</li>
 *   <li>ADMIN: Email/Password → Firebase → Partial Session → 2FA TOTP → Full Session</li>
 * </ul>
 */
@Service
@ScenarioScope
@Slf4j
public class MultiUserAuthService {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TotpCodeGenerator totpCodeGenerator;

    @Autowired(required = false)
    private TotpFirestoreService totpFirestoreService;

    private String baseUrl;

    /**
     * Sets the base URL for API requests.
     * Must be called before login operations.
     *
     * @param baseUrl the base URL (e.g., "http://localhost:8080/api")
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Performs full login flow for a user and returns populated UserSession.
     *
     * @param alias the actor alias for logging
     * @param firebaseUid the Firebase UID
     * @param email the user's email
     * @param password the user's password
     * @param role the user's role (COMPANY, INFLUENCER, ADMIN)
     * @return a UserSession with real cookies
     */
    public UserSession login(String alias, String firebaseUid, String email, String password, String role) {
        validateBaseUrl();

        UserSession session = new UserSession();
        session.setAlias(alias);
        session.setFirebaseUid(firebaseUid);
        session.setEmail(email);
        session.setRole(role);

        // Step 1: Sync user from Firestore to PostgreSQL
        syncUserFromFirestore(session, firebaseUid, role);

        // Step 2: Firebase login
        performFirebaseLogin(session, email, password);

        // Step 2.5: Seed cookie consent (exchange-token checks consent, returns 451 without it)
        seedCookieConsent(session);

        // Step 3: Exchange for backend session
        exchangeTokenForSession(session);

        log.info("[E2E] Login complete for '{}' as {} - {} session",
            alias, role, session.isAuthenticated() ? "full" : "partial");
        return session;
    }

    /**
     * Completes 2FA verification for admin users.
     *
     * @param session the session with partial cookies
     * @param totpCode the 6-digit TOTP code
     */
    public void complete2FA(UserSession session, String totpCode) {
        validateBaseUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Add partial session cookies
        if (session.getPartialSessionCookie() != null) {
            headers.add("Cookie", "partialSession=" + session.getPartialSessionCookie());
        }
        if (session.getPartialSessionSigCookie() != null) {
            headers.add("Cookie", "partialSessionSig=" + session.getPartialSessionSigCookie());
        }

        // Add Firebase cookies (may be needed for verification)
        if (session.getFirebaseIdToken() != null) {
            headers.add("Cookie", "FirebaseIdToken=" + session.getFirebaseIdToken());
        }
        if (session.getFirebaseIdTokenSig() != null) {
            headers.add("Cookie", "FirebaseIdToken_sig=" + session.getFirebaseIdTokenSig());
        }

        Map<String, String> request = Map.of("code", totpCode);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/twofactor/verify",
                entity,
                Map.class
            );

            session.setLastResponse(response);
            session.setTotpVerified(response.getStatusCode().is2xxSuccessful());

            // Extract full session cookies after 2FA
            extractSessionCookies(response, session);

            // IMPORTANT: Add small delay for Firebase claims propagation.
            // Firebase custom claims have eventual consistency - after setCustomUserClaims(),
            // subsequent getUser() calls may return stale data for a few hundred milliseconds.
            // This delay prevents race conditions in multi-actor tests where multiple admins
            // authenticate concurrently.
            if (session.isTotpVerified()) {
                try {
                    Thread.sleep(500); // 500ms delay for Firebase claims propagation
                    log.debug("[E2E] Added 500ms delay for Firebase claims propagation after 2FA");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            log.info("[E2E] 2FA completed for '{}': verified={}, authenticated={}",
                session.getAlias(), session.isTotpVerified(), session.isAuthenticated());
        } catch (HttpClientErrorException e) {
            log.error("[E2E] 2FA failed for '{}': {} - {}",
                session.getAlias(), e.getStatusCode(), e.getResponseBodyAsString());
            session.setLastResponse(ResponseEntity.status(e.getStatusCode()).body(
                Map.of("error", e.getStatusText(), "message", e.getResponseBodyAsString())));
            throw new RuntimeException("2FA verification failed: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // INFLUENCER OAuth Flow
    // =========================================================================

    /**
     * Login for INFLUENCER using OAuth simulation (NOT email/password).
     * Uses Instagram token from Firestore (auto-decrypted via KMS).
     *
     * <p>This is the correct flow for influencers - they don't use email/password
     * but instead authenticate via Instagram OAuth which retrieves their token
     * from Firestore.
     *
     * @param alias the actor alias for logging
     * @param firebaseUid the Firebase UID (used to find Instagram data in Firestore)
     * @return a UserSession with real OAuth session cookies
     */
    public UserSession loginInfluencerWithOAuth(String alias, String firebaseUid) {
        validateBaseUrl();

        UserSession session = new UserSession();
        session.setAlias(alias);
        session.setFirebaseUid(firebaseUid);
        session.setRole("INFLUENCER");
        session.setOauth(true);
        session.setProvider("instagram");

        // Step 1: Sync user from Firestore (with Instagram data)
        syncUserFromFirestore(session, firebaseUid, "INFLUENCER");

        // Step 2: Call OAuth simulation endpoint (NOT Firebase login!)
        simulateInfluencerOAuth(session, firebaseUid);

        log.info("[E2E] Influencer OAuth login complete for '{}' - {} session",
            alias, session.isAuthenticated() ? "full" : "failed");
        return session;
    }

    /**
     * Simulates OAuth login for influencer using Instagram token from Firestore.
     */
    private void simulateInfluencerOAuth(UserSession session, String firebaseUid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of("firebaseUid", firebaseUid);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/test/auth/simulate-influencer-oauth",
                entity,
                Map.class
            );

            session.setLastResponse(response);
            extractSessionCookies(response, session);

            // Extract OAuth-specific data from response
            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body.get("instagramUserId") != null) {
                    session.setInstagramUserId((String) body.get("instagramUserId"));
                }
                if (body.get("instagramUsername") != null) {
                    session.setInstagramUsername((String) body.get("instagramUsername"));
                }
                if (body.get("socialConnectionId") != null) {
                    session.setSocialConnectionId(((Number) body.get("socialConnectionId")).longValue());
                }
            }

            log.info("[E2E] OAuth simulation for '{}': status={}, hasSession={}",
                session.getAlias(), response.getStatusCode(), session.isAuthenticated());
        } catch (HttpClientErrorException e) {
            log.error("[E2E] OAuth simulation failed for '{}': {} - {}",
                session.getAlias(), e.getStatusCode(), e.getResponseBodyAsString());
            session.setLastResponse(ResponseEntity.status(e.getStatusCode()).body(
                Map.of("error", e.getStatusText())));
            throw new RuntimeException("OAuth simulation failed: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // ADMIN 2FA Flow with Auto-Completion
    // =========================================================================

    /**
     * Login for ADMIN with automatic 2FA completion using KMS-decrypted TOTP.
     *
     * <p>This method performs the complete admin login flow including:
     * <ol>
     *   <li>Sync user from Firestore</li>
     *   <li>Firebase login</li>
     *   <li>Token exchange (gets partial session)</li>
     *   <li>TOTP secret retrieval from Firestore (KMS decrypted)</li>
     *   <li>TOTP code generation</li>
     *   <li>2FA verification</li>
     *   <li>Full session acquisition</li>
     * </ol>
     *
     * @param alias the actor alias for logging
     * @param firebaseUid the Firebase UID
     * @param email the admin's email
     * @param password the admin's password
     * @return a UserSession with real full session cookies after 2FA
     */
    public UserSession loginAdminWithAuto2FA(String alias, String firebaseUid, String email, String password) {
        validateBaseUrl();

        // Steps 1-3: Standard login flow (gets partial session for admin)
        UserSession session = login(alias, firebaseUid, email, password, "ADMIN");

        // Step 4: If partial session (2FA required), complete it automatically
        if (session.hasPartialSession() && !session.isAuthenticated()) {
            complete2FAWithFirestoreSecret(session);
        }

        log.info("[E2E] Admin auto-2FA login complete for '{}': authenticated={}",
            alias, session.isAuthenticated());
        return session;
    }

    /**
     * Completes 2FA by decrypting TOTP secret from Firestore via KMS and generating code.
     * Includes retry logic for TOTP time boundary failures.
     *
     * @param session the session with partial cookies and firebaseUid
     */
    public void complete2FAWithFirestoreSecret(UserSession session) {
        String firebaseUid = session.getFirebaseUid();

        // Step 4a: Decrypt TOTP secret from Firestore via KMS
        String plainSecret;
        if (totpFirestoreService != null) {
            try {
                plainSecret = totpFirestoreService.getTotpSecret(firebaseUid);
                if (plainSecret == null) {
                    throw new IllegalStateException("TOTP secret not found in Firestore for: " + firebaseUid);
                }
                log.info("[E2E] TOTP secret decrypted from Firestore for '{}'", session.getAlias());
            } catch (Exception e) {
                throw new RuntimeException("Failed to decrypt TOTP secret from Firestore: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalStateException("TotpFirestoreService not available for 2FA");
        }

        // Step 4b: Generate and submit TOTP code with retry logic for time boundary failures
        // TOTP codes are valid for 30-second windows. If we generate a code near the boundary,
        // it may fail because the server has already moved to the next window.
        int maxRetries = 2;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Generate fresh TOTP code for each attempt
                int codeInt = totpCodeGenerator.generateTotpCode(plainSecret);
                String totpCode = String.format("%06d", codeInt);
                session.setTotpCode(totpCode);
                log.info("[E2E] TOTP code generated for '{}' (attempt {}/{})",
                    session.getAlias(), attempt, maxRetries);

                // Submit TOTP code
                complete2FA(session, totpCode);

                // If we get here without exception, 2FA succeeded
                if (session.isTotpVerified()) {
                    log.info("[E2E] TOTP verification succeeded for '{}' on attempt {}",
                        session.getAlias(), attempt);
                    break;
                }
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("[E2E] TOTP verification failed for '{}' on attempt {}: {}",
                    session.getAlias(), attempt, e.getMessage());

                if (attempt < maxRetries) {
                    // Wait until next TOTP window (30 seconds) before retry
                    // Calculate time until next window to minimize wait time
                    long currentTimeSeconds = System.currentTimeMillis() / 1000;
                    long secondsIntoCurrentWindow = currentTimeSeconds % 30;
                    long waitTime = (31 - secondsIntoCurrentWindow) * 1000; // Wait until start of next window + 1s buffer

                    log.info("[E2E] Waiting {}ms until next TOTP window for retry...", waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during TOTP retry wait", ie);
                    }
                }
            }
        }

        // If still not verified after retries, throw the last exception
        if (!session.isTotpVerified() && lastException != null) {
            throw new RuntimeException("2FA verification failed after " + maxRetries +
                " attempts: " + lastException.getMessage(), lastException);
        }

        // Step 5: Exchange for full session (if not already done by complete2FA)
        if (!session.isAuthenticated() && session.isTotpVerified()) {
            exchangeTokenForSession(session);
        }

        log.info("[E2E] Admin 2FA auto-completed for '{}': authenticated={}",
            session.getAlias(), session.isAuthenticated());
    }

    // =========================================================================
    // User Sync
    // =========================================================================

    /**
     * Syncs user from Firestore to local PostgreSQL (Testcontainers).
     * This ensures the user exists in the test database before login.
     * Also forces Firebase custom claims to ensure JWT role is correct.
     */
    private void syncUserFromFirestore(UserSession session, String firebaseUid, String role) {
        // STEP 1: Force Firebase custom claims FIRST (JWT role comes from Firebase claims!)
        // This ensures the token exchange will use the correct role.
        forceFirebaseClaims(firebaseUid, role);

        // STEP 2: Sync user from Firestore to PostgreSQL
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> syncRequest = new HashMap<>();
        syncRequest.put("firebaseUid", firebaseUid);
        syncRequest.put("role", role);
        syncRequest.put("syncInstagramData", "INFLUENCER".equals(role));
        // Pass canonical email so the sync endpoint can self-heal Firebase Auth
        // if a previous test changed it (defense against cross-suite pollution)
        if (session.getEmail() != null) {
            syncRequest.put("email", session.getEmail());
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(syncRequest, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/test/auth/sync-user-from-firestore",
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body.get("userId") != null) {
                    session.setUserId(((Number) body.get("userId")).longValue());
                }
                // Propagate email from sync response to session (critical for OAuth flow
                // where session.email is null — enables self-healing on subsequent syncs)
                if (body.get("email") != null) {
                    session.setEmail((String) body.get("email"));
                }
                boolean created = Boolean.TRUE.equals(body.get("created"));
                log.info("[E2E] User synced: {} ({}) - {}",
                    firebaseUid, role, created ? "created" : "existing");
            }
        } catch (HttpClientErrorException e) {
            log.warn("[E2E] User sync failed for {} ({}): {} - continuing gracefully",
                firebaseUid, role, e.getStatusCode());
        } catch (Exception e) {
            log.warn("[E2E] User sync failed for {} ({}): {} - continuing gracefully",
                firebaseUid, role, e.getMessage());
        }
    }

    /**
     * Forces Firebase custom claims for a user to ensure JWT role is correct.
     * This is critical because JWT role comes from Firebase claims, NOT the PostgreSQL database.
     *
     * @param firebaseUid the Firebase UID
     * @param role the expected role (COMPANY, INFLUENCER, ADMIN)
     */
    private void forceFirebaseClaims(String firebaseUid, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
            "firebaseUid", firebaseUid,
            "role", role
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/test/auth/force-firebase-claims",
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[E2E] Firebase claims forced for {} with role {}", firebaseUid, role);
            } else {
                log.warn("[E2E] Force Firebase claims returned non-success status: {}",
                    response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.warn("[E2E] Force Firebase claims failed for {}: {} - continuing",
                firebaseUid, e.getStatusCode());
        } catch (Exception e) {
            log.warn("[E2E] Force Firebase claims failed for {}: {} - continuing",
                firebaseUid, e.getMessage());
        }
    }

    /**
     * Performs Firebase login using email/password.
     */
    private void performFirebaseLogin(UserSession session, String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
            "email", email,
            "password", password
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/auth/firebase/login",
                entity,
                Map.class
            );

            session.setLastResponse(response);
            extractFirebaseCookies(response, session);

            // Extract uid from response body if present
            if (response.getBody() != null && response.getBody().containsKey("uid")) {
                String uid = (String) response.getBody().get("uid");
                if (session.getFirebaseUid() == null || session.getFirebaseUid().isEmpty()) {
                    session.setFirebaseUid(uid);
                }
            }

            log.info("[E2E] Firebase login for '{}': status={}, hasToken={}",
                session.getAlias(), response.getStatusCode(), session.hasFirebaseToken());
        } catch (HttpClientErrorException e) {
            log.error("[E2E] Firebase login failed for '{}': {} - {}",
                session.getAlias(), e.getStatusCode(), e.getResponseBodyAsString());
            session.setLastResponse(ResponseEntity.status(e.getStatusCode()).body(
                Map.of("error", e.getStatusText(), "message", e.getResponseBodyAsString())));
            throw new RuntimeException("Firebase login failed: " + e.getMessage(), e);
        }
    }

    /**
     * Seeds a COOKIE_POLICY consent record for the user so exchange-token doesn't return 451.
     */
    private void seedCookieConsent(UserSession session) {
        if (session.getEmail() == null) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> request = Map.of("email", session.getEmail());
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            restTemplate.postForEntity(baseUrl + "/test/legal/seed-cookie-consent", entity, Map.class);
            log.debug("[E2E] Cookie consent seeded for '{}'", session.getAlias());
        } catch (Exception e) {
            log.warn("[E2E] Cookie consent seeding failed for '{}': {} - continuing", session.getAlias(), e.getMessage());
        }
    }

    /**
     * Exchanges Firebase token for backend session cookies.
     */
    private void exchangeTokenForSession(UserSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Add Firebase cookies
        if (session.getFirebaseIdToken() != null) {
            headers.add("Cookie", "FirebaseIdToken=" + session.getFirebaseIdToken());
        }
        if (session.getFirebaseIdTokenSig() != null) {
            headers.add("Cookie", "FirebaseIdToken_sig=" + session.getFirebaseIdTokenSig());
        }

        Map<String, Object> requestBody = Map.of("expirationDays", 7);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/auth/exchange-token",
                entity,
                Map.class
            );

            session.setLastResponse(response);
            extractSessionCookies(response, session);

            // Check if 2FA is required (admin)
            if (response.getBody() != null) {
                Boolean requires2FA = (Boolean) response.getBody().get("requires2FA");
                if (Boolean.TRUE.equals(requires2FA)) {
                    log.info("[E2E] Token exchange for '{}': 2FA required", session.getAlias());
                }
            }

            log.info("[E2E] Token exchange for '{}': status={}, authenticated={}",
                session.getAlias(), response.getStatusCode(), session.isAuthenticated());
        } catch (HttpClientErrorException e) {
            log.error("[E2E] Token exchange failed for '{}': {} - {}",
                session.getAlias(), e.getStatusCode(), e.getResponseBodyAsString());
            session.setLastResponse(ResponseEntity.status(e.getStatusCode()).body(
                Map.of("error", e.getStatusText(), "message", e.getResponseBodyAsString())));
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts Firebase token cookies from response.
     */
    private void extractFirebaseCookies(ResponseEntity<?> response, UserSession session) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("FirebaseIdToken=") && !cookie.startsWith("FirebaseIdToken_sig=")) {
                    session.setFirebaseIdToken(extractCookieValue(cookie));
                } else if (cookie.startsWith("FirebaseIdToken_sig=")) {
                    session.setFirebaseIdTokenSig(extractCookieValue(cookie));
                }
            }
        }
    }

    /**
     * Extracts session cookies from response.
     */
    private void extractSessionCookies(ResponseEntity<?> response, UserSession session) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session=") && !cookie.startsWith("session_sig=")) {
                    session.setSessionCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("session_sig=")) {
                    session.setSessionSigCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("partialSession=") && !cookie.startsWith("partialSessionSig=")) {
                    session.setPartialSessionCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("partialSessionSig=")) {
                    session.setPartialSessionSigCookie(extractCookieValue(cookie));
                }
            }
        }
    }

    /**
     * Extracts cookie value from Set-Cookie header.
     */
    private String extractCookieValue(String cookie) {
        int equalsIndex = cookie.indexOf('=');
        int semicolonIndex = cookie.indexOf(';');
        if (semicolonIndex == -1) {
            return cookie.substring(equalsIndex + 1);
        }
        return cookie.substring(equalsIndex + 1, semicolonIndex);
    }

    /**
     * Validates that baseUrl has been set.
     */
    private void validateBaseUrl() {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException(
                "Base URL not set. Call setBaseUrl() before login operations.");
        }
    }
}
