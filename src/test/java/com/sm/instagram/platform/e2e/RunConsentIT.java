package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Cucumber runner for Consent Module E2E tests.
 *
 * <p>Runs scenarios tagged with @consent in isolated JVM.
 * Tests the full consent lifecycle: anonymous consent, registration with
 * consent cookies, consent enforcement, re-consent flow, and admin endpoints.
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pe2e -Dit.test=RunConsentIT
 * </pre>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@consent or @consent-lifecycle")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/consent.html, json:target/cucumber-reports/consent.json")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunConsentIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run all features matching the @consent tag.
}
