#!/bin/bash
# Script: secure-logger.sh v3.1 FIXED
# Purpose: Secure logging library with immutable log files
# Pattern: /var/log/CiCd/{env}/cicd-{operation}-{timestamp}.log
# Security: Each script creates its own immutable log file with chattr +a
# Created: 2025-01-30
# Modified: 2025-01-31 - FIXED: Use sudo for log directory creation

# Prevent direct execution
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "ERROR: This script should be sourced, not executed directly" >&2
    exit 1
fi

# Global variables for logging
CICD_LOG_BASE="${CICD_LOG_BASE:-/var/log/CiCd}"
ENVIRONMENT="${ENVIRONMENT:-test}"
OPERATION="${CICD_OPERATION:-unknown}"
TIMESTAMP="${CICD_TIMESTAMP:-$(date +%Y%m%d-%H%M%S)}"

# Color codes for visibility
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Initialize secure logging for a specific operation
# Usage: init_secure_logging "deploy" "prod"
init_secure_logging() {
    local operation="${1:-$OPERATION}"
    local environment="${2:-$ENVIRONMENT}"
    local timestamp="${3:-$TIMESTAMP}"

    # Set global vars for this session
    CICD_OPERATION="$operation"
    CICD_ENVIRONMENT="$environment"
    CICD_TIMESTAMP="$timestamp"

    # Create log directory structure using sudo (as defined in sudoers)
    local log_dir="${CICD_LOG_BASE}/${environment}"

    # First create base directory if needed
    if [[ ! -d "$CICD_LOG_BASE" ]]; then
        if ! sudo /bin/mkdir -p "$CICD_LOG_BASE" 2>/dev/null; then
            echo "ERROR: Failed to create base log directory: $CICD_LOG_BASE" >&2
            return 1
        fi
        sudo /bin/chmod 755 "$CICD_LOG_BASE" 2>/dev/null || true
        sudo /bin/chown instagram-scripts-admin:instagram-cicd "$CICD_LOG_BASE" 2>/dev/null || true
    fi

    # Then create environment-specific directory
    if [[ ! -d "$log_dir" ]]; then
        if ! sudo /bin/mkdir -p "$log_dir" 2>/dev/null; then
            echo "ERROR: Failed to create log directory: $log_dir" >&2
            return 1
        fi
        sudo /bin/chmod 755 "$log_dir" 2>/dev/null || true
        sudo /bin/chown instagram-scripts-admin:instagram-cicd "$log_dir" 2>/dev/null || true
    fi

    # Generate unique log file name
    CICD_LOG_FILE="${log_dir}/cicd-${operation}-${timestamp}.log"
    # Also export as LOG_FILE for compatibility
    export LOG_FILE="$CICD_LOG_FILE"

    # Create log file using sudo (as defined in sudoers)
    if ! sudo /usr/bin/touch "$CICD_LOG_FILE" 2>/dev/null; then
        echo "ERROR: Failed to create log file: $CICD_LOG_FILE" >&2
        return 1
    fi

    # Set permissions and ownership using sudo
    sudo /bin/chmod 640 "$CICD_LOG_FILE" 2>/dev/null || true
    sudo /bin/chown instagram-scripts-admin:instagram-cicd "$CICD_LOG_FILE" 2>/dev/null || true

    # Make immutable (append-only) using sudo
    if command -v chattr >/dev/null 2>&1; then
        sudo /usr/bin/chattr +a "$CICD_LOG_FILE" 2>/dev/null || {
            echo "WARN: Could not make log file immutable" >&2
        }
    fi

    # Redirect stdout and stderr to log file while keeping console output
    # Use sudo tee as defined in sudoers
    exec 1> >(sudo /usr/bin/tee -a "$CICD_LOG_FILE")
    exec 2>&1

    # Log initialization
    log "INFO" "=== Secure Logging Initialized ==="
    log "INFO" "Environment: $environment"
    log "INFO" "Operation: $operation"
    log "INFO" "Timestamp: $timestamp"
    log "INFO" "Log File: $CICD_LOG_FILE"
    log "INFO" "PID: $$"
    log "INFO" "User: $(whoami)"
    log "INFO" "Host: $(hostname)"
    log "INFO" "================================="

    return 0
}

# Enhanced logging function with structured format
# Usage: log "LEVEL" "message"
log() {
    local level="${1:-INFO}"
    shift
    local message="$*"

    # Get color based on level
    local color="$NC"
    case "$level" in
        ERROR)   color="$RED" ;;
        SUCCESS) color="$GREEN" ;;
        WARN)    color="$YELLOW" ;;
        INFO)    color="$BLUE" ;;
        DEBUG)   [[ "${DEBUG:-0}" == "1" ]] || return 0; color="$NC" ;;
        *)       color="$NC" ;;
    esac

    # Structured log format
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S.%3N')
    # Safely get caller information - handle cases where BASH_SOURCE[2] doesn't exist
    local caller="unknown:0"
    if [ ${#BASH_SOURCE[@]} -gt 2 ] && [ ${#BASH_LINENO[@]} -gt 1 ]; then
        caller="${BASH_SOURCE[2]##*/}:${BASH_LINENO[1]}"
    elif [ ${#BASH_SOURCE[@]} -gt 1 ] && [ ${#BASH_LINENO[@]} -gt 0 ]; then
        caller="${BASH_SOURCE[1]##*/}:${BASH_LINENO[0]}"
    fi

    # Output to console and log file
    echo -e "${color}[$timestamp] [$level] [${CICD_OPERATION}] [$caller] $message${NC}"
}

# Log error with stack trace
log_error() {
    local message="$1"
    local exit_code="${2:-1}"

    log "ERROR" "$message"
    log "ERROR" "Stack trace:"

    local frame=0
    while caller $frame; do
        ((frame++))
    done | while read line func file; do
        log "ERROR" "  at $func ($file:$line)"
    done

    return $exit_code
}

# Log command execution with timing
# Usage: log_exec "command" "args"
log_exec() {
    local cmd="$1"
    shift
    local args="$*"

    log "INFO" "Executing: $cmd $args"
    local start_time=$(date +%s.%N)

    # Execute command
    if "$cmd" "$@"; then
        local end_time=$(date +%s.%N)
        local duration=$(echo "$end_time - $start_time" | bc)
        log "SUCCESS" "Command completed in ${duration}s: $cmd"
        return 0
    else
        local exit_code=$?
        local end_time=$(date +%s.%N)
        local duration=$(echo "$end_time - $start_time" | bc)
        log "ERROR" "Command failed (exit $exit_code) after ${duration}s: $cmd"
        return $exit_code
    fi
}

# Create audit entry for critical operations
# Usage: audit_log "action" "target" "result"
audit_log() {
    local action="$1"
    local target="$2"
    local result="${3:-SUCCESS}"

    log "AUDIT" "Action=$action Target=$target Result=$result User=$(whoami) UID=$UID"
}

# Cleanup function to finalize logging
finalize_logging() {
    local exit_code="${1:-$?}"

    # Only log if logging was initialized
    if [ -n "${CICD_LOG_FILE:-}" ]; then
        log "INFO" "=== Finalizing Secure Logging ==="
        log "INFO" "Exit Code: $exit_code"
        log "INFO" "Duration: ${SECONDS:-0} seconds"
        log "INFO" "Log File: $CICD_LOG_FILE"
        log "INFO" "================================="

        # Ensure log file remains immutable using sudo
        if [[ -f "$CICD_LOG_FILE" ]] && command -v chattr >/dev/null 2>&1; then
            sudo /usr/bin/chattr +a "$CICD_LOG_FILE" 2>/dev/null || true
        fi
    fi
    
    return $exit_code
}

# Set up exit trap to finalize logging
trap 'finalize_logging $?' EXIT

# Export functions for use in scripts
export -f init_secure_logging
export -f log
export -f log_error
export -f log_exec
export -f audit_log
export -f finalize_logging

# Return success
return 0 2>/dev/null || true
