# GitLab Mirror

GitLab project synchronization tool - Server/Client architecture for batch configuration and management of GitLab project mirroring.

## Features

- 🔄 **Automated Synchronization** - Continuous sync between source and target GitLab instances
- 🎯 **Batch Configuration** - Configure multiple projects at once
- 📊 **Real-time Monitoring** - Monitor sync status and detect inconsistencies
- 🛠️ **REST API** - Full-featured REST API for integration
- 💻 **CLI Client** - Easy-to-use command-line interface
- 📈 **Performance** - Optimized with GraphQL batch queries
- 🔒 **Secure** - Token-based authentication, secure configuration

## Architecture

```
┌─────────────────┐
│  CLI Client     │  ← Command line interface
└────────┬────────┘
         │ HTTP/REST
         ↓
┌─────────────────┐
│  Server (API)   │  ← Spring Boot REST API
└────────┬────────┘
         │
    ┌────┴────┐
    ↓         ↓
┌────────┐  ┌────────┐
│ MySQL  │  │ GitLab │  ← Source & Target GitLab
└────────┘  └────────┘
```

## Quick Start

### Installation

```bash
# Extract package
tar -xzf gitlab-mirror-1.0.0-dist.tar.gz
cd gitlab-mirror-1.0.0

# Run installation script
sudo ./scripts/install.sh

# Configure
sudo vi /opt/gitlab-mirror/conf/.env

# Start service
sudo systemctl start gitlab-mirror-server
```

See [INSTALL.md](INSTALL.md) for detailed installation instructions.

### Configuration

Edit `/opt/gitlab-mirror/conf/.env`:

```bash
# Source GitLab
SOURCE_GITLAB_URL=https://source-gitlab.com
SOURCE_GITLAB_TOKEN=glpat-xxxxx

# Target GitLab
TARGET_GITLAB_URL=https://target-gitlab.com
TARGET_GITLAB_TOKEN=glpat-xxxxx

# Database
DB_HOST=localhost
DB_NAME=gitlab_mirror
DB_USERNAME=gitlab_mirror
DB_PASSWORD=your_password
```

### Usage

#### CLI Commands

```bash
# List all projects
gitlab-mirror projects

# Show project sync differences
gitlab-mirror diff

# Show specific project diff
gitlab-mirror diff mygroup/myproject
gitlab-mirror diff 123  # by ID

# Trigger scan
gitlab-mirror scan

# Show scan results
gitlab-mirror scan --status=OUTDATED
```

#### Service Management

```bash
# Start/stop service
sudo systemctl start gitlab-mirror-server
sudo systemctl stop gitlab-mirror-server
sudo systemctl restart gitlab-mirror-server

# Check status
sudo systemctl status gitlab-mirror-server
sudo /opt/gitlab-mirror/server/bin/status.sh

# View logs
sudo journalctl -u gitlab-mirror-server -f
sudo tail -f /opt/gitlab-mirror/server/logs/gitlab-mirror.log
```

## REST API

### Endpoints

```bash
# Trigger manual scan
POST /api/sync/scan?type=incremental

# Get project list
GET /api/sync/projects?status=active&page=1&size=20

# Get project details
GET /api/sync/projects/{projectKey}

# Get project diff
GET /api/sync/diff?projectKey=mygroup/myproject
GET /api/sync/diff?syncProjectId=123

# Get project diffs (paginated)
GET /api/sync/diffs?status=OUTDATED&page=1&size=20
```

### Example

```bash
# Get project diff
curl -X GET "http://localhost:9999/api/sync/diff?projectKey=devops/gitlab-mirror" \
  -H "Content-Type: application/json"

# Trigger scan
curl -X POST "http://localhost:9999/api/sync/scan?type=incremental" \
  -H "Content-Type: application/json"
```

See [API.md](API.md) for complete API documentation.

## Monitoring

### Health Check

```bash
# Check service health
/opt/gitlab-mirror/scripts/health-check.sh

# Or via API
curl http://localhost:9999/actuator/health
```

### Metrics

Available at: `http://localhost:9999/actuator/metrics`

Key metrics:
- `jvm.memory.used` - JVM memory usage
- `system.cpu.usage` - CPU usage
- `gitlab.mirror.sync.count` - Sync operation count
- `gitlab.mirror.projects.total` - Total projects

## Backup & Restore

### Backup

```bash
# Full backup (config + database)
sudo /opt/gitlab-mirror/scripts/backup.sh
```

Backups are stored in `/var/backups/gitlab-mirror/` by default.

### Restore

```bash
# Restore from backup
sudo /opt/gitlab-mirror/scripts/restore.sh /var/backups/gitlab-mirror/backup_20250122_143000
```

## Performance

- ⚡ **Fast Scanning** - GraphQL batch queries reduce API calls by 93%
- 📊 **Efficient Caching** - 15-minute cache for diff results
- 🎯 **Incremental Sync** - Only update changed projects
- 🚀 **Batch Processing** - Process multiple projects in parallel

Example: Scanning 14 projects
- Traditional REST API: ~487ms (14 calls)
- Optimized GraphQL: ~148ms (1 call)
- **70% faster**

## System Requirements

- **Java**: 17 or higher
- **MySQL**: 8.0 or higher
- **Memory**: 2GB minimum, 4GB recommended
- **Disk**: 10GB+ for application and database
- **OS**: Linux (CentOS 7+, Ubuntu 18.04+, Debian 10+)

## Directory Structure

```
/opt/gitlab-mirror/
├── server/           # Server components
│   ├── bin/         # Start/stop scripts
│   ├── lib/         # Server JAR
│   ├── conf/        # Server configuration
│   └── logs/        # Log files
├── cli/             # CLI client
│   ├── bin/         # CLI script
│   └── lib/         # CLI JAR
├── conf/            # Global configuration (.env)
├── sql/             # Database scripts
├── scripts/         # Utility scripts
│   ├── install.sh
│   ├── uninstall.sh
│   ├── backup.sh
│   └── restore.sh
└── systemd/         # systemd service file
```

## Maintenance

### View Logs

```bash
# Service logs (systemd)
sudo journalctl -u gitlab-mirror-server -f

# Application logs
sudo tail -f /opt/gitlab-mirror/server/logs/gitlab-mirror.log

# Access logs
sudo tail -f /opt/gitlab-mirror/server/logs/access.log
```

### Rotate Logs

Log rotation is configured automatically via logrotate.
Configuration: `/etc/logrotate.d/gitlab-mirror`

### Update Configuration

```bash
# Edit configuration
sudo vi /opt/gitlab-mirror/conf/.env

# Restart service to apply changes
sudo systemctl restart gitlab-mirror-server
```

## Troubleshooting

### Common Issues

**Service won't start:**
```bash
# Check logs
sudo journalctl -u gitlab-mirror-server -n 50

# Check database connection
mysql -h localhost -u gitlab_mirror -p

# Verify Java version
java -version  # Should be 17+
```

**API not responding:**
```bash
# Check if port is in use
sudo netstat -tlnp | grep 9999

# Check firewall
sudo firewall-cmd --list-ports  # CentOS
sudo ufw status                  # Ubuntu
```

**Database errors:**
```bash
# Verify credentials in .env
sudo cat /opt/gitlab-mirror/conf/.env | grep DB_

# Test database connection
mysql -h localhost -P 3306 -u gitlab_mirror -p gitlab_mirror
```

## Uninstallation

```bash
sudo /opt/gitlab-mirror/scripts/uninstall.sh
```

Options:
- Keep configuration and data (for reinstallation)
- Complete removal (delete everything)

## Documentation

- [Installation Guide](INSTALL.md) - Detailed installation instructions
- [Configuration Guide](CONFIGURATION.md) - Configuration options
- [API Documentation](API.md) - REST API reference
- [Troubleshooting](TROUBLESHOOTING.md) - Common issues and solutions

## Support

- **Issues**: https://github.com/your-org/gitlab-mirror/issues
- **Wiki**: https://github.com/your-org/gitlab-mirror/wiki
- **Discussions**: https://github.com/your-org/gitlab-mirror/discussions

## License

See [LICENSE](../LICENSE) file for details.

## Version

Current version: 1.0.0

See [VERSION](../VERSION) for build information.
