# GitLab Mirror - Configuration Guide

## Configuration File

The main configuration file is located at: `/opt/gitlab-mirror/conf/.env`

This file is shared between the server and CLI client.

## Configuration Variables

### Database Configuration

```bash
# MySQL database connection
DB_HOST=localhost          # Database server hostname
DB_PORT=3306              # Database server port
DB_NAME=gitlab_mirror     # Database name
DB_USERNAME=gitlab_mirror # Database username
DB_PASSWORD=your_password # Database password

# MySQL Root Password (Docker only)
MYSQL_ROOT_PASSWORD=root_password_123
```

### GitLab Source Configuration

```bash
# Source GitLab instance
SOURCE_GITLAB_URL=http://localhost:8000
SOURCE_GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxx
```

**Token Requirements:**
- Scopes: `api`, `read_repository`, `write_repository`
- Role: Maintainer or Owner on source projects
- Expiration: Set based on your security policy

### GitLab Target Configuration

```bash
# Target GitLab instance
TARGET_GITLAB_URL=http://localhost:9000
TARGET_GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxx

# Mirror URL (for container-to-container communication)
TARGET_GITLAB_MIRROR_URL=http://host.docker.internal:9000
```

**Notes:**
- `TARGET_GITLAB_URL` - External access URL
- `TARGET_GITLAB_MIRROR_URL` - URL used by source GitLab to push to target
  - Same as `TARGET_GITLAB_URL` for most setups
  - Different if using Docker/Kubernetes (e.g., `http://host.docker.internal:9000`)

### API Service Configuration

```bash
# Server listening address and port
API_HOST=0.0.0.0         # Listen on all interfaces
API_PORT=9999            # API port

# API authentication key (independent from GitLab tokens)
# Used for CLI to Server authentication
GITLAB_MIRROR_API_KEY=your-secure-api-key-here

# CLI client API connection URL (supports variable substitution)
GITLAB_MIRROR_API_URL=http://localhost:${API_PORT}
```

**Variable Substitution:**
- `${API_PORT}` is automatically replaced with the value of `API_PORT`
- Allows server and CLI to use the same configuration

**Security Notes:**
- `GITLAB_MIRROR_API_KEY` is independent from GitLab access tokens
- Use a strong random key (UUID or similar)
- Change the default key in production

### Webhook Configuration (Optional)

```bash
WEBHOOK_SECRET=your-webhook-secret
```

### Logging Configuration

```bash
LOG_LEVEL=INFO                    # DEBUG, INFO, WARN, ERROR
LOG_FILE=logs/gitlab-mirror.log   # Log file path
```

### Pull Sync Configuration (Optional)

```bash
PULL_SYNC_ENABLED=true
PULL_SYNC_BASE_PATH=/opt/gitlab-mirror/repos
PULL_SYNC_RETENTION_DAYS=7
```

### Spring Profiles

```bash
# Active Spring profile
SPRING_PROFILES_ACTIVE=prod  # dev, test, prod
```

## Server Configuration (application.yml)

Additional server configuration is in: `/opt/gitlab-mirror/server/conf/application.yml`

### Basic Configuration

```yaml
server:
  port: ${API_PORT:9999}
  shutdown: graceful

spring:
  application:
    name: gitlab-mirror-server
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:prod}
```

### Database Configuration

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:gitlab_mirror}
    username: ${DB_USERNAME:gitlab_mirror}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### MyBatis-Plus Configuration

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

### Scheduler Configuration

```yaml
scheduler:
  # Project discovery - every day at 2 AM
  discovery-cron: "0 0 2 * * ?"

  # Monitor sync status - every 5 minutes
  monitor-cron: "0 */5 * * * ?"
```

### Logging Configuration (logback-spring.xml)

Located at: `/opt/gitlab-mirror/server/conf/logback-spring.xml`

```xml
<configuration>
    <property name="LOG_PATH" value="${LOG_FILE:-logs/gitlab-mirror.log}"/>
    <property name="LOG_LEVEL" value="${LOG_LEVEL:-INFO}"/>

    <!-- Console appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- File appender -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}.%d{yyyy-MM-dd}.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="${LOG_LEVEL}">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

## Environment-Specific Configuration

### Development (application-dev.yml)

```yaml
logging:
  level:
    root: INFO
    com.gitlab.mirror: DEBUG
```

### Production (application-prod.yml)

```yaml
logging:
  level:
    root: WARN
    com.gitlab.mirror: INFO

spring:
  datasource:
    hikari:
      maximum-pool-size: 20
```

## Configuration Best Practices

### Security

1. **Protect .env file:**
   ```bash
   sudo chmod 600 /opt/gitlab-mirror/conf/.env
   sudo chown gitlab-mirror:gitlab-mirror /opt/gitlab-mirror/conf/.env
   ```

2. **Use environment variables for secrets:**
   - Never commit `.env` to version control
   - Use `.env.example` as a template

3. **Rotate tokens regularly:**
   - GitLab tokens should be rotated periodically
   - Update `.env` and restart service

### Performance

1. **Database connection pool:**
   - Adjust `maximum-pool-size` based on load
   - Monitor connection usage

2. **JVM options:**
   ```bash
   # In server/bin/start.sh
   JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
   ```

3. **Log level:**
   - Use `INFO` or `WARN` in production
   - Use `DEBUG` only for troubleshooting

### Reliability

1. **Database backup:**
   - Regular backups via cron
   ```bash
   0 2 * * * /opt/gitlab-mirror/scripts/backup.sh
   ```

2. **Health monitoring:**
   ```bash
   */5 * * * * /opt/gitlab-mirror/scripts/health-check.sh
   ```

3. **Log rotation:**
   - Configure via logrotate
   - Keep 30 days of logs

## Verification

After configuration changes:

```bash
# Restart service
sudo systemctl restart gitlab-mirror-server

# Check status
sudo systemctl status gitlab-mirror-server

# Verify configuration
gitlab-mirror projects

# Check logs
sudo tail -f /opt/gitlab-mirror/server/logs/gitlab-mirror.log
```

## Troubleshooting

### Configuration Not Loading

```bash
# Check .env file exists
ls -l /opt/gitlab-mirror/conf/.env

# Check permissions
sudo ls -l /opt/gitlab-mirror/conf/.env

# Verify syntax
source /opt/gitlab-mirror/conf/.env && env | grep GITLAB
```

### Database Connection Failed

```bash
# Test database connection
mysql -h $DB_HOST -P $DB_PORT -u $DB_USERNAME -p

# Check credentials in .env
sudo cat /opt/gitlab-mirror/conf/.env | grep DB_
```

### API Connection Failed

```bash
# Check if service is running
sudo systemctl status gitlab-mirror-server

# Check port
sudo netstat -tlnp | grep $API_PORT

# Test API
curl http://localhost:$API_PORT/actuator/health
```
