# Role: 15-final-hardening

## Purpose

This role applies network, firewall, and SSH hardening configurations **as the very last step** of deployment.

## Why This is Separate

These configurations can potentially disrupt SSH connectivity:
- **Network changes**: Could change IP addresses or routing
- **Firewall rules**: Could block SSH port (22)
- **SSH hardening**: Could disable password auth or root login

By applying them LAST, we ensure:
1. All infrastructure and applications are deployed first
2. If something goes wrong, you can still SSH in with original settings
3. Console access (OVH VNC/KVM) can be used if SSH breaks

## Tasks Applied

1. **Network configuration** (from 01-base-system/tasks/network.yml)
2. **Firewall rules** (from 01-base-system/tasks/firewall.yml)
3. **SSH hardening** (from 01-base-system/tasks/ssh.yml)

## Variables

- `apply_network_hardening`: Apply network changes (default: true)
- `apply_firewall_hardening`: Apply firewall rules (default: true)
- `apply_ssh_hardening`: Apply SSH hardening (default: true)

## Usage

```bash
# Deploy everything including final hardening
ansible-playbook -i inventory/production.yml playbooks/site.yml

# Deploy but skip final hardening (for testing)
ansible-playbook -i inventory/production.yml playbooks/site.yml --skip-tags final-hardening

# Apply only final hardening (after initial deployment)
ansible-playbook -i inventory/production.yml playbooks/site.yml --tags final-hardening
```

## Safety

- 10-second pause before applying changes (can press Ctrl+C to cancel)
- Auto-revert for network changes (120-second timeout)
- Connection verification after each step
