# Test Suites - Claude Code Execution Guide

> **AI-First Guide**: This document defines how Claude Code should execute tests via the Bash tool on Windows.

---

## Test Types Overview

| Type | Naming Pattern | Plugin | Profile | Coverage File |
|------|----------------|--------|---------|---------------|
| **Unit Tests** | `*Test.java` | Surefire | `test` | `jacoco-unit.exec` |
| **Service Integration** | `*IntegrationTest.java` | Failsafe | `integration` | `jacoco-integration.exec` |
| **E2E/Cucumber** | `*IT.java` | Failsafe | `e2e` | `jacoco-e2e.exec` |

---

## Claude Code Bash Tool Patterns

### Critical: Windows Bash Execution

Claude Code runs in a bash environment on Windows. Maven commands require `cmd //c ""` wrapper for proper stdout capture.

**Pattern**: `cmd //c "mvn <command>"` with `2>&1` for stderr capture.

---

## Step 1: Clean Environment

Always kill orphaned Java processes before running tests.

```
Bash(
  command: "taskkill //F //IM java.exe 2>nul; rm -rf target 2>nul; echo 'Cleaned'",
  description: "Kill orphaned Java processes and clean target"
)
```

---

## Unit Tests (No Spring Context, No Database)

Unit tests are located in `src/test/java/com/sm/instagram/platform/unit/` and use:
- `@ExtendWith(MockitoExtension.class)` for service tests
- Plain JUnit 5 for utility/entity tests
- **NO Spring Boot context or database connection**

### Run All Unit Tests

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn test -Dtest=*UnitTest -DskipITs=true -DskipPmd=true\" 2>&1",
  description: "Run all unit tests (no Spring context)",
  timeout: 300000
)
```

### Run Unit Tests with Output to File

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn test -Dtest=*UnitTest -DskipITs=true -DskipPmd=true\" > UnitTestLogs.txt 2>&1",
  description: "Run unit tests with output redirected to file",
  timeout: 300000,
  run_in_background: true
)
```

### Check Unit Test Results

```
Bash(
  command: "grep -h \"Tests run:\" /path/to/checkitout-backend/target/surefire-reports/*.txt | awk -F'[,:]' '{tests+=$2; failures+=$4; errors+=$6; skipped+=$8} END {print \"Total Tests: \" tests \", Failures: \" failures \", Errors: \" errors \", Skipped: \" skipped}'",
  description: "Calculate total unit test results"
)
```

**Expected:** ~9,500+ tests, 0 failures, 0 errors

---

## Service Integration Tests (Full Spring Context, No HTTP)

Service Integration Tests are located in `src/test/java/com/sm/instagram/platform/integration/` and use:
- Full Spring Boot context with `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- TestContainers for PostgreSQL and Redis
- Programmatic `SecurityContext` setup (no real Firebase auth)
- `@Transactional` for automatic rollback after each test
- **Naming Pattern**: `*IntegrationTest.java` (NOT `*IT.java`)

### Run All Service Integration Tests

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn clean verify -Pintegration -DskipPmd=true\" 2>&1",
  description: "Run all service integration tests",
  timeout: 300000
)
```

### Run Service Integration Tests with Output to File

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn clean verify -Pintegration -DskipPmd=true\" > IntegrationTestLogs.txt 2>&1",
  description: "Run service integration tests with output redirected to file",
  timeout: 300000,
  run_in_background: true
)
```

### Check Service Integration Test Results

```
Bash(
  command: "cat /path/to/checkitout-backend/target/failsafe-reports/failsafe-summary.xml",
  description: "View integration test results summary"
)
```

### When to Use Service Integration Tests

Use `*IntegrationTest.java` when:
- Testing services with complex dependencies (10+ injected dependencies)
- Testing services that use `getSelf()` pattern (requires Spring proxy)
- Testing transactional behavior across multiple repositories
- Testing authorization logic with programmatic `SecurityContext`

**Example Test Location**: `src/test/java/com/sm/instagram/platform/integration/service/PartnershipOpportunityServiceIntegrationTest.java`

---

## Step 2: Run E2E Tests

### Run Admin Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.security.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -DRL_STANDARD_REQUESTS=1000\" 2>&1",
  description: "Run Admin E2E tests",
  timeout: 600000
)
```

### Run Security Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true -DRL_STANDARD_REQUESTS=1000\" 2>&1",
  description: "Run Security E2E tests",
  timeout: 600000
)
```

### Run Normal Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.security.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true -DRL_STANDARD_REQUESTS=1000\" 2>&1",
  description: "Run Normal E2E tests",
  timeout: 600000
)
```

### Run Rate Limiting Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.security.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.admin.tests=true\" 2>&1",
  description: "Run Rate Limiting E2E tests",
  timeout: 600000
)
```

### Run Session Expiry Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.security.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true\" 2>&1",
  description: "Run Session Expiry E2E tests",
  timeout: 600000
)
```

### Run Multi-User Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.security.tests=true -Dskip.session-expiry.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true\" 2>&1",
  description: "Run Multi-User E2E tests",
  timeout: 600000
)
```

### Run Consent Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.security.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true -Dskip.consolidated.tests=true -Dskip.partnership.tests=true -Dskip.notification.tests=true -DRL_STANDARD_REQUESTS=1000\" 2>&1",
  description: "Run Consent E2E tests",
  timeout: 600000
)
```

### Run Registry Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.security.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true -Dskip.consolidated.tests=true -Dskip.partnership.tests=true -Dskip.notification.tests=true -Dskip.consent.tests=true -DRL_STANDARD_REQUESTS=1000\" 2>&1",
  description: "Run Registry E2E tests",
  timeout: 600000
)
```

### Run Partnership Flow Tests Only

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.security.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true -Dskip.consolidated.tests=true\" 2>&1",
  description: "Run Partnership Flow E2E tests",
  timeout: 600000
)
```

### Run ALL Suites

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -DRL_STANDARD_REQUESTS=1000\" 2>&1",
  description: "Run ALL E2E test suites",
  timeout: 600000
)
```

### Run by Cucumber Tag

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -Dcucumber.filter.tags=\\\"@smoke\\\"\" 2>&1",
  description: "Run E2E tests by Cucumber tag",
  timeout: 600000
)
```

---

## Available Test Suites

| Suite | Runner Class | Cucumber Tag | Skip Property |
|-------|--------------|--------------|---------------|
| Normal | `RunCucumberIT` | excludes special tags | `skip.normal.tests` |
| Session Expiry | `RunSessionExpiryIT` | `@session-expiry` | `skip.session-expiry.tests` |
| Multi-User | `RunMultiUserIT` | `@multi-user` | `skip.multi-user.tests` |
| Rate Limiting | `RunRateLimitingIT` | `@rate-limiting` | `skip.rate-limiting.tests` |
| Admin | `RunAdminIT` | `@admin-ops` | `skip.admin.tests` |
| Security | `RunSecurityIT` | `@security-suite` | `skip.security.tests` |
| Consolidated | `RunConsolidatedIT` | `@consolidated-suite` | `skip.consolidated.tests` |
| Partnership | `RunPartnershipFlowIT` | `@partnership-flow` | `skip.partnership.tests` |
| Notification | `RunNotificationIT` | `@notification` | `skip.notification.tests` |
| Consent | `RunConsentIT` | `@consent` | `skip.consent.tests` |
| Registry | `RunRegistryIT` | `@registry` | `skip.registry.tests` |
| Step-Up Auth | `RunStepUpAuthIT` | `@step-up-auth` | `skip.step-up.tests` |
| Subscription | `RunSubscriptionIT` | `@subscription` | `skip.subscription.tests` |
| Payments Off | `RunPaymentsDisabledIT` | `@payments-off` | `skip.payments-off.tests` (runs with `-Dapp.payments.enabled=false`) |

---

## Key Parameters

| Parameter | Purpose | Example |
|-----------|---------|---------|
| `-DskipPmd=true` | Skip PMD analysis | **Recommended** - cleaner output |
| `-Pe2e` | Activate E2E profile | Required for all E2E runs |
| `-Dskip.X.tests=true` | Skip specific suite | See table above |
| `-DRL_STANDARD_REQUESTS=1000` | High rate limit (avoid 429) | Use for non-rate-limit tests |
| `-Dcucumber.filter.tags="@tag"` | Run specific tags | `"@smoke"`, `"@admin-ops"` |
| `timeout: 600000` | 10 minute timeout | Required for E2E tests |

---

## NEVER Use `-Dit.test=`

```
# WRONG - runs test 6x in all execution contexts
cmd //c "mvn verify -Pe2e -Dit.test=RunSecurityIT"

# CORRECT - use skip flags
cmd //c "mvn verify -Pe2e -DskipPmd=true -Dskip.normal.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true"
```

---

## Check Results

```
Bash(
  command: "cat /path/to/checkitout-backend/target/failsafe-reports/failsafe-summary.xml",
  description: "View test results summary"
)
```

---

## Reports Location

| Report | Path |
|--------|------|
| Cucumber HTML | `target/cucumber-reports/{suite}.html` |
| Cucumber JSON | `target/cucumber-reports/{suite}.json` |
| Failsafe Summary | `target/failsafe-reports/failsafe-summary.xml` |

---

## Live-Stack Manual E2E (Chrome + GreenMail) — Runbook

The Cucumber suites above test the BE against itself. This runbook is the
complementary tier: a **real running stack** (BE + greenfield FE + Chrome)
for manually / agent-driven verification of email-gated and auth-gated flows
— the tier used for the support-ticket magic-link verification (2026-06-13,
see `docs/features/SUPPORT-TICKET-MAGIC-LINK.md`).

### Topology

| Process | Port | How |
|---|---|---|
| BE (Spring Boot) | `https://localhost:8080` | profiles **`e2e,dev,ssl`** → DB `checkitout_e2e`, GreenMail + test controllers active |
| GreenMail SMTP | `localhost:3025` | **in-process** inside the BE (`dev/GreenMailConfig.java`); captures every outgoing email; no external mail catcher needed |
| Greenfield FE | `https://localhost:4201` | `npm start` in `checkitout-frontend` (**Node ≥22.22.3 or 24.x** — the Angular 22 dev-server needs `tls.getCACertificates`; see the FE README toolchain notes) |
| PostgreSQL | `localhost:5432` | docker `instagram-postgres` (`docker compose -f docker-compose-dev-redis.yml up -d postgres redis` from the FE's `stack:deps`) |
| Redis | `localhost:6379` | docker `instagram-redis` |

Boot order: docker deps → BE → FE. The FE repo also carries
`scripts/start-be.js` + pm2 `stack:up` for the multi-process variant.

### Test-only controllers (profile-guarded, 404 in prod)

**Inbox — `TestEmailController`** (`@Profile("(e2e | dev) & !prod & !test")`), base `/api/test/email`:

| Call | Purpose |
|---|---|
| `GET /api/test/email?to=<addr>` | list captured emails, newest first, optional recipient filter |
| `GET /api/test/email/latest?to=<addr>` | newest match or 404 |
| `DELETE /api/test/email` | purge the inbox — **call before each scenario** so a stale email can't false-positive a link/code regex |
| `POST /api/test/email/flush` | synchronously run the 15-min notification email cron (`EmailCronJob.processEmailQueue`) so queued notification emails land now |

Note: transactional support-ticket emails send immediately; only
*notification* emails sit in the cron queue and need `/flush`.

**Actors — `TestAuthController`** (`@Profile("e2e & !prod & !test")`), base `/api/test/auth`:

```
POST /api/test/auth/mock-session
{ "email": "e2e.admin@test.com", "role": "ADMIN", "partial": false,
  "firebaseUid": "<uid>", "setupCompleted": true }
```

Sets real `session` + `session_sig` cookies — the browser is then
authenticated as that actor with no Firebase involved. Default actor emails
live in `application-e2e.yml` (`e2e.admin@test.com`, `e2e.company@test.com`,
`e2e.influencer@test.com`). Drive it from the FE origin (or with cookies
scoped to `localhost`) so the FE picks the session up.

### Magic-link verification matrix (the scripted flow)

1. `DELETE /api/test/email` — clean inbox.
2. **Anon create**: FE `/support/tickets/create` → submit with a test email.
3. `GET /api/test/email/latest?to=<that email>` → regex the body for
   `/support/tickets/status?ref=…&token=…`.
4. **One-click open**: navigate the link → ticket auto-opens, no form.
5. **Negative**: tamper one char of `token=` → 401 → page falls back to the
   ref+email lookup form with an error (no redirect loop, no session-refresh
   attempt — `/support/ticket/access` is interceptor-skip-listed in the FE).
6. **Anon reply** from the status page → reply appears; form resets clean.
7. **Admin**: `mock-session` as ADMIN → `/support/admin/tickets` → open the
   ticket → respond with `sendEmail=true` + a status transition.
8. `GET /api/test/email/latest` again → the response email carries a **fresh**
   token link → open it → updated ticket auto-opens.
9. **Logged-in user**: `mock-session` as COMPANY/INFLUENCER → create from the
   authed shell → `/support/tickets/my-tickets` lists it.
10. Console + network sweep in Chrome DevTools (no errors, no CSP violations).
11. **Rate-limit burst LAST**: hammer `GET /api/support/ticket/access?token=x`
    and watch the `X-RateLimit-*` headers count down. Last because the per-IP
    bucket then throttles you. **Profile note:** the `e2e` profile raises the
    standard bucket to 10 000 (so Cucumber suites never false-positive on
    429) — under this stack you verify the limiter is *counting*
    (`X-RateLimit-Remaining` decrements), not that it trips. The 429 path
    itself is covered by `RunRateLimitingIT` and the tightened dev-profile
    limits.

### ⚠ Conflicts with `mvn verify -Pe2e`

The live BE and the Cucumber e2e suite **cannot run simultaneously** — they
collide on port 8080, GreenMail port 3025, and the `checkitout_e2e`
database. Stop the live BE (and free 8080/3025) before any `-Pe2e` run,
then reboot the stack for manual verification afterwards.

---

## Appendix: Running Isolated Tests with JVM Reuse

When running a few tests in isolation, use these options to avoid spawning a new Spring context per scenario.

### Quick Reference

| Goal | Add This Option |
|------|-----------------|
| Reuse JVM between tests | `-DreuseForks=true` |
| High upload rate limits | `-Drate-limit.upload.per-hour=10000 -Drate-limit.upload.per-day=50000` |
| High controller rate limits | `-DRL_STANDARD_REQUESTS=10000` |

### Running Single Feature File

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -DreuseForks=true -Dcucumber.features=src/test/resources/features/files/file-upload-signed-url.feature -DRL_STANDARD_REQUESTS=10000 -Drate-limit.upload.per-hour=10000 -Drate-limit.upload.per-day=50000\"",
  description: "Run single feature with JVM reuse",
  timeout: 600000
)
```

### Running by Tag with JVM Reuse

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -DreuseForks=true -Dcucumber.filter.tags=@file-upload -DRL_STANDARD_REQUESTS=10000 -Drate-limit.upload.per-hour=10000 -Drate-limit.upload.per-day=50000\"",
  description: "Run tagged tests with JVM reuse",
  timeout: 600000
)
```

### Full Options for Fast Isolated Test Runs

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipPmd=true -DreuseForks=true -Dcucumber.filter.tags=@your-tag -DRL_STANDARD_REQUESTS=10000 -DRL_STRICT_REQUESTS=10000 -DRL_AUTH_REQUESTS=10000 -Drate-limit.upload.per-hour=10000 -Drate-limit.upload.per-day=50000 -Drate-limit.upload.global-per-minute=1000\"",
  description: "Run isolated tests with all rate limits disabled and JVM reuse",
  timeout: 600000
)
```

### Why This Matters

| Without JVM Reuse | With JVM Reuse |
|-------------------|----------------|
| New JVM per suite | Single JVM for all |
| Containers restart | Containers reused |
| Spring context restarts | Spring context reused |
| ~2-3 min per suite startup | ~2-3 min total startup |

### Upload-Specific Rate Limit Options

The `/upload/signed-url` endpoint has separate rate limits from controller-level `RL_*` options:

| Option | Default | Recommended for Tests |
|--------|---------|----------------------|
| `-Drate-limit.upload.per-hour` | 10 | 10000 |
| `-Drate-limit.upload.per-day` | 50 | 50000 |
| `-Drate-limit.upload.global-per-minute` | 100 | 1000 |

---

## JaCoCo Code Coverage

### Coverage Files

Each test type generates its own JaCoCo coverage file:

| Test Type | Coverage File | Report Location |
|-----------|---------------|-----------------|
| Unit Tests | `target/jacoco-unit.exec` | `target/site/jacoco-unit/` |
| Service Integration | `target/jacoco-integration.exec` | `target/site/jacoco-integration/` |
| E2E/Cucumber | `target/jacoco-e2e.exec` | `target/site/jacoco-e2e/` |
| **Merged (All)** | `target/jacoco-merged.exec` | `target/site/jacoco-merged/` |

### Run All Tests with Combined Coverage

To get coverage from all 3 test types merged into a single report:

```
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn clean verify -Ptest,integration,e2e -DskipPmd=true\" 2>&1",
  description: "Run all test types and generate merged coverage report",
  timeout: 1200000
)
```

### Run Tests Individually (Coverage Accumulates)

```
# Step 1: Unit tests → jacoco-unit.exec
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn clean test -Ptest -DskipPmd=true\" 2>&1",
  description: "Run unit tests",
  timeout: 300000
)

# Step 2: Service Integration tests → jacoco-integration.exec
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pintegration -DskipTests=true -DskipPmd=true\" 2>&1",
  description: "Run service integration tests",
  timeout: 300000
)

# Step 3: E2E tests → jacoco-e2e.exec
Bash(
  command: "cd /path/to/checkitout-backend && cmd //c \"mvn verify -Pe2e -DskipTests=true -DskipPmd=true\" 2>&1",
  description: "Run E2E tests",
  timeout: 600000
)
```

### View Coverage Reports

```
Bash(
  command: "start /path/to/checkitout-backend/target/site/jacoco-merged/index.html",
  description: "Open merged coverage report in browser"
)
```

### Coverage Report Contents

Each report shows:
- **Line Coverage**: Percentage of lines executed
- **Branch Coverage**: Percentage of conditional branches executed
- **Method Coverage**: Percentage of methods called
- **Class Coverage**: Percentage of classes loaded

The merged report shows the **combined contribution** from all test types, helping identify:
- Which areas are covered by unit tests only
- Which areas need integration test coverage
- Which areas are only covered by E2E tests

### Quick Coverage Summary

```
Bash(
  command: "grep -o 'Total[^<]*' /path/to/checkitout-backend/target/site/jacoco-merged/index.html | head -5",
  description: "Quick coverage summary from merged report"
)
```

---

## Test Run Results — 2026-05-31

Full sequential run of all three tiers (BE tip `6d21b206`). Raw logs: `docs/Tests/test-run-2026-05-31/{unit,integration,e2e}.log`. Commands: `mvn` directly from bash (NOT `cmd //c` — MSYS mangles it). Redis (WSL) + Neo4j left running; BE/FE killed for clean test JVMs; Docker up (Testcontainers).

### Tier summary

| Tier | Command | Result | Pass | Fail | Skip |
|------|---------|--------|------|------|------|
| **Unit** | `mvn clean test -Ptest -DskipPmd=true` | ✅ BUILD SUCCESS | **10,214** | 0 | 0 |
| **Integration** | `mvn verify -Pintegration -DskipPmd=true` | ⚠️ 3 fail (all 1 env-artifact class) | 872 | 3 | 0 |
| **E2E** | `mvn verify -Pe2e -DskipPmd=true -DRL_STANDARD_REQUESTS=1000` | ❌ BUILD FAILURE | — | **7** | — |

### Integration — the 3 failures are environmental (NOT a code regression)

All 3 are `FakturowniaAdapter_IntegrationTest$CreateInvoice` (`shouldCreateRealInvoice`, `shouldCreateEnterpriseInvoice`, `shouldPreventDuplicateViaOid`). They call the **live Fakturownia "test department"** API, which returned **HTTP 422: `"Prosimy o wcześniejsze opłacenie planu Standard"`** ("pay for the Standard plan first") — the test account's billing plan has lapsed. The adapter handles the 422 correctly (`success=false`, logged). **Code is fine.**
- **Recommendation:** gate this class behind `-Dfakturownia.live.test=true` (or `@EnabledIfSystemProperty`) like its sibling Fakturownia tests, so `mvn verify -Pintegration` is green by default. (Not auto-applied — test-strategy decision.)

### E2E — per-suite

| Suite | Executed | Fail | Status |
|-------|----------|------|--------|
| RunCucumberIT (Normal) | 36 | 0 | ✅ |
| RunSessionExpiryIT | 1 | 0 | ✅ |
| RunMultiUserIT | 6 | 0 | ✅ |
| RunRateLimitingIT | 4 | 0 | ✅ |
| RunAdminIT | 12 | 0 | ✅ |
| **RunSecurityIT** | 126 | **4** | ❌ |
| **RunConsolidatedIT** | 9 | **1** (scenario, 5 soft-assertions) | ❌ |
| RunPartnershipFlowIT | 3 | 0 | ✅ |
| RunNotificationIT | 3 | 0 | ✅ |
| RunConsentIT | 35 | 0 | ✅ |
| RunRegistryIT | 18 | 0 | ✅ |
| RunStepUpAuthIT | 9 | 0 | ✅ |
| RunInfluencerVerificationIT | 5 | 0 | ✅ |
| **RunSubscriptionIT** | 7 | **2** | ❌ |
| RunPaymentsDisabledIT | 3 | 0 | ✅ |

### E2E failure analysis (evidence-tiered)

**Cluster A — `company1` 401 cascade (Security ×4 + Consolidated ×3 soft-assert) — SUSPECTED, needs investigation:**
- company1 `firebase/login` succeeds (`requires_2fa=false`, AUTH_SUCCESS, role=COMPANY) but the **token-exchange step logs `requires2FA=true`** → company1 gets a *partial* session → protected calls (`/users/me`, `/partnership-opportunity/paged`) return **401** (tests expect 200/403).
- Candidate causes (unconfirmed): (a) `exchange-token` incorrectly flagging COMPANY as `requires2FA`; (b) e2e 2FA/AdminIntegrityChecker state bleeding across scenarios; (c) regression from the bug-fix campaign's auth changes (BUG-14/BUG-21 touched token/refresh paths); (d) live-Firebase company1 2FA state.
- **Not fixed** — would need an isolated single-scenario re-run + pre-campaign (`6d21b206^`) comparison before concluding regression vs harness/env. Speculative auth edits risk the campaign's verified fixes.

**Cluster B — banned-token `419` vs `401` (Consolidated ×2 soft-assert) — SUSPECTED:**
- A stored `banned_token` to `/users/me` returns **401**, test expects **419** (tokenVersion-stale → silent-refresh contract). Possible behavioral change in banned-user handling (BannedUserAuthorizationFilter / token-version path) — same investigation as Cluster A.

**Cluster C — Subscription (×2) — LIKELY environmental:**
- `expected "SENT" but was "FAILED"`: a subscription notification/invoice email status — e2e has no real SMTP and Fakturownia billing is lapsed (see above), so the send fails.
- `expected 200 to be >= 400`: a request expected to be rejected returned 200 — needs a quick check (could be test-expectation drift or a real gap).

### Net

- **Unit + Integration code: clean** (the only 3 integration reds are the lapsed-Fakturownia-plan artifact).
- **E2E: 7 reds in 3 suites**, dominated by one auth/session root cause (company1 partial-session) that needs a focused investigation before any fix — flagged, not patched.

### E2E failing tests — exact list (Security + Consolidated)

**RunSecurityIT (4)** — all in `features/security-advanced-session.feature`, class "Advanced Session Security Tests (CONSOLIDATED)":
1. `Admin bans both COMPANY and INFLUENCER users (consolidated)` — feature:111 — company1 GET `/partnership-opportunity/paged` expected **403**, got **401**.
2. `Token version validation - 419 after status change (consolidated)` — feature:146 — company1 access `/users/me` expected success, got **401**.
3. `Complete session lifecycle - ban, refresh, unban, refresh (consolidated)` — feature:164 — company1 see account status `BANNED` → assertion false (got **401**).
4. `Token version access denial - all patterns (consolidated)` — feature:193 — company1 access `/users/me` expected success, got **401**.

**RunConsolidatedIT (1 scenario, 5 soft-assertions)** — `Admin INACTIVE/ACTIVE status flow - Company and Influencer (consolidated - 20+ assertions)` (class "Admin INACTIVE/ACTIVE Status Flow (Consolidated)"), 5 of 20 failed:
- #13 get accountStatus for company1 → **401 UNAUTHORIZED** (expected ok)
- #14 stored `banned_token` → `/users/me`: expected **419**, got **401**
- #15 get accountStatus for company1 → **401 UNAUTHORIZED** (expected ok)
- #16 company1 → `/partnership-opportunity/paged`: expected **200**, got **401**
- #20 stored `banned_token` → `/users/me`: expected **419**, got **401**

Common thread: `company1` 401 on authenticated requests (partial-session) + `banned_token` 401-instead-of-419 (token-version/stale contract). Both clusters live in the ban / token-version / session-lifecycle paths — the area BUG-21 + BUG-14 modified.

### Resolution — BUG-21 narrowed (commit `af67e3d3`, 2026-05-31)

Root cause (3 opus-code-crawler agents, CONFIRMED): BUG-21 (`1d831456`) made `TokenExchangeService.createRefreshedSession` throw `account_disabled` for BANNED users — contradicting the whitelist design (`BannedUserAuthorizationFilter` whitelists `/users/me` for BANNED) + 5 pre-existing e2e scenarios that ban → refresh → view ban page. The `company1`-401 cascade AND `banned_token` 419-vs-401 were both **symptoms of the single refresh-throw**: the e2e refresh step swallows the refusal and leaves stale cookies, so every downstream call 401s.

Fix (user-approved "narrow BUG-21"): removed the BANNED-specific throw. Banned users now refresh to a valid session whose JWT carries `accountStatus=BANNED` (FE renders the ban UI); the filter still blocks every non-whitelisted endpoint, so no extra reach. INACTIVE/DELETED/TO_BE_DELETED stay denied via the `isUserActive` guard.

Verified: `TokenExchangeServiceUnitTest` 47/0/0 · auth-cluster subset 568/0/0 · **RunSecurityIT 0 failures (was 4)** · **RunConsolidatedIT 0 failures (was 1)**. Commit `af67e3d3` on `main` (NOT pushed).

REMAINING (separate, not addressed here): `RunSubscriptionIT` 2 failures — `200 not >= 400` and notification/invoice `"SENT"` vs `"FAILED"`. Likely environmental (no real SMTP in e2e + lapsed Fakturownia Standard plan), not yet root-caused.

---

## Test Run Results — 2026-05-31 (re-run #2, post-BUG-22, BE tip `a0f8ad57`)

Full sequential re-run of all three tiers after the BUG-22 fix (`a0f8ad57` — nullable-compensation browse 400). Raw logs overwritten at `docs/Tests/test-run-2026-05-31/{unit,integration,e2e}.log`. BE/FE stopped for clean test JVMs; Docker up (Testcontainers); Neo4j/Redis left running. Commands: `mvn` directly from bash.

### Tier summary

| Tier | Command | Result | Pass | Fail | Notes |
|------|---------|--------|------|------|-------|
| **Unit** | `mvn clean test -Ptest -DskipPmd=true` | ✅ BUILD SUCCESS | **10,214** | 0 | unchanged vs prior run — BUG-22 entity/DTO change has no unit regression |
| **Integration** | `mvn verify -Pintegration -DskipPmd=true` | ⚠️ 3 fail (env) | 874 | 3 | **877 total = prior 875 + 2 new `PartnershipOpportunityService_NullCompensation_IntegrationTest` (both pass)**. The 3 fails are the lapsed-plan `FakturowniaAdapter_IntegrationTest$CreateInvoice` (HTTP 422 "Prosimy o wcześniejsze opłacenie planu Standard") |
| **E2E** | `mvn verify -Pe2e -DskipPmd=true -DRL_STANDARD_REQUESTS=1000` | ⚠️ 2 fail (RunSubscriptionIT) | — | 2 | same pre-existing pair; **all other 14 suites green** |

### E2E — per-suite (executed = 276 − skipped for tag-filtered suites)

| Suite | Executed | Fail | Status | vs prior |
|-------|----------|------|--------|----------|
| RunCucumberIT (Normal) | 36 | 0 | ✅ | = |
| RunSessionExpiryIT | 1 | 0 | ✅ | = |
| RunMultiUserIT | 6 | 0 | ✅ | = |
| RunRateLimitingIT | 4 | 0 | ✅ | = |
| RunAdminIT | 12 | 0 | ✅ | = |
| **RunSecurityIT** | 126 | **0** | ✅ | **was 4 fail → fixed by af67e3d3** |
| **RunConsolidatedIT** | 9 | **0** | ✅ | **was 1 fail → fixed by af67e3d3** |
| RunPartnershipFlowIT | 3 | 0 | ✅ | = (BUG-22 did not regress campaign flow) |
| RunNotificationIT | 3 | 0 | ✅ | = |
| RunConsentIT | 35 | 0 | ✅ | = |
| RunRegistryIT | 18 | 0 | ✅ | = |
| RunStepUpAuthIT | 9 | 0 | ✅ | = |
| RunInfluencerVerificationIT | 5 | 0 | ✅ | = |
| **RunSubscriptionIT** | 7 | **2** | ❌ | = (unchanged) |
| RunPaymentsDisabledIT | 3 | 0 | ✅ | = |

### The only 2 E2E failures (RunSubscriptionIT — pre-existing, NOT a BUG-22 regression)

1. **`FREE plan blocks campaign creation at limit 5`** — `AssertionError: ... to be greater than or equal to` (expected an HTTP rejection ≥400 on the over-limit campaign, got **200**). Pre-existing; candidate **real gap** (FREE-tier campaign-count limit not enforced) OR test-state drift (subscription e2e is stateful; leftover campaigns skew the count). Needs an isolated single-scenario run to decide. Unrelated to BUG-22 (which only touched compensation field typing/mapping, not creation limits).
2. **`Invoice created on payment and sent to Fakturownia`** — `expected: "SENT" but was: "FAILED"` (`SubscriptionSteps:192`). **Environmental**: the invoice send fails because the lapsed test-account Fakturownia plan returns 422 — same root as the 3 integration Fakturownia fails. Gate behind `-Dfakturownia.live.test=true` to make e2e green by default.

### Net

- **Unit + Integration code: clean** (the only 3 integration reds are the lapsed-Fakturownia-plan artifact).
- **E2E: 2 reds, both in RunSubscriptionIT** — one environmental (Fakturownia), one pre-existing FREE-limit assertion to investigate. **The full Security + Consolidated + Partnership suites are green** — BUG-22 fix verified across the suite with zero new failures; af67e3d3 holds.
