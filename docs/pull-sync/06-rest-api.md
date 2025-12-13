# 模块 6: REST API 集成 (REST API Integration)

**状态**: ✅ 已完成 (Completed) - Core endpoints implemented

**目标**: 实现 Pull Sync 的 REST API 端点，提供配置管理、任务监控和调度器控制功能。

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

### T6.1 Pull Sync 配置管理 API
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块1 - 数据模型扩展, 模块2 - 项目发现

**任务目标**:
- 创建 `PullSyncConfigController`
- 实现 Pull Sync 配置的 CRUD API
- 支持优先级更新和启用/禁用控制

**API 端点**:
```
GET    /api/pull-sync/config                      # 列表查询（支持过滤）
GET    /api/pull-sync/config/{projectId}          # 获取配置详情
PUT    /api/pull-sync/config/{projectId}/priority # 更新优先级
PUT    /api/pull-sync/config/{projectId}/enabled  # 启用/禁用
```

**核心功能**:
- 分页查询配置列表，支持按优先级、启用状态过滤
- 获取配置详情，包含项目信息和任务状态
- 更新优先级（critical/high/normal/low）
- 启用/禁用同步，同时更新 project 和 config

**DTO 设计**:
- `PullSyncConfigDTO` - 配置详情（含项目和任务元数据）
- `UpdatePriorityRequest` - 优先级更新请求
- `UpdateEnabledRequest` - 启用状态更新请求

**验收标准**:
- 所有端点正常工作
- 支持分页和过滤
- 优先级更新立即生效
- 启用/禁用同时更新 config 和 project
- 返回完整的配置和状态信息

**测试要求**:
- 测试列表查询和过滤
- 测试优先级更新（4种优先级）
- 测试启用/禁用功能
- 测试错误处理（项目不存在、无效优先级）
- 测试并发更新

**提交**: `feat(api): implement Pull sync config management API`

---

### T6.2 任务管理 API
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块3 - Pull Sync Executor, 模块4 - 统一调度器

**任务目标**:
- 创建 `TaskController`
- 实现任务查询、手动重试、失败重置功能
- 提供任务统计信息

**API 端点**:
```
GET    /api/tasks                        # 列表查询（支持多维度过滤）
GET    /api/tasks/{taskId}               # 获取任务详情
POST   /api/tasks/{taskId}/retry         # 手动重试任务
PUT    /api/tasks/{taskId}/reset-failures # 重置失败计数
GET    /api/tasks/stats                  # 任务统计
```

**核心功能**:
- 多维度过滤任务列表（类型、状态、优先级、启用状态）
- 手动重试任务（设置 next_run_at=NOW）
- 重置失败计数并重新启用项目
- 统计任务数量和优先级分布

**DTO 设计**:
- `TaskDTO` - 任务详情（含项目信息和 Pull 优先级）
- `TaskStatsDTO` - 任务统计（总数、类型分布、状态分布、优先级分布）

**验收标准**:
- 列表支持多维度过滤
- 手动重试立即生效
- 失败重置同时重启项目
- 统计信息准确

**测试要求**:
- 测试各种过滤组合
- 测试手动重试功能
- 测试失败重置功能
- 测试统计准确性
- 测试并发操作

**提交**: `feat(api): implement task management API`

---

### T6.3 调度器控制 API
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块4 - 统一调度器

**任务目标**:
- 创建 `SchedulerController`
- 实现调度器状态查询、手动触发和监控指标

**API 端点**:
```
GET    /api/scheduler/status    # 调度器状态
POST   /api/scheduler/trigger   # 手动触发调度
GET    /api/scheduler/metrics   # 调度器指标
```

**核心功能**:
- 查询调度器状态（启用状态、高峰检测、并发限制、活跃数）
- 手动触发调度（可选指定任务类型）
- 查询调度器指标（调度次数、成功/失败统计）

**DTO 设计**:
- `SchedulerStatusDTO` - 调度器状态（含高峰检测、并发限制、活跃数）
- `SchedulerMetricsDTO` - 调度器指标（调度次数、执行统计）
- `TriggerScheduleRequest` - 手动触发请求（可选任务类型）

**验收标准**:
- 状态信息实时准确
- 手动触发立即执行
- 指标统计正确

**测试要求**:
- 测试状态查询
- 测试手动触发
- 测试高峰/非高峰检测
- 测试指标准确性

**提交**: `feat(api): implement scheduler control API`

---

### T6.4 API 认证授权
**状态**: ⏸️ 待处理 (Pending) - Deferred to production phase
**依赖**: T6.1, T6.2, T6.3

**任务目标**:
- 实现 API 认证机制（JWT 或 API Key）
- 实现基于角色的访问控制（RBAC）
- 添加 API 调用审计日志

**认证方案**:
- JWT Token 或 API Key 认证
- 角色定义：
  - `ADMIN` - 全部权限（配置修改、调度器控制）
  - `OPERATOR` - 操作权限（任务重试、失败重置）
  - `VIEWER` - 只读权限（查询配置、任务、状态）
- 审计日志：记录用户、接口、参数、结果、耗时

**验收标准**:
- 未认证请求被拒绝（401）
- 权限不足被拒绝（403）
- 角色控制生效
- 审计日志完整

**测试要求**:
- 测试各种认证场景
- 测试角色权限
- 测试审计日志
- 测试 Token 过期处理

**提交**: `feat(api): add authentication and authorization`

---

### T6.5 API 集成测试
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T6.1, T6.2, T6.3

**任务目标**:
- 编写完整的 API 集成测试
- 测试所有端点和场景
- 测试错误处理和边界情况

**测试用例**:
- 完整 API 流程测试（查询、更新、重试、触发）
- 错误处理测试（404, 400, 500）
- 并发操作测试

**验收标准**:
- 所有端点测试通过
- 边界情况覆盖
- 错误处理验证
- 并发测试通过

**测试要求**:
- 覆盖所有 API 端点
- 测试各种过滤组合
- 测试错误响应
- 测试并发安全性

**提交**: `test(api): add API integration tests`

---

## 模块输出

- ✅ PullSyncConfigController - 配置管理 API
- ✅ TaskController - 任务管理 API
- ✅ SchedulerController - 调度器控制 API
- ✅ 12 个 DTO 类
- ✅ Build Status: SUCCESS
- ⏸️ API 认证授权（延后到生产阶段）
- ⏸️ API 集成测试（待实现）

---

## 关键决策

1. **统一响应格式**: 使用 `ApiResponse<T>` 包装所有响应
2. **分页支持**: 列表查询统一支持分页和过滤
3. **RESTful 设计**: 遵循 REST 规范，使用标准 HTTP 方法
4. **DTO 转换**: Controller 层负责 Entity → DTO 转换
5. **错误处理**: 统一异常处理，返回标准错误格式

---

## API 文档

### Pull Sync Config API

**列表查询**
```http
GET /api/pull-sync/config?priority=high&enabled=true&page=1&size=20

Response:
{
  "success": true,
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

**更新优先级**
```http
PUT /api/pull-sync/config/123/priority
Content-Type: application/json

{
  "priority": "critical"
}

Response:
{
  "success": true,
  "data": {
    "id": 123,
    "priority": "critical",
    ...
  }
}
```

### Task API

**列表查询**
```http
GET /api/tasks?type=pull&status=waiting&priority=high&page=1&size=20
```

**手动重试**
```http
POST /api/tasks/456/retry

Response:
{
  "success": true,
  "data": {
    "id": 456,
    "nextRunAt": "2025-12-14T04:00:00Z",
    "status": "waiting"
  }
}
```

**任务统计**
```http
GET /api/tasks/stats

Response:
{
  "success": true,
  "data": {
    "totalTasks": 1000,
    "pullTasks": 600,
    "pushTasks": 400,
    "waitingTasks": 150,
    "criticalTasks": 50,
    "highPriorityTasks": 200
  }
}
```

### Scheduler API

**调度器状态**
```http
GET /api/scheduler/status

Response:
{
  "success": true,
  "data": {
    "enabled": true,
    "isPeakHours": false,
    "peakConcurrency": 3,
    "offPeakConcurrency": 8,
    "activeTasksCount": 2,
    "queuedTasksCount": 45,
    "peakHoursRange": "9-18"
  }
}
```

**手动触发**
```http
POST /api/scheduler/trigger
Content-Type: application/json

{
  "taskType": "pull"
}

Response:
{
  "success": true,
  "data": "Scheduler triggered successfully"
}
```

---

## 注意事项

1. **权限控制**: 生产环境需要添加认证授权
2. **速率限制**: 考虑添加 API 速率限制防止滥用
3. **监控告警**: 集成 Prometheus/Grafana 监控
4. **API 版本**: 预留 API 版本化路径（/api/v1/）
5. **文档**: 使用 Swagger/OpenAPI 生成文档
