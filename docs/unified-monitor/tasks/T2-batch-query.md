# T2: 批量查询服务

**状态**: 🔄 进行中 (In Progress)
**依赖**: T1 - 数据模型扩展
**预计时间**: 2天

---

## 任务目标

- 实现 BatchQueryExecutor 批量查询服务
- 支持增量查询（updated_after 参数）
- 实现并发查询优化
- 更新项目表的监控字段
- 实现查询结果批量写入

---

## 子任务

### T2.1 GitLab API 批量查询封装
**状态**: ✅ 已完成

**任务内容**:
- 创建 `BatchQueryExecutor` 服务类
- 实现 `queryProjects()` 方法
  - 支持 `updated_after` 参数（增量查询）
  - 支持 `per_page` 分页参数
  - 支持 `statistics=true` 参数（获取仓库大小）
  - 支持 `with_custom_attributes=false` 优化性能
- 实现分页处理逻辑
- 处理 GitLab API 返回的项目信息:
  - `default_branch`
  - `last_activity_at`
  - `statistics.repository_size`
- 实现查询超时控制（30秒）
- 实现错误重试（最多3次）

**验收标准**:
- 批量查询正确执行
- 增量查询参数正确传递
- 分页处理正确（自动获取所有页）
- 统计信息正确获取
- 超时控制生效
- 错误重试正确

**API 示例**:
```
GET /api/v4/projects?updated_after=2025-12-14T10:00:00Z&per_page=50&statistics=true
```

---

### T2.2 获取项目详细信息
**状态**: ✅ 已完成

**任务内容**:
- 实现 `getProjectDetails()` 方法
- 调用 GitLab API 获取:
  - Branches: `GET /api/v4/projects/:id/repository/branches`
  - Commits: `GET /api/v4/projects/:id/repository/commits`
  - Latest commit SHA: 从 branches 中获取 default_branch 的 commit.id
- 实现字段解析:
  - `latest_commit_sha` - 默认分支的最新 commit SHA
  - `commit_count` - commits 总数（可选，性能考虑）
  - `branch_count` - 分支总数
- 实现批量并发查询（5个并发）
- 使用 CompletableFuture 异步处理

**验收标准**:
- 项目详细信息正确获取
- 并发查询正确执行
- commit SHA 正确解析
- 分支数量正确统计
- 异步处理无阻塞

---

### T2.3 更新项目表监控字段
**状态**: ✅ 已完成

**任务内容**:
- 创建 `UpdateProjectDataService` 服务类
- 实现 `updateSourceProjects()` 方法
  - 批量更新 SOURCE_PROJECT_INFO 监控字段
  - 使用 MyBatis-Plus 批量更新
- 实现 `updateTargetProjects()` 方法
  - 批量更新 TARGET_PROJECT_INFO 监控字段
- 实现事务控制
- 实现更新结果统计（成功数/失败数）
- 记录更新日志

**更新字段**:
- `latest_commit_sha`
- `commit_count`
- `branch_count`
- `repository_size`
- `last_activity_at`
- `updated_at`

**验收标准**:
- 批量更新正确执行
- 事务正确提交/回滚
- 更新结果统计准确
- 日志记录完整
- 性能达标（1000个项目 <10秒）

---

### T2.4 增量查询优化
**状态**: ⏸️ 待处理

**任务内容**:
- 实现增量查询时间管理
- 从数据库读取 `last_scan_time`（可存储在配置表或 Redis）
- 构造 `updated_after` 参数
- 更新 `last_scan_time` 到当前时间
- 实现全量查询降级逻辑（增量失败时）
- 实现查询结果过滤（排除未变更项目）

**验收标准**:
- 增量查询时间正确管理
- `updated_after` 参数正确传递
- 全量降级逻辑正确
- 仅处理变更项目
- API 调用次数减少 80-90%

---

### T2.5 单元测试
**状态**: ⏸️ 待处理

**任务内容**:
- 测试批量查询 API 调用
- 测试增量查询参数
- 测试项目详细信息获取
- 测试并发查询
- 测试批量更新
- 测试事务回滚
- Mock GitLab API 响应

**验收标准**:
- 所有测试通过
- Mock 响应正确
- 并发测试无竞态
- 事务测试正确

---

## 提交信息

```
feat(monitor): implement batch query executor and project update service
```

---

## 参考文档

- [UNIFIED_PROJECT_MONITOR_DESIGN.md - 批量查询](../UNIFIED_PROJECT_MONITOR_DESIGN.md#🔄-关键处理流程)
- GitLab API 文档: https://docs.gitlab.com/ee/api/projects.html
