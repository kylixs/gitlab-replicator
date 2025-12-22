#!/bin/bash
#
# GitLab Mirror - Health Check Script
#
# Can be used by monitoring systems (Nagios, Zabbix, etc.)
# Exit codes: 0 = OK, 1 = Warning, 2 = Critical
#

# Configuration
INSTALL_DIR="/opt/gitlab-mirror"
ENV_FILE="$INSTALL_DIR/conf/.env"
PID_FILE="$INSTALL_DIR/server/gitlab-mirror-server.pid"

# Load environment
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

API_PORT="${API_PORT:-9999}"
EXIT_CODE=0
WARNINGS=()
ERRORS=()

# Check if process is running
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ! ps -p "$PID" > /dev/null 2>&1; then
        ERRORS+=("Process not running (PID $PID not found)")
        EXIT_CODE=2
    fi
else
    ERRORS+=("PID file not found")
    EXIT_CODE=2
fi

# Check if API is responding
if command -v curl > /dev/null 2>&1; then
    HEALTH_URL="http://localhost:$API_PORT/actuator/health"
    if ! curl -s -f --max-time 5 "$HEALTH_URL" > /dev/null 2>&1; then
        ERRORS+=("API not responding on port $API_PORT")
        EXIT_CODE=2
    fi
else
    WARNINGS+=("curl not available, skipping API check")
    [ $EXIT_CODE -eq 0 ] && EXIT_CODE=1
fi

# Check database connectivity
if command -v mysql > /dev/null 2>&1; then
    if ! mysql -h "${DB_HOST:-localhost}" \
                -P "${DB_PORT:-3306}" \
                -u "${DB_USERNAME}" \
                -p"${DB_PASSWORD}" \
                -e "SELECT 1" "${DB_NAME}" > /dev/null 2>&1; then
        ERRORS+=("Database connection failed")
        EXIT_CODE=2
    fi
else
    WARNINGS+=("mysql client not available, skipping database check")
    [ $EXIT_CODE -eq 0 ] && EXIT_CODE=1
fi

# Check log directory
if [ ! -d "$INSTALL_DIR/server/logs" ]; then
    WARNINGS+=("Log directory not found")
    [ $EXIT_CODE -eq 0 ] && EXIT_CODE=1
fi

# Check disk space (warn if > 80%)
DISK_USAGE=$(df -h "$INSTALL_DIR" | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -gt 90 ]; then
    ERRORS+=("Disk usage critical: ${DISK_USAGE}%")
    EXIT_CODE=2
elif [ "$DISK_USAGE" -gt 80 ]; then
    WARNINGS+=("Disk usage high: ${DISK_USAGE}%")
    [ $EXIT_CODE -eq 0 ] && EXIT_CODE=1
fi

# Output results
if [ $EXIT_CODE -eq 0 ]; then
    echo "OK - GitLab Mirror Server is healthy"
elif [ $EXIT_CODE -eq 1 ]; then
    echo "WARNING - GitLab Mirror Server has warnings"
    for warning in "${WARNINGS[@]}"; do
        echo "  - $warning"
    done
else
    echo "CRITICAL - GitLab Mirror Server has errors"
    for error in "${ERRORS[@]}"; do
        echo "  - $error"
    done
    for warning in "${WARNINGS[@]}"; do
        echo "  - $warning"
    done
fi

exit $EXIT_CODE
