# checkItOut — backend

**Where the business rules live, and where the contract comes from.**

A marketplace connecting brands with influencers: companies publish campaigns,
creators apply, and both sides run the collaboration through content submission,
review and rating. Spring Boot 3.4 on Java 21, PostgreSQL with Liquibase, Redis,
Firebase authentication, Stripe billing and Polish e-invoicing.

[![License: MIT](https://img.shields.io/badge/License-MIT-1f6feb.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-f89820.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-6db33f.svg)](https://spring.io/projects/spring-boot)
[![Test methods](https://img.shields.io/badge/test_methods-8922-15c213.svg)](#the-numbers)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-3.1-85ea2d.svg)](docs/openapi/openapi.json)

▶ **[checkitout.app](https://checkitout.app)** — the live demo (the frontend's
FE-only build, every `/api` call mocked in the browser)

## One system, two repositories

This is half of a system, and the other half is not a client that happens to talk
to it — the two are joined at two seams, and both seams are files in this
repository.

```text
        THIS REPOSITORY                             THE FRONTEND
   ──────────────────────────                 ──────────────────────────
   the business rules                   ┌───►  the same rules, re-proven
   · 34 Cucumber feature files          │      through the screens a user
   · 148 scenarios + 28 outlines  ──────┘      really touches
   · the subscription state machine            · 26 features ported, 8 waived
                                               · a gate fails on the 9th

   the contract                         ┌───►  compiles against it
   · openapi.json, taken from a    ─────┘      · 181 model types, 41 services
     server that actually booted               · generated, never hand-written
   · 234 paths · 277 operations                · a drifted signature is a
   · 182 schemas · 41 operation tags             compile error, not a bug report
```

**The contract is not a document about the code; it is taken from the running
code.** `OpenApiSpecGeneratorTest` starts the whole application on a random port
under the `integration` profile, fetches `/api/v3/api-docs` over HTTP from the
server it just started, and writes the result to
[`docs/openapi/openapi.json`](docs/openapi/openapi.json). A spec produced that way
cannot describe an endpoint that does not exist, because an endpoint that does not
exist could not have answered.

The same test canonicalises the JSON before writing it — keys sorted, output
pretty-printed — because springdoc's runtime map ordering shuffles between boots
and was re-dirtying the committed artifact with eighty-line no-op diffs on every
run. Key order is meaningless to OpenAPI and to the code generator, and making it
deterministic is what turns the spec into something two repositories can hold
*identically* rather than merely equivalently.

Which makes the claim one command to check:

```bash
sha256sum docs/openapi/openapi.json
# 96ceb20f44831ba48ac6a01349e953c0131260ff8db84b447d704893b01e7cbb
# byte for byte the same file in both repositories
```

**The Cucumber corpus is the other seam.** The 34 feature files under
[`src/test/resources/features/`](src/test/resources/features/) are the executable
statement of what this platform promises. The frontend ports 26 of them into its
own BDD tier and waives 8 with written reasons — and it runs a gate that fails if
a 35th feature appears here without being either ported or waived there. The rules
are proven twice: once against the service layer, once through the screens.

## The numbers

Measured 2026-09-26, on this tree, with commands you can run.

| | | |
| :-- | --: | :-- |
| **Test methods** | **8,922** | 8,326 `@Test` + 596 `@ParameterizedTest`, across **273** test classes |
| **Test code : main code** | **2.0 : 1** | 182,241 lines of test Java against 90,195 of main |
| **Cucumber** | **34 files** | 148 `Scenario` + 28 `Scenario Outline` |
| **Domain** | **40 entities** | 50 REST controllers |
| **Contract** | **234 paths** | 277 operations · 182 schemas · OpenAPI 3.1 |

> [!NOTE]
> These are declarations counted in the source, not a green run — a build here needs
> Docker for Testcontainers and takes a while. The counts are reproducible with
> `grep -rh '^\s*@Test' src/test --include='*.java' | wc -l` and its siblings; the
> suites themselves are described in [`docs/Tests/`](docs/Tests/).

## Sister repositories

- **[`checkitout-frontend`](https://github.com/Check-It-Out-Dev/checkitout-frontend)** —
  Angular 22, and the other end of both seams above: it generates its client from
  this contract and ports this Cucumber corpus. Its README is where the test
  strategy is argued.
- **[`graph-theory-system-modeling`](https://github.com/Check-It-Out-Dev/graph-theory-system-modeling)** —
  the method this platform is modelled with, and the AI tooling built on it:
  knowledge-graph navigation, retrieval, prompt evaluation. The answer to how one
  person kept a system this size navigable.

## Run it without any credentials

```bash
node tools/dev-lite.mjs
```

> On Windows, run `git config --global core.longpaths true` **before cloning** —
> some test paths here exceed the classic 260-character limit.

One command brings up PostgreSQL and Redis in Docker, the backend on its
credential-less `dev-lite` profile, and the frontend if you have it checked out
next door. It narrates every step, installs nothing behind your back, and is
safe to re-run.

You get a seeded world (~26 campaigns and their applications), three demo
accounts to sign in as, a readable local mailbox, and working file uploads — no
Firebase project, no Stripe keys, no vendor sign-ups. What is real and what is
simulated, plus every flag you can flip: **[docs/DEV-LITE.md](docs/DEV-LITE.md)**.

> dev-lite exists to lower the barrier to entry, not to test the system. The
> integration and e2e suites deliberately run against **real** integrations —
> Testcontainers-backed databases, a real SMTP server, live vendors where
> credentials exist. Simulating what you are trying to verify defeats the point.

## Run it as a developer would

Prerequisites: JDK 21 (the build enforces `[21,22)`), Maven wrapper included,
Docker for the databases.

```bash
docker compose -f docker-compose-dev-redis.yml up -d postgres redis
./mvnw spring-boot:run        # profile `dev` by default
```

No `.env` file is required: every external credential falls back to a blank
placeholder, and the feature behind it degrades to a clean failure rather than
a boot failure. [`docs/DEV-LITE.md`](docs/DEV-LITE.md#flags--what-to-switch-and-where)
lists each one and what switching it on turns back to real.

The application creates its database, user and extensions on first boot
(`LocalDatabaseInitializer`), then Liquibase migrates and seeds it. It serves on
`http://localhost:8080/api`.

For HTTPS on the same port, add the `ssl` profile. The keystore is deliberately
not committed, so generate one first — this is exactly what the wizard does for
you:

```bash
keytool -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 -storepass changeit \
  -dname "CN=localhost, OU=dev, O=checkItOut, C=PL" \
  -ext "SAN=dns:localhost,ip:127.0.0.1" \
  -keystore src/main/resources/keystore.p12
```

The alias and password are not arbitrary — `application-ssl.yml` looks for
`tomcat` / `changeit` on the classpath.

The frontend dev-server proxy expects the backend on HTTPS, so the `ssl` profile
is the normal way to run the pair.

API documentation is served at `/api/swagger-ui/index.html`, and the committed
contract lives at [`docs/openapi/openapi.json`](docs/openapi/openapi.json) —
regenerate it with [`docs/openapi/REGENERATE.md`](docs/openapi/REGENERATE.md).

## Tests

Three tiers, all runnable locally:

```bash
./mvnw test                      # unit — no external services
./mvnw verify -Pintegration      # service + repository tier (Testcontainers)
./mvnw verify -Pe2e              # Cucumber end-to-end suites (needs port 8080 free)
./mvnw jacoco:report             # coverage
```

The e2e tier boots the whole application against real infrastructure and drives
it through the same HTTP surface the frontend uses; the guides in
[`docs/Tests/`](docs/Tests/) explain the suites, the actors and how to add a new
one. Tests that need live vendor credentials skip honestly when those are
absent, so a fresh clone runs the full suite green.

Why the tiers are shaped this way — real database, real SMTP, whole-application
end-to-end, and a generated client so the frontend compiles against the contract
— is argued in [docs/TESTING-PHILOSOPHY.md](docs/TESTING-PHILOSOPHY.md).

## Running it for real

[docs/ROLLOUT.md](docs/ROLLOUT.md) is the path from a credential-less clone to
something you can put in front of users: which seam to switch back to a real
vendor at each stage, what it unlocks, what breaks without it, and why the order
matters.

## Architecture

- **Feature-first packages** — `partnershipopportunities/`, `subscription/`,
  `support/`, `registry/`, `storage/`, each owning its controllers, services and
  entities.
- **Ports and adapters where the vendor is replaceable** — `InvoicingPort` →
  Fakturownia, `CompanyRegistryPort` → GUS/CEIDG/VAT. Strategic vendors
  (Stripe, Firebase) are integrated directly.
- **Optimistic locking everywhere** (`@Version` on every entity), ShedLock for
  scheduled jobs, transactional event listeners for decoupled side-effects.
- **Security**: HttpOnly HMAC-signed session cookies bound to a request
  fingerprint, step-up authentication for sensitive changes, per-endpoint rate
  limiting, consent and terms enforcement filters, HMAC consent cookies.

Deeper reading: [`docs/Architecture/`](docs/Architecture/) (security, CI/CD,
monitoring, data privacy), [`docs/features/`](docs/features/) for individual
subsystems, [`docs/StripeGateway/`](docs/StripeGateway/) for the billing
pipeline, and [`docs/security/pentest-remediation.md`](docs/security/pentest-remediation.md)
for the external penetration test and what was done about each finding.

## Deployment

Production runs on a hardened VPS provisioned by Ansible (15 roles covering base
system, Docker, PostgreSQL, nginx with mTLS, log shipping and backups) and
deployed by GitHub Actions with checksum-validated scripts, immutable containers
and automated rollback. The runbooks are in [`ansible/`](ansible/) and
[`deployment/`](deployment/); the pipeline is described in
[`docs/Architecture/02_CICD_DEPLOYMENT.md`](docs/Architecture/02_CICD_DEPLOYMENT.md).

## Configuration

Every setting is a Spring property, overridable by environment variable or
`.env` (loaded from the repository root by `spring-dotenv`). No `.env` ships
with the repository and none is needed to boot — every external credential
falls back to a blank placeholder. The full flag reference — what each one
switches on, and what happens when its credentials are missing — is in
[docs/DEV-LITE.md](docs/DEV-LITE.md#flags--what-to-switch-and-where).

## License

MIT — see [LICENSE](LICENSE). Contributions welcome; see
[CONTRIBUTING.md](CONTRIBUTING.md).
