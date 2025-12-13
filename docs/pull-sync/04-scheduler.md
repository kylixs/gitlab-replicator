# 模块 4: 统一任务调度器 (Unified Task Scheduler)

**状态**: ✅ 已完成 (Completed)

**目标**: 实现统一的任务调度器，同时调度 Push Mirror 轮询和 Pull 同步任务。

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

### T4.1 创建统一调度器核心
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块3 - Pull 同步执行器

**任务目标**:
- 创建 `UnifiedSyncScheduler` 调度器类
- 实现定时触发（每分钟）
- 实现峰谷时段判断
- 实现并发控制
  - 参考 [PULL_SYNC_DESIGN.md - Pull 任务调度](../PULL_SYNC_DESIGN.md#流程-2-pull-任务调度)

**核心方法**:
- `scheduleTask()` - 定时调度方法（通过 cron 触发）
  1. 判断当前时段（高峰/非高峰）
  2. 获取可用并发槽位
  3. 查询 waiting 状态任务
  4. 按优先级排序
  5. 更新状态: waiting → pending
  6. 提交到执行器
- `isPeakHours()` - 判断当前是否为高峰时段
- `getAvailableSlots()` - 获取可用的并发槽位数
- `queryPendingTasks(limit)` - 查询待调度的任务列表

**验收标准**:
- 定时任务正常触发
- 峰谷时段判断准确
- 并发槽位控制生效
- 任务状态更新正确（waiting → pending）
- 优先级排序正确

**测试要求**:
- 测试定时触发
- 测试峰谷时段判断
- 测试并发控制
- 测试优先级排序
- 测试状态更新

**提交**: `feat(scheduler): add unified task scheduler core`

---

### T4.2 Pull 任务调度逻辑
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.1

**任务目标**:
- 实现 Pull 任务查询和过滤
- 实现优先级间隔计算
- 实现 next_run_at 更新逻辑
- 提交任务到 PullSyncExecutor

**查询逻辑**:
- 查询条件：
  - 任务类型为 `pull`
  - 任务状态为 `waiting`
  - `next_run_at` 已到期（<= 当前时间）
  - 对应的 `pull_sync_config` 的 `enabled = true`
  - 连续失败次数 `< 5`
- 排序规则：
  - 按优先级排序：critical > high > normal > low
  - 相同优先级按 `next_run_at` 升序
- 限制返回数量

**next_run_at 计算**:
- `calculateNextRunTime()` - 成功后计算下次执行时间（按优先级）
  - CRITICAL: 10 分钟
  - HIGH: 30 分钟
  - NORMAL: 120 分钟
  - LOW: 360 分钟
- `calculateRetryTime()` - 失败后计算重试时间（指数退避）
  - 延迟时间 = 5 * 2^重试次数（分钟）

**验收标准**:
- 查询过滤条件正确
- 优先级排序生效
- 连续失败≥5次的任务不被调度
- enabled=false 的任务不被调度
- next_run_at 计算准确

**测试要求**:
- 测试任务查询和过滤
- 测试优先级排序
- 测试 next_run_at 计算
- 测试失败任务重试时间计算
- 测试自动禁用逻辑

**提交**: `feat(scheduler): implement pull task scheduling logic`

---

### T4.3 Push Mirror 轮询适配
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.1

**任务目标**:
- 将现有 Push Mirror 轮询逻辑适配到 SYNC_TASK 表
- 更新 Push Mirror 状态到 SYNC_TASK
- 保持现有轮询间隔（30秒）
- 事件记录适配

**适配要点**:
- `pollPushMirrorStatus()` - Push Mirror 轮询方法（定时触发）
  1. 查询所有 `push` 类型任务
  2. 调用 GitLab API 获取 Mirror 状态
  3. 对比状态变化
  4. 更新 SYNC_TASK 字段：
     - `last_sync_status`
     - `last_run_at`
     - `source_commit_sha`（从 Mirror API 获取）
     - `error_message`
  5. 记录 SYNC_EVENT

**验收标准**:
- Push Mirror 轮询正常工作
- SYNC_TASK 状态正确更新
- 事件正常记录
- 不影响现有 Push Mirror 功能

**测试要求**:
- 测试 Push Mirror 轮询
- 测试状态更新
- 测试事件记录
- 测试与原有功能的兼容性

**提交**: `feat(scheduler): adapt push mirror polling to SYNC_TASK`

---

### T4.4 任务执行器集成
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.2, T4.3, 模块3

**任务目标**:
- 创建任务执行线程池
- 实现任务提交和执行
- 实现执行状态监控
- 实现异常处理

**核心实现**:
- `submitTask(task)` - 提交任务到线程池执行
  - 如果是 PULL 任务，调用 `pullSyncExecutor.execute()`
  - Push 任务由轮询器处理，不需要执行
  - 返回 Future 对象用于监控
- `getActiveTaskCount()` - 获取当前活跃任务数
- `getAvailableThreads()` - 获取可用线程数

**线程池配置**:
- `core-pool-size`: 核心线程数（3）
- `max-pool-size`: 最大线程数（10）
- `queue-capacity`: 队列容量（50）
- `thread-name-prefix`: 线程名前缀（"sync-exec-"）

**验收标准**:
- 线程池正确配置
- 任务正确提交和执行
- 并发控制生效
- 异常正确处理
- 线程池监控指标可用

**测试要求**:
- 测试任务提交
- 测试并发执行
- 测试线程池满的场景
- 测试异常处理
- 测试线程池监控

**提交**: `feat(scheduler): integrate task executor with thread pool`

---

### T4.5 调度器监控和日志
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.4

**任务目标**:
- 添加调度器监控指标
- 实现详细的调度日志
- 实现性能统计
- 实现告警机制

**监控指标**:
- 调度统计：
  - `scheduled_tasks_total` - 已调度任务总数
  - `scheduled_tasks_by_type{type}` - 按类型分组（push/pull）
  - `scheduled_tasks_by_priority{priority}` - 按优先级分组
  - `active_sync_tasks` - 当前活跃任务数
  - `peak_hour_concurrent_limit` - 高峰期并发限制
  - `off_peak_concurrent_limit` - 非高峰期并发限制
- 性能统计：
  - `schedule_duration_seconds` - 调度耗时
  - `task_queue_size` - 任务队列大小

**日志设计**:
- 调度开始：记录是否高峰期、可用槽位数
- 任务调度：记录项目 key、任务类型、优先级
- 调度完成：记录已调度数量、跳过数量、耗时

**验收标准**:
- 监控指标正确采集
- 日志信息完整清晰
- 性能统计准确
- 告警规则生效

**测试要求**:
- 测试监控指标采集
- 验证日志输出
- 测试性能统计
- 测试告警触发

**提交**: `feat(scheduler): add monitoring and logging`

---

## 模块输出

- ✅ UnifiedSyncScheduler 统一调度器
- ✅ Pull 任务调度逻辑
- ✅ Push Mirror 轮询适配到 SYNC_TASK
- ✅ 任务执行线程池
- ✅ 调度器监控和日志

---

## 关键决策

1. **统一调度**: Push 和 Pull 任务使用同一个调度器框架
2. **峰谷调度**: 高峰期降低并发，非高峰期提升并发
3. **优先级排序**: critical > high > normal > low
4. **Push Mirror**: 保持独立轮询（30秒），只更新 SYNC_TASK 状态
5. **Pull 任务**: 调度器改变状态（waiting → pending），执行器执行

---

## 调度流程图

```
定时调度器（每分钟）
    ├─ 判断时段（高峰/非高峰）
    ├─ 计算可用槽位
    ├─ 查询待调度任务
    │   ├─ Pull 任务（status=waiting, next_run_at到期）
    │   └─ 过滤（enabled=true, failures<5）
    ├─ 按优先级排序
    ├─ 更新状态（waiting → pending）
    └─ 提交到执行器
```

---

## 配置示例

```yaml
sync:
  scheduler:
    cron: "0 * * * * ?"           # 每分钟触发
    default-interval: 3           # 默认间隔3分钟

  peak-hours: "9-18"               # 高峰时段
  peak-concurrent: 3               # 高峰并发
  off-peak-concurrent: 8           # 非高峰并发

  pull:
    interval:
      critical: 10
      high: 30
      normal: 120
      low: 360

  push:
    poll-interval: 30             # Push Mirror 轮询30秒

  executor:
    core-pool-size: 3
    max-pool-size: 10
```

---

## 注意事项

1. **状态一致性**: 确保任务状态更新的原子性
2. **并发控制**: 防止同一项目被重复调度
3. **线程池管理**: 合理配置线程池大小，避免资源耗尽
4. **异常处理**: 执行失败不影响调度器继续运行
5. **监控告警**: 及时发现调度异常和性能问题
6. **时区处理**: 峰谷时段判断需考虑时区
