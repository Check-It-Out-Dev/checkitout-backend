# Getting Started with Instagram Platform Ansible

**New to this project? Start here.**

This guide helps you deploy Instagram Platform infrastructure using Ansible in the shortest time possible.

---

## 🚀 Fast Track (30 minutes to first deployment)

### Step 1: Prerequisites (5 minutes)

```bash
# Install Ansible
pip install ansible

# Verify installation
ansible --version
# Should show: ansible [core 2.19] or later
```

### Step 2: Setup Project (5 minutes)

```bash
# Navigate to ansible directory
cd ansible/

# Install dependencies
ansible-galaxy install -r requirements.yml

# This installs:
# - community.docker
# - community.general
# - community.crypto
# - community.postgresql
```

### Step 3: Configure Secrets (10 minutes)

```bash
# Create vault password
echo "MyStrongVaultPassword123!" > ~/.ansible/vault-pass
chmod 600 ~/.ansible/vault-pass

# Edit test secrets
ansible-vault create inventory/group_vars/test/vault.yml
# Add GCP service account, database passwords, etc.

# Edit production secrets
ansible-vault create inventory/group_vars/prod/vault.yml
# Add GCP service account, database passwords, etc.
```

**Required secrets:**
- GCP service account JSON (test + prod)
- Database passwords
- JWT secrets
- SSH public keys

See `inventory/group_vars/test/vault.yml` for template.

### Step 4: Test Before Deploying (5 minutes)

```bash
# Check syntax
ansible-playbook playbooks/site.yml --syntax-check

# Dry run (see what would change)
ansible-playbook -i inventory/production.yml playbooks/site.yml \
  --check \
  --diff \
  --vault-password-file ~/.ansible/vault-pass

# Review proposed changes carefully
```

### Step 5: Deploy (5 minutes + 30-45 minute deployment)

```bash
# Deploy complete infrastructure
ansible-playbook -i inventory/production.yml playbooks/site.yml \
  --vault-password-file ~/.ansible/vault-pass

# Wait ~30-45 minutes for completion

# Start applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# Wait ~5-10 minutes for startup

# Verify
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

**Done!** Instagram Platform deployed and running.

---

## 📚 Detailed Walkthrough

### What You Need to Know

**Key Concepts:**
1. **Roles** - Modular components (01-base-system, 02-users-groups, etc.)
2. **Playbooks** - Orchestrate roles (site.yml, startup.yml, verify.yml)
3. **Inventory** - Define target servers (inventory/production.yml)
4. **Variables** - Configuration values (group_vars/)
5. **Vault** - Encrypt secrets (vault.yml files)

**Execution Flow:**
```
site.yml → Runs all 10 roles → Infrastructure deployed
startup.yml → Starts applications → Apps running
verify.yml → Tests everything → Verified healthy
```

---

## 🔐 Secrets Configuration

### Step-by-Step Secrets Setup

**1. Create vault password:**
```bash
echo "your-secure-password" > ~/.ansible/vault-pass
chmod 600 ~/.ansible/vault-pass
```

**2. Edit test secrets:**
```bash
ansible-vault create inventory/group_vars/test/vault.yml
```

Add:
```yaml
---
vault_gcp_service_account_test: |
  {
    "type": "service_account",
    "project_id": "check-it-out-47c50",
    "private_key": "YOUR_ACTUAL_KEY",
    ...
  }

vault_test_database_password: "your-test-db-password"
vault_test_jwt_secret: "your-test-jwt-secret"
vault_test_deploy_ssh_public_key: "ssh-rsa YOUR_KEY"
```

**3. Edit production secrets:**
```bash
ansible-vault create inventory/group_vars/prod/vault.yml
```

Use same structure with production values.

**4. Verify encryption:**
```bash
# File should look encrypted
head -1 inventory/group_vars/test/vault.yml
# Should show: $ANSIBLE_VAULT;1.1;AES256

# Test decryption
ansible-vault view inventory/group_vars/test/vault.yml
# Should show decrypted content
```

---

## 🧪 Testing Before Production

### Level 1: Syntax Check (1 minute)

```bash
ansible-playbook playbooks/site.yml --syntax-check
```

✅ **Must pass** before proceeding

### Level 2: Linting (2 minutes)

```bash
# YAML linting
yamllint .

# Ansible linting
ansible-lint playbooks/site.yml
```

⚠️ Warnings OK, errors should be fixed

### Level 3: Dry Run (5 minutes)

```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml \
  --check \
  --diff \
  --vault-password-file ~/.ansible/vault-pass
```

📋 **Review all proposed changes**

### Level 4: Molecule (Optional, 30 minutes)

```bash
# Install Molecule
pip install molecule "molecule[docker]"

# Run tests
molecule test
```

✅ Verifies roles work in Docker container

---

## 📦 What Gets Deployed

### Infrastructure

- ✅ Ubuntu 24.04 configured
- ✅ 1,595+ packages installed
- ✅ Users and groups (exact UIDs/GIDs)
- ✅ Docker (daemon, networks, volumes)
- ✅ Firewall (iptables)
- ✅ SSH hardened

### Applications

- ✅ Test: 5 containers (gsm-init, postgres, redis, sentinel, app)
- ✅ Prod: 4 containers (gsm-init, redis, sentinel, app)
- ✅ Monitoring: Loki + Alloy

### Configuration

- ✅ Nginx (web server, SSL, reverse proxy)
- ✅ Systemd services (auto-start)
- ✅ Sudo permissions (fine-grained)
- ✅ Secrets (encrypted and secured)
- ✅ Automation (cron, timers)

---

## ❓ Common Questions

### Q: How long does deployment take?

**A:** 30-45 minutes for infrastructure, 5-10 minutes for application startup. Total: ~40-60 minutes.

### Q: Can I test without deploying to production?

**A:** Yes! Use:
1. Molecule: `molecule test` (Docker container)
2. Dry run: `--check` mode
3. Test VPS: Deploy to cheap VPS first (Hetzner €5.83/month)

### Q: What if deployment fails?

**A:**
1. Review error message
2. Check TROUBLESHOOTING.md
3. Restore VPS snapshot (if created)
4. Fix issue and re-run (idempotent, safe to re-run)

### Q: Can I deploy only part of the system?

**A:** Yes! Use:
- `playbooks/infra-only.yml` - Infrastructure only
- `playbooks/apps-only.yml` - Applications only
- `--tags` - Specific roles: `--tags nginx,docker`

### Q: How do I update application configs?

**A:**
```bash
# Update configs only
ansible-playbook -i inventory/production.yml playbooks/apps-only.yml --vault-password-file ~/.ansible/vault-pass

# Restart apps
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

### Q: Is it safe to re-run playbooks?

**A:** Yes! All playbooks are idempotent. Second run produces no changes if nothing changed.

---

## 🎯 First Deployment Checklist

Before deploying:

- [ ] Ansible installed
- [ ] Dependencies installed (`ansible-galaxy install -r requirements.yml`)
- [ ] Vault password created (`~/.ansible/vault-pass`)
- [ ] Test secrets configured (vault.yml)
- [ ] Production secrets configured (vault.yml)
- [ ] Vault files encrypted
- [ ] SSH access to VPS verified
- [ ] Syntax check passed
- [ ] Dry run reviewed
- [ ] VPS snapshot created
- [ ] Console access available

Then deploy:

```bash
make full
```

Or:

```bash
make deploy  # Deploy infrastructure
make start   # Start applications
make verify  # Verify deployment
```

---

## 🆘 Need Help?

### Documentation

1. **QUICK-REFERENCE.md** - Essential commands (start here)
2. **README.md** - Complete overview
3. **TESTING.md** - Testing strategies
4. **DEPLOYMENT-GUIDE.md** - Step-by-step deployment
5. **TROUBLESHOOTING.md** - Common issues
6. **CONTRIBUTING.md** - How to extend

### Specific Issues

- **Can't connect to VPS** → TROUBLESHOOTING.md → "SSH Connection Failures"
- **Vault errors** → TROUBLESHOOTING.md → "Vault Decryption Errors"
- **Docker failures** → TROUBLESHOOTING.md → "Docker Network Creation Fails"
- **Application won't start** → TROUBLESHOOTING.md → "Health Checks Fail"

### Commands

```bash
# View all documentation
make docs

# Run syntax check
make syntax

# Run all tests
make test

# Get help
make help
```

---

## 💡 Pro Tips

### 1. Use Makefile

```bash
make help      # See all commands
make install   # Install everything
make test      # Run all tests
make full      # Deploy + Start + Verify
```

### 2. Start Small

Test individual roles first:
```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags base-system --check
```

### 3. Use Tags

```bash
# Deploy only infrastructure
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags phase3,phase4

# Update only nginx
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags nginx
```

### 4. Monitor Deployment

Open separate terminal:
```bash
ssh ubuntu@192.0.2.10 'sudo journalctl -f'
```

Watch deployment progress in real-time.

### 5. Keep Vault Password Safe

```bash
# Never commit vault password
# It's in .gitignore

# Use secure password manager
# Or encrypt with GPG:
gpg -c ~/.ansible/vault-pass
```

---

## 📊 Expected Output

### Successful Deployment

```
PLAY RECAP *********************************************
vps-example01  : ok=150  changed=120  unreachable=0  failed=0  skipped=10  rescued=0  ignored=0
```

### Successful Startup

```
TASK [Display startup completion summary] *************
ok: [vps-example01] => {
    "msg": [
        "Test environment: HEALTHY",
        "Production environment: HEALTHY",
        "Monitoring stack: Started"
    ]
}
```

### Successful Verification

```
TASK [Display final verification summary] *************
ok: [vps-example01] => {
    "msg": [
        "Network: PASS",
        "Docker: PASS",
        "Services: PASS",
        "Test app: HEALTHY",
        "Prod app: HEALTHY",
        "ALL TESTS PASSED - Ready for production!"
    ]
}
```

---

## 🎉 You're Ready!

If you've completed the checklist above, you're ready to deploy:

```bash
make full
```

This single command will:
1. Deploy complete infrastructure (30-45 minutes)
2. Start all applications (5-10 minutes)
3. Verify everything works (2-5 minutes)

**Total:** ~40-60 minutes from zero to fully deployed VPS.

---

**Good luck with your deployment!**

For detailed information, see:
- **README.md** - Complete documentation
- **DEPLOYMENT-GUIDE.md** - Step-by-step walkthrough
- **TROUBLESHOOTING.md** - If issues arise
