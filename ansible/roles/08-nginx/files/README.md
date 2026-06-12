# Nginx Configuration Files

## Instructions

This directory should contain Nginx configuration files from:

1. **deployment/config/nginx-refactored/** (if using refactored configs)
2. Or copy from existing VPS

## Structure

```
files/
├── nginx.conf (main config)
├── conf.d/ (modular configs)
│   ├── 01-cloudflare.conf
│   ├── 02-rate-limiting.conf
│   ├── 03-security.conf
│   ├── 04-ssl.conf
│   ├── 05-gzip.conf
│   ├── 06-mtls.conf
│   ├── 07-proxy.conf
│   ├── 08-headers.conf
│   └── 09-logging.conf
└── sites-available/
    ├── checkitout.app.conf
    ├── check-it-out.pl.conf
    ├── loki.checkitout.app.conf
    └── default.conf
```

## Copying from VPS

```bash
# Copy from existing VPS
scp -r ubuntu@OLD_VPS:/etc/nginx/conf.d/*.conf files/conf.d/
scp -r ubuntu@OLD_VPS:/etc/nginx/sites-available/*.conf files/sites-available/
```

## Or Use deployment/config/nginx-refactored/

The role will automatically use files from `deployment/config/nginx-refactored/` if available.
