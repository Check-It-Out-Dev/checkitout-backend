package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for consent module E2E tests.
 * Tests the full consent lifecycle: anonymous consent, consent preparation,
 * registration with consents, consent enforcement, re-consent, and admin endpoints.
 */
@Slf4j
public class ConsentSteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Consent cookies accumulated across steps
    private final Map<String, String> consentCookies = new HashMap<>();

    /**
     * Before each consent scenario, delete documents above v2 to ensure a clean baseline.
     * v2 is the canonical published version that production runs on; tests register
     * against v2 and the "publish new version" lifecycle scenario advances to v3.
     */
    @Before("@consent or @consent-lifecycle")
    public void ensureV2OnlyDocuments() {
        try {
            restTemplate.delete(url("/test/legal/delete-documents-above-version?maxVersion=2"));
            log.info("[E2E] Cleaned up documents above v2 before consent scenario");
        } catch (Exception e) {
            log.warn("[E2E] Failed to clean up documents above v2: {}", e.getMessage());
        }
        consentCookies.clear();
    }

    // ==================== Legal Documents ====================

    @When("I request the current legal documents")
    public void requestCurrentLegalDocuments() {
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(
                    url("/legal/current"), List.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @Then("the response should contain legal documents with types")
    public void responseContainsLegalDocumentTypes(DataTable dataTable) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) response.getBody();
        List<String> expectedTypes = dataTable.asList();
        List<String> actualTypes = documents.stream()
                .map(d -> (String) d.get("type"))
                .toList();

        for (String expected : expectedTypes) {
            assertThat(actualTypes).as("Should contain document type: %s", expected)
                    .contains(expected);
        }
        log.info("[E2E] Verified legal documents contain types: {}", expectedTypes);
    }

    // ==================== Cookie Banner (Anonymous Consent) ====================

    @When("I accept the cookie banner for document {string}")
    public void acceptCookieBanner(String documentName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addConsentCookiesToHeaders(headers);

        Map<String, Object> body = Map.of(
                "documentName", documentName,
                "language", "pl",
                "isTrusted", true
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/legal/anonymous/consent"), entity, Map.class);
            context.setLastResponse(response);
            extractConsentCookiesFromResponse(response);
            log.info("[E2E] Cookie banner accepted: status={}", response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @Then("the response should contain a consent record ID")
    public void responseContainsConsentRecordId() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Object recordId = body.get("consentRecordId");
        assertThat(recordId).as("Response should contain consentRecordId").isNotNull();
        log.info("[E2E] Consent record ID: {}", recordId);
    }

    @Then("the response should set cookie {string}")
    public void responseShouldSetCookie(String cookieName) {
        ResponseEntity<?> response = context.getLastResponse();
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("Response should have Set-Cookie headers").isNotNull();

        boolean found = cookies.stream().anyMatch(c -> c.startsWith(cookieName + "="));
        assertThat(found).as("Cookie '%s' should be set", cookieName).isTrue();
        log.info("[E2E] Verified cookie '{}' is set", cookieName);
    }

    @And("I store the anonymous consent record ID")
    public void storeAnonymousConsentRecordId() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Object recordId = body.get("consentRecordId");
        assertThat(recordId).isNotNull();
        context.put("anonymousConsentRecordId", recordId);
        log.info("[E2E] Stored anonymous consent record ID: {}", recordId);
    }

    // ==================== Consent Preparation ====================

    @When("I prepare consent for document type {string} version {int}")
    public void prepareConsentForDocumentType(String documentType, int version) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addConsentCookiesToHeaders(headers);

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
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url("/legal/consent/prepare"), entity, String.class);
            // Convert to ResponseEntity<Map> for consistency, preserving headers (Set-Cookie)
            context.setLastResponse(ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(Map.of()));
            extractConsentCookiesFromResponse(response);
            log.info("[E2E] Consent prepared for {}: status={}", documentType, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    // ==================== Registration ====================

    @When("I attempt to register without consent cookies")
    public void attemptRegisterWithoutConsentCookies(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap(String.class, String.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Deliberately NOT adding consent cookies

        Map<String, Object> body = new HashMap<>();
        body.put("email", data.get("email"));
        body.put("password", data.get("password"));
        body.put("userType", data.get("userType"));
        body.put("firstName", "E2E");
        body.put("lastName", "Test");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/test/auth/register-without-firebase"), entity, Map.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @Given("a user already exists with email {string}")
    public void userAlreadyExistsWithEmail(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "email", email,
                "role", "COMPANY"
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url("/test/auth/ensure-user"), entity, Map.class);
        log.info("[E2E] Ensured user exists: {}", email);
    }

    @When("I register with consent cookies")
    public void registerWithConsentCookies(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap(String.class, String.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addConsentCookiesToHeaders(headers);

        Map<String, Object> body = new HashMap<>();
        body.put("email", data.get("email"));
        body.put("password", data.get("password"));
        body.put("userType", data.get("userType"));
        body.put("firstName", "E2E");
        body.put("lastName", "Consent");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/test/auth/register-without-firebase"), entity, Map.class);
            context.setLastResponse(response);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                context.put("registeredUserId", response.getBody().get("userId"));
                context.put("registeredUserEmail", data.get("email"));
                log.info("[E2E] Registration successful: userId={}", response.getBody().get("userId"));
            }
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @Then("the registration should be successful")
    public void registrationShouldBeSuccessful() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
    }

    // ==================== Admin Session & Consent Records ====================

    @Given("{string} logs in as ADMIN with mock session")
    public void logsInAsAdminWithMockSession(String actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "email", actor.toLowerCase() + "-consent-e2e@admin.test",
                "role", "ADMIN",
                "partial", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/mock-session"), entity, Map.class);

        // Extract session cookies for admin
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session=") && !cookie.startsWith("session_sig=")) {
                    context.put(actor + "_sessionCookie", extractCookieValue(cookie));
                } else if (cookie.startsWith("session_sig=")) {
                    context.put(actor + "_sessionSigCookie", extractCookieValue(cookie));
                }
            }
        }

        if (response.getBody() != null) {
            context.put(actor + "_userId", response.getBody().get("userId"));
        }

        log.info("[E2E] {} logged in as ADMIN", actor);
    }

    @When("{string} queries consent records for the newly registered user")
    public void queriesConsentRecordsForNewlyRegisteredUser(String actor) {
        Object userId = context.get("registeredUserId");
        assertThat(userId).as("Registered user ID should be stored").isNotNull();
        queryConsentRecordsForUser(actor, userId);
    }

    @When("{string} queries consent records for that user")
    public void queriesConsentRecordsForThatUser(String actor) {
        Object userId = context.get("targetUserId");
        assertThat(userId).as("Target user ID should be stored").isNotNull();
        queryConsentRecordsForUser(actor, userId);
    }

    private void queryConsentRecordsForUser(String actor, Object userId) {
        HttpHeaders headers = new HttpHeaders();
        addActorSessionCookies(headers, actor);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url("/admin/legal/consent-records/" + userId),
                    HttpMethod.GET, entity, List.class);
            context.setLastResponse(response);
            context.put("consentRecords", response.getBody());
            log.info("[E2E] {} queried consent records for userId={}: count={}",
                    actor, userId, response.getBody() != null ? response.getBody().size() : 0);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        } catch (HttpServerErrorException e) {
            log.error("[E2E] Server error querying consent records: {}", e.getResponseBodyAsString());
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @When("{string} checks orphaned anonymous consent records")
    public void checksOrphanedAnonymousRecords(String actor) {
        HttpHeaders headers = new HttpHeaders();
        addActorSessionCookies(headers, actor);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url("/admin/legal/orphaned-anonymous?hoursBack=1"),
                    HttpMethod.GET, entity, List.class);
            context.setLastResponse(response);
            context.put("orphanedRecords", response.getBody());
            log.info("[E2E] {} checked orphaned records: count={}",
                    actor, response.getBody() != null ? response.getBody().size() : 0);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        } catch (HttpServerErrorException e) {
            log.error("[E2E] Server error checking orphaned records: {}", e.getResponseBodyAsString());
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @Then("the consent records should contain {int} entries")
    public void consentRecordsShouldContainEntries(int count) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = context.get("consentRecords");
        assertThat(records).as("Consent records should not be null").isNotNull();
        assertThat(records).as("Should have %d consent records", count).hasSize(count);
    }

    @Then("the consent records should contain at least {int} entry")
    public void consentRecordsShouldContainAtLeast(int count) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = context.get("consentRecords");
        assertThat(records).as("Consent records should not be null").isNotNull();
        assertThat(records.size()).as("Should have at least %d consent records", count)
                .isGreaterThanOrEqualTo(count);
    }

    @Then("one record should have source {string} with the user linked")
    public void oneRecordShouldHaveSourceWithUserLinked(String source) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = context.get("consentRecords");
        assertThat(records).isNotNull();

        boolean found = records.stream().anyMatch(r ->
                source.equals(r.get("source")) && r.get("userId") != null);
        assertThat(found).as("Should have a %s record with user linked", source).isTrue();
    }

    @Then("one record should have document type {string}")
    public void oneRecordShouldHaveDocumentType(String documentType) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = context.get("consentRecords");
        assertThat(records).isNotNull();

        boolean found = records.stream().anyMatch(r ->
                documentType.equals(r.get("documentType")));
        assertThat(found).as("Should have a record with document type %s", documentType).isTrue();
    }

    @Then("the orphaned records should contain the stored anonymous record ID")
    public void orphanedRecordsShouldContainStoredId() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orphaned = context.get("orphanedRecords");
        Object storedId = context.get("anonymousConsentRecordId");
        assertThat(orphaned).isNotNull();
        assertThat(storedId).isNotNull();

        boolean found = orphaned.stream().anyMatch(r -> {
            Object id = r.get("id");
            return id != null && id.toString().equals(storedId.toString());
        });
        assertThat(found).as("Orphaned records should contain anonymous record ID %s", storedId).isTrue();
    }

    @Then("the linked record ID should match the stored anonymous record ID")
    public void linkedRecordIdShouldMatchStoredId() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = context.get("consentRecords");
        Object storedId = context.get("anonymousConsentRecordId");
        assertThat(records).isNotNull();
        assertThat(storedId).isNotNull();

        boolean found = records.stream().anyMatch(r ->
                "COOKIE_BANNER".equals(r.get("source"))
                        && r.get("id") != null
                        && r.get("id").toString().equals(storedId.toString()));
        assertThat(found).as("Linked COOKIE_BANNER record ID should match stored ID %s", storedId).isTrue();
    }

    // ==================== Consent Enforcement ====================

    @Given("a user exists with status {string}")
    public void userExistsWithStatus(String status) {
        String email = "consent-blocked-" + System.currentTimeMillis() + "@e2e.test";

        // Create user
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> ensureBody = Map.of("email", email, "role", "COMPANY");
        HttpEntity<Map<String, Object>> ensureEntity = new HttpEntity<>(ensureBody, headers);
        ResponseEntity<Map> ensureResp = restTemplate.postForEntity(
                url("/test/auth/ensure-user"), ensureEntity, Map.class);

        assertThat(ensureResp.getStatusCode().is2xxSuccessful()).isTrue();
        Object userId = ensureResp.getBody().get("userId");
        context.put("blockedUserEmail", email);
        context.put("blockedUserId", userId);

        // Set account status
        Map<String, String> statusBody = Map.of("email", email, "status", status);
        HttpEntity<Map<String, String>> statusEntity = new HttpEntity<>(statusBody, headers);
        restTemplate.postForEntity(url("/test/auth/set-account-status"), statusEntity, Map.class);

        log.info("[E2E] Created user with status {}: email={}, userId={}", status, email, userId);
    }

    @And("the user has a valid session")
    public void userHasValidSession() {
        String email = context.get("blockedUserEmail");
        assertThat(email).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "email", email,
                "role", "COMPANY",
                "partial", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/mock-session"), entity, Map.class);

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

        // Now set the status AFTER creating the session (mock-session creates user as ACTIVE)
        String statusEmail = context.get("blockedUserEmail");
        Map<String, String> statusBody = Map.of("email", statusEmail,
                "status", "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS");
        HttpEntity<Map<String, String>> statusEntity = new HttpEntity<>(statusBody, headers);
        restTemplate.postForEntity(url("/test/auth/set-account-status"), statusEntity, Map.class);

        log.info("[E2E] Session created for blocked user: {}", email);
    }

    @When("the user requests {string}")
    public void userRequestsEndpoint(String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        if (context.getSessionCookie() != null) {
            headers.add("Cookie", "session=" + context.getSessionCookie());
        }
        if (context.getSessionSigCookie() != null) {
            headers.add("Cookie", "session_sig=" + context.getSessionSigCookie());
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + endpoint,
                HttpMethod.GET, entity, String.class);
        Map<String, Object> bodyMap = parseJson(response.getBody());
        context.setLastResponse(ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(bodyMap));
    }

    @Then("the response should have header {string}")
    public void responseShouldHaveHeader(String headerName) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getHeaders().containsKey(headerName))
                .as("Response should have header '%s'", headerName).isTrue();
    }

    @Then("the header {string} should contain {string}")
    public void headerShouldContain(String headerName, String expected) {
        ResponseEntity<?> response = context.getLastResponse();
        List<String> values = response.getHeaders().get(headerName);
        assertThat(values).isNotNull();
        assertThat(String.join(",", values)).contains(expected);
    }

    // ==================== Re-consent ====================

    @When("the user records consent for all required documents")
    public void userRecordsConsentForAllRequiredDocuments() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (context.getSessionCookie() != null) {
            headers.add("Cookie", "session=" + context.getSessionCookie());
        }
        if (context.getSessionSigCookie() != null) {
            headers.add("Cookie", "session_sig=" + context.getSessionSigCookie());
        }

        Map<String, Object> proof = Map.of(
                "timestamp", System.currentTimeMillis(),
                "eventTrusted", true,
                "screenX", 100.0,
                "screenY", 200.0,
                "checkboxId", "reconsent-modal"
        );

        List<Map<String, Object>> records = List.of(
                Map.of("documentType", "COOKIE_POLICY", "action", "ACCEPTED", "proof", proof),
                Map.of("documentType", "TERMS_OF_SERVICE", "action", "ACCEPTED", "proof", proof),
                Map.of("documentType", "PRIVACY_POLICY", "action", "ACCEPTED", "proof", proof)
        );

        Map<String, Object> body = Map.of("records", records);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url("/legal/consent/record-batch"), entity, String.class);
            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(Map.of()));
            log.info("[E2E] Recorded batch consent: status={}", response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @And("the user refreshes their mock session")
    public void userRefreshesMockSession() {
        String email = context.get("blockedUserEmail");
        assertThat(email).as("blockedUserEmail should be stored in context").isNotNull();
        refreshMockSession(email, "COMPANY");
    }

    @And("the user {string} refreshes their mock session as {word}")
    public void namedUserRefreshesMockSession(String email, String role) {
        refreshMockSession(email, role);
    }

    private void refreshMockSession(String email, String role) {
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
        log.info("[E2E] Mock session refreshed for user: {}", email);
    }

    // ==================== Consent Status ====================

    @Given("a registered user with all consents accepted")
    public void registeredUserWithAllConsentsAccepted() {
        // Accept cookie banner
        acceptCookieBanner("cookie_policy_v2_pl.pdf");
        // Prepare ToS
        prepareConsentForDocumentType("TERMS_OF_SERVICE", 2);
        // Prepare Privacy Policy
        prepareConsentForDocumentType("PRIVACY_POLICY", 2);

        // Register
        String email = "consent-status-" + System.currentTimeMillis() + "@e2e.test";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addConsentCookiesToHeaders(headers);

        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", "TestPassword123!");
        body.put("userType", "COMPANY");
        body.put("firstName", "E2E");
        body.put("lastName", "Consent");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/register-without-firebase"), entity, Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Object userId = response.getBody().get("userId");
        context.put("targetUserId", userId);
        context.put("targetUserEmail", email);

        // Create session for this user
        HttpHeaders sessionHeaders = new HttpHeaders();
        sessionHeaders.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> sessionBody = Map.of(
                "email", email,
                "role", "COMPANY",
                "partial", false
        );
        HttpEntity<Map<String, Object>> sessionEntity = new HttpEntity<>(sessionBody, sessionHeaders);
        ResponseEntity<Map> sessionResponse = restTemplate.postForEntity(
                url("/test/auth/mock-session"), sessionEntity, Map.class);

        List<String> cookies = sessionResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session=") && !cookie.startsWith("session_sig=")) {
                    context.setSessionCookie(extractCookieValue(cookie));
                } else if (cookie.startsWith("session_sig=")) {
                    context.setSessionSigCookie(extractCookieValue(cookie));
                }
            }
        }

        log.info("[E2E] Created registered user with consents: email={}, userId={}", email, userId);
    }

    @When("the user checks consent status at {string}")
    public void userChecksConsentStatus(String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        if (context.getSessionCookie() != null) {
            headers.add("Cookie", "session=" + context.getSessionCookie());
        }
        if (context.getSessionSigCookie() != null) {
            headers.add("Cookie", "session_sig=" + context.getSessionSigCookie());
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "http://localhost:" + port + endpoint,
                    HttpMethod.GET, entity, Map.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @Then("the consent status should show newestConsentsAccepted is true")
    public void consentStatusNewestAccepted() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("newestConsentsAccepted")).isEqualTo(true);
    }

    @Then("the response error code should be {string}")
    public void responseErrorCodeShouldBe(String expectedCode) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        // Check both 'message' and 'errorCode' fields
        String message = (String) body.get("message");
        String errorCode = (String) body.get("errorCode");
        assertThat(message != null ? message : errorCode)
                .as("Error code/message should contain '%s'", expectedCode)
                .containsIgnoringCase(expectedCode.replace("error.", "").replace("_", " ")
                        .substring(0, Math.min(10, expectedCode.length())));
    }

    // ==================== OAuth Consent Cookie Survival ====================

    @When("I simulate OAuth callback for a new influencer with consent cookies")
    public void simulateOAuthCallbackWithConsentCookies() {
        String email = "oauth-influencer-" + System.currentTimeMillis() + "@e2e.test";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addConsentCookiesToHeaders(headers);

        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", "TestPassword123!");
        body.put("userType", "INFLUENCER");
        body.put("firstName", "E2E");
        body.put("lastName", "OAuth");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/test/auth/register-without-firebase"), entity, Map.class);
            context.setLastResponse(response);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                context.put("registeredUserId", response.getBody().get("userId"));
                context.put("registeredUserEmail", email);
                log.info("[E2E] OAuth simulation (with cookies) successful: userId={}", response.getBody().get("userId"));
            }
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @When("I simulate OAuth callback for a new influencer without consent cookies")
    public void simulateOAuthCallbackWithoutConsentCookies() {
        String email = "oauth-noconsent-" + System.currentTimeMillis() + "@e2e.test";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Deliberately NOT adding consent cookies — simulates cookies lost during redirect

        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", "TestPassword123!");
        body.put("userType", "INFLUENCER");
        body.put("firstName", "E2E");
        body.put("lastName", "NoCookies");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/test/auth/register-without-firebase"), entity, Map.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @When("the registered user creates a mock session without consent cookies")
    public void registeredUserCreatesMockSessionWithoutConsent() {
        String email = context.get("registeredUserEmail");
        assertThat(email).as("Registered user email should be in context").isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // NO consent cookies — proving re-login doesn't need them

        Map<String, Object> body = Map.of(
                "email", email,
                "role", "INFLUENCER",
                "partial", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/test/auth/mock-session"), entity, Map.class);
            context.setLastResponse(response);

            // Extract session cookies for subsequent access checks
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
            log.info("[E2E] Mock session created for existing user (no consent cookies): {}", email);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @Then("the response should indicate consent required error")
    public void responseShouldIndicateConsentRequired() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getStatusCode().value())
                .as("Should return 400 for missing/invalid consent cookies")
                .isEqualTo(400);

        if (response.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            String message = body.get("message") != null ? body.get("message").toString() : "";
            String errorCode = body.get("errorCode") != null ? body.get("errorCode").toString() : "";
            String combined = (message + " " + errorCode).toLowerCase();
            assertThat(combined)
                    .as("Error response should mention consent")
                    .containsAnyOf("consent", "missing");
        }
        log.info("[E2E] Verified consent required error response");
    }

    @Then("no user should be created from the OAuth callback")
    public void noUserCreatedFromCallback() {
        Object userId = context.get("registeredUserId");
        assertThat(userId).as("No user ID should be stored when registration fails").isNull();
    }

    @Then("the cookie {string} should have SameSite {string}")
    public void cookieShouldHaveSameSite(String cookieName, String expectedSameSite) {
        ResponseEntity<?> response = context.getLastResponse();
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("Response should have Set-Cookie headers").isNotNull();

        String targetCookie = cookies.stream()
                .filter(c -> c.startsWith(cookieName + "="))
                .findFirst()
                .orElse(null);
        assertThat(targetCookie)
                .as("Cookie '%s' should be present in Set-Cookie headers", cookieName)
                .isNotNull();
        assertThat(targetCookie.toLowerCase())
                .as("Cookie '%s' should have SameSite=%s", cookieName, expectedSameSite)
                .contains("samesite=" + expectedSameSite.toLowerCase());

        log.info("[E2E] Verified cookie '{}' has SameSite={}", cookieName, expectedSameSite);
    }

    @Then("the cookie {string} should be HttpOnly")
    public void cookieShouldBeHttpOnly(String cookieName) {
        ResponseEntity<?> response = context.getLastResponse();
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("Response should have Set-Cookie headers").isNotNull();

        String targetCookie = cookies.stream()
                .filter(c -> c.startsWith(cookieName + "="))
                .findFirst()
                .orElse(null);
        assertThat(targetCookie)
                .as("Cookie '%s' should be present in Set-Cookie headers", cookieName)
                .isNotNull();
        assertThat(targetCookie.toLowerCase())
                .as("Cookie '%s' should be HttpOnly", cookieName)
                .contains("httponly");

        log.info("[E2E] Verified cookie '{}' is HttpOnly", cookieName);
    }

    @When("I tamper with the consent cookie {string}")
    public void tamperWithConsentCookie(String cookieName) {
        assertThat(consentCookies.containsKey(cookieName))
                .as("Consent cookie '%s' should exist before tampering", cookieName)
                .isTrue();

        String originalValue = consentCookies.get(cookieName);
        // Flip last character to invalidate HMAC — signature cookie (_sig) remains unchanged
        String tampered = originalValue.substring(0, originalValue.length() - 1) +
                (originalValue.endsWith("X") ? "Y" : "X");
        consentCookies.put(cookieName, tampered);

        log.info("[E2E] Tampered with consent cookie '{}': value modified to invalidate HMAC", cookieName);
    }

    // ==================== Cookie Helpers ====================

    private void extractConsentCookiesFromResponse(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return;

        for (String cookie : cookies) {
            String name = cookie.split("=")[0];
            String value = extractCookieValue(cookie);
            if (name.startsWith("consent_")) {
                consentCookies.put(name, value);
            }
        }
    }

    private void addConsentCookiesToHeaders(HttpHeaders headers) {
        if (consentCookies.isEmpty()) return;

        StringBuilder cookieHeader = new StringBuilder();
        for (Map.Entry<String, String> entry : consentCookies.entrySet()) {
            if (!cookieHeader.isEmpty()) cookieHeader.append("; ");
            cookieHeader.append(entry.getKey()).append("=").append(entry.getValue());
        }
        headers.add("Cookie", cookieHeader.toString());
    }

    private void addActorSessionCookies(HttpHeaders headers, String actor) {
        String sessionCookie = context.get(actor + "_sessionCookie");
        String sessionSigCookie = context.get(actor + "_sessionSigCookie");
        if (sessionCookie != null) {
            headers.add("Cookie", "session=" + sessionCookie);
        }
        if (sessionSigCookie != null) {
            headers.add("Cookie", "session_sig=" + sessionSigCookie);
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
