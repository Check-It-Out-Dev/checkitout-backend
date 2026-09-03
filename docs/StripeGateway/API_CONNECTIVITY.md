# API Connectivity & Sandbox Verification

Verified: 2026-03-22

## Fakturownia API Documentation

Full API documentation (REST endpoints, examples in curl/PHP/Ruby, KSeF integration guide, bank accounts):

```
fakturownia-api-docs/
├── README.md                 # Full API reference (invoices, clients, products, payments, webhooks, departments, etc.)
├── API_RACHUNKI_BANKOWE.md   # Bank accounts on invoices (CRUD, deduplication, security levels, mass payments)
├── KSeF.md                   # KSeF integration guide (DEMO/production, validation, sending, offline modes)
├── example.curl              # curl examples
├── example1.php              # PHP examples
└── example1.rb               # Ruby examples
```

## Stripe Sandbox

| Item | Value |
|------|-------|
| Status | **WORKING** |
| Mode | `livemode: false` (test/sandbox) |
| Currency | PLN |
| Public key | `STRIPE_PUBLIC_KEY` in .env (`pk_test_...`) |
| Secret key | `STRIPE_PRIVATE_KEY` in .env (`sk_test_...`) |

### Products & Prices Created

| Product | Product ID | Price | Price ID |
|---------|-----------|-------|----------|
| checkItOut Business | `prod_UCEJdIcXpCI8XY` | 29.00 PLN/month | `price_1TDprAEF0n7JDo59KpKh8vnt` |
| checkItOut Enterprise | `prod_UCEJEPOpJvXeut` | 99.00 PLN/month | `price_1TDprIEF0n7JDo591xcehTLl` |

### Stripe Test Cards

| Card Number | Scenario |
|-------------|----------|
| `4242 4242 4242 4242` | Successful payment |
| `4000 0000 0000 3220` | 3D Secure authentication required |
| `4000 0000 0000 9995` | Payment declined (insufficient funds) |
| `4000 0000 0000 0341` | Attaching card fails |
| `4000 0000 0000 0002` | Card declined |
| `4000 0000 0000 0069` | Expired card |

Any expiry date in the future works. Any 3-digit CVC. Any billing ZIP.

### Webhook Testing

```bash
# Install Stripe CLI, then:
stripe listen --forward-to localhost:8080/api/webhooks/stripe

# Trigger test events:
stripe trigger checkout.session.completed
stripe trigger invoice.paid
stripe trigger invoice.payment_failed
stripe trigger customer.subscription.deleted
stripe trigger customer.subscription.updated
```

### Available Stripe APIs Verified

| API | Endpoint | Status |
|-----|----------|--------|
| Customers | `GET /v1/customers` | 200 OK |
| Balance | `GET /v1/balance` | 200 OK (0 PLN) |
| Products | `POST /v1/products` | Created 2 products |
| Prices | `POST /v1/prices` | Created 2 recurring prices |
| Subscriptions | `POST /v1/subscriptions` | Available |
| Subscription Schedules | `POST /v1/subscription_schedules` | Available |
| Checkout Sessions | `POST /v1/checkout/sessions` | Available |
| Billing Portal | `POST /v1/billing_portal/sessions` | Available |

---

## Fakturownia

| Item | Value |
|------|-------|
| Status | **WORKING** |
| Domain | `checkitout.fakturownia.pl` |
| API key | `FAKTUROWNIA_API_KEY` in .env |
| Doc key | `FAKTUROWNIA_DOC_KEY` in .env |
| KSeF | DEMO mode, validation on send, no auto-send |

### Departments

| Department | ID | Type |
|------------|-----|------|
| CHECK IT OUT SP. Z O.O. | `1878632` | Main |
| CHECK IT OUT SP. Z O.O. TEST | `1878648` | **Test (use this for development)** |

### Seller Data (from department)

| Field | Value |
|-------|-------|
| Name | CHECK IT OUT SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ |
| NIP | 8943264018 |
| Street | Stanisławowska 47 |
| Post code | 54-611 |
| City | Wrocław |
| Country | PL |

### Test Invoice Created

| Field | Value |
|-------|-------|
| Invoice ID | `476301568` |
| Number | `1/03/2026` |
| Department | TEST (`1878648`) |
| Buyer | Testowa Firma Sp. z o.o. (NIP: 5252445767) |
| Amount | 29.00 PLN |
| Tax | `zw` (VAT exempt) |
| Status | `issued` |
| View URL | `https://checkitout.fakturownia.net/f/1-03-2026/BdObyGJuRpboKNtikJW4` |
| Position | "checkItOut Business - subskrypcja miesięczna" |
| Client auto-created | ID `238643771` |

### Invoice Creation Template (for implementation)

```json
{
  "api_token": "${FAKTUROWNIA_API_KEY}",
  "invoice": {
    "kind": "vat",
    "department_id": 1878648,
    "sell_date": "YYYY-MM-DD",
    "issue_date": "YYYY-MM-DD",
    "payment_to": "YYYY-MM-DD",
    "seller_name": "CHECK IT OUT SP. Z O.O.",
    "seller_tax_no": "8943264018",
    "buyer_name": "${company.name}",
    "buyer_tax_no": "${company.nip}",
    "buyer_company": true,
    "exempt_tax_kind": "art113",
    "positions": [
      {
        "name": "checkItOut ${plan_name} - subskrypcja miesięczna",
        "quantity": 1,
        "total_price_gross": "${plan_price}",
        "tax": "zw"
      }
    ]
  }
}
```

### Fakturownia API Endpoints Verified

| API | Endpoint | Status |
|-----|----------|--------|
| Departments | `GET /departments.json` | 200 OK |
| Create invoice | `POST /invoices.json` | 201 Created |
| Get invoice | `GET /invoices/{id}.json` | Available |
| Send by email | `POST /invoices/{id}/send_by_email.json` | Available |
| Get PDF | `GET /invoices/{id}.pdf` | Available |
| KSeF send | `POST /invoices/{id}/gov_send.json` | Available (DEMO) |
| Bank accounts | `GET /bank_accounts.json` | Available |

---

## Configuration for application.yml

```yaml
# Stripe
stripe:
  public-key: ${STRIPE_PUBLIC_KEY}
  secret-key: ${STRIPE_PRIVATE_KEY}
  products:
    business: prod_UCEJdIcXpCI8XY
    enterprise: prod_UCEJEPOpJvXeut
  prices:
    business: price_1TDprAEF0n7JDo59KpKh8vnt      # 29.00 PLN/month
    enterprise: price_1TDprIEF0n7JDo591xcehTLl      # 99.00 PLN/month
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}  # From stripe listen output

# Fakturownia
fakturownia:
  api-key: ${FAKTUROWNIA_API_KEY}
  doc-key: ${FAKTUROWNIA_DOC_KEY}
  domain: checkitout                                 # -> https://checkitout.fakturownia.pl
  department-id: 1878648                             # TEST department
  department-id-main: 1878632                        # PRODUCTION department (do not use in dev)
  seller:
    name: "CHECK IT OUT SP. Z O.O."
    tax-no: "8943264018"
    street: "Stanisławowska 47"
    post-code: "54-611"
    city: "Wrocław"
    country: "PL"
  exempt-tax-kind: art113
  # Full API docs: fakturownia-api-docs/README.md
