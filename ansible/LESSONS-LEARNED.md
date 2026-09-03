# Lessons Learned - Instagram Platform VPS Deployment

**Project**: Instagram Platform Ansible Automation
**Date**: November 2025
**Purpose**: Document lessons learned during VPS formation to avoid future issues

---

## Critical Lessons Learned

### 1. **Network Configuration - Use DHCP, Not Static**

**Issue**: Applying static netplan configuration broke SSH connectivity completely.

**Root Cause**:
- Template used `/32` IPv4 prefix with `on-link: true` workaround
- When `netplan apply` runs, network briefly goes down
- If gateway ARP fails to resolve quickly, routing table becomes incomplete
- SSH connection times out permanently

**Solution**:
- ✅ **Use DHCP** (like old VPS did)
- ✅ Let OVH manage IP configuration automatically
- ✅ Don't deploy netplan configuration at all

**Lesson**: VPS network is pre-configured by hosting provider. Don't change it unless absolutely necessary.

**Code Change**:
```yaml
# ansible/roles/15-final-hardening/defaults/main.yml
apply_network_hardening: false  # DISABLED - use DHCP
```

---

### 2. **Deployment Order - Risky Tasks LAST**

**Issue**: Network/Firewall/SSH changes early in deployment could lock you out mid-deployment.

**Original Plan**:
- Phase 3: Base system (network, firewall, SSH hardening)
- Problem: If these fail, rest of deployment can't complete

**Revised Plan**:
- Phases 1-6: All infrastructure and applications
- Phase 7: Final hardening (network, firewall only)
- Separate playbook: SSH hardening (very last)

**Solution**:
- ✅ Created role `15-final-hardening` for risky changes
- ✅ Disabled risky tasks in `01-base-system`
- ✅ SSH hardening in separate playbook after everything else works

**Lesson**: Deploy critical infrastructure first, apply security restrictions last.

---

### 3. **Windows Line Endings Break Linux**

**Issue**: Files copied from Windows to ansible have `\r\n` (CRLF) endings, causing:
- Sudoers validation failures
- Script execution errors (`/bin/bash^M: bad interpreter`)
- Template rendering issues

**Solution**:
```bash
# Convert all files
dos2unix ansible/roles/09-sudoers/files/*
dos2unix deployment/loki/setup-certificates.sh
dos2unix ansible/roles/15-final-hardening/files/*
```

**Lesson**: Always convert Windows files to Unix format before deployment.

**Prevention**: Add to ansible tasks:
```yaml
- name: Fix line endings
  shell: "dos2unix {{ file_path }} || sed -i 's/\\r$//' {{ file_path }}"
```

---

### 4. **Ansible Vault Not Needed for Dummy Secrets**

**Issue**: Makefile required `--vault-password-file` but we're using dummy values in Git.

**Solution**:
- ✅ Keep vault.yml files as **plain YAML** (not encrypted)
- ✅ Real secrets in **local vault.yml** (never commit)
- ✅ Dummy values in **Git**
- ✅ Removed `--vault-password-file` from Makefile

**Lesson**: Ansible Vault encryption only needed if committing real secrets (we don't).

---

### 5. **Group Variables Must Match Inventory Groups**

**Issue**: Variables in `group_vars/test/` and `group_vars/prod/` weren't loading.

**Root Cause**: No groups named "test" or "prod" in inventory - group is `instagram_platform`.

**Solution**:
```
ansible/inventory/group_vars/
├── instagram_platform/      ← Variables loaded from here
│   ├── vault-test.yml
│   └── vault-prod.yml
├── test/                     ← NOT loaded (no group named "test")
└── prod/                     ← NOT loaded (no group named "prod")
```

**Lesson**: Group variable directories MUST match inventory group names exactly.

---

### 6. **Path References Need Two Levels Up**

**Issue**: Paths like `{{ playbook_dir }}/../deployment` didn't work.

**Root Cause**: playbook_dir points to `ansible/playbooks/`, need to go up TWO levels to reach project root.

**Solution**:
```yaml
# Wrong:
local_scripts_path: "{{ playbook_dir }}/../deployment"  # Goes to ansible/deployment (doesn't exist)

# Correct:
local_scripts_path: "{{ playbook_dir }}/../../deployment"  # Goes to checkitout-backend/deployment
```

**Lesson**: From `ansible/playbooks/` directory, use `../../` to reach project root.

---

### 7. **Docker Go Templates Need Escaping in Ansible**

**Issue**: Docker commands with Go templates like `{{.Name}}` broke Ansible Jinja2 parsing.

**Solution**:
```yaml
# Use !unsafe tag:
command: !unsafe "docker inspect --format '{{.Mountpoint}}' container"

# Or use {% raw %}{% endraw %} in templates:
"tag": "docker/{% raw %}{{.Name}}{% endraw %}"
```

**Lesson**: Escape Docker/Prometheus/Go templates to prevent Jinja2 parsing.

---

### 8. **SSH Service Name is 'ssh' Not 'sshd' on Ubuntu**

**Issue**: `systemctl reload sshd` fails on Ubuntu.

**Solution**:
```yaml
# Wrong:
name: sshd  # RedHat/CentOS naming

# Correct:
name: ssh   # Debian/Ubuntu naming
```

**Lesson**: Service names differ between distributions. Ubuntu uses `ssh`.

---

### 9. **.env Files Managed by GitHub Actions, Not Ansible**

**Issue**: Ansible tried to deploy .env files but was missing variables (spring_profile, etc.).

**Solution**:
- ✅ **GCP service accounts**: Deployed by Ansible (bootstrap secrets)
- ❌ **.env files**: Skip in Ansible (GitHub Actions handles these)
- ✅ Set `deploy_env_files: false`

**Lesson**: Separate bootstrap secrets (Ansible) from runtime configuration (CI/CD pipeline).

---

### 10. **Localhost Tasks Need `become: false`**

**Issue**: Tasks using `delegate_to: localhost` tried to use sudo on local Windows machine.

**Solution**:
```yaml
- name: Find files locally
  ansible.builtin.find:
    paths: "{{ some_path }}"
  delegate_to: localhost
  become: false  # Don't use sudo on local machine
```

**Lesson**: Always add `become: false` to localhost-delegated tasks.

---

### 11. **Sudoers Files Need Exact Naming**

**Issue**: Files in `deployment/config/sudoers/` had `90-` prefix but ansible expected no prefix.

**Solution**:
- Files on VPS: `/etc/sudoers.d/instagram-test-deploy` (no prefix)
- Ansible role files: `roles/09-sudoers/files/instagram-test-deploy` (no prefix)
- Deployment folder: Can have prefix for organization, remove when copying to ansible

**Lesson**: Ansible role files should match final deployed filenames.

---

### 12. **Docker daemon.json Not Needed**

**Issue**: Template tried to deploy daemon.json but old VPS didn't have one.

**Solution**: Set `deploy_daemon_json: false` - Docker works fine with defaults.

**Lesson**: Don't deploy configuration files that don't exist on working source system.

---

### 13. **Firewall Must Allow SSH BEFORE Applying**

**Critical**: Firewall script correctly puts SSH (port 22) rule FIRST:

```bash
# CRITICAL: Allow SSH (port 22) from anywhere - MUST BE FIRST
iptables -A INPUT -p tcp --dport 22 -m state --state NEW -j ACCEPT
```

**Lesson**: Always ensure SSH access before blocking other ports.

---

### 14. **Check Mode Limitations with APT Repositories**

**Issue**: `--check` (dry-run) mode fails at Docker installation because repository cache isn't actually updated.

**Solution**: This is normal behavior. Check mode validates syntax and logic, but package availability requires actual execution.

**Lesson**: `--check` failures at package installation are expected. Run actual deployment to complete.

---

## Deployment Order (Final)

**Correct deployment order based on lessons:**

1. **Phase 3**: Base system (packages only - no network/firewall/SSH changes)
2. **Phase 4**: Users, directories, secrets, scripts, systemd, sudoers
3. **Phase 5**: Nginx (self-signed SSL placeholders), monitoring, docker-compose
4. **Phase 6**: Automation, security (AppArmor, immutability)
5. **Phase 7**: Final hardening (firewall only - no network!)
6. **Separate**: Zsh setup (optional, before SSH hardening)
7. **Separate**: SSH hardening (very last, when everything else works)

---

## Quick Reference - Correct Paths

```yaml
# From playbooks/ directory to project root:
"{{ playbook_dir }}/../../deployment"
"{{ playbook_dir }}/../../scripts"

# From roles/ to find templates:
Use role's own templates/ directory, not ../other-role/templates/

# Group variables:
Must be in group_vars/instagram_platform/ (matches inventory group name)
```

---

## Recovery Procedures

**If Network Breaks**:
1. Use OVH VNC/KVM console
2. Remove `/etc/netplan/01-netcfg.yaml`
3. Run `netplan apply` (reverts to DHCP)

**If SSH Breaks**:
1. Use OVH console
2. Check `/etc/ssh/sshd_config`
3. Run `sshd -t` to test
4. Restore from backup if needed

**If Sudo Breaks**:
1. Use OVH console (boots to root)
2. Fix `/etc/sudoers.d/` files
3. Always validate with `visudo -c` first

---

## Best Practices Established

1. ✅ Always create VPS snapshot before final hardening
2. ✅ Keep OVH console access ready during deployment
3. ✅ Test SSH in new terminal before closing current session
4. ✅ Use `--skip-tags` to resume from specific phase
5. ✅ Convert Windows files to Unix line endings
6. ✅ Verify files actually deployed (add confirmation tasks)
7. ✅ Use DHCP for network unless static IP absolutely required
8. ✅ Apply security restrictions last, not first
9. ✅ Keep deployment users with NOPASSWD for CI/CD automation
10. ✅ Mirror working source configuration exactly

---

## Future Improvements

- [ ] Add pre-flight checks (verify files exist before starting)
- [ ] Add rollback mechanism for each phase
- [ ] Create idempotency tests (safe to re-run)
- [ ] Add more detailed logging/confirmation at each step
- [ ] Consider using Ansible Molecule for testing
- [ ] Document all variable requirements
- [ ] Create troubleshooting decision tree

---

**This document should be reviewed before each VPS deployment!**
