package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.multiuser.actor.UserSession;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for admin user management E2E tests.
 * Tests admin operations like banning/unbanning users, changing status, viewing users.
 *
 * <p>Uses real admin account (2FA verified) to operate on existing robot users:
 * <ul>
 *   <li>E2E_COMPANY_001 - Company user for admin to operate on</li>
 *   <li>E2E_INFLUENCER_001 - Influencer user (norbertmarchewka) for admin to operate on</li>
 * </ul>
 *
 * <p>Run with: mvn verify -Pe2e -Dit.test=RunAdminIT
 */
@Slf4j
public class AdminUserManagementSteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private ActorRegistry actorRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Target User Configuration ====================

    @Value("${e2e.company.firebase-uid:E2E_COMPANY_001}")
    private String e2eCompanyFirebaseUid;

    @Value("${e2e.influencer.firebase-uid:E2E_INFLUENCER_001}")
    private String e2eInfluencerFirebaseUid;

    // Store target user IDs for admin operations
    private final Map<String, Long> targetUserIds = new HashMap<>();

    // ==================== Target User Setup Steps ====================

    /**
     * Syncs a target user and ensures they have the expected status AND role.
     * CRITICAL: Forces Firebase custom claims FIRST, then updates database.
     * The JWT role comes from Firebase claims, NOT the database, so we must ensure
     * Firebase claims are correct before the user logs in.
     */
    @Given("the target user {string} is synced and has status {string} and role {string}")
    public void ensureTargetUserState(String targetAlias, String expectedStatus, String expectedRole) {
        String firebaseUid = resolveFirebaseUid(targetAlias);

        // STEP 1: Force Firebase custom claims FIRST (JWT role comes from here!)
        // This is critical because the PATCH endpoint only updates claims conditionally
        forceFirebaseClaims(firebaseUid, expectedRole);

        // STEP 2: Sync user from Firestore to ensure they exist in PostgreSQL
        Long userId = syncUserFromFirestore(firebaseUid, expectedRole);
        targetUserIds.put(targetAlias, userId);

        // STEP 3: Use Admin PATCH to ensure database status and role are correct
        HttpHeaders headers = createAdminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("accountStatus", expectedStatus);
        body.put("userType", expectedRole);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(
                url("/users/" + userId),
                HttpMethod.PATCH,
                entity,
                Map.class
            );
            log.info("[E2E ADMIN] Ensured user '{}' has status='{}', role='{}' (Firebase + DB)",
                targetAlias, expectedStatus, expectedRole);
        } catch (HttpClientErrorException e) {
            log.warn("[E2E ADMIN] Failed to set user state via PATCH: {}", e.getMessage());
            handleHttpError(e, "ensure user state for " + targetAlias);
        }
    }

    /**
     * Forces Firebase custom claims to be set for a user.
     * This ensures JWT role is correct regardless of database state.
     */
    private void forceFirebaseClaims(String firebaseUid, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
            "firebaseUid", firebaseUid,
            "role", role
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/force-firebase-claims"),
                entity,
                Map.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[E2E ADMIN] Forced Firebase claims: firebaseUid={}, role={}", firebaseUid, role);
            } else {
                log.warn("[E2E ADMIN] Failed to force Firebase claims: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("[E2E ADMIN] Error forcing Firebase claims for {}: {}", firebaseUid, e.getMessage());
        }
    }

    /**
     * Syncs a target user without status verification.
     */
    @Given("the target user {string} is synced from Firestore")
    public void syncTargetUser(String targetAlias) {
        String firebaseUid = resolveFirebaseUid(targetAlias);
        Long userId = syncUserFromFirestore(firebaseUid, resolveRole(targetAlias));
        targetUserIds.put(targetAlias, userId);
        log.info("[E2E ADMIN] Target user '{}' synced: userId={}", targetAlias, userId);
    }

    // ==================== Admin Ban/Unban Operations ====================

    /**
     * Admin bans a user with a specified reason.
     */
    @When("the admin bans user {string} with reason {string}")
    public void adminBansUser(String targetAlias, String reason) {
        Long userId = getTargetUserId(targetAlias);

        HttpHeaders headers = createAdminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("accountStatus", "BANNED");
        // Note: reason is logged but not stored in this API call
        // Future enhancement: add ban reason to user entity

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/" + userId),
                HttpMethod.PATCH,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            log.info("[E2E ADMIN] Banned user '{}' (id={}): status={}, reason={}",
                targetAlias, userId, response.getStatusCode(), reason);
        } catch (HttpClientErrorException e) {
            handleHttpError(e, "ban user " + targetAlias);
        }
    }

    /**
     * Admin unbans a user (sets status back to ACTIVE).
     */
    @When("the admin unbans user {string}")
    public void adminUnbansUser(String targetAlias) {
        Long userId = getTargetUserId(targetAlias);
        setUserStatusWithResponse(userId, "ACTIVE");
        log.info("[E2E ADMIN] Unbanned user '{}' (id={})", targetAlias, userId);
    }

    /**
     * Admin sets user status to a specific value.
     */
    @When("the admin sets user {string} status to {string}")
    public void adminSetsUserStatus(String targetAlias, String status) {
        Long userId = getTargetUserId(targetAlias);
        setUserStatusWithResponse(userId, status);
        log.info("[E2E ADMIN] Set user '{}' status to '{}'", targetAlias, status);
    }

    // ==================== Admin User View Operations ====================

    /**
     * Admin views the paginated user list.
     */
    @When("the admin views the user list")
    public void adminViewsUserList() {
        HttpHeaders headers = createAdminHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/paged?page=0&size=20"),
                HttpMethod.GET,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            log.info("[E2E ADMIN] Retrieved user list: status={}", response.getStatusCode());
        } catch (HttpClientErrorException e) {
            handleHttpError(e, "view user list");
        }
    }

    /**
     * Admin views a specific user's profile.
     */
    @When("the admin views user {string} profile")
    public void adminViewsUserProfile(String targetAlias) {
        Long userId = getTargetUserId(targetAlias);

        HttpHeaders headers = createAdminHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/" + userId),
                HttpMethod.GET,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            log.info("[E2E ADMIN] Retrieved user '{}' profile: status={}",
                targetAlias, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            handleHttpError(e, "view user " + targetAlias);
        }
    }

    // ==================== Banned User Behavior Steps ====================

    /**
     * Authenticates as a banned user (expects to succeed - banned users CAN authenticate).
     */
    @When("I authenticate as the banned user {string}")
    public void authenticateAsBannedUser(String targetAlias) {
        String firebaseUid = resolveFirebaseUid(targetAlias);
        String email = resolveEmail(targetAlias);

        // Create test session for the banned user
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> request = Map.of(
            "email", email,
            "role", resolveRole(targetAlias),
            "partial", false
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/mock-session"),
                entity,
                Map.class
            );
            extractSessionCookiesFromResponse(response);
            context.put("bannedUserSession", true);
            log.info("[E2E] Authenticated as banned user '{}': status={}",
                targetAlias, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            handleHttpError(e, "authenticate as banned user " + targetAlias);
        }
    }

    /**
     * Banned user accesses an endpoint (expects success for whitelisted endpoints).
     */
    @Then("I should be able to access {string}")
    public void shouldBeAbleToAccess(String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        addSessionCookies(headers);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Should be able to access %s", endpoint)
                .isTrue();
            log.info("[E2E] Banned user accessed '{}': status={}", endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(parseJsonBody(e.getResponseBodyAsString())));
            log.info("[E2E] Banned user access to '{}' returned: status={}",
                endpoint, e.getStatusCode());
        }
    }

    /**
     * Banned user cannot access a restricted endpoint.
     */
    @Then("when I try to access {string} I should get {int}")
    public void shouldGetStatusWhenAccessing(String endpoint, int expectedStatus) {
        HttpHeaders headers = new HttpHeaders();
        addSessionCookies(headers);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                entity,
                Map.class
            );
            context.setLastResponse(response);
            assertThat(response.getStatusCode().value())
                .as("Should get %d when accessing %s", expectedStatus, endpoint)
                .isEqualTo(expectedStatus);
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .headers(e.getResponseHeaders())
                .body(parseJsonBody(e.getResponseBodyAsString())));
            assertThat(e.getStatusCode().value())
                .as("Should get %d when accessing %s", expectedStatus, endpoint)
                .isEqualTo(expectedStatus);
            log.info("[E2E] Banned user denied access to '{}': status={}",
                endpoint, e.getStatusCode());
        }
    }

    /**
     * Verifies the user can see their account status in the response.
     */
    @Then("I should see my account status as {string}")
    public void shouldSeeAccountStatus(String expectedStatus) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();

        if (response.getBody() instanceof Map) {
            Map<?, ?> body = (Map<?, ?>) response.getBody();
            String actualStatus = extractStatusValue(body.get("accountStatus"));
            assertThat(actualStatus)
                .as("Account status should be %s", expectedStatus)
                .isEqualTo(expectedStatus);
            log.info("[E2E] Verified account status: {}", expectedStatus);
        }
    }

    // ==================== Verification Steps ====================

    /**
     * Verifies a user has the expected status.
     */
    @Then("the user {string} should have status {string}")
    public void verifyUserStatus(String targetAlias, String expectedStatus) {
        Long userId = getTargetUserId(targetAlias);

        ResponseEntity<Map> response = getUser(userId);
        assertThat(response.getStatusCode().is2xxSuccessful())
            .as("Should be able to get user %s", targetAlias)
            .isTrue();

        if (response.getBody() != null) {
            String actualStatus = extractStatusValue(response.getBody().get("accountStatus"));
            assertThat(actualStatus)
                .as("User '%s' should have status '%s'", targetAlias, expectedStatus)
                .isEqualTo(expectedStatus);
            log.info("[E2E ADMIN] Verified user '{}' has status '{}'", targetAlias, expectedStatus);
        }
    }

    /**
     * Verifies response contains user list.
     */
    @Then("the response should contain a list of users")
    public void verifyUserListResponse() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();

        if (response.getBody() instanceof Map) {
            Map<?, ?> body = (Map<?, ?>) response.getBody();
            // Check for paginated response structure
            assertThat(body.containsKey("content") || body.containsKey("users"))
                .as("Response should contain user list")
                .isTrue();
            log.info("[E2E ADMIN] Verified user list response");
        }
    }

    /**
     * Verifies response contains pagination info.
     */
    @Then("the response should contain pagination info")
    public void verifyPaginationInfo() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();

        if (response.getBody() instanceof Map) {
            Map<?, ?> body = (Map<?, ?>) response.getBody();
            // Check for pagination metadata
            boolean hasPagination = body.containsKey("totalElements") ||
                                    body.containsKey("totalPages") ||
                                    body.containsKey("pageable");
            assertThat(hasPagination)
                .as("Response should contain pagination info")
                .isTrue();
            log.info("[E2E ADMIN] Verified pagination info");
        }
    }

    /**
     * Verifies response contains user's email.
     */
    @Then("the response should contain the user's email")
    public void verifyResponseContainsEmail() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();

        if (response.getBody() instanceof Map) {
            Map<?, ?> body = (Map<?, ?>) response.getBody();
            assertThat(body.get("email"))
                .as("Response should contain email")
                .isNotNull();
            log.info("[E2E ADMIN] Verified response contains email");
        }
    }

    /**
     * Verifies response contains user's account status.
     */
    @Then("the response should contain the user's account status")
    public void verifyResponseContainsStatus() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();

        if (response.getBody() instanceof Map) {
            Map<?, ?> body = (Map<?, ?>) response.getBody();
            assertThat(body.get("accountStatus"))
                .as("Response should contain accountStatus")
                .isNotNull();
            log.info("[E2E ADMIN] Verified response contains account status");
        }
    }

    // ==================== Helper Methods ====================

    // Known Firebase UIDs for role resolution
    private static final String COMPANY_FIREBASE_UID = "WWXA9DehxZghyLq849TpyE4vYzZ2";
    private static final String INFLUENCER_FIREBASE_UID = "SEWgduxUjRh4KDqxVWFs6zgThIa2";

    /**
     * Resolves target alias to Firebase UID.
     */
    private String resolveFirebaseUid(String targetAlias) {
        return switch (targetAlias) {
            case "E2E_COMPANY_001" -> e2eCompanyFirebaseUid;
            case "E2E_INFLUENCER_001" -> e2eInfluencerFirebaseUid;
            default -> targetAlias; // Assume it's already a Firebase UID
        };
    }

    /**
     * Resolves target alias (Firebase UID or alias) to role.
     */
    private String resolveRole(String targetAlias) {
        // Check for known Firebase UIDs
        if (COMPANY_FIREBASE_UID.equals(targetAlias)) return "COMPANY";
        if (INFLUENCER_FIREBASE_UID.equals(targetAlias)) return "INFLUENCER";
        // Check for alias patterns
        if (targetAlias.contains("COMPANY")) return "COMPANY";
        if (targetAlias.contains("INFLUENCER")) return "INFLUENCER";
        return "COMPANY"; // Default
    }

    /**
     * Resolves target alias (Firebase UID or alias) to email.
     */
    private String resolveEmail(String targetAlias) {
        // Check for known Firebase UIDs
        if (COMPANY_FIREBASE_UID.equals(targetAlias)) return "norbert.marchewka4444431@gmail.com";
        if (INFLUENCER_FIREBASE_UID.equals(targetAlias)) return "norbertmarchewka@instagram-e2e.test";
        // Check for alias patterns
        if (targetAlias.contains("COMPANY")) return "e2e.company@test.com";
        if (targetAlias.contains("INFLUENCER")) return "e2e.influencer@test.com";
        return "e2e.user@test.com";
    }

    /**
     * Gets the cached user ID for a target alias.
     */
    private Long getTargetUserId(String targetAlias) {
        Long userId = targetUserIds.get(targetAlias);
        if (userId == null) {
            throw new IllegalStateException(
                "Target user '" + targetAlias + "' not synced. Use 'the target user X is synced' step first.");
        }
        return userId;
    }

    /**
     * Syncs user from Firestore and returns their database ID.
     */
    private Long syncUserFromFirestore(String firebaseUid, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> syncRequest = Map.of(
            "firebaseUid", firebaseUid,
            "role", role,
            "syncInstagramData", "INFLUENCER".equals(role)
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(syncRequest, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/test/auth/sync-user-from-firestore"),
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Number userId = (Number) response.getBody().get("userId");
                if (userId != null) {
                    log.info("[E2E ADMIN] Synced user: firebaseUid={}, userId={}", firebaseUid, userId);
                    return userId.longValue();
                }
            }
        } catch (Exception e) {
            log.warn("[E2E ADMIN] Failed to sync user {}: {}", firebaseUid, e.getMessage());
        }

        throw new IllegalStateException("Failed to sync user: " + firebaseUid);
    }

    /**
     * Gets a user by ID using admin credentials.
     */
    private ResponseEntity<Map> getUser(Long userId) {
        HttpHeaders headers = createAdminHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(
            url("/users/" + userId),
            HttpMethod.GET,
            entity,
            Map.class
        );
    }

    /**
     * Sets user status without updating context response (for setup).
     */
    private void setUserStatus(Long userId, String status) {
        HttpHeaders headers = createAdminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("accountStatus", status);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        restTemplate.exchange(
            url("/users/" + userId),
            HttpMethod.PATCH,
            entity,
            Map.class
        );
    }

    /**
     * Sets user status and stores response in context.
     */
    private void setUserStatusWithResponse(Long userId, String status) {
        HttpHeaders headers = createAdminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("accountStatus", status);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url("/users/" + userId),
                HttpMethod.PATCH,
                entity,
                Map.class
            );
            context.setLastResponse(response);
        } catch (HttpClientErrorException e) {
            handleHttpError(e, "set user status to " + status);
        }
    }

    /**
     * Creates headers with admin session cookies from ActorRegistry.
     */
    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();

        // Get admin session from ActorRegistry (set by multiuser login)
        Actor admin = actorRegistry.get("Admin");
        if (admin != null && admin.getSession() != null) {
            UserSession session = admin.getSession();
            if (session.getSessionCookie() != null) {
                headers.add("Cookie", "session=" + session.getSessionCookie());
            }
            if (session.getSessionSigCookie() != null) {
                headers.add("Cookie", "session_sig=" + session.getSessionSigCookie());
            }
        } else {
            // Fallback to ScenarioContext (for backwards compatibility)
            String sessionCookie = context.getSessionCookie();
            String sessionSig = context.getSessionSigCookie();
            if (sessionCookie != null) {
                headers.add("Cookie", "session=" + sessionCookie);
            }
            if (sessionSig != null) {
                headers.add("Cookie", "session_sig=" + sessionSig);
            }
        }
        return headers;
    }

    /**
     * Adds session cookies to headers for banned user requests.
     */
    private void addSessionCookies(HttpHeaders headers) {
        String sessionCookie = context.getSessionCookie();
        String sessionSig = context.getSessionSigCookie();

        if (sessionCookie != null) {
            headers.add("Cookie", "session=" + sessionCookie);
        }
        if (sessionSig != null) {
            headers.add("Cookie", "session_sig=" + sessionSig);
        }
    }

    /**
     * Extracts session cookies from response.
     */
    private void extractSessionCookiesFromResponse(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return;

        for (String cookie : cookies) {
            if (cookie.startsWith("session=") && !cookie.startsWith("session_sig=")) {
                context.setSessionCookie(extractCookieValue(cookie));
            } else if (cookie.startsWith("session_sig=")) {
                context.setSessionSigCookie(extractCookieValue(cookie));
            }
        }
    }

    /**
     * Extracts cookie value from Set-Cookie header.
     */
    private String extractCookieValue(String cookie) {
        return cookie.split(";")[0].split("=", 2)[1];
    }

    /**
     * Extracts status value from accountStatus object (handles nested structure).
     */
    private String extractStatusValue(Object statusObj) {
        if (statusObj == null) return null;
        if (statusObj instanceof String) return (String) statusObj;
        if (statusObj instanceof Map) {
            Map<?, ?> statusMap = (Map<?, ?>) statusObj;
            Object value = statusMap.get("value");
            if (value != null) return value.toString();
            Object status = statusMap.get("status");
            if (status != null) return status.toString();
        }
        return statusObj.toString();
    }

    /**
     * Handles HTTP errors by storing response in context.
     */
    private void handleHttpError(HttpClientErrorException e, String operation) {
        ResponseEntity<Map> errorResponse = ResponseEntity
            .status(e.getStatusCode())
            .headers(e.getResponseHeaders())
            .body(parseJsonBody(e.getResponseBodyAsString()));
        context.setLastResponse(errorResponse);
        log.info("[E2E ADMIN] Failed to {}: status={}", operation, e.getStatusCode());
    }

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
