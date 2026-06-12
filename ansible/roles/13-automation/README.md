# Role: 13-automation

## Description

Configures automated tasks and scheduled maintenance:
- Cron jobs for Cloudflare IP updates, backups
- Docker cleanup scheduling
- Health check automation
- Logrotate configuration
- Certbot SSL renewal (systemd timers)

## Requirements

- Scripts deployed (06-scripts)
- Applications running (for health checks)

## Dependencies

- 06-scripts

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 13-automation
```

## Tags

- `automation` - All tasks
- `cron` - Cron jobs only
- `timers` - Systemd timers only
