# Testing in depth

The [README](../../README.md) says what is tested in six lines and one table. This page is the rest:
how the tiers are shaped, what runs where, how the suite itself is governed, and what a week of
machine-written change did to the scanners. The argument for the shape is
[TESTING-PHILOSOPHY.md](../TESTING-PHILOSOPHY.md); the suites and how to add one are in
[`docs/Tests/`](../Tests/).

## Three tiers, one rule

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

## The numbers, and how they stay true

The measured table is in the [README](../../README.md#the-numbers). It is produced by
`node tools/ci/measure-counts.mjs`, written to [`measured-counts.json`](measured-counts.json), and the
unit job re-runs the script with `--check`, so a figure that no longer matches the tree fails the
build instead of ageing in public. They are declarations counted in the source, not a green run;
reproduce any of them with `grep -rhE '^\s*@Test\b' src/test --include='*.java' | wc -l` and its
siblings (`@ParameterizedTest`, `@Nested`, `@Disabled`, `^\s*Scenario:`, `@Entity`,
`@RestController`).

## The Cucumber corpus is a seam, not only a suite

The feature files under [`src/test/resources/features/`](../../src/test/resources/features/) are the
executable statement of what the platform promises. The frontend ports them into its own
browser-driven tier and waives the rest with written reasons, and a gate there fails when a new
feature appears here without being either ported or waived. The rules are proven twice: once against
the service layer, once through the screens. The counts on that side belong to the
[frontend README](https://github.com/Check-It-Out-Dev/checkitout-frontend#readme).

## What runs where

| Tier | What runs |
| :--- | :--- |
| [`ci-tests.yml`](../../.github/workflows/ci-tests.yml) | Unit (surefire, `-Ptest`), integration (failsafe, `-Pintegration`, Testcontainers) and end-to-end (Cucumber, `-Pe2e`) — which tiers run is the `tier` input, never the event. Allure 3 with history to Pages. |
| [`mutation.yml`](../../.github/workflows/mutation.yml) | PIT over the security, rate-limit and auth services. Coverage says a line ran; this says whether anything checked the result. The current score, on all mutants and on the code the unit suite actually reaches, is on the [quality dashboard](https://check-it-out-dev.github.io/checkitout-backend/#quality); the report names the classes with no unit test at all rather than hiding them in an average. |
| [`api-fuzz.yml`](../../.github/workflows/api-fuzz.yml) | Schemathesis generates requests from the OpenAPI document and sends them at a running instance, checking every response against the schema it claims. |
| [`security.yml`](../../.github/workflows/security.yml) | Semgrep over the OWASP, secrets and Java rule sets; Checkov on the Dockerfiles and workflows; Trivy on the tree and the published image, with an SBOM of each. Every scanner writes SARIF into code scanning. |
| [`sonar.yml`](../../.github/workflows/sonar.yml) | SonarQube Cloud, fed the JaCoCo coverage the unit tier writes **and the dependency classpath Maven resolves**. The second half is not a detail: the CLI scanner has no view of the reactor, and without `sonar.java.libraries` every rule that needs a resolved type quietly degrades. It was reporting fourteen inner test classes as missing `@Nested` when the annotation was on the line above, and missing fifteen real defects — a guaranteed NPE, three `@Transactional` annotations on private methods, two methods that only looked like overrides — because it could not resolve the types to see them. |

| | Trigger | What runs |
| :--- | :--- | :--- |
| [`build-image.yml`](../../.github/workflows/build-image.yml) | push to main | Publishes the container image to `ghcr.io/check-it-out-dev/checkitout-backend` — what the frontend's full-stack and Kubernetes tiers boot against. |

The pipelines these tiers are called from, the release chain and the reviewer are in
[CI/CD](../guide/cicd.md).

## Test governance

Two things, named as two, because they run at different times and answer different questions.

**On every pull request: the invariants.** The unit tier runs armed — a JaCoCo listener records what
every test exercised — and the `invariants` job in [`ci-tests.yml`](../../.github/workflows/ci-tests.yml)
judges the change on that run's own probes against the base artefacts the last governance run
published, in one line: coverage never lower on any file, class or method the change did not touch
(I1); no mutant killed on base left unkilled by the tests still in the tier (I2); the suite green
(I3); the published numbers consistent (I4). It proposes nothing, calls no model, holds no secret. A
missing artefact is INCOMPLETE, never PASS.

**As a process of its own: the governance round.** A proposal run computes, from per-test coverage and
the whole-estate kill matrix (the `mutation-matrix` profile, every class in `main`, every killing test
per mutant), which tests are carried by others: a test may leave the pull-request tier only if every
probe it covers and every mutant it kills is also covered and killed by tests that stay, and the
matrix actually ran it against a mutant. An exact minimum-cost cover picks the set; a round policy
takes the surest evidence tier first — an exact duplicate in the same class before a test with two
carriers before one with one — under a budget, never more than half of any class in one round.
`apply.mjs` tags each one `@Tag("subsumed")` (never `@Disabled`, which this suite deactivates on
purpose; never a deletion — the nightly passes `-Dsubsume.excludedGroups=never` and runs everything)
and opens a branch `test-governance/round-N-<runId>` whose tracked
[`docs/testing/governance/round.json`](governance/round.json) is the link to the
proposal run. On that branch, under the label `test-governance`,
[`test-governance-pr.yml`](../../.github/workflows/test-governance-pr.yml) trusts nothing the proposal
said and re-measures on the reduced tier: the tier armed, then the invariants from that run; the
tier again in random order, so a kept test that only passed because a demoted one ran before it
shows itself; PIT again with the demoted group excluded, so the mutation score is measured, not
projected; then a ledger with one rule — tests or seconds lower, **and** none of coverage on
unchanged code, kills on unchanged code or mutation score lower, both runs green, the published
numbers consistent — drawn as a gains diagram beside the subsumption diagram. A person merges.

Three voices appear on such a pull request, each with a fixed first line, so a reader knows who is
speaking before reading a number: `▣ Proposer` (the tool on the box, applies CONFIRMED only, never
merges), `▮ Invariants` (the replay plane, no model), `◇ Reviewer` (Claude Code on a pull request,
quotes the gate's numbers only, never approves). Every figure on the page comes from a report the
run produced; the row above is the only one that lives in this README, and the gate keeps it true.
The method — identities, artefact shapes, the solver, the metrics, the round policy — is the
frontend's [`tools/subsume/README.md`](https://github.com/Check-It-Out-Dev/checkitout-frontend/blob/main/tools/subsume/README.md)
and its [ADR](https://github.com/Check-It-Out-Dev/checkitout-frontend/blob/main/docs/ci/ADR-test-subsumption.md).
Round 1 is [pull request #30](https://github.com/Check-It-Out-Dev/checkitout-backend/pull/30); its
measured ledger is in that pull request, dated, and nowhere else.

The plain-words version of this, with the loop drawn, is [ai-in-the-loop.md](ai-in-the-loop.md); if
mutants, kills and set cover are new words, [mutation-primer.md](mutation-primer.md) starts from a
house and its guards.

## In progress

Dated 2026-09. ✅ built · 🟡 under way · ⬜ designed, not started.

|     | What | Detail |
| :-- | :-- | :-- |
| ✅ | **A test pipeline** | All three tiers run in `ci-tests.yml` on free hosted runners: unit on every push, integration on Testcontainers, the Cucumber corpus nightly against the Firebase emulators with no credential. Not the sharded Testkube-on-ARC shape this row once described — the Kubernetes tier lives in the frontend repository, where a kind cluster runs Playwright as an Indexed Job and k6 against this backend's image |
| ✅ | **Rollback wired into the release chain** | `auto-rollback-systemd.yml` is called from both deployment chains and fires when the backup succeeded and a later stage failed; its inputs are bound through `env:` rather than interpolated into shell, which is what a security triage required before it could be wired |
| ✅ | **Report aggregation** | JUnit XML from every tier feeds a quality dashboard on GitHub Pages with run history and a flaky list over the last ten runs, plus an Allure report whose history carries across runs. ReportPortal stays deferred; a flaky *quarantine* is a policy question, not a missing tool, and is still open |
| ✅ | **Performance in CI** | k6 gates this API's public surface — browse and apply journeys with per-journey and per-endpoint thresholds — run from the frontend repository, which owns the script: as a k6-operator `TestRun` against the in-cluster service in the Kubernetes tier, and against the live sandbox after every deploy. Metrics are remote-written to a public Grafana Cloud dashboard |
| ✅ | **Schemathesis against the running server** | `api-fuzz.yml` generates requests from the schema and sends them at a real instance. It earned its place on the first run: every secured operation answered 401 while the document declared none, which is a contract defect because the frontend generates its client from that document. Fixed by `AuthFailureResponsesCustomizer`; the fuzzer now runs nightly |
| ✅ | **OWASP Top 10 in the pipeline** | `security.yml`: Semgrep over the OWASP, secrets and Java rule sets; Checkov on the Dockerfiles and workflows for the misconfiguration surface nothing else reaches; Trivy on both the source tree and the **published image**, with an SBOM of each. All SARIF into code scanning. The dynamic half runs from the frontend repository, against the sandbox — which is this backend |
| ✅ | **SonarQube Cloud quality gate** | Free for public repositories; fed the JaCoCo coverage the unit tier already writes. Its gate can be set on new code alone, which is what makes an existing backlog survivable |
| 🟡 | **The test population under invariants** | Per-test coverage for every unit test (a JaCoCo listener that resets after each test and writes the exec file back whole), the whole-estate `mutation-matrix` profile, the invariants job on every pull request, and the governance round with its special re-measuring job — see [Test governance](#test-governance). Round 1 is open as [#30](https://github.com/Check-It-Out-Dev/checkitout-backend/pull/30). Next: the reviewer that draws it on the pull request, and the scheduled governance run that replaces the developer box |

## Seven days of machine-written change

Coding agents ran in a loop over this repository and the frontend for about seven
days, with the security scanners switched on while they worked. Counted through
the GitHub API on 2026-09-12 — a dated observation of a service, not a fact about
this tree, so no gate re-derives it:

|                 | Code scanning | Fixed | Dismissed | Open | Dependabot |
| :-------------- | :------------ | :---- | :-------- | :--- | :--------- |
| this repository | 1151 | 582 | 550 | 19 | 138 (137 fixed) |
| the frontend | 335 | 265 | 63 | 7 | 82 (all fixed) |

```bash
gh api "repos/Check-It-Out-Dev/checkitout-backend/code-scanning/alerts?per_page=100" \
  --paginate -q '.[].state' | sort | uniq -c
```

1486 findings in three days is three scanners meeting a codebase for the first
time, not a collapse. What happened to them is the part worth reading: 509 of the
550 dismissals here are one rule, closed by one control at the sink with a
[written disposition](../security/log-injection-disposition.md) and a test that
fails if the control is ever reverted. Every dismissal carries a comment. None of
that is enforced by anything yet — it holds because one person did it that way.

The loop this codebase was built with assumed the test and the code came from two
separate readings of the requirement. An agent writing both in one pass ends that,
and the same seven days left the contract seam above out of step without a single
red build. **So: after seven days of machine-written change, does the system still
do the same thing for the user?** Answering that mechanically — invariants
extracted from the code, enforced on the diff, with a human ratifying every
loosening — is in progress, and the method, its precedents and its honest status
are in the frontend's
[design of record](https://github.com/Check-It-Out-Dev/checkitout-frontend/blob/main/docs/ci/GOVERNING-MACHINE-WRITTEN-CHANGE.md).
