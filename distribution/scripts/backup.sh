#!/bin/bash
#
# GitLab Mirror - Backup Script
#
# Backs up configuration and database
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
INSTALL_DIR="/opt/gitlab-mirror"
BACKUP_BASE_DIR="${GITLAB_MIRROR_BACKUP_DIR:-/var/backups/gitlab-mirror}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$BACKUP_BASE_DIR/backup_$TIMESTAMP"

echo "======================================"
echo "  GitLab Mirror Backup Script"
echo "======================================"
echo ""

# Load environment
if [ -f "$INSTALL_DIR/conf/.env" ]; then
    source "$INSTALL_DIR/conf/.env"
else
    echo -e "${RED}Error: Configuration file not found${NC}"
    echo "Expected: $INSTALL_DIR/conf/.env"
    exit 1
fi

# Create backup directory
mkdir -p "$BACKUP_DIR"
echo "Backup directory: $BACKUP_DIR"

# Backup configuration files
echo ""
echo "Backing up configuration files..."
mkdir -p "$BACKUP_DIR/conf"
cp -r "$INSTALL_DIR/conf/"* "$BACKUP_DIR/conf/"
echo -e "${GREEN}✓${NC} Configuration backed up"

# Backup database
echo ""
echo "Backing up database..."
DUMP_FILE="$BACKUP_DIR/database.sql"

mysqldump \
    --host="${DB_HOST:-localhost}" \
    --port="${DB_PORT:-3306}" \
    --user="${DB_USERNAME}" \
    --password="${DB_PASSWORD}" \
    --single-transaction \
    --routines \
    --triggers \
    --databases "${DB_NAME}" \
    > "$DUMP_FILE"

# Compress database dump
gzip "$DUMP_FILE"
echo -e "${GREEN}✓${NC} Database backed up to: $DUMP_FILE.gz"

# Create backup metadata
cat > "$BACKUP_DIR/backup_info.txt" << EOF
Backup Information
==================
Timestamp: $TIMESTAMP
Date: $(date)
Hostname: $(hostname)
Install Directory: $INSTALL_DIR
Database: ${DB_NAME}
Database Host: ${DB_HOST:-localhost}:${DB_PORT:-3306}

Contents:
- conf/          : Configuration files
- database.sql.gz: Database dump

Restore command:
  $INSTALL_DIR/scripts/restore.sh $BACKUP_DIR
EOF

echo -e "${GREEN}✓${NC} Backup metadata created"

# Calculate backup size
BACKUP_SIZE=$(du -sh "$BACKUP_DIR" | awk '{print $1}')

# Cleanup old backups (keep last 7 days by default)
RETENTION_DAYS="${GITLAB_MIRROR_BACKUP_RETENTION_DAYS:-7}"
echo ""
echo "Cleaning up old backups (keeping last $RETENTION_DAYS days)..."
find "$BACKUP_BASE_DIR" -maxdepth 1 -type d -name "backup_*" -mtime +$RETENTION_DAYS -exec rm -rf {} \;
echo -e "${GREEN}✓${NC} Old backups cleaned up"

# Summary
echo ""
echo "======================================"
echo "  Backup Complete!"
echo "======================================"
echo ""
echo "Backup location: $BACKUP_DIR"
echo "Backup size: $BACKUP_SIZE"
echo ""
echo "Contents:"
echo "  - Configuration: $BACKUP_DIR/conf/"
echo "  - Database: $BACKUP_DIR/database.sql.gz"
echo "  - Metadata: $BACKUP_DIR/backup_info.txt"
echo ""
echo "To restore this backup:"
echo "  sudo $INSTALL_DIR/scripts/restore.sh $BACKUP_DIR"
echo ""
