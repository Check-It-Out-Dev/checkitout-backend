#!/bin/bash

# =============================================================================
# SECRET TESTER SCRIPT - CI/CD OPTIMIZED
# Purpose: Comprehensive verification of secrets from shared volume
# Includes validation, security checks, and detailed reporting
# =============================================================================

set -euo pipefail

# Configuration
SECRETS_FILE='/app/config/secrets.env'
BACKUP_DIR='/app/backup'
LOG_FILE='/app/logs/secret-test.log'
CI_MODE="${CI:-false}"

# Initialize log
mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null || true
echo "[$(date -Iseconds)] Secret verification started" > "$LOG_FILE"

# Colors (disabled in CI mode)
if [ "$CI_MODE" = "false" ]; then
    GREEN='\033[0;32m'
    RED='\033[0;31m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    NC='\033[0m'
else
    GREEN=''
    RED=''
    YELLOW=''
    BLUE=''
    NC=''
fi

echo -e "${BLUE}🧪 SECRET TESTER: Starting comprehensive verification...${NC}"
echo "📋 Environment:"
echo "   CI Mode: $CI_MODE"
echo "   User: $(id -un) ($(id -u):$(id -g))"
echo ""

# Function to log messages
log() {
    echo "[$(date -Iseconds)] $1" >> "$LOG_FILE"
}

# Check if secrets file exists
if [ ! -f "$SECRETS_FILE" ]; then
    echo -e "${RED}❌ ERROR: Secrets file not found at: $SECRETS_FILE${NC}"
    echo "📁 Directory contents:"
    ls -la /app/config/ 2>&1 || echo "Cannot list directory"
    log "ERROR: Secrets file not found"
    exit 1
fi

echo -e "${GREEN}✅ Found secrets file!${NC}"
log "Secrets file found at $SECRETS_FILE"

# Check file permissions
FILE_PERMS=$(stat -c "%a" "$SECRETS_FILE" 2>/dev/null || echo "unknown")
echo "🔒 File permissions: $FILE_PERMS"
if [ "$FILE_PERMS" = "640" ] || [ "$FILE_PERMS" = "644" ]; then
    echo -e "${GREEN}✅ File permissions are secure${NC}"
else
    echo -e "${YELLOW}⚠️  WARNING: Unexpected file permissions${NC}"
fi

# Check file size
FILE_SIZE=$(stat -c "%s" "$SECRETS_FILE" 2>/dev/null || echo "0")
echo "📏 File size: ${FILE_SIZE} bytes"
if [ "$FILE_SIZE" -lt 100 ]; then
    echo -e "${YELLOW}⚠️  WARNING: Secrets file seems too small${NC}"
fi

echo ""

# Display secrets summary (never show values in production)
echo "📄 Secrets content summary:"
echo "🔑 Available secret keys:"
grep -E '^[A-Z_]+=' "$SECRETS_FILE" | cut -d'=' -f1 | while read -r key; do
    # Show value length for sensitive fields
    if [[ "$key" == *"PASSWORD"* ]] || [[ "$key" == *"SECRET"* ]] || [[ "$key" == *"KEY"* ]]; then
        VALUE_LENGTH=$(grep "^${key}=" "$SECRETS_FILE" | cut -d'=' -f2- | wc -c)
        echo "   - $key (${VALUE_LENGTH} chars)"
    else
        echo "   - $key"
    fi
done

echo ""
echo -e "${YELLOW}🔍 Performing verification checks...${NC}"

# Initialize check counters
TOTAL_CHECKS=0
PASSED_CHECKS=0

# Function to perform a check
check() {
    local description="$1"
    local command="$2"
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    
    if eval "$command"; then
        echo -e "${GREEN}✅ $description${NC}"
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        log "PASS: $description"
        return 0
    else
        echo -e "${RED}❌ $description${NC}"
        log "FAIL: $description"
        return 1
    fi
}

# Perform checks
check "HCP init status is success" "grep -q 'HCP_INIT_STATUS=success' '$SECRETS_FILE'"
check "HCP app name is set" "grep -q 'HCP_APP_NAME=' '$SECRETS_FILE'"
check "Secret count is positive" "grep -q 'HCP_SECRET_COUNT=[1-9]' '$SECRETS_FILE'"
check "Init timestamp exists" "grep -q 'HCP_INIT_TIMESTAMP=' '$SECRETS_FILE'"
check "No error markers present" "! grep -q 'ERROR\\|FAIL\\|error\\|fail' '$SECRETS_FILE'"

# Count actual secrets (excluding metadata)
ACTUAL_SECRET_COUNT=$(grep -E '^[A-Z_]+=' "$SECRETS_FILE" | grep -v '^HCP_' | wc -l || echo 0)
echo ""
echo "📊 Statistics:"
echo "   Total entries: $(grep -c '=' '$SECRETS_FILE' || echo 0)"
echo "   Application secrets: $ACTUAL_SECRET_COUNT"
echo "   Metadata entries: $(grep -c '^HCP_' '$SECRETS_FILE' || echo 0)"

# Check backup exists
echo ""
if ls "$BACKUP_DIR"/secrets-*.env >/dev/null 2>&1; then
    BACKUP_COUNT=$(ls -1 "$BACKUP_DIR"/secrets-*.env 2>/dev/null | wc -l)
    echo -e "${GREEN}✅ Backup files found: $BACKUP_COUNT${NC}"
    
    # Verify latest backup matches current secrets
    LATEST_BACKUP=$(ls -t "$BACKUP_DIR"/secrets-*.env 2>/dev/null | head -1)
    if [ -n "$LATEST_BACKUP" ]; then
        if diff -q "$SECRETS_FILE" "$LATEST_BACKUP" >/dev/null 2>&1; then
            echo -e "${GREEN}✅ Latest backup matches current secrets${NC}"
        else
            echo -e "${YELLOW}⚠️  Latest backup differs from current secrets${NC}"
        fi
    fi
else
    echo -e "${YELLOW}⚠️  No backup files found${NC}"
fi

# Final summary
echo ""
echo "=================="
echo -e "${BLUE}📊 VERIFICATION SUMMARY${NC}"
echo "=================="
echo "Checks passed: $PASSED_CHECKS/$TOTAL_CHECKS"

if [ $PASSED_CHECKS -eq $TOTAL_CHECKS ]; then
    echo -e "${GREEN}✅ ALL CHECKS PASSED!${NC}"
    echo ""
    echo -e "${GREEN}🎉 SECRET TESTER: Verification completed successfully!${NC}"
    log "All verification checks passed ($PASSED_CHECKS/$TOTAL_CHECKS)"
    
    # Create success marker in writable tmpfs
    echo "success" > /app/test-results/.test-complete
    echo "Test passed at: $(date)" > /app/test-results/status.txt
    exit 0
else
    FAILED_CHECKS=$((TOTAL_CHECKS - PASSED_CHECKS))
    echo -e "${RED}❌ SOME CHECKS FAILED: $FAILED_CHECKS failed${NC}"
    echo ""
    echo -e "${RED}💥 SECRET TESTER: Verification failed!${NC}"
    log "Verification failed ($PASSED_CHECKS/$TOTAL_CHECKS passed)"
    
    # Create failure marker in writable tmpfs
    echo "failed" > /app/test-results/.test-complete
    echo "Test failed at: $(date)" > /app/test-results/status.txt
    exit 1
fi
