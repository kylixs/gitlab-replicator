#!/bin/bash
#
# GitLab Mirror Server - Status Script
#

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_HOME="$(cd "$SERVER_HOME/.." && pwd)"

# Configuration
PID_FILE="$SERVER_HOME/gitlab-mirror-server.pid"
ENV_FILE="$APP_HOME/conf/.env"

# Load environment for API port
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

API_PORT="${API_PORT:-9999}"

echo "GitLab Mirror Server Status"
echo "=============================="

# Check PID file
if [ ! -f "$PID_FILE" ]; then
    echo "Status: NOT RUNNING (PID file not found)"
    exit 1
fi

# Read PID
PID=$(cat "$PID_FILE")

# Check if process is running
if ! ps -p "$PID" > /dev/null 2>&1; then
    echo "Status: NOT RUNNING (process $PID not found)"
    rm -f "$PID_FILE"
    exit 1
fi

# Process is running
echo "Status: RUNNING"
echo "PID: $PID"

# Show process details
echo ""
echo "Process Details:"
ps -p "$PID" -o pid,ppid,user,%cpu,%mem,vsz,rss,etime,command

# Check if server is responding
echo ""
echo "Health Check:"
if command -v curl > /dev/null 2>&1; then
    if curl -s -f "http://localhost:$API_PORT/actuator/health" > /dev/null 2>&1; then
        echo "Server is responding on port $API_PORT"
        curl -s "http://localhost:$API_PORT/actuator/health" | python3 -m json.tool 2>/dev/null || cat
    else
        echo "Warning: Server is not responding on port $API_PORT"
    fi
else
    echo "curl not found, skipping health check"
fi

# Show recent log entries
echo ""
echo "Recent Logs (last 10 lines):"
if [ -f "$SERVER_HOME/logs/gitlab-mirror.log" ]; then
    tail -10 "$SERVER_HOME/logs/gitlab-mirror.log"
else
    echo "Log file not found"
fi
