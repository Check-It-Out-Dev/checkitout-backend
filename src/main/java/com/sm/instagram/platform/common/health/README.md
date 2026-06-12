# Health Module

This module provides Kubernetes-compatible health checks for the CheckItOut Backend application.

## Available Endpoints

- `/api/health/ready` - Readiness probe (returns 200 if ready to serve requests)
- `/api/health/live` - Liveness probe (returns 200 if application is alive)
- `/api/health/startup` - Startup probe (returns 200 if startup is complete)
- `/api/actuator/health` - Detailed health information

## Health Indicators

The module includes several custom health indicators:
- **ApplicationHealthIndicator** - Overall application status
- **DatabaseHealthIndicator** - Database connectivity
- **DiskSpaceHealthIndicator** - Disk space monitoring
- **LiquibaseHealthIndicator** - Database migration status

## Configuration

Health checks are configured in `application.properties`:

```properties
# Health check configuration
management.health.readinessstate.enabled=true
management.health.livenessstate.enabled=true
management.endpoint.health.probes.enabled=true
```

## Usage

The health endpoints are used by Kubernetes for:
- **Readiness**: Determines if the pod should receive traffic
- **Liveness**: Determines if the pod should be restarted
- **Startup**: Allows longer startup time before liveness checks begin
