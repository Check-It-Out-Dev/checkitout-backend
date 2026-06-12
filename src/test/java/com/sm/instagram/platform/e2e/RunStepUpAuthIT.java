package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit 5 Platform Suite runner for Step-Up Authentication E2E tests.
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pe2e -DskipPmd=true -DreuseForks=true -Dcucumber.filter.tags=@step-up-auth -DRL_STANDARD_REQUESTS=10000
 * </pre>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/step-up-auth.html, json:target/cucumber-reports/step-up-auth.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@step-up-auth")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunStepUpAuthIT {
}
