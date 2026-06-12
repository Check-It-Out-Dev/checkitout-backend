# Stripe Payment Gateway - Requirements

<!--
  STATE_MACHINE_GRAPH:
  The fully modeled subscription state machine (10 states, 56 transitions, 8 rules, 3 design decisions)
  is persisted in Neo4j under namespace: 'subscription'

  To query the full state machine:
    MATCH (s:SubState {namespace: 'subscription'})-[t:TRANSITION]->(target:SubState)
    RETURN s.name, t.name, t.trigger, t.guard, t.action, target.name ORDER BY s.name

  To query business rules:
    MATCH (r:SubRule {namespace: 'subscription'}) RETURN r.name, r.description, r.enforcement

  To query design decisions:
    MATCH (d:SubDesignDecision {namespace: 'subscription'}) RETURN d.id, d.description

  To query corner cases (all resolved):
    MATCH (h:SubCornerCase {namespace: 'subscription'}) RETURN h.id, h.severity, h.status, h.title

  Node labels in namespace 'subscription':
    SubState, SubRule, SubDesignDecision, SubCornerCase, SubActor, SubExtSystem
-->

## 1. Subscription Plans

| Plan | Price | Campaign Limit (per billing period) |
|------|-------|-------------------------------------|
| Free | 0 PLN | 2 |
| Business | 29 PLN/month | 5 |
| Enterprise | 99 PLN/month | 10 |

- Only **company accounts** can purchase subscriptions (not influencers, not admins)
- Billing period: always **1 calendar month** from subscription start date
- All new accounts start on **Free** plan upon activation

## 2. Free Trial

- **3-month Enterprise trial**, no card details required
- Available **once per company account**, at **any time** after account activation
- Condition: company must have **never had a paid subscription** AND `trial_used = false`
- `trial_used` flag set to `true` on trial activation, **never unset** (survives suspension/reactivation)
- Trial activation is **purely backend** — no Stripe involvement
- During trial, company gets full Enterprise limits (10 campaigns/period)
- Company can **upgrade to paid** during trial — trial ends, billing starts immediately
- Company can **cancel trial early** — returns to Free
- After trial expiry: **auto-downgrade to Free** (cron)
- UI: Banner/CTA to activate free Enterprise trial

## 3. Trial Expiry Notifications

- **Daily cron job** checks trial end dates
- Generates **in-app notifications + emails** at:
  - 14 days before trial end
  - 7 days before trial end
  - 1 day before trial end

## 4. Upgrade Flow

1. Company selects target plan (Business or Enterprise)
2. **Immediate activation consent checkbox** (EU Article 16(m)):
   - Text: Consumer expressly consents to immediate service activation and acknowledges loss of 14-day withdrawal right
   - Checkbox click generates **HMAC-signed cookie** (`immediate_activation_consent`) using `CONSENT_HMAC_SECRET` from .env
3. Redirect to **Stripe Checkout Session**
4. On `checkout.session.completed` webhook:
   - Validate HMAC consent cookie
   - Store consent in `immediate_activation_consent` table
   - Activate subscription
   - **Reset billing cycle** (`billing_cycle_anchor: "now"`) — Stripe credits unused old plan time, charges full new plan price
   - **Reset campaign count** (new billing period = fresh quota)
   - Create invoice via **Fakturownia API**
5. Stripe Customer object created **lazily** on first paid subscription, `stripe_customer_id` stored on company record
6. Upgrade also possible from **DOWNGRADE_PENDING** (cancels pending downgrade) and **PAYMENT_FAILED** (cancels old failed subscription, creates new one)

## 5. Downgrade Flow

- Company can request downgrade at **any time**
- **No proration, no correction invoices** — immediate activation consent covers this
- Current plan remains active **until end of current billing period** (Netflix model)

### Downgrade to Free
- Stripe subscription set to `cancel_at_period_end: true`
- After period ends: **cron applies downgrade** to Free

### Downgrade Enterprise to Business
- **Consent collected at downgrade request time** (not at period end)
- **Stripe Subscription Schedule** created: Enterprise now → Business at period end
- Stripe auto-switches price at period end, fires `customer.subscription.updated` webhook
- No cron needed — Stripe handles the switch

### Cancel Downgrade
- User can **cancel pending downgrade** before period ends → restores previous plan
- User can **upgrade from DOWNGRADE_PENDING** via new checkout → overrides pending downgrade

## 6. Immediate Activation Consent (Legal)

- Legal basis: **EU Consumer Rights Directive, Article 16(m)**
- Requirements:
  1. Prior express consent to begin performance immediately
  2. Acknowledgment of losing 14-day withdrawal right
  3. Confirmation of contract provided
- **Consent document** versioned in DB with content hash (new migration, mock hash initially)
- Each consent checkbox generates **HMAC-signed cookie** (tamper-proof, timestamped)
- Cookie persisted to `immediate_activation_consent` table **only on successful payment**
- Shown on: **upgrade**, **initial subscription purchase**, and **Enterprise-to-Business downgrade request** (not on recurring payments)

## 7. Recurring Payments

- Handled by **Stripe** automatically
- On `invoice.paid` webhook:
  - Extend billing period
  - Create invoice via Fakturownia API
- On `invoice.payment_failed` webhook:
  - Mark payment as failed
  - Send notification to user ("Update your payment method")
- Stripe auto-retries failed payments for **~3 weeks**
- During retry period: **service continues**, campaign limit from **previous plan**
- After **all retries exhausted** (`customer.subscription.deleted`):
  - Downgrade to **Free**
  - Send notification + email

## 8. Campaign Creation Enforcement

- On every campaign creation request:
  1. Get company's current subscription status
  2. Determine campaign limit:
     - `FREE_ACTIVE`: 2, `BUSINESS_ACTIVE`: 5, `ENTERPRISE_ACTIVE`/`TRIAL_ENTERPRISE`: 10
     - `DOWNGRADE_PENDING`/`PAYMENT_FAILED`/`TERMS_PENDING`: use **previous_plan.campaign_limit**
     - `SUSPENDED_LEGAL`: **0** (hard block all creation)
  3. Get company's current billing period (start_date, end_date)
  4. Count campaigns created by this company within current billing period
  5. If count >= plan limit → **hard block** with message: "Upgrade to create more campaigns"
- Existing campaigns from previous billing periods are **never touched**

## 9. Invoicing (Fakturownia)

- Invoices generated **only for paid plans**
- **VAT exempt**: `tax: "zw"`, legal basis: `Art. 113 ust. 1 ustawy o VAT`
- API: `POST /invoices.json` to `{domain}.fakturownia.pl`
- API key: `FAKTUROWNIA_API_KEY` from .env
- **Test department** — no auto-send to KSeF, but with validation on manual send
- Buyer data: company's NIP, name, address (already available via public APIs in the app)
- Invoice created on each successful payment (`checkout.session.completed` and `invoice.paid`)
- Invoice record tracked in local DB with `fakturownia_invoice_id`

## 10. Stripe Integration Details

- **Sandbox/test mode** with existing keys from .env:
  - `STRIPE_PUBLIC_KEY` (pk_test_...)
  - `STRIPE_PRIVATE_KEY` (sk_test_...)
- **Stripe Checkout Sessions** for payment flow
- **Stripe Subscription Schedules** for deferred downgrades (Enterprise → Business)
- **Stripe hosted Customer Portal** for card management & invoice history
- Stripe Customer created lazily, `stripe_customer_id` on company record
- Server-side subscription cancellation via `DELETE /v1/subscriptions/{id}` (for deactivation, suspension, payment exhaustion)

### Webhooks to implement

| Webhook | Action |
|---------|--------|
| `checkout.session.completed` | Validate consent, activate subscription, reset billing cycle, create Fakturownia invoice |
| `invoice.paid` | Extend billing period, create Fakturownia invoice |
| `invoice.payment_failed` | Mark failed, notify user to update payment method |
| `customer.subscription.deleted` | Downgrade to Free, notify + email |
| `customer.subscription.updated` | Handle Subscription Schedule phase change (downgrade applied) |

### Stripe API mechanisms validated

| Mechanism | Stripe API | Verified |
|-----------|-----------|----------|
| Checkout Sessions for subscription | `POST /checkout/sessions` mode: subscription | Yes |
| Billing cycle reset on upgrade | `billing_cycle_anchor: "now"` on subscription update | Yes |
| Deferred downgrade | Subscription Schedules with phases | Yes |
| Cancel at period end | `cancel_at_period_end: true` | Yes |
| Immediate cancel (our end) | `DELETE /subscriptions/{id}` | Yes |
| Customer Portal | `POST /billing_portal/sessions` | Yes |

## 11. Subscription State Machine

**10 states, 56 transitions — fully modeled in Neo4j namespace `subscription`**

```
                        ACCOUNT_CREATED
                              | (activate)
                              v
  +------ FREE_ACTIVE <------------------------------+-----------------------------+
  |            |    |                                 |                             |
  |     activate   upgrade                    trial_expired /               accept_terms +
  |       trial     (pay)                    downgrade_applied /              reactivate
  |            |    |                        payment_exhausted                      |
  |            v    |                                 |                             |
  |   TRIAL_ENTERPRISE ---+                           |                   SUSPENDED_LEGAL
  |     |    |    |       |                           |                        ^
  |  cancel upgrade  new_terms                        |                        |
  |  trial  (pay)     |                               |                 grace_expired
  |     |    |        v                               |                        |
  |     |    |   TERMS_PENDING -----(accept)----> [previous_state]       TERMS_PENDING
  |     |    |        |                               ^                   (self-loops:
  |     |    |   (grace expires)                      |                    invoice.paid,
  |     |    |        v                          (accept terms)            payment_failed,
  |     |    |   SUSPENDED_LEGAL                      |                    trial_expired,
  |     |    |                                   TERMS_PENDING              sub.updated)
  |     +----+                                        ^
  |          |                                        |
  |          v                                   new_terms_published
  |   BUSINESS_ACTIVE --(upgrade)--> ENTERPRISE_ACTIVE
  |       |    |    |                    |    |    |
  |    renew  fail downgrade          renew fail downgrade
  |       |    |    |                    |    |    |
  |       v    v    v                    v    v    v
  |    (self) PAYMENT  DOWNGRADE      (self) PAYMENT  DOWNGRADE
  |           FAILED    PENDING              FAILED    PENDING
  |             |          |                   |          |
  |          recover    applied              recover   applied
  |          exhaust    cancel               exhaust   cancel
  |             |       upgrade                |       upgrade
  |             v          v                   v          v
  |         [plan]    [target/new]          [plan]   [target/new]
  |
  +---- All states --(deactivate)--> ACCOUNT_DEACTIVATED (terminal)
```

### States

| State | Campaign Limit | Description |
|-------|---------------|-------------|
| `ACCOUNT_CREATED` | n/a | Initial, not yet activated |
| `FREE_ACTIVE` | 2 | Free plan |
| `TRIAL_ENTERPRISE` | 10 | 3-month free trial |
| `BUSINESS_ACTIVE` | 5 | Paid Business plan |
| `ENTERPRISE_ACTIVE` | 10 | Paid Enterprise plan |
| `DOWNGRADE_PENDING` | previous_plan | Downgrade requested, old plan active until period end |
| `PAYMENT_FAILED` | previous_plan | Stripe retrying, service continues |
| `TERMS_PENDING` | previous_plan | 38-day grace period for new terms acceptance |
| `SUSPENDED_LEGAL` | 0 | Suspended, must accept terms to reactivate |
| `ACCOUNT_DEACTIVATED` | n/a | Terminal, account deleted |

### Key fields on `company_subscription`

| Field | Purpose |
|-------|---------|
| `current_plan` | FK to subscription_plan |
| `previous_plan` | FK — campaign limit source for transitional states |
| `previous_state` | Enum — for TERMS_PENDING restore target |
| `status` | Current state machine state |
| `version` | Optimistic locking — all transitions in DB transaction |
| `trial_used` | Boolean, set true on trial start, never unset |
| `newest_terms_accepted` | Boolean, per company |
| `grace_deadline` | Timestamp, terms_shown_date + 38 days |

## 12. Terms & Pricing Versioning

- All subscriptions are **max 1 month** — any pricing change can take effect within one cycle
- Admin publishes new terms/pricing → all companies enter **TERMS_PENDING**
- Flag `newest_terms_accepted = false` set on company
- **38-day grace period** (> 30 days, so all subscriptions burn out before enforcement)
- During grace: **persistent amber banner** on every login (no cron notifications needed)
- During grace: Stripe **keeps billing at old price**, webhooks handled as self-loops
- If trial expires during grace: `previous_state` updated from TRIAL to FREE
- If Stripe Schedule fires during grace: `previous_state` updated to new plan
- **User accepts terms** → restored to `previous_state`, next renewal at new Stripe Price
- **Grace expires without acceptance** → Stripe subscription cancelled immediately → `SUSPENDED_LEGAL`
- From `SUSPENDED_LEGAL`: user accepts terms → `FREE_ACTIVE` (must re-purchase paid plan)
- **Must accept terms before any purchase** (intentional friction, legal compliance)

### Design Decisions

| ID | Decision |
|----|----------|
| DECISION-001 | Must accept new terms before any purchase. Cannot upgrade in TERMS_PENDING. |
| DECISION-002 | Suspension consumes trial entitlement. `trial_used` never unset. |
| DECISION-003 | Grace period notification = persistent amber banner, no cron emails. |

## 13. Database Tables (new)

### `subscription_plan` (static/seed data)
- id, name (FREE/BUSINESS/ENTERPRISE), price_pln, campaign_limit, active

### `company_subscription`
- id, company_id (FK), current_plan (FK), previous_plan (FK, nullable), status (enum), previous_state (enum, nullable), trial_end_date, trial_used, stripe_customer_id, stripe_subscription_id, stripe_schedule_id (nullable), newest_terms_accepted, grace_deadline (nullable), version, created_at, updated_at

### `subscription_event`
- id, company_id (FK), event_type (enum), plan_from, plan_to, stripe_event_id (nullable), billing_period_start, billing_period_end, created_at

### `billing_period`
- id, company_id (FK), plan (FK), start_date, end_date, status (ACTIVE/EXPIRED/PENDING_DOWNGRADE)

### `immediate_activation_consent`
- id, company_id (FK), consent_document_id (FK), hmac_hash, stripe_session_id, created_at

### `consent_document`
- id, type, version, content_hash (mock hash initially), created_at

### `invoice_record`
- id, company_id (FK), billing_period_id (FK), fakturownia_invoice_id (nullable — set on success), type (STANDARD), amount_pln, status (PENDING/SENT/FAILED), retry_count (default 0), max_retries (default 5), error_message (nullable), last_attempt_at (nullable), created_at

### `terms_version`
- id, version, content_hash, pricing_snapshot (JSON), published_at, grace_period_days (38), created_at

## 14. Cron Jobs

| Cron | Schedule | Action |
|------|----------|--------|
| Trial expiry notifier | Daily | Check trials ending in 14d/7d/1d, create notifications + send emails |
| Subscription period processor | Daily | Expire trials → downgrade to Free; Apply pending downgrades to Free when period ends; Downgrade accounts with exhausted payment retries |
| Terms grace processor | Daily | Check TERMS_PENDING with grace_deadline <= now → cancel Stripe, set SUSPENDED_LEGAL; Check trial expiry inside TERMS_PENDING → update previous_state |
| Fakturownia invoice retry | Every 15 min | Pick invoice_record WHERE status IN (PENDING, FAILED) AND retry_count < max_retries AND (last_attempt_at IS NULL OR last_attempt_at < now - 15min). Attempt POST to Fakturownia. On success: status=SENT, store fakturownia_invoice_id. On failure: status=FAILED, retry_count++, store error_message. After max_retries: status=DEAD_LETTER, create alert/notification for admin. |

## 15. Business Rules

| Rule | Enforcement | Description |
|------|-------------|-------------|
| CAMPAIGN_LIMIT_CHECK | HARD_BLOCK | Count campaigns in billing period, block if >= limit |
| CAMPAIGN_COUNT_ATOMICITY | HARD_BLOCK | `SELECT FOR UPDATE` on billing_period row, count + insert in single transaction. Prevents race condition where concurrent requests both pass limit check. |
| TRIAL_ONCE_ONLY | HARD_BLOCK | trial_used=false AND never had paid subscription |
| CONSENT_REQUIRED_ON_PURCHASE | HARD_BLOCK | HMAC cookie required before Stripe Checkout |
| COMPANY_ONLY_PURCHASE | HARD_BLOCK | Only company accounts can buy |
| WEBHOOK_SIGNATURE_VERIFICATION | HARD_BLOCK | Validate `Stripe-Signature` header using `STRIPE_WEBHOOK_SECRET` before any processing. Reject 400 if invalid. First layer of defense. |
| WEBHOOK_IDEMPOTENCY | HARD_BLOCK | Check `stripe_event_id` in `subscription_event` table before processing. If exists, return 200 OK and skip. Prevents duplicate state transitions and duplicate invoices. |
| STATE_TRANSITION_OPTIMISTIC_LOCK | HARD_BLOCK | Every state transition: `UPDATE company_subscription SET status=:new, version=version+1 WHERE id=:id AND version=:expected AND status=:current`. If 0 rows → transition preempted, retry or reject. |
| BILLING_CYCLE_RESET_ON_UPGRADE | AUTO | Reset anchor, fresh campaign count |
| DOWNGRADE_DEFERRED | AUTO | Old plan active until period end |
| INVOICE_ON_PAYMENT_ONLY | AUTO | Fakturownia invoice only for paid plans |
| PAYMENT_FAILURE_GRACE | AUTO | Service continues during Stripe retry (~3 weeks) |
| FAKTUROWNIA_ASYNC_WITH_RETRY | AUTO | Invoice creation MUST NOT block subscription flow. Fire-and-forget with async retry cron. `invoice_record.status`: PENDING → SENT or FAILED → retry up to max_retries → DEAD_LETTER + admin alert. |

## 16. Stripe Products/Prices (sandbox)

- Product: "checkItOut Business" → Price: 29.00 PLN/month
- Product: "checkItOut Enterprise" → Price: 99.00 PLN/month
- Store Stripe Price IDs in application config

## 17. Account Deactivation

- Reachable from **every non-terminal state** via user self-delete or admin action
- Stripe subscription cancelled **immediately** from server side (`DELETE /v1/subscriptions/{id}`)
- Any Stripe Schedule cancelled
- `subscription_event` created with type `ACCOUNT_DEACTIVATED`

## Verification

- Use Stripe CLI (`stripe listen --forward-to localhost:8080/api/webhooks/stripe`) to test webhooks locally
- Create test company account → activate trial → verify 10 campaign limit
- Cancel trial early → verify return to Free with 2 campaign limit
- Let trial expire (or mock) → verify downgrade to Free → verify 2 campaign limit
- Purchase Business plan → verify Stripe Checkout → verify webhook → verify Fakturownia invoice
- Upgrade Business → Enterprise → verify billing cycle reset + new invoice
- Downgrade Enterprise → Business → verify Stripe Schedule created → verify switch at period end
- Cancel pending downgrade → verify restore to original plan
- Downgrade to Free → verify plan active until period end → verify cron applies downgrade
- Simulate payment failure via Stripe test cards → verify notifications → verify eventual downgrade
- Upgrade from PAYMENT_FAILED with new card → verify old subscription cancelled, new one created
- Publish new terms → verify all companies enter TERMS_PENDING with amber banner
- Accept terms → verify restore to previous state, next renewal at new price
- Let grace expire → verify SUSPENDED_LEGAL, Stripe cancelled
- Reactivate from SUSPENDED_LEGAL → verify FREE_ACTIVE, trial_used still true
- Deactivate account → verify Stripe subscription cancelled immediately

---

# Appendix A — As built (2026-06)

The requirements above shipped. The flow as it runs today: a company hits
`SubscriptionPaidController` (`POST /upgrade` creates a Stripe Checkout Session on
first purchase, updates the subscription price in place afterwards) and **nothing is
written locally until Stripe confirms**. Confirmation lands on
`StripeWebhookController` (HMAC `Stripe-Signature` verification) and
`StripeWebhookHandler` routes it through three idempotency layers — fast-path
`existsByStripeEventId`, a UNIQUE constraint on `SubscriptionEvent.stripe_event_id`
that settles concurrent duplicate deliveries, and `@Version` optimistic locking on the
`CompanySubscription` aggregate. `SubscriptionService` drives the nine-state status
machine, extends the billing period, and persists a PENDING `InvoiceRecord`; its
AFTER_COMMIT event triggers an immediate send through `InvoicingPort` →
`FakturowniaAdapter` (art. 113 VAT-exempt, provider-side `oid_unique` dedup). Failures
retry every 15 minutes (`InvoiceRetryCronJob`, ShedLock) until `maxRetries=5` flips the
record to DEAD_LETTER. `SubscriptionPeriodProcessorCronJob` handles trial expiry,
pending downgrades and FREE rollover daily; `CampaignLimitService` enforces plan quotas
under a pessimistic billing-period lock. The entire paid surface hangs on one flag —
`app.payments.enabled=false` deregisters the paid controller, webhook, listeners and
invoicing cron at the bean level (FREE housekeeping keeps running), and
`PaymentsDisabledBootGuard` fail-fasts startup if any row is stranded mid-paid-flow.

# Appendix B — Sandbox wiring & verification

How to verify your own Stripe + Fakturownia sandbox end-to-end (this exact procedure
was used to verify the production pipeline).

## Stripe (test mode)

Create two recurring PLN prices (Business / Enterprise) in your Stripe test dashboard
and put their IDs in configuration. Test cards (any future expiry, any CVC):

| Card | Scenario |
|---|---|
| `4242 4242 4242 4242` | Successful payment |
| `4000 0000 0000 3220` | 3D Secure required |
| `4000 0000 0000 9995` | Declined — insufficient funds |
| `4000 0000 0000 0002` | Declined — generic |
| `4000 0000 0000 0069` | Expired card |

Webhook testing without a public URL:

```bash
stripe listen --forward-to localhost:8080/api/webhooks/stripe
stripe trigger checkout.session.completed
stripe trigger invoice.paid
stripe trigger invoice.payment_failed
stripe trigger customer.subscription.deleted
```

## Fakturownia (+ KSeF)

Create a fakturownia.pl account (your own subdomain), generate an API token and a
document key, and add a **TEST department** — issue development invoices only there.
KSeF runs in DEMO mode until you flip it; sending to KSeF is one API call
(`POST /invoices/{id}/gov_send.json`) or a flag on create.

Invoice template the adapter sends (VAT-exempt under art. 113):

```json
{
  "api_token": "${FAKTUROWNIA_API_KEY}",
  "invoice": {
    "kind": "vat",
    "department_id": "<your TEST department id>",
    "seller_name": "<your company>",
    "seller_tax_no": "<your NIP>",
    "buyer_name": "${company.name}",
    "buyer_tax_no": "${company.nip}",
    "buyer_company": true,
    "exempt_tax_kind": "art113",
    "positions": [{ "name": "checkItOut ${plan} — monthly subscription",
                    "quantity": 1, "total_price_gross": "${price}", "tax": "zw" }]
  }
}
```

Endpoint matrix the adapter relies on: `GET /departments.json`, `POST /invoices.json`,
`GET /invoices/{id}.json`, `POST /invoices/{id}/send_by_email.json`,
`GET /invoices/{id}.pdf`, `POST /invoices/{id}/gov_send.json` (KSeF).

## application.yml wiring

```yaml
stripe:
  public-key: ${STRIPE_PUBLIC_KEY}
  secret-key: ${STRIPE_PRIVATE_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  prices:
    business: price_...      # your test price IDs
    enterprise: price_...

fakturownia:
  api-key: ${FAKTUROWNIA_API_KEY}
  doc-key: ${FAKTUROWNIA_DOC_KEY}
  domain: your-subdomain     # -> https://your-subdomain.fakturownia.pl
  department-id: <test department id>
  exempt-tax-kind: art113
  seller:
    name: "<your company>"
    tax-no: "<your NIP>"
```
