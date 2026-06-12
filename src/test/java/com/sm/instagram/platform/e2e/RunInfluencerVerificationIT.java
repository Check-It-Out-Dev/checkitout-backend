package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit 5 Platform Suite runner for Influencer Verification + Password E2E tests.
 *
 * <p>Tests the transactional email verification + password setup flow for influencers:
 * <ul>
 *   <li>Influencer verifies email and sets password in one atomic operation</li>
 *   <li>Company/Admin verification without password (unchanged flow)</li>
 *   <li>Reset and re-verification of the same influencer</li>
 *   <li>Error handling for invalid/expired oobCodes</li>
 * </ul>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/influencer-verification.html, json:target/cucumber-reports/influencer-verification.json")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@influencer-verification")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunInfluencerVerificationIT {
    // Cucumber discovers and runs features matching @influencer-verification tag.
}
