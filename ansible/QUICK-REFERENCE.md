# Quick Reference - Instagram Platform Ansible

## Essential Commands

### Deployment

```bash
# Full deployment
make deploy

# Or manually:
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass

# Start applications
make start

# Verify deployment
make verify
```

### Testing

```bash
# All tests
make test

# Syntax only
make syntax

# Lint only
make lint

# Dry run
make dry-run

# Molecule
make molecule
```

### Vault Operations

```bash
# Create/edit test vault
ansible-vault edit inventory/group_vars/test/vault.yml

# Create/edit prod vault
ansible-vault edit inventory/group_vars/prod/vault.yml

# View vault (decrypt temporarily)
ansible-vault view inventory/group_vars/test/vault.yml

# Encrypt file
ansible-vault encrypt inventory/group_vars/test/vault.yml

# Decrypt file
ansible-vault decrypt inventory/group_vars/test/vault.yml

# Change vault password
ansible-vault rekey inventory/group_vars/test/vault.yml
```

### Playbooks

```bash
# Complete deployment
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass

# Infrastructure only
ansible-playbook -i inventory/production.yml playbooks/infra-only.yml

# Applications only
ansible-playbook -i inventory/production.yml playbooks/apps-only.yml --vault-password-file ~/.ansible/vault-pass

# Start applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# Verify deployment
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

### Run Specific Roles

```bash
# Run only base system
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags base-system

# Run only secrets
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags secrets --vault-password-file ~/.ansible/vault-pass

# Run only nginx
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags nginx

# Skip security hardening
ansible-playbook -i inventory/production.yml playbooks/site.yml --skip-tags security
```

### Debugging

```bash
# Verbose output
ansible-playbook -i inventory/production.yml playbooks/site.yml -vvv

# Step through tasks
ansible-playbook -i inventory/production.yml playbooks/site.yml --step

# Start at specific task
ansible-playbook -i inventory/production.yml playbooks/site.yml --start-at-task "Deploy secrets"

# Check mode (dry run)
ansible-playbook -i inventory/production.yml playbooks/site.yml --check --diff
```

---

## Important Paths

### On Control Machine (Local)

```
ansible/
├── inventory/production.yml          # VPS IP and connection details
├── inventory/group_vars/all.yml      # Common variables
├── inventory/group_vars/test.yml     # Test environment vars
├── inventory/group_vars/prod.yml     # Production vars
├── inventory/group_vars/test/vault.yml  # Test secrets (ENCRYPTED)
├── inventory/group_vars/prod/vault.yml  # Prod secrets (ENCRYPTED)
├── playbooks/site.yml                # Main deployment
└── roles/                            # 10 roles
```

### On VPS (Remote)

```
/opt/instagram-platform/
├── test/
│   ├── deployment/
│   │   ├── .env                      # Test environment variables
│   │   ├── docker-compose-test.yml   # Test stack definition
│   │   └── systemd-wrapper.sh        # Service manager
│   └── runtime/logs/                 # Application logs
├── prod/
│   ├── deployment/
│   │   ├── .env                      # Production environment variables
│   │   ├── docker-compose-prod.yml   # Production stack
│   │   └── systemd-wrapper.sh        # Service manager
│   └── runtime/logs/                 # Application logs
└── loki/
    └── docker-compose.yml            # Monitoring stack

/etc/instagram-platform/
├── test/secrets/
│   └── service-account.json          # GCP credentials (640)
└── prod/secrets/
    └── service-account.json          # GCP credentials (640)

/etc/systemd/system/
├── instagram-platform-test.service   # Test systemd service
└── instagram-platform-prod.service   # Prod systemd service

/etc/nginx/
├── nginx.conf                        # Main nginx config
├── conf.d/                           # Modular configs (9 files)
├── sites-available/                  # Site configs (4 files)
└── sites-enabled/                    # Enabled sites (symlinks)

/etc/sudoers.d/
├── instagram-test-deploy             # Test sudo permissions (301 lines)
├── instagram-prod-deploy             # Prod sudo permissions (162 lines)
└── instagram-scripts-admin           # Admin sudo permissions (389 lines)
```

---

## Critical Values

### Network

- VPS IP: `192.0.2.10`
- VPS IPv6: `2001:41d0:601:1100::65cd`
- Gateway: `51.38.135.1`
- Hostname: `vps-69c5792e`

### Docker Networks

- Test: `172.20.0.0/16` (gateway: 172.20.0.1)
- Production: `172.21.0.0/16` (gateway: 172.21.0.1)
- Monitoring: `172.28.0.0/16` (gateway: 172.28.0.1)

### User IDs (MUST be exact)

- instagram-test-deploy: UID `1002`, GID `1003`
- instagram-prod-deploy: UID `1006`, GID `1006`
- instagram-scripts-admin: UID `997`, GID `985`
- loki: UID `995`, GID `984`

### Ports

- SSH: `22` (public)
- HTTP: `80` (public)
- HTTPS: `443` (public)
- Test app: `8082` (localhost only)
- Prod app: `8083` (localhost only)
- Loki: `3100` (localhost only)

### Permissions

- Secrets: `640` (rw-r-----)
- Scripts: `755` (rwxr-xr-x)
- Directories: `755` (standard) or `750` (restricted)
- Sudoers: `440` (r--r-----)

---

## Common Tasks

### Check Deployment Status

```bash
# Services
ssh ubuntu@192.0.2.10 'sudo systemctl status instagram-platform-test instagram-platform-prod'

# Containers
ssh ubuntu@192.0.2.10 'docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"'

# Health
ssh ubuntu@192.0.2.10 'curl -s http://localhost:8082/actuator/health | jq'
ssh ubuntu@192.0.2.10 'curl -s http://localhost:8083/actuator/health | jq'

# Logs
ssh ubuntu@192.0.2.10 'sudo journalctl -u instagram-platform-test -n 50 --no-pager'
```

### Restart Applications

```bash
# Via systemd
ssh ubuntu@192.0.2.10 'sudo systemctl restart instagram-platform-test'
ssh ubuntu@192.0.2.10 'sudo systemctl restart instagram-platform-prod'

# Or via Ansible
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

### Update Secrets

```bash
# 1. Edit vault
ansible-vault edit inventory/group_vars/test/vault.yml

# 2. Redeploy secrets
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags secrets --vault-password-file ~/.ansible/vault-pass

# 3. Restart applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

### Update Docker Compose

```bash
# 1. Edit docker-compose templates
# roles/11-docker-compose/templates/docker-compose-test.yml.j2

# 2. Redeploy
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags docker-compose

# 3. Restart
ansible-playbook -i inventory/production.yml playbooks/startup.yml
```

---

## Emergency Commands

### SSH Lost

```bash
# Use OVH console/VNC
# Fix firewall:
sudo iptables -I INPUT 1 -p tcp --dport 22 -j ACCEPT
sudo iptables-save > /etc/iptables/rules.v4
```

### Application Won't Start

```bash
# Check logs
ssh ubuntu@192.0.2.10 'sudo journalctl -u instagram-platform-test -n 100 --no-pager'

# Check containers
ssh ubuntu@192.0.2.10 'docker ps -a'

# Check specific container
ssh ubuntu@192.0.2.10 'docker logs instagram-platform-test-app-1 --tail 100'
```

### Restore from Backup

```bash
# In OVH control panel:
# VPS → Snapshots → Restore snapshot
```

### Re-run Failed Deployment

```bash
# Continue from where it failed
ansible-playbook -i inventory/production.yml playbooks/site.yml --start-at-task "Failed task name" --vault-password-file ~/.ansible/vault-pass
```

---

## Documentation Links

- **README.md** - Main documentation, quick start
- **TESTING.md** - Complete testing guide (6 levels)
- **CONTRIBUTING.md** - How to add roles/variables
- **TROUBLESHOOTING.md** - Common issues and fixes
- **DEPLOYMENT-GUIDE.md** - Step-by-step deployment walkthrough
- **PROJECT-SUMMARY.md** - Complete project overview
- **playbooks/README.md** - Playbook documentation
- **molecule/README.md** - Molecule testing guide

---

## Support

### Check Logs

```bash
# Local Ansible log
tail -f ansible.log

# VPS systemd journal
ssh ubuntu@192.0.2.10 'sudo journalctl -f'

# Specific service
ssh ubuntu@192.0.2.10 'sudo journalctl -u instagram-platform-test -f'

# Docker logs
ssh ubuntu@192.0.2.10 'docker logs instagram-platform-test-app-1 -f'
```

### Get Help

1. Check TROUBLESHOOTING.md
2. Check Ansible docs: https://docs.ansible.com/
3. Review role-specific README in roles/*/README.md

---

**Last Updated:** 2025-11-10
