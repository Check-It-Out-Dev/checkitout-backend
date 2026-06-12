# Role: 06-scripts

## Description

Deploys all operational scripts for Instagram Platform:
- Systemd wrapper scripts (orchestrate Docker Compose lifecycle)
- Docker entrypoint scripts (initialize containers)
- Health check scripts
- Admin CI/CD scripts
- System maintenance scripts

## Requirements

- 03-directories completed (script directories must exist)
- Scripts available in deployment/scripts/ or files/ directory

## Dependencies

- 03-directories

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 06-scripts
```

## Tags

- `scripts` - All tasks
- `systemd-wrappers` - Systemd wrapper scripts only
- `entrypoint-scripts` - Docker entrypoints only
- `admin-scripts` - CI/CD admin scripts only

## Note

Script sources should be in:
- `deployment/test/` (systemd-wrapper.sh, entrypoints)
- `deployment/prod/` (systemd-wrapper.sh, entrypoints)
- `files/scripts/` (system scripts)
