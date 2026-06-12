# Role: 10-monitoring

## Description

Deploys Loki monitoring stack:
- Loki log aggregation service
- Alloy log collector (sends logs to Loki)
- mTLS certificates for secure access
- Docker Compose configuration for Loki stack

## Components

- Loki (log storage and querying)
- Alloy (log collection from journald)
- mTLS certificates (for Nginx → Loki secure communication)

## Requirements

- Docker configured (04-docker)
- Loki directories created (03-directories)
- Nginx configured (08-nginx) for mTLS proxy

## Dependencies

- 03-directories
- 04-docker

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 10-monitoring
```

## Tags

- `monitoring` - All tasks
- `loki-config` - Loki configuration only
- `alloy-config` - Alloy configuration only
- `mtls-certs` - mTLS certificates only
