# E2E Test Strategy

> **Snapshot from 2026-01-17.** The current, measured figures are in the [README's numbers table](../README.md#testing) (test methods, classes, Cucumber files and scenarios, measured 2026-09-08). This document keeps its own numbers as they were when it was written.

> **Last Updated**: January 17, 2026
> **Status**: Phase 2 (Security) COMPLETE | Phase 3 (Business Logic) NEXT
> **Total Scenarios**: 84 (across 20 feature files)
> **Test Suites**: 7

---

## Philosophy: Gherkin as Business Language for AI Self-Loop

With AI-assisted development, large E2E suites become **assets, not liabilities**. When an AI reads a failing Gherkin test, it understands the **business impact** (not just a technical assertion failure). This enables the AI self-loop:

1. AI makes code changes
2. AI runs E2E tests
3. AI reads failures in **business language**
4. AI fixes code OR intentionally updates tests
5. Loop until green

**Key benefits:**
- 84 Gherkin scenarios = 84 documented behaviors (living documentation)
- Tests verify BEHAVIOR, not implementation (refactoring freedom)
- Readable by non-technical stakeholders
- AI can auto-fix regressions or update tests for intentional changes

---

## The Hard Part is Already Done

The most complex engineering work is complete. The authentication infrastructure required solving integration challenges that all future tests simply reuse:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMPLETED INFRASTRUCTURE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  EXTERNAL SERVICE INTEGRATION:                                              │
│  ┌────────────────────────────────────────────────────────────────┐         │
│  │  ✅ Firebase REST API Authentication                           │         │
│  │     • signInWithPassword endpoint                              │         │
│  │     • Token refresh flow                                       │         │
│  │     • Error handling (wrong password, rate limits)             │         │
│  │                                                                │         │
│  │  ✅ Firestore Database Access                                  │         │
│  │     • User document retrieval                                  │         │
│  │     • TOTP secrets collection                                  │         │
│  │     • Instagram tokens collection                              │         │
│  │                                                                │         │
│  │  ✅ Google Cloud KMS Decryption                                │         │
│  │     • totp-secrets-key for Admin 2FA                           │         │
│  │     • token-encryption-key for Instagram OAuth                 │         │
│  └────────────────────────────────────────────────────────────────┘         │
│                                                                             │
│  SESSION MANAGEMENT:                                                        │
│  ┌────────────────────────────────────────────────────────────────┐         │
│  │  ✅ Token Exchange (Firebase → Backend Session)                │         │
│  │  ✅ HTTP-Only Cookie Handling (session + session_sig)          │         │
│  │  ✅ HMAC Signature Verification                                │         │
│  │  ✅ Session Invalidation on Logout                             │         │
│  │  ✅ 2FA Challenge/Response Flow                                │         │
│  │  ✅ TOTP Code Generation from Decrypted Secrets                │         │
│  └────────────────────────────────────────────────────────────────┘         │
│                                                                             │
│  REUSABLE COMPONENTS:                                                       │
│  ┌────────────────────────────────────────────────────────────────┐         │
│  │  ✅ ScenarioContext - State sharing between steps              │         │
│  │  ✅ TotpCodeGenerator - TOTP generation utility                │         │
│  │  ✅ createAuthHeaders() - Cookie injection                     │         │
│  │  ✅ url() helper - Base URL construction                       │         │
│  │  ✅ Multi-user actor management (parallel sessions)            │         │
│  └────────────────────────────────────────────────────────────────┘         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why New Tests Are Now Easy

With infrastructure complete, every new business logic test follows this pattern:

```gherkin
# STEP 1: Reuse existing authentication (ALREADY WORKS)
Given "company1" logs in as COMPANY with Firebase UID "..." email "..." password "..."

# STEP 2: Simple HTTP call (trivial to implement)
When "company1" creates a partnership opportunity with title "Summer Campaign"

# STEP 3: Simple assertion (trivial to implement)
Then the response status should be 200
And the opportunity should appear in the company's list
```

**The step definitions for Step 1 are DONE.** Steps 2 and 3 are just:
- One HTTP call with existing session cookies
- One JSON response assertion

---

## Progress Tracker

```
╔═════════════════════════════════════════════════════════════════════════════╗
║                        E2E TEST SUITE PROGRESS                              ║
╠═════════════════════════════════════════════════════════════════════════════╣
║                                                                             ║
║  CURRENT: 84 SCENARIOS (Phase 1-2 Complete)                                 ║
║                                                                             ║
║  ████████████████████████████████░░░░░░░░░░  84/~120 (70%)                  ║
║                                                                             ║
╠═════════════════════════════════════════════════════════════════════════════╣
║  PHASE 1: Foundation (Auth)           ████████████████████  17/17  (100%) ✓ ║
║    └─ Login (COMPANY/ADMIN/INFLUENCER)                       3 scenarios    ║
║    └─ Logout (all user types)                                3 scenarios    ║
║    └─ Login Errors (invalid creds, TOTP)                     5 scenarios    ║
║    └─ Multi-User Session Isolation                           6 scenarios    ║
║                                                                             ║
║  PHASE 2: Security + Admin            ████████████████████  67/67  (100%) ✓ ║
║    └─ 401 Unauthenticated Access                            14 scenarios    ║
║    └─ 403 Company Forbidden                                  4 scenarios    ║
║    └─ 403 Influencer Forbidden                               4 scenarios    ║
║    └─ 403 Authorization Boundary                             1 scenario     ║
║    └─ 401 Unauthorized (expired session)                     2 scenarios    ║
║    └─ Rate Limiting (429)                                    4 scenarios    ║
║    └─ Advanced Security (GDPR, 2FA partial)                  3 scenarios    ║
║    └─ Advanced Session Security                              9 scenarios    ║
║    └─ Admin GeoIP Analysis                                   4 scenarios    ║
║    └─ Admin User Management                                  1 scenario     ║
║    └─ Admin Platform Management                             12 scenarios    ║
║    └─ Admin Inactive Flow                                    1 scenario     ║
║    └─ Profile (Critical + Non-Critical + Validation)         6 scenarios    ║
║    └─ File Upload (Signed URL)                               2 scenarios    ║
║                                                                             ║
║  PHASE 3: Business Logic              ░░░░░░░░░░░░░░░░░░░░   0/~36 (0%)     ║
║    └─ Partnership Opportunities (creation, CRUD)             TODO           ║
║    └─ Applied Opportunities (12-state machine)               TODO           ║
║    └─ Full Choreography (Admin+Company+Influencer)           TODO           ║
║                                                                             ║
╠═════════════════════════════════════════════════════════════════════════════╣
║  TEST SUITES (7 Isolated JVMs)                                              ║
║  ─────────────────────────────                                              ║
║  RunSecurityIT      ████████████████████   37 scenarios  (@security-suite)  ║
║  RunCucumberIT      ████████████████████   11 scenarios  (default/auth)     ║
║  RunMultiUserIT     ████████████████████    6 scenarios  (@multi-user)      ║
║  RunRateLimitingIT  ████████████████████    4 scenarios  (@rate-limiting)   ║
║  RunAdminIT         ████████████████████    1 scenario   (@admin-ops)       ║
║  RunConsolidatedIT  ████████████████████   23 scenarios  (@consolidated)    ║
║  RunSessionExpiryIT ████████████████████    2 scenarios  (@session-expiry)  ║
║  ───────────────────────────────────────────────────────────────────────────║
║  RunPartnershipIT   ░░░░░░░░░░░░░░░░░░░░  ~36 scenarios  (@partnership-flow)║
║                                                                             ║
╠═════════════════════════════════════════════════════════════════════════════╣
║  COVERAGE METRICS (JaCoCo Merged - 2026-01-17)                              ║
║  ─────────────────────────────────────────────                              ║
║  Line Coverage:      74% ██████████████████████░░░░░░░░                     ║
║  Branch Coverage:    64% ████████████████████░░░░░░░░░░  (target: 62%)      ║
║  Method Coverage:    83% ████████████████████████░░░░░░                     ║
║  Instruction Cov:    71% █████████████████████░░░░░░░░░                     ║
║                                                                             ║
╚═════════════════════════════════════════════════════════════════════════════╝
```

---

## Test Suite Architecture

### 7 Isolated Test Suites (+ 1 Planned)

Each suite runs in its own JVM with isolated configuration:

| Suite | Tag Filter | Scenarios | Purpose |
|-------|-----------|-----------|---------|
| `RunSecurityIT` | `@security-suite` | 37 | Security tests (401/403, GDPR, session) |
| `RunConsolidatedIT` | `@consolidated` | 23 | Admin platform, profile, file upload |
| `RunCucumberIT` | Default (negative) | 11 | Auth flows, login errors |
| `RunMultiUserIT` | `@multi-user` | 6 | Multi-user session isolation |
| `RunRateLimitingIT` | `@rate-limiting` | 4 | Rate limit enforcement |
| `RunSessionExpiryIT` | `@session-expiry` | 2 | Expired session handling |
| `RunAdminIT` | `@admin-ops` | 1 | Admin user management |
| **`RunPartnershipIT`** | `@partnership-flow` | ~36 | **PLANNED** - Business logic (Suite 8) |

### Running Tests

```powershell
# Full E2E suite (all 6 suites)
mvn verify -Pe2e

# Individual suites
mvn verify -Pe2e -Dskip.security.tests=false -Dskip.normal.tests=true -Dskip.session.tests=true -Dskip.rate.tests=true -Dskip.admin.tests=true -Dskip.multi.tests=true

# By tag (runs in all matching suites)
mvn verify -Pe2e "-Dcucumber.filter.tags=@authentication"
```

---

## Feature File Summary

| Feature File | Scenarios | Tag | Suite |
|--------------|-----------|-----|-------|
| `security-unauthenticated-access.feature` | 14 | @security-suite | RunSecurityIT |
| `admin-platform-management.feature` | 12 | @consolidated | RunConsolidatedIT |
| `security-advanced-session.feature` | 9 | @security-suite | RunSecurityIT |
| `multi-user-session-isolation.feature` | 6 | @multi-user | RunMultiUserIT |
| `login-errors.feature` | 5 | @authentication | RunCucumberIT |
| `security-company-forbidden.feature` | 4 | @security-suite | RunSecurityIT |
| `security-influencer-forbidden.feature` | 4 | @security-suite | RunSecurityIT |
| `admin-geoip-analysis.feature` | 4 | @security-suite | RunSecurityIT |
| `rate-limiting.feature` | 4 | @rate-limiting | RunRateLimitingIT |
| `login.feature` | 3 | @authentication | RunCucumberIT |
| `logout.feature` | 3 | @authentication | RunCucumberIT |
| `security-advanced.feature` | 3 | @security-suite | RunSecurityIT |
| `profile-critical-consolidated.feature` | 2 | @consolidated | RunConsolidatedIT |
| `profile-non-critical-consolidated.feature` | 2 | @consolidated | RunConsolidatedIT |
| `validation-edge-cases.feature` | 2 | @consolidated | RunConsolidatedIT |
| `file-upload-signed-url.feature` | 2 | @consolidated | RunConsolidatedIT |
| `security-401-unauthorized.feature` | 2 | @session-expiry | RunSessionExpiryIT |
| `security-403-forbidden.feature` | 1 | @security | RunCucumberIT |
| `admin-user-management.feature` | 1 | @admin-ops | RunAdminIT |
| `admin-inactive-flow-consolidated.feature` | 1 | @consolidated | RunConsolidatedIT |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         E2E TEST ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────────────────────┐ │
│  │   Cucumber   │────▶│  Step Defs   │────▶│     Spring Boot App          │ │
│  │   Feature    │     │  (Java)      │     │     (Testcontainers)         │ │
│  │   Files      │     │              │     │                              │ │
│  └──────────────┘     └──────────────┘     │  ┌────────────────────────┐  │ │
│                              │              │  │  PostgreSQL Container  │  │ │
│                              │              │  │  (postgres:15-alpine)  │  │ │
│                              │              │  └────────────────────────┘  │ │
│                              │              │  ┌────────────────────────┐  │ │
│                              │              │  │    Redis Container     │  │ │
│                              │              │  │    (redis:7-alpine)    │  │ │
│                              │              │  └────────────────────────┘  │ │
│                              │              └──────────────────────────────┘ │
│                              │                                               │
│                              ▼                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    EXTERNAL SERVICES (Real)                            │  │
│  │                                                                        │  │
│  │  ┌─────────────┐   ┌─────────────┐   ┌─────────────────────────────┐  │  │
│  │  │  Firebase   │   │  Firestore  │   │   Google Cloud KMS          │  │  │
│  │  │   Auth      │   │  Database   │   │   (totp-secrets-key)        │  │  │
│  │  │             │   │             │   │   (token-encryption-key)    │  │  │
│  │  └─────────────┘   └─────────────┘   └─────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PRE-CONFIGURED TEST USERS                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  COMPANY USER                                                                │
│  ├── Firebase UID: OhhdU5ffXweJRlnN5AzQxSnjIvw1                             │
│  ├── Email: e2e.company@test.com                              │
│  ├── Role: COMPANY                                                          │
│  └── Pre-existing in: Firebase Auth + Firestore + PostgreSQL                │
│                                                                              │
│  ADMIN USER (with 2FA)                                                       │
│  ├── Firebase UID: E2E_ADMIN_001                             │
│  ├── Email: e2e.admin@test.com                                   │
│  ├── Role: ADMIN                                                            │
│  ├── TOTP Secret: Encrypted in Firestore totpSecrets/{uid}                  │
│  └── KMS Key: totp-secrets-key (symmetric decryption)                       │
│                                                                              │
│  INFLUENCER USER (OAuth)                                                     │
│  ├── Firebase UID: E2E_INFLUENCER_001                             │
│  ├── Role: INFLUENCER                                                       │
│  ├── Instagram Token: Encrypted in Firestore instagramUsers/{uid}           │
│  └── KMS Key: token-encryption-key (symmetric decryption)                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Infrastructure

### Testcontainers
- **PostgreSQL 15** (alpine) - Database
- **Redis 7** (alpine) - Sessions & Rate Limiting

### External Services (Real)
- Firebase Auth - REST API authentication
- Firestore - User data, TOTP secrets, Instagram tokens
- Google Cloud KMS - Decryption keys

### Key Components
- `ScenarioContext` - State sharing between steps
- `TotpCodeGenerator` - TOTP code generation
- `createAuthHeaders()` - Session cookie injection

---

## Windows Setup (WSL2)

```powershell
# 1. Enable WSL2 + Docker Desktop (WSL2 backend)
# 2. Clone and configure
git clone <repo>
cd checkitout-backend
cp src/test/resources/.env.template src/test/resources/.env
# Edit .env with secrets

# 3. Run tests
mvn verify -Pe2e -Dpmd.skip=true -Dcpd.skip=true
```

---

## Cucumber Tags Reference

| Tag | Description |
|-----|-------------|
| `@security-suite` | All security tests |
| `@authentication` | Login/logout flows |
| `@401-unauthenticated` | Unauthenticated access tests |
| `@403-company-forbidden` | Company role restrictions |
| `@403-influencer-forbidden` | Influencer role restrictions |
| `@rate-limiting` | Rate limit tests |
| `@multi-user` | Multi-user isolation |
| `@session-expiry` | Expired session tests |
| `@admin-ops` | Admin operations |
| `@consolidated` | Consolidated scenarios (multiple tests per login) |
| `@partnership-flow` | **PLANNED** - Partnership & application state machine |
| `@state-machine` | **PLANNED** - State transition tests |
| `@choreography` | **PLANNED** - Multi-actor workflow tests |

---

## Next Phase: Business Logic (Suite 8)

Phase 2 (Security) is complete. **Phase 3 focuses on core business workflows.**

See **[E2E-REMAINING.md](../E2E-REMAINING.md)** for detailed implementation plan.

### Suite 8: Partnership Flow (`@partnership-flow`)

| Feature | Scenarios | Focus |
|---------|-----------|-------|
| `partnership-opportunity-creation.feature` | ~10 | CRUD + validation edge cases |
| `applied-opportunity-state-machine.feature` | ~18 | All 12 state transitions |
| `partnership-full-choreography.feature` | ~8 | Multi-actor happy paths |

### Coverage Impact

| Component | Current | Potential Gain |
|-----------|---------|----------------|
| AppliedOpportunityService | 0.0% branch | +1.57% |
| PartnershipOpportunityService | 14.5% branch | +1.04% |
| **Total Branch Coverage** | **53%** | **→ ~56%** |

### State Machine (12 States)

```
APPLIED → ACCEPTED_BY_COMPANY → ACCEPTED_BY_INFLUENCER →
CONTENT_SEND_TO_ACCEPT → CONTENT_APPROVED → CONTENT_POSTED →
TO_BE_PAID → DONE

Terminal: REJECTED_BY_COMPANY, REJECTED_BY_INFLUENCER, DONE
```

### Multi-Actor Choreography

1. **Admin** sets user status to ACTIVE (Company + Influencer)
2. **Company** creates partnership opportunity
3. **Influencer** applies to opportunity
4. **Company** accepts/rejects application
5. **Influencer** accepts offer, submits content
6. **Company** reviews content, approves/rejects
7. **Influencer** posts content
8. **Company** verifies post, marks payment complete
9. **Both** rate each other

---

*Last updated: 2026-01-17 | 84 scenarios passing across 7 isolated test suites*
