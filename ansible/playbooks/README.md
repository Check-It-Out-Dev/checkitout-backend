# Ansible Playbooks

## Available Playbooks

### 1. site.yml - Complete Deployment

Deploys the complete Instagram Platform infrastructure from scratch.

**What it does:**
- Configures base system (Ubuntu 24.04)
- Creates users, groups, directories
- Installs and configures Docker
- Deploys secrets (via Ansible Vault)
- Deploys all scripts
- Configures systemd services
- Configures Nginx (web server, SSL)
- Configures sudo permissions
- Deploys monitoring stack (Loki)
- Deploys Docker Compose files
- Configures automation (cron, timers)
- Applies security hardening

**What it does NOT do:**
- Start applications (use startup.yml)

**Usage:**
```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass
```

**Estimated time:** 30-60 minutes

---

### 2. startup.yml - Start Applications

Starts all Instagram Platform applications after infrastructure deployment.

**What it does:**
- Starts Loki monitoring stack
- Starts test environment (5 containers)
- Starts production environment (4 containers)
- Waits for health checks to pass
- Verifies containers are healthy

**Prerequisites:**
- site.yml completed successfully

**Usage:**
```bash
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

**Estimated time:** 5-10 minutes

---

### 3. verify.yml - Verification Checklist

Runs comprehensive verification tests on the deployed infrastructure.

**What it tests:**
- Network connectivity
- Docker configuration
- Services status
- Application health
- Security configuration
- Firewall rules
- File permissions

**Usage:**
```bash
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

**Estimated time:** 2-5 minutes

---

### 4. infra-only.yml - Infrastructure Only

Deploys only infrastructure without application configurations.

**Useful for:**
- Preparing VPS without deploying apps
- Testing infrastructure deployment separately
- Incremental deployments

**Usage:**
```bash
ansible-playbook -i inventory/production.yml playbooks/infra-only.yml
```

---

### 5. apps-only.yml - Applications Only

Deploys only application configurations (secrets, monitoring, compose files).

**Useful for:**
- Updating application configs without touching infrastructure
- Deploying new application versions
- Updating secrets

**Prerequisites:**
- Infrastructure already deployed (infra-only.yml or site.yml)

**Usage:**
```bash
ansible-playbook -i inventory/production.yml playbooks/apps-only.yml --vault-password-file ~/.ansible/vault-pass
```

---

## Typical Deployment Flow

### First-Time Deployment

```bash
# 1. Deploy complete infrastructure
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass

# 2. Start applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# 3. Verify everything
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

### Updating Application Configs

```bash
# 1. Update application configs only
ansible-playbook -i inventory/production.yml playbooks/apps-only.yml --vault-password-file ~/.ansible/vault-pass

# 2. Restart applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

---

## Dry Run (Check Mode)

Test what would change without actually making changes:

```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml --check --diff
```

---

## Run Specific Roles Only

Use tags to run only specific roles:

```bash
# Deploy only secrets
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags secrets --vault-password-file ~/.ansible/vault-pass

# Configure only nginx
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags nginx

# Skip security hardening
ansible-playbook -i inventory/production.yml playbooks/site.yml --skip-tags security
```

---

## Common Options

- `--check` - Dry run (don't make changes)
- `--diff` - Show differences
- `--tags TAG` - Run only tasks with TAG
- `--skip-tags TAG` - Skip tasks with TAG
- `--vault-password-file FILE` - Vault password file
- `--ask-vault-pass` - Prompt for vault password
- `-v` / `-vv` / `-vvv` - Increase verbosity
- `--start-at-task "TASK NAME"` - Start at specific task
