# Why the tests look like this

`docs/Tests/` explains how each tier works. This is the argument for why they
are shaped the way they are — worth writing down, because the shape is a
deliberate choice that costs more up front and keeps paying afterwards.

The rule underneath all of it: **simulate what you are not testing, never what
you are.** A test that replaces the thing it is meant to verify does not fail
when that thing breaks. It fails when you change your mind about the mock.

---

## Unit — mock freely, and run in seconds

Thousands of tests (8,908 test methods across the three tiers as of 2026-09-08 — the README keeps the measured table), no external services, the whole tier in about a minute.

Here mocking is correct. A unit test asks whether one piece of logic is right
given its inputs; the database, the clock and the vendor are noise. Speed is the
feature — this tier runs on every commit through the pre-commit gate, so it has
to stay fast enough that nobody is tempted to skip it.

What we do NOT do here is let mocks encode assumptions about someone else's
behaviour and then call that coverage. "The repository returns a user" is a
statement about our code. "Postgres applies this constraint" is a guess, and it
belongs one tier down.

---

## Integration — real database, real SMTP, real migrations

About 890 tests against **Testcontainers PostgreSQL** and **GreenMail**.

This is where the interesting bugs live, and none of them are visible with a
mocked repository:

- SQL that is valid until a reserved word appears in a table name.
- A Liquibase changeset that works on an empty schema and not on a migrated one.
- Transaction boundaries, optimistic locking, cascade rules, lazy loading.
- Mappers that quietly drop a field.
- E-mail templates that render fine as a string and malformed as MIME.

A real database is not slower in any way that matters — a container starts in
seconds and the tier runs in minutes. A real SMTP server means we assert on the
message that actually went over the wire, headers and encoding included, rather
than on the fact that a method was called.

GreenMail is in-memory, but note what it is *not*: it is not a stub of our mail
code. It is a genuine SMTP server. Our code does not know it is in a test.

---

## End-to-end — the whole application, and the logs to read afterwards

Cucumber suites boot the real application, with the real security filters, and
drive it through the same HTTP surface the frontend uses. Real actors, real
sessions, real rate limiting.

Two things make this tier worth its runtime:

**It mirrors production as closely as we can afford.** Every layer that a
request passes through in production is present. That is the only way a test can
tell you something you did not already assume — a mocked filter chain always
authorises exactly the way you wrote the mock.

**It leaves a readable trail.** The runs tail their logs to file, which turns a
failure into evidence instead of a verdict. A human — or an agent — can read the
sequence of what actually happened, in order, across every layer, and find the
layer where reality diverged from the story. Post-mortem beats guess-and-retry,
and it is what makes a failure in this tier cheap to act on rather than a day of
bisecting.

That combination is why this tier has caught defects that never reached a user.
The cost is real and paid once; the alternative is discovering the same defects
in production, where the cost is paid repeatedly and by someone else.

---

## The frontend belongs in the same argument

This is the part teams most often skip, and skipping it is what makes a rewrite
drag.

The OpenAPI contract is generated from the backend, committed byte-identical to
both repositories, and the TypeScript client is generated from it — never
hand-written. That single decision changes what a frontend test can be:

1. **The contract is compiled, not agreed.** If the backend renames a field, the
   frontend stops building. Not a runtime surprise, not a bug report from a user
   — a red compiler, before anything is merged.

2. **The same generated types flow into the services.** The logic layer speaks
   the schema exactly, so "what shape is this response" stops being a question
   anyone answers from memory or from a screenshot of Swagger.

3. **You can test the logic before a single component exists.** Services, guards,
   state, error handling and the BDD tiers run against a real backend and a real
   database. By the time a component is written, the behaviour behind it is
   already verified.

4. **What is left is the visual layer.** And that is the point: when the logic is
   proven and the schema is exact, building the UI is the honest, pleasant part of
   the work rather than an archaeology exercise. The frontend tiers here —
   973 unit tests, plus Playwright sandbox, MSW, visual and scenario tiers, plus
   integration and BDD suites against a live stack — exist so that the last mile
   is the only mile left.

The frontend e2e tier is not duplicating the backend's. It tests the contract
from the other side: that the client we generated, used the way our services use
it, gets what the backend actually sends.

---

## Where dev-lite fits — and where it does not

`dev-lite` simulates sign-in, e-mail, the company registries and the upload
transport so that a stranger can run the product in one command with no
credentials. It exists to lower the barrier to a first run.

It is deliberately **not** part of the test story. No tier was moved onto it,
and none should be. Where a live vendor is unavailable, the affected tests skip
honestly — they announce that they did not run rather than passing against a
substitute. A green suite that verified nothing is worse than a skipped one,
because you believe it.

The two ideas are consistent: make the seams real enough to test against, and
the same seams become the thing you can swap for an on-ramp. See
[`ROLLOUT.md`](ROLLOUT.md) for switching each simulated seam back to the real
vendor.
