package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.multiuser.actor.UserSession;
import com.sm.instagram.platform.e2e.multiuser.auth.MultiUserAuthService;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Advanced Session Security E2E tests.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Token version mismatch detection (HTTP 419)</li>
 *   <li>User banning while actively logged in</li>
 *   <li>User-Agent mismatch / session hijack detection (HTTP 401)</li>
 *   <li>GeoIP impossible travel detection (HTTP 401)</li>
 *   <li>Cookie forgery / HMAC signature validation (HTTP 401)</li>
 *   <li>Session refresh lifecycle</li>
 * </ul>
 *
 * <p><b>Attack Simulation Approach:</b> These tests use REAL header manipulation
 * (not mocking) to simulate actual attack vectors:
 * <ul>
 *   <li>Cookie forgery: Modify cookie values directly</li>
 *   <li>User-Agent hijack: Send different User-Agent header</li>
 *   <li>IP spoofing: Use X-Forwarded-For header (app trusts it)</li>
 * </ul>
 */
@Slf4j
public class AdvancedSessionSecuritySteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private ScenarioContext context;

    @Autowired
    private MultiUserAuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Token storage for testing old tokens against new token versions
    private final Map<String, String> storedTokens = new HashMap<>();

    // Standard User-Agent strings for testing
    private static final String FIREFOX_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0";
    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
    private static final String MOBILE_SAFARI_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1";

    // ============================================================================
    // TOKEN VERSION / STATUS CHANGE STEPS
    // ============================================================================
    // NOTE: Token version is automatically invalidated when admin changes user status
    // (ban, deactivate, etc.) - no special test endpoint needed!

    /**
     * Verifies that an actor can access an endpoint successfully.
     */
    @Then("{string} can access {string} successfully")
    public void canAccessSuccessfully(String alias, String endpoint) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            assertThat(response.getStatusCode().is2xxSuccessful())
                .as("'%s' should access '%s' successfully but got %s", alias, endpoint, response.getStatusCode())
                .isTrue();

            log.info("[E2E] '{}' accessed '{}' successfully (status {})", alias, endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            throw new AssertionError(String.format(
                "'%s' should access '%s' successfully but got %s: %s",
                alias, endpoint, e.getStatusCode(), e.getResponseBodyAsString()), e);
        }
    }

    /**
     * Explicitly refreshes a user's session token via POST /auth/refresh-session.
     * Use this when you know you need a fresh token (e.g., testing refresh flow).
     * For conditional refresh after admin actions, use "{string} refreshes their session if needed".
     */
    @When("{string} refreshes their session token")
    public void refreshSessionToken(String alias) {
        Actor actor = actorRegistry.get(alias);
        UserSession session = actor.getSession();

        try {
            performSessionRefresh(actor, session);
            context.setLastResponse(ResponseEntity.status(200).body(Map.of("success", true)));
            log.info("[E2E] '{}' refreshed session token successfully", alias);
        } catch (AssertionError e) {
            // performSessionRefresh throws AssertionError on failure - convert to response for assertions
            context.setLastResponse(ResponseEntity.status(500).body(Map.of("error", e.getMessage())));
            log.info("[E2E] '{}' session refresh failed: {}", alias, e.getMessage());
        }
    }

    /**
     * Attempts to refresh a user's session token, capturing the result for assertions.
     * Use this when testing that refresh FAILS (e.g., for INACTIVE/BANNED users).
     *
     * <p>Unlike "{string} refreshes their session token" which expects success,
     * this step always captures the result and allows subsequent assertions like:
     * <ul>
     *   <li>{@code soft assert refresh fails with "error.auth.account_disabled"}</li>
     *   <li>{@code soft assert refresh succeeds}</li>
     * </ul>
     */
    @When("{string} attempts to refresh their session token")
    public void attemptsToRefreshSessionToken(String alias) {
        Actor actor = actorRegistry.get(alias);
        UserSession session = actor.getSession();

        HttpHeaders headers = session.buildAuthHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url("/auth/refresh-session"),
                HttpMethod.POST,
                entity,
                String.class
            );

            // Success - extract new cookies and update session
            extractAndUpdateSessionCookies(response, session);
            ResponseEntity<Map> successResponse = ResponseEntity.status(response.getStatusCode()).body(Map.of("success", true));
            context.setLastResponse(successResponse);
            session.setLastResponse(successResponse);
            log.info("[E2E] '{}' refresh attempt succeeded", alias);
        } catch (HttpClientErrorException e) {
            // Capture failure for assertions - don't throw
            Map<String, Object> errorBody = parseJsonBody(e.getResponseBodyAsString());
            ResponseEntity<Map> errorResponse = ResponseEntity.status(e.getStatusCode()).body(errorBody);
            context.setLastResponse(errorResponse);
            session.setLastResponse(errorResponse);
            log.info("[E2E] '{}' refresh attempt returned {} - {}", alias, e.getStatusCode(), errorBody);
        }
    }

    /**
     * Conditionally refreshes a user's session if their token is outdated (HTTP 419).
     * This step should be called after admin actions that may have incremented the user's tokenVersion.
     *
     * <p>Flow:
     * <ol>
     *   <li>Make a test request to /users/me</li>
     *   <li>If HTTP 419 (token version mismatch) â†’ refresh session automatically</li>
     *   <li>If HTTP 2xx (token still valid) â†’ do nothing</li>
     *   <li>If HTTP 401/403 (banned/inactive) â†’ throw error (account issue, not token)</li>
     * </ol>
     */
    @When("{string} refreshes their session if needed")
    public void refreshSessionIfNeeded(String alias) {
        Actor actor = actorRegistry.get(alias);
        UserSession session = actor.getSession();

        HttpHeaders headers = session.buildAuthHeaders();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url("/users/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            // Token is still valid, no refresh needed
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[E2E] '{}' token is still valid, no refresh needed", alias);
                return;
            }
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();

            if (status == 419) {
                // Token version mismatch - need to refresh
                log.info("[E2E] '{}' got HTTP 419 (token outdated) - refreshing session...", alias);
                performSessionRefresh(actor, session);
                log.info("[E2E] '{}' session refreshed successfully after 419", alias);
                return;
            }

            if (status == 401 || status == 403) {
                // Account issue (banned, inactive, etc.) - not a token version problem
                log.error("[E2E] '{}' got HTTP {} - account issue, not token version", alias, status);
                throw new AssertionError(String.format(
                    "'%s' got HTTP %d - this indicates an account issue (banned/inactive), not token staleness. " +
                    "Check if admin properly restored user state before this step.", alias, status));
            }

            // Other error - log and throw
            log.error("[E2E] '{}' unexpected error during token check: {}", alias, status);
            throw new AssertionError("Unexpected error checking token for " + alias + ": " + e.getMessage(), e);
        }
    }

    /**
     * Performs the actual session refresh via POST /auth/refresh-session.
     * Extracted to be reusable by both explicit refresh and conditional refresh.
     */
    private void performSessionRefresh(Actor actor, UserSession session) {
        HttpHeaders headers = session.buildAuthHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url("/auth/refresh-session"),
                HttpMethod.POST,
                entity,
                String.class
            );

            // Extract new cookies from response headers
            extractAndUpdateSessionCookies(response, session);
        } catch (HttpClientErrorException e) {
            log.error("[E2E] Session refresh failed for '{}': {} - {}",
                actor.getName(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new AssertionError("Session refresh failed for " + actor.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the error code in the response body.
     */
    @Then("the error code should be {string}")
    public void verifyErrorCode(String expectedCode) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response).isNotNull();

        Object body = response.getBody();
        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) body;
            String actualCode = (String) map.get("code");
            assertThat(actualCode)
                .as("Error code should be '%s'", expectedCode)
                .isEqualTo(expectedCode);
        }
    }

    // ============================================================================
    // USER BANNING STEPS
    // ============================================================================

    /**
     * Admin bans a user by updating their account status to BANNED.
     */
    @When("{string} bans user {string} with reason {string}")
    public void adminBansUser(String adminAlias, String targetAlias, String reason) {
        Actor admin = actorRegistry.get(adminAlias);
        Actor target = actorRegistry.get(targetAlias);
        Long targetUserId = target.getSession().getUserId();

        // Ensure we have a valid userId
        assertThat(targetUserId)
            .as("Target user '%s' must have userId set (check login step)", targetAlias)
            .isNotNull();

        HttpHeaders headers = admin.getSession().buildAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("accountStatus", "BANNED");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/" + targetUserId),
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                Map.class
            );

            assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Admin should be able to ban user (got %s)", response.getStatusCode())
                .isTrue();

            log.info("[E2E] Admin '{}' banned user '{}' (userId={}, reason: {})",
                adminAlias, targetAlias, targetUserId, reason);
        } catch (HttpClientErrorException e) {
            log.error("[E2E] Failed to ban user '{}': {} - {}",
                targetAlias, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AssertionError("Failed to ban user: " + e.getMessage(), e);
        }
    }

    /**
     * Admin unbans a user by updating their account status to ACTIVE.
     */
    @When("{string} unbans user {string}")
    public void adminUnbansUser(String adminAlias, String targetAlias) {
        Actor admin = actorRegistry.get(adminAlias);
        Actor target = actorRegistry.get(targetAlias);
        Long targetUserId = target.getSession().getUserId();

        // Ensure we have a valid userId
        assertThat(targetUserId)
            .as("Target user '%s' must have userId set (check login step)", targetAlias)
            .isNotNull();

        HttpHeaders headers = admin.getSession().buildAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("accountStatus", "ACTIVE");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/" + targetUserId),
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                Map.class
            );

            assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Admin should be able to unban user (got %s)", response.getStatusCode())
                .isTrue();

            log.info("[E2E] Admin '{}' unbanned user '{}' (userId={})", adminAlias, targetAlias, targetUserId);
        } catch (HttpClientErrorException e) {
            log.error("[E2E] Failed to unban user '{}': {} - {}",
                targetAlias, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AssertionError("Failed to unban user: " + e.getMessage(), e);
        }
    }

    /**
     * Admin sets a user's account status to a specific value.
     * Generalizes ban/unban to support INACTIVE/ACTIVE transitions.
     */
    @When("{string} sets user {string} status to {string}")
    public void setUserStatus(String adminAlias, String targetAlias, String newStatus) {
        Actor admin = actorRegistry.get(adminAlias);
        Actor target = actorRegistry.get(targetAlias);
        Long targetUserId = target.getSession().getUserId();

        assertThat(targetUserId)
            .as("Target user '%s' must have userId set", targetAlias)
            .isNotNull();

        HttpHeaders headers = admin.getSession().buildAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("accountStatus", newStatus);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/" + targetUserId),
                HttpMethod.PATCH,
                new HttpEntity<>(body, headers),
                Map.class
            );

            assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Admin should be able to set user status to '%s' (got %s)",
                    newStatus, response.getStatusCode())
                .isTrue();

            log.info("[E2E] Admin '{}' set user '{}' (id={}) status to '{}'",
                adminAlias, targetAlias, targetUserId, newStatus);
        } catch (HttpClientErrorException e) {
            log.error("[E2E] Failed to set user status: {} - {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new AssertionError("Failed to set user status: " + e.getMessage(), e);
        }
    }

    // ============================================================================
    // TOKEN STORAGE AND ACCESS DENIAL TESTING
    // ============================================================================

    /**
     * Stores the current session cookie as a named token for later use.
     * Used to test that old tokens are properly blocked after status changes.
     */
    @And("{string} stores their current token as {string}")
    public void storeCurrentToken(String alias, String tokenName) {
        Actor actor = actorRegistry.get(alias);
        String sessionCookie = actor.getSession().getSessionCookie();
        String sigCookie = actor.getSession().getSessionSigCookie();

        // Store both cookies together in class-level map (for existing steps)
        storedTokens.put(tokenName + "_session", sessionCookie);
        storedTokens.put(tokenName + "_sig", sigCookie);

        // Also store in Actor resources (for soft assert steps in other classes)
        actor.storeResource(tokenName, sessionCookie);
        actor.storeResource(tokenName + "_sig", sigCookie);

        log.info("[E2E] Stored token '{}' for actor '{}' (session cookie length: {})",
            tokenName, alias, sessionCookie != null ? sessionCookie.length() : 0);
    }

    /**
     * Makes a request using a previously stored token and verifies the expected status.
     * This tests that old tokens are properly BLOCKED when token version mismatches.
     */
    @Then("{string} using stored token {string} calling {string} should return {int}")
    public void useStoredTokenExpectStatus(String alias, String tokenName, String endpoint, int expectedStatus) {
        String sessionCookie = storedTokens.get(tokenName + "_session");
        String sigCookie = storedTokens.get(tokenName + "_sig");

        assertThat(sessionCookie)
            .as("Stored session cookie '%s' should exist", tokenName)
            .isNotNull();
        assertThat(sigCookie)
            .as("Stored signature cookie '%s' should exist", tokenName)
            .isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "session=" + sessionCookie);
        headers.add("Cookie", "session_sig=" + sigCookie);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            assertThat(response.getStatusCode().value())
                .as("Using stored token '%s' to access '%s' should return %d but got %d",
                    tokenName, endpoint, expectedStatus, response.getStatusCode().value())
                .isEqualTo(expectedStatus);

            log.info("[E2E] Stored token '{}' -> {} returned {}",
                tokenName, endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value())
                .as("Using stored token '%s' to access '%s' should return %d but got %d",
                    tokenName, endpoint, expectedStatus, e.getStatusCode().value())
                .isEqualTo(expectedStatus);

            log.info("[E2E] Stored token '{}' -> {} BLOCKED with {} (expected)",
                tokenName, endpoint, e.getStatusCode());
        }
    }

    /**
     * Verifies that a user's next request returns a specific status code.
     */
    @Then("{string}'s next authenticated GET request to {string} should return {int}")
    public void nextRequestShouldReturn(String alias, String endpoint, int expectedStatus) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of()));
            assertThat(response.getStatusCode().value())
                .as("'%s' request to '%s' should return %d", alias, endpoint, expectedStatus)
                .isEqualTo(expectedStatus);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
            assertThat(e.getStatusCode().value())
                .as("'%s' request to '%s' should return %d", alias, endpoint, expectedStatus)
                .isEqualTo(expectedStatus);
        }
    }

    /**
     * Verifies that a user's authenticated request returns a specific status code.
     */
    @Then("{string}'s authenticated GET request to {string} should return {int}")
    public void authenticatedRequestShouldReturn(String alias, String endpoint, int expectedStatus) {
        nextRequestShouldReturn(alias, endpoint, expectedStatus);
    }

    /**
     * Verifies that user can see their account status.
     */
    @Then("{string} can see their account status as {string}")
    public void canSeeAccountStatus(String alias, String expectedStatus) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            // accountStatus may be a nested object or a string
            String actualStatus = extractStatusValue(response.getBody().get("accountStatus"));
            assertThat(actualStatus)
                .as("Account status should be '%s'", expectedStatus)
                .isEqualTo(expectedStatus);

            log.info("[E2E] '{}' verified their account status is '{}'", alias, actualStatus);
        } catch (HttpClientErrorException e) {
            throw new AssertionError("Failed to get user profile: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts status value from accountStatus object (handles nested structure).
     */
    private String extractStatusValue(Object statusObj) {
        if (statusObj == null) return null;
        if (statusObj instanceof String) return (String) statusObj;
        if (statusObj instanceof Map) {
            Map<?, ?> statusMap = (Map<?, ?>) statusObj;
            // Try common nested keys
            Object value = statusMap.get("value");
            if (value != null) return value.toString();
            Object status = statusMap.get("status");
            if (status != null) return status.toString();
            Object name = statusMap.get("name");
            if (name != null) return name.toString();
        }
        return statusObj.toString();
    }

    // ============================================================================
    // USER-AGENT MISMATCH STEPS (SESSION HIJACK DETECTION)
    // ============================================================================

    /**
     * Logs in a COMPANY user with a specific User-Agent (Firefox).
     */
    @Given("{string} logs in as COMPANY with Firefox User-Agent")
    public void loginCompanyWithFirefoxUA(String alias) {
        loginWithCustomUserAgent(alias, "COMPANY", FIREFOX_UA,
            "E2E_COMPANY_001",
            "e2e.company@test.com",
            "e2e-emulator-password");
    }

    /**
     * Logs in a COMPANY user with valid session (standard login).
     */
    @Given("{string} logs in as COMPANY with valid session")
    public void loginCompanyWithValidSession(String alias) {
        authService.setBaseUrl(baseUrl());
        UserSession session = authService.login(alias,
            "E2E_COMPANY_001",
            "e2e.company@test.com",
            "e2e-emulator-password",
            "COMPANY");
        actorRegistry.register(alias, session);
        log.info("[E2E] '{}' logged in as COMPANY with valid session", alias);
    }

    /**
     * Logs in a COMPANY user (short form).
     */
    @Given("{string} logs in as COMPANY")
    public void loginCompanyShort(String alias) {
        loginCompanyWithValidSession(alias);
    }

    /**
     * Logs in an INFLUENCER user with Mobile Safari User-Agent.
     */
    @Given("{string} logs in as INFLUENCER via OAuth with Mobile Safari User-Agent")
    public void loginInfluencerWithMobileSafariUA(String alias) {
        // For OAuth, we use the test endpoint with custom UA
        authService.setBaseUrl(baseUrl());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", MOBILE_SAFARI_UA);

        Map<String, Object> body = Map.of(
            "firebaseUid", "E2E_INFLUENCER_001",
            "email", "test-influencer@e2e.test",
            "validateInstagramToken", false
        );

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/test/auth/simulate-influencer-oauth"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
            );

            // Extract cookies and create session
            UserSession session = extractSessionFromResponse(response, alias);
            session.setRole("INFLUENCER");
            session.setOauth(true);
            session.setProvider("instagram");
            session.setFirebaseUid("E2E_INFLUENCER_001");
            session.put("customUserAgent", MOBILE_SAFARI_UA);

            if (response.getBody() != null) {
                session.setUserId(((Number) response.getBody().get("userId")).longValue());
            }

            actorRegistry.register(alias, session);
            log.info("[E2E] '{}' logged in as INFLUENCER via OAuth with Mobile Safari UA", alias);
        } catch (HttpClientErrorException e) {
            throw new AssertionError("Failed to login influencer: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies access with the same User-Agent that was used during login.
     */
    @And("{string} can access {string} with same User-Agent")
    public void canAccessWithSameUA(String alias, String endpoint) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();

        // Use the same User-Agent that was used during session creation
        String sessionUA = (String) actor.getSession().get("customUserAgent");
        if (sessionUA != null) {
            headers.set("User-Agent", sessionUA);
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            assertThat(response.getStatusCode().is2xxSuccessful())
                .as("'%s' should access '%s' with same User-Agent", alias, endpoint)
                .isTrue();

            log.info("[E2E] '{}' accessed '{}' successfully with same UA", alias, endpoint);
        } catch (HttpClientErrorException e) {
            throw new AssertionError(String.format(
                "'%s' should access '%s' with same UA but got %s: %s",
                alias, endpoint, e.getStatusCode(), e.getResponseBodyAsString()), e);
        }
    }

    /**
     * Makes a request with a different User-Agent than session was created with.
     * This simulates a session hijack attempt.
     */
    @When("{string} makes an authenticated GET request to {string} with Chrome User-Agent")
    public void makeRequestWithChromeUA(String alias, String endpoint) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();
        headers.set("User-Agent", CHROME_UA);  // Different UA than session creation

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of()));
            log.info("[E2E] Request with different UA returned {}", response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
            log.info("[E2E] Request with different UA blocked: {}", e.getStatusCode());
        }
    }

    /**
     * Makes a request with Desktop Chrome User-Agent.
     */
    @When("{string} makes an authenticated GET request to {string} with Desktop Chrome User-Agent")
    public void makeRequestWithDesktopChromeUA(String alias, String endpoint) {
        makeRequestWithChromeUA(alias, endpoint);
    }

    /**
     * Verifies that session was terminated.
     */
    @Then("the session should be terminated")
    public void sessionShouldBeTerminated() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getStatusCode().value())
            .as("Session should be terminated with 401")
            .isEqualTo(401);
    }

    // ============================================================================
    // GEOIP / IMPOSSIBLE TRAVEL STEPS
    // ============================================================================

    /**
     * Logs in a COMPANY user from a specific IP address.
     * Uses X-Forwarded-For to simulate the source IP.
     */
    @Given("{string} logs in as COMPANY from IP {string} \\({word}\\)")
    public void loginFromSpecificIP(String alias, String ip, String country) {
        authService.setBaseUrl(baseUrl());

        // Create session with custom IP via test endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);

        Map<String, Object> body = Map.of(
            "email", "e2e.company@test.com",
            "role", "COMPANY",
            "firebaseUid", "E2E_COMPANY_001"
        );

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/test/auth/mock-session"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
            );

            UserSession session = extractSessionFromResponse(response, alias);
            session.setRole("COMPANY");
            session.setFirebaseUid("E2E_COMPANY_001");
            session.setEmail("e2e.company@test.com");
            session.put("sessionIP", ip);
            session.put("sessionCountry", country);

            if (response.getBody() != null && response.getBody().containsKey("userId")) {
                session.setUserId(((Number) response.getBody().get("userId")).longValue());
            }

            actorRegistry.register(alias, session);
            log.info("[E2E] '{}' logged in as COMPANY from IP {} ({})", alias, ip, country);
        } catch (HttpClientErrorException e) {
            throw new AssertionError("Failed to create session from IP: " + e.getMessage(), e);
        }
    }

    /**
     * Makes a request from a different IP (simulates impossible travel).
     */
    @When("{string} makes a request from IP {string} \\({word}\\) within {int} minutes")
    public void makeRequestFromDifferentIP(String alias, String ip, String country, int minutes) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();
        headers.set("X-Forwarded-For", ip);  // Spoof different IP

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url("/users/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of()));
            log.info("[E2E] Request from different IP {} returned {}", ip, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
            log.info("[E2E] Request from different IP {} blocked: {}", ip, e.getStatusCode());
        }
    }

    /**
     * Verifies that impossible travel was detected.
     */
    @Then("impossible travel should be detected")
    public void impossibleTravelDetected() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getStatusCode().value())
            .as("Impossible travel should be blocked with 401")
            .isEqualTo(401);
    }

    /**
     * Verifies the error indicates impossible travel.
     */
    @Then("the error should indicate impossible travel detected")
    public void errorIndicatesImpossibleTravel() {
        impossibleTravelDetected();
    }

    // ============================================================================
    // COOKIE FORGERY STEPS (HMAC VALIDATION)
    // ============================================================================

    /**
     * Sends a request with tampered session cookie content.
     * Keeps the original signature, which will cause HMAC mismatch.
     */
    @When("{string} sends a request with modified session cookie content")
    public void sendTamperedCookie(String alias) {
        Actor actor = actorRegistry.get(alias);
        String originalCookie = actor.getSession().getSessionCookie();
        String originalSig = actor.getSession().getSessionSigCookie();

        // Tamper with the cookie (change last 5 characters)
        String tamperedCookie = originalCookie.substring(0, originalCookie.length() - 5) + "XXXXX";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "session=" + tamperedCookie);
        headers.add("Cookie", "session_sig=" + originalSig);  // Keep original signature

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url("/users/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of()));
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
        }

        log.info("[E2E] '{}' sent tampered cookie (HMAC should fail)", alias);
    }

    /**
     * Sends a request with valid cookie but wrong HMAC signature.
     */
    @When("{string} sends a request with valid cookie but wrong signature")
    public void sendWrongSignature(String alias) {
        Actor actor = actorRegistry.get(alias);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "session=" + actor.getSession().getSessionCookie());
        headers.add("Cookie", "session_sig=invalid_forged_signature_12345");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url("/users/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of()));
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
        }

        log.info("[E2E] '{}' sent valid cookie with wrong signature", alias);
    }

    /**
     * One user attempts to use another user's session cookies (cross-user injection).
     */
    @When("{string} uses {string}'s session cookies")
    public void useStolenCookies(String attackerAlias, String victimAlias) {
        Actor victim = actorRegistry.get(victimAlias);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "session=" + victim.getSession().getSessionCookie());
        headers.add("Cookie", "session_sig=" + victim.getSession().getSessionSigCookie());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url("/users/me"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of()));
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
        }

        log.info("[E2E] '{}' attempted to use '{}'s session cookies", attackerAlias, victimAlias);
    }

    /**
     * Verifies error indicates invalid session signature.
     */
    @Then("the error should indicate invalid session signature")
    public void errorIndicatesInvalidSignature() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getStatusCode().value())
            .as("Invalid signature should return 401")
            .isEqualTo(401);
    }

    // ============================================================================
    // SESSION REFRESH LIFECYCLE STEPS
    // ============================================================================

    /**
     * Verifies that a user has a valid session.
     */
    @Given("{string} has a valid session")
    public void hasValidSession(String alias) {
        Actor actor = actorRegistry.get(alias);
        assertThat(actor.getSession().isAuthenticated())
            .as("'%s' should have a valid session", alias)
            .isTrue();
    }

    /**
     * Calls the refresh-session endpoint with current session.
     */
    @When("{string} calls POST {string} with current session")
    public void callsPostWithSession(String alias, String endpoint) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class
            );

            // Store new cookies
            extractAndUpdateSessionCookies(response, actor.getSession());
            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of("success", true)));
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
        }
    }

    /**
     * Verifies user received new session cookies.
     */
    @Then("{string} should receive new session cookies")
    public void shouldReceiveNewCookies(String alias) {
        Actor actor = actorRegistry.get(alias);
        assertThat(actor.getSession().getSessionCookie())
            .as("'%s' should have session cookie", alias)
            .isNotNull();
        assertThat(actor.getSession().getSessionSigCookie())
            .as("'%s' should have session signature cookie", alias)
            .isNotNull();
    }

    /**
     * Makes a request with new cookies (after refresh).
     */
    @When("{string} makes an authenticated GET request to {string} with new cookies")
    public void makeRequestWithNewCookies(String alias, String endpoint) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of()));
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Logs in a user with a custom User-Agent header.
     */
    private void loginWithCustomUserAgent(String alias, String role, String userAgent,
                                          String firebaseUid, String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", userAgent);

        Map<String, Object> body = Map.of(
            "email", email,
            "role", role,
            "firebaseUid", firebaseUid
        );

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/test/auth/mock-session"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
            );

            UserSession session = extractSessionFromResponse(response, alias);
            session.setRole(role);
            session.setFirebaseUid(firebaseUid);
            session.setEmail(email);
            session.put("customUserAgent", userAgent);

            if (response.getBody() != null && response.getBody().containsKey("userId")) {
                session.setUserId(((Number) response.getBody().get("userId")).longValue());
            }

            actorRegistry.register(alias, session);
            log.info("[E2E] '{}' logged in as {} with custom UA", alias, role);
        } catch (HttpClientErrorException e) {
            throw new AssertionError("Failed to login with custom UA: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts session cookies from response and creates a UserSession.
     */
    private UserSession extractSessionFromResponse(ResponseEntity<?> response, String alias) {
        UserSession session = new UserSession();
        session.setAlias(alias);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session=")) {
                    session.setSessionCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("session_sig=")) {
                    session.setSessionSigCookie(extractCookieValue(cookie));
                }
            }
        }

        return session;
    }

    /**
     * Extracts cookie value from Set-Cookie header.
     */
    private String extractCookieValue(String setCookieHeader) {
        int equalsIndex = setCookieHeader.indexOf('=');
        int semicolonIndex = setCookieHeader.indexOf(';');
        if (semicolonIndex == -1) {
            return setCookieHeader.substring(equalsIndex + 1);
        }
        return setCookieHeader.substring(equalsIndex + 1, semicolonIndex);
    }

    /**
     * Extracts and updates session cookies from response.
     */
    private void extractAndUpdateSessionCookies(ResponseEntity<?> response, UserSession session) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session=")) {
                    session.setSessionCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("session_sig=")) {
                    session.setSessionSigCookie(extractCookieValue(cookie));
                }
            }
        }
    }

    /**
     * Parses JSON response body into a Map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(String body) {
        if (body == null || body.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON body: {}", body);
            return Map.of("rawBody", body);
        }
    }
}
