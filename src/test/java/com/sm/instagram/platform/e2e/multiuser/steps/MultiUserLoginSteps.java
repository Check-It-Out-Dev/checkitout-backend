package com.sm.instagram.platform.e2e.multiuser.steps;

import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.multiuser.actor.UserSession;
import com.sm.instagram.platform.e2e.multiuser.auth.MultiUserAuthService;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for multi-user login scenarios.
 * All credentials are passed as parameters - nothing hardcoded.
 *
 * <p>These steps enable multiple users to be logged in simultaneously
 * with isolated sessions, supporting complex multi-user business flows.
 *
 * <p>Example usage in feature files:
 * <pre>
 * Given "FashionCo" logs in as COMPANY with Firebase UID "UID_001" email "fashion@test.com" password "Pass123!"
 * Given "StyleGuru" logs in as INFLUENCER with Firebase UID "UID_002" email "style@test.com" password "Pass456!"
 * When "FashionCo" creates a campaign
 * Then "StyleGuru" can see the campaign
 * </pre>
 */
@Slf4j
public class MultiUserLoginSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private MultiUserAuthService authService;

    /**
     * Logs in a COMPANY user with explicit credentials.
     * Creates a new actor with isolated session.
     *
     * @param alias actor name for later reference
     * @param firebaseUid Firebase UID for user sync
     * @param email login email
     * @param password login password
     */
    @Given("{string} logs in as COMPANY with Firebase UID {string} email {string} password {string}")
    public void companyLogsIn(String alias, String firebaseUid, String email, String password) {
        authService.setBaseUrl(baseUrl());

        UserSession session = authService.login(alias, firebaseUid, email, password, "COMPANY");

        assertThat(session.isAuthenticated())
            .as("Company '%s' should have valid session", alias)
            .isTrue();

        actorRegistry.register(alias, session);
        log.info("[E2E] Company actor '{}' ready with authenticated session", alias);
    }

    /**
     * Logs in an INFLUENCER user with explicit credentials (email/password).
     * NOTE: This is kept for backwards compatibility but the OAuth method is preferred.
     *
     * @param alias actor name for later reference
     * @param firebaseUid Firebase UID for user sync
     * @param email login email
     * @param password login password
     */
    @Given("{string} logs in as INFLUENCER with Firebase UID {string} email {string} password {string}")
    public void influencerLogsIn(String alias, String firebaseUid, String email, String password) {
        authService.setBaseUrl(baseUrl());

        UserSession session = authService.login(alias, firebaseUid, email, password, "INFLUENCER");

        assertThat(session.isAuthenticated())
            .as("Influencer '%s' should have valid session", alias)
            .isTrue();

        actorRegistry.register(alias, session);
        log.info("[E2E] Influencer actor '{}' ready with authenticated session", alias);
    }

    /**
     * Logs in an INFLUENCER user via OAuth simulation (correct flow for influencers).
     * Uses Instagram token from Firestore (auto-decrypted via KMS).
     *
     * @param alias actor name for later reference
     * @param firebaseUid Firebase UID (used to find Instagram data in Firestore)
     */
    @Given("{string} logs in as INFLUENCER via OAuth with Firebase UID {string}")
    public void influencerLogsInViaOAuth(String alias, String firebaseUid) {
        authService.setBaseUrl(baseUrl());

        UserSession session = authService.loginInfluencerWithOAuth(alias, firebaseUid);

        assertThat(session.isAuthenticated())
            .as("Influencer '%s' should have valid OAuth session", alias)
            .isTrue();
        assertThat(session.isOauth())
            .as("Influencer '%s' should have OAuth flag set", alias)
            .isTrue();

        actorRegistry.register(alias, session);
        log.info("[E2E] Influencer actor '{}' ready with OAuth session", alias);
    }

    /**
     * Logs in an ADMIN user with explicit credentials.
     * Note: Admin may get partial session if 2FA is required.
     *
     * @param alias actor name for later reference
     * @param firebaseUid Firebase UID for user sync
     * @param email login email
     * @param password login password
     */
    @Given("{string} logs in as ADMIN with Firebase UID {string} email {string} password {string}")
    public void adminLogsIn(String alias, String firebaseUid, String email, String password) {
        authService.setBaseUrl(baseUrl());

        UserSession session = authService.login(alias, firebaseUid, email, password, "ADMIN");

        // Admin may have partial session (2FA pending) or full session
        assertThat(session.hasPartialSession() || session.isAuthenticated())
            .as("Admin '%s' should have partial or full session", alias)
            .isTrue();

        actorRegistry.register(alias, session);
        log.info("[E2E] Admin actor '{}' registered (2FA {})",
            alias, session.isAuthenticated() ? "complete" : "pending");
    }

    /**
     * Logs in an ADMIN user with automatic 2FA completion.
     * TOTP secret is retrieved from Firestore and decrypted via KMS.
     *
     * @param alias actor name for later reference
     * @param firebaseUid Firebase UID for user sync
     * @param email login email
     * @param password login password
     */
    @Given("{string} logs in as ADMIN with Firebase UID {string} email {string} password {string} and completes 2FA")
    public void adminLogsInWithAuto2FA(String alias, String firebaseUid, String email, String password) {
        authService.setBaseUrl(baseUrl());

        UserSession session = authService.loginAdminWithAuto2FA(alias, firebaseUid, email, password);

        assertThat(session.isAuthenticated())
            .as("Admin '%s' should have full session after 2FA", alias)
            .isTrue();
        assertThat(session.isTotpVerified())
            .as("Admin '%s' should have TOTP verified", alias)
            .isTrue();

        actorRegistry.register(alias, session);
        log.info("[E2E] Admin actor '{}' ready with full session (2FA completed)", alias);
    }

    /**
     * Completes 2FA verification for an admin actor using explicit code.
     *
     * @param alias the admin actor's name
     * @param totpCode the 6-digit TOTP code
     */
    @When("{string} completes 2FA with code {string}")
    public void completes2FA(String alias, String totpCode) {
        authService.setBaseUrl(baseUrl());

        Actor actor = actorRegistry.get(alias);
        authService.complete2FA(actor.getSession(), totpCode);

        assertThat(actor.getSession().isAuthenticated())
            .as("Admin '%s' should have full session after 2FA", alias)
            .isTrue();

        log.info("[E2E] Admin actor '{}' completed 2FA with explicit code, full session active", alias);
    }

    /**
     * Completes 2FA verification using Firestore secret (auto-generate code).
     *
     * @param alias the admin actor's name
     */
    @When("{string} completes 2FA verification")
    public void completes2FAWithFirestore(String alias) {
        authService.setBaseUrl(baseUrl());

        Actor actor = actorRegistry.get(alias);
        authService.complete2FAWithFirestoreSecret(actor.getSession());

        assertThat(actor.getSession().isAuthenticated())
            .as("Admin '%s' should have full session after 2FA", alias)
            .isTrue();

        log.info("[E2E] Admin actor '{}' completed 2FA via Firestore, full session active", alias);
    }

    /**
     * Switches context to an existing actor.
     * Used when you need to explicitly switch between actors.
     *
     * @param alias the actor's name
     */
    @Given("{string} is the active user")
    public void switchToActor(String alias) {
        actorRegistry.switchTo(alias);
        log.info("[E2E] Switched to actor '{}'", alias);
    }

    /**
     * Verifies that an actor is authenticated.
     *
     * @param alias the actor's name
     */
    @Then("{string} should be authenticated")
    public void verifyAuthenticated(String alias) {
        Actor actor = actorRegistry.get(alias);
        assertThat(actor.getSession().isAuthenticated())
            .as("Actor '%s' should be authenticated", alias)
            .isTrue();
    }

    /**
     * Verifies actor has a specific role.
     *
     * @param alias the actor's name
     * @param role expected role (COMPANY, INFLUENCER, ADMIN)
     */
    @Then("{string} should have role {string}")
    public void verifyRole(String alias, String role) {
        Actor actor = actorRegistry.get(alias);
        assertThat(actor.getSession().getRole())
            .as("Actor '%s' should have role %s", alias, role)
            .isEqualTo(role);
    }

    /**
     * Verifies the number of registered actors.
     *
     * @param count expected actor count
     */
    @Then("there should be {int} registered actors")
    public void verifyActorCount(int count) {
        assertThat(actorRegistry.size())
            .as("Should have %d registered actors", count)
            .isEqualTo(count);
    }

    /**
     * Logs a summary of all registered actors (for debugging).
     */
    @And("I print the actor registry")
    public void printActorRegistry() {
        log.info("[E2E] Actor Registry:\n{}", actorRegistry.summary());
    }

    /**
     * Verifies that sessions are isolated between actors.
     * Ensures two actors have different session cookies.
     *
     * @param actor1 first actor's name
     * @param actor2 second actor's name
     */
    @Then("{string} and {string} should have different sessions")
    public void verifySessionIsolation(String actor1, String actor2) {
        Actor a1 = actorRegistry.get(actor1);
        Actor a2 = actorRegistry.get(actor2);

        assertThat(a1.getSession().getSessionCookie())
            .as("Actors should have different session cookies")
            .isNotEqualTo(a2.getSession().getSessionCookie());

        log.info("[E2E] Verified session isolation between '{}' and '{}'", actor1, actor2);
    }

    // =========================================================================
    // OAuth-specific Assertions
    // =========================================================================

    /**
     * Verifies that an actor has OAuth authentication.
     *
     * @param alias the actor's name
     */
    @Then("{string} should have OAuth authentication")
    public void verifyOAuthAuth(String alias) {
        Actor actor = actorRegistry.get(alias);
        assertThat(actor.getSession().isOauth())
            .as("Actor '%s' should have OAuth authentication", alias)
            .isTrue();
    }

    /**
     * Verifies that an actor has a specific provider.
     *
     * @param alias the actor's name
     * @param provider expected provider (e.g., "instagram")
     */
    @Then("{string} should have provider {string}")
    public void verifyProvider(String alias, String provider) {
        Actor actor = actorRegistry.get(alias);
        assertThat(actor.getSession().getProvider())
            .as("Actor '%s' should have provider '%s'", alias, provider)
            .isEqualTo(provider);
    }

    /**
     * Verifies that an actor has 2FA verified.
     *
     * @param alias the actor's name
     */
    @Then("{string} should have 2FA verified")
    public void verify2FACompleted(String alias) {
        Actor actor = actorRegistry.get(alias);
        assertThat(actor.getSession().isTotpVerified())
            .as("Actor '%s' should have 2FA verified", alias)
            .isTrue();
    }
}
