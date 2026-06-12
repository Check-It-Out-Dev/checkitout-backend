#!/bin/bash
# =============================================================================
# GOOGLE SECRET MANAGER - SECRET CREATION SCRIPT
# Run this script once to create all required secrets in Google Secret Manager
# =============================================================================

set -e

# Configuration
ENVIRONMENT="${1:-test}"
if [ "$ENVIRONMENT" != "test" ] && [ "$ENVIRONMENT" != "prod" ]; then
    echo "Usage: $0 [test|prod]"
    exit 1
fi

# Set project based on environment
if [ "$ENVIRONMENT" = "test" ]; then
    PROJECT_ID="check-it-out-47c50"
    ENV_PREFIX="test"
else
    PROJECT_ID="check-it-out-prod"
    ENV_PREFIX="prod"
fi

echo "🔐 Creating secrets in Google Secret Manager"
echo "============================================"
echo "Environment: $ENVIRONMENT"
echo "Project: $PROJECT_ID"
echo ""

# Check if authenticated
echo "🔑 Checking authentication..."
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q .; then
    echo "❌ Not authenticated. Please run: gcloud auth login"
    exit 1
fi

# Set project
gcloud config set project "$PROJECT_ID"

# Function to create or update a secret
create_or_update_secret() {
    local SECRET_NAME="$1"
    local SECRET_VALUE="$2"
    local FULL_NAME="${ENV_PREFIX}-${SECRET_NAME}"
    
    echo "📝 Processing: $FULL_NAME"
    
    # Check if secret exists
    if gcloud secrets describe "$FULL_NAME" --project="$PROJECT_ID" >/dev/null 2>&1; then
        echo "   ✓ Secret exists, adding new version..."
        echo -n "$SECRET_VALUE" | gcloud secrets versions add "$FULL_NAME" \
            --data-file=- \
            --project="$PROJECT_ID"
    else
        echo "   ✓ Creating new secret..."
        echo -n "$SECRET_VALUE" | gcloud secrets create "$FULL_NAME" \
            --data-file=- \
            --replication-policy="automatic" \
            --project="$PROJECT_ID"
    fi
}

# Read secrets from .env file or prompt
if [ -f "../../.env" ]; then
    echo "📄 Loading secrets from .env file..."
    source ../../.env
elif [ -f ".env" ]; then
    echo "📄 Loading secrets from .env file..."
    source .env
else
    echo "⚠️ No .env file found. Please enter secrets manually:"
    echo ""
    
    read -p "POSTGRES_USER: " POSTGRES_USER
    read -sp "POSTGRES_PASSWORD: " POSTGRES_PASSWORD
    echo ""
    read -p "POSTGRES_DB: " POSTGRES_DB
    read -p "APP_DB_USER: " APP_DB_USER
    read -sp "APP_DB_PASSWORD: " APP_DB_PASSWORD
    echo ""
    read -sp "INSTAGRAM_CLIENT_SECRET: " INSTAGRAM_CLIENT_SECRET
    echo ""
    read -sp "META_APP_SECRET: " META_APP_SECRET
    echo ""
    read -sp "JWT_SECRET: " JWT_SECRET
    echo ""
    read -sp "COOKIE_HMAC_SECRET: " COOKIE_HMAC_SECRET
    echo ""
fi

echo ""
echo "🚀 Creating secrets in Google Secret Manager..."
echo ""

# Database secrets
create_or_update_secret "postgres-user" "${POSTGRES_USER:-postgres}"
create_or_update_secret "postgres-password" "${POSTGRES_PASSWORD}"
create_or_update_secret "postgres-db" "${POSTGRES_DB:-checkitout_${ENV_PREFIX}_db}"
create_or_update_secret "app-db-user" "${APP_DB_USER:-checkitout_app_${ENV_PREFIX}}"
create_or_update_secret "app-db-password" "${APP_DB_PASSWORD}"

# OAuth secrets
create_or_update_secret "instagram-client-secret" "${INSTAGRAM_CLIENT_SECRET}"
create_or_update_secret "meta-app-secret" "${META_APP_SECRET}"

# JWT/Cookie secrets
create_or_update_secret "jwt-secret" "${JWT_SECRET}"
create_or_update_secret "cookie-hmac-secret" "${COOKIE_HMAC_SECRET}"

# Firebase service account (special handling)
echo "📝 Processing: ${ENV_PREFIX}-firebase-service-account"
if [ "$ENVIRONMENT" = "test" ]; then
    FIREBASE_SA_FILE="../../src/main/resources/service-account.json"
else
    FIREBASE_SA_FILE="../../src/main/resources/service-accountPROD.json"
fi

if [ -f "$FIREBASE_SA_FILE" ]; then
    echo "   ✓ Using Firebase service account from: $FIREBASE_SA_FILE"
    
    if gcloud secrets describe "${ENV_PREFIX}-firebase-service-account" --project="$PROJECT_ID" >/dev/null 2>&1; then
        echo "   ✓ Secret exists, adding new version..."
        gcloud secrets versions add "${ENV_PREFIX}-firebase-service-account" \
            --data-file="$FIREBASE_SA_FILE" \
            --project="$PROJECT_ID"
    else
        echo "   ✓ Creating new secret..."
        gcloud secrets create "${ENV_PREFIX}-firebase-service-account" \
            --data-file="$FIREBASE_SA_FILE" \
            --replication-policy="automatic" \
            --project="$PROJECT_ID"
    fi
else
    echo "   ⚠️ Firebase service account file not found at: $FIREBASE_SA_FILE"
fi

echo ""
echo "✅ Secret creation complete!"
echo ""
echo "📊 To verify secrets, run:"
echo "   gcloud secrets list --project=$PROJECT_ID"
echo ""
echo "🔍 To view a secret value:"
echo "   gcloud secrets versions access latest --secret=${ENV_PREFIX}-<secret-name> --project=$PROJECT_ID"
echo ""

# Grant service account access to secrets
echo "🔐 Setting up service account permissions..."
echo ""

# Get the Firebase service account email
if [ "$ENVIRONMENT" = "test" ]; then
    SA_EMAIL="firebase-adminsdk-yn7bs@check-it-out-47c50.iam.gserviceaccount.com"
else
    SA_EMAIL="firebase-adminsdk-fbsvc@check-it-out-prod.iam.gserviceaccount.com"
fi

echo "📧 Service account: $SA_EMAIL"
echo ""

# Grant access to all secrets
for SECRET in postgres-user postgres-password postgres-db app-db-user app-db-password \
              instagram-client-secret meta-app-secret jwt-secret cookie-hmac-secret \
              firebase-service-account; do
    
    FULL_SECRET="${ENV_PREFIX}-${SECRET}"
    echo "   🔑 Granting access to: $FULL_SECRET"
    
    gcloud secrets add-iam-policy-binding "$FULL_SECRET" \
        --member="serviceAccount:$SA_EMAIL" \
        --role="roles/secretmanager.secretAccessor" \
        --project="$PROJECT_ID" \
        --quiet >/dev/null 2>&1
done

echo ""
echo "✅ All permissions granted!"
echo ""
echo "🎯 Next steps:"
echo "1. Build the init container:"
echo "   docker build -t gcr.io/$PROJECT_ID/google-secrets-init:latest ."
echo ""
echo "2. Push to Container Registry:"
echo "   docker push gcr.io/$PROJECT_ID/google-secrets-init:latest"
echo ""
echo "3. Update docker-compose.yml to use the new init container"
echo ""