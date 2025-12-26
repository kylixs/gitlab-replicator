# 模块 3: 任务状态机 (Task State Machine)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 实现任务状态机，管理同步任务的执行状态（waiting/scheduled/running/blocked），支持调度器和执行器的协同工作。

**预计时间**: 2天

---

## 参考文档

- [状态机设计文档](../state-machine-design.md)
  - [任务状态定义](../state-machine-design.md#21-状态定义)
  - [状态转换图](../state-machine-design.md#22-状态转换图)
  - [辅助状态字段](../state-machine-design.md#23-辅助状态字段)
  - [状态转换规则](../state-machine-design.md#24-状态转换规则)

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

### T3.1 创建任务状态枚举和状态机核心类
**状态**: ⏸️ 待处理 (Pending)
**依赖**: 无

**任务目标**:
创建任务状态枚举和状态机核心类，定义任务执行的状态转换规则。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/service/state/TaskStatus.java`
- `server/src/main/java/com/gitlab/mirror/server/service/state/LastSyncStatus.java`
- `server/src/main/java/com/gitlab/mirror/server/service/state/TaskStateMachine.java`

**核心类设计**:

1. **TaskStatus (枚举)**
   - `WAITING` - 等待中
   - `SCHEDULED` - 已调度
   - `RUNNING` - 运行中
   - `BLOCKED` - 阻塞

2. **LastSyncStatus (枚举)**
   - `SUCCESS` - 成功
   - `FAILED` - 失败
   - `SKIPPED` - 跳过

3. **TaskStateMachine (服务类)**
   - `scheduleTask(taskId)` - 调度任务（waiting → scheduled）
   - `startTask(taskId)` - 开始执行（scheduled → running）
   - `completeTask(taskId, result)` - 完成任务（running → waiting/blocked）
   - `blockTask(taskId, reason)` - 阻塞任务（running → blocked）
   - `resetTask(taskId)` - 重置任务（blocked → scheduled）

**状态转换规则**:
- `WAITING` → `SCHEDULED` （next_run_at 到达）
- `SCHEDULED` → `RUNNING` （执行器开始执行）
- `RUNNING` → `WAITING` （同步成功/失败可重试）
- `RUNNING` → `BLOCKED` （不可重试错误/失败≥5次）
- `BLOCKED` → `SCHEDULED` （用户手动重置）

**关键点**:
- 使用乐观锁（version 字段）防止并发冲突
- 状态转换必须更新相关时间字段（started_at/completed_at）
- 记录状态转换到日志和 sync_event 表
- 计算 next_run_at 时使用退避策略

**验收标准**:
- 正确定义 4 种任务状态和 3 种同步结果状态
- 状态转换规则符合设计文档
- 并发场景下状态转换安全
- 编写并通过单元测试验证状态机的所有转换路径

**提交**: `feat(state): add task status enum and state machine`

---

### T3.2 统一 SyncTask 状态常量和添加 BLOCKED 状态
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T3.1

**任务目标**:
统一 SyncTask.TaskStatus 常量定义，添加 BLOCKED 和 SCHEDULED 状态，添加乐观锁支持。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/entity/SyncTask.java`
- `sql/migrations/006_add_task_blocked_status.sql`

**核心改造**:

1. **SyncTask.TaskStatus 扩展**
   - 保留现有：`WAITING`, `PENDING`, `RUNNING`
   - 新增：`SCHEDULED` - 已调度（替代 PENDING）
   - 新增：`BLOCKED` - 已阻塞（需人工介入）
   - 建议：将 `PENDING` 标记为 `@Deprecated`，逐步替换为 `SCHEDULED`

2. **SyncTask 实体扩展**
   - 添加 `@Version` 注解的 `version` 字段（乐观锁）
   - `lastSyncStatus` 字段已存在，扩展枚举值：添加 `SKIPPED`

3. **数据库迁移脚本**
   - 添加 `version` 列，BIGINT，默认值 0
   - 添加索引：`idx_task_status_next_run (task_status, next_run_at)`
   - 更新现有数据：`pending` → `scheduled`（可选，保持兼容）

**关键点**:
- 使用 MyBatis-Plus 的 @Version 注解实现乐观锁
- 保持现有 `task_status` 字段不变，只扩展枚举值
- `lastSyncStatus` 已有 SUCCESS/FAILED，新增 SKIPPED
- 迁移脚本支持幂等执行

**验收标准**:
- SyncTask.TaskStatus 包含 5 种状态（包括 BLOCKED 和 SCHEDULED）
- version 字段正确配置乐观锁
- 数据库迁移脚本成功执行
- 乐观锁在并发更新时正常工作
- 编写并通过单元测试验证乐观锁功能

**提交**: `feat(entity): add BLOCKED status and optimistic lock to SyncTask`

---

### T3.3 改造调度器使用任务状态机
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T3.2

**任务目标**:
改造 UnifiedSyncScheduler，使用任务状态机管理任务调度，确保任务状态正确转换。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/scheduler/UnifiedSyncScheduler.java`

**核心改造**:

1. **查询可调度任务**
   - 查询条件：`task_status = 'WAITING'` AND `next_run_at <= NOW()`
   - 项目条件：`sync_status = 'ACTIVE'` AND `enabled = TRUE`
   - 限制数量：单次调度最多 10 个任务

2. **调度任务**
   - 遍历可调度任务
   - 调用 `taskStateMachine.scheduleTask(taskId)`
   - 状态转换：`WAITING` → `SCHEDULED`
   - 处理乐观锁冲突（重试机制）

3. **提交任务到线程池**
   - 使用 ThreadPoolExecutor 执行任务
   - 任务开始时调用 `taskStateMachine.startTask(taskId)`
   - 状态转换：`SCHEDULED` → `RUNNING`

**关键点**:
- 使用 `@Scheduled(fixedDelay = 30000)` 定时调度
- 防止同一任务被多次调度（使用乐观锁）
- 调度失败不影响其他任务
- 添加监控指标（调度数量、失败数量）

**验收标准**:
- 调度器正确查询可调度任务
- 任务状态正确转换（WAITING → SCHEDULED → RUNNING）
- 并发场景下无重复调度
- 调度失败有重试机制
- 编写并通过集成测试验证调度器功能

**提交**: `refactor(scheduler): use task state machine in scheduler`

---

### T3.4 改造执行器使用任务状态机
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T3.3

**任务目标**:
改造 PullSyncExecutorService，使用任务状态机管理任务执行状态和结果。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/service/PullSyncExecutorService.java`

**核心改造**:

1. **任务执行开始**
   - 调用 `taskStateMachine.startTask(taskId)`
   - 更新 `started_at` 时间
   - 状态转换：`SCHEDULED` → `RUNNING`

2. **任务执行完成（成功）**
   - 调用 `taskStateMachine.completeTask(taskId, SUCCESS)`
   - 更新 `completed_at`、`last_sync_status = 'SUCCESS'`
   - 重置 `consecutive_failures = 0`
   - 计算 `next_run_at = NOW() + interval`
   - 状态转换：`RUNNING` → `WAITING`

3. **任务执行失败（可重试）**
   - 调用 `taskStateMachine.completeTask(taskId, FAILED)`
   - 更新 `last_sync_status = 'FAILED'`
   - 增加 `consecutive_failures`
   - 计算 `next_run_at = NOW() + backoff`（指数退避）
   - 状态转换：`RUNNING` → `WAITING`

4. **任务执行失败（不可重试/达到阈值）**
   - 调用 `taskStateMachine.blockTask(taskId, reason)`
   - 更新 `last_sync_status = 'FAILED'`
   - 设置 `next_run_at = NULL`
   - 状态转换：`RUNNING` → `BLOCKED`

**关键点**:
- 使用 try-catch-finally 确保状态总能更新
- 退避策略：失败次数 * 5分钟，最大 1小时
- 阻塞原因记录到 error_message 字段
- 更新任务状态后发送事件通知

**验收标准**:
- 任务执行成功后状态正确更新
- 任务失败后根据失败次数正确处理
- 连续失败≥5次后任务被阻塞
- next_run_at 计算正确（包含退避策略）
- 编写并通过集成测试验证执行器状态转换

**提交**: `refactor(executor): use task state machine in executor`

---

### T3.5 添加任务状态管理 API
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T3.4

**任务目标**:
添加 REST API 端点，支持用户查看任务状态和手动重置阻塞任务。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/controller/SyncController.java`
- `server/src/main/java/com/gitlab/mirror/server/controller/dto/TaskStatusDTO.java`

**API端点列表**:

1. **GET /api/sync/projects/{id}/tasks** - 查询项目的任务列表
   - 响应：包含任务状态、上次同步结果、下次执行时间

2. **GET /api/sync/tasks/{taskId}** - 查询任务详情
   - 响应：完整的任务信息（包含执行历史）

3. **POST /api/sync/tasks/{taskId}/reset** - 重置阻塞任务
   - 触发转换：`BLOCKED` → `SCHEDULED`
   - 请求体：无
   - 响应：`ApiResponse<Void>`

4. **POST /api/sync/tasks/{taskId}/trigger** - 立即触发任务
   - 触发转换：`WAITING` → `SCHEDULED`
   - 设置 `next_run_at = NOW()`

**核心逻辑**:
- `resetTask()` - 调用 TaskStateMachine 重置任务
- `triggerTask()` - 设置 next_run_at 并调度任务
- `getTaskStatus()` - 查询任务状态详情

**关键点**:
- 只能重置 BLOCKED 状态的任务
- 重置后任务立即可被调度
- 记录操作审计日志
- 返回友好的错误信息

**验收标准**:
- 任务列表 API 正确返回任务状态
- 重置阻塞任务功能正常工作
- 立即触发任务功能正常工作
- 非法操作返回清晰的错误信息
- 编写并通过 API 集成测试

**提交**: `feat(api): add task state management endpoints`

---

### T3.6 前端展示任务状态和操作
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T3.5

**任务目标**:
在前端项目详情页面展示任务状态，支持重置阻塞任务和立即触发同步。

**文件路径**:
- `web-ui/src/components/project-detail/TaskStatusPanel.vue`
- `web-ui/src/components/project-detail/OverviewTab.vue`

**核心功能**:

1. **TaskStatusPanel 组件**
   - 显示任务状态徽章（waiting/scheduled/running/blocked）
   - 显示上次同步结果（success/failed/skipped）
   - 显示下次执行时间
   - 显示连续失败次数（如果 > 0）
   - 根据状态显示操作按钮

2. **操作按钮**
   - WAITING 状态：显示"立即同步"按钮
   - BLOCKED 状态：显示"重置"按钮
   - RUNNING 状态：显示"同步中..."（禁用状态）
   - 操作成功后刷新任务状态

3. **OverviewTab 集成**
   - 在概览页面嵌入 TaskStatusPanel 组件
   - 显示任务执行历史（最近 10 次）
   - 显示失败原因（如果有）

**关键点**:
- 使用 Element Plus 的 Tag 组件显示状态
- BLOCKED 状态使用红色警告样式
- 定时刷新任务状态（每 10 秒）
- 操作按钮使用确认对话框

**验收标准**:
- 任务状态正确显示
- 立即同步功能正常工作
- 重置阻塞任务功能正常工作
- 任务状态自动刷新
- UI 美观且符合设计规范
- 编写并通过 E2E 测试验证任务状态展示

**提交**: `feat(ui): display task status and actions`

---

## 模块验收

**验收检查项**:
1. 任务状态机正确实现 4 种状态和所有转换规则
2. SyncTask 实体包含状态字段，乐观锁正常工作
3. 调度器和执行器正确使用任务状态机
4. 任务执行失败后退避策略正确
5. API 支持查询任务状态和手动操作
6. 前端正确展示任务状态并提供操作功能
7. 所有单元测试、集成测试、E2E 测试通过

**完成标志**: 所有任务状态为 ✅，模块状态更新为 ✅ 已完成

---
