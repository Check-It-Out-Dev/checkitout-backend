# checkItOut — backend

**Where the business rules live, and where the contract comes from.**

A marketplace connecting brands with influencers: companies publish campaigns,
creators apply, and both sides run the collaboration through content submission,
review and rating. Spring Boot 3.4 on Java 21, PostgreSQL with Liquibase, Redis,
Firebase authentication, Stripe billing and Polish e-invoicing. It ran in
production with real users; the frontend's mocked build is the open demo today.

[![License: MIT](https://img.shields.io/badge/License-MIT-1f6feb.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-f89820.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-6db33f.svg)](https://spring.io/projects/spring-boot)
[![Test methods](https://img.shields.io/badge/test_methods-8908-15c213.svg)](#testing)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-3.1-85ea2d.svg)](docs/openapi/openapi.json)

▶ **[checkitout.app](https://checkitout.app)** — the live demo (the frontend's
FE-only build, every `/api` call mocked in the browser) ·
**[the engineering page](https://checkitout.app/technical-survey/engineering)** —
the estate in one screen

## Run it

**Without any credentials — one command.**

```bash
node tools/dev-lite.mjs
```

> On Windows, run `git config --global core.longpaths true` **before cloning** —
> some test paths here exceed the classic 260-character limit.

It brings up PostgreSQL and Redis in Docker, the backend on its credential-less
`dev-lite` profile, and the frontend if you have it checked out next door. It
narrates every step, installs nothing behind your back, and is safe to re-run.
You get a seeded world (~26 campaigns and their applications), three demo
accounts, a readable local mailbox and working file uploads — no Firebase
project, no Stripe keys, no vendor sign-ups. What is real and what is simulated,
plus every flag you can flip: **[docs/DEV-LITE.md](docs/DEV-LITE.md)**.

**As a developer would.** JDK 21 (the build enforces `[21,22)`), the Maven
wrapper, Docker for the databases:

```bash
docker compose -f docker-compose-dev-redis.yml up -d postgres redis
./mvnw spring-boot:run        # profile `dev`; add `ssl` for HTTPS on the same port
```

No `.env` is required: every external credential falls back to a blank
placeholder and the feature behind it degrades to a clean failure rather than a
boot failure. The application creates its database, user and extensions on first
boot, then Liquibase migrates and seeds it; it serves on `http://localhost:8080/api`
with Swagger at `/api/swagger-ui/index.html`. The frontend's dev proxy expects
HTTPS, so the `ssl` profile is the normal way to run the pair — the keystore is
not committed; the command that generates it is in
[docs/DEV-LITE.md](docs/DEV-LITE.md#flags--what-to-switch-and-where).

**For real.** [docs/ROLLOUT.md](docs/ROLLOUT.md) is the path from a
credential-less clone to something in front of users: which seam to switch back
to a real vendor at each stage, what it unlocks, what breaks without it.

## Testing

Three tiers, all runnable locally, shaped by one rule — **simulate what you are
not testing, never what you are**:

```bash
./mvnw test -Ptest               # unit — no external services, about a minute
./mvnw verify -Pintegration      # service + repository tier on Testcontainers (real PostgreSQL, real SMTP)
./mvnw verify -Pe2e              # Cucumber end-to-end suites — the whole application booted, driven over HTTP
./mvnw jacoco:report             # coverage
```

The unit tier mocks freely and runs in seconds. The integration tier runs the
real database, real migrations and a real mail server, because "PostgreSQL
applies this constraint" is a guess until it is not. The e2e tier boots the whole
application against real infrastructure and drives it through the same HTTP
surface the frontend uses, with real authentication and multiple actors. Tests
that need live vendor credentials skip honestly when those are absent, so a
fresh clone runs the full suite green.

Every build also passes six gates: Enforcer (Java 21), Spotless, PMD, SpotBugs +
FindSecBugs at maximum effort, OWASP dependency-check (fails at CVSS ≥ 7) and
JaCoCo. If a gate blocks a change, the cause is fixed — never the gate.

**The numbers**, measured 2026-09-08 on this tree, with commands you can run:

| | | |
| :-- | --: | :-- |
| **Test methods** | **8,908** | 8,312 `@Test` + 596 `@ParameterizedTest`, across **256** test classes and the **2,152** `@Nested` groups inside them |
| **Test code : main code** | **2.0 : 1** | 182,241 lines of test Java against 90,195 of main |
| **Cucumber** | **34 files** | 148 `Scenario` + 28 `Scenario Outline`, **277 after Examples expansion** |
| **Domain** | **38 entities** | 50 REST controllers |
| **Contract** | **234 paths** | 279 operations · 182 schemas · OpenAPI 3.1 |

And one number worth more than any of them: **`@Disabled` appears zero times**
across all 325 test files. Nothing is quarantined, skipped-and-forgotten, or
commented out waiting for someone to come back to it.

> [!NOTE]
> These are declarations counted in the source, not a green run — a build here
> needs Docker for Testcontainers and takes a while. Reproduce them with
> `grep -rhE '^\s*@Test\b' src/test --include='*.java' | wc -l` and its siblings
> (`@ParameterizedTest`, `@Nested`, `@Disabled`, `^\s*Scenario:`, `@Entity`,
> `@RestController`). The suites, the actors and how to add one are in
> [`docs/Tests/`](docs/Tests/); the argument for the shape is
> [docs/TESTING-PHILOSOPHY.md](docs/TESTING-PHILOSOPHY.md). Older snapshots in
> those documents carry their own dates; this table is the current one.

## The contract, and the corpus

This is half of a system, and the other half is not a client that happens to
talk to it — the two are joined at two seams, and both seams are files here.

```text
        THIS REPOSITORY                             THE FRONTEND
   ──────────────────────────                 ──────────────────────────
   the business rules                   ┌───►  the same rules, re-proven
   · 34 Cucumber feature files          │      through the screens a user
   · 277 scenarios after expansion ─────┘      really touches
   · the subscription state machine            · 26 features ported, 8 waived
                                               · a gate fails on the 35th

   the contract                         ┌───►  compiles against it
   · openapi.json, taken from a    ─────┘      · 181 model types, 41 services
     server that actually booted               · generated, never hand-written
   · 234 paths · 279 operations                · a drifted signature is a
   · 182 schemas                                 compile error, not a bug report
```

**The contract is taken from the running code, not written about it.**
`OpenApiSpecGeneratorTest` starts the whole application on a random port under
the `integration` profile, fetches `/api/v3/api-docs` from the server it just
started, canonicalises the JSON (keys sorted, pretty-printed — springdoc's map
ordering shuffles between boots) and writes
[`docs/openapi/openapi.json`](docs/openapi/openapi.json). A spec produced that
way cannot describe an endpoint that does not exist, and the determinism is what
lets two repositories hold it *identically*:

```bash
sha256sum docs/openapi/openapi.json
# 96ceb20f44831ba48ac6a01349e953c0131260ff8db84b447d704893b01e7cbb
# byte for byte the same file in both repositories
```

**The Cucumber corpus is the other seam.** The 34 feature files under
[`src/test/resources/features/`](src/test/resources/features/) are the executable
statement of what the platform promises. The frontend ports 26 into its own BDD
tier and waives 8 with written reasons, and runs a gate that fails if a 35th
feature appears here without being either ported or waived there. The rules are
proven twice: once against the service layer, once through the screens.
Regenerating the spec: [`docs/openapi/REGENERATE.md`](docs/openapi/REGENERATE.md).

## Architecture

- **Feature-first packages** — `partnershipopportunities/`, `subscription/`,
  `support/`, `registry/`, `storage/`, each owning its controllers, services and
  entities.
- **Ports and adapters where the vendor is replaceable** — `InvoicingPort` →
  Fakturownia, `CompanyRegistryPort` → GUS/CEIDG/VAT. Strategic vendors (Stripe,
  Firebase) are integrated directly.
- **Optimistic locking where writes contend** — `@Version` on 7 of the 38
  entities, the ones two actors can touch at once. ShedLock for scheduled jobs,
  transactional event listeners for decoupled side-effects.
- **The billing saga** — Stripe → Fakturownia → KSeF as an explicit state
  machine: 10 subscription states, 56 transitions, the business rules and the
  resolved corner cases stored as data, not prose ([docs/StripeGateway/](docs/StripeGateway/)).
- **Security** — HttpOnly HMAC-signed session cookies bound to a request
  fingerprint, step-up authentication for sensitive changes, per-endpoint rate
  limiting, consent and terms enforcement filters, HMAC consent cookies. An
  external penetration test and what was done about each finding:
  [`docs/security/pentest-remediation.md`](docs/security/pentest-remediation.md).

Deeper reading, one document each: [security](docs/Architecture/01_SECURITY_ARCHITECTURE.md),
[CI/CD and deployment](docs/Architecture/02_CICD_DEPLOYMENT.md),
[monitoring](docs/Architecture/03_MONITORING_OBSERVABILITY.md),
[data privacy and RODO](docs/Architecture/04_DATA_PRIVACY_COMPLIANCE.md),
[the notification system](docs/NotificationSystem/), and
[docs/features/](docs/features/) for individual subsystems. The system is also
maintained as a knowledge graph — the method is the
[graph-theory repository](https://github.com/Check-It-Out-Dev/graph-theory-system-modeling),
the graph itself is drawn from its data on
[the engineering page](https://checkitout.app/technical-survey/engineering#graph-topology).

## CI/CD

What exists in [`.github/workflows/`](.github/workflows/) is a **release
pipeline**, built as reusable workflows and used through the production period:

```text
push main / prod
  └─► config-loader ─► config-validator (strict) ─► maven + Docker image → GHCR
        ─► pre-deployment backup ─► deploy-and-secure ─► reload-and-validate ─► summary
```

Every uploaded script is sha256-gated and made immutable on the host before it
runs; images are tagged by version and commit; the target is a hardened VPS
provisioned by Ansible (15 roles — base system, Docker, PostgreSQL, nginx with
mTLS, log shipping, backups; runbooks in [`ansible/`](ansible/) and
[`deployment/`](deployment/)). A reusable rollback workflow
([`auto-rollback-systemd.yml`](.github/workflows/auto-rollback-systemd.yml):
restore the backup, restart, nine health attempts) exists and its wiring into
the chain is in progress. Hosting is Docker Compose and systemd on that one VPS —
right-sized for this product, and the reason the documents say no to Kubernetes
*for hosting*.

There is **no test pipeline in GitHub Actions today**: the 8,908 test methods run
locally through `./mvnw` and the six build gates, and that is the gap the next
section closes first.

## In progress

Dated 2026-09. 🟡 under way · ⬜ designed, not started.

|     | What | Detail |
| :-- | :-- | :-- |
| 🟡 | **A test pipeline** | The three tiers in CI, sharded: JUnit 5 parallel for the unit tier, the integration tier on Testcontainers, the Cucumber suites split by feature across ephemeral runners (Testkube Test Workflows on GitHub ARC) — Kubernetes as the place tests run, not as hosting |
| 🟡 | **Rollback wired into the release chain** | The reusable rollback exists; it becomes the failure path of `reload-and-validate` |
| ⬜ | **Report aggregation** | JUnit XML → GitHub checks on the pull request; Allure Report first, ReportPortal when run history and a flaky quarantine matter |
| ⬜ | **Performance in CI** | k6 thresholds on the API's public surface, run through k6-operator as a `TestRun` once the test pipeline is on Kubernetes; the frontend already gates the demo's routes the same way |
| ⬜ | **Schemathesis against the running server** | The one thing the contract chain does not prove: that the server matches its own published spec |

## The rest of the estate

- **[`checkitout-frontend`](https://github.com/Check-It-Out-Dev/checkitout-frontend)** —
  Angular 22, and the other end of both seams: it generates its client from this
  contract and ports this Cucumber corpus. Its README is where the test strategy
  is argued and where every published number is gated.
- **[`graph-theory-system-modeling`](https://github.com/Check-It-Out-Dev/graph-theory-system-modeling)** —
  the method this platform is modelled with, and the AI tooling built on it:
  knowledge-graph navigation, retrieval, prompt contracts, evaluation. The answer
  to how one person kept a system this size navigable.
- **[checkitout.app/technical-survey/engineering](https://checkitout.app/technical-survey/engineering)** —
  the estate in one screen, with the pipelines and what is under way.

## Configuration

Every setting is a Spring property, overridable by environment variable or
`.env` (loaded from the repository root by `spring-dotenv`). No `.env` ships and
none is needed to boot. The full flag reference — what each one switches on and
what happens when its credentials are missing — is in
[docs/DEV-LITE.md](docs/DEV-LITE.md#flags--what-to-switch-and-where).

## License

MIT — see [LICENSE](LICENSE). Contributions welcome; see
[CONTRIBUTING.md](CONTRIBUTING.md).
