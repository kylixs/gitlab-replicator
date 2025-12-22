#!/bin/bash
#
# GitLab Mirror Server - Start Script
#

set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_HOME="$(cd "$SERVER_HOME/.." && pwd)"

# Configuration
JAR_FILE="$SERVER_HOME/lib/gitlab-mirror-server.jar"
PID_FILE="$SERVER_HOME/gitlab-mirror-server.pid"
LOG_DIR="$SERVER_HOME/logs"
ENV_FILE="$APP_HOME/conf/.env"

# Java options
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g -XX:+UseG1GC}"

# Load environment variables
if [ -f "$ENV_FILE" ]; then
    echo "Loading environment from $ENV_FILE"
    set -a
    source "$ENV_FILE"
    set +a
else
    echo "Warning: Environment file not found at $ENV_FILE"
    echo "Please copy conf/.env.example to conf/.env and configure it"
    exit 1
fi

# Check if server is already running
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Server is already running with PID $PID"
        exit 1
    else
        echo "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
fi

# Check JAR file exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    exit 1
fi

# Create logs directory if not exists
mkdir -p "$LOG_DIR"

# Start server
echo "Starting GitLab Mirror Server..."
echo "JAR: $JAR_FILE"
echo "Logs: $LOG_DIR"
echo "Java Options: $JAVA_OPTS"

nohup java $JAVA_OPTS \
    -jar "$JAR_FILE" \
    --spring.config.additional-location="file:$SERVER_HOME/conf/" \
    > "$LOG_DIR/stdout.log" 2>&1 &

PID=$!
echo $PID > "$PID_FILE"

# Wait a moment and check if process is still running
sleep 2
if ps -p "$PID" > /dev/null 2>&1; then
    echo "Server started successfully with PID $PID"
    echo "View logs: tail -f $LOG_DIR/gitlab-mirror.log"
else
    echo "Error: Server failed to start"
    rm -f "$PID_FILE"
    exit 1
fi
