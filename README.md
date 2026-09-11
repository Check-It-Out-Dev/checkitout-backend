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
[![Tests](https://img.shields.io/endpoint?url=https://check-it-out-dev.github.io/checkitout-backend/badges/tests.json)](https://check-it-out-dev.github.io/checkitout-backend/)
[![Flaky](https://img.shields.io/endpoint?url=https://check-it-out-dev.github.io/checkitout-backend/badges/flaky.json)](https://check-it-out-dev.github.io/checkitout-backend/)
[![Mutation](https://img.shields.io/endpoint?url=https://check-it-out-dev.github.io/checkitout-backend/badges/mutation.json)](https://check-it-out-dev.github.io/checkitout-backend/#quality)
[![Security](https://img.shields.io/endpoint?url=https://check-it-out-dev.github.io/checkitout-backend/badges/security.json)](https://check-it-out-dev.github.io/checkitout-backend/#quality)
[![pull-request pipeline](https://github.com/Check-It-Out-Dev/checkitout-backend/actions/workflows/pr.yml/badge.svg)](https://github.com/Check-It-Out-Dev/checkitout-backend/actions/workflows/pr.yml)
[![nightly pipeline](https://github.com/Check-It-Out-Dev/checkitout-backend/actions/workflows/nightly.yml/badge.svg)](https://github.com/Check-It-Out-Dev/checkitout-backend/actions/workflows/nightly.yml)
[![image](https://github.com/Check-It-Out-Dev/checkitout-backend/actions/workflows/build-image.yml/badge.svg)](https://github.com/Check-It-Out-Dev/checkitout-backend/actions/workflows/build-image.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=Check-It-Out-Dev_checkitout-backend&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Check-It-Out-Dev_checkitout-backend)
[![Reliability](https://sonarcloud.io/api/project_badges/measure?project=Check-It-Out-Dev_checkitout-backend&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=Check-It-Out-Dev_checkitout-backend)
[![Security](https://sonarcloud.io/api/project_badges/measure?project=Check-It-Out-Dev_checkitout-backend&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=Check-It-Out-Dev_checkitout-backend)

<sub>The test, flaky, mutation and security badges are read live from the <a href="https://check-it-out-dev.github.io/checkitout-backend/">quality dashboard</a>, which every run republishes.</sub>

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

> **The results, live** — [quality dashboard](https://check-it-out-dev.github.io/checkitout-backend/) ·
> [Allure with history](https://check-it-out-dev.github.io/checkitout-backend/allure/latest/) ·
> [k6 against the sandbox](https://checkitoutapp.grafana.net/public-dashboards/f48c40b8b3244bdfa019117fa9fdcbbe)
>
> Everything below is the design. The dashboard is the same thing after it has run: pass rate and
> its trend, mutation score, the security finding count, the list of every test that failed or
> flaked in the last ten runs, and links to the reports themselves. The badges at the top of this
> file are read from it, which is why they can be red.

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

**The numbers**, measured on this tree by `node tools/ci/measure-counts.mjs`, which the unit
job re-runs with `--check` so a stale figure fails the build rather than ageing in public:

| | | |
| :-- | --: | :-- |
| **Test methods** | **9,101** | 8,494 `@Test` + 607 `@ParameterizedTest`, across **292** test classes and the **2,162** `@Nested` groups inside them |
| **Test code : main code** | **2.0 : 1** | ~188k lines of test Java against ~93k of main |
| **Cucumber** | **34 files** | 148 `Scenario` + 28 `Scenario Outline`, **277 after Examples expansion** |
| **Domain** | **38 entities** | 50 REST controllers |
| **Contract** | **233 paths** | 272 operations · 205 schemas · OpenAPI 3.1 |

And one number worth more than any of them: **`@Disabled` appears zero times**
across all 363 test files. Nothing is quarantined, skipped-and-forgotten, or
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
[`deployment/`](deployment/)). **Rollback is automatic.** [`auto-rollback-systemd.yml`](.github/workflows/auto-rollback-systemd.yml)
— restore the backup, restart the unit, nine health attempts — is wired into both chains as a job
that fires when the backup succeeded and something after it did not. The condition is narrow on
purpose: rolling back to a backup that does not exist is worse than staying broken, and a failure in
config, validation or build never reached the server, so there is nothing there to undo. Hosting is Docker Compose and systemd on that one VPS —
right-sized for this product, and the reason the documents say no to Kubernetes
*for hosting*.

The **test pipeline** runs beside it, on GitHub-hosted runners on the free tier, as **two pipelines
with two verdicts**. Six workflows used to fire on every push and pull request, each with its own
outcome; every tier below is now a reusable workflow with no trigger of its own — still dispatchable
while you work on it, never firing by itself — and each pipeline ends in one line.

| Pipeline | Trigger | What it calls | Budget |
| :--- | :--- | :--- | :--- |
| [`pr.yml`](.github/workflows/pr.yml) | pull request, push to `main` | unit · integration · Sonar's new-code gate · dependency review | ~7 min |
| [`nightly.yml`](.github/workflows/nightly.yml) | 02:30 UTC, on demand | every tier including end-to-end · Schemathesis · mutation · security | as long as it takes |

| Tier | What runs |
| :--- | :--- |
| [`ci-tests.yml`](.github/workflows/ci-tests.yml) | Unit (surefire, `-Ptest`), integration (failsafe, `-Pintegration`, Testcontainers) and end-to-end (Cucumber, `-Pe2e`) — which tiers run is the `tier` input, never the event. Allure 3 with history to Pages. |
| [`mutation.yml`](.github/workflows/mutation.yml) | PIT over the security, rate-limit and auth services: **43.35 %**, or **69.22 %** on the code the unit suite actually reaches, across 2,397 mutants. Coverage says a line ran; this says whether anything checked the result. The report names the seventeen classes with no unit test at all rather than hiding them in an average. |
| [`api-fuzz.yml`](.github/workflows/api-fuzz.yml) | Schemathesis generates requests from the OpenAPI document and sends them at a running instance, checking every response against the schema it claims. |
| [`security.yml`](.github/workflows/security.yml) | Semgrep over the OWASP, secrets and Java rule sets; Checkov on the Dockerfiles and workflows; Trivy on the tree and the published image, with an SBOM of each. Every scanner writes SARIF into code scanning. |
| [`sonar.yml`](.github/workflows/sonar.yml) | SonarQube Cloud, fed the JaCoCo coverage the unit tier writes **and the dependency classpath Maven resolves**. The second half is not a detail: the CLI scanner has no view of the reactor, and without `sonar.java.libraries` every rule that needs a resolved type quietly degrades. It was reporting fourteen inner test classes as missing `@Nested` when the annotation was on the line above, and missing fifteen real defects — a guaranteed NPE, three `@Transactional` annotations on private methods, two methods that only looked like overrides — because it could not resolve the types to see them. |

| | Trigger | What runs |
| :--- | :--- | :--- |
| [`build-image.yml`](.github/workflows/build-image.yml) | push to main | Publishes the container image to `ghcr.io/check-it-out-dev/checkitout-backend` — what the frontend's full-stack and Kubernetes tiers boot against. |

Every run publishes to the [quality dashboard](https://check-it-out-dev.github.io/checkitout-backend/) — pass rate and its trend, **mutation score**,
per-tier counts, the flaky list over the last ten runs — and an
[Allure report](https://check-it-out-dev.github.io/checkitout-backend/allure/latest/) whose history carries across runs. The frontend's dashboard is
the [same page for that repository](https://check-it-out-dev.github.io/checkitout-frontend/), and its
[docs/ci/METRICS.md](https://github.com/Check-It-Out-Dev/checkitout-frontend/blob/main/docs/ci/METRICS.md)
is the schema both sites publish.

The end-to-end tier is the one worth a sentence. It used to need a real Firebase project, which is why it
could not run in public CI at all. It now runs against the emulator suite — password sign-in, ID tokens,
custom claims, Firestore — seeded at startup with the three actors the feature files name, so the whole
corpus runs on a public runner with no credential anywhere in the environment. One scenario class abstains
honestly rather than passing: travel analysis needs the licensed GeoLite2 database, which is not shipped, so
those scenarios report a missing input.

Two things outside this repository close the loop. The frontend's
[`contract-check`](https://github.com/Check-It-Out-Dev/checkitout-frontend/actions/workflows/contract-check.yml)
boots this application daily, takes the OpenAPI document from the running server and compares it with the copy
the frontend compiles against, so a contract change here becomes a build error there rather than a runtime
surprise. And [checkitout.app/sandbox](https://checkitout.app/sandbox/) is this backend on the `dev-lite`
profile with two fixed demo accounts — a live instance to look at rather than run.

## In progress

Dated 2026-09. ✅ built · 🟡 under way · ⬜ designed, not started.

|     | What | Detail |
| :-- | :-- | :-- |
| ✅ | **A test pipeline** | All three tiers run in `ci-tests.yml` on free hosted runners: unit on every push, integration on Testcontainers, the Cucumber corpus nightly against the Firebase emulators with no credential. Not the sharded Testkube-on-ARC shape this row once described — the Kubernetes tier lives in the frontend repository, where a kind cluster runs Playwright as an Indexed Job and k6 against this backend's image |
| 🟡 | **Rollback wired into the release chain** | The reusable rollback exists and is still not wired — and a security triage found why that is no longer a simple job. `auto-rollback-systemd.yml` interpolates `${{ }}` expressions straight into shell across 91 sites, which CodeQL flags and which is harmless today only because the workflow has no callers and its self-hosted runner cannot be scheduled on a public repository. Wire it to a caller that passes a commit message and those become remote code execution on a runner holding the deploy key. The `env:`-binding fix comes first |
| ✅ | **Report aggregation** | JUnit XML from every tier feeds a quality dashboard on GitHub Pages with run history and a flaky list over the last ten runs, plus an Allure report whose history carries across runs. ReportPortal stays deferred; a flaky *quarantine* is a policy question, not a missing tool, and is still open |
| ✅ | **Performance in CI** | k6 gates this API's public surface — browse and apply journeys with per-journey and per-endpoint thresholds — run from the frontend repository, which owns the script: as a k6-operator `TestRun` against the in-cluster service in the Kubernetes tier, and against the live sandbox after every deploy. Metrics are remote-written to a public Grafana Cloud dashboard |
| ✅ | **Schemathesis against the running server** | `api-fuzz.yml` generates requests from the schema and sends them at a real instance. It earned its place on the first run: every secured operation answered 401 while the document declared none, which is a contract defect because the frontend generates its client from that document. Fixed by `AuthFailureResponsesCustomizer`; the fuzzer now runs nightly |
| ✅ | **OWASP Top 10 in the pipeline** | `security.yml`: Semgrep over the OWASP, secrets and Java rule sets; Checkov on the Dockerfiles and workflows for the misconfiguration surface nothing else reaches; Trivy on both the source tree and the **published image**, with an SBOM of each. All SARIF into code scanning. The dynamic half runs from the frontend repository, against the sandbox — which is this backend |
| ✅ | **SonarQube Cloud quality gate** | Free for public repositories; fed the JaCoCo coverage the unit tier already writes. Its gate can be set on new code alone, which is what makes an existing backlog survivable |

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
