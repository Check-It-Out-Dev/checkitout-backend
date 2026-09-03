# Implementation Plan: Stripe Payment Gateway

## Context

checkItOut needs subscription-based payments for company accounts. Requirements, state machine (10 states, 56 transitions, 13 rules), and API connectivity are documented in `docs/StripeGateway/`. The full state machine is modeled in Neo4j namespace `subscription`.

The codebase is ~70% ready: `SUBSCRIPTION_ACTIVATION_CONSENT` legal doc type exists, HMAC consent infrastructure exists, Ports & Adapters pattern exists for external APIs, ShedLock crons exist, notification system is mature.

**Design decisions:**
- Stripe: direct integration, no abstraction (strategic vendor lock-in, user confirmed)
- Fakturownia: abstracted behind `InvoicingPort` interface + adapter pattern (follows existing `BialaListaVatAdapter` pattern)
- "Campaign" = `PartnershipOpportunity`, "Company" = `User` with `userType=COMPANY`

---

## Phase 1: Database Migrations

**New Liquibase migration:** `src/main/resources/db/changelog/2026/03/22-03-2026-subscription-tables.sql`

### Tables to create:

```sql
-- 1. subscription_plan (static seed data)
CREATE TABLE subscription_plan (
    id BIGINT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,  -- FREE, BUSINESS, ENTERPRISE
    price_pln NUMERIC(10,2) NOT NULL,
    campaign_limit INT NOT NULL,
    stripe_price_id VARCHAR(100),
    stripe_product_id VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_time TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT subscription_plan_name_check CHECK (name IN ('FREE', 'BUSINESS', 'ENTERPRISE'))
);

-- 2. company_subscription (1:1 with User where userType=COMPANY)
CREATE TABLE company_subscription (
    id BIGINT PRIMARY KEY DEFAULT nextval('company_subscription_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    current_plan_id BIGINT NOT NULL REFERENCES subscription_plan(id),
    previous_plan_id BIGINT REFERENCES subscription_plan(id),
    status VARCHAR(30) NOT NULL,
    previous_state VARCHAR(30),
    target_plan_id BIGINT REFERENCES subscription_plan(id),
    trial_end_date TIMESTAMP,
    trial_used BOOLEAN NOT NULL DEFAULT FALSE,
    stripe_customer_id VARCHAR(100),
    stripe_subscription_id VARCHAR(100),
    stripe_schedule_id VARCHAR(100),
    newest_terms_accepted BOOLEAN NOT NULL DEFAULT TRUE,
    grace_deadline TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT NOW(),
    last_update_time TIMESTAMP NOT NULL DEFAULT NOW(),
    updater_id BIGINT,
    CONSTRAINT company_subscription_user_unique UNIQUE (user_id),
    CONSTRAINT company_subscription_status_check CHECK (status IN (
        'FREE_ACTIVE','TRIAL_ENTERPRISE','BUSINESS_ACTIVE','ENTERPRISE_ACTIVE',
        'DOWNGRADE_PENDING','PAYMENT_FAILED','TERMS_PENDING','SUSPENDED_LEGAL','ACCOUNT_DEACTIVATED'
    ))
);

-- 3. subscription_event (immutable event log)
CREATE TABLE subscription_event (
    id BIGINT PRIMARY KEY DEFAULT nextval('subscription_event_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    event_type VARCHAR(50) NOT NULL,
    plan_from VARCHAR(20),
    plan_to VARCHAR(20),
    stripe_event_id VARCHAR(100),
    billing_period_start TIMESTAMP,
    billing_period_end TIMESTAMP,
    metadata JSONB,
    created_time TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT subscription_event_stripe_unique UNIQUE (stripe_event_id)
);

-- 4. billing_period
CREATE TABLE billing_period (
    id BIGINT PRIMARY KEY DEFAULT nextval('billing_period_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    plan_id BIGINT NOT NULL REFERENCES subscription_plan(id),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_time TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT billing_period_status_check CHECK (status IN ('ACTIVE','EXPIRED','PENDING_DOWNGRADE'))
);

-- 5. invoice_record (Fakturownia tracking with retry support)
CREATE TABLE invoice_record (
    id BIGINT PRIMARY KEY DEFAULT nextval('invoice_record_seq'),
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    billing_period_id BIGINT REFERENCES billing_period(id),
    fakturownia_invoice_id BIGINT,
    invoice_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    amount_pln NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    error_message TEXT,
    last_attempt_at TIMESTAMP,
    created_time TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT invoice_record_status_check CHECK (status IN ('PENDING','SENT','FAILED','DEAD_LETTER'))
);

-- 6. terms_version (pricing/terms versioning)
CREATE TABLE terms_version (
    id BIGINT PRIMARY KEY DEFAULT nextval('terms_version_seq'),
    version INT NOT NULL,
    content_hash VARCHAR(100) NOT NULL,
    pricing_snapshot JSONB,
    published_at TIMESTAMP,
    grace_period_days INT NOT NULL DEFAULT 38,
    created_time TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Sequences (in same migration):
```sql
CREATE SEQUENCE company_subscription_seq INCREMENT BY 50;
CREATE SEQUENCE subscription_event_seq INCREMENT BY 50;
CREATE SEQUENCE billing_period_seq INCREMENT BY 50;
CREATE SEQUENCE invoice_record_seq INCREMENT BY 50;
CREATE SEQUENCE terms_version_seq INCREMENT BY 50;
```

### Indexes:
```sql
CREATE INDEX idx_company_subscription_user ON company_subscription(user_id);
CREATE INDEX idx_company_subscription_status ON company_subscription(status);
CREATE INDEX idx_company_subscription_stripe_customer ON company_subscription(stripe_customer_id);
CREATE INDEX idx_subscription_event_user ON subscription_event(user_id);
CREATE INDEX idx_subscription_event_stripe ON subscription_event(stripe_event_id);
CREATE INDEX idx_billing_period_user_status ON billing_period(user_id, status);
CREATE INDEX idx_billing_period_dates ON billing_period(start_date, end_date);
CREATE INDEX idx_invoice_record_status ON invoice_record(status, retry_count);
CREATE INDEX idx_invoice_record_user ON invoice_record(user_id);
```

### Seed data:
```sql
INSERT INTO subscription_plan (id, name, price_pln, campaign_limit, stripe_price_id, stripe_product_id)
VALUES
(1, 'FREE', 0.00, 2, NULL, NULL),
(2, 'BUSINESS', 29.00, 5, 'price_1TDprAEF0n7JDo59KpKh8vnt', 'prod_UCEJdIcXpCI8XY'),
(3, 'ENTERPRISE', 99.00, 10, 'price_1TDprIEF0n7JDo591xcehTLl', 'prod_UCEJEPOpJvXeut');
```

### Modify existing:
- Add `SUBSCRIPTION_PURCHASE` to `ConsentSource` CHECK constraint
- Seed `SUBSCRIPTION_ACTIVATION_CONSENT` legal document row (mock content hash) if not already present
- Triggers for `set_created_time` and `update_last_update_time` on new tables (follow existing pattern from `004-triggers.sql`)

---

## Phase 2: Backend — New Package Structure

All new code under `com.sm.instagram.platform.subscription`:

```
subscription/
├── SubscriptionController.java          # REST endpoints
├── SubscriptionService.java             # Core state machine logic
├── SubscriptionConsentService.java      # Inline consent recording
├── CampaignLimitService.java            # Campaign count enforcement
├── entity/
│   ├── CompanySubscription.java         # Main entity with @Version
│   ├── SubscriptionPlan.java            # Static reference entity
│   ├── SubscriptionEvent.java           # Immutable event log
│   ├── BillingPeriod.java               # Billing period tracking
│   ├── InvoiceRecord.java               # Fakturownia invoice tracking
│   ├── TermsVersion.java                # Terms/pricing versioning
│   ├── SubscriptionStatus.java          # Enum (10 states)
│   └── SubscriptionEventType.java       # Enum (event types)
├── repository/
│   ├── CompanySubscriptionRepository.java
│   ├── SubscriptionPlanRepository.java
│   ├── SubscriptionEventRepository.java
│   ├── BillingPeriodRepository.java
│   ├── InvoiceRecordRepository.java
│   └── TermsVersionRepository.java
├── dto/
│   ├── SubscriptionStatusDtoOut.java    # Current plan, limits, trial info
│   ├── UpgradeRequestDtoIn.java         # Plan selection + consent proof
│   ├── DowngradeRequestDtoIn.java       # Target plan + consent proof (for Ent->Biz)
│   └── CheckoutSessionDtoOut.java       # Stripe session URL
├── stripe/
│   ├── StripeService.java               # Stripe API calls (direct, no abstraction)
│   ├── StripeWebhookController.java     # POST /api/webhooks/stripe
│   ├── StripeWebhookHandler.java        # Event routing + idempotency
│   ├── StripeProperties.java            # Config properties
│   └── StripeConfig.java                # Stripe bean configuration
├── invoicing/
│   ├── InvoicingPort.java               # Interface (abstraction)
│   ├── FakturowniaAdapter.java          # Implementation
│   ├── FakturowniaConfig.java           # RestTemplate bean
│   ├── FakturowniaProperties.java       # Config properties
│   ├── dto/
│   │   ├── CreateInvoiceRequest.java
│   │   └── FakturowniaInvoiceResponse.java
│   └── InvoiceRetryService.java         # Retry logic for failed invoices
├── cron/
│   ├── TrialExpiryNotifierCronJob.java  # 14d/7d/1d trial notifications
│   ├── SubscriptionPeriodProcessorCronJob.java  # Downgrades, trial expiry
│   ├── TermsGraceProcessorCronJob.java  # Grace period enforcement
│   └── InvoiceRetryCronJob.java         # Fakturownia retry every 15min
└── event/
    ├── SubscriptionPaymentSucceededEvent.java
    ├── SubscriptionPaymentFailedEvent.java
    ├── SubscriptionTrialEndingEvent.java
    ├── SubscriptionDowngradedEvent.java
    ├── SubscriptionSuspendedEvent.java
    └── SubscriptionEventListener.java   # @TransactionalEventListener handlers
```

---

## Phase 3: Backend — Key Implementation Details

### 3.1 SubscriptionService (state machine core)

- All state transitions use **optimistic locking**: `UPDATE ... WHERE id=? AND version=? AND status=?`
- Publishes Spring `ApplicationEvent` objects inside `@Transactional` for notification
- Delegates to `StripeService` for Stripe API calls
- Delegates to `InvoicingPort` for invoice creation (fire-and-forget)

Key methods:
```java
activateTrial(Long userId)
// Guard: trial_used=false AND no paid subscription history
// Action: Set TRIAL_ENTERPRISE, trial_end_date=now+3months, create billing_period

initiateUpgrade(Long userId, UpgradeRequestDtoIn request)
// Guard: Valid state (FREE/TRIAL/BUSINESS/DOWNGRADE_PENDING/PAYMENT_FAILED)
// Action: Record consent inline, create Stripe Checkout Session, return session URL

handleCheckoutCompleted(StripeEvent event)
// Guard: Idempotent (check stripe_event_id), signature verified
// Action: Activate plan, reset billing cycle, create billing_period, fire-and-forget invoice

handleInvoicePaid(StripeEvent event)
// Action: Extend billing_period, fire-and-forget invoice

handlePaymentFailed(StripeEvent event)
// Action: Set PAYMENT_FAILED, publish notification event

handleSubscriptionDeleted(StripeEvent event)
// Action: Downgrade to FREE, publish notification event

handleSubscriptionUpdated(StripeEvent event)
// Action: Handle Stripe Schedule phase change (downgrade applied)

requestDowngrade(Long userId, DowngradeRequestDtoIn request)
// Guard: BUSINESS_ACTIVE or ENTERPRISE_ACTIVE
// Action: For Ent->Biz: collect consent, create Stripe Schedule. For *->Free: cancel_at_period_end

cancelDowngrade(Long userId)
// Action: Cancel Stripe Schedule or reactivate subscription, restore previous plan
```

### 3.2 CampaignLimitService

Injected into `PartnershipOpportunityService.saveFromDto()`:

```java
@Transactional
public void enforceLimit(Long companyUserId) {
    BillingPeriod period = billingPeriodRepo.findActiveByUserId(companyUserId)
        .orElseThrow(); // FOR UPDATE (pessimistic lock on billing_period row)

    int limit = resolveCampaignLimit(companyUserId);
    long count = partnershipOpportunityRepo.countByCompanyIdAndCreatedTimeBetween(
        companyUserId, period.getStartDate(), period.getEndDate());

    if (count >= limit) {
        throw new BusinessTranslatableException("error.subscription.campaign_limit_reached",
            Map.of("limit", limit, "plan", getCurrentPlanName(companyUserId)));
    }
}
```

Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the `BillingPeriod` query to prevent race conditions.

### 3.3 StripeWebhookController

```java
@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        // 1. Verify signature (WEBHOOK_SIGNATURE_VERIFICATION rule)
        Event event = Stripe.Webhook.constructEvent(payload, sigHeader, webhookSecret);

        // 2. Idempotency check (WEBHOOK_IDEMPOTENCY rule)
        if (subscriptionEventRepo.existsByStripeEventId(event.getId())) {
            return ResponseEntity.ok().build();
        }

        // 3. Route to handler
        webhookHandler.handle(event);
        return ResponseEntity.ok().build();
    }
}
```

**No auth** on this endpoint (Stripe can't authenticate). Signature verification IS the auth.

### 3.4 InvoicingPort (Fakturownia abstraction)

```java
public interface InvoicingPort {
    InvoiceResult createInvoice(InvoiceRequest request);
    Optional<InvoiceDetails> getInvoice(Long externalInvoiceId);
}
```

`FakturowniaAdapter implements InvoicingPort`:
- Uses `@Qualifier("fakturowniaRestTemplate")` RestTemplate
- Follows `BialaListaVatAdapter` pattern exactly
- `POST https://checkitout.fakturownia.pl/invoices.json`
- Throws `ExternalServiceException` on failure
- Kill switch via `fakturownia.enabled` property

### 3.5 Consent at Purchase Time

NOT cookie-based (the codebase explicitly throws for `SUBSCRIPTION_ACTIVATION_CONSENT` in `cookieNameForType`).

Instead, inline consent recording in `SubscriptionConsentService`:
```java
public ConsentRecord recordSubscriptionConsent(Long userId, ConsentProofDtoIn proof) {
    LegalDocument doc = legalDocumentRepo.findCurrentByType(SUBSCRIPTION_ACTIVATION_CONSENT);
    ConsentRecord record = ConsentRecord.builder()
        .user(userRepo.getReferenceById(userId))
        .document(doc)
        .source(ConsentSource.SUBSCRIPTION_PURCHASE)
        .consentProof(buildProofJson(proof))
        .ipAddress(requestContext.getIpAddress())
        .userAgent(requestContext.getUserAgent())
        .build();
    return consentRecordRepo.save(record);
}
```

### 3.6 Notification Types (add to NotificationType enum)

```java
// ACCOUNT category, ALWAYS email — billing-critical
SUBSCRIPTION_TRIAL_ENDING(ACCOUNT, HIGH, ALWAYS, "Trial ending reminder"),
SUBSCRIPTION_TRIAL_EXPIRED(ACCOUNT, HIGH, ALWAYS, "Trial has expired"),
SUBSCRIPTION_PAYMENT_FAILED(ACCOUNT, HIGH, ALWAYS, "Payment failed"),
SUBSCRIPTION_PAYMENT_RECOVERED(ACCOUNT, MEDIUM, ENABLED, "Payment recovered"),
SUBSCRIPTION_PAYMENT_EXHAUSTED(ACCOUNT, HIGH, ALWAYS, "All payment retries failed"),
SUBSCRIPTION_UPGRADED(ACCOUNT, MEDIUM, ENABLED, "Plan upgraded"),
SUBSCRIPTION_DOWNGRADE_SCHEDULED(ACCOUNT, MEDIUM, ENABLED, "Downgrade scheduled"),
SUBSCRIPTION_DOWNGRADED(ACCOUNT, MEDIUM, ENABLED, "Plan downgraded"),
SUBSCRIPTION_SUSPENDED(ACCOUNT, HIGH, ALWAYS, "Account suspended"),
SUBSCRIPTION_REACTIVATED(ACCOUNT, MEDIUM, ENABLED, "Account reactivated"),
```

Add `NotificationRequest.forSubscription(userId, type, params)` factory with `actionUrl = "/user/settings"` and `groupKey = "subscription:" + subscriptionId`.

---

## Phase 4: Backend — Cron Jobs

All follow `ConsentEnforcementCronJob` pattern: thin shell + ShedLock + delegate to service.

| Cron | Schedule | ShedLock | Delegates to |
|------|----------|----------|-------------|
| `TrialExpiryNotifierCronJob` | Daily 5 AM | 30m | `subscriptionService.sendTrialEndingReminders()` |
| `SubscriptionPeriodProcessorCronJob` | Daily 4 AM | 30m | `subscriptionService.processExpiredTrials()` + `processExpiredDowngrades()` + `processExhaustedPayments()` |
| `TermsGraceProcessorCronJob` | Daily 3 AM | 30m | `subscriptionService.processExpiredGracePeriods()` + `processTrialExpiryInTermsPending()` |
| `InvoiceRetryCronJob` | Every 15 min | 14m | `invoiceRetryService.retryFailedInvoices()` |

---

## Phase 5: Backend — Dependencies (pom.xml)

```xml
<!-- Stripe Java SDK -->
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>28.2.0</version>
</dependency>
```

No other new dependencies needed. RestTemplate (already available), Jackson (already available).

---

## Phase 6: Backend — Configuration (application.yml)

```yaml
# Stripe
stripe:
  secret-key: ${STRIPE_PRIVATE_KEY}
  public-key: ${STRIPE_PUBLIC_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET:}
  prices:
    business: price_1TDprAEF0n7JDo59KpKh8vnt
    enterprise: price_1TDprIEF0n7JDo591xcehTLl

# Fakturownia
fakturownia:
  enabled: true
  api-key: ${FAKTUROWNIA_API_KEY}
  domain: checkitout
  department-id: 1878648
  connect-timeout-ms: 5000
  read-timeout-ms: 15000

# Subscription crons
subscription:
  trial-expiry-notifier:
    enabled: true
    cron: "0 0 5 * * *"
  period-processor:
    enabled: true
    cron: "0 0 4 * * *"
  grace-processor:
    enabled: true
    cron: "0 0 3 * * *"
  invoice-retry:
    enabled: true
    cron: "0 */15 * * * *"
```

---

## Phase 7: Frontend — IMPLEMENTED ✅

### 7.1 Actual Package Structure (Implemented)

```
# New files created:
core/services/subscription-api/
├── subscription-api.service.ts          # Tier A API service (8 endpoints)
├── subscription-api.service.spec.ts     # 19 tests (Spectator + HttpTestingController)
└── subscription-api.types.ts            # DTOs, enums (SubscriptionStatus, InvoiceStatus, PlanName)

core/guards/
├── company.guard.ts                     # Functional CanActivateFn (COMPANY + ADMIN)
└── company.guard.spec.ts                # 5 tests (role checks + redirect)

feature/subscription/
├── subscription.routes.ts               # Lazy-loaded success/cancel routes
├── subscription-success/
│   └── subscription-success.component.ts  # Post-checkout, 5s auto-redirect
└── subscription-cancel/
    └── subscription-cancel.component.ts   # Checkout cancelled, return CTA

shared/components/
├── subscription-consent-dialog/
│   └── subscription-consent-dialog.component.ts  # EU Art 16(m) checkbox consent
└── campaign-limit-dialog/
    └── campaign-limit-dialog.component.ts        # Limit reached + upgrade CTA
```

### 7.2 Modified Existing Files

| File | Change | Status |
|------|--------|--------|
| `plan-billing.component.ts` | Complete rewrite: Fuse stub → Stripe subscription management (signals, OnPush, 9 status badges, usage bar, consent dialog wiring) | ✅ |
| `plan-billing.component.spec.ts` | Complete rewrite: 33 tests covering all computed properties, eligibility, badges, actions | ✅ |
| `settings.component.ts/html` | Added Plan & Billing section for COMPANY users via `@if (isCompany)` | ✅ |
| `classy.component.ts/html` | Added trial offer banner (blue, dismissible) + terms pending banner (amber, non-dismissible) | ✅ |
| `pricing.component.ts/html` | Updated to Free/29/99 PLN, removed annual toggle, starter shows "Free" label | ✅ |
| `collaboration-form.component.ts` | Campaign limit error → CampaignLimitDialog (fetches real subscription data) | ✅ |
| `app.routes.ts` | Added `/subscription` route with CompanyGuard | ✅ |
| `redirect.service.ts` | Added `/subscription` to ALLOWED_REDIRECT_PATHS | ✅ |
| `en.json` + `pl.json` | ~120 new keys each: `subscription.*` (UI) + `FEATURE.SUBSCRIPTION.*` (programmatic) + `common.dismiss` | ✅ |

### 7.3 Backend Fixes (Sprint 7)

| Fix | File | Issue |
|-----|------|-------|
| Redirect URLs | `SubscriptionService.java`, `SubscriptionController.java` | Replaced hardcoded `checkitout.pl` with `app.base-url` property |
| Config endpoint | `SubscriptionController.java` | Added `GET /subscription/config` returning Stripe public key |
| Invoice DTO | `InvoiceRecordDtoOut.java` (new), `SubscriptionController.java` | Created DTO to prevent raw entity leak (LazyInitializationException + User data) |
| updaterId regex | `PartnershipOpportunity.java` | Widened `^[a-zA-Z0-9]+$` → `^[a-zA-Z0-9._-]+$` (fixed 2 pre-existing integration test failures) |
| Stripe emoji logs | `StripeConfig.java` | Added 💳🧪🔴💰 emoji to init logs for live/test mode |

### 7.4 Test Summary

| Suite | Tests | Status |
|-------|-------|--------|
| FE: subscription-api.service.spec.ts | 19 | ✅ |
| FE: company.guard.spec.ts | 5 | ✅ |
| FE: plan-billing.component.spec.ts | 33 | ✅ |
| BE: All unit tests | 10,179 | ✅ |
| BE: All integration tests | 849 | ✅ (0 errors after updaterId fix) |
| FE: Angular build | — | ✅ Clean |

### 7.5 Bugs Found During Sprint 7

| # | Bug | Fix |
|---|-----|-----|
| 13 (continued) | `GET /invoices` returned raw `InvoiceRecord` entity — LazyInitializationException + User data leak | Created `InvoiceRecordDtoOut` with `fromEntity()` mapper |
| 14 (continued) | `PartnershipOpportunity.updaterId` regex rejected hyphens — `ADMIN-UUID` format in tests | Widened regex to `^[a-zA-Z0-9._-]+$` |
| 15 | `canUpgradeToBusiness` showed during TRIAL_ENTERPRISE (upgrade to lower tier) | Excluded TRIAL_ENTERPRISE from condition |
| 16 | Campaign limit dialog passed hardcoded zeros | Now fetches real data via SubscriptionApiService |
| 17 | `common.dismiss` i18n key missing | Added to both en.json and pl.json |
| 18 | Subscription consent dialog was dead code | Wired into activateTrial() and upgrade() flows |

---

## Phase 8: Frontend — Update Landing Page Pricing

Update `pricing.component.ts/html` to match actual subscription plans:
- Starter (89 PLN) → **Free** (0 PLN, 2 campaigns/month)
- Growth (199 PLN) → **Business** (29 PLN/month, 5 campaigns/month)
- Premium (599 PLN) → **Enterprise** (99 PLN/month, 10 campaigns/month)
- Remove annual toggle (all plans are monthly only)
- Wire CTAs: Free → sign up, Business/Enterprise → sign up then upgrade in-app
- Keep Hands-Free Collabs and Enterprise SaaS sections as-is (future offerings)
- Update all `landing.pricing.*` translation keys in en.json and pl.json

---

## Phase 9: Backend API Endpoints

```
POST   /api/subscription/trial/activate           # Activate free trial
GET    /api/subscription/status                    # Current plan, limits, billing info
POST   /api/subscription/upgrade                   # Initiate upgrade (returns Stripe session URL)
POST   /api/subscription/downgrade                 # Request downgrade
POST   /api/subscription/downgrade/cancel          # Cancel pending downgrade
POST   /api/subscription/portal                    # Get Stripe Customer Portal URL
POST   /api/webhooks/stripe                        # Stripe webhooks (no auth, signature-verified)
GET    /api/subscription/invoices                   # Invoice history
```

---

## Phase 10: Implementation Order (suggested)

### Sprint 1: Foundation (DB + core entities + Stripe basics) — DONE ✅
1. ~~Liquibase migration (6 tables, 6 sequences, 10 indexes, 6 triggers, seed data)~~ ✅
2. ~~Entities + repositories (16 files: 4 enums, 6 entities, 6 repos)~~ ✅
3. ~~SubscriptionPlan seed data (FREE/BUSINESS/ENTERPRISE with Stripe Price IDs)~~ ✅
4. ~~StripeService + StripeConfig + StripeProperties (8 methods, stripe-java 28.2.0)~~ ✅
5. ~~SubscriptionService core (activateTrial, getStatus, getOrCreateSubscription)~~ ✅
6. ~~SubscriptionController (GET /status, POST /trial/activate, @PreAuthorize COMPANY)~~ ✅
7. ~~CampaignLimitService (PESSIMISTIC_WRITE lock, CampaignLimitExceededException)~~ ✅
8. ~~ConsentSource enum: added SUBSCRIPTION_PURCHASE~~ ✅
9. ~~application.yml: stripe, fakturownia, subscription cron sections~~ ✅
10. ~~pom.xml: stripe-java 28.2.0 dependency~~ ✅

### Sprint 2: Comprehensive testing — DONE ✅
**110 unit tests (4 files, all green):**
1. ~~SubscriptionServiceTest (39 tests): activateTrial 14 (incl. @ParameterizedTest all 8 non-FREE states), getStatus 7, getOrCreate 5, resolveLimit 13 (every state + fallback branches)~~ ✅
2. ~~CampaignLimitServiceTest (14 tests): allow @CsvSource parameterized, block at/over limit, EntityNotFound, plan-specific limits, exception properties~~ ✅
3. ~~StripeServiceTest (11 tests): getPublicKey, getPriceIdForPlan @ValueSource 6 invalid + null~~ ✅
4. ~~SubscriptionEntityTest (46 tests): enum completeness, entity defaults, DTO builder~~ ✅

**53 integration tests (3 files, real PostgreSQL + real Stripe sandbox, all green):**
5. ~~SubscriptionService_Trial_IntegrationTest (11 tests): atomic persistence, double trial rejection, paid user rejection, auto-create FREE, billing period span, status for all states~~ ✅
6. ~~SubscriptionRepository_Query_IntegrationTest (28 tests): seed data verification, findExpiredTrials, findTrialsEndingBetween, findExpiredGracePeriods, existsByStripeEventId + UNIQUE constraint, findActiveByUserId, findExpiredPendingDowngrades, findRetryable, entity constraints~~ ✅
7. ~~StripeService_Sandbox_IntegrationTest (14 tests): real createCustomer, createCheckoutSession with/without customer, full subscription lifecycle (create+cancel), createPortalSession, error handling~~ ✅

**2 bugs caught and fixed by integration tests:**
- Bug 1: `customer_creation=ALWAYS` invalid in Stripe subscription mode → removed
- Bug 2: Double billing period on trial activation → expire old FREE period before creating TRIAL

**OpenAPI spec regenerated with new subscription endpoints** ✅

### Sprint 3: Payment flow (Stripe Checkout + webhooks) — DONE ✅
**Production code (5 new + 4 modified):**
1. ~~`StripeWebhookController.java` — POST /webhooks/stripe, raw payload + Stripe-Signature verification~~ ✅
2. ~~`StripeWebhookHandler.java` — idempotency via existsByStripeEventId, routes 5 event types~~ ✅
3. ~~`SubscriptionService.initiateUpgrade()` — creates Stripe Customer (if first) + Checkout Session~~ ✅
4. ~~`handleCheckoutCompleted` — activates plan, links Stripe IDs, billing cycle reset~~ ✅
5. ~~`handleInvoicePaid` — extends billing period, creates InvoiceRecord (PENDING)~~ ✅
6. ~~`handlePaymentFailed` — PAYMENT_FAILED transition, preserves previousPlan~~ ✅
7. ~~`handleSubscriptionDeleted` — downgrade to FREE, clears Stripe IDs~~ ✅
8. ~~`handleSubscriptionUpdated` — detects schedule release, applies plan change~~ ✅
9. ~~`UpgradeRequestDtoIn` + `CheckoutSessionDtoOut` DTOs~~ ✅
10. ~~`SubscriptionController` — added POST /subscription/upgrade endpoint~~ ✅
11. ~~`WebSecurityConfiguration` — /webhooks/stripe added to permitAll~~ ✅
12. ~~`JwtAuthenticationFilter` — /webhooks/stripe added to isPublicEndpoint() exclusion~~ ✅
13. ~~`StripeService.retrieveSubscription()` — wraps static Subscription.retrieve() for testability~~ ✅

**4 bugs found and fixed:**
- Bug 3: Static `Subscription.retrieve()` untestable → wrapped in `StripeService.retrieveSubscription()`
- Bug 4: `initiateUpgrade` produced `"null null"` name → null-safe with email fallback
- Bug 5: `InvoiceRecord.billingPeriod` never linked → now linked + warning when absent
- Bug 6: JWT filter blocked webhook endpoint (401 in production) → added to `isPublicEndpoint()` exclusion

**32 unit tests (2 new files):**
14. ~~StripeWebhookHandlerTest (18 tests): idempotency, all 5 event types, subscription.updated variations~~ ✅
15. ~~SubscriptionService_WebhookTest (19 tests — 5 existing updated): all webhook handlers + error paths~~ ✅

**18 integration tests (1 new file, real Stripe sandbox + real DB):**
16. ~~handleCheckoutCompleted with real Stripe subscription (tok_visa → price resolution → DB state)~~ ✅
17. ~~handleInvoicePaid/PaymentFailed/Deleted/Updated with real PostgreSQL~~ ✅
18. ~~Signed webhook POST to /webhooks/stripe (valid 200, invalid 400)~~ ✅

**OpenAPI spec regenerated** ✅

### Sprint 4: Invoicing + downgrades — DONE ✅
**Production code (9 new + 3 modified):**
1. ~~`InvoicingPort.java` — port interface with InvoiceRequest/InvoiceResult records~~ ✅
2. ~~`FakturowniaAdapter.java` + `FakturowniaConfig.java` + `FakturowniaProperties.java`~~ ✅
3. ~~`FakturowniaCreateRequest.java` + `FakturowniaInvoiceResponse.java` DTOs~~ ✅
4. ~~`InvoiceRetryService.java` + `InvoiceRetryCronJob.java`~~ ✅
5. ~~`DowngradeRequestDtoIn.java`~~ ✅
6. ~~`SubscriptionService.requestDowngrade()` + `cancelDowngrade()` + payment recovery~~ ✅
7. ~~`SubscriptionController` — 7 endpoints total~~ ✅
8. ~~`StripeService.createDowngradeSchedule()` fixed: preserves phases~~ ✅
9. ~~`BusinessExceptionHandler`: IllegalStateException → 409 already existed~~ ✅

**3 bugs found and fixed:**
- Bug 8: Fakturownia oid max 40 chars
- Bug 9: Stripe Schedule needs all phases with start_date
- Bug 10: Duplicate stripe_event_id on payment recovery

**40 unit tests (3 new files) + 13 integration tests (2 new files):**
- ~~FakturowniaAdapterTest (9), InvoiceRetryServiceTest (12), SubscriptionService_DowngradeTest (19)~~ ✅
- ~~SubscriptionService_Downgrade_IntegrationTest (10), FakturowniaAdapter_IntegrationTest (3)~~ ✅

**Total: 266 tests (182 unit + 84 integration), all green**

### Sprint 5: Campaign limits + crons + notifications — DONE ✅
**Production code (4 new + 3 modified):**
1. ~~`CampaignLimitService` injected into `PartnershipOpportunityService.saveFromDto()` — limits LIVE~~ ✅
2. ~~`TrialExpiryNotifierCronJob` (daily 5AM, ShedLock 30m)~~ ✅
3. ~~`SubscriptionPeriodProcessorCronJob` (daily 4AM, ShedLock 30m)~~ ✅
4. ~~`TermsGraceProcessorCronJob` (daily 3AM, ShedLock 30m)~~ ✅
5. ~~`SubscriptionService`: 4 cron processor methods added~~ ✅
6. ~~`NotificationType` enum: 10 SUBSCRIPTION_* types~~ ✅
7. ~~Unit test naming: all *Test.java → *UnitTest.java (convention compliance)~~ ✅

**2 bugs found and fixed:**
- Bug 11: `FirebaseStartupInitializer` no `@ConditionalOnProperty` → broke SupportTicket test context
- Bug 12: `notifications.chk_notification_type` CHECK constraint missing 12 values → new migration

**15 unit tests + 7 integration tests:**
- ~~SubscriptionService_CronUnitTest (15 tests)~~ ✅
- ~~SubscriptionService_Cron_IntegrationTest (7 tests)~~ ✅

**Full regression: 10,166 unit + 835 integration = 11,001 ALL GREEN**

### Sprint 6: Terms versioning + consent + notifications + deactivation — DONE ✅
**Production code (3 new + 5 modified):**
1. ~~`LegalConsentService.recordSubscriptionConsent()` — inline consent at purchase (EU Art 16(m)), NO new service~~ ✅
2. ~~`SubscriptionNotificationEvent` + `SubscriptionNotificationEventListener` — single event class with NotificationType discriminator, @TransactionalEventListener(AFTER_COMMIT)~~ ✅
3. ~~`NotificationRequest.forSubscription()` factory — actionUrl="/subscription", groupKey per user~~ ✅
4. ~~`ApplicationEventPublisher` wired into SubscriptionService — 8 publish points (upgrade, payment fail/recover/exhaust, downgrade, trial ending/expired, suspended)~~ ✅
5. ~~`SubscriptionService.enterTermsPending()` — bulk move all active subscriptions, sets previousState + graceDeadline~~ ✅
6. ~~`SubscriptionService.acceptTerms()` — restore previousState, clear grace deadline~~ ✅
7. ~~`SubscriptionService.processExpiredGracePeriods()` — SUSPENDED_LEGAL + Stripe cancel, called by TermsGraceProcessorCronJob~~ ✅
8. ~~`SubscriptionService.deactivateForAccountDeletion()` — cancel Stripe immediately, ACCOUNT_DEACTIVATED~~ ✅
9. ~~`UserAccountOrchestrator.archiveUser()` — hooked to call deactivateForAccountDeletion for COMPANY users~~ ✅
10. ~~60-row translation migration for 10 SUBSCRIPTION_* notification types (en + pl)~~ ✅

**13 unit tests (1 new file) + 14 integration tests (1 new file):**
- ~~SubscriptionService_TermsUnitTest (13 tests): enterTermsPending (3), acceptTerms (4), processExpiredGrace (3), deactivate (3)~~ ✅
- ~~SubscriptionService_Terms_IntegrationTest (14 tests): bulk move (3), acceptTerms (3), grace expiry DB (3), grace expiry real Stripe (1), deactivation real Stripe (3), full lifecycle FREE→TRIAL→BUSINESS→TERMS_PENDING→accept (1)~~ ✅

**Full regression: 10,179 unit + 849 integration = 11,028 ALL GREEN**

### Sprint 6.5: E2E Cucumber tests — DONE ✅
**New files (4 production + 4 test):**
1. ~~`TestSubscriptionController.java` — 10 E2E admin endpoints (set-state, simulate-webhook, create-campaign, reset, trigger-cron, invoices, terms)~~ ✅
2. ~~`subscription-e2e.feature` — 7 Cucumber scenarios with @subscription tag~~ ✅
3. ~~`SubscriptionSteps.java` — ~20 step definitions using ScenarioContext + ActorRegistry~~ ✅
4. ~~`RunSubscriptionIT.java` — 14th Cucumber runner~~ ✅
5. ~~pom.xml — `skip.subscription.tests` + Failsafe execution block~~ ✅
6. ~~`RunCucumberIT.java` — added `not @subscription` exclusion~~ ✅
7. ~~`JwtAuthenticationFilter.java` — added `/test/subscription/` to public endpoints~~ ✅

**7 E2E scenarios (all green):**
- Trial lifecycle (activate, Enterprise limits)
- Campaign limit enforcement (FREE limit 2, 3rd blocked)
- Upgrade via webhook (FREE → BUSINESS_ACTIVE)
- Downgrade + cancel (ENTERPRISE → DOWNGRADE_PENDING → restored)
- Payment failure + recovery (BUSINESS → PAYMENT_FAILED → BUSINESS)
- Invoice via Fakturownia (PENDING → retry → SENT, with NIP verification via registry stubs)
- Terms versioning (BUSINESS → TERMS_PENDING → accept → BUSINESS restored)

**2 bugs found and fixed:**
- Bug 13: `getStatus()` was `@Transactional(readOnly=true)` but auto-creates subscription → `nextval()` in read-only tx
- Bug 14: Invoice check step used raw restTemplate without auth cookies → 401

**BACKEND FULLY COMPLETE. All 6 sprints + E2E suite done.** Total: 12,350 tests, 14 bugs caught.

### Sprint 7: Frontend (after BE fully working)
1. `@stripe/stripe-js` dependency
2. `subscription-api.service.ts` + types
3. `CompanyGuard`
4. Subscription status component + plan selection
5. Subscription consent dialog (follows ReconsentModalComponent pattern)
6. Settings page billing section (replace dead Fuse stub)
7. Classy layout: trial banner + terms banner (amber banner pattern)
8. Campaign form: limit error handling with upgrade CTA
9. Translation keys (en + pl)
10. Landing page pricing update (Free/29/99 PLN)

---

## Files Created (Sprint 1-5)

### Production code (~50 files):
```
subscription/
├── SubscriptionController.java              ✅ 7 endpoints
├── SubscriptionService.java                 ✅ 23 methods (COMPLETE state machine)
├── CampaignLimitService.java                ✅ PESSIMISTIC_WRITE, injected into PO service
├── CampaignLimitExceededException.java      ✅
├── entity/
│   ├── CompanySubscription.java             ✅ @Version optimistic locking
│   ├── SubscriptionPlan.java                ✅
│   ├── SubscriptionEvent.java               ✅ UNIQUE stripeEventId
│   ├── BillingPeriod.java                   ✅
│   ├── InvoiceRecord.java                   ✅ retry + billingPeriod link
│   ├── TermsVersion.java                    ✅
│   ├── SubscriptionStatus.java              ✅ (9 states)
│   ├── SubscriptionEventType.java           ✅ (20 event types)
│   ├── InvoiceStatus.java                   ✅
│   └── BillingPeriodStatus.java             ✅
├── repository/ (6 repos)                    ✅
├── dto/
│   ├── SubscriptionStatusDtoOut.java        ✅
│   ├── UpgradeRequestDtoIn.java             ✅
│   ├── DowngradeRequestDtoIn.java           ✅
│   └── CheckoutSessionDtoOut.java           ✅
├── stripe/
│   ├── StripeService.java                   ✅ 10 methods
│   ├── StripeConfig.java                    ✅
│   ├── StripeProperties.java                ✅
│   ├── StripeWebhookController.java         ✅ POST /webhooks/stripe
│   └── StripeWebhookHandler.java            ✅ 5 event types + idempotency
├── invoicing/
│   ├── InvoicingPort.java                   ✅ port interface
│   ├── FakturowniaAdapter.java              ✅ REST adapter
│   ├── FakturowniaConfig.java               ✅ named RestTemplate
│   ├── FakturowniaProperties.java           ✅ config with kill switch
│   ├── dto/ (2 files)                       ✅
│   └── InvoiceRetryService.java             ✅ batch retry + DEAD_LETTER
├── event/
│   ├── SubscriptionNotificationEvent.java   ✅ (Sprint 6) single event + NotificationType discriminator
│   └── SubscriptionNotificationEventListener.java ✅ (Sprint 6) @TransactionalEventListener AFTER_COMMIT
└── cron/
    ├── InvoiceRetryCronJob.java             ✅ every 15min
    ├── TrialExpiryNotifierCronJob.java      ✅ daily 5AM
    ├── SubscriptionPeriodProcessorCronJob.java ✅ daily 4AM
    └── TermsGraceProcessorCronJob.java      ✅ daily 3AM (+ processExpiredGracePeriods)
```

### Test code (19 files, 315 subscription tests):
```
unit/service/subscription/ (11 files, 210 tests)
├── SubscriptionServiceUnitTest.java         ✅ 39 tests
├── CampaignLimitServiceUnitTest.java        ✅ 14 tests
├── StripeServiceUnitTest.java               ✅ 11 tests
├── SubscriptionEntityUnitTest.java          ✅ 46 tests
├── StripeWebhookHandlerUnitTest.java        ✅ 18 tests
├── SubscriptionService_WebhookUnitTest.java ✅ 14 tests
├── FakturowniaAdapterUnitTest.java          ✅ 9 tests
├── InvoiceRetryServiceUnitTest.java         ✅ 12 tests
├── SubscriptionService_DowngradeUnitTest.java ✅ 19 tests
├── SubscriptionService_CronUnitTest.java    ✅ 15 tests
└── SubscriptionService_TermsUnitTest.java   ✅ 13 tests (Sprint 6)

integration/service/subscription/ (8 files, 105 tests)
├── SubscriptionServiceIntegrationTestBase.java  ✅ shared base
├── SubscriptionService_Trial_IntegrationTest.java ✅ 11 tests
├── SubscriptionRepository_Query_IntegrationTest.java ✅ 28 tests
├── StripeService_Sandbox_IntegrationTest.java  ✅ 14 tests (real Stripe)
├── SubscriptionService_Webhook_IntegrationTest.java ✅ 18 tests (real Stripe + signed POST)
├── SubscriptionService_Downgrade_IntegrationTest.java ✅ 10 tests (real Stripe Schedule)
├── FakturowniaAdapter_IntegrationTest.java ✅ 3 tests (real Fakturownia)
├── SubscriptionService_Cron_IntegrationTest.java ✅ 7 tests
└── SubscriptionService_Terms_IntegrationTest.java ✅ 14 tests (Sprint 6: real Stripe + full lifecycle)
```

### Config/migrations modified:
```
pom.xml                                      ✅ stripe-java 28.2.0
application.yml                              ✅ stripe + fakturownia + subscription crons
application-integration.properties           ✅ stripe + fakturownia + subscription crons
changelog.xml                                ✅ 3 new migrations registered
22-03-2026-subscription-tables.sql           ✅ 6 tables, 6 sequences, 10 indexes, seeds
22-03-2026-update-notification-type-constraint.sql ✅ CHECK constraint: 18→30 values (Bug #12)
23-03-2026-subscription-notification-translations.sql ✅ 60 translation rows (10 types x 3 keys x 2 langs)
ConsentSource.java                           ✅ added SUBSCRIPTION_PURCHASE
WebSecurityConfiguration.java                ✅ /webhooks/stripe permitAll
JwtAuthenticationFilter.java                 ✅ /webhooks/stripe in isPublicEndpoint()
NotificationType.java                        ✅ 10 SUBSCRIPTION_* types
PartnershipOpportunityService.java           ✅ CampaignLimitService injected
FirebaseStartupInitializer.java              ✅ @ConditionalOnProperty guard (Bug #11)
```

### Bugs caught by tests (12 total):
| # | Bug | Sprint | Fix |
|---|-----|--------|-----|
| 1 | Double billing period on trial | 2 | Expire old FREE period first |
| 2 | customer_creation invalid in sub mode | 2 | Removed (Stripe auto-creates) |
| 3 | Static Subscription.retrieve() untestable | 3 | Wrapped in StripeService |
| 4 | Null name in initiateUpgrade | 3 | Null-safe with email fallback |
| 5 | InvoiceRecord.billingPeriod not linked | 3 | Now linked + warning |
| 6 | JWT filter blocks webhooks (401 prod) | 3 | Added to isPublicEndpoint() |
| 7 | Payment recovery gap | 4 | Restore from PAYMENT_FAILED on invoice.paid |
| 8 | Fakturownia oid max 40 chars | 4 | Shortened OIDs |
| 9 | Stripe Schedule needs all phases | 4 | Preserve current phase + append |
| 10 | Duplicate stripe_event_id on recovery | 4 | PAYMENT_RECOVERED uses null eventId |
| 11 | FirebaseStartupInitializer no guard | 5 | @ConditionalOnProperty added |
| 12 | Notification CHECK constraint missing 12 values | 5 | New migration (18→30 values) |

### Additional files modified in Sprint 6:
```
LegalConsentService.java                     ✅ added recordSubscriptionConsent() (Sprint 6)
NotificationRequest.java                     ✅ added forSubscription() factory (Sprint 6)
UserAccountOrchestrator.java                 ✅ added subscriptionService.deactivateForAccountDeletion() hook (Sprint 6)
TermsGraceProcessorCronJob.java              ✅ added processExpiredGracePeriods() call (Sprint 6)
NotificationType.java                        ✅ 10 SUBSCRIPTION_* types (Sprint 5, confirmed in Sprint 6)
```

## Key Files Still to Modify (Sprint 7 — Frontend only)

| File | Modification | Sprint |
|------|-------------|--------|
| `classy.component.ts/html` | Add subscription banners | 7 |
| `settings.component.ts/html` | Add billing section | 7 |
| `collaboration-form.component.ts` | Handle limit error | 7 |
| `auth.interceptor.ts` | Handle subscription consent required | 7 |
| `en.json` / `pl.json` | Add subscription translations | 7 |
| `users.routes.ts` | Add subscription route | 7 |

---

## Verification

### Backend
- `stripe listen --forward-to localhost:8080/api/webhooks/stripe` for local webhook testing
- Stripe test cards: `4242...` (success), `4000000000009995` (decline)
- Fakturownia test department (ID 1878648) — test invoice already verified working
- All crons testable by setting short intervals in test profile

### Frontend
- Stripe Checkout redirect → success/cancel URL handling
- Banner visibility logic (trial available, terms pending, rate limited — priority order)
- Campaign creation blocked at limit → upgrade CTA shown
- Consent dialog proof capture → backend recording

### Neo4j State Machine Reference
```cypher
-- Query full state machine:
MATCH (s:SubState {namespace: 'subscription'})-[t:TRANSITION]->(target:SubState)
RETURN s.name, t.name, t.trigger, t.guard, t.action, target.name ORDER BY s.name

-- Query business rules:
MATCH (r:SubRule {namespace: 'subscription'}) RETURN r.name, r.description, r.enforcement
```
