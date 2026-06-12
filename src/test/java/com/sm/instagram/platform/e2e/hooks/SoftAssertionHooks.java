package com.sm.instagram.platform.e2e.hooks;

import com.sm.instagram.platform.e2e.support.SoftAssertionContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cucumber hooks for soft assertion handling.
 *
 * <p>These hooks:
 * <ul>
 *   <li>Reset the soft assertion context before each scenario</li>
 *   <li>Report all collected failures after each scenario</li>
 *   <li>Fail the scenario if any soft assertions failed</li>
 * </ul>
 *
 * <p>This enables consolidated scenarios where multiple checks are performed
 * in a single scenario, reducing Firebase API calls while still catching all failures.
 */
@Slf4j
@RequiredArgsConstructor
public class SoftAssertionHooks {

    private final SoftAssertionContext softAssertionContext;

    /**
     * Resets soft assertion context before each scenario.
     * Order 0 ensures this runs before other @Before hooks.
     */
    @Before(order = 0)
    public void resetSoftAssertions(Scenario scenario) {
        softAssertionContext.reset();
        log.debug("[SOFT ASSERTIONS] Reset for scenario: {}", scenario.getName());
    }

    /**
     * Reports soft assertion failures after each scenario.
     * Order 10000 ensures this runs after other @After hooks.
     *
     * <p>If any soft assertions failed during the scenario:
     * <ul>
     *   <li>Logs the full failure report</li>
     *   <li>Attaches report to Cucumber scenario output</li>
     *   <li>Throws AssertionError to fail the scenario</li>
     * </ul>
     */
    @After(order = 10000)
    public void reportSoftAssertions(Scenario scenario) {
        if (softAssertionContext.getTotalAssertions() == 0) {
            // No soft assertions were used in this scenario
            return;
        }

        String report = softAssertionContext.generateReport();

        if (softAssertionContext.hasFailures()) {
            // Log the failures
            log.error("[SOFT ASSERTIONS] Scenario '{}' had {} failures out of {} assertions",
                scenario.getName(),
                softAssertionContext.getFailureCount(),
                softAssertionContext.getTotalAssertions());
            log.error(report);

            // Attach report to Cucumber output
            scenario.attach(report.getBytes(), "text/plain", "Soft Assertion Report");

            // Fail the scenario with comprehensive message
            throw new AssertionError(String.format(
                "Scenario '%s' failed with %d soft assertion failure(s) out of %d total assertions.\n%s",
                scenario.getName(),
                softAssertionContext.getFailureCount(),
                softAssertionContext.getTotalAssertions(),
                report
            ));
        } else {
            // All assertions passed
            log.info("[SOFT ASSERTIONS] Scenario '{}' passed all {} assertions",
                scenario.getName(),
                softAssertionContext.getTotalAssertions());
        }
    }
}
