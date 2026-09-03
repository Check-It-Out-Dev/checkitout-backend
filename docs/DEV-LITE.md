# Running checkItOut without any credentials — dev-lite

checkItOut talks to Firebase, Google Cloud Storage, Stripe, Fakturownia, the
Polish company registries (GUS/CEIDG/VAT), MaxMind and Instagram. None of those
accounts should be needed to look at the product, read the code with a running
system in front of you, or hack on a feature.

`dev-lite` is a Spring profile that boots the real application with those edges
simulated locally. One command brings up the whole thing:

```bash
node tools/dev-lite.mjs
```

The wizard checks what you have, starts what is missing, and tells you where to
click. It is safe to re-run; nothing it does is destructive.

> **This is not how the project is tested.** The integration and e2e suites run
> against **real** integrations — Testcontainers-backed PostgreSQL and Redis,
> a real SMTP server, real vendors wherever credentials exist. Test what is
> real; simulate only to lower the barrier to entry. See
> `docs/Tests/` for the suites that do the actual verifying.

---

## What the wizard does

| Step | What happens | If it fails |
| --- | --- | --- |
| 1. Tools | Finds git, Docker, a JDK 21 (`BE_JAVA_HOME` overrides the search) and Node for the UI | It tells you exactly which one is missing and what to install |
| 2. Ports | Checks 5432 / 6379 / 8080 / 3025 / 4201; offers spare ports when something already holds them (`--yes` takes the offer rather than assume a stranger's PostgreSQL is yours) | Answer `y` to the remap offer, or stop the other process |
| 3. Databases | `docker compose up -d postgres redis` — only those two services | Start Docker Desktop / `systemctl start docker` and re-run |
| 4. Backend | `mvnw spring-boot:run` with `dev-lite,ssl`; first run downloads Maven dependencies | Read `.dev-lite/backend.log`; the wizard prints its tail |
| 5. Demo users | Mints local sessions for the seeded accounts and verifies the seeded campaigns are visible | Means the profile is not really `dev-lite` — check the log |
| 6. Frontend | `npm ci` if needed, then the Angular dev-server — which generates its own self-signed certificate on first start | Backend stays usable; the UI can be started by hand later |

Useful switches: `--yes` (no prompts), `--no-fe` (backend only), `--status`,
`--stop`. Ports can be pinned with `DEV_LITE_PG_PORT`, `DEV_LITE_REDIS_PORT`,
`DEV_LITE_BE_PORT`, `DEV_LITE_SMTP_PORT`, `DEV_LITE_FE_PORT`, and a second
isolated stack gets its own `DEV_LITE_COMPOSE_PROJECT`.

`--stop` only stops what this wizard started. It reads back the ports the run
actually used (a remapped stack stops correctly), and before reclaiming port
8080 it checks that the backend answering there is a dev-lite one. Anything else
on those ports is reported and left running.

---

## Signing in

There are no passwords in dev-lite: the normal sign-in form talks to Firebase
Identity Toolkit, which needs a real project. Instead the `dev-lite` profile
exposes the same test-session endpoint the e2e suites use. It mints the
production-shape, HMAC-signed session cookies locally — the rest of the
application (guards, roles, filters, `/users/me`) behaves exactly as in
production.

From the browser console **on the app's own origin** (so the cookie sticks):

```js
await fetch('/api/test/auth/mock-session', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  credentials: 'include',
  body: JSON.stringify({ email: 'company@checkitout.app', role: 'COMPANY' }),
});
location.reload();
```

Accounts that already own seeded data:

| Email | Role | What they see |
| --- | --- | --- |
| `company@checkitout.app` | COMPANY | ~26 seeded campaigns, applicants, plan & billing |
| `test.influencer@test.com` | INFLUENCER | Campaign browsing, applications, content submission |
| `test.admin@test.com` | ADMIN | User management, dictionary editor, support queue |

Any other address works too — it creates a fresh, empty account.

To apply to a campaign, the account needs a connected Instagram profile.
Instagram OAuth is genuinely external, so dev-lite seeds the connection row
directly:

```bash
curl -k -X POST https://localhost:8080/api/test/auth/seed-instagram-connection \
     -H 'Content-Type: application/json' \
     -d '{"email":"test.influencer@test.com"}'
```

---

## E-mail

Every message the application sends — verification links, step-up codes,
password prompts, ticket notifications — is delivered to an in-memory SMTP
server (GreenMail) and readable over HTTP:

```
GET  https://localhost:8080/api/test/email          all messages
GET  https://localhost:8080/api/test/email/latest   the newest one
POST https://localhost:8080/api/test/email/flush    empty the inbox
```

That covers the flows worth exercising locally. The two exceptions are password
reset and the support magic-link, which mint their codes through the Firebase
Admin SDK.

Try it — open a support ticket and read the confirmation it sends:

```bash
curl -k -X POST https://localhost:8080/api/support/ticket \
     -H 'Content-Type: application/json' \
     -d '{"contactEmail":"visitor@example.com","subject":"Hello",
          "description":"Testing the local mailbox.","category":"TECHNICAL_PROBLEM"}'
curl -k https://localhost:8080/api/test/email/latest
# subject: "Otrzymaliśmy Twoje zgłoszenie [CIO-…]"
```

Step-up codes reach the same mailbox, but not for any of the seeded accounts,
so they make a poor first test: `test.admin@test.com` is an administrator and
administrators are always challenged through an authenticator app, while the
company and influencer accounts have not completed initial account setup, and
step-up is not required until they do. Complete a profile first, or use the
ticket above.

---

## Files and images

Uploads normally go straight from the browser to Google Cloud Storage using a
signed URL. In dev-lite the **byte transport** is swapped for a local sink:
the backend mints a single-use, expiring token, the browser PUTs to
`/api/dev-lite/upload/{token}`, and the file is served back from
`/api/dev-lite/files/...`. Everything around it — validation, rate limits,
storage quota, the `file_uploads` tracking row, and the rule that a client may
never supply a file URL (only an `uploadId`) — is the production code path.

The token behaves like the signature it stands in for: single-use, expiring, and
bound to the content type the upload was prepared with, so a PUT that lies about
its type is rejected here exactly as Google Cloud Storage would reject it. The
sink additionally caps a body at 5 MB — stricter than GCS on purpose, because a
lying client should not be able to fill your disk.

Files land in `dev-lite-storage/` inside the backend repository (change it with
`file-upload.local-sink.dir`). Delete the directory to reset.

The seeded demo campaigns reference stock photos on the internet. A dev-lite
Liquibase changeset repoints them at `/api/dev-lite/placeholder/{seed}`, which
renders a deterministic SVG, so the seeded world looks complete with no network
access at all.

---

## What is real, what is not

| Area | dev-lite |
| --- | --- |
| Campaigns, applications, content submission, ratings | **Real** — the actual services against real PostgreSQL |
| Sessions, guards, roles, rate limiting, consent + terms enforcement | **Real** |
| Support tickets, notifications, admin dictionary | **Real** |
| E-mail | **Real SMTP**, delivered to a local in-memory inbox |
| File uploads and images | **Simulated transport** — local disk instead of the bucket |
| Company registry lookup (NIP → GUS/CEIDG/VAT) | **Simulated** — stub ports. An unconfigured NIP simply reports "not found"; teach it one first: `POST /api/test/registry/configure-krs-company` with `{"nip":"1234567890","name":"Example Sp. z o.o."}` |
| Sign-in with a password | **Unavailable** — needs a real Firebase project; use the test-session endpoint |
| Payments, subscriptions checkout, invoicing | **Off by feature flag** — the UI shows its payments-disabled state |
| Instagram OAuth, KMS-backed token storage, GeoIP | **Unavailable / degraded** — documented, non-fatal |

---

## Flags — what to switch, and where

Everything below is a normal Spring property: set it in `.env` (loaded
automatically from the repository root by `spring-dotenv`), as an environment
variable, or with `-Dproperty=value`. The defaults column is what a fresh clone
gets — there is no `.env` file in the repository and none is needed to boot;
create one only when you want to switch something back to real.

| Property | Default | What it controls |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev-lite,ssl` is what the wizard uses. `dev-lite` implies `dev`. |
| `app.payments.enabled` | `false` | Stripe wiring. `true` needs real Stripe keys; the boot guard refuses to start if subscriptions already exist in a paid state. |
| `fakturownia.enabled` + `FAKTUROWNIA_API_KEY` | off / blank | Invoicing. Blank key = every invoice call returns a clean failure. |
| `admin.check.enabled` | `true` (`false` in dev-lite) | Boot-time admin account check against Firebase. |
| `recaptcha.enabled` | `false` in dev/e2e | reCAPTCHA Enterprise verification on public forms. |
| `gcp.storage.enabled` | `true` | Google Cloud Storage client. dev-lite keeps it on but routes uploads to the local sink. |
| `gcp.kms.enabled` | `true` | KMS encryption for Instagram tokens and TOTP secrets. Without credentials those specific paths fail on use. |
| `instagram.validation.enabled` | `true` | Startup validation of the Instagram app credentials — logs, never fatal. |
| `app.email.enabled` | `true` | Application e-mail. In dev/dev-lite it targets GreenMail. |
| `notification.email.enabled` | `true` | Notification e-mails specifically. |
| `local.db.init.enabled` | `true` in dev | Creates the database, application user and extensions using the compose superuser. Needs a superuser connection. |
| `file-upload.local-sink.dir` | `./dev-lite-storage` | Where dev-lite stores uploaded files. |
| `MAXMIND_LICENSE_KEY` | blank | GeoIP database. Blank = every lookup returns "unknown" (by design). |
| `STRIPE_PRIVATE_KEY` / `STRIPE_PUBLIC_KEY` / `STRIPE_WEBHOOK_SECRET` | blank | Only needed with payments enabled. |
| `ADMIN_EMAIL` | `admin@synthetic.test` | Address the platform treats as the administrator contact. |

Profiles worth knowing: `dev` (local development), `dev-lite` (this simulator),
`ssl` (HTTPS — the frontend proxy expects it; the keystore is not committed, so
the wizard generates one into `.dev-lite/`, and the README shows the `keytool`
line if you would rather do it by hand),
`e2e` (the Cucumber/Playwright suites, Testcontainers-backed), `test` and
`integration` (the unit and service tiers), `no-redis` (in-memory cache instead
of Redis), `prod` / `prod-standalone` (real credentials mandatory).

---

## Troubleshooting

**Windows: the clone itself fails with "Filename too long".** Some test paths
here are 150 characters, and Windows caps a path at 260 unless long-path
support is on. Enable it once, then clone again:

```powershell
git config --global core.longpaths true
```

**Port 5432 is taken.** A system PostgreSQL is the usual culprit. Accept the
wizard's offer to move to spare ports, or stop the other service.

**Docker is installed but nothing starts.** The daemon has to be running:
Docker Desktop on Windows/macOS, `sudo systemctl start docker` on Linux. Under
WSL, either Docker Desktop's WSL integration or a docker engine inside the
distribution works.

**The backend never becomes healthy.** Read `.dev-lite/backend.log`. The usual
causes are a JDK that is not 21 (the build enforces `[21,22)`), or the database
container not being reachable on the expected port.

**The browser refuses the certificate.** Both certificates are self-signed and
generated on this machine — the wizard makes the backend keystore in
`.dev-lite/`, and the frontend makes its own pair on first start. Neither is
committed anywhere. Accept the warning once for `https://localhost:8080` and
once for `https://localhost:4201`.

**Everything looks empty after signing in.** You are probably signed in as an
account that has no data. Use one of the three seeded addresses above.

**`--stop` says the port is held by something that is not a dev-lite backend.**
That check is what stops the wizard from killing a stranger's server, and it
answers honestly in one awkward case: a backend that wedged badly enough to stop
serving while still holding the port. Confirm it is yours, then end it by hand —
`netstat -ano | findstr :8080` on Windows, `lsof -ti tcp:8080` elsewhere.

**Reset everything.**

```bash
node tools/dev-lite.mjs --stop
docker compose -f docker-compose-dev-redis.yml down -v   # wipes the database
rm -rf dev-lite-storage .dev-lite
```
