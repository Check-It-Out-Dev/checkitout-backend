# Firebase Magic Link Pages — Specification

## 1. Overview

Custom FE pages that handle Firebase Authentication magic links (email verification and password reset). Instead of using Firebase's default hosted pages, users stay on `checkitout.app` throughout the entire flow — consistent, branded experience with full i18n support.

These pages are used by **all user types** (company + influencer), not just Instagram OAuth users.

### Related Documents

- **Instagram OAuth Refinement** (`InstagramOAuthRefinement.md`) — parent specification.
- **Step-Up Authentication** (`StepUpAuthentication.md`) — email change flow uses password reset page.

---

## 2. Architecture

Firebase generates magic links containing `oobCode` (one-time action code) and `mode` parameters. We configure Firebase to point these links to our FE instead of the default `firebaseapp.com` hosted pages.

### Firebase Configuration

In Firebase Console → Authentication → Templates, set the **Action URL** for all email templates to:

```
https://checkitout.app/auth/action
```

All Firebase email templates (verification, password reset) will now link to our domain.

---

## 3. FE Routes

| Route                   | Purpose                                              |
|-------------------------|------------------------------------------------------|
| `/auth/action`          | Firebase magic link router (reads `mode`, redirects) |
| `/auth/verify-email`    | Email verification page (handles `oobCode`)          |
| `/auth/reset-password`  | Password setup / reset page (handles `oobCode`)      |

All three routes are **public** (no auth guard) — the user may not be logged in when clicking the link from their email.

---

## 4. Action Router Page

**Route**: `/auth/action`

Single entry point that reads Firebase query parameters and redirects to the appropriate handler page.

**Query parameters** (from Firebase magic link):
- `mode` — `verifyEmail` or `resetPassword`
- `oobCode` — one-time action code
- `apiKey` — Firebase API key
- `continueUrl` — optional redirect after completion
- `lang` — optional language hint

**Logic**:
- `mode=verifyEmail` → redirect to `/auth/verify-email?oobCode=...&continueUrl=...&lang=...`
- `mode=resetPassword` → redirect to `/auth/reset-password?oobCode=...&lang=...`
- Unknown mode → show error page

---

## 5. Email Verification Page

**Route**: `/auth/verify-email`

**Query parameters**:
- `oobCode` — required
- `continueUrl` — optional redirect after success
- `lang` — optional language

### Flow

1. Page reads `oobCode` from URL on init.
2. Calls Firebase Auth REST API: `applyActionCode(oobCode)` to verify the email.
3. **On success**:
   - Show confirmation message: "Your email has been verified."
   - Call BE `POST /api/auth/token/exchange` to refresh the session (so `emailVerified` flag updates server-side).
   - If `continueUrl` is present → show "Continue" button linking to it.
   - Otherwise → redirect to platform home (or `/company/setup` for company users, `/dashboard` for influencers).
4. **On error** (expired/invalid code):
   - Show error message: "This verification link has expired or is invalid."
   - Show "Resend verification email" button → calls `POST /api/auth/send-verification-email`.

### States

```
VERIFYING → SUCCESS | ERROR | EXPIRED
```

---

## 6. Password Setup / Reset Page

**Route**: `/auth/reset-password`

**Query parameters**:
- `oobCode` — required
- `lang` — optional language

### Flow

1. Page reads `oobCode` from URL on init.
2. Calls Firebase Auth REST API: `verifyPasswordResetCode(oobCode)` to validate the code and retrieve the associated email.
3. **If code is valid** → show password form:
   - Email displayed (read-only, from `verifyPasswordResetCode` response).
   - New password input.
   - Confirm password input.
   - Password strength indicator (min 8 chars, uppercase, lowercase, number).
4. On submit → calls `confirmPasswordReset(oobCode, newPassword)`.
5. **On success** → show confirmation + "Go to login" button.
6. **On error** (expired/invalid code) → show error message with "Request new link" button.

### States

```
VALIDATING_CODE → FORM | CODE_EXPIRED
FORM → SUBMITTING → SUCCESS | ERROR
```

---

## 7. Firebase REST API Calls

Since there is no Firebase JS SDK on the FE, these pages call the **Firebase Auth REST API** directly:

### `applyActionCode` (email verification)

```
POST https://identitytoolkit.googleapis.com/v1/accounts:update?key={API_KEY}
Content-Type: application/json

{ "oobCode": "<code>" }
```

### `verifyPasswordResetCode`

```
POST https://identitytoolkit.googleapis.com/v1/accounts:resetPassword?key={API_KEY}
Content-Type: application/json

{ "oobCode": "<code>" }
```

Response includes `email` and `requestType`.

### `confirmPasswordReset`

```
POST https://identitytoolkit.googleapis.com/v1/accounts:resetPassword?key={API_KEY}
Content-Type: application/json

{ "oobCode": "<code>", "newPassword": "<password>" }
```

### FE Service

New `FirebaseActionService` wrapping these REST calls:

```typescript
@Injectable({ providedIn: 'root' })
export class FirebaseActionService {
  applyActionCode(oobCode: string): Observable<void>
  verifyPasswordResetCode(oobCode: string): Observable<{ email: string }>
  confirmPasswordReset(oobCode: string, newPassword: string): Observable<void>
}
```

Uses `environment.firebaseApiKey` for the API key.

---

## 8. Dev / Local / Test Fallback Mode

In development and test environments, real Firebase magic links are impractical — emails go to real inboxes, codes expire, and the Firebase Action URL points to production. A fallback mode enables local testing without external dependencies.

### Configuration

```typescript
// environment.ts / environment.development.ts
export const environment = {
  firebaseMagicLinkFallback: true,   // true in dev/local/test, false in production
  firebaseApiKey: '...',
};
```

### Behavior When `firebaseMagicLinkFallback = true`

#### Email Verification Page (`/auth/verify-email`)

- If `oobCode` query param is **missing or equals `dev-bypass`**:
  - Skip the `applyActionCode` call entirely.
  - Call BE `POST /api/auth/send-verification-email` instead (which uses the existing server-side mechanism).
  - Show a message: "Dev mode: verification email sent via BE. Check your inbox or use the BE test endpoint."
- If `oobCode` is present and valid → normal flow (still works if testing with real Firebase codes locally).

#### Password Reset Page (`/auth/reset-password`)

- If `oobCode` query param is **missing or equals `dev-bypass`**:
  - Show the password form immediately with a prefilled email from the logged-in user (if available).
  - On submit → call BE `POST /api/auth/reset-password` (the existing server-side password reset endpoint) instead of Firebase REST API.
  - Show a message: "Dev mode: password reset handled via BE."
- If `oobCode` is present and valid → normal flow.

#### Action Router Page (`/auth/action`)

- If `firebaseMagicLinkFallback = true` and no `mode` param → show a **dev tools panel**:
  - "Verify Email (dev bypass)" button → navigates to `/auth/verify-email?oobCode=dev-bypass`
  - "Reset Password (dev bypass)" button → navigates to `/auth/reset-password?oobCode=dev-bypass`

### Why This Design

- **Same FE pages** in all environments — no separate dev-only components.
- **Real Firebase flow still works** locally if you have a valid `oobCode` (e.g., from a test Firebase project).
- **`dev-bypass` is gated** by the `firebaseMagicLinkFallback` environment flag — production ignores it entirely.
- **No security risk** — fallback mode in prod is `false`; even if someone navigates to `?oobCode=dev-bypass` in prod, the page calls `applyActionCode('dev-bypass')` which Firebase rejects.

---

## 9. i18n

All pages use Transloco. New translation keys:

```
auth.verify_email.title
auth.verify_email.verifying
auth.verify_email.success
auth.verify_email.error_expired
auth.verify_email.resend_button

auth.reset_password.title
auth.reset_password.validating
auth.reset_password.new_password_label
auth.reset_password.confirm_password_label
auth.reset_password.submit_button
auth.reset_password.success
auth.reset_password.error_expired
auth.reset_password.request_new_link

auth.action.unknown_mode
auth.action.dev_tools_title
```

---

## 10. Benefits

- **Branded experience**: users stay on `checkitout.app` throughout — no redirect to `firebaseapp.com`.
- **Unified for all users**: company users (email/password registration) and influencers (Instagram OAuth) use the same pages.
- **Custom error handling**: expired links, resend options, and redirects are fully under our control.
- **Localization**: PL/EN support via Transloco, matching the rest of the platform.
- **Testable**: dev fallback mode enables local testing without Firebase email delivery.

---

## 11. Summary of Changes

| Area                    | Change                                                                    |
|-------------------------|---------------------------------------------------------------------------|
| New FE routes           | `/auth/action`, `/auth/verify-email`, `/auth/reset-password`             |
| New FE service          | `FirebaseActionService` — wraps Firebase Auth REST API                   |
| New FE components       | `ActionRouterComponent`, `VerifyEmailComponent`, `ResetPasswordComponent` |
| Environment config      | Add `firebaseMagicLinkFallback` and `firebaseApiKey`                     |
| i18n                    | New `auth.*` translation keys (EN + PL)                                  |
| Firebase Console        | Configure Action URL to `https://checkitout.app/auth/action`             |
| Routing module          | Add 3 public routes (no auth guard)                                      |
