# Role: 02-users-groups

## Description

Creates the complete user and group structure for Instagram Platform:
- Security groups (docker-secrets-test, docker-secrets-prod, docker-secrets-admin)
- Deployment users (instagram-test-deploy, instagram-prod-deploy)
- CI/CD admin user (instagram-scripts-admin)
- Monitoring user (loki)
- Configures group memberships and SSH access

## Critical

UIDs and GIDs MUST match exactly as documented for proper file permissions and Docker volume access.

## Requirements

- Ubuntu 24.04 LTS
- Docker group already created (from 01-base-system)

## Role Variables

See `defaults/main.yml`. All user/group configurations are in `group_vars/all.yml`.

## Dependencies

- 01-base-system (Docker must be installed)

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 02-users-groups
```

## Tags

- `users-groups` - All tasks
- `groups` - Group creation only
- `users` - User creation only
- `ssh-keys` - SSH key configuration only

## Author

Generated for Instagram Platform VPS Migration
