# T5: 监控模块（告警和指标）

**状态**: ✅ 已完成 (Completed)
**依赖**: T4 - 同步模块
**预计时间**: 2天

---

## 任务目标

- 实现同步监控逻辑
- 实现告警事件管理
- 实现 Prometheus 指标导出
- 提供监控模块 REST API 和 CLI
- 集成 Micrometer

---

## 子任务

### T5.1 同步监控服务
**状态**: ✅ 已完成

**任务内容**:
- 创建 `SyncMonitorService` 同步监控服务
- 实现 `evaluateProjects()` 评估方法
  - 从 Redis 读取差异缓存
  - 调用 DiffCalculator.evaluateThresholds() 判定告警
  - 创建/更新/解决 MONITOR_ALERT 记录
- 实现 `createAlert()` 创建告警方法
  - 检查是否已存在活跃告警（去重）
  - 构造告警对象（title, description, metadata）
  - 保存到数据库
  - 记录告警日志
- 实现 `resolveAlert()` 解决告警方法
  - 更新告警状态为 resolved
  - 设置 resolved_at 时间
  - 记录解决日志
- 实现 `autoResolveAlerts()` 自动解决方法
  - 检查问题是否已修复（从差异结果对比）
  - 自动标记为 resolved
- 实现告警去重逻辑（同一项目同一类型60分钟内不重复）

**验收标准**:
- 告警正确创建
- 告警去重生效
- 自动解决正确
- 告警元数据完整
- 日志记录完整

---

### T5.2 告警管理 API
**状态**: ✅ 已完成

**任务内容**:
- 创建 `MonitorController` 控制器
- 实现 API 端点:
  - `GET /api/monitor/status` - 获取监控总览
    - 返回统计摘要（从 Redis 读取）
    - 包含项目数、同步状态分布、告警统计
  - `GET /api/monitor/alerts` - 获取告警列表
    - 查询参数: `severity`, `status`, `page`, `size`
    - 支持按严重程度、状态过滤
    - 支持分页
  - `POST /api/monitor/alerts/{id}/resolve` - 解决告警
    - 标记告警为 resolved
    - 记录操作日志
  - `POST /api/monitor/alerts/{id}/mute` - 静默告警
    - 标记告警为 muted
    - 支持设置静默时长
- 实现权限控制
- 实现统一响应格式

**API 响应示例**:
```json
{
  "success": true,
  "data": {
    "summary": {
      "total_projects": 127,
      "synced": 118,
      "outdated": 5,
      "failed": 2
    },
    "alerts": {
      "active": 9,
      "critical": 1,
      "high": 2
    }
  }
}
```

**验收标准**:
- 所有 API 正确实现
- 权限控制生效
- 查询过滤正确
- 分页正确
- 操作日志完整

---

### T5.3 Prometheus 指标导出
**状态**: ✅ 已完成

**任务内容**:
- 创建 `MetricsExporter` 指标导出服务
- 集成 Micrometer（Spring Boot Actuator）
- 实现系统级指标:
  - `gitlab_mirror_projects_total` - 项目总数（Gauge）
  - `gitlab_mirror_sync_status{status}` - 同步状态分布（Gauge）
  - `gitlab_mirror_alerts_active{severity}` - 活跃告警数（Gauge）
  - `gitlab_mirror_scan_duration_seconds{type}` - 扫描耗时（Summary）
  - `gitlab_mirror_api_calls_total{instance}` - API 调用次数（Counter）
  - `gitlab_mirror_projects_discovered{type}` - 项目发现统计（Counter）
- 实现项目级指标:
  - `gitlab_mirror_project_commits{project, type}` - 提交数量（Gauge）
  - `gitlab_mirror_project_last_commit_time{project, type}` - 最后提交时间（Gauge）
  - `gitlab_mirror_project_size_bytes{project, type}` - 仓库大小（Gauge）
  - `gitlab_mirror_project_branches{project, type}` - 分支数量（Gauge）
- 实现指标刷新机制（从 Redis 和数据库读取）
- 配置 Prometheus 端点 `/actuator/prometheus`

**验收标准**:
- 所有指标正确导出
- 指标值准确
- Tags 正确设置
- Prometheus 可以正常抓取
- 性能达标（<100ms 响应）

---

### T5.4 监控模块 CLI
**状态**: ✅ 已完成

**任务内容**:
- 创建 CLI 命令:
  - `monitor status` - 查看监控总览
    - 调用 `GET /api/monitor/status`
    - 格式化输出表格（带颜色和图标）
  - `monitor alerts` - 查看告警列表
    - 选项: `--severity=critical|high|medium|low`
    - 调用 `GET /api/monitor/alerts`
    - 格式化输出表格
- 实现美化输出
- 实现错误处理

**CLI 输出示例**:
```
╔════════════════════════════════════════╗
║       Monitor Status Overview          ║
╠════════════════════════════════════════╣
║ 📊 Projects Summary                    ║
║   Total:        127                    ║
║   ✓ Synced:     118  (92.9%)           ║
║   ⟳ Outdated:   5    (3.9%)            ║
║   ✗ Failed:     2    (1.6%)            ║
╠════════════════════════════════════════╣
║ 🚨 Active Alerts   9                   ║
║   🔴 Critical:  1                       ║
║   🟠 High:      2                       ║
╚════════════════════════════════════════╝
```

**验收标准**:
- 所有命令正确实现
- 输出格式美观
- 颜色和图标正确
- 错误处理完善

---

### T5.5 单元测试和集成测试
**状态**: ⏸️ 待处理

**任务内容**:
- 测试 SyncMonitorService 监控逻辑
- 测试告警创建/解决/去重
- 测试 REST API 端点
- 测试 Prometheus 指标导出
- 测试 CLI 命令
- Mock 数据库和 Redis

**验收标准**:
- 所有测试通过
- 告警逻辑正确
- 指标导出正确
- Mock 正确

---

## 提交信息

```
feat(monitor): implement monitor module with alerts and prometheus metrics
```

---

## 参考文档

- [UNIFIED_PROJECT_MONITOR_DESIGN.md - 监控模块](../UNIFIED_PROJECT_MONITOR_DESIGN.md#🔌-rest-api-设计)
- [UNIFIED_PROJECT_MONITOR_DESIGN.md - Prometheus](../UNIFIED_PROJECT_MONITOR_DESIGN.md#prometheus指标定义)
