# System Architecture — checkItOut

The platform behind the subscription + invoicing pipeline. Six behavioural layers
(Controller · Security · Implementation · Lifecycle · External · Config), modelled
from the source-of-truth in [`docs/StripeGateway/REQUIREMENTS.md`](../StripeGateway/REQUIREMENTS.md).

```mermaid
graph TD
    classDef ctrl fill:#1e3a8a,stroke:#1e40af,color:#fff
    classDef impl fill:#065f46,stroke:#047857,color:#fff
    classDef sec  fill:#7f1d1d,stroke:#991b1b,color:#fff
    classDef ext  fill:#92400e,stroke:#b45309,color:#fff
    classDef cfg  fill:#374151,stroke:#4b5563,color:#fff

    subgraph Frontend["Angular 17 FE (Material · Tailwind · Transloco)"]
        FE["Subscription UI<br/>components · guard · service"]:::ctrl
    end

    subgraph API["Controller Layer"]
        SC["SubscriptionController"]:::ctrl
        WH["StripeWebhookController"]:::ctrl
        CC["CampaignController"]:::ctrl
    end

    subgraph Sec["Security Layer"]
        JWT["FirebaseJwtFilter"]:::sec
        SIG["WebhookSignatureVerifier<br/>(Stripe-Signature HMAC)"]:::sec
        HMAC["ConsentCookieService<br/>(HMAC immediate-activation)"]:::sec
    end

    subgraph Impl["Implementation Layer"]
        SS["SubscriptionService<br/>(state machine · @Version lock)"]:::impl
        CLS["CampaignLimitService"]:::impl
        STR["StripeService"]:::impl
        INV["InvoiceRetryService"]:::impl
    end

    subgraph Life["Lifecycle Layer (crons · events)"]
        CRON["Daily crons:<br/>trial-expiry · period-processor<br/>terms-grace · invoice-retry(15m)"]:::cfg
        EVT["InvoiceCreatedEvent<br/>(AFTER_COMMIT)"]:::cfg
    end

    subgraph Ext["External Systems"]
        STRIPE["Stripe<br/>Checkout · Schedules · Portal"]:::ext
        FAKT["Fakturownia<br/>(VAT-exempt invoices → KSeF)"]:::ext
        FB["Firebase Auth"]:::ext
        PG[("PostgreSQL")]:::cfg
    end

    FE -->|REST| SC
    SC -->|guarded by| JWT
    WH -->|verified by| SIG
    SC -->|consent via| HMAC
    SC -->|orchestrates| SS
    WH -->|orchestrates| SS
    CC -->|enforces limit| CLS
    SS -->|calls| STR
    SS -->|triggers| INV
    CLS -->|depends on| SS
    STR -->|"DELETE / Checkout / Schedule"| STRIPE
    INV -->|"POST /invoices.json"| FAKT
    JWT -->|verifies| FB
    EVT -->|fires| INV
    CRON -->|drives| SS
    CRON -->|drives| INV
    SS -->|persists| PG
    STRIPE -.->|webhooks| WH
```
