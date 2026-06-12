# Role: 04-docker

## Description

Configures Docker for Instagram Platform:
- Docker daemon configuration (logging, storage driver)
- Docker networks with specific subnets (172.20.0.0/16, 172.21.0.0/16, 172.28.0.0/16)
- Docker volumes with proper ownership
- Docker service management

## Critical

Network subnets MUST match exactly - they're hardcoded in Docker Compose files and application configurations.

## Requirements

- Docker Engine installed (from 01-base-system)
- Users created (from 02-users-groups)

## Dependencies

- 01-base-system
- 02-users-groups

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 04-docker
```

## Tags

- `docker` - All tasks
- `docker-daemon` - Daemon configuration only
- `docker-networks` - Networks only
- `docker-volumes` - Volumes only
