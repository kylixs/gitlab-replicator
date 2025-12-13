# 模块 7: CLI 客户端集成 (CLI Client Integration)

**状态**: ✅ 已完成 (Completed)

**目标**: 扩展 CLI 客户端，添加 Pull Sync 管理、任务监控和调度器控制命令。

**预计时间**: 2-3天

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

### T7.1 Pull Sync 管理命令
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块6 - REST API Integration

**任务目标**:
- 创建 `PullSyncCommand` 类
- 实现 Pull Sync 配置管理命令
- 支持列表查询、配置更新、启用/禁用

**CLI 命令**:
```bash
# 列表查询（支持过滤）
gitlab-mirror pull list
gitlab-mirror pull list --priority=high --enabled
gitlab-mirror pull list --page=2 --size=50

# 显示配置详情
gitlab-mirror pull show <project-id>

# 更新优先级
gitlab-mirror pull priority <project-id> <priority>
gitlab-mirror pull priority 123 critical

# 启用/禁用
gitlab-mirror pull enable <project-id>
gitlab-mirror pull disable <project-id>
```

**核心功能**:
- 解析命令行选项（--priority, --enabled, --page, --size）
- 调用对应的 REST API
- 格式化输出 JSON 结果

**输出格式**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 123,
        "projectKey": "group/project",
        "priority": "high",
        "enabled": true,
        "lastSyncAt": "2025-12-14T03:00:00Z",
        "consecutiveFailures": 0
      }
    ],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

**验收标准**:
- 所有子命令正常工作
- 支持选项解析和过滤
- 错误提示清晰
- JSON 输出格式化

**测试要求**:
- 测试所有子命令
- 测试选项解析
- 测试错误处理
- 测试输出格式

**提交**: `feat(cli): add Pull sync management commands`

---

### T7.2 任务监控命令
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块6 - REST API Integration

**任务目标**:
- 创建 `TaskCommand` 类
- 实现任务查询、重试、统计命令

**CLI 命令**:
```bash
# 列表查询（支持多维度过滤）
gitlab-mirror task list
gitlab-mirror task list --type=pull --status=waiting
gitlab-mirror task list --priority=high --enabled
gitlab-mirror task list --page=2 --size=50

# 显示任务详情
gitlab-mirror task show <task-id>

# 手动重试任务
gitlab-mirror task retry <task-id>

# 重置失败计数
gitlab-mirror task reset <task-id>

# 任务统计
gitlab-mirror task stats
```

**核心功能**:
- 多维度过滤（type, status, priority, enabled）
- 手动重试和重置失败计数
- 显示任务统计信息

**输出格式**:
```json
// 任务列表
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 456,
        "projectKey": "group/project",
        "taskType": "pull",
        "taskStatus": "waiting",
        "priority": "high",
        "nextRunAt": "2025-12-14T04:00:00Z",
        "consecutiveFailures": 0
      }
    ]
  }
}

// 任务统计
{
  "success": true,
  "data": {
    "totalTasks": 1000,
    "pullTasks": 600,
    "pushTasks": 400,
    "waitingTasks": 150,
    "criticalTasks": 50
  }
}
```

**验收标准**:
- 所有子命令正常工作
- 支持多维度过滤
- 重试和重置立即生效
- 统计信息准确

**测试要求**:
- 测试所有子命令
- 测试过滤组合
- 测试手动操作
- 测试错误处理

**提交**: `feat(cli): add task monitoring commands`

---

### T7.3 调度器管理命令
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块6 - REST API Integration

**任务目标**:
- 创建 `SchedulerCommand` 类
- 实现调度器状态查询、手动触发、指标查看

**CLI 命令**:
```bash
# 查看调度器状态
gitlab-mirror scheduler status

# 手动触发调度
gitlab-mirror scheduler trigger
gitlab-mirror scheduler trigger --type=pull

# 查看调度器指标
gitlab-mirror scheduler metrics
```

**核心功能**:
- 查询调度器状态和指标
- 手动触发调度（支持指定任务类型）

**输出格式**:
```json
// 调度器状态
{
  "success": true,
  "data": {
    "enabled": true,
    "isPeakHours": false,
    "peakConcurrency": 3,
    "offPeakConcurrency": 8,
    "activeTasksCount": 2,
    "queuedTasksCount": 45
  }
}

// 调度器指标
{
  "success": true,
  "data": {
    "totalScheduled": 10000,
    "pullTasksScheduled": 6000,
    "successfulExecutions": 9500,
    "failedExecutions": 500
  }
}
```

**验收标准**:
- 状态查询实时准确
- 手动触发立即执行
- 指标统计正确

**测试要求**:
- 测试状态查询
- 测试手动触发
- 测试指标查看
- 测试选项解析

**提交**: `feat(cli): add scheduler management commands`

---

### T7.4 CLI 集成测试
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T7.1, T7.2, T7.3

**任务目标**:
- 编写 CLI 命令的集成测试
- 测试所有命令和选项组合
- 测试错误处理和用户反馈

**测试用例**:
- Pull sync 命令测试（list/show/priority/enable/disable）
- Task 命令测试（list/show/retry/reset/stats）
- Scheduler 命令测试（status/trigger/metrics）
- 错误处理和输出格式测试

**验收标准**:
- 所有命令测试通过
- 错误处理完善
- 输出格式正确
- 帮助信息清晰

**测试要求**:
- 覆盖所有命令
- 测试选项解析
- 测试错误场景
- 测试输出格式

**提交**: `test(cli): add CLI integration tests`

---

## 模块输出

- ✅ PullSyncCommand - Pull sync 管理命令
- ✅ TaskCommand - 任务监控命令
- ✅ SchedulerCommand - 调度器管理命令
- ✅ ApiClient 简化方法（get/post/put 返回原始 JSON）
- ✅ OutputFormatter.printJson() - JSON 格式化输出
- ✅ 主 CLI 类更新（新增 3 个命令分类）
- ✅ Build Status: SUCCESS
- ⏸️ CLI 集成测试（待实现）

---

## 关键决策

1. **子命令设计**: 使用 `命令 子命令 [选项]` 三层结构
2. **JSON 输出**: 统一使用 JSON 格式输出，支持管道处理
3. **错误提示**: 清晰的错误信息和使用帮助
4. **选项解析**: 支持 `--key=value` 格式的选项
5. **API 简化**: 添加简单的 get/post/put 方法返回原始 JSON

---

## CLI 使用示例

### Pull Sync 管理

```bash
# 查看所有高优先级且启用的项目
$ gitlab-mirror pull list --priority=high --enabled

{
  "success": true,
  "data": {
    "items": [
      {
        "id": 123,
        "projectKey": "backend/api-service",
        "priority": "high",
        "enabled": true
      }
    ]
  }
}

# 将项目设为关键优先级
$ gitlab-mirror pull priority 123 critical
✓ Priority updated to: critical

# 禁用项目同步
$ gitlab-mirror pull disable 123
⚠ Pull sync disabled for project: 123
```

### 任务监控

```bash
# 查看所有等待中的 Pull 任务
$ gitlab-mirror task list --type=pull --status=waiting

# 手动重试失败任务
$ gitlab-mirror task retry 456
✓ Task scheduled for immediate retry: 456

# 查看任务统计
$ gitlab-mirror task stats
ℹ Task Statistics:
{
  "totalTasks": 1000,
  "pullTasks": 600,
  "criticalTasks": 50
}
```

### 调度器管理

```bash
# 查看调度器状态
$ gitlab-mirror scheduler status
ℹ Scheduler Status:
{
  "enabled": true,
  "isPeakHours": false,
  "activeTasksCount": 2,
  "queuedTasksCount": 45
}

# 手动触发 Pull 任务调度
$ gitlab-mirror scheduler trigger --type=pull
✓ Scheduler triggered successfully
```

---

## 注意事项

1. **环境变量**: 需要配置 `GITLAB_MIRROR_API_URL` 和 `GITLAB_MIRROR_TOKEN`
2. **JSON 解析**: 输出可通过 `jq` 等工具进一步处理
3. **错误处理**: 网络错误、认证失败等都有清晰提示
4. **帮助信息**: 每个命令都支持 `--help` 显示帮助
5. **并发安全**: CLI 可安全并发执行
