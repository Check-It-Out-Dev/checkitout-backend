#!/bin/bash
# Script: reload-and-validate.sh (GOLD STANDARD V5)
# Purpose: Reload systemd service and verify deployment is running
# Modified: 2025-01-31 - Updated for admin user with sudo privileges
#
# GOLD STANDARD V5:
# 1. Uses secure-logger.sh for consistent logging (MANDATORY)
# 2. Admin user execution with proper sudo usage
# 3. Includes all helper functions (safe_increment, retry_operation)
# 4. Enhanced debug checkpoints
# 5. Consistent error handling
# 6. No hardcoded values - all from environment

set -euo pipefail

# Force all output to be unbuffered and visible
export TERM=xterm

# CRITICAL: Verify secure-logger.sh exists BEFORE doing anything
LOGGER_FOUND=0
LOGGER_LOCATIONS=(
    "$(pwd)/secure-logger.sh"
    "$(dirname "$0")/secure-logger.sh"
    "$HOME/secure-logger.sh"
    "/var/lib/instagram-scripts-admin/secure-logger.sh"
)

echo "[INIT] Searching for mandatory secure-logger.sh..." >&2

for location in "${LOGGER_LOCATIONS[@]}"; do
    if [ -f "$location" ]; then
        echo "[INIT] Found secure-logger.sh at: $location" >&2

        # Check if logger is immutable (indicates proper deployment)
        if sudo lsattr "$location" 2>/dev/null | grep -q '^....i'; then
            echo "[INIT] secure-logger.sh is properly protected (immutable)" >&2
        else
            echo "[WARN] secure-logger.sh is not immutable" >&2
        fi

        # Source the logger
        if source "$location"; then
            LOGGER_FOUND=1
            SECURE_LOGGER_PATH="$location"
            break
        else
            echo "[ERROR] Failed to source secure-logger.sh from: $location" >&2
        fi
    fi
done

# MANDATORY CHECK: Fail if logger not found
if [ $LOGGER_FOUND -eq 0 ]; then
    echo "[FATAL] secure-logger.sh is REQUIRED but not found!" >&2
    echo "[FATAL] This script cannot run without secure logging." >&2
    echo "[FATAL] Ensure secure-logger.sh is uploaded before running this script." >&2
    exit 1
fi

# Initialize secure logging for this operation
init_secure_logging "reload-validate" "${ENVIRONMENT:-test}"

# Safe increment function
safe_increment() {
    local -n var_ref=$1
    ((var_ref++)) || true
}

# Retry operation function
retry_operation() {
    local max_attempts="${1:-3}"
    local delay="${2:-1}"
    local attempt=1
    shift 2
    local command=("$@")

    while [ $attempt -le $max_attempts ]; do
        if "${command[@]}"; then
            return 0
        fi

        if [ $attempt -lt $max_attempts ]; then
            log "WARN" "Command failed (attempt $attempt/$max_attempts): ${command[*]}"
            log "INFO" "Retrying in ${delay}s..."
            sleep "$delay"
        fi
        ((attempt++)) || true
    done

    log "ERROR" "Command failed after $max_attempts attempts: ${command[*]}"
    return 1
}

# Wrapper functions for common operations with retry logic (NO sudo - script runs as root)
systemctl_with_retry() {
    local action="$1"
    local service="$2"
    # Increased retries and delay for complex stack startup
    # Using 120s delay to avoid conflicts with SystemD's RestartSec=30s
    retry_operation 3 120 systemctl "$action" "$service"
}

docker_ps_with_retry() {
    retry_operation 3 1 docker ps "$@"
}

# Debug checkpoint function
debug_checkpoint() {
    local checkpoint="$1"
    local details="${2:-}"

    echo ""
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║ 🔍 CHECKPOINT: $checkpoint"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo "📅 Time: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "📁 PWD: $(pwd)"
    echo "👤 User: $(whoami)"
    echo "🔢 PID: $$"
    echo "🔑 Sudo: Available"
    if [ -n "$details" ]; then
        echo "📋 Details: $details"
    fi
    echo "═══════════════════════════════════════════════════════════════"
}

# Enhanced error handler
error_handler() {
    local line_no=${1:-$LINENO}
    local exit_code=${2:-$?}
    local func="${3:-${FUNCNAME[1]:-main}}"

    log "ERROR" "═══════════════════════════════════════════════════════════════"
    log "ERROR" "Script failed at line $line_no in function $func"
    log "ERROR" "Exit code: $exit_code"
    log "ERROR" "Last command: ${BASH_COMMAND:-unknown}"
    log "ERROR" "Stack trace:"

    local frame=0
    while caller $frame; do
        ((frame++)) || true
    done

    log "ERROR" "═══════════════════════════════════════════════════════════════"
    log "ERROR" "Full log available at: $LOG_FILE"

    cleanup_and_exit $exit_code
}

# Cleanup function
cleanup_and_exit() {
    local exit_code=${1:-$?}
    log "INFO" "Performing cleanup (exit code: $exit_code)"

    # Auto-cleanup script and checksum
    if [ -f "$SCRIPT_PATH" ]; then
        rm -f "$SCRIPT_PATH" "${SCRIPT_PATH}.sha256" 2>/dev/null || true
        log "INFO" "Script self-deleted"
    fi

    # DO NOT DELETE secure-logger.sh - it's reusable
    log "INFO" "Preserving secure-logger.sh for future use"

    # Finalize logging
    finalize_logging $exit_code

    exit $exit_code
}

# Set traps
trap 'error_handler $LINENO $? ${FUNCNAME[0]:-main}' ERR
trap 'cleanup_and_exit' EXIT

# Start script
log "INFO" "=== RELOAD AND VALIDATE SCRIPT STARTED ==="
log "INFO" "Script version: GOLD STANDARD V5 - Admin User"
log "INFO" "User: $(whoami) (with sudo privileges)"
log "INFO" "Log file: $CICD_LOG_FILE"

# Store script path for cleanup
SCRIPT_PATH="${BASH_SOURCE[0]}"
SCRIPT_NAME=$(basename "$SCRIPT_PATH")

log "INFO" "═══════════════════════════════════════════════════════════════"
log "INFO" "RELOAD AND VALIDATION STARTED"
log "INFO" "═══════════════════════════════════════════════════════════════"
log "INFO" "Version: V5 - Gold Standard (Admin)"
log "INFO" "Script: $SCRIPT_NAME"
log "INFO" "User: $(whoami)"
log "INFO" "UID: $(id -u)"
log "INFO" "PID: $$"
log "INFO" "Log file: $LOG_FILE"
log "INFO" "Secure Logger: $SECURE_LOGGER_PATH"

debug_checkpoint "INITIALIZATION" "Starting service reload and validation"

# Get CI/CD log configuration from environment
export CICD_LOG_PATH="${CICD_LOG_PATH:-/var/log/CiCd}"
export CICD_LOG_FILE="${CICD_LOG_FILE:-instagram-platform-cicd.log}"
export CICD_LOG_GROUP="${CICD_LOG_GROUP:-instagram-cicd}"

# Timestamp validation (security check)
SCRIPT_TIMESTAMP=$(stat -c %Y "$SCRIPT_PATH" 2>/dev/null || date +%s)
CURRENT_TIME=$(date +%s)
TIME_DIFF=$((CURRENT_TIME - SCRIPT_TIMESTAMP))
MAX_AGE="${SCRIPT_MAX_AGE_SECONDS:-600}"

if [ $TIME_DIFF -gt $MAX_AGE ]; then
    log "ERROR" "Script timestamp validation failed (age: ${TIME_DIFF}s, max: ${MAX_AGE}s)"
    exit 1
fi

log "SUCCESS" "Script timestamp validated (${TIME_DIFF}s old)"

debug_checkpoint "ENVIRONMENT_VALIDATION" "Checking required variables"

# .env file should already be loaded by validate-and-exec-ci-script.sh
# Validate only minimal required variables
REQUIRED_VARS=(
    "DEPLOYMENT_PATH"
    "SYSTEMD_SERVICE"
    "ENVIRONMENT"
)

log "INFO" "Validating required environment variables..."
missing_vars=0
for var in "${REQUIRED_VARS[@]}"; do
    if [[ -z "${!var:-}" ]]; then
        log "ERROR" "Required variable $var is not set"
        safe_increment missing_vars
    else
        log "SUCCESS" "✓ $var = ${!var}"
    fi
done

if [ $missing_vars -gt 0 ]; then
    log "ERROR" "$missing_vars required variables are missing!"
    exit 1
fi

# Optional but useful variables
GSM_PROJECT_ID="${GSM_PROJECT_ID:-check-it-out-${ENVIRONMENT}}"
# Increased timeouts for complex stack (init container, redis, sentinel, postgres, app)
SERVICE_START_TIMEOUT="${SERVICE_START_TIMEOUT:-600}"  # 10 minutes default
SERVICE_STOP_TIMEOUT="${SERVICE_STOP_TIMEOUT:-60}"    # 1 minute for stop

log "SUCCESS" "All required variables validated"

# Display configuration
log "INFO" "Service reload configuration:"
log "INFO" "  Environment: $ENVIRONMENT"
log "INFO" "  Deployment path: $DEPLOYMENT_PATH"
log "INFO" "  Systemd service: $SYSTEMD_SERVICE"
log "INFO" "  GSM Project ID: $GSM_PROJECT_ID"
log "INFO" "  Start timeout: ${SERVICE_START_TIMEOUT}s"
log "INFO" "  User: $(whoami) (admin with sudo)"

debug_checkpoint "DEPLOYMENT_VERIFICATION" "Checking deployment directory"

# Verify deployment directory exists
if [ ! -d "${DEPLOYMENT_PATH}" ]; then
    log "ERROR" "Deployment path not found: ${DEPLOYMENT_PATH}"
    exit 1
fi

# Change to deployment directory
cd "${DEPLOYMENT_PATH}"
log "SUCCESS" "Changed to deployment directory"

# Count and list files
total_files=$(find . -maxdepth 1 -type f | wc -l)
log "INFO" "Total files in deployment: $total_files"

# Check for critical files
critical_files=("docker-compose-${ENVIRONMENT}.yml" "systemd-wrapper.sh" ".env")
critical_found=0
for file in "${critical_files[@]}"; do
    if [ -f "$file" ]; then
        perms=$(stat -c "%a" "$file")
        owner=$(stat -c "%U:%G" "$file")
        size=$(stat -c%s "$file")
        log "SUCCESS" "✓ Critical file: $file ($perms $owner ${size}B)"
        safe_increment critical_found
    else
        log "WARN" "✗ Missing: $file"
    fi
done

log "INFO" "Found $critical_found of ${#critical_files[@]} critical files"

debug_checkpoint "SERVICE_CHECK" "Verifying systemd service"

# Check if systemd service exists (NO sudo - running as root)
if ! systemctl list-unit-files "${SYSTEMD_SERVICE}.service" >/dev/null 2>&1; then
    log "ERROR" "Systemd service ${SYSTEMD_SERVICE} not found"
    exit 1
fi

log "SUCCESS" "Service ${SYSTEMD_SERVICE} exists"

# Get service info (NO sudo - running as root)
SERVICE_USER=$(systemctl show "${SYSTEMD_SERVICE}" --property=User --value 2>/dev/null || echo "unknown")
SERVICE_GROUP=$(systemctl show "${SYSTEMD_SERVICE}" --property=Group --value 2>/dev/null || echo "unknown")
SERVICE_STATE=$(systemctl is-active "${SYSTEMD_SERVICE}" 2>&1 || echo "unknown")

log "INFO" "Service user: ${SERVICE_USER}:${SERVICE_GROUP}"
log "INFO" "Current state: ${SERVICE_STATE}"

# Reset failed state if needed (NO sudo - running as root)
FAILED_CHECK=$(systemctl show "${SYSTEMD_SERVICE}" --property=Result 2>&1 | grep -oP 'Result=\K.*' || echo "unknown")

if [ "$SERVICE_STATE" = "failed" ] || [ "$FAILED_CHECK" = "exit-code" ] || [ "$FAILED_CHECK" = "failure" ]; then
    log "WARN" "Service in failed state - resetting..."
    systemctl reset-failed "${SYSTEMD_SERVICE}" || true
    log "SUCCESS" "Failed state cleared"
fi

debug_checkpoint "SERVICE_RELOAD" "Reloading service"

# Force actual restart by stop + start (not reload-or-restart)
log "INFO" "Stopping service ${SYSTEMD_SERVICE}..."

# Get current PID to verify actual restart
OLD_PID=$(systemctl show "${SYSTEMD_SERVICE}" --property=MainPID --value 2>/dev/null || echo "0")
log "INFO" "Current MainPID: ${OLD_PID}"

# Stop the service
if systemctl stop "${SYSTEMD_SERVICE}"; then
    log "SUCCESS" "Service stopped successfully"
    sleep 2  # Brief wait between stop and start
else
    log "WARN" "Service stop had issues but continuing"
fi

# Start the service
log "INFO" "Starting service ${SYSTEMD_SERVICE}..."
log "INFO" "Stack includes: init container, postgres, redis, sentinel, app"

if systemctl start "${SYSTEMD_SERVICE}"; then
    log "SUCCESS" "Service start command accepted"
    audit_log "SERVICE_RESTART" "${SYSTEMD_SERVICE}" "INITIATED"
    
    # Verify PID changed
    sleep 3
    NEW_PID=$(systemctl show "${SYSTEMD_SERVICE}" --property=MainPID --value 2>/dev/null || echo "0")
    log "INFO" "New MainPID: ${NEW_PID}"
    
    if [ "$OLD_PID" = "$NEW_PID" ] && [ "$NEW_PID" != "0" ]; then
        log "ERROR" "PID did not change - service may not have restarted!"
        exit 1
    fi
else
    log "ERROR" "Failed to start ${SYSTEMD_SERVICE} service"
    systemctl status "${SYSTEMD_SERVICE}" --no-pager -n 50 2>&1 || true
    exit 1
fi

debug_checkpoint "JOURNAL_VALIDATION" "Starting journal-based health monitoring"

# Function to validate stack health via journal
validate_stack_health() {
    local timeout="${SERVICE_START_TIMEOUT:-360}"
    local start_time=$(date +%s)
    local patterns_found=0
    local required_patterns=4  # gsm, pg, redis, app
    local errors_found=0
    local max_errors=10
    
    log "INFO" "Monitoring journal for health patterns (timeout: ${timeout}s)..."
    log "INFO" "Required: GSM init, PostgreSQL, Redis, Application"
    
    # Track what we've found
    local gsm_ok=0 pg_ok=0 redis_ok=0 app_ok=0
    
    # Use timeout command to ensure we don't hang forever
    timeout $((timeout + 10)) journalctl -fu "${SYSTEMD_SERVICE}" --since "1 minute ago" 2>/dev/null | while IFS= read -r line; do
        # Stream to CI/CD
        echo "[JOURNAL] $line"
        
        # Check elapsed time
        local current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        
        if [ $elapsed -gt $timeout ]; then
            log "ERROR" "Timeout after ${elapsed}s waiting for healthy stack"
            return 1
        fi
        
        # Pattern matching for health indicators
        case "$line" in
            *"google-secrets-init"*"exited"*"code 0"*)
                if [ $gsm_ok -eq 0 ]; then
                    log "SUCCESS" "✅ GSM init completed successfully"
                    gsm_ok=1
                    ((patterns_found++))
                fi
                ;;
            *"google-secrets-init"*"exited"*"code 1"*)
                log "ERROR" "❌ GSM init failed with exit code 1"
                ((errors_found++))
                ;;
            *"database system is ready to accept connections"*)
                if [ $pg_ok -eq 0 ]; then
                    log "SUCCESS" "✅ PostgreSQL is ready"
                    pg_ok=1
                    ((patterns_found++))
                fi
                ;;
            *"Ready to accept connections"*"tcp"*)
                if [ $redis_ok -eq 0 ]; then
                    log "SUCCESS" "✅ Redis is ready"
                    redis_ok=1
                    ((patterns_found++))
                fi
                ;;
            *"Started InstagramPlatformApplication"*|*"Started Application"*|*"Tomcat started on port"*|*"CheckItOut Backend Started Successfully"*)
                if [ $app_ok -eq 0 ]; then
                    log "SUCCESS" "✅ Application started successfully"
                    app_ok=1
                    ((patterns_found++))
                fi
                # If we see the final success message, we're definitely done
                if [[ "$line" == *"CheckItOut Backend Started Successfully"* ]]; then
                    log "SUCCESS" "✅ Final startup confirmation received"
                    patterns_found=$required_patterns  # Force completion
                fi
                ;;
            *"ERROR"*|*"FATAL"*|*"Failed"*|*"exception"*|*"Exception"*)
                if [[ ! "$line" =~ "test-connection" ]]; then  # Ignore test connection errors
                    log "WARN" "⚠️ Error detected: ${line:0:200}"
                    ((errors_found++))
                    
                    # Critical errors should fail fast
                    if [[ "$line" =~ "FATAL" ]] || [[ "$line" =~ "OutOfMemoryError" ]]; then
                        log "ERROR" "Critical error detected - failing fast"
                        return 1
                    fi
                fi
                ;;
        esac
        
        # Progress update every 30 seconds
        if [ $((elapsed % 30)) -eq 0 ] && [ $elapsed -gt 0 ]; then
            log "INFO" "Progress: ${patterns_found}/${required_patterns} patterns found (${elapsed}s elapsed)"
            log "INFO" "Status: GSM=$gsm_ok PG=$pg_ok Redis=$redis_ok App=$app_ok Errors=$errors_found"
        fi
        
        # Check if all required patterns found
        if [ $patterns_found -ge $required_patterns ]; then
            log "SUCCESS" "✅ All ${required_patterns} required services are healthy!"
            log "INFO" "Validation completed in ${elapsed} seconds"
            # Set success flag before breaking
            echo "VALIDATION_SUCCESS" > /tmp/validation_result_$$
            # Kill the journalctl process to stop following logs
            pkill -f "journalctl -fu ${SYSTEMD_SERVICE}" 2>/dev/null || true
            break
        fi
        
        # Too many errors - fail
        if [ $errors_found -gt 10 ]; then
            log "ERROR" "Too many errors detected ($errors_found) - aborting"
            return 1
        fi
    done
    
    # Check if validation succeeded (using file flag to avoid subshell issues)
    if [ -f "/tmp/validation_result_$$" ]; then
        rm -f /tmp/validation_result_$$
        return 0
    fi
    
    # If we got here via break, it means success
    if [ $patterns_found -ge $required_patterns ]; then
        return 0
    fi
    
    # Return based on the pipeline exit status (but ignore SIGPIPE from pkill)
    local pipe_status=${PIPESTATUS[0]}
    if [ $pipe_status -eq 141 ] || [ $pipe_status -eq 143 ]; then
        # 141 = SIGPIPE, 143 = SIGTERM - both are expected when we kill journalctl
        return 0
    fi
    return $pipe_status
}

# Run the validation
if validate_stack_health; then
    log "SUCCESS" "Stack validation completed successfully"
else
    log "ERROR" "Stack validation failed"
    
    # Show current state for debugging
    log "ERROR" "Current service status:"
    systemctl status "${SYSTEMD_SERVICE}" --no-pager -n 20 2>&1 || true
    
    log "ERROR" "Current container status:"
    cd "${DEPLOYMENT_PATH}" && docker compose ps 2>&1 || true
    
    exit 1
fi

debug_checkpoint "CONTAINER_VERIFICATION" "Final container health check"

# Wait briefly for Docker health checks to catch up (but don't fail if they're slow)
log "INFO" "Waiting for Docker health checks to stabilize..."
sleep 5

# Final verification of container states
log "INFO" "Final container verification:"
cd "${DEPLOYMENT_PATH}"

# Check each critical container
declare -a services=("google-secrets-init" "postgres" "redis" "app")
for service in "${services[@]}"; do
    state=$(docker compose -f "docker-compose-${ENVIRONMENT}.yml" ps "$service" --format json 2>/dev/null | jq -r '.State // "unknown"' 2>/dev/null || echo "unknown")
    health=$(docker compose -f "docker-compose-${ENVIRONMENT}.yml" ps "$service" --format json 2>/dev/null | jq -r '.Health // "N/A"' 2>/dev/null || echo "N/A")
    
    if [ "$service" = "google-secrets-init" ]; then
        # Init container should be exited with code 0
        exit_code=$(docker compose -f "docker-compose-${ENVIRONMENT}.yml" ps "$service" --format json 2>/dev/null | jq -r '.ExitCode // "1"' 2>/dev/null || echo "1")
        if [ "$state" = "exited" ] && [ "$exit_code" = "0" ]; then
            log "SUCCESS" "✅ $service: completed successfully (exit 0)"
        else
            log "WARN" "⚠️ $service: state=$state, exit=$exit_code"
        fi
    else
        # Other services should be running
        if [ "$state" = "running" ]; then
            # For app service, "starting" health is acceptable if the app logs show it's ready
            if [ "$service" = "app" ] && [ "$health" = "starting" ]; then
                log "INFO" "ℹ️ $service: $state (health: $health - Docker healthcheck pending, but app is ready)"
            else
                log "SUCCESS" "✅ $service: $state (health: $health)"
            fi
        else
            log "WARN" "⚠️ $service: $state (health: $health)"
        fi
    fi
done

audit_log "CONTAINER_CHECK" "${ENVIRONMENT}" "COMPLETED"

debug_checkpoint "FINAL_VERIFICATION" "Performing final checks"

# Get elapsed time from start
SCRIPT_END_TIME=$(date +%s)
TOTAL_TIME=$((SCRIPT_END_TIME - SCRIPT_TIMESTAMP))

# Final service check
FINAL_STATE=$(systemctl is-active "${SYSTEMD_SERVICE}" 2>&1 || echo "unknown")

if [ "$FINAL_STATE" = "active" ]; then
    log "SUCCESS" "═══════════════════════════════════════════════════════════════"
    log "SUCCESS" "✅ RELOAD AND VALIDATION COMPLETED SUCCESSFULLY"
    log "SUCCESS" "═══════════════════════════════════════════════════════════════"
    log "INFO" "Service: ${SYSTEMD_SERVICE} is active and running"
    log "INFO" "Environment: $ENVIRONMENT"
    log "INFO" "Deployment path: $DEPLOYMENT_PATH"
    log "INFO" "Total validation time: ${TOTAL_TIME}s"
    log "INFO" "Executed by: $(whoami) (admin user)"
    log "SUCCESS" "═══════════════════════════════════════════════════════════════"
    
    audit_log "RELOAD_VALIDATE_COMPLETE" "${SYSTEMD_SERVICE}" "SUCCESS"
else
    log "ERROR" "═══════════════════════════════════════════════════════════════"
    log "ERROR" "❌ RELOAD AND VALIDATION FAILED"
    log "ERROR" "═══════════════════════════════════════════════════════════════"
    log "ERROR" "Service ${SYSTEMD_SERVICE} final state: $FINAL_STATE"
    
    audit_log "RELOAD_VALIDATE_COMPLETE" "${SYSTEMD_SERVICE}" "FAILED"
    exit 1
fi

log "SUCCESS" "✅ RELOAD AND VALIDATION FINISHED"

# Success exit
exit 0
