# Support Ticket Magic-Link Access

> Signed, expiring one-click access to support tickets for anonymous reporters.
> Landed 2026-06-13 on the `greenfield` branch as the follow-up to pentest
> findings 3.2/3.3 (predictable ticket reference + unauthenticated reply surface).

---

## Problem

Anonymous users create support tickets with nothing but an email address. To
check status they had exactly one credential: the human-readable ticket
reference (`CIO-<yyyyMMdd>-XXXXXXXX`) plus their email, typed into a lookup
form. Two weaknesses:

1. **UX** — the reporter has to find the reference in their inbox, then
   re-type reference + email into the status form. High friction for the
   exact population (non-customers) least motivated to fight a form.
2. **Security** — anything that makes the reference itself the access
   credential invites enumeration and leakage (references appear in email
   subjects, screenshots, forwarded threads). The pentest (3.3) demonstrated
   brute-forcing the old 4-digit suffix.

The remediation hardened the reference (SecureRandom, 8-char Crockford
suffix, DB-unique — see `TicketReferenceService`) and rate-limited the
anonymous surfaces. The magic link then goes one step further: the reference
stops being a credential at all.

## Design

Every email the platform sends about a ticket carries a **magic link**:

```
{frontendUrl}/support/tickets/status?ref=CIO-20260902-DCRCS21P&token=<accessToken>
```

The token is minted by
`support/ticket/services/TicketAccessTokenService.java`:

| Property | Value |
|---|---|
| Format | `base64url( ticketId + ":" + exp + ":" + HMAC-SHA256(ticketId:exp, key) )`, no padding |
| Key | `cookie.hmac.secret` + `"-ticket-access"` — **domain-separated**, never the raw cookie key |
| TTL | 14 days (`exp` = epoch seconds at mint + 14d) |
| State | Stateless — verification recomputes the HMAC; no DB row, no revocation table |
| Reuse | Reusable until expiry (bookmark / multi-device friendly) |

Security properties, in order of importance:

- **Unforgeable.** Knowing a ticket id or reference is not enough; the HMAC
  binds access to possession of the emailed link. Enumeration is impossible.
- **Expiring.** A leaked link (forwarded email, screenshot) is bounded to
  14 days, unlike a reference which is valid for the ticket's lifetime.
- **Domain-separated key.** The signing key is derived from the cookie HMAC
  secret with a `-ticket-access` suffix, so a ticket token can never be
  replayed as a consent cookie or vice versa.
- **GDPR-logged.** Every token access logs
  `GDPR: Operation=getTicketByAccessToken, Purpose=support_access`.

## Flow

```
createTicket / addAdminResponse (SupportTicketService)
        │  statusToken = accessTokenService.mint(ticket.getId())
        ▼
EmailService.buildTicketStatusLink(ticketReference, statusToken)
        │  → {frontendUrl}/support/tickets/status?ref=…&token=…
        │    (null-tolerant: no token → plain ?ref= link, old behavior)
        ▼
Reporter clicks the link
        ▼
FE ticket-status page (greenfield: feature/support/ticket-status)
        │  sees ?token= → loadByToken(): GET /api/support/ticket/access?token=…
        ▼
SupportTicketController.getTicketByAccessToken  (GET /support/ticket/access)
        │  TicketAccessTokenService.verify(token) → Optional<ticketId>
        │  valid   → 200 SupportTicketDtoOut (ticket auto-opens, no form)
        │  invalid → 401 {"messageKey":"error.auth.invalid_token"}
        ▼
FE fallback: on 401/404 the page drops to the classic ref+email lookup form
```

Minting call sites (both in `SupportTicketService`):

1. **`createTicket`** — the confirmation email's link opens the fresh ticket.
2. **`addAdminResponse`** (when `sendEmail=true`) — the "new response" email
   carries a **fresh** token, so long-lived tickets keep working even after
   the original 14-day token expires.

## The dual-whitelist gotcha (read this before adding any public endpoint)

Making `GET /support/ticket/access` public required **two** changes, not one:

| Layer | File | What |
|---|---|---|
| Spring Security | `common/authorization/WebSecurityConfiguration.java` | `permitAll()` matcher for the path |
| JWT filter | `common/authorization/JwtAuthenticationFilter.java` → `isPublicEndpoint` | a **duplicate, hand-maintained** public-path list |

The filter runs before the authorization rules and rejects requests without
a session **even for permitAll paths** unless the path is also in its own
list. Symptom of forgetting the second half: a valid magic-link token gets
`401 Unauthorized` despite the endpoint being "public" (this exact bug
shipped and was fixed in commit `5a0b4eaf`). The filter's `/support/` block
matches both `/support/ticket/access` and `/api/support/ticket/access`
shapes and gates on `GET` only.

## Rate limiting

The anonymous support surfaces share the tightened per-IP bucket introduced
by the pentest remediation (finding 3.2):

- `GET  /support/ticket/status` (ref+email lookup)
- `GET  /support/ticket/access` (magic-link)
- `POST /support/ticket/response` (anonymous reply)

Token verification is cheap (one HMAC), so the rate limit exists to blunt
harvesting attempts, not to protect CPU.

## FE consumption (greenfield)

| Piece | File | Behavior |
|---|---|---|
| Wrapper | `src/app/core/support/support-ticket.service.ts` | `getTicketByAccessToken(token)` over the generated `SupportTicketControllerService` |
| Page | `src/app/feature/support/ticket-status/ticket-status.component.ts` | `?token=` present → `loadByToken()` auto-opens the ticket; on error falls back to the lookup form with an i18n error |
| Interceptor | `src/app/core/interceptors/error.interceptor.ts` | `/support/ticket/access` is in `SKIP_REFRESH_PATHS` — a 401 here is "bad ticket token", **not** "stale session", and must never trigger the silent-refresh loop |

## Testing

| Tier | What |
|---|---|
| Unit | `support/ticket/services/TicketAccessTokenServiceUnitTest.java` — mint/verify round-trip, tamper, expiry, malformed input |
| Live-stack manual E2E | `docs/Tests/E2E-TEST-SUITES-GUIDE.md` § "Live-Stack Manual E2E (Chrome + GreenMail)" — full create→email→click→reply matrix against the real stack |

## Deliberate non-features

- **No revocation.** Stateless tokens can't be individually revoked; rotating
  `cookie.hmac.secret` kills all outstanding links at once. Accepted: the
  blast radius of one leaked link is one ticket for ≤14 days.
- **No single-use.** Single-use tokens break "open on phone, reply on
  laptop" and re-reads of the same email. Reuse-until-expiry is the point.
- **Reference+email lookup stays.** The magic link is additive; the classic
  form remains both the fallback (expired token) and the no-email-access path.
