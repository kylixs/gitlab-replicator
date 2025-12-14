# 统一项目监控 - 任务总览

**项目状态**: ⏸️ 待处理 (Pending)
**总预计时间**: 约12天（2-3周）
**设计文档**: [UNIFIED_PROJECT_MONITOR_DESIGN.md](../UNIFIED_PROJECT_MONITOR_DESIGN.md)

---

## 📋 任务清单总览

| 任务 | 描述 | 状态 | 预计时间 | 依赖 |
|------|------|------|---------|------|
| [T1](tasks/T1-data-model.md) | 数据模型扩展 | ⏸️ 待处理 | 1天 | 无 |
| [T2](tasks/T2-batch-query.md) | 批量查询服务 | ⏸️ 待处理 | 2天 | T1 |
| [T3](tasks/T3-diff-calculator.md) | 差异计算服务 | ⏸️ 待处理 | 2天 | T2 |
| [T4](tasks/T4-sync-module.md) | 同步模块（项目发现） | ⏸️ 待处理 | 2天 | T3 |
| [T5](tasks/T5-monitor-module.md) | 监控模块（告警和指标） | ⏸️ 待处理 | 2天 | T4 |
| [T6](tasks/T6-scheduler.md) | 定时调度器 | ⏸️ 待处理 | 1天 | T5 |
| [T7](tasks/T7-integration-test.md) | 集成测试和文档 | ⏸️ 待处理 | 2天 | T6 |

---

## 🎯 核心目标

### 模块化设计
- **同步模块**：查询、扫描、项目发现
- **监控模块**：指标导出、告警管理

### 双层指标体系
- **系统级指标**：整体监控，同步异常数量告警
- **项目级指标**：细粒度监控，支持差异对比

### 轻量化实现
- 复用现有表（SOURCE_PROJECT_INFO/TARGET_PROJECT_INFO）
- 差异结果仅缓存到本地内存（无需Redis）
- 告警驱动（仅在异常时记录）

---

## 📦 任务详情

### T1: 数据模型扩展（1天）

**子任务**:
- [ ] T1.1 扩展 SOURCE_PROJECT_INFO 表
- [ ] T1.2 扩展 TARGET_PROJECT_INFO 表
- [ ] T1.3 创建 MONITOR_ALERT 表
- [ ] T1.4 单元测试

**输出**:
- 数据库迁移脚本
- Entity 和 Mapper 类
- 单元测试

**详情**: [T1-data-model.md](tasks/T1-data-model.md)

---

### T2: 批量查询服务（2天）

**子任务**:
- [ ] T2.1 GitLab API 批量查询封装
- [ ] T2.2 获取项目详细信息
- [ ] T2.3 更新项目表监控字段
- [ ] T2.4 增量查询优化
- [ ] T2.5 单元测试

**输出**:
- BatchQueryExecutor 服务
- UpdateProjectDataService 服务
- 增量查询逻辑

**详情**: [T2-batch-query.md](tasks/T2-batch-query.md)

---

### T3: 差异计算服务（2天）

**子任务**:
- [ ] T3.1 差异对象模型
- [ ] T3.2 差异计算逻辑
- [ ] T3.3 本地内存缓存
- [ ] T3.4 告警阈值判定
- [ ] T3.5 单元测试

**输出**:
- DiffCalculator 服务
- ProjectDiff 差异对象
- LocalCacheManager 本地缓存

**详情**: [T3-diff-calculator.md](tasks/T3-diff-calculator.md)

---

### T4: 同步模块（项目发现）（2天）

**子任务**:
- [ ] T4.1 统一监控服务
- [ ] T4.2 项目发现服务
- [ ] T4.3 同步模块 REST API
- [ ] T4.4 同步模块 CLI
- [ ] T4.5 单元测试和集成测试

**输出**:
- UnifiedProjectMonitor 服务
- ProjectDiscoveryService 服务
- 同步模块 API 和 CLI

**API**:
- `POST /api/sync/scan`
- `GET /api/sync/projects`
- `GET /api/sync/projects/{projectKey}/diff`

**CLI**:
- `gitlab-mirror scan`
- `gitlab-mirror projects`
- `gitlab-mirror diff`

**详情**: [T4-sync-module.md](tasks/T4-sync-module.md)

---

### T5: 监控模块（告警和指标）（2天）

**子任务**:
- [ ] T5.1 同步监控服务
- [ ] T5.2 告警管理 API
- [ ] T5.3 Prometheus 指标导出
- [ ] T5.4 监控模块 CLI
- [ ] T5.5 单元测试和集成测试

**输出**:
- SyncMonitorService 服务
- MetricsExporter 指标导出
- 监控模块 API 和 CLI

**API**:
- `GET /api/monitor/status`
- `GET /api/monitor/alerts`
- `POST /api/monitor/alerts/{id}/resolve`
- `GET /actuator/prometheus`

**CLI**:
- `gitlab-mirror monitor status`
- `gitlab-mirror monitor alerts`

**Prometheus 指标**:
- 系统级: `gitlab_mirror_projects_total`, `gitlab_mirror_sync_status`, `gitlab_mirror_alerts_active`
- 项目级: `gitlab_mirror_project_commits`, `gitlab_mirror_project_last_commit_time`, `gitlab_mirror_project_size_bytes`

**详情**: [T5-monitor-module.md](tasks/T5-monitor-module.md)

---

### T6: 定时调度器（1天）

**子任务**:
- [ ] T6.1 增量扫描调度器（5分钟）
- [ ] T6.2 全量对账调度器（每天）
- [ ] T6.3 告警自动解决调度器（10分钟）
- [ ] T6.4 调度配置
- [ ] T6.5 单元测试

**输出**:
- MonitorScheduler 调度器
- 调度配置

**详情**: [T6-scheduler.md](tasks/T6-scheduler.md)

---

### T7: 集成测试和文档（2天）

**子任务**:
- [ ] T7.1 端到端集成测试
- [ ] T7.2 性能测试
- [ ] T7.3 Grafana 监控面板配置
- [ ] T7.4 Prometheus 告警规则配置
- [ ] T7.5 使用文档编写

**输出**:
- 集成测试
- Grafana Dashboard JSON
- Prometheus 告警规则
- 用户手册、运维手册、开发文档

**详情**: [T7-integration-test.md](tasks/T7-integration-test.md)

---

## 📈 进度跟踪

### 完成度统计
- **已完成**: 0/7 (0%)
- **进行中**: 0/7 (0%)
- **待处理**: 7/7 (100%)

### 时间进度
- **总预计时间**: 12天
- **已用时间**: 0天
- **剩余时间**: 12天

---

## 🎬 开始指南

### 第一步：数据模型扩展
```bash
# 查看任务详情
cat docs/unified-monitor/tasks/T1-data-model.md

# 开始任务前，更新状态为"进行中"
# 编辑 T1-data-model.md，修改顶部状态行
```

### 第二步：执行任务
按照任务文档中的子任务顺序执行，完成后更新状态。

### 第三步：提交代码
```bash
# 按任务文档中的提交信息格式提交
git add .
git commit -m "feat(monitor): extend project tables and add monitor alert table"
```

---

## ⚠️ 重要提醒

### 任务状态管理规范

**【必须】在处理每个任务前后更新状态：**

1. **开始任务前**：将 `tasks/TX-xxx.md` 顶部状态改为 `🔄 进行中 (In Progress)`
2. **完成任务后**：将状态改为 `✅ 已完成 (Completed)` 或 `❌ 失败 (Failed)`
3. **同时更新本文件**：更新进度统计和完成度

**状态标记**:
- ⏸️ 待处理 (Pending)
- 🔄 进行中 (In Progress)
- ✅ 已完成 (Completed)
- ❌ 失败 (Failed)
- ⚠️ 阻塞 (Blocked)

---

## 🔗 相关文档

- [UNIFIED_PROJECT_MONITOR_DESIGN.md](../UNIFIED_PROJECT_MONITOR_DESIGN.md) - 设计文档
- [DESIGN_DOC_SPECIFICATION.md](../DESIGN_DOC_SPECIFICATION.md) - 设计规范
- [../pull-sync/03-pull-executor.md](../pull-sync/03-pull-executor.md) - 参考示例

---

## 📝 注意事项

1. **模块划分清晰**：严格区分同步模块和监控模块
2. **API 和 CLI 对应**：每个模块的 API 和 CLI 要对应
3. **指标体系完整**：系统级和项目级指标都要实现
4. **性能达标**：增量扫描 <15秒，无变更检测 <1秒
5. **测试覆盖**：每个任务都要有单元测试和集成测试
6. **文档同步**：代码完成后及时更新文档

---

**最后更新**: 2025-12-14
**维护者**: GitLab Mirror Team
