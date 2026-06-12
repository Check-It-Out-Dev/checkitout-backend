package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit 5 Platform Suite runner for Cucumber E2E tests.
 *
 * <p>This class serves as the entry point for running all Cucumber scenarios.
 * It configures Cucumber to:
 * <ul>
 *   <li>Find feature files in src/test/resources/features</li>
 *   <li>Find step definitions in com.sm.instagram.platform.e2e package</li>
 *   <li>Generate HTML and JSON reports</li>
 * </ul>
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pe2e
 * </pre>
 *
 * <p>Run specific tags:
 * <pre>
 * mvn verify -Pe2e -Dcucumber.filter.tags="@smoke"
 * mvn verify -Pe2e -Dcucumber.filter.tags="@company or @influencer"
 * mvn verify -Pe2e -Dcucumber.filter.tags="@admin and @2fa"
 * </pre>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/cucumber.html, json:target/cucumber-reports/cucumber.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip and not @session-expiry and not @multi-user and not @rate-limiting and not @admin-ops and not @security-suite and not @consolidated-suite and not @consent and not @consent-lifecycle and not @registry and not @influencer-verification and not @step-up-auth and not @partnership-flow and not @notification-e2e and not @subscription and not @payments-off")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunCucumberIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run all features matching the configuration.
}
