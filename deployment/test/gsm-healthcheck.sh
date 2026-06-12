#!/bin/sh
# GSM Init Container Health Check
# Ensures secrets are fully written and contain required values

SECRETS_FILE="/app/config/secrets.env"

# Check if file exists
if [ ! -f "$SECRETS_FILE" ]; then
    exit 1
fi

# Check if file has content
if [ ! -s "$SECRETS_FILE" ]; then
    exit 1
fi

# Check for required PostgreSQL secrets (unprefixed after stripping)
if ! grep -q "POSTGRES_USER=" "$SECRETS_FILE"; then
    exit 1
fi

if ! grep -q "POSTGRES_PASSWORD=" "$SECRETS_FILE"; then
    exit 1
fi

if ! grep -q "POSTGRES_DB=" "$SECRETS_FILE"; then
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

# Add a small delay to ensure file is fully synced to disk
sleep 1

exit 0
