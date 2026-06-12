package com.sm.instagram.platform.e2e.steps;

import com.icegreen.greenmail.util.GreenMail;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for magic link E2E tests.
 * Tests the apply-action-code, verify-reset-code, and confirm-password-reset
 * endpoints for both error handling (negative) and happy path scenarios.
 */
@Slf4j
public class MagicLinkSteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private GreenMail greenMail;

    private static final String OOB_CODE_KEY = "extractedOobCode";

    // Handles both raw & and HTML-encoded &amp; in email bodies
    private static final Pattern OOB_CODE_PATTERN = Pattern.compile(
            "(?:[?&]|&amp;)oobCode=([A-Za-z0-9_-]+)"
    );

    // ========================================================================
    // USER SETUP STEPS
    // ========================================================================

    // Note: "the user has emailVerified set to {word}" step is defined in RegistrySteps
    // and is reused here — no duplicate definition needed.
    // Real Firebase login steps are defined in FullAuthSteps (loginAsCompanyWithCredentials,
    // exchangeFirebaseTokenForSession, syncUserFromFirestore).

    @Given("the current email is {string}")
    public void setCurrentEmail(String email) {
        context.setCurrentEmail(email);
        log.info("[E2E] Set current email in context: {}", email);
    }

    // ========================================================================
    // EMAIL TRIGGERING STEPS
    // ========================================================================

    @When("I request a verification email")
    public void requestVerificationEmail() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addSessionCookies(headers);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        postWithRateLimitRetry("/auth/send-verification-email", entity, "Verification email");
    }

    @When("I request a password reset email for the current user")
    public void requestPasswordResetEmail() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", context.getCurrentEmail());
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        postWithRateLimitRetry("/auth/firebase/forgot-password", entity, "Password reset email");
    }

    // ========================================================================
    // GREENMAIL EMAIL INTERCEPTION STEPS
    // ========================================================================

    // NOTE: "GreenMail should have received at least {int} email(s) within {int} seconds"
    // is defined in StepUpAuthSteps.java — shared via common glue path. Do not duplicate here.

    @And("I extract the oobCode from the last GreenMail email")
    public void extractOobCodeFromLastEmail() {
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages.length).as("GreenMail should have at least 1 email").isGreaterThan(0);

        String body = getEmailBody(messages[messages.length - 1]);
        String oobCode = extractOobCodeFromHtml(body);

        context.put(OOB_CODE_KEY, oobCode);
        log.info("[E2E] Extracted oobCode from email (length={})", oobCode.length());
    }

    // ========================================================================
    // MAGIC LINK ENDPOINT STEPS — with explicit oobCode
    // ========================================================================

    @When("I send apply-action-code with oobCode {string}")
    public void sendApplyActionCode(String oobCode) {
        Map<String, Object> body = Map.of("oobCode", oobCode);
        postJsonToEndpoint("/auth/firebase/apply-action-code", body);
    }

    @When("I send verify-reset-code with oobCode {string}")
    public void sendVerifyResetCode(String oobCode) {
        Map<String, Object> body = Map.of("oobCode", oobCode);
        postJsonToEndpoint("/auth/firebase/verify-reset-code", body);
    }

    @When("I send confirm-password-reset with oobCode {string} and newPassword {string}")
    public void sendConfirmPasswordReset(String oobCode, String newPassword) {
        Map<String, Object> body = Map.of("oobCode", oobCode, "newPassword", newPassword);
        postJsonToEndpoint("/auth/firebase/confirm-password-reset", body);
    }

    // ========================================================================
    // MAGIC LINK ENDPOINT STEPS — with extracted oobCode from context
    // ========================================================================

    @When("I send apply-action-code with the extracted oobCode")
    public void sendApplyActionCodeWithExtracted() {
        String oobCode = context.get(OOB_CODE_KEY);
        assertThat(oobCode).as("Extracted oobCode should exist in context").isNotNull();
        Map<String, Object> body = Map.of("oobCode", oobCode);
        postJsonToEndpoint("/auth/firebase/apply-action-code", body);
    }

    @When("I send verify-reset-code with the extracted oobCode")
    public void sendVerifyResetCodeWithExtracted() {
        String oobCode = context.get(OOB_CODE_KEY);
        assertThat(oobCode).as("Extracted oobCode should exist in context").isNotNull();
        Map<String, Object> body = Map.of("oobCode", oobCode);
        postJsonToEndpoint("/auth/firebase/verify-reset-code", body);
    }

    @When("I send confirm-password-reset with the extracted oobCode and newPassword {string}")
    public void sendConfirmPasswordResetWithExtracted(String newPassword) {
        String oobCode = context.get(OOB_CODE_KEY);
        assertThat(oobCode).as("Extracted oobCode should exist in context").isNotNull();
        Map<String, Object> body = Map.of("oobCode", oobCode, "newPassword", newPassword);
        postJsonToEndpoint("/auth/firebase/confirm-password-reset", body);
    }

    // ========================================================================
    // DIRECT OOB CODE GENERATION — via test endpoint (no email, no rate limit)
    // ========================================================================

    @When("I generate a verification oobCode via test endpoint")
    public void generateVerificationOobViaTestEndpoint() {
        String email = context.getCurrentEmail();
        String firebaseUid = context.getCurrentFirebaseUid();
        assertThat(email).as("Current email should be in context").isNotNull();
        assertThat(firebaseUid).as("Firebase UID should be in context").isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", email, "firebaseUid", firebaseUid);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/generate-verification-oob"), entity, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("generate-verification-oob should succeed").isTrue();

        String oobCode = (String) response.getBody().get("oobCode");
        assertThat(oobCode).as("oobCode should not be null").isNotNull();

        context.put(OOB_CODE_KEY, oobCode);
        log.info("[E2E] Generated verification oobCode via test endpoint (length={})", oobCode.length());
    }

    @When("I generate a password reset oobCode via test endpoint")
    public void generatePasswordResetOobViaTestEndpoint() {
        String email = context.getCurrentEmail();
        assertThat(email).as("Current email should be in context").isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", email);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/generate-password-reset-oob"), entity, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("generate-password-reset-oob should succeed").isTrue();

        String oobCode = (String) response.getBody().get("oobCode");
        assertThat(oobCode).as("oobCode should not be null").isNotNull();

        context.put(OOB_CODE_KEY, oobCode);
        log.info("[E2E] Generated password reset oobCode via test endpoint (length={})", oobCode.length());
    }

    // ========================================================================
    // GENERIC POST STEPS — for DTO validation and malformed JSON tests
    // ========================================================================

    @When("I send a POST to {string} with body:")
    public void sendPostWithDocString(String endpoint, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url(endpoint), entity, Map.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJsonBody(e.getResponseBodyAsString())));
        } catch (HttpServerErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJsonBody(e.getResponseBodyAsString())));
        }
    }

    @When("I send a POST to {string} with raw body {string}")
    public void sendPostWithRawBody(String endpoint, String rawBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(rawBody, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url(endpoint), entity, Map.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJsonBody(e.getResponseBodyAsString())));
        } catch (HttpServerErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJsonBody(e.getResponseBodyAsString())));
        }
    }

    @When("I send confirm-password-reset with oobCode {string} and a password exceeding maximum length")
    public void sendConfirmPasswordResetWithLongPassword(String oobCode) {
        // Generate a 129-character password that meets complexity requirements
        String longPassword = "Ab1" + "x".repeat(126);
        Map<String, Object> body = Map.of("oobCode", oobCode, "newPassword", longPassword);
        postJsonToEndpoint("/auth/firebase/confirm-password-reset", body);
    }

    // ========================================================================
    // FIREBASE STATE MANAGEMENT STEPS (for happy path + cleanup)
    // ========================================================================

    @Given("the Firebase user has password {string}")
    public void setFirebaseUserPassword(String password) {
        String firebaseUid = context.getCurrentFirebaseUid();
        assertThat(firebaseUid).as("Firebase UID should be in context").isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("firebaseUid", firebaseUid, "password", password);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/update-firebase-user"), entity, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("update-firebase-user should succeed").isTrue();

        log.info("[E2E] Set Firebase user password for uid={}", firebaseUid);
    }

    @Given("the Firebase user has emailVerified set to {word}")
    public void setFirebaseUserEmailVerified(String verifiedStr) {
        boolean verified = Boolean.parseBoolean(verifiedStr);
        String firebaseUid = context.getCurrentFirebaseUid();
        assertThat(firebaseUid).as("Firebase UID should be in context").isNotNull();

        // Update Firebase Auth
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> firebaseBody = Map.of("firebaseUid", firebaseUid, "emailVerified", verified);
        HttpEntity<Map<String, Object>> firebaseEntity = new HttpEntity<>(firebaseBody, headers);

        ResponseEntity<Map> firebaseResponse = restTemplate.postForEntity(
                url("/test/auth/update-firebase-user"), firebaseEntity, Map.class);
        assertThat(firebaseResponse.getStatusCode().is2xxSuccessful())
                .as("update-firebase-user should succeed").isTrue();

        // Also sync PostgreSQL via existing endpoint
        Map<String, Object> pgBody = Map.of("firebaseUid", firebaseUid, "verified", verified);
        HttpEntity<Map<String, Object>> pgEntity = new HttpEntity<>(pgBody, headers);

        ResponseEntity<Map> pgResponse = restTemplate.postForEntity(
                url("/test/registry/set-email-verified"), pgEntity, Map.class);
        assertThat(pgResponse.getStatusCode().is2xxSuccessful())
                .as("set-email-verified should succeed").isTrue();

        log.info("[E2E] Set emailVerified={} in Firebase + PostgreSQL for uid={}", verified, firebaseUid);
    }

    @Given("the password reset cooldown is cleared")
    public void clearPasswordResetCooldown() {
        String firebaseUid = context.getCurrentFirebaseUid();
        assertThat(firebaseUid).as("Firebase UID should be in context").isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("firebaseUid", firebaseUid);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/registry/clear-password-reset-cooldown"), entity, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("clear-password-reset-cooldown should succeed").isTrue();

        log.info("[E2E] Cleared password reset cooldown for uid={}", firebaseUid);
    }

    @Then("I should be able to login with the new password {string}")
    public void verifyLoginWithNewPassword(String newPassword) {
        String email = context.getCurrentEmail();
        assertThat(email).as("Current email should be in context").isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("email", email, "password", newPassword);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/auth/firebase/login"), entity, Map.class);
            assertThat(response.getStatusCode().is2xxSuccessful())
                    .as("Login with new password should succeed").isTrue();
            log.info("[E2E] Successfully logged in with new password for email={}", email);
        } catch (HttpClientErrorException e) {
            throw new AssertionError("Login with new password failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private void postJsonToEndpoint(String endpoint, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url(endpoint), entity, Map.class);
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJsonBody(e.getResponseBodyAsString())));
        } catch (HttpServerErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                    .body(parseJsonBody(e.getResponseBodyAsString())));
        }
    }

    /**
     * POST with automatic retry on 429 (Firebase TOO_MANY_ATTEMPTS rate limit).
     * TestRestTemplate does not throw on 4xx — it returns the response directly.
     */
    private void postWithRateLimitRetry(String endpoint, HttpEntity<?> entity, String label) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url(endpoint), entity, Map.class);
            int status = response.getStatusCode().value();
            if (status >= 200 && status < 300) {
                log.info("[E2E] {} requested, status={}", label, response.getStatusCode());
                return;
            }
            if (status == 429 && attempt < maxRetries) {
                log.warn("[E2E] {} rate limited (attempt {}/{}), retrying after 15s delay",
                        label, attempt, maxRetries);
                try { Thread.sleep(15_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                continue;
            }
            throw new AssertionError(label + " request failed with status " + status
                    + " (attempt " + attempt + "/" + maxRetries + ")");
        }
    }

    private void addSessionCookies(HttpHeaders headers) {
        StringBuilder cookie = new StringBuilder();
        if (context.getSessionCookie() != null) {
            cookie.append("session=").append(context.getSessionCookie());
        }
        if (context.getSessionSigCookie() != null) {
            if (!cookie.isEmpty()) cookie.append("; ");
            cookie.append("session_sig=").append(context.getSessionSigCookie());
        }
        if (!cookie.isEmpty()) {
            headers.add("Cookie", cookie.toString());
        }
    }

    private String getEmailBody(MimeMessage message) {
        try {
            Object content = message.getContent();
            if (content instanceof String) {
                return (String) content;
            }
            if (content instanceof Multipart multipart) {
                String result = extractTextFromMultipart(multipart);
                if (result != null) {
                    return result;
                }
            }
            throw new AssertionError("Could not extract text content from email. Content type: " + content.getClass());
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("Failed to read email body: " + e.getMessage(), e);
        }
    }

    private String extractTextFromMultipart(Multipart multipart) throws Exception {
        // First pass: look for text/html (preferred)
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.isMimeType("text/html")) {
                return (String) part.getContent();
            }
            // Recurse into nested multipart
            if (part.getContent() instanceof Multipart nested) {
                String result = extractTextFromMultipart(nested);
                if (result != null) return result;
            }
        }
        // Second pass: fall back to text/plain
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.isMimeType("text/plain")) {
                return (String) part.getContent();
            }
        }
        return null;
    }

    private String extractOobCodeFromHtml(String htmlBody) {
        Matcher matcher = OOB_CODE_PATTERN.matcher(htmlBody);
        if (!matcher.find()) {
            throw new AssertionError(
                    "Could not find oobCode in email body. Body preview: " +
                            htmlBody.substring(0, Math.min(500, htmlBody.length()))
            );
        }
        return matcher.group(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("message", json);
        }
    }

}
