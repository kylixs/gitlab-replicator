#!/bin/bash
#
# GitLab Mirror - Database Initialization Script
#
# Initialize or update the database schema
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  Database Initialization${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""

# Check if running from installed location or source directory
if [ -f "$APP_HOME/conf/.env" ]; then
    ENV_FILE="$APP_HOME/conf/.env"
elif [ -f "$SCRIPT_DIR/../../.env" ]; then
    # Running from source directory
    ENV_FILE="$SCRIPT_DIR/../../.env"
    APP_HOME="$(cd "$SCRIPT_DIR/../.." && pwd)"
else
    echo -e "${RED}Error: Configuration file (.env) not found${NC}"
    echo "Expected locations:"
    echo "  - $APP_HOME/conf/.env (installed)"
    echo "  - $SCRIPT_DIR/../../.env (source)"
    exit 1
fi

# Load environment variables
echo "Loading configuration from: $ENV_FILE"
set -a
source "$ENV_FILE"
set +a

# Check required variables
if [ -z "$DB_HOST" ] || [ -z "$DB_NAME" ] || [ -z "$DB_USERNAME" ]; then
    echo -e "${RED}Error: Missing required database configuration${NC}"
    echo "Required variables: DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD"
    exit 1
fi

echo -e "${GREEN}✓${NC} Configuration loaded"
echo "  Database: $DB_NAME"
echo "  Host: $DB_HOST:${DB_PORT:-3306}"
echo "  User: $DB_USERNAME"
echo ""

# Locate SQL schema file
if [ -f "$APP_HOME/sql/schema.sql" ]; then
    SCHEMA_FILE="$APP_HOME/sql/schema.sql"
elif [ -f "$SCRIPT_DIR/../../sql/schema.sql" ]; then
    # Running from source directory
    SCHEMA_FILE="$SCRIPT_DIR/../../sql/schema.sql"
else
    echo -e "${RED}Error: Schema file not found${NC}"
    echo "Expected locations:"
    echo "  - $APP_HOME/sql/schema.sql"
    echo "  - $SCRIPT_DIR/../../sql/schema.sql"
    exit 1
fi

echo "Schema file: $SCHEMA_FILE"
echo ""

# Check MySQL client
if ! command -v mysql &> /dev/null; then
    echo -e "${RED}Error: mysql client is not installed${NC}"
    echo "Please install mysql-client package"
    exit 1
fi

# Test database connection
echo "Testing database connection..."
if ! mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p"$DB_PASSWORD" -e "SELECT 1" &> /dev/null; then
    echo -e "${RED}Error: Cannot connect to database${NC}"
    echo "Please check:"
    echo "  1. Database server is running"
    echo "  2. Connection parameters are correct"
    echo "  3. User has proper privileges"
    exit 1
fi
echo -e "${GREEN}✓${NC} Database connection successful"
echo ""

# Check if database exists
echo "Checking database existence..."
DB_EXISTS=$(mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p"$DB_PASSWORD" \
    -e "SHOW DATABASES LIKE '$DB_NAME'" -s -N | wc -l)

if [ "$DB_EXISTS" -eq 0 ]; then
    echo -e "${YELLOW}Database '$DB_NAME' does not exist${NC}"
    read -p "Do you want to create it? (y/N): " CREATE_DB
    if [[ $CREATE_DB =~ ^[Yy]$ ]]; then
        echo "Creating database..."
        mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p"$DB_PASSWORD" \
            -e "CREATE DATABASE $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        echo -e "${GREEN}✓${NC} Database created"
    else
        echo "Aborted"
        exit 1
    fi
else
    echo -e "${GREEN}✓${NC} Database exists"
fi
echo ""

# Check current schema version
echo "Checking schema version..."
SCHEMA_VERSION_EXISTS=$(mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" \
    -e "SHOW TABLES LIKE 'schema_version'" -s -N | wc -l)

if [ "$SCHEMA_VERSION_EXISTS" -gt 0 ]; then
    CURRENT_VERSION=$(mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" \
        -s -N -e "SELECT version FROM schema_version ORDER BY applied_at DESC LIMIT 1" || echo "unknown")
    echo -e "${YELLOW}Warning: Database schema already initialized${NC}"
    echo "Current version: $CURRENT_VERSION"
    echo ""

    read -p "Do you want to re-run schema initialization? This may cause errors if tables exist. (y/N): " REINIT
    if [[ ! $REINIT =~ ^[Yy]$ ]]; then
        echo "Aborted"
        exit 0
    fi
fi

# Execute schema
echo -e "${BLUE}Initializing database schema...${NC}"
echo ""

if mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" < "$SCHEMA_FILE"; then
    echo ""
    echo -e "${GREEN}✓${NC} Schema initialization completed successfully"
    echo ""

    # Display schema version
    NEW_VERSION=$(mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" \
        -s -N -e "SELECT version FROM schema_version ORDER BY applied_at DESC LIMIT 1" || echo "unknown")
    echo "Schema version: $NEW_VERSION"

    # Display table count
    TABLE_COUNT=$(mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" \
        -s -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'")
    echo "Tables created: $TABLE_COUNT"

    echo ""
    echo -e "${GREEN}======================================${NC}"
    echo -e "${GREEN}  Initialization Complete!${NC}"
    echo -e "${GREEN}======================================${NC}"
else
    echo ""
    echo -e "${RED}Error: Schema initialization failed${NC}"
    echo "Please check the error messages above"
    exit 1
fi
