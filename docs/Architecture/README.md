# Architecture documents

Five documents, one subject each. They describe what is implemented; where a
document says a thing is planned, it says so.

| Document | What it covers |
| :-- | :-- |
| [01_SECURITY_ARCHITECTURE.md](01_SECURITY_ARCHITECTURE.md) | Edge protection (Cloudflare, nginx rate limiting, Redis), hardware keys for operators, session cookies and step-up authentication, the sealed audit log |
| [02_CICD_DEPLOYMENT.md](02_CICD_DEPLOYMENT.md) | The GitHub Actions release chain, the immutable-script deployment model, systemd and Docker Compose on the VPS, environment separation |
| [03_MONITORING_OBSERVABILITY.md](03_MONITORING_OBSERVABILITY.md) | Grafana, Loki and Alloy; what is logged, what is alerted on, how logs are shipped and sealed |
| [04_DATA_PRIVACY_COMPLIANCE.md](04_DATA_PRIVACY_COMPLIANCE.md) | RODO/GDPR in code: consent records and versions, erasure, data residency |
| [05_INNOVATION_SCALABILITY.md](05_INNOVATION_SCALABILITY.md) | Scaling decisions and their reasons, including why the product stays on Docker Compose rather than Kubernetes |

[COMPLETE_TECHNICAL_DOCUMENTATION.md](COMPLETE_TECHNICAL_DOCUMENTATION.md) and
[Architecture.md](Architecture.md) are the older long-form versions the five
documents were split from; they are kept for history.

The testing story is not here: it is in the [README](../../README.md#testing),
[TESTING-PHILOSOPHY.md](../TESTING-PHILOSOPHY.md) and [docs/Tests/](../Tests/).
