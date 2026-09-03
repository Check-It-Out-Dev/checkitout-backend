# Instagram OAuth Refinement — Specification

## 1. Overview

This document covers four refinements to the Instagram OAuth integration:

1. **Post-registration email verification + password setup** — after Instagram OAuth registration, the user verifies their email and sets a password. Account becomes `ACTIVE` only after completion.
2. **Deauthorization Callback** — Meta notifies us when a user removes our app from Instagram.
3. **Data Deletion Callback** — Meta notifies us when a user requests deletion of their data via Facebook/Instagram settings.

### Context

Instagram OAuth users currently have no alternative login method. If they deauthorize the app or Meta revokes access, they lose access to their account and any active collaborations. These refinements ensure resilience and Meta platform compliance.

### Related Documents

- **Consent Module** (`ConsetModule.md`) — consent proof is stored in HMAC-signed cookies (using `consent.hmac-secret`), which survive OAuth redirects. The Instagram OAuth registration flow relies on this mechanism.

---

## 2. Post-Registration Account Activation

### Current Flow

Instagram OAuth user authorizes → callback returns profile (Instagram user ID, username) → account is created immediately as `ACTIVE`.

### New Flow

1. Instagram OAuth user authorizes → callback returns profile → redirect to **influencer registration page**.
2. Registration page displays consent checkboxes (ToS, Privacy Policy). Consent proofs stored in HMAC-signed cookies (see Consent Module spec section 3).
3. User accepts consents, provides required data, and submits → **account is created with status `INACTIVE`**.
4. User receives a **Firebase email verification email** with a magic link pointing to our dedicated verification page.
5. User clicks the link → our FE page handles the Firebase magic parameters → email is verified.
6. User receives (or is redirected to) a **password setup page** with a Firebase password reset magic link.
7. User sets their password on our dedicated password setup page.
8. Once all activation criteria are met (email verified, password set, other required data) → account status transitions to `ACTIVE`.

### Why

- `INACTIVE` is the entry state — account exists but is not fully operational until setup is complete.
- Every influencer ends up with a verified email + password as a fallback login method.
- The existing BE email verification mechanism handles the status transition logic.
- Dedicated FE pages for magic link handling ensure a smooth, branded experience instead of generic Firebase pages.

### Backend Changes

- Instagram OAuth registration creates user with status `INACTIVE`.
- Activation logic: when all required criteria are met → set user status to `ACTIVE`. This is handled by the existing BE activation mechanism.
- Add `hasPasswordProvider: boolean` to `/me` endpoint response.

### Frontend Changes

- Consent proofs are stored in HMAC-signed cookies (`consent_terms_of_service`, `consent_privacy_policy`) — these survive the Instagram OAuth redirect. See Consent Module spec section 3 for details.
- Post-registration: show setup progress (email verification → password setup).
- Non-blocking banner for `INACTIVE` users: *"Complete your account setup to start using the platform."*

---

## 3. Firebase Magic Link Handling Pages

Firebase Authentication generates magic links (with `oobCode` and `mode` parameters) for email verification and password reset. Instead of using Firebase's default hosted pages, we create **dedicated FE pages** that handle these parameters, ensuring a consistent, branded experience for all users (not just Instagram OAuth).

### 3.1 Email Verification Page

**Route**: `/auth/verify-email`

**Query parameters** (from Firebase magic link):
- `mode=verifyEmail`
- `oobCode=<one-time-code>`
- `apiKey=<firebase-api-key>`
- `continueUrl=<optional-redirect>`

**Flow**:
1. Page reads `oobCode` from URL.
2. Calls Firebase Auth REST API: `applyActionCode(oobCode)` to verify the email.
3. On success → show confirmation message + redirect to the platform (or to the password setup step for new Instagram OAuth users).
4. On error (expired/invalid code) → show error message with a "Resend verification email" button.

### 3.2 Password Setup / Reset Page

**Route**: `/auth/reset-password`

**Query parameters** (from Firebase magic link):
- `mode=resetPassword`
- `oobCode=<one-time-code>`
- `apiKey=<firebase-api-key>`

**Flow**:
1. Page reads `oobCode` from URL.
2. Calls Firebase Auth REST API: `verifyPasswordResetCode(oobCode)` to validate the code and get the email.
3. Shows a password input form (with confirmation field, strength validation).
4. On submit → calls `confirmPasswordReset(oobCode, newPassword)`.
5. On success → show confirmation + redirect to login page.
6. On error (expired/invalid code) → show error message with a "Request new link" button.

### 3.3 Firebase Configuration

In Firebase Console → Authentication → Templates, configure the **Action URL** for all email templates to point to our FE:

```
https://checkitout.app/auth/action
```

Alternatively, use a single **action handler page** at `/auth/action` that reads the `mode` parameter and redirects:
- `mode=verifyEmail` → `/auth/verify-email?oobCode=...`
- `mode=resetPassword` → `/auth/reset-password?oobCode=...`

### 3.4 Benefits

- **Branded experience**: users stay on `checkitout.app` throughout the entire flow — no redirect to `firebaseapp.com`.
- **Unified for all users**: company users (email/password registration) and influencers (Instagram OAuth) use the same verification and password reset pages.
- **Custom error handling**: expired links, resend options, and redirects are fully under our control.
- **Localization**: pages can use Transloco for PL/EN support, matching the rest of the platform.

---

## 4. Deauthorization Callback

**URL**: `/api/auth/instagram/deauthorize` (public, no auth required)

Meta sends a POST request when a user removes our app from their Instagram account settings.

### Request

Meta POSTs with `Content-Type: application/x-www-form-urlencoded`:

```
signed_request=<encoded_payload>
```

The `signed_request` is a base64url-encoded JSON payload with an HMAC-SHA256 signature, signed with `meta.app-secret`.

### Parsing `signed_request`

1. Split by `.` → `[signature, payload]`.
2. Decode `signature` from base64url.
3. Compute HMAC-SHA256 of `payload` using `meta.app-secret`.
4. Compare computed HMAC with decoded signature — reject if mismatch.
5. Decode `payload` from base64url → JSON with `user_id` (Instagram app-scoped ID).

### Flow

1. Parse and validate `signed_request`.
2. Find the user by Instagram app-scoped ID.
3. **Invalidate** the stored Instagram access token (mark as revoked/expired).
4. **Mark social connection** as `DISCONNECTED` (new status or flag on the user entity).
5. **Send Firebase password reset email** to the user's verified email — so they can regain access via email + password.
6. **Log** the deauthorization event for audit purposes.
7. Return HTTP `200 OK`.

### What Does NOT Happen

- Account is **not** deleted.
- User is **not** blocked.
- Active collaborations continue unaffected.
- User can still log in via email + password (set up during post-registration flow, section 2) or re-authorize Instagram OAuth.

---

## 5. Data Deletion Callback

**URL**: `/api/auth/instagram/data-deletion` (public, no auth required)

Meta sends a POST request when a user requests their data be deleted via Facebook/Instagram privacy settings.

### Request

Same `signed_request` format as the deauthorization callback (section 4).

### Response

Must return JSON:

```json
{
  "url": "https://checkitout.app/deletion-status?code=abc123",
  "confirmation_code": "abc123"
}
```

- `url` — a publicly accessible page where the user can check the status of their deletion request.
- `confirmation_code` — unique identifier for tracking.

### Flow

1. Parse and validate `signed_request` (same logic as deauthorization callback — extract to shared service).
2. Find the user by Instagram app-scoped ID.
3. **Check for active collaborations** (reuse `UserAccountOrchestrator.checkDeletionEligibilityForUser()`):

#### Case A: No Active Collaborations

4a. Initiate deletion immediately — reuse `UserAccountOrchestrator.archiveUser()`.
5a. Generate `confirmation_code` (UUID), store it with the deletion request.
6a. Return `{ url, confirmation_code }` with status = `COMPLETED` or `IN_PROGRESS`.

#### Case B: Active Collaborations Exist

4b. **Queue for deferred deletion** — create a `pending_data_deletion_request` record:

| Column              | Type      | Description                                        |
|---------------------|-----------|----------------------------------------------------|
| `id`                | BIGINT PK | Auto-generated                                     |
| `user_id`           | BIGINT FK | References `users.id`                              |
| `confirmation_code` | VARCHAR   | UUID, returned to Meta                             |
| `status`            | VARCHAR   | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`  |
| `requested_at`      | TIMESTAMP | When Meta sent the request                         |
| `completed_at`      | TIMESTAMP | When deletion was actually executed (nullable)     |
| `blockers`          | JSONB     | Snapshot of active collaborations blocking deletion |

5b. Mark user status as something appropriate (e.g., `PENDING_DELETION`) — user can still participate in existing collaborations but cannot start new ones.
6b. Return `{ url, confirmation_code }` with status = `PENDING`.

### Deferred Deletion Cron

- **Schedule**: Daily.
- **Logic**: For each `pending_data_deletion_request` with status `PENDING`, re-check `checkDeletionEligibilityForUser()`.
- If collaborations have ended → execute deletion, set status to `COMPLETED`.
- Uses ShedLock.

### Status Check Endpoint

**URL**: `GET /api/auth/instagram/deletion-status?code={confirmation_code}` (public)

Returns:

```json
{
  "confirmation_code": "abc123",
  "status": "PENDING",
  "reason": "Active collaborations must complete before data can be deleted."
}
```

Or for completed:

```json
{
  "confirmation_code": "abc123",
  "status": "COMPLETED",
  "completed_at": "2026-04-15T10:30:00Z"
}
```

### Legal Justification for Deferred Deletion

- **GDPR Art. 17(3)(e)**: Right to erasure does not apply when processing is necessary for the establishment, exercise, or defence of legal claims.
- **GDPR Art. 6(1)(b)**: Data necessary for performance of an active contract.
- **Meta Platform Terms**: Data may be retained when needed for a "legitimate business purpose" or "required by applicable law."

The company has a legitimate interest in knowing which influencer applied/is collaborating. Immediate deletion mid-collaboration would break contractual obligations to both parties.

---

## 6. Shared Infrastructure

### `MetaSignedRequestService`

New service to parse and validate Meta's `signed_request` format. Used by both callbacks.

```
parseSignedRequest(signedRequest: String) → MetaCallbackPayload
```

- Splits, decodes base64url, verifies HMAC-SHA256 against `meta.app-secret`.
- Returns parsed payload with `user_id` (app-scoped Instagram ID).
- Throws `InvalidSignedRequestException` on signature mismatch.

### Security Configuration

Both callback endpoints (`/api/auth/instagram/deauthorize`, `/api/auth/instagram/data-deletion`) and the status check endpoint (`/api/auth/instagram/deletion-status`) must be added to `permitAll()` paths in `WebSecurityConfiguration` — Meta sends unauthenticated requests.

### Meta App Dashboard Configuration

After implementation, configure in Meta App Dashboard → Instagram → Business Login:

1. **Deauthorize Callback URL**: `https://checkitout.app/api/auth/instagram/deauthorize`
2. **Data Deletion Request URL**: `https://checkitout.app/api/auth/instagram/data-deletion`

---

## 7. Summary of New Endpoints

| Method | Path                                     | Auth     | Purpose                              |
|--------|------------------------------------------|----------|--------------------------------------|
| POST   | `/api/auth/instagram/deauthorize`        | Public   | Meta deauthorization callback        |
| POST   | `/api/auth/instagram/data-deletion`      | Public   | Meta data deletion callback          |
| GET    | `/api/auth/instagram/deletion-status`    | Public   | Deletion status check (for Meta UI)  |

### New FE Routes

| Route                   | Purpose                                              |
|-------------------------|------------------------------------------------------|
| `/auth/action`          | Firebase magic link router (reads `mode`, redirects) |
| `/auth/verify-email`    | Email verification page (handles `oobCode`)          |
| `/auth/reset-password`  | Password setup / reset page (handles `oobCode`)      |

## 8. Summary of Changes to Existing Code

| Area                        | Change                                                                     |
|-----------------------------|----------------------------------------------------------------------------|
| Registration flow           | Instagram OAuth creates user as `INACTIVE`; activates after email verification + password setup |
| Post-registration           | Trigger email verification (existing mechanism), then password setup link  |
| `/me` endpoint              | Add `hasPasswordProvider` field                                            |
| `WebSecurityConfiguration`  | Add new public paths for Meta callbacks                                    |
| User entity                 | Add `instagramConnectionStatus` or similar field for tracking OAuth state  |
| New service                 | `MetaSignedRequestService` — shared `signed_request` parser               |
| New table                   | `pending_data_deletion_request` — tracks deferred deletions               |
| New cron                    | Daily check for deferred deletions ready to execute                        |
| New FE pages                | `/auth/action`, `/auth/verify-email`, `/auth/reset-password` — Firebase magic link handlers |
| Firebase Console            | Configure Action URL to `https://checkitout.app/auth/action`              |
