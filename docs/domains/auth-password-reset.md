# Password Reset Feature - Architecture Document

**Version:** 1.0
**Date:** 2026-01-29
**Status:** Production Ready (with cleanup required)

---

## Overview

The password reset feature follows the same Firebase-hosted pattern as email verification. **Firebase handles the actual password reset UI and logic** - our application only triggers the flow by sending an email with a Firebase-generated link.

---

## Reference Implementation: Email Verification

### How Email Verification Currently Works

**File:** `EmailVerificationService.java`

```java
// Generate Firebase-hosted verification link
ActionCodeSettings settings = ActionCodeSettings.builder()
    .setUrl(frontendUrl + "/settings/account")  // Where to redirect AFTER verification
    .setHandleCodeInApp(false)  // Firebase hosts the verification page
    .build();

String verificationLink = firebaseAuth.generateEmailVerificationLink(user.getEmail(), settings);

// Send email with Firebase link
emailService.sendVerificationEmail(email, userName, verificationLink, language);
```

**Flow:**
1. User requests verification email
2. Backend generates Firebase link
3. Email sent with link: `https://checkitout-app.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=xyz`
4. User clicks → **Firebase hosted page** (not our app)
5. Firebase verifies email automatically
6. Firebase redirects to: `frontendUrl + "/settings/account"`
7. User sees updated status in our app

**Key Point:** We generate the link and send the email. **Firebase handles the verification UI.**

---

## Password Reset Architecture (Same Pattern)

### Design Principle

**Firebase Hosts, We Orchestrate**

Our application:
- ✅ Validates email is verified in our system
- ✅ Generates Firebase password reset link
- ✅ Sends email with link
- ✅ Provides redirect destination after reset
- ❌ Does NOT host password reset form
- ❌ Does NOT handle oobCode verification
- ❌ Does NOT perform password change

Firebase:
- ✅ Hosts password reset form
- ✅ Validates oobCode
- ✅ Enforces password strength rules
- ✅ Changes password in Firebase Auth
- ✅ Redirects to our app after success

---

## Components

### Backend

#### 1. Endpoint: `POST /auth/firebase/forgot-password`

**File:** `FirebaseAuthProxyController.java` (lines 683-731)

**Input:**
```json
{
  "email": "user@example.com"
}
```

**Headers:**
- `Accept-Language: en|pl` (optional, defaults to "en")
- `X-Recaptcha-Token: <token>` (required by @RequiresRecaptcha)

**Rate Limiting:**
- Profile: `RateLimitProfile.AUTH` (50 requests per 60 seconds)
- Key Type: `RateLimitKeyType.IP_ENDPOINT` (per IP, per endpoint)
- **NOT keyed by email** (prevents user enumeration via rate limit)

**Response:**
```json
{
  "success": true,
  "messageKey": "auth.forgot_password.success_message"
}
```

**ALWAYS the same response** regardless of:
- Email exists or not
- Email verified or not
- Verification email sent or password reset email sent

**Exception:** Rate limit (429):
```json
{
  "error": "error.ratelimit.password_reset"
}
```

---

#### 2. Service: PasswordResetService

**File:** `PasswordResetService.java`

**Method:** `requestPasswordReset(String email, String language)`

**Logic:**
```
IF email NOT in database:
    → Log (masked email)
    → Timing normalization
    → Return success() [SILENT FAIL for security]

IF email in database BUT NOT verified:
    → Send verification email instead
    → Update passwordResetSentAt timestamp
    → Timing normalization
    → Return success() [SAME RESPONSE]

IF email in database AND verified:
    → Generate Firebase password reset link
    → Send password reset email with link
    → Update passwordResetSentAt timestamp
    → Timing normalization
    → Return success() [SAME RESPONSE]
```

**Security Features:**
1. **No User Enumeration:**
   - Same HTTP status (200)
   - Same response body
   - Timing normalization (500ms ± random jitter)
   - No "action" field differentiating responses

2. **Rate Limiting:**
   - 60-second cooldown per user (passwordResetSentAt check)
   - 50 requests per minute per IP (controller level)
   - IP + endpoint keyed (not email keyed)

3. **Input Sanitization:**
   - Email validated: `@NotBlank`, `@Email`, `@Size(max=254)`
   - Language sanitized: max 10 chars, newlines removed
   - Log injection prevented: newlines replaced with underscores

---

#### 3. Firebase Link Generation

```java
ActionCodeSettings settings = ActionCodeSettings.builder()
    .setUrl(frontendUrl + "/auth/sign-in?passwordReset=success")
    .setHandleCodeInApp(false)  // Firebase hosts the reset page
    .build();

String resetLink = firebaseAuth.generatePasswordResetLink(email, settings);
```

**Generated Link Format:**
```
https://checkitout-app.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=ABC123&continueUrl=https://app.check-it-out.pl/auth/sign-in?passwordReset=success
```

**Link Behavior:**
- Opens Firebase's hosted password reset page
- User enters new password on Firebase's page
- Firebase validates and changes password
- Firebase redirects to `continueUrl` after success

---

### Frontend

#### 1. Component: Forgot Password

**File:** `forgot-password.component.ts`

**Form Fields:**
- Email (required, validated)
- **NO password field**

**Flow:**
1. User enters email
2. ReCAPTCHA token generated (action: "PASSWORD_RESET")
3. Calls `authService.forgotPassword(email, recaptchaToken)`
4. Shows success alert: "Email sent to you. First confirm email, then click reset password again"
5. Form re-enabled (email preserved on error)

**Error Handling:**
- Rate limit (429) → Shows rate limit message
- Other errors → Shows generic error
- **Does NOT differentiate** based on email existence

---

#### 2. Component: Sign-In (Success Message)

**File:** `sign-in.component.ts`

**Enhancement Needed:**
```typescript
ngOnInit() {
  // Check if redirected from Firebase after password reset
  const passwordReset = this._activatedRoute.snapshot.queryParamMap.get('passwordReset');
  if (passwordReset === 'success') {
    this.alert = {
      type: 'success',
      message: this._translocoService.translate('auth.sign_in.password_reset_success')
    };
    this.showAlert = true;
  }
}
```

---

## Email Templates

### 1. Password Reset Email

**File:** `password-reset.html`

**Variables:**
- `${userName}` - User's first name or "User"
- `${resetLink}` - Firebase-generated link
- `${currentYear}` - Current year for footer

**Structure:**
- Table-based layout (Gmail/Outlook compatible)
- Inline CSS (no style blocks)
- VML button for Outlook
- Mobile responsive (max-width: 600px)

**Button Links To:**
Firebase's hosted password reset page (not our app!)

---

### 2. Verification Email

**File:** `verification.html`

**Same structure** as password-reset.html (table-based, inline CSS, VML)

**Button Links To:**
Firebase's hosted email verification page (not our app!)

---

## Comparison: Email Verification vs Password Reset

| Aspect | Email Verification | Password Reset |
|--------|-------------------|----------------|
| **Trigger** | User clicks "Resend verification" | User clicks "Forgot password" |
| **Input** | User's firebaseUid (logged in) | Email address (not logged in) |
| **Email Check** | Throws error if already verified | Sends verification email if not verified |
| **Firebase Method** | `generateEmailVerificationLink()` | `generatePasswordResetLink()` |
| **Email Template** | `verification.html` | `password-reset.html` |
| **Firebase Action** | Verifies email | Resets password |
| **Redirect After** | `/settings/account` | `/auth/sign-in?passwordReset=success` |
| **User Enumeration** | N/A (user already logged in) | Prevented (same response always) |

---

## Security Architecture

### 1. No User Enumeration

**Problem:**
Attackers can enumerate valid emails by observing different responses.

**Solution:**
```
User exists + verified    → ForgotPasswordResponse.success()
User exists + unverified  → ForgotPasswordResponse.success()
User does NOT exist       → ForgotPasswordResponse.success()

ALL RETURN IDENTICAL RESPONSE
```

**Additional Protections:**
- Timing normalization (all responses ~500ms)
- No "action" field in response
- Same HTTP status code (200)
- Exception swallowing (no leaked information)

---

### 2. Rate Limiting Strategy

**Controller Level:**
```java
@RateLimit(
  profile = RateLimitProfile.AUTH,           // 50 req/min
  keyType = RateLimitKeyType.IP_ENDPOINT,    // Per IP + endpoint
  errorMessage = "error.ratelimit.password_reset"
)
```

**Service Level:**
```java
// Check passwordResetSentAt timestamp
if (lastRequest < 60 seconds ago) {
    return success();  // Same response, logged internally
}
```

**Why IP + Endpoint (NOT per email):**
- Keying by email would reveal email existence (enumeration attack)
- Attacker testing many emails from one IP gets rate limited
- Attacker cannot distinguish between:
  - Rate limit for this specific email
  - Rate limit for endpoint in general

---

### 3. Exception Handling

**All exceptions caught and swallowed:**
```java
try {
    emailVerificationService.sendVerificationEmail(...)
} catch (Exception e) {
    // ALL exceptions caught (not just specific types)
    // Logged for debugging
    // NOT propagated to prevent info leakage
    log.warn("Failed to send verification email: {}", e.getMessage());
}
return ForgotPasswordResponse.success();  // Same response
```

**Exceptions that could leak information:**
- `ResourceNotFoundException` - user not found
- `ValidationTranslatableException` - email already verified
- `RateLimitTranslatableException` - too many attempts
- `MessagingException` - SMTP failure

**All return same response to frontend.**

---

## Message Keys (i18n)

### Success Message (Always Shown)

**Key:** `auth.forgot_password.success_message`

**English:**
```
"We have sent an email to your address. If your email is in our system and verified, you will receive a password reset link. If your email is not yet verified, please verify it first using the link in the email, then click 'Forgot Password' again."
```

**Polish:**
```
"Wysłaliśmy email na Twój adres. Jeśli Twój email jest w naszym systemie i zweryfikowany, otrzymasz link do resetowania hasła. Jeśli Twój email nie jest jeszcze zweryfikowany, proszę najpierw go zweryfikować używając linku w emailu, a następnie kliknij 'Zapomniałem hasła' ponownie."
```

**Key Characteristics:**
- Doesn't confirm email exists
- Explains both scenarios (verified vs unverified)
- Guides user to verify email first if needed
- No information leakage

---

### Rate Limit Message

**Key:** `auth.forgot_password.rate_limited`

**English:**
```
"Too many password reset requests. Please wait before trying again."
```

**Polish:**
```
"Zbyt wiele żądań resetowania hasła. Proszę poczekać przed ponowną próbą."
```

**Key Characteristics:**
- Doesn't reveal whether limit is for specific email or general
- Doesn't reveal email existence
- Clear instruction to wait

---

## Data Model

### User Entity Fields

```java
// Email verification (existing)
@Column(name = "email_verified", nullable = false)
private Boolean emailVerified = false;

@Column(name = "email_verification_sent_at")
private LocalDateTime emailVerificationSentAt;

@Column(name = "email_verified_at")
private LocalDateTime emailVerifiedAt;

// Password reset (NEW - added for duplicate prevention)
@Column(name = "password_reset_sent_at")
private LocalDateTime passwordResetSentAt;
```

**Migration:** `007-password-reset-timestamp.sql`

---

## API Contract

### Request

**Endpoint:** `POST /auth/firebase/forgot-password`

**Headers:**
```
Content-Type: application/json
Accept-Language: en|pl (optional)
X-Recaptcha-Token: <token> (required)
```

**Body:**
```json
{
  "email": "user@example.com"
}
```

**Validation:**
- `@NotBlank` - email required
- `@Email` - valid email format
- `@Size(max=254)` - RFC 5321 maximum length
- Explicit null checks in controller

---

### Response (Success)

**Status:** `200 OK`

**Body:**
```json
{
  "success": true,
  "messageKey": "auth.forgot_password.success_message"
}
```

**This response is returned for ALL scenarios:**
- User not found
- User found but email unverified (verification email sent)
- User found and email verified (password reset email sent)
- Duplicate request within cooldown window

---

### Response (Rate Limited)

**Status:** `429 Too Many Requests`

**Body:**
```json
{
  "error": {
    "message": "error.ratelimit.password_reset"
  }
}
```

---

## Execution Flow

### Scenario 1: Email Does Not Exist

```
1. User enters: nonexistent@example.com
2. Backend: userRepository.findByEmail() → Optional.empty()
3. Backend: log.info("Password reset requested for non-existent email")
4. Backend: normalizeResponseTiming(500ms)
5. Backend: return ForgotPasswordResponse.success()
6. Frontend: Shows "Email sent to you. First confirm email..."
7. User: Receives NO email (silent fail for security)
```

**No information leaked** - same message as if email existed.

---

### Scenario 2: Email Exists But NOT Verified

```
1. User enters: unverified@example.com
2. Backend: User found, emailVerified = false
3. Backend: Sends VERIFICATION email (not password reset)
4. Backend: Updates passwordResetSentAt timestamp
5. Backend: normalizeResponseTiming(500ms)
6. Backend: return ForgotPasswordResponse.success()
7. Frontend: Shows "Email sent to you. First confirm email..."
8. User: Receives VERIFICATION email with Firebase link
9. User: Clicks link → Firebase hosted verification page
10. Firebase: Verifies email
11. Firebase: Redirects to /settings/account
12. User: Returns to app, clicks "Forgot Password" again
13. Now goes to Scenario 3 (verified)
```

**Same message** as Scenario 1 - no enumeration.

---

### Scenario 3: Email Exists AND Verified

```
1. User enters: verified@example.com
2. Backend: User found, emailVerified = true
3. Backend: Calls firebaseAuth.generatePasswordResetLink(email, settings)
4. Backend: Sends PASSWORD RESET email with Firebase link
5. Backend: Updates passwordResetSentAt timestamp
6. Backend: normalizeResponseTiming(500ms)
7. Backend: return ForgotPasswordResponse.success()
8. Frontend: Shows "Email sent to you. First confirm email..."
9. User: Receives PASSWORD RESET email
10. User: Clicks link → Firebase hosted password reset page
11. Firebase: Shows password form
12. User: Enters new password
13. Firebase: Validates and changes password
14. Firebase: Redirects to /auth/sign-in?passwordReset=success
15. User: Signs in with new password
```

**Same message** as Scenarios 1 & 2 - no enumeration.

---

### Scenario 4: Rate Limited

```
1. User submits 51st request within 60 seconds
2. Backend: @RateLimit annotation blocks request
3. Backend: Returns 429 status
4. Frontend: Shows "Too many requests. Please wait."
```

**Different message** (only exception to unified messaging).

**Does NOT reveal:**
- Whether rate limit is for this specific email
- Whether email exists in system
- Just: "You hit a rate limit, wait"

---

## Firebase Configuration

### ActionCodeSettings

**Current Implementation:**
```java
ActionCodeSettings settings = ActionCodeSettings.builder()
    .setUrl(frontendUrl + "/auth/reset-password")  // ⚠️ WRONG - points to broken component
    .setHandleCodeInApp(false)
    .build();
```

**Correct Implementation:**
```java
ActionCodeSettings settings = ActionCodeSettings.builder()
    .setUrl(frontendUrl + "/auth/sign-in?passwordReset=success")
    .setHandleCodeInApp(false)  // Firebase hosts the reset form
    .build();
```

**Explanation:**
- `.setUrl()` = Where to redirect AFTER password reset is complete
- `.setHandleCodeInApp(false)` = Firebase hosts the reset page (not our app)
- Firebase manages the entire password reset UI and logic

---

## Frontend Components

### Required: forgot-password Component

**File:** `forgot-password.component.ts`

**Purpose:** Trigger password reset flow

**Fields:**
- Email (only field)

**Behavior:**
- Validates email format
- Generates reCAPTCHA token
- Calls backend `/auth/firebase/forgot-password`
- Shows success message (same for all scenarios)
- Preserves email on error (no form reset)

---

### NOT Required: reset-password Component

**Current State:** Component exists with password form

**Problem:** Duplicates Firebase functionality

**Recommendation:** **DELETE THIS COMPONENT**

**Why:**
- Firebase already hosts password reset page
- Our component calls non-existent backend endpoint
- Creates confusion about where password reset happens
- Firebase link goes directly to Firebase page, not our app

**Cleanup Required:**
1. Delete `reset-password/` component directory
2. Remove route from `authentication.routes.ts`
3. Remove `resetPassword()` method from `auth.service.ts` (dead code)

---

### Optional: Success Message in Sign-In

**Enhancement:** Show success message when redirected from Firebase

**File:** `sign-in.component.ts`

**Add to ngOnInit:**
```typescript
const passwordReset = this._activatedRoute.snapshot.queryParamMap.get('passwordReset');
if (passwordReset === 'success') {
  this.alert = {
    type: 'success',
    message: this._translocoService.translate('auth.sign_in.password_reset_success')
  };
  this.showAlert = true;
}
```

**Translation Key:**
```json
"password_reset_success": "Your password has been reset successfully. Please sign in with your new password."
```

---

## Comparison Table: What We Handle vs What Firebase Handles

| Task | Our Application | Firebase |
|------|----------------|----------|
| Validate email verified | ✅ YES | - |
| Generate reset link | ✅ YES (via SDK) | - |
| Send email | ✅ YES | - |
| Host password reset form | ❌ NO | ✅ YES |
| Validate oobCode | ❌ NO | ✅ YES |
| Enforce password strength | ❌ NO | ✅ YES |
| Change password in Auth | ❌ NO | ✅ YES |
| Redirect after success | ✅ YES (configure URL) | ✅ YES (performs redirect) |

---

## Security Guarantees

### What Attackers CANNOT Learn

❌ Whether email exists in system
❌ Whether email is verified
❌ Which type of email was sent (verification vs reset)
❌ Whether email delivery succeeded or failed
❌ User IDs, names, or any other information

### What Attackers CAN Learn

✅ Rate limit threshold (~50 requests)
✅ That the endpoint uses reCAPTCHA
✅ That email verification is required (stated in message)

**These are acceptable disclosures** - they don't enable enumeration attacks.

---

## Files Affected

### Backend (Correct - Keep)

| File | Purpose | Status |
|------|---------|--------|
| `PasswordResetService.java` | Core logic | ✅ Correct |
| `FirebaseAuthProxyController.java` | `/forgot-password` endpoint | ✅ Correct |
| `ForgotPasswordRequest.java` | DTO (email only) | ✅ Correct |
| `ForgotPasswordResponse.java` | DTO (unified response) | ✅ Correct |
| `EmailService.java` | Send password reset email | ✅ Correct |
| `password-reset.html` | Email template | ✅ Correct (after VML fix) |
| `verification.html` | Email template | ✅ Correct (after rebuild) |
| `User.java` | Added passwordResetSentAt | ✅ Correct |

### Frontend (Needs Cleanup)

| File | Purpose | Status |
|------|---------|--------|
| `forgot-password.component.ts` | Email input form | ✅ KEEP |
| `authentication.routes.ts` | Route for forgot-password | ✅ KEEP |
| `auth.service.forgotPassword()` | Call backend | ✅ KEEP |
| `reset-password.component.ts` | ❌ Password form (unnecessary) | ⚠️ DELETE |
| `authentication.routes.ts` | ❌ /reset-password route | ⚠️ REMOVE |
| `auth.service.resetPassword()` | ❌ Dead code | ⚠️ REMOVE |

---

## Testing Strategy

### Unit Tests Required

**Backend:**
1. `PasswordResetServiceTest.java` (20-25 tests)
   - User not found → returns success
   - User unverified → sends verification email, returns success
   - User verified → sends reset email, returns success
   - Cooldown active → returns success (no email sent)
   - Null firebaseUserId → skips verification email
   - Timing normalization works
   - Log injection prevented

2. Controller tests for `/forgot-password` endpoint (6 tests)
   - Valid request → 200 OK
   - Null request → 400 validation error
   - Null email → 400 validation error
   - Rate limited → 429
   - Language sanitization works
   - Client IP extracted

**Frontend:**
- `forgot-password.component.spec.ts` - ✅ Already exists (17 tests)
- Remove reset-password tests (component will be deleted)

---

### Integration Tests Required

1. **Timing consistency test:**
   - Measure response time for existing vs non-existing email
   - Assert difference < 100ms

2. **Rate limiting test:**
   - Send 51 requests
   - Assert 51st returns 429

3. **Email sending test:**
   - Mock SMTP
   - Verify email sent with correct template
   - Verify Firebase link in email body

---

### Manual Tests Required

1. **Email client rendering:**
   - Send test email to Gmail, Outlook, Yahoo
   - Verify button visible and clickable
   - Verify mobile responsive

2. **Complete user journey:**
   - Request password reset for unverified email
   - Verify email via Firebase link
   - Request password reset again
   - Reset password via Firebase link
   - Sign in with new password

---

## Cleanup Actions Required

### 1. Remove Unnecessary Frontend Components

**Files to DELETE:**
```
src/app/feature/authentication/components/reset-password/
├── reset-password.component.ts
├── reset-password.component.html
├── reset-password.component.spec.ts
└── reset-password.component.scss (if exists)
```

**Why:** Firebase hosts the password reset form, not our app.

---

### 2. Remove Dead Code from auth.service.ts

**Lines 283-289 - DELETE:**
```typescript
resetPassword(password: string, oobCode: string): Observable<any> {
  return this._httpClient.post(
    `${this._baseUrl}/reset-password`,  // Endpoint doesn't exist
    { password, oobCode },
    { withCredentials: true }
  );
}
```

**Why:** This calls a non-existent backend endpoint.

---

### 3. Remove Route

**File:** `authentication.routes.ts`

**DELETE:**
```typescript
{
  path: 'reset-password',
  canActivate: [NoAuthGuard],
  component: AuthResetPasswordComponent
},
```

**Also remove import:**
```typescript
import { AuthResetPasswordComponent } from './reset-password/reset-password.component';
```

---

### 4. Update ActionCodeSettings

**File:** `PasswordResetService.java` Line 115

**Change from:**
```java
.setUrl(frontendUrl + "/auth/reset-password")
```

**To:**
```java
.setUrl(frontendUrl + "/auth/sign-in?passwordReset=success")
```

---

### 5. Add Success Message to Sign-In (Optional)

**File:** `sign-in.component.ts`

**Add logic to check for passwordReset=success query param**
**Add translation key:** `auth.sign_in.password_reset_success`

---

## Architecture Decisions

### Why Firebase Hosts the Reset Page

**Pros:**
- ✅ Firebase handles security (oobCode validation, expiry, single-use)
- ✅ Firebase enforces password strength rules
- ✅ Reduces our attack surface (no oobCode handling)
- ✅ Consistent with email verification pattern
- ✅ No frontend Firebase SDK needed
- ✅ Firebase automatically localizes the page

**Cons:**
- ❌ Less control over UI/UX
- ❌ User leaves our app briefly
- ❌ Cannot customize password reset form

**Decision:** Pros outweigh cons - security and simplicity more important than custom UI.

---

### Why No Firebase SDK on Frontend

**Current Architecture:**
- Frontend calls our backend exclusively
- Backend uses Firebase Admin SDK
- All auth flows proxied through backend

**Benefits:**
- ✅ Complete audit trail (all requests logged)
- ✅ Centralized rate limiting
- ✅ GDPR compliance (all access logged)
- ✅ No client-side Firebase credentials
- ✅ Backend can add business logic (email verification requirement)

**Alternative (Not Used):**
- Frontend uses Firebase Web SDK directly
- Would bypass our business rules
- Would miss audit logging

---

## GDPR Compliance

### Data Processing Log

**Operation:** `forgotPassword`

**Logged Fields:**
```
Operation=forgotPassword
Email=use***@example.com (masked)
IP=192.168.1.xxx (masked)
DataAccessed=email.address
Purpose=password_reset
LegalBasis=contract
```

**Retention:** Logs retained per GDPR policy (90 days)

---

## Future Enhancements (Optional)

### 1. Per-Email Rate Limiting (Secondary)

**Current:** IP + endpoint rate limiting only

**Enhancement:** Add secondary rate limit keyed by hashed email
```java
@RateLimit(keyType = CUSTOM, customKey = "email:{hash}")
```

**Benefit:** Prevents distributed botnet enumeration

---

### 2. Timing Attack Mitigation

**Current:** 500ms ± 100ms normalization

**Enhancement:** Add database query padding for not-found cases
```java
if (userOpt.isEmpty()) {
    // Simulate database lookup time for realistic timing
    Thread.sleep(random(5, 15)); // ms
}
```

---

### 3. Honeypot Field

**Enhancement:** Add hidden "phone" field to forgot-password form

**Benefit:** Catch bots that auto-fill all fields

---

## Conclusion

The password reset architecture follows **Firebase-hosted** pattern, identical to email verification. Our application orchestrates the flow but delegates UI and security-critical operations to Firebase.

**Key Principle:** Uniform responses prevent user enumeration while still providing necessary functionality.

**Status:** Implementation correct for forgot-password flow. Cleanup required to remove unnecessary reset-password component that duplicates Firebase functionality.

---

**Document Author:** System Architecture Review
**Review Date:** 2026-01-29
**Next Review:** After cleanup completion
