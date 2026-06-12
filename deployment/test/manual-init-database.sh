#!/bin/bash
# =============================================================================
# MANUAL DATABASE INITIALIZATION SCRIPT
# Purpose: Manually trigger PostgreSQL database initialization
# Usage: ./manual-init-database.sh
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

echo -e "${BLUE}=== Manual Database Initialization ===${NC}"
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

# Check if running inside container or from host
if [ -f "/.dockerenv" ]; then
    echo "Running inside Docker container"
    SECRETS_FILE="/app/config/secrets.env"
    INIT_SQL="/docker-entrypoint-initdb.d/INIT_TEST_DATABASE.sql"
    IN_CONTAINER=true
else
    echo "Running from host - using docker exec"
    CONTAINER_NAME="${1:-postgres}"
    echo "Using container: $CONTAINER_NAME"
    IN_CONTAINER=false
fi

if [ "$IN_CONTAINER" = true ]; then
    # Running inside container
    
    # Check for secrets file
    if [ ! -f "$SECRETS_FILE" ]; then
        echo -e "${RED}ERROR: Secrets file not found at $SECRETS_FILE${NC}"
        echo "Make sure GSM init container has run successfully"
        exit 1
    fi
    
    # Source the secrets
    set -a
    source "$SECRETS_FILE"
    set +a
    
    # Extract values
    PG_USER=$(extract_value "${POSTGRES_USER}")
    PG_PASSWORD=$(extract_value "${POSTGRES_PASSWORD}")
    APP_USER=$(extract_value "${DATABASE_USERNAME}")
    APP_PASSWORD=$(extract_value "${DATABASE_PASSWORD}")
    DB_NAME=$(extract_value "${DATABASE_NAME}")
    
    echo -e "${GREEN}✓ Loaded configuration from secrets${NC}"
    echo "  PostgreSQL superuser: $PG_USER"
    echo "  Application database: $DB_NAME"
    echo "  Application user: $APP_USER"
    
    # Check if PostgreSQL is ready
    export PGPASSWORD="$PG_PASSWORD"
    
    echo ""
    echo "Checking PostgreSQL connection..."
    if ! pg_isready -U "$PG_USER" -h localhost -p 5432; then
        echo -e "${RED}ERROR: PostgreSQL is not ready${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ PostgreSQL is ready${NC}"
    
    # Check if database exists
    echo ""
    echo "Checking if database '$DB_NAME' exists..."
    if psql -U "$PG_USER" -h localhost -p 5432 -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1; then
        echo -e "${GREEN}✓ Database exists${NC}"
    else
        echo -e "${YELLOW}⚠ Database does not exist, creating...${NC}"
        psql -U "$PG_USER" -h localhost -p 5432 -c "CREATE DATABASE \"$DB_NAME\";"
        echo -e "${GREEN}✓ Database created${NC}"
    fi
    
    # Check if init SQL exists
    if [ ! -f "$INIT_SQL" ]; then
        echo -e "${RED}ERROR: Init SQL not found at $INIT_SQL${NC}"
        exit 1
    fi
    
    # Run the initialization with secure variable passing
    echo ""
    echo -e "${BLUE}Running database initialization...${NC}"
    echo "----------------------------------------"
    
    if psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
           -c "SET my.app_user = '$APP_USER'" \
           -c "SET my.app_password = '$APP_PASSWORD'" \
           -c "SET my.db_name = '$DB_NAME'" \
           -f "$INIT_SQL" 2>&1 | tee /tmp/manual_init.log; then
        echo "----------------------------------------"
        echo -e "${GREEN}✓ Initialization completed successfully${NC}"
    else
        echo "----------------------------------------"
        echo -e "${RED}✗ Initialization failed - check /tmp/manual_init.log${NC}"
        exit 1
    fi
    
    # Quick verification
    echo ""
    echo -e "${BLUE}Quick Verification:${NC}"
    
    # Check user exists
    if psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
           -tc "SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER'" | grep -q 1; then
        echo -e "${GREEN}✓ Application user exists${NC}"
    else
        echo -e "${RED}✗ Application user NOT found${NC}"
    fi
    
    # Count sequences
    SEQ_COUNT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
                    -tc "SELECT COUNT(*) FROM pg_sequences WHERE schemaname = 'public'" | xargs)
    echo -e "${GREEN}✓ Sequences created: $SEQ_COUNT${NC}"
    
    # Check Liquibase tables
    LIQ_COUNT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
                    -tc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('databasechangelog', 'databasechangeloglock')" | xargs)
    echo -e "${GREEN}✓ Liquibase tables: $LIQ_COUNT/2${NC}"
    
    # Test app user connection
    echo ""
    echo "Testing application user connection..."
    export PGPASSWORD="$APP_PASSWORD"
    if psql -U "$APP_USER" -h localhost -p 5432 -d "$DB_NAME" \
           -c "SELECT current_user, current_database();" 2>/dev/null; then
        echo -e "${GREEN}✓ Application user can connect${NC}"
    else
        echo -e "${RED}✗ Application user connection failed${NC}"
    fi
    
    echo ""
    echo -e "${GREEN}=== Manual initialization complete ===${NC}"
    
else
    # Running from host - use docker exec
    
    # Check if container exists
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        echo -e "${RED}ERROR: Container '$CONTAINER_NAME' not found or not running${NC}"
        echo "Available containers:"
        docker ps --format 'table {{.Names}}\t{{.Status}}'
        exit 1
    fi
    
    # Copy this script to container and execute it
    echo "Copying script to container..."
    docker cp "$0" "${CONTAINER_NAME}:/tmp/manual_init.sh"
    
    echo "Executing script inside container..."
    docker exec -it "${CONTAINER_NAME}" bash /tmp/manual_init.sh
    
    # Clean up
    docker exec "${CONTAINER_NAME}" rm -f /tmp/manual_init.sh
fi
