# Role: 08-nginx

## Description

Configures Nginx web server and reverse proxy:
- Main nginx.conf configuration
- 9 modular configs (Cloudflare IPs, rate limiting, security, SSL, etc.)
- 4 site configurations (checkitout.app, check-it-out.pl, loki, default)
- SSL certificates via Let's Encrypt
- mTLS for Loki monitoring access

## Requirements

- Nginx installed (from 01-base-system)
- Domains pointing to VPS (for SSL certificate generation)

## Dependencies

- 01-base-system

## Example Playbook

```yaml
- hosts: instagram_platform
  become: true
  roles:
    - 08-nginx
```

## Important

SSL certificates require domains to be pointing to the VPS. If domains aren't ready, certificates can be generated later.

## Tags

- `nginx` - All tasks
- `nginx-config` - Configuration only
- `nginx-sites` - Site configs only
- `nginx-ssl` - SSL certificates only
