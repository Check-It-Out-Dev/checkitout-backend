# E2E tests — Cucumber suites, actors, and the e2e Spring mode

175 Cucumber scenarios drive the real HTTP API against a fully booted application
(PostgreSQL + Redis via Testcontainers, GreenMail for mail). Every suite runs in its
own JVM fork. This page covers the architecture, how to run any slice of it, and how
to add a new suite. It is written to be executable by a human **or an AI agent** —
that is a design goal of the whole tier: an agent can read a task, run one suite in
isolation, and read the failure logs without touching anything else.

## The three tiers at a glance

| Tier | Naming | Plugin | Profile | What it boots |
|---|---|---|---|---|
| Unit | `*UnitTest.java` / `*Test.java` | Surefire | `test` | Nothing — Mockito/JUnit only |
| Service integration | `*IntegrationTest.java` | Failsafe | `integration` | Full Spring context + Testcontainers (PG, Redis), `@Transactional` rollback |
| E2E | `Run*IT.java` runners | Failsafe | `e2e` | Whole app over HTTP + Firebase test project + GreenMail |

```bash
# unit only
./mvnw test -Dtest=*UnitTest -DskipITs=true -DskipPmd=true

# service integration only
./mvnw clean verify -Pintegration -DskipPmd=true

# everything e2e (10+ minutes, 15 forks)
./mvnw clean verify -Pe2e -DskipPmd=true
```

Tip: kill orphaned JVMs and clean `target/` before big runs; results land in
`target/surefire-reports/` and `target/failsafe-reports/` (plus per-suite Cucumber
HTML/JSON under `target/cucumber-reports/`).

## E2E architecture

### Three actors, three auth flows

| Actor | Role | Auth flow |
|---|---|---|
| Company | COMPANY | sync-user → force-claims → firebase-login → seed-cookie-consent → exchange-token → session cookies |
| Admin | ADMIN + 2FA | Company flow **plus** TOTP verify (Firestore-stored secret, decrypted, retried across window boundaries) |
| Influencer | INFLUENCER | sync-user → simulate-influencer-oauth → session cookies (no password — mirrors OAuth-only login) |

Actors are real accounts in a dedicated **Firebase test project** — the E2E tier
authenticates exactly the way production does, cookies and all. The identities in the
feature files (`e2e-admin@example.test`, …) are placeholders: point them at your own
test project's users (see the root README, *Full mode*).

### Test-only state endpoints (`@Profile("e2e & !prod & !test")`)

The app exposes a state-manipulation API **only** in e2e mode — this is what makes
scenarios deterministic and agent-runnable:

| Controller | Endpoints | Purpose |
|---|---|---|
| `TestAuthController` `/test/auth/*` | sync-user-from-firestore · force-firebase-claims · simulate-influencer-oauth · mock-session · set-account-status · update-firebase-user | Full user-state control across PG + Firebase + Redis + cookies |
| `TestLegalController` `/test/legal/*` | seed-cookie-consent · trigger-enforcement · reset-consents · publish-document-version | Consent/terms state control |

The profile expression guarantees these can never exist in a `prod` or `test` deployment.

### Hooks

| Hook | Trigger | Behavior |
|---|---|---|
| `SoftAssertionHooks` | all scenarios | collects failures, reports them together at scenario end |
| `StepUpHooks` | `@step-up-auth` | clears Redis + GreenMail, restores actors to login-ready state before, resets after |
| `RateLimitingHooks` | `@rate-limiting` | deletes `rate_limit*` Redis keys |

### Multi-actor choreography

Each `Actor` owns an isolated `UserSession` (its own cookies, `buildAuthHeaders()` per
request). A scenario-scoped `ActorRegistry` resolves actors by alias (`get("FashionCo")`,
`switchTo(…)`, `findByRole(…)`); resources created by one actor are stored in a shared
map so another actor can reference them by name. `ScenarioCleanupManager` unwinds
created state LIFO on scenario end. This is how scenarios like *company creates a
campaign → influencer applies → company accepts* read like a screenplay and stay isolated.

## Running one suite in isolation

Each suite has a skip flag; run a single suite by skipping all the others
(~60-90 s instead of 10+ min):

```bash
./mvnw verify -Pe2e -DskipPmd=true \
  -Dskip.normal.tests=true -Dskip.session-expiry.tests=true \
  -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true \
  -Dskip.admin.tests=true -Dskip.security.tests=true \
  -Dskip.consolidated.tests=true -Dskip.consent.tests=true \
  -Dskip.registry.tests=true -Dskip.step-up.tests=true \
  -Dskip.notification.tests=true -Dskip.influencer-verification.tests=true \
  -Dskip.partnership.tests=true -Dskip.subscription.tests=true \
  -Dskip.payments-off.tests=true \
  -DRL_STANDARD_REQUESTS=1000
# ...then DELETE the skip flag of the one suite you want to run.
```

> **Never use `-Dit.test=RunXxxIT`.** It overrides every Failsafe execution's includes,
> so your suite runs once per execution context (6×). Skip flags are the only correct
> isolation mechanism here.

### All suites

| Suite | Runner | Tag | Skip flag |
|---|---|---|---|
| Normal | `RunCucumberIT` | (excludes special tags) | `skip.normal.tests` |
| Session expiry | `RunSessionExpiryIT` | `@session-expiry` | `skip.session-expiry.tests` |
| Multi-user | `RunMultiUserIT` | `@multi-user` | `skip.multi-user.tests` |
| Rate limiting | `RunRateLimitingIT` | `@rate-limiting` | `skip.rate-limiting.tests` |
| Admin ops | `RunAdminIT` | `@admin-ops` | `skip.admin.tests` |
| Security | `RunSecurityIT` | `@security-suite` | `skip.security.tests` |
| Consolidated | `RunConsolidatedIT` | `@consolidated-suite` | `skip.consolidated.tests` |
| Partnership flow | `RunPartnershipFlowIT` | `@partnership-flow` | `skip.partnership.tests` |
| Notifications | `RunNotificationIT` | `@notification-e2e` | `skip.notification.tests` |
| Consent | `RunConsentIT` | `@consent` | `skip.consent.tests` |
| Registry | `RunRegistryIT` | `@registry` | `skip.registry.tests` |
| Step-up auth | `RunStepUpAuthIT` | `@step-up-auth` | `skip.step-up.tests` |
| Influencer verification | `RunInfluencerVerificationIT` | `@influencer-verification` | `skip.influencer-verification.tests` |
| Subscription | `RunSubscriptionIT` | `@subscription` | `skip.subscription.tests` |
| Payments off | `RunPaymentsDisabledIT` | `@payments-off` | `skip.payments-off.tests` |

## Adding a new suite

1. **Pick a tag**, e.g. `@subscription`.
2. **Write the feature** under `src/test/resources/features/<domain>/…`:

```gherkin
@subscription
Feature: Subscription lifecycle
  Background:
    Given "FashionCo" logs in as COMPANY with Firebase UID "…" email "…" password "…"

  Scenario: Company activates free trial
    When "FashionCo" activates the free Enterprise trial
    Then the subscription status should be "TRIAL_ENTERPRISE"
    And the campaign limit should be 10
```

3. **Step definitions** in `src/test/java/com/sm/instagram/platform/e2e/steps/`,
   extending `CucumberSpringConfig`, using the actor's session for HTTP calls.
4. **Runner** in `…/e2e/RunYourThingIT.java`:

```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.filter.tags", value = "@subscription")
@ConfigurationParameter(key = "cucumber.plugin",
    value = "pretty, html:target/cucumber-reports/subscription.html, json:target/cucumber-reports/subscription.json")
public class RunSubscriptionIT {}
```

5. **Wire a skip flag** for the new runner in `pom.xml` (copy an existing Failsafe
   execution block) so the suite participates in the isolation pattern above.

Cucumber's human-language scenarios are the reason this tier doubles as living
documentation: the business flows are readable without opening a single Java file.
