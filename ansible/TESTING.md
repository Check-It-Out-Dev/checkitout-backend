# Testing Guide - Instagram Platform Ansible

This guide covers all testing approaches for the Instagram Platform Ansible deployment.

## Testing Levels

### Level 1: Local Syntax Validation (5 minutes)

No VPS access required. Tests syntax locally.

```bash
# YAML syntax
yamllint .

# Ansible syntax
ansible-playbook playbooks/site.yml --syntax-check

# Ansible lint
ansible-lint playbooks/site.yml
```

**Success criteria:** All commands exit with 0 (no errors)

---

### Level 2: Dry Run (10 minutes)

Simulates deployment without making changes. Requires SSH access to VPS.

```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml \
  --check \
  --diff \
  --vault-password-file ~/.ansible/vault-pass
```

**Success criteria:** Shows what WOULD change, no errors

---

### Level 3: Molecule Docker Testing (30-60 minutes)

Full deployment to Docker container. Tests roles in isolated environment.

#### Setup

```bash
# Install Molecule
pip install molecule "molecule[docker]"

# Pull test image
docker pull geerlingguy/docker-ubuntu2404-ansible:latest
```

#### Run Tests

```bash
cd ansible/

# Full test cycle
molecule test

# Or step by step:
molecule create      # Create container
molecule converge    # Apply roles
molecule verify      # Run tests
molecule destroy     # Cleanup
```

#### What It Tests

- User/group creation with exact UIDs/GIDs
- Directory structure and permissions
- Docker installation and configuration
- File deployments
- Idempotency (second run = no changes)

#### Limitations

- Network configuration (netplan) won't work in container
- Some systemd features limited
- Firewall rules behave differently
- SSL certificate generation won't work

**Success criteria:**
- All roles apply without errors
- Idempotency test passes (0 changes on second run)
- Verification tests pass

---

### Level 4: Cheap Test VPS (1-2 hours, €5-10)

Full deployment to real VPS. 100% identical to production.

#### Providers

**Hetzner CX22** (Recommended)
- Cost: €5.83/month
- Specs: 2 vCPU, 4GB RAM, 40GB SSD
- Location: Choose closest to OVH
- URL: https://www.hetzner.com/cloud

**DigitalOcean Basic**
- Cost: $6/month
- Specs: 1 vCPU, 1GB RAM, 25GB SSD
- URL: https://www.digitalocean.com/pricing

#### Workflow

```bash
# 1. Create test VPS (Ubuntu 24.04)
# Use provider web UI

# 2. Add to inventory
# Edit inventory/testing.yml with test VPS IP

# 3. Deploy
ansible-playbook -i inventory/testing.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass

# 4. Start applications
ansible-playbook -i inventory/testing.yml playbooks/startup.yml

# 5. Verify
ansible-playbook -i inventory/testing.yml playbooks/verify.yml

# 6. Destroy VPS when testing complete
# Use provider web UI
```

**Success criteria:**
- All playbooks complete successfully
- All services healthy
- Applications accessible
- Verification playbook passes 100%

**Benefits:**
- Real network, kernel, systemd
- Test SSL certificate generation
- Test actual database connections
- Identical to production VPS

---

### Level 5: Production Dry Run (10 minutes)

Final check before production deployment.

```bash
ansible-playbook -i inventory/production.yml playbooks/site.yml \
  --check \
  --diff \
  --verbose \
  --vault-password-file ~/.ansible/vault-pass
```

**Review:** Manually review ALL proposed changes before proceeding

---

### Level 6: Production Deployment (30-60 minutes)

Deploy to actual production VPS.

#### Prerequisites Checklist

- [ ] Levels 1-5 completed and passed
- [ ] VPS snapshot taken (OVH control panel)
- [ ] Console access available (OVH VNC/KVM)
- [ ] Vault password accessible
- [ ] Backup plan ready
- [ ] Team notified
- [ ] Maintenance window scheduled

#### Deployment

```bash
# Deploy infrastructure
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass

# Start applications
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# Verify
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```

#### Monitoring During Deployment

```bash
# Watch logs (in separate terminal)
ssh ubuntu@192.0.2.10 'sudo journalctl -f'

# Watch containers
ssh ubuntu@192.0.2.10 'watch docker ps'

# Monitor resources
ssh ubuntu@192.0.2.10 'htop'
```

---

## Testing Individual Roles

### Test One Role

```bash
# Test base system only
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags base-system --check

# Test secrets only
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags secrets --check --vault-password-file ~/.ansible/vault-pass
```

### Test Idempotency

```bash
# Run twice, second run should show 0 changes
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass
```

Second run should report: `changed=0`

---

## Common Test Failures

### SSH Connection Failed

```bash
# Check SSH manually
ssh -i ~/.ssh/id_rsa ubuntu@192.0.2.10

# Verify inventory
ansible-inventory -i inventory/production.yml --graph
ansible-inventory -i inventory/production.yml --host vps-69c5792e
```

### Vault Decryption Failed

```bash
# Test vault password
ansible-vault view inventory/group_vars/test/vault.yml --vault-password-file ~/.ansible/vault-pass

# Check file is encrypted
head -1 inventory/group_vars/test/vault.yml
# Should start with: $ANSIBLE_VAULT;
```

### Docker Network Creation Failed

```bash
# Check for subnet conflicts
ssh ubuntu@192.0.2.10 'docker network ls'
ssh ubuntu@192.0.2.10 'docker network inspect <conflicting-network>'

# Remove conflicting network
ssh ubuntu@192.0.2.10 'docker network rm <network-name>'
```

### Permission Denied

```bash
# Check user exists
ssh ubuntu@192.0.2.10 'id instagram-test-deploy'

# Check directory ownership
ssh ubuntu@192.0.2.10 'ls -la /opt/instagram-platform/test/'
```

---

## Molecule Advanced

### Custom Scenarios

```bash
# Create new scenario
molecule init scenario --driver-name docker custom-scenario

# Test specific scenario
molecule test -s custom-scenario
```

### Debug Failed Tests

```bash
# Keep container running after failure
molecule --debug converge

# Login to inspect
molecule login

# View logs
molecule login -- journalctl -f
```

### Test Multiple Platforms

Edit `molecule/default/molecule.yml`:

```yaml
platforms:
  - name: ubuntu-2404
    image: geerlingguy/docker-ubuntu2404-ansible
  - name: ubuntu-2204
    image: geerlingguy/docker-ubuntu2204-ansible
```

---

## Performance Testing

### Check Deployment Time

```bash
# Time the deployment
time ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass
```

**Target:** < 60 minutes

### Parallelize Tasks

```bash
# Increase forks in ansible.cfg
forks = 20

# Or via command line
ansible-playbook -i inventory/production.yml playbooks/site.yml -f 20
```

---

## Continuous Integration

### GitHub Actions Example

```yaml
name: Test Ansible Deployment

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.12'

      - name: Install dependencies
        run: |
          pip install ansible molecule "molecule[docker]"
          ansible-galaxy install -r ansible/requirements.yml

      - name: Run Molecule tests
        run: |
          cd ansible
          molecule test
```

---

## Recommended Testing Order

1. **Local syntax check** (always)
2. **Molecule Docker test** (before VPS deployment)
3. **Cheap test VPS** (optional, highly recommended)
4. **Production dry run** (always before prod deployment)
5. **Production deployment** (with monitoring)

---

## Test Results Checklist

After testing, verify:

- [ ] Syntax checks pass (yamllint, ansible-lint)
- [ ] Dry run shows expected changes only
- [ ] Molecule tests pass
- [ ] Idempotency verified (second run = 0 changes)
- [ ] All roles complete without errors
- [ ] Applications start and pass health checks
- [ ] Security audit passes (no world-readable secrets, firewall correct)
- [ ] Performance acceptable (deployment < 60 min)

**Only proceed to production when ALL tests pass.**

---

## Quick Reference

```bash
# Syntax
ansible-playbook playbooks/site.yml --syntax-check

# Lint
ansible-lint playbooks/site.yml

# Dry run
ansible-playbook -i inventory/production.yml playbooks/site.yml --check --vault-password-file ~/.ansible/vault-pass

# Molecule
molecule test

# Deploy
ansible-playbook -i inventory/production.yml playbooks/site.yml --vault-password-file ~/.ansible/vault-pass

# Start
ansible-playbook -i inventory/production.yml playbooks/startup.yml

# Verify
ansible-playbook -i inventory/production.yml playbooks/verify.yml
```
