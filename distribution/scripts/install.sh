#!/bin/bash
#
# GitLab Mirror - Installation Script
#
# This script installs GitLab Mirror Server as a systemd service
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

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "======================================"
echo "  GitLab Mirror Installation Script"
echo "======================================"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Error: This script must be run as root${NC}"
    echo "Please run: sudo $0"
    exit 1
fi

# Check if Java is installed
echo "Checking Java installation..."
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed${NC}"
    echo "Please install Java 17 or higher"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Error: Java 17 or higher is required${NC}"
    echo "Current version: $JAVA_VERSION"
    exit 1
fi
echo -e "${GREEN}✓${NC} Java $JAVA_VERSION found"

# Check if MySQL is available
echo "Checking MySQL availability..."
if ! command -v mysql &> /dev/null; then
    echo -e "${YELLOW}Warning: mysql client not found${NC}"
    echo "Please ensure MySQL server is installed and running"
else
    echo -e "${GREEN}✓${NC} MySQL client found"
fi

# Create service user and group
echo ""
echo "Creating service user and group..."
if ! getent group "$SERVICE_GROUP" > /dev/null 2>&1; then
    groupadd --system "$SERVICE_GROUP"
    echo -e "${GREEN}✓${NC} Created group: $SERVICE_GROUP"
else
    echo "Group already exists: $SERVICE_GROUP"
fi

if ! getent passwd "$SERVICE_USER" > /dev/null 2>&1; then
    useradd --system --gid "$SERVICE_GROUP" \
        --home-dir "$INSTALL_DIR" \
        --shell /bin/false \
        --comment "GitLab Mirror Service User" \
        "$SERVICE_USER"
    echo -e "${GREEN}✓${NC} Created user: $SERVICE_USER"
else
    echo "User already exists: $SERVICE_USER"
fi

# Create installation directory
echo ""
echo "Installing to $INSTALL_DIR..."
if [ -d "$INSTALL_DIR" ]; then
    echo -e "${YELLOW}Warning: Installation directory already exists${NC}"
    read -p "Do you want to overwrite? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Installation cancelled"
        exit 1
    fi
    echo "Backing up existing installation..."
    BACKUP_DIR="/tmp/gitlab-mirror-backup-$(date +%Y%m%d%H%M%S)"
    cp -r "$INSTALL_DIR" "$BACKUP_DIR"
    echo "Backup created at: $BACKUP_DIR"
fi

# Copy files
mkdir -p "$INSTALL_DIR"
rsync -av --exclude='distribution' "$APP_HOME/" "$INSTALL_DIR/"

# Create .env from example if not exists
if [ ! -f "$INSTALL_DIR/conf/.env" ]; then
    if [ -f "$INSTALL_DIR/conf/.env.example" ]; then
        cp "$INSTALL_DIR/conf/.env.example" "$INSTALL_DIR/conf/.env"
        echo -e "${GREEN}✓${NC} Created conf/.env from template"
        echo -e "${YELLOW}  Please edit $INSTALL_DIR/conf/.env with your configuration${NC}"
    fi
fi

# Set ownership
chown -R "$SERVICE_USER:$SERVICE_GROUP" "$INSTALL_DIR"
echo -e "${GREEN}✓${NC} Set ownership to $SERVICE_USER:$SERVICE_GROUP"

# Set permissions
chmod -R 755 "$INSTALL_DIR/server/bin"
chmod -R 755 "$INSTALL_DIR/cli/bin"
chmod -R 755 "$INSTALL_DIR/scripts"
chmod 600 "$INSTALL_DIR/conf/.env" 2>/dev/null || true
echo -e "${GREEN}✓${NC} Set file permissions"

# Create symlink for CLI
echo ""
echo "Creating CLI symlink..."
if [ -L "/usr/local/bin/gitlab-mirror" ]; then
    rm -f "/usr/local/bin/gitlab-mirror"
fi
ln -s "$INSTALL_DIR/cli/bin/gitlab-mirror" "/usr/local/bin/gitlab-mirror"
echo -e "${GREEN}✓${NC} Created symlink: /usr/local/bin/gitlab-mirror"

# Install systemd service
echo ""
echo "Installing systemd service..."
cp "$INSTALL_DIR/systemd/gitlab-mirror-server.service" "$SYSTEMD_SERVICE_FILE"
systemctl daemon-reload
echo -e "${GREEN}✓${NC} Installed systemd service"

# Initialize database (if needed)
echo ""
echo "Database initialization:"
echo "  SQL schema: $INSTALL_DIR/sql/schema.sql"
echo ""
read -p "Do you want to initialize the database now? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    source "$INSTALL_DIR/conf/.env"

    echo "Initializing database..."
    mysql -h "${DB_HOST:-localhost}" \
          -P "${DB_PORT:-3306}" \
          -u "${DB_USERNAME}" \
          -p"${DB_PASSWORD}" \
          "${DB_NAME}" < "$INSTALL_DIR/sql/schema.sql"

    echo -e "${GREEN}✓${NC} Database initialized"
else
    echo "Skipped database initialization"
    echo "Run manually: mysql -u user -p database < $INSTALL_DIR/sql/schema.sql"
fi

# Summary
echo ""
echo "======================================"
echo "  Installation Complete!"
echo "======================================"
echo ""
echo "Installation directory: $INSTALL_DIR"
echo "Service user: $SERVICE_USER"
echo "Systemd service: $SYSTEMD_SERVICE_FILE"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo ""
echo "1. Configure environment variables:"
echo "   sudo vi $INSTALL_DIR/conf/.env"
echo ""
echo "2. Start the server:"
echo "   sudo systemctl start gitlab-mirror-server"
echo ""
echo "3. Enable auto-start on boot:"
echo "   sudo systemctl enable gitlab-mirror-server"
echo ""
echo "4. Check server status:"
echo "   sudo systemctl status gitlab-mirror-server"
echo "   or"
echo "   sudo $INSTALL_DIR/server/bin/status.sh"
echo ""
echo "5. Use CLI commands:"
echo "   gitlab-mirror projects"
echo "   gitlab-mirror diff"
echo ""
echo "6. View logs:"
echo "   sudo journalctl -u gitlab-mirror-server -f"
echo "   or"
echo "   sudo tail -f $INSTALL_DIR/server/logs/gitlab-mirror.log"
echo ""
