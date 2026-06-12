package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Dedicated JUnit 5 Platform Suite runner for rate limiting tests.
 *
 * <p>This runner is executed in a SEPARATE JVM fork with strict rate limits
 * to test rate limiting enforcement without affecting other tests.
 *
 * <p>The separation is critical because:
 * <ul>
 *   <li>Rate limiting tests need low limits (5 requests) to easily trigger 429 responses</li>
 *   <li>Other tests need high limits (10000) to avoid false rate limit failures</li>
 *   <li>Spring Context caches configuration at startup - different limits need different contexts</li>
 * </ul>
 *
 * <p>Maven Failsafe is configured to run this in a fresh JVM fork with system properties:
 * <pre>
 * -DRL_STANDARD_REQ=5  -DRL_STANDARD_WIN=60  -DRL_STANDARD_BLOCK=10
 * -DRL_STRICT_REQ=3    -DRL_STRICT_WIN=60    -DRL_STRICT_BLOCK=10
 * -DRL_RELAXED_REQ=10  -DRL_RELAXED_WIN=60   -DRL_RELAXED_BLOCK=10
 * -DRL_HIGH_REQ=20     -DRL_HIGH_WIN=60      -DRL_HIGH_BLOCK=10
 * -DRL_AUTH_REQ=5      -DRL_AUTH_WIN=60      -DRL_AUTH_BLOCK=10
 * reuseForks=false
 * </pre>
 *
 * <p><b>How it works:</b> Spring's PropertySource priority ensures system properties (-D flags)
 * override placeholder defaults in application-e2e.yml (e.g., ${RL_STANDARD_REQ:10000}).
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pe2e -Dit.test=RunRateLimitingIT
 * </pre>
 *
 * @see RunCucumberIT for normal E2E tests (excludes @rate-limiting)
 * @see RunSessionExpiryIT for session expiry tests
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/rate-limiting.html, json:target/cucumber-reports/rate-limiting.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@rate-limiting")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunRateLimitingIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run features matching @rate-limiting tag.
}
