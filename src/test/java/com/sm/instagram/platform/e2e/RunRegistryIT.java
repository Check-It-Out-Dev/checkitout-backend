package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Cucumber runner for Registry Module E2E tests.
 *
 * <p>Runs scenarios tagged with @registry in isolated JVM.
 * Tests the full company registry lifecycle: NIP lookup, confirmation,
 * auto-activation, JDG consent flow, and permission enforcement.
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pintegration -DskipTests=true -DskipPmd=true -Dfailsafe.includes=&#42;&#42;/RunRegistryIT.java
 * </pre>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/registry")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@registry")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/registry.html, json:target/cucumber-reports/registry.json")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunRegistryIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run all features matching the @registry tag.
}
