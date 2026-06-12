# Security architecture — defence in depth

Every layer assumes the one above it has already failed. This page walks the layers
from the edge to the data, naming the real classes so you can verify every claim in
code. Companions: [step-up-authentication.md](step-up-authentication.md) ·
[consent-gdpr.md](consent-gdpr.md) · the trust-boundary map in
[domain-model.md §3](../domain-model.md#3-trust-boundaries--sensitive-assets).

## Layer 0 — Cloudflare edge

DNS-proxied edge: DDoS absorption, TLS termination to the origin over strict TLS, and
the `CF-Connecting-IP` header that the next layer depends on.

## Layer 1 — nginx perimeter

- **Six rate-limit zones keyed on the real client IP**, reconstructed from
  `CF-Connecting-IP`. Keying on `$binary_remote_addr` behind a CDN would collapse all
  users into a handful of edge-IP buckets — a daily cron refreshes Cloudflare's IP
  ranges (with timestamped backups) so the reconstruction never goes stale.
- **TLS 1.2/1.3 only**, modern ECDHE/CHACHA20 ciphers, session tickets off.
- **Security headers** as a modular vhost include; the observability vhost goes
  further and requires **mutual TLS** (client certificates) to reach the log store.

## Layer 2 — the Spring Security filter chain

One `SecurityFilterChain` (`WebSecurityConfiguration`) stacks four custom filters in a
deliberate order, each an independently insertable enforcement concern:

```
JwtAuthenticationFilter → BannedUserFilter → ConsentEnforcementFilter → EmailVerificationEnforcementFilter
```

- `JwtAuthenticationFilter` validates a backend JWT session cookie **plus a separate
  HMAC-SHA256 signature cookie in constant time** (`HmacUtils` →
  `MessageDigest.isEqual`) — ~1.2 ms, zero external calls.
- Sessions are **HttpOnly cookies only**. No `Authorization: Bearer`, no localStorage
  tokens — the backend rejects them by design.
- `SessionSecurityService` re-derives a session fingerprint from live IP + User-Agent
  and runs **impossible-travel detection** (country jumps, a 500 km/h speed ceiling,
  GeoIP behind a circuit breaker — `GeoLocationFacade`, `TravelPatternService`).
- Actuator beyond `/health` is locked to ADMIN; public endpoints live on an explicit
  allowlist.

## Layer 3 — identity & sessions

- **One minting point**: `TokenExchangeService` verifies the Firebase ID token,
  re-validates the role through the Firebase Admin SDK (never trusting embedded
  claims), and issues role-aware TTLs — 7 days for users, 2 hours for admins,
  10-minute partial sessions carrying `PARTIAL_AUTH`/`PENDING_2FA` authorities until
  TOTP clears.
- **Instant fleet-wide revocation**: a `tokenVersion` claim is checked against
  `RedisUserCache` on every request; admin actions bump the *target's* version. A
  mismatch returns **HTTP 419** (silent refresh) instead of 401 — with a
  `/refresh-session` exemption that prevents an infinite-419 loop.
- **All credential traffic to Google flows through a server-side proxy**
  (`FirebaseAuthProxyService`): centralized audit logging, rate limiting, and
  reCAPTCHA verification on the auth surface.
- **Anti-enumeration by construction**: `PasswordResetService` returns identical
  generic responses with response-timing normalization performed *outside* the
  database transaction.

## Layer 4 — step-up & admin 2FA

Sensitive operations re-authenticate regardless of session state (full ladder in
[step-up-authentication.md](step-up-authentication.md)):

- Users: SHA-256-hashed 6-digit email codes, 5 attempts/code, 15-minute cooldown,
  24-hour lockout after 3 burned cycles; the resulting one-time token is consumed via
  atomic Redis `getAndDelete` — double-spend is structurally impossible.
- Admins: TOTP. Secrets are **KMS-encrypted in Firestore** (`TotpFirestoreService`,
  `TotpEncryptionService`) — never in PostgreSQL — with BCrypt-hashed backup codes and
  a per-user audit subcollection. Enrollment checks **fail closed**: an infrastructure
  error can never demote an enrolled admin back to setup mode.

## Layer 5 — application-level protections

- **Rate limiting** is a custom Redis ZSET sliding-window (Lua), wrapped twice:
  a Resilience4j circuit breaker with in-memory fallback below, and a **GDPR
  anonymization/retention layer above** (`GdprCompliantRateLimiterService`). Handlers
  opt in via `@RateLimit` annotations.
- **Errors** flow through a `TranslatableException` hierarchy carrying i18n message
  keys plus trace IDs — the frontend localizes, the logs stay correlatable, and no
  stack trace ever reaches a client.
- **Request logging** is correlation-ID'd and PII-masked at the filter level.
- **Optimistic locking everywhere**: every entity carries `@Version` (retrofitted as
  an explicit OWASP-driven migration wave).

## Layer 6 — data & secrets

- Secrets follow a Kubernetes-style pattern: a **Google Secret Manager init container
  writes files to a mounted volume** (`ProductionSecretService`); dev falls back to
  local properties. Nothing is hard-coded; `.env.example` documents every key.
- **Least-privilege database access by construction**: Liquibase gets the DDL-capable
  user, the runtime Hikari pool gets a restricted one
  (`DualDataSourceConfiguration`).
- Dedicated, independently rotatable HMAC secrets: session cookies, consent cookies,
  storage webhooks.
- GDPR erasure is a four-system pipeline (archive-to-GCS first, then PostgreSQL →
  Firestore → Storage → Firebase Auth) with a retry ledger — see
  [consent-gdpr.md](consent-gdpr.md).

## Layer 7 — host & deployment

The host is Ansible-provisioned and hardened (a ~30-key sysctl set, an 849-line
no-wildcard sudoers allowlist, fail2ban, chattr-frozen configs). Operator SSH is
hardware-backed — an ED25519-SK (FIDO2/YubiKey) key requiring PIN **and** physical
touch sits in `authorized_keys`; the provider's out-of-band VNC/KVM console is the
break-glass path — [operations/deployment.md](../operations/deployment.md) — and
deployment scripts run under a kernel-immutability chain with timestamp-guarded
auto-rollback — [operations/immutability.md](../operations/immutability.md).

## Independent validation

The application and infrastructure are undergoing independent penetration tests by the
Wrocław Centre for Networking and Supercomputing (WCSS) at the Wrocław University of
Science and Technology, within the WRO4digITal programme (EDIH Wrocław). The
[domain model](../domain-model.md) doubles as the testers' briefing document — including
its honest *intended vs enforced* notes, which we chose to publish rather than polish away.
