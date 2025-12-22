# GitLab Mirror - Installation Guide

## System Requirements

### Hardware Requirements

- **CPU**: 2 cores minimum, 4 cores recommended
- **Memory**: 2GB minimum, 4GB recommended
- **Disk**: 10GB minimum for application, additional space for database

### Software Requirements

- **OS**: Linux (CentOS 7+, Ubuntu 18.04+, Debian 10+)
- **Java**: OpenJDK or Oracle JDK 17 or higher
- **MySQL**: 8.0 or higher
- **Python**: 3.6+ (for utility scripts)
- **systemd**: For service management

## Pre-Installation Steps

### 1. Install Java 17

**CentOS/RHEL:**
```bash
sudo yum install java-17-openjdk java-17-openjdk-devel
```

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install openjdk-17-jdk
```

Verify installation:
```bash
java -version
```

### 2. Install MySQL 8.0

**CentOS/RHEL:**
```bash
sudo yum install mysql-server
sudo systemctl start mysqld
sudo systemctl enable mysqld
```

**Ubuntu/Debian:**
```bash
sudo apt-get install mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql
```

### 3. Create Database and User

```bash
mysql -u root -p
```

```sql
CREATE DATABASE gitlab_mirror CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'gitlab_mirror'@'localhost' IDENTIFIED BY 'your_password_here';
GRANT ALL PRIVILEGES ON gitlab_mirror.* TO 'gitlab_mirror'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

## Installation

### 1. Download and Extract

```bash
# Download the package
wget https://github.com/your-org/gitlab-mirror/releases/download/v1.0.0/gitlab-mirror-1.0.0-dist.tar.gz

# Extract
tar -xzf gitlab-mirror-1.0.0-dist.tar.gz
cd gitlab-mirror-1.0.0
```

### 2. Run Installation Script

```bash
sudo ./scripts/install.sh
```

The installation script will:
- ✅ Check Java installation
- ✅ Create service user and group (`gitlab-mirror`)
- ✅ Install files to `/opt/gitlab-mirror`
- ✅ Install systemd service
- ✅ Create CLI symlink at `/usr/local/bin/gitlab-mirror`
- ✅ Optionally initialize database

### 3. Configure Environment

Edit the configuration file:

```bash
sudo vi /opt/gitlab-mirror/conf/.env
```

**Required settings:**

```bash
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=gitlab_mirror
DB_USERNAME=gitlab_mirror
DB_PASSWORD=your_database_password

# Source GitLab
SOURCE_GITLAB_URL=https://source-gitlab.example.com
SOURCE_GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxx

# Target GitLab
TARGET_GITLAB_URL=https://target-gitlab.example.com
TARGET_GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxx
TARGET_GITLAB_MIRROR_URL=https://target-gitlab.example.com

# API Service
API_PORT=9999
API_TOKEN_1=your-api-token-here
GITLAB_MIRROR_API_URL=http://localhost:${API_PORT}
```

### 4. Initialize Database

If not done during installation:

```bash
mysql -h localhost -P 3306 -u gitlab_mirror -p gitlab_mirror < /opt/gitlab-mirror/sql/schema.sql
```

### 5. Start Service

```bash
# Start service
sudo systemctl start gitlab-mirror-server

# Enable auto-start on boot
sudo systemctl enable gitlab-mirror-server

# Check status
sudo systemctl status gitlab-mirror-server
```

### 6. Verify Installation

```bash
# Check service health
sudo /opt/gitlab-mirror/server/bin/status.sh

# Test CLI
gitlab-mirror projects

# Check logs
sudo tail -f /opt/gitlab-mirror/server/logs/gitlab-mirror.log
```

## Post-Installation

### Configure Firewall

If using firewalld:

```bash
sudo firewall-cmd --permanent --add-port=9999/tcp
sudo firewall-cmd --reload
```

If using iptables:

```bash
sudo iptables -A INPUT -p tcp --dport 9999 -j ACCEPT
sudo service iptables save
```

### Set Up Log Rotation

Create `/etc/logrotate.d/gitlab-mirror`:

```
/opt/gitlab-mirror/server/logs/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    create 0644 gitlab-mirror gitlab-mirror
    sharedscripts
    postrotate
        systemctl reload gitlab-mirror-server > /dev/null 2>&1 || true
    endscript
}
```

### Configure Monitoring

Add health check to your monitoring system:

```bash
/opt/gitlab-mirror/scripts/health-check.sh
```

Exit codes:
- `0` = OK
- `1` = Warning
- `2` = Critical

## Troubleshooting

### Service Won't Start

Check logs:
```bash
sudo journalctl -u gitlab-mirror-server -n 50
sudo tail -100 /opt/gitlab-mirror/server/logs/gitlab-mirror.log
```

Common issues:
- Database connection failure - check DB credentials in `.env`
- Port already in use - check if port 9999 is available
- Permission issues - ensure files owned by `gitlab-mirror` user

### Database Connection Issues

Test connection:
```bash
mysql -h localhost -P 3306 -u gitlab_mirror -p
```

Verify credentials in `/opt/gitlab-mirror/conf/.env`

### API Not Responding

Check if service is running:
```bash
sudo systemctl status gitlab-mirror-server
```

Test API directly:
```bash
curl http://localhost:9999/actuator/health
```

## Upgrading

See [UPGRADE.md](UPGRADE.md) for upgrade instructions.

## Uninstallation

```bash
sudo /opt/gitlab-mirror/scripts/uninstall.sh
```

Options:
- Keep configuration and data (recommended for temporary removal)
- Complete removal (delete all files)
- Remove service user and group

## Support

- Documentation: https://github.com/your-org/gitlab-mirror/docs
- Issues: https://github.com/your-org/gitlab-mirror/issues
- Wiki: https://github.com/your-org/gitlab-mirror/wiki
