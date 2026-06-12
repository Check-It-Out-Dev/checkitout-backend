# checkItOut backend — documentation

Start here. The docs are organized as a reading path: map → business layer → domains →
security → operations → testing. Everything names real classes; everything is
verifiable in the source sitting next to it.

## Start

| Doc | What you get |
|---|---|
| [architecture.md](architecture.md) | The whole system in one read — runtime layers, domain map, conventions |
| [domain-model.md](domain-model.md) | The business layer: actors, entity lifecycles (Mermaid state machines), core flows, invariants. Also served as the pentest briefing |
| [diagrams/](diagrams/) | The 6-layer architecture diagram and the subscription state machine |

## Domains

| Doc | Domain |
|---|---|
| [domains/subscription-billing.md](domains/subscription-billing.md) | Subscription requirements + the as-built Stripe → Fakturownia → KSeF saga + sandbox wiring guide |
| [domains/marketplace.md](domains/marketplace.md) | Campaigns, applications, the 12-state cooperation machine |
| [domains/company-onboarding.md](domains/company-onboarding.md) | NIP-driven onboarding verified against GUS / CEIDG / MF White List |
| [domains/notifications.md](domains/notifications.md) | AFTER_COMMIT event pipeline, snapshot-frozen feed, batched mail |
| [domains/storage.md](domains/storage.md) | Direct-to-Firebase uploads with V4-signed URLs |
| [domains/instagram-oauth.md](domains/instagram-oauth.md) | Instagram OAuth refinement + Meta deauthorize/data-deletion callbacks |
| [domains/auth-magic-links.md](domains/auth-magic-links.md) | Firebase magic-link pages |
| [domains/auth-password-reset.md](domains/auth-password-reset.md) | Anti-enumeration password reset architecture |
| [domains/auth-2fa-passkeys.md](domains/auth-2fa-passkeys.md) | Company 2FA / passkeys design |
| [domains/follower-validation.md](domains/follower-validation.md) | Instagram follower validation |

## Security

| Doc | What you get |
|---|---|
| [security/architecture.md](security/architecture.md) | Defence in depth, layer by layer, with class names |
| [security/step-up-authentication.md](security/step-up-authentication.md) | The step-up ladder: email codes, TOTP, Redis getAndDelete tokens |
| [security/consent-gdpr.md](security/consent-gdpr.md) | Consent module spec + the full as-built RODO layer (crons, erasure pipeline) |

## Operations

| Doc | What you get |
|---|---|
| [operations/local-dev.md](operations/local-dev.md) | Self-bootstrapping local database + HTTPS for OAuth |
| [operations/deployment.md](operations/deployment.md) | CI/CD pipeline, Ansible provisioning, secrets flow |
| [operations/immutability.md](operations/immutability.md) | The chattr-immutable deploy-script chain and timestamp-guarded rollback |
| [operations/observability.md](operations/observability.md) | journald → Alloy → Loki → GCS, alerts, metrics gating |

## Testing

| Doc | What you get |
|---|---|
| [testing/strategy.md](testing/strategy.md) | The pyramid (7,970 / 842 / 175), decision matrix, the Maven gate wall, AI-native design |
| [testing/integration.md](testing/integration.md) | Testcontainers integration tier architecture |
| [testing/e2e.md](testing/e2e.md) | Cucumber suites, actors, e2e state endpoints, suite isolation |

## Reference

| Doc | What you get |
|---|---|
| [openapi/openapi.json](openapi/openapi.json) | The API contract (payments surface excluded while gated) — [how to regenerate](openapi/REGENERATE.md) |
| [research/chromatic-numbers.md](research/chromatic-numbers.md) | Graph-coloring applied to dependency conflict resolution — the research mindset behind the graph tooling |
| [../ansible/README.md](../ansible/README.md) | VPS provisioning: 15 roles, molecule-tested |
| Co-located `README.md` files | Deep dives next to the code: `src/main/java/**/storage/`, `consent/`, content submissions, logging, health |

## Honesty notes

These docs state real gaps where they exist (no magic-byte sniffing in uploads,
intended-vs-enforced notes in the domain model, GCS cleanup roadmap). The platform runs
in production with logs flowing and crons on schedule; the payments pipeline was
verified end-to-end on staging (invoices generated and delivered) and currently ships
behind `app.payments.enabled=false`. Independent penetration tests by WCSS
(Politechnika Wrocławska, WRO4digITal / EDIH Wrocław) are in progress.
