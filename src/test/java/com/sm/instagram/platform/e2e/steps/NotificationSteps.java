package com.sm.instagram.platform.e2e.steps;

import com.icegreen.greenmail.util.GreenMail;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import com.sm.instagram.platform.notification.email.EmailCronJob;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for notification E2E tests.
 *
 * <p>Tests notification CRUD via HTTP endpoints and email delivery via GreenMail.
 *
 * <p>Endpoints tested:
 * <ul>
 *   <li>GET /notifications?page={page}&amp;size={size}</li>
 *   <li>GET /notifications/unread/count</li>
 *   <li>GET /notifications/{id}</li>
 *   <li>PATCH /notifications/{id}/read</li>
 *   <li>POST /notifications/read-all</li>
 *   <li>DELETE /notifications/{id} (archive)</li>
 * </ul>
 */
@Slf4j
public class NotificationSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private ScenarioContext context;

    @Autowired
    private GreenMail greenMail;

    @Autowired
    private EmailCronJob emailCronJob;

    // Stored values for cross-step assertions
    private final Map<String, Long> storedCounts = new HashMap<>();
    private final Map<String, Long> storedNotificationIds = new HashMap<>();

    // Last notification list response
    private ResponseEntity<Map> lastNotificationResponse;

    // ========================================================================
    // GREENMAIL STEPS
    // ========================================================================

    @And("the GreenMail SMTP server is running")
    public void greenMailIsRunning() {
        assertThat(greenMail).as("GreenMail should be injected").isNotNull();
        log.info("[E2E] GreenMail SMTP is running, received messages so far: {}",
                greenMail.getReceivedMessages().length);
    }

    @When("the GreenMail inbox is cleared")
    public void clearGreenMail() {
        greenMail.reset();
        log.info("[E2E] GreenMail inbox cleared");
    }

    @Then("GreenMail should have received at least {int} email(s)")
    public void greenMailShouldHaveReceivedAtLeast(int minCount) {
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages.length)
                .as("GreenMail should have received at least %d email(s), got %d", minCount, messages.length)
                .isGreaterThanOrEqualTo(minCount);
        log.info("[E2E] GreenMail has {} received email(s)", messages.length);
    }

    @Then("GreenMail should have received {int} email(s)")
    public void greenMailShouldHaveReceivedExactly(int count) {
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages.length)
                .as("GreenMail should have received exactly %d email(s), got %d", count, messages.length)
                .isEqualTo(count);
        log.info("[E2E] GreenMail has {} received email(s)", messages.length);
    }

    @Then("the last GreenMail email should contain subject {string}")
    public void lastGreenMailEmailShouldContainSubject(String expectedSubjectPart) {
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages.length).as("GreenMail should have at least 1 email").isGreaterThan(0);

        try {
            MimeMessage lastMessage = messages[messages.length - 1];
            String subject = lastMessage.getSubject();
            assertThat(subject)
                    .as("Email subject should contain '%s'", expectedSubjectPart)
                    .containsIgnoringCase(expectedSubjectPart);
            log.info("[E2E] Last email subject: '{}'", subject);
        } catch (Exception e) {
            throw new AssertionError("Failed to read email subject: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // EMAIL QUEUE PROCESSING
    // ========================================================================

    @When("the email queue is processed")
    public void processEmailQueue() {
        log.info("[E2E] Manually triggering email queue processing");
        emailCronJob.processEmailQueue();
        log.info("[E2E] Email queue processing complete, GreenMail messages: {}",
                greenMail.getReceivedMessages().length);
    }

    // ========================================================================
    // NOTIFICATION PREFERENCE MANAGEMENT (via Admin)
    // ========================================================================

    @And("{string} enables all notification preferences for user {string}")
    public void enableAllNotificationPreferences(String adminActor, String targetFirebaseUid) {
        Actor admin = actorRegistry.get(adminActor);

        Long userId = resolveUserId(targetFirebaseUid);
        assertThat(userId)
                .as("Must resolve DB userId for Firebase UID %s — ensure 'the target user X is synced' runs first", targetFirebaseUid)
                .isNotNull();

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("notificationPartnershipEnabled", true);
        prefs.put("notificationSupportEnabled", true);
        prefs.put("notificationSystemEnabled", true);
        prefs.put("notificationEmailEnabled", true);
        prefs.put("notificationEmailPartnershipEnabled", true);
        prefs.put("notificationEmailSupportEnabled", true);

        ResponseEntity<Map> response = admin.patch(restTemplate,
                url("/user-preferences/user/" + userId), prefs);

        context.setLastResponse(response);
        log.info("[E2E] Enabled all notification prefs for user {} (Firebase UID {}): status={}",
                userId, targetFirebaseUid, response.getStatusCode());
    }

    @And("{string} disables partnership notification preferences for user {string}")
    public void disablePartnershipNotificationPreferences(String adminActor, String targetFirebaseUid) {
        Actor admin = actorRegistry.get(adminActor);

        Long userId = resolveUserId(targetFirebaseUid);
        assertThat(userId)
                .as("Must resolve DB userId for Firebase UID %s — ensure 'the target user X is synced' runs first", targetFirebaseUid)
                .isNotNull();

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("notificationPartnershipEnabled", false);
        prefs.put("notificationEmailPartnershipEnabled", false);
        prefs.put("notificationEmailEnabled", false);

        ResponseEntity<Map> response = admin.patch(restTemplate,
                url("/user-preferences/user/" + userId), prefs);

        context.setLastResponse(response);
        log.info("[E2E] Disabled partnership notification prefs for user {} (Firebase UID {}): status={}",
                userId, targetFirebaseUid, response.getStatusCode());
    }

    // ========================================================================
    // NOTIFICATION RETRIEVAL
    // ========================================================================

    @When("{string} checks unread notification count")
    public void checkUnreadCount(String actorName) {
        Actor actor = actorRegistry.get(actorName);

        lastNotificationResponse = actor.get(restTemplate, url("/notifications/unread/count"));
        context.setLastResponse(lastNotificationResponse);

        log.info("[E2E] Actor '{}' unread count response: status={}, body={}",
                actorName, lastNotificationResponse.getStatusCode(), lastNotificationResponse.getBody());
    }

    @Then("the unread count is stored as {string}")
    public void storeUnreadCount(String ref) {
        assertThat(lastNotificationResponse).isNotNull();
        assertThat(lastNotificationResponse.getBody()).isNotNull();

        Number count = (Number) lastNotificationResponse.getBody().get("count");
        storedCounts.put(ref, count != null ? count.longValue() : 0L);

        log.info("[E2E] Stored unread count '{}' = {}", ref, storedCounts.get(ref));
    }

    @Then("the unread count should be greater than {string}")
    public void unreadCountShouldBeGreaterThan(String ref) {
        assertThat(lastNotificationResponse).isNotNull();
        assertThat(lastNotificationResponse.getBody()).isNotNull();

        Number currentCount = (Number) lastNotificationResponse.getBody().get("count");
        long current = currentCount != null ? currentCount.longValue() : 0L;
        long stored = storedCounts.getOrDefault(ref, 0L);

        assertThat(current)
                .as("Unread count (%d) should be greater than stored '%s' (%d)", current, ref, stored)
                .isGreaterThan(stored);

        log.info("[E2E] Unread count {} > stored '{}' ({})", current, ref, stored);
    }

    @Then("the unread count should equal {string}")
    public void unreadCountShouldEqual(String ref) {
        assertThat(lastNotificationResponse).isNotNull();
        assertThat(lastNotificationResponse.getBody()).isNotNull();

        Number currentCount = (Number) lastNotificationResponse.getBody().get("count");
        long current = currentCount != null ? currentCount.longValue() : 0L;
        long stored = storedCounts.getOrDefault(ref, 0L);

        assertThat(current)
                .as("Unread count (%d) should equal stored '%s' (%d)", current, ref, stored)
                .isEqualTo(stored);

        log.info("[E2E] Unread count {} == stored '{}' ({})", current, ref, stored);
    }

    @Then("the unread count should be {int}")
    public void unreadCountShouldBe(int expected) {
        assertThat(lastNotificationResponse).isNotNull();
        assertThat(lastNotificationResponse.getBody()).isNotNull();

        Number count = (Number) lastNotificationResponse.getBody().get("count");
        long actual = count != null ? count.longValue() : 0L;

        assertThat(actual)
                .as("Unread count should be %d", expected)
                .isEqualTo(expected);

        log.info("[E2E] Unread count = {}", actual);
    }

    @When("{string} fetches notifications page {int} size {int}")
    public void fetchNotifications(String actorName, int page, int size) {
        Actor actor = actorRegistry.get(actorName);

        lastNotificationResponse = actor.get(restTemplate,
                url("/notifications?page=" + page + "&size=" + size));
        context.setLastResponse(lastNotificationResponse);

        log.info("[E2E] Actor '{}' notifications response: status={}", actorName,
                lastNotificationResponse.getStatusCode());
    }

    @Then("the notifications response should contain at least {int} notification(s)")
    public void notificationsResponseShouldContainAtLeast(int minCount) {
        assertThat(lastNotificationResponse).isNotNull();
        assertThat(lastNotificationResponse.getBody()).isNotNull();

        Map<String, Object> body = lastNotificationResponse.getBody();
        Object contentObj = body.get("content");
        assertThat(contentObj).as("Response should have 'content'").isNotNull();

        List<?> content = (List<?>) contentObj;
        assertThat(content.size())
                .as("Should have at least %d notification(s), got %d", minCount, content.size())
                .isGreaterThanOrEqualTo(minCount);

        log.info("[E2E] Notifications response has {} items", content.size());
    }

    @Then("the first notification should have type {string}")
    public void firstNotificationShouldHaveType(String expectedType) {
        List<?> content = extractNotificationContent();

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) content.get(0);
        assertThat(first.get("type"))
                .as("First notification should have type '%s'", expectedType)
                .isEqualTo(expectedType);

        log.info("[E2E] First notification type: {}", first.get("type"));
    }

    @And("{string} stores the first notification id as {string}")
    public void storeFirstNotificationId(String actorName, String ref) {
        List<?> content = extractNotificationContent();

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) content.get(0);
        Long id = ((Number) first.get("id")).longValue();
        storedNotificationIds.put(ref, id);

        log.info("[E2E] Stored notification ID '{}' = {}", ref, id);
    }

    @Then("the notifications response should not contain notification {string}")
    public void notificationsResponseShouldNotContain(String ref) {
        Long excludedId = storedNotificationIds.get(ref);
        assertThat(excludedId).as("Notification ref '%s' should be stored", ref).isNotNull();

        assertThat(lastNotificationResponse).isNotNull();
        assertThat(lastNotificationResponse.getBody()).isNotNull();

        Map<String, Object> body = lastNotificationResponse.getBody();
        Object contentObj = body.get("content");
        if (contentObj == null) {
            // Empty response means the notification is definitely not there
            return;
        }

        List<?> content = (List<?>) contentObj;
        for (Object item : content) {
            @SuppressWarnings("unchecked")
            Map<String, Object> notif = (Map<String, Object>) item;
            Long notifId = ((Number) notif.get("id")).longValue();
            assertThat(notifId)
                    .as("Notification list should not contain archived notification %d", excludedId)
                    .isNotEqualTo(excludedId);
        }

        log.info("[E2E] Confirmed notification {} is NOT in the list ({} items)", excludedId, content.size());
    }

    // ========================================================================
    // NOTIFICATION ACTIONS
    // ========================================================================

    @When("{string} marks notification {string} as read")
    public void markNotificationAsRead(String actorName, String ref) {
        Actor actor = actorRegistry.get(actorName);
        Long notificationId = storedNotificationIds.get(ref);
        assertThat(notificationId).as("Notification ref '%s' should be stored", ref).isNotNull();

        lastNotificationResponse = actor.patch(restTemplate,
                url("/notifications/" + notificationId + "/read"));
        context.setLastResponse(lastNotificationResponse);

        log.info("[E2E] Actor '{}' marked notification {} as read: status={}",
                actorName, notificationId, lastNotificationResponse.getStatusCode());
    }

    @Then("the notification response should have isRead true")
    public void notificationShouldBeRead() {
        assertThat(lastNotificationResponse).isNotNull();
        assertThat(lastNotificationResponse.getBody()).isNotNull();

        Object isRead = lastNotificationResponse.getBody().get("isRead");
        assertThat(isRead)
                .as("Notification isRead should be true")
                .isEqualTo(true);

        log.info("[E2E] Notification isRead = {}", isRead);
    }

    @When("{string} marks all notifications as read")
    public void markAllAsRead(String actorName) {
        Actor actor = actorRegistry.get(actorName);

        lastNotificationResponse = actor.post(restTemplate,
                url("/notifications/read-all"), Map.of());
        context.setLastResponse(lastNotificationResponse);

        log.info("[E2E] Actor '{}' mark-all-as-read response: status={}, body={}",
                actorName, lastNotificationResponse.getStatusCode(), lastNotificationResponse.getBody());
    }

    @When("{string} archives notification {string}")
    public void archiveNotification(String actorName, String ref) {
        Actor actor = actorRegistry.get(actorName);
        Long notificationId = storedNotificationIds.get(ref);
        assertThat(notificationId).as("Notification ref '%s' should be stored", ref).isNotNull();

        ResponseEntity<Map> response = actor.delete(restTemplate,
                url("/notifications/" + notificationId));
        context.setLastResponse(response);

        log.info("[E2E] Actor '{}' archived notification {}: status={}",
                actorName, notificationId, response.getStatusCode());
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private List<?> extractNotificationContent() {
        assertThat(lastNotificationResponse).isNotNull();
        assertThat(lastNotificationResponse.getBody()).isNotNull();

        Map<String, Object> body = lastNotificationResponse.getBody();
        Object contentObj = body.get("content");
        assertThat(contentObj).as("Response should have 'content'").isNotNull();

        List<?> content = (List<?>) contentObj;
        assertThat(content).as("Content should not be empty").isNotEmpty();
        return content;
    }

    /**
     * Resolves a Firebase UID to a database user ID by syncing the user from Firestore.
     * Uses POST /test/auth/sync-user-from-firestore which is the canonical E2E pattern.
     * This endpoint requires no authentication and returns the userId in the response.
     */
    private Long resolveUserId(String firebaseUid) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            Map<String, Object> syncRequest = Map.of(
                    "firebaseUid", firebaseUid,
                    "role", "COMPANY",
                    "syncInstagramData", false
            );

            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(syncRequest, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url("/test/auth/sync-user-from-firestore"),
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Number userId = (Number) response.getBody().get("userId");
                if (userId != null) {
                    log.info("[E2E] Resolved userId={} for Firebase UID {}", userId.longValue(), firebaseUid);
                    return userId.longValue();
                }
            }
        } catch (Exception e) {
            log.warn("[E2E] Failed to resolve userId for Firebase UID {}: {}", firebaseUid, e.getMessage());
        }

        return null;
    }

    // ========================================================================
    // SCENARIO-CONTEXT NOTIFICATION STEPS (for registry/consent flows that don't use ActorRegistry)
    // ========================================================================

    private ResponseEntity<Map> lastUserNotificationResponse;

    @When("the user checks unread notification count")
    public void userChecksUnreadNotificationCount() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        addScenarioSessionCookies(headers);

        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/notifications/unread/count"),
                org.springframework.http.HttpMethod.GET,
                entity,
                Map.class
        );
        context.setLastResponse(response);
        log.info("[E2E] User checked unread count: status={}, body={}", response.getStatusCode(), response.getBody());
    }

    @Then("the user unread count should be at least {int}")
    public void userUnreadCountShouldBeAtLeast(int minCount) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Number count = (Number) body.get("count");
        assertThat(count.longValue())
                .as("Unread notification count should be at least %d", minCount)
                .isGreaterThanOrEqualTo(minCount);
    }

    @When("the user fetches notifications page {int} size {int}")
    public void userFetchesNotifications(int page, int size) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        addScenarioSessionCookies(headers);

        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
        lastUserNotificationResponse = restTemplate.exchange(
                url("/notifications?page=" + page + "&size=" + size),
                org.springframework.http.HttpMethod.GET,
                entity,
                Map.class
        );
        context.setLastResponse(lastUserNotificationResponse);
        log.info("[E2E] User fetched notifications: status={}", lastUserNotificationResponse.getStatusCode());
    }

    @Given("the user account status is set to {string}")
    public void userAccountStatusSetTo(String status) {
        String email = context.getCurrentEmail();
        assertThat(email).as("ScenarioContext must have a current email").isNotNull();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", email, "status", status);
        org.springframework.http.HttpEntity<Map<String, String>> entity = new org.springframework.http.HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/set-account-status"), entity, Map.class);
        context.setLastResponse(response);
        log.info("[E2E] Set account status to {} for user {}: {}", status, email, response.getStatusCode());
    }

    private void addScenarioSessionCookies(org.springframework.http.HttpHeaders headers) {
        if (context.getSessionCookie() != null) {
            headers.add("Cookie", "session=" + context.getSessionCookie());
        }
        if (context.getSessionSigCookie() != null) {
            headers.add("Cookie", "session_sig=" + context.getSessionSigCookie());
        }
    }

    @When("the user refreshes their session after activation")
    public void userRefreshesSessionAfterActivation() {
        String email = context.getCurrentEmail();
        String role = context.getCurrentRole();
        assertThat(email).as("currentEmail must be set in ScenarioContext").isNotNull();
        assertThat(role).as("currentRole must be set in ScenarioContext").isNotNull();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("email", email, "role", role, "partial", false);
        org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/mock-session"), entity, Map.class);

        List<String> cookies = response.getHeaders().get(org.springframework.http.HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session=") && !cookie.startsWith("session_sig=")) {
                    context.setSessionCookie(cookie.split(";")[0].substring("session=".length()));
                } else if (cookie.startsWith("session_sig=")) {
                    context.setSessionSigCookie(cookie.split(";")[0].substring("session_sig=".length()));
                }
            }
        }
        log.info("[E2E] Session refreshed after activation for user: {}", email);
    }

    @Then("the user notifications should contain type {string}")
    @SuppressWarnings("unchecked")
    public void userNotificationsShouldContainType(String expectedType) {
        assertThat(lastUserNotificationResponse).as("Must fetch notifications first").isNotNull();
        assertThat(lastUserNotificationResponse.getBody()).isNotNull();

        Map<String, Object> body = lastUserNotificationResponse.getBody();
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).as("Notifications content should not be empty").isNotEmpty();

        boolean found = content.stream()
                .anyMatch(n -> expectedType.equals(n.get("type")));
        assertThat(found)
                .as("Should find notification with type '%s' in %d notifications", expectedType, content.size())
                .isTrue();
    }
}
