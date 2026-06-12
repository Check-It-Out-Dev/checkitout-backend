package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for consent lifecycle E2E tests.
 * Tests: cron simulation, grace period, blocked user restrictions, re-consent, full lifecycle.
 */
@Slf4j
public class ConsentLifecycleSteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * After each lifecycle scenario, reset document published_at to now to avoid polluting
     * other consent tests that rely on default seed data dates.
     */
    @After("@consent-lifecycle")
    public void resetPublishedAtAfterScenario() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("publishedAt", LocalDateTime.now().toString());
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url("/test/legal/set-published-at"), entity, Map.class);
            log.info("[E2E-Lifecycle] Reset published_at to now (cleanup)");
        } catch (Exception e) {
            log.warn("[E2E-Lifecycle] Failed to reset published_at: {}", e.getMessage());
        }
    }

    // ==================== User Setup Steps ====================

    @Given("a(n) {word} user {string} with consents not accepted")
    public void userWithConsentsNotAccepted(String role, String email) {
        ensureUser(email, role);
        resetConsentsForUser(email);
        setAccountStatus(email, "ACTIVE");
        log.info("[E2E-Lifecycle] Created {} user {} with consents not accepted", role, email);
    }

    @Given("a(n) {word} user {string} with status {string}")
    public void userWithStatus(String role, String email, String status) {
        ensureUser(email, role);
        setAccountStatus(email, status);
        log.info("[E2E-Lifecycle] Created {} user {} with status {}", role, email, status);
    }

    @Given("a registered {word} user {string} with all consents accepted")
    public void registeredUserWithAllConsentsAccepted(String role, String email) {
        // Step 1: Accept cookie banner
        HttpHeaders cookieBannerHeaders = new HttpHeaders();
        cookieBannerHeaders.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> cookieBannerBody = Map.of(
                "documentName", "cookie_policy_v2_pl.pdf",
                "language", "pl",
                "isTrusted", true
        );
        HttpEntity<Map<String, Object>> cookieBannerEntity = new HttpEntity<>(cookieBannerBody, cookieBannerHeaders);
        ResponseEntity<Map> cookieBannerResp = restTemplate.postForEntity(
                url("/legal/anonymous/consent"), cookieBannerEntity, Map.class);
        Map<String, String> consentCookies = extractConsentCookies(cookieBannerResp);

        // Step 2: Prepare ToS consent
        consentCookies.putAll(prepareConsent("TERMS_OF_SERVICE", 2, consentCookies));

        // Step 3: Prepare Privacy Policy consent
        consentCookies.putAll(prepareConsent("PRIVACY_POLICY", 2, consentCookies));

        // Step 4: Register
        HttpHeaders regHeaders = new HttpHeaders();
        regHeaders.setContentType(MediaType.APPLICATION_JSON);
        addCookiesToHeaders(regHeaders, consentCookies);

        Map<String, Object> regBody = new HashMap<>();
        regBody.put("email", email);
        regBody.put("password", "TestPassword123!");
        regBody.put("userType", role);
        regBody.put("firstName", "E2E");
        regBody.put("lastName", "Lifecycle");

        HttpEntity<Map<String, Object>> regEntity = new HttpEntity<>(regBody, regHeaders);
        ResponseEntity<Map> regResp = restTemplate.postForEntity(
                url("/test/auth/register-without-firebase"), regEntity, Map.class);
        assertThat(regResp.getStatusCode().is2xxSuccessful())
                .as("Registration should succeed for %s", email).isTrue();

        Object userId = regResp.getBody().get("userId");
        context.put("registeredEmail_" + email, email);
        context.put("registeredUserId_" + email, userId);

        log.info("[E2E-Lifecycle] Registered {} user {} with all consents accepted, userId={}", role, email, userId);
    }

    @Given("a session exists for {string} as {word}")
    public void sessionExistsForUser(String email, String role) {
        // Step 1: Create mock session
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "email", email,
                "role", role,
                "partial", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/mock-session"), entity, Map.class);

        extractSessionCookiesFromResponse(response);

        // Step 2: Refresh session to get JWT with current tokenVersion from DB.
        // Mock-session hardcodes tokenVersion=1 but blockExpiredUsers() may have incremented it.
        // /auth/refresh-session is exempt from token version checks and returns a fresh JWT.
        refreshSession();

        // Step 3: For users with consents not accepted, re-set BLOCKED status after mock-session.
        // Mock-session creates JWT with ACTIVE status. We need to set the DB+cache status
        // to BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS so the ConsentEnforcementFilter sees it.
        // (Same pattern as ConsentSteps line 470)
        try {
            ResponseEntity<Map> statusResp = restTemplate.getForEntity(
                    url("/test/legal/user-consent-status?email=" + email), Map.class);
            log.info("[E2E-Lifecycle] user-consent-status for {}: httpStatus={}, body={}",
                    email, statusResp.getStatusCode(), statusResp.getBody());
            if (statusResp.getBody() != null) {
                Boolean consentsAccepted = (Boolean) statusResp.getBody().get("newestConsentsAccepted");
                String currentStatus = (String) statusResp.getBody().get("accountStatus");
                log.info("[E2E-Lifecycle] {} -> newestConsentsAccepted={}, accountStatus={}",
                        email, consentsAccepted, currentStatus);
                if (consentsAccepted != null && !consentsAccepted) {
                    // User has consents not accepted — set to BLOCKED
                    setAccountStatus(email, "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS");
                    log.info("[E2E-Lifecycle] Set {} to BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS after session creation", email);
                }
            }
        } catch (Exception e) {
            log.error("[E2E-Lifecycle] FAILED to check consent status for {}: {} - {}",
                    email, e.getClass().getSimpleName(), e.getMessage());
        }

        log.info("[E2E-Lifecycle] Session created for {}", email);
    }

    // ==================== Document Manipulation Steps ====================

    @When("all document published_at dates are set to {string} days ago")
    public void setPublishedAtDaysAgo(String daysAgo) {
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(Integer.parseInt(daysAgo));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("publishedAt", publishedAt.toString());
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/test/legal/set-published-at"), entity, Map.class);
            assertThat(response.getStatusCode().is2xxSuccessful())
                    .as("set-published-at should succeed, got %s", response.getStatusCode()).isTrue();
            log.info("[E2E-Lifecycle] Set all document published_at to {} days ago ({}), response: {}",
                    daysAgo, publishedAt, response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[E2E-Lifecycle] set-published-at failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    @When("a new version {int} of {string} is published with published_at {string} days ago")
    public void publishNewVersion(int version, String type, String daysAgo) {
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(Integer.parseInt(daysAgo));

        for (String lang : List.of("pl", "en")) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "type", type,
                    "language", lang,
                    "version", version,
                    "publishedAt", publishedAt.toString()
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        url("/test/legal/publish-document-version"), entity, Map.class);
                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.error("[E2E-Lifecycle] publish-document-version failed: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString());
                throw e;
            }
        }

        log.info("[E2E-Lifecycle] Published {} v{} with published_at {} days ago", type, version, daysAgo);
    }

    // ==================== Enforcement Steps ====================

    @When("consent enforcement is triggered")
    public void triggerEnforcement() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/legal/trigger-enforcement"), entity, Map.class);
        log.info("[E2E-Lifecycle] Consent enforcement response: {} - {}", response.getStatusCode(), response.getBody());
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("trigger-enforcement should succeed, got %s: %s",
                        response.getStatusCode(), response.getBody())
                .isTrue();
    }

    @When("all users' consents are reset")
    public void resetAllConsents() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of(), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/legal/reset-consents"), entity, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        log.info("[E2E-Lifecycle] All users' consents reset");
    }

    @When("user {string} consents are reset")
    public void resetUserConsents(String email) {
        resetConsentsForUser(email);
        log.info("[E2E-Lifecycle] Consents reset for {}", email);
    }

    // ==================== Request Steps ====================

    @When("the user sends POST to {string} with empty body")
    public void sendPostWithEmptyBody(String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addSessionCookies(headers);

        HttpEntity<String> entity = new HttpEntity<>("{}", headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "http://localhost:" + port + endpoint,
                    HttpMethod.POST, entity, Map.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            ResponseEntity<Map> errorResponse = ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(parseJson(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
        }
    }

    // ==================== Assertion Steps ====================

    @Then("user {string} should have account status {string}")
    public void userShouldHaveAccountStatus(String email, String expectedStatus) {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/test/legal/user-consent-status?email=" + email), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();

        String actualStatus = (String) response.getBody().get("accountStatus");
        assertThat(actualStatus)
                .as("User %s should have status %s but was %s", email, expectedStatus, actualStatus)
                .isEqualTo(expectedStatus);
    }

    @Then("GET {string} should return newestConsentsAccepted {word}")
    public void getReturnsNewestConsentsAccepted(String endpoint, String expected) {
        HttpHeaders headers = new HttpHeaders();
        addSessionCookies(headers);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "http://localhost:" + port + endpoint,
                    HttpMethod.GET, entity, Map.class);
            context.setLastResponse(response);
            assertThat(response.getBody()).isNotNull();
            Boolean actual = (Boolean) response.getBody().get("newestConsentsAccepted");
            assertThat(actual)
                    .as("newestConsentsAccepted should be %s but was %s", expected, actual)
                    .isEqualTo(Boolean.parseBoolean(expected));
        } catch (HttpClientErrorException e) {
            ResponseEntity<Map> errorResponse = ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(parseJson(e.getResponseBodyAsString()));
            context.setLastResponse(errorResponse);
            // If we expected false and got a 403 for blocked user, that's also valid
            if ("false".equals(expected) && e.getStatusCode().value() == 403) {
                return;
            }
            throw e;
        }
    }

    @Then("GET {string} should return daysToAcceptNewTerms approximately {int}")
    public void getReturnsDaysToAcceptApprox(String endpoint, int expectedDays) {
        HttpHeaders headers = new HttpHeaders();
        addSessionCookies(headers);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "http://localhost:" + port + endpoint,
                    HttpMethod.GET, entity, Map.class);
            assertThat(response.getBody()).isNotNull();

            Object daysObj = response.getBody().get("daysToAcceptNewTerms");
            assertThat(daysObj).as("daysToAcceptNewTerms should be present").isNotNull();
            int actual = ((Number) daysObj).intValue();
            assertThat(actual)
                    .as("daysToAcceptNewTerms should be approximately %d but was %d", expectedDays, actual)
                    .isBetween(expectedDays - 1, expectedDays + 1);
        } catch (HttpClientErrorException e) {
            log.error("[E2E-Lifecycle] GET {} failed: {} - {}", endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    @Then("the response body field {string} should be {string}")
    public void responseBodyFieldShouldBe(String fieldName, String expectedValue) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Object actual = body.get(fieldName);
        assertThat(actual).as("Field '%s' should be '%s'", fieldName, expectedValue).isNotNull();

        // Handle enum-like objects that return {value: "X", label: "..."}
        if (actual instanceof Map) {
            Map<?, ?> mapValue = (Map<?, ?>) actual;
            if (mapValue.containsKey("value")) {
                actual = mapValue.get("value");
            }
        }
        assertThat(actual.toString()).isEqualTo(expectedValue);
    }

    // ==================== Helper Methods ====================

    private void ensureUser(String email, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("email", email, "role", role.toUpperCase());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/ensure-user"), entity, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private void setAccountStatus(String email, String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", email, "status", status);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/set-account-status"), entity, Map.class);
        log.info("[E2E-Lifecycle] Set account status for {}: {} (response: {})", email, status,
                response.getStatusCode());
    }

    private void resetConsentsForUser(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", email);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/test/legal/reset-consents"), entity, Map.class);
            assertThat(response.getStatusCode().is2xxSuccessful())
                    .as("reset-consents should succeed for %s, got %s", email, response.getStatusCode())
                    .isTrue();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[E2E-Lifecycle] reset-consents failed for {}: {} - {}",
                    email, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private Map<String, String> prepareConsent(String documentType, int version, Map<String, String> existingCookies) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addCookiesToHeaders(headers, existingCookies);

        Map<String, Object> proof = Map.of(
                "timestamp", System.currentTimeMillis(),
                "eventTrusted", true,
                "screenX", 100.0,
                "screenY", 200.0,
                "checkboxId", "consent-checkbox-" + documentType.toLowerCase()
        );

        Map<String, Object> body = Map.of(
                "documentType", documentType,
                "version", version,
                "documentHash", "e2e-test-hash-" + documentType,
                "proof", proof
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/legal/consent/prepare"), entity, String.class);

        return extractConsentCookies(response);
    }

    private Map<String, String> extractConsentCookies(ResponseEntity<?> response) {
        Map<String, String> cookies = new HashMap<>();
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            for (String cookie : setCookies) {
                String name = cookie.split("=")[0];
                if (name.startsWith("consent_")) {
                    cookies.put(name, extractCookieValue(cookie));
                }
            }
        }
        return cookies;
    }

    private void addCookiesToHeaders(HttpHeaders headers, Map<String, String> cookies) {
        if (cookies.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        headers.add("Cookie", sb.toString());
    }

    private void addSessionCookies(HttpHeaders headers) {
        if (context.getSessionCookie() != null) {
            headers.add("Cookie", "session=" + context.getSessionCookie());
        }
        if (context.getSessionSigCookie() != null) {
            headers.add("Cookie", "session_sig=" + context.getSessionSigCookie());
        }
    }

    private void extractSessionCookiesFromResponse(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session=") && !cookie.startsWith("session_sig=")) {
                    context.setSessionCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("session_sig=")) {
                    context.setSessionSigCookie(extractCookieValue(cookie));
                }
            }
        }
    }

    /**
     * Refresh session via POST /auth/refresh-session to get a JWT with current tokenVersion.
     * The refresh endpoint is exempt from token version checks in JwtAuthenticationFilter.
     */
    private void refreshSession() {
        HttpHeaders headers = new HttpHeaders();
        addSessionCookies(headers);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/auth/refresh-session",
                HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            extractSessionCookiesFromResponse(response);
            log.info("[E2E-Lifecycle] Session refreshed successfully (tokenVersion updated)");
        } else {
            log.warn("[E2E-Lifecycle] Session refresh returned {}", response.getStatusCode());
        }
    }

    private String extractCookieValue(String cookie) {
        return cookie.split(";")[0].split("=", 2)[1];
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("error", "Parse error", "message", json != null ? json : "");
        }
    }
}
