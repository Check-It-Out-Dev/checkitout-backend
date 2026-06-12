#!/bin/bash
# Script: deploy-env-validator.sh (GOLD STANDARD V3)
# Purpose: Validate deployment .env file contains all required variables
# Modified: 2025-01-31 - Implemented secure-logger.sh transfer mechanism
#
# GOLD STANDARD V3:
# 1. Uses secure-logger.sh for consistent logging
# 2. Secure logger is transferred with script and deleted after use
# 3. Includes all helper functions (safe_increment, retry_operation)
# 4. Enhanced debug checkpoints
# 5. Consistent error handling
# 6. No hardcoded values - all from environment

set -euo pipefail

# Force all output to be unbuffered and visible
export TERM=xterm

# Source secure logging library from current directory
if [ -f ./secure-logger.sh ]; then
    source ./secure-logger.sh
else
    echo "ERROR: secure-logger.sh not found in current directory" >&2
    exit 1
fi

# Initialize secure logging for this operation
init_secure_logging "env-validator" "${ENVIRONMENT:-test}"

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
    log "ERROR" "Full log available at: ${LOG_FILE:-not set}"

    cleanup_and_exit $exit_code
}

# Cleanup function
cleanup_and_exit() {
    local exit_code=${1:-$?}
    log "INFO" "Performing cleanup (exit code: $exit_code)"

    # Append to CI/CD log
    if [ -n "${LOG_FILE:-}" ] && [ -f "$LOG_FILE" ]; then
        local cicd_log="${CICD_LOG_PATH:-/var/log/CiCd}/${CICD_LOG_FILE:-instagram-platform-cicd.log}"
        if [ -f "$cicd_log" ]; then
            echo "=== ENV-VALIDATOR LOG START ===" >> "$cicd_log"
            cat "$LOG_FILE" >> "$cicd_log" 2>/dev/null || true
            echo "=== ENV-VALIDATOR LOG END ===" >> "$cicd_log"
        fi
    fi

    exit $exit_code
}

# Set traps
trap 'error_handler $LINENO $? ${FUNCNAME[0]:-main}' ERR
trap 'cleanup_and_exit' EXIT

# Start script
log "INFO" "=== DEPLOY ENV VALIDATOR SCRIPT STARTED ==="
log "INFO" "Script version: GOLD STANDARD V3 - Secure Logger Transfer"
log "INFO" "Log file: ${CICD_LOG_FILE:-not set}"

log "INFO" "═══════════════════════════════════════════════════════════════"
log "INFO" "DEPLOYMENT ENV VALIDATOR STARTED"
log "INFO" "═══════════════════════════════════════════════════════════════"
log "INFO" "Version: V2 - Gold Standard"
log "INFO" "Script: $(basename "$0")"
log "INFO" "User: $(whoami)"
log "INFO" "PID: $$"
log "INFO" "Log file: ${LOG_FILE:-not set}"

debug_checkpoint "INITIALIZATION" "Parsing command line arguments"

# Get command line arguments
ENV_FILE="${1:-}"
ENVIRONMENT="${2:-${ENVIRONMENT:-test}}"

# Validate arguments
if [ -z "$ENV_FILE" ] || [ ! -f "$ENV_FILE" ]; then
    log "ERROR" "Usage: $0 <env-file> [environment]"
    log "ERROR" "Env file not found or not specified: $ENV_FILE"
    exit 1
fi

log "INFO" "Validating deployment .env file: $ENV_FILE"
log "INFO" "Environment: $ENVIRONMENT"

debug_checkpoint "FILE_ANALYSIS" "Analyzing env file structure"

# Get file stats
FILE_SIZE=$(stat -c%s "$ENV_FILE")
FILE_LINES=$(wc -l < "$ENV_FILE")
FILE_PERMS=$(stat -c "%a" "$ENV_FILE")
FILE_OWNER=$(stat -c "%U:%G" "$ENV_FILE")

log "INFO" "File statistics:"
log "INFO" "  Size: $FILE_SIZE bytes"
log "INFO" "  Lines: $FILE_LINES"
log "INFO" "  Permissions: $FILE_PERMS"
log "INFO" "  Owner: $FILE_OWNER"

if [ $FILE_SIZE -lt ${MIN_ENV_FILE_SIZE:-1000} ]; then
    log "WARN" "File seems too small for deployment configuration!"
fi

debug_checkpoint "VARIABLE_DEFINITION" "Defining required variables"

# Required deployment variables from environment or defaults
# NOTE: Sensitive values like DATABASE_USER, DATABASE_PASSWORD, etc.
# should NEVER be in .env files - they come from Google Secret Manager at runtime
DEFAULT_REQUIRED_VARS=(
    "ENVIRONMENT"
    "CONTAINER_GROUP_NAME"
    "CONTAINER_USER_NAME"
    "DEPLOYMENT_PATH"
    "DATABASE_NAME"
    "SERVER_PORT"
    "REGISTRY_PRIMARY"
    "IMAGE_TAG"
    "GSM_INIT_IMAGE_FULL"
    "GSM_SECRET_PREFIX"
    "GCP_SA_KEY_PATH"
    "APP_CONTAINER_NAME"
    "DB_CONTAINER_NAME"
    "MAIN_APP_IMAGE"  # C10 fix: Ensure MAIN_APP_IMAGE is validated
)

# Allow custom required vars from environment
REQUIRED_VARS=("${ENV_VALIDATOR_REQUIRED_VARS[@]:-${DEFAULT_REQUIRED_VARS[@]}}")

log "INFO" "Checking ${#REQUIRED_VARS[@]} required variables"

debug_checkpoint "ENV_LOADING" "Loading environment file"

# Source the env file with error handling
# Note: We need to source in the current shell, not a subshell
log "INFO" "Attempting to source environment file: $ENV_FILE"

# First, let's check what's in the file
log "INFO" "Checking for required variables in file before sourcing:"
for check_var in CONTAINER_GROUP_NAME CONTAINER_USER_NAME DATABASE_NAME SERVER_PORT; do
    if grep -q "^${check_var}=" "$ENV_FILE"; then
        value=$(grep "^${check_var}=" "$ENV_FILE" | head -1 | cut -d'=' -f2-)
        log "INFO" "  Found in file: ${check_var}=${value:0:30}..."
    else
        log "WARN" "  NOT found in file: ${check_var}"
    fi
done

log "INFO" "First 5 non-comment lines of env file:"
grep -v '^#' "$ENV_FILE" | grep -v '^$' | head -5 | while IFS= read -r line; do
    if [[ ! "$line" =~ (PASSWORD|SECRET|TOKEN|KEY) ]]; then
        log "INFO" "  $line"
    fi
done

# Now source the file
set -a  # Mark all new variables for export
# shellcheck disable=SC1090
if source "$ENV_FILE"; then
    set +a  # Stop marking variables for export
    log "SUCCESS" "Environment file loaded successfully"
    
    # Debug: Show some loaded variables
    log "INFO" "Sample of loaded variables after sourcing:"
    log "INFO" "  ENVIRONMENT=${ENVIRONMENT:-not set}"
    log "INFO" "  CONTAINER_GROUP_NAME=${CONTAINER_GROUP_NAME:-not set}"
    log "INFO" "  DATABASE_NAME=${DATABASE_NAME:-not set}"
    log "INFO" "  SERVER_PORT=${SERVER_PORT:-not set}"
    log "INFO" "  APP_CONTAINER_NAME=${APP_CONTAINER_NAME:-not set}"
    log "INFO" "  DB_CONTAINER_NAME=${DB_CONTAINER_NAME:-not set}"
else
    set +a  # Stop marking variables for export
    log "ERROR" "Failed to source environment file"
    exit 1
fi

debug_checkpoint "VALIDATION" "Validating required variables"

# Check each required variable
missing_count=0
found_count=0

for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var:-}" ]; then
        log "ERROR" "Missing required variable: $var"
        safe_increment missing_count
    else
        # Don't log sensitive values
        if [[ "$var" =~ PASSWORD|TOKEN|SECRET|KEY ]]; then
            log "SUCCESS" "✓ Found: $var=***"
        else
            log "SUCCESS" "✓ Found: $var=${!var}"
        fi
        safe_increment found_count
    fi
done

debug_checkpoint "SUMMARY" "Generating validation summary"

# Summary
log "INFO" "═══════════════════════════════════════════════════════════════"
log "INFO" "Validation Summary:"
log "INFO" "  Environment: $ENVIRONMENT"
log "INFO" "  File: $ENV_FILE"
log "INFO" "  Required variables: ${#REQUIRED_VARS[@]}"
log "INFO" "  Found: $found_count"
log "INFO" "  Missing: $missing_count"
log "INFO" "═══════════════════════════════════════════════════════════════"

if [ $missing_count -gt 0 ]; then
    log "ERROR" "❌ VALIDATION FAILED - Missing $missing_count required variables"
    exit 1
fi

log "SUCCESS" "✅ ALL REQUIRED DEPLOYMENT VARIABLES ARE PRESENT"

# Success exit
exit 0
