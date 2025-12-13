# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GitLab Mirror MVP is a GitLab project synchronization tool based on GitLab's native Push Mirror feature. It provides a client/server architecture for batch configuration and management of GitLab Push Mirrors.

**Key Architecture**: Client/Server separation
- **CLI Client** (`cli-client/`): Shell scripts + Java JAR for user interaction
- **Sync Service** (`server/`): Spring Boot 3 REST API server for sync logic
- **Common** (`common/`): Shared models and utilities

**Tech Stack**: Spring Boot 3 + MyBatis-Plus + MySQL 8.0 + Docker

## Development Environment Setup

### Start Development Environment

```bash
# Start MySQL database
docker-compose up -d

# Verify MySQL is running
docker ps | grep gitlab-mirror-mysql

# Test database connection
docker exec -it gitlab-mirror-mysql mysql -ugitlab_mirror -pmirror_pass_123 gitlab_mirror -e "SELECT 'Database OK' AS status;"
```

### Start GitLab Test Instances

```bash
# Start source GitLab (port 8000)
cd docker/gitlab-source
docker-compose up -d
docker-compose logs -f  # Wait for "Reconfigured!" message (~5-10 min)

# Start target GitLab (port 9000)
cd ../gitlab-target
docker-compose up -d
docker-compose logs -f
```

**GitLab Credentials**:
- Source GitLab: http://localhost:8000 (SSH: 2222)
- Target GitLab: http://localhost:9000 (SSH: 2223)
- Username: `root`
- Password: `My2024@1213!`

**Access Tokens** (configured in `.env`):
- Source: `glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq`
- Target: `glpat-b2nrFAAy9q2SozZr3Dm0N286MQp1OjEH.01.0w0t2khzm`

### Environment Variables

Configuration is managed via `.env` file (copied from `.env.example`):

```bash
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=gitlab_mirror
DB_USERNAME=gitlab_mirror
DB_PASSWORD=mirror_pass_123

# Source GitLab
SOURCE_GITLAB_URL=http://localhost:8000
SOURCE_GITLAB_TOKEN=glpat-...

# Target GitLab
TARGET_GITLAB_URL=http://localhost:9000
TARGET_GITLAB_TOKEN=glpat-...
```

## Core Architecture

### Multi-Module Maven/Gradle Structure

```
gitlab-mirror/
├── server/              # Spring Boot service (REST API + business logic)
│   ├── entity/          # JPA/MyBatis entities
│   ├── mapper/          # MyBatis-Plus mappers
│   ├── service/         # Business services
│   ├── controller/      # REST controllers
│   └── scheduler/       # Scheduled tasks
├── cli-client/          # CLI client (HTTP client + commands)
│   ├── client/          # REST API client
│   └── command/         # CLI command implementations
├── common/              # Shared code
│   ├── model/           # DTOs and request/response models
│   └── util/            # Utility classes
└── scripts/             # Shell scripts for CLI
```

### Core Data Model

Five main entities (all depend on `SYNC_PROJECT`):

1. **SYNC_PROJECT** (主表): Core sync configuration
   - `project_key` (UK): Unique identifier (source project path)
   - `sync_method`: push_mirror/pull_mirror/clone_push
   - `sync_status`: pending/target_created/mirror_configured/active/failed

2. **SOURCE_PROJECT_INFO**: Source GitLab project metadata
3. **TARGET_PROJECT_INFO**: Target GitLab project metadata
4. **PUSH_MIRROR_CONFIG**: Push Mirror configuration (mirror_id, mirror_url)
5. **SYNC_EVENT**: Sync event history and logs

**Important**: When creating/modifying entities, always create `SYNC_PROJECT` first as all other tables reference it via `project_key` or `sync_project_id`.

### Business Flow

```
Project Discovery → Target Creation → Mirror Configuration → Sync Monitoring
     ↓                    ↓                    ↓                    ↓
Save to DB         Create groups        POST /mirror API      Poll status
Apply filters      Create projects      Save mirror_id        Record events
```

## Key Design References

All detailed designs are in `PUSH_MIRROR_MVP_DESIGN.md`:

- **Data Model**: Section "核心实体及关系" - ER diagram and field definitions
- **Business Logic**: Section "关键处理流程" - Flowcharts for discovery, mirror setup, monitoring
- **REST API**: Section "REST API 设计" - API endpoints, request/response formats
- **CLI Commands**: Section "CLI 命令设计" - Command structure and examples
- **Error Handling**: Section "错误处理" - Retry strategies, API rate limiting
- **Logging**: Section "日志设计" - JSON structured logging with MDC context

## Configuration Format

Full configuration specification is in `mvp/CONFIGURATION.md`. Key structure:

```yaml
source:
  url: ${SOURCE_GITLAB_URL}
  token: ${SOURCE_GITLAB_TOKEN}
target:
  url: ${TARGET_GITLAB_URL}
  token: ${TARGET_GITLAB_TOKEN}
sync:
  groups:
    - path: "group1"
      include_subgroups: true
  filters:
    exclude_archived: true
    exclude_empty: true
scheduler:
  discovery_cron: "0 0 2 * * ?"
  monitor_cron: "0 */5 * * * ?"
```

## Development Tasks

Tasks are organized in 8 modules under `mvp/`:

1. **01-infrastructure.md**: Project init, logging, config, DB connection (Week 1, 3-4d)
2. **02-data-model.md**: 5 entities, Mappers, CRUD operations (Week 1, 2d)
3. **03-gitlab-client.md**: GitLab API client with retry (Week 2, 2d)
4. **04-business-services.md**: Discovery, target mgmt, mirror config, events (Week 2, 5-6d)
5. **05-rest-api.md**: RESTful API, authentication, responses (Week 3, 2-3d)
6. **06-cli-client.md**: Shell scripts + Java JAR commands (Week 3, 2-3d)
7. **07-testing.md**: Integration tests, performance (Week 4, 3-4d)
8. **08-deployment.md**: Docker, build config, docs (Week 4, 1-2d)

**Task Dependencies**:
- Data model entities must be created in order: `SYNC_PROJECT` → others
- GitLab client is prerequisite for business services
- REST API is prerequisite for CLI client

## MySQL 8.4 Compatibility Note

MySQL 8.4 removed `--default-authentication-plugin`. The docker-compose.yml uses:

```yaml
command:
  - --mysql-native-password=ON  # Not --default-authentication-plugin
```

If MySQL container fails to start with authentication plugin errors, ensure this flag is used instead of the deprecated parameter.

## GitLab API Token Management

To create/recreate tokens via Rails console:

```bash
# For source GitLab
docker exec gitlab-source gitlab-rails runner '
user = User.find_by(username: "root")
token = PersonalAccessToken.create!(
  user: user,
  name: "gitlab-mirror-source",
  scopes: [:api, :read_repository, :write_repository],
  expires_at: 365.days.from_now
)
puts "Token: #{token.token}"
'

# For target GitLab
docker exec gitlab-target gitlab-rails runner '...'
```

Required scopes: `api`, `read_repository`, `write_repository`

## Docker Management

### Common Operations

```bash
# View all project containers
docker ps | grep gitlab

# View MySQL logs
docker logs gitlab-mirror-mysql

# View GitLab logs
cd docker/gitlab-source
docker-compose logs -f

# Restart GitLab
docker exec gitlab-source gitlab-ctl restart

# Reconfigure GitLab after config changes
docker exec gitlab-source gitlab-ctl reconfigure
```

### Port Mappings

- MySQL: 3306
- Source GitLab HTTP: 8000
- Source GitLab SSH: 2222
- Target GitLab HTTP: 9000
- Target GitLab SSH: 2223

## Task Management Guidelines

**IMPORTANT**: When working on development tasks, you MUST use the TodoWrite tool to track progress:

### Required Task Tracking Workflow

1. **Before starting a task**: Mark it as `in_progress`
   ```
   Mark task as in_progress BEFORE beginning any work
   ```

2. **After completing a task successfully**: Mark it as `completed`
   ```
   Mark task as completed IMMEDIATELY after finishing
   ```

3. **If a task fails or is blocked**: Keep it as `in_progress` and document the issue
   ```
   Do NOT mark as completed if tests fail or errors occur
   Create a new task for the blocker if needed
   ```

### Task Status Rules

- **EXACTLY ONE** task must be `in_progress` at any time (not less, not more)
- **NEVER** batch multiple completions - update status after each task
- **ALWAYS** use both forms for task descriptions:
  - `content`: Imperative form (e.g., "Create database schema")
  - `activeForm`: Present continuous (e.g., "Creating database schema")

### Example Workflow

```
1. User asks to implement feature X
2. TodoWrite: Mark "Implement feature X" as in_progress
3. Do the work...
4. TodoWrite: Mark "Implement feature X" as completed
5. TodoWrite: Mark "Test feature X" as in_progress
6. Run tests...
7. If tests pass: TodoWrite: Mark "Test feature X" as completed
8. If tests fail: Keep "Test feature X" as in_progress, fix issues
```

**Failure to follow this workflow will result in lost track of progress and incomplete task management.**

## Important Notes

1. **Client/Server Communication**: CLI client communicates with sync service via HTTP REST API with token authentication
2. **Push Mirror Approach**: Uses GitLab's native Push Mirror API - requires admin access on source GitLab
3. **Limitations**: MVP only syncs Git repositories (code, branches, tags), not Issues/MRs/Wiki
4. **Scheduled Tasks**: Project discovery and mirror monitoring are scheduled tasks in the server module
5. **Configuration Hot Reload**: Server supports configuration hot reload for sync rules
