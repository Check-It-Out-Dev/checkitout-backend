# Instagram OAuth Callbacks — Implementation Specification

## 1. Overview

Two Meta-required callbacks for Instagram Business Login integration:

1. **Deauthorization Callback** — Meta notifies us when a user removes our app from Instagram.
2. **Data Deletion Callback** — Meta notifies us when a user requests deletion of their data via Facebook/Instagram settings.

Both callbacks share the same `signed_request` parsing mechanism (section 3).

### Out of Scope

- Post-registration account activation (email verification + password setup) — separate spec.
- Firebase magic link handling pages — separate spec (`FirebaseMagicLinkPages.md`).

---

## 2. New Endpoints

| Method | Path                                     | Auth   | Purpose                              |
|--------|------------------------------------------|--------|--------------------------------------|
| POST   | `/api/auth/instagram/deauthorize`        | Public | Meta deauthorization callback        |
| POST   | `/api/auth/instagram/data-deletion`      | Public | Meta data deletion callback          |
| GET    | `/api/auth/instagram/deletion-status`    | Public | Deletion status check (for Meta UI)  |

All three must be added to `permitAll()` paths in `WebSecurityConfiguration`.

---

## 3. Shared Infrastructure — `MetaSignedRequestService`

New service to parse and validate Meta's `signed_request` format. Used by both callbacks.

```
parseSignedRequest(signedRequest: String) → MetaCallbackPayload
```

### Request Format

Meta POSTs with `Content-Type: application/x-www-form-urlencoded`:

```
signed_request=<encoded_payload>
```

### Parsing Algorithm

1. Split by `.` → `[signature, payload]`.
2. Decode `signature` from base64url.
3. Compute HMAC-SHA256 of `payload` using `meta.app-secret`.
4. Compare computed HMAC with decoded signature — reject if mismatch.
5. Decode `payload` from base64url → JSON with `user_id` (Instagram app-scoped ID).

### `MetaCallbackPayload`

```java
public record MetaCallbackPayload(
    String userId,       // Instagram app-scoped user ID
    String algorithm,    // e.g. "HMAC-SHA256"
    long issuedAt        // Unix timestamp
)
```

Throws `InvalidSignedRequestException` on signature mismatch or malformed payload.

### Configuration

```yaml
meta:
  app-secret: ${META_APP_SECRET}
```

---

## 4. Deauthorization Callback

**URL**: `POST /api/auth/instagram/deauthorize`

### Flow

1. Parse and validate `signed_request` via `MetaSignedRequestService`.
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
- User can still log in via email + password or re-authorize Instagram OAuth.

---

## 5. Data Deletion Callback

**URL**: `POST /api/auth/instagram/data-deletion`

### Response Format

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

1. Parse and validate `signed_request` via `MetaSignedRequestService`.
2. Find the user by Instagram app-scoped ID.
3. **Check for active collaborations** (reuse `UserAccountOrchestrator.checkDeletionEligibilityForUser()`).

#### Case A: No Active Collaborations

4a. Initiate deletion immediately — reuse `UserAccountOrchestrator.archiveUser()`.
5a. Generate `confirmation_code` (UUID), store it with the deletion request.
6a. Return `{ url, confirmation_code }` with status = `COMPLETED` or `IN_PROGRESS`.

#### Case B: Active Collaborations Exist

4b. **Queue for deferred deletion** — create a `pending_data_deletion_request` record.
5b. Mark user status as `TO_BE_DELETED` — user can still participate in existing collaborations but cannot start new ones.
6b. Return `{ url, confirmation_code }` with status = `PENDING`.

---

## 6. Data Storage — `pending_data_deletion_request`

New PostgreSQL table (Liquibase changeset):

| Column              | Type      | Description                                        |
|---------------------|-----------|----------------------------------------------------|
| `id`                | BIGINT PK | Auto-generated                                     |
| `user_id`           | BIGINT FK | References `users.id`                              |
| `confirmation_code` | VARCHAR   | UUID, returned to Meta                             |
| `status`            | VARCHAR   | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`  |
| `requested_at`      | TIMESTAMP | When Meta sent the request                         |
| `completed_at`      | TIMESTAMP | When deletion was actually executed (nullable)     |
| `blockers`          | JSONB     | Snapshot of active collaborations blocking deletion |

---

## 7. Deferred Deletion Cron

- **Schedule**: Daily.
- **Logic**: For each `pending_data_deletion_request` with status `PENDING`, re-check `checkDeletionEligibilityForUser()`.
- If collaborations have ended → execute deletion via `archiveUser()`, set status to `COMPLETED`.
- Uses ShedLock.

---

## 8. Deletion Status Check

`GET /api/auth/instagram/deletion-status?code={confirmation_code}`

Pending:

```json
{
  "confirmation_code": "abc123",
  "status": "PENDING",
  "reason": "Active collaborations must complete before data can be deleted."
}
```

Completed:

```json
{
  "confirmation_code": "abc123",
  "status": "COMPLETED",
  "completed_at": "2026-04-15T10:30:00Z"
}
```

---

## 9. Legal Justification for Deferred Deletion

- **GDPR Art. 17(3)(e)**: Right to erasure does not apply when processing is necessary for the establishment, exercise, or defence of legal claims.
- **GDPR Art. 6(1)(b)**: Data necessary for performance of an active contract.
- **Meta Platform Terms**: Data may be retained when needed for a "legitimate business purpose" or "required by applicable law."

The company has a legitimate interest in knowing which influencer applied/is collaborating. Immediate deletion mid-collaboration would break contractual obligations to both parties.

---

## 10. FE — Deletion Status Page

**Route**: `/deletion-status`

**Query parameter**: `code={confirmation_code}`

Simple public page (no auth required) that calls `GET /api/auth/instagram/deletion-status?code=...` and displays the status. Required by Meta's response format.

---

## 11. Meta App Dashboard Configuration

After implementation, configure in Meta App Dashboard → Instagram → Business Login:

1. **Deauthorize Callback URL**: `https://checkitout.app/api/auth/instagram/deauthorize`
2. **Data Deletion Request URL**: `https://checkitout.app/api/auth/instagram/data-deletion`

---

## 12. Summary of Changes

| Area                       | Change                                                              |
|----------------------------|---------------------------------------------------------------------|
| New service                | `MetaSignedRequestService` — shared `signed_request` parser         |
| New controller             | `InstagramCallbackController` — deauth + data deletion + status     |
| New table                  | `pending_data_deletion_request` — tracks deferred deletions         |
| New entity + repository    | `PendingDataDeletionRequest`                                        |
| New cron                   | Daily deferred deletion check (ShedLock)                            |
| `WebSecurityConfiguration` | Add 3 new public paths to `permitAll()`                             |
| User entity                | Add field/flag to track Instagram connection status                 |
| Configuration              | Add `meta.app-secret` property                                      |
| Email                      | Send password reset email on deauthorization                        |
| New FE page                | `/deletion-status` — public status check page                      |
