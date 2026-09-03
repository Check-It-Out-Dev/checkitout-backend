# Rolling this out for real

`docs/DEV-LITE.md` gets the platform running on your machine with nothing at
all. This document is the other direction: what you replace, in what order, to
turn that into something you can put in front of users.

We wrote it as stages rather than a checklist because the order matters. Each
stage is independently useful — you can stop after any of them and still have a
working system, just with fewer capabilities. Nothing here needs a rewrite; it
is configuration and credentials, because the seams were built as seams.

---

## Stage 0 — what you already have

A credential-less clone runs the whole product: campaigns, applications, content
submission and approval, ratings, support tickets, notifications, the admin
surface, sessions, guards, roles, rate limiting, consent and terms enforcement.
That is not a demo shell — it is the production code path against a real
PostgreSQL.

What is simulated: sign-in, outbound e-mail, the Polish company registries, and
the byte transport for file uploads. Payments and invoicing are switched off.
Each of the stages below turns exactly one of those back into the real thing.

**Verify before moving on:** `node tools/dev-lite.mjs` reaches "Ready", you can
sign in as the three demo accounts, and the seeded campaigns are visible.

---

## Stage 1 — real identity (Firebase)

This is the one that changes the product from a demo into something a stranger
can sign up for. Everything else can wait; this cannot.

| What | Where |
| --- | --- |
| Service-account JSON | `firebase.config.path` (default `src/main/resources/service-account.json`) or `FIREBASE_SERVICE_ACCOUNT_JSON` |
| Project id | `firebase.project.id`, `gcp.project-id` |
| Admin account | `ADMIN_EMAIL`, `admin.firebase.uid` |
| Boot-time admin check | `admin.check.enabled` — leave `true` outside dev-lite; it fails fast when the admin account is missing |

The file is gitignored and must never be committed. In production, mount it as a
secret or hand it in through `FIREBASE_SERVICE_ACCOUNT_JSON`.

**What this unlocks:** password sign-in, e-mail verification links, password
reset, magic links, and ID-token verification on the token-authenticated paths.
The local `mock-session` endpoint stops existing entirely — it is bound to the
`e2e` and `dev-lite` profiles and cannot be activated alongside `prod`, which
`ProfileGateUnitTest` pins.

**Verify:** register a real address, follow the verification link, sign in with
the password, and confirm `/api/users/me` returns that account.

---

## Stage 2 — real file storage

| What | Where |
| --- | --- |
| Bucket | `GCP_BUCKET_NAME` / `gcp.bucket-name` |
| Storage client | `gcp.storage.enabled: true` |
| Credentials | the same service account as Stage 1 |

The upload contract does not change: the browser asks for a signed URL, PUTs the
bytes straight to storage, and the server confirms. dev-lite swapped only the
transport, so nothing on either side needs editing — validation, rate limits,
quota, the tracking row and the uploadId-only persistence rule were always the
production path.

**Verify:** upload a profile photo and confirm the stored URL points at your
bucket rather than `/api/dev-lite/files/...`.

**While you are here:** `gcp.kms.*` encrypts Instagram tokens and TOTP secrets.
Without KMS credentials those specific paths fail on use — the application still
boots, but two-factor setup and Instagram connections will not work.

---

## Stage 3 — real e-mail

| What | Where |
| --- | --- |
| SMTP host, port, credentials | `spring.mail.*` (`MAIL_PASSWORD` for the secret) |
| Sender address | `app.email.from-email` |
| Master switches | `app.email.enabled`, `notification.email.enabled` |

Point `spring.mail.*` at a real provider and the in-memory GreenMail server is
simply not there — it is bound to `e2e` and `dev`, and cannot load in `prod`.
The `/api/test/email` reader disappears with it.

**Verify:** trigger a support ticket and confirm the confirmation lands in a real
inbox, with the reference number in the subject.

---

## Stage 4 — company registries

| What | Where |
| --- | --- |
| CEIDG key | `CEIDG_APP_KEY` |
| GUS / VAT | the registry adapters read their own configuration; the stub ports exist only under `e2e` / `dev-lite` |

Without this, NIP lookups report "not found" rather than failing — the account
flow stays usable, it just cannot pre-fill company data. With it, a business
account is verified against the real register at sign-up.

**Verify:** enter a real NIP during company onboarding and confirm the name and
address come back pre-filled.

---

## Stage 5 — payments and invoicing

Leave this for last, and read `docs/StripeGateway/` before you start. It is the
only stage with a boot guard that will deliberately refuse to start the
application if the data and the configuration disagree.

| What | Where |
| --- | --- |
| Master switch | `app.payments.enabled` (`APP_PAYMENTS_ENABLED`) |
| Stripe keys | `STRIPE_PRIVATE_KEY`, `STRIPE_PUBLIC_KEY` |
| Webhook secret | `STRIPE_WEBHOOK_SECRET` — the endpoint verifies signatures; a wrong value fails closed |
| Invoicing | `fakturownia.enabled`, `FAKTUROWNIA_API_KEY`, `FAKTUROWNIA_DOC_KEY` |
| KSeF | one flag on the invoicing adapter once Fakturownia is live |

`PaymentsDisabledBootGuard` refuses to boot with payments disabled while
subscriptions exist in a paid state — that is intentional. It is the difference
between noticing a misconfiguration at startup and discovering it when a
customer is billed. If it fires, reconcile the subscription rows first; do not
work around the guard.

**Verify on a Stripe test key first:** run a subscription through trial →
upgrade → payment → invoice, and confirm the invoice is generated and delivered.
The `subscription` state machine (10 states, 56 transitions) is documented in
the Neo4j namespace of the same name.

---

## Stage 6 — deployment

| What | Where |
| --- | --- |
| Profile | `SPRING_PROFILES_ACTIVE=prod` (or `prod-standalone`) |
| Environment marker | `app.environment` — must say `PRODUCTION`, it gates the synthetic-credential fallbacks |
| Public URL | `APP_BASE_URL` — used in every generated link |
| CORS | `cors.allowed-origins` — exact origins. Do **not** copy dev-lite's loopback patterns here |
| Bot protection | `recaptcha.enabled: true` on the public forms |
| GeoIP | `MAXMIND_LICENSE_KEY` — blank means every lookup returns "unknown" |
| TLS, headers, rate limiting at the edge | `deployment/` and `ansible/` |

The runbooks in [`ansible/`](../ansible/) and [`deployment/`](../deployment/)
cover the VPS side: nginx, certificates, the Docker composition, log shipping to
Loki, and the deploy pipeline described in
[`docs/Architecture/02_CICD_DEPLOYMENT.md`](Architecture/02_CICD_DEPLOYMENT.md).

**Two failure modes worth knowing before your first deploy**, because both look
like something else:

- The payments boot guard crash-looping (see Stage 5) reads as "the container
  will not start".
- A certificate renewal that leaves a dangling nginx include reads as a 521 from
  the edge, not as a certificate problem.

---

## The order, and why

Identity first, because nothing else is reachable by a real user without it.
Storage and mail next, because they are what the product visibly needs to feel
finished. Registries after that — they improve onboarding but nothing breaks
without them. Payments last, because it is the only stage that can take money
from someone, and it deserves a system you already trust.

At every stage the thing you are switching on is a seam that already exists.
That is the whole reason the credential-less mode was worth building: it forced
every external dependency to be a boundary with a working substitute behind it,
and a boundary you can swap is a boundary you can also reason about, test, and
degrade gracefully when the vendor is down.
