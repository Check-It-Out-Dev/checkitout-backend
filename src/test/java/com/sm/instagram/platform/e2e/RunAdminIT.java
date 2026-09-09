package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Cucumber runner for Admin User Management E2E tests.
 *
 * <p>Runs scenarios tagged with @admin in isolated JVM.
 * Admin tests use the real admin account with 2FA verification
 * to operate on existing robot users (company and influencer).
 *
 * <p>Test Configuration:
 * <pre>
 * - Uses real admin: E2E_ADMIN_001 (with 2FA)
 * - Target Company: E2E_COMPANY_001
 * - Target Influencer: E2E_INFLUENCER_001 (norbertmarchewka)
 * </pre>
 *
 * <p>Run with Maven:
 * <pre>
 * mvn verify -Pe2e -Dit.test=RunAdminIT
 * </pre>
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Admin can ban/unban users</li>
 *   <li>Admin can change user status (ACTIVE, INACTIVE, BANNED, IN_VALIDATION)</li>
 *   <li>Admin can view user list and profiles</li>
 *   <li>Banned user behavior (whitelisted endpoints accessible)</li>
 * </ul>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.sm.instagram.platform.e2e")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@admin-ops")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/admin.html, json:target/cucumber-reports/admin.json")
@ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
public class RunAdminIT {
    // This class is intentionally empty.
    // It serves only as a holder for the above annotations.
    // Cucumber will discover and run all features matching the @admin tag.
}
