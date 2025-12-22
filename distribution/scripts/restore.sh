#!/bin/bash
#
# GitLab Mirror - Restore Script
#
# Restores configuration and database from backup
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
INSTALL_DIR="/opt/gitlab-mirror"

echo "======================================"
echo "  GitLab Mirror Restore Script"
echo "======================================"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Error: This script must be run as root${NC}"
    echo "Please run: sudo $0 <backup_directory>"
    exit 1
fi

# Check backup directory argument
if [ -z "$1" ]; then
    echo -e "${RED}Error: Backup directory not specified${NC}"
    echo ""
    echo "Usage: $0 <backup_directory>"
    echo ""
    echo "Available backups:"
    BACKUP_BASE_DIR="${GITLAB_MIRROR_BACKUP_DIR:-/var/backups/gitlab-mirror}"
    if [ -d "$BACKUP_BASE_DIR" ]; then
        ls -1dt "$BACKUP_BASE_DIR"/backup_* 2>/dev/null | head -5 || echo "  No backups found"
    else
        echo "  No backups found in $BACKUP_BASE_DIR"
    fi
    exit 1
fi

BACKUP_DIR="$1"

# Verify backup directory
if [ ! -d "$BACKUP_DIR" ]; then
    echo -e "${RED}Error: Backup directory not found: $BACKUP_DIR${NC}"
    exit 1
fi

# Show backup information
if [ -f "$BACKUP_DIR/backup_info.txt" ]; then
    echo "Backup Information:"
    cat "$BACKUP_DIR/backup_info.txt"
    echo ""
fi

# Confirmation
echo -e "${YELLOW}WARNING: This will restore configuration and database${NC}"
echo -e "${YELLOW}Current data will be backed up first${NC}"
echo ""
read -p "Are you sure you want to restore? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Restore cancelled"
    exit 0
fi

# Stop service if running
echo ""
echo "Stopping service..."
if systemctl is-active --quiet gitlab-mirror-server; then
    systemctl stop gitlab-mirror-server
    echo -e "${GREEN}✓${NC} Service stopped"
fi

# Backup current configuration
echo ""
echo "Backing up current configuration..."
CURRENT_BACKUP="/tmp/gitlab-mirror-current-$(date +%Y%m%d_%H%M%S)"
mkdir -p "$CURRENT_BACKUP"
if [ -d "$INSTALL_DIR/conf" ]; then
    cp -r "$INSTALL_DIR/conf" "$CURRENT_BACKUP/"
    echo -e "${GREEN}✓${NC} Current configuration backed up to: $CURRENT_BACKUP"
fi

# Load environment from backup
if [ -f "$BACKUP_DIR/conf/.env" ]; then
    source "$BACKUP_DIR/conf/.env"
else
    echo -e "${RED}Error: Backup configuration not found${NC}"
    exit 1
fi

# Restore configuration files
echo ""
echo "Restoring configuration files..."
if [ -d "$BACKUP_DIR/conf" ]; then
    cp -r "$BACKUP_DIR/conf/"* "$INSTALL_DIR/conf/"
    chown -R gitlab-mirror:gitlab-mirror "$INSTALL_DIR/conf"
    chmod 600 "$INSTALL_DIR/conf/.env"
    echo -e "${GREEN}✓${NC} Configuration restored"
else
    echo -e "${YELLOW}Warning: No configuration files found in backup${NC}"
fi

# Restore database
echo ""
echo "Restoring database..."

# Find database dump
DUMP_FILE=""
if [ -f "$BACKUP_DIR/database.sql.gz" ]; then
    DUMP_FILE="$BACKUP_DIR/database.sql.gz"
elif [ -f "$BACKUP_DIR/database.sql" ]; then
    DUMP_FILE="$BACKUP_DIR/database.sql"
else
    echo -e "${RED}Error: Database dump not found in backup${NC}"
    exit 1
fi

# Decompress if needed
if [[ "$DUMP_FILE" == *.gz ]]; then
    echo "Decompressing database dump..."
    gunzip -c "$DUMP_FILE" | mysql \
        --host="${DB_HOST:-localhost}" \
        --port="${DB_PORT:-3306}" \
        --user="${DB_USERNAME}" \
        --password="${DB_PASSWORD}"
else
    mysql \
        --host="${DB_HOST:-localhost}" \
        --port="${DB_PORT:-3306}" \
        --user="${DB_USERNAME}" \
        --password="${DB_PASSWORD}" \
        < "$DUMP_FILE"
fi

echo -e "${GREEN}✓${NC} Database restored"

# Ask to restart service
echo ""
read -p "Do you want to start the service now? (Y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    systemctl start gitlab-mirror-server
    sleep 2
    if systemctl is-active --quiet gitlab-mirror-server; then
        echo -e "${GREEN}✓${NC} Service started successfully"
    else
        echo -e "${RED}Error: Service failed to start${NC}"
        echo "Check logs: journalctl -u gitlab-mirror-server -n 50"
    fi
fi

# Summary
echo ""
echo "======================================"
echo "  Restore Complete!"
echo "======================================"
echo ""
echo "Restored from: $BACKUP_DIR"
echo "Current configuration backup: $CURRENT_BACKUP"
echo ""
echo "Check service status:"
echo "  sudo systemctl status gitlab-mirror-server"
echo ""
