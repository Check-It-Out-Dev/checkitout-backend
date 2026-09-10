package com.sm.instagram.platform.e2e.hooks;

import com.icegreen.greenmail.util.GreenMail;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;

/**
 * Cucumber hooks for step-up authentication scenarios.
 *
 * <p>Three-layer defense-in-depth for test state management:
 * <ol>
 *   <li>{@code @After} resets users to raw defaults (INACTIVE, unverified, setup incomplete)</li>
 *   <li>{@code @Before} restores users to login-ready state (ACTIVE, verified, setup complete)</li>
 *   <li>Background login calls sync-user-from-firestore which forces ACTIVE/verified/setupComplete</li>
 * </ol>
 *
 * <p>Any single layer is sufficient. Together they guarantee clean state even if
 * a previous scenario failed mid-way, a previous suite's @After crashed, or
 * Firebase Auth was polluted by an external change.
 */
@Slf4j
public class StepUpHooks extends CucumberSpringConfig {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GreenMail greenMail;

    @Autowired
    private RestTemplate restTemplate;

    // Firebase UIDs are constant — the only stable identifier across environments
    private static final String COMPANY_FIREBASE_UID = "E2E_COMPANY_001";
    private static final String COMPANY_EMAIL = "e2e.company@test.com";

    private static final String INFLUENCER_FIREBASE_UID = "E2E_INFLUENCER_001";
    private static final String INFLUENCER_EMAIL = "e2e.influencer@test.com";

    /**
     * Restores users to login-ready state before each scenario.
     * Sets email=canonical, emailVerified=true, accountStatus=ACTIVE,
     * initialAccountSetupCompleted=true so Background login steps succeed.
     */
    @Before("@step-up-auth")
    public void beforeStepUp() {
        clearRedisAndGreenMail();
        resetUserToLoginReady(COMPANY_FIREBASE_UID, COMPANY_EMAIL, "COMPANY");
        resetUserToLoginReady(INFLUENCER_FIREBASE_UID, INFLUENCER_EMAIL, "INFLUENCER");
        log.info("[E2E] Step-up state reset to login-ready (before scenario)");
    }

    /**
     * Resets users to raw defaults after each scenario.
     * Sets email=canonical, emailVerified=false, accountStatus=INACTIVE,
     * initialAccountSetupCompleted=false. Cucumber guarantees this runs
     * even when a scenario fails mid-way.
     */
    @After("@step-up-auth")
    public void afterStepUp() {
        clearRedisAndGreenMail();
        resetUserToRawDefaults(COMPANY_FIREBASE_UID, COMPANY_EMAIL, "COMPANY");
        resetUserToRawDefaults(INFLUENCER_FIREBASE_UID, INFLUENCER_EMAIL, "INFLUENCER");
        log.info("[E2E] Step-up state reset to raw defaults (after scenario)");
    }

    private void clearRedisAndGreenMail() {
        Set<String> keys = redisTemplate.keys("step_up_*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[E2E] Cleared {} step-up Redis keys", keys.size());
        }
        greenMail.reset();
    }

    /**
     * Sets a user to login-ready state: canonical email, emailVerified=true,
     * accountStatus=ACTIVE, initialAccountSetupCompleted=true.
     *
     * <p>Steps:
     * <ol>
     *   <li>update-firebase-user: heals Firebase Auth email + sets emailVerified=true</li>
     *   <li>sync-user-from-firestore: heals PG email + forces ACTIVE/verified/setupComplete</li>
     * </ol>
     */
    private void resetUserToLoginReady(String firebaseUid, String email, String role) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Step 1: Heal Firebase Auth email + set emailVerified=true
            restTemplate.postForEntity(url("/test/auth/update-firebase-user"),
                    new HttpEntity<>(Map.of(
                            "firebaseUid", firebaseUid,
                            "email", email,
                            "emailVerified", true
                    ), headers), Map.class);

            // Step 2: Sync to PG (forces ACTIVE, emailVerified=true, initialAccountSetupCompleted=true)
            restTemplate.postForEntity(url("/test/auth/sync-user-from-firestore"),
                    new HttpEntity<>(Map.of(
                            "firebaseUid", firebaseUid,
                            "role", role,
                            "email", email
                    ), headers), Map.class);

            log.info("[E2E] {} user reset to login-ready (email: {})", role, email);
        } catch (Exception e) {
            log.warn("[E2E] Could not reset {} to login-ready (user may not exist yet): {}", role, e.getMessage());
        }
    }

    /**
     * Resets a user to raw defaults: canonical email, emailVerified=false,
     * accountStatus=INACTIVE, initialAccountSetupCompleted=false.
     *
     * <p>Steps:
     * <ol>
     *   <li>sync-user-from-firestore: heals Firebase Auth + PG email (forces ACTIVE/verified/setupComplete)</li>
     *   <li>update-firebase-user: overrides Firebase Auth emailVerified=false (sync set it to true)</li>
     *   <li>set-email-verified: overrides PG emailVerified=false + accountStatus=INACTIVE</li>
     *   <li>set-initial-setup: overrides PG initialAccountSetupCompleted=false</li>
     * </ol>
     *
     * <p>Order matters: sync must run first (heals email in both layers), then update-firebase-user
     * corrects emailVerified AFTER sync's unconditional emailVerified=true override.
     */
    private void resetUserToRawDefaults(String firebaseUid, String email, String role) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Step 1: Sync heals Firebase Auth + PG email, forces ACTIVE/verified/setupComplete
            restTemplate.postForEntity(url("/test/auth/sync-user-from-firestore"),
                    new HttpEntity<>(Map.of(
                            "firebaseUid", firebaseUid,
                            "role", role,
                            "email", email
                    ), headers), Map.class);

            // Step 2: Override Firebase Auth emailVerified=false (sync forced it to true)
            restTemplate.postForEntity(url("/test/auth/update-firebase-user"),
                    new HttpEntity<>(Map.of(
                            "firebaseUid", firebaseUid,
                            "emailVerified", false
                    ), headers), Map.class);

            // Step 3: Override PG emailVerified=false + accountStatus=INACTIVE
            restTemplate.postForEntity(url("/test/registry/set-email-verified"),
                    new HttpEntity<>(Map.of(
                            "firebaseUid", firebaseUid,
                            "verified", false,
                            "accountStatus", "INACTIVE"
                    ), headers), Map.class);

            // Step 4: Override PG initialAccountSetupCompleted=false
            restTemplate.postForEntity(url("/test/registry/set-initial-setup"),
                    new HttpEntity<>(Map.of(
                            "firebaseUid", firebaseUid,
                            "completed", false
                    ), headers), Map.class);

            log.info("[E2E] {} user reset to raw defaults (email: {}, INACTIVE, unverified)", role, email);
        } catch (Exception e) {
            log.warn("[E2E] Could not reset {} to raw defaults: {}", role, e.getMessage());
        }
    }
}
