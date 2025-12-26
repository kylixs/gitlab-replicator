# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 本地开发
- 后端端口：9999
- 前端端口：3000
- 后端启动脚本: ./scripts/gitlab-mirror restart
- 前端启动脚本: ./start-dev.sh
  
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



### Task Document Location

For Pull-Sync tasks, update status in these files:
- `docs/pull-sync/01-data-model.md`
- `docs/pull-sync/02-project-discovery.md`

Each task has a status line like:
```markdown
### T3.2 Pull 同步服务 - 首次同步
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T3.1, 模块2 - 项目发现扩展
```

Update the `**状态**:` line before and after working on the task.

**Failure to follow this workflow will result in lost track of progress and incomplete task management.**

## Git Commit Guidelines

**IMPORTANT**: When creating git commits, follow these rules strictly:

1. **Commit Message Format**:
   - Keep messages concise and focused on key information only
   - Use conventional commit format: `type(scope): brief description`
   - Examples: `feat(pull-sync): add incremental sync`, `fix(api): correct timeout handling`

2. **What NOT to Include**:
   - ❌ DO NOT include any Claude-related information
   - ❌ DO NOT add unnecessary preamble or elaboration


## Important Notes

1. **Client/Server Communication**: CLI client communicates with sync service via HTTP REST API with token authentication
2. **Push Mirror Approach**: Uses GitLab's native Push Mirror API - requires admin access on source GitLab
3. **Limitations**: MVP only syncs Git repositories (code, branches, tags), not Issues/MRs/Wiki
4. **Scheduled Tasks**: Project discovery and mirror monitoring are scheduled tasks in the server module
5. **Configuration Hot Reload**: Server supports configuration hot reload for sync rules
