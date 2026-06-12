package com.sm.instagram.platform.e2e.steps;

import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.support.SoftAssertionContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.Map;

/**
 * Step definitions for admin operations on user status - SOFT ASSERTION VARIANTS.
 *
 * <p>Note: The following steps are defined in AdvancedSessionSecuritySteps and should be reused:
 * <ul>
 *   <li>"{string} sets user {string} status to {string}"</li>
 *   <li>"{string} bans user {string} with reason {string}"</li>
 *   <li>"{string} unbans user {string}"</li>
 *   <li>"{string} stores their current token as {string}"</li>
 *   <li>"{string} using stored token {string} calling {string} should return {int}"</li>
 * </ul>
 *
 * <p>This class provides ADDITIONAL steps for:
 * <ul>
 *   <li>Soft assertion variants of token/status tests</li>
 *   <li>Admin user field restoration</li>
 *   <li>Profile value storage/restoration</li>
 * </ul>
 */
@Slf4j
public class AdminStatusFlowSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private SoftAssertionContext softAssert;

    // =========================================================================
    // Admin User Field Updates (not duplicates)
    // =========================================================================

    @When("{string} updates user {string} {word} to original value")
    public void adminRestoresUserField(String adminName, String targetUserName, String field) {
        Actor admin = actorRegistry.get(adminName);
        Actor targetUser = actorRegistry.get(targetUserName);
        Long targetUserId = targetUser.getSession().getUserId();

        // Get original value stored during login or use default
        String originalValue = targetUser.getSession().get("original_" + field);
        if (originalValue == null) {
            // Fetch current value as "original" for testing
            ResponseEntity<Map> userResponse = admin.get(restTemplate, url("/users/" + targetUserId));
            if (userResponse.getBody() != null) {
                originalValue = (String) userResponse.getBody().get(field);
            }
        }

        Map<String, Object> update = new HashMap<>();
        update.put(field, originalValue != null ? originalValue : "OriginalValue");

        ResponseEntity<Map> response = admin.patch(restTemplate, url("/users/" + targetUserId), update);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("[E2E] CLEANUP FAILED: Admin '{}' could not restore user '{}' {} - status: {}",
                adminName, targetUserName, field, response.getStatusCode());
        }
        log.info("[E2E] Admin '{}' restored user '{}' {} to original value (status: {})",
            adminName, targetUserName, field, response.getStatusCode());
    }

    @When("{string} restores user {string} original profile values")
    public void adminRestoresUserProfile(String adminName, String targetUserName) {
        adminRestoresUserField(adminName, targetUserName, "firstName");
        adminRestoresUserField(adminName, targetUserName, "lastName");
    }

    // =========================================================================
    // Soft Assert Token Testing (different pattern from AdvancedSessionSecuritySteps)
    // =========================================================================

    @Then("soft assert {string} using stored token {string} calling {string} returns {int}")
    public void softAssertStoredTokenReturnsStatus(String actorName, String tokenKey, String endpoint, int expectedStatus) {
        Actor actor = actorRegistry.get(actorName);
        String storedToken = actor.requireResource(tokenKey);
        // Get sig cookie from stored resources (stored alongside the session token)
        String storedSig = actor.getResource(tokenKey + "_sig");

        // Make request with the stored token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Cookie", "session=" + storedToken);
        if (storedSig != null) {
            headers.add("Cookie", "session_sig=" + storedSig);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        int actualStatus;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url(endpoint), HttpMethod.GET, entity, Map.class);
            actualStatus = response.getStatusCode().value();
        } catch (HttpClientErrorException e) {
            // Capture the error status instead of throwing
            actualStatus = e.getStatusCode().value();
            log.debug("[E2E] Stored token '{}' returned error {}: {}",
                tokenKey, actualStatus, e.getResponseBodyAsString());
        }

        softAssert.softAssertStatus(expectedStatus, actualStatus,
            "Request with stored token '" + tokenKey + "' to " + endpoint);
    }

    @Then("soft assert {string} using {string} returns {int}")
    public void softAssertTokenReturnsStatus(String actorName, String tokenKey, int expectedStatus) {
        softAssertStoredTokenReturnsStatus(actorName, tokenKey, "/users/me", expectedStatus);
    }

    @Then("soft assert {string} can access {string} with status {int}")
    public void softAssertCanAccessWithStatus(String actorName, String endpoint, int expectedStatus) {
        Actor actor = actorRegistry.get(actorName);
        ResponseEntity<Map> response = actor.get(restTemplate, url(endpoint));
        softAssert.softAssertStatus(expectedStatus, response.getStatusCode().value(),
            actorName + " accessing " + endpoint);
    }

    @Then("soft assert {string} accountStatus is {string}")
    public void softAssertActorAccountStatus(String actorName, String expectedStatus) {
        Actor actor = actorRegistry.get(actorName);
        ResponseEntity<Map> response = actor.get(restTemplate, url("/users/me"));

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            // accountStatus is an object with 'value' field containing the status string
            Object accountStatusObj = response.getBody().get("accountStatus");
            String actualStatus = extractAccountStatusValue(accountStatusObj);
            softAssert.softAssertEquals(expectedStatus, actualStatus,
                actorName + " accountStatus should be " + expectedStatus);
        } else {
            softAssert.softAssertTrue(false,
                "Failed to get accountStatus for " + actorName + " - status: " + response.getStatusCode());
        }
    }

    /**
     * Extracts the status value from accountStatus object.
     * Handles both Map (from JSON) and String (legacy) formats.
     */
    @SuppressWarnings("unchecked")
    private String extractAccountStatusValue(Object accountStatusObj) {
        if (accountStatusObj == null) {
            return null;
        }
        if (accountStatusObj instanceof String) {
            return (String) accountStatusObj;
        }
        if (accountStatusObj instanceof Map) {
            Map<String, Object> statusMap = (Map<String, Object>) accountStatusObj;
            return (String) statusMap.get("value");
        }
        return accountStatusObj.toString();
    }

    // =========================================================================
    // Profile Value Storage
    // =========================================================================

    @And("{string} stores their original profile values")
    public void storeOriginalProfileValues(String actorName) {
        Actor actor = actorRegistry.get(actorName);
        ResponseEntity<Map> response = actor.get(restTemplate, url("/users/me"));

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> profile = response.getBody();
            actor.getSession().put("original_firstName", profile.get("firstName"));
            actor.getSession().put("original_lastName", profile.get("lastName"));
            actor.getSession().put("original_email", profile.get("email"));
            actor.getSession().put("original_phoneNumber", profile.get("phoneNumber"));
            log.info("[E2E] Actor '{}' stored original profile values", actorName);
        }
    }
}
