#!/bin/bash
# Script: env-checker.sh (GOLD STANDARD LITE V2)
# Purpose: Runtime environment variable checker for CI/CD
# Usage: source this at the start of deployment scripts
#
# GOLD STANDARD LITE V2:
# 1. Standardized log() function for consistency
# 2. Safe increment for counters
# 3. Designed to be sourced by other scripts
# 4. No hardcoded values - all configurable

# Don't use set -euo pipefail here as this is sourced
# The sourcing script should set these options

# Color codes for better visibility
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Enhanced logging function (if not already defined)
if ! type log &> /dev/null; then
    log() {
        local level="$1"
        shift
        local color=""
        case "$level" in
            ERROR) color="$RED" ;;
            SUCCESS) color="$GREEN" ;;
            WARN) color="$YELLOW" ;;
            INFO) color="$BLUE" ;;
            *) color="$NC" ;;
        esac
        echo -e "${color}[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*${NC}"
    }
fi

# Safe increment function (if not already defined)
if ! type safe_increment &> /dev/null; then
    safe_increment() {
        local -n var_ref=$1
        ((var_ref++)) || true
    }
fi

# Required environment variables for deployment (configurable)
REQUIRED_VARS=(
    "${ENV_CHECK_REQUIRED_VARS[@]:-"
    "CONTAINER_USER_NAME"
    "CONTAINER_GROUP_NAME"
    "DEPLOYMENT_PATH"
    "ENVIRONMENT"
    "ADMIN_PACKAGE_PATH"
    "SCRIPT_EXECUTION_TIMEOUT_SECONDS"}"
)

# Optional but recommended (configurable)
OPTIONAL_VARS=(
    "${ENV_CHECK_OPTIONAL_VARS[@]:-"
    "GITHUB_SHA"
    "GITHUB_RUN_ID"
    "CI"}"
)

# Validation patterns
declare -A VALIDATION_PATTERNS=(
    ["CONTAINER_USER_NAME"]="^[a-z0-9-]+$"
    ["CONTAINER_GROUP_NAME"]="^[a-z0-9-]+$"
    ["DEPLOYMENT_PATH"]="^/[a-zA-Z0-9/_-]+$"
    ["ENVIRONMENT"]="^(test|prod|production|staging|dev)$"
    ["SCRIPT_EXECUTION_TIMEOUT_SECONDS"]="^[0-9]+$"
)

# Function to check environment
check_deployment_environment() {
    local errors=0
    local warnings=0

    echo ""
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║           🔍 DEPLOYMENT ENVIRONMENT CHECK                      ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo "📅 Time: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "👤 User: $(whoami)"
    echo "🖥️  Host: $(hostname)"
    echo "📁 PWD: $(pwd)"
    echo "═══════════════════════════════════════════════════════════════"

    # Check required variables
    log "INFO" "Checking required environment variables..."
    for var in "${REQUIRED_VARS[@]}"; do
        if [[ -z "${!var:-}" ]]; then
            log "ERROR" "Required variable $var is not set"
            safe_increment errors
        else
            # Validate format if pattern exists
            local pattern="${VALIDATION_PATTERNS[$var]:-}"
            if [[ -n "$pattern" ]]; then
                if [[ ! "${!var}" =~ $pattern ]]; then
                    log "ERROR" "Variable $var has invalid format: ${!var}"
                    log "ERROR" "Expected pattern: $pattern"
                    safe_increment errors
                else
                    log "SUCCESS" "✓ $var = ${!var}"
                fi
            else
                log "SUCCESS" "✓ $var = ${!var}"
            fi
        fi
    done

    # Check optional variables
    echo ""
    log "INFO" "Checking optional environment variables..."
    for var in "${OPTIONAL_VARS[@]}"; do
        if [[ -z "${!var:-}" ]]; then
            log "WARN" "Optional variable $var is not set"
            safe_increment warnings
        else
            log "SUCCESS" "✓ $var = ${!var}"
        fi
    done

    # Check for common issues
    echo ""
    log "INFO" "Checking for common configuration issues..."

    # Check container user name format
    if [[ -n "${CONTAINER_USER_NAME:-}" ]]; then
        local expected_pattern="${CONTAINER_USER_PATTERN:-instagram-.*-deploy}"
        if [[ "$CONTAINER_USER_NAME" == "instagram" ]]; then
            log "WARN" "Container user name is generic 'instagram'"
            log "WARN" "Expected format: instagram-${ENVIRONMENT}-deploy"
            safe_increment warnings
        elif [[ ! "$CONTAINER_USER_NAME" =~ $expected_pattern ]]; then
            log "WARN" "Container user name doesn't match expected pattern"
            log "WARN" "Current: $CONTAINER_USER_NAME"
            log "WARN" "Expected pattern: $expected_pattern"
            safe_increment warnings
        fi
    fi

    # Check if deployment path matches environment
    if [[ -n "${DEPLOYMENT_PATH:-}" ]] && [[ -n "${ENVIRONMENT:-}" ]]; then
        if [[ ! "$DEPLOYMENT_PATH" =~ $ENVIRONMENT ]]; then
            log "WARN" "Deployment path doesn't contain environment name"
            log "WARN" "Path: $DEPLOYMENT_PATH"
            log "WARN" "Environment: $ENVIRONMENT"
            safe_increment warnings
        fi
    fi

    # Check for CI/CD environment
    if [[ -z "${CI:-}" ]]; then
        log "WARN" "CI environment variable not set - are we in CI/CD context?"
        safe_increment warnings
    fi

    # Generate summary
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    log "INFO" "Environment check summary: $errors errors, $warnings warnings"

    if [[ $errors -gt 0 ]]; then
        log "ERROR" "═══════════════════════════════════════════════════════════════"
        log "ERROR" "❌ ENVIRONMENT CHECK FAILED"
        log "ERROR" "═══════════════════════════════════════════════════════════════"
        log "ERROR" "Critical errors found. Deployment cannot proceed."
        echo ""
        log "INFO" "Troubleshooting steps:"
        log "INFO" "1. Check that all variables are exported in the SSH session"
        log "INFO" "2. Verify the workflow is passing all required inputs"
        log "INFO" "3. Check for typos in variable names"
        log "INFO" "4. Ensure config loader is setting outputs correctly"
        echo ""
        log "INFO" "Debug commands:"
        log "INFO" "  In workflow: echo \"container-user-name=\${{ needs.config.outputs.container-user-name }}\""
        log "INFO" "  In SSH: env | grep CONTAINER"
        return 1
    elif [[ $warnings -gt 0 ]]; then
        log "WARN" "═══════════════════════════════════════════════════════════════"
        log "WARN" "⚠️  ENVIRONMENT CHECK PASSED WITH WARNINGS"
        log "WARN" "═══════════════════════════════════════════════════════════════"
        log "WARN" "Deployment can proceed, but please review warnings above."
    else
        log "SUCCESS" "═══════════════════════════════════════════════════════════════"
        log "SUCCESS" "✅ ENVIRONMENT CHECK PASSED"
        log "SUCCESS" "═══════════════════════════════════════════════════════════════"
        log "SUCCESS" "All environment variables are properly configured"
    fi
    echo "═══════════════════════════════════════════════════════════════"
    echo ""

    return 0
}

# Extended validation function for more thorough checks
validate_deployment_paths() {
    local errors=0

    log "INFO" "Validating deployment paths and permissions..."

    # Check deployment path exists and is accessible
    if [[ -n "${DEPLOYMENT_PATH:-}" ]]; then
        if [[ ! -d "$DEPLOYMENT_PATH" ]]; then
            log "ERROR" "Deployment path does not exist: $DEPLOYMENT_PATH"
            safe_increment errors
        elif [[ ! -r "$DEPLOYMENT_PATH" ]]; then
            log "ERROR" "Deployment path is not readable: $DEPLOYMENT_PATH"
            safe_increment errors
        else
            log "SUCCESS" "✓ Deployment path exists and is readable"
        fi
    fi

    # Check admin package path if set
    if [[ -n "${ADMIN_PACKAGE_PATH:-}" ]]; then
        if [[ ! -d "$ADMIN_PACKAGE_PATH" ]]; then
            log "WARN" "Admin package path does not exist yet: $ADMIN_PACKAGE_PATH"
        else
            log "SUCCESS" "✓ Admin package path exists"
        fi
    fi

    return $errors
}

# Export functions so they can be used when sourced
export -f check_deployment_environment
export -f validate_deployment_paths

# Auto-run if not sourced (for standalone testing)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    check_deployment_environment "$@"
fi
