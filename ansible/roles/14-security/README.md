# Role: 14-security

## Description

Final security hardening for Instagram Platform:
- AppArmor profile verification
- File immutability (chattr +i) for critical configs
- Security audit (open ports, permissions, services)
- Fail2Ban configuration (optional)

## Critical

File immutability prevents accidental changes to critical files (.env, systemd services, sudoers). Files must be un-immutable before editing.

## Requirements

- All previous roles completed
- Applications configured

## Dependencies

All previous roles

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 14-security
```

## Tags

- `security` - All tasks
- `apparmor` - AppArmor only
- `immutable` - File immutability only
- `audit` - Security audit only
