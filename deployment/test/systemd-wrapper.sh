#!/bin/bash
# PRODUCTION SYSTEMD WRAPPER - Optimized for quick returns and async operations
# Follows production standards: fast start/stop, async health checks, proper logging

set -e

# Script initialization
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"

# Load environment variables
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
else
    echo "[$(date -Iseconds)] [ERROR] Environment file not found: $ENV_FILE" >&2
    exit 1
fi

# Validate required variables
required_vars=(
    "DEPLOYMENT_REMOTE_PATH"
    "RUNTIME_PATH"
    "COMPOSE_FILE"
    "STATE_FILE"
    "LOCK_FILE"
    "MAIN_APP_IMAGE"
)

for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "[$(date -Iseconds)] [ERROR] Required variable $var not set" >&2
        exit 1
    fi
done

# Set aliases
DEPLOYMENT_DIR="$DEPLOYMENT_REMOTE_PATH"
RUNTIME_DIR="$RUNTIME_PATH"

# PRODUCTION LOGGING: Unbuffered output for journald
exec 1> >(stdbuf -oL cat)
exec 2>&1

# Simple structured logging
log() {
    local level="${1:-INFO}"
    local message="${2:-$1}"
    echo "[$(date -Iseconds)] [$level] $message"
}

# Fast lock acquisition (no waiting)
acquire_lock() {
    if mkdir "$LOCK_FILE" 2>/dev/null; then
        echo $$ > "$LOCK_FILE/pid"
        return 0
    fi
    
    # Check for stale lock
    if [ -f "$LOCK_FILE/pid" ]; then
        local lock_pid=$(cat "$LOCK_FILE/pid" 2>/dev/null || echo "0")
        if ! kill -0 "$lock_pid" 2>/dev/null; then
            rm -rf "$LOCK_FILE"
            mkdir "$LOCK_FILE" && echo $$ > "$LOCK_FILE/pid"
            return 0
        fi
    fi
    
    log "ERROR" "Another operation in progress (lock held by PID $(cat "$LOCK_FILE/pid" 2>/dev/null))"
    return 1
}

release_lock() {
    rm -rf "$LOCK_FILE"
}

# Save state (fast, no validation)
save_state() {
    echo "STATE=$1
TIMESTAMP=$(date -Iseconds)
DETAILS=$2
ENVIRONMENT=${ENVIRONMENT:-test}" > "$STATE_FILE" 2>/dev/null || true
}

# PRODUCTION START: Fast, returns quickly
start_containers() {
    log "INFO" "Starting Instagram Platform ${ENVIRONMENT:-test}"
    
    cd "$DEPLOYMENT_DIR"
    
    # Quick cleanup (background)
    {
        sudo /usr/bin/docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null
        sudo /usr/bin/docker volume prune -f 2>/dev/null
    } &
    
    # Wait briefly for cleanup
    sleep 2
    
    # Validate image
    if [ -z "${MAIN_APP_IMAGE:-}" ]; then
        log "ERROR" "MAIN_APP_IMAGE not set"
        save_state "failed" "Missing configuration"
        return 1
    fi
    
    # Start containers (detached, returns immediately)
    log "INFO" "Starting Docker Compose stack"
    if ! sudo /usr/bin/docker compose -f "$COMPOSE_FILE" up -d --remove-orphans; then
        log "ERROR" "Docker compose failed"
        save_state "failed" "Compose up failed"
        return 1
    fi
    
    save_state "starting" "Containers launched"
    
    # ASYNC HEALTH CHECK: Run in background
    {
        sleep 5  # Give containers time to start
        
        # Quick health check (30s max total)
        local healthy=true
        
        # Check GSM init (critical)
        for i in {1..10}; do
            local state=$(sudo /usr/bin/docker compose -f "$COMPOSE_FILE" ps google-secrets-init --format json 2>/dev/null | jq -r '.State // "unknown"' 2>/dev/null)
            if [[ "$state" == "exited" ]]; then
                local exit_code=$(sudo /usr/bin/docker compose -f "$COMPOSE_FILE" ps google-secrets-init --format json 2>/dev/null | jq -r '.ExitCode // "1"' 2>/dev/null)
                if [[ "$exit_code" == "0" ]]; then
                    log "INFO" "GSM init completed"
                    break
                else
                    log "ERROR" "GSM init failed"
                    healthy=false
                    break
                fi
            fi
            sleep 2
        done
        
        # Quick check other services (don't wait for full health)
        for service in postgres redis app; do
            local state=$(sudo /usr/bin/docker compose -f "$COMPOSE_FILE" ps "$service" --format json 2>/dev/null | jq -r '.State // "unknown"' 2>/dev/null)
            if [[ "$state" == "running" ]]; then
                log "INFO" "$service is running"
            else
                log "WARN" "$service not running yet"
            fi
        done
        
        if $healthy; then
            save_state "running" "Services started"
        else
            save_state "degraded" "Some services failed"
        fi
    } &
    
    log "SUCCESS" "Startup initiated successfully"
    return 0
}

# PRODUCTION STOP: Fast, graceful
stop_containers() {
    log "INFO" "Stopping Instagram Platform ${ENVIRONMENT:-test}"
    
    cd "$DEPLOYMENT_DIR"
    
    # Fast stop with timeout
    sudo /usr/bin/docker compose -f "$COMPOSE_FILE" stop --timeout 10 2>/dev/null || \
    sudo /usr/bin/docker compose -f "$COMPOSE_FILE" kill 2>/dev/null || true
    
    # Remove containers
    sudo /usr/bin/docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true
    
    save_state "stopped" "Services stopped"
    log "SUCCESS" "Services stopped"
    return 0
}

# HEALTH CHECK: Separate command for monitoring
check_health() {
    local all_healthy=true
    
    # Check each service
    for service in google-secrets-init postgres redis app; do
        local info=$(sudo /usr/bin/docker compose -f "$COMPOSE_FILE" ps "$service" --format json 2>/dev/null || echo '{}')
        local state=$(echo "$info" | jq -r '.State // "unknown"' 2>/dev/null)
        local health=$(echo "$info" | jq -r '.Health // "no-healthcheck"' 2>/dev/null)
        
        if [[ "$service" == "google-secrets-init" ]]; then
            # Init container should be exited with code 0
            if [[ "$state" != "exited" ]] || [[ "$(echo "$info" | jq -r '.ExitCode // "1"')" != "0" ]]; then
                all_healthy=false
                log "WARN" "$service: unhealthy (state=$state)"
            fi
        else
            # Other services should be running
            if [[ "$state" != "running" ]]; then
                all_healthy=false
                log "WARN" "$service: unhealthy (state=$state, health=$health)"
            else
                log "INFO" "$service: healthy"
            fi
        fi
    done
    
    if $all_healthy; then
        save_state "healthy" "All services healthy"
        return 0
    else
        save_state "degraded" "Some services unhealthy"
        return 1
    fi
}

# Main command handler
case "$1" in
    start)
        if ! acquire_lock; then
            exit 1
        fi
        trap "release_lock" EXIT
        
        start_containers
        exit $?
        ;;
        
    stop)
        if ! acquire_lock; then
            exit 1
        fi
        trap "release_lock" EXIT
        
        stop_containers
        exit $?
        ;;
        
    restart)
        "$0" stop && sleep 2 && "$0" start
        ;;
        
    status)
        cd "$DEPLOYMENT_DIR"
        
        # Quick status
        if [ -f "$STATE_FILE" ]; then
            cat "$STATE_FILE"
            echo ""
        fi
        
        # Container status
        sudo /usr/bin/docker compose -f "$COMPOSE_FILE" ps
        ;;
        
    health)
        # Detailed health check (for monitoring)
        cd "$DEPLOYMENT_DIR"
        check_health
        exit $?
        ;;
        
    health-check)
        # Quick health check (for systemd)
        if [ -f "$STATE_FILE" ]; then
            STATE=$(grep "^STATE=" "$STATE_FILE" | cut -d= -f2)
            [[ "$STATE" == "healthy" || "$STATE" == "running" ]]
            exit $?
        fi
        exit 1
        ;;
        
    logs)
        shift
        cd "$DEPLOYMENT_DIR"
        sudo /usr/bin/docker compose -f "$COMPOSE_FILE" logs "$@"
        ;;
        
    follow)
        # Follow journal logs
        journalctl -fu "instagram-platform-${ENVIRONMENT:-test}" --output=cat
        ;;
        
    wait-healthy)
        # Wait for services to be healthy (for CI/CD)
        log "INFO" "Waiting for services to be healthy (max 3 minutes)..."
        
        for i in {1..36}; do  # 36 * 5s = 180s
            if "$0" health-check 2>/dev/null; then
                log "SUCCESS" "All services healthy"
                exit 0
            fi
            log "INFO" "Waiting... ($((i*5))s elapsed)"
            sleep 5
        done
        
        log "ERROR" "Timeout waiting for healthy state"
        "$0" status
        exit 1
        ;;
        
    exec-start)
        # FOR SYSTEMD Type=simple - keeps running
        if ! acquire_lock; then
            exit 1
        fi
        trap "release_lock; stop_containers" EXIT TERM INT
        
        start_containers || exit 1
        
        # Keep process running for systemd
        log "INFO" "Service running. PID $$"
        
        # Follow docker-compose logs to keep process alive
        cd "$DEPLOYMENT_DIR"
        exec sudo /usr/bin/docker compose -f "$COMPOSE_FILE" logs -f 2>&1
        ;;
        
    *)
        cat <<EOF
Usage: $0 {start|stop|restart|status|health|logs|follow|wait-healthy|exec-start}

Commands:
  start         - Start containers (returns quickly)
  stop          - Stop containers
  restart       - Restart containers
  status        - Show current status
  health        - Detailed health check
  health-check  - Quick health check (exit code only)
  logs [opts]   - Show container logs
  follow        - Follow systemd journal
  wait-healthy  - Wait for healthy state (CI/CD)
  exec-start    - Start and keep running (for Type=simple)

Production optimizations:
- 'start' returns within 10 seconds
- Health checks run asynchronously
- No blocking waits in critical path
- Structured logging for journal
EOF
        exit 1
        ;;
esac