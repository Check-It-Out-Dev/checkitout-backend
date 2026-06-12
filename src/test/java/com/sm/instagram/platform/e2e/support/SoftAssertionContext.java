package com.sm.instagram.platform.e2e.support;

import io.cucumber.spring.ScenarioScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft assertion context for collecting assertion failures without stopping test execution.
 *
 * <p>This enables consolidated scenarios where multiple assertions are checked,
 * and all failures are reported at the end of the scenario instead of stopping
 * at the first failure.
 *
 * <p>Usage:
 * <pre>
 * // In step definitions:
 * softAssertionContext.softAssertEquals(expected, actual, "Description");
 * softAssertionContext.softAssertTrue(condition, "Description");
 *
 * // Failures are automatically reported in @After hook via SoftAssertionHooks
 * </pre>
 *
 * <p>Benefits:
 * <ul>
 *   <li>Run all assertions in a scenario even if some fail</li>
 *   <li>Reduce Firebase API calls by consolidating scenarios</li>
 *   <li>Get comprehensive test results in single run</li>
 * </ul>
 */
@Slf4j
@Component
@ScenarioScope
public class SoftAssertionContext {

    private final List<AssertionFailure> failures = new ArrayList<>();
    private int totalAssertions = 0;
    private boolean softModeEnabled = true;

    /**
     * Records an assertion failure for later reporting.
     *
     * @param stepDescription Description of the step that failed
     * @param expected Expected value
     * @param actual Actual value
     * @param message Additional context message
     */
    public void addFailure(String stepDescription, Object expected, Object actual, String message) {
        AssertionFailure failure = new AssertionFailure(
            stepDescription,
            expected != null ? expected.toString() : "null",
            actual != null ? actual.toString() : "null",
            message,
            totalAssertions
        );
        failures.add(failure);
        log.warn("[SOFT ASSERT FAILED] Step #{}: {} - Expected: {}, Actual: {}, Message: {}",
            totalAssertions, stepDescription, expected, actual, message);
    }

    /**
     * Soft assertion for equality check.
     * Records failure but continues execution.
     *
     * @param expected Expected value
     * @param actual Actual value
     * @param description Description of what's being checked
     */
    public void softAssertEquals(Object expected, Object actual, String description) {
        totalAssertions++;
        if (!equals(expected, actual)) {
            addFailure(description, expected, actual, "Values not equal");
        } else {
            log.debug("[SOFT ASSERT PASSED] Step #{}: {} - Value: {}", totalAssertions, description, actual);
        }
    }

    /**
     * Soft assertion for status code check.
     * Records failure but continues execution.
     *
     * @param expectedStatus Expected HTTP status
     * @param actualStatus Actual HTTP status
     * @param endpoint Endpoint being tested
     */
    public void softAssertStatus(int expectedStatus, int actualStatus, String endpoint) {
        totalAssertions++;
        if (expectedStatus != actualStatus) {
            addFailure("HTTP Status for " + endpoint, expectedStatus, actualStatus,
                "Expected status " + expectedStatus + " but got " + actualStatus);
        } else {
            log.debug("[SOFT ASSERT PASSED] Step #{}: {} returned {}", totalAssertions, endpoint, actualStatus);
        }
    }

    /**
     * Soft assertion for status code range check (e.g., 400 or 403).
     * Records failure but continues execution.
     *
     * @param expectedStatuses Array of acceptable status codes
     * @param actualStatus Actual HTTP status
     * @param endpoint Endpoint being tested
     */
    public void softAssertStatusIn(int[] expectedStatuses, int actualStatus, String endpoint) {
        totalAssertions++;
        boolean matches = false;
        for (int expected : expectedStatuses) {
            if (expected == actualStatus) {
                matches = true;
                break;
            }
        }
        if (!matches) {
            StringBuilder expected = new StringBuilder();
            for (int i = 0; i < expectedStatuses.length; i++) {
                if (i > 0) expected.append(" or ");
                expected.append(expectedStatuses[i]);
            }
            addFailure("HTTP Status for " + endpoint, expected.toString(), actualStatus,
                "Status not in expected set");
        } else {
            log.debug("[SOFT ASSERT PASSED] Step #{}: {} returned {}", totalAssertions, endpoint, actualStatus);
        }
    }

    /**
     * Soft assertion for boolean true check.
     * Records failure but continues execution.
     *
     * @param condition Condition to check
     * @param description Description of what's being checked
     */
    public void softAssertTrue(boolean condition, String description) {
        totalAssertions++;
        if (!condition) {
            addFailure(description, true, false, "Condition was false");
        } else {
            log.debug("[SOFT ASSERT PASSED] Step #{}: {}", totalAssertions, description);
        }
    }

    /**
     * Soft assertion for boolean false check.
     * Records failure but continues execution.
     *
     * @param condition Condition to check
     * @param description Description of what's being checked
     */
    public void softAssertFalse(boolean condition, String description) {
        totalAssertions++;
        if (condition) {
            addFailure(description, false, true, "Condition was true");
        } else {
            log.debug("[SOFT ASSERT PASSED] Step #{}: {}", totalAssertions, description);
        }
    }

    /**
     * Soft assertion for not null check.
     * Records failure but continues execution.
     *
     * @param value Value to check
     * @param description Description of what's being checked
     */
    public void softAssertNotNull(Object value, String description) {
        totalAssertions++;
        if (value == null) {
            addFailure(description, "non-null", "null", "Value was null");
        } else {
            log.debug("[SOFT ASSERT PASSED] Step #{}: {} is not null", totalAssertions, description);
        }
    }

    /**
     * Soft assertion for numeric comparison (greater than).
     */
    public void softAssertGreaterThan(Number actual, Number threshold, String description) {
        totalAssertions++;
        if (actual.doubleValue() <= threshold.doubleValue()) {
            addFailure(description, ">" + threshold, actual, "Value not greater than threshold");
        } else {
            log.debug("[SOFT ASSERT PASSED] Step #{}: {} ({} > {})", totalAssertions, description, actual, threshold);
        }
    }

    /**
     * Soft assertion for numeric comparison (less than).
     */
    public void softAssertLessThan(Number actual, Number threshold, String description) {
        totalAssertions++;
        if (actual.doubleValue() >= threshold.doubleValue()) {
            addFailure(description, "<" + threshold, actual, "Value not less than threshold");
        } else {
            log.debug("[SOFT ASSERT PASSED] Step #{}: {} ({} < {})", totalAssertions, description, actual, threshold);
        }
    }

    /**
     * Checks if there are any recorded failures.
     *
     * @return true if failures exist
     */
    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    /**
     * Gets the number of recorded failures.
     *
     * @return failure count
     */
    public int getFailureCount() {
        return failures.size();
    }

    /**
     * Gets the total number of assertions performed.
     *
     * @return total assertion count
     */
    public int getTotalAssertions() {
        return totalAssertions;
    }

    /**
     * Gets the list of recorded failures.
     *
     * @return list of failures
     */
    public List<AssertionFailure> getFailures() {
        return new ArrayList<>(failures);
    }

    /**
     * Generates a failure report suitable for test output.
     *
     * @return formatted failure report
     */
    public String generateReport() {
        if (failures.isEmpty()) {
            return String.format("All %d assertions passed", totalAssertions);
        }

        StringBuilder report = new StringBuilder();
        report.append(String.format("\n========== SOFT ASSERTION REPORT ==========\n"));
        report.append(String.format("Total Assertions: %d\n", totalAssertions));
        report.append(String.format("Passed: %d\n", totalAssertions - failures.size()));
        report.append(String.format("Failed: %d\n", failures.size()));
        report.append("============================================\n\n");

        for (int i = 0; i < failures.size(); i++) {
            AssertionFailure f = failures.get(i);
            report.append(String.format("FAILURE #%d (Assertion #%d):\n", i + 1, f.assertionNumber()));
            report.append(String.format("  Step: %s\n", f.stepDescription()));
            report.append(String.format("  Expected: %s\n", f.expected()));
            report.append(String.format("  Actual: %s\n", f.actual()));
            report.append(String.format("  Message: %s\n\n", f.message()));
        }

        return report.toString();
    }

    /**
     * Resets the context for a new scenario.
     */
    public void reset() {
        failures.clear();
        totalAssertions = 0;
        softModeEnabled = true;
    }

    /**
     * Enables or disables soft assertion mode.
     * When disabled, assertions throw immediately.
     *
     * @param enabled true to enable soft mode
     */
    public void setSoftModeEnabled(boolean enabled) {
        this.softModeEnabled = enabled;
    }

    /**
     * Checks if soft mode is enabled.
     *
     * @return true if soft assertions are enabled
     */
    public boolean isSoftModeEnabled() {
        return softModeEnabled;
    }

    private boolean equals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Record of an assertion failure.
     */
    public record AssertionFailure(
        String stepDescription,
        String expected,
        String actual,
        String message,
        int assertionNumber
    ) {}
}
