#!/bin/bash
# =============================================================================
# POSTGRES ENTRYPOINT - GSM CONFIGURATION WITH FORCED INIT
# Purpose: Setup PostgreSQL with superuser and ALWAYS run init script
# Ensures database and user are correctly configured on every startup
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

echo -e "${BLUE}=== PostgreSQL GSM Configuration Loader ===${NC}"

# Path to the secrets file created by init container
SECRETS_FILE="/app/config/secrets.env"

# Wait for secrets file to be available and properly written
WAIT_TIME=0
MAX_WAIT=120  # 2 minutes
RETRY_COUNT=0
MAX_RETRIES=10

echo "Waiting for GSM secrets to be available..."

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if [ -f "$SECRETS_FILE" ] && [ -s "$SECRETS_FILE" ]; then
        # File exists and has content, check if it contains required secrets (now unprefixed)
        if grep -q "POSTGRES_PASSWORD=" "$SECRETS_FILE" && \
           grep -q "DATABASE_PASSWORD=" "$SECRETS_FILE" && \
           grep -q "GSM_INIT_STATUS=success" "$SECRETS_FILE"; then
            echo -e "${GREEN}✓ Secrets file is ready and contains all required secrets${NC}"
            # Add a small delay to ensure file is fully synced
            sleep 2
            break
        else
            echo "Secrets file exists but is incomplete. Waiting... (attempt $((RETRY_COUNT + 1))/$MAX_RETRIES)"
        fi
    else
        echo "Secrets file not found or empty. Waiting... (attempt $((RETRY_COUNT + 1))/$MAX_RETRIES)"
    fi
    
    sleep 5  # Wait 5 seconds between retries
    RETRY_COUNT=$((RETRY_COUNT + 1))
done

if [ ! -f "$SECRETS_FILE" ]; then
    echo -e "${RED}ERROR: Secrets file not found after $MAX_WAIT seconds!${NC}"
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
    # Check if it looks like JSON with "value" field
    if echo "$input" | grep -q '^{"value":'; then
        # Extract value using sed (works without jq)
        echo "$input" | sed 's/^{"value":"\(.*\)"}$/\1/'
    else
        echo "$input"
    fi
}

# =============================================================================
# NON-SECRETS: Hardcoded (not stored in GSM — these are not secrets)
# =============================================================================
EXTRACTED_POSTGRES_USER="postgres"
EXTRACTED_DATABASE_NAME="instagram_platform_test"
EXTRACTED_APP_USER="app_user_test"

export POSTGRES_USER="$EXTRACTED_POSTGRES_USER"
export POSTGRES_DB="$EXTRACTED_DATABASE_NAME"

echo -e "${GREEN}✓ POSTGRES_USER: $EXTRACTED_POSTGRES_USER (hardcoded)${NC}"
echo -e "${GREEN}✓ DATABASE_NAME: $EXTRACTED_DATABASE_NAME (hardcoded)${NC}"
echo -e "${GREEN}✓ DATABASE_USERNAME: $EXTRACTED_APP_USER (hardcoded)${NC}"

# =============================================================================
# SECRETS: Only passwords come from GSM (the only actual secrets)
# =============================================================================
ERROR_COUNT=0

if [ -n "$POSTGRES_PASSWORD" ]; then
    EXTRACTED_POSTGRES_PASSWORD=$(extract_value "$POSTGRES_PASSWORD")
    export POSTGRES_PASSWORD="$EXTRACTED_POSTGRES_PASSWORD"
    echo -e "${GREEN}✓ POSTGRES_PASSWORD loaded from GSM (${#EXTRACTED_POSTGRES_PASSWORD} chars)${NC}"
else
    echo -e "${RED}✗ POSTGRES_PASSWORD not found in GSM${NC}"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

if [ -n "$DATABASE_PASSWORD" ]; then
    EXTRACTED_APP_PASSWORD=$(extract_value "$DATABASE_PASSWORD")
    echo -e "${GREEN}✓ DATABASE_PASSWORD loaded from GSM (${#EXTRACTED_APP_PASSWORD} chars)${NC}"
else
    echo -e "${RED}✗ DATABASE_PASSWORD not found in GSM${NC}"
    ERROR_COUNT=$((ERROR_COUNT + 1))
fi

if [ $ERROR_COUNT -gt 0 ]; then
    echo -e "${RED}ERROR: Missing $ERROR_COUNT required password secrets!${NC}"
    echo "Required in Google Secret Manager:"
    echo "  - TEST_POSTGRES_PASSWORD"
    echo "  - TEST_DATABASE_PASSWORD"
    exit 1
fi

# Display loaded configuration (without passwords)
echo ""
echo -e "${BLUE}PostgreSQL Configuration:${NC}"
echo "  Superuser: $EXTRACTED_POSTGRES_USER"
echo "  Superuser Password: [HIDDEN - ${#EXTRACTED_POSTGRES_PASSWORD} chars]"
echo "  Application Database: $EXTRACTED_DATABASE_NAME"
echo "  Application User: $EXTRACTED_APP_USER"
echo "  Application Password: [HIDDEN - ${#EXTRACTED_APP_PASSWORD} chars]"
echo "  Data Directory: ${PGDATA:-/var/lib/postgresql/data/pgdata}"
echo ""

# Create the idempotent initialization script
cat > "/docker-entrypoint-initdb.d/01-idempotent-init.sh" <<'IDEMPOTENT_INIT'
#!/bin/bash
set -e

echo "=== PostgreSQL Idempotent Initialization Script ==="
echo "Time: $(date '+%Y-%m-%d %H:%M:%S')"

# Function to extract value from potential JSON
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

echo "Configuration:"
echo "  PostgreSQL superuser: $PG_USER"
echo "  Application database: $DB_NAME"
echo "  Application user: $APP_USER"

# Export for psql
export PGPASSWORD="$PG_PASSWORD"

# List all existing databases
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

# Always run the idempotent init script
if [ -f "/docker-entrypoint-initdb.d/INIT_TEST_DATABASE.sql" ]; then
    echo ""
    echo "Running idempotent initialization script..."
    echo "This will ensure:"
    echo "  1. Application user exists with correct password"
    echo "  2. Schema ownership is correct"
    echo "  3. All permissions are granted"
    echo "  4. All sequences exist"
    echo "  5. Liquibase tables exist"
    
    # Run the init script with variables passed securely via session settings
    # This approach NEVER logs passwords
    echo "Executing SQL script with secure variable passing..."
    psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
        -c "SET my.app_user = '$APP_USER'" \
        -c "SET my.app_password = '$APP_PASSWORD'" \
        -c "SET my.db_name = '$DB_NAME'" \
        -f /docker-entrypoint-initdb.d/INIT_TEST_DATABASE.sql 2>&1 | tee /tmp/init.log
    
    echo ""
    echo "✓ Initialization script completed"
    
    # Detailed verification
    echo ""
    echo "=== VERIFICATION ==="
    
    # Check user
    if psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER'" | grep -q 1; then
        echo "✓ User '$APP_USER' exists"
        
        # Check if user can login
        if psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT rolcanlogin FROM pg_roles WHERE rolname = '$APP_USER'" | grep -q t; then
            echo "✓ User can login"
        else
            echo "✗ User cannot login!"
        fi
    else
        echo "✗ User '$APP_USER' NOT found!"
    fi
    
    # Check schema ownership
    OWNER=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT nspowner::regrole FROM pg_namespace WHERE nspname = 'public'" | xargs)
    echo "✓ Schema 'public' owner: $OWNER"
    
    # Count sequences
    SEQ_COUNT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" -tc "SELECT COUNT(*) FROM pg_sequences WHERE schemaname = 'public'" | xargs)
    echo "✓ Sequences created: $SEQ_COUNT"
    
    # Check Liquibase tables
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
    echo "=== DATABASE READY FOR APPLICATION ==="
else
    echo "ERROR: Init script not found at /docker-entrypoint-initdb.d/INIT_TEST_DATABASE.sql"
    exit 1
fi
IDEMPOTENT_INIT

chmod +x "/docker-entrypoint-initdb.d/01-idempotent-init.sh"

# Create wrapper to always run init after PostgreSQL starts
cat > "/usr/local/bin/postgres-wrapper.sh" <<'WRAPPER'
#!/bin/bash
set -e

echo "=== PostgreSQL Wrapper Script ==="
echo "This ensures initialization runs after every startup"

# Function to run init in background after PostgreSQL starts
run_init_after_startup() {
    echo "Waiting for PostgreSQL to become ready..."
    
    # Wait up to 60 seconds for PostgreSQL to be ready
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
    
    # Run the idempotent init script
    if [ -f "/docker-entrypoint-initdb.d/01-idempotent-init.sh" ]; then
        /docker-entrypoint-initdb.d/01-idempotent-init.sh
    else
        echo "WARNING: Idempotent init script not found"
    fi
}

# Start the init process in background
(
    run_init_after_startup
) &

INIT_PID=$!

# Start PostgreSQL normally using the original entrypoint
echo "Starting PostgreSQL..."
exec docker-entrypoint.sh postgres
WRAPPER

chmod +x "/usr/local/bin/postgres-wrapper.sh"

# Check if standalone init script is mounted
INIT_SCRIPT="/docker-entrypoint-initdb.d/INIT_TEST_DATABASE.sql"

if [ -f "$INIT_SCRIPT" ]; then
    echo -e "${GREEN}✓ Found mounted init script: $INIT_SCRIPT${NC}"
else
    echo -e "${YELLOW}⚠ No mounted init script found at $INIT_SCRIPT${NC}"
    echo -e "${YELLOW}  Database will be created but without schema setup${NC}"
fi

# Execute PostgreSQL with our wrapper
echo -e "${GREEN}Starting PostgreSQL with GSM configuration...${NC}"

# Use wrapper script to ensure init runs after startup
exec /usr/local/bin/postgres-wrapper.sh