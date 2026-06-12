#!/bin/bash
# Pre-deployment backup script v2 [SECURE VERSION - MANDATORY LOGGER]
# This script creates a backup of the current deployment before updates
# FIXES: Already running as root, so no sudo needed
# C09 FIX: Removed checksum file handling - checksum passed as parameter to validator
# V2: Makes secure-logger.sh mandatory and uses secure logging throughout

set -euo pipefail

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
        if lsattr "$location" 2>/dev/null | grep -q '^....i'; then
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

# MANDATORY CHECK: Verify logger functions are available
if ! type -t init_secure_logging >/dev/null 2>&1; then
    echo "[FATAL] secure-logger.sh was sourced but init_secure_logging function not found!" >&2
    echo "[FATAL] The secure-logger.sh file may be corrupted or incomplete." >&2
    exit 1
fi

if ! type -t log >/dev/null 2>&1; then
    echo "[FATAL] secure-logger.sh was sourced but log function not found!" >&2
    exit 1
fi

# Initialize secure logging
init_secure_logging "pre-deployment-backup" "${ENVIRONMENT:-test}"

log "INFO" "=== PRE-DEPLOYMENT BACKUP STARTED (v2 - SECURE) ==="
log "INFO" "Script: $(basename "$0")"
log "INFO" "User: $(whoami)"
log "INFO" "Effective UID: $(id -u)"
log "INFO" "PID: $"
log "INFO" "Secure Logger: $SECURE_LOGGER_PATH"

# Store script path for cleanup (CRITICAL)
SCRIPT_PATH="$0"
SCRIPT_DIR=$(dirname "$SCRIPT_PATH")
SCRIPT_NAME=$(basename "$SCRIPT_PATH")

log "INFO" "Script path: $SCRIPT_PATH"

# Timestamp validation (security check)
SCRIPT_TIMESTAMP=$(stat -c %Y "$0" 2>/dev/null || date +%s)
CURRENT_TIME=$(date +%s)
TIME_DIFF=$((CURRENT_TIME - SCRIPT_TIMESTAMP))

if [ $TIME_DIFF -gt 600 ]; then
    log "ERROR" "Script timestamp validation failed (age: ${TIME_DIFF} seconds, max allowed: 600)"
    exit 1
fi

log "INFO" "Script timestamp validated (${TIME_DIFF} seconds old)"

# Enhanced cleanup function with secure logging
cleanup_and_exit() {
    local exit_code=${1:-$?}
    log "INFO" "Cleanup initiated with exit code: $exit_code"

    # Remove temporary files
    rm -f /tmp/backup-manifest-$.txt 2>/dev/null || true

    # CRITICAL: Self-delete the script
    if [ -f "$SCRIPT_PATH" ]; then
        log "INFO" "Removing script: $SCRIPT_PATH"
        rm -f "$SCRIPT_PATH" 2>/dev/null || log "WARN" "Could not remove script"
    fi

    # DO NOT DELETE secure-logger.sh - it's reusable
    log "INFO" "Preserving secure-logger.sh for future use"

    log "INFO" "Cleanup completed, exiting with code: $exit_code"
    
    # Finalize logging before exit
    finalize_logging $exit_code
    exit $exit_code
}

# Enhanced error handler using secure logger
error_handler() {
    local exit_code=$?
    log_error "Script failed at line ${BASH_LINENO[0]}" $exit_code
    log_error "Failed command: ${BASH_COMMAND}" $exit_code
    cleanup_and_exit $exit_code
}

# FIXED: Set traps for all signals
trap error_handler ERR
trap 'cleanup_and_exit 0' EXIT
trap 'log "WARN" "Received INT signal"; cleanup_and_exit 130' INT
trap 'log "WARN" "Received TERM signal"; cleanup_and_exit 143' TERM

# Environment variables are already loaded by the validator script
log "INFO" "Environment variables should be pre-loaded by validator script"

# Check if environment variables are actually loaded
if [[ -z "${ENVIRONMENT:-}" ]]; then
    log_error "Environment variables not loaded! ENVIRONMENT is not set." 1
fi

# Debug environment
log "DEBUG" "Environment variables:"
env | grep -E '^(ENVIRONMENT|DEPLOYMENT_PATH|BACKUP_BASE_PATH|RETENTION_COUNT|GITHUB_RUN_ID)' | while read -r line; do
    log "DEBUG" "  $line"
done

# Validate required variables
REQUIRED_VARS=("ENVIRONMENT" "DEPLOYMENT_PATH" "BACKUP_BASE_PATH" "RETENTION_COUNT" "GITHUB_RUN_ID")
for var in "${REQUIRED_VARS[@]}"; do
    if [[ -z "${!var:-}" ]]; then
        log_error "Required variable $var is not set" 1
    fi
done

log "SUCCESS" "All required variables validated"

# Generate timestamp and backup name
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="${TIMESTAMP}_${ENVIRONMENT}_pre-deployment_backup"

# Define paths
SOURCE_DIR="${DEPLOYMENT_PATH}"
RUNTIME_DIR="${DEPLOYMENT_PATH%/deployment}/runtime"
BACKUP_DIR="${BACKUP_BASE_PATH}/${BACKUP_NAME}"

log "INFO" "Backup configuration:"
log "INFO" "  Environment: $ENVIRONMENT"
log "INFO" "  Source: $SOURCE_DIR"
log "INFO" "  Runtime: $RUNTIME_DIR"
log "INFO" "  Destination: $BACKUP_DIR"

# Check if source exists
if [[ ! -d "$SOURCE_DIR" ]] || [[ -z "$(ls -A "$SOURCE_DIR" 2>/dev/null)" ]]; then
    log "WARN" "Source directory doesn't exist or is empty: $SOURCE_DIR"
    echo "BACKUP_CREATED=false"
    echo "BACKUP_LOCATION=none"
    cleanup_and_exit 0
fi

# H08 FIX: Count source files and size before backup
log "INFO" "Counting source files before backup..."
SOURCE_FILE_COUNT=$(find "$SOURCE_DIR" -type f 2>/dev/null | wc -l || echo "0")
SOURCE_SIZE_KB=$(du -sk "$SOURCE_DIR" 2>/dev/null | cut -f1 || echo "0")
if [[ -d "$RUNTIME_DIR" ]]; then
    RUNTIME_FILE_COUNT=$(find "$RUNTIME_DIR" -type f 2>/dev/null | wc -l || echo "0")
    RUNTIME_SIZE_KB=$(du -sk "$RUNTIME_DIR" 2>/dev/null | cut -f1 || echo "0")
else
    RUNTIME_FILE_COUNT=0
    RUNTIME_SIZE_KB=0
fi
TOTAL_SOURCE_FILES=$((SOURCE_FILE_COUNT + RUNTIME_FILE_COUNT))
TOTAL_SOURCE_SIZE_KB=$((SOURCE_SIZE_KB + RUNTIME_SIZE_KB))
log "INFO" "Source stats: $TOTAL_SOURCE_FILES files, ${TOTAL_SOURCE_SIZE_KB}KB"

log "INFO" "Creating backup directory..."
if ! mkdir -p "$BACKUP_DIR"; then
    log_error "Failed to create backup directory: $BACKUP_DIR" 1
fi
audit_log "CREATE_BACKUP_DIR" "$BACKUP_DIR" "SUCCESS"

# Copy deployment files
log "INFO" "Copying deployment files..."
mkdir -p "$BACKUP_DIR/deployment"

if cp -rp "$SOURCE_DIR"/* "$BACKUP_DIR/deployment/" 2>/dev/null; then
    log "SUCCESS" "Deployment files copied"
    audit_log "COPY_DEPLOYMENT" "$SOURCE_DIR" "SUCCESS"
else
    log "WARN" "Some deployment files could not be copied"
    audit_log "COPY_DEPLOYMENT" "$SOURCE_DIR" "PARTIAL"
fi

# Copy hidden files
if cp -rp "$SOURCE_DIR"/.[^.]* "$BACKUP_DIR/deployment/" 2>/dev/null; then
    log "SUCCESS" "Hidden files copied"
else
    log "DEBUG" "No hidden files to copy or copy failed"
fi

# Copy runtime files if they exist
if [[ -d "$RUNTIME_DIR" ]]; then
    log "INFO" "Copying runtime files..."
    mkdir -p "$BACKUP_DIR/runtime"

    if cp -rp "$RUNTIME_DIR"/* "$BACKUP_DIR/runtime/" 2>/dev/null; then
        log "SUCCESS" "Runtime files copied"
        audit_log "COPY_RUNTIME" "$RUNTIME_DIR" "SUCCESS"
    else
        log "WARN" "Some runtime files could not be copied"
        audit_log "COPY_RUNTIME" "$RUNTIME_DIR" "PARTIAL"
    fi

    if cp -rp "$RUNTIME_DIR"/.[^.]* "$BACKUP_DIR/runtime/" 2>/dev/null; then
        log "SUCCESS" "Runtime hidden files copied"
    else
        log "DEBUG" "No runtime hidden files to copy"
    fi
else
    log "INFO" "No runtime directory found at: $RUNTIME_DIR"
fi

# H08 FIX: Verify backup integrity
log "INFO" "Verifying backup integrity..."
BACKUP_FILE_COUNT=$(find "$BACKUP_DIR" -type f 2>/dev/null | wc -l || echo "0")
BACKUP_SIZE_KB=$(du -sk "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo "0")

# Calculate expected vs actual with 5% tolerance for size
SIZE_TOLERANCE=$((TOTAL_SOURCE_SIZE_KB / 20))  # 5% tolerance
SIZE_DIFF=$((BACKUP_SIZE_KB - TOTAL_SOURCE_SIZE_KB))
SIZE_DIFF_ABS=${SIZE_DIFF#-}  # Absolute value

if [[ "$BACKUP_FILE_COUNT" -ne "$TOTAL_SOURCE_FILES" ]]; then
    log "WARN" "File count mismatch! Source: $TOTAL_SOURCE_FILES, Backup: $BACKUP_FILE_COUNT"
    log "WARN" "Human intervention may be needed to verify backup completeness"
elif [[ "$SIZE_DIFF_ABS" -gt "$SIZE_TOLERANCE" ]]; then
    log "WARN" "Size mismatch! Source: ${TOTAL_SOURCE_SIZE_KB}KB, Backup: ${BACKUP_SIZE_KB}KB (diff: ${SIZE_DIFF}KB)"
    log "WARN" "Human intervention may be needed to verify backup completeness"
else
    log "SUCCESS" "Backup integrity verified: $BACKUP_FILE_COUNT files, ${BACKUP_SIZE_KB}KB"
fi

# Create backup manifest
log "INFO" "Creating backup manifest..."
MANIFEST_FILE="$BACKUP_DIR/backup-info.txt"
cat > "$MANIFEST_FILE" << EOF
Backup: $BACKUP_NAME
Created: $(date)
Environment: $ENVIRONMENT
GitHub Run: $GITHUB_RUN_ID
Script: $SCRIPT_NAME
Size: $(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo "unknown")
Files: $(find "$BACKUP_DIR" -type f 2>/dev/null | wc -l || echo "0")
Deployment files: $(find "$BACKUP_DIR/deployment" -type f 2>/dev/null | wc -l || echo "0")
Runtime files: $(find "$BACKUP_DIR/runtime" -type f 2>/dev/null | wc -l || echo "0")
Secure Logger: YES (from $SECURE_LOGGER_PATH)
--- Integrity Check ---
Source files: $TOTAL_SOURCE_FILES
Backup files: $BACKUP_FILE_COUNT
Source size: ${TOTAL_SOURCE_SIZE_KB}KB
Backup size: ${BACKUP_SIZE_KB}KB
Verification: $(if [[ "$BACKUP_FILE_COUNT" -eq "$TOTAL_SOURCE_FILES" ]] && [[ "$SIZE_DIFF_ABS" -le "$SIZE_TOLERANCE" ]]; then echo "PASSED"; else echo "WARNING - Manual check recommended"; fi)
EOF

log "SUCCESS" "Backup manifest created"
audit_log "CREATE_MANIFEST" "$MANIFEST_FILE" "SUCCESS"

# CORRECT: Clean up old backups - NO SUDO NEEDED, already running as root
log "INFO" "Starting cleanup of old backups..."
cd "$BACKUP_BASE_PATH" || {
    log_error "Cannot change to backup directory: $BACKUP_BASE_PATH" 1
}

# Check if chattr command exists
if ! command -v chattr &> /dev/null; then
    log "WARN" "chattr command not found, attempting with full path"
    CHATTR_CMD="/usr/bin/chattr"
else
    CHATTR_CMD="chattr"
fi

# Remove immutable flags from old backups - NO SUDO NEEDED
log "INFO" "Removing immutable flags from old backups..."
find . -maxdepth 1 -type d \( -name "*_pre-deployment_backup" -o -name "*_post-deployment_backup" -o -name "*_backup" \) -mtime +7 -print0 2>/dev/null | \
while IFS= read -r -d '' old_backup; do
    log "DEBUG" "Removing immutable flag from: $old_backup"
    # NO SUDO - already root
    if $CHATTR_CMD -R -i "$old_backup" 2>&1; then
        log "DEBUG" "Successfully removed immutable flag from: $old_backup"
    else
        log "WARN" "Failed to remove immutable flag from: $old_backup (may not have been set)"
    fi
done

# Count backups before cleanup
BEFORE_CLEANUP=$(find . -maxdepth 1 -type d \( -name "*_pre-deployment_backup" -o -name "*_post-deployment_backup" -o -name "*_backup" \) 2>/dev/null | wc -l)
log "INFO" "Found $BEFORE_CLEANUP total backups before cleanup"

# Remove backups older than 7 days - NO SUDO NEEDED
log "INFO" "Removing backups older than 7 days..."
find . -maxdepth 1 -type d \( -name "*_pre-deployment_backup" -o -name "*_post-deployment_backup" -o -name "*_backup" \) -mtime +7 -print0 2>/dev/null | \
while IFS= read -r -d '' old_backup; do
    # H03 fix: Validate variable before rm -rf
    if [[ -z "${old_backup:-}" ]] || [[ "${old_backup}" == "/" ]] || [[ "${old_backup}" == "//" ]] || [[ "${old_backup}" == "." ]] || [[ "${old_backup}" == "./" ]]; then
        log "ERROR" "Invalid backup path, refusing to delete: $old_backup"
        continue
    fi
    log "INFO" "Removing old backup: $old_backup"
    # NO SUDO - already root
    if rm -rf "$old_backup" 2>&1; then
        log "SUCCESS" "Removed: $old_backup"
        audit_log "DELETE_OLD_BACKUP" "$old_backup" "SUCCESS"
    else
        log "WARN" "Failed to remove: $old_backup"
        audit_log "DELETE_OLD_BACKUP" "$old_backup" "FAILED"
    fi
done

# Apply retention count policy
if [[ "$RETENTION_COUNT" -gt 0 ]]; then
    log "INFO" "Applying retention policy (keeping last $RETENTION_COUNT pre-deployment backups)..."

    # List and remove excess pre-deployment backups
    ls -t -d *_pre-deployment_backup 2>/dev/null | tail -n +$((RETENTION_COUNT + 1)) | \
    while read -r excess_backup; do
        # H03 fix: Validate variable before rm -rf
        if [[ -z "${excess_backup:-}" ]] || [[ "${excess_backup}" == "/" ]] || [[ "${excess_backup}" == "//" ]] || [[ "${excess_backup}" == "." ]] || [[ "${excess_backup}" == "./" ]]; then
            log "ERROR" "Invalid excess backup path, refusing to delete: $excess_backup"
            continue
        fi
        log "INFO" "Removing excess backup: $excess_backup"
        # NO SUDO - already root
        $CHATTR_CMD -R -i "$excess_backup" 2>&1 || true
        if rm -rf "$excess_backup" 2>&1; then
            log "SUCCESS" "Removed excess: $excess_backup"
            audit_log "DELETE_EXCESS_BACKUP" "$excess_backup" "SUCCESS"
        else
            log "WARN" "Failed to remove excess: $excess_backup"
            audit_log "DELETE_EXCESS_BACKUP" "$excess_backup" "FAILED"
        fi
    done
fi

# Count remaining backups
PRE_DEPLOY_COUNT=$(find . -maxdepth 1 -type d -name "*_pre-deployment_backup" 2>/dev/null | wc -l)
POST_DEPLOY_COUNT=$(find . -maxdepth 1 -type d -name "*_post-deployment_backup" 2>/dev/null | wc -l)
OTHER_COUNT=$(find . -maxdepth 1 -type d -name "*_backup" ! -name "*_pre-deployment_backup" ! -name "*_post-deployment_backup" 2>/dev/null | wc -l)
REMAINING_BACKUPS=$((PRE_DEPLOY_COUNT + POST_DEPLOY_COUNT + OTHER_COUNT))
REMOVED_COUNT=$((BEFORE_CLEANUP - REMAINING_BACKUPS))

log "SUCCESS" "Cleanup complete: removed $REMOVED_COUNT old backups"
log "INFO" "Remaining backups: Pre-deploy=$PRE_DEPLOY_COUNT, Post-deploy=$POST_DEPLOY_COUNT, Other=$OTHER_COUNT"

# Final summary
BACKUP_SIZE=$(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo "unknown")
BACKUP_FILES=$(find "$BACKUP_DIR" -type f 2>/dev/null | wc -l || echo "0")

# Log summary using secure logger
log "SUCCESS" "═══════════════════════════════════════════════════════════════"
log "SUCCESS" "✅ BACKUP COMPLETED SUCCESSFULLY"
log "SUCCESS" "═══════════════════════════════════════════════════════════════"
log "SUCCESS" "📁 Location: $BACKUP_DIR"
log "SUCCESS" "💾 Size: $BACKUP_SIZE"
log "SUCCESS" "📑 Files: $BACKUP_FILES"
log "SUCCESS" "🗂️  Total backups: $REMAINING_BACKUPS"
log "SUCCESS" "🔒 Secure Logger: ACTIVE"
log "SUCCESS" "═══════════════════════════════════════════════════════════════"

# Output for workflow
echo "BACKUP_CREATED=true"
echo "BACKUP_LOCATION=$BACKUP_DIR"

audit_log "BACKUP_COMPLETE" "$BACKUP_DIR" "SUCCESS"

log "SUCCESS" "Backup completed successfully"
log "INFO" "=== PRE-DEPLOYMENT BACKUP FINISHED ==="

# Script will self-delete via EXIT trap
exit 0
