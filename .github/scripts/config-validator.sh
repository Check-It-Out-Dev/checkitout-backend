#!/bin/bash
# config-validator.sh - Validates deployment configuration files
# Usage: ./config-validator.sh <environment>

set -euo pipefail

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
ENVIRONMENT="${1:-test}"
CONFIG_DIR="${2:-src/main/resources}"
ERRORS=0
WARNINGS=0

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
    ((ERRORS++))
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*"
    ((WARNINGS++))
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $*"
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

# Check if yq is installed
if ! command -v yq &> /dev/null; then
    log_error "yq is required but not installed. Install with: wget -qO /usr/local/bin/yq https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64"
    exit 1
fi

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║        Deployment Configuration Validator                      ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
log_info "Environment: $ENVIRONMENT"
log_info "Config directory: $CONFIG_DIR"
echo ""

# Check if config files exist
DEFAULT_CONFIG="$CONFIG_DIR/deployment-defaults.yml"
ENV_CONFIG="$CONFIG_DIR/deployment-$ENVIRONMENT.yml"

if [[ ! -f "$DEFAULT_CONFIG" ]]; then
    log_error "Default config not found: $DEFAULT_CONFIG"
    exit 1
fi

if [[ ! -f "$ENV_CONFIG" ]]; then
    log_error "Environment config not found: $ENV_CONFIG"
    exit 1
fi

log_success "Configuration files found"

# Merge configurations
log_info "Merging configurations..."
MERGED_CONFIG="/tmp/merged-config-$$.yml"
yq eval-all '. as $item ireduce ({}; . * $item)' "$DEFAULT_CONFIG" "$ENV_CONFIG" > "$MERGED_CONFIG"

# Required fields validation
log_info "Checking required fields..."

check_field() {
    local field="$1"
    local description="$2"
    local value=$(yq eval "$field" "$MERGED_CONFIG" 2>/dev/null)
    
    if [[ "$value" == "null" ]] || [[ -z "$value" ]]; then
        log_error "$description is missing (field: $field)"
        return 1
    else
        log_success "$description: $value"
        return 0
    fi
}

# Core required fields
check_field ".deployment.environment" "Environment identifier"
check_field ".deployment.registry.primary" "Container registry"
check_field ".deployment.image.name" "Image name"
check_field ".deployment.container.user.name" "Container user name"
check_field ".deployment.container.group.name" "Container group name"
check_field ".deployment.container.user.id" "Container user ID"
check_field ".deployment.container.group.id" "Container group ID"
check_field ".deployment.admin.package.path" "Admin package path"
check_field ".deployment.script.execution.timeout.seconds" "Script timeout"

# Database configuration
check_field ".spring.datasource.host" "Database host"
check_field ".spring.datasource.port" "Database port"
check_field ".spring.datasource.database" "Database name"

# Server configuration
check_field ".server.port" "Server port"
check_field ".server.servlet.context-path" "Context path"

# Check for container user name consistency
log_info "Checking container user name consistency..."
BASE_USER=$(yq eval '.deployment.container.user.name' "$DEFAULT_CONFIG" 2>/dev/null)
ENV_USER=$(yq eval '.deployment.container.user.name' "$ENV_CONFIG" 2>/dev/null)
EXPECTED_USER="instagram-${ENVIRONMENT}-deploy"

if [[ "$ENV_USER" == "null" ]]; then
    log_warning "Environment config doesn't override container user name"
    log_info "Base user: $BASE_USER"
    log_info "Expected for environment: $EXPECTED_USER"
    log_warning "This might cause issues with derived values in workflows"
fi

# Check for unresolved variables
log_info "Checking for unresolved variables..."
UNRESOLVED=$(grep -o '\${[^}]*}' "$MERGED_CONFIG" 2>/dev/null | sort | uniq || true)
if [[ -n "$UNRESOLVED" ]]; then
    log_warning "Found unresolved variables:"
    echo "$UNRESOLVED" | while read -r var; do
        echo "  - $var"
    done
fi

# Validate paths
log_info "Validating deployment paths..."
DEPLOYMENT_PATH=$(yq eval '.deployment.destination.deployment.path' "$MERGED_CONFIG")
RUNTIME_PATH=$(yq eval '.deployment.destination.runtime.path' "$MERGED_CONFIG")

if [[ "$DEPLOYMENT_PATH" == "null" ]]; then
    log_error "Deployment path is not set"
else
    log_success "Deployment path: $DEPLOYMENT_PATH"
fi

if [[ "$RUNTIME_PATH" == "null" ]]; then
    log_error "Runtime path is not set"
else
    log_success "Runtime path: $RUNTIME_PATH"
fi

# Environment-specific checks
if [[ "$ENVIRONMENT" == "prod" ]] || [[ "$ENVIRONMENT" == "production" ]]; then
    log_info "Running production-specific checks..."
    
    # Check for debug mode
    DEBUG_MODE=$(yq eval '.deployment.debug' "$MERGED_CONFIG" 2>/dev/null)
    if [[ "$DEBUG_MODE" == "true" ]]; then
        log_warning "Debug mode is enabled in production!"
    fi
    
    # Check for proper secrets
    GSM_PREFIX=$(yq eval '.deployment.gsm.secret.prefix' "$MERGED_CONFIG" 2>/dev/null)
    if [[ "$GSM_PREFIX" != "PROD_" ]]; then
        log_warning "GSM secret prefix might be wrong for production: $GSM_PREFIX"
    fi
fi

# Generate summary
echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║                      Validation Summary                        ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo "Errors: $ERRORS"
echo "Warnings: $WARNINGS"

if [[ $ERRORS -gt 0 ]]; then
    echo -e "${RED}Validation FAILED${NC}"
    
    echo ""
    echo "Suggested fixes:"
    echo "1. Add missing fields to $ENV_CONFIG"
    echo "2. Ensure container.user.name is set correctly for the environment"
    echo "3. Check that all required values are properly inherited"
    
    # Cleanup
    rm -f "$MERGED_CONFIG"
    exit 1
else
    echo -e "${GREEN}Validation PASSED${NC}"
    
    if [[ $WARNINGS -gt 0 ]]; then
        echo ""
        echo "Please review warnings above"
    fi
    
    # Cleanup
    rm -f "$MERGED_CONFIG"
    exit 0
fi
