# Google Secret Manager Migration Guide

## 🎯 Migration from HCP Vault to Google Secret Manager

### ✅ What We've Done
1. **Removed all HCP Vault code** from Spring Boot
2. **Created Google Secret Manager init container** (`docker/google-secrets-init/`)
3. **Updated configuration files** to remove vault references
4. **Simplified secret reading** - only from mounted volumes, no fallback

### 📦 Architecture Changes

**Before (HCP Vault):**
```
Init Container (HCP) → Mounted Volume → Spring Boot
                 ↓ (fallback)
             HCP Direct API
```

**After (Google Secret Manager):**
```
Init Container (GSM) → Mounted Volume → Spring Boot
                      (no fallback - simpler!)
```

### 🔐 Secrets to Create in Google Secret Manager

Since you have 2 Firebase projects, you already have the Google Cloud projects:
- **TEST**: `check-it-out-47c50`
- **PROD**: `check-it-out-prod`

#### Test Environment Secrets (prefix: `test-`)
```bash
test-postgres-user
test-postgres-password
test-postgres-db
test-app-db-user
test-app-db-password
test-instagram-client-secret
test-meta-app-secret
test-jwt-secret
test-cookie-hmac-secret
test-firebase-service-account  # Upload service-account.json
```

#### Production Environment Secrets (prefix: `prod-`)
```bash
prod-postgres-user
prod-postgres-password
prod-postgres-db
prod-app-db-user
prod-app-db-password
prod-instagram-client-secret
prod-meta-app-secret
prod-jwt-secret
prod-cookie-hmac-secret
prod-firebase-service-account  # Upload service-accountPROD.json
```

### 🚀 Deployment Steps

#### 1. Create Secrets in Google Secret Manager
```bash
# For test environment
gcloud config set project check-it-out-47c50

# Create each secret
echo -n "your-secret-value" | gcloud secrets create test-postgres-password \
  --data-file=- \
  --replication-policy="automatic"

# For Firebase service account (JSON file)
gcloud secrets create test-firebase-service-account \
  --data-file=src/main/resources/service-account.json \
  --replication-policy="automatic"
```

#### 2. Grant Permissions
```bash
# Grant the Firebase service account access to secrets
gcloud secrets add-iam-policy-binding test-postgres-password \
  --member="serviceAccount:firebase-adminsdk-yn7bs@check-it-out-47c50.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

#### 3. Build Init Container
```bash
cd docker/google-secrets-init
docker build -t gcr.io/check-it-out-47c50/google-secrets-init:latest .
docker push gcr.io/check-it-out-47c50/google-secrets-init:latest
```

#### 4. Deploy with New docker-compose
```bash
# Use the service account key for authentication
export GCP_SA_KEY_PATH=./src/main/resources/service-account.json
export GCP_PROJECT_ID=check-it-out-47c50
export ENVIRONMENT=test

# Start services
docker-compose -f deployment/test/docker-compose-gsm.yml up -d
```

### 🔍 Verification

Check that secrets are loaded:
```bash
# Check init container logs
docker logs instagram-platform-test-secrets-init

# Check secrets file was created
docker exec instagram-platform-test-secrets-init ls -la /app/config/

# Test Spring Boot can read secrets
curl http://localhost:8082/api/actuator/health
```

### 📋 Key Differences from HCP

1. **No fallback mechanism** - simpler, more reliable
2. **Uses Firebase service account** - no separate credentials needed
3. **Automatic caching** - same as HCP for offline resilience
4. **Environment-based prefixes** - `test-*` for test, `prod-*` for production

### ⚠️ Important Notes

- The init container uses the **same service account** as Firebase
- Secrets are cached locally for resilience
- Spring Boot reads from `/app/config/secrets.env` (same as before)
- No code changes needed in Spring Boot - it just reads from mounted files

### 🏆 Benefits of This Migration

1. **Unified authentication** - same service account for Firebase and secrets
2. **Native Google Cloud integration** - no third-party dependencies
3. **Simpler architecture** - no fallback complexity
4. **Cost effective** - Google Secret Manager is very affordable
5. **Better security** - secrets never in code, only in Google Cloud

### 📅 Timeline

- **Now**: Create secrets in Google Secret Manager
- **Test**: Deploy to test environment
- **Before HCP deadline**: Migrate production
- **After migration**: Remove any remaining HCP references

### 🆘 Troubleshooting

If secrets aren't loading:
1. Check service account has `secretmanager.secretAccessor` role
2. Verify secret names match exactly (with environment prefix)
3. Check init container logs for errors
4. Ensure service account key is mounted correctly

### 🎯 Success Criteria

- [ ] All secrets created in Google Secret Manager
- [ ] Init container builds and runs successfully
- [ ] Spring Boot starts without secret errors
- [ ] OAuth flows still work
- [ ] Database connections work
- [ ] No HCP references remain in code