package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/subscription")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/subscription.html, json:target/cucumber-reports/subscription.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@subscription")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunSubscriptionIT {
}
