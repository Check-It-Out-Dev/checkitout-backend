package com.sm.instagram.platform.e2e.steps;

import com.icegreen.greenmail.util.GreenMail;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for influencer email verification + password setup.
 * Tests the transactional complete-verification endpoint that verifies email
 * and sets password in one atomic operation.
 *
 * <p>Uses the multi-user actor pattern with real Firebase users. The influencer
 * must be logged in via OAuth with a real Firebase UID before verification steps.
 */
@Slf4j
public class InfluencerVerificationSteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private GreenMail greenMail;

    private static final String OOB_CODE_KEY = "extractedOobCode";

    // ========================================================================
    // ACTOR-BASED SETUP STEPS (multi-user pattern)
    // ========================================================================

    /**
     * Resets a named actor's influencer account for verification.
     * Sets PG: IN_VALIDATION + emailVerified=false.
     * Sets Firebase: emailVerified=false, removes password provider.
     */
    @Given("{string} is reset for verification")
    public void actorIsResetForVerification(String actorName) {
        Actor actor = actorRegistry.get(actorName);
        String email = actor.getSession().getEmail();

        // If email is null, resolve from session userId
        if (email == null || email.isBlank()) {
            // Use the sync endpoint to get email — but we can also try the actor's stored email
            log.warn("[E2E] Actor '{}' has no email in session, using firebaseUid for reset", actorName);
            email = actor.getSession().getFirebaseUid() + "@e2e.test";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", email);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/reset-influencer-for-verification"), entity, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("reset-influencer-for-verification should succeed for %s", actorName)
                .isTrue();

        // Also store email in ScenarioContext for steps that use it
        context.setCurrentEmail(email);
        context.setCurrentFirebaseUid(actor.getSession().getFirebaseUid());

        log.info("[E2E] Actor '{}' reset for verification: email={}", actorName, email);
    }

    /**
     * Requests a verification email for a named actor.
     * Uses the test bypass endpoint to avoid Firebase's external rate limit
     * (TOO_MANY_ATTEMPTS_TRY_LATER) which causes flaky tests.
     * The email is sent via SMTP and captured by GreenMail — identical to production
     * except the oobCode is synthetic (validated against Redis, not Firebase).
     */
    @When("{string} requests a verification email")
    public void actorRequestsVerificationEmail(String actorName) {
        Actor actor = actorRegistry.get(actorName);

        Map<String, String> body = Map.of("firebaseUid", actor.getSession().getFirebaseUid());
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/send-verification-email-bypass"), entity, Map.class);
        context.setLastResponse(response);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("send-verification-email-bypass should succeed for %s (got %s)", actorName, response.getStatusCode())
                .isTrue();

        log.info("[E2E] Actor '{}' verification email sent via bypass: status={}", actorName, response.getStatusCode());
    }

    /**
     * Drives the BUG-6 enforcement-filter regression: an unverified-email actor POSTs to
     * /applied-opportunity. EmailVerificationEnforcementFilter intercepts BEFORE the controller
     * reads the body, so an empty body is enough to assert the filter's 403 behavior.
     */
    @When("{string} attempts to POST \\/applied-opportunity")
    public void actorAttemptsPostAppliedOpportunity(String actorName) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> body = new HashMap<>();
        try {
            ResponseEntity<Map> response = actor.post(restTemplate, url("/applied-opportunity"), body);
            context.setLastResponse(response);
            log.info("[E2E] Actor '{}' POST /applied-opportunity: status={}", actorName, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJsonBody(e.getResponseBodyAsString())));
            log.info("[E2E] Actor '{}' POST /applied-opportunity error: status={}", actorName, e.getStatusCode());
        }
    }

    // ========================================================================
    // COMPLETE-VERIFICATION STEPS (public endpoint, no session needed)
    // ========================================================================

    @When("complete-verification is called with the extracted oobCode and password {string}")
    public void completeVerificationWithExtractedCode(String password) {
        String oobCode = context.get(OOB_CODE_KEY);
        assertThat(oobCode).as("Extracted oobCode should exist in context").isNotNull();

        callCompleteVerification(oobCode, password);
    }

    @When("complete-verification is called with oobCode {string} and password {string}")
    public void completeVerificationWithExplicitCode(String oobCode, String password) {
        callCompleteVerification(oobCode, password);
    }

    @When("complete-verification is called with the extracted oobCode only")
    public void completeVerificationWithoutPassword() {
        String oobCode = context.get(OOB_CODE_KEY);
        assertThat(oobCode).as("Extracted oobCode should exist in context").isNotNull();

        callCompleteVerification(oobCode, null);
    }

    private void callCompleteVerification(String oobCode, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("oobCode", oobCode);
        if (password != null) {
            body.put("password", password);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/auth/firebase/complete-verification"), entity, Map.class);
            context.setLastResponse(response);
            log.info("[E2E] complete-verification response: status={}", response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJsonBody(e.getResponseBodyAsString())));
            log.info("[E2E] complete-verification error: status={}", e.getStatusCode());
        }
    }

    // ========================================================================
    // ASSERTION STEPS
    // ========================================================================

    @Then("the response should contain userType {string}")
    public void responseShouldContainUserType(String expectedUserType) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isInstanceOf(Map.class);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("userType")).isEqualTo(expectedUserType);
    }

    @Then("the response should contain messageKey {string}")
    public void responseShouldContainMessageKey(String expectedKey) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isInstanceOf(Map.class);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("messageKey")).isEqualTo(expectedKey);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(String responseBody) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(responseBody, Map.class);
        } catch (Exception e) {
            return Map.of("rawBody", responseBody);
        }
    }
}
