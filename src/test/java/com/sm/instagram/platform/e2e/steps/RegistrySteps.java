package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for registry module E2E tests.
 * Tests the full company registry lifecycle: NIP lookup, confirmation,
 * auto-activation, and permission enforcement.
 */
@Slf4j
public class RegistrySteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Stored lookup response for cross-step assertions */
    private Map<String, Object> lastLookupResponse;

    /** Stored confirm response for cross-step assertions */
    private Map<String, Object> lastConfirmResponse;

    /** Stored company data response for cross-step assertions */
    private Map<String, Object> lastCompanyDataResponse;

    @Before("@registry")
    public void resetRegistryState() {
        try {
            restTemplate.postForEntity(testUrl("/registry/reset"), null, Map.class);
            restTemplate.postForEntity(testUrl("/registry/clear-cache"), null, Map.class);
            log.info("[E2E] Reset registry stubs and cache before scenario");
        } catch (Exception e) {
            log.warn("[E2E] Failed to reset registry state: {}", e.getMessage());
        }
        lastLookupResponse = null;
        lastConfirmResponse = null;
        lastCompanyDataResponse = null;
    }

    // ==================== Stub Configuration ====================

    @Given("registry stubs are reset")
    public void registryStubsAreReset() {
        restTemplate.postForEntity(testUrl("/registry/reset"), null, Map.class);
        restTemplate.postForEntity(testUrl("/registry/clear-cache"), null, Map.class);
        log.info("[E2E] Registry stubs reset");
    }

    @Given("a KRS company is configured for NIP {string}")
    public void krsCompanyConfigured(String nip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("nip", nip);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(testUrl("/registry/configure-krs-company"), entity, Map.class);
        log.info("[E2E] Configured KRS company for NIP: {}", nip);
    }

    @Given("a JDG company is configured for NIP {string}")
    public void jdgCompanyConfigured(String nip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("nip", nip);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(testUrl("/registry/configure-jdg-company"), entity, Map.class);
        log.info("[E2E] Configured JDG company for NIP: {}", nip);
    }

    @Given("GUS returns not-found for NIP {string}")
    public void gusNotFoundConfigured(String nip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("nip", nip);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(testUrl("/registry/configure-gus-not-found"), entity, Map.class);
        log.info("[E2E] Configured GUS not-found for NIP: {}", nip);
    }

    @Given("an inactive company is configured for NIP {string}")
    public void inactiveCompanyConfigured(String nip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("nip", nip);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        restTemplate.postForEntity(testUrl("/registry/configure-inactive-company"), entity, Map.class);
        log.info("[E2E] Configured inactive company for NIP: {}", nip);
    }

    // ==================== User Authentication ====================

    @Given("a COMPANY user is authenticated for registry tests")
    public void companyUserAuthenticated() {
        authenticateUser("COMPANY");
    }

    @Given("an INFLUENCER user is authenticated for registry tests")
    public void influencerUserAuthenticated() {
        authenticateUser("INFLUENCER");
    }

    private void authenticateUser(String role) {
        String email = "registry-" + role.toLowerCase() + "-" + System.currentTimeMillis() + "@e2e.test";

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

        // Extract session cookies
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

        if (response.getBody() != null) {
            context.setCurrentFirebaseUid((String) response.getBody().get("firebaseUid"));
            Object userId = response.getBody().get("userId");
            if (userId instanceof Number) {
                context.setCurrentUserId(((Number) userId).longValue());
            }
            context.setCurrentEmail(email);
            context.setCurrentRole(role);
        }

        log.info("[E2E] Authenticated {} user: email={}, firebaseUid={}",
                role, email, context.getCurrentFirebaseUid());
    }

    @Given("the user has emailVerified set to {word}")
    public void setEmailVerified(String verified) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "firebaseUid", context.getCurrentFirebaseUid(),
                "verified", Boolean.parseBoolean(verified)
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(testUrl("/registry/set-email-verified"), entity, Map.class);
        log.info("[E2E] Set emailVerified={} for user: {}", verified, context.getCurrentFirebaseUid());
    }

    @Given("another user already has company data with NIP {string}")
    public void anotherUserHasCompanyData(String nip) {
        // Create another user
        String email = "existing-nip-" + System.currentTimeMillis() + "@e2e.test";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> ensureBody = Map.of("email", email, "role", "COMPANY");
        HttpEntity<Map<String, Object>> ensureEntity = new HttpEntity<>(ensureBody, headers);
        ResponseEntity<Map> ensureResp = restTemplate.postForEntity(
                url("/test/auth/ensure-user"), ensureEntity, Map.class);

        String otherFirebaseUid = (String) ensureResp.getBody().get("firebaseUid");

        // Create company data for that user
        Map<String, Object> cdBody = Map.of(
                "firebaseUid", otherFirebaseUid,
                "nip", nip
        );
        HttpEntity<Map<String, Object>> cdEntity = new HttpEntity<>(cdBody, headers);
        restTemplate.postForEntity(testUrl("/registry/create-company-data"), cdEntity, Map.class);

        log.info("[E2E] Created company data for other user with NIP: {}", nip);
    }

    // ==================== Registry Actions ====================

    @When("the user performs a registry lookup for NIP {string}")
    public void performLookup(String nip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addSessionCookies(headers);

        Map<String, String> body = Map.of("nip", nip);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/registry/lookup"), entity, Map.class);
            context.setLastResponse(response);
            lastLookupResponse = response.getBody();
            log.info("[E2E] Lookup for NIP {}: status={}", nip, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            Map<String, Object> errorBody = parseJson(e.getResponseBodyAsString());
            context.setLastResponse(ResponseEntity.status(e.getStatusCode()).body(errorBody));
            log.info("[E2E] Lookup for NIP {} failed: {} - {}", nip, e.getStatusCode(), errorBody);
        }
    }

    @When("an unauthenticated user performs a registry lookup for NIP {string}")
    public void unauthenticatedLookup(String nip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Deliberately NOT adding session cookies

        Map<String, String> body = Map.of("nip", nip);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/registry/lookup"), entity, Map.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @When("the user confirms company data for NIP {string}")
    public void confirmCompanyData(String nip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addSessionCookies(headers);

        Map<String, String> body = Map.of("nip", nip);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/registry/confirm"), entity, Map.class);
            context.setLastResponse(response);
            lastConfirmResponse = response.getBody();
            log.info("[E2E] Confirm for NIP {}: status={}, body={}", nip, response.getStatusCode(), response.getBody());
        } catch (HttpClientErrorException e) {
            Map<String, Object> errorBody = parseJson(e.getResponseBodyAsString());
            context.setLastResponse(ResponseEntity.status(e.getStatusCode()).body(errorBody));
            log.info("[E2E] Confirm for NIP {} failed: {} - {}", nip, e.getStatusCode(), errorBody);
        }
    }

    @When("the user requests their company data")
    public void getCompanyData() {
        HttpHeaders headers = new HttpHeaders();
        addSessionCookies(headers);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url("/registry/company-data"), HttpMethod.GET, entity, Map.class);
            context.setLastResponse(response);
            lastCompanyDataResponse = response.getBody();
            log.info("[E2E] Get company data: status={}", response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    @When("the user refreshes their company data")
    public void refreshCompanyData() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addSessionCookies(headers);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/registry/refresh"), entity, Map.class);
            context.setLastResponse(response);
            lastLookupResponse = response.getBody();
            log.info("[E2E] Refresh company data: status={}", response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJson(e.getResponseBodyAsString())));
        }
    }

    // ==================== Lookup Assertions ====================

    @Then("the lookup response should contain NIP {string}")
    public void lookupContainsNip(String nip) {
        assertThat(lastLookupResponse).as("Lookup response should not be null").isNotNull();
        assertThat(lastLookupResponse.get("nip")).as("Lookup NIP").isEqualTo(nip);
    }

    @Then("the lookup response companyType should be {string}")
    public void lookupCompanyType(String expectedType) {
        assertThat(lastLookupResponse).isNotNull();
        assertThat(lastLookupResponse.get("companyType")).as("Company type").isEqualTo(expectedType);
    }

    @Then("the lookup response should contain company name")
    public void lookupContainsCompanyName() {
        assertThat(lastLookupResponse).isNotNull();
        assertThat(lastLookupResponse.get("companyName")).as("Company name").isNotNull();
    }

    @Then("the lookup response should contain address fields")
    public void lookupContainsAddressFields() {
        assertThat(lastLookupResponse).isNotNull();
        assertThat(lastLookupResponse.get("city")).as("City").isNotNull();
        assertThat(lastLookupResponse.get("postalCode")).as("Postal code").isNotNull();
    }

    @Then("the lookup response should contain owner name")
    public void lookupContainsOwnerName() {
        assertThat(lastLookupResponse).isNotNull();
        assertThat(lastLookupResponse.get("ownerName")).as("Owner name").isNotNull();
    }

    // ==================== Confirm Assertions ====================

    @Then("the confirm response activated should be {word}")
    public void confirmActivated(String expected) {
        assertThat(lastConfirmResponse).as("Confirm response should not be null").isNotNull();
        assertThat(lastConfirmResponse.get("activated"))
                .as("Confirm activated")
                .isEqualTo(Boolean.parseBoolean(expected));
    }

    @Then("the confirm response accountStatus should be {string}")
    public void confirmAccountStatus(String expected) {
        assertThat(lastConfirmResponse).isNotNull();
        assertThat(lastConfirmResponse.get("accountStatus"))
                .as("Confirm accountStatus")
                .isEqualTo(expected);
    }

    // ==================== Company Data Assertions ====================

    @Then("the company data response should contain NIP {string}")
    public void companyDataContainsNip(String nip) {
        assertThat(lastCompanyDataResponse).as("Company data response should not be null").isNotNull();
        assertThat(lastCompanyDataResponse.get("nip")).as("Company data NIP").isEqualTo(nip);
    }

    @Then("the company data response should contain company name")
    public void companyDataContainsCompanyName() {
        assertThat(lastCompanyDataResponse).isNotNull();
        assertThat(lastCompanyDataResponse.get("companyName")).as("Company name").isNotNull();
    }

    @Then("the company data response should contain owner name")
    public void companyDataContainsOwnerName() {
        assertThat(lastCompanyDataResponse).isNotNull();
        assertThat(lastCompanyDataResponse.get("ownerName")).as("Owner name").isNotNull();
    }

    @Then("the company data response body should be empty")
    public void companyDataBodyIsEmpty() {
        ResponseEntity<?> response = context.getLastResponse();
        // A 204 carries no body at all, which is what getCompanyData answers when the user has
        // confirmed nothing; it used to be a 200 with a null body, which is a 200 with no body and
        // no Content-Type. Both arrive here as null, so this step reads the same either way.
        Object body = response.getBody();
        boolean isEmpty = body == null
                || (body instanceof Map && ((Map<?, ?>) body).isEmpty())
                || "null".equals(String.valueOf(body));
        assertThat(isEmpty).as("Company data body should be empty/null").isTrue();
    }

    // ==================== Cookie Helpers ====================

    private void addSessionCookies(HttpHeaders headers) {
        if (context.getSessionCookie() != null) {
            headers.add("Cookie", "session=" + context.getSessionCookie());
        }
        if (context.getSessionSigCookie() != null) {
            headers.add("Cookie", "session_sig=" + context.getSessionSigCookie());
        }
    }

    private String extractCookieValue(String cookie) {
        return cookie.split(";")[0].split("=", 2)[1];
    }

    // ==================== URL Helpers ====================

    /**
     * Constructs a URL for test controller endpoints (under /api context path).
     */
    private String testUrl(String endpoint) {
        return url("/test" + endpoint);
    }

    // ==================== JSON Helpers ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("error", "Parse error", "message", json != null ? json : "");
        }
    }
}
