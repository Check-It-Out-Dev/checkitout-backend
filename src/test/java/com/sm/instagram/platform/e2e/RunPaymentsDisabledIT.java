package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * E2E suite for the payments-OFF scenario.
 *
 * <p>Runs in a dedicated Failsafe fork with {@code -Dapp.payments.enabled=false}
 * (see {@code pom.xml} {@code <execution id="payments-off-tests">}). This suite is
 * intentionally separate from {@code RunSubscriptionIT} because the two require
 * incompatible Spring contexts (paid beans loaded vs. not loaded).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/payments_off")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/payments-off.html, json:target/cucumber-reports/payments-off.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@payments-off")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunPaymentsDisabledIT {
}
