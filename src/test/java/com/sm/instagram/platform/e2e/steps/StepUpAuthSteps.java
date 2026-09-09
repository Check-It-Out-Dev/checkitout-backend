package com.sm.instagram.platform.e2e.steps;

import com.icegreen.greenmail.util.GreenMail;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.support.SoftAssertionContext;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.e2e.multiuser.auth.MultiUserAuthService;
import com.sm.instagram.platform.e2e.support.TotpCodeGenerator;
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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for step-up authentication E2E tests.
 * Tests the full flow: check → request code → GreenMail capture → verify → token → PATCH email.
 */
@Slf4j
public class StepUpAuthSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private SoftAssertionContext softAssert;

    @Autowired
    private GreenMail greenMail;

    @Autowired
    private TotpCodeGenerator totpCodeGenerator;

    @Autowired
    private TotpFirestoreService totpFirestoreService;

    @Autowired
    private MultiUserAuthService multiUserAuthService;

    // Negative lookbehind for # to skip CSS hex colors like #333333, #667eea
    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("(?<!#)\\b(\\d{6})\\b");

    // =========================================================================
    // CHECK REQUIREMENT
    // =========================================================================

    @When("{string} checks step-up requirement for {string}")
    public void checksStepUpRequirement(String actorName, String actionType) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        ResponseEntity<Map> response = actor.get(restTemplate, url("/step-up/check?actionType=" + actionType));
        log.info("[E2E] Actor '{}' checked step-up requirement for {} -> {}", actorName, actionType, response.getStatusCode());
    }

    // =========================================================================
    // REQUEST CODE
    // =========================================================================

    @When("{string} requests step-up code for {string}")
    public void requestsStepUpCode(String actorName, String actionType) {
        greenMail.reset();
        actorRegistry.switchTo(actorName);

        Actor actor = actorRegistry.get(actorName);
        Map<String, String> body = Map.of("actionType", actionType);
        ResponseEntity<Map> response = actor.post(restTemplate, url("/step-up/request"), body);
        actor.storeResource("stepUpActionType", actionType);
        log.info("[E2E] Actor '{}' requested step-up code for {} -> {}", actorName, actionType, response.getStatusCode());
    }

    // =========================================================================
    // GREENMAIL WAIT
    // =========================================================================

    @Then("GreenMail should have received at least {int} email\\(s) within {int} seconds")
    public void greenMailShouldHaveReceivedAtLeastWithin(int minCount, int timeoutSeconds) {
        boolean received = greenMail.waitForIncomingEmail(timeoutSeconds * 1000L, minCount);
        assertThat(received)
                .as("GreenMail should have received at least %d email(s) within %d seconds, got %d",
                        minCount, timeoutSeconds, greenMail.getReceivedMessages().length)
                .isTrue();
    }

    // =========================================================================
    // EXTRACT CODE FROM GREENMAIL
    // =========================================================================

    @And("{string} extracts the 6-digit code from the last GreenMail email")
    public void extractsCodeFromGreenMail(String actorName) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages.length).as("GreenMail should have at least 1 email").isGreaterThan(0);

        String body = getEmailBody(messages[messages.length - 1]);
        Matcher matcher = SIX_DIGIT_CODE.matcher(body);
        assertThat(matcher.find())
                .as("Email body should contain a 6-digit code. Preview: %s",
                        body.substring(0, Math.min(300, body.length())))
                .isTrue();

        String code = matcher.group(1);
        actor.storeResource("stepUpCode", code);
        log.info("[E2E] Actor '{}' extracted step-up code from email", actorName);
    }

    // =========================================================================
    // VERIFY CODE
    // =========================================================================

    @When("{string} verifies step-up code for {string}")
    public void verifiesStepUpCode(String actorName, String actionType) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        String code = (String) actor.requireResource("stepUpCode");

        Map<String, String> body = Map.of("actionType", actionType, "code", code);
        ResponseEntity<Map> response = actor.post(restTemplate, url("/step-up/verify"), body);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Object token = response.getBody().get("token");
            if (token != null) {
                actor.storeResource("stepUpToken", token.toString());
                log.info("[E2E] Actor '{}' received step-up token", actorName);
            }
        }
    }

    @When("{string} submits wrong step-up code {string} for {string}")
    public void submitsWrongCode(String actorName, String wrongCode, String actionType) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        Map<String, String> body = Map.of("actionType", actionType, "code", wrongCode);
        ResponseEntity<Map> response = actor.post(restTemplate, url("/step-up/verify"), body);
        log.info("[E2E] Actor '{}' submitted wrong code -> {}", actorName, response.getStatusCode());
    }

    // =========================================================================
    // ADMIN TOTP VERIFY
    // =========================================================================

    @When("{string} verifies step-up with TOTP code")
    public void verifiesStepUpWithTotp(String actorName) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        // Use Firestore+KMS path (same as admin login 2FA) — not the empty property-based path
        String firebaseUid = actor.getSession().getFirebaseUid();
        String plainSecret = totpFirestoreService.getTotpSecret(firebaseUid);
        int totpCode = totpCodeGenerator.generateTotpCode(plainSecret);
        String code = String.format("%06d", totpCode);

        Map<String, String> body = Map.of("actionType", "EMAIL_CHANGE", "code", code);
        ResponseEntity<Map> response = actor.post(restTemplate, url("/step-up/verify"), body);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Object token = response.getBody().get("token");
            if (token != null) {
                actor.storeResource("stepUpToken", token.toString());
                log.info("[E2E] Actor '{}' received step-up token via TOTP", actorName);
            }
        }
    }

    // =========================================================================
    // PATCH EMAIL WITH TOKEN
    // =========================================================================

    @When("{string} updates their email to {string} with step-up token")
    public void updatesEmailWithToken(String actorName, String newEmail) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        Long userId = actor.getSession().getUserId();
        String token = (String) actor.requireResource("stepUpToken");

        Map<String, Object> body = new HashMap<>();
        body.put("email", newEmail);

        // Build headers with session cookies + step-up token
        HttpHeaders headers = actor.getSession().buildAuthHeaders();
        headers.add("X-Step-Up-Token", token);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/" + userId), HttpMethod.PATCH, entity, Map.class);
        actor.getSession().setLastResponse(response);
        log.info("[E2E] Actor '{}' patched email to '{}' with step-up token -> {}",
                actorName, newEmail, response.getStatusCode());
    }

    // =========================================================================
    // COMPOSITE: Full step-up flow
    // =========================================================================

    @When("{string} completes full step-up flow for {string}")
    public void completesFullStepUpFlow(String actorName, String actionType) {
        requestsStepUpCode(actorName, actionType);
        boolean received = greenMail.waitForIncomingEmail(5000L, 1);
        assertThat(received).as("GreenMail should receive step-up email").isTrue();
        extractsCodeFromGreenMail(actorName);
        verifiesStepUpCode(actorName, actionType);
    }

    // =========================================================================
    // RE-AUTHENTICATE (refresh session after tokenVersion change)
    // =========================================================================

    @When("{string} re-authenticates")
    public void reAuthenticates(String actorName) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        var oldSession = actor.getSession();

        // Re-login to get fresh session cookies (new tokenVersion in JWT)
        var newSession = multiUserAuthService.login(
                actorName, oldSession.getFirebaseUid(), oldSession.getEmail(),
                "e2e-emulator-password", oldSession.getRole());

        // Update session cookies on the existing actor (preserves stored resources like stepUpToken)
        oldSession.setSessionCookie(newSession.getSessionCookie());
        oldSession.setSessionSigCookie(newSession.getSessionSigCookie());
        log.info("[E2E] Actor '{}' re-authenticated with fresh session cookies", actorName);
    }

    // =========================================================================
    // EMAIL VERIFIED TOGGLE
    // =========================================================================

    @Given("{string} has emailVerified set to {word}")
    public void setEmailVerified(String actorName, String value) {
        Actor actor = actorRegistry.get(actorName);
        boolean verified = Boolean.parseBoolean(value);

        // Update PostgreSQL via test endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "firebaseUid", actor.getSession().getFirebaseUid(),
                "verified", verified
        );
        restTemplate.postForEntity(url("/test/registry/set-email-verified"),
                new HttpEntity<>(body, headers), Map.class);

        // Update Firebase Auth
        Map<String, Object> fbBody = Map.of(
                "firebaseUid", actor.getSession().getFirebaseUid(),
                "emailVerified", verified
        );
        restTemplate.postForEntity(url("/test/auth/update-firebase-user"),
                new HttpEntity<>(fbBody, headers), Map.class);

        log.info("[E2E] Actor '{}' emailVerified set to {}", actorName, verified);
    }

    // =========================================================================
    // MOCK SESSION LOGIN (for PENDING_ADMIN)
    // =========================================================================

    @Given("{string} logs in as PENDING_ADMIN via mock session")
    public void logsInAsPendingAdmin(String actorName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "email", "e2e-pending-admin@test.com",
                "role", "PENDING_ADMIN"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/mock-session"), new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Mock session for PENDING_ADMIN should succeed").isTrue();

        // Build UserSession from Set-Cookie headers
        com.sm.instagram.platform.e2e.multiuser.actor.UserSession session =
                new com.sm.instagram.platform.e2e.multiuser.actor.UserSession();
        session.setRole("PENDING_ADMIN");
        session.setEmail("e2e-pending-admin@test.com");

        var setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            for (String cookie : setCookies) {
                if (cookie.startsWith("session=")) {
                    session.setSessionCookie(cookie.split(";")[0].substring("session=".length()));
                } else if (cookie.startsWith("session_sig=")) {
                    session.setSessionSigCookie(cookie.split(";")[0].substring("session_sig=".length()));
                }
            }
        }

        // Extract userId and firebaseUid from response
        if (response.getBody() != null) {
            if (response.getBody().containsKey("userId")) {
                session.setUserId(((Number) response.getBody().get("userId")).longValue());
            }
            if (response.getBody().containsKey("firebaseUid")) {
                session.setFirebaseUid((String) response.getBody().get("firebaseUid"));
            }
        }

        actorRegistry.register(actorName, session);
        log.info("[E2E] Actor '{}' logged in as PENDING_ADMIN via mock session", actorName);
    }

    // =========================================================================
    // ADMIN RESTORE EMAIL
    // =========================================================================

    @When("{string} restores user {string} email to original value")
    public void restoresEmailToOriginal(String adminActorName, String targetActorName) {
        Actor admin = actorRegistry.get(adminActorName);
        Actor target = actorRegistry.get(targetActorName);
        Long userId = target.getSession().getUserId();

        // Get original email stored during "stores their original profile values"
        String originalEmail = (String) target.getSession().get("original_email");
        if (originalEmail == null) {
            log.warn("[E2E] No original email stored for '{}', skipping restore", targetActorName);
            return;
        }

        // Admin needs step-up too? No — admin can use their own step-up.
        // But actually, for cleanup we bypass step-up by using the test endpoint
        // to update Firebase user directly, then sync
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Update Firebase email
        Map<String, Object> fbBody = Map.of(
                "firebaseUid", target.getSession().getFirebaseUid(),
                "email", originalEmail
        );
        restTemplate.postForEntity(url("/test/auth/update-firebase-user"),
                new HttpEntity<>(fbBody, headers), Map.class);

        // Sync user from Firestore to PG
        Map<String, Object> syncBody = Map.of(
                "firebaseUid", target.getSession().getFirebaseUid(),
                "role", target.getSession().getRole()
        );
        restTemplate.postForEntity(url("/test/auth/sync-user-from-firestore"),
                new HttpEntity<>(syncBody, headers), Map.class);

        log.info("[E2E] Admin '{}' restored '{}' email to '{}'", adminActorName, targetActorName, originalEmail);
    }

    // =========================================================================
    // PATCH EMAIL WITHOUT TOKEN
    // =========================================================================

    @When("{string} updates their email to {string} without step-up token")
    public void updatesEmailWithoutToken(String actorName, String newEmail) {
        actorRegistry.switchTo(actorName);
        Actor actor = actorRegistry.get(actorName);
        Long userId = actor.getSession().getUserId();

        Map<String, Object> body = new HashMap<>();
        body.put("email", newEmail);

        // Build headers with session cookies but NO step-up token
        HttpHeaders headers = actor.getSession().buildAuthHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/" + userId), HttpMethod.PATCH, entity, Map.class);
        actor.getSession().setLastResponse(response);
        log.info("[E2E] Actor '{}' patched email to '{}' WITHOUT step-up token -> {}",
                actorName, newEmail, response.getStatusCode());
    }

    // =========================================================================
    // INITIAL ACCOUNT SETUP TOGGLE
    // =========================================================================

    @Given("{string} has initialAccountSetupCompleted set to {word}")
    public void setInitialAccountSetupCompleted(String actorName, String value) {
        Actor actor = actorRegistry.get(actorName);
        boolean completed = Boolean.parseBoolean(value);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "firebaseUid", actor.getSession().getFirebaseUid(),
                "completed", completed
        );
        restTemplate.postForEntity(url("/test/registry/set-initial-setup"),
                new HttpEntity<>(body, headers), Map.class);

        log.info("[E2E] Actor '{}' initialAccountSetupCompleted set to {}", actorName, completed);
    }

    // =========================================================================
    // ASSERTIONS
    // =========================================================================

    @Then("soft assert step-up status is {int}")
    public void softAssertStepUpStatus(int expectedStatus) {
        Actor actor = actorRegistry.current();
        int actual = actor.getLastStatusCode();
        softAssert.softAssertStatus(expectedStatus, actual, "step-up");
    }

    @And("soft assert step-up challengeType is {string}")
    public void softAssertChallengeType(String expectedType) {
        Actor actor = actorRegistry.current();
        ResponseEntity<?> response = actor.getLastResponse();
        if (response.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            String challengeType = (String) body.get("challengeType");
            softAssert.softAssertEquals(expectedType, challengeType, "challengeType");
        } else {
            softAssert.softAssertTrue(false, "Response body should contain challengeType");
        }
    }

    @And("soft assert step-up required is {word}")
    public void softAssertStepUpRequired(String expectedRequired) {
        Actor actor = actorRegistry.current();
        ResponseEntity<?> response = actor.getLastResponse();
        if (response.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            boolean required = Boolean.TRUE.equals(body.get("required"));
            boolean expected = Boolean.parseBoolean(expectedRequired);
            softAssert.softAssertEquals(String.valueOf(expected), String.valueOf(required), "step-up required");
        } else {
            softAssert.softAssertTrue(false, "Response body should contain required field");
        }
    }

    @And("soft assert step-up response contains token")
    public void softAssertResponseContainsToken() {
        Actor actor = actorRegistry.current();
        ResponseEntity<?> response = actor.getLastResponse();
        if (response.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            softAssert.softAssertNotNull(body.get("token"), "step-up token should be present");
            softAssert.softAssertTrue(Boolean.TRUE.equals(body.get("success")), "success should be true");
        } else {
            softAssert.softAssertTrue(false, "Response body should contain token");
        }
    }

    // =========================================================================
    // EMAIL BODY EXTRACTION (same pattern as MagicLinkSteps)
    // =========================================================================

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
        // First pass: look for text/html (preferred — code is in the HTML template)
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.isMimeType("text/html")) {
                return (String) part.getContent();
            }
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
}
