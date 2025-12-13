# 模块 1: 数据模型扩展 (Data Model Extension)

**目标**: 创建 Pull 同步相关的数据表，扩展统一任务模型。

**预计时间**: 1-2天

---

## 任务清单

### T1.1 创建 PULL_SYNC_CONFIG 表
**依赖**: Push Mirror MVP 数据模型

**任务目标**:
- 创建数据库表 PULL_SYNC_CONFIG
  - 参考 [PULL_SYNC_DESIGN.md - 核心实体及关系](../PULL_SYNC_DESIGN.md#-核心实体及关系)
- 创建实体类 `PullSyncConfig`
- 创建 Mapper 接口 `PullSyncConfigMapper`
- 配置 MyBatis-Plus 映射

**字段说明**:
```sql
CREATE TABLE pull_sync_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sync_project_id BIGINT NOT NULL UNIQUE COMMENT '关联项目ID',
    priority VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '优先级: critical/high/normal/low',
    enabled BOOLEAN NOT NULL DEFAULT true COMMENT '是否启用',
    local_repo_path VARCHAR(500) COMMENT '本地仓库路径',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE
);
```

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
**依赖**: T1.1

**任务目标**:
- 创建数据库表 SYNC_TASK（统一任务表）
  - 参考 [PULL_SYNC_DESIGN.md - SYNC_TASK 表](../PULL_SYNC_DESIGN.md#3-sync_task统一同步任务表新增)
- 创建实体类 `SyncTask`
- 创建 Mapper 接口 `SyncTaskMapper`
- 配置 MyBatis-Plus 映射

**字段说明**:
```sql
CREATE TABLE sync_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sync_project_id BIGINT NOT NULL UNIQUE COMMENT '关联项目ID(唯一)',
    task_type VARCHAR(20) NOT NULL COMMENT '任务类型: push/pull',
    task_status VARCHAR(20) NOT NULL DEFAULT 'waiting' COMMENT '任务状态: waiting/pending/running',
    next_run_at TIMESTAMP COMMENT '下次执行时间',
    last_run_at TIMESTAMP COMMENT '上次执行时间',
    started_at TIMESTAMP COMMENT '本次开始时间',
    completed_at TIMESTAMP COMMENT '本次完成时间',
    duration_seconds INT COMMENT '本次执行耗时',
    has_changes BOOLEAN COMMENT '本次是否有变更',
    changes_count INT COMMENT '本次变更数量',
    source_commit_sha VARCHAR(64) COMMENT '本次源SHA',
    target_commit_sha VARCHAR(64) COMMENT '本次目标SHA',
    last_sync_status VARCHAR(20) COMMENT '最后同步状态: success/failed',
    error_type VARCHAR(50) COMMENT '错误类型',
    error_message TEXT COMMENT '错误信息',
    consecutive_failures INT DEFAULT 0 COMMENT '连续失败次数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (sync_project_id) REFERENCES sync_project(id) ON DELETE CASCADE,
    INDEX idx_task_status (task_status),
    INDEX idx_next_run_at (next_run_at),
    INDEX idx_task_type (task_type)
);
```

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
**依赖**: T1.2

**任务目标**:
- 添加 `repository_size` 字段到 SOURCE_PROJECT_INFO 表
- 更新实体类 `SourceProjectInfo`
- 更新 Mapper 查询

**字段说明**:
```sql
ALTER TABLE source_project_info
ADD COLUMN repository_size BIGINT COMMENT '仓库大小(字节)' AFTER empty_repo;
```

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
**依赖**: T1.1, T1.2, T1.3

**任务目标**:
- 创建 Flyway/Liquibase 迁移脚本
- 为 Push Mirror 项目初始化 SYNC_TASK 记录
- 数据一致性验证

**迁移脚本内容**:
```sql
-- V2.0__create_pull_sync_tables.sql
-- 1. 创建 PULL_SYNC_CONFIG 表
-- 2. 创建 SYNC_TASK 表
-- 3. 扩展 SOURCE_PROJECT_INFO 表
-- 4. 为现有 Push Mirror 项目初始化 SYNC_TASK 记录

-- 初始化 Push Mirror 任务
INSERT INTO sync_task (sync_project_id, task_type, task_status, next_run_at)
SELECT
    id,
    'push' as task_type,
    'waiting' as task_status,
    NOW() as next_run_at
FROM sync_project
WHERE sync_method = 'push_mirror'
AND NOT EXISTS (SELECT 1 FROM sync_task WHERE sync_project_id = sync_project.id);
```

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
