#!/bin/sh
# =============================================================================
# SPRING BOOT ENTRYPOINT - PRODUCTION VERSION
# Purpose: Wait for secrets file and start Spring Boot application
# Note: Init container strips PROD_ prefix, so secrets are unprefixed
# Spring Boot reads directly from /app/config/secrets.env via ProductionSecretService
# =============================================================================

set -e

echo "🚀 Starting Spring Boot application (PRODUCTION)..."

# Wait for secrets file to be available
SECRETS_FILE="/app/config/secrets.env"
WAIT_TIME=0
MAX_WAIT=120  # Longer timeout for production

while [ ! -f "$SECRETS_FILE" ] && [ $WAIT_TIME -lt $MAX_WAIT ]; do
    if [ $((WAIT_TIME % 10)) -eq 0 ]; then
        echo "⏳ Waiting for secrets file... ($WAIT_TIME/$MAX_WAIT seconds)"
    fi
    sleep 1
    WAIT_TIME=$((WAIT_TIME + 1))
done

if [ ! -f "$SECRETS_FILE" ]; then
    echo "❌ ERROR: Secrets file not found after $MAX_WAIT seconds!"
    echo "Expected location: $SECRETS_FILE"
    exit 1
fi

echo "✅ Secrets file found at $SECRETS_FILE"

# Quick validation - check if file contains expected secrets (now unprefixed)
# NOTE: Not checking POSTGRES_USER, POSTGRES_PASSWORD, DATABASE_HOST, DATABASE_PORT
# These are provided in application-prod.yml, not via secrets
MISSING_SECRETS=""

# Database password (name/username hardcoded in application-prod.yml)
if ! grep -q "DATABASE_PASSWORD=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} DATABASE_PASSWORD"
fi
# DATABASE_HOST and DATABASE_PORT are provided in application-prod.yml

# Application secrets
if ! grep -q "JWT_SECRET=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} JWT_SECRET"
fi
if ! grep -q "COOKIE_HMAC_SECRET=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} COOKIE_HMAC_SECRET"
fi

# Meta/Instagram secrets
if ! grep -q "META_APP_SECRET=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} META_APP_SECRET"
fi
if ! grep -q "INSTAGRAM_CLIENT_SECRET=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} INSTAGRAM_CLIENT_SECRET"
fi

# Firebase secret
if ! grep -q "FIREBASE_SERVICE_ACCOUNT_JSON=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} FIREBASE_SERVICE_ACCOUNT_JSON"
fi

# Instagram test token
if ! grep -q "INSTAGRAM_TEST_TOKEN=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} INSTAGRAM_TEST_TOKEN"
fi

# Admin configuration secrets
if ! grep -q "ADMIN_FIREBASE_UID=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} ADMIN_FIREBASE_UID"
fi
if ! grep -q "ADMIN_EMAIL=" "$SECRETS_FILE"; then
    MISSING_SECRETS="${MISSING_SECRETS} ADMIN_EMAIL"
fi

if [ -n "$MISSING_SECRETS" ]; then
    echo "⚠️  WARNING: Missing secrets:$MISSING_SECRETS"
    echo "These secrets should exist in GSM with PROD_ prefix"
fi

# Log configuration for debugging
echo "📋 Configuration:"
echo "   Spring Profile: ${SPRING_PROFILES_ACTIVE:-prod}"
echo "   Server Port: ${SERVER_PORT:-8083}"
echo "   Secrets Mount Path: ${SECRETS_MOUNT_PATH:-/app/config}"
echo "   Java Options: ${JAVA_OPTS}"

# Create log directory if it doesn't exist
LOG_DIR="/var/log/instagram-platform/prod/application"
if [ ! -d "$LOG_DIR" ]; then
    echo "📁 Creating log directory: $LOG_DIR"
    mkdir -p "$LOG_DIR" 2>/dev/null || echo "⚠️  Could not create log directory (may already exist)"
fi

# Verify app.jar exists
if [ ! -f /app/app.jar ]; then
    echo "❌ ERROR: app.jar not found at /app/app.jar"
    echo "📂 Contents of /app directory:"
    ls -la /app/ || echo "Could not list /app directory"
    exit 1
fi

# Source the secrets file to make them available as environment variables
echo "📋 Loading secrets as environment variables..."

# Debug: Show secrets file status (NO VALUES!)
echo "🔍 Secrets file status:"
SECRET_COUNT=$(grep -c "=" "$SECRETS_FILE" || echo "0")
echo "   Total secrets loaded: $SECRET_COUNT"

set -a  # Export all variables
source "$SECRETS_FILE"
set +a  # Stop exporting

# Debug: Verify environment variables are set (values masked - NEVER log actual values!)
echo "🔍 Verifying exported variables (existence only, no values)..."

# Database password (name/username hardcoded in application-prod.yml)
if [ -n "$DATABASE_PASSWORD" ]; then
    echo "   ✅ DATABASE_PASSWORD is set (${#DATABASE_PASSWORD} chars)"
else
    echo "   ❌ DATABASE_PASSWORD is NOT set"
fi

# Application secrets
if [ -n "$JWT_SECRET" ]; then
    echo "   ✅ JWT_SECRET is set (${#JWT_SECRET} chars)"
else
    echo "   ❌ JWT_SECRET is NOT set"
fi
if [ -n "$COOKIE_HMAC_SECRET" ]; then
    echo "   ✅ COOKIE_HMAC_SECRET is set (${#COOKIE_HMAC_SECRET} chars)"
else
    echo "   ❌ COOKIE_HMAC_SECRET is NOT set"
fi

# Meta/Instagram secrets
if [ -n "$META_APP_SECRET" ]; then
    echo "   ✅ META_APP_SECRET is set (${#META_APP_SECRET} chars)"
else
    echo "   ❌ META_APP_SECRET is NOT set"
fi
if [ -n "$INSTAGRAM_CLIENT_SECRET" ]; then
    echo "   ✅ INSTAGRAM_CLIENT_SECRET is set (${#INSTAGRAM_CLIENT_SECRET} chars)"
else
    echo "   ❌ INSTAGRAM_CLIENT_SECRET is NOT set"
fi

# Firebase secret
if [ -n "$FIREBASE_SERVICE_ACCOUNT_JSON" ]; then
    # Firebase JSON is typically base64 encoded and very long
    echo "   ✅ FIREBASE_SERVICE_ACCOUNT_JSON is set (${#FIREBASE_SERVICE_ACCOUNT_JSON} chars - base64)"
else
    echo "   ❌ FIREBASE_SERVICE_ACCOUNT_JSON is NOT set"
fi

# Instagram test token
if [ -n "$INSTAGRAM_TEST_TOKEN" ]; then
    echo "   ✅ INSTAGRAM_TEST_TOKEN is set (${#INSTAGRAM_TEST_TOKEN} chars)"
else
    echo "   ❌ INSTAGRAM_TEST_TOKEN is NOT set"
fi

# Admin configuration
if [ -n "$ADMIN_FIREBASE_UID" ]; then
    echo "   ✅ ADMIN_FIREBASE_UID is set (${#ADMIN_FIREBASE_UID} chars)"
else
    echo "   ❌ ADMIN_FIREBASE_UID is NOT set"
fi
if [ -n "$ADMIN_EMAIL" ]; then
    echo "   ✅ ADMIN_EMAIL is set (${#ADMIN_EMAIL} chars)"
else
    echo "   ❌ ADMIN_EMAIL is NOT set"
fi

# Verify critical database password is set (name/username hardcoded in application-prod.yml)
if [ -z "$DATABASE_PASSWORD" ]; then
    echo "❌ ERROR: Missing DATABASE_PASSWORD!"
    echo "Required from GSM: PROD_DATABASE_PASSWORD"
    exit 1
fi

# Export Spring datasource password (name/username from application-prod.yml)
export SPRING_DATASOURCE_PASSWORD="${DATABASE_PASSWORD}"
export SPRING_LIQUIBASE_PASSWORD="${DATABASE_PASSWORD}"

echo "✅ Database configuration loaded:"
echo "   Database Password: [SET - ${#DATABASE_PASSWORD} chars]"
echo ""
echo "✅ Application secrets loaded:"
echo "   JWT_SECRET: [SET - ${#JWT_SECRET} chars]"
echo "   COOKIE_HMAC_SECRET: [SET - ${#COOKIE_HMAC_SECRET} chars]"
echo "   META_APP_SECRET: [SET - ${#META_APP_SECRET} chars]"
echo "   INSTAGRAM_CLIENT_SECRET: [SET - ${#INSTAGRAM_CLIENT_SECRET} chars]"
echo "   FIREBASE_SERVICE_ACCOUNT_JSON: [SET - ${#FIREBASE_SERVICE_ACCOUNT_JSON} chars]"
echo "   INSTAGRAM_TEST_TOKEN: [SET - ${#INSTAGRAM_TEST_TOKEN} chars]"
echo ""
echo "✅ Admin configuration loaded:"
echo "   ADMIN_FIREBASE_UID: [SET - ${#ADMIN_FIREBASE_UID} chars]"
echo "   ADMIN_EMAIL: [SET - ${#ADMIN_EMAIL} chars]"

# CRITICAL: Never log actual secret values, only their existence and length
echo ""
echo "🔒 Security: All secret values are masked in logs"

# Start the Spring Boot application
echo "🚀 Starting Spring Boot with secrets loaded as environment variables..."
exec java ${JAVA_OPTS} -jar /app/app.jar "$@"
