# Webhook Integration Design (Simplified)

## Overview

实现GitLab webhook集成，实现实时同步触发，将同步延迟从5分钟（定时扫描）降低到1分钟以内。

**设计原则**: 简化实现，不保存webhook事件到数据库，直接触发同步任务。

## Architecture

```
GitLab Source
    ↓ (HTTP POST webhook)
Mirror Server - Webhook Endpoint (/api/webhooks/gitlab)
    ↓ (parse & validate)
Webhook Service
    ↓ (update sync_task: next_run_at=NOW, trigger_source='webhook')
Database (sync_task table updated)
    ↓ (poll every 5s)
Fast Sync Scheduler (webhook tasks have highest priority)
    ↓ (execute within 10s)
Pull Sync Executor
    ↓ (sync to target)
GitLab Target
```

**Key Points**:
- No webhook event storage in database
- Webhook directly updates existing `sync_task` record
- Scheduler prioritizes webhook-triggered tasks
- Fast response time: webhook → sync complete < 60s

## Webhook Events

### 1. Push Event (commit/branch changes)
```json
{
  "object_kind": "push",
  "project": {
    "id": 20,
    "path_with_namespace": "ai/test-rails-5"
  },
  "ref": "refs/heads/master",
  "before": "06ce2962...",
  "after": "3343a8ca...",
  "total_commits_count": 1
}
```

**Trigger Action**: Schedule immediate sync with `forceSync=false` (use change detection)

### 2. Tag Push Event
```json
{
  "object_kind": "tag_push",
  "project": {
    "id": 20,
    "path_with_namespace": "ai/test-rails-5"
  },
  "ref": "refs/tags/v1.0.0"
}
```

**Trigger Action**: Schedule immediate sync (tags need to be synced)

### 3. Project Create Event
```json
{
  "object_kind": "project_create",
  "project": {
    "id": 25,
    "path_with_namespace": "devops/new-project"
  }
}
```

**Trigger Action**: Trigger project discovery scan

## Fast Sync Scheduler

### Priority Levels

1. **webhook** (highest) - Triggered by webhook events, execute within 10 seconds
2. **critical** - Existing critical priority projects
3. **high** - Existing high priority projects
4. **normal** - Existing normal priority projects
5. **low** - Existing low priority projects

### Execution Strategy

```java
// Webhook-triggered tasks bypass normal interval checks
// They are scheduled to run immediately (next_run_at = now)

if (task.getTriggerSource() == "webhook") {
    task.setNextRunAt(Instant.now());
    task.setPriority("webhook");
} else {
    task.setNextRunAt(calculateNextRunTime(task));
}
```

### Scheduler Enhancement

```java
@Scheduled(fixedDelay = 5000) // Poll every 5 seconds (reduced from 10s)
public void schedulePullTasks() {
    // Priority order: webhook > critical > high > normal > low
    List<SyncTask> tasks = syncTaskMapper.selectPullTasksWithPriority(
        Instant.now(),
        MAX_CONSECUTIVE_FAILURES,
        availableSlots
    );

    // Execute webhook-triggered tasks first
    tasks.stream()
        .filter(t -> "webhook".equals(t.getTriggerSource()))
        .forEach(this::executeTask);

    // Then execute other tasks by priority
    tasks.stream()
        .filter(t -> !"webhook".equals(t.getTriggerSource()))
        .forEach(this::executeTask);
}
```

## Database Schema Changes (Simplified)

### Add webhook trigger tracking to sync_task only

```sql
-- Migration: 004_add_webhook_trigger_support.sql
ALTER TABLE sync_task
ADD COLUMN trigger_source VARCHAR(20) DEFAULT 'scheduled'
    COMMENT 'scheduled/webhook/manual/api - determines priority',
ADD COLUMN webhook_event_id BIGINT
    COMMENT 'Reserved for future use (currently unused)',
ADD INDEX idx_trigger_source_status_next_run (trigger_source, task_status, next_run_at);
```

**No webhook event storage table** - Events are processed immediately and discarded.

## API Endpoints

### POST /api/webhooks/gitlab

```java
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    @PostMapping("/gitlab")
    public ResponseEntity<?> handleGitLabWebhook(
        @RequestHeader("X-Gitlab-Event") String eventType,
        @RequestHeader("X-Gitlab-Token") String token,
        @RequestBody Map<String, Object> payload
    ) {
        // 1. Validate webhook token
        if (!validateWebhookToken(token)) {
            return ResponseEntity.status(401).build();
        }

        // 2. Save webhook event
        WebhookEvent event = saveWebhookEvent(eventType, payload);

        // 3. Process event (async)
        webhookProcessor.processAsync(event);

        return ResponseEntity.ok().build();
    }
}
```

## Webhook Registration

GitLab webhooks will be registered automatically when a project is added to sync:

```java
public void registerWebhook(Long gitlabProjectId, String projectPath) {
    String webhookUrl = properties.getServer().getPublicUrl() + "/api/webhooks/gitlab";
    String webhookToken = generateWebhookToken(projectPath);

    // Register webhook via GitLab API
    gitLabApiClient.createWebhook(gitlabProjectId, webhookUrl, webhookToken,
        Arrays.asList("push", "tag_push"));

    // Store token for validation
    webhookTokenService.save(projectPath, webhookToken);
}
```

## Performance Targets

| Metric | Target | Current |
|--------|--------|---------|
| Webhook → Sync Start | < 10 seconds | N/A |
| Webhook → Sync Complete | < 60 seconds | N/A |
| Webhook Processing | < 1 second | N/A |
| Scheduler Poll Interval | 5 seconds | 10 seconds |

## Testing Strategy

### Unit Tests
- Webhook payload parsing
- Token validation
- Event mapping to sync projects

### Integration Tests
1. **Commit Push Test**: Create commit → webhook → verify sync within 60s
2. **Branch Create Test**: Create branch → webhook → verify sync within 60s
3. **Branch Delete Test**: Delete branch → webhook → verify sync within 60s
4. **Tag Push Test**: Create tag → webhook → verify sync within 60s
5. **Project Create Test**: Create project → webhook → verify discovery within 60s

### Load Tests
- 100 concurrent webhook events
- Verify all syncs complete within SLA

## Security

1. **Token Validation**: Each project has unique webhook token
2. **IP Whitelist**: Only accept webhooks from GitLab server IPs
3. **Rate Limiting**: Max 100 webhooks/minute per project
4. **Payload Size Limit**: Max 1MB webhook payload

## Configuration

```yaml
gitlab-mirror:
  webhook:
    enabled: true
    token-secret: ${WEBHOOK_TOKEN_SECRET:change-me-in-production}
    rate-limit: 100 # requests per minute
    fast-sync:
      enabled: true
      max-delay-seconds: 60
      scheduler-interval-ms: 5000
```

## Rollout Plan

### Phase 1: Basic Implementation (Week 1)
- [ ] Database schema changes
- [ ] Webhook endpoint and token validation
- [ ] Basic event processing (push events only)

### Phase 2: Fast Sync Scheduler (Week 1-2)
- [ ] Priority queue implementation
- [ ] Scheduler optimization (5s polling)
- [ ] Webhook-triggered sync execution

### Phase 3: Webhook Registration (Week 2)
- [ ] Auto-register webhooks on project create
- [ ] Webhook management UI
- [ ] Token rotation support

### Phase 4: Testing & Monitoring (Week 2-3)
- [ ] Integration tests
- [ ] Performance testing
- [ ] Metrics and alerting

## Metrics

### Prometheus Metrics

```java
// Webhook event counter
webhook_events_total{event_type="push", status="processed"}

// Webhook processing duration
webhook_processing_duration_seconds{event_type="push"}

// Webhook to sync delay
webhook_to_sync_delay_seconds{project="ai/test-rails-5"}

// Fast sync execution time
fast_sync_duration_seconds{priority="webhook"}
```

## References

- [GitLab Webhook Documentation](https://docs.gitlab.com/ee/user/project/integrations/webhooks.html)
- [GitLab Webhook Events](https://docs.gitlab.com/ee/user/project/integrations/webhook_events.html)
