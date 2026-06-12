# Subscription State Machine — checkItOut

9 states, service-driven (not a framework FSM): `SubscriptionService` mutates
`SubscriptionStatus` and appends a `SubscriptionEvent` audit row on every transition,
guarded by an `@Version` optimistic lock. Trial → Business/Enterprise → downgrade →
payment-failure → legal-suspension, with a 38-day terms-grace overlay.
Source of truth: [`docs/StripeGateway/REQUIREMENTS.md`](../StripeGateway/REQUIREMENTS.md) §11/§12/§15.

```mermaid
stateDiagram-v2
    direction TB
    [*] --> ACCOUNT_CREATED
    ACCOUNT_CREATED --> FREE_ACTIVE: activate

    FREE_ACTIVE --> TRIAL_ENTERPRISE: activate_trial (trial unused)
    FREE_ACTIVE --> BUSINESS_ACTIVE: upgrade + consent
    FREE_ACTIVE --> ENTERPRISE_ACTIVE: upgrade + consent

    TRIAL_ENTERPRISE --> FREE_ACTIVE: cancel / trial_expired
    TRIAL_ENTERPRISE --> BUSINESS_ACTIVE: upgrade
    TRIAL_ENTERPRISE --> ENTERPRISE_ACTIVE: upgrade

    BUSINESS_ACTIVE --> BUSINESS_ACTIVE: invoice.paid renew
    BUSINESS_ACTIVE --> ENTERPRISE_ACTIVE: upgrade
    BUSINESS_ACTIVE --> DOWNGRADE_PENDING: request_downgrade
    BUSINESS_ACTIVE --> PAYMENT_FAILED: payment_failed

    ENTERPRISE_ACTIVE --> ENTERPRISE_ACTIVE: invoice.paid renew
    ENTERPRISE_ACTIVE --> DOWNGRADE_PENDING: request_downgrade
    ENTERPRISE_ACTIVE --> PAYMENT_FAILED: payment_failed

    DOWNGRADE_PENDING --> FREE_ACTIVE: period_end (cron)
    DOWNGRADE_PENDING --> BUSINESS_ACTIVE: schedule fires
    DOWNGRADE_PENDING --> ENTERPRISE_ACTIVE: cancel_downgrade
    DOWNGRADE_PENDING --> BUSINESS_ACTIVE: upgrade overrides

    PAYMENT_FAILED --> ENTERPRISE_ACTIVE: invoice.paid recover
    PAYMENT_FAILED --> BUSINESS_ACTIVE: invoice.paid recover
    PAYMENT_FAILED --> FREE_ACTIVE: retries exhausted
    PAYMENT_FAILED --> ENTERPRISE_ACTIVE: upgrade new sub

    FREE_ACTIVE --> TERMS_PENDING: new_terms_published
    BUSINESS_ACTIVE --> TERMS_PENDING: new_terms_published
    ENTERPRISE_ACTIVE --> TERMS_PENDING: new_terms_published
    TRIAL_ENTERPRISE --> TERMS_PENDING: new_terms_published
    TERMS_PENDING --> FREE_ACTIVE: accept_terms
    TERMS_PENDING --> BUSINESS_ACTIVE: accept_terms
    TERMS_PENDING --> ENTERPRISE_ACTIVE: accept_terms
    TERMS_PENDING --> SUSPENDED_LEGAL: grace_expired

    SUSPENDED_LEGAL --> FREE_ACTIVE: accept_terms + reactivate

    FREE_ACTIVE --> ACCOUNT_DEACTIVATED: deactivate
    TRIAL_ENTERPRISE --> ACCOUNT_DEACTIVATED: deactivate
    BUSINESS_ACTIVE --> ACCOUNT_DEACTIVATED: deactivate
    ENTERPRISE_ACTIVE --> ACCOUNT_DEACTIVATED: deactivate
    DOWNGRADE_PENDING --> ACCOUNT_DEACTIVATED: deactivate
    PAYMENT_FAILED --> ACCOUNT_DEACTIVATED: deactivate
    TERMS_PENDING --> ACCOUNT_DEACTIVATED: deactivate
    SUSPENDED_LEGAL --> ACCOUNT_DEACTIVATED: deactivate
    ACCOUNT_DEACTIVATED --> [*]
```

**Terms-grace overlay.** `new_terms_published` moves any active state into `TERMS_PENDING` for a 38-day grace
window. Billing keeps running there (self-loops on `invoice.paid` / `payment_failed` / `trial_expired` /
`sub.updated`); `accept_terms` restores the previous tier. If the grace expires, Stripe is cancelled and the
account drops to `SUSPENDED_LEGAL` (0 campaigns) until the user accepts and reactivates.

## Campaign limits per state

| State | Active campaigns |
|---|---|
| FREE_ACTIVE | 2 |
| TRIAL_ENTERPRISE | 10 |
| BUSINESS_ACTIVE | 5 |
| ENTERPRISE_ACTIVE | 10 |
| DOWNGRADE_PENDING / PAYMENT_FAILED / TERMS_PENDING | previous plan's limit |
| SUSPENDED_LEGAL | 0 |
