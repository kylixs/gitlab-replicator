# 模块 4: 统一任务调度器 (Unified Task Scheduler)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 实现统一的任务调度器，同时调度 Push Mirror 轮询和 Pull 同步任务。

**预计时间**: 2-3天

---

## 任务清单

### T4.1 创建统一调度器核心
**状态**: ⏸️ 待处理 (Pending)
**依赖**: 模块3 - Pull 同步执行器

**任务目标**:
- 创建 `UnifiedSyncScheduler` 调度器类
- 实现定时触发（每分钟）
- 实现峰谷时段判断
- 实现并发控制
  - 参考 [PULL_SYNC_DESIGN.md - Pull 任务调度](../PULL_SYNC_DESIGN.md#流程-2-pull-任务调度)

**核心方法**:
```java
@Component
public class UnifiedSyncScheduler {
    @Scheduled(cron = "${sync.scheduler.cron}")
    public void scheduleTask() {
        // 1. 判断当前时段（高峰/非高峰）
        // 2. 获取可用并发槽位
        // 3. 查询 waiting 状态任务
        // 4. 按优先级排序
        // 5. 更新状态: waiting → pending
        // 6. 提交到执行器
    }

    // 判断高峰时段
    boolean isPeakHours();

    // 获取可用槽位
    int getAvailableSlots();

    // 查询待调度任务
    List<SyncTask> queryPendingTasks(int limit);
}
```

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
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.1

**任务目标**:
- 实现 Pull 任务查询和过滤
- 实现优先级间隔计算
- 实现 next_run_at 更新逻辑
- 提交任务到 PullSyncExecutor

**查询逻辑**:
```java
// 查询待调度的 Pull 任务
SELECT * FROM sync_task
WHERE task_type = 'pull'
  AND task_status = 'waiting'
  AND next_run_at <= NOW()
  AND EXISTS (
      SELECT 1 FROM pull_sync_config
      WHERE sync_project_id = sync_task.sync_project_id
      AND enabled = true
  )
  AND consecutive_failures < 5
ORDER BY
  CASE pull_sync_config.priority
    WHEN 'critical' THEN 1
    WHEN 'high' THEN 2
    WHEN 'normal' THEN 3
    WHEN 'low' THEN 4
  END,
  next_run_at ASC
LIMIT ?;
```

**next_run_at 计算**:
```java
// 成功后计算下次执行时间（按优先级）
Instant calculateNextRunTime(Priority priority, Instant now) {
    int intervalMinutes = switch (priority) {
        case CRITICAL -> 10;
        case HIGH -> 30;
        case NORMAL -> 120;
        case LOW -> 360;
    };
    return now.plusMinutes(intervalMinutes);
}

// 失败后计算重试时间（指数退避）
Instant calculateRetryTime(int retryCount, Instant now) {
    int delayMinutes = 5 * (int) Math.pow(2, retryCount);
    return now.plusMinutes(delayMinutes);
}
```

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
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.1

**任务目标**:
- 将现有 Push Mirror 轮询逻辑适配到 SYNC_TASK 表
- 更新 Push Mirror 状态到 SYNC_TASK
- 保持现有轮询间隔（30秒）
- 事件记录适配

**适配要点**:
```java
@Scheduled(fixedDelayString = "${sync.push.poll-interval}")
public void pollPushMirrorStatus() {
    // 1. 查询所有 push 类型任务
    // 2. 调用 GitLab API 获取 Mirror 状态
    // 3. 对比状态变化
    // 4. 更新 SYNC_TASK 字段:
    //    - last_sync_status
    //    - last_run_at
    //    - source_commit_sha (from mirror API)
    //    - error_message
    // 5. 记录 SYNC_EVENT
}
```

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
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.2, T4.3, 模块3

**任务目标**:
- 创建任务执行线程池
- 实现任务提交和执行
- 实现执行状态监控
- 实现异常处理

**核心实现**:
```java
@Component
public class TaskExecutorService {
    private final ThreadPoolTaskExecutor executor;

    // 提交任务执行
    public Future<Void> submitTask(SyncTask task) {
        return executor.submit(() -> {
            if (task.getTaskType() == TaskType.PULL) {
                pullSyncExecutor.execute(task);
            }
            // Push 任务由轮询器处理，不需要执行
        });
    }

    // 监控执行状态
    public int getActiveTaskCount();
    public int getAvailableThreads();
}
```

**线程池配置**:
```yaml
sync:
  executor:
    core-pool-size: 3
    max-pool-size: 10
    queue-capacity: 50
    thread-name-prefix: "sync-exec-"
```

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
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.4

**任务目标**:
- 添加调度器监控指标
- 实现详细的调度日志
- 实现性能统计
- 实现告警机制

**监控指标**:
```java
// 调度统计
- scheduled_tasks_total
- scheduled_tasks_by_type{type="push|pull"}
- scheduled_tasks_by_priority{priority="critical|high|normal|low"}
- active_sync_tasks
- peak_hour_concurrent_limit
- off_peak_concurrent_limit

// 性能统计
- schedule_duration_seconds
- task_queue_size
```

**日志设计**:
```java
// 调度开始
log.info("Scheduler triggered, peak={}, availableSlots={}", isPeak, slots);

// 任务调度
log.debug("Task scheduled, projectKey={}, type={}, priority={}",
    task.getProjectKey(), task.getTaskType(), config.getPriority());

// 调度完成
log.info("Scheduler completed, scheduled={}, skipped={}, duration={}ms",
    scheduledCount, skippedCount, duration);
```

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
