# Consent Module — Specification

> **Status: DONE** — Fully implemented (BE + FE) as of 2026-03-10. All unit, integration, and E2E tests pass.

## 1. Overview

A consent module that requires active consent from users during registration for the following legal documents:

- **Cookie Policy** (Polityka cookies) — all users
- **Terms of Service** (Regulamin) — all users
- **Privacy Policy** (Polityka prywatności) — all users
- **Professional Character Declaration** (Oświadczenie o charakterze zawodowym) — JDG sole proprietors only (see `CompaniesDataFormExpansionModule.md` section 4.3)

Each document is available in two languages: **Polish (PL)** and **English (EN)**. Acceptance is language-agnostic — accepting the PL version satisfies the EN requirement and vice versa, as the terms are identical in both languages.

### Terminology

- **Backend / database**: uses the term **"consent"** throughout — table names (`consent_record`), enums, services. This is the technical term.
- **Frontend / UI**: checkboxes use proper Polish legal phrasing — e.g., *"Wyrażam zgodę na regulamin"* (I consent to the terms of service), *"Zapoznałem/am się z polityką prywatności"* (I have read the privacy policy).

### Legal Basis

The legal basis for processing personal data is **contractual** (GDPR Art. 6(1)(b)) — providing services to influencers and companies. Cookie policy consent falls under the ePrivacy Directive / Polish Telecommunications Law (Prawo telekomunikacyjne, Art. 173).

Because the basis is contractual, withdrawal of consent is not required. Account deletion is permitted only when the user has no active ongoing collaborations (no in-progress applications from influencers, no active partnerships for companies).


---

## 2. Database Design

### 2.1 `legal_document` Table

Stores metadata for each version of each legal document.

| Column          | Type        | Description                                                                 |
|-----------------|-------------|-----------------------------------------------------------------------------|
| `id`            | BIGINT PK   | Auto-generated                                                              |
| `type`          | VARCHAR     | Enum: `COOKIE_POLICY`, `TERMS_OF_SERVICE`, `PRIVACY_POLICY`, `PROFESSIONAL_CHARACTER_DECLARATION` |
| `language`      | VARCHAR(5)  | `pl` or `en`                                                                |
| `version`       | INTEGER     | Incremental version number                                                  |
| `content_hash`  | VARCHAR     | SHA-256 hash of the PDF file                                                |
| `document_url`  | TEXT        | Public URL to the PDF (GCS bucket)                                          |
| `published_at`  | TIMESTAMP   | Date the document became effective (env-specific via Liquibase migrations)  |

**Unique constraint**: `(type, language, version)`

Document versioning: all three document types are always versioned together (e.g., v2 of Cookie Policy, ToS, and Privacy Policy are published simultaneously). This means 6 files per version (3 types × 2 languages).

### 2.2 `consent_record` Table

Stores every individual acceptance event.

| Column           | Type        | Description                                                              |
|------------------|-------------|--------------------------------------------------------------------------|
| `id`             | BIGINT PK   | Auto-generated                                                           |
| `user_id`        | BIGINT FK   | References `users.id`. **Nullable** for anonymous cookie consents        |
| `document_id`    | BIGINT FK   | References `legal_document.id`                                           |
| `timestamp`      | TIMESTAMP   | When the consent was recorded                                            |
| `user_agent`     | TEXT        | Browser user-agent string                                                |
| `ip_address`     | INET        | Client IP address (nullable, anonymized after 6 months)                  |
| `is_trusted`     | BOOLEAN     | Value of `event.isTrusted` from the FE click event                       |
| `consent_proof`  | JSONB       | Full proof bundle (documentHash, additional metadata)                    |
| `source`         | VARCHAR     | CHECK constraint: `REGISTRATION`, `OAUTH_REGISTRATION`, `SOCIAL_REGISTRATION`, `LOGIN_PROMPT`, `COOKIE_BANNER`, `SETTINGS`, `ACCOUNT_DELETION` |

### 2.3 `users` Table — New Column

| Column                      | Type    | Description                                                        |
|-----------------------------|---------|--------------------------------------------------------------------|
| `newest_consents_accepted`  | BOOLEAN | `true` if the user has accepted the latest version of all documents. Set to `false` by migration when new document versions are introduced. |

### 2.4 User Status Enum — New Value

Extend the existing user status enum (`ACTIVE`, `INACTIVE`, `BLOCKED`) with:

- **`BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS`** — applied automatically by cron after the 38-day grace period expires.

---

## 3. Consent Proof Storage — Unified HMAC Cookies

All consent proofs (cookie policy, ToS, privacy policy) are stored in **HMAC-signed cookies** using a **dedicated secret** (`consent.hmac-secret`), separate from the authentication HMAC secret. This ensures:

- Consent cookies survive OAuth redirects (unlike sessionStorage).
- A single, consistent mechanism for all consent types.
- No risk of cross-contamination with authentication cookies.

### Cookie Configuration

| Cookie Name                        | Contains                        | Created When                        |
|------------------------------------|---------------------------------|-------------------------------------|
| `consent_cookie_policy`            | `consent_record.id`             | Cookie banner acceptance            |
| `consent_terms_of_service`         | Consent proof JSON              | Registration checkbox click         |
| `consent_privacy_policy`           | Consent proof JSON              | Registration checkbox click         |
| `consent_professional_character`   | Consent proof JSON              | Registration checkbox click (JDG only) |

**Consent proof JSON** structure (for ToS and Privacy Policy cookies):

```json
{
  "timestamp": "2026-03-08T14:30:00Z",
  "isTrusted": true,
  "documentHash": "sha256-...",
  "userAgent": "Mozilla/5.0 ...",
  "language": "pl",
  "documentName": "terms_of_service_v1_pl.pdf"
}
```

**All cookies share these attributes**: `HttpOnly`, `Secure`, `SameSite=Lax`, HMAC-signed with `consent.hmac-secret`.

**Why `SameSite=Lax`**: `Strict` cookies are not sent on the redirect back from Instagram OAuth. `Lax` sends cookies on top-level navigations (GET redirects), which is what OAuth callbacks are.

**Why a dedicated HMAC secret**: Consent cookies serve a different purpose than authentication cookies. A separate `consent.hmac-secret` prevents overloading the auth secret and allows independent rotation.

---

## 4. Cookie Consent (Anonymous Flow)

**Trigger**: User sees the cookie banner on FE (before login/registration).

### Flow

1. User clicks "Accept" on the cookie banner.
2. FE captures consent proof: `userAgent`, `timestamp`, `event.isTrusted`, `language`, IP (resolved by BE).
3. FE sends POST to **anonymous** consent endpoint (`/api/anonymous/consent`). The `document_name` field (which includes the version string) is mapped by the BE controller to the correct `legal_document.id`.
4. BE creates a `consent_record` with `user_id = NULL`.
5. BE returns the `consent_record.id` in the response.
6. FE stores the `consent_record.id` in the `consent_cookie_policy` HMAC-signed cookie.

---

## 5. Registration Flow

### 5.1 Standard Registration (Companies)

1. Registration form displays checkboxes for **Terms of Service** and **Privacy Policy** with links to the documents.
2. Both checkboxes must be actively clicked before the "Register" button becomes enabled.
3. Each checkbox click: FE computes SHA-256 hash of the document PDF (Web Crypto API), captures consent proof, and stores it in the corresponding HMAC-signed cookie (`consent_terms_of_service`, `consent_privacy_policy`).
4. On form submit, BE reads all three consent cookies from the request, validates HMAC signatures, and proceeds with registration.

### 5.2 OAuth Registration (Influencers — Instagram)

1. If an Instagram OAuth user has no existing account, redirect them to a dedicated **influencer registration page**.
2. This page shows the same ToS and Privacy Policy checkboxes as the company registration.
3. Consent proofs are stored in HMAC-signed cookies (same as company flow).
4. Cookies **survive the Instagram OAuth redirect** naturally — no sessionStorage or query param bridge needed.
5. During the OAuth callback phase, BE reads the consent cookies from the request alongside the registration completion data.

### 5.3 Error Handling for OAuth

If an unregistered influencer clicks the Instagram OAuth button on the login page (not the registration page), handle this gracefully — redirect them to the influencer registration page with appropriate context rather than failing silently.

### 5.4 Backend Registration Logic

On receiving a registration request, the BE validates:

1. **Cookie consent**: Extract `consent_record.id` from the `consent_cookie_policy` cookie, validate HMAC, verify the record exists in the database.
2. **ToS consent**: Extract consent proof from the `consent_terms_of_service` cookie, validate HMAC, verify `documentHash` and `documentName` match a valid `legal_document` record.
3. **Privacy Policy consent**: Same as ToS, from the `consent_privacy_policy` cookie.
4. If all three are valid:
   - Create the user.
   - Update the previously-anonymous cookie consent record: set `user_id` to the newly created user's PostgreSQL ID.
   - Create `consent_record` entries for ToS and Privacy Policy with the user's ID.
   - Set `newest_consents_accepted = true` on the new user.
   - Clear all three consent cookies from the response.
5. If any consent is missing or invalid → reject the registration request.

---

## 6. Login & Re-consent Flow

### 6.1 `/me` Endpoint

The `/me` endpoint response includes:

| Field                        | Type    | Description                                              |
|------------------------------|---------|----------------------------------------------------------|
| `newestConsentsAccepted`     | BOOLEAN | Whether the user has accepted the latest document versions |
| `daysToAcceptNewTerms`       | INTEGER | Days remaining before account gets blocked (out of 38). `null` if `newestConsentsAccepted = true` |

This information is returned **on every `/me` call**, not just once.

### 6.2 FE Re-consent Modal

When the FE detects `newestConsentsAccepted = false` from the `/me` response:

1. Display a **modal overlay** showing links to the updated Cookie Policy, Terms of Service, and Privacy Policy.
2. The modal can be **dismissed** — the user is not forced to accept immediately.
3. The modal shows how many days remain before the account will be blocked.
4. On acceptance, FE captures consent proof (same structure as registration) and sends it to the BE.
5. BE records the consents and sets `newest_consents_accepted = true`.

Unlike registration, the cookie consent can be included in the JSON payload rather than via cookie — the user is already authenticated.

---

## 7. User Status & Enforcement

### What `BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS` Blocks

- Companies: **cannot** create new campaigns/partnerships.
- Influencers: **cannot** apply to new opportunities or join new campaigns.

### What Remains Accessible

- **Login**: users can still log in.
- **Re-consent modal**: users can still see and accept the new terms (which reverts their status to `ACTIVE`).
- **Existing collaborations**: users can continue participating in already-started applications and active partnerships.

### Unblocking Path

1. Blocked user logs in.
2. Re-consent modal is displayed.
3. User accepts all new documents.
4. BE sets `newest_consents_accepted = true` and reverts user status from `BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS` to `ACTIVE`.

---

## 8. Cron Jobs

### 8.1 Daily: Consent Grace Period Enforcement

- **Schedule**: Daily.
- **Logic**: For each user where `newest_consents_accepted = false`, check if the time since the latest legal document version was published exceeds **38 days**.
- **Action**: Set user status to `BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS`.
- **Why 38 days**: Maximum session lifetime is 7 days. 38 days guarantees at least one forced re-login, ensuring users see the re-consent modal before being blocked.
- Uses ShedLock to prevent concurrent execution.

### 8.2 Weekly: Anonymous Consent Cleanup

- **Schedule**: Weekly.
- **Logic**: Delete `consent_record` entries where `user_id IS NULL` and `timestamp` is older than **1 year**.
- **Purpose**: Clean up anonymous cookie consents from users who never completed registration.
- Only deletes **unmatched** records (those still with `user_id = NULL`).

---

## 9. Edge Cases & Error Handling

### 9.1 Missing Anonymous Consent at Registration

If the HMAC-signed cookie is missing, invalid, or references a deleted/expired record:
- Return a specific error code to the FE.
- FE clears the stale cookie and re-shows the cookie consent banner.
- User must re-accept cookies before proceeding with registration.

### 9.2 Document Version Change During Registration

If a user accepted cookies under document v1 but v2 is published before they submit the registration form:
- The BE should validate against the document version that was active when the anonymous consent was recorded.
- Alternatively, reject and prompt the user to re-accept under the new version — simpler and safer.

### 9.3 Existing Users at Module Launch

When the consent module is first deployed:
- Liquibase migration sets `newest_consents_accepted = false` for all existing users.
- Existing users enter the same re-consent flow as they would for any future document update.
- The 38-day grace period applies from the migration date.

### 9.4 Cron Failure Recovery

If the daily cron job fails to run:
- ShedLock ensures it doesn't double-execute.
- Next successful run catches up — it checks against absolute time, not incremental state.

### 9.5 Account Deletion Constraints

Account deletion is permitted only when:
- The user has **no active ongoing collaborations** (no in-progress influencer applications, no active company partnerships).
- If active collaborations exist, deletion is blocked with an appropriate message.

---

## 10. Frontend Document Serving

Legal document PDFs are served directly from the FE (public GCS bucket). The filename convention includes the version number. The FE computes SHA-256 hashes of PDFs client-side using the Web Crypto API — these hashes are included in the consent proof to prove which exact file content the user was shown.

Documents always come in sets of 6 (3 types × 2 languages) and the FE repository contains them for display to users when the re-consent modal is triggered.

---

# Appendix — As built: the full RODO/GDPR layer (2026-06)

The specification above shipped. Around it grew a complete data-protection layer;
all class names verifiable in `legal/`, `consent/` and `admin/`.

## Consent as forensic evidence

Every acceptance becomes an immutable `ConsentRecord` carrying IP (INET type), user
agent, and a jsonb proof payload with browser event-trust signals (`isTrusted`, screen
coordinates, checkbox id). Pre-registration consents travel in HMAC-signed cookies
(dedicated secret, independently rotatable from session cookies, `SameSite=Lax` to
survive the Instagram OAuth redirect); anonymous banner consents are recorded with
`user_id = NULL` and claimed by the account at registration or login via the record id
embedded in the cookie — the audit trail has no gap.

Document versioning is schema-enforced — unique `(type, language, version)` with
content hashes — and **legal documents are seeded by content hash**: changing the
legal text bumps the hash, which automatically triggers user re-consent. A parallel
granular-consent module tracks marketing consents in an append-only event log plus a
composite-PK current-state projection for O(1) reads.

## The three enforcement crons

| Cron | Cadence | What it embodies |
|---|---|---|
| `ConsentEnforcementCronJob` | daily | 38-day grace window: blocks users who ignored updated terms (guarded status flip + tokenVersion bump = instant session kill + Redis evict) |
| `NoConsentAccountCleanupCronJob` | weekly | GDPR Art. 6: no lawful basis ⇒ no processing — zero-consent accounts are archived proactively |
| `AnonymousConsentCleanupCronJob` | weekly | Purges year-old anonymous consent records (data minimization) |

## Right to erasure — two paths, four systems

- **Meta data-deletion callbacks** (`InstagramDataDeletionService`) are idempotent by
  confirmation code; erasure runs immediately or queues (`DeferredDeletionCronJob`,
  daily) until active collaborations end.
- **Admin cascade delete** (`AdminCascadeDeleteController`, preview-then-confirm) runs
  a safest-order pipeline: archive a full JSON envelope **to GCS first** (the recovery
  option), then PostgreSQL → Firestore (including TOTP secrets) → Firebase Storage →
  Firebase Auth last. Per-system outcomes land in a `CascadeDeleteTask` ledger; a
  daily `OrphanCleanupTask` retries failures up to three times — eventual consistency
  across all four systems is guaranteed, not hoped for.
- **Self-service deletion** computes typed, localized `DeletionBlocker` lists (active
  campaigns or applications per user type) before any erasure; company archival also
  deactivates the Stripe subscription.

Every admin action — claims changes (with before/after permission snapshots), force
deletions — lands in structured audit logs with MDC request ids; rate-limit storage
runs through a GDPR anonymization/retention wrapper so even abuse telemetry respects
data protection.
