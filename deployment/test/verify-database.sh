#!/bin/bash
# =============================================================================
# DATABASE VERIFICATION SCRIPT
# Purpose: Verify PostgreSQL database is properly initialized
# Usage: ./verify-database.sh [container_name]
# =============================================================================

set -e

# Color codes for better visibility
if [ -t 1 ]; then
    GREEN='\033[0;32m'
    RED='\033[0;31m'
    BLUE='\033[0;34m'
    YELLOW='\033[0;33m'
    CYAN='\033[0;36m'
    NC='\033[0m'
else
    GREEN=''
    RED=''
    BLUE=''
    YELLOW=''
    CYAN=''
    NC=''
fi

echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}           DATABASE VERIFICATION SCRIPT                      ${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo "Time: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

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
    IN_CONTAINER=true
else
    echo "Running from host - using docker exec"
    CONTAINER_NAME="${1:-postgres}"
    echo "Using container: $CONTAINER_NAME"
    IN_CONTAINER=false
    
    # Check if container exists
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        echo -e "${RED}ERROR: Container '$CONTAINER_NAME' not found or not running${NC}"
        echo "Available containers:"
        docker ps --format 'table {{.Names}}\t{{.Status}}'
        exit 1
    fi
    
    # Copy this script to container and execute it
    echo "Copying script to container..."
    docker cp "$0" "${CONTAINER_NAME}:/tmp/verify_database.sh"
    
    echo "Executing script inside container..."
    docker exec -it "${CONTAINER_NAME}" bash /tmp/verify_database.sh
    
    # Clean up
    docker exec "${CONTAINER_NAME}" rm -f /tmp/verify_database.sh
    exit $?
fi

# Running inside container - perform actual verification

# Initialize counters
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
WARNINGS=0

# Check for secrets file
echo -e "${CYAN}[1/10] Checking secrets file...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
if [ -f "$SECRETS_FILE" ]; then
    echo -e "${GREEN}  ✓ Secrets file exists${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
    
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
    
    export PGPASSWORD="$PG_PASSWORD"
else
    echo -e "${RED}  ✗ Secrets file not found at $SECRETS_FILE${NC}"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
    exit 1
fi

# Check PostgreSQL connection
echo -e "${CYAN}[2/10] Checking PostgreSQL connection...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
if pg_isready -U "$PG_USER" -h localhost -p 5432 > /dev/null 2>&1; then
    echo -e "${GREEN}  ✓ PostgreSQL is running and ready${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
else
    echo -e "${RED}  ✗ PostgreSQL is not ready${NC}"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
    exit 1
fi

# Check database exists
echo -e "${CYAN}[3/10] Checking database existence...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
if psql -U "$PG_USER" -h localhost -p 5432 -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1; then
    echo -e "${GREEN}  ✓ Database '$DB_NAME' exists${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
else
    echo -e "${RED}  ✗ Database '$DB_NAME' not found${NC}"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
fi

# Check application user
echo -e "${CYAN}[4/10] Checking application user...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
if psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
       -tc "SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER'" | grep -q 1; then
    echo -e "${GREEN}  ✓ Application user '$APP_USER' exists${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
    
    # Check login capability
    if psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
           -tc "SELECT rolcanlogin FROM pg_roles WHERE rolname = '$APP_USER'" | grep -q t; then
        echo -e "${GREEN}    ✓ User can login${NC}"
    else
        echo -e "${RED}    ✗ User cannot login${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "${RED}  ✗ Application user '$APP_USER' not found${NC}"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
fi

# Check schema ownership
echo -e "${CYAN}[5/10] Checking schema ownership...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
OWNER=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
            -tc "SELECT nspowner::regrole FROM pg_namespace WHERE nspname = 'public'" 2>/dev/null | xargs)
if [ "$OWNER" = "$APP_USER" ]; then
    echo -e "${GREEN}  ✓ Schema 'public' owned by '$APP_USER'${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
else
    echo -e "${YELLOW}  ⚠ Schema 'public' owned by '$OWNER' (expected: $APP_USER)${NC}"
    WARNINGS=$((WARNINGS + 1))
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
fi

# Check sequences
echo -e "${CYAN}[6/10] Checking sequences...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
SEQ_COUNT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
                -tc "SELECT COUNT(*) FROM pg_sequences WHERE schemaname = 'public'" 2>/dev/null | xargs)
EXPECTED_SEQ=26  # Based on the init script
if [ "$SEQ_COUNT" -ge "$EXPECTED_SEQ" ]; then
    echo -e "${GREEN}  ✓ All sequences created: $SEQ_COUNT (expected: ≥$EXPECTED_SEQ)${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
else
    echo -e "${YELLOW}  ⚠ Only $SEQ_COUNT sequences found (expected: ≥$EXPECTED_SEQ)${NC}"
    WARNINGS=$((WARNINGS + 1))
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
fi

# Check Liquibase tables
echo -e "${CYAN}[7/10] Checking Liquibase tables...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
LIQ_COUNT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
                -tc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('databasechangelog', 'databasechangeloglock')" 2>/dev/null | xargs)
if [ "$LIQ_COUNT" -eq 2 ]; then
    echo -e "${GREEN}  ✓ Liquibase tables exist: $LIQ_COUNT/2${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
else
    echo -e "${YELLOW}  ⚠ Liquibase tables incomplete: $LIQ_COUNT/2${NC}"
    WARNINGS=$((WARNINGS + 1))
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
fi

# Check extensions
echo -e "${CYAN}[8/10] Checking PostgreSQL extensions...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
UUID_EXT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
              -tc "SELECT 1 FROM pg_extension WHERE extname = 'uuid-ossp'" 2>/dev/null | xargs)
CRYPTO_EXT=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
                -tc "SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto'" 2>/dev/null | xargs)
if [ "$UUID_EXT" = "1" ] && [ "$CRYPTO_EXT" = "1" ]; then
    echo -e "${GREEN}  ✓ Required extensions installed (uuid-ossp, pgcrypto)${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
else
    echo -e "${YELLOW}  ⚠ Some extensions missing${NC}"
    [ "$UUID_EXT" != "1" ] && echo -e "${YELLOW}    - uuid-ossp not installed${NC}"
    [ "$CRYPTO_EXT" != "1" ] && echo -e "${YELLOW}    - pgcrypto not installed${NC}"
    WARNINGS=$((WARNINGS + 1))
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
fi

# Check user permissions
echo -e "${CYAN}[9/10] Checking user permissions...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
HAS_CREATE=$(psql -U "$PG_USER" -h localhost -p 5432 -d "$DB_NAME" \
                -tc "SELECT has_schema_privilege('$APP_USER', 'public', 'CREATE')" 2>/dev/null | xargs)
if [ "$HAS_CREATE" = "t" ]; then
    echo -e "${GREEN}  ✓ User has CREATE privilege on schema${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
else
    echo -e "${RED}  ✗ User missing CREATE privilege on schema${NC}"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
fi

# Test application user connection
echo -e "${CYAN}[10/10] Testing application user connection...${NC}"
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
export PGPASSWORD="$APP_PASSWORD"
if psql -U "$APP_USER" -h localhost -p 5432 -d "$DB_NAME" \
       -c "SELECT current_user, current_database();" > /dev/null 2>&1; then
    echo -e "${GREEN}  ✓ Application user can connect successfully${NC}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
    
    # Try to create a test table
    if psql -U "$APP_USER" -h localhost -p 5432 -d "$DB_NAME" \
           -c "CREATE TABLE IF NOT EXISTS verify_test (id INT); DROP TABLE IF EXISTS verify_test;" > /dev/null 2>&1; then
        echo -e "${GREEN}    ✓ User can create and drop tables${NC}"
    else
        echo -e "${YELLOW}    ⚠ User cannot create/drop tables${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "${RED}  ✗ Application user connection failed${NC}"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
fi

# Summary
echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}                    VERIFICATION SUMMARY                     ${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Total checks:   $TOTAL_CHECKS"
echo -e "  ${GREEN}Passed:${NC}         $PASSED_CHECKS"
echo -e "  ${RED}Failed:${NC}         $FAILED_CHECKS"
echo -e "  ${YELLOW}Warnings:${NC}       $WARNINGS"
echo ""

if [ $FAILED_CHECKS -eq 0 ]; then
    if [ $WARNINGS -eq 0 ]; then
        echo -e "${GREEN}✓ DATABASE IS FULLY INITIALIZED AND READY${NC}"
        EXIT_CODE=0
    else
        echo -e "${YELLOW}⚠ DATABASE IS INITIALIZED WITH WARNINGS${NC}"
        echo "  Please review the warnings above"
        EXIT_CODE=0
    fi
else
    echo -e "${RED}✗ DATABASE INITIALIZATION INCOMPLETE${NC}"
    echo "  Please run ./manual-init-database.sh to fix issues"
    EXIT_CODE=1
fi

echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"

exit $EXIT_CODE
