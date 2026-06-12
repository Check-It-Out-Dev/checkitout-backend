package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import com.sm.instagram.platform.e2e.support.TotpCodeGenerator;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for full authentication flows (Company + Admin).
 * Uses Cucumber Scenario Outline + Examples pattern for credentials.
 *
 * <p>All three user types follow the SAME graceful sync pattern:
 * <ol>
 *   <li>Try to sync user from Firestore to local PostgreSQL</li>
 *   <li>If user exists - synced successfully, continue</li>
 *   <li>If sync fails - log warning, continue gracefully (don't fail test)</li>
 *   <li>Store Firebase UID in context for later use</li>
 *   <li>Continue with authentication flow</li>
 * </ol>
 *
 * @see <a href="https://toolsqa.com/cucumber/data-driven-testing-in-cucumber/">Cucumber Data-Driven Testing</a>
 */
@Slf4j
public class FullAuthSteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private TotpCodeGenerator totpCodeGenerator;

    @Autowired
    private TotpFirestoreService totpFirestoreService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Company Auth ====================

    @Given("a company user with Firebase UID {string} is synced from Firestore")
    public void companyUserSyncedFromFirestore(String firebaseUid) {
        // Same graceful sync pattern as Influencer - calls /test/auth/sync-user-from-firestore
        syncUserFromFirestore(firebaseUid, "COMPANY", false);
    }

    @When("I login as company with email {string} and password {string}")
    public void loginAsCompanyWithCredentials(String email, String password) {
        performFirebaseLogin(email, password, "COMPANY");
    }

    /**
     * Attempts company login, expecting it to fail with 4xx error.
     * Captures the error response for assertion.
     */
    @When("I attempt to login as company with email {string} and password {string}")
    public void attemptLoginAsCompanyWithCredentials(String email, String password) {
        attemptFirebaseLogin(email, password, "COMPANY");
    }

    @When("I exchange the Firebase token for a backend session")
    public void exchangeFirebaseTokenForSession() {
        // Seed cookie consent before exchange (consent check blocks login with 451)
        seedCookieConsent();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Add Firebase token cookies from previous login
        addFirebaseCookies(headers);

        // Send empty TokenExchangeRequest - idToken is read from FirebaseIdToken cookie
        Map<String, Object> requestBody = Map.of("expirationDays", 7);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/auth/exchange-token"),
                entity,
                Map.class
            );

            context.setLastResponse(response);
            extractSessionCookiesFromResponse(response);

            // Check if this is a partial session (2FA required)
            if (response.getBody() != null) {
                Boolean requires2FA = (Boolean) response.getBody().get("requires2FA");
                context.put("requires2FA", requires2FA);
            }

            log.info("[E2E] Token exchange: status={}, requires2FA={}",
                response.getStatusCode(), context.get("requires2FA"));
        } catch (Exception e) {
            log.error("[E2E] Token exchange failed", e);
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    @Then("the Firebase authentication should succeed")
    public void verifyFirebaseAuthSucceeded() {
        assertThat(context.getFirebaseIdToken())
            .as("Firebase ID token should be set")
            .isNotNull()
            .isNotEmpty();
    }

    // ==================== Admin 2FA Auth ====================

    @Given("an admin user with Firebase UID {string} is synced from Firestore")
    public void adminUserSyncedFromFirestore(String firebaseUid) {
        // Same graceful sync pattern as Influencer - calls /test/auth/sync-user-from-firestore
        syncUserFromFirestore(firebaseUid, "ADMIN", false);
    }

    @Given("the admin has TOTP configured in Firestore")
    public void adminHasTotpConfiguredInFirestore() {
        // Pre-condition: Admin must have TOTP secret in totpSecrets/{uid} collection
        // This is a declarative step - actual verification happens when decrypting the secret
        log.info("[E2E] Assuming admin has TOTP configured in Firestore");
    }

    @When("I login as admin with email {string} and password {string}")
    public void loginAsAdminWithCredentials(String email, String password) {
        performFirebaseLogin(email, password, "ADMIN");
    }

    /**
     * Attempts admin login, expecting it to fail with 4xx error.
     * Captures the error response for assertion.
     */
    @When("I attempt to login as admin with email {string} and password {string}")
    public void attemptLoginAsAdminWithCredentials(String email, String password) {
        attemptFirebaseLogin(email, password, "ADMIN");
    }

    @Then("a partial session should be returned with 2FA challenge")
    public void verifyPartialSessionWith2FAChallenge() {
        Boolean requires2FA = context.get("requires2FA");
        assertThat(requires2FA)
            .as("Should require 2FA for admin")
            .isTrue();
    }

    @When("I decrypt the admin TOTP secret from Firestore via KMS")
    public void decryptAdminTotpSecretFromFirestore() {
        String firebaseUid = context.getCurrentFirebaseUid();
        assertThat(firebaseUid)
            .as("Firebase UID should be available")
            .isNotNull();

        try {
            // TotpFirestoreService handles Firestore read + KMS decryption automatically
            String plainSecret = totpFirestoreService.getTotpSecret(firebaseUid);
            assertThat(plainSecret)
                .as("TOTP secret should be decrypted from Firestore")
                .isNotNull()
                .isNotEmpty();
            context.put("decryptedTotpSecret", plainSecret);
            log.info("[E2E] Successfully decrypted TOTP secret from Firestore via KMS for uid={}", firebaseUid);
        } catch (Exception e) {
            log.error("[E2E] Failed to decrypt TOTP secret for uid={}", firebaseUid, e);
            throw new RuntimeException("TOTP secret decryption failed: " + e.getMessage(), e);
        }
    }

    @When("I generate a valid TOTP code from the decrypted secret")
    public void generateTotpCodeFromDecryptedSecret() throws InterruptedException {
        String plainSecret = context.get("decryptedTotpSecret");
        assertThat(plainSecret)
            .as("Decrypted TOTP secret should be available")
            .isNotNull();

        // Avoid TOTP window boundary: if near the end of the current 30-second window,
        // wait for the next window to ensure the generated code won't expire mid-submission
        long secondsIntoWindow = (System.currentTimeMillis() / 1000) % 30;
        if (secondsIntoWindow >= 25) {
            long waitMs = (31 - secondsIntoWindow) * 1000;
            log.info("[E2E] Near TOTP window boundary ({}s into window), waiting {}ms", secondsIntoWindow, waitMs);
            Thread.sleep(waitMs);
        }

        // Generate 6-digit TOTP code
        int code = totpCodeGenerator.generateTotpCode(plainSecret);
        String codeStr = String.format("%06d", code);
        context.setTotpCode(codeStr);
        log.info("[E2E] Generated TOTP code from KMS-decrypted secret");
    }

    /**
     * Submits an invalid TOTP code to test error handling.
     * Captures the error response for assertion.
     */
    @When("I submit an invalid TOTP code {string}")
    public void submitInvalidTotpCode(String invalidCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Add partial session cookies for 2FA verification
        addPartialSessionCookies(headers);

        // Add Firebase cookies
        addFirebaseCookies(headers);

        Map<String, Object> request = Map.of("code", invalidCode);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/twofactor/verify"),
                entity,
                Map.class
            );
            context.setLastResponse(response);
            log.info("[E2E] Invalid TOTP submission got unexpected success: status={}",
                response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 4xx error - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] Invalid TOTP submission failed as expected: status={}",
                e.getStatusCode());
        }
    }

    @When("I exchange the Firebase token for a full backend session")
    public void exchangeFirebaseTokenForFullSession() {
        // After 2FA verification, exchange again for full session
        exchangeFirebaseTokenForSession();
    }

    @Then("the JWT should have admin privileges")
    public void verifyJwtHasAdminPrivileges() {
        String jwt = context.getSessionCookie();
        assertThat(jwt)
            .as("Session JWT should be set")
            .isNotNull();

        String payload = decodeJwtPayload(jwt);
        assertThat(payload)
            .as("JWT should contain admin role")
            .contains("\"role\":\"ADMIN\"");
    }

    // ==================== Helper Methods ====================

    /**
     * Seeds a COOKIE_POLICY consent record so exchange-token doesn't return 451.
     */
    private void seedCookieConsent() {
        String email = (String) context.get("syncedEmail");
        if (email == null) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> request = Map.of("email", email);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            restTemplate.postForEntity(url("/test/legal/seed-cookie-consent"), entity, Map.class);
            log.debug("[E2E] Cookie consent seeded for {}", email);
        } catch (Exception e) {
            log.warn("[E2E] Cookie consent seeding failed for {}: {}", email, e.getMessage());
        }
    }

    /**
     * Syncs user from Firestore to local Testcontainers PostgreSQL.
     * Uses the same endpoint as Influencer OAuth: /test/auth/sync-user-from-firestore
     *
     * <p>GRACEFUL PATTERN:
     * <ul>
     *   <li>Try to fetch/sync user from Firestore</li>
     *   <li>If exists - synced, continue</li>
     *   <li>If fails - log warning, continue anyway (don't fail test)</li>
     * </ul>
     *
     * <p>This ensures E2E tests are resilient to sync issues.
     */
    private void syncUserFromFirestore(String firebaseUid, String role, boolean syncInstagramData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> syncRequest = Map.of(
            "firebaseUid", firebaseUid,
            "role", role,
            "syncInstagramData", syncInstagramData
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(syncRequest, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/sync-user-from-firestore"),
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                context.setCurrentFirebaseUid(firebaseUid);
                context.setCurrentRole(role);
                context.put("syncedUserId", body.get("userId"));
                context.put("syncedEmail", body.get("email"));

                boolean created = Boolean.TRUE.equals(body.get("created"));
                log.info("[E2E] {} user synced from Firestore: userId={}, created={}, email={}",
                    role, body.get("userId"), created, body.get("email"));
            } else {
                log.warn("[E2E] {} sync returned non-success status: {} - continuing gracefully",
                    role, response.getStatusCode());
                // Set context anyway - Firebase login will handle user creation
                context.setCurrentFirebaseUid(firebaseUid);
                context.setCurrentRole(role);
            }
        } catch (Exception e) {
            log.warn("[E2E] Failed to sync {} user from Firestore: {} - continuing gracefully",
                role, e.getMessage());
            // Set context anyway - Firebase login will handle user creation
            context.setCurrentFirebaseUid(firebaseUid);
            context.setCurrentRole(role);
        }
    }

    /**
     * Performs Firebase login using the /auth/firebase/login endpoint.
     * Extracts FirebaseIdToken cookies on success.
     */
    private void performFirebaseLogin(String email, String password, String expectedRole) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
            "email", email,
            "password", password
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/auth/firebase/login"),
                entity,
                Map.class
            );

            context.setLastResponse(response);
            extractFirebaseCookies(response);

            // Store Firebase UID if returned in response body
            if (response.getBody() != null && response.getBody().containsKey("uid")) {
                context.setCurrentFirebaseUid((String) response.getBody().get("uid"));
            }

            log.info("[E2E] Firebase login for {}: status={}", expectedRole, response.getStatusCode());
        } catch (Exception e) {
            log.error("[E2E] Firebase login failed for {}", email, e);
            throw new RuntimeException("Firebase login failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts Firebase token cookies from response.
     */
    private void extractFirebaseCookies(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("FirebaseIdToken=")) {
                    context.setFirebaseIdToken(extractCookieValue(cookie));
                } else if (cookie.startsWith("FirebaseIdToken_sig=")) {
                    context.setFirebaseIdTokenSig(extractCookieValue(cookie));
                }
            }
        }
    }

    /**
     * Extracts session cookies from response.
     */
    private void extractSessionCookiesFromResponse(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session=") && !cookie.startsWith("session_sig=")) {
                    context.setSessionCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("session_sig=")) {
                    context.setSessionSigCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("partialSession=") && !cookie.startsWith("partialSessionSig=")) {
                    context.setPartialSessionCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("partialSessionSig=")) {
                    context.setPartialSessionSigCookie(extractCookieValue(cookie));
                }
            }
        }
    }

    /**
     * Adds Firebase token cookies to request headers.
     */
    private void addFirebaseCookies(HttpHeaders headers) {
        String idToken = context.getFirebaseIdToken();
        String idTokenSig = context.getFirebaseIdTokenSig();
        if (idToken != null) {
            headers.add("Cookie", "FirebaseIdToken=" + idToken);
        }
        if (idTokenSig != null) {
            headers.add("Cookie", "FirebaseIdToken_sig=" + idTokenSig);
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
     * Decodes the JWT payload (second part) from Base64.
     */
    private String decodeJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return "";
            }
            return new String(Base64.getUrlDecoder().decode(parts[1]));
        } catch (Exception e) {
            log.error("[E2E] Failed to decode JWT payload", e);
            return "";
        }
    }

    /**
     * Attempts Firebase login, catching expected 4xx errors for negative tests.
     * Stores the error response in context for assertion.
     */
    private void attemptFirebaseLogin(String email, String password, String expectedRole) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
            "email", email,
            "password", password
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/auth/firebase/login"),
                entity,
                Map.class
            );
            // Unexpected success - store response anyway
            context.setLastResponse(response);
            extractFirebaseCookies(response);
            log.info("[E2E] Login attempt for {} unexpectedly succeeded: status={}",
                email, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 4xx error - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] Login attempt for {} failed as expected: status={}",
                email, e.getStatusCode());
        }
    }

    /**
     * Adds partial session cookies to request headers (for 2FA verification).
     */
    private void addPartialSessionCookies(HttpHeaders headers) {
        String partialSession = context.getPartialSessionCookie();
        String partialSessionSig = context.getPartialSessionSigCookie();
        if (partialSession != null) {
            headers.add("Cookie", "partialSession=" + partialSession);
        }
        if (partialSessionSig != null) {
            headers.add("Cookie", "partialSessionSig=" + partialSessionSig);
        }
    }

    /**
     * Parses JSON error response body into a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("[E2E] Failed to parse JSON body: {}", e.getMessage());
            return Map.of("error", "Parse error", "message", json);
        }
    }
}
