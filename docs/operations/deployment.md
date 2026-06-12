# Deployment — from blank VPS to running release

Two cooperating systems: **Ansible** provisions and hardens the host once;
**GitHub Actions** deploys releases onto it repeatedly. Both ship in this repo and
both were exercised on the real production host.

## 1. Provisioning (Ansible, one `site.yml` run)

15 numbered roles take a blank Ubuntu 24.04 VPS to a production-hardened host in
roughly 70 minutes: base system → users → directories → Docker → secrets → scripts →
systemd → nginx → sudoers → monitoring → compose → automation → security → final
hardening → operator shell. Full docs: [`ansible/README.md`](../../ansible/README.md).

The choices worth copying:

- **Lockout-safe sequencing** — connectivity-breaking changes (netplan, iptables,
  SSH lockdown) are authored early but **applied last** (role 15), so a mid-run
  failure never severs the operator's session. Day-to-day operator login is a
  hardware-backed ED25519-SK key (FIDO2/YubiKey, PIN + touch); break-glass is the
  provider's out-of-band VNC/KVM console.
- **SSH crypto pinning** — chacha20-poly1305 / AES-GCM ciphers only, ETM-only MACs,
  curve25519 key exchange; plus a ~30-key sysctl set (no IP forwarding, no source
  routing, no ICMP redirects, strict rp_filter, SYN cookies).
- **Sudoers as deny-by-default made concrete** — 849 lines granting the deploy user
  only explicit full-path `docker compose` commands, zero wildcards, `visudo -c`
  validated before install, keystroke auditing on the admin account.
- **Role 14 freezes the result** — `chattr +i` on `.env` files, systemd units and
  sudoers: even root must consciously unflag before editing.
- **Molecule before production** — every role passes a containerized
  converge → idempotence → verify gate.
- **Observability ships in the same run** — Loki + Grafana Alloy behind an
  mTLS-enforcing nginx vhost ([observability.md](observability.md)).

## 2. Release pipeline (GitHub Actions)

A six-stage composition (`.github/workflows/backend-deployment-prod.yml`) built from
six reusable `workflow_call` units, with **Spring properties as the single source of
truth** — a config-loader stage emits the config JSON every downstream stage consumes,
so deploy parameters cannot drift from application configuration.

```
validate config → build (gate wall) → pre-deployment backup → secured deploy → reload & validate → (failure ⇒ auto-rollback)
```

- **The Maven gate wall** fronts everything: Enforcer pins Java [21,22) and bans
  conflicting transitives; Spotless, PMD; SpotBugs+FindSecBugs at effort=Max /
  threshold=Low with `failOnError`; OWASP dependency-check fails at **CVSS ≥ 7**;
  JaCoCo per-tier coverage.
- **Backups are coerced, not optional** — the pre-deployment backup script
  *hard-fails* unless the append-only audit logger (`chattr +a` logs under
  `/var/log/CiCd/{env}`) is present.
- **Deploy scripts are untrusted artifacts** — sha256 + JSON metadata
  (`github_run_id`, `locked_at`), locked immutable the second they land, re-verified
  at the execution choke point, self-deleting afterwards. The full chain:
  [immutability.md](immutability.md).
- **Auto-rollback** restores the systemd-managed previous release when post-deploy
  validation fails; the rollback script itself refuses to run if older than 600 s
  (replay protection) and self-deletes with its checksum sidecar on every exit path.

## 3. Secrets flow

Production secrets live in **Google Secret Manager**; an init container materializes
them as files on a mounted volume (Kubernetes-style), `ProductionSecretService`
resolves them at boot, and dev machines fall back to local `.env`
(see [`.env.example`](../../.env.example) — every key documented). Ansible-side
bootstrap secrets are Vault-encrypted with 640 perms and group scoping
(`ansible/inventory/group_vars/*/vault-*.yml` ship as safe dummies).

## 4. Database

Managed PostgreSQL (any provider). Liquibase owns the schema
(`ddl-auto=validate` everywhere); `deployment/INIT_PRODUCTION_DATABASE.sql` bootstraps
the role/database with least-privilege split (DDL user for migrations, restricted
runtime user). See [`deployment/DATABASE_SETUP_GUIDE.md`](../../deployment/DATABASE_SETUP_GUIDE.md).
