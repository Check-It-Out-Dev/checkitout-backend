package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Dedicated JUnit 5 Platform Suite runner for partnership flow E2E tests.
 *
 * <p>This runner executes scenarios tagged with @partnership-flow to test
 * partnership opportunity creation and management workflows:
 * <ul>
 *   <li>Company creates partnership opportunity (happy path)</li>
 *   <li>Validation error scenarios (future)</li>
 *   <li>Photo upload flows (future)</li>
 * </ul>
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pe2e -Dit.test=RunPartnershipFlowIT
 * </pre>
 *
 * <p>Or with tag filter:
 * <pre>
 * mvn verify -Pe2e -Dcucumber.filter.tags="@partnership-flow"
 * </pre>
 *
 * @see RunCucumberIT for normal E2E tests
 * @see RunMultiUserIT for multi-user session isolation tests
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/partnership")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/partnership-flow.html, json:target/cucumber-reports/partnership-flow.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@partnership-flow")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunPartnershipFlowIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run features matching @partnership-flow tag.
}
