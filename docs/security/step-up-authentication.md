# Step-Up Authentication — Specification

## 1. Overview

A 6-digit email-based verification code required before executing critical actions. Designed as a reusable mechanism that any future feature can plug into.

### Scope (MVP)

Required for **companies and influencers**:
- **Email change**
- **Payment method changes** (Stripe card add/remove/update)

Can be extended later to: subscription plan changes, account deletion, etc.

**Not required for admins** — admins are already protected by TOTP on login + short session lifetime (see section below).

### Security Model by Role

| Role        | Step-up auth     | Session lifetime | MFA at login |
|-------------|------------------|------------------|--------------|
| Company     | Yes (email code) | 7 days           | No           |
| Influencer  | Yes (email code) | 7 days           | No           |
| Admin       | No               | 2 hours          | TOTP         |

**Why no step-up for admins**: Admins already authenticate with TOTP at every login. With a 2-hour session expiry, an attacker with a stolen session cookie has an extremely narrow window — and would need physical access to the machine within that window. Adding step-up on top of TOTP + 2hr session provides diminishing returns.

**Why 2 hours (not 1)**: 1 hour is too aggressive for admin workflows (reviewing cases, writing responses, checking dashboards). 2 hours balances security with usability. Session is not extended by activity — after 2 hours, re-login with TOTP is required regardless.

### Design Principles

- Email is the trusted verification channel for companies and influencers.
- No trusted device system for now — always require code.
- Low overhead: small, generic, pluggable into any action.

---

## 2. Code Lifecycle

### Generation

1. User initiates a critical action on FE.
2. FE calls `POST /api/auth/step-up/request` with `actionType` (e.g., `EMAIL_CHANGE`, `PAYMENT_METHOD_CHANGE`).
3. BE generates a **cryptographically random 6-digit code** (`SecureRandom`, not sequential).
4. BE stores the code in a `step_up_code` table (see section 3).
5. BE sends the code to the user's **current verified email**.
6. Only **one active code per user per action type** at a time — requesting a new code invalidates any previous one.

### Validation

1. User enters the code on FE.
2. FE calls `POST /api/auth/step-up/verify` with `actionType` and `code`.
3. BE validates: correct code, not expired, not exceeded attempt limit.
4. On success → BE returns a **single-use step-up token** (UUID, stored server-side, 10-minute TTL).
5. FE includes this token in the subsequent critical action request (e.g., the email change request).
6. BE validates the step-up token before executing the critical action, then invalidates it.

### Why a Step-Up Token

The code verification and the critical action are two separate API calls. The step-up token bridges them — it proves "this user passed step-up auth for this action within the last 10 minutes." This avoids race conditions and replay attacks.

---

## 3. Data Storage — Firestore + KMS

Step-up auth data is **ephemeral and security-sensitive** — Firestore is the natural home, alongside the existing TOTP codes and OAuth secrets. No new PostgreSQL tables needed.

### Why Firestore

- **Already stores encrypted auth data**: TOTP codes, OAuth secrets — proven pattern.
- **Built-in TTL**: Firestore document expiration handles cleanup automatically — no cron needed.
- **Atomic counters**: `FieldValue.increment()` for attempt tracking — no race conditions.
- **KMS encryption**: same envelope encryption used for TOTP codes.
- **Natural rate limiting**: Firestore read/write quotas provide an additional layer against abuse.

### Collection: `step_up_codes`

Document ID: `{firebaseUid}_{actionType}` (one active code per user per action type)

```
step_up_codes/{firebaseUid}_EMAIL_CHANGE
├── code: <KMS-encrypted 6-digit code>
├── actionType: "EMAIL_CHANGE"
├── attempts: 0
├── status: "ACTIVE"              // ACTIVE, USED, INVALIDATED
├── ipAddress: "192.168.1.1"
├── userAgent: "Mozilla/5.0 ..."
├── createdAt: Timestamp
├── expiresAt: Timestamp           // createdAt + 1 hour (Firestore TTL)
```

### Collection: `step_up_tokens`

Document ID: `{token UUID}`

```
step_up_tokens/{uuid}
├── firebaseUid: "abc123"
├── actionType: "EMAIL_CHANGE"
├── used: false
├── createdAt: Timestamp
├── expiresAt: Timestamp           // createdAt + 10 minutes (Firestore TTL)
```

### Collection: `step_up_lockouts`

Document ID: `{firebaseUid}_{actionType}`

```
step_up_lockouts/{firebaseUid}_EMAIL_CHANGE
├── failedCycles: 2
├── lockedUntil: Timestamp | null  // null if not locked, now + 24hr if locked
├── lastFailureAt: Timestamp
├── expiresAt: Timestamp           // auto-cleanup via TTL (24hr after lockedUntil)
```

### Encryption

- The 6-digit code is encrypted with **KMS** (same key ring / approach as TOTP codes) before storing in Firestore.
- Step-up tokens are UUIDs — no encryption needed (they're opaque, single-use, and short-lived).
- Decryption happens server-side only during verification.

### TTL / Cleanup

No cleanup cron required. Firestore's built-in TTL policy on the `expiresAt` field handles automatic document deletion:
- Codes: deleted after 1 hour.
- Tokens: deleted after 10 minutes.
- Lockouts: deleted 24 hours after lockout expires.

---

## 4. Brute Force Protection

### Three Layers

#### Layer 1: Per-Code Attempt Limit

- Each code allows **5 verification attempts**.
- After 5 failed attempts → code status set to `INVALIDATED`.
- User must request a new code.

#### Layer 2: Cooldown Between Codes

- After a code is invalidated (5 failed attempts) → **15-minute cooldown** before a new code can be requested for the same action type.
- Requesting a code during cooldown → return error with remaining cooldown time.

#### Layer 3: Action Lockout

- After **3 consecutively invalidated codes** (= 15 failed attempts total) → **24-hour lockout** for that action type.
- During lockout: code requests for that action type are rejected.
- Lockout counter resets on successful verification.

### Emergency Response on Lockout

When the 24-hour lockout is triggered (3 failed code cycles):

1. **Emergency email** sent to the user:
   - *"Someone has repeatedly attempted to perform a critical action on your account. If this was not you, your account may be compromised."*
   - Include: action type, IP address, timestamp, user-agent.
2. **Force logout** — invalidate all active sessions for the user (increment token version, evict from cache).
3. **Log** the event for security audit.

This way, if an attacker stole only the session cookie (not the password), the forced logout revokes their access immediately.

---

## 5. Email Change Flow (End-to-End)

Email change is the highest-risk action — it's a two-email flow.

### Steps

1. User navigates to "Change email" in settings.
2. User enters the **new email address**.
3. FE calls `POST /api/auth/step-up/request` with `actionType = EMAIL_CHANGE`.
4. BE sends 6-digit code to the **current email** (proves the real owner is making this request).
5. User enters the code → FE calls `POST /api/auth/step-up/verify` → receives step-up token.
6. FE calls `POST /api/users/me/change-email` with `newEmail` + step-up token.
7. BE validates the step-up token, then sends a **confirmation link to the new email**.
8. User clicks the confirmation link → BE updates the email.
9. BE sends an **emergency notification to the old email**:
   - *"Your email address has been changed to [new-email]. If this was not you, contact support immediately."*
   - Include a support link / recovery instructions.

### Cooldown After Failure

If the email change fails at any step (invalid code, expired token, etc.):
- A new email change operation is available only after **24 hours** (on top of the brute force cooldown).
- This prevents rapid-fire attempts even with valid codes.

### Cooldown After Success

After a successful email change:
- No further email change allowed for **72 hours**.
- Gives the user time to notice the emergency email if the change was unauthorized.

---

## 6. Payment Method Change Flow

Simpler than email change — single verification step.

### Steps

1. User navigates to payment settings.
2. User initiates card add/remove/update.
3. FE calls `POST /api/auth/step-up/request` with `actionType = PAYMENT_METHOD_CHANGE`.
4. BE sends 6-digit code to the user's email.
5. User enters the code → FE calls `POST /api/auth/step-up/verify` → receives step-up token.
6. FE includes the step-up token in the Stripe payment method mutation request.
7. BE validates the step-up token, executes the Stripe operation, invalidates the token.

No emergency email needed for payment changes (cards are tokenized, low risk). Standard operation.

---

## 7. Backend Implementation

### `StepUpAuthService`

Core service, pluggable by any feature.

```
requestCode(userId, actionType, ipAddress) → void
verifyCode(userId, actionType, code) → StepUpToken
validateToken(userId, actionType, token) → void  // throws if invalid
```

### Integration Pattern

Any controller requiring step-up auth:

```java
@PostMapping("/users/me/change-email")
public ResponseEntity<?> changeEmail(
        @RequestBody ChangeEmailRequest request,
        HttpServletRequest servletRequest) {
    User user = resolveCurrentUser();
    stepUpAuthService.validateToken(user.getId(), ActionType.EMAIL_CHANGE, request.getStepUpToken());
    // proceed with email change...
}
```

The service should check the user's role — admins skip step-up validation entirely (they are protected by TOTP + 2hr session instead).

No cleanup cron needed — Firestore TTL on the `expiresAt` field handles automatic document deletion for all three collections.

### Admin Session Configuration

Reduce admin session lifetime from 7 days to **2 hours**. Session is **not extended by activity** — hard expiry forces re-login with TOTP regardless.

Changes required:
- Session/JWT TTL: role-based configuration (`admin: 2h`, `company/influencer: 7d`).
- FE: handle session expiry gracefully for admins — redirect to login without losing unsaved work if possible.

---

## 8. API Endpoints

| Method | Path                          | Auth       | Purpose                                  |
|--------|-------------------------------|------------|------------------------------------------|
| POST   | `/api/auth/step-up/request`   | Logged in  | Request a 6-digit code for an action     |
| POST   | `/api/auth/step-up/verify`    | Logged in  | Verify the code, receive step-up token   |

### Request Code — `POST /api/auth/step-up/request`

```json
{
  "actionType": "EMAIL_CHANGE"
}
```

Response `200 OK`:
```json
{
  "message": "Verification code sent to your email.",
  "expiresInMinutes": 60
}
```

Response `429 Too Many Requests` (cooldown active):
```json
{
  "error": "step_up.cooldown",
  "retryAfterMinutes": 12
}
```

Response `423 Locked` (24hr lockout):
```json
{
  "error": "step_up.locked",
  "retryAfterMinutes": 1380
}
```

### Verify Code — `POST /api/auth/step-up/verify`

```json
{
  "actionType": "EMAIL_CHANGE",
  "code": "482917"
}
```

Response `200 OK`:
```json
{
  "stepUpToken": "a1b2c3d4-e5f6-...",
  "expiresInMinutes": 10
}
```

Response `400 Bad Request` (wrong code):
```json
{
  "error": "step_up.invalid_code",
  "attemptsRemaining": 3
}
```

---

## 9. Summary of Changes

| Area                   | Change                                                              |
|------------------------|---------------------------------------------------------------------|
| Firestore collections  | `step_up_codes`, `step_up_tokens`, `step_up_lockouts` (KMS-encrypted, TTL auto-cleanup) |
| New service            | `StepUpAuthService` — generic, reusable for any critical action     |
| New endpoints          | `/api/auth/step-up/request`, `/api/auth/step-up/verify`            |
| Email templates        | Step-up code email, emergency brute force alert, email change alert |
| Email change flow      | Requires step-up + confirmation link to new email + old email alert |
| Payment method flow    | Requires step-up before Stripe mutations                            |
| Session management     | Force logout on brute force lockout (increment token version)       |
| Admin session          | Reduce from 7 days to 2 hours (hard expiry, no extension)          |
| JWT/session config     | Role-based TTL: admin 2h, company/influencer 7d                    |

---

## 10. Future Consideration: WebAuthn / Passkeys

Email codes are the MVP step-up mechanism. A future upgrade path is **WebAuthn (FIDO2) passkeys** — using the phone as a secure hardware module with biometric authentication (Face ID, fingerprint, Windows Hello).

### Why passkeys are better (long-term)

- **Phishing-resistant**: cryptographic challenge-response bound to the origin — cannot be intercepted or replayed.
- **Better UX**: tap phone / scan face vs. switch to email inbox, find code, type 6 digits.
- **No shared secret**: public key cryptography — nothing to steal from the server.

### Why not now

- **Implementation scope**: WebAuthn API on FE, FIDO2 server library on BE (`java-webauthn-server`), credential storage, cross-platform testing (Android, iOS, Windows Hello), device management UI, recovery flows.
- **Email codes cover the current risk profile**: with IP binding, session fingerprint, impossible travel detection, and the low user count — email codes provide sufficient security at a fraction of the effort.

### When to revisit

- When the platform scales to a point where companies handle significant payment volumes.
- When passkey adoption is mainstream enough that users expect it.
- Can be offered as an optional upgrade alongside email codes — not a replacement.
