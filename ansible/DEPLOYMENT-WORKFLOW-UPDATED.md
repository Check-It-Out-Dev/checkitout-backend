# Complete Deployment Workflow - UPDATED November 2025

**Based on actual deployment experience with lessons learned**

**Total Time:** ~50-60 minutes (automated) + manual steps
**Difficulty:** Medium
**Prerequisites**: Fresh Ubuntu 24.04 VPS with SSH access

---

## Pre-Deployment Checklist

- [ ] **VPS snapshot created** (critical for rollback)
- [ ] **OVH console access** verified (VNC/KVM)
- [ ] **SSH key copied to WSL**: `~/.ssh/id_ed25519`
- [ ] **Ansible installed in WSL**: `pip install ansible`
- [ ] **Dependencies installed**: `ansible-galaxy install -r requirements.yml`
- [ ] **ANSIBLE_CONFIG exported**: `export ANSIBLE_CONFIG=/path/to/checkitout-backend/ansible/ansible.cfg`
- [ ] **Real secrets in vault files**: `inventory/group_vars/instagram_platform/vault-*.yml`
- [ ] **Vault files NOT committed to Git**

---

## Phase 1: Prepare Local Environment (5-10 minutes)

### Step 1.1: Set Up WSL

```bash
# In PowerShell (if WSL not installed)
wsl --install -d Ubuntu-24.04

# Launch WSL
wsl
```

### Step 1.2: Install Ansible

```bash
# In WSL
pip3 install ansible
ansible --version
```

### Step 1.3: Configure Environment

```bash
# Copy SSH key from Windows to WSL
mkdir -p ~/.ssh
cp /mnt/c/Users/<you>/.ssh/id_ed25519 ~/.ssh/
chmod 600 ~/.ssh/id_ed25519

# Set Ansible config
export ANSIBLE_CONFIG=/path/to/checkitout-backend/ansible/ansible.cfg

# Make it permanent
echo 'export ANSIBLE_CONFIG=/path/to/checkitout-backend/ansible/ansible.cfg' >> ~/.bashrc

# Navigate to project
cd /path/to/checkitout-backend/ansible

# Install dependencies
ansible-galaxy install -r requirements.yml
```

### Step 1.4: Update Vault Files with Real Secrets

Edit these files with real GCP service accounts and SSH keys:
- `inventory/group_vars/instagram_platform/vault-test.yml`
- `inventory/group_vars/instagram_platform/vault-prod.yml`

**⚠️ Important**: Do NOT commit these files to Git after editing!

---

## Phase 2: Validate Configuration (5 minutes)

### Step 2.1: Test Connection

```bash
# Test SSH
ssh ubuntu@192.0.2.10

# Test Ansible connection
ansible -i inventory/production.yml instagram_platform -m ping
# Should show: "pong"
```

### Step 2.2: Syntax Check

```bash
# Check all playbooks
make syntax

# Should show:
# playbook: playbooks/site.yml
# playbook: playbooks/startup.yml
# playbook: playbooks/verify.yml
```

### Step 2.3: Create VPS Snapshot

**CRITICAL**: Create snapshot in OVH control panel BEFORE deployment!

---

## Phase 3: Main Deployment (40-50 minutes)

### Step 3.1: Deploy Infrastructure

```bash
# Run complete deployment (skipping risky early tasks)
ansible-playbook -i inventory/production.yml playbooks/site.yml
```

**What gets deployed:**
- ✅ Base system packages (1,500+ packages)
- ✅ Users and groups (exact UIDs/GIDs from old VPS)
- ✅ Directory structure
- ✅ Docker (networks, volumes, daemon)
- ✅ GCP service accounts (test, prod, loki)
- ✅ Scripts (systemd wrappers, entrypoints)
- ✅ Systemd services
- ✅ Sudoers (fine-grained permissions)
- ✅ Nginx (configs + self-signed SSL placeholders)
- ✅ Monitoring (Loki + Alloy configs)
- ✅ Docker Compose files
- ✅ Automation (cron, logrotate)
- ✅ Security (AppArmor, immutability)
- ✅ Firewall (iptables rules)

**What does NOT get deployed:**
- ❌ Network configuration (uses DHCP from OVH)
- ❌ SSH hardening (separate playbook)
- ❌ SSH keys (separate playbook)
- ❌ .env files (managed by GitHub Actions)
- ❌ Real SSL certificates (DNS not pointed yet)

**Duration**: ~40-50 minutes

---

## Phase 4: Zsh Setup (Optional, 5 minutes)

```bash
# Set up zsh for ubuntu user (BEFORE SSH hardening)
ansible-playbook -i inventory/production.yml playbooks/zsh-setup.yml
```

**Installs:**
- Zsh shell
- Oh-My-Zsh framework
- Powerlevel10k theme
- Essential plugins (autosuggestions, syntax-highlighting, etc.)
- Sets zsh as default shell

**Duration**: ~5 minutes

---

## Phase 5: SSH Hardening (5 minutes)

**⚠️ CRITICAL**: Run this LAST, after everything else works!

### Step 5.1: Verify SSH Keys Work

```bash
# Test SSH with your key
ssh -i ~/.ssh/id_ed25519 ubuntu@192.0.2.10

# If this works, proceed
```

### Step 5.2: Run SSH Hardening

```bash
# Deploy SSH keys and harden SSH daemon
ansible-playbook -i inventory/production.yml playbooks/ssh-hardening.yml
```

**This will:**
1. Deploy SSH public keys to all users (from vault files)
2. Deploy main sshd_config (exact copy from old VPS)
3. Test with `sshd -t`
4. Reload SSH service
5. Verify connection still works

**After this:**
- ✅ Password login: DISABLED
- ✅ Only SSH key authentication
- ✅ Exact SSH config from old VPS

**Duration**: ~5 minutes

---

## Phase 6: Optional - Disable Ubuntu Passwordless Sudo

**Only if needed:**

```bash
# Set ubuntu password first
ssh ubuntu@192.0.2.10
sudo passwd ubuntu
# Enter new password twice

# Then disable passwordless sudo
ansible-playbook -i inventory/production.yml playbooks/disable-ubuntu-passwordless-sudo.yml
```

---

## Phase 7: Start Applications (10-15 minutes)

```bash
# Start all services
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# Or
make start
```

**Starts:**
- Loki monitoring stack
- Test environment (5 containers)
- Production environment (4 containers)

**Duration**: ~10-15 minutes

---

## Phase 8: Verify Deployment (5 minutes)

```bash
# Run verification tests
ansible-playbook -i inventory/production.yml playbooks/verify.yml

# Or
make verify
```

**Verifies:**
- Network connectivity
- Docker (networks, volumes, containers)
- Services (systemd)
- Applications (health endpoints)
- Security (firewall, SSH)

**Duration**: ~5 minutes

---

## Phase 9: Post-Deployment Manual Steps

### Step 9.1: Verify Everything Works

```bash
# SSH still works
ssh ubuntu@192.0.2.10

# Check services
sudo systemctl status instagram-platform-test
sudo systemctl status instagram-platform-prod

# Check containers
sudo docker ps

# Check firewall
sudo iptables -L -n -v
```

### Step 9.2: Configure Real SSL Certificates (After DNS Migration)

**When DNS points to new VPS:**

```bash
# Generate real Let's Encrypt certificates
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags nginx-ssl
```

---

## Deployment Timeline Summary

| Phase | Duration | What | Can Skip? |
|-------|----------|------|-----------|
| 1. Prepare WSL | 5-10 min | Ansible setup | No |
| 2. Validate | 5 min | Test connections | No |
| 3. Main Deploy | 40-50 min | Infrastructure | No |
| 4. Zsh Setup | 5 min | Shell config | Yes |
| 5. SSH Hardening | 5 min | SSH security | **Run Last** |
| 6. Disable Passwordless Sudo | 2 min | Ubuntu sudo | Yes |
| 7. Start Apps | 10-15 min | Services | No |
| 8. Verify | 5 min | Tests | No |
| **Total** | **77-97 min** | **Complete** | |

---

## Key Commands Reference

```bash
# Export config (every WSL session)
export ANSIBLE_CONFIG=/path/to/checkitout-backend/ansible/ansible.cfg

# Navigate to project
cd /path/to/checkitout-backend/ansible

# Test connection
ansible -i inventory/production.yml instagram_platform -m ping

# Check syntax
make syntax

# Deploy infrastructure
ansible-playbook -i inventory/production.yml playbooks/site.yml

# Set up zsh (optional)
ansible-playbook -i inventory/production.yml playbooks/zsh-setup.yml

# Harden SSH (last!)
ansible-playbook -i inventory/production.yml playbooks/ssh-hardening.yml

# Start applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# Verify
ansible-playbook -i inventory/production.yml playbooks/verify.yml

# Disable ubuntu passwordless sudo (optional)
ansible-playbook -i inventory/production.yml playbooks/disable-ubuntu-passwordless-sudo.yml
```

---

## Troubleshooting Quick Reference

**Connection lost during deployment:**
- Restore from snapshot (OVH control panel)
- Use OVH console (VNC/KVM)

**Ansible errors:**
- Add `-vvv` for verbose output
- Use `--start-at-task="task name"` to resume
- Use `--tags tagname` to run specific roles

**Service won't start:**
- SSH to VPS: `ssh ubuntu@192.0.2.10`
- Check logs: `sudo journalctl -u service-name -f`
- Check containers: `sudo docker ps -a`

---

## Success Criteria

✅ **Deployment successful when:**
- All phases complete without fatal errors
- SSH access works with key
- Services running: `systemctl status instagram-platform-*`
- Containers healthy: `docker ps` shows all running
- Health endpoints return 200: `curl localhost:8082/actuator/health`
- Firewall rules applied: `sudo iptables -L -n -v`

---

**See LESSONS-LEARNED.md for detailed explanations of all changes!**
