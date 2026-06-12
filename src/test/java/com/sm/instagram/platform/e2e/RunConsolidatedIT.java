package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit 5 Platform Suite runner for Consolidated E2E tests.
 *
 * <p>This suite runs consolidated test scenarios that:
 * <ul>
 *   <li>Use soft assertions for comprehensive validation</li>
 *   <li>Minimize Firebase logins by consolidating multiple tests per scenario</li>
 *   <li>Test profile changes, file uploads, and admin status flows</li>
 * </ul>
 *
 * <p>Included feature files:
 * <ul>
 *   <li>admin-inactive-flow-consolidated.feature - Admin INACTIVE/ACTIVE/BAN flows</li>
 *   <li>profile-non-critical-consolidated.feature - Non-critical profile field changes</li>
 *   <li>profile-critical-consolidated.feature - Critical field changes + token invalidation</li>
 *   <li>file-upload-signed-url.feature - Firebase Storage signed URL uploads</li>
 *   <li>validation-edge-cases.feature - Field validation boundary tests</li>
 * </ul>
 *
 * <p>Firebase Login Budget: ~13-15 logins total across all consolidated scenarios
 *
 * <p><b>Run ALL consolidated tests:</b>
 * <pre>
 * mvn verify -Pe2e -Dskip.normal.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true -Dskip.security.tests=true
 * </pre>
 *
 * <p><b>Run specific consolidated test by tag:</b>
 * <pre>
 * mvn verify -Pe2e -Dcucumber.filter.tags="@consolidated-suite and @admin-inactive-flow" ...skip flags...
 * mvn verify -Pe2e -Dcucumber.filter.tags="@consolidated-suite and @profile-non-critical" ...skip flags...
 * mvn verify -Pe2e -Dcucumber.filter.tags="@consolidated-suite and @file-upload" ...skip flags...
 * </pre>
 *
 * @see RunCucumberIT for normal E2E tests (excludes @consolidated-suite)
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/consolidated.html, json:target/cucumber-reports/consolidated.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@consolidated-suite")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunConsolidatedIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run all features matching the @consolidated-suite tag.
}
