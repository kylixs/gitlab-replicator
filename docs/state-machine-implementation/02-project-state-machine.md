# 模块 2: 项目状态机 (Project State Machine)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 实现项目状态机，管理项目从 discovered 到 deleted 的完整生命周期，支持自动和手动状态转换。

**预计时间**: 2天

---

## 参考文档

- [状态机设计文档](../state-machine-design.md)
  - [项目状态定义](../state-machine-design.md#11-状态定义)
  - [状态转换图](../state-machine-design.md#12-状态转换图)
  - [状态转换规则](../state-machine-design.md#13-状态转换规则)

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

### T2.1 完善现有 ProjectStateMachine 状态定义
**状态**: ⏸️ 待处理 (Pending)
**依赖**: 无

**任务目标**:
完善现有的 ProjectStateMachine 类，统一状态常量定义，添加 DISCOVERED 和 DELETED 状态。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/service/state/ProjectStateMachine.java`
- `server/src/main/java/com/gitlab/mirror/server/entity/SyncProject.java`

**核心改造**:

1. **统一 ProjectStateMachine.Status 常量**
   - 保留现有：`DISCOVERED`, `INITIALIZING`, `ACTIVE`, `ERROR`, `SOURCE_MISSING`, `DISABLED`, `DELETED`
   - 确保与 SyncProject.SyncStatus 保持一致
   - 移除 SyncProject 中过时的状态（如 `PENDING`, `TARGET_CREATED` 等）

2. **完善状态转换方法**
   - 保留现有方法：`toInitializing()`, `toActive()`, `onSyncSuccess()`, `onSyncFailure()`
   - 补充文档注释，说明每个转换的触发条件
   - 添加状态转换验证逻辑

3. **新增辅助方法**
   - `canTransition(from, to)` - 判断转换是否合法
   - `getAvailableActions(currentStatus)` - 获取当前状态下可用的操作

**关键点**:
- 保持现有方法签名不变，确保兼容性
- 状态常量使用统一的命名空间（ProjectStateMachine.Status）
- 所有状态转换都要记录日志
- 不合法的转换记录警告日志但不抛异常

**验收标准**:
- ProjectStateMachine 包含完整的 7 种状态常量
- 现有状态转换逻辑正确
- 新增辅助方法功能正常
- 编写并通过单元测试验证状态转换规则

**提交**: `refactor(state): refine ProjectStateMachine status definitions`

---

### T2.2 清理 SyncProject 实体的过时状态常量
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T2.1

**任务目标**:
清理 SyncProject.SyncStatus 中的过时状态常量，统一使用 ProjectStateMachine.Status 定义。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/entity/SyncProject.java`
- `sql/migrations/005_cleanup_project_status.sql`

**核心改造**:

1. **SyncProject.SyncStatus 清理**
   - 移除过时常量：`PENDING`, `TARGET_CREATED`, `MIRROR_CONFIGURED`, `FAILED`, `SOURCE_NOT_FOUND`
   - 保留/新增：`DISCOVERED`, `INITIALIZING`, `ACTIVE`, `ERROR`, `SOURCE_MISSING`, `DISABLED`, `DELETED`
   - 添加文档注释说明每个状态的含义

2. **数据库迁移脚本**
   - 更新现有数据的状态值：
     - `pending` → `active` (如果目标项目已创建) 或 `discovered` (未创建)
     - `target_created` → `initializing`
     - `mirror_configured` → `active`
     - `failed` → `error`
     - `source_not_found` → `source_missing`
   - 迁移脚本支持幂等执行

3. **代码中硬编码状态值的替换**
   - 全局搜索 `"pending"`, `"failed"` 等字符串
   - 替换为 `ProjectStateMachine.Status.XXX` 常量

**关键点**:
- 迁移脚本要在生产环境测试前充分验证
- 保持 `enabled` 和 `error_message` 字段不变（已存在）
- 替换代码中的硬编码字符串，使用常量

**验收标准**:
- SyncProject.SyncStatus 只包含新定义的 7 种状态
- 数据库迁移脚本成功执行，数据正确转换
- 代码中不再使用过时的状态值
- 现有功能测试通过
- 编写并通过集成测试验证状态迁移

**提交**: `refactor(entity): cleanup obsolete SyncProject status constants`

---

### T2.3 集成状态机到同步流程
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T2.2

**任务目标**:
在同步执行流程中集成项目状态机，根据同步结果自动转换项目状态。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/service/PullSyncExecutorService.java`
- `server/src/main/java/com/gitlab/mirror/server/service/monitor/UnifiedProjectMonitor.java`

**核心改造**:

1. **PullSyncExecutorService 集成状态机**
   - 首次同步成功：`INITIALIZING` → `ACTIVE`
   - 初始化失败（连续3次）：`INITIALIZING` → `ERROR`
   - 连续同步失败（≥5次）：`ACTIVE` → `ERROR`
   - 调用 `projectStateMachine.transition()` 执行转换

2. **UnifiedProjectMonitor 检测状态变化**
   - 监控连续失败次数
   - 达到阈值时触发状态转换
   - 记录状态变化事件到 sync_event 表

3. **异常处理**
   - 捕获状态转换异常
   - 记录到日志，不影响同步流程继续
   - 发送告警通知（可选）

**关键点**:
- 状态转换必须在事务中执行
- 失败次数统计基于 SyncTask 的 consecutive_failures 字段
- 状态转换前检查当前状态，避免重复转换
- 添加详细日志记录状态变化原因

**验收标准**:
- 首次同步成功后项目状态变为 ACTIVE
- 连续失败达到阈值后状态变为 ERROR
- 状态转换事件正确记录
- 状态转换不影响同步流程的正常执行
- 编写并通过集成测试验证状态自动转换

**提交**: `feat(sync): integrate project state machine into sync flow`

---

### T2.4 添加项目状态管理 API
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T2.3

**任务目标**:
添加 REST API 端点，支持用户手动管理项目状态（启用/禁用/重新启用/删除）。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/controller/SyncController.java`
- `server/src/main/java/com/gitlab/mirror/server/service/ProjectStateService.java`

**API端点列表**:

1. **POST /api/sync/projects/{id}/enable** - 启用项目
   - 触发转换：`DISABLED` → `ACTIVE` 或 `ERROR` → `ACTIVE`
   - 请求体：无
   - 响应：`ApiResponse<Void>`

2. **POST /api/sync/projects/{id}/disable** - 禁用项目
   - 触发转换：`ACTIVE` → `DISABLED`
   - 请求体：无
   - 响应：`ApiResponse<Void>`

3. **POST /api/sync/projects/{id}/delete** - 删除项目（逻辑删除）
   - 触发转换：`*` → `DELETED`
   - 请求体：无
   - 响应：`ApiResponse<Void>`

4. **GET /api/sync/projects/{id}/status** - 查询项目状态
   - 响应：项目状态详情（包含可用的状态转换操作）

**核心逻辑**:

1. **ProjectStateService**
   - `enableProject(syncProjectId)` - 启用项目
   - `disableProject(syncProjectId)` - 禁用项目
   - `deleteProject(syncProjectId)` - 删除项目
   - `getProjectStatusDetail(syncProjectId)` - 获取状态详情

**关键点**:
- 使用 `@PostMapping` 和 `@PathVariable` 注解
- 调用 ProjectStateMachine 执行状态转换
- 验证状态转换的合法性，返回友好错误信息
- 记录操作审计日志

**验收标准**:
- 启用/禁用/删除 API 正常工作
- 非法操作返回 400 错误和清晰的错误信息
- 状态转换成功后立即生效
- 操作记录到审计日志
- 编写并通过 API 集成测试

**提交**: `feat(api): add project state management endpoints`

---

### T2.5 前端集成项目状态管理功能
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T2.4

**任务目标**:
在前端项目列表和详情页面添加项目状态管理功能（启用/禁用/删除），根据项目状态显示可用操作。

**文件路径**:
- `web-ui/src/components/project-detail/ProjectActions.vue`
- `web-ui/src/views/Projects.vue`
- `web-ui/src/api/projects.ts`

**核心功能**:

1. **ProjectActions 组件**
   - 根据项目状态显示可用操作按钮
   - ACTIVE 状态：显示"禁用"、"删除"
   - DISABLED 状态：显示"启用"、"删除"
   - ERROR 状态：显示"重新启用"、"删除"
   - 操作前显示确认对话框

2. **Projects 列表页增强**
   - 显示项目状态徽章（使用颜色区分）
   - 添加状态过滤器（全部/活跃/已禁用/错误）
   - 支持批量操作（批量启用/禁用）

3. **API 客户端**
   - `enableProject(id)` - 调用启用 API
   - `disableProject(id)` - 调用禁用 API
   - `deleteProject(id)` - 调用删除 API

**关键点**:
- 使用 Element Plus 的 Dropdown 和 Popconfirm 组件
- 操作成功后刷新项目列表
- 显示操作成功/失败的通知消息
- 删除操作需要二次确认

**验收标准**:
- 项目列表正确显示项目状态
- 启用/禁用/删除操作正常工作
- 操作按钮根据状态正确显示/隐藏
- 状态过滤器功能正常
- 批量操作功能正常
- 编写并通过 E2E 测试验证状态管理功能

**提交**: `feat(ui): add project state management UI`

---

## 模块验收

**验收检查项**:
1. 项目状态机正确实现 6 种状态和所有转换规则
2. SyncProject 实体和数据库表包含状态字段
3. 同步流程根据结果自动转换项目状态
4. API 支持手动管理项目状态
5. 前端正确展示项目状态并提供管理功能
6. 所有单元测试、集成测试、E2E 测试通过

**完成标志**: 所有任务状态为 ✅，模块状态更新为 ✅ 已完成

---
