#!/bin/bash
# =============================================================================
# Google Cloud Storage Bucket Setup for Loki Logs
# =============================================================================

set -euo pipefail

# Configuration
PROJECT_ID="${1:-your-gcp-project-id}"
BUCKET_NAME="${2:-checkitout-logs-prod}"
REGION="${3:-us-central1}"
SERVICE_ACCOUNT_NAME="loki-gcs-writer"
SERVICE_ACCOUNT_EMAIL="${SERVICE_ACCOUNT_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

echo "Setting up GCS bucket for Loki logs..."

# 1. Create the bucket with uniform bucket-level access
echo "Creating bucket ${BUCKET_NAME}..."
gsutil mb -p "${PROJECT_ID}" \
    -c STANDARD \
    -l "${REGION}" \
    -b on \
    "gs://${BUCKET_NAME}/" || echo "Bucket might already exist"

# 2. Enable versioning for data protection
echo "Enabling versioning..."
gsutil versioning set on "gs://${BUCKET_NAME}/"

# 3. Set encryption with customer-managed key (optional)
# If using CMEK, uncomment and configure:
# KMS_KEY="projects/${PROJECT_ID}/locations/${REGION}/keyRings/loki-keyring/cryptoKeys/loki-key"
# gsutil kms encryption "gs://${BUCKET_NAME}/" -k "${KMS_KEY}"

# 4. Configure lifecycle rule for 30-day retention
echo "Setting up lifecycle rules for 30-day retention..."
cat > /tmp/lifecycle.json << 'EOF'
{
  "lifecycle": {
    "rule": [
      {
        "action": {
          "type": "Delete"
        },
        "condition": {
          "age": 30,
          "matchesPrefix": ["loki_index_", "loki-"]
        }
      },
      {
        "action": {
          "type": "Delete"
        },
        "condition": {
          "age": 7,
          "matchesPrefix": ["tmp/"]
        }
      }
    ]
  }
}
EOF

gsutil lifecycle set /tmp/lifecycle.json "gs://${BUCKET_NAME}/"

# 5. Create service account for Loki
echo "Creating service account ${SERVICE_ACCOUNT_NAME}..."
gcloud iam service-accounts create "${SERVICE_ACCOUNT_NAME}" \
    --display-name="Loki GCS Writer" \
    --project="${PROJECT_ID}" || echo "Service account might already exist"

# 6. Grant necessary permissions to the service account
echo "Granting permissions..."
gsutil iam ch "serviceAccount:${SERVICE_ACCOUNT_EMAIL}:objectAdmin" "gs://${BUCKET_NAME}/"
gsutil iam ch "serviceAccount:${SERVICE_ACCOUNT_EMAIL}:legacyBucketReader" "gs://${BUCKET_NAME}/"

# 7. Create and download service account key
echo "Creating service account key..."
gcloud iam service-accounts keys create \
    ./gcs-service-account.json \
    --iam-account="${SERVICE_ACCOUNT_EMAIL}" \
    --project="${PROJECT_ID}"

# 8. Set bucket configuration for security
echo "Configuring bucket security..."

# Enable uniform bucket-level access (already done with -b on)
gsutil uniformbucketlevelaccess set on "gs://${BUCKET_NAME}/"

# Set public access prevention
gsutil pap set enforced "gs://${BUCKET_NAME}/"

# 9. Configure logging for the bucket itself
echo "Enabling bucket access logging..."
LOG_BUCKET="${BUCKET_NAME}-logs"
gsutil mb -p "${PROJECT_ID}" -c STANDARD -l "${REGION}" "gs://${LOG_BUCKET}/" || echo "Log bucket might exist"
gsutil logging set on -b "gs://${LOG_BUCKET}/" -o "access_logs/" "gs://${BUCKET_NAME}/"

# Set lifecycle for log bucket (90 days)
cat > /tmp/log_lifecycle.json << 'EOF'
{
  "lifecycle": {
    "rule": [
      {
        "action": {
          "type": "Delete"
        },
        "condition": {
          "age": 90
        }
      }
    ]
  }
}
EOF

gsutil lifecycle set /tmp/log_lifecycle.json "gs://${LOG_BUCKET}/"

# 10. Configure CORS if needed for web access
cat > /tmp/cors.json << 'EOF'
[
  {
    "origin": ["https://grafana.com", "https://checkitout.app"],
    "method": ["GET", "HEAD"],
    "responseHeader": ["Content-Type"],
    "maxAgeSeconds": 3600
  }
]
EOF

gsutil cors set /tmp/cors.json "gs://${BUCKET_NAME}/"

# 11. Set up monitoring alerts
echo "Setting up monitoring alerts..."
cat > /tmp/alert_policy.yaml << EOF
displayName: "Loki GCS Bucket High Usage"
conditions:
  - displayName: "Bucket size > 100GB"
    conditionThreshold:
      filter: 'resource.type="gcs_bucket" AND resource.labels.bucket_name="${BUCKET_NAME}" AND metric.type="storage.googleapis.com/storage/total_bytes"'
      comparison: COMPARISON_GT
      thresholdValue: 107374182400  # 100GB in bytes
      duration: 300s
notificationChannels: []  # Add your notification channels here
EOF

# Create the alert (requires gcloud alpha)
# gcloud alpha monitoring policies create --policy-from-file=/tmp/alert_policy.yaml

# 12. Print summary
echo ""
echo "=========================================="
echo "GCS Bucket Setup Complete!"
echo "=========================================="
echo "Bucket Name: ${BUCKET_NAME}"
echo "Region: ${REGION}"
echo "Retention: 30 days"
echo "Encryption: Default (Google-managed)"
echo "Service Account: ${SERVICE_ACCOUNT_EMAIL}"
echo "Key File: ./gcs-service-account.json"
echo ""
echo "Next steps:"
echo "1. Copy gcs-service-account.json to /opt/loki/"
echo "2. Update .env file with bucket name"
echo "3. Secure the service account key file:"
echo "   chmod 400 gcs-service-account.json"
echo "   chown loki:loki gcs-service-account.json"
echo ""
echo "Test bucket access:"
echo "gsutil ls gs://${BUCKET_NAME}/"
echo ""
echo "Monitor usage:"
echo "gsutil du -sh gs://${BUCKET_NAME}/"
