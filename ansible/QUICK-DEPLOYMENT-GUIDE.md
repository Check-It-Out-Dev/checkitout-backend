# Quick Deployment Guide - November 2025

**Use this guide for fast VPS deployment with all lessons learned applied**

---

## Prerequisites (5 minutes)

```bash
# In WSL
export ANSIBLE_CONFIG=/path/to/checkitout-backend/ansible/ansible.cfg
cd /path/to/checkitout-backend/ansible

# Verify connection
ansible -i inventory/production.yml instagram_platform -m ping
```

✅ Create **VPS snapshot** in OVH before starting!

---

## Full Deployment (Copy & Paste)

```bash
# === STEP 1: Deploy Infrastructure (40-50 min) ===
ansible-playbook -i inventory/production.yml playbooks/site.yml

# === STEP 2: Optional - Set up Zsh (5 min) ===
ansible-playbook -i inventory/production.yml playbooks/zsh-setup.yml

# === STEP 3: SSH Hardening (5 min) - LAST! ===
ansible-playbook -i inventory/production.yml playbooks/ssh-hardening.yml

# === STEP 4: Start Applications (10 min) ===
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# === STEP 5: Verify (5 min) ===
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

**Total Time**: ~65-75 minutes

---

## What Gets Deployed

✅ Complete infrastructure mirroring old VPS
✅ Users, groups, directories (exact UIDs/GIDs)
✅ Docker with networks and volumes
✅ GCP service accounts
✅ Nginx with self-signed SSL
✅ Systemd services
✅ Sudoers (fine-grained permissions)
✅ Firewall (ports 22, 80, 443)
✅ Monitoring stack (Loki + Alloy)
✅ Automation (cron, logrotate)
✅ Security hardening

**Network**: DHCP (OVH-managed) ✅
**SSH**: Hardened (key-only auth) ✅
**.env files**: Skipped (GitHub Actions manages) ✅

---

## Run Specific Parts Only

```bash
# Secrets only
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags secrets

# Nginx only
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags nginx

# Monitoring only
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags monitoring

# Docker Compose only
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags docker-compose

# Final hardening (firewall)
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags final-hardening

# Skip completed phases
ansible-playbook -i inventory/production.yml playbooks/site.yml --skip-tags phase3
```

---

## Key Differences from Original Plan

| Original | Final | Reason |
|----------|-------|--------|
| Static netplan config | **DHCP** | Static /32 broke connectivity |
| Network in Phase 3 | **Skipped** | OVH DHCP works perfectly |
| SSH hardening in Phase 3 | **Separate playbook** | Could lock out mid-deployment |
| Ansible Vault encryption | **Plain YAML** | Dummy values safe in Git |
| .env via Ansible | **Skip** | GitHub Actions handles |
| Files in role templates/ | **Copy from deployment/** | Actual files, not templates |

---

## Critical Safety Features

1. ✅ **VPS Snapshot**: Restore if anything breaks
2. ✅ **OVH Console Access**: Recover if SSH fails
3. ✅ **DHCP Network**: No network changes = no connectivity issues
4. ✅ **Deployment Order**: Infrastructure first, security last
5. ✅ **Validation**: sshd -t, visudo -c before applying
6. ✅ **Confirmation Messages**: Know what succeeded/failed at each step

---

## Recovery Procedures

**If deployment fails:**
```bash
# Resume from where it failed
ansible-playbook -i inventory/production.yml playbooks/site.yml --start-at-task="TaskName"

# Or skip completed phases
ansible-playbook -i inventory/production.yml playbooks/site.yml --skip-tags phase3,phase4
```

**If SSH breaks:**
1. Use OVH VNC/KVM console
2. Restore from snapshot OR
3. Fix `/etc/ssh/sshd_config` and `systemctl reload ssh`

**If network breaks:**
1. Use OVH console
2. Restore from snapshot (fastest)

---

## Verification Checklist

After deployment, verify:

```bash
# SSH works
ssh ubuntu@192.0.2.10  ✅

# Services running
sudo systemctl status instagram-platform-test  ✅
sudo systemctl status instagram-platform-prod  ✅

# Docker containers
sudo docker ps  ✅

# Firewall rules
sudo iptables -L -n -v  ✅

# Passwordless sudo works
sudo whoami  ✅ (or asks for password if disabled)

# Health endpoints (after make start)
curl localhost:8082/actuator/health  ✅
curl localhost:8083/actuator/health  ✅
```

---

## Post-Deployment Tasks

**When DNS points to new VPS:**

```bash
# Generate real SSL certificates
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags nginx-ssl
```

**Optional hardening:**

```bash
# Disable ubuntu passwordless sudo
ansible-playbook -i inventory/production.yml playbooks/disable-ubuntu-passwordless-sudo.yml
```

---

## Documentation

**Read these in order:**

1. **LESSONS-LEARNED.md** - What went wrong and how we fixed it
2. **DEPLOYMENT-WORKFLOW-UPDATED.md** - Detailed step-by-step process
3. **DEPLOYMENT-COMPLETE-SUMMARY.md** - This file
4. **TROUBLESHOOTING.md** - Common issues
5. **README.md** - Project overview

---

**VPS is production-ready! 🚀**
