# Testing strategy — the pyramid and the gate wall

Measured on this tree, 2026-06 (annotation/scenario counts):

| Tier | Count | What it proves |
|---|---|---|
| Unit (`unit/`, Surefire) | **7,970** (`@Test` + `@ParameterizedTest`) | Pure logic — no Spring, no I/O, Mockito only |
| Service integration (`integration/`, Failsafe) | **842** | Real Spring context + Testcontainers PostgreSQL & Redis, transactional rollback |
| E2E (Cucumber, Failsafe) | **175 scenarios** in 15 suites | The whole product over HTTP, real auth flows, mail via GreenMail |

~9k tests for a three-person product is a deliberate trade: the suite is the safety
net that let the platform be rewritten, hardened and operated without a QA department.

## What gets tested where

The decision matrix that keeps each tier honest:

| Situation | Tier | Why |
|---|---|---|
| Pure functions, mappers, validators, crypto utils (`HmacUtils`, `PiiMaskingUtils`, …) | Unit | No mocking needed — fastest feedback, highest density |
| Services with a handful of mockable collaborators | Unit (Mockito) | Behavior contracts without container cost |
| Services with 10+ dependencies, `getSelf()` proxies, `@Transactional` semantics, programmatic `SecurityContext` | Integration | Faking that much wiring lies; Testcontainers tells the truth |
| Anything touching Firebase REST, full filter chain, cookies, multi-actor flows | E2E | Only the booted app over HTTP proves it |
| Test infrastructure itself, Spring auto-config, pure-delegation controllers | Not tested | Testing scaffolding is circular; delegation is covered one layer down |

## The Maven gate wall

Every `verify` run passes through, in order: **Enforcer** (Java 21 pinned, dependency
convergence) → **Spotless** (format) → **PMD** → **SpotBugs + FindSecBugs** (max
effort) → **OWASP dependency-check** (build FAILS at CVSS ≥ 7) → **JaCoCo** (per-tier
`.exec` files, merged report). A change that compiles but smells does not merge.

```bash
./mvnw clean verify                    # gates + unit + IT (current profile)
./mvnw jacoco:report                   # coverage -> target/site/jacoco/index.html
```

## Designed for AI agents — on purpose

The test infrastructure is shaped so an AI agent can develop against this repo
unsupervised, which is exactly how much of the recent work was done:

1. **Read the task, design, compile** — `./mvnw test-compile` is fast and honest.
2. **Run the right slice** — each E2E suite isolates via skip-flags
   ([e2e.md](e2e.md#running-one-suite-in-isolation)); unit and integration tiers run
   independently (`-Dtest=*UnitTest`, `-Pintegration`).
3. **Read the failures where they land** — `target/surefire-reports/`,
   `target/failsafe-reports/`, per-suite Cucumber HTML/JSON, plus application logs
   from the same monorepo run.
4. **Manipulate state legally** — the dedicated e2e Spring mode exposes
   `/test/auth/*` and `/test/legal/*` state endpoints (`@Profile("e2e & !prod & !test")`),
   so scenarios are deterministic instead of sleep-and-pray.
5. **Understand the business from the outside** — Cucumber scenarios are written in
   human language; an agent (or a new hire) reads the marketplace's rules from
   `src/test/resources/features/**` without opening a single Java file.

## Layout

```
src/test/java/com/sm/instagram/platform/
  unit/           *UnitTest.java        — Surefire, no context
  integration/    *IntegrationTest.java — Failsafe -Pintegration, Testcontainers
  e2e/            Run*IT.java runners, steps/, hooks/, support/
src/test/resources/features/            — Cucumber, tag-routed to suites
```

Deep dives: [integration.md](integration.md) · [e2e.md](e2e.md)
