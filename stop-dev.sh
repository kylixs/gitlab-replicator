#!/bin/bash

# GitLab Mirror Development Mode Stop Script

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
echo -e "${BLUE}Stopping Development Environment${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to stop process by PID file
stop_process() {
    local name=$1
    local pidfile=$2

    if [ -f "$pidfile" ]; then
        PID=$(cat "$pidfile")
        if ps -p $PID > /dev/null 2>&1; then
            echo -e "${YELLOW}Stopping $name (PID: $PID)...${NC}"
            kill $PID

            # Wait for process to stop
            for i in {1..10}; do
                if ! ps -p $PID > /dev/null 2>&1; then
                    echo -e "${GREEN}✓${NC} $name stopped"
                    rm -f "$pidfile"
                    return 0
                fi
                sleep 1
            done

            # Force kill if still running
            if ps -p $PID > /dev/null 2>&1; then
                echo -e "${YELLOW}Force stopping $name...${NC}"
                kill -9 $PID
                rm -f "$pidfile"
                echo -e "${GREEN}✓${NC} $name stopped (forced)"
            fi
        else
            echo -e "${YELLOW}$name process not running${NC}"
            rm -f "$pidfile"
        fi
    else
        echo -e "${YELLOW}$name PID file not found${NC}"
    fi
}

# Stop frontend
stop_process "Frontend" "$PROJECT_ROOT/logs/frontend.pid"

# Stop backend
stop_process "Backend" "$PROJECT_ROOT/logs/backend.pid"

echo ""
echo -e "${GREEN}✓ Development environment stopped${NC}"
