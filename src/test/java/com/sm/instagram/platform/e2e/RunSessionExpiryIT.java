package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Dedicated JUnit 5 Platform Suite runner for session expiry tests.
 *
 * <p>This runner is executed in a SEPARATE JVM fork with short session duration
 * (15 seconds) to test expired session scenarios without affecting other tests.
 *
 * <p>The separation is critical because:
 * <ul>
 *   <li>Session expiry tests need 15-second sessions to complete in reasonable time</li>
 *   <li>Other tests need production-like sessions (7 days) to avoid flaky failures</li>
 *   <li>Spring Context caches configuration at startup - different durations need different contexts</li>
 * </ul>
 *
 * <p>Maven Failsafe is configured to run this in a fresh JVM fork with system properties:
 * <pre>
 * -Dsession.full-duration-seconds=15
 * -Dsession.partial-duration-seconds=15
 * reuseForks=false
 * </pre>
 *
 * <p><b>How it works:</b> Spring's PropertySource priority ensures system properties (-D flags)
 * override values in application-e2e.yml. No YAML placeholders needed - Spring handles it automatically.
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pe2e -Dit.test=RunSessionExpiryIT
 * </pre>
 *
 * @see RunCucumberIT for normal E2E tests (excludes @session-expiry)
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/session-expiry.html, json:target/cucumber-reports/session-expiry.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@session-expiry")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunSessionExpiryIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run features matching @session-expiry tag.
}
