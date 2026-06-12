#!/bin/bash
# =============================================================================
# POSTGRES ENTRYPOINT - GSM CONFIGURATION WITH FORCED INIT (PRODUCTION)
# Purpose: Setup PostgreSQL with superuser and ALWAYS run init script
# Ensures database and user are correctly configured on every startup
# =============================================================================
# Adapted from deployment/test/postgres-entrypoint.sh for production.
# Same idempotent init pattern — only the hardcoded non-secret values differ.
# =============================================================================

set -e

# Color codes for better visibility
if [ -t 1 ]; then
    GREEN='\033[0;32m'
    RED='\033[0;31m'
    BLUE='\033[0;34m'
    YELLOW='\033[0;33m'
    NC='\033[0m'
else
    GREEN=''
    RED=''
    BLUE=''
    YELLOW=''
    NC=''
fi

echo -e "${BLUE}=== PostgreSQL GSM Configuration Loader (PRODUCTION) ===${NC}"

# Path to the secrets file created by init container
SECRETS_FILE="/app/config/secrets.env"

# Wait for secrets file to be available and properly written
RETRY_COUNT=0
MAX_RETRIES=10

echo "Waiting for GSM secrets to be available..."

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if [ -f "$SECRETS_FILE" ] && [ -s "$SECRETS_FILE" ]; then
        # Only check for DATABASE_PASSWORD (not POSTGRES_PASSWORD — we reuse
        # DATABASE_PASSWORD as the superuser password, so GSM never writes
        # a separate POSTGRES_PASSWORD entry).
        if grep -q "DATABASE_PASSWORD=" "$SECRETS_FILE" && \
           grep -q "GSM_INIT_STATUS=success" "$SECRETS_FILE"; then
            echo -e "${GREEN}✓ Secrets file is ready and contains all required secrets${NC}"
            sleep 2
            break
        else
            echo "Secrets file exists but is incomplete. Waiting... (attempt $((RETRY_COUNT + 1))/$MAX_RETRIES)"
        fi
    else
        echo "Secrets file not found or empty. Waiting... (attempt $((RETRY_COUNT + 1))/$MAX_RETRIES)"
    fi

    sleep 5
    RETRY_COUNT=$((RETRY_COUNT + 1))
done

if [ ! -f "$SECRETS_FILE" ]; then
    echo -e "${RED}ERROR: Secrets file not found after waiting!${NC}"
    echo "Expected location: $SECRETS_FILE"
    exit 1
fi

echo -e "${GREEN}✓ Found secrets file, loading configuration...${NC}"

# Source the secrets file
set -a
source "$SECRETS_FILE"
set +a

# Function to extract value from potential JSON (without jq)
extract_value() {
    local input="$1"
    if echo "$input" | grep -q '^{"value":'; then
        echo "$input" | sed 's/^{"value":"\(.*\)"}$/\1/'
    else
        echo "$input"
    fi
}

# =============================================================================
# NON-SECRETS: Hardcoded (not stored in GSM — these are not secrets)
# =============================================================================
EXTRACTED_POSTGRES_USER="postgres"
EXTRACTED_DATABASE_NAME="checkitout_app_prod_database"
EXTRACTED_APP_USER="checkitout_app_prod"

export POSTGRES_USER="$EXTRACTED_POSTGRES_USER"
export POSTGRES_DB="$EXTRACTED_DATABASE_NAME"

echo -e "${GREEN}✓ POSTGRES_USER: $EXTRACTED_POSTGRES_USER (hardcoded)${NC}"
echo -e "${GREEN}✓ DATABASE_NAME: $EXTRACTED_DATABASE_NAME (hardcoded)${NC}"
echo -e "${GREEN}✓ DATABASE_USERNAME: $EXTRACTED_APP_USER (hardcoded)${NC}"

# =============================================================================
# SECRETS: Only DATABASE_PASSWORD comes from GSM.
# POSTGRES_PASSWORD (superuser) reuses the same value — the superuser is only
# used internally by this entrypoint to create the app user and run init SQL.
# No external connection as 'postgres' is possible (port bound to 127.0.0.1).
# =============================================================================
if [ -n "$DATABASE_PASSWORD" ]; then
    EXTRACTED_APP_PASSWORD=$(extract_value "$DATABASE_PASSWORD")
    export POSTGRES_PASSWORD="$EXTRACTED_APP_PASSWORD"
    echo -e "${GREEN}✓ DATABASE_PASSWORD loaded from GSM (${#EXTRACTED_APP_PASSWORD} chars)${NC}"
    echo -e "${GREEN}✓ POSTGRES_PASSWORD = DATABASE_PASSWORD (reused, superuser is internal-only)${NC}"
else
    echo -e "${RED}✗ DATABASE_PASSWORD not found in GSM${NC}"
    echo "Required in Google Secret Manager:"
    echo "  - PROD_DATABASE_PASSWORD"
    exit 1
fi

# Display loaded configuration (without passwords)
echo ""
echo -e "${BLUE}PostgreSQL Configuration (PRODUCTION):${NC}"
echo "  Superuser: $EXTRACTED_POSTGRES_USER"
echo "  Superuser Password: [HIDDEN - ${#EXTRACTED_POSTGRES_PASSWORD} chars]"
echo "  Application Database: $EXTRACTED_DATABASE_NAME"
echo "  Application User: $EXTRACTED_APP_USER"
echo "  Application Password: [HIDDEN - ${#EXTRACTED_APP_PASSWORD} chars]"
echo "  Data Directory: ${PGDATA:-/var/lib/postgresql/data/pgdata}"
echo ""

# =============================================================================
# IDEMPOTENT INIT: Runs on EVERY startup to ensure DB + user are correct
# =============================================================================
cat > "/docker-entrypoint-initdb.d/01-idempotent-init.sh" <<'IDEMPOTENT_INIT'
#!/bin/bash
set -e

echo "=== PostgreSQL Idempotent Initialization Script (PRODUCTION) ==="
echo "Time: $(date '+%Y-%m-%d %H:%M:%S')"

extract_value() {
    local input="$1"
    if echo "$input" | grep -q '^{"value":'; then
        echo "$input" | sed 's/^{"value":"\(.*\)"}$/\1/'
    else
        echo "$input"
    fi
}

# Source the secrets file
if [ -f "/app/config/secrets.env" ]; then
    set -a
    source "/app/config/secrets.env"
    set +a
    echo "✓ Secrets loaded from /app/config/secrets.env"
else
    echo "ERROR: Secrets file not found!"
    exit 1
fi

# Get clean values
PG_USER=$(extract_value "${POSTGRES_USER}")
PG_PASSWORD=$(extract_value "${POSTGRES_PASSWORD}")
APP_USER=$(extract_value "${DATABASE_USERNAME}")
APP_PASSWORD=$(extract_value "${DATABASE_PASSWORD}")
DB_NAME=$(extract_value "${DATABASE_NAME}")

# Use hardcoded defaults if GSM didn't provide non-secret values
PG_USER="${PG_USER:-postgres}"
DB_NAME="${DB_NAME:-checkitout_app_prod_database}"
APP_USER="${APP_USER:-checkitout_app_prod}"

echo "Configuration:"
echo "  PostgreSQL superuser: $PG_USER"
echo "  Application database: $DB_NAME"
echo "  Application user: $APP_USER"

export PGPASSWORD="$PG_PASSWORD"

# List existing databases
echo ""
echo "Existing databases:"
psql -U "$PG_USER" -h localhost -p 5432 -tc "SELECT datname FROM pg_database WHERE datistemplate = false;" | while read -r db; do
    db=$(echo "$db" | xargs)
    if [ -n "$db" ]; then
        echo "  - $db"
    fi
done

# Create database if it doesn't exist
echo ""
echo "Ensuring database '$DB_NAME' exists..."
if ! psql -U "$PG_USER" -h localhost -p 5432 -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1; then
    echo "Creating database $DB_NAME..."
    psql -U "$PG_USER" -h localhost -p 5432 -c "CREATE DATABASE \"$DB_NAME\";"
    echo "✓ Database created"
else
    echo "✓ Database already exists"
fi

# Run the idempotent init script (same parameterized script as test)
if [ -f "/docker-entrypoint-initdb.d/INIT_PROD_DATABASE.sql" ]; then
    echo ""
    echo "Running idempotent initialization script..."

    psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
        -c "SET my.app_user = '$APP_USER'" \
        -c "SET my.app_password = '$APP_PASSWORD'" \
        -c "SET my.db_name = '$DB_NAME'" \
        -f /docker-entrypoint-initdb.d/INIT_PROD_DATABASE.sql 2>&1 | tee /tmp/init.log

    echo ""
    echo "✓ Initialization script completed"

    # Verification
    echo ""
    echo "=== VERIFICATION ==="

    if psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER'" | grep -q 1; then
        echo "✓ User '$APP_USER' exists"
        if psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT rolcanlogin FROM pg_roles WHERE rolname = '$APP_USER'" | grep -q t; then
            echo "✓ User can login"
        else
            echo "✗ User cannot login!"
        fi
    else
        echo "✗ User '$APP_USER' NOT found!"
    fi

    OWNER=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT nspowner::regrole FROM pg_namespace WHERE nspname = 'public'" | xargs)
    echo "✓ Schema 'public' owner: $OWNER"

    SEQ_COUNT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT COUNT(*) FROM pg_sequences WHERE schemaname = 'public'" | xargs)
    echo "✓ Sequences created: $SEQ_COUNT"

    LIQ_COUNT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('databasechangelog', 'databasechangeloglock')" | xargs)
    echo "✓ Liquibase tables: $LIQ_COUNT/2"

    # Test connection as app user
    echo ""
    echo "Testing connection as application user..."
    export PGPASSWORD="$APP_PASSWORD"
    if psql -U "$APP_USER" -h localhost -p 5432 -d "$DB_NAME" -c "SELECT current_user, current_database();" 2>/dev/null; then
        echo "✓ Application user can connect successfully"
    else
        echo "✗ Application user connection failed!"
    fi

    echo ""
    echo "=== PRODUCTION DATABASE READY FOR APPLICATION ==="
else
    echo "ERROR: Init script not found at /docker-entrypoint-initdb.d/INIT_PROD_DATABASE.sql"
    exit 1
fi
IDEMPOTENT_INIT

chmod +x "/docker-entrypoint-initdb.d/01-idempotent-init.sh"

# Create wrapper to always run init after PostgreSQL starts
cat > "/usr/local/bin/postgres-wrapper.sh" <<'WRAPPER'
#!/bin/bash
set -e

echo "=== PostgreSQL Wrapper Script (PRODUCTION) ==="

run_init_after_startup() {
    echo "Waiting for PostgreSQL to become ready..."

    COUNTER=0
    until pg_isready -U "$POSTGRES_USER" -h localhost -p 5432 || [ $COUNTER -eq 60 ]; do
        sleep 1
        COUNTER=$((COUNTER + 1))
    done

    if [ $COUNTER -eq 60 ]; then
        echo "ERROR: PostgreSQL did not become ready in 60 seconds"
        return 1
    fi

    echo "PostgreSQL is ready, running idempotent initialization..."

    if [ -f "/docker-entrypoint-initdb.d/01-idempotent-init.sh" ]; then
        /docker-entrypoint-initdb.d/01-idempotent-init.sh
    else
        echo "WARNING: Idempotent init script not found"
    fi
}

(
    run_init_after_startup
) &

echo "Starting PostgreSQL..."
exec docker-entrypoint.sh postgres
WRAPPER

chmod +x "/usr/local/bin/postgres-wrapper.sh"

# Check if init script is mounted
INIT_SCRIPT="/docker-entrypoint-initdb.d/INIT_PROD_DATABASE.sql"

if [ -f "$INIT_SCRIPT" ]; then
    echo -e "${GREEN}✓ Found mounted init script: $INIT_SCRIPT${NC}"
else
    echo -e "${YELLOW}⚠ No mounted init script found at $INIT_SCRIPT${NC}"
    echo -e "${YELLOW}  Database will be created but without schema setup${NC}"
fi

echo -e "${GREEN}Starting PostgreSQL with GSM configuration (PRODUCTION)...${NC}"
exec /usr/local/bin/postgres-wrapper.sh
