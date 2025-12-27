# 模块 2: 数据模型层 (Data Model Layer)

**目标**: 一次性实现所有实体类和 Mapper，处理好实体间的依赖关系。

**预计时间**: Week 1 (2-3天)

**重要说明**: 由于实体间存在外键依赖，所有数据模型集中在这个模块实现，而不是分散到各业务模块。

---

## 实体依赖关系

```
SYNC_PROJECT (核心表，无依赖)
  ├─ SOURCE_PROJECT_INFO (依赖 SYNC_PROJECT)
  ├─ TARGET_PROJECT_INFO (依赖 SYNC_PROJECT)
  ├─ PUSH_MIRROR_CONFIG (依赖 SYNC_PROJECT)
  └─ SYNC_EVENT (依赖 SYNC_PROJECT)
```

**详细设计参考**: [PUSH_MIRROR_MVP_DESIGN.md - 核心实体及关系](../PUSH_MIRROR_MVP_DESIGN.md#-核心实体及关系)

---

## 任务清单

### T2.1 实现所有实体类和数据访问层
**依赖**: T1.3 (数据库表结构)

**任务目标**:
- 定义 5 个实体类（按依赖顺序）
  1. **SYNC_PROJECT** - 同步项目主表
  2. **SOURCE_PROJECT_INFO** - 源项目信息
  3. **TARGET_PROJECT_INFO** - 目标项目信息
  4. **PUSH_MIRROR_CONFIG** - Push Mirror 配置
  5. **SYNC_EVENT** - 同步事件
- 实现对应的 Mapper 接口（继承 BaseMapper）
- 实现基础 CRUD 操作
- 实现 JSON 字段的 TypeHandler（用于元数据序列化）
- 配置自动填充（created_at、updated_at）

**验收标准**:
- 所有实体类正确映射到数据库表
- CRUD 操作正常
- 外键约束生效
- JSON 字段正常存取
- 时间戳自动填充

**测试要求**:
- 测试每个实体的 CRUD 操作
- 测试外键级联
- 测试 JSON 字段序列化/反序列化
- 测试唯一约束
- 测试自动填充

**提交**: `feat(model): implement all entities and mappers with dependencies`

---

### T2.2 实现复杂查询和统计功能
**依赖**: T2.1

**任务目标**:
- 实现多维度查询（按状态、类型、时间范围等）
- 实现关联查询（JOIN 查询获取完整信息）
- 实现分页查询
- 实现统计查询
  - 同步项目统计（各状态项目数）
  - Mirror 状态统计（成功/失败/同步中）
  - 事件统计（频率、延迟分析）
- 优化查询性能（合理使用索引）

**验收标准**:
- 多维度查询返回正确结果
- 关联查询获取完整数据
- 分页查询正常
- 统计数据准确
- 查询性能良好（< 100ms）

**测试要求**:
- 测试各种查询条件组合
- 测试关联查询
- 测试分页查询
- 测试统计计算准确性
- 测试大数据量查询性能

**提交**: `feat(model): add complex queries and statistics`

---

## 实体详情

### 1. SYNC_PROJECT（同步项目）
**字段**:
- id (PK)
- project_key (UK) - 项目唯一标识
- sync_method - 同步方式（push_mirror/pull_sync）
- sync_status - 同步状态
- enabled - 是否启用
- error_message
- created_at, updated_at

**Mapper 方法**:
- 基础 CRUD
- 按状态查询
- 按同步方式查询
- 启用/禁用项目

---

### 2. SOURCE_PROJECT_INFO（源项目信息）
**字段**:
- id (PK)
- sync_project_id (FK)
- gitlab_project_id
- path_with_namespace
- name, description
- metadata (JSON)
- created_at, updated_at

**Mapper 方法**:
- 基础 CRUD
- 按同步项目查询
- 关联查询（JOIN SYNC_PROJECT）

---

### 3. TARGET_PROJECT_INFO（目标项目信息）
**字段**:
- id (PK)
- sync_project_id (FK)
- gitlab_project_id
- path_with_namespace
- name
- status - 状态（not_exist/creating/created/ready/error）
- last_checked_at
- error_message
- retry_count
- created_at, updated_at

**Mapper 方法**:
- 基础 CRUD
- 按状态查询
- 按同步项目查询
- 批量检查

---

### 4. PUSH_MIRROR_CONFIG（Push Mirror 配置）
**字段**:
- id (PK)
- sync_project_id (FK, UK) - 一个项目只能有一个 Mirror
- gitlab_mirror_id
- mirror_url
- last_update_status
- last_update_at
- last_successful_update_at
- consecutive_failures
- error_message
- created_at, updated_at

**Mapper 方法**:
- 基础 CRUD
- 按同步项目查询
- 按状态查询
- 按失败次数查询
- 更新状态和失败计数

---

### 5. SYNC_EVENT（同步事件）
**字段**:
- id (PK)
- sync_project_id (FK)
- event_type - 事件类型
- event_data (JSON)
- created_at

**Mapper 方法**:
- 基础 CRUD
- 批量插入
- 按项目查询
- 按事件类型查询
- 按时间范围查询
- 事件统计

---

## 模块输出

- ✅ 5 个实体类及其 Mapper 接口
- ✅ 完整的 CRUD 操作
- ✅ 复杂查询和统计功能
- ✅ JSON 字段序列化支持
- ✅ 外键约束和级联处理

---

## 关键决策

1. **集中实现**: 所有实体集中在一个模块实现，避免依赖问题
2. **依赖顺序**: 按照 SYNC_PROJECT → 其他实体的顺序实现
3. **JSON 字段**: 使用 MyBatis-Plus TypeHandler 处理 JSON 序列化
4. **唯一约束**: PUSH_MIRROR_CONFIG.sync_project_id 唯一，确保一个项目只有一个 Mirror
