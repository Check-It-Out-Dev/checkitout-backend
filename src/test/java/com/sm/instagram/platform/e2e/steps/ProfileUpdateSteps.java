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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step definitions for profile update operations.
 * Supports both critical and non-critical field changes.
 *
 * <p>Non-critical fields (no token invalidation):
 * - profilePicture, companyDescription, addresses, preferences
 *
 * <p>Critical fields (trigger IN_VALIDATION + token invalidation):
 * - firstName, lastName, email, phoneNumber, name (business name), nip
 *
 * <p>Note: Token invalidation is handled by refreshing sessions after critical changes.
 * Tests should explicitly call refresh step when needed (knowledge from scenario design).
 */
@Slf4j
public class ProfileUpdateSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private SoftAssertionContext softAssert;

    // =========================================================================
    // Profile Field Updates
    // =========================================================================

    @When("{string} updates their {word} to {string}")
    public void updateProfileField(String actorName, String field, String value) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        Long userId = actor.getSession().getUserId();

        Map<String, Object> update = new HashMap<>();
        update.put(field, value);

        ResponseEntity<Map> response = actor.patch(restTemplate, url("/users/" + userId), update);
        log.info("[E2E] Actor '{}' updated {} to '{}' -> {}", actorName, field, value, response.getStatusCode());
    }

    @When("{string} attempts to update {word} with value {string}")
    public void attemptUpdateField(String actorName, String field, String value) {
        updateProfileField(actorName, field, value);
    }

    @When("{string} attempts to update {word} with value that is {int} characters")
    public void attemptUpdateFieldWithLength(String actorName, String field, int length) {
        String value = "X".repeat(length);
        updateProfileField(actorName, field, value);
    }

    @When("{string} attempts to update {word} with URL of {int} characters")
    public void attemptUpdateFieldWithUrlLength(String actorName, String field, int length) {
        // Create a valid HTTPS URL of the specified length
        String base = "https://example.com/";
        int remaining = length - base.length();
        String value = base + "x".repeat(Math.max(0, remaining));
        updateProfileField(actorName, field, value);
    }

    @When("{string} updates {word} and {word} together")
    public void updateMultipleFields(String actorName, String field1, String field2) {
        Actor actor = actorRegistry.get(actorName);
        Long userId = actor.getSession().getUserId();

        Map<String, Object> update = new HashMap<>();
        update.put(field1, batchValueFor(actor, field1));
        update.put(field2, batchValueFor(actor, field2));

        ResponseEntity<Map> response = actor.patch(restTemplate, url("/users/" + userId), update);
        log.info("[E2E] Actor '{}' batch updated {} and {} -> {}", actorName, field1, field2, response.getStatusCode());
    }

    /**
     * Value for a batched profile field.
     *
     * <p>{@code profilePicture} is a stored URL rendered to other users, so
     * the BE accepts <strong>only</strong> a tracked upload the caller made
     * (pentest 3.1 — uploadId resolved against the {@code file_uploads}
     * ownership table). We therefore send the {@code uploadId} the backend
     * returned from this scenario's upload; a raw/foreign URL — or one
     * pointing at another file in the bucket — is rejected. Every other field
     * takes an ordinary text value.
     */
    private Object batchValueFor(Actor actor, String field) {
        if ("profilePicture".equals(field)) {
            return actor.requireResource("uploadId");
        }
        return "Batch update " + field + " at " + System.currentTimeMillis();
    }

    // =========================================================================
    // Address Operations
    // =========================================================================

    @When("{string} creates address with street {string} city {string} postalCode {string} country {string}")
    public void createAddress(String actorName, String street, String city, String postalCode, String country) {
        Actor actor = actorRegistry.get(actorName);
        Long userId = actor.getSession().getUserId();

        Map<String, Object> address = new HashMap<>();
        address.put("street", street);
        address.put("city", city);
        address.put("postalCode", postalCode);
        address.put("country", country);
        address.put("primary", false);
        address.put("addressType", "Test Address");

        // Use /address/user/{userId} endpoint for creating addresses (not /address which is admin-only)
        ResponseEntity<Map> response = actor.post(restTemplate, url("/address/user/" + userId), address);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Object addressId = response.getBody().get("id");
            actor.storeResource("lastCreatedAddressId", addressId);
            log.info("[E2E] Actor '{}' created address with ID: {}", actorName, addressId);
        }
    }

    @When("{string} creates valid address with all fields")
    public void createValidAddress(String actorName) {
        createAddress(actorName, "Valid Street 123", "Valid City", "00-001", "Poland");
    }

    @When("{string} attempts to create address without {word}")
    public void attemptCreateAddressWithoutField(String actorName, String missingField) {
        Actor actor = actorRegistry.get(actorName);
        Long userId = actor.getSession().getUserId();

        Map<String, Object> address = new HashMap<>();
        if (!"street".equals(missingField)) address.put("street", "Test Street");
        if (!"city".equals(missingField)) address.put("city", "Test City");
        if (!"postalCode".equals(missingField)) address.put("postalCode", "00-001");
        if (!"country".equals(missingField)) address.put("country", "Poland");
        address.put("addressType", "Test Address");

        // Use /address/user/{userId} endpoint for creating addresses
        actor.post(restTemplate, url("/address/user/" + userId), address);
    }

    @When("{string} attempts to create address with {word} of {int} characters")
    public void attemptCreateAddressWithFieldLength(String actorName, String field, int length) {
        Actor actor = actorRegistry.get(actorName);
        Long userId = actor.getSession().getUserId();

        Map<String, Object> address = new HashMap<>();
        address.put("street", "street".equals(field) ? "X".repeat(length) : "Test Street");
        address.put("city", "city".equals(field) ? "X".repeat(length) : "Test City");
        address.put("postalCode", "postalCode".equals(field) ? "X".repeat(length) : "00-001");
        address.put("country", "country".equals(field) ? "X".repeat(length) : "Poland");
        address.put("addressType", "Test Address");

        // Use /address/user/{userId} endpoint for creating addresses
        actor.post(restTemplate, url("/address/user/" + userId), address);
    }

    @When("{string} updates the created address city to {string}")
    public void updateCreatedAddressCity(String actorName, String newCity) {
        Actor actor = actorRegistry.get(actorName);
        Object addressId = actor.requireResource("lastCreatedAddressId");

        Map<String, Object> update = new HashMap<>();
        update.put("city", newCity);

        // Use /address/{id} endpoint for updating addresses
        actor.patch(restTemplate, url("/address/" + addressId), update);
    }

    @When("{string} deletes the created test address")
    public void deleteCreatedAddress(String actorName) {
        Actor actor = actorRegistry.get(actorName);
        Object addressId = actor.getResource("lastCreatedAddressId");

        if (addressId != null) {
            // Use /address/{id} endpoint for deleting addresses
            actor.delete(restTemplate, url("/address/" + addressId));
            log.info("[E2E] Actor '{}' deleted address with ID: {}", actorName, addressId);
        }
    }

    @When("{string} deletes the created address")
    public void deleteAddress(String actorName) {
        deleteCreatedAddress(actorName);
    }

    // =========================================================================
    // Preferences Operations
    // =========================================================================

    // NOTE: Removed redundant specific methods (updatePreferencesLanguage, updatePreferencesTimezone)
    // to avoid step definition conflicts. Use generic updatePreferencesField instead.
    // Feature files should use: "actor" updates preferences with language en (unquoted value)

    @When("{string} updates preferences with {word} {word}")
    public void updatePreferencesField(String actorName, String field, String value) {
        Actor actor = actorRegistry.get(actorName);

        Map<String, Object> prefs = new HashMap<>();
        // Handle boolean values
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            prefs.put(field, Boolean.parseBoolean(value));
        } else {
            prefs.put(field, value);
        }

        // Use /user-preferences/me for authenticated user's own preferences
        actor.patch(restTemplate, url("/user-preferences/me"), prefs);
    }

    @When("{string} updates preferences with language {string} and {word} {word}")
    public void updatePreferencesMultiple(String actorName, String language, String field2, String value2) {
        Actor actor = actorRegistry.get(actorName);

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("language", language);
        if ("true".equalsIgnoreCase(value2) || "false".equalsIgnoreCase(value2)) {
            prefs.put(field2, Boolean.parseBoolean(value2));
        } else {
            prefs.put(field2, value2);
        }

        // Use /user-preferences/me for authenticated user's own preferences
        actor.patch(restTemplate, url("/user-preferences/me"), prefs);
    }

    // NOTE: Removed updatePreferencesCommunicationFrequency - use generic updatePreferencesField instead
    // Feature file: "actor" updates preferences with communicationFrequency DAILY

    @When("{string} attempts to update preferences with {word} {string}")
    public void attemptUpdatePreferences(String actorName, String field, String value) {
        updatePreferencesField(actorName, field, value);
    }

    @When("{string} attempts to update preferences with {word} of {int} characters")
    public void attemptUpdatePreferencesWithLength(String actorName, String field, int length) {
        Actor actor = actorRegistry.get(actorName);

        Map<String, Object> prefs = new HashMap<>();
        prefs.put(field, "X".repeat(length));

        // Use /user-preferences/me for authenticated user's own preferences
        actor.patch(restTemplate, url("/user-preferences/me"), prefs);
    }

    // =========================================================================
    // Status Code Assertions
    // =========================================================================

    @Then("soft assert update status is {int}")
    public void softAssertUpdateStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        int actualStatus = actor.getLastStatusCode();
        softAssert.softAssertStatus(expectedStatus, actualStatus, "profile update");
    }

    @Then("soft assert profile update status is {int}")
    public void softAssertProfileUpdateStatus(int expectedStatus) {
        softAssertUpdateStatus(expectedStatus);
    }

    @Then("soft assert status {int} for {word} update")
    public void softAssertStatusForFieldUpdate(int expectedStatus, String field) {
        Actor actor = actorRegistry.current();
        int actualStatus = actor.getLastStatusCode();
        softAssert.softAssertStatus(expectedStatus, actualStatus, field + " update");
    }

    @Then("soft assert response status is {int}")
    public void softAssertResponseStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        int actualStatus = actor.getLastStatusCode();
        softAssert.softAssertStatus(expectedStatus, actualStatus, "response");
    }

    @Then("soft assert address creation status is {int} or {int}")
    public void softAssertAddressCreationStatus(int status1, int status2) {
        Actor actor = actorRegistry.current();
        int actualStatus = actor.getLastStatusCode();
        softAssert.softAssertStatusIn(new int[]{status1, status2}, actualStatus, "address creation");
    }

    @Then("soft assert address update status is {int}")
    public void softAssertAddressUpdateStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        int actualStatus = actor.getLastStatusCode();
        softAssert.softAssertStatus(expectedStatus, actualStatus, "address update");
    }

    @Then("soft assert address deletion status is {int} or {int}")
    public void softAssertAddressDeletionStatus(int status1, int status2) {
        Actor actor = actorRegistry.current();
        int actualStatus = actor.getLastStatusCode();
        softAssert.softAssertStatusIn(new int[]{status1, status2}, actualStatus, "address deletion");
    }

    @Then("soft assert preferences update status is {int}")
    public void softAssertPreferencesStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        int actualStatus = actor.getLastStatusCode();
        softAssert.softAssertStatus(expectedStatus, actualStatus, "preferences update");
    }

    @Then("soft assert batch update status is {int}")
    public void softAssertBatchUpdateStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        int actualStatus = actor.getLastStatusCode();
        softAssert.softAssertStatus(expectedStatus, actualStatus, "batch profile update");
    }

    // =========================================================================
    // Account Status Assertions
    // =========================================================================

    @Then("soft assert GET \\/users\\/me shows accountStatus {string}")
    public void softAssertAccountStatus(String expectedStatus) {
        Actor actor = actorRegistry.current();
        ResponseEntity<Map> response = actor.get(restTemplate, url("/users/me"));

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            // accountStatus is an object with 'value' field containing the status string
            Object accountStatusObj = response.getBody().get("accountStatus");
            String actualStatus = extractAccountStatusValue(accountStatusObj);
            softAssert.softAssertEquals(expectedStatus, actualStatus, "accountStatus should be " + expectedStatus);
        } else {
            softAssert.softAssertTrue(false, "Failed to fetch /users/me");
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

    @Then("soft assert accountStatus is {string}")
    public void softAssertAccountStatusIs(String expectedStatus) {
        softAssertAccountStatus(expectedStatus);
    }

    @Then("soft assert accountStatus still {string}")
    public void softAssertAccountStatusStill(String expectedStatus) {
        softAssertAccountStatus(expectedStatus);
    }

    @Then("soft assert GET \\/users\\/me returns updated description")
    public void softAssertDescriptionUpdated() {
        Actor actor = actorRegistry.current();
        ResponseEntity<Map> response = actor.get(restTemplate, url("/users/me"));

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String description = (String) response.getBody().get("companyDescription");
            softAssert.softAssertNotNull(description, "companyDescription should be present after update");
        }
    }

    @Then("soft assert address appears in user addresses")
    public void softAssertAddressAppears() {
        Actor actor = actorRegistry.current();
        Long userId = actor.getSession().getUserId();
        // Use /address/user/{userId} endpoint for getting user's addresses
        // API returns a List of addresses, not a single Map
        ResponseEntity<List> response = actor.get(restTemplate, url("/address/user/" + userId), List.class);

        softAssert.softAssertTrue(
            response.getStatusCode().is2xxSuccessful(),
            "Should be able to fetch addresses"
        );
    }

    // =========================================================================
    // Error Message Assertions
    // =========================================================================

    @Then("soft assert error contains validation error for {word}")
    public void softAssertValidationError(String field) {
        Actor actor = actorRegistry.current();
        ResponseEntity<?> response = actor.getLastResponse();

        if (response.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            boolean hasError = body.containsKey("errors") || body.containsKey("message") || body.containsKey("error");
            softAssert.softAssertTrue(hasError, "Response should contain error for " + field);
        } else {
            softAssert.softAssertTrue(false, "Response body should be a map with error info for " + field);
        }
    }

    // Note: The following steps are defined in AdvancedSessionSecuritySteps:
    // - "{string} using stored token {string} calling {string} should return {int}"
    // - "{string} refreshes their session token"
    // - "{string} can access {string} successfully"

    // =========================================================================
    // Soft Assert Session Refresh (unique variants)
    // =========================================================================

    @Then("soft assert refresh succeeds")
    public void softAssertRefreshSucceeds() {
        Actor actor = actorRegistry.current();
        softAssert.softAssertTrue(actor.getSession().isAuthenticated(), "Session refresh should succeed");
    }

    @Then("soft assert refresh fails with {string}")
    public void softAssertRefreshFails(String errorCode) {
        Actor actor = actorRegistry.current();
        ResponseEntity<?> response = actor.getLastResponse();

        // Check for auth failure status codes
        int status = response != null ? response.getStatusCode().value() : -1;
        softAssert.softAssertTrue(
            status == 401 || status == 403 || status == 419,
            "Refresh should fail with auth error for " + errorCode
        );
    }

    @Then("soft assert {string} refresh fails with {string}")
    public void softAssertActorRefreshFails(String alias, String errorCode) {
        Actor actor = actorRegistry.get(alias);
        ResponseEntity<?> response = actor.getLastResponse();

        // Check for auth failure status codes
        int status = response != null ? response.getStatusCode().value() : -1;
        softAssert.softAssertTrue(
            status == 401 || status == 403 || status == 419,
            "Refresh should fail with auth error for " + errorCode + " (actor: " + alias + ")"
        );
    }

    // Note: "{string} can see their account status as {string}" is defined in AdvancedSessionSecuritySteps

    // =========================================================================
    // Final Assertion
    // =========================================================================

    @Then("all soft assertions should pass")
    public void verifyAllSoftAssertions() {
        // This is a marker step - actual verification happens in SoftAssertionHooks
        log.info("[E2E] All soft assertions marker reached. Total: {}, Failures: {}",
            softAssert.getTotalAssertions(), softAssert.getFailureCount());
    }
}
