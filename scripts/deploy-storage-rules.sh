#!/bin/bash

# Deployment script for Firebase Storage rules
# This ensures rules are properly tested before production deployment

set -e  # Exit on error

echo "🔒 Firebase Storage Rules Deployment"
echo "===================================="

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo "❌ Firebase CLI not found. Please install: npm install -g firebase-tools"
    exit 1
fi

# Get environment argument
ENV=${1:-test}

if [ "$ENV" == "production" ]; then
    RULES_FILE="firebase-storage.rules"
    PROJECT_ID="instagram-platform-prod"
    echo "⚠️  Deploying to PRODUCTION"
    
    # Extra confirmation for production
    read -p "Are you sure you want to deploy to production? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        echo "❌ Deployment cancelled"
        exit 1
    fi
else
    RULES_FILE="firebase-storage-test.rules"
    PROJECT_ID="instagram-platform-test"
    echo "📝 Deploying to TEST environment"
fi

# Deploy rules
echo "📤 Deploying $RULES_FILE to $PROJECT_ID..."
firebase deploy --only storage:rules --project $PROJECT_ID

echo "✅ Storage rules deployed successfully!"

# Run security tests
if [ "$ENV" != "production" ]; then
    echo "🧪 Running security tests..."
    npm run test:storage-rules
fi

echo "🎉 Deployment complete!"
