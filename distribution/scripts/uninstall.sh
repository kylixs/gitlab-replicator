#!/bin/bash
#
# GitLab Mirror - Uninstallation Script
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
INSTALL_DIR="/opt/gitlab-mirror"
SERVICE_USER="gitlab-mirror"
SERVICE_GROUP="gitlab-mirror"
SYSTEMD_SERVICE_FILE="/etc/systemd/system/gitlab-mirror-server.service"
CLI_SYMLINK="/usr/local/bin/gitlab-mirror"

echo "======================================"
echo "  GitLab Mirror Uninstallation Script"
echo "======================================"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Error: This script must be run as root${NC}"
    echo "Please run: sudo $0"
    exit 1
fi

# Confirmation
echo -e "${YELLOW}WARNING: This will remove GitLab Mirror Server${NC}"
echo ""
read -p "Are you sure you want to uninstall? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Uninstallation cancelled"
    exit 0
fi

# Ask about data preservation
KEEP_DATA=false
echo ""
read -p "Do you want to keep configuration and data? (Y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    KEEP_DATA=true
fi

# Stop and disable service
echo ""
echo "Stopping service..."
if systemctl is-active --quiet gitlab-mirror-server; then
    systemctl stop gitlab-mirror-server
    echo -e "${GREEN}✓${NC} Service stopped"
else
    echo "Service is not running"
fi

if systemctl is-enabled --quiet gitlab-mirror-server 2>/dev/null; then
    systemctl disable gitlab-mirror-server
    echo -e "${GREEN}✓${NC} Service disabled"
fi

# Remove systemd service file
if [ -f "$SYSTEMD_SERVICE_FILE" ]; then
    rm -f "$SYSTEMD_SERVICE_FILE"
    systemctl daemon-reload
    echo -e "${GREEN}✓${NC} Removed systemd service"
fi

# Remove CLI symlink
if [ -L "$CLI_SYMLINK" ]; then
    rm -f "$CLI_SYMLINK"
    echo -e "${GREEN}✓${NC} Removed CLI symlink"
fi

# Backup or remove installation directory
if [ -d "$INSTALL_DIR" ]; then
    if [ "$KEEP_DATA" = true ]; then
        BACKUP_DIR="/tmp/gitlab-mirror-backup-$(date +%Y%m%d%H%M%S)"
        echo ""
        echo "Backing up installation to: $BACKUP_DIR"
        cp -r "$INSTALL_DIR" "$BACKUP_DIR"
        echo -e "${GREEN}✓${NC} Backup created"

        # Keep only configuration and data
        echo "Cleaning up installation directory..."
        rm -rf "$INSTALL_DIR/server/lib"
        rm -rf "$INSTALL_DIR/cli/lib"
        rm -rf "$INSTALL_DIR/server/bin"
        rm -rf "$INSTALL_DIR/cli/bin"
        rm -rf "$INSTALL_DIR/scripts"
        rm -rf "$INSTALL_DIR/systemd"
        rm -rf "$INSTALL_DIR/docs"
        echo -e "${GREEN}✓${NC} Kept configuration in: $INSTALL_DIR/conf"
        echo -e "${GREEN}✓${NC} Kept logs in: $INSTALL_DIR/server/logs"
    else
        echo ""
        echo "Removing installation directory..."
        rm -rf "$INSTALL_DIR"
        echo -e "${GREEN}✓${NC} Installation directory removed"
    fi
fi

# Ask about user and group removal
echo ""
read -p "Do you want to remove service user and group? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    if getent passwd "$SERVICE_USER" > /dev/null 2>&1; then
        userdel "$SERVICE_USER"
        echo -e "${GREEN}✓${NC} Removed user: $SERVICE_USER"
    fi

    if getent group "$SERVICE_GROUP" > /dev/null 2>&1; then
        groupdel "$SERVICE_GROUP"
        echo -e "${GREEN}✓${NC} Removed group: $SERVICE_GROUP"
    fi
else
    echo "Kept service user and group"
fi

# Summary
echo ""
echo "======================================"
echo "  Uninstallation Complete!"
echo "======================================"
echo ""

if [ "$KEEP_DATA" = true ]; then
    echo -e "${YELLOW}Configuration and logs preserved:${NC}"
    if [ -d "$INSTALL_DIR/conf" ]; then
        echo "  Configuration: $INSTALL_DIR/conf"
    fi
    if [ -d "$INSTALL_DIR/server/logs" ]; then
        echo "  Logs: $INSTALL_DIR/server/logs"
    fi
    echo "  Backup: $BACKUP_DIR"
    echo ""
    echo "To completely remove, run: sudo rm -rf $INSTALL_DIR"
else
    echo "All files have been removed"
fi
echo ""
