# Production Logging Architecture

## Overview

All logging is centralized through systemd journal, providing a single source of truth for both system services and Docker containers. This architecture enables:

- Unified log collection
- Structured logging with metadata
- Easy integration with Promtail/Loki
- No log file management overhead
- Automatic log rotation by systemd

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Application Stack                  │
├───────────────┬───────────┬────────────┬────────────┤
│ SystemD       │ Docker    │ Docker     │ Docker     │
│ Service       │ Container │ Container  │ Container  │
│ (wrapper.sh)  │ (app)     │ (postgres) │ (redis)    │
└───────┬───────┴─────┬─────┴──────┬─────┴─────┬──────┘
        │             │             │           │
        └─────────────┴─────────────┴───────────┘
                          │
                    ┌─────▼─────┐
                    │  systemd   │
                    │  journal   │
                    └─────┬─────┘
                          │
                    ┌─────▼─────┐
                    │ Alloy     │
                    └─────┬─────┘
                          │
                    ┌─────▼─────┐
                    │   Loki    │
                    └─────┬─────┘
                          │
                    ┌─────▼─────┐
                    │  Grafana  │
                    └───────────┘
```

## Configuration Details

### 1. SystemD Service (instagram-platform-*.service)

```ini
[Service]
Type=simple                    # Maintains stdout connection
StandardOutput=journal         # All output to journal
StandardError=journal          # All errors to journal
SyslogIdentifier=instagram-platform-test  # Unique identifier for filtering
LogLevelMax=info              # Production log level
```

### 2. Docker Compose (docker-compose-*.yml)

```yaml
services:
  app:
    logging:
      driver: "journald"
      options:
        tag: "instagram-test-app"        # Service identifier
        labels: "environment,service,version"  # Metadata labels
```

### 3. Promtail Configuration

```yaml
# /etc/promtail/config.yml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  # Scrape systemd journal
  - job_name: systemd-journal
    journal:
      json: false
      max_age: 12h
      labels:
        job: systemd-journal
      path: /var/log/journal
    relabel_configs:
      # Extract unit name
      - source_labels: ['__journal__systemd_unit']
        target_label: 'unit'
      # Extract container name from docker logs
      - source_labels: ['__journal_container_name']
        target_label: 'container'
      # Extract custom tags
      - source_labels: ['__journal_container_tag']
        target_label: 'service'
    pipeline_stages:
      # Parse structured logs
      - json:
          expressions:
            level: level
            timestamp: timestamp
            message: message
      # Set log level
      - labels:
          level:
```

## Log Queries in Grafana/Loki

### Service Logs
```logql
# All logs from the service
{unit="instagram-platform-test.service"}

# Only errors
{unit="instagram-platform-test.service"} |= "ERROR"

# Specific container
{service="instagram-test-app"}

# Combined service and container logs
{unit="instagram-platform-test.service"} + {service=~"instagram-test-.*"}
```

### Useful Queries
```logql
# Startup sequence
{unit="instagram-platform-test.service"} |= "Starting"

# Health checks
{service="instagram-test-app"} |= "health"

# Database connections
{service="instagram-test-postgres"} |= "connection"

# Redis operations
{service="instagram-test-redis"} |~ "SET|GET|DEL"

# Errors across all services
{service=~"instagram-test-.*"} |= "ERROR"
```

## Monitoring Commands

### Real-time Monitoring
```bash
# Follow service logs
journalctl -fu instagram-platform-test

# Follow with priority filtering
journalctl -fu instagram-platform-test -p err

# Follow specific container logs
journalctl -fu docker.service -t instagram-test-app

# All Instagram platform logs
journalctl -f CONTAINER_TAG=instagram-test-app
```

### Historical Analysis
```bash
# Last hour of logs
journalctl -u instagram-platform-test --since "1 hour ago"

# Specific time range
journalctl -u instagram-platform-test --since "2024-01-15 10:00" --until "2024-01-15 11:00"

# Export as JSON for analysis
journalctl -u instagram-platform-test -o json --since today > logs.json

# Count errors
journalctl -u instagram-platform-test -p err --since today | wc -l
```

### Debugging
```bash
# Check if journald driver is active
docker info | grep "Logging Driver"

# Verify container logging configuration
docker inspect instagram-platform-test-app | jq '.[0].HostConfig.LogConfig'

# Check journal disk usage
journalctl --disk-usage

# Verify Promtail is reading journal
curl -s http://localhost:9080/metrics | grep promtail_journal_entries_total
```

## Best Practices

### 1. Structured Logging in Application
```java
// Use structured logging in Spring Boot
log.info("action=startup status=complete duration_ms={} version={}", 
         duration, version);
```

### 2. Wrapper Script Logging
```bash
# Use consistent format
log() {
    local level="$1"
    local message="$2"
    echo "[$(date -Iseconds)] [$level] $message"
}
```

### 3. Log Levels
- **ERROR**: Application errors, failures
- **WARN**: Degraded performance, retries
- **INFO**: Normal operations, state changes
- **DEBUG**: Detailed diagnostics (disabled in production)

### 4. Retention Policy
```bash
# Configure journal retention
sudo mkdir -p /etc/systemd/journald.conf.d/
cat <<EOF | sudo tee /etc/systemd/journald.conf.d/retention.conf
[Journal]
SystemMaxUse=10G
SystemKeepFree=1G
SystemMaxFileSize=100M
MaxRetentionSec=30d
EOF

sudo systemctl restart systemd-journald
```

## Troubleshooting

### Issue: No logs visible
```bash
# Check service status
systemctl status instagram-platform-test

# Verify journal is receiving logs
journalctl -f  # Should show system-wide logs

# Check Docker daemon logging driver
docker info | grep "Logging Driver"
# Should show: Logging Driver: journald
```

### Issue: Logs not reaching Loki
```bash
# Check Promtail status
systemctl status promtail

# Test Promtail can read journal
sudo -u promtail journalctl -n 10

# Check Promtail metrics
curl -s http://localhost:9080/metrics | grep journal
```

### Issue: High log volume
```bash
# Identify chatty services
journalctl --since "1 hour ago" -o json | \
  jq -r '._SYSTEMD_UNIT' | sort | uniq -c | sort -rn

# Adjust log level in docker-compose.yml
environment:
  - LOG_LEVEL=WARN  # Reduce from INFO
```

## Migration Checklist

- [x] Update systemd service files to Type=simple
- [x] Configure StandardOutput=journal
- [x] Update docker-compose.yml with journald driver
- [x] Add appropriate tags and labels
- [ ] Deploy Promtail with journal scraping
- [ ] Configure Loki data source in Grafana
- [ ] Create dashboards for log analysis
- [ ] Set up alerts for error patterns
- [ ] Document runbooks for common issues

## Security Considerations

1. **Journal Access**: Promtail needs to be in `systemd-journal` group
2. **Log Sanitization**: Ensure no secrets in logs
3. **Rate Limiting**: Configure Loki ingestion limits
4. **Retention**: Balance retention vs disk usage
5. **Access Control**: Restrict Grafana dashboard access

## Performance Impact

- **CPU**: Minimal (<1% for journald)
- **Memory**: ~50MB for Promtail
- **Disk I/O**: Reduced vs file-based logging
- **Network**: Promtail batches to Loki (configurable)

## References

- [SystemD Journal](https://www.freedesktop.org/software/systemd/man/systemd-journald.service.html)
- [Docker Journald Driver](https://docs.docker.com/config/containers/logging/journald/)
- [Promtail Journal Scraping](https://grafana.com/docs/loki/latest/clients/promtail/scraping/#journal-scraping-linux-only)
- [Loki Best Practices](https://grafana.com/docs/loki/latest/best-practices/)