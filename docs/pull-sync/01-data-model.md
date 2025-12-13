# 模块 1: 数据模型扩展 (Data Model Extension)

**状态**: ✅ 已完成 (Completed)

**目标**: 创建 Pull 同步相关的数据表，扩展统一任务模型。

**预计时间**: 1-2天

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

**示例**：
```markdown
### T1.1 创建 PULL_SYNC_CONFIG 表
**状态**: 🔄 进行中 (In Progress)  # ← 开始时修改为此状态
**依赖**: Push Mirror MVP 数据模型

# ... 完成工作后 ...

**状态**: ✅ 已完成 (Completed)  # ← 完成时修改为此状态
```

---

## 任务清单

### T1.1 创建 PULL_SYNC_CONFIG 表
**状态**: ✅ 已完成
**依赖**: Push Mirror MVP 数据模型

**任务目标**:
- 创建数据库表 PULL_SYNC_CONFIG
  - 参考 [PULL_SYNC_DESIGN.md - 核心实体及关系](../PULL_SYNC_DESIGN.md#-核心实体及关系)
- 创建实体类 `PullSyncConfig`
- 创建 Mapper 接口 `PullSyncConfigMapper`
- 配置 MyBatis-Plus 映射

**核心字段**:
- id, sync_project_id (UK, FK), priority (critical/high/normal/low)
- enabled, local_repo_path
- created_at, updated_at

**验收标准**:
- 表创建成功，字段类型正确
- 实体类注解完整（@Table, @TableField）
- Mapper 基本 CRUD 方法可用
- 1:1 关系正确映射

**测试要求**:
- 测试基本 CRUD 操作
- 测试唯一约束（sync_project_id）
- 测试外键级联删除
- 测试默认值和自动填充

**提交**: `feat(data): add PULL_SYNC_CONFIG table and mapper`

---

### T1.2 创建 SYNC_TASK 统一任务表
**状态**: ✅ 已完成
**依赖**: T1.1

**任务目标**:
- 创建数据库表 SYNC_TASK（统一任务表）
  - 参考 [PULL_SYNC_DESIGN.md - SYNC_TASK 表](../PULL_SYNC_DESIGN.md#3-sync_task统一同步任务表新增)
- 创建实体类 `SyncTask`
- 创建 Mapper 接口 `SyncTaskMapper`
- 配置 MyBatis-Plus 映射

**核心字段**:
- id, sync_project_id (UK, FK)
- task_type (push/pull), task_status (waiting/pending/running)
- next_run_at, last_run_at, started_at, completed_at, duration_seconds
- has_changes, changes_count, source_commit_sha, target_commit_sha
- last_sync_status (success/failed), error_type, error_message
- consecutive_failures, created_at, updated_at
- 索引: idx_task_status, idx_next_run_at, idx_task_type

**验收标准**:
- 表创建成功，字段类型正确
- 实体类注解完整
- Mapper 基本 CRUD 和查询方法可用
- 索引创建正确（status, next_run_at, type）
- 1:1 关系正确映射

**测试要求**:
- 测试基本 CRUD 操作
- 测试唯一约束（sync_project_id）
- 测试按状态和时间查询
- 测试按任务类型查询
- 测试外键级联删除

**提交**: `feat(data): add SYNC_TASK unified task table`

---

### T1.3 扩展 SOURCE_PROJECT_INFO 表
**状态**: ✅ 已完成 (Completed)
**依赖**: T1.2

**任务目标**:
- 添加 `repository_size` 字段到 SOURCE_PROJECT_INFO 表
- 更新实体类 `SourceProjectInfo`
- 更新 Mapper 查询

**新增字段**:
- repository_size (BIGINT) - 仓库大小(字节)

**验收标准**:
- 字段添加成功
- 实体类更新
- 查询正常包含新字段

**测试要求**:
- 测试字段读写
- 测试空值处理

**提交**: `feat(data): add repository_size to SOURCE_PROJECT_INFO`

---

### T1.4 创建数据迁移脚本
**状态**: ✅ 已完成 (Completed)
**依赖**: T1.1, T1.2, T1.3

**任务目标**:
- 创建 Flyway/Liquibase 迁移脚本
- 为 Push Mirror 项目初始化 SYNC_TASK 记录
- 数据一致性验证

**迁移脚本内容** (V2.0__create_pull_sync_tables.sql):
- 创建 PULL_SYNC_CONFIG 表
- 创建 SYNC_TASK 表
- 扩展 SOURCE_PROJECT_INFO 表（添加 repository_size）
- 为现有 Push Mirror 项目初始化 SYNC_TASK 记录 (task_type='push')

**验收标准**:
- 迁移脚本执行成功
- 现有 Push Mirror 项目有对应 SYNC_TASK 记录
- 数据完整性约束正常
- 可回滚

**测试要求**:
- 测试迁移脚本执行
- 测试回滚脚本
- 验证数据一致性
- 测试在有数据的环境中执行

**提交**: `feat(data): add database migration scripts for pull sync`

---

## 模块输出

- ✅ PULL_SYNC_CONFIG 表和实体
- ✅ SYNC_TASK 统一任务表和实体
- ✅ SOURCE_PROJECT_INFO 扩展 repository_size 字段
- ✅ 数据迁移脚本
- ✅ 现有 Push Mirror 项目已初始化任务记录

---

## 关键决策

1. **统一任务表**: SYNC_TASK 使用 1:1 关系，每个项目只有一条任务记录，避免任务表膨胀
2. **任务类型**: task_type 字段区分 push/pull，复用同一张表
3. **任务状态**: 只有 waiting/pending/running 三种状态，成功/失败记录在 last_sync_status
4. **索引设计**: 为调度查询优化（task_status + next_run_at）
5. **配置分离**: PULL_SYNC_CONFIG 只存静态配置，动态状态在 SYNC_TASK

---

## 数据库 ER 图

```
SYNC_PROJECT (主表)
    ├── SOURCE_PROJECT_INFO (1:1)
    ├── TARGET_PROJECT_INFO (1:1)
    ├── PUSH_MIRROR_CONFIG (1:0..1) - Push 配置
    ├── PULL_SYNC_CONFIG (1:0..1) - Pull 配置
    ├── SYNC_TASK (1:1) - 统一任务
    └── SYNC_EVENT (1:N) - 事件历史
```

---

## 注意事项

1. **外键级联**: 删除 SYNC_PROJECT 时自动级联删除配置和任务
2. **唯一约束**: sync_project_id 必须唯一，确保 1:1 关系
3. **索引优化**: 为调度查询添加复合索引
4. **数据迁移**: 确保现有 Push Mirror 数据正确迁移到 SYNC_TASK
5. **字段长度**: error_message 使用 TEXT 类型支持长错误信息
