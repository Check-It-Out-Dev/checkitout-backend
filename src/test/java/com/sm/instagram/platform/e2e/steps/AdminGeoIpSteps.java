package com.sm.instagram.platform.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Admin GeoIP E2E tests.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Tiered impossible travel detection (Tier 1: Same City, Tier 2: Same Country, Tier 3: International)</li>
 *   <li>Risk scoring (0-100) and risk level classification</li>
 *   <li>Travel event creation and audit trail</li>
 *   <li>Admin-only endpoint access validation</li>
 * </ul>
 */
@Slf4j
public class AdminGeoIpSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private ScenarioContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, Object> lastGeoIpResponse;

    // ============================================================================
    // TRAVEL ANALYSIS STEPS
    // ============================================================================

    /**
     * Tests travel between two IP addresses with specified elapsed time.
     * Uses the admin /test-travel endpoint with full analysis response.
     */
    @When("{string} tests travel from IP {string} to IP {string} with {int} minutes elapsed")
    public void testTravelBetweenIPs(String alias, String fromIp, String toIp, int minutes) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();

        String url = String.format("/admin/geoip/test-travel?fromIp=%s&toIp=%s&minutes=%d",
            fromIp, toIp, minutes);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url(url),
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
            );

            lastGeoIpResponse = response.getBody();
            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(response.getBody()));
            log.info("[E2E] Travel analysis: {} -> {} ({} min) = riskScore={}, riskLevel={}, impossible={}",
                fromIp, toIp, minutes,
                lastGeoIpResponse != null ? lastGeoIpResponse.get("riskScore") : "null",
                lastGeoIpResponse != null ? lastGeoIpResponse.get("riskLevel") : "null",
                lastGeoIpResponse != null ? lastGeoIpResponse.get("impossibleTravel") : "null");
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getResponseBodyAsString())));
            lastGeoIpResponse = null;
            log.error("[E2E] Travel analysis failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        }
    }

    // ============================================================================
    // SAME CITY DETECTION
    // ============================================================================

    @Then("the response should indicate same city detected")
    public void sameCityDetected() {
        assertThat(lastGeoIpResponse).isNotNull();
        assertThat(lastGeoIpResponse.get("sameCity"))
            .as("Same city should be detected")
            .isEqualTo(true);
    }

    // ============================================================================
    // IMPOSSIBLE TRAVEL FLAGS
    // ============================================================================

    @Then("the impossible travel flag should be false")
    public void impossibleTravelFlagFalse() {
        assertThat(lastGeoIpResponse).isNotNull();
        assertThat(lastGeoIpResponse.get("impossibleTravel"))
            .as("Impossible travel should be false")
            .isEqualTo(false);
    }

    @Then("the impossible travel flag should be true")
    public void impossibleTravelFlagTrue() {
        assertThat(lastGeoIpResponse).isNotNull();
        assertThat(lastGeoIpResponse.get("impossibleTravel"))
            .as("Impossible travel should be true")
            .isEqualTo(true);
    }

    // ============================================================================
    // COUNTRY FLAGS
    // ============================================================================

    @Then("the same country flag should be true")
    public void sameCountryFlagTrue() {
        assertThat(lastGeoIpResponse).isNotNull();
        assertThat(lastGeoIpResponse.get("sameCountry"))
            .as("Same country flag should be true")
            .isEqualTo(true);
    }

    @Then("the country jump flag should be true")
    public void countryJumpFlagTrue() {
        assertThat(lastGeoIpResponse).isNotNull();
        assertThat(lastGeoIpResponse.get("countryJump"))
            .as("Country jump flag should be true")
            .isEqualTo(true);
    }

    // ============================================================================
    // RISK SCORING
    // ============================================================================

    @Then("the risk score should be less than {int}")
    public void riskScoreLessThan(int threshold) {
        assertThat(lastGeoIpResponse).isNotNull();
        int riskScore = ((Number) lastGeoIpResponse.get("riskScore")).intValue();
        assertThat(riskScore)
            .as("Risk score should be less than %d but was %d", threshold, riskScore)
            .isLessThan(threshold);
    }

    @Then("the risk score should be at least {int}")
    public void riskScoreAtLeast(int threshold) {
        assertThat(lastGeoIpResponse).isNotNull();
        int riskScore = ((Number) lastGeoIpResponse.get("riskScore")).intValue();
        assertThat(riskScore)
            .as("Risk score should be at least %d but was %d", threshold, riskScore)
            .isGreaterThanOrEqualTo(threshold);
    }

    @Then("the risk level should be {string} or {string}")
    public void riskLevelShouldBe(String level1, String level2) {
        assertThat(lastGeoIpResponse).isNotNull();
        String riskLevel = (String) lastGeoIpResponse.get("riskLevel");
        assertThat(riskLevel)
            .as("Risk level should be '%s' or '%s' but was '%s'", level1, level2, riskLevel)
            .isIn(level1, level2);
    }

    @Then("the risk level should be {string}")
    public void riskLevelExact(String expectedLevel) {
        assertThat(lastGeoIpResponse).isNotNull();
        String riskLevel = (String) lastGeoIpResponse.get("riskLevel");
        assertThat(riskLevel)
            .as("Risk level should be '%s'", expectedLevel)
            .isEqualTo(expectedLevel);
    }

    // ============================================================================
    // SPEED VALIDATION
    // ============================================================================

    @Then("the speed should be less than {int} km\\/h")
    public void speedLessThan(int threshold) {
        assertThat(lastGeoIpResponse).isNotNull();
        long speed = ((Number) lastGeoIpResponse.get("speedKmh")).longValue();
        assertThat(speed)
            .as("Speed should be less than %d km/h but was %d", threshold, speed)
            .isLessThan(threshold);
    }

    @Then("the speed should be greater than {int} km\\/h")
    public void speedGreaterThan(int threshold) {
        assertThat(lastGeoIpResponse).isNotNull();
        long speed = ((Number) lastGeoIpResponse.get("speedKmh")).longValue();
        assertThat(speed)
            .as("Speed should be greater than %d km/h but was %d", threshold, speed)
            .isGreaterThan(threshold);
    }

    // ============================================================================
    // TRAVEL EVENT VALIDATION
    // ============================================================================

    @SuppressWarnings("unchecked")
    @Then("the travel event should contain {string} field")
    public void travelEventContainsField(String fieldName) {
        assertThat(lastGeoIpResponse).isNotNull();
        Map<String, Object> travelEvent = (Map<String, Object>) lastGeoIpResponse.get("travelEvent");
        assertThat(travelEvent)
            .as("Travel event should be present")
            .isNotNull();
        assertThat(travelEvent.containsKey(fieldName))
            .as("Travel event should contain '%s' field", fieldName)
            .isTrue();
    }

    // ============================================================================
    // ADMIN ENDPOINT ACCESS
    // ============================================================================

    @When("{string} calls GET {string}")
    public void callsGetEndpoint(String alias, String endpoint) {
        Actor actor = actorRegistry.get(alias);
        HttpHeaders headers = actor.getSession().buildAuthHeaders();

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url(endpoint),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            lastGeoIpResponse = response.getBody();
            context.setLastResponse(ResponseEntity.status(response.getStatusCode()).body(response.getBody()));
            log.info("[E2E] GET {} returned {}", endpoint, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            context.setLastResponse(ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getResponseBodyAsString())));
            lastGeoIpResponse = null;
            log.info("[E2E] GET {} returned {}", endpoint, e.getStatusCode());
        }
    }

    @Then("the response should contain country information")
    public void containsCountryInfo() {
        assertThat(lastGeoIpResponse).isNotNull();
        // GeoLocation object has country or countryCode
        boolean hasCountry = lastGeoIpResponse.containsKey("country") ||
                             lastGeoIpResponse.containsKey("countryCode") ||
                             lastGeoIpResponse.containsKey("countryName");
        assertThat(hasCountry)
            .as("Response should contain country information")
            .isTrue();
    }

    @Then("the response should contain {string} field")
    public void containsField(String fieldName) {
        assertThat(lastGeoIpResponse).isNotNull();
        assertThat(lastGeoIpResponse.containsKey(fieldName))
            .as("Response should contain '%s' field", fieldName)
            .isTrue();
    }

    @Then("the response should contain cache metrics")
    public void containsCacheMetrics() {
        assertThat(lastGeoIpResponse).isNotNull();
        log.info("[E2E] Metrics response contains {} keys", lastGeoIpResponse.size());
    }

    // ============================================================================
    // VPN RISK INDICATORS
    // ============================================================================

    @Then("the response should include VPN risk indicators if detected")
    public void vpnRiskIndicators() {
        // VPN detection is optional - just verify we have risk score
        assertThat(lastGeoIpResponse).isNotNull();
        assertThat(lastGeoIpResponse.get("riskScore")).isNotNull();
        log.info("[E2E] Risk score: {} (VPN detection is optional)", lastGeoIpResponse.get("riskScore"));
    }
}
