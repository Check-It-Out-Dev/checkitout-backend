# Role: 07-systemd

## Description

Creates systemd service definitions for Instagram Platform applications:
- instagram-platform-test.service (test environment)
- instagram-platform-prod.service (production environment)

Services manage Docker Compose stacks with auto-start, restart on failure, and journald logging.

## Requirements

- 06-scripts completed (systemd-wrapper scripts must exist)
- Docker service running

## Dependencies

- 06-scripts

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 07-systemd
```

## Important

Services are ENABLED but NOT STARTED by this role. Starting happens in playbooks/startup.yml (Phase 12).

## Tags

- `systemd` - All tasks
- `test-service` - Test service only
- `prod-service` - Production service only
