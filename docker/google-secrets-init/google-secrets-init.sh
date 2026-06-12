#!/bin/bash
# =============================================================================
# GOOGLE SECRET MANAGER INIT SCRIPT - PROJECT-BASED FETCHING
# Fetches ALL secrets from the project (TEST project has TEST_*, PROD has PROD_*)
# Strips prefix when writing if STRIP_PREFIX_ON_WRITE=true
# No filtering needed - projects are already separated by environment
# =============================================================================

set -uo pipefail

# Configuration from environment variables (passed by docker-compose)
ENVIRONMENT="${ENVIRONMENT:-test}"
GCP_PROJECT_ID="${GCP_PROJECT_ID:-}"
GSM_SECRET_PREFIX="${GSM_SECRET_PREFIX:-}"  # Used only for stripping, not filtering
STRIP_PREFIX_ON_WRITE="${STRIP_PREFIX_ON_WRITE:-true}"
GCP_SA_KEY_PATH="${GOOGLE_APPLICATION_CREDENTIALS:-/run/secrets/service-account.json}"
SECRETS_OUTPUT_FILE="/app/config/secrets.env"
SECRETS_CACHE_FILE="/app/config/.secrets.cache"
BACKUP_DIR="/app/backup"
MARKER_FILE="/app/config/.init-complete"
CACHE_METADATA_FILE="/app/config/.cache-metadata"
GSM_INIT_DEBUG="${GSM_INIT_DEBUG:-false}"

# Determine defaults if not provided
if [ -z "$GCP_PROJECT_ID" ]; then
    case "$ENVIRONMENT" in
        "test"|"tst")
            GCP_PROJECT_ID="check-it-out-47c50"
            ;;
        "prod"|"production")
            GCP_PROJECT_ID="check-it-out-prod"
            ;;
        *)
            echo "❌ ERROR: Unknown environment: $ENVIRONMENT"
            exit 1
            ;;
    esac
fi

if [ -z "$GSM_SECRET_PREFIX" ]; then
    case "$ENVIRONMENT" in
        "test"|"tst")
            GSM_SECRET_PREFIX="TEST_"
            ;;
        "prod"|"production")
            GSM_SECRET_PREFIX="PROD_"
            ;;
        *)
            GSM_SECRET_PREFIX=""
            ;;
    esac
fi

echo "🔐 Google Secret Manager Init Container"
echo "=========================================================="
echo "Environment: $ENVIRONMENT"
echo "Project ID: $GCP_PROJECT_ID"
if [ -n "$GSM_SECRET_PREFIX" ]; then
    echo "Expected Prefix: $GSM_SECRET_PREFIX (for stripping)"
fi
echo "Strip Prefix: $STRIP_PREFIX_ON_WRITE"
echo "Service Account: $GCP_SA_KEY_PATH"
[ "$GSM_INIT_DEBUG" = "true" ] && echo "Debug Mode: ENABLED"
echo ""

# Create directories
mkdir -p "$BACKUP_DIR" "$(dirname "$SECRETS_OUTPUT_FILE")" 2>/dev/null || true

# =============================================================================
# CHECK FOR CACHED SECRETS
# =============================================================================
CACHE_EXISTS=false
CACHE_AGE_HOURS="unknown"

if [ -f "$SECRETS_CACHE_FILE" ]; then
    CACHE_EXISTS=true
    echo "📦 Found cached secrets at: $SECRETS_CACHE_FILE"

    if [ -f "$CACHE_METADATA_FILE" ]; then
        CACHE_TIMESTAMP=$(grep "^CACHE_TIMESTAMP=" "$CACHE_METADATA_FILE" 2>/dev/null | cut -d= -f2 || echo "0")
        if [ -n "$CACHE_TIMESTAMP" ] && [ "$CACHE_TIMESTAMP" != "0" ]; then
            CURRENT_TIMESTAMP=$(date +%s)
            CACHE_AGE_SECONDS=$((CURRENT_TIMESTAMP - CACHE_TIMESTAMP))
            CACHE_AGE_HOURS=$((CACHE_AGE_SECONDS / 3600))
            echo "📅 Cache age: ${CACHE_AGE_HOURS} hours"
        fi
    fi
else
    echo "📭 No cached secrets found"
fi

# =============================================================================
# TRY TO FETCH SECRETS FROM GOOGLE SECRET MANAGER
# =============================================================================
GSM_FETCH_SUCCESS=false
GSM_CONNECTION_ERROR=""

fetch_secrets_from_gsm() {
    echo "🔐 Attempting to fetch secrets from Google Secret Manager..."

    # Check service account key
    if [ ! -f "$GCP_SA_KEY_PATH" ]; then
        GSM_CONNECTION_ERROR="Service account key not found at: $GCP_SA_KEY_PATH"
        return 1
    fi

    echo "✅ Service account key found"

    # Authenticate with Google Cloud
    echo "🔑 Authenticating with Google Cloud..."
    if ! gcloud auth activate-service-account --key-file="$GCP_SA_KEY_PATH" 2>/tmp/auth_error.log; then
        GSM_CONNECTION_ERROR="Authentication failed: $(cat /tmp/auth_error.log 2>/dev/null | head -n 1)"
        return 1
    fi

    echo "✅ Google Cloud authentication successful"
    
    # Set project
    gcloud config set project "$GCP_PROJECT_ID"

    # Create temp file for new secrets
    TEMP_SECRETS_FILE="/tmp/secrets_new.env"

    # Start building secrets file
    cat > "$TEMP_SECRETS_FILE" << EOF
# Secrets from Google Secret Manager - Retrieved $(date -Iseconds)
# Project: $GCP_PROJECT_ID
# All secrets fetched (no filtering - separate projects for TEST/PROD)
# Prefix stripped: ${GSM_SECRET_PREFIX:-none} (Stripped: $STRIP_PREFIX_ON_WRITE)
GSM_INIT_STATUS=success
GCP_PROJECT_ID=$GCP_PROJECT_ID
GSM_INIT_TIMESTAMP=$(date -Iseconds)
ENVIRONMENT=${ENVIRONMENT}

EOF

    # List ALL secrets in project (no filtering needed - separate projects for TEST/PROD)
    echo "📥 Fetching all secrets from project: $GCP_PROJECT_ID"
    SECRET_LIST=$(gcloud secrets list --project="$GCP_PROJECT_ID" --format="value(name)" 2>/tmp/list_error.log)
    
    if [ -z "$SECRET_LIST" ]; then
        echo "❌ No secrets found in project: $GCP_PROJECT_ID"
        GSM_CONNECTION_ERROR="No secrets found in project $GCP_PROJECT_ID"
        return 1
    fi
    
    # Count secrets
    SECRET_COUNT=0
    TOTAL_SECRETS=$(echo "$SECRET_LIST" | wc -l)
    echo "📦 Found $TOTAL_SECRETS secrets to fetch"
    echo ""
    
    # Fetch each secret
    while IFS= read -r SECRET_NAME; do
        if [ -z "$SECRET_NAME" ]; then
            continue
        fi
        
        # Determine the environment variable name
        if [ "$STRIP_PREFIX_ON_WRITE" = "true" ] && [ -n "$GSM_SECRET_PREFIX" ]; then
            # Remove prefix when writing
            ENV_VAR_NAME=${SECRET_NAME#${GSM_SECRET_PREFIX}}
        else
            # Keep original name
            ENV_VAR_NAME=$SECRET_NAME
        fi
        
        echo -n "  📥 Fetching: $SECRET_NAME"
        if [ "$SECRET_NAME" != "$ENV_VAR_NAME" ]; then
            echo -n " -> $ENV_VAR_NAME"
        fi
        echo ""
        
        # Fetch the secret value
        if SECRET_VALUE=$(gcloud secrets versions access latest \
            --secret="$SECRET_NAME" \
            --project="$GCP_PROJECT_ID" 2>/tmp/secret_error.log); then
            
            if [ -n "$SECRET_VALUE" ]; then
                # Handle special cases
                if [[ "$ENV_VAR_NAME" == *"FIREBASE"* ]] && [[ "$ENV_VAR_NAME" == *"JSON"* ]]; then
                    # Firebase service account JSON - encode to base64 if not already
                    if echo "$SECRET_VALUE" | jq -e . >/dev/null 2>&1; then
                        # It's valid JSON, encode it
                        SECRET_VALUE=$(echo "$SECRET_VALUE" | base64 -w 0)
                        echo "     ✅ Fetched and base64 encoded"
                    else
                        echo "     ✅ Fetched (already base64)"
                    fi
                else
                    echo "     ✅ Fetched successfully"
                fi
                
                # Debug mode - only show length, never values
                if [ "$GSM_INIT_DEBUG" = "true" ]; then
                    echo "     🔒 [${#SECRET_VALUE} chars]"
                fi
                
                # Write to file
                echo "${ENV_VAR_NAME}=${SECRET_VALUE}" >> "$TEMP_SECRETS_FILE"
                
                SECRET_COUNT=$((SECRET_COUNT + 1))
            else
                echo "     ⚠️ Empty value"
            fi
        else
            echo "     ❌ Failed: $(cat /tmp/secret_error.log 2>/dev/null | head -n 1)"
            # Continue with other secrets
        fi
        
        # Small delay to avoid rate limiting
        sleep 0.1
    done <<< "$SECRET_LIST"

    # Add secret count
    echo "GSM_SECRET_COUNT=$SECRET_COUNT" >> "$TEMP_SECRETS_FILE"

    if [ $SECRET_COUNT -eq 0 ]; then
        GSM_CONNECTION_ERROR="No secrets were successfully fetched"
        return 1
    fi

    echo ""
    echo "✅ Fetched $SECRET_COUNT secrets from Google Secret Manager"

    # Move temp file to final location
    mv "$TEMP_SECRETS_FILE" "$SECRETS_OUTPUT_FILE"

    # Create cache copy
    cp "$SECRETS_OUTPUT_FILE" "$SECRETS_CACHE_FILE"

    # Update cache metadata
    cat > "$CACHE_METADATA_FILE" << EOF
CACHE_TIMESTAMP=$(date +%s)
CACHE_DATE=$(date -Iseconds)
CACHE_SECRET_COUNT=$SECRET_COUNT
CACHE_SOURCE=gsm
EOF

    return 0
}

# Try to fetch secrets from Google Secret Manager
if fetch_secrets_from_gsm; then
    GSM_FETCH_SUCCESS=true
    echo "✅ Successfully fetched and cached secrets from Google Secret Manager"
else
    echo "⚠️ Failed to fetch secrets from Google Secret Manager: $GSM_CONNECTION_ERROR"

    # Check if we have a cache to fall back to
    if [ "$CACHE_EXISTS" = true ]; then
        echo "📦 Using cached secrets (age: ${CACHE_AGE_HOURS} hours)"
        echo "⚠️ WARNING: Running with cached secrets - Google Secret Manager was not accessible!"

        # Copy cache to output location
        cp "$SECRETS_CACHE_FILE" "$SECRETS_OUTPUT_FILE"

        # Update the file to indicate it's from cache
        sed -i "1s/^/# WARNING: Using CACHED secrets - Google Secret Manager was not accessible at $(date -Iseconds)\n/" "$SECRETS_OUTPUT_FILE"
        echo "GSM_INIT_STATUS=success_with_cache" >> "$SECRETS_OUTPUT_FILE"
        echo "CACHE_WARNING=true" >> "$SECRETS_OUTPUT_FILE"
        echo "CACHE_AGE_HOURS=${CACHE_AGE_HOURS}" >> "$SECRETS_OUTPUT_FILE"

        # Still mark as success so services can start
        GSM_FETCH_SUCCESS=true
    else
        echo "❌ ERROR: No cached secrets available and Google Secret Manager is not accessible!"
        echo "   This appears to be the first run - secrets cache has not been created yet."
        echo "   Cannot proceed without secrets."
        exit 1
    fi
fi

# =============================================================================
# VERIFY REQUIRED SECRETS (check for common ones after stripping prefix)
# =============================================================================
echo ""
echo "🔍 Verifying required secrets..."

# Required secrets (names after prefix stripping)
REQUIRED_SECRETS=()

# Additional required secrets based on environment
if [ "$ENVIRONMENT" = "test" ] || [ "$ENVIRONMENT" = "tst" ]; then
    # TEST environment — only passwords are required from GSM
    # Non-secrets (POSTGRES_USER, DATABASE_NAME, DATABASE_USERNAME) are hardcoded in entrypoint
    REQUIRED_SECRETS+=(
        "POSTGRES_PASSWORD"
        "DATABASE_PASSWORD"
    )
else
    # PROD environment uses external OVH database with Spring profile credentials
    # No database secrets required from Google Secret Manager
    echo "  ℹ️ Production environment - database credentials handled by Spring profile"
fi

echo "🐘 Checking required secrets..."
MISSING_SECRETS=()

for secret in "${REQUIRED_SECRETS[@]}"; do
    if grep -q "^${secret}=" "$SECRETS_OUTPUT_FILE" 2>/dev/null; then
        echo "  ✓ $secret found"
    else
        echo "  ✗ $secret NOT FOUND"
        MISSING_SECRETS+=("$secret")
    fi
done

if [ ${#MISSING_SECRETS[@]} -gt 0 ]; then
    echo "❌ Missing required secrets: ${MISSING_SECRETS[*]}"
    echo ""
    echo "Please ensure these secrets exist in Google Secret Manager with prefix: $GSM_SECRET_PREFIX"
    echo "Project: $GCP_PROJECT_ID"
    exit 1
fi

echo "✅ All required secrets present"

# =============================================================================
# FINALIZE
# =============================================================================

# Set secure permissions
chmod 644 "$SECRETS_OUTPUT_FILE"
[ -f "$SECRETS_CACHE_FILE" ] && chmod 644 "$SECRETS_CACHE_FILE"
[ -f "$CACHE_METADATA_FILE" ] && chmod 644 "$CACHE_METADATA_FILE"

# Create backup with timestamp
BACKUP_FILE="$BACKUP_DIR/secrets-$(date +%Y%m%d-%H%M%S).env"
cp "$SECRETS_OUTPUT_FILE" "$BACKUP_FILE"
echo "📦 Backup created: $BACKUP_FILE"

# Create success marker
echo "success" > "$MARKER_FILE"

# Summary
echo ""
echo "✅ Init container completed successfully!"
echo "📄 Secrets file: $SECRETS_OUTPUT_FILE"
echo "🔐 Environment: $ENVIRONMENT"
echo "☁️ Project: $GCP_PROJECT_ID"
if [ -n "$GSM_SECRET_PREFIX" ] && [ "$STRIP_PREFIX_ON_WRITE" = "true" ]; then
    echo "✏️ Prefix stripped: $GSM_SECRET_PREFIX"
fi
echo "📊 Secrets fetched: $SECRET_COUNT"
if [ -f "$CACHE_METADATA_FILE" ]; then
    source "$CACHE_METADATA_FILE"
    echo "💾 Secret source: ${CACHE_SOURCE:-unknown}"
    if [ "${CACHE_SOURCE}" = "gsm" ]; then
        echo "🔄 Cache updated: $(date -d @${CACHE_TIMESTAMP} 2>/dev/null || date -r ${CACHE_TIMESTAMP} 2>/dev/null || echo 'recently')"
    fi
fi
if grep -q "CACHE_WARNING=true" "$SECRETS_OUTPUT_FILE" 2>/dev/null; then
    echo "⚠️ WARNING: Using cached secrets - Google Secret Manager was not accessible!"
fi
echo ""

# Keep container running briefly for health checks to register success
echo "💤 Container will exit after health checks confirm success..."
sleep 10
echo "✅ Exiting successfully - secrets are ready!"
exit 0
