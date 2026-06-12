# Role: 11-docker-compose

## Description

Deploys Docker Compose files for Instagram Platform applications:
- Test environment compose (5 containers: gsm-init, postgres, redis, sentinel, app)
- Production environment compose (4 containers: gsm-init, redis, sentinel, app)
- Database initialization SQL for test environment

## Critical

- Test environment includes local PostgreSQL container
- Production environment uses external OVH PostgreSQL (no postgres container)
- Networks must match: test (172.20.0.0/16), prod (172.21.0.0/16)

## Requirements

- Docker networks created (04-docker)
- Secrets deployed (05-secrets)
- Scripts deployed (06-scripts)

## Dependencies

- 04-docker
- 05-secrets
- 06-scripts

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 11-docker-compose
```

## Tags

- `docker-compose` - All tasks
- `test-compose` - Test environment only
- `prod-compose` - Production environment only
