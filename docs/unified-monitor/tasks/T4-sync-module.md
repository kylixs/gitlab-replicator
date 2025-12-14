# T4: 同步模块（项目发现）

**状态**: ⏸️ 待处理 (Pending)
**依赖**: T3 - 差异计算服务
**预计时间**: 2天

---

## 任务目标

- 整合项目发现逻辑
- 实现新项目检测和创建
- 实现项目更新检测和触发同步
- 实现项目删除检测
- 提供同步模块 REST API 和 CLI

---

## 子任务

### T4.1 统一监控服务
**状态**: ✅ 已完成

**任务内容**:
- 创建 `UnifiedProjectMonitor` 统一监控服务
- 实现 `scan()` 扫描方法
  - 接受参数: `type`（incremental/full）
  - 调用 BatchQueryExecutor 批量查询源项目
  - 调用 UpdateProjectDataService 更新项目表
  - 调用 DiffCalculator 计算差异
  - 调用 ProjectDiscoveryService 处理发现逻辑
  - 返回扫描结果统计
- 实现扫描结果统计:
  - `duration_ms` - 扫描耗时
  - `projects_scanned` - 扫描项目数
  - `projects_updated` - 更新项目数
  - `new_projects` - 新发现项目数
  - `changes_detected` - 检测到变更数
- 实现扫描日志记录

**验收标准**:
- 扫描流程正确执行
- 各模块正确调用
- 统计信息准确
- 日志记录完整
- 事务控制正确

---

### T4.2 项目发现服务
**状态**: ✅ 已完成

**任务内容**:
- 创建 `ProjectDiscoveryService` 项目发现服务
- 实现 `detectNewProjects()` 检测新项目
  - 从差异结果中找出 target_missing 的项目
  - 创建 SYNC_PROJECT 记录
  - 创建 PULL_SYNC_CONFIG 配置
  - 状态设置为 pending
- 实现 `detectUpdatedProjects()` 检测更新项目
  - 从差异结果中找出有变更的项目
  - 检查 commit SHA 是否变化
  - 触发同步任务（调用 PullSyncExecutor）
- 实现 `detectDeletedProjects()` 检测删除项目
  - 对比数据库中的项目和 GitLab 查询结果
  - 标记删除的项目（sync_status = deleted）
  - 创建告警（alert_type = project_deleted）
- 实现发现结果统计

**验收标准**:
- 新项目正确检测和创建
- 更新项目正确检测和触发同步
- 删除项目正确标记
- 发现结果统计准确
- 重复项目不会重复创建

---

### T4.3 同步模块 REST API
**状态**: ✅ 已完成

**任务内容**:
- 创建 `SyncController` 控制器
- 实现 API 端点:
  - `POST /api/sync/scan` - 手动触发扫描
    - 请求参数: `type`（incremental/full，默认 incremental）
    - 返回扫描结果统计
  - `GET /api/sync/projects` - 获取项目列表
    - 查询参数: `status`, `page`, `size`
    - 返回项目列表和分页信息
  - `GET /api/sync/projects/{projectKey}` - 获取项目详情
    - 返回项目信息和同步配置
  - `GET /api/sync/projects/{projectKey}/diff` - 获取项目差异
    - 从 Redis 缓存读取差异结果
    - 缓存不存在则实时计算
- 实现权限控制（ADMIN/READ）
- 实现请求验证
- 实现统一响应格式

**API 响应示例**:
```json
{
  "success": true,
  "data": {
    "duration_ms": 8500,
    "projects_scanned": 127,
    "new_projects": 2,
    "changes_detected": 8
  }
}
```

**验收标准**:
- 所有 API 正确实现
- 权限控制生效
- 参数验证正确
- 响应格式统一
- 错误处理完善

---

### T4.4 同步模块 CLI
**状态**: ⏸️ 待处理 (跳过，后续实现)

**任务内容**:
- 创建 CLI 命令（在 `scripts/gitlab-mirror` 中）:
  - `scan` - 触发扫描
    - 选项: `--type=incremental|full`
    - 调用 `POST /api/sync/scan`
    - 打印扫描结果统计
  - `projects` - 列出项目
    - 选项: `--status=synced|outdated|failed`
    - 调用 `GET /api/sync/projects`
    - 格式化输出表格
  - `diff` - 查看项目差异
    - 参数: `<project-key>`
    - 调用 `GET /api/sync/projects/{projectKey}/diff`
    - 格式化输出差异对比
- 实现美化输出（表格、颜色）
- 实现错误处理和提示

**CLI 输出示例**:
```
$ gitlab-mirror scan --type=incremental

Scanning projects...
✓ Scanned 127 projects in 8.5s
  • New projects:     2
  • Changes detected: 8
  • Updated:          127
```

**验收标准**:
- 所有命令正确实现
- API 调用正确
- 输出格式美观
- 错误处理完善
- 帮助文档完整

---

### T4.5 单元测试和集成测试
**状态**: ⏸️ 待处理

**任务内容**:
- 测试 UnifiedProjectMonitor 扫描流程
- 测试 ProjectDiscoveryService 发现逻辑
- 测试 REST API 端点
- 测试 CLI 命令
- Mock GitLab API 和数据库
- 测试并发扫描（防止重复）

**验收标准**:
- 所有测试通过
- Mock 正确
- 并发测试无竞态
- 集成测试覆盖完整流程

---

## 提交信息

```
feat(monitor): implement sync module with project discovery and apis
```

---

## 参考文档

- [UNIFIED_PROJECT_MONITOR_DESIGN.md - REST API](../UNIFIED_PROJECT_MONITOR_DESIGN.md#🔌-rest-api-设计)
- [UNIFIED_PROJECT_MONITOR_DESIGN.md - CLI](../UNIFIED_PROJECT_MONITOR_DESIGN.md#💻-cli-命令设计)
