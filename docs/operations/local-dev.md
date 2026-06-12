# Local development — database & HTTPS

Everything you need to run the backend on your machine: an auto-provisioned PostgreSQL
database and a self-signed HTTPS certificate (required the moment you want to test
Instagram OAuth, because Meta requires HTTPS redirect URIs even on localhost).

For the full first-run walkthrough (env keys, Firebase, Stripe), start at the
[root README](../../README.md). This page covers the two local-only mechanisms in depth.

---

## 1. Database — zero manual setup

Local profiles (`dev`, `no-redis`) provision their own database on first start.
You only need PostgreSQL running with a reachable superuser.

### How it works

Two credential sets, mirroring production's least-privilege model:

| Role | Used for |
|---|---|
| `postgres` superuser | One-time creation of the database and app user |
| `checkitout_app_local` | Runtime + Liquibase migrations (owns the schema) |

Components:

- **`LocalDatabaseInitializer`** (`com.sm.instagram.platform.config`) — runs before Spring
  connects, only on `dev`/`no-redis`. Idempotent: checks database, user, schema ownership
  and permissions, creates only what is missing, and prints a clear ✓/✗ status report.
- **`DualDataSourceConfiguration`** — exposes `liquibaseDataSource` (migrations) and the
  primary `dataSource` (runtime), both running as the app user.

### Configuration (`application-dev.yml`)

```yaml
postgres:
  superuser:
    username: postgres
    password: admin          # local default; change to match your install

spring:
  datasource:
    database: checkitout_local_db
    username: checkitout_app_local
    password: local_dev_password

local:
  db:
    init:
      enabled: true   # auto-initialization on/off
      force: false    # true = drop & recreate (data loss — use once, then revert)
```

### Troubleshooting

- **Force a clean slate:** set `local.db.init.force: true`, start the app once, set it back.
- **Auto-init fails / restricted superuser:** create the pieces manually:

```sql
CREATE USER checkitout_app_local WITH PASSWORD 'local_dev_password' CREATEDB;
CREATE DATABASE checkitout_local_db OWNER checkitout_app_local;
\c checkitout_local_db
ALTER SCHEMA public OWNER TO checkitout_app_local;
GRANT ALL PRIVILEGES ON DATABASE checkitout_local_db TO checkitout_app_local;
GRANT ALL ON SCHEMA public TO checkitout_app_local;
```

- **Opt out entirely:** `local.db.init.enabled: false` and manage the database yourself.

The same Liquibase changelog runs in every environment, so whatever it creates locally
is exactly what production runs.

---

## 2. HTTPS — required for Instagram OAuth

Instagram/Meta OAuth refuses plain-HTTP redirect URIs, localhost included. The `ssl`
profile turns the embedded server into HTTPS with a self-signed certificate.

### Generate the keystore (once per developer)

From the repo root:

```bash
keytool -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore keystore.p12 -validity 3650 -storepass changeit \
  -dname "CN=localhost, OU=Development, O=CheckItOut, L=Warsaw, ST=Mazovia, C=PL"

# move it next to the configs (gitignored — never commit it)
mv keystore.p12 src/main/resources/
```

`application-ssl.yml` already points at it (`classpath:keystore.p12`, password
`changeit`, TLS 1.2/1.3 only). Port 8080 simply becomes the HTTPS port.

### Run with SSL

```bash
# Maven
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,ssl

# IntelliJ: duplicate your run config and set Active profiles to: dev,ssl
```

Then open `https://localhost:8080` and click through the browser's self-signed-cert
warning (Advanced → Proceed). Verify:

```bash
curl -k https://localhost:8080/api/actuator/health    # -> {"status":"UP"}
```

### OAuth redirect URI

Register this in your Meta app dashboard (HTTPS, exact port):

```
https://localhost:8080/api/auth/social/callback/instagram
```

Deployed environments follow the same shape on your real domain:
`https://your-domain.example/api/auth/social/callback/instagram`.

### Common errors

| Symptom | Fix |
|---|---|
| `class path resource [keystore.p12] cannot be resolved` | Keystore not in `src/main/resources/` |
| `Password verification failed` | Keystore password ≠ `changeit` — regenerate or update `application-ssl.yml` |
| Port 8080 already in use | Stop the other process or override `-Dserver.port` |
| Browser "Not secure" | Expected for self-signed certs — proceed |
| OAuth redirect rejected | Meta dashboard must hold the **https** localhost URL, exact port |
