# Instagram Platform VPS Deployment Guide

## Complete Step-by-Step Deployment

This guide walks through deploying Instagram Platform to a new VPS from start to finish.

**Estimated total time:** 2-3 hours (including testing)

---

## Phase 1: Preparation (30 minutes)

### 1.1. Prepare Secrets

```bash
cd ansible/

# Edit test secrets (add GCP service account, passwords, etc.)
ansible-vault create inventory/group_vars/test/vault.yml
# Or if exists: ansible-vault edit inventory/group_vars/test/vault.yml

# Edit production secrets
ansible-vault create inventory/group_vars/prod/vault.yml
# Or if exists: ansible-vault edit inventory/group_vars/prod/vault.yml

# Create vault password file
echo "your-strong-vault-password-here" > ~/.ansible/vault-pass
chmod 600 ~/.ansible/vault-pass
```

**Required secrets:**
- GCP service account JSONs (test + prod)
- Database passwords
- JWT secrets
- SSH public keys for GitHub Actions
- API keys

### 1.2. Verify Prerequisites

```bash
# Ansible installed
ansible --version
# Expected: ansible [core 2.19] or later

# Collections installed
ansible-galaxy install -r requirements.yml

# SSH access to VPS
ssh -i ~/.ssh/id_rsa ubuntu@192.0.2.10
# Should connect successfully

# VPS meets requirements
ssh ubuntu@192.0.2.10 'lsb_release -a | grep 24.04'
ssh ubuntu@192.0.2.10 'df -h | grep -E "^/dev"'
# Should show Ubuntu 24.04 with >50GB free
```

### 1.3. Copy Required Files

```bash
# Copy scripts from existing VPS or local repo to appropriate locations:
# - deployment/test/systemd-wrapper.sh
# - deployment/prod/systemd-wrapper.sh
# - Nginx configs from deployment/config/nginx-refactored/
# - Sudoers files from existing VPS /etc/sudoers.d/

# Or if files already in repo, they'll be copied automatically
```

---

## Phase 2: Testing (30-60 minutes)

### 2.1. Syntax Validation

```bash
cd ansible/

# YAML syntax
yamllint .

# Ansible syntax
ansible-playbook -i inventory/production.yml playbooks/site.yml --syntax-check

# Should complete with no errors
```

### 2.2. Dry Run

```bash
# Check what would change (don't make actual changes)
ansible-playbook -i inventory/production.yml playbooks/site.yml \
  --check \
  --diff \
  --vault-password-file ~/.ansible/vault-pass

# Review all proposed changes carefully
```

### 2.3. Molecule Testing (Optional but Recommended)

```bash
# Install Molecule
pip install molecule "molecule[docker]"

# Run full test
molecule test

# Expected: All tests pass
```

---

## Phase 3: Infrastructure Deployment (30-45 minutes)

### 3.1. Take VPS Snapshot

Before deploying, create snapshot in OVH control panel:
1. Go to OVH Manager
2. Select VPS
3. Click "Snapshot"
4. Create snapshot with name: "pre-ansible-deployment-2025-XX-XX"

**Important:** This allows rollback if something goes wrong.

### 3.2. Deploy Infrastructure

```bash
cd ansible/

# Deploy complete infrastructure
ansible-playbook -i inventory/production.yml playbooks/site.yml \
  --vault-password-file ~/.ansible/vault-pass
```

**What happens:**
1. Base system configuration (packages, network, firewall)
2. User and group creation
3. Directory structure creation
4. Docker installation and configuration
5. Secrets deployment
6. Script deployment
7. Systemd service creation
8. Nginx configuration
9. Sudo permissions configuration
10. Monitoring stack deployment
11. Docker Compose file deployment
12. Automation configuration
13. Security hardening

**Expected duration:** 30-45 minutes

**Monitor progress:** Watch the output for any errors

### 3.3. Verify Infrastructure

```bash
# Run verification playbook (applications not started yet)
ansible-playbook -i inventory/production.yml playbooks/verify.yml

# Should show infrastructure deployed but applications not running
```

---

## Phase 4: Application Startup (10-15 minutes)

### 4.1. Start Applications

```bash
# Start all applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

**What happens:**
1. Starts Loki monitoring stack
2. Starts test environment (5 containers)
3. Starts production environment (4 containers)
4. Waits for health checks

**Expected duration:** 10-15 minutes (database initialization is slow)

### 4.2. Monitor Startup

In a separate terminal, watch logs:

```bash
# Watch test environment logs
ssh ubuntu@192.0.2.10 'sudo journalctl -u instagram-platform-test -f'

# Watch containers
ssh ubuntu@192.0.2.10 'watch docker ps'
```

### 4.3. Verify Applications Started

```bash
# Check health endpoints
ssh ubuntu@192.0.2.10 'curl http://localhost:8082/actuator/health'
# Expected: {"status":"UP"}

ssh ubuntu@192.0.2.10 'curl http://localhost:8083/actuator/health'
# Expected: {"status":"UP"}

# Check all containers running
ssh ubuntu@192.0.2.10 'docker ps --format "{{.Names}}: {{.Status}}"'
```

---

## Phase 5: Final Verification (15-30 minutes)

### 5.1. Run Complete Verification

```bash
# Run verification playbook
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

**All tests must pass:**
- Network connectivity
- Services running
- Docker configuration correct
- Applications healthy
- Security configuration correct

### 5.2. Manual Verification

```bash
# SSH to VPS
ssh ubuntu@192.0.2.10

# Check all services
sudo systemctl status instagram-platform-test
sudo systemctl status instagram-platform-prod
sudo systemctl status nginx
sudo systemctl status docker

# Check containers
docker ps -a

# Test health endpoints
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health

# Check Nginx
curl http://localhost
curl https://localhost -k

# Check firewall
sudo iptables -L -n -v

# Check logs
sudo journalctl -u instagram-platform-test -n 50
```

### 5.3. Test from External

From your local machine:

```bash
# Test HTTP (should redirect to HTTPS)
curl -I http://192.0.2.10

# Once domains point to VPS:
curl https://checkitout.app/api/actuator/health
curl https://check-it-out.pl/api/actuator/health
```

---

## Phase 6: DNS Cutover (Variable Time)

### 6.1. Update DNS Records

**Before cutover:**
1. Ensure ALL verification tests pass
2. Applications healthy for at least 1 hour
3. Logs show no errors

**DNS changes:**
1. Go to your DNS provider
2. Update A records:
   - `checkitout.app` → `192.0.2.10`
   - `check-it-out.pl` → `192.0.2.10`
   - `loki.checkitout.app` → `192.0.2.10`
3. Update AAAA records (IPv6):
   - `checkitout.app` → `2001:db8::10`
   - Same for other domains

**TTL considerations:**
- Low TTL (300 seconds) = faster propagation but more DNS queries
- High TTL (3600 seconds) = slower propagation but fewer queries

### 6.2. Wait for DNS Propagation

```bash
# Check DNS from different locations
nslookup checkitout.app
nslookup check-it-out.pl

# Check from external DNS
nslookup checkitout.app 8.8.8.8

# Wait until all show new IP: 192.0.2.10
```

**Propagation time:** 5 minutes to 48 hours (depends on TTL)

### 6.3. Update GitHub Actions Secrets

Update GitHub repository secrets with new VPS IP:
1. Go to GitHub → Settings → Secrets and variables → Actions
2. Update SSH_HOST secrets:
   - `TEST_SSH_HOST` → `192.0.2.10`
   - `PROD_SSH_HOST` → `192.0.2.10`
   - `ADMIN_SSH_HOST` → `192.0.2.10`

---

## Phase 7: Post-Deployment (30 minutes)

### 7.1. Monitor for 24 Hours

Watch for:
- Application errors
- Container crashes
- High resource usage
- Failed cron jobs

```bash
# Watch logs
ssh ubuntu@192.0.2.10 'sudo journalctl -f'

# Monitor resources
ssh ubuntu@192.0.2.10 'htop'

# Check container health
ssh ubuntu@192.0.2.10 'docker ps'
```

### 7.2. Test All Functionality

- [ ] Users can access checkitout.app
- [ ] Users can access check-it-out.pl
- [ ] API endpoints work
- [ ] Authentication works
- [ ] Database operations work
- [ ] Logs being collected
- [ ] Monitoring accessible

### 7.3. Decommission Old VPS

**Only after new VPS stable for 7+ days:**

1. Backup any remaining data from old VPS
2. Verify nothing points to old IP
3. Cancel old VPS in OVH
4. Update documentation with new IP

---

## Rollback Procedure

If deployment fails or issues arise:

### Option 1: Restore VPS Snapshot

1. Go to OVH control panel
2. Select VPS
3. Restore snapshot: "pre-ansible-deployment-YYYY-MM-DD"
4. Wait for restoration (5-15 minutes)
5. Verify old VPS working
6. Analyze what went wrong

### Option 2: Redeploy Clean

1. Create new VPS (Ubuntu 24.04)
2. Update IP in inventory/production.yml
3. Re-run deployment:

```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

---

## Updating Deployed Infrastructure

### Update Application Configs Only

```bash
# Update secrets, docker-compose, monitoring
ansible-playbook -i inventory/production.yml playbooks/apps-only.yml --vault-password-file ~/.ansible/vault-pass

# Restart applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

### Update Infrastructure Only

```bash
# Update nginx, firewall, etc.
ansible-playbook -i inventory/production.yml playbooks/infra-only.yml
```

### Update Single Role

```bash
# Update only nginx
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags nginx
```

---

## Timeline Example

### Day 1: Preparation and Testing (2-3 hours)

- 09:00 - Prepare secrets, verify prerequisites
- 09:30 - Run syntax checks and dry run
- 10:00 - Run Molecule tests
- 10:30 - Deploy to cheap test VPS (optional)
- 11:30 - Review test results

### Day 2: Production Deployment (2-3 hours)

- 14:00 - Take VPS snapshot
- 14:10 - Deploy infrastructure (site.yml)
- 14:45 - Infrastructure complete
- 14:45 - Start applications (startup.yml)
- 15:00 - Applications healthy
- 15:00 - Run verification (verify.yml)
- 15:15 - All tests pass
- 15:30 - Monitor for issues

### Day 3: DNS Cutover (Variable)

- Update DNS records
- Wait for propagation
- Update GitHub Actions
- Monitor new VPS
- Decommission old VPS (after 7 days)

---

## Success Criteria

Deployment is successful when:

- [ ] All playbooks complete without errors
- [ ] All services running (docker, nginx, instagram-platform-test/prod)
- [ ] All containers healthy
- [ ] Health endpoints return HTTP 200
- [ ] No errors in logs
- [ ] Firewall rules correct (only ports 22, 80, 443 public)
- [ ] Security audit passes
- [ ] Applications accessible from internet
- [ ] Monitoring collecting logs
- [ ] Automation tasks scheduled

---

## Quick Reference

```bash
# Full deployment
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass

# Start apps
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# Verify
ansible-playbook -i inventory/production.yml playbooks/verify.yml

# Update apps only
ansible-playbook -i inventory/production.yml playbooks/apps-only.yml --vault-password-file ~/.ansible/vault-pass

# Dry run
ansible-playbook -i inventory/production.yml playbooks/site.yml --check --diff --vault-password-file ~/.ansible/vault-pass
```
