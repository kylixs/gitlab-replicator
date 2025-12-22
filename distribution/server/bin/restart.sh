#!/bin/bash
#
# GitLab Mirror Server - Restart Script
#

set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Restarting GitLab Mirror Server..."

# Stop server
"$SCRIPT_DIR/stop.sh"

# Wait a moment
sleep 2

# Start server
"$SCRIPT_DIR/start.sh"
