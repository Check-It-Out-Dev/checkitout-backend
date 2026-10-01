# Documentation

Start with the guide: one page per question, each drawn from the code, each with one diagram and a
way to check it yourself. Everything older is kept, and says at its top that it is older.

## The guide

| If you want to know | Read |
| :-- | :-- |
| How the system is put together | [Architecture overview](guide/overview.md) |
| What the product does, and which package owns what | [Domain model and modules](guide/domain.md) |
| How billing survives three writers on one row | [The subscription state machine, as a graph](guide/billing-graph.md) |
| Why the frontend cannot drift from this API | [The OpenAPI contract pipeline](guide/openapi-contract.md) |
| How sessions, second factors and secrets work — and what an outside team found | [Security](guide/security.md) |
| What "GDPR-aware" means here, exactly | [Privacy and GDPR: a base you can build on](guide/gdpr.md) |
| How a change gets to production, and back out of it | [CI/CD, rollback and the reviewer that cannot approve](guide/cicd.md) |
| How the API behaves under load | [Load testing on Kubernetes](guide/load-testing.md) |
| How the tests are shaped and governed | [Testing in depth](testing/README.md) |

## Running it

| | |
| :-- | :-- |
| [DEV-LITE.md](DEV-LITE.md) | The whole platform with no credentials: what is real, what is simulated, every flag |
| [ROLLOUT.md](ROLLOUT.md) | From a credential-less clone to production, one vendor at a time |
| [LOCAL_DATABASE_SETUP.md](LOCAL_DATABASE_SETUP.md), [SSL-SETUP-GUIDE.md](SSL-SETUP-GUIDE.md) | Local database and HTTPS, by hand |
| [openapi/REGENERATE.md](openapi/REGENERATE.md) | Regenerating the contract |
| [`../ansible/`](../ansible/), [`../deployment/`](../deployment/) | Provisioning the host; runbooks |

## Evidence

| | |
| :-- | :-- |
| [security/pentest-remediation.md](security/pentest-remediation.md) | The independent penetration test, finding by finding, with what was done |
| [security/log-injection-disposition.md](security/log-injection-disposition.md) | Why one scanner rule was dismissed in bulk, and the test that guards the decision |
| [evidence/meta-tech-provider.png](evidence/meta-tech-provider.png) | Meta's business and Tech Provider verification |
| [testing/measured-counts.json](testing/measured-counts.json) | Every number the README publishes, as measured |
| [openapi/openapi.json](openapi/openapi.json) | The contract, as generated |

## Testing, in more detail

[TESTING-PHILOSOPHY.md](TESTING-PHILOSOPHY.md) (why three tiers) ·
[testing/ai-in-the-loop.md](testing/ai-in-the-loop.md) (the invariants and the governance round) ·
[testing/mutation-primer.md](testing/mutation-primer.md) (mutation testing from first principles) ·
[`Tests/`](Tests/) (the suites, dated snapshots).

## History

Design documents kept as they were written. Each opens with a status line; none is the current
description of the code.

- [`Architecture/`](Architecture/) — the 2025 design notes.
- [`StripeGateway/`](StripeGateway/) — the billing requirements and plan, and the designs for the
  consent module, step-up authentication and the Instagram connection.
- [`NotificationSystem/`](NotificationSystem/) — the notification design, January 2025.
- [`features/`](features/) — notes on individual features, including two that were proposed and not
  built (passkeys, an AI assistant).
