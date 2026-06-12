# Role: 01-base-system

## Description

This role establishes the foundation of the Instagram Platform VPS by configuring:
- Ubuntu 24.04 LTS base system
- Essential packages (1,595+ packages)
- Network configuration with static IPs
- Firewall rules (iptables)
- Kernel security parameters (sysctl)
- SSH hardening
- System hostname and timezone

## Requirements

- Ubuntu 24.04 LTS (Noble Numbat)
- SSH access with sudo privileges
- Internet connectivity

## Role Variables

See `defaults/main.yml` for all configurable variables. Key variables:

```yaml
# Network configuration
vps_ipv4: "192.0.2.10"
vps_ipv6: "2001:db8::10"
vps_gateway_ipv4: "192.0.2.1"
vps_interface_name: "ens3"

# Firewall
firewall_allowed_tcp_ports: [22, 80, 443]

# SSH hardening
sshd_permit_root_login: "no"
sshd_password_authentication: "no"
```

## Dependencies

None

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 01-base-system
```

## Tags

- `base-system` - All base system tasks
- `packages` - Package installation only
- `network` - Network configuration only
- `firewall` - Firewall configuration only
- `sysctl` - Kernel parameters only
- `ssh` - SSH hardening only

## Author

Generated for Instagram Platform VPS Migration
