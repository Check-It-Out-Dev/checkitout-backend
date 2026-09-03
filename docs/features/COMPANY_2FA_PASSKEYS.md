# Company 2FA with Passkeys

**Status:** Proposed  
**Author:** Norbert Marchewka  
**Date:** January 2026  
**Priority:** High (Security Gap)

---

## 1. Current Security Gap

### Authentication Model Analysis

| User Type | Current Auth | 2FA | Risk Level |
|-----------|--------------|-----|------------|
| Influencer | Instagram OAuth + KMS | Delegated to Meta | ✓ Low |
| Admin | Email + Password + TOTP | Mandatory | ✓ Low |
| **Company** | **Email + Password** | **None** | **⚠️ HIGH** |

### Threat Vectors for Company Accounts

- Credential stuffing
- Phishing attacks
- Password reuse
- Shared credentials among employees (no accountability)

### "The King is Naked" - Pragmatic Approach

Companies share passwords anyway. We can't change that. But we can add:
- Per-person passkey (who did what)
- Phishing-resistant 2FA
- Audit trail with employee email confirmation

Low cost, no seat model complexity. We protect them in a way we can afford.

---

## 2. Proposed Solution: Passkeys with Multi-Key Support

### Model Overview

One company account (shared email + password), multiple passkeys (one per employee), ROOT delegation.

```
Company Account "BrandX" (shared: company@brandx.com)
    │
    ├── Key: jan.kowalski@brandx.com     → ROOT
    ├── Key: anna.nowak@brandx.com       → ROOT  
    ├── Key: marek.w@brandx.com          → MEMBER
    └── Key: ola.k@brandx.com            → MEMBER (Pending)
```

### Key Constraints

| Constraint | Value | Rationale |
|------------|-------|-----------|
| Max keys per account | 5 | Simplicity, covers most teams |
| Keys per email | 1 | One person = one key, no duplicates |
| Device type | Phone with biometrics only | Security + simplicity |
| First ROOT | First user at registration | Obvious, same as seat model |

### Why Passkeys Over TOTP

| Aspect | TOTP | Passkeys |
|--------|------|----------|
| Identify who logged in | Check which secret matched | Built-in - email = key |
| UX | Type 6 digits | Touch fingerprint |
| Phishing resistance | ✗ (code can be phished) | ✓ (origin-bound) |
| Shared account support | Hacky | Native |
| Audit trail | "Someone with TOTP #3" | "jan.kowalski@brandx.com, 14:32" |

### Permission Matrix

| Action | ROOT | MEMBER |
|--------|------|--------|
| Login | ✓ | ✓ |
| Remove self | ✓* | ✓ |
| Remove other MEMBER | ✓ | ✗ |
| Remove other ROOT | ✓ | ✗ |
| Grant ROOT | ✓ | ✗ |
| Revoke ROOT | ✓ | ✗ |
| Invite new user | ✓ | ✗ |
| View team members | ✓ | ✗ |

*ROOT cannot remove self if they are the last ROOT (system constraint).

---

## 3. Dead King Problem

### Definition

**Dead King Problem** - a situation in an access control system where the only entity with highest administrative privileges loses the ability to exercise them (death, incapacity, loss of access, malicious action), and the system has no built-in succession mechanism.

### Universal Occurrence

| System | "King" | Dead King Scenario |
|--------|--------|-------------------|
| Monarchy | King | King dies without heir |
| AWS | Root Account | Owner dies, no one knows password |
| Crypto wallet | Private key holder | Key lost / owner dies |
| CheckItOut | First passkey | Admin leaves with phone |
| Google Workspace | Super Admin | Super Admin unavailable |

### Solution: Built-in Succession

1. **Allow 2+ ROOTs** - redundancy
2. **Constraint: min 1 ROOT** - prevent total lockout
3. **Break glass procedure** - support ticket when all ROOTs unavailable

### Break Glass Procedure

When ALL ROOTs are unavailable:

```
1. Company submits support ticket
2. Required verification:
   - Email from company domain
   - Qualified electronic signature (e-Dowód, Certum, KIR)
   - Signer must be in KRS (company registry)
3. CheckItOut verifies:
   - Check KRS for signatory authority
   - Call phone number FROM KRS (not from ticket!)
4. Reset: Remove all keys, company re-registers first ROOT
```

**Critical:** Call number from KRS, not from ticket - social engineering protection.

---

## 4. Invite Flow

### Secure Channel: Company Email

ROOT doesn't send link manually. CheckItOut sends branded email directly.

```
ROOT types: "jan.nowy@brandx.com" → [Send Invite]
                    │
                    ▼
        CheckItOut sends email to jan.nowy@brandx.com
                    │
                    ▼
        Jan receives email, clicks link, adds key
                    │
                    ▼
        Jan receives confirmation email with key details
                    │
                    ▼
        ROOT sees pending key, approves
```

### HMAC Signed Link (Stateless)

No database table for invitations. Link contains everything:

```
https://checkitout.app/join?
  c=123                              # company_id
  &e=jan.nowy@brandx.com             # email (URL encoded)
  &t=1706450000                      # created_at (unix timestamp)
  &sig=a3f2c1d4e5b6...               # HMAC-SHA256(secret, c|e|t)
```

**Backend validation:**
```java
// Pseudo-code
String payload = companyId + "|" + email + "|" + createdAt;
String expectedSig = HMAC_SHA256(SECRET, payload);

if (!constantTimeEquals(sig, expectedSig)) {
    return INVALID_LINK;
}

if (now() - createdAt > 24_HOURS) {
    return LINK_EXPIRED;
}

if (keyExistsForEmail(companyId, email)) {
    return EMAIL_ALREADY_HAS_KEY;
}

// Valid - show consent screen
```

### Invitation Email (sent by CheckItOut)

```
From: noreply@checkitout.app
To: jan.nowy@brandx.com
Subject: Zaproszenie do konta BrandX na CheckItOut

Cześć,

jan.kowalski@brandx.com zaprasza Cię do konta firmowego 
"BrandX Sp. z o.o." na platformie CheckItOut.

Kliknij aby dołączyć:
[Dołącz do zespołu]

Link ważny 24 godziny.

---
Jeśli nie spodziewałeś się tego zaproszenia, zignoruj ten email
lub zgłoś to administratorowi swojej firmy.
```

### Consent Screen

```
┌───────────────────────────────────────────────────────────────┐
│  Dołączasz do konta firmy: "BrandX Sp. z o.o."                │
│                                                               │
│  Twój identyfikator: jan.nowy@brandx.com                      │
│                                                               │
│  ☐ Akceptuję regulamin i politykę prywatności                 │
│  ☐ Zgadzam się na logowanie moich działań przez 2 lata        │
│  ☐ Rozumiem że administrator może usunąć mój klucz            │
│                                                               │
│  [Dodaj klucz bezpieczeństwa]                                 │
└───────────────────────────────────────────────────────────────┘
```

### Confirmation Email (sent to employee after key added)

```
From: noreply@checkitout.app
To: jan.nowy@brandx.com
Subject: Klucz bezpieczeństwa dodany - CheckItOut

Twój klucz bezpieczeństwa został dodany do konta "BrandX Sp. z o.o."

Szczegóły:
- Email: jan.nowy@brandx.com
- Credential ID: a3f2c1d4e5b6a7f8...
- Urządzenie: Google Password Manager (Android)
- Data dodania: 2026-01-28 14:32:15 UTC
- Status: Oczekuje na akceptację administratora

Zachowaj ten email jako potwierdzenie.

---
Jeśli nie dodawałeś klucza, natychmiast skontaktuj się 
z administratorem swojej firmy.
```

**Purpose:** Employee has proof of what was registered. Can defend themselves if needed.

### ROOT Approval

```
┌─────────────────────────────────────────────────────────────────┐
│  👥 Team Members (4/5)                          [Invite User]   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ⏳ Pending Approval                                            │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 👤 jan.nowy@brandx.com                                    │  │
│  │ 📱 Google Password Manager (Android)                      │  │
│  │ 🕐 5 minutes ago                                          │  │
│  │                                                           │  │
│  │ [✓ Approve]  [✗ Reject]                                   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ✓ Active                                                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 👑 jan.kowalski@brandx.com          ROOT                  │  │
│  │ 👑 anna.nowak@brandx.com            ROOT                  │  │
│  │ 👤 marek.w@brandx.com               MEMBER    [Remove]    │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

ROOT verifies out-of-band (Slack/Teams/phone): "Jan, dodałeś klucz?" → "Tak" → Approve.

---

## 5. Database Schema

### Table: passkey_credentials

```sql
CREATE TABLE passkey_credentials (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    credential_id VARCHAR(255) UNIQUE NOT NULL,
    public_key BYTEA NOT NULL,
    sign_count BIGINT DEFAULT 0,
    aaguid VARCHAR(36),
    email VARCHAR(255) NOT NULL,  -- friendly_name = email
    status VARCHAR(20) DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    is_root BOOLEAN DEFAULT FALSE,
    invited_by_email VARCHAR(255),  -- who sent the invite
    approved_by_email VARCHAR(255), -- who approved (NULL if pending)
    consent_accepted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP,
    
    -- Constraints
    UNIQUE(user_id, email)  -- one key per email per company
);

CREATE INDEX idx_passkey_credentials_user_id ON passkey_credentials(user_id);
CREATE INDEX idx_passkey_credentials_credential_id ON passkey_credentials(credential_id);
CREATE INDEX idx_passkey_credentials_status ON passkey_credentials(status);
CREATE INDEX idx_passkey_credentials_email ON passkey_credentials(email);

-- Ensure max 5 keys per account
CREATE OR REPLACE FUNCTION check_max_keys()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT COUNT(*) FROM passkey_credentials 
        WHERE user_id = NEW.user_id AND status != 'REVOKED') >= 5 THEN
        RAISE EXCEPTION 'Maximum 5 keys per account';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_max_keys
    BEFORE INSERT ON passkey_credentials
    FOR EACH ROW EXECUTE FUNCTION check_max_keys();

-- Ensure at least 1 ROOT
CREATE OR REPLACE FUNCTION check_min_root()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.is_root = TRUE AND (
        SELECT COUNT(*) FROM passkey_credentials 
        WHERE user_id = OLD.user_id AND is_root = TRUE AND status = 'ACTIVE'
    ) <= 1 THEN
        RAISE EXCEPTION 'Cannot remove last ROOT';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_min_root
    BEFORE DELETE OR UPDATE ON passkey_credentials
    FOR EACH ROW EXECUTE FUNCTION check_min_root();
```

### No invitation_tokens Table

Invitations are stateless HMAC signed links. No database storage needed.

---

## 6. GDPR Compliance

### What We Log

| Field | Example | Retention |
|-------|---------|-----------|
| email | `jan.kowalski@brandx.com` | 2 years |
| credential_id | `a3f2c1d4e5b6...` | 2 years |
| aaguid | `ea9b8d66-4d01...` | 2 years |
| action | `CAMPAIGN_DELETED` | 2 years |
| timestamp | `2026-01-28T14:35:22Z` | 2 years |
| ip_hash | `sha256:...` | 2 years |

### What We Cannot Access

- Private key (never leaves device)
- Biometric data (never leaves device, GDPR Art. 9 special category)

### Legal Basis

- **Legitimate interest** (Art. 6(1)(f)) - dispute resolution, security audit
- **Explicit consent** - checkboxes during key registration
- **Data minimization** - only what's needed
- **Storage limitation** - 2 years, then auto-delete

### Employee Protection

Confirmation email to employee contains:
- email
- credential_id
- device type (from AAGUID)
- timestamp

Employee can use this as evidence in disputes ("I didn't do that, here's my key registration, different credential_id").

---

## 7. Comparison: This Model vs Seat Model

| Aspect | Seat Model | This Model |
|--------|------------|------------|
| DB Tables | 5+ | 1 (passkey_credentials) |
| Invitation storage | DB table | Stateless HMAC link |
| Identity | Separate user accounts | Email = key identifier |
| Complexity | High | Minimal |
| Implementation | 2-3 weeks | 3-5 days |
| Dead King problem | Same | Same |
| Accountability | User ID | Email + credential_id |
| UX for ROOT | "Manage organization" | "Manage team" (same feeling) |

**Verdict:** Same security guarantees, 1/5 the code.

---

## 8. API Endpoints

### Invite User (ROOT only)

```
POST /api/company/invite
Authorization: Bearer <JWT with ROOT passkey>
Content-Type: application/json

{
  "email": "jan.nowy@brandx.com"
}

Response 200:
{
  "message": "Invitation sent",
  "email": "jan.nowy@brandx.com",
  "expires_at": "2026-01-29T14:32:00Z"
}

Response 400:
{
  "error": "EMAIL_ALREADY_HAS_KEY"
}

Response 400:
{
  "error": "MAX_KEYS_REACHED"
}
```

### Validate Invite Link

```
GET /api/company/join?c=123&e=jan.nowy@brandx.com&t=1706450000&sig=...

Response 200:
{
  "valid": true,
  "company_name": "BrandX Sp. z o.o.",
  "email": "jan.nowy@brandx.com"
}

Response 400:
{
  "valid": false,
  "error": "LINK_EXPIRED" | "INVALID_SIGNATURE" | "EMAIL_ALREADY_HAS_KEY"
}
```

### Register Passkey

```
POST /api/company/join/register
Content-Type: application/json

{
  "invite_token": "c=123&e=jan.nowy@brandx.com&t=1706450000&sig=...",
  "credential": { /* WebAuthn response */ },
  "consent_accepted": true
}

Response 201:
{
  "status": "PENDING",
  "message": "Key registered. Waiting for admin approval."
}
```

### Approve/Reject Key (ROOT only)

```
POST /api/company/keys/{credential_id}/approve
Authorization: Bearer <JWT with ROOT passkey>

Response 200:
{
  "status": "ACTIVE",
  "email": "jan.nowy@brandx.com"
}
```

```
POST /api/company/keys/{credential_id}/reject
Authorization: Bearer <JWT with ROOT passkey>

Response 200:
{
  "status": "REVOKED",
  "email": "jan.nowy@brandx.com"
}
```

### List Team Members (ROOT only)

```
GET /api/company/keys
Authorization: Bearer <JWT with ROOT passkey>

Response 200:
{
  "max_keys": 5,
  "keys": [
    {
      "email": "jan.kowalski@brandx.com",
      "is_root": true,
      "status": "ACTIVE",
      "device": "Google Password Manager",
      "created_at": "2026-01-01T10:00:00Z",
      "last_used_at": "2026-01-28T14:00:00Z"
    },
    {
      "email": "jan.nowy@brandx.com",
      "is_root": false,
      "status": "PENDING",
      "device": "iCloud Keychain",
      "created_at": "2026-01-28T14:32:00Z",
      "last_used_at": null
    }
  ]
}
```

---

## 9. Implementation Phases

### Phase 1: Core Infrastructure (3 days)
- [ ] passkey_credentials table with constraints
- [ ] WebAuthn registration endpoint
- [ ] WebAuthn authentication endpoint
- [ ] First user = ROOT logic

### Phase 2: Invite Flow (2 days)
- [ ] HMAC signed link generation
- [ ] Link validation endpoint
- [ ] Invitation email template
- [ ] Consent screen UI
- [ ] Confirmation email template

### Phase 3: ROOT Management (2 days)
- [ ] Team members panel UI
- [ ] Approve/reject endpoints
- [ ] Grant/revoke ROOT endpoints
- [ ] Remove key endpoint

### Phase 4: Audit & Compliance (1 day)
- [ ] Action logging with email + credential_id
- [ ] 2-year retention policy config
- [ ] Auto-delete scheduled job

**Total: ~8 days**

---

## 10. Security Summary

| Layer | Protection |
|-------|------------|
| 1st factor | Shared password (we accept this reality) |
| 2nd factor | Individual passkey (phishing-resistant) |
| Invite channel | Company email (secure, branded) |
| Link security | HMAC signed, 24h expiry, one-time use |
| Approval | 4-eyes principle (invite + approve) |
| Audit | Email + credential_id in logs, 2 years |
| Employee protection | Confirmation email with key details |
| Dead King | 2+ ROOTs + break glass procedure |

---

*Document version: 2.0*  
*Last updated: January 2026*
