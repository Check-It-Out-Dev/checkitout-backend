#!/bin/bash
# Script: immutable-aware-backup.sh (GOLD STANDARD V3)
# Purpose: Handles backups with proper immutable file handling
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
init_secure_logging "immutable-backup" "${ENVIRONMENT:-test}"

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

# Wrapper functions for helper script operations
helper_with_retry() {
    local operation="$1"
    shift
    retry_operation 3 1 "$HELPER_SCRIPT_PATH" "$operation" "$@"
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

    # Append to CI/CD log
    if [ -f "$LOG_FILE" ]; then
        local cicd_log="${CICD_LOG_PATH:-/var/log/CiCd}/${CICD_LOG_FILE:-instagram-platform-cicd.log}"
        if [ -f "$cicd_log" ]; then
            echo "=== IMMUTABLE-BACKUP LOG START ===" >> "$cicd_log"
            cat "$LOG_FILE" >> "$cicd_log" 2>/dev/null || true
            echo "=== IMMUTABLE-BACKUP LOG END ===" >> "$cicd_log"
        fi
    fi

    exit $exit_code
}

# Set traps
trap 'error_handler $LINENO $? ${FUNCNAME[0]:-main}' ERR
trap 'cleanup_and_exit' EXIT INT TERM

# Store script path for cleanup
SCRIPT_PATH="$0"
SCRIPT_NAME=$(basename "$0")
CHECKSUM_FILE="${SCRIPT_PATH}.sha256"

log "INFO" "═══════════════════════════════════════════════════════════════"
log "INFO" "IMMUTABLE-AWARE BACKUP STARTED"
log "INFO" "═══════════════════════════════════════════════════════════════"
log "INFO" "Version: V2 - Gold Standard"
log "INFO" "Script: $SCRIPT_NAME"
log "INFO" "User: $(whoami)"
log "INFO" "PID: $$"
log "INFO" "Log file: $LOG_FILE"

debug_checkpoint "INITIALIZATION" "Validating script timestamp"

# Timestamp validation (security check)
SCRIPT_TIMESTAMP=$(stat -c %Y "$0" 2>/dev/null || date +%s)
CURRENT_TIME=$(date +%s)
TIME_DIFF=$((CURRENT_TIME - SCRIPT_TIMESTAMP))
MAX_AGE="${SCRIPT_MAX_AGE_SECONDS:-600}"

if [ $TIME_DIFF -gt $MAX_AGE ]; then
    log "ERROR" "Script timestamp validation failed (age: ${TIME_DIFF}s, max: ${MAX_AGE}s)"
    exit 1
fi

log "SUCCESS" "Script timestamp validated (${TIME_DIFF}s old)"

debug_checkpoint "ENV_LOADING" "Loading environment file"

# Source environment variables from the first argument
ENV_FILE="${1:-.envBackup}"
if [[ ! -f "$ENV_FILE" ]]; then
    log "ERROR" "Environment file not found: $ENV_FILE"
    log "ERROR" "Usage: $0 <env-file>"
    exit 1
fi

if retry_operation 3 1 bash -c "source '$ENV_FILE'"; then
    source "$ENV_FILE"
    log "SUCCESS" "Environment loaded from: $ENV_FILE"
else
    log "ERROR" "Failed to source environment file"
    exit 1
fi

debug_checkpoint "VALIDATION" "Validating required variables"

# Validate required variables
REQUIRED_VARS=(
    "ENVIRONMENT"
    "DEPLOYMENT_PATH"
    "BACKUP_BASE_PATH"
    "RETENTION_COUNT"
    "BACKUP_TYPE"
    "GITHUB_RUN_ID"
    "UNIQUE_ID"
    "ADMIN_HOME"
    "HELPER_SCRIPT_PATH"
    "ROLLBACK_SCRIPT_PATH"
)

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

# Generate timestamp and backup name if not provided
TIMESTAMP="${TIMESTAMP:-$(date +%Y%m%d_%H%M%S)}"
BACKUP_NAME="${TIMESTAMP}_${ENVIRONMENT}_${BACKUP_TYPE}_backup"
BACKUP_DIR="${BACKUP_BASE_PATH}/${BACKUP_NAME}"

# Parse GitHub context if provided
GITHUB_CONTEXT="${GITHUB_CONTEXT:-{\}}"

log "INFO" "Backup configuration:"
log "INFO" "  Environment: $ENVIRONMENT"
log "INFO" "  Path: $DEPLOYMENT_PATH"
log "INFO" "  Type: $BACKUP_TYPE"
log "INFO" "  Backup dir: $BACKUP_DIR"

# Validate helper script exists
if [[ ! -f "$HELPER_SCRIPT_PATH" ]]; then
    log "ERROR" "Helper script not found: $HELPER_SCRIPT_PATH"
    exit 1
fi

# Function to determine directory structure
determine_structure() {
    local input_path="$1"

    if [[ "$input_path" =~ /deployment$ ]]; then
        log "INFO" "Path already points to deployment subdirectory - using new structure"
        DEPLOYMENT_DIR="$input_path"
        BASE_PATH="$(dirname "$input_path")"
        RUNTIME_PATH="$BASE_PATH/runtime"
        STRUCTURE_TYPE="new"
    elif [[ -d "${input_path}/deployment" ]]; then
        log "INFO" "Detected new directory structure with deployment/ subdirectory"
        BASE_PATH="$input_path"
        DEPLOYMENT_DIR="${input_path}/deployment"
        RUNTIME_PATH="${input_path}/runtime"
        STRUCTURE_TYPE="new"
    else
        log "INFO" "Using legacy flat directory structure"
        BASE_PATH="$input_path"
        DEPLOYMENT_DIR="$input_path"
        RUNTIME_PATH="$input_path/runtime"
        STRUCTURE_TYPE="legacy"
    fi

    log "INFO" "Path analysis complete:"
    log "INFO" "  Input path: $input_path"
    log "INFO" "  Base path: $BASE_PATH"
    log "INFO" "  Deployment dir: $DEPLOYMENT_DIR"
    log "INFO" "  Runtime path: $RUNTIME_PATH"
    log "INFO" "  Structure type: $STRUCTURE_TYPE"
}

# Function to count immutable files
count_immutable_files() {
    local total_count=0
    local deployment_count
    local runtime_count

    if [[ -d "$DEPLOYMENT_DIR" ]]; then
        deployment_count=$("$HELPER_SCRIPT_PATH" count-immutable "$DEPLOYMENT_DIR" 2>/dev/null || echo "0")
        deployment_count=${deployment_count:-0}
        deployment_count=$(echo "$deployment_count" | tr -d '\n' | grep -o '^[0-9]*' || echo "0")
        total_count=$((total_count + deployment_count))
        log "INFO" "Deployment immutable files: $deployment_count"
    fi

    if [[ -d "$RUNTIME_PATH" ]]; then
        runtime_count=$("$HELPER_SCRIPT_PATH" count-immutable "$RUNTIME_PATH" 2>/dev/null || echo "0")
        runtime_count=${runtime_count:-0}
        runtime_count=$(echo "$runtime_count" | tr -d '\n' | grep -o '^[0-9]*' || echo "0")
        total_count=$((total_count + runtime_count))
        log "INFO" "Runtime immutable files: $runtime_count"
    fi

    echo "$total_count"
}

# Function to remove immutable flags
remove_immutable_flags() {
    log "INFO" "Temporarily removing immutable flags for backup..."
    helper_with_retry "remove-immutable" "$DEPLOYMENT_DIR"
    if [[ -d "$RUNTIME_PATH" ]]; then
        helper_with_retry "remove-immutable" "$RUNTIME_PATH"
    fi
}

# Function to restore immutable flags
restore_immutable_flags() {
    log "INFO" "Restoring immutable flags..."

    if [[ "$STRUCTURE_TYPE" == "new" ]]; then
        helper_with_retry "restore-immutable" "$DEPLOYMENT_DIR"
    else
        if [ -d "$DEPLOYMENT_DIR/runtime" ]; then
            helper_with_retry "restore-immutable-exclude" "$DEPLOYMENT_DIR" "$DEPLOYMENT_DIR/runtime"
        else
            helper_with_retry "restore-immutable" "$DEPLOYMENT_DIR"
        fi
    fi
}

# Function to make backup files immutable
make_backup_immutable() {
    log "INFO" "Making backup files immutable..."

    if [[ "$STRUCTURE_TYPE" == "new" ]]; then
        helper_with_retry "restore-immutable" "$BACKUP_DIR/deployment"
    else
        if [ -d "$BACKUP_DIR/runtime" ]; then
            helper_with_retry "restore-immutable-exclude" "$BACKUP_DIR" "$BACKUP_DIR/runtime"
        else
            helper_with_retry "restore-immutable" "$BACKUP_DIR"
        fi
    fi
}

# Function to perform backup
perform_backup() {
    log "INFO" "Creating backup directory structure..."
    helper_with_retry "mkdir-p" "$BACKUP_DIR"

    if [[ "$STRUCTURE_TYPE" == "new" ]]; then
        helper_with_retry "mkdir-p" "$BACKUP_DIR/deployment"
        log "INFO" "Copying deployment files..."
        helper_with_retry "copy-contents" "$DEPLOYMENT_DIR" "$BACKUP_DIR/deployment"

        if [[ -d "$RUNTIME_PATH" ]]; then
            helper_with_retry "mkdir-p" "$BACKUP_DIR/runtime"
            log "INFO" "Copying runtime files..."
            helper_with_retry "copy-contents" "$RUNTIME_PATH" "$BACKUP_DIR/runtime"
        fi
    else
        log "INFO" "Copying all files..."
        helper_with_retry "copy-contents" "$DEPLOYMENT_DIR" "$BACKUP_DIR"
    fi

    log "SUCCESS" "Backup files copied successfully"
}

# Function to create metadata
create_metadata() {
    local immutable_count="$1"

    log "INFO" "Creating backup metadata..."

    # Create metadata JSON
    local metadata_json="{
  \"created\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
  \"timestamp\": \"$TIMESTAMP\",
  \"environment\": \"$ENVIRONMENT\",
  \"type\": \"$BACKUP_TYPE\",
  \"structure_type\": \"$STRUCTURE_TYPE\",
  \"base_path\": \"$BASE_PATH\",
  \"deployment_path\": \"$DEPLOYMENT_DIR\",
  \"runtime_path\": \"$RUNTIME_PATH\",
  \"backup_path\": \"$BACKUP_DIR\",
  \"immutable_files\": $immutable_count,
  \"has_runtime_dir\": $([ -d "$RUNTIME_PATH" ] && echo "true" || echo "false"),
  \"github_context\": ${GITHUB_CONTEXT}
}"

    echo "$metadata_json" | helper_with_retry "write-file" "$BACKUP_DIR/backup-metadata.json"
    log "SUCCESS" "Metadata created"
}

# Function to clean old backups
clean_old_backups() {
    log "INFO" "Cleaning old backups (keeping last $RETENTION_COUNT)..."
    helper_with_retry "clean-old-backups" "$BACKUP_BASE_PATH" "$RETENTION_COUNT"
}

# Main execution
main() {
    log "INFO" "=== IMMUTABLE-AWARE BACKUP SCRIPT STARTED ==="
    log "INFO" "Script version: GOLD STANDARD V3 - Secure Logger Transfer"
    log "INFO" "Log file: $CICD_LOG_FILE"

    debug_checkpoint "STRUCTURE_ANALYSIS" "Determining directory structure"

    # Determine structure
    determine_structure "$DEPLOYMENT_PATH"

    # Create backup directory
    helper_with_retry "mkdir-p" "$BACKUP_BASE_PATH"

    debug_checkpoint "SOURCE_CHECK" "Checking source directory"

    # Check if source exists
    if [[ ! -d "$DEPLOYMENT_DIR" ]]; then
        log "WARN" "Deployment path doesn't exist: $DEPLOYMENT_DIR"
        echo "backup-created=false" > ${ADMIN_HOME}/backup_status_${UNIQUE_ID}
        echo "backup-location=none" > ${ADMIN_HOME}/backup_location_${UNIQUE_ID}
        echo "backup-timestamp=$TIMESTAMP" > ${ADMIN_HOME}/backup_timestamp_${UNIQUE_ID}
        exit 0
    fi

    # Check if deployment directory has any content
    FILE_COUNT=$("$HELPER_SCRIPT_PATH" count-files "$DEPLOYMENT_DIR" 2>/dev/null || echo "0")
    FILE_COUNT=${FILE_COUNT:-0}
    FILE_COUNT=$(echo "$FILE_COUNT" | tr -d '[:space:]' | grep -o '^[0-9]*' || echo "0")
    log "INFO" "Found $FILE_COUNT files in deployment directory"

    if [[ $FILE_COUNT -eq 0 ]]; then
        log "WARN" "Deployment directory exists but is empty!"
        "$HELPER_SCRIPT_PATH" list-dir "$DEPLOYMENT_DIR" 2>&1 || true
    fi

    debug_checkpoint "IMMUTABLE_CHECK" "Checking for immutable files"

    # Check for immutable files
    IMMUTABLE_COUNT=$(count_immutable_files)
    IMMUTABLE_COUNT=${IMMUTABLE_COUNT:-0}
    IMMUTABLE_COUNT=$(echo "$IMMUTABLE_COUNT" | tr -d '[:space:]' | grep -o '^[0-9]*' || echo "0")
    log "INFO" "Found $IMMUTABLE_COUNT immutable files total"

    debug_checkpoint "BACKUP_EXECUTION" "Performing backup operation"

    # Handle backup based on immutable file presence
    if [[ "$IMMUTABLE_COUNT" -gt 0 ]]; then
        remove_immutable_flags
        perform_backup
        restore_immutable_flags
        make_backup_immutable
    else
        log "INFO" "No immutable files found, creating regular backup..."
        perform_backup
    fi

    # Create metadata
    create_metadata "$IMMUTABLE_COUNT"

    # Create rollback instructions if script exists
    if [[ -f "$ROLLBACK_SCRIPT_PATH" ]]; then
        log "INFO" "Creating rollback instructions..."
        chmod +x "$ROLLBACK_SCRIPT_PATH"
        helper_with_retry "exec-rollback-script" "$ROLLBACK_SCRIPT_PATH" "$BACKUP_DIR" "$ENVIRONMENT" "$STRUCTURE_TYPE" "$BASE_PATH" "$DEPLOYMENT_DIR" "$RUNTIME_PATH"
    else
        log "WARN" "Rollback script not found: $ROLLBACK_SCRIPT_PATH"
    fi

    debug_checkpoint "CLEANUP" "Cleaning old backups"

    # Clean old backups
    clean_old_backups

    debug_checkpoint "SUMMARY" "Generating backup summary"

    # Get backup statistics
    BACKUP_SIZE=$("$HELPER_SCRIPT_PATH" disk-usage "$BACKUP_DIR" 2>/dev/null || echo "unknown")
    BACKUP_FILES=$("$HELPER_SCRIPT_PATH" count-files "$BACKUP_DIR" 2>/dev/null || echo "0")

    # Summary
    log "SUCCESS" "═══════════════════════════════════════════════════════════════"
    log "SUCCESS" "✅ BACKUP COMPLETED SUCCESSFULLY"
    log "SUCCESS" "═══════════════════════════════════════════════════════════════"
    log "INFO" "📁 Location: $BACKUP_DIR"
    log "INFO" "💾 Size: $BACKUP_SIZE"
    log "INFO" "📑 Files: $BACKUP_FILES"
    log "INFO" "🔒 Immutable files: $IMMUTABLE_COUNT"
    log "INFO" "📂 Structure type: $STRUCTURE_TYPE"
    log "SUCCESS" "═══════════════════════════════════════════════════════════════"

    # Save outputs
    echo "$BACKUP_DIR" > ${ADMIN_HOME}/backup_location_${UNIQUE_ID}
    echo "backup-created=true" > ${ADMIN_HOME}/backup_status_${UNIQUE_ID}
    echo "backup-timestamp=$TIMESTAMP" > ${ADMIN_HOME}/backup_timestamp_${UNIQUE_ID}
}

# Run main function
main

log "SUCCESS" "✅ IMMUTABLE-AWARE BACKUP FINISHED"

# Success exit
exit 0
