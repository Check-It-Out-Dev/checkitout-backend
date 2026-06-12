package com.sm.instagram.platform.e2e.steps;

import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for the payments-OFF E2E suite. Uses the bare {@link #restTemplate}
 * (no auth, no actor) to verify anonymous endpoints and the bean-gating behavior.
 */
public class PaymentsDisabledSteps extends CucumberSpringConfig {

    private ResponseEntity<String> lastResponse;

    // =========================================================================
    // Anonymous HTTP requests
    // =========================================================================

    @When("anonymous client GETs {string}")
    public void anonymousGet(String path) {
        lastResponse = restTemplate.getForEntity(url(path), String.class);
    }

    @When("anonymous client POSTs {string} with body {string}")
    public void anonymousPost(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        lastResponse = restTemplate.exchange(url(path), HttpMethod.POST, entity, String.class);
    }

    @When("anonymous client POSTs {string} with header {string} {string} and body {string}")
    public void anonymousPostWithHeader(String path, String headerName, String headerValue, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(headerName, headerValue);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        lastResponse = restTemplate.exchange(url(path), HttpMethod.POST, entity, String.class);
    }

    // =========================================================================
    // Assertions
    // =========================================================================

    @Then("the anonymous response status should be {int}")
    public void responseStatusShouldBe(int expectedStatus) {
        assertThat(lastResponse.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @Then("the anonymous response body should contain {string} with value {string}")
    public void bodyShouldContainKeyValue(String key, String value) {
        assertThat(lastResponse.getBody()).isNotNull();
        assertThat(lastResponse.getBody()).contains("\"" + key + "\":" + value);
    }

    @Then("the anonymous response Cache-Control header should contain {string}")
    public void cacheControlShouldContain(String value) {
        String cc = lastResponse.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL);
        assertThat(cc).isNotNull().contains(value);
    }
}
