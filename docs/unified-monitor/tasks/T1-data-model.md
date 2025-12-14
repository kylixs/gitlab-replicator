# T1: 数据模型扩展

**状态**: ✅ 已完成 (Completed)
**依赖**: 无
**预计时间**: 1天

---

## 任务目标

- 扩展 SOURCE_PROJECT_INFO 表，新增监控字段
- 扩展 TARGET_PROJECT_INFO 表，新增监控字段
- 创建 MONITOR_ALERT 表
- 编写数据库迁移脚本
- 更新 Entity 和 Mapper 类

---

## 子任务

### T1.1 扩展 SOURCE_PROJECT_INFO 表
**状态**: ⏸️ 待处理

**任务内容**:
- 创建数据库迁移脚本 `V1.x__extend_source_project_info.sql`
- 新增字段:
  - `latest_commit_sha VARCHAR(64)` - 最新提交SHA
  - `commit_count INT` - 提交数量
  - `branch_count INT` - 分支数量
  - `repository_size BIGINT` - 仓库大小（字节）
  - `last_activity_at DATETIME` - 最后活动时间
- 创建索引: `idx_last_activity (last_activity_at)`
- 更新 `SourceProjectInfo` Entity 类
- 更新 `SourceProjectInfoMapper` 接口

**验收标准**:
- 迁移脚本正确执行
- 新增字段正确创建
- 索引正确创建
- Entity 类字段正确映射
- Mapper 正确查询和更新新字段

---

### T1.2 扩展 TARGET_PROJECT_INFO 表
**状态**: ⏸️ 待处理

**任务内容**:
- 创建数据库迁移脚本 `V1.x__extend_target_project_info.sql`
- 新增字段（同 SOURCE_PROJECT_INFO）:
  - `latest_commit_sha VARCHAR(64)`
  - `commit_count INT`
  - `branch_count INT`
  - `repository_size BIGINT`
  - `last_activity_at DATETIME`
- 创建索引: `idx_last_activity (last_activity_at)`
- 更新 `TargetProjectInfo` Entity 类
- 更新 `TargetProjectInfoMapper` 接口

**验收标准**:
- 迁移脚本正确执行
- 新增字段正确创建
- 索引正确创建
- Entity 类字段正确映射
- Mapper 正确查询和更新新字段

---

### T1.3 创建 MONITOR_ALERT 表
**状态**: ⏸️ 待处理

**任务内容**:
- 创建数据库迁移脚本 `V1.x__create_monitor_alert.sql`
- 创建表结构:
  - `id BIGINT` - 主键
  - `sync_project_id BIGINT` - 关联项目
  - `alert_type VARCHAR(50)` - 告警类型
  - `severity VARCHAR(20)` - 严重程度
  - `title VARCHAR(255)` - 告警标题
  - `description TEXT` - 告警描述
  - `metadata TEXT` - 元数据JSON
  - `status VARCHAR(20)` - 状态
  - `triggered_at DATETIME` - 触发时间
  - `resolved_at DATETIME` - 解决时间
  - `created_at DATETIME` - 创建时间
  - `updated_at DATETIME` - 更新时间
- 创建索引:
  - `idx_project (sync_project_id)`
  - `idx_status_severity (status, severity, triggered_at)`
  - `idx_type (alert_type, triggered_at)`
- 创建外键: `sync_project_id → sync_project.id`
- 创建 `MonitorAlert` Entity 类
- 创建 `MonitorAlertMapper` 接口

**验收标准**:
- 表结构正确创建
- 索引正确创建
- 外键约束正确
- Entity 类字段正确映射
- Mapper 支持基本 CRUD 操作
- 支持按状态、严重程度、类型查询

---

### T1.4 单元测试
**状态**: ⏸️ 待处理

**任务内容**:
- 测试 SOURCE_PROJECT_INFO 新字段读写
- 测试 TARGET_PROJECT_INFO 新字段读写
- 测试 MONITOR_ALERT CRUD 操作
- 测试索引性能
- 测试外键约束

**验收标准**:
- 所有测试通过
- 新字段读写正常
- 查询性能符合预期
- 外键约束生效

---

## 提交信息

```
feat(monitor): extend project tables and add monitor alert table
```

---

## 参考文档

- [UNIFIED_PROJECT_MONITOR_DESIGN.md - 数据模型](../UNIFIED_PROJECT_MONITOR_DESIGN.md#📊-核心实体及关系)
