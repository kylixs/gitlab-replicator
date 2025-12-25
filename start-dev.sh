#!/bin/bash

# GitLab Mirror Development Mode Startup Script

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}GitLab Mirror - Development Mode${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if .env file exists
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${YELLOW}⚠️  Warning: .env file not found${NC}"
    echo -e "${YELLOW}Creating .env from .env.example...${NC}"
    cp .env.example .env 2>/dev/null || echo -e "${RED}✗ Failed to create .env${NC}"
fi

# Load environment variables
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${GREEN}✓${NC} Loading environment variables from .env"
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

# Function to check if a process is running on a port
check_port() {
    lsof -i :$1 >/dev/null 2>&1
    return $?
}

# Function to start backend
start_backend() {
    echo ""
    echo -e "${BLUE}Starting Backend Server...${NC}"

    cd "$PROJECT_ROOT/server"

    # Check if Maven is installed
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}✗ Maven not found. Please install Maven first.${NC}"
        exit 1
    fi

    # Start Spring Boot in background
    echo -e "${GREEN}Starting Spring Boot on port ${API_PORT:-9999}...${NC}"
    mvn spring-boot:run -Dspring-boot.run.profiles=dev > ../logs/backend.log 2>&1 &
    BACKEND_PID=$!
    echo $BACKEND_PID > ../logs/backend.pid

    # Wait for backend to start
    echo -n "Waiting for backend to start"
    for i in {1..30}; do
        if check_port ${API_PORT:-9999}; then
            echo ""
            echo -e "${GREEN}✓${NC} Backend started successfully (PID: $BACKEND_PID)"
            return 0
        fi
        echo -n "."
        sleep 1
    done

    echo ""
    echo -e "${RED}✗ Backend failed to start${NC}"
    return 1
}

# Function to start frontend
start_frontend() {
    echo ""
    echo -e "${BLUE}Starting Frontend Dev Server...${NC}"

    cd "$PROJECT_ROOT/web-ui"

    # Check if node_modules exists
    if [ ! -d "node_modules" ]; then
        echo -e "${YELLOW}Installing npm dependencies...${NC}"
        npm install
    fi

    # Start Vite dev server
    echo -e "${GREEN}Starting Vite dev server on port 3000...${NC}"
    npm run dev > ../logs/frontend.log 2>&1 &
    FRONTEND_PID=$!
    echo $FRONTEND_PID > ../logs/frontend.pid

    # Wait for frontend to start
    echo -n "Waiting for frontend to start"
    for i in {1..20}; do
        if check_port 3000; then
            echo ""
            echo -e "${GREEN}✓${NC} Frontend started successfully (PID: $FRONTEND_PID)"
            return 0
        fi
        echo -n "."
        sleep 1
    done

    echo ""
    echo -e "${RED}✗ Frontend failed to start${NC}"
    return 1
}

# Create logs directory
mkdir -p "$PROJECT_ROOT/logs"

# Check database
echo -e "${BLUE}Checking database...${NC}"
if command -v mysql &> /dev/null; then
    mysql -h"${DB_HOST:-localhost}" -P"${DB_PORT:-3306}" -u"${DB_USERNAME:-gitlab_mirror}" -p"${DB_PASSWORD:-mirror_pass_123}" -e "SELECT 1" "${DB_NAME:-gitlab_mirror}" &> /dev/null
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓${NC} Database connection OK"
    else
        echo -e "${YELLOW}⚠️  Warning: Cannot connect to database${NC}"
        echo -e "${YELLOW}Please start MySQL: docker-compose up -d${NC}"
        echo ""
        read -p "Continue anyway? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# Start services
start_backend
BACKEND_RESULT=$?

if [ $BACKEND_RESULT -eq 0 ]; then
    start_frontend
    FRONTEND_RESULT=$?
fi

# Display status
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Development Environment Ready!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ $BACKEND_RESULT -eq 0 ] && [ $FRONTEND_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ Backend API:${NC}  http://localhost:${API_PORT:-9999}/api"
    echo -e "${GREEN}✓ Frontend UI:${NC}  http://localhost:3000"
    echo -e "${GREEN}✓ Health Check:${NC} http://localhost:${API_PORT:-9999}/actuator/health"
    echo ""
    echo -e "${YELLOW}📝 Logs:${NC}"
    echo -e "   Backend:  tail -f logs/backend.log"
    echo -e "   Frontend: tail -f logs/frontend.log"
    echo ""
    echo -e "${YELLOW}🛑 To stop:${NC}"
    echo -e "   Run: ./stop-dev.sh"
    echo -e "   Or kill processes manually"
    echo ""
    echo -e "${GREEN}Press Ctrl+C to view logs (services will continue running)${NC}"
    echo ""

    # Follow logs
    trap "echo ''; echo 'Services are still running. Use ./stop-dev.sh to stop them.'; exit 0" INT
    tail -f logs/backend.log logs/frontend.log
else
    echo -e "${RED}✗ Failed to start development environment${NC}"
    echo -e "${YELLOW}Check logs for details:${NC}"
    echo -e "   Backend:  tail -f logs/backend.log"
    echo -e "   Frontend: tail -f logs/frontend.log"
    exit 1
fi
