package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for rate limiting E2E tests.
 * Tests 429 responses, rate limit headers, and user isolation.
 *
 * <p>These tests require strict rate limits configured via Maven argLine:
 * <pre>
 * -DRL_STANDARD_REQ=5 -DRL_STRICT_REQ=3 -DRL_RELAXED_REQ=10
 * -DRL_STANDARD_WIN=60 -DRL_STRICT_WIN=60 -DRL_RELAXED_WIN=60
 * -DRL_STANDARD_BLOCK=10 -DRL_STRICT_BLOCK=10 -DRL_RELAXED_BLOCK=10
 * </pre>
 *
 * <p>Run with: mvn verify -Pe2e -Dit.test=RunRateLimitingIT
 */
@Slf4j
public class RateLimitingSteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${e2e.company.firebase-uid:E2E_COMPANY_001}")
    private String e2eCompanyFirebaseUid;

    @Value("${e2e.company.email:e2e.company@test.com}")
    private String e2eCompanyEmail;

    @Value("${e2e.influencer.firebase-uid:E2E_INFLUENCER_001}")
    private String e2eInfluencerFirebaseUid;

    @Value("${e2e.influencer.email:e2e.influencer@test.com}")
    private String e2eInfluencerEmail;

    // ==================== Authentication Steps ====================

    /**
     * Authenticates as the E2E company user by creating a test session.
     * Uses the test auth endpoint to create a valid session without real Firebase auth.
     */
    @Given("I am authenticated as E2E company user")
    public void authenticateAsE2ECompanyUser() {
        createTestSession("COMPANY", e2eCompanyFirebaseUid, e2eCompanyEmail);
    }

    /**
     * Authenticates as the E2E influencer user by creating a test session.
     */
    @When("I authenticate as E2E influencer user")
    public void authenticateAsE2EInfluencerUser() {
        createTestSession("INFLUENCER", e2eInfluencerFirebaseUid, e2eInfluencerEmail);
    }

    /**
     * Syncs an influencer user from Firestore (similar to company user sync).
     */
    @Given("an influencer user with Firebase UID {string} is synced from Firestore")
    public void influencerUserSyncedFromFirestore(String firebaseUid) {
        syncUserFromFirestore(firebaseUid, "INFLUENCER");
    }

    // ==================== Request Steps ====================

    /**
     * Makes multiple successful GET requests to an endpoint.
     * All requests must return 2xx to be considered successful.
     */
    @When("I make {int} successful GET requests to {string}")
    public void makeMultipleSuccessfulGetRequests(int count, String endpoint) {
        for (int i = 1; i <= count; i++) {
            ResponseEntity<Map> response = makeAuthenticatedGetRequest(endpoint);
            assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Request %d to %s should succeed (status: %d)", i, endpoint, response.getStatusCode().value())
                .isTrue();
            log.info("[E2E] Rate limit test: GET request {}/{} to {} succeeded", i, count, endpoint);

            // Store the last successful response
            context.setLastResponse(response);
        }
    }

    /**
     * Makes one more GET request (expected to be rate limited after exhausting quota).
     */
    @When("I make one more GET request to {string}")
    public void makeOneMoreGetRequest(String endpoint) {
        try {
            ResponseEntity<Map> response = makeAuthenticatedGetRequest(endpoint);
            context.setLastResponse(response);
            log.info("[E2E] One more GET request to {}: status={}", endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 429 - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] One more GET request to {} blocked: status={}", endpoint, e.getStatusCode());
        }
    }

    /**
     * Makes a single GET request to an endpoint.
     */
    @When("I make a GET request to {string}")
    public void makeGetRequest(String endpoint) {
        try {
            ResponseEntity<Map> response = makeAuthenticatedGetRequest(endpoint);
            context.setLastResponse(response);
            log.info("[E2E] GET request to {}: status={}", endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] GET request to {} failed: status={}", endpoint, e.getStatusCode());
        }
    }

    /**
     * Makes multiple successful POST requests to an endpoint.
     * Used for testing STRICT profile endpoints like password reset.
     */
    @When("I make {int} successful POST requests to {string}")
    public void makeMultipleSuccessfulPostRequests(int count, String endpoint) {
        for (int i = 1; i <= count; i++) {
            ResponseEntity<Map> response = makeAuthenticatedPostRequest(endpoint, Map.of());
            // For rate limit testing, we accept both 2xx and 4xx (like 400 for invalid request body)
            // The key is that we're NOT rate limited (not 429)
            assertThat(response.getStatusCode().value())
                .as("Request %d to %s should not be rate limited (429)", i, endpoint)
                .isNotEqualTo(429);
            log.info("[E2E] Rate limit test: POST request {}/{} to {} completed with status {}",
                i, count, endpoint, response.getStatusCode().value());

            context.setLastResponse(response);
        }
    }

    /**
     * Makes one more POST request (expected to be rate limited after exhausting quota).
     */
    @When("I make one more POST request to {string}")
    public void makeOneMorePostRequest(String endpoint) {
        try {
            ResponseEntity<Map> response = makeAuthenticatedPostRequest(endpoint, Map.of());
            context.setLastResponse(response);
            log.info("[E2E] One more POST request to {}: status={}", endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] One more POST request to {} blocked: status={}", endpoint, e.getStatusCode());
        }
    }

    // ==================== Header Assertion Steps ====================

    /**
     * Verifies that a response contains a specific header.
     */
    @Then("the response should contain header {string}")
    public void responseContainsHeader(String headerName) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        List<String> headerValues = response.getHeaders().get(headerName);
        assertThat(headerValues)
            .as("Response should contain header '%s'", headerName)
            .isNotNull()
            .isNotEmpty();
        log.info("[E2E] Verified header '{}' is present with value: {}", headerName, headerValues.get(0));
    }

    /**
     * Verifies that a response contains a specific header with expected value.
     */
    @Then("the response should contain header {string} with value {string}")
    public void responseContainsHeaderWithValue(String headerName, String expectedValue) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        List<String> headerValues = response.getHeaders().get(headerName);
        assertThat(headerValues)
            .as("Response should contain header '%s'", headerName)
            .isNotNull()
            .isNotEmpty();

        assertThat(headerValues.get(0))
            .as("Header '%s' should have value '%s'", headerName, expectedValue)
            .isEqualTo(expectedValue);
        log.info("[E2E] Verified header '{}' = '{}'", headerName, expectedValue);
    }

    // ==================== Body Assertion Steps ====================

    /**
     * Verifies that the response body contains a specific string.
     */
    @Then("the response body should contain {string}")
    public void responseBodyContains(String expectedContent) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        String bodyString = body.toString();
        if (body instanceof Map) {
            try {
                bodyString = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                bodyString = body.toString();
            }
        }

        assertThat(bodyString)
            .as("Response body should contain '%s'", expectedContent)
            .containsIgnoringCase(expectedContent);
        log.info("[E2E] Verified response body contains: {}", expectedContent);
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a test session using the test auth controller.
     */
    private void createTestSession(String role, String firebaseUid, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
            "email", email,
            "role", role,
            "partial", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/test/auth/mock-session"),
            entity,
            Map.class
        );

        extractSessionCookiesFromResponse(response);
        context.setCurrentRole(role);
        context.setCurrentFirebaseUid(firebaseUid);
        context.setCurrentEmail(email);

        log.info("[E2E] Created test session for {} user: firebaseUid={}", role, firebaseUid);
    }

    /**
     * Syncs user from Firestore to local Testcontainers PostgreSQL.
     */
    private void syncUserFromFirestore(String firebaseUid, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> syncRequest = Map.of(
            "firebaseUid", firebaseUid,
            "role", role,
            "syncInstagramData", false
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
                context.setCurrentFirebaseUid(firebaseUid);
                context.setCurrentRole(role);
            }
        } catch (Exception e) {
            log.warn("[E2E] Failed to sync {} user from Firestore: {} - continuing gracefully",
                role, e.getMessage());
            context.setCurrentFirebaseUid(firebaseUid);
            context.setCurrentRole(role);
        }
    }

    /**
     * Makes an authenticated GET request with session cookies.
     */
    private ResponseEntity<Map> makeAuthenticatedGetRequest(String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        addSessionCookies(headers);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(
            url(endpoint),
            HttpMethod.GET,
            entity,
            Map.class
        );
    }

    /**
     * Makes an authenticated POST request with session cookies.
     */
    private ResponseEntity<Map> makeAuthenticatedPostRequest(String endpoint, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addSessionCookies(headers);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            return restTemplate.postForEntity(
                url(endpoint),
                entity,
                Map.class
            );
        } catch (HttpClientErrorException e) {
            // Convert to ResponseEntity for consistent handling
            return ResponseEntity
                .status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(parseJsonBody(e.getResponseBodyAsString()));
        }
    }

    /**
     * Adds session cookies to request headers.
     */
    private void addSessionCookies(HttpHeaders headers) {
        String sessionCookie = context.getSessionCookie();
        String sessionSig = context.getSessionSigCookie();

        if (sessionCookie != null) {
            headers.add("Cookie", "session=" + sessionCookie);
        }
        if (sessionSig != null) {
            headers.add("Cookie", "session_sig=" + sessionSig);
        }
    }

    /**
     * Extracts session cookies from response.
     */
    private void extractSessionCookiesFromResponse(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            log.warn("[E2E] No cookies in response");
            return;
        }

        for (String cookie : cookies) {
            if (cookie.startsWith("session=") && !cookie.startsWith("session_sig=")) {
                context.setSessionCookie(extractCookieValue(cookie));
            } else if (cookie.startsWith("session_sig=")) {
                context.setSessionSigCookie(extractCookieValue(cookie));
            }
        }
    }

    /**
     * Extracts cookie value from Set-Cookie header.
     */
    private String extractCookieValue(String cookie) {
        return cookie.split(";")[0].split("=", 2)[1];
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
