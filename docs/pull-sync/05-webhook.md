# 模块 5: Webhook 准实时同步 (Webhook Real-time Sync)

**状态**: ✅ 已完成 (Completed) - Core functionality implemented

**目标**: 实现 GitLab Push Webhook 接收和处理，实现准实时同步。

**预计时间**: 1-2天

---

## ⚠️ 重要提醒：任务状态管理规范

**【必须】在开始处理下面的每个子任务前及后需要修改其任务状态：**

1. **开始任务前**：将任务状态从 `⏸️ 待处理 (Pending)` 修改为 `🔄 进行中 (In Progress)`
2. **完成任务后**：将任务状态修改为 `✅ 已完成 (Completed)` 或 `❌ 失败 (Failed)`
3. **更新位置**：在本文档对应任务的 `**状态**:` 行进行修改

**状态标记说明**：
- `⏸️ 待处理 (Pending)` - 任务未开始
- `🔄 进行中 (In Progress)` - 任务正在处理中
- `✅ 已完成 (Completed)` - 任务成功完成，测试通过
- `❌ 失败 (Failed)` - 任务失败，需要修复
- `⚠️ 阻塞 (Blocked)` - 任务被依赖阻塞

---

## 任务清单

### T5.1 Webhook Controller 实现
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块2 - 项目发现扩展, 模块4 - 统一任务调度器

**任务目标**:
- 创建 `WebhookController` 接收 GitLab Push 事件
- 实现 Secret Token 验证
- 解析 Webhook 请求体
- 实现快速响应（异步处理）
  - 参考 [PULL_SYNC_DESIGN.md - Webhook 准实时同步](../PULL_SYNC_DESIGN.md#流程-5-webhook-准实时同步)

**核心实现**:
- `WebhookController` - Webhook 接收控制器
- `handlePushEvent()` - 处理 GitLab Push 事件
  1. 从 Header 中验证 Secret Token（X-Gitlab-Token）
  2. 解析项目路径
  3. 异步处理事件（防止阻塞 GitLab）
  4. 立即返回 202 Accepted

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
**状态**: ✅ 已完成 (Completed)
**依赖**: T5.1

**任务目标**:
- 创建 `WebhookEventService` 处理 Push 事件
- 实现项目自动初始化
- 实现防抖逻辑（2分钟）
- 触发立即调度

**核心逻辑**:
- `WebhookEventService.handlePushEvent()` - 异步处理 Push 事件
  1. 从事件中提取项目路径（project.pathWithNamespace）
  2. 检查项目是否已存在
  3. 如果项目不存在，自动初始化项目（调用 `initializeProject()`）
  4. 防抖检查：如果最近成功同步 < 2分钟，则忽略本次事件
  5. 更新任务的 `next_run_at=NOW` 触发立即调度
  6. 记录 Webhook 事件到 SYNC_EVENT

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
**状态**: ✅ 已完成 (Completed)
**依赖**: T5.2

**任务目标**:
- 从 Webhook 数据提取项目信息
- 创建完整的项目配置和任务
- 复用 ProjectDiscoveryService 逻辑
- 事务一致性保证

**初始化流程**:
- `initializeProject()` - 从 Webhook 数据自动初始化项目（事务保证）
  1. 创建 `SYNC_PROJECT` 记录
     - `project_key`: 从 Webhook 提取
     - `sync_method`: 设置为 PULL_SYNC
     - `sync_status`: 设置为 PENDING
     - `enabled`: 设置为 true
  2. 创建 `SOURCE_PROJECT_INFO` 记录
     - 从 Webhook event.project 中提取字段
     - 包括：gitlab_project_id, path_with_namespace, name, visibility 等
  3. 创建 `PULL_SYNC_CONFIG` 记录
     - 调用 `pullSyncConfigService.initializeConfig()` 使用默认配置
  4. 创建 `SYNC_TASK` 记录
     - `task_type`: 设置为 PULL
     - `task_status`: 设置为 WAITING
     - `next_run_at`: 设置为当前时间（触发立即调度）
  5. 返回创建的 SyncProject 对象

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
**状态**: ⏸️ 待处理 (Pending) - Deferred to production phase
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
- `verifyToken()` - Secret Token 验证
  - 使用安全的字符串比较（防止时序攻击）
- `verifyIpWhitelist()` - IP 白名单验证（可选）
  - 如果未配置白名单，则允许所有 IP
  - 如果配置了白名单，则只允许白名单中的 IP
- 请求速率限制（防止 DDoS）
  - 使用 `@RateLimiter` 注解
  - 限制：100 次/分钟

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
**状态**: ⏸️ 待处理 (Pending) - Deferred to production phase
**依赖**: T5.2, T5.4

**任务目标**:
- 添加 Webhook 监控指标
- 实现详细的事件日志
- 实现告警机制
- 实现故障诊断工具

**监控指标**:
- `webhook_requests_total{status}` - Webhook 请求总数（按状态分组：accepted/ignored/rejected）
- `webhook_processing_duration_seconds` - Webhook 处理耗时
- `webhook_initialization_total` - 自动初始化项目总数
- `webhook_debounce_skipped_total` - 因防抖跳过的 Webhook 总数
- `webhook_errors_total{type}` - Webhook 错误总数（按类型分组：auth/parse/processing）

**日志设计**:
- Webhook 接收：记录项目 key、分支、提交数量
- 自动初始化：记录项目 key、优先级
- 防抖跳过：记录项目 key、最近同步时间
- 触发调度：记录项目 key、下次执行时间

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
- ✅ 基本 Secret Token 验证（开发模式）
- ✅ Webhook 事件记录
- ⏸️ IP 白名单和速率限制 (待实现)
- ⏸️ 详细监控指标 (待实现)

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
