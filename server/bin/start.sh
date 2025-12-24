#!/bin/bash

# GitLab Mirror Web Server Startup Script

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SERVER_DIR="$PROJECT_ROOT/server"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}GitLab Mirror Web Server${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if .env file exists
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${YELLOW}⚠️  Warning: .env file not found${NC}"
    echo -e "${YELLOW}Please create .env file from .env.example${NC}"
    echo ""
fi

# Load environment variables from .env if exists
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${GREEN}✓${NC} Loading environment variables from .env"
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}✗ Maven not found. Please install Maven first.${NC}"
    exit 1
fi

# Check if database is running
echo -e "${BLUE}Checking database connection...${NC}"
if command -v mysql &> /dev/null; then
    mysql -h"${DB_HOST:-localhost}" -P"${DB_PORT:-3306}" -u"${DB_USERNAME:-gitlab_mirror}" -p"${DB_PASSWORD:-mirror_pass_123}" -e "SELECT 1" "${DB_NAME:-gitlab_mirror}" &> /dev/null
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓${NC} Database connection OK"
    else
        echo -e "${YELLOW}⚠️  Warning: Cannot connect to database${NC}"
        echo -e "${YELLOW}Please make sure MySQL is running: docker-compose up -d${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  MySQL client not found, skipping database check${NC}"
fi

echo ""
echo -e "${BLUE}Starting GitLab Mirror Server...${NC}"
echo -e "${GREEN}Server will be available at:${NC}"
echo -e "  ${GREEN}➜${NC}  Web UI:  http://localhost:${API_PORT:-9999}"
echo -e "  ${GREEN}➜${NC}  API:     http://localhost:${API_PORT:-9999}/api"
echo -e "  ${GREEN}➜${NC}  Health:  http://localhost:${API_PORT:-9999}/actuator/health"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop the server${NC}"
echo ""

# Change to server directory
cd "$SERVER_DIR"

# Run Spring Boot application
mvn spring-boot:run -Dspring-boot.run.profiles=dev
