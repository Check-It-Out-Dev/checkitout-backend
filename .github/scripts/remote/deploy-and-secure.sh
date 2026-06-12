#!/bin/bash
# Script: deploy-and-secure.sh v6.0 - FULL IMMUTABILITY PROTECTION
# Purpose: Deploy files with complete immutability chain
# Modified: 2025-02-01 - Eliminate ALL attack windows

set -euo pipefail

# Force all output to be unbuffered and visible
export TERM=xterm

# Get script directory and current directory for reference
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CURRENT_DIR="$(pwd)"

echo "[INFO] Script directory: $SCRIPT_DIR" >&2
echo "[INFO] Current directory: $CURRENT_DIR" >&2

# Source .env file first to get all environment variables
ENV_FOUND=0
for location in "$CURRENT_DIR" "$SCRIPT_DIR" "$HOME" "/var/lib/instagram-scripts-admin"; do
    if [ -f "$location/.env" ]; then
        echo "[INFO] Sourcing .env from: $location" >&2
        set -a
        source "$location/.env"
        set +a
        ENV_FOUND=1
        break
    fi
done

if [ $ENV_FOUND -eq 0 ]; then
    echo "[ERROR] .env file not found in any expected location" >&2
    exit 1
fi

# Source secure logging library
LOGGER_FOUND=0
for location in "$CURRENT_DIR" "$SCRIPT_DIR" "$HOME" "/var/lib/instagram-scripts-admin"; do
    if [ -f "$location/secure-logger.sh" ]; then
        echo "[INFO] Found secure-logger.sh in: $location" >&2
        source "$location/secure-logger.sh"
        LOGGER_FOUND=1
        break
    fi
done

if [ $LOGGER_FOUND -eq 0 ] || ! type -t init_secure_logging >/dev/null 2>&1; then
    echo "[ERROR] secure-logger.sh not found or failed to source" >&2
    exit 1
fi

# Initialize secure logging
init_secure_logging "deploy-secure" "${ENVIRONMENT:-test}"

# Helper functions with immutability support

# Function to validate and fix YAML files
validate_and_fix_yaml() {
    local yaml_file="$1"
    
    if [[ ! -f "$yaml_file" ]]; then
        log "ERROR" "YAML file not found: $yaml_file"
        return 1
    fi
    
    # Basic YAML validation - check for common issues
    if grep -q $'\t' "$yaml_file"; then
        log "WARN" "YAML file contains tabs, converting to spaces"
        # Convert tabs to spaces
        sed -i 's/\t/  /g' "$yaml_file"
    fi
    
    # Check for trailing whitespace
    if grep -q '[[:space:]]$' "$yaml_file"; then
        log "WARN" "YAML file has trailing whitespace, removing"
        sed -i 's/[[:space:]]*$//' "$yaml_file"
    fi
    
    # Verify it's not empty
    if [[ ! -s "$yaml_file" ]]; then
        log "ERROR" "YAML file is empty: $yaml_file"
        return 1
    fi
    
    log "SUCCESS" "YAML file validated: $(basename "$yaml_file")"
    return 0
}

copy_with_immutability() {
    local src="$1"
    local dst="$2"
    local src_was_immutable=0

    # Check if source is immutable
    if lsattr "$src" 2>/dev/null | grep -q '^....i'; then
        src_was_immutable=1
        log "INFO" "Source file is immutable, temporarily removing: $(basename "$src")"
        chattr -i "$src" || {
            log "ERROR" "Failed to remove immutability from source: $src"
            return 1
        }
    fi

    # Copy the file
    if cp -v "$src" "$dst"; then
        log "SUCCESS" "Copied: $(basename "$src")"

        # Restore source immutability if it was immutable
        if [ $src_was_immutable -eq 1 ]; then
            chattr +i "$src" || log "WARN" "Failed to restore immutability on source: $src"
        fi

        # Make destination immutable immediately
        chattr +i "$dst" || log "WARN" "Failed to make destination immutable: $dst"

        return 0
    else
        log "ERROR" "Failed to copy: $(basename "$src")"

        # Restore source immutability on failure
        if [ $src_was_immutable -eq 1 ]; then
            chattr +i "$src" || log "WARN" "Failed to restore immutability on source: $src"
        fi

        return 1
    fi
}

# Function to make a directory's contents immutable
make_directory_immutable() {
    local dir="$1"
    local count=0
    local failed=0

    log "INFO" "Making directory contents immutable: $dir"

    while IFS= read -r -d '' file; do
        local filename=$(basename "$file")
        
        # Remove error suppression to see actual errors
        if chattr +i "$file" 2>&1 | tee -a /tmp/chattr-debug.log; then
            ((count++)) || true
            log "SUCCESS" "Made immutable: $filename"
        else
            log "ERROR" "Failed to make immutable: $filename"
            ((failed++)) || true
            
            # Special handling for .env file - FORCE IT!
            if [[ "$filename" == ".env" ]]; then
                log "ERROR" "Critical: .env file failed to become immutable! Forcing..."
                
                # Debug information
                log "INFO" "Checking what's accessing .env..."
                
                # Find processes accessing the file
                if command -v lsof >/dev/null 2>&1; then
                    local pids=$(lsof -t "$file" 2>/dev/null || true)
                    if [ -n "$pids" ]; then
                        log "WARN" "Found processes accessing .env: $pids"
                        
                        # In production, we forcefully close file descriptors
                        log "WARN" "Forcing processes to release .env..."
                        for pid in $pids; do
                            # Try to get process name
                            local pname=$(ps -p $pid -o comm= 2>/dev/null || echo "unknown")
                            log "WARN" "Process $pid ($pname) has .env open"
                            
                            # Force close file descriptors for .env in that process
                            # This is aggressive but necessary for security
                            if [ -d "/proc/$pid/fd" ]; then
                                for fd in /proc/$pid/fd/*; do
                                    if [ -L "$fd" ]; then
                                        local link=$(readlink "$fd" 2>/dev/null || true)
                                        if [[ "$link" == *"/.env" ]] || [[ "$link" == "$file" ]]; then
                                            local fd_num=$(basename "$fd")
                                            log "WARN" "Closing fd $fd_num for pid $pid"
                                            # Use gdb to close the file descriptor if available
                                            if command -v gdb >/dev/null 2>&1; then
                                                gdb -p $pid -batch \
                                                    -ex "call close($fd_num)" \
                                                    -ex "detach" \
                                                    -ex "quit" >/dev/null 2>&1 || true
                                            fi
                                        fi
                                    fi
                                done
                            fi
                        done
                        
                        # Give processes a moment to release
                        sleep 0.5
                    fi
                fi
                
                # Now try multiple times with increasing force
                local retry_count=0
                local max_retries=5
                
                while [ $retry_count -lt $max_retries ]; do
                    ((retry_count++))
                    log "INFO" "Attempt $retry_count/$max_retries to make .env immutable"
                    
                    # Ensure ownership first
                    chown root:"$GROUP" "$file" 2>/dev/null || true
                    chmod 440 "$file" 2>/dev/null || true
                    
                    # Try to set immutable
                    if chattr +i "$file" 2>&1; then
                        log "SUCCESS" "Successfully made .env immutable on attempt $retry_count"
                        ((count++)) || true
                        ((failed--)) || true
                        break
                    else
                        if [ $retry_count -lt $max_retries ]; then
                            # Exponential backoff
                            local wait_time=$(echo "0.1 * 2^($retry_count-1)" | bc)
                            log "WARN" "Attempt $retry_count failed, waiting ${wait_time}s before retry"
                            sleep "$wait_time"
                            
                            # On later attempts, try more aggressive approaches
                            if [ $retry_count -ge 3 ]; then
                                log "WARN" "Using aggressive approach - copying file"
                                # Create a new inode by copying
                                cp -f "$file" "$file.tmp" 2>/dev/null || true
                                mv -f "$file.tmp" "$file" 2>/dev/null || true
                            fi
                        fi
                    fi
                done
                
                # Final check
                if ! lsattr "$file" 2>/dev/null | grep -q '^....i'; then
                    log "ERROR" "Failed to make .env immutable after $max_retries attempts"
                    log "ERROR" "This is a critical security failure"
                    
                    # Last resort - try with a fresh inode
                    log "WARN" "Last resort: creating fresh .env file"
                    cp "$file" "$file.new"
                    chown root:"$GROUP" "$file.new"
                    chmod 440 "$file.new"
                    if chattr +i "$file.new" 2>&1; then
                        mv -f "$file.new" "$file"
                        log "SUCCESS" "Fresh .env file is now immutable"
                        ((count++)) || true
                        ((failed--)) || true
                    else
                        rm -f "$file.new"
                        return 1
                    fi
                fi
            fi
        fi
    done < <(find "$dir" -maxdepth 1 -type f -print0)

    log "SUCCESS" "Made $count files immutable in $dir"
    
    if [ $failed -gt 0 ]; then
        log "ERROR" "$failed files failed to become immutable - this is a security risk!"
        return 1
    fi
    
    return 0
}

# Function to remove immutability from a directory
remove_directory_immutability() {
    local dir="$1"
    local count=0

    log "INFO" "Removing immutability from directory: $dir"

    while IFS= read -r -d '' file; do
        if chattr -i "$file" 2>/dev/null; then
            ((count++)) || true
        fi
    done < <(find "$dir" -maxdepth 1 -type f -print0)

    log "INFO" "Removed immutability from $count files"
}

# Enhanced error handler
error_handler() {
    local line_no=$1
    local exit_code=$2
    local func="${3:-main}"

    log_error "Script failed at line $line_no in function $func (exit code: $exit_code)" $exit_code
}

# Cleanup function
cleanup_and_exit() {
    local exit_code=${1:-$?}

    # Delete secure-logger.sh (no longer needed)
    if [ -f "${SCRIPT_DIR}/secure-logger.sh" ]; then
        # Remove immutability first if present
        chattr -i "${SCRIPT_DIR}/secure-logger.sh" 2>/dev/null || true
        rm -f "${SCRIPT_DIR}/secure-logger.sh" 2>/dev/null || true
        log "INFO" "Secure logger removed"
    fi

    # Auto-cleanup script
    SCRIPT_PATH="${BASH_SOURCE[0]}"
    if [ -f "$SCRIPT_PATH" ]; then
        # Script should already have immutability removed by validator
        rm -f "$SCRIPT_PATH" "${SCRIPT_PATH}.sha256" 2>/dev/null || true
        log "INFO" "Script self-deleted"
    fi

    exit $exit_code
}

# Set traps
trap 'error_handler $LINENO $? ${FUNCNAME[0]:-main}' ERR
trap 'cleanup_and_exit' EXIT

# Start script
log "INFO" "=== DEPLOY AND SECURE SCRIPT STARTED ==="
log "INFO" "Script version: v6.0 - Full Immutability Protection"
log "INFO" "Log file: $CICD_LOG_FILE"

# Validate script age (security check)
SCRIPT_PATH="${BASH_SOURCE[0]}"
SCRIPT_TIMESTAMP=$(stat -c %Y "$SCRIPT_PATH" 2>/dev/null || date +%s)
CURRENT_TIME=$(date +%s)
TIME_DIFF=$((CURRENT_TIME - SCRIPT_TIMESTAMP))
SCRIPT_TIMEOUT=${SCRIPT_EXECUTION_TIMEOUT_SECONDS:-600}

log "INFO" "Script age validation: ${TIME_DIFF}s (max: ${SCRIPT_TIMEOUT}s)"
if [ $TIME_DIFF -gt $SCRIPT_TIMEOUT ]; then
    log "ERROR" "Script too old! Age: ${TIME_DIFF}s, Max: ${SCRIPT_TIMEOUT}s"
    exit 1
fi

# Get arguments
DEPLOYMENT_PATH="${1:-${DEPLOYMENT_PATH:-}}"
ENVIRONMENT="${2:-${ENVIRONMENT:-}}"

# Validate arguments
if [[ -z "$DEPLOYMENT_PATH" ]] || [[ -z "$ENVIRONMENT" ]]; then
    log "ERROR" "Missing required arguments"
    exit 1
fi

# Set variables
GROUP="${CONTAINER_GROUP_NAME:-}"
DEPLOY_USER="${CONTAINER_USER_NAME:-}"
ADMIN_BASE="${ADMIN_PACKAGE_PATH:-/var/lib/instagram-scripts-admin}"
SOURCE_DIR="${ADMIN_BASE}/deployment-package"

# Validate required variables
if [[ -z "$GROUP" ]] || [[ -z "$DEPLOY_USER" ]]; then
    log "ERROR" "Required variables not set"
    exit 1
fi

# Log configuration
log "INFO" "=== DEPLOYMENT CONFIGURATION ==="
log "INFO" "Environment: $ENVIRONMENT"
log "INFO" "Deployment Path: $DEPLOYMENT_PATH"
log "INFO" "Source Directory: $SOURCE_DIR"
log "INFO" "================================"

# SECURITY CHECK: Verify source files are immutable
log "INFO" "🔒 Verifying source files are protected..."
UNPROTECTED_COUNT=0

# Check deployment package files
if [ -d "$SOURCE_DIR" ]; then
    while IFS= read -r -d '' file; do
        if ! lsattr "$file" 2>/dev/null | grep -q '^....i'; then
            log "WARN" "Source file not immutable: $(basename "$file")"
            ((UNPROTECTED_COUNT++)) || true
        fi
    done < <(find "$SOURCE_DIR" -maxdepth 1 -type f -print0)
fi

# Check .env in admin home
if [ -f "$ADMIN_BASE/.env" ]; then
    if ! lsattr "$ADMIN_BASE/.env" 2>/dev/null | grep -q '^....i'; then
        log "WARN" ".env file not immutable in admin home"
        ((UNPROTECTED_COUNT++)) || true
    fi
fi

if [ $UNPROTECTED_COUNT -gt 0 ]; then
    log "WARN" "$UNPROTECTED_COUNT source files are not immutable!"
    log "INFO" "Making source files immutable for protection..."

    # Make deployment package immutable
    if [ -d "$SOURCE_DIR" ]; then
        make_directory_immutable "$SOURCE_DIR"
    fi

    # Make .env immutable
    if [ -f "$ADMIN_BASE/.env" ]; then
        chattr +i "$ADMIN_BASE/.env" || log "WARN" "Failed to make .env immutable"
    fi
fi

# Prepare deployment directory
if [[ ! -d "$DEPLOYMENT_PATH" ]]; then
    log "INFO" "Creating deployment directory: $DEPLOYMENT_PATH"
    mkdir -p "$DEPLOYMENT_PATH"
else
    log "INFO" "Deployment directory exists, cleaning..."

    # Remove immutability from existing files before deletion
    remove_directory_immutability "$DEPLOYMENT_PATH"
    
    # Force remove with more aggressive cleanup
    log "INFO" "Force cleaning deployment directory..."
    
    # First try to remove all immutability recursively
    find "$DEPLOYMENT_PATH" -type f -exec chattr -i {} \; 2>/dev/null || true
    find "$DEPLOYMENT_PATH" -type d -exec chattr -i {} \; 2>/dev/null || true
    
    # Now forcefully remove everything
    find "$DEPLOYMENT_PATH" -mindepth 1 -delete 2>/dev/null || {
        # Fallback to rm -rf if find fails
        rm -rf "${DEPLOYMENT_PATH:?}"/* 2>/dev/null || true
        rm -rf "${DEPLOYMENT_PATH}"/.[!.]* 2>/dev/null || true
    }
fi

# Define essential files
ESSENTIAL_FILES=(
    "docker-compose-${ENVIRONMENT}.yml"
    "systemd-wrapper.sh"
    "postgres-entrypoint.sh"
    "sentinel-entrypoint.sh"
    "spring-boot-entrypoint.sh"
    "gsm-healthcheck.sh"
    "INIT_${ENVIRONMENT^^}_DATABASE.sql"
    "instagram-platform-${ENVIRONMENT}.service"
)

# Define executable scripts
EXECUTABLE_SCRIPTS=(
    "systemd-wrapper.sh"
    "postgres-entrypoint.sh"
    "sentinel-entrypoint.sh"
    "gsm-healthcheck.sh"
    "spring-boot-entrypoint.sh"
    "01-read-password.sh"
)

# Copy files with immutability protection
log "INFO" "Copying files with immutability protection..."
copied=0
failed=0

# Change to source directory
cd "$SOURCE_DIR"

# Copy each file with immutability handling
while IFS= read -r -d '' file; do
    if [[ "$file" == "." ]] || [[ "$file" == ".." ]]; then
        continue
    fi

    filename=$(basename "$file")

    # Temporarily remove immutability, copy, then re-apply
    if copy_with_immutability "$file" "$DEPLOYMENT_PATH/$filename"; then
        ((copied++)) || true
    else
        ((failed++)) || true
    fi
done < <(find . -maxdepth 1 -type f -print0)

# Handle .env file specially
if [[ ! -f "$DEPLOYMENT_PATH/.env" ]]; then
    log "INFO" "Copying .env from admin home..."
    if [[ -f "$ADMIN_BASE/.env" ]]; then
        if copy_with_immutability "$ADMIN_BASE/.env" "$DEPLOYMENT_PATH/.env"; then
            ((copied++)) || true
        else
            ((failed++)) || true
        fi
    else
        log "WARN" ".env file not found"
        touch "$DEPLOYMENT_PATH/.env"
        chattr +i "$DEPLOYMENT_PATH/.env"
    fi
fi

log "INFO" "Copy summary: $copied successful, $failed failed"

if [[ $failed -gt 0 ]]; then
    log "ERROR" "Some files failed to copy!"
    exit 1
fi

# At this point, all files in deployment directory should be immutable
# We need to temporarily remove immutability to:
# 1. Fix YAML files
# 2. Set permissions
# Then re-apply immutability

log "INFO" "Temporarily removing immutability for configuration..."
remove_directory_immutability "$DEPLOYMENT_PATH"

# Validate and fix Docker Compose YAML file
COMPOSE_FILE="$DEPLOYMENT_PATH/docker-compose-${ENVIRONMENT}.yml"
if [[ -f "$COMPOSE_FILE" ]]; then
    if validate_and_fix_yaml "$COMPOSE_FILE"; then
        log "SUCCESS" "Docker Compose file validated"
    else
        log "ERROR" "Failed to validate Docker Compose file"
        exit 1
    fi
fi

# Set permissions
log "INFO" "Setting file permissions..."

# Set read-only for all files
find "$DEPLOYMENT_PATH" -type f -exec chmod 444 {} \;

# Make scripts executable
for script in "${EXECUTABLE_SCRIPTS[@]}"; do
    if [[ -f "$DEPLOYMENT_PATH/$script" ]]; then
        # Use 755 instead of 750 to ensure containers can read the scripts
        chmod 755 "$DEPLOYMENT_PATH/$script"
        log "SUCCESS" "Made executable: $script"
    fi
done

# Special permissions for .env - unified for all environments
if [[ -f "$DEPLOYMENT_PATH/.env" ]]; then
    log "INFO" "Setting unified .env permissions"
    
    # First ensure root ownership
    if chown root:"$GROUP" "$DEPLOYMENT_PATH/.env"; then
        log "SUCCESS" "Set .env ownership to root:$GROUP"
    else
        log "ERROR" "Failed to set .env ownership!"
    fi
    
    # Set permissions: 440 = read-only for owner (root) and group
    if chmod 440 "$DEPLOYMENT_PATH/.env"; then
        log "SUCCESS" "Set .env permissions to 440 (r--r-----)"
    else
        log "ERROR" "Failed to set .env permissions!"
    fi
    
    # Verify the settings
    log "INFO" ".env file status:"
    log "INFO" "  Permissions: $(stat -c '%a (%A)' "$DEPLOYMENT_PATH/.env")"
    log "INFO" "  Ownership: $(stat -c '%U:%G' "$DEPLOYMENT_PATH/.env")"
fi

# Set ownership
log "INFO" "Setting ownership..."
chown -R root:"$GROUP" "$DEPLOYMENT_PATH"

# FINAL STEP: Apply immutability to everything
log "INFO" "🔒 Applying final immutability protection..."

# Pre-verification for .env
if [[ -f "$DEPLOYMENT_PATH/.env" ]]; then
    log "INFO" "Pre-immutability verification for .env:"
    
    # Ensure we have the right ownership and permissions
    chown root:"$GROUP" "$DEPLOYMENT_PATH/.env" || log "WARN" "Could not set ownership"
    chmod 440 "$DEPLOYMENT_PATH/.env" || log "WARN" "Could not set permissions"
    
    # Show current state
    ls -la "$DEPLOYMENT_PATH/.env"
    
    # Check if we're running as root
    if [ "$(id -u)" -ne 0 ]; then
        log "ERROR" "Not running as root - immutability will fail!"
        log "ERROR" "Current user: $(whoami) (UID: $(id -u))"
        exit 1
    fi
fi

# Apply immutability
if ! make_directory_immutable "$DEPLOYMENT_PATH"; then
    log "ERROR" "Failed to make directory contents immutable!"
    log "ERROR" "This is a critical security failure - deployment aborted"
    exit 1
fi

# Final verification with enhanced .env checking
log "INFO" "=== FINAL STATE ==="
ENV_PROTECTED=0
for file in "${ESSENTIAL_FILES[@]}" ".env"; do
    if [[ -f "$DEPLOYMENT_PATH/$file" ]]; then
        perms=$(stat -c "%a" "$DEPLOYMENT_PATH/$file")
        owner=$(stat -c "%U:%G" "$DEPLOYMENT_PATH/$file")
        attrs=$(lsattr "$DEPLOYMENT_PATH/$file" 2>/dev/null | cut -c5 || echo "?")
        size=$(stat -c%s "$DEPLOYMENT_PATH/$file")

        immutable=""
        if [[ "$attrs" == "i" ]]; then
            immutable="[IMMUTABLE]"
            [[ "$file" == ".env" ]] && ENV_PROTECTED=1
        else
            immutable="[MUTABLE-ERROR]"
        fi

        log "INFO" "$(printf "%-40s %s %-15s %6d bytes %s" "$file" "$perms" "$owner" "$size" "$immutable")"
    fi
done

# Critical check for .env
if [[ -f "$DEPLOYMENT_PATH/.env" ]] && [[ $ENV_PROTECTED -eq 0 ]]; then
    log "ERROR" "CRITICAL SECURITY FAILURE: .env file is not immutable!"
    log "ERROR" "Deployment is NOT secure. Manual intervention required."
    
    # Try one more time as a last resort
    log "INFO" "Attempting emergency immutability application..."
    if chattr +i "$DEPLOYMENT_PATH/.env" 2>&1; then
        log "SUCCESS" "Emergency immutability applied to .env"
    else
        log "ERROR" "Emergency immutability failed. Deployment aborted."
        exit 1
    fi
fi

log "SUCCESS" "════════════════════════════════════════════════════════════════"
log "SUCCESS" "✅ DEPLOYMENT COMPLETED WITH FULL IMMUTABILITY PROTECTION"
log "SUCCESS" "════════════════════════════════════════════════════════════════"

# Success exit
exit 0
