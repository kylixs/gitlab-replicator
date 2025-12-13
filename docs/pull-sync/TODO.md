# Pull Sync Development TODO List

**Last Updated**: 2025-12-14

---

## ⚠️ 重要提醒：任务状态管理规范

**【必须】在开始处理下面的每个任务前及后需要修改其任务状态：**

1. **开始任务前**：
   - 在 TodoWrite 工具中标记任务为 `in_progress`
   - 在对应模块的任务文档中（如 `04-scheduler.md`）更新任务状态为 `🔄 进行中 (In Progress)`

2. **完成任务后**：
   - 在 TodoWrite 工具中标记任务为 `completed`
   - 在对应模块的任务文档中更新任务状态为 `✅ 已完成 (Completed)` 或 `❌ 失败 (Failed)`

3. **状态同步要求**：
   - **两处都必须更新**：TodoWrite 工具 + 任务文档
   - **实时更新**：不要批量更新，每完成一个任务立即更新
   - **准确性**：确保测试通过后才标记为 `completed`

**状态标记说明**：
- `⏸️ 待处理 (Pending)` / `pending` - 任务未开始
- `🔄 进行中 (In Progress)` / `in_progress` - 任务正在处理中
- `✅ 已完成 (Completed)` / `completed` - 任务成功完成，测试通过
- `❌ 失败 (Failed)` / `failed` - 任务失败，需要修复
- `⚠️ 阻塞 (Blocked)` / `blocked` - 任务被依赖阻塞

---

## Progress Summary

- ✅ Module 1: Data Model Extension (100% Complete)
- ✅ Module 2: Project Discovery Extension (100% Complete)
- ✅ Module 3: Pull Sync Executor (100% Complete)
- 🔄 Module 4: Unified Task Scheduler (85% Complete - Tests in Progress)
- ⏸️ Module 5: Webhook Real-time Sync (Pending)
- ⏸️ Module 6: REST API Integration (Pending)
- ⏸️ Module 7: CLI Client Integration (Pending)
- ⏸️ Module 8: Integration & Performance Testing (Pending)
- ⏸️ Module 9: Documentation & Deployment (Pending)

---

## Module 4: Unified Task Scheduler (Current Focus)

### ✅ Completed Tasks

- [x] T4.1: Create UnifiedSyncScheduler core with peak/off-peak scheduling
  - File: `server/src/main/java/com/gitlab/mirror/server/scheduler/UnifiedSyncScheduler.java`
  - Features: Peak hours detection (9-18), concurrent limit control (peak: 3, off-peak: 8)

- [x] T4.2: Implement Pull task scheduling logic with priority-based intervals
  - Priority ordering: critical > high > normal > low
  - Mapper method: `SyncTaskMapper.selectPullTasksWithPriority()`
  - Query filters: task_type=pull, status=waiting, next_run_at<=NOW, enabled=true, failures<5

- [x] T4.3: Adapt Push Mirror polling to SYNC_TASK table
  - Push tasks handled via existing polling mechanism
  - Status updates to SYNC_TASK table integrated

- [x] T4.4: Integrate task executor with thread pool
  - Thread pool config: core=3, max=10, queue=50
  - Rejection policy: CallerRunsPolicy
  - Graceful shutdown support

- [x] T4.5: Add scheduler monitoring metrics and logging
  - Metrics: scheduled tasks count, active tasks, peak/off-peak status
  - Logs: scheduler trigger, task scheduled, completion summary

### 🔄 In Progress

- [ ] T4.6: Write comprehensive tests for scheduler (unit + integration)
  - Status: 13 tests written, 3 failing (needs investigation)
  - Test file: `server/src/test/java/com/gitlab/mirror/server/scheduler/UnifiedSyncSchedulerTest.java`
  - Failing tests:
    - `testSchedulePullTasks_NoAvailableSlots`
    - `testSchedulePullTasks_PriorityOrdering` (needs verification)
    - One more (needs identification)
  - **Action Required**: Debug and fix failing tests

### ⏸️ Pending

- [ ] T4.7: Update task document 04-scheduler.md status to completed
  - Update all task statuses to ✅ 已完成 (Completed)
  - Update module status to ✅ 已完成 (Completed)

---

## Module 5: Webhook Real-time Sync

### ⏸️ Pending Tasks

- [ ] T5.1: Create WebhookController with Secret Token authentication
  - Endpoint: `POST /api/webhook/gitlab/push`
  - Headers: `X-Gitlab-Token` verification
  - Response: 202 Accepted (async processing)
  - Target time: <100ms response

- [ ] T5.2: Implement WebhookEventService with debounce logic
  - Service: `WebhookEventService`
  - Debounce: Skip if last successful sync < 2 minutes
  - Action: Update `next_run_at=NOW()` to trigger immediate scheduling
  - Event recording: Save to SYNC_EVENT table

- [ ] T5.3: Implement project auto-initialization from Webhook
  - Parse GitLab Push Event payload
  - Create SYNC_PROJECT + SOURCE_PROJECT_INFO + PULL_SYNC_CONFIG + SYNC_TASK
  - Transaction: Ensure atomicity
  - Defaults: priority=normal, enabled=true, next_run_at=NOW

- [ ] T5.4: Add Webhook security (IP whitelist, rate limiting)
  - Secret Token validation (from env var)
  - IP whitelist support (optional)
  - Rate limiting: 100 requests/minute
  - Rejection logging and metrics

- [ ] T5.5: Add Webhook monitoring metrics and logging
  - Metrics: webhook_requests_total, processing_duration, initialization_total, debounce_skipped
  - Logs: received, auto-initialized, debounced, scheduled
  - Error tracking by type (auth, parse, processing)

- [ ] T5.6: Write comprehensive tests for Webhook (unit + integration)
  - Unit tests: Controller, Service, Security
  - Integration tests: End-to-end Webhook flow
  - Test scenarios: Valid/invalid token, debounce, auto-init, concurrent requests

- [ ] T5.7: Update task document 05-webhook.md status to completed

---

## Module 6: REST API Integration

### ⏸️ Pending Tasks

- [ ] T6.1: Create REST API endpoints for Pull sync configuration
  - `GET /api/pull-sync/config/{projectId}` - Get config
  - `PUT /api/pull-sync/config/{projectId}/priority` - Update priority
  - `PUT /api/pull-sync/config/{projectId}/enabled` - Enable/disable
  - `GET /api/pull-sync/config` - List all configs with filters

- [ ] T6.2: Create REST API endpoints for task management
  - `GET /api/tasks` - List tasks (filter by type, status, priority)
  - `GET /api/tasks/{taskId}` - Get task details
  - `POST /api/tasks/{taskId}/retry` - Manually retry task
  - `PUT /api/tasks/{taskId}/reset-failures` - Reset failure count
  - `GET /api/tasks/stats` - Task statistics

- [ ] T6.3: Create REST API endpoints for scheduler control
  - `GET /api/scheduler/status` - Get scheduler status
  - `POST /api/scheduler/trigger` - Manually trigger scheduling
  - `GET /api/scheduler/metrics` - Get scheduler metrics
  - `PUT /api/scheduler/config` - Update scheduler config (peak hours, concurrent limits)

- [ ] T6.4: Add API authentication and authorization
  - JWT token or API key authentication
  - Role-based access control (admin, operator, viewer)
  - Audit logging for API calls

- [ ] T6.5: Write API integration tests
  - Test all endpoints with various inputs
  - Test authentication and authorization
  - Test error handling (400, 401, 403, 404, 500)

---

## Module 7: CLI Client Integration

### ⏸️ Pending Tasks

- [ ] T7.1: Extend CLI client for Pull sync commands
  - `gitlab-mirror pull discover <group-path>` - Discover and initialize Pull projects
  - `gitlab-mirror pull list [--priority=<p>] [--enabled]` - List Pull projects
  - `gitlab-mirror pull config <project-key> --priority=<p>` - Update priority
  - `gitlab-mirror pull config <project-key> --enable/--disable` - Enable/disable

- [ ] T7.2: Add CLI commands for task monitoring and control
  - `gitlab-mirror task list [--type=pull|push] [--status=<s>]` - List tasks
  - `gitlab-mirror task show <project-key>` - Show task details
  - `gitlab-mirror task retry <project-key>` - Retry failed task
  - `gitlab-mirror task stats` - Show task statistics

- [ ] T7.3: Add CLI commands for scheduler management
  - `gitlab-mirror scheduler status` - Show scheduler status
  - `gitlab-mirror scheduler trigger` - Manually trigger scheduling
  - `gitlab-mirror scheduler metrics` - Show scheduler metrics

- [ ] T7.4: Write CLI integration tests
  - Test all CLI commands
  - Test error handling and user feedback
  - Test output formatting (table, JSON)

---

## Module 8: Integration & Performance Testing

### ⏸️ End-to-End Integration Tests

- [ ] T8.1: End-to-end test - Full Pull sync flow (first sync)
  - Scenario: New project → discover → create target → clone → push → verify
  - Verification: All repos synced, no data loss, correct commit history

- [ ] T8.2: End-to-end test - Incremental sync with change detection
  - Scenario: Make changes to source → scheduler triggers → git ls-remote detects → sync → verify
  - Verification: Only changed repos synced, no-change repos skipped (<1s)

- [ ] T8.3: End-to-end test - Webhook triggered real-time sync
  - Scenario: Push to source → Webhook → auto-init or immediate schedule → sync → verify
  - Verification: Sync completes within 2 minutes of push

- [ ] T8.4: End-to-end test - Mixed Push and Pull projects
  - Scenario: Some projects using Push Mirror, others using Pull sync
  - Verification: Both types work correctly, no interference

- [ ] T8.5: End-to-end test - Error handling and auto-disable
  - Scenario: Network error → retry with backoff → 5 failures → auto-disable
  - Verification: Correct retry intervals, auto-disable after 5 failures, error logging

### ⏸️ Performance Tests

- [ ] T8.6: Performance test - 100 projects concurrent sync
  - Goal: Sync 100 projects concurrently
  - Metrics: Total time, throughput (projects/minute), resource usage
  - Target: Complete within 10-15 minutes (off-peak)

- [ ] T8.7: Performance test - Scheduler efficiency with 1000+ tasks
  - Goal: Scheduler handles 1000+ tasks efficiently
  - Metrics: Query time, scheduling overhead, memory usage
  - Target: Schedule 1000 tasks in <5 seconds

- [ ] T8.8: Load test - Webhook burst handling
  - Goal: Handle 100 concurrent Webhook requests
  - Metrics: Response time, rejection rate, processing time
  - Target: All requests <100ms response, no failures

---

## Module 9: Documentation & Deployment

### ⏸️ Documentation Tasks

- [ ] T9.1: Update API documentation with Pull sync endpoints
  - OpenAPI/Swagger spec
  - Request/response examples
  - Error code reference

- [ ] T9.2: Update CLI documentation with new commands
  - Command reference
  - Usage examples
  - Best practices

- [ ] T9.3: Create Pull sync user guide
  - Concepts: Pull sync vs Push Mirror
  - Setup guide: Configuration, discovery, scheduling
  - Operations guide: Monitoring, troubleshooting, maintenance
  - Performance tuning

### ⏸️ Deployment Tasks

- [ ] T9.4: Update Docker deployment configuration
  - Update docker-compose.yml with new configs
  - Environment variables for Pull sync
  - Volume mounts for local repos
  - Resource limits and health checks

- [ ] T9.5: Create migration guide from Push to Pull sync
  - When to use Pull sync vs Push Mirror
  - Migration steps
  - Rollback procedure
  - Troubleshooting common issues

---

## Next Steps

1. **Immediate**: Fix failing scheduler tests (T4.6)
2. **Short-term**: Complete Module 4, update task document (T4.7)
3. **Medium-term**: Implement Module 5 (Webhook) - Critical for real-time sync
4. **Long-term**: Complete Modules 6-9 for production readiness

---

## Notes

- **Testing Strategy**: Fix unit tests before moving to integration tests
- **Incremental Approach**: Complete and verify each module before proceeding
- **Documentation**: Update task documents after each module completion
- **Git Commits**: Commit after each major task with descriptive messages
- **Code Review**: Ensure code quality and test coverage before marking complete
