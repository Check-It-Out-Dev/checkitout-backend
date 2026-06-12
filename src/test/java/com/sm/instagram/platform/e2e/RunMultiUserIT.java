package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Dedicated JUnit 5 Platform Suite runner for multi-user E2E tests.
 *
 * <p>This runner executes scenarios tagged with @multi-user in an isolated suite.
 * Multi-user tests verify:
 * <ul>
 *   <li>Multiple actors can log in with isolated sessions</li>
 *   <li>Session cookies are properly isolated between actors</li>
 *   <li>Complex multi-user business flows (company + influencer interactions)</li>
 * </ul>
 *
 * <p>The separation ensures:
 * <ul>
 *   <li>Multi-user tests don't interfere with single-user tests</li>
 *   <li>Clear reporting for multi-user scenarios</li>
 *   <li>Ability to run multi-user tests independently</li>
 * </ul>
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pe2e -Dit.test=RunMultiUserIT
 * </pre>
 *
 * @see RunCucumberIT for normal single-user E2E tests (excludes @multi-user)
 * @see RunSessionExpiryIT for session expiry tests
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/multi-user.html, json:target/cucumber-reports/multi-user.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@multi-user")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunMultiUserIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run features matching @multi-user tag.
}
