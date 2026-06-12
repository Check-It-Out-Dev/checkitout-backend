package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.multiuser.actor.UserSession;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import static org.assertj.core.api.Assertions.assertThat;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

/**
 * Step definitions for security-related E2E tests.
 * Tests 401 Unauthorized and 403 Forbidden scenarios.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Protected endpoints reject unauthenticated requests (401)</li>
 *   <li>Users cannot access resources they don't own (403)</li>
 *   <li>Role-based access control is enforced (403)</li>
 * </ul>
 */
@Slf4j
public class SecuritySteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private ActorRegistry actorRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Session duration in seconds (from config).
     * E2E profile uses 15 seconds, production uses 604800 (7 days).
     */
    @Value("${session.full-duration-seconds:604800}")
    private int sessionDurationSeconds;

    // ==================== Given Steps ====================

    /**
     * Ensures no authentication cookies are present.
     * Used to test 401 Unauthorized responses.
     */
    @Given("I am not logged in")
    public void iAmNotLoggedIn() {
        context.clearCookies();
        log.info("[E2E] Cleared all session cookies - user is not logged in");
    }

    // ==================== When Steps ====================

    /**
     * Attempts to access a protected endpoint without authentication.
     * Captures the response (expected to be 401 or 403).
     */
    @When("I try to access the protected endpoint {string}")
    public void tryAccessProtectedEndpoint(String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            log.info("[E2E] Accessed {} without auth: status={}", endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 401/403 - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] Access to {} denied as expected: status={}", endpoint, e.getStatusCode());
        }
    }

    // ==================== Session Expiry Steps ====================

    /**
     * Waits for the session to expire.
     * Uses configured session duration + 5 second buffer.
     */
    @When("I wait for the session to expire")
    public void waitForSessionToExpire() throws InterruptedException {
        int waitTimeSeconds = sessionDurationSeconds + 5; // 5 second buffer
        log.info("[E2E] Waiting {} seconds for session to expire (session duration: {}s + 5s buffer)...",
            waitTimeSeconds, sessionDurationSeconds);

        Thread.sleep(waitTimeSeconds * 1000L);

        log.info("[E2E] Wait complete - session should now be expired");
    }

    /**
     * Attempts to access a protected endpoint with an expired session.
     * Uses the stored session cookies from context.
     */
    @When("I try to access the protected endpoint {string} with my expired session")
    public void tryAccessWithExpiredSession(String endpoint) {
        HttpHeaders headers = new HttpHeaders();

        // Add the (now expired) session cookies from context
        String sessionCookie = context.getSessionCookie();
        String sessionSig = context.getSessionSigCookie();

        if (sessionCookie != null) {
            headers.add("Cookie", "session=" + sessionCookie);
        }
        if (sessionSig != null) {
            headers.add("Cookie", "session_sig=" + sessionSig);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            log.info("[E2E] Unexpected success accessing {} with expired session: status={}",
                endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 401 - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] Access to {} denied with expired session as expected: status={}",
                endpoint, e.getStatusCode());
        }
    }

    // ==================== Partial Session Access Steps ====================

    /**
     * Attempts to access an admin-only endpoint using partial session cookies.
     * Used to verify 403 Forbidden for admin without 2FA completion.
     */
    @When("I try to access the admin endpoint {string} with my partial session")
    public void tryAccessAdminEndpointWithPartialSession(String endpoint) {
        HttpHeaders headers = new HttpHeaders();

        // Add partial session cookies (from 2FA pending state)
        String partialSession = context.getPartialSessionCookie();
        String partialSessionSig = context.getPartialSessionSigCookie();

        if (partialSession != null) {
            headers.add("Cookie", "partialSession=" + partialSession);
        }
        if (partialSessionSig != null) {
            headers.add("Cookie", "partialSessionSig=" + partialSessionSig);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            log.info("[E2E] Accessed {} with partial session: status={}", endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 403 Forbidden - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] Access to {} denied with partial session: status={}", endpoint, e.getStatusCode());
        }
    }

    /**
     * Attempts to access an admin-only endpoint using full session cookies.
     * Used to verify 403 Forbidden for non-admin users.
     */
    @When("I try to access the admin endpoint {string} with my session")
    public void tryAccessAdminEndpointWithSession(String endpoint) {
        HttpHeaders headers = new HttpHeaders();

        // Add full session cookies
        String sessionCookie = context.getSessionCookie();
        String sessionSig = context.getSessionSigCookie();

        if (sessionCookie != null) {
            headers.add("Cookie", "session=" + sessionCookie);
        }
        if (sessionSig != null) {
            headers.add("Cookie", "session_sig=" + sessionSig);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            log.info("[E2E] Accessed {} with session: status={}", endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 403 Forbidden - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] Access to {} denied with session: status={}", endpoint, e.getStatusCode());
        }
    }

    // ==================== Unauthenticated Multi-Method Access ====================

    /**
     * Makes an unauthenticated request with specified HTTP method.
     * Used for comprehensive security testing of all protected endpoints.
     * Supports GET, POST, PUT, PATCH, DELETE methods.
     *
     * @param method   HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param endpoint The endpoint path (will be prefixed with base URL)
     */
    @When("I make an unauthenticated {word} request to {string}")
    public void makeUnauthenticatedRequest(String method, String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        // NO cookies, NO Authorization header - deliberately unauthenticated

        // GET and DELETE should not have body, POST/PUT/PATCH need body
        HttpEntity<?> entity = switch (method.toUpperCase()) {
            case "GET", "DELETE" -> new HttpEntity<>(headers);
            default -> new HttpEntity<>("{}", headers);
        };

        try {
            ResponseEntity<String> response = switch (method.toUpperCase()) {
                case "GET" -> restTemplate.exchange(url(endpoint), HttpMethod.GET, entity, String.class);
                case "POST" -> restTemplate.exchange(url(endpoint), HttpMethod.POST, entity, String.class);
                case "PUT" -> restTemplate.exchange(url(endpoint), HttpMethod.PUT, entity, String.class);
                case "PATCH" -> restTemplate.exchange(url(endpoint), HttpMethod.PATCH, entity, String.class);
                case "DELETE" -> restTemplate.exchange(url(endpoint), HttpMethod.DELETE, entity, String.class);
                default -> throw new IllegalArgumentException("Unknown HTTP method: " + method);
            };
            // Unexpected success - store response for assertion
            context.setLastResponse(ResponseEntity
                .status(response.getStatusCode())
                .body(Map.of("unexpected", "success", "body", response.getBody() != null ? response.getBody() : "")));
            log.info("[E2E] Unauthenticated {} {} returned: {} (unexpected success)",
                method, endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 401/403 - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] Unauthenticated {} {} denied as expected: {}",
                method, endpoint, e.getStatusCode());
        }
    }

    // ==================== Authenticated Role-Based Access Steps ====================

    /**
     * Makes an authenticated request with specified HTTP method using actor's session.
     * Used for 403 Forbidden tests where user is authenticated but lacks permission.
     *
     * @param alias    The actor alias (e.g., "influencer1")
     * @param method   HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param endpoint The endpoint path (will be prefixed with base URL)
     */
    @When("{string} makes an authenticated {word} request to {string}")
    public void makeAuthenticatedRequest(String alias, String method, String endpoint) {
        UserSession session = actorRegistry.get(alias).getSession();

        // Use the session's built-in header builder (includes session cookies)
        HttpHeaders headers = session.buildAuthHeaders();

        HttpEntity<?> entity = switch (method.toUpperCase()) {
            case "GET", "DELETE" -> new HttpEntity<>(headers);
            default -> new HttpEntity<>("{}", headers);
        };

        try {
            ResponseEntity<String> response = switch (method.toUpperCase()) {
                case "GET" -> restTemplate.exchange(url(endpoint), HttpMethod.GET, entity, String.class);
                case "POST" -> restTemplate.exchange(url(endpoint), HttpMethod.POST, entity, String.class);
                case "PUT" -> restTemplate.exchange(url(endpoint), HttpMethod.PUT, entity, String.class);
                case "PATCH" -> restTemplate.exchange(url(endpoint), HttpMethod.PATCH, entity, String.class);
                case "DELETE" -> restTemplate.exchange(url(endpoint), HttpMethod.DELETE, entity, String.class);
                default -> throw new IllegalArgumentException("Unknown HTTP method: " + method);
            };
            // Unexpected success - store response for assertion
            context.setLastResponse(ResponseEntity
                .status(response.getStatusCode())
                .body(Map.of("body", response.getBody() != null ? response.getBody() : "")));
            log.info("[E2E] {} authenticated {} {} returned: {}",
                alias, method, endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 403 Forbidden - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] {} authenticated {} {} denied: {}",
                alias, method, endpoint, e.getStatusCode());
        }
    }

    // ==================== Dedicated Steps with Valid Request Bodies ====================

    /**
     * Makes an authenticated request to city endpoint with valid CityDto body.
     * This ensures we bypass validation (400) and reach authorization (403).
     */
    @When("{string} makes an authenticated {word} request to {string} with valid city body")
    public void makeAuthenticatedCityRequest(String alias, String method, String endpoint) {
        UserSession session = actorRegistry.get(alias).getSession();
        HttpHeaders headers = session.buildAuthHeaders();

        // Valid CityDto body: name is required (2-50 chars)
        String validCityBody = """
            {"name": "TestCity", "state": "TestState", "country": "Polska"}
            """;

        HttpEntity<String> entity = new HttpEntity<>(validCityBody, headers);

        executeAuthenticatedRequest(alias, method, endpoint, entity);
    }

    /**
     * Makes an authenticated request to consent definition endpoint with valid body.
     * This ensures we bypass validation (400) and reach authorization (403).
     */
    @When("{string} makes an authenticated POST request to {string} with valid consent definition body")
    public void makeAuthenticatedConsentDefinitionRequest(String alias, String endpoint) {
        UserSession session = actorRegistry.get(alias).getSession();
        HttpHeaders headers = session.buildAuthHeaders();

        // Valid ConsentDefinitionDtoIn body: consentType and name are required
        String validConsentBody = """
            {"consentType": "TEST_CONSENT", "name": "Test Consent Definition", "description": "Test description", "isActive": true}
            """;

        HttpEntity<String> entity = new HttpEntity<>(validConsentBody, headers);

        executeAuthenticatedRequest(alias, "POST", endpoint, entity);
    }

    /**
     * Makes an authenticated request to partnership opportunity endpoint with valid body.
     * This ensures we bypass validation (400) and reach authorization (403).
     */
    @When("{string} makes an authenticated {word} request to {string} with valid partnership body")
    public void makeAuthenticatedPartnershipRequest(String alias, String method, String endpoint) {
        UserSession session = actorRegistry.get(alias).getSession();
        HttpHeaders headers = session.buildAuthHeaders();

        // Valid PartnershipOpportunityDtoIn body: name, city, title, company are required
        String validPartnershipBody = """
            {"name": "Test Partnership", "city": "Warsaw", "title": "Test Title", "company": 1, "active": true}
            """;

        HttpEntity<String> entity = new HttpEntity<>(validPartnershipBody, headers);

        executeAuthenticatedRequest(alias, method, endpoint, entity);
    }

    /**
     * Makes an authenticated request without any body.
     * Used for action endpoints (like update-database, clean-cache) that don't accept request bodies.
     */
    @When("{string} makes an authenticated {word} request to {string} without body")
    public void makeAuthenticatedRequestWithoutBody(String alias, String method, String endpoint) {
        UserSession session = actorRegistry.get(alias).getSession();
        HttpHeaders headers = session.buildAuthHeaders();

        // No body - action endpoints don't consume request body
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        executeAuthenticatedRequest(alias, method, endpoint, entity);
    }

    /**
     * Helper method to execute authenticated request and handle response.
     */
    private void executeAuthenticatedRequest(String alias, String method, String endpoint, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> response = switch (method.toUpperCase()) {
                case "GET" -> restTemplate.exchange(url(endpoint), HttpMethod.GET, entity, String.class);
                case "POST" -> restTemplate.exchange(url(endpoint), HttpMethod.POST, entity, String.class);
                case "PUT" -> restTemplate.exchange(url(endpoint), HttpMethod.PUT, entity, String.class);
                case "PATCH" -> restTemplate.exchange(url(endpoint), HttpMethod.PATCH, entity, String.class);
                case "DELETE" -> restTemplate.exchange(url(endpoint), HttpMethod.DELETE, entity, String.class);
                default -> throw new IllegalArgumentException("Unknown HTTP method: " + method);
            };
            // Unexpected success - store response for assertion
            context.setLastResponse(ResponseEntity
                .status(response.getStatusCode())
                .body(Map.of("body", response.getBody() != null ? response.getBody() : "")));
            log.info("[E2E] {} authenticated {} {} returned: {}",
                alias, method, endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            // Expected: 403 Forbidden - convert to ResponseEntity for assertions
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] {} authenticated {} {} denied: {}",
                alias, method, endpoint, e.getStatusCode());
        }
    }

    // ==================== Assertion Steps ====================

    /**
     * Verifies the response status is one of the allowed status codes.
     * Used when endpoints may return 400 (validation-first) or 403 (authorization-first).
     */
    @io.cucumber.java.en.Then("the response status should be {int} or {int}")
    public void verifyResponseStatusIsOneOf(int status1, int status2) {
        assertThat(context.getLastResponse())
            .as("Response should not be null")
            .isNotNull();
        int actualStatus = context.getLastResponse().getStatusCode().value();
        assertThat(actualStatus)
            .as("Response status should be %d or %d, but was %d", status1, status2, actualStatus)
            .isIn(status1, status2);
    }

    /**
     * Verifies the response status is one of three allowed status codes.
     * Used for complex scenarios where multiple outcomes are acceptable.
     */
    @io.cucumber.java.en.Then("the response status should be {int} or {int} or {int}")
    public void verifyResponseStatusIsOneOfThree(int status1, int status2, int status3) {
        assertThat(context.getLastResponse())
            .as("Response should not be null")
            .isNotNull();
        int actualStatus = context.getLastResponse().getStatusCode().value();
        assertThat(actualStatus)
            .as("Response status should be %d, %d, or %d, but was %d", status1, status2, status3, actualStatus)
            .isIn(status1, status2, status3);
    }

    // ==================== Owner-Based Authorization Steps ====================

    /**
     * Makes an authenticated request to a user-specific endpoint using the actor's OWN userId.
     * Used to test owner-based authorization patterns like GDPR endpoints.
     *
     * @param alias    The actor alias (e.g., "influencer1")
     * @param method   HTTP method (GET, POST, DELETE)
     * @param endpointTemplate The endpoint template with {userId} placeholder
     */
    @When("{string} makes an authenticated {word} request to {string} using their own userId")
    public void makeAuthenticatedRequestWithOwnUserId(String alias, String method, String endpointTemplate) {
        UserSession session = actorRegistry.get(alias).getSession();
        Long userId = session.getUserId();

        if (userId == null) {
            throw new IllegalStateException("User ID not set for actor " + alias + ". Ensure login step populates userId.");
        }

        // Replace {userId} placeholder with actual user ID
        String endpoint = endpointTemplate.replace("{userId}", userId.toString());

        HttpHeaders headers = session.buildAuthHeaders();
        HttpEntity<?> entity = switch (method.toUpperCase()) {
            case "GET", "DELETE" -> new HttpEntity<>(headers);
            default -> new HttpEntity<>("{}", headers);
        };

        executeAuthenticatedRequest(alias, method, endpoint, entity);
        log.info("[E2E] {} made {} request to {} with their own userId={}", alias, method, endpoint, userId);
    }

    /**
     * Makes an authenticated request to a user-specific endpoint using a DIFFERENT userId.
     * Used to test that owner-based authorization denies access to other users' data.
     *
     * @param alias    The actor alias (e.g., "influencer1")
     * @param method   HTTP method (GET, POST, DELETE)
     * @param endpointTemplate The endpoint template with {userId} placeholder
     * @param targetUserId The userId to access (should be different from actor's own)
     */
    @When("{string} makes an authenticated {word} request to {string} using userId {long}")
    public void makeAuthenticatedRequestWithSpecificUserId(String alias, String method, String endpointTemplate, long targetUserId) {
        UserSession session = actorRegistry.get(alias).getSession();

        // Replace {userId} placeholder with specified user ID
        String endpoint = endpointTemplate.replace("{userId}", String.valueOf(targetUserId));

        HttpHeaders headers = session.buildAuthHeaders();
        HttpEntity<?> entity = switch (method.toUpperCase()) {
            case "GET", "DELETE" -> new HttpEntity<>(headers);
            default -> new HttpEntity<>("{}", headers);
        };

        executeAuthenticatedRequest(alias, method, endpoint, entity);
        log.info("[E2E] {} made {} request to {} with targetUserId={}", alias, method, endpoint, targetUserId);
    }

    // ==================== Partial Session (2FA Pending) Steps ====================

    /**
     * Makes an authenticated request using PARTIAL session (2FA not completed).
     * Used to test that partial sessions cannot access protected endpoints.
     *
     * @param alias    The actor alias (e.g., "admin1")
     * @param method   HTTP method (GET, POST, etc.)
     * @param endpoint The endpoint path
     */
    @When("{string} makes a partial-session {word} request to {string}")
    public void makePartialSessionRequest(String alias, String method, String endpoint) {
        UserSession session = actorRegistry.get(alias).getSession();

        if (!session.hasPartialSession()) {
            throw new IllegalStateException("Actor " + alias + " does not have partial session. Use a 2FA-pending login step.");
        }

        // Use partial session headers instead of full session
        HttpHeaders headers = session.buildPartialSessionHeaders();
        HttpEntity<?> entity = switch (method.toUpperCase()) {
            case "GET", "DELETE" -> new HttpEntity<>(headers);
            default -> new HttpEntity<>("{}", headers);
        };

        try {
            ResponseEntity<String> response = switch (method.toUpperCase()) {
                case "GET" -> restTemplate.exchange(url(endpoint), HttpMethod.GET, entity, String.class);
                case "POST" -> restTemplate.exchange(url(endpoint), HttpMethod.POST, entity, String.class);
                case "PUT" -> restTemplate.exchange(url(endpoint), HttpMethod.PUT, entity, String.class);
                case "PATCH" -> restTemplate.exchange(url(endpoint), HttpMethod.PATCH, entity, String.class);
                case "DELETE" -> restTemplate.exchange(url(endpoint), HttpMethod.DELETE, entity, String.class);
                default -> throw new IllegalArgumentException("Unknown HTTP method: " + method);
            };
            context.setLastResponse(ResponseEntity
                .status(response.getStatusCode())
                .body(Map.of("body", response.getBody() != null ? response.getBody() : "")));
            log.info("[E2E] {} partial-session {} {} returned: {}",
                alias, method, endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            ResponseEntity<Map> errorResponse = ResponseEntity
                .status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            log.info("[E2E] {} partial-session {} {} denied: {}",
                alias, method, endpoint, e.getStatusCode());
        }
    }

    // ==================== Helper Methods ====================

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
