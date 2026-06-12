# checkItOut VPS Provisioning — Ansible Automation

> Role and path names still say `instagram-platform` — the product's legacy internal
> name. Functionally identical; renaming across 15 roles wasn't worth the churn.

Automated provisioning for the checkItOut infrastructure on an Ubuntu 24.04 VPS,
battle-tested on the production deployment (November 2025).

**Documentation map:** [GETTING-STARTED](GETTING-STARTED.md) (first run) ·
[DEPLOYMENT-GUIDE](DEPLOYMENT-GUIDE.md) (full procedure) ·
[BEFORE-DEPLOYMENT-CHECKLIST](BEFORE-DEPLOYMENT-CHECKLIST.md) ·
[QUICK-REFERENCE](QUICK-REFERENCE.md) (command crib sheet) ·
[TESTING](TESTING.md) (molecule) · [TROUBLESHOOTING](TROUBLESHOOTING.md) ·
per-role READMEs under `roles/*/`.

**Deployment Time:** 65-75 minutes fully automated (vs 12-14 hours manual)
**Automation Level:** 95% (only secrets need manual replacement)
**Status:** ✅ Production-Ready

---

## 🚀 Quick Start

**New to this project? Start here:**

1. **[QUICK-DEPLOYMENT-GUIDE.md](QUICK-DEPLOYMENT-GUIDE.md)** - Fast deployment commands
2. **[LESSONS-LEARNED.md](LESSONS-LEARNED.md)** - Critical issues and solutions
3. **[DEPLOYMENT-WORKFLOW-UPDATED.md](DEPLOYMENT-WORKFLOW-UPDATED.md)** - Detailed step-by-step

---

## Overview

This Ansible project automates the complete deployment of the Instagram Platform infrastructure, converting 17 manual migration guides (697+ commands) into automated, idempotent playbooks with lessons learned from actual deployment.

**What Gets Deployed:**
- Ubuntu 24.04 base system (packages, network, firewall, SSH hardening)
- User and permission model (exact UIDs/GIDs for Docker volumes)
- Docker infrastructure (networks, volumes, daemon configuration)
- Secrets management (GCP service accounts, .env files via Ansible Vault)
- Instagram Platform Test environment (5 containers)
- Instagram Platform Production environment (4 containers)
- Nginx web server (SSL, mTLS, reverse proxy)
- Loki monitoring stack (log aggregation)
- Automation (cron jobs, systemd timers)
- Security hardening (AppArmor, file immutability, sudo restrictions)

**Environments:**
- **Test**: `check-it-out.pl` (5 containers with local PostgreSQL)
- **Production**: `checkitout.app` (4 containers with external OVH PostgreSQL)
- **Monitoring**: Loki + Alloy for log aggregation

## Quick Start

### Prerequisites

1. **Local machine:**
   - Ansible installed (`pip install ansible`)
   - Ansible collections: `ansible-galaxy install -r requirements.yml`
   - SSH access to target VPS
   - Vault password file: `~/.ansible/vault-pass`

2. **Target VPS:**
   - Ubuntu 24.04 LTS (fresh install)
   - SSH access as ubuntu user with sudo
   - Minimum 50GB disk space
   - Internet connectivity

3. **Secrets:**
   - GCP service account JSONs
   - Database passwords
   - SSH public keys for GitHub Actions
   - Update `inventory/group_vars/{test,prod}/vault.yml`
   - Encrypt: `ansible-vault encrypt inventory/group_vars/test/vault.yml`

### Installation

```bash
# Clone repository
git clone <your-repo>
cd checkItOut-be2/ansible

# Install Ansible and dependencies
pip install ansible
ansible-galaxy install -r requirements.yml

# Prepare vault files (see Secrets section below)
# Edit inventory/group_vars/test/vault.yml
# Edit inventory/group_vars/prod/vault.yml

# Encrypt vault files
ansible-vault encrypt inventory/group_vars/test/vault.yml
ansible-vault encrypt inventory/group_vars/prod/vault.yml

# Create vault password file
echo "your-vault-password" > ~/.ansible/vault-pass
chmod 600 ~/.ansible/vault-pass
```

### Deployment

```bash
# 1. Test syntax
ansible-playbook -i inventory/production.yml playbooks/site.yml --syntax-check

# 2. Dry run (see what would change)
ansible-playbook -i inventory/production.yml playbooks/site.yml --check --diff --vault-password-file ~/.ansible/vault-pass

# 3. Deploy infrastructure
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass

# 4. Start applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# 5. Verify deployment
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

## Project Structure

```
ansible/
├── ansible.cfg                 # Ansible configuration
├── requirements.yml            # Galaxy dependencies
├── inventory/
│   ├── production.yml          # VPS inventory (IP: 192.0.2.10)
│   └── group_vars/
│       ├── all.yml             # Common variables
│       ├── test.yml            # Test environment variables
│       ├── prod.yml            # Production environment variables
│       ├── test/vault.yml      # Test secrets (encrypted)
│       └── prod/vault.yml      # Production secrets (encrypted)
├── roles/
│   ├── 01-base-system/         # Base system setup
│   ├── 02-users-groups/        # User and group creation
│   ├── 03-directories/         # Directory structure
│   ├── 04-docker/              # Docker configuration
│   ├── 05-secrets/             # Secrets deployment
│   ├── 06-scripts/             # Script deployment
│   ├── 07-systemd/             # Systemd services
│   ├── 08-nginx/               # Nginx configuration
│   ├── 09-sudoers/             # Sudo permissions
│   ├── 10-monitoring/          # Loki stack
│   ├── 11-docker-compose/      # Docker Compose files
│   ├── 13-automation/          # Cron and automation
│   └── 14-security/            # Security hardening
├── playbooks/
│   ├── site.yml                # Main deployment playbook
│   ├── startup.yml             # Start applications
│   ├── verify.yml              # Verification tests
│   ├── infra-only.yml          # Infrastructure only
│   └── apps-only.yml           # Applications only
└── molecule/
    └── default/                # Testing framework
```

## Roles

### Critical Infrastructure (Phase 3)

1. **01-base-system** - Ubuntu 24.04 setup, packages, network, firewall, SSH hardening
2. **02-users-groups** - Create deployment users with exact UIDs/GIDs
3. **03-directories** - Create directory structure with correct permissions
4. **04-docker** - Docker daemon, networks (172.20/21/28.0.0/16), volumes

### Security & Configuration (Phase 4)

5. **05-secrets** - Deploy GCP service accounts and .env files (encrypted with Vault)
6. **06-scripts** - Deploy systemd wrappers, entrypoints, admin scripts
7. **07-systemd** - Create systemd service definitions
9. **09-sudoers** - Configure fine-grained sudo permissions (849 lines, NO wildcards)

### Web & Applications (Phase 5)

8. **08-nginx** - Web server, SSL certificates, reverse proxy, mTLS
10. **10-monitoring** - Loki + Alloy log aggregation
11. **11-docker-compose** - Deploy Docker Compose files for test/prod

### Automation & Security (Phase 6)

13. **13-automation** - Cron jobs, systemd timers, logrotate
14. **14-security** - AppArmor, file immutability, security audit

## Playbooks

### site.yml - Complete Deployment

Deploys entire infrastructure from scratch.

```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass
```

**Time:** 30-60 minutes

### startup.yml - Start Applications

Starts all applications after infrastructure deployment.

```bash
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

**Time:** 5-10 minutes

### verify.yml - Verification Tests

Runs comprehensive verification tests.

```bash
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

**Time:** 2-5 minutes

### infra-only.yml - Infrastructure Only

Deploys infrastructure without application configs.

```bash
ansible-playbook -i inventory/production.yml playbooks/infra-only.yml
```

### apps-only.yml - Applications Only

Updates application configs without touching infrastructure.

```bash
ansible-playbook -i inventory/production.yml playbooks/apps-only.yml --vault-password-file ~/.ansible/vault-pass
```

## Secrets Management

### Ansible Vault Setup

1. **Create vault password file:**
```bash
echo "your-strong-vault-password" > ~/.ansible/vault-pass
chmod 600 ~/.ansible/vault-pass
```

2. **Edit vault files:**
```bash
# Edit test secrets
ansible-vault edit inventory/group_vars/test/vault.yml

# Edit production secrets
ansible-vault edit inventory/group_vars/prod/vault.yml
```

3. **Required secrets:**
   - `vault_gcp_service_account_test` - GCP service account JSON (test)
   - `vault_gcp_service_account_prod` - GCP service account JSON (prod)
   - `vault_test_database_password` - Test DB password
   - `vault_prod_database_password` - Production DB password
   - `vault_test_jwt_secret` - Test JWT secret
   - `vault_prod_jwt_secret` - Production JWT secret
   - SSH public keys for GitHub Actions

### Hybrid Secret Strategy

- **Ansible Vault:** Bootstrap secrets (.env templates, GCP service accounts)
- **GCP Secret Manager:** Runtime secrets (fetched by google-secrets-init container)

## Testing

### 1. Syntax Check

```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml --syntax-check
```

### 2. Dry Run

```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml --check --diff --vault-password-file ~/.ansible/vault-pass
```

### 3. Molecule (Docker)

```bash
molecule test
```

See [TESTING.md](TESTING.md) for detailed testing guide.

## Configuration

### VPS Details

Update `inventory/production.yml`:
- IP address: `192.0.2.10`
- Hostname: `vps-example01`
- SSH user: `ubuntu`
- SSH key: `~/.ssh/id_rsa`

### Environment Variables

See `inventory/group_vars/` for all configurable variables:
- `all.yml` - Common variables
- `test.yml` - Test environment
- `prod.yml` - Production environment

## Troubleshooting

### SSH Connection Failed

```bash
# Test SSH manually
ssh -i ~/.ssh/id_rsa ubuntu@192.0.2.10

# Check SSH key
ls -la ~/.ssh/id_rsa

# Check inventory
ansible-inventory -i inventory/production.yml --list
```

### Vault Decryption Failed

```bash
# Verify vault password
ansible-vault view inventory/group_vars/test/vault.yml

# Check vault password file
cat ~/.ansible/vault-pass
```

### Role Failed

```bash
# Run with verbose output
ansible-playbook -i inventory/production.yml playbooks/site.yml -vvv

# Run specific role only
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags base-system

# Start at specific task
ansible-playbook -i inventory/production.yml playbooks/site.yml --start-at-task "task name"
```

### Check Logs

```bash
# View Ansible logs
tail -f ansible.log

# View systemd journal on VPS
ssh ubuntu@192.0.2.10 'sudo journalctl -f'
```

## Production Deployment Checklist

- [ ] Secrets updated in vault files
- [ ] Vault files encrypted
- [ ] Vault password file created
- [ ] SSH key configured for VPS access
- [ ] VPS IP confirmed in inventory
- [ ] Syntax check passed
- [ ] Dry run reviewed
- [ ] VPS snapshot taken (OVH control panel)
- [ ] Console access available (OVH VNC/KVM)
- [ ] Ready to deploy

## Support

- **Molecule Testing:** See `molecule/README.md`
- **Playbooks:** See `playbooks/README.md`

## Architecture

**Infrastructure:**
- Ubuntu 24.04 LTS
- Docker 28.5.1+ with Compose v2
- Nginx (web server + reverse proxy)
- iptables firewall (ports 22, 80, 443 only)

**Applications:**
- Spring Boot backend (Java 21)
- PostgreSQL 16.9 (test: containerized, prod: external OVH)
- Redis with Sentinel HA
- Google Secrets Manager integration

**Monitoring:**
- Loki log aggregation
- Alloy log collection
- journald integration

**Security:**
- SSH hardened (no password auth, strong ciphers)
- AppArmor profiles
- File immutability (chattr +i)
- Fine-grained sudo permissions (NO wildcards)
- Secret files 640 permissions

## License

Internal use - Instagram Platform VPS Migration

## Credits

Automated VPS deployment configuration for Instagram Platform
