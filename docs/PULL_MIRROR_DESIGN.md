# GitLab Pull Mirror 设计方案

## 1. 概述

### 1.1 方案目标

基于Git Pull机制实现GitLab项目的定期同步，通过智能调度策略控制同步过程，降低源GitLab服务器压力。

### 1.2 核心优势

- **压力可控**：源GitLab仅被动响应pull请求，压力等同于普通开发者操作
- **增量传输**：每次pull仅传输增量commits，网络和磁盘压力小
- **精确调度**：完全控制同步时机、优先级和并发度
- **灵活配置**：支持优先级分级、错峰调度、动态限流

### 1.3 适用场景

- 源项目数量：200+ 项目
- 目标GitLab：1个或多个
- 同步频率：根据项目优先级 5分钟到6小时不等
- 源GitLab压力要求：低于15% CPU使用率

---

## 2. 核心架构

### 2.1 系统组件

```
┌─────────────────────────────────────────────────────┐
│              Pull Scheduler Service                 │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────────┐      ┌──────────────────┐   │
│  │  Scheduling      │      │  Pull Executor   │   │
│  │  Engine          │─────→│                  │   │
│  │  - 优先级队列    │      │  - 并发控制      │   │
│  │  - 时间窗口      │      │  - 变更检测      │   │
│  │  - 任务分配      │      │  - Git操作       │   │
│  └──────────────────┘      └──────────────────┘   │
│           │                         │              │
│           ↓                         ↓              │
│  ┌──────────────────┐      ┌──────────────────┐   │
│  │  Strategy        │      │  Progress        │   │
│  │  Manager         │      │  Monitor         │   │
│  │  - 策略配置      │      │  - 任务状态      │   │
│  │  - 动态调整      │      │  - 成功率统计    │   │
│  │  - 错峰控制      │      │  - 错误追踪      │   │
│  └──────────────────┘      └──────────────────┘   │
│                                                     │
└─────────────────────────────────────────────────────┘
         │                            │
         ↓                            ↓
┌─────────────────┐          ┌─────────────────┐
│  Source GitLab  │          │  Target GitLab  │
│  (被动响应)      │          │  (执行pull)      │
└─────────────────┘          └─────────────────┘
```

### 2.2 数据模型

#### 核心实体

**PULL_SYNC_CONFIG** - Pull同步配置表
- sync_project_id (FK) - 关联同步项目
- priority - 优先级(critical/high/normal/low)
- sync_interval - 同步间隔(分钟)
- enabled - 是否启用
- max_retries - 最大重试次数
- retry_delay - 重试延迟(分钟)
- last_sync_at - 最后同步时间
- next_sync_at - 下次同步时间
- consecutive_failures - 连续失败次数
- created_at/updated_at

**PULL_SYNC_TASK** - Pull同步任务表
- sync_project_id (FK) - 关联同步项目
- task_status - 任务状态(pending/running/success/failed/skipped)
- scheduled_at - 调度时间
- started_at - 开始时间
- completed_at - 完成时间
- source_commit_sha - 源仓库commit SHA
- target_commit_sha - 目标仓库commit SHA
- has_changes - 是否有变更
- changes_count - 变更数量(commits数)
- error_message - 错误信息
- retry_count - 已重试次数
- created_at/updated_at

**PULL_SYNC_SCHEDULE** - 调度策略配置表
- strategy_name - 策略名称
- max_concurrent_pulls - 最大并发pull数
- enable_peak_avoidance - 是否启用错峰
- peak_hours_start - 高峰时段开始(小时)
- peak_hours_end - 高峰时段结束(小时)
- peak_max_concurrent - 高峰期最大并发
- off_peak_max_concurrent - 非高峰期最大并发
- enable_adaptive - 是否启用自适应调整
- enabled - 是否启用
- created_at/updated_at

---

## 3. 关键处理流程

### 3.1 调度引擎流程

#### 3.1.1 任务调度主流程

```
[定时调度触发] (每分钟)
     ↓
[加载调度策略]
     ↓
[查询待调度项目]
  - 条件1: enabled=true
  - 条件2: next_sync_at <= now()
  - 条件3: consecutive_failures < max_retries
     ↓
[按优先级排序]
  - critical 优先级最高
  - high 次之
  - normal 再次
  - low 最低
     ↓
[应用调度策略]
  - 检查当前并发数
  - 检查是否在高峰时段
  - 检查源GitLab负载(可选)
     ↓
[创建Pull任务]
  - 状态: pending
  - scheduled_at: now()
     ↓
[提交到执行器队列]
     ↓
[更新next_sync_at]
  - next_sync_at = now() + sync_interval
```

#### 3.1.2 优先级队列管理

```
[优先级定义]
┌──────────┬────────────┬──────────────┬────────────┐
│ 优先级   │ 同步间隔   │ 典型场景     │ 占比建议   │
├──────────┼────────────┼──────────────┼────────────┤
│ critical │ 5-10分钟   │ 核心服务     │ 5%         │
│ high     │ 15-30分钟  │ 重要服务     │ 15%        │
│ normal   │ 1-2小时    │ 普通项目     │ 50%        │
│ low      │ 6-24小时   │ 归档/工具    │ 30%        │
└──────────┴────────────┴──────────────┴────────────┘

[优先级调度规则]
1. 同一时刻多个项目到期
   - 优先调度critical级别
   - 同级别按last_sync_at升序

2. 并发槽位不足
   - 等待当前任务完成释放槽位
   - 高优先级任务排队等待
   - 低优先级任务延后调度

3. 连续失败处理
   - 失败3次: 降低优先级
   - 失败5次: 暂停调度
   - 需要手动干预恢复
```

### 3.2 Pull执行流程

#### 3.2.1 变更检测流程

```
[接收Pull任务]
     ↓
[更新任务状态: running]
     ↓
[Git操作: ls-remote]
  - 获取源仓库refs
  - 命令: git ls-remote <source-url> HEAD
  - 耗时: ~100-500ms
     ↓
[对比本地refs]
  - 读取目标仓库当前HEAD SHA
  - 对比源和目标SHA
     ↓
[判断是否有变更]
     ↓
  [SHA一致] ──→ [标记: skipped]
     │              ↓
     │          [记录: has_changes=false]
     │              ↓
     │          [更新last_sync_at]
     │              ↓
     │          [任务完成]
     ↓
  [SHA不一致]
     ↓
[执行Git Pull]
```

#### 3.2.2 Git Pull执行流程

```
[执行Git Pull操作]
     ↓
[克隆/更新本地镜像仓库]
  - 首次: git clone --mirror <source-url>
  - 后续: git remote update
     ↓
[推送到目标GitLab]
  - git push --mirror <target-url>
  - 包含所有分支和tags
     ↓
[记录同步结果]
  - source_commit_sha: 源仓库SHA
  - target_commit_sha: 目标仓库SHA
  - has_changes: true
  - changes_count: commit数量
     ↓
[更新任务状态]
  - task_status: success
  - completed_at: now()
     ↓
[重置失败计数]
  - consecutive_failures: 0
     ↓
[记录同步事件]
  - event_type: pull_sync_success
```

#### 3.2.3 错误处理流程

```
[Pull操作失败]
     ↓
[捕获错误信息]
  - Git错误输出
  - 网络错误
  - 认证错误
     ↓
[错误分类]
     ↓
  ┌────────────────────┬──────────────────┬────────────────┐
  │ 网络超时           │ 认证失败         │ 冲突/其他      │
  ├────────────────────┼──────────────────┼────────────────┤
  │ 可重试             │ 不可重试         │ 需人工处理     │
  │ retry_count++      │ 标记错误         │ 标记错误       │
  │ 计算下次重试时间   │ 暂停调度         │ 暂停调度       │
  └────────────────────┴──────────────────┴────────────────┘
     ↓
[更新任务状态]
  - task_status: failed
  - error_message: 详细错误信息
  - retry_count: 当前重试次数
     ↓
[更新配置表]
  - consecutive_failures++
     ↓
[判断是否继续重试]
     ↓
  [retry_count < max_retries]
     ↓
  [计算下次调度时间]
    next_sync_at = now() + retry_delay
     ↓
  [retry_count >= max_retries]
     ↓
  [暂停该项目调度]
    enabled = false
     ↓
  [发送告警通知]
```

### 3.3 调度策略控制流程

#### 3.3.1 错峰调度策略

```
[每次调度前检查]
     ↓
[判断当前时间]
     ↓
  [是否在高峰时段?]
    peak_hours_start ~ peak_hours_end
     ↓
  ┌─────────────┬─────────────┐
  │ 是(高峰)    │ 否(非高峰)  │
  ├─────────────┼─────────────┤
  │ 应用高峰策略│ 应用常规策略│
  └─────────────┴─────────────┘
     ↓               ↓
[高峰期策略]    [非高峰策略]
- 降低并发度    - 正常并发度
- 仅调度        - 所有优先级
  critical/high - 批量调度
- 延后normal/low  - 充分利用资源
     ↓               ↓
[动态调整并发数]
  max_concurrent = 根据时段配置
```

#### 3.3.2 自适应调整策略

```
[启用自适应调整]
  enable_adaptive = true
     ↓
[监控源GitLab指标]
  - CPU使用率
  - 内存使用率
  - 网络吞吐量
  - Git进程数
     ↓
[评估系统负载]
     ↓
  ┌─────────────┬─────────────┬─────────────┐
  │ 负载过高    │ 负载正常    │ 负载很低    │
  │ CPU>80%     │ 40%<CPU<80% │ CPU<40%     │
  ├─────────────┼─────────────┼─────────────┤
  │ 降低并发    │ 保持当前    │ 提高并发    │
  │ concurrent-=1│ 不调整      │ concurrent+=1│
  │ 最小: 2     │             │ 最大: 10     │
  └─────────────┴─────────────┴─────────────┘
     ↓
[应用新的并发配置]
     ↓
[记录调整日志]
```

### 3.4 进度查看流程

#### 3.4.1 实时进度监控

```
[用户请求进度信息]
     ↓
[查询维度选择]
  - 全局概览
  - 按优先级统计
  - 按项目查询
  - 按时间范围
     ↓
[聚合统计数据]
     ↓
┌────────────────────────────────────────┐
│ 全局统计                               │
├────────────────────────────────────────┤
│ - 总项目数: 200                        │
│ - 已同步: 180 (90%)                    │
│ - 同步中: 5 (2.5%)                     │
│ - 等待中: 10 (5%)                      │
│ - 失败: 5 (2.5%)                       │
│ - 平均延迟: 15分钟                     │
│ - 成功率: 97.5%                        │
└────────────────────────────────────────┘
     ↓
┌────────────────────────────────────────┐
│ 按优先级统计                           │
├──────────┬─────────┬─────────┬─────────┤
│ 优先级   │ 项目数  │ 已同步  │ 平均延迟│
├──────────┼─────────┼─────────┼─────────┤
│ critical │ 10      │ 10(100%)│ 5分钟   │
│ high     │ 30      │ 29(97%) │ 12分钟  │
│ normal   │ 100     │ 92(92%) │ 25分钟  │
│ low      │ 60      │ 49(82%) │ 2小时   │
└──────────┴─────────┴─────────┴─────────┘
     ↓
[返回进度信息]
```

#### 3.4.2 任务详情查询

```
[查询特定项目]
  - 输入: project_key
     ↓
[获取同步配置]
  - priority, sync_interval, enabled
  - last_sync_at, next_sync_at
  - consecutive_failures
     ↓
[获取最近任务历史]
  - 最近10次任务
  - 包括成功和失败
     ↓
[格式化输出]
┌────────────────────────────────────────┐
│ 项目: devops/core-service             │
├────────────────────────────────────────┤
│ 优先级: critical                       │
│ 同步间隔: 10分钟                       │
│ 状态: 启用                             │
│ 最后同步: 2025-12-13 23:50:00         │
│ 下次同步: 2025-12-13 24:00:00         │
│ 连续失败: 0次                          │
│                                        │
│ 最近任务历史:                          │
│ ┌────────┬──────────┬─────────┬──────┐│
│ │ 时间   │ 状态     │ 耗时    │ 变更 ││
│ ├────────┼──────────┼─────────┼──────┤│
│ │ 23:50  │ success  │ 2.3s    │ 3个  ││
│ │ 23:40  │ skipped  │ 0.5s    │ 0个  ││
│ │ 23:30  │ success  │ 1.8s    │ 1个  ││
│ └────────┴──────────┴─────────┴──────┘│
└────────────────────────────────────────┘
```

### 3.5 手工触发流程

#### 3.5.1 单项目手工触发

```
[用户发起手工触发]
  - 输入: project_key
  - 可选: force=true (强制pull)
     ↓
[验证项目存在]
     ↓
[检查当前状态]
     ↓
  [是否有运行中任务?]
     ↓
  ┌──────────────┬──────────────┐
  │ 有(running)  │ 无           │
  ├──────────────┼──────────────┤
  │ 拒绝触发     │ 允许触发     │
  │ 返回错误信息 │              │
  └──────────────┴──────────────┘
     ↓
[创建手工触发任务]
  - task_status: pending
  - trigger_type: manual
  - trigger_user: 当前用户
     ↓
[立即提交执行]
  - 不受并发限制影响
  - 优先级最高
     ↓
[返回任务ID]
  - 用户可通过任务ID查询进度
```

#### 3.5.2 批量手工触发

```
[批量触发请求]
  - 输入: project_keys[] 或 priority
     ↓
[验证项目列表]
     ↓
[过滤运行中项目]
  - 排除已有running任务的项目
     ↓
[批量创建任务]
  - 每个项目创建一个任务
  - trigger_type: manual_batch
     ↓
[应用批量并发控制]
  - 批量任务也受max_concurrent限制
  - 避免同时触发过多任务
     ↓
[返回批量任务摘要]
  - 成功创建: N个
  - 已跳过(运行中): M个
  - 任务ID列表
```

### 3.6 错误信息处理流程

#### 3.6.1 错误信息记录

```
[Pull任务失败]
     ↓
[捕获完整错误上下文]
  - Git命令输出(stdout/stderr)
  - 异常堆栈
  - 系统环境信息
     ↓
[错误信息分类]
     ↓
┌──────────────────┬────────────────────────┐
│ 错误类型         │ 典型错误消息           │
├──────────────────┼────────────────────────┤
│ NETWORK_TIMEOUT  │ Connection timed out   │
│ AUTH_FAILED      │ Authentication failed  │
│ NOT_FOUND        │ Repository not found   │
│ PERMISSION_DENIED│ Access denied          │
│ CONFLICT         │ Merge conflict         │
│ DISK_FULL        │ No space left          │
│ UNKNOWN          │ 其他未分类错误         │
└──────────────────┴────────────────────────┘
     ↓
[存储结构化错误信息]
  {
    "error_type": "NETWORK_TIMEOUT",
    "error_message": "原始错误消息",
    "git_command": "执行的Git命令",
    "source_url": "源仓库URL",
    "target_url": "目标仓库URL",
    "timestamp": "发生时间",
    "retry_count": "当前重试次数"
  }
     ↓
[更新任务表]
  - error_message: JSON格式存储
```

#### 3.6.2 错误信息查询

```
[查询错误信息]
     ↓
[查询维度]
  - 按项目查询最近错误
  - 按错误类型统计
  - 按时间范围查询
     ↓
[错误统计视图]
┌─────────────────────────────────────────┐
│ 错误类型统计 (最近24小时)              │
├──────────────────┬──────────┬───────────┤
│ 错误类型         │ 发生次数 │ 影响项目  │
├──────────────────┼──────────┼───────────┤
│ NETWORK_TIMEOUT  │ 15       │ 8个项目   │
│ AUTH_FAILED      │ 3        │ 3个项目   │
│ NOT_FOUND        │ 2        │ 2个项目   │
└──────────────────┴──────────┴───────────┘
     ↓
[详细错误列表]
  - 时间倒序
  - 包含完整错误上下文
  - 支持按项目/类型过滤
```

#### 3.6.3 错误告警流程

```
[监控失败任务]
     ↓
[告警触发条件]
  - 单项目连续失败3次
  - 某错误类型1小时内发生10次
  - 整体失败率超过5%
     ↓
[生成告警事件]
  - 告警级别: warning/error/critical
  - 告警内容: 包含错误统计和受影响项目
     ↓
[发送通知]
  - 钉钉/企业微信
  - 邮件
  - 系统日志
     ↓
[记录告警历史]
```

---

## 4. REST API设计

### 4.1 调度策略管理

#### 获取调度策略
```
GET /api/pull/strategy
响应: 当前调度策略配置
```

#### 更新调度策略
```
PUT /api/pull/strategy
请求体:
{
  "max_concurrent_pulls": 5,
  "enable_peak_avoidance": true,
  "peak_hours_start": 9,
  "peak_hours_end": 18,
  "peak_max_concurrent": 3,
  "off_peak_max_concurrent": 8,
  "enable_adaptive": true
}
```

### 4.2 项目配置管理

#### 获取项目Pull配置
```
GET /api/pull/projects/{project_key}/config
响应:
{
  "priority": "high",
  "sync_interval": 15,
  "enabled": true,
  "max_retries": 3,
  "retry_delay": 5,
  "last_sync_at": "2025-12-13T23:50:00",
  "next_sync_at": "2025-12-13T00:05:00",
  "consecutive_failures": 0
}
```

#### 更新项目Pull配置
```
PUT /api/pull/projects/{project_key}/config
请求体:
{
  "priority": "critical",
  "sync_interval": 10,
  "enabled": true
}
```

#### 批量更新优先级
```
POST /api/pull/projects/batch-update-priority
请求体:
{
  "projects": ["devops/core", "devops/payment"],
  "priority": "critical"
}
```

### 4.3 进度查看

#### 全局进度统计
```
GET /api/pull/progress/overview
响应:
{
  "total_projects": 200,
  "synced": 180,
  "syncing": 5,
  "pending": 10,
  "failed": 5,
  "success_rate": 0.975,
  "average_delay_minutes": 15
}
```

#### 按优先级统计
```
GET /api/pull/progress/by-priority
响应:
{
  "critical": {
    "total": 10,
    "synced": 10,
    "success_rate": 1.0,
    "avg_delay_minutes": 5
  },
  "high": {...},
  "normal": {...},
  "low": {...}
}
```

#### 项目任务历史
```
GET /api/pull/projects/{project_key}/tasks?limit=10
响应:
{
  "items": [
    {
      "task_id": 12345,
      "status": "success",
      "scheduled_at": "2025-12-13T23:50:00",
      "started_at": "2025-12-13T23:50:01",
      "completed_at": "2025-12-13T23:50:03",
      "duration_seconds": 2.3,
      "has_changes": true,
      "changes_count": 3,
      "source_commit_sha": "abc123...",
      "target_commit_sha": "abc123..."
    },
    ...
  ],
  "total": 150,
  "page": 1,
  "size": 10
}
```

### 4.4 手工触发

#### 单项目触发
```
POST /api/pull/projects/{project_key}/trigger
请求体:
{
  "force": false  // 可选，是否强制pull
}
响应:
{
  "task_id": 12346,
  "status": "pending",
  "message": "Pull task created successfully"
}
```

#### 批量触发
```
POST /api/pull/trigger/batch
请求体:
{
  "project_keys": ["devops/core", "devops/payment"],
  // 或者按优先级
  "priority": "critical"
}
响应:
{
  "created_tasks": 8,
  "skipped_running": 2,
  "task_ids": [12347, 12348, ...]
}
```

#### 查询触发任务状态
```
GET /api/pull/tasks/{task_id}
响应:
{
  "task_id": 12346,
  "project_key": "devops/core",
  "status": "running",
  "progress": "Pulling changes...",
  "started_at": "2025-12-13T23:55:00"
}
```

### 4.5 错误信息查询

#### 获取项目错误历史
```
GET /api/pull/projects/{project_key}/errors?limit=10
响应:
{
  "items": [
    {
      "task_id": 12340,
      "occurred_at": "2025-12-13T20:00:00",
      "error_type": "NETWORK_TIMEOUT",
      "error_message": "Connection timed out after 30s",
      "git_command": "git remote update",
      "retry_count": 2
    },
    ...
  ]
}
```

#### 错误类型统计
```
GET /api/pull/errors/statistics?hours=24
响应:
{
  "time_range": "2025-12-12T23:55 - 2025-12-13T23:55",
  "total_errors": 20,
  "by_type": {
    "NETWORK_TIMEOUT": {
      "count": 15,
      "affected_projects": 8
    },
    "AUTH_FAILED": {
      "count": 3,
      "affected_projects": 3
    },
    "NOT_FOUND": {
      "count": 2,
      "affected_projects": 2
    }
  }
}
```

#### 失败项目列表
```
GET /api/pull/projects/failed?consecutive_failures_gte=3
响应:
{
  "items": [
    {
      "project_key": "devops/legacy-service",
      "consecutive_failures": 5,
      "last_error_type": "AUTH_FAILED",
      "last_error_message": "Invalid credentials",
      "last_failed_at": "2025-12-13T23:00:00",
      "enabled": false  // 已自动暂停
    },
    ...
  ]
}
```

---

## 5. CLI命令设计

### 5.1 调度策略命令

```bash
# 查看当前调度策略
gitlab-mirror pull strategy show

# 更新调度策略
gitlab-mirror pull strategy update \
  --max-concurrent=5 \
  --peak-hours=9-18 \
  --peak-concurrent=3

# 启用/禁用错峰调度
gitlab-mirror pull strategy set-peak-avoidance --enable

# 启用/禁用自适应调整
gitlab-mirror pull strategy set-adaptive --enable
```

### 5.2 项目配置命令

```bash
# 查看项目配置
gitlab-mirror pull config <project-key>

# 设置项目优先级
gitlab-mirror pull config <project-key> \
  --priority=critical \
  --interval=10

# 批量设置优先级
gitlab-mirror pull config batch-set-priority \
  --projects=devops/core,devops/payment \
  --priority=critical

# 启用/禁用项目同步
gitlab-mirror pull config <project-key> --enable
gitlab-mirror pull config <project-key> --disable
```

### 5.3 进度查看命令

```bash
# 查看全局进度
gitlab-mirror pull progress

# 查看指定优先级进度
gitlab-mirror pull progress --priority=critical

# 查看项目任务历史
gitlab-mirror pull tasks <project-key> --limit=10

# 实时监控(持续输出)
gitlab-mirror pull progress --watch
```

### 5.4 手工触发命令

```bash
# 触发单个项目
gitlab-mirror pull trigger <project-key>

# 强制触发(即使无变更)
gitlab-mirror pull trigger <project-key> --force

# 批量触发指定项目
gitlab-mirror pull trigger \
  --projects=devops/core,devops/payment

# 触发指定优先级所有项目
gitlab-mirror pull trigger --priority=critical

# 查看触发任务状态
gitlab-mirror pull task-status <task-id>
```

### 5.5 错误查询命令

```bash
# 查看项目错误历史
gitlab-mirror pull errors <project-key> --limit=10

# 查看错误统计
gitlab-mirror pull errors statistics --hours=24

# 查看所有失败项目
gitlab-mirror pull errors failed-projects

# 重置项目失败计数(恢复调度)
gitlab-mirror pull errors reset <project-key>
```

---

## 6. 定时调度配置

### 6.1 调度任务

```yaml
scheduled_jobs:
  # 主调度器 - 每分钟执行
  pull_scheduler:
    cron: "0 * * * * ?"  # 每分钟
    enabled: true

  # 进度统计 - 每5分钟
  progress_statistics:
    cron: "0 */5 * * * ?"
    enabled: true

  # 错误检测与告警 - 每10分钟
  error_monitoring:
    cron: "0 */10 * * * ?"
    enabled: true

  # 清理历史任务 - 每天凌晨2点
  task_cleanup:
    cron: "0 0 2 * * ?"
    retention_days: 30
    enabled: true
```

---

## 7. 监控指标

### 7.1 核心指标

```yaml
metrics:
  # 调度指标
  scheduling:
    - pull_tasks_scheduled_total      # 总调度任务数
    - pull_tasks_scheduled_per_minute # 每分钟调度任务数
    - pull_tasks_queued              # 队列中任务数
    - pull_tasks_running             # 运行中任务数

  # 执行指标
  execution:
    - pull_tasks_success_total       # 成功任务数
    - pull_tasks_failed_total        # 失败任务数
    - pull_tasks_skipped_total       # 跳过任务数(无变更)
    - pull_task_duration_seconds     # 任务耗时(直方图)

  # 变更指标
  changes:
    - pull_projects_with_changes     # 有变更的项目数
    - pull_commits_synced_total      # 同步的commit总数
    - pull_sync_success_rate         # 同步成功率

  # 错误指标
  errors:
    - pull_errors_by_type            # 按类型分组的错误数
    - pull_consecutive_failures      # 连续失败项目数
    - pull_retry_count               # 重试次数

  # 源GitLab压力
  source_gitlab:
    - pull_concurrent_connections    # 并发连接数
    - pull_bandwidth_bytes           # 传输字节数
```

---

## 8. 性能优化

### 8.1 变更检测优化

**策略**: 使用git ls-remote快速检查变更
- 耗时: 100-500ms vs 完整pull的5-30秒
- 无变更项目直接跳过
- 预期跳过率: 70-90%

### 8.2 并发控制优化

**动态并发调整**:
- 夜间时段(00:00-06:00): 并发8-10
- 工作时段(09:00-18:00): 并发3-5
- 根据源GitLab负载实时调整

### 8.3 批处理优化

**批量任务调度**:
- 同一批次的任务共享连接池
- 减少数据库查询次数
- 批量更新任务状态

### 8.4 缓存优化

**项目配置缓存**:
- 缓存项目优先级和同步间隔
- 减少数据库查询
- 缓存失效时间: 5分钟

---

## 9. 容错设计

### 9.1 重试机制

```yaml
retry_policy:
  max_retries: 3           # 最大重试次数
  retry_delay: 5           # 重试延迟(分钟)
  backoff_multiplier: 2    # 延迟倍增因子

  # 重试间隔
  # 第1次失败: 5分钟后重试
  # 第2次失败: 10分钟后重试
  # 第3次失败: 20分钟后重试
  # 超过3次: 暂停调度，需手动恢复
```

### 9.2 降级策略

**源GitLab高负载时**:
- 自动降低并发度
- 延长同步间隔
- 暂停低优先级项目

**连续失败处理**:
- 3次失败: 记录告警
- 5次失败: 自动禁用该项目
- 手动恢复: 重置失败计数

### 9.3 数据一致性

**事务保证**:
- 任务创建和配置更新使用事务
- 失败回滚，不影响其他任务

**幂等性**:
- 支持重复执行pull操作
- 任务ID唯一，避免重复调度

---

## 10. 总结

### 10.1 方案优势

1. **压力可控**: 源GitLab压力≈普通开发者操作
2. **灵活调度**: 支持优先级、错峰、自适应等多种策略
3. **完整监控**: 进度、错误、性能全方位监控
4. **易于维护**: 清晰的错误信息和手工干预机制
5. **可扩展**: 支持200+项目，可线性扩展

### 10.2 适用场景

- ✅ 大量项目(200+)需要同步
- ✅ 源GitLab压力敏感
- ✅ 需要灵活控制同步策略
- ✅ 可接受一定同步延迟(分钟级)

### 10.3 与Push Mirror对比

| 维度 | Pull方案 | Push Mirror |
|------|---------|-------------|
| 压力可控性 | ✅ 完全可控 | ⚠️ 被动响应 |
| 调度灵活性 | ✅ 高度灵活 | ❌ 无法控制 |
| 实时性 | ⚠️ 分钟级延迟 | ✅ 秒级实时 |
| 大规模支持 | ✅ 非常好 | ⚠️ 压力较大 |

### 10.4 推荐配置

**200个项目建议配置**:
```yaml
priority_distribution:
  critical: 10 (5%)   - 10分钟同步
  high: 30 (15%)      - 30分钟同步
  normal: 100 (50%)   - 2小时同步
  low: 60 (30%)       - 6小时同步

scheduling:
  max_concurrent: 5
  peak_hours: 9-18
  peak_concurrent: 3
  off_peak_concurrent: 8
  enable_adaptive: true

performance:
  expected_cpu: <15%
  expected_delay:
    critical: <10分钟
    high: <30分钟
    normal: <2小时
```
