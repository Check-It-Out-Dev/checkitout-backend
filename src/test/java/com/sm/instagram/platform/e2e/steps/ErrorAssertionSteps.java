package com.sm.instagram.platform.e2e.steps;

import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for asserting error responses in E2E tests.
 * Used for negative test scenarios to verify proper error handling.
 *
 * <p>Error response structure expected from the API:
 * <pre>
 * {
 *   "timestamp": "2026-01-10T12:34:56.789",
 *   "status": 401,
 *   "error": "Unauthorized",
 *   "message": "Invalid credentials provided",
 *   "path": "/auth/firebase/login",
 *   "requestId": "REQ-a1b2c3d4",
 *   "validationErrors": { "field": "error message" }
 * }
 * </pre>
 */
@Slf4j
public class ErrorAssertionSteps extends CucumberSpringConfig {

    @Autowired
    private ScenarioContext context;

    @Then("the error message should be {string}")
    public void verifyErrorMessage(String expectedMessage) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorBody = (Map<String, Object>) body;
            String actualMessage = (String) errorBody.get("message");
            assertThat(actualMessage)
                .as("Error message should match expected")
                .isEqualTo(expectedMessage);
            log.info("[E2E] Verified error message: {}", expectedMessage);
        } else {
            throw new AssertionError("Response body is not a Map: " + body.getClass());
        }
    }

    @Then("the error message should contain {string}")
    public void verifyErrorMessageContains(String expectedSubstring) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorBody = (Map<String, Object>) body;
            String actualMessage = (String) errorBody.get("message");
            assertThat(actualMessage)
                .as("Error message should contain: " + expectedSubstring)
                .containsIgnoringCase(expectedSubstring);
            log.info("[E2E] Verified error message contains: {}", expectedSubstring);
        } else {
            throw new AssertionError("Response body is not a Map: " + body.getClass());
        }
    }

    @Then("the response error should be {string}")
    public void verifyResponseError(String expectedError) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorBody = (Map<String, Object>) body;
            String actualError = (String) errorBody.get("error");
            assertThat(actualError)
                .as("Response error field should match")
                .isEqualTo(expectedError);
            log.info("[E2E] Verified response error: {}", expectedError);
        } else {
            throw new AssertionError("Response body is not a Map: " + body.getClass());
        }
    }

    @Then("the validation error for {string} should be {string}")
    public void verifyValidationError(String field, String expectedError) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorBody = (Map<String, Object>) body;
            @SuppressWarnings("unchecked")
            Map<String, String> validationErrors = (Map<String, String>) errorBody.get("validationErrors");
            assertThat(validationErrors)
                .as("Validation errors should not be null")
                .isNotNull();
            assertThat(validationErrors.get(field))
                .as("Validation error for field " + field)
                .isEqualTo(expectedError);
            log.info("[E2E] Verified validation error for {}: {}", field, expectedError);
        } else {
            throw new AssertionError("Response body is not a Map: " + body.getClass());
        }
    }

    @Then("the response should have a request ID")
    public void verifyRequestIdPresent() {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorBody = (Map<String, Object>) body;
            assertThat(errorBody.get("requestId"))
                .as("Request ID should be present")
                .isNotNull();
            log.info("[E2E] Verified request ID present: {}", errorBody.get("requestId"));
        } else {
            throw new AssertionError("Response body is not a Map: " + body.getClass());
        }
    }

    @Then("the response path should be {string}")
    public void verifyResponsePath(String expectedPath) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorBody = (Map<String, Object>) body;
            String actualPath = (String) errorBody.get("path");
            assertThat(actualPath)
                .as("Response path should match")
                .isEqualTo(expectedPath);
            log.info("[E2E] Verified response path: {}", expectedPath);
        } else {
            throw new AssertionError("Response body is not a Map: " + body.getClass());
        }
    }

    @Then("the response messageKey should be {string}")
    public void verifyMessageKey(String expectedMessageKey) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorBody = (Map<String, Object>) body;
            String actualKey = (String) errorBody.get("messageKey");
            assertThat(actualKey)
                .as("Response messageKey should match")
                .isEqualTo(expectedMessageKey);
            log.info("[E2E] Verified messageKey: {}", expectedMessageKey);
        } else {
            throw new AssertionError("Response body is not a Map: " + body.getClass());
        }
    }

    @Then("the validation error for {string} should contain {string}")
    public void verifyValidationErrorContains(String field, String expectedFragment) {
        ResponseEntity<?> response = context.getLastResponse();
        assertThat(response)
            .as("Response should not be null")
            .isNotNull();

        Object body = response.getBody();
        assertThat(body)
            .as("Response body should not be null")
            .isNotNull();

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorBody = (Map<String, Object>) body;
            @SuppressWarnings("unchecked")
            Map<String, String> validationErrors = (Map<String, String>) errorBody.get("validationErrors");
            assertThat(validationErrors)
                .as("Validation errors should not be null")
                .isNotNull();
            assertThat(validationErrors.get(field))
                .as("Validation error for field '%s' should contain '%s'", field, expectedFragment)
                .containsIgnoringCase(expectedFragment);
            log.info("[E2E] Verified validation error for {} contains: {}", field, expectedFragment);
        } else {
            throw new AssertionError("Response body is not a Map: " + body.getClass());
        }
    }
}
