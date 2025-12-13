# 模块 5: Webhook 准实时同步 (Webhook Real-time Sync)

**目标**: 实现 GitLab Push Webhook 接收和处理，实现准实时同步。

**预计时间**: 1-2天

---

## 任务清单

### T5.1 Webhook Controller 实现
**依赖**: 模块2 - 项目发现扩展, 模块4 - 统一任务调度器

**任务目标**:
- 创建 `WebhookController` 接收 GitLab Push 事件
- 实现 Secret Token 验证
- 解析 Webhook 请求体
- 实现快速响应（异步处理）
  - 参考 [PULL_SYNC_DESIGN.md - Webhook 准实时同步](../PULL_SYNC_DESIGN.md#流程-5-webhook-准实时同步)

**核心实现**:
```java
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {
    @PostMapping("/gitlab/push")
    public ResponseEntity<WebhookResponse> handlePushEvent(
            @RequestHeader("X-Gitlab-Token") String token,
            @RequestBody GitLabPushEvent event) {
        // 1. 验证 Secret Token
        // 2. 解析项目路径
        // 3. 异步处理（防止阻塞 GitLab）
        // 4. 立即返回 202 Accepted
    }
}
```

**验收标准**:
- Webhook 端点正确接收请求
- Secret Token 验证生效
- 请求体正确解析
- 快速响应（<100ms）
- 异步处理生效

**测试要求**:
- 测试 Secret Token 验证
- 测试 Webhook 请求解析
- 测试响应时间
- 测试异步处理
- 测试错误 Token 拒绝

**提交**: `feat(webhook): add webhook controller with authentication`

---

### T5.2 Webhook 事件处理服务
**依赖**: T5.1

**任务目标**:
- 创建 `WebhookEventService` 处理 Push 事件
- 实现项目自动初始化
- 实现防抖逻辑（2分钟）
- 触发立即调度

**核心逻辑**:
```java
@Service
public class WebhookEventService {
    @Async
    public void handlePushEvent(GitLabPushEvent event) {
        String projectKey = event.getProject().getPathWithNamespace();

        // 1. 检查项目是否已存在
        SyncProject project = findProject(projectKey);

        if (project == null) {
            // 2. 自动初始化项目
            project = initializeProject(event);
        }

        // 3. 防抖检查：最近成功同步 < 2分钟则忽略
        SyncTask task = getTask(project.getId());
        if (shouldDebounce(task)) {
            log.debug("Debounce: recent sync < 2min, ignored");
            return;
        }

        // 4. 更新 next_run_at=NOW 触发立即调度
        updateTaskForImmediateSchedule(task);

        // 5. 记录事件
        recordWebhookEvent(project, event);
    }
}
```

**验收标准**:
- 项目自动初始化成功
- 防抖逻辑正确（2分钟内忽略）
- next_run_at 正确更新为 NOW
- 事件正确记录
- 异步处理不阻塞

**测试要求**:
- 测试新项目自动初始化
- 测试已存在项目处理
- 测试防抖逻辑
- 测试立即调度触发
- 测试并发 Webhook 处理

**提交**: `feat(webhook): implement push event processing with debounce`

---

### T5.3 项目自动初始化逻辑
**依赖**: T5.2

**任务目标**:
- 从 Webhook 数据提取项目信息
- 创建完整的项目配置和任务
- 复用 ProjectDiscoveryService 逻辑
- 事务一致性保证

**初始化流程**:
```java
private SyncProject initializeProject(GitLabPushEvent event) {
    return transactionTemplate.execute(status -> {
        // 1. 创建 SYNC_PROJECT
        SyncProject project = new SyncProject();
        project.setProjectKey(event.getProject().getPathWithNamespace());
        project.setSyncMethod(SyncMethod.PULL_SYNC);
        project.setSyncStatus(SyncStatus.PENDING);
        project.setEnabled(true);
        syncProjectMapper.insert(project);

        // 2. 创建 SOURCE_PROJECT_INFO（从 Webhook 数据）
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setSyncProjectId(project.getId());
        sourceInfo.setGitlabProjectId(event.getProject().getId());
        sourceInfo.setPathWithNamespace(event.getProject().getPathWithNamespace());
        sourceInfo.setName(event.getProject().getName());
        sourceInfo.setVisibility(event.getProject().getVisibility());
        // ... 其他字段
        sourceProjectInfoMapper.insert(sourceInfo);

        // 3. 创建 PULL_SYNC_CONFIG
        PullSyncConfig config = pullSyncConfigService.initializeConfig(
            project.getId(), project.getProjectKey());

        // 4. 创建 SYNC_TASK
        SyncTask task = new SyncTask();
        task.setSyncProjectId(project.getId());
        task.setTaskType(TaskType.PULL);
        task.setTaskStatus(TaskStatus.WAITING);
        task.setNextRunAt(Instant.now()); // 立即调度
        syncTaskMapper.insert(task);

        return project;
    });
}
```

**验收标准**:
- 从 Webhook 数据正确提取字段
- 完整创建所有相关记录
- 事务一致性保证
- 初始化失败正确回滚

**测试要求**:
- 测试完整初始化流程
- 测试 Webhook 数据解析
- 测试事务回滚
- 测试重复初始化处理
- 测试缺失字段处理

**提交**: `feat(webhook): implement project auto-initialization`

---

### T5.4 Webhook 配置和安全
**依赖**: T5.1

**任务目标**:
- 添加 Webhook 配置项
- 实现 Secret Token 管理
- 实现 IP 白名单（可选）
- 实现请求日志和审计

**配置项**:
```yaml
sync:
  pull:
    webhook:
      enabled: true                     # 启用 Webhook
      secret-token: ${WEBHOOK_SECRET}   # Secret Token（环境变量）
      debounce-seconds: 120             # 防抖时间：2分钟
      ip-whitelist:                     # IP 白名单（可选）
        - 10.0.0.0/8
        - 172.16.0.0/12
      log-request-body: false           # 是否记录请求体（调试用）
```

**安全措施**:
```java
// 1. Secret Token 验证
boolean verifyToken(String providedToken) {
    return secureEquals(expectedToken, providedToken);
}

// 2. IP 白名单验证（可选）
boolean verifyIpWhitelist(String remoteIp) {
    return ipWhitelist.isEmpty() || ipWhitelist.contains(remoteIp);
}

// 3. 请求速率限制（防止 DDoS）
@RateLimiter(key = "webhook", limit = 100, duration = 60)
public void handlePushEvent(...) { ... }
```

**验收标准**:
- Secret Token 正确验证
- IP 白名单生效（如配置）
- 速率限制生效
- 配置正确加载
- 环境变量替换生效

**测试要求**:
- 测试 Token 验证
- 测试 IP 白名单
- 测试速率限制
- 测试配置加载
- 测试安全性（错误 Token、非法 IP）

**提交**: `feat(webhook): add security and configuration`

---

### T5.5 Webhook 监控和日志
**依赖**: T5.2, T5.4

**任务目标**:
- 添加 Webhook 监控指标
- 实现详细的事件日志
- 实现告警机制
- 实现故障诊断工具

**监控指标**:
```java
- webhook_requests_total{status="accepted|ignored|rejected"}
- webhook_processing_duration_seconds
- webhook_initialization_total
- webhook_debounce_skipped_total
- webhook_errors_total{type="auth|parse|processing"}
```

**日志设计**:
```java
// Webhook 接收
log.info("Webhook received, projectKey={}, ref={}, commits={}",
    projectKey, event.getRef(), event.getCommits().size());

// 自动初始化
log.info("Project auto-initialized from webhook, projectKey={}, priority={}",
    projectKey, config.getPriority());

// 防抖跳过
log.debug("Webhook debounced, projectKey={}, lastSyncAt={}",
    projectKey, task.getLastRunAt());

// 触发调度
log.info("Immediate schedule triggered by webhook, projectKey={}, nextRunAt={}",
    projectKey, task.getNextRunAt());
```

**验收标准**:
- 监控指标正确采集
- 日志信息完整
- 告警规则生效
- 故障可快速定位

**测试要求**:
- 测试监控指标
- 验证日志输出
- 测试告警触发
- 测试故障诊断

**提交**: `feat(webhook): add monitoring and logging`

---

## 模块输出

- ✅ WebhookController 接收 GitLab Push 事件
- ✅ WebhookEventService 处理事件
- ✅ 项目自动初始化功能
- ✅ 防抖逻辑（2分钟）
- ✅ Secret Token 和 IP 白名单安全控制
- ✅ Webhook 监控和日志

---

## 关键决策

1. **异步处理**: Webhook 接收后立即返回，异步处理避免阻塞 GitLab
2. **自动初始化**: 新项目自动创建配置和任务，无需手动发现
3. **防抖保护**: 2分钟内不重复触发，避免频繁同步
4. **安全优先**: Secret Token + IP 白名单双重保护
5. **快速响应**: 响应时间 <100ms，不影响 GitLab

---

## Webhook 配置（GitLab 端）

在源 GitLab 项目中配置 Webhook：

```
URL: http://your-server/api/webhook/gitlab/push
Secret Token: <WEBHOOK_SECRET>
Trigger: Push events
SSL verification: Enable
```

---

## 时序图

```
GitLab               Webhook Controller    WebhookEventService    Scheduler
  |                         |                       |                  |
  |-- Push Event ---------> |                       |                  |
  |                         |                       |                  |
  |                         |-- Verify Token -----> |                  |
  |                         |                       |                  |
  |                         |-- Parse Event ------> |                  |
  |                         |                       |                  |
  |<-- 202 Accepted ------- |                       |                  |
  |                         |                       |                  |
  |                         |-- Async Process ----> |                  |
  |                         |                       |                  |
  |                         |                       |-- Check Project  |
  |                         |                       |-- Initialize (if needed)
  |                         |                       |-- Debounce Check |
  |                         |                       |-- Update next_run_at=NOW
  |                         |                       |                  |
  |                         |                       |                  |<-- Schedule
  |                         |                       |                  |    (next minute)
```

---

## 注意事项

1. **快速响应**: Webhook 必须快速响应，否则 GitLab 会重试
2. **幂等性**: 处理重复 Webhook（GitLab 可能重试）
3. **Token 安全**: Secret Token 存储在环境变量，不提交代码
4. **IP 白名单**: 生产环境建议配置 IP 白名单
5. **事务处理**: 自动初始化需要事务保证
6. **并发处理**: 同一项目多个 Push 事件并发处理需要防护
