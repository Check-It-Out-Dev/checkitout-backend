# How to Run a Single E2E Suite and Create New Suites

## Neo4j Graph — Test Infrastructure Quick Access

The entire test infrastructure is modeled in Neo4j namespace `CheckItOutSystem`. Use these queries to retrieve critical test info:

### Get test infrastructure overview
```cypher
MATCH (t:EntityNavigator {name: 'TestingLayer', namespace: 'CheckItOutSystem'})-[:IMPLEMENTS]->(c:ConcreteImpl)
RETURN c.name, c.node_subtype, c.ai_description
ORDER BY c.node_subtype, c.name
```

### Get E2E test actors (real Firebase UIDs, auth flows, passwords)
```cypher
MATCH (c:ConcreteImpl {namespace: 'CheckItOutSystem', node_subtype: 'TestActor'})
RETURN c.name, c.role, c.firebase_uid, c.email, c.password, c.auth_flow, c.auth_flow_detail
```

### Get test-only admin endpoints (state manipulation)
```cypher
MATCH (c:ConcreteImpl {namespace: 'CheckItOutSystem', node_subtype: 'TestController'})
RETURN c.name, c.endpoints, c.profile_guard, c.ai_description
```

### Get E2E hooks (pre/post scenario setup)
```cypher
MATCH (c:ConcreteImpl {name: 'E2E_Hooks', namespace: 'CheckItOutSystem'})
RETURN c.pre_hook_detail, c.post_hook_detail, c.defense_layers
```

### Get multi-actor choreography pattern
```cypher
MATCH (c:ConcreteImpl {name: 'ActorRegistry', namespace: 'CheckItOutSystem'})
RETURN c.isolation_mechanism, c.resource_tracking, c.cross_actor_pattern
```

### Get multi-user auth service (login flows per actor type)
```cypher
MATCH (c:ConcreteImpl {name: 'MultiUserAuthService', namespace: 'CheckItOutSystem'})
RETURN c.login_methods, c.retry_logic, c.firebase_rate_limit
```

### Get runner suites with tags and skip flags
```cypher
MATCH (c:ConcreteImpl {name: 'E2E_RunnerSuites', namespace: 'CheckItOutSystem'})
RETURN c.runners, c.skip_flag_pattern, c.critical_rule
```

---

## E2E Test Architecture Summary

### 3 Test Actors (real Firebase accounts)

| Actor | Role | Auth Flow |
|-------|------|-----------|
| **Company** | COMPANY | sync-user → force-claims → firebase-login → seed-cookie-consent → exchange-token → session cookies |
| **Admin** | ADMIN + 2FA | Same as Company + TOTP verify (Firestore secret, KMS decrypt, retry on window boundary) |
| **Influencer** | INFLUENCER | sync-user → simulate-influencer-oauth → session cookies (no password) |

### Test-Only Admin Endpoints (`@Profile("e2e & !prod & !test")`)

| Controller | Key Endpoints | Purpose |
|------------|--------------|---------|
| **TestAuthController** `/test/auth/*` | sync-user-from-firestore, force-firebase-claims, simulate-influencer-oauth, mock-session, set-account-status, update-firebase-user | Full user state manipulation (PG + Firebase + Redis + Cookies) |
| **TestLegalController** `/test/legal/*` | seed-cookie-consent, trigger-enforcement, reset-consents, publish-document-version | Legal consent state manipulation |

### Hooks (pre/post scenario)

| Hook | When | What |
|------|------|------|
| **SoftAssertionHooks** | All scenarios | `@Before`: reset failure list. `@After`: report collected failures, fail if any. |
| **StepUpHooks** | `@step-up-auth` only | `@Before`: clear Redis + GreenMail, restore users to login-ready. `@After`: reset to raw defaults. 3-layer defense-in-depth. |
| **RateLimitingHooks** | `@rate-limiting` only | `@Before`: delete `rate_limit*` Redis keys. |

### Multi-Actor Choreography Pattern

1. Each `Actor` holds isolated `UserSession` with own cookies (`buildAuthHeaders()` per request)
2. `ActorRegistry` (@ScenarioScope) manages actors by alias: `get("FashionCo")`, `switchTo()`, `findByRole()`
3. Cross-actor resource referencing: Actor A creates resource → step stores in shared map → Actor B references by name
4. `ScenarioCleanupManager`: LIFO stack, `@PreDestroy` cleanup in reverse order
5. `TestRestTemplate` is shared but stateless — session isolation is per-Actor cookies

---

## Running a Single Suite in Isolation

Each E2E suite runs in its own JVM fork. To run **only one suite**, skip all the others via `-Dskip.X.tests=true` flags.

### Pattern: Skip everything except your target suite

```bash
mvn verify -Pe2e -DskipPmd=true \
  -Dskip.normal.tests=true \
  -Dskip.session-expiry.tests=true \
  -Dskip.multi-user.tests=true \
  -Dskip.rate-limiting.tests=true \
  -Dskip.admin.tests=true \
  -Dskip.security.tests=true \
  -Dskip.consolidated.tests=true \
  -Dskip.consent.tests=true \
  -Dskip.registry.tests=true \
  -Dskip.step-up.tests=true \
  -Dskip.notification.tests=true \
  -Dskip.influencer-verification.tests=true \
  -Dskip.partnership.tests=true \
  -DRL_STANDARD_REQUESTS=1000
```

This runs **zero** suites (all skipped). To run one, remove its skip flag. For example, to run **only partnership**:

```bash
# Remove -Dskip.partnership.tests=true from the list above
mvn verify -Pe2e -DskipPmd=true \
  -Dskip.normal.tests=true \
  -Dskip.session-expiry.tests=true \
  -Dskip.multi-user.tests=true \
  -Dskip.rate-limiting.tests=true \
  -Dskip.admin.tests=true \
  -Dskip.security.tests=true \
  -Dskip.consolidated.tests=true \
  -Dskip.consent.tests=true \
  -Dskip.registry.tests=true \
  -Dskip.step-up.tests=true \
  -Dskip.notification.tests=true \
  -Dskip.influencer-verification.tests=true \
  -DRL_STANDARD_REQUESTS=1000
```

**Execution time**: ~60-90 seconds for a single suite (1 JVM fork) vs 10+ minutes for all 13.

### CRITICAL: Never use `-Dit.test=RunXxxIT`

This overrides ALL Failsafe execution includes, causing your suite to run 6x in every execution context. Always use skip flags.

---

## All Available Suites

| Suite | Runner Class | Cucumber Tag | Skip Flag |
|-------|-------------|--------------|-----------|
| Normal | `RunCucumberIT` | excludes special tags | `skip.normal.tests` |
| Session Expiry | `RunSessionExpiryIT` | `@session-expiry` | `skip.session-expiry.tests` |
| Multi-User | `RunMultiUserIT` | `@multi-user` | `skip.multi-user.tests` |
| Rate Limiting | `RunRateLimitingIT` | `@rate-limiting` | `skip.rate-limiting.tests` |
| Admin | `RunAdminIT` | `@admin-ops` | `skip.admin.tests` |
| Security | `RunSecurityIT` | `@security-suite` | `skip.security.tests` |
| Consolidated | `RunConsolidatedIT` | `@consolidated-suite` | `skip.consolidated.tests` |
| Partnership | `RunPartnershipFlowIT` | `@partnership-flow` | `skip.partnership.tests` |
| Notification | `RunNotificationIT` | `@notification-e2e` | `skip.notification.tests` |
| Consent | `RunConsentIT` | `@consent` | `skip.consent.tests` |
| Registry | `RunRegistryIT` | `@registry` | `skip.registry.tests` |
| Step-Up Auth | `RunStepUpAuthIT` | `@step-up-auth` | `skip.step-up.tests` |
| Influencer Verification | `RunInfluencerVerificationIT` | `@influencer-verification` | `skip.influencer-verification.tests` |
| Subscription | `RunSubscriptionIT` | `@subscription` | `skip.subscription.tests` |
| Payments Off | `RunPaymentsDisabledIT` | `@payments-off` | `skip.payments-off.tests` |

---

## How to Create a New E2E Suite

### Step 1: Create a Cucumber tag

Choose a unique tag for your feature, e.g., `@subscription`.

### Step 2: Create the feature file

Location: `src/test/resources/features/subscription/subscription-flow.feature`

```gherkin
@subscription
Feature: Subscription lifecycle

  Background:
    Given "FashionCo" logs in as COMPANY with Firebase UID "..." email "..." password "..."

  Scenario: Company activates free trial
    When "FashionCo" activates the free Enterprise trial
    Then the subscription status should be "TRIAL_ENTERPRISE"
    And the campaign limit should be 10
```

### Step 3: Create step definitions

Location: `src/test/java/com/sm/instagram/platform/e2e/steps/SubscriptionSteps.java`

```java
package com.sm.instagram.platform.e2e.steps;

import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import io.cucumber.java.en.*;
// ...

public class SubscriptionSteps extends CucumberSpringConfig {

    @When("{string} activates the free Enterprise trial")
    public void activateTrial(String actorName) {
        // POST /subscription/trial/activate with actor's session cookies
    }

    @Then("the subscription status should be {string}")
    public void verifyStatus(String expectedStatus) {
        // GET /subscription/status, assert status field
    }
}
```

### Step 4: Create the runner class

Location: `src/test/java/com/sm/instagram/platform/e2e/RunSubscriptionIT.java`

```java
package com.sm.instagram.platform.e2e;

import org.junit.platform.suite.api.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.filter.tags", value = "@subscription")
@ConfigurationParameter(key = "cucumber.plugin", value = "pretty, html:target/cucumber-reports/subscription.html, json:target/cucumber-reports/subscription.json")
public class RunSubscriptionIT {
}
```

### Step 5: Add skip flag property to `pom.xml`

In `<properties>` section (~line 42):

```xml
<skip.subscription.tests>false</skip.subscription.tests>
```

### Step 6: Add Failsafe execution to `pom.xml`

In the `e2e` profile, add a new execution block after the last existing one:

```xml
<execution>
    <id>subscription-tests</id>
    <goals><goal>integration-test</goal></goals>
    <configuration>
        <skip>${skip.subscription.tests}</skip>
        <includes>
            <include>**/RunSubscriptionIT.java</include>
        </includes>
        <reportsDirectory>${project.build.directory}/failsafe-reports/subscription</reportsDirectory>
        <forkCount>1</forkCount>
        <reuseForks>false</reuseForks>
        <argLine>
            @{argLine}
            -Dfile.encoding=UTF-8
            -Duser.timezone=UTC
            -DSESSION_DURATION_MINUTES=10080
            -DPARTIAL_SESSION_DURATION_MINUTES=10
            -DRL_STANDARD_REQUESTS=${RL_STANDARD_REQUESTS}
            -DRL_STANDARD_WINDOW=60
            -DRL_STANDARD_BLOCK=10
            -Xmx2048m
        </argLine>
    </configuration>
</execution>
```

### Step 7: Exclude the tag from `RunCucumberIT`

In `RunCucumberIT.java`, add `and not @subscription` to the tag filter:

```java
@ConfigurationParameter(key = "cucumber.filter.tags",
    value = "not @wip and not @session-expiry and not @multi-user ... and not @subscription")
```

### Step 8: Run your new suite in isolation

```bash
# Run ONLY subscription E2E suite
mvn verify -Pe2e -DskipPmd=true \
  -Dskip.normal.tests=true \
  -Dskip.session-expiry.tests=true \
  -Dskip.multi-user.tests=true \
  -Dskip.rate-limiting.tests=true \
  -Dskip.admin.tests=true \
  -Dskip.security.tests=true \
  -Dskip.consolidated.tests=true \
  -Dskip.consent.tests=true \
  -Dskip.registry.tests=true \
  -Dskip.step-up.tests=true \
  -Dskip.notification.tests=true \
  -Dskip.influencer-verification.tests=true \
  -Dskip.partnership.tests=true \
  -DRL_STANDARD_REQUESTS=1000
```

### Step 9: Add to the suites table in `E2E-TEST-SUITES-GUIDE.md`

| Subscription | `RunSubscriptionIT` | `@subscription` | `skip.subscription.tests` |

---

## Checklist for New Suite

- [ ] Feature file with `@tag` in `src/test/resources/features/`
- [ ] Step definitions extending `CucumberSpringConfig` in `src/test/java/.../e2e/steps/`
- [ ] Runner class `Run{Name}IT.java` in `src/test/java/.../e2e/`
- [ ] Skip property `skip.{name}.tests` in pom.xml `<properties>`
- [ ] Failsafe `<execution>` block in pom.xml e2e profile
- [ ] Tag excluded from `RunCucumberIT.java` tag filter
- [ ] Entry in `E2E-TEST-SUITES-GUIDE.md` suites table
- [ ] Tested in isolation with skip flags
