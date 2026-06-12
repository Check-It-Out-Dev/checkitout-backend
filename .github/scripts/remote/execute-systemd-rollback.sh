#!/bin/bash
# Systemd-aware rollback script with enhanced debugging and validation
# Restores deployment from backup with proper systemd integration
# Focused on essential and optional files only

set -euo pipefail

# Timestamp validation (redundant security check - defense in depth)
# This script should be executed within 10 minutes of creation
SCRIPT_TIMESTAMP=$(stat -c %Y "$0" 2>/dev/null || date +%s)
CURRENT_TIME=$(date +%s)
TIME_DIFF=$((CURRENT_TIME - SCRIPT_TIMESTAMP))

if [ $TIME_DIFF -gt 600 ]; then
    echo "[ERROR] Script timestamp validation failed (age: ${TIME_DIFF} seconds, max allowed: 600)"
    exit 1
fi

echo "[INFO] Script timestamp validated (${TIME_DIFF} seconds old)"

# Store script path for cleanup
SCRIPT_PATH="$0"
SCRIPT_DIR=$(dirname "$SCRIPT_PATH")
SCRIPT_NAME=$(basename "$SCRIPT_PATH")

# Combined trap for errors, exits, and cleanup
cleanup_and_exit() {
    local exit_code=$?
    echo "[INFO] Script exiting with code $exit_code"
    # Auto-cleanup (backup in case validation wrapper fails)
    rm -f "$SCRIPT_PATH" "$SCRIPT_DIR/${SCRIPT_NAME}.sha256" 2>/dev/null || true
    exit $exit_code
}

# Trap to catch errors and perform cleanup
trap 'echo "[ERROR] Script failed at line $LINENO with exit code $?"; cleanup_and_exit' ERR
trap 'cleanup_and_exit' EXIT

# Source environment file if it exists
if [[ -f ".envRollback" ]]; then
    # H04 fix: Validate .envRollback before sourcing
    if [ ! -f .envRollback ]; then
        log "ERROR" "Environment file .envRollback not found"
        exit 1
    fi

    # Check file isn't too large (prevent DoS)
    FILE_SIZE=$(stat -c%s .envRollback 2>/dev/null || echo "999999")
    if [ "$FILE_SIZE" -gt 10000 ]; then
        log "ERROR" ".envRollback file too large: $FILE_SIZE bytes (max 10000)"
        exit 1
    fi

    # Basic validation - check for dangerous patterns
    if grep -qE '(;|\||`|\$\(|&&|\|\|)' .envRollback; then
        log "ERROR" ".envRollback contains potentially dangerous shell metacharacters"
        exit 1
    fi

    source .envRollback
else
    echo "❌ Error: .envRollback file not found"
    exit 1
fi

# Define essential and optional files
ESSENTIAL_FILES=(
    "docker-compose-${ENVIRONMENT}.yml"
    "systemd-wrapper.sh"
    "postgres-entrypoint.sh"
    "gsm-healthcheck.sh"
    "INIT_PROD_DATABASE.sql"
    "sentinel-entrypoint.sh"
    "instagram-platform-${ENVIRONMENT}.service"
    ".env"
)

OPTIONAL_FILES=(
    "spring-boot-entrypoint.sh"
    "01-read-password.sh"
    ".gitignore"
    ".deployment-info"
)

# Validate required variables
REQUIRED_VARS=(
    "BACKUP_LOCATION"
    "DEPLOYMENT_PATH"
    "ENVIRONMENT"
    "SERVICE_NAME"
    "CONTAINER_USER_NAME"
    "CONTAINER_GROUP_NAME"
    "USE_SUDO_DOCKER"
)

for var in "${REQUIRED_VARS[@]}"; do
    if [[ -z "${!var:-}" ]]; then
        echo "❌ Error: Required variable $var is not set"
        exit 1
    fi
done

# Set default for optional variables
if [[ -z "${DOCKER_COMPOSE_FILE:-}" ]]; then
    # Default based on environment
    case "$ENVIRONMENT" in
        "test")
            DOCKER_COMPOSE_FILE="docker-compose-test.yml"
            ;;
        "prod"|"production")
            DOCKER_COMPOSE_FILE="docker-compose-prod.yml"
            ;;
        *)
            DOCKER_COMPOSE_FILE="docker-compose-${ENVIRONMENT}.yml"
            ;;
    esac
    echo "ℹ️ Using default docker-compose file for $ENVIRONMENT environment: $DOCKER_COMPOSE_FILE"
fi

# Check for required tools
REQUIRED_TOOLS=(curl ss python3)
for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "$tool" &> /dev/null; then
        echo "⚠️ Warning: $tool is not installed. Some diagnostics may be limited."
    fi
done

BACKUP_DIR="$BACKUP_LOCATION"
DEPLOY_DIR="$DEPLOYMENT_PATH"
ROLLBACK_SUCCESS="false"
HEALTH_CHECK_PASSED="false"

# Set variables from environment (passed by workflow from Spring properties)
GROUP="${CONTAINER_GROUP_NAME}"
DEPLOY_USER="${DEPLOYMENT_SERVER_USER}"

# Validate required variables
if [[ -z "$GROUP" ]]; then
    echo "❌ Error: CONTAINER_GROUP_NAME is not set"
    exit 1
fi

if [[ -z "$DEPLOY_USER" ]]; then
    echo "❌ Error: DEPLOYMENT_SERVER_USER is not set"
    exit 1
fi

# Create log directory
ROLLBACK_LOG_DIR="$(dirname "$DEPLOY_DIR")/rollback-logs"
if [[ ! -d "$ROLLBACK_LOG_DIR" ]]; then
    echo "📁 Creating rollback log directory: $ROLLBACK_LOG_DIR"
    /bin/mkdir -p "$ROLLBACK_LOG_DIR"
    /bin/chown "$DEPLOY_USER:$GROUP" "$ROLLBACK_LOG_DIR"
    /bin/chmod 775 "$ROLLBACK_LOG_DIR"
fi

# Create log file with proper permissions
ROLLBACK_LOG="$ROLLBACK_LOG_DIR/rollback_$(date +%Y%m%d_%H%M%S).log"
/usr/bin/touch "$ROLLBACK_LOG"
/bin/chmod 664 "$ROLLBACK_LOG"

# Set up tee command
TEE_CMD="/usr/bin/tee -a"

echo "Rollback starting at $(date)" | $TEE_CMD "$ROLLBACK_LOG" >/dev/null
echo "Backup: $BACKUP_DIR" | $TEE_CMD "$ROLLBACK_LOG" >/dev/null
echo "Deploy: $DEPLOY_DIR" | $TEE_CMD "$ROLLBACK_LOG" >/dev/null
echo "Environment: $ENVIRONMENT" | $TEE_CMD "$ROLLBACK_LOG" >/dev/null
echo "Group: $GROUP" | $TEE_CMD "$ROLLBACK_LOG" >/dev/null
echo "Deploy User: $DEPLOY_USER" | $TEE_CMD "$ROLLBACK_LOG" >/dev/null

# Verify backup exists
if [[ ! -d "$BACKUP_DIR" ]]; then
    echo "❌ Backup not found: $BACKUP_DIR" | $TEE_CMD "$ROLLBACK_LOG"
    exit 1
fi

# Stop service
echo "🛑 Stopping $SERVICE_NAME..." | $TEE_CMD "$ROLLBACK_LOG"

# Check current service state (pattern from deploy-and-validate.sh)
SERVICE_STATE=$(/bin/systemctl is-active "$SERVICE_NAME" 2>&1 || echo "unknown")
echo "📋 Current service state: ${SERVICE_STATE}" | $TEE_CMD "$ROLLBACK_LOG"

# Reset failed state if needed (pattern from deploy-and-validate.sh)
FAILED_CHECK=$(/bin/systemctl show "$SERVICE_NAME" --property=Result 2>&1 | grep -oP 'Result=\K.*' || echo "unknown")
if [ "$SERVICE_STATE" = "failed" ] || [ "$FAILED_CHECK" = "exit-code" ] || [ "$FAILED_CHECK" = "failure" ]; then
    echo "🔄 Service needs reset - clearing failed state..." | $TEE_CMD "$ROLLBACK_LOG"
    /bin/systemctl reset-failed "$SERVICE_NAME" || true
    echo "✅ Failed state cleared" | $TEE_CMD "$ROLLBACK_LOG"
fi

/bin/systemctl stop "$SERVICE_NAME" || true

# Set up docker command - NO SUDO when running as root
DOCKER_CMD="/usr/bin/docker"
DOCKER_COMPOSE_CMD="/usr/bin/docker compose"

echo "🔐 Script running as root - no sudo needed for docker" | $TEE_CMD "$ROLLBACK_LOG"

# Clean containers (preserve volumes)
COMPOSE_FILE="$DEPLOY_DIR/$DOCKER_COMPOSE_FILE"
if [ -f "$COMPOSE_FILE" ]; then
    cd /tmp
    # IMPORTANT: Do NOT use --volumes flag to preserve postgres data!
    $DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" down --remove-orphans --timeout 30 2>&1 | $TEE_CMD "$ROLLBACK_LOG" || true
fi

# Restore backup
echo "📦 Restoring backup..." | $TEE_CMD "$ROLLBACK_LOG"

# DEBUG STEP 1: Current content of deployment folder with permissions
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "[DEBUG] 1. CURRENT CONTENT OF DEPLOYMENT FOLDER WITH PERMISSIONS:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
if [[ -d "$DEPLOY_DIR" ]]; then
    echo "Directory exists. Contents:" | $TEE_CMD "$ROLLBACK_LOG"
    ls -la "$DEPLOY_DIR" 2>&1 | $TEE_CMD "$ROLLBACK_LOG" || echo "  (empty or error listing)" | $TEE_CMD "$ROLLBACK_LOG"
    echo "" | $TEE_CMD "$ROLLBACK_LOG"
    echo "File attributes (i = immutable):" | $TEE_CMD "$ROLLBACK_LOG"
    find "$DEPLOY_DIR" -maxdepth 1 -type f -exec lsattr {} \; 2>&1 | $TEE_CMD "$ROLLBACK_LOG" || echo "  (no files)" | $TEE_CMD "$ROLLBACK_LOG"
else
    echo "Directory does not exist yet" | $TEE_CMD "$ROLLBACK_LOG"
fi
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

# Remove immutable flags from current deployment if it exists (pattern from deploy-and-secure.sh)
if [[ -d "$DEPLOY_DIR" ]]; then
    # C12 fix: Remove || true and add proper error handling
    echo "🔓 Removing immutable flags from current deployment..." | $TEE_CMD "$ROLLBACK_LOG"

    # Remove immutable flags with error checking
    if ! /usr/bin/find "$DEPLOY_DIR" -type f -exec /usr/bin/chattr -i {} \; 2>/dev/null; then
        echo "⚠️ Some files could not have immutable flag removed" | $TEE_CMD "$ROLLBACK_LOG"
    fi

    if ! /usr/bin/find "$DEPLOY_DIR" -type d -exec /usr/bin/chattr -i {} \; 2>/dev/null; then
        echo "⚠️ Some directories could not have immutable flag removed" | $TEE_CMD "$ROLLBACK_LOG"
    fi
fi

# DEBUG STEP 2: List of files after removing immutable flags
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "[DEBUG] 2. FILES AFTER REMOVING IMMUTABLE FLAGS:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
if [[ -d "$DEPLOY_DIR" ]]; then
    ls -la "$DEPLOY_DIR" 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
    echo "" | $TEE_CMD "$ROLLBACK_LOG"
    echo "File attributes (should show no immutable flags):" | $TEE_CMD "$ROLLBACK_LOG"
    find "$DEPLOY_DIR" -maxdepth 1 -type f -exec lsattr {} \; 2>&1 | $TEE_CMD "$ROLLBACK_LOG" || echo "  (no files)" | $TEE_CMD "$ROLLBACK_LOG"
fi
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

# Remove current deployment directory
echo "🗑️ Removing current deployment..." | $TEE_CMD "$ROLLBACK_LOG"
# H03 fix: Validate DEPLOY_DIR before dangerous rm -rf
if [ -z "${DEPLOY_DIR:-}" ]; then
    echo "❌ ERROR: DEPLOY_DIR is not set - refusing to execute rm -rf" | $TEE_CMD "$ROLLBACK_LOG"
    exit 1
fi

if [[ "${DEPLOY_DIR}" == "/" ]] || [[ "${DEPLOY_DIR}" == "//" ]]; then
    echo "❌ ERROR: DEPLOY_DIR is root directory - refusing to execute rm -rf" | $TEE_CMD "$ROLLBACK_LOG"
    exit 1
fi

/bin/rm -rf "$DEPLOY_DIR"

# DEBUG STEP 3: List of files after deleting all files
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "[DEBUG] 3. DEPLOYMENT FOLDER AFTER DELETION:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
if [[ -d "$DEPLOY_DIR" ]]; then
    echo "Directory still exists (should be empty):" | $TEE_CMD "$ROLLBACK_LOG"
    ls -la "$DEPLOY_DIR" 2>&1 | $TEE_CMD "$ROLLBACK_LOG" || echo "  (empty)" | $TEE_CMD "$ROLLBACK_LOG"
else
    echo "Directory successfully removed" | $TEE_CMD "$ROLLBACK_LOG"
fi
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

# DEBUG STEP 4: Listing files in backup folder before removing immutable flags
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "[DEBUG] 4. BACKUP FOLDER CONTENTS BEFORE REMOVING IMMUTABLE FLAGS:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
echo "Backup location: $BACKUP_DIR" | $TEE_CMD "$ROLLBACK_LOG"
ls -la "$BACKUP_DIR" 2>&1 | $TEE_CMD "$ROLLBACK_LOG" || echo "Error listing backup" | $TEE_CMD "$ROLLBACK_LOG"
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "Essential files present in backup:" | $TEE_CMD "$ROLLBACK_LOG"
for file in "${ESSENTIAL_FILES[@]}"; do
    if [[ -f "$BACKUP_DIR/$file" ]] || [[ -f "$BACKUP_DIR/deployment/$file" ]]; then
        echo "  ✓ $file" | $TEE_CMD "$ROLLBACK_LOG"
    else
        echo "  ✗ $file (MISSING)" | $TEE_CMD "$ROLLBACK_LOG"
    fi
done
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "Optional files present in backup:" | $TEE_CMD "$ROLLBACK_LOG"
for file in "${OPTIONAL_FILES[@]}"; do
    if [[ -f "$BACKUP_DIR/$file" ]] || [[ -f "$BACKUP_DIR/deployment/$file" ]]; then
        echo "  ✓ $file" | $TEE_CMD "$ROLLBACK_LOG"
    else
        echo "  - $file (not present)" | $TEE_CMD "$ROLLBACK_LOG"
    fi
done
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "File attributes:" | $TEE_CMD "$ROLLBACK_LOG"
find "$BACKUP_DIR" -maxdepth 1 -type f -exec lsattr {} \; 2>&1 | $TEE_CMD "$ROLLBACK_LOG" || echo "  (no files)" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

# Remove immutable flags from backup (pattern from deploy-and-secure.sh)
echo "🔓 Removing immutable flags from backup..." | $TEE_CMD "$ROLLBACK_LOG"

# C12 fix: Remove || true and add proper error handling
if ! /usr/bin/find "$BACKUP_DIR" -type f -exec /usr/bin/chattr -i {} \; 2>/dev/null; then
    echo "⚠️ Some backup files could not have immutable flag removed" | $TEE_CMD "$ROLLBACK_LOG"
fi

if ! /usr/bin/find "$BACKUP_DIR" -type d -exec /usr/bin/chattr -i {} \; 2>/dev/null; then
    echo "⚠️ Some backup directories could not have immutable flag removed" | $TEE_CMD "$ROLLBACK_LOG"
fi

# Handle nested deployment directory if present
if [ -d "${BACKUP_DIR}/deployment" ]; then
    echo "⚠️ Backup has nested deployment directory structure" | $TEE_CMD "$ROLLBACK_LOG"

    if ! /usr/bin/find "${BACKUP_DIR}/deployment" -type f -exec /usr/bin/chattr -i {} \; 2>/dev/null; then
        echo "⚠️ Some nested backup files could not have immutable flag removed" | $TEE_CMD "$ROLLBACK_LOG"
    fi

    if ! /usr/bin/find "${BACKUP_DIR}/deployment" -type d -exec /usr/bin/chattr -i {} \; 2>/dev/null; then
        echo "⚠️ Some nested backup directories could not have immutable flag removed" | $TEE_CMD "$ROLLBACK_LOG"
    fi
fi

# DEBUG STEP 5: Listing files in backup folder after removing immutable flags
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "[DEBUG] 5. BACKUP FOLDER CONTENTS AFTER REMOVING IMMUTABLE FLAGS:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
ls -la "$BACKUP_DIR" 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "File attributes (should show no immutable flags):" | $TEE_CMD "$ROLLBACK_LOG"
find "$BACKUP_DIR" -maxdepth 1 -type f -exec lsattr {} \; 2>&1 | $TEE_CMD "$ROLLBACK_LOG" || echo "  (no files)" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

# Move/copy backup to deployment location
echo "📦 Moving backup to deployment location..." | $TEE_CMD "$ROLLBACK_LOG"

# Check if backup has nested deployment directory structure
if [ -d "${BACKUP_DIR}/deployment" ]; then
    # CREATE THE DEPLOYMENT DIRECTORY BEFORE COPYING
    echo "📁 Creating deployment directory: ${DEPLOY_DIR}" | $TEE_CMD "$ROLLBACK_LOG"
    /bin/mkdir -p ${DEPLOY_DIR}

    # Set proper ownership based on environment
    if [ "${ENVIRONMENT}" = "test" ]; then
        /bin/chown root:docker-secrets-test ${DEPLOY_DIR}
    elif [ "${ENVIRONMENT}" = "prod" ]; then
        /bin/chown root:docker-secrets-prod ${DEPLOY_DIR}
    fi

    echo "📦 Copying contents from ${BACKUP_DIR}/deployment/ to ${DEPLOY_DIR}/" | $TEE_CMD "$ROLLBACK_LOG"

    # Copy only essential and optional files
    ALL_FILES=("${ESSENTIAL_FILES[@]}" "${OPTIONAL_FILES[@]}")
    for file in "${ALL_FILES[@]}"; do
        if [[ -f "${BACKUP_DIR}/deployment/$file" ]]; then
            echo "   Copying: $file" | $TEE_CMD "$ROLLBACK_LOG"
            /bin/cp -p "${BACKUP_DIR}/deployment/$file" "${DEPLOY_DIR}/"
        fi
    done
else
    # Direct move - no nested structure
    echo "📦 Moving backup directly to deployment location..." | $TEE_CMD "$ROLLBACK_LOG"
    /bin/mv ${BACKUP_DIR} ${DEPLOY_DIR}
fi

# Validate the restored structure
echo "🔍 Validating restored deployment structure..." | $TEE_CMD "$ROLLBACK_LOG"
if [[ -d "$DEPLOY_DIR/deployment" ]]; then
    echo "❌ ERROR: Nested deployment directory detected at $DEPLOY_DIR/deployment" | $TEE_CMD "$ROLLBACK_LOG"
    echo "This indicates a problem with the backup structure. Manual intervention required!" | $TEE_CMD "$ROLLBACK_LOG"
    exit 1
fi

# Check for essential files
MISSING_ESSENTIAL=false
for file in "${ESSENTIAL_FILES[@]}"; do
    if [[ ! -f "$DEPLOY_DIR/$file" ]]; then
        echo "❌ ERROR: Essential file not found: $DEPLOY_DIR/$file" | $TEE_CMD "$ROLLBACK_LOG"
        MISSING_ESSENTIAL=true
    fi
done

if [[ "$MISSING_ESSENTIAL" = "true" ]]; then
    echo "❌ ERROR: One or more essential files are missing" | $TEE_CMD "$ROLLBACK_LOG"
    exit 1
fi

echo "✅ Deployment structure validated - all essential files present" | $TEE_CMD "$ROLLBACK_LOG"

# Fix ownership (pattern from deploy-and-secure.sh)
EXPECTED_USER="${CONTAINER_USER_NAME}"
EXPECTED_GROUP=$(id -gn "$EXPECTED_USER" 2>/dev/null || echo "$EXPECTED_USER")

echo "🔧 Setting ownership..." | $TEE_CMD "$ROLLBACK_LOG"

# Set ownership: root owns static files, deployment user owns runtime
/bin/chown -R "root:$GROUP" "$DEPLOY_DIR"

# .env needs to be owned by deployment user
if [[ -f "$DEPLOY_DIR/.env" ]]; then
    /bin/chown "$DEPLOY_USER:$GROUP" "$DEPLOY_DIR/.env"
    echo "✅ Set .env owner to $DEPLOY_USER:$GROUP" | $TEE_CMD "$ROLLBACK_LOG"
fi

# If runtime directory exists, make it writable by deployment user
RUNTIME_DIR="$(dirname $DEPLOY_DIR)/runtime"
if [ -d "$RUNTIME_DIR" ]; then
    /bin/chown -R "$DEPLOY_USER:$GROUP" "$RUNTIME_DIR"
    echo "✅ Runtime directory ownership set" | $TEE_CMD "$ROLLBACK_LOG"
fi

# Set permissions (pattern from deploy-and-secure.sh)
echo "🔐 Setting file permissions..." | $TEE_CMD "$ROLLBACK_LOG"

# Set permissions for all files first
for file in "${ESSENTIAL_FILES[@]}" "${OPTIONAL_FILES[@]}"; do
    if [[ -f "$DEPLOY_DIR/$file" ]]; then
        /bin/chmod 444 "$DEPLOY_DIR/$file"
    fi
done

# Make shell scripts executable (750 for security - no world access)
SHELL_SCRIPTS=(
    "systemd-wrapper.sh"
    "postgres-entrypoint.sh"
    "gsm-healthcheck.sh"
    "spring-boot-entrypoint.sh"
    "01-read-password.sh"
)

for script in "${SHELL_SCRIPTS[@]}"; do
    if [[ -f "$DEPLOY_DIR/$script" ]]; then
        /bin/chmod 750 "$DEPLOY_DIR/$script"
        echo "  ✓ Made $script executable (750)" | $TEE_CMD "$ROLLBACK_LOG"
    fi
done

# Ensure SQL init scripts are readable
if [[ -f "$DEPLOY_DIR/INIT_PROD_DATABASE.sql" ]]; then
    /bin/chmod 444 "$DEPLOY_DIR/INIT_PROD_DATABASE.sql"
    echo "  ✓ Set INIT_PROD_DATABASE.sql as read-only (444)" | $TEE_CMD "$ROLLBACK_LOG"
fi

# .env needs to be writable
if [[ -f "$DEPLOY_DIR/.env" ]]; then
    /bin/chmod 644 "$DEPLOY_DIR/.env"
    echo "  ✓ Set .env as writable (644)" | $TEE_CMD "$ROLLBACK_LOG"
fi

# DEBUG STEP 6: List files before making immutable
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "[DEBUG] 6. DEPLOYMENT FOLDER BEFORE MAKING IMMUTABLE:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
ls -la "$DEPLOY_DIR" 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "File attributes (before immutable):" | $TEE_CMD "$ROLLBACK_LOG"
find "$DEPLOY_DIR" -maxdepth 1 -type f -exec lsattr {} \; 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

# Restore immutable flags on static files (pattern from deploy-and-secure.sh)
echo "🔒 Restoring immutable flags on static files..." | $TEE_CMD "$ROLLBACK_LOG"

# Safety check: Ensure critical scripts are executable before making immutable
CRITICAL_SCRIPTS=("systemd-wrapper.sh" "postgres-entrypoint.sh" "gsm-healthcheck.sh")
for script in "${CRITICAL_SCRIPTS[@]}"; do
    if [[ -f "$DEPLOY_DIR/$script" ]]; then
        if [[ ! -x "$DEPLOY_DIR/$script" ]]; then
            echo "  ⚠️  WARNING: $script is not executable! Fixing..." | $TEE_CMD "$ROLLBACK_LOG"
            /bin/chmod 750 "$DEPLOY_DIR/$script"
        fi
    fi
done

# Apply immutable flags (except .env)
immutable_count=0

# Set immutable on essential and optional files (except .env)
for file in "${ESSENTIAL_FILES[@]}" "${OPTIONAL_FILES[@]}"; do
    if [[ -f "$DEPLOY_DIR/$file" ]] && [[ "$file" != ".env" ]]; then
        # C12 fix: Remove || true and add proper error handling
        if ! /usr/bin/chattr +i "$DEPLOY_DIR/$file" 2>/dev/null; then
            echo "⚠️ Failed to make $file immutable" | $TEE_CMD "$ROLLBACK_LOG"
        else
            immutable_count=$((immutable_count + 1))
        fi
    fi
done

echo "✅ Made $immutable_count files immutable" | $TEE_CMD "$ROLLBACK_LOG"

# DEBUG STEP 7: List files after making immutable
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "[DEBUG] 7. DEPLOYMENT FOLDER AFTER MAKING IMMUTABLE:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
ls -la "$DEPLOY_DIR" 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "File attributes (i = immutable):" | $TEE_CMD "$ROLLBACK_LOG"
find "$DEPLOY_DIR" -maxdepth 1 -type f -exec lsattr {} \; 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "Critical files check:" | $TEE_CMD "$ROLLBACK_LOG"
for file in "${ESSENTIAL_FILES[@]}"; do
    if [[ -f "$DEPLOY_DIR/$file" ]]; then
        perms=$(stat -c "%a" "$DEPLOY_DIR/$file")
        echo "  ✓ $file exists (permissions: $perms)" | $TEE_CMD "$ROLLBACK_LOG"
    else
        echo "  ✗ $file MISSING!" | $TEE_CMD "$ROLLBACK_LOG"
    fi
done
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

# Display .env file contents (pattern from deploy-and-validate.sh)
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "📋 DISPLAYING .ENV FILE CONTENTS:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
ENV_FILE="$DEPLOY_DIR/.env"
if [ -f "$ENV_FILE" ]; then
    echo "✅ .env file found at $ENV_FILE" | $TEE_CMD "$ROLLBACK_LOG"
    echo "----------------------------------------" | $TEE_CMD "$ROLLBACK_LOG"
    cat "$ENV_FILE" 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
    echo "----------------------------------------" | $TEE_CMD "$ROLLBACK_LOG"
    echo "📊 .env file size: $(wc -l < "$ENV_FILE") lines, $(wc -c < "$ENV_FILE") bytes" | $TEE_CMD "$ROLLBACK_LOG"
else
    echo "❌ WARNING: .env file not found at $ENV_FILE" | $TEE_CMD "$ROLLBACK_LOG"
fi
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

# Check and pull required images (pattern from deploy-and-validate.sh)
cd "$DEPLOY_DIR"

# Source .env file if it exists
if [ -f "$ENV_FILE" ]; then
    echo "📋 Loading environment from $ENV_FILE" | $TEE_CMD "$ROLLBACK_LOG"
    set -a
    source "$ENV_FILE"
    set +a
fi

# C07 fix: Validate MAIN_APP_IMAGE before docker compose operations
if [ -z "${MAIN_APP_IMAGE:-}" ]; then
    echo "❌ ERROR: MAIN_APP_IMAGE is not set in .env file" | $TEE_CMD "$ROLLBACK_LOG"
    echo "Docker compose requires MAIN_APP_IMAGE to be defined" | $TEE_CMD "$ROLLBACK_LOG"
    ROLLBACK_SUCCESS="false"
    exit 1
fi

# Extract and pull images from docker compose
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "🐳 CHECKING AND PULLING DOCKER IMAGES:" | $TEE_CMD "$ROLLBACK_LOG"
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

COMPOSE_FILE="$DOCKER_COMPOSE_FILE"
REQUIRED_IMAGES=$($DOCKER_COMPOSE_CMD -f "$COMPOSE_FILE" config --images 2>/dev/null || true)

echo "Required images:" | $TEE_CMD "$ROLLBACK_LOG"
for IMAGE in $REQUIRED_IMAGES; do
    echo "  - $IMAGE" | $TEE_CMD "$ROLLBACK_LOG"
done

MISSING=false
for IMAGE in $REQUIRED_IMAGES; do
    if [ -n "$IMAGE" ] && ! $DOCKER_CMD image inspect "$IMAGE" >/dev/null 2>&1; then
        echo "❌ Missing image: $IMAGE" | $TEE_CMD "$ROLLBACK_LOG"
        MISSING=true
    else
        echo "✅ Image exists: $IMAGE" | $TEE_CMD "$ROLLBACK_LOG"
    fi
done

if [ "$MISSING" = "true" ]; then
    echo "📥 Attempting to pull missing images..." | $TEE_CMD "$ROLLBACK_LOG"

    # Check if we need to login to registry (pattern from execute-systemd-rollback.sh)
    if [[ "$REQUIRED_IMAGES" == *"ghcr.io"* ]]; then
        echo "🔐 Images are from GitHub Container Registry" | $TEE_CMD "$ROLLBACK_LOG"
        if [ -n "${GITHUB_TOKEN:-}" ] && [ -n "${GITHUB_ACTOR:-}" ]; then
            echo "🔑 Attempting to login to ghcr.io..." | $TEE_CMD "$ROLLBACK_LOG"
            # H02 fix: Secure token passing - never echo the token
            $DOCKER_CMD login ghcr.io -u "$GITHUB_ACTOR" --password-stdin <<< "$GITHUB_TOKEN" 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
        else
            echo "⚠️ No GitHub credentials available, attempting pull without authentication..." | $TEE_CMD "$ROLLBACK_LOG"
        fi
    fi

    # Extract registry info from .env if available
    if [ -n "${DOCKER_REGISTRY:-}" ] && [ -n "${DOCKER_REGISTRY_USERNAME:-}" ] && [ -n "${DOCKER_REGISTRY_PASSWORD:-}" ]; then
        echo "🔑 Attempting to login to $DOCKER_REGISTRY..." | $TEE_CMD "$ROLLBACK_LOG"
        echo "$DOCKER_REGISTRY_PASSWORD" | $DOCKER_CMD login "$DOCKER_REGISTRY" -u "$DOCKER_REGISTRY_USERNAME" --password-stdin 2>&1 | $TEE_CMD "$ROLLBACK_LOG"
    fi

    # Pull each missing image
    PULL_FAILED=false
    for IMAGE in $REQUIRED_IMAGES; do
        if [ -n "$IMAGE" ] && ! $DOCKER_CMD image inspect "$IMAGE" >/dev/null 2>&1; then
            echo "🔄 Pulling image: $IMAGE" | $TEE_CMD "$ROLLBACK_LOG"
            if $DOCKER_CMD pull "$IMAGE" 2>&1 | $TEE_CMD "$ROLLBACK_LOG"; then
                echo "✅ Successfully pulled: $IMAGE" | $TEE_CMD "$ROLLBACK_LOG"
            else
                echo "❌ Failed to pull: $IMAGE" | $TEE_CMD "$ROLLBACK_LOG"
                PULL_FAILED=true
            fi
        fi
    done

    if [ "$PULL_FAILED" = "true" ]; then
        echo "❌ Failed to pull all required images" | $TEE_CMD "$ROLLBACK_LOG"
        ROLLBACK_SUCCESS="false"
    else
        echo "✅ All required images are now available" | $TEE_CMD "$ROLLBACK_LOG"
        MISSING=false
    fi
fi
echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

if [ "$MISSING" = "true" ]; then
    echo "❌ Missing required images and unable to pull them" | $TEE_CMD "$ROLLBACK_LOG"
    ROLLBACK_SUCCESS="false"
else
    # Restart service with improved error handling (pattern from deploy-and-validate.sh)
    echo "" | $TEE_CMD "$ROLLBACK_LOG"
    echo "🚀 RESTARTING SERVICE WITH VALIDATION:" | $TEE_CMD "$ROLLBACK_LOG"
    echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"

    # Check if systemd-wrapper.sh exists and is executable
    echo "🔍 Checking systemd wrapper script..." | $TEE_CMD "$ROLLBACK_LOG"
    WRAPPER_SCRIPT="${DEPLOY_DIR}/systemd-wrapper.sh"
    if [ ! -f "$WRAPPER_SCRIPT" ]; then
        echo "❌ ERROR: systemd-wrapper.sh not found at $WRAPPER_SCRIPT" | $TEE_CMD "$ROLLBACK_LOG"
        exit 1
    fi

    # Check if executable
    if [ ! -x "$WRAPPER_SCRIPT" ]; then
        echo "❌ ERROR: systemd-wrapper.sh is not executable" | $TEE_CMD "$ROLLBACK_LOG"
        echo "   This is causing the 203/EXEC error in systemd" | $TEE_CMD "$ROLLBACK_LOG"
        exit 1
    fi
    echo "✅ Wrapper script is executable" | $TEE_CMD "$ROLLBACK_LOG"

    # Show what the service is configured to execute
    echo "📋 Service ExecStart command:" | $TEE_CMD "$ROLLBACK_LOG"
    /bin/systemctl show "$SERVICE_NAME" --property=ExecStart 2>&1 | grep -oP 'ExecStart=\K.*' | $TEE_CMD "$ROLLBACK_LOG" || echo "   Could not determine ExecStart" | $TEE_CMD "$ROLLBACK_LOG"

    # Reload systemd daemon
    echo "🔄 Reloading systemd daemon..." | $TEE_CMD "$ROLLBACK_LOG"
    /bin/systemctl daemon-reload

    # Reset failed state again
    echo "🔧 Resetting failed state..." | $TEE_CMD "$ROLLBACK_LOG"
    /bin/systemctl reset-failed "$SERVICE_NAME" || true

    # Show systemd service configuration
    echo "📋 Checking systemd service configuration..." | $TEE_CMD "$ROLLBACK_LOG"
    /bin/systemctl show "$SERVICE_NAME" --property=WorkingDirectory,ExecStart,User,Group | $TEE_CMD "$ROLLBACK_LOG"

    # Start service
    echo "🚀 Starting service..." | $TEE_CMD "$ROLLBACK_LOG"
    if /bin/systemctl start "$SERVICE_NAME" 2>&1 | $TEE_CMD "$ROLLBACK_LOG"; then
        echo "✅ Service started" | $TEE_CMD "$ROLLBACK_LOG"
        ROLLBACK_SUCCESS="true"

        # Wait for service to be active
        echo "⏳ Waiting for service to be active..." | $TEE_CMD "$ROLLBACK_LOG"
        WAIT_COUNT=0
        MAX_WAIT=30

        while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
            if /bin/systemctl is-active --quiet "$SERVICE_NAME"; then
                echo "✅ Service $SERVICE_NAME is active" | $TEE_CMD "$ROLLBACK_LOG"
                break
            fi
            echo "⏳ Waiting... (${WAIT_COUNT}/${MAX_WAIT})" | $TEE_CMD "$ROLLBACK_LOG"
            sleep 2
            WAIT_COUNT=$((WAIT_COUNT + 1))
        done

        if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
            echo "❌ Service failed to become active after ${MAX_WAIT} attempts" | $TEE_CMD "$ROLLBACK_LOG"
            ROLLBACK_SUCCESS="false"
        fi

        echo "⏳ Waiting for containers to start..." | $TEE_CMD "$ROLLBACK_LOG"
        sleep 30

        # Check containers with health status (pattern from deploy-and-validate.sh)
        echo "" | $TEE_CMD "$ROLLBACK_LOG"
        echo "🐳 Checking container health status..." | $TEE_CMD "$ROLLBACK_LOG"
        CONTAINER_PREFIX="instagram-platform-${ENVIRONMENT}"
        UNHEALTHY_CONTAINERS=0
        TOTAL_CONTAINERS=0

        for container in $($DOCKER_CMD ps --format "{{.Names}}" | grep "^${CONTAINER_PREFIX}"); do
            TOTAL_CONTAINERS=$((TOTAL_CONTAINERS + 1))
            HEALTH_STATUS=$($DOCKER_CMD inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "none")
            CONTAINER_STATUS=$($DOCKER_CMD inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "unknown")

            if [ "$HEALTH_STATUS" = "none" ]; then
                echo "   📦 $container: Status=$CONTAINER_STATUS (no healthcheck defined)" | $TEE_CMD "$ROLLBACK_LOG"
            elif [ "$HEALTH_STATUS" = "healthy" ]; then
                echo "   ✅ $container: Healthy (Status=$CONTAINER_STATUS)" | $TEE_CMD "$ROLLBACK_LOG"
            else
                echo "   ❌ $container: Health=$HEALTH_STATUS (Status=$CONTAINER_STATUS)" | $TEE_CMD "$ROLLBACK_LOG"
                UNHEALTHY_CONTAINERS=$((UNHEALTHY_CONTAINERS + 1))

                # Get health check logs for unhealthy containers
                echo "      Health check logs:" | $TEE_CMD "$ROLLBACK_LOG"
                $DOCKER_CMD inspect --format='{{range .State.Health.Log}}{{.Output}}{{end}}' "$container" 2>&1 | tail -3 | sed 's/^/        /' | $TEE_CMD "$ROLLBACK_LOG"
            fi
        done

        if [ "$TOTAL_CONTAINERS" -eq 0 ]; then
            echo "   ❌ No containers found with prefix: $CONTAINER_PREFIX" | $TEE_CMD "$ROLLBACK_LOG"
            ROLLBACK_SUCCESS="false"
        elif [ "$UNHEALTHY_CONTAINERS" -gt 0 ]; then
            echo "   ⚠️ Warning: $UNHEALTHY_CONTAINERS of $TOTAL_CONTAINERS containers are unhealthy" | $TEE_CMD "$ROLLBACK_LOG"
        else
            echo "   ✅ All $TOTAL_CONTAINERS containers are healthy" | $TEE_CMD "$ROLLBACK_LOG"
        fi

        if ! /bin/systemctl is-active --quiet "$SERVICE_NAME"; then
            echo "❌ Service not active" | $TEE_CMD "$ROLLBACK_LOG"
            ROLLBACK_SUCCESS="false"
        fi
    else
        echo "❌ Failed to start service" | $TEE_CMD "$ROLLBACK_LOG"
        echo "📋 Service status:" | $TEE_CMD "$ROLLBACK_LOG"
        /bin/systemctl status "$SERVICE_NAME" --no-pager | $TEE_CMD "$ROLLBACK_LOG" || true
        echo "📋 Recent journal entries:" | $TEE_CMD "$ROLLBACK_LOG"
        /bin/journalctl -u "$SERVICE_NAME" -n 20 --no-pager | $TEE_CMD "$ROLLBACK_LOG" || true
        ROLLBACK_SUCCESS="false"
    fi

    echo "=========================================================" | $TEE_CMD "$ROLLBACK_LOG"
fi

# Enhanced Health Check and Validation
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "🏥 Health Check Configuration:" | $TEE_CMD "$ROLLBACK_LOG"
echo "   URL: ${HEALTH_CHECK_URL:-Not configured}" | $TEE_CMD "$ROLLBACK_LOG"
echo "   Timeout: ${HEALTH_CHECK_TIMEOUT:-30} seconds" | $TEE_CMD "$ROLLBACK_LOG"

# Container Health Status Check
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "🐳 Final container health status check..." | $TEE_CMD "$ROLLBACK_LOG"
CONTAINER_PREFIX="instagram-platform-${ENVIRONMENT}"
UNHEALTHY_CONTAINERS=0
TOTAL_CONTAINERS=0

for container in $($DOCKER_CMD ps --format "{{.Names}}" | grep "^${CONTAINER_PREFIX}"); do
    TOTAL_CONTAINERS=$((TOTAL_CONTAINERS + 1))
    HEALTH_STATUS=$($DOCKER_CMD inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "none")
    CONTAINER_STATUS=$($DOCKER_CMD inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "unknown")

    if [ "$HEALTH_STATUS" = "none" ]; then
        echo "   📦 $container: Status=$CONTAINER_STATUS (no healthcheck defined)" | $TEE_CMD "$ROLLBACK_LOG"
    elif [ "$HEALTH_STATUS" = "healthy" ]; then
        echo "   ✅ $container: Healthy (Status=$CONTAINER_STATUS)" | $TEE_CMD "$ROLLBACK_LOG"
    else
        echo "   ❌ $container: Health=$HEALTH_STATUS (Status=$CONTAINER_STATUS)" | $TEE_CMD "$ROLLBACK_LOG"
        UNHEALTHY_CONTAINERS=$((UNHEALTHY_CONTAINERS + 1))
    fi
done

if [ "$TOTAL_CONTAINERS" -eq 0 ]; then
    echo "   ❌ No containers found with prefix: $CONTAINER_PREFIX" | $TEE_CMD "$ROLLBACK_LOG"
    ROLLBACK_SUCCESS="false"
elif [ "$UNHEALTHY_CONTAINERS" -gt 0 ]; then
    echo "   ⚠️ Warning: $UNHEALTHY_CONTAINERS of $TOTAL_CONTAINERS containers are unhealthy" | $TEE_CMD "$ROLLBACK_LOG"
fi

# HTTP Health Check
if [ "$ROLLBACK_SUCCESS" = "true" ] && [ -n "${HEALTH_CHECK_URL:-}" ]; then
    echo "" | $TEE_CMD "$ROLLBACK_LOG"
    echo "🌐 Starting HTTP health checks..." | $TEE_CMD "$ROLLBACK_LOG"
    echo "⏳ Waiting 30 seconds for application to stabilize..." | $TEE_CMD "$ROLLBACK_LOG"
    sleep 30

    # Try for ~90 seconds total (9 attempts with 10 second intervals)
    MAX_ATTEMPTS=9
    RETRY_INTERVAL=10

    for i in $(seq 1 $MAX_ATTEMPTS); do
        echo "" | $TEE_CMD "$ROLLBACK_LOG"
        echo "🔍 HTTP health check attempt $i/$MAX_ATTEMPTS..." | $TEE_CMD "$ROLLBACK_LOG"
        echo "   Timestamp: $(date '+%Y-%m-%d %H:%M:%S')" | $TEE_CMD "$ROLLBACK_LOG"
        echo "   URL: $HEALTH_CHECK_URL" | $TEE_CMD "$ROLLBACK_LOG"

        # Detailed curl with timing information
        CURL_OUTPUT=$(mktemp)
        CURL_TIMING="time_namelookup:%{time_namelookup}s\ntime_connect:%{time_connect}s\ntime_starttransfer:%{time_starttransfer}s\ntime_total:%{time_total}s\nhttp_code:%{http_code}"

        if curl -s -o "$CURL_OUTPUT" -w "$CURL_TIMING" --connect-timeout 5 --max-time 10 "$HEALTH_CHECK_URL" 2>&1 > "${CURL_OUTPUT}.timing"; then
            HTTP_CODE=$(grep "http_code:" "${CURL_OUTPUT}.timing" | cut -d: -f2)
            RESPONSE_TIME=$(grep "time_total:" "${CURL_OUTPUT}.timing" | cut -d: -f2)
            BODY=$(cat "$CURL_OUTPUT" | head -c 500)  # Limit body to 500 chars

            echo "   Response code: $HTTP_CODE" | $TEE_CMD "$ROLLBACK_LOG"
            echo "   Response time: ${RESPONSE_TIME}" | $TEE_CMD "$ROLLBACK_LOG"

            if [ "$HTTP_CODE" = "200" ]; then
                HEALTH_CHECK_PASSED="true"
                echo "   ✅ Health check PASSED!" | $TEE_CMD "$ROLLBACK_LOG"

                # Try to parse JSON response if it looks like JSON
                if echo "$BODY" | grep -q "^\s*{"; then
                    echo "   Response body (JSON):" | $TEE_CMD "$ROLLBACK_LOG"
                    echo "$BODY" | python3 -m json.tool 2>/dev/null | head -20 | sed 's/^/      /' | $TEE_CMD "$ROLLBACK_LOG" || echo "$BODY" | head -20 | sed 's/^/      /' | $TEE_CMD "$ROLLBACK_LOG"
                else
                    echo "   Response body: $BODY" | $TEE_CMD "$ROLLBACK_LOG"
                fi
                break
            else
                echo "   ❌ Health check failed" | $TEE_CMD "$ROLLBACK_LOG"
                echo "   Response body: $BODY" | $TEE_CMD "$ROLLBACK_LOG"
            fi
        else
            CURL_ERROR=$?
            echo "   ❌ Curl failed with error code: $CURL_ERROR" | $TEE_CMD "$ROLLBACK_LOG"
            echo "   Error details: $(cat "${CURL_OUTPUT}.timing" 2>/dev/null || echo "No timing data")" | $TEE_CMD "$ROLLBACK_LOG"
        fi

        rm -f "$CURL_OUTPUT" "${CURL_OUTPUT}.timing"

        if [ "$i" -lt "$MAX_ATTEMPTS" ]; then
            echo "   ⏳ Waiting $RETRY_INTERVAL seconds before retry..." | $TEE_CMD "$ROLLBACK_LOG"
            sleep $RETRY_INTERVAL
        fi
    done

    if [ "$HEALTH_CHECK_PASSED" = "false" ]; then
        echo "" | $TEE_CMD "$ROLLBACK_LOG"
        echo "❌ HTTP health check failed after $MAX_ATTEMPTS attempts (~90 seconds)" | $TEE_CMD "$ROLLBACK_LOG"

        # Enhanced diagnostics
        echo "" | $TEE_CMD "$ROLLBACK_LOG"
        echo "📊 Enhanced diagnostic information:" | $TEE_CMD "$ROLLBACK_LOG"

        # Check all containers
        echo "   🐳 Container status:" | $TEE_CMD "$ROLLBACK_LOG"
        $DOCKER_CMD ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep "$CONTAINER_PREFIX" | sed 's/^/      /' | $TEE_CMD "$ROLLBACK_LOG"

        # Check application container logs
        APP_CONTAINER="instagram-platform-${ENVIRONMENT}-app"
        if $DOCKER_CMD ps --format "{{.Names}}" | grep -q "$APP_CONTAINER"; then
            echo "" | $TEE_CMD "$ROLLBACK_LOG"
            echo "   📋 Last 30 lines of application logs:" | $TEE_CMD "$ROLLBACK_LOG"
            $DOCKER_CMD logs "$APP_CONTAINER" --tail 30 2>&1 | sed 's/^/       /' | $TEE_CMD "$ROLLBACK_LOG"

            # Check for specific error patterns
            echo "" | $TEE_CMD "$ROLLBACK_LOG"
            echo "   🔍 Checking for common error patterns:" | $TEE_CMD "$ROLLBACK_LOG"
            if $DOCKER_CMD logs "$APP_CONTAINER" --tail 100 2>&1 | grep -i "error\|exception\|failed" | tail -5; then
                echo "      ⚠️ Errors found in logs (see above)" | $TEE_CMD "$ROLLBACK_LOG"
            else
                echo "      ✅ No obvious errors in recent logs" | $TEE_CMD "$ROLLBACK_LOG"
            fi
        else
            echo "   ❌ Application container is NOT running!" | $TEE_CMD "$ROLLBACK_LOG"
        fi

        # Network connectivity tests
        echo "" | $TEE_CMD "$ROLLBACK_LOG"
        echo "   🔌 Network diagnostics:" | $TEE_CMD "$ROLLBACK_LOG"

        # Check if port is listening
        if ss -tlnp 2>/dev/null | grep ":${SERVER_PORT:-8080}" > /dev/null; then
            echo "      ✅ Port ${SERVER_PORT:-8080} is listening" | $TEE_CMD "$ROLLBACK_LOG"
            ss -tlnp 2>/dev/null | grep ":${SERVER_PORT:-8080}" | sed 's/^/         /' | $TEE_CMD "$ROLLBACK_LOG"
        else
            echo "      ❌ Port ${SERVER_PORT:-8080} is NOT listening" | $TEE_CMD "$ROLLBACK_LOG"
        fi

        # Docker network inspection
        echo "" | $TEE_CMD "$ROLLBACK_LOG"
        echo "   🌐 Docker network status:" | $TEE_CMD "$ROLLBACK_LOG"
        NETWORKS=$($DOCKER_CMD ps --format "{{.Names}}" | grep "^${CONTAINER_PREFIX}" | head -1 | xargs -I {} $DOCKER_CMD inspect {} --format='{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null)
        for network in $NETWORKS; do
            echo "      Network: $network" | $TEE_CMD "$ROLLBACK_LOG"
        done
    fi
else
    if [ "$ROLLBACK_SUCCESS" != "true" ]; then
        echo "⚠️ Skipping HTTP health check - rollback was not successful" | $TEE_CMD "$ROLLBACK_LOG"
    elif [ -z "${HEALTH_CHECK_URL:-}" ]; then
        echo "⚠️ Skipping HTTP health check - no URL configured" | $TEE_CMD "$ROLLBACK_LOG"
    fi
fi

# Final Validation Step
echo "" | $TEE_CMD "$ROLLBACK_LOG"
echo "🔍 Final validation..." | $TEE_CMD "$ROLLBACK_LOG"

VALIDATION_PASSED="true"

# 1. Check systemd service is active
if /bin/systemctl is-active --quiet "$SERVICE_NAME"; then
    echo "   ✅ Systemd service is active" | $TEE_CMD "$ROLLBACK_LOG"
else
    echo "   ❌ Systemd service is NOT active" | $TEE_CMD "$ROLLBACK_LOG"
    VALIDATION_PASSED="false"
fi

# 2. Check expected number of containers
EXPECTED_CONTAINERS="${EXPECTED_CONTAINERS}"  # Use value from Spring properties
if [[ -z "$EXPECTED_CONTAINERS" ]]; then
    echo "⚠️ Warning: EXPECTED_CONTAINERS not set, skipping container count validation"
    EXPECTED_CONTAINERS=0
fi
ACTUAL_CONTAINERS=$($DOCKER_CMD ps --format "{{.Names}}" | grep -c "^${CONTAINER_PREFIX}" || true)
# Ensure we have a valid number
ACTUAL_CONTAINERS=$(echo "$ACTUAL_CONTAINERS" | tr -d '\n' | grep -o '[0-9]*' | head -1)
ACTUAL_CONTAINERS=${ACTUAL_CONTAINERS:-0}
if [ "$ACTUAL_CONTAINERS" -ge 1 ]; then
    echo "   ✅ Running containers: $ACTUAL_CONTAINERS" | $TEE_CMD "$ROLLBACK_LOG"
else
    echo "   ❌ No containers running (expected at least 1)" | $TEE_CMD "$ROLLBACK_LOG"
    VALIDATION_PASSED="false"
fi

# 3. Check no containers in restart loop
RESTARTING_OUTPUT=$($DOCKER_CMD ps --format "{{.Names}} {{.Status}}" | grep "^${CONTAINER_PREFIX}" | grep "Restarting" || true)
if [ -z "$RESTARTING_OUTPUT" ]; then
    RESTARTING=0
else
    RESTARTING=$(echo "$RESTARTING_OUTPUT" | wc -l)
fi
if [ "$RESTARTING" -eq 0 ]; then
    echo "   ✅ No containers in restart loop" | $TEE_CMD "$ROLLBACK_LOG"
else
    echo "   ❌ $RESTARTING containers are restarting" | $TEE_CMD "$ROLLBACK_LOG"
    VALIDATION_PASSED="false"
fi

# 4. Check all essential files are present and correct
echo "   🔍 Checking essential files..." | $TEE_CMD "$ROLLBACK_LOG"
FILES_OK=true
for file in "${ESSENTIAL_FILES[@]}"; do
    if [[ -f "$DEPLOY_DIR/$file" ]]; then
        echo "      ✓ $file present" | $TEE_CMD "$ROLLBACK_LOG"
    else
        echo "      ✗ $file MISSING!" | $TEE_CMD "$ROLLBACK_LOG"
        FILES_OK=false
    fi
done
if [ "$FILES_OK" = "false" ]; then
    echo "   ❌ Some essential files are missing" | $TEE_CMD "$ROLLBACK_LOG"
    VALIDATION_PASSED="false"
else
    echo "   ✅ All essential files present" | $TEE_CMD "$ROLLBACK_LOG"
fi

# Final decision
if [ "$VALIDATION_PASSED" = "false" ]; then
    echo "" | $TEE_CMD "$ROLLBACK_LOG"
    echo "⚠️ Validation failed - marking rollback as failed" | $TEE_CMD "$ROLLBACK_LOG"
    ROLLBACK_SUCCESS="false"
fi

echo "" | $TEE_CMD "$ROLLBACK_LOG"
if [ "$ROLLBACK_SUCCESS" = "true" ]; then
    echo "✅ ROLLBACK SUCCESSFUL" | $TEE_CMD "$ROLLBACK_LOG"
    echo "ROLLBACK_STATUS=SUCCESS"
    echo "HEALTH_CHECK_STATUS=$HEALTH_CHECK_PASSED"
else
    echo "❌ ROLLBACK FAILED" | $TEE_CMD "$ROLLBACK_LOG"
    echo "ROLLBACK_STATUS=FAILED"
    echo "HEALTH_CHECK_STATUS=$HEALTH_CHECK_PASSED"
fi
echo "Log: $ROLLBACK_LOG" | $TEE_CMD "$ROLLBACK_LOG"

# Exit with appropriate code
if [ "$ROLLBACK_SUCCESS" = "true" ]; then
    exit 0
else
    exit 1
fi
