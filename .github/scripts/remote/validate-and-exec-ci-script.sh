#!/bin/bash
# Script: validate-and-exec-ci-script.sh (SECURE VERSION V10.1)
# Purpose: Validates and executes CI/CD scripts with immutability protection
#
# FIXES IN V10.1:
# - Removes ALL immutability flags recursively before exit
# - Enables nuclear cleanup to run without sudo
# - Cleans immutability from pwd, $HOME, and common script directories
#
# FIXES IN V10:
# - Handles immutable secure-logger.sh properly
# - Checks and temporarily removes immutability from dependencies
# - Restores immutability after execution
#
# All fixes from V9 still apply

set -euo pipefail

# Enhanced logging function
log() {
    local level="$1"
    shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*" | tee -a /tmp/ci-debug.log
}

# Store own path for self-deletion
SELF_PATH="$0"
SELF_NAME=$(basename "$0")

log "INFO" "=== VALIDATE AND EXEC SCRIPT STARTED V10.1 (SECURE + NUCLEAR CLEANUP) ==="
log "INFO" "Script: $SELF_NAME"
log "INFO" "User: $(whoami)"
log "INFO" "UID: $(id -u)"
log "INFO" "Working Directory: $(pwd)"
log "INFO" "PID: $$"

# Check if running as root
IS_ROOT=0
if [ "$(id -u)" -eq 0 ]; then
    IS_ROOT=1
    log "INFO" "Running with ROOT privileges"
else
    log "WARN" "Running as non-root user (UID: $(id -u))"
fi

# Find and source environment file (same as V9)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CURRENT_DIR="$(pwd)"

ENV_LOCATIONS=()
if [ -n "${ENV_FILE_PATH:-}" ]; then
    ENV_LOCATIONS+=("$ENV_FILE_PATH")
fi

ENV_LOCATIONS+=(
    "$CURRENT_DIR/.env"
    ".env"
    "$SCRIPT_DIR/.env"
    "$HOME/.env"
    "/var/lib/instagram-scripts-admin/.env"
    "$CURRENT_DIR/.envPreBackup"
    ".envPreBackup"
    "$SCRIPT_DIR/.envPreBackup"
    "$HOME/.envPreBackup"
    "/var/lib/instagram-scripts-admin/.envPreBackup"
)

ENV_FOUND=0
ENV_FILE_PATH_FOUND=""
log "INFO" "Searching for environment file..."
for env_file in "${ENV_LOCATIONS[@]}"; do
    if [ -f "$env_file" ]; then
        log "INFO" "Found environment file at: $env_file"

        # V10: Check if .env is immutable and handle it
        if lsattr "$env_file" 2>/dev/null | grep -q '^....i'; then
            log "INFO" ".env file is immutable, temporarily removing protection"
            if [ $IS_ROOT -eq 1 ]; then
                chattr -i "$env_file" || log "WARN" "Failed to remove immutability from .env"
            fi
        fi

        set -a
        source "$env_file"
        set +a

        # Restore immutability
        if [ $IS_ROOT -eq 1 ]; then
            chattr +i "$env_file" 2>/dev/null || true
        fi

        log "INFO" "Environment loaded from $env_file"
        ENV_FOUND=1
        ENV_FILE_PATH_FOUND="$env_file"
        break
    fi
done

if [ $ENV_FOUND -eq 0 ]; then
    log "ERROR" "No environment file found in any expected location"
    exit 1
fi

# Check arguments
if [ $# -ne 2 ]; then
    log "ERROR" "Usage: $0 <script-path> <expected-checksum>"
    exit 1
fi

SCRIPT="$1"
EXPECTED_CHECKSUM="$2"

log "INFO" "Script to validate: $SCRIPT"

# Enhanced security validation function (from V9)
validate_script_security() {
    local script_path="$1"
    local expected_checksum="$2"
    local metadata_path="${script_path}.metadata"

    log "INFO" "🔒 Performing enhanced security validation..."

    # 1. Check if script exists
    if [ ! -f "$script_path" ]; then
        log "ERROR" "Script not found: $script_path"
        return 1
    fi

    # 2. Check if metadata exists
    if [ ! -f "$metadata_path" ]; then
        log "ERROR" "Script metadata not found: $metadata_path"
        log "ERROR" "This script was not uploaded securely!"
        return 1
    fi

    # 3. Verify both files are immutable
    if ! lsattr "$script_path" 2>/dev/null | grep -q '^....i'; then
        log "ERROR" "Script is not immutable - potential tampering!"
        return 1
    fi

    if ! lsattr "$metadata_path" 2>/dev/null | grep -q '^....i'; then
        log "ERROR" "Metadata is not immutable - potential tampering!"
        return 1
    fi

    log "INFO" "✅ Both script and metadata are immutable"

    # 4. Parse metadata
    if ! command -v jq >/dev/null 2>&1; then
        # Fallback to grep if jq not available
        local upload_timestamp=$(grep -o '"upload_timestamp":[[:space:]]*[0-9]*' "$metadata_path" | grep -o '[0-9]*$')
        local metadata_checksum=$(grep -o '"checksum":[[:space:]]*"[^"]*"' "$metadata_path" | sed 's/.*"checksum":[[:space:]]*"\([^"]*\)"/\1/')
        local locked=$(grep -o '"locked":[[:space:]]*[a-z]*' "$metadata_path" | grep -o '[a-z]*$')
    else
        local upload_timestamp=$(jq -r '.upload_timestamp' "$metadata_path")
        local metadata_checksum=$(jq -r '.checksum' "$metadata_path")
        local locked=$(jq -r '.locked' "$metadata_path")
    fi

    log "INFO" "Metadata: timestamp=$upload_timestamp, locked=$locked"

    # 5. Verify locked status
    if [ "$locked" != "true" ]; then
        log "ERROR" "Script is not marked as locked in metadata!"
        return 1
    fi

    # 6. Validate timestamp (prevent replay attacks)
    local current_time=$(date +%s)
    local age=$((current_time - upload_timestamp))
    local max_age=${SCRIPT_EXECUTION_TIMEOUT_SECONDS:-600}

    log "INFO" "Script age: ${age}s (max allowed: ${max_age}s)"

    if [ $age -gt $max_age ]; then
        log "ERROR" "Script too old - potential replay attack!"
        log "ERROR" "Age: ${age}s, Max: ${max_age}s"
        return 1
    fi

    if [ $age -lt 0 ]; then
        log "ERROR" "Script timestamp is in the future - clock skew or tampering!"
        return 1
    fi

    # 7. Verify checksum matches
    if [ "$metadata_checksum" != "$expected_checksum" ]; then
        log "ERROR" "Metadata checksum doesn't match expected!"
        log "ERROR" "Expected: $expected_checksum"
        log "ERROR" "Metadata: $metadata_checksum"
        return 1
    fi

    # 8. Calculate actual checksum (requires removing immutability temporarily)
    if [ $IS_ROOT -eq 1 ]; then
        # Temporarily remove immutability to read file
        chattr -i "$script_path" 2>/dev/null || true
        local actual_checksum=$(sha256sum "$script_path" | awk '{print $1}')
        # Restore immutability
        chattr +i "$script_path" 2>/dev/null || true
    else
        # Non-root can still read immutable files
        local actual_checksum=$(sha256sum "$script_path" | awk '{print $1}')
    fi

    if [ "$expected_checksum" != "$actual_checksum" ]; then
        log "ERROR" "Script checksum validation failed!"
        log "ERROR" "Expected: $expected_checksum"
        log "ERROR" "Actual: $actual_checksum"
        return 1
    fi

    log "INFO" "✅ All security validations passed"
    return 0
}

# V10: Enhanced cleanup function that handles dependencies
cleanup_and_exit() {
    local exit_code=${1:-$?}
    log "INFO" "Cleanup initiated with exit code: $exit_code"

    # Remove immutability and delete scripts
    if [ $IS_ROOT -eq 1 ]; then
        # Remove immutability from executed script and metadata
        if [ -n "${SCRIPT:-}" ] && [ -f "$SCRIPT" ]; then
            log "INFO" "Removing immutability from executed script"
            chattr -i "$SCRIPT" "${SCRIPT}.metadata" 2>/dev/null || true
            rm -f "$SCRIPT" "${SCRIPT}.metadata" 2>/dev/null || true
        fi

        # V10: Clean up secure-logger.sh if it was made mutable
        if [ -n "${SECURE_LOGGER_PATH:-}" ] && [ -f "$SECURE_LOGGER_PATH" ]; then
            if [ "${LOGGER_WAS_IMMUTABLE:-0}" -eq 1 ]; then
                log "INFO" "Restoring immutability to secure-logger.sh"
                chattr +i "$SECURE_LOGGER_PATH" 2>/dev/null || true
            fi
        fi

        # V10: Restore .env immutability if needed
        if [ -n "${ENV_FILE_PATH_FOUND:-}" ] && [ -f "$ENV_FILE_PATH_FOUND" ]; then
            if ! lsattr "$ENV_FILE_PATH_FOUND" 2>/dev/null | grep -q '^....i'; then
                log "INFO" "Restoring immutability to .env"
                chattr +i "$ENV_FILE_PATH_FOUND" 2>/dev/null || true
            fi
        fi

        # Remove immutability from self
        if [ -f "$SELF_PATH" ]; then
            log "INFO" "Removing immutability from validator script"
            chattr -i "$SELF_PATH" "${SELF_PATH}.metadata" 2>/dev/null || true
        fi
    fi

    # Delete self on success
    if [ $exit_code -eq 0 ]; then
        log "INFO" "Cleaning up validator script"
        rm -f "$SELF_PATH" "${SELF_PATH}.metadata" 2>/dev/null || true
    else
        log "WARN" "Keeping scripts for debugging (exit code: $exit_code)"
    fi

    # V10.1: Nuclear cleanup preparation - remove ALL immutability
    # This ensures the nuclear cleanup script can run without sudo
    if [ $IS_ROOT -eq 1 ]; then
        log "INFO" "Removing all immutability flags for nuclear cleanup preparation..."
        
        # Remove immutability from current working directory
        if command -v chattr >/dev/null 2>&1; then
            chattr -R -i "$(pwd)" 2>/dev/null || true
            log "INFO" "Removed immutability from: $(pwd)"
            
            # Also remove from home directory if different
            if [ "$(pwd)" != "$HOME" ]; then
                chattr -R -i "$HOME" 2>/dev/null || true
                log "INFO" "Removed immutability from: $HOME"
            fi
            
            # Remove from common script locations
            for dir in "/var/lib/instagram-scripts-admin" "/tmp" "/var/tmp"; do
                if [ -d "$dir" ]; then
                    chattr -R -i "$dir" 2>/dev/null || true
                    log "INFO" "Removed immutability from: $dir"
                fi
            done
        else
            log "WARN" "chattr command not found - cannot remove immutability"
        fi
        
        log "INFO" "Nuclear cleanup preparation completed"
    else
        log "INFO" "Non-root execution - skipping immutability cleanup"
    fi
    
    log "INFO" "Cleanup completed"
    exit $exit_code
}

# Set traps
trap 'cleanup_and_exit $?' ERR EXIT
trap 'log "WARN" "Received INT signal"; cleanup_and_exit 130' INT
trap 'log "WARN" "Received TERM signal"; cleanup_and_exit 143' TERM

# Perform security validation
if ! validate_script_security "$SCRIPT" "$EXPECTED_CHECKSUM"; then
    log "ERROR" "Security validation failed!"
    exit 1
fi

# Make script executable (remove immutability first if root)
if [ $IS_ROOT -eq 1 ]; then
    chattr -i "$SCRIPT" 2>/dev/null || true
fi
chmod +x "$SCRIPT"
log "INFO" "Made script executable: $SCRIPT"

# Execute the script (enhanced from V10)
TIMEOUT=${SCRIPT_EXECUTION_TIMEOUT_SECONDS:-300}
log "INFO" "Script execution timeout: ${TIMEOUT}s"
log "INFO" "Executing script: $SCRIPT"
log "INFO" "════════════════════════════════════════════════════════════════"

# Execute from script directory with environment
SCRIPT_DIR="$(dirname "$SCRIPT")"
SCRIPT_NAME="$(basename "$SCRIPT")"

# V10: Enhanced secure-logger.sh handling
SECURE_LOGGER_PATH=""
LOGGER_WAS_IMMUTABLE=0

for location in "$CURRENT_DIR" "$SCRIPT_DIR" "$HOME" "/var/lib/instagram-scripts-admin"; do
    if [ -f "$location/secure-logger.sh" ]; then
        SECURE_LOGGER_PATH="$location/secure-logger.sh"

        # V10: Check if logger is immutable
        if lsattr "$SECURE_LOGGER_PATH" 2>/dev/null | grep -q '^....i'; then
            LOGGER_WAS_IMMUTABLE=1
            log "INFO" "secure-logger.sh is immutable, will handle during execution"
        fi

        log "INFO" "Found secure-logger.sh at: $SECURE_LOGGER_PATH"
        break
    fi
done

# Build execution command
EXEC_COMMAND="cd '$SCRIPT_DIR'"

# Add environment sourcing
if [ -n "${ENV_FILE_PATH_FOUND:-}" ] && [ -f "$ENV_FILE_PATH_FOUND" ]; then
    # V10: Handle immutable .env during execution
    if [ $IS_ROOT -eq 1 ]; then
        EXEC_COMMAND="$EXEC_COMMAND && chattr -i '$ENV_FILE_PATH_FOUND' 2>/dev/null || true"
    fi
    EXEC_COMMAND="$EXEC_COMMAND && set -a && source '$ENV_FILE_PATH_FOUND' && set +a"
    if [ $IS_ROOT -eq 1 ]; then
        EXEC_COMMAND="$EXEC_COMMAND && chattr +i '$ENV_FILE_PATH_FOUND' 2>/dev/null || true"
    fi
fi

# Add secure-logger sourcing if found
if [ -n "$SECURE_LOGGER_PATH" ]; then
    # V10: Handle immutable logger
    if [ $IS_ROOT -eq 1 ] && [ $LOGGER_WAS_IMMUTABLE -eq 1 ]; then
        EXEC_COMMAND="$EXEC_COMMAND && chattr -i '$SECURE_LOGGER_PATH' 2>/dev/null || true"
    fi
    EXEC_COMMAND="$EXEC_COMMAND && source '$SECURE_LOGGER_PATH'"
    # Note: We restore immutability in cleanup, not here
fi

# Add script execution
EXEC_COMMAND="$EXEC_COMMAND && ./'$SCRIPT_NAME'"

log "INFO" "Final execution command: $EXEC_COMMAND"

# Execute with timeout
if [ $IS_ROOT -eq 1 ]; then
    timeout --preserve-status --signal=TERM --kill-after=10 $TIMEOUT \
        /bin/bash -c "$EXEC_COMMAND" &
else
    timeout --preserve-status --signal=TERM --kill-after=10 $TIMEOUT \
        sudo -E /bin/bash -c "$EXEC_COMMAND" &
fi

EXEC_PID=$!
wait $EXEC_PID
EXIT_CODE=$?

log "INFO" "════════════════════════════════════════════════════════════════"
log "INFO" "Script execution completed with exit code: $EXIT_CODE"

# Handle timeout
if [ $EXIT_CODE -eq 124 ] || [ $EXIT_CODE -eq 137 ]; then
    log "ERROR" "Script execution timed out after ${TIMEOUT} seconds"
    cleanup_and_exit 1
fi

# Success - cleanup will happen in trap
exit $EXIT_CODE
