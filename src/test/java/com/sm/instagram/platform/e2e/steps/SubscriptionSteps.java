package com.sm.instagram.platform.e2e.steps;

import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class SubscriptionSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private ScenarioContext context;

    private Map lastSubscriptionStatus;

    // =========================================================================
    // Status
    // =========================================================================

    @When("{string} checks subscription status")
    public void checksSubscriptionStatus(String actorName) {
        var actor = actorRegistry.get(actorName);
        var response = actor.get(restTemplate, url("/subscription/status"));
        context.setLastResponse(response);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            lastSubscriptionStatus = response.getBody();
        }
    }

    @Then("the subscription status should be {string}")
    public void subscriptionStatusShouldBe(String expectedStatus) {
        assertThat(lastSubscriptionStatus).isNotNull();
        assertThat(lastSubscriptionStatus.get("status")).isEqualTo(expectedStatus);
    }

    @Then("the subscription should have plan {string}")
    public void subscriptionShouldHavePlan(String planName) {
        assertThat(lastSubscriptionStatus.get("currentPlanName")).isEqualTo(planName);
    }

    @Then("the subscription should have campaign limit {int}")
    public void subscriptionShouldHaveCampaignLimit(int limit) {
        assertThat(((Number) lastSubscriptionStatus.get("campaignLimit")).intValue()).isEqualTo(limit);
    }

    @Then("the subscription should be trial eligible")
    public void subscriptionShouldBeTrialEligible() {
        assertThat(lastSubscriptionStatus.get("trialEligible")).isEqualTo(true);
    }

    @Then("the subscription should NOT be trial eligible")
    public void subscriptionShouldNotBeTrialEligible() {
        assertThat(lastSubscriptionStatus.get("trialEligible")).isEqualTo(false);
    }

    // =========================================================================
    // Trial
    // =========================================================================

    @When("{string} activates trial")
    public void activatesTrial(String actorName) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/subscription/trial/activate"), null);
        context.setLastResponse(response);
    }

    // =========================================================================
    // Downgrade
    // =========================================================================

    @When("{string} requests downgrade to plan {string}")
    public void requestsDowngrade(String actorName, String planName) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/subscription/downgrade"), Map.of("targetPlan", planName));
        context.setLastResponse(response);
    }

    @When("{string} cancels pending downgrade")
    public void cancelsPendingDowngrade(String actorName) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/subscription/downgrade/cancel"), null);
        context.setLastResponse(response);
    }

    // =========================================================================
    // Campaign creation (via test endpoint)
    // =========================================================================

    @When("{string} creates a test campaign named {string}")
    public void createsTestCampaign(String actorName, String campaignName) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/test/subscription/create-campaign"),
                Map.of("campaignName", campaignName));
        context.setLastResponse(response);
    }

    @Then("the campaign creation should be blocked")
    public void campaignCreationShouldBeBlocked() {
        var response = context.getLastResponse();
        assertThat(response.getStatusCode().value()).isGreaterThanOrEqualTo(400);
    }

    // =========================================================================
    // Admin: webhook simulation
    // =========================================================================

    @When("{string} simulates webhook {string} for user {string} with plan {string}")
    public void simulatesWebhookWithPlan(String actorName, String eventType, String email, String planName) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/test/subscription/simulate-webhook"),
                Map.of("email", email, "eventType", eventType, "planName", planName));
        context.setLastResponse(response);
    }

    @When("{string} simulates webhook {string} for user {string}")
    public void simulatesWebhook(String actorName, String eventType, String email) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/test/subscription/simulate-webhook"),
                Map.of("email", email, "eventType", eventType));
        context.setLastResponse(response);
    }

    @When("{string} simulates webhook {string} for user {string} with amount {int}")
    public void simulatesWebhookWithAmount(String actorName, String eventType, String email, int amount) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/test/subscription/simulate-webhook"),
                Map.of("email", email, "eventType", eventType, "amountPaidCents", amount));
        context.setLastResponse(response);
    }

    // =========================================================================
    // Admin: state setup
    // =========================================================================

    @Given("{string} resets subscription for user {string}")
    public void resetsSubscription(String actorName, String email) {
        var actor = actorRegistry.get(actorName);
        actor.post(restTemplate, url("/test/subscription/reset"), Map.of("email", email));
    }

    @Given("{string} sets subscription for user {string} to plan {string} with status {string}")
    public void setsSubscription(String actorName, String email, String planName, String status) {
        var actor = actorRegistry.get(actorName);
        actor.post(restTemplate, url("/test/subscription/set-state"),
                Map.of("email", email, "planName", planName, "status", status));
    }

    // =========================================================================
    // Company data (NIP verification via real registries)
    // =========================================================================

    @Given("{string} verifies company NIP {string}")
    public void verifiesCompanyNip(String actorName, String nip) {
        var actor = actorRegistry.get(actorName);

        // Step 1: Configure the registry stub (E2E uses @Primary stub, not real GUS)
        actor.post(restTemplate, url("/test/registry/configure-krs-company"), Map.of("nip", nip));

        // Step 2: Lookup via production endpoint (hits stub)
        actor.post(restTemplate, url("/registry/lookup"), Map.of("nip", nip));

        // Step 3: Confirm via production endpoint (persists CompanyData to real DB)
        var response = actor.post(restTemplate, url("/registry/confirm"), Map.of("nip", nip));
        context.setLastResponse(response);

        int status = response.getStatusCode().value();
        assertThat(status)
                .as("NIP verification should succeed (200) or already exist (409)")
                .isIn(200, 409);
    }

    // =========================================================================
    // Admin: invoices
    // =========================================================================

    @Then("user {string} should have an invoice with status {string}")
    public void userShouldHaveInvoiceWithStatus(String email, String expectedStatus) {
        // Use Admin actor's session for test endpoint access
        var admin = actorRegistry.get("Admin");
        var response = admin.get(restTemplate, url("/test/subscription/invoices?email=" + email));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var invoices = (java.util.List<Map<String, Object>>) response.getBody().get("invoices");
        assertThat(invoices).isNotEmpty();
        assertThat(invoices.get(0).get("status")).isEqualTo(expectedStatus);
    }

    @When("{string} triggers invoice retry")
    public void triggersInvoiceRetry(String actorName) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/test/subscription/trigger-invoice-retry"), null);
        context.setLastResponse(response);
    }

    // =========================================================================
    // Admin: terms
    // =========================================================================

    @When("{string} triggers enter-terms-pending")
    public void triggersEnterTermsPending(String actorName) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/test/subscription/enter-terms-pending"), Map.of());
        context.setLastResponse(response);
    }

    @When("{string} accepts terms for user {string}")
    public void acceptsTerms(String actorName, String email) {
        var actor = actorRegistry.get(actorName);
        var response = actor.post(restTemplate, url("/test/subscription/accept-terms"), Map.of("email", email));
        context.setLastResponse(response);
    }
}
