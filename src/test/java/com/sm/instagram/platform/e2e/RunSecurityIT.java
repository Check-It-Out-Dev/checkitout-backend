package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit 5 Platform Suite runner for Security E2E tests.
 *
 * <p>This suite validates:
 * <ul>
 *   <li>401 Unauthorized - All protected endpoints reject unauthenticated requests</li>
 *   <li>403 Forbidden - Role-restricted endpoints reject unauthorized roles (e.g., INFLUENCER accessing ADMIN endpoints)</li>
 * </ul>
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Does NOT invoke Firebase Auth for 401 tests - no rate limiting concerns</li>
 *   <li>Uses OAuth multi-user scheme for 403 tests (INFLUENCER role)</li>
 *   <li>Tests security layer independently of business logic</li>
 *   <li>Uses parameterized scenarios for comprehensive endpoint coverage</li>
 *   <li>Fast execution - simple 401/403 checks, no complex setup</li>
 * </ul>
 *
 * <p><b>Run ONLY security tests (recommended):</b>
 * <pre>
 * mvn verify -Pe2e -Dskip.normal.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true
 * </pre>
 *
 * <p><b>WARNING:</b> Do NOT use {@code -Dit.test=RunSecurityIT} - it overrides ALL suite includes
 * and causes security tests to run in ALL execution contexts (including rate-limiting with LOW limits).
 *
 * <p>Run specific endpoint category:
 * <pre>
 * mvn verify -Pe2e -Dcucumber.filter.tags="@security-suite and @user-endpoints"
 * mvn verify -Pe2e -Dcucumber.filter.tags="@security-suite and @admin-endpoints"
 * mvn verify -Pe2e -Dcucumber.filter.tags="@403-influencer-forbidden"
 * </pre>
 *
 * @see RunCucumberIT for normal E2E tests (excludes @security-suite)
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/security.html, json:target/cucumber-reports/security.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@security-suite")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunSecurityIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run all features matching the @security-suite tag.
}
