package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit 5 Platform Suite runner for Notification E2E tests.
 *
 * <p>This suite runs notification scenarios that test:
 * <ul>
 *   <li>Notification CRUD via HTTP endpoints (list, read, mark-as-read, archive)</li>
 *   <li>Email delivery via GreenMail in-memory SMTP server</li>
 *   <li>Cross-user notification isolation</li>
 *   <li>Preference-driven notification gating</li>
 * </ul>
 *
 * <p>Run ONLY notification tests (skip all other suites):
 * <pre>
 * mvn verify -Pe2e -Dskip.normal.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true -Dskip.security.tests=true -Dskip.consolidated.tests=true -Dskip.partnership.tests=true
 * </pre>
 *
 * @see RunPartnershipFlowIT for partnership lifecycle tests
 * @see RunConsolidatedIT for consolidated profile/admin tests
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/notification")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/notification.html, json:target/cucumber-reports/notification.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@notification-e2e")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunNotificationIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run features matching @notification-e2e tag.
}
