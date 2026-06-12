#!/bin/sh
# GSM Init Container Health Check
# Ensures secrets are fully written and init completed successfully

SECRETS_FILE="/app/config/secrets.env"
INIT_COMPLETE_FILE="/app/config/.init-complete"

# Primary check: .init-complete file with success status
if [ -f "$INIT_COMPLETE_FILE" ]; then
    if grep -q "success" "$INIT_COMPLETE_FILE"; then
        exit 0
    fi
fi

# Fallback checks if .init-complete doesn't exist yet
# Check if secrets file exists and has content
if [ ! -f "$SECRETS_FILE" ]; then
    exit 1
fi

if [ ! -s "$SECRETS_FILE" ]; then
    exit 1
fi

# Check for required database secrets (unprefixed after stripping PROD_)
if ! grep -q "DATABASE_USERNAME=" "$SECRETS_FILE"; then
    exit 1
fi

if ! grep -q "DATABASE_PASSWORD=" "$SECRETS_FILE"; then
    exit 1
fi

if ! grep -q "DATABASE_NAME=" "$SECRETS_FILE"; then
    exit 1
fi

# Check if GSM_INIT_STATUS is success
if ! grep -q "GSM_INIT_STATUS=success" "$SECRETS_FILE"; then
    exit 1
fi

# Ensure file is readable by other containers (important for shared volumes)
if [ ! -r "$SECRETS_FILE" ]; then
    exit 1
fi

# All checks passed
exit 0
