#!/bin/bash
#
# GitLab Mirror Server - Stop Script
#

set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
PID_FILE="$SERVER_HOME/gitlab-mirror-server.pid"
TIMEOUT=30

# Check if PID file exists
if [ ! -f "$PID_FILE" ]; then
    echo "Server is not running (PID file not found)"
    exit 0
fi

# Read PID
PID=$(cat "$PID_FILE")

# Check if process is running
if ! ps -p "$PID" > /dev/null 2>&1; then
    echo "Server is not running (process $PID not found)"
    rm -f "$PID_FILE"
    exit 0
fi

# Stop server gracefully
echo "Stopping GitLab Mirror Server (PID $PID)..."
kill "$PID"

# Wait for process to stop
COUNT=0
while ps -p "$PID" > /dev/null 2>&1; do
    if [ $COUNT -ge $TIMEOUT ]; then
        echo "Warning: Server did not stop gracefully, forcing shutdown..."
        kill -9 "$PID"
        break
    fi
    echo "Waiting for server to stop... ($COUNT/$TIMEOUT)"
    sleep 1
    COUNT=$((COUNT + 1))
done

# Remove PID file
rm -f "$PID_FILE"

echo "Server stopped successfully"
