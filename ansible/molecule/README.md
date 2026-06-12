# Molecule Testing Framework

## Overview

Molecule tests Ansible roles in isolated Docker containers before production deployment. This ensures roles work correctly and are idempotent.

## Installation

```bash
# Install Molecule with Docker driver
pip install molecule "molecule[docker]"

# Pull test image
docker pull geerlingguy/docker-ubuntu2404-ansible:latest
```

## Usage

### Run Full Test Suite

```bash
# From ansible/ directory
cd ansible/
molecule test
```

This runs the complete test cycle:
1. Create test container
2. Apply roles (converge)
3. Apply roles again (idempotence test)
4. Run verification tests
5. Destroy test container

### Development Workflow

```bash
# Create test container
molecule create

# Apply roles to container
molecule converge

# SSH into container to inspect
molecule login

# Run verification tests
molecule verify

# Destroy container when done
molecule destroy
```

### Test Specific Roles

```bash
# Test only infrastructure roles
molecule converge -- --tags phase3

# Test only one role
molecule converge -- --tags base-system
```

## Test Scenario

The default scenario tests:
- User and group creation with exact UIDs/GIDs
- Directory structure creation with correct permissions
- Docker installation and configuration
- File deployments
- Idempotency (second run produces no changes)

## Limitations

Docker containers have some limitations compared to real VPS:
- Network configuration (netplan) won't work
- Some systemd features limited
- Kernel parameters may not apply
- Firewall rules behave differently

For complete testing, use a cheap test VPS (Hetzner CX22, €5.83/month).

## Quick Commands

```bash
# Create and test
molecule test

# Just converge (apply roles)
molecule converge

# Check for idempotency
molecule idempotence

# Login to inspect
molecule login

# Destroy and start over
molecule destroy
```

## Continuous Integration

Add to GitHub Actions:

```yaml
- name: Test with Molecule
  run: |
    pip install molecule "molecule[docker]"
    cd ansible
    molecule test
```

## Troubleshooting

### Container won't start

```bash
# Check Docker
docker ps -a

# View Molecule logs
molecule --debug create
```

### Converge fails

```bash
# View detailed output
molecule converge -- -vvv

# Check specific task
molecule converge -- --start-at-task "task name"
```

### Want to keep container for debugging

```bash
# Run without automatic destroy
molecule create
molecule converge
# Container stays running for inspection
molecule destroy  # When ready to cleanup
```
