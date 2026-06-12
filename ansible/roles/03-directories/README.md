# Role: 03-directories

## Description

Creates the complete directory structure for Instagram Platform with exact ownership and permissions:
- Application directories (/opt/instagram-platform/{test,prod})
- Secrets directories (/etc/instagram-platform/{test,prod}/secrets)
- Web roots (/var/www/)
- Log directories (/var/log/)
- Monitoring directories (/opt/instagram-platform/loki)
- Scripts directories

## Critical

Directory permissions MUST be exact for security:
- 755: Standard directories
- 750: Restricted directories (deployment, backups, secrets)
- Ownership must match deployment users

## Requirements

- 02-users-groups completed (users must exist)

## Dependencies

- 02-users-groups

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 03-directories
```

## Tags

- `directories` - All tasks
- `app-dirs` - Application directories only
- `secrets-dirs` - Secrets directories only
- `web-dirs` - Web root directories only
