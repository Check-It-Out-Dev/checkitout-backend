# Contributing to checkItOut backend

Thanks for looking under the hood. This repo is a production-grade blueprint — the
bar for changes is the same one we held ourselves to.

## Ground rules

1. **The build gates are the contract.** `./mvnw clean verify` must pass: Enforcer
   (Java 21), Spotless, PMD, SpotBugs + FindSecBugs (max effort), OWASP
   dependency-check (fails at CVSS ≥ 7), JaCoCo. If a gate blocks you, fix the cause —
   never disable the gate.
2. **Tests ride along.** A change to domain logic comes with tests in the right tier
   (see [docs/testing/strategy.md](docs/testing/strategy.md) for the decision matrix).
   Bug fixes come with a regression test that fails before the fix.
3. **Schema changes go through Liquibase only.** `ddl-auto=validate` is non-negotiable;
   add a changelog under `src/main/resources/db/changelog/` (chronological `YYYY/MM`
   layout, context-gated if environment-specific). Every new entity carries `@Version`.
4. **Conventions are load-bearing.** Feature-first packages; constructor injection;
   `@TransactionalEventListener(AFTER_COMMIT)` for side-effects; ShedLock on every
   `@Scheduled`; Ports & Adapters for replaceable vendors; errors via
   `TranslatableException` with i18n keys.
5. **No secrets, ever.** `.env` stays gitignored; new keys are added to
   `.env.example` with a placeholder and a comment that says where to obtain the value.

## Local setup

The short version (full walkthrough in the [README](README.md)):

```bash
cp .env.example .env          # fill in your keys
docker compose -f docker-compose-dev-redis.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev` self-provisions its PostgreSQL database on first run
([docs/operations/local-dev.md](docs/operations/local-dev.md)). No Redis? Use the
`no-redis` profile — `storage.mode` swaps every Redis-backed component for an
in-memory one.

## Running tests

```bash
./mvnw test -Dtest=*UnitTest -DskipITs=true -DskipPmd=true   # unit (~7,970)
./mvnw clean verify -Pintegration -DskipPmd=true             # integration (Testcontainers)
./mvnw clean verify -Pe2e -DskipPmd=true                     # all 15 E2E suites (10+ min)
```

Run a single E2E suite with the skip-flag pattern —
[docs/testing/e2e.md](docs/testing/e2e.md). Never use `-Dit.test=`.

## API changes

The OpenAPI spec ([docs/openapi/openapi.json](docs/openapi/openapi.json)) is the
published contract — the frontend generates its client from it. If you change a
controller or DTO, regenerate per [docs/openapi/REGENERATE.md](docs/openapi/REGENERATE.md)
and commit the spec diff in the same PR.

## Pull requests

- One coherent change per PR — code + tests + migration + docs touchpoints together.
- Describe **what breaks without your change** (for fixes) or **what it enables**
  (for features).
- If you touched security-sensitive surfaces (auth filters, consent, webhooks,
  uploads, rate limiting), say so explicitly — those reviews go deeper.

## Where to start reading

[docs/README.md](docs/README.md) is the index;
[docs/architecture.md](docs/architecture.md) is the map. The E2E feature files under
`src/test/resources/features/` double as executable product documentation.
