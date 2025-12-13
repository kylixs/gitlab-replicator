# 模块 4: 业务服务层 (Business Services)

**目标**: 实现核心业务逻辑，包括项目发现、目标项目管理、Push Mirror 管理和事件管理。

**预计时间**: Week 2 (5-6天)

**业务流程设计参考**: [PUSH_MIRROR_MVP_DESIGN.md - 关键处理流程](../PUSH_MIRROR_MVP_DESIGN.md#-关键处理流程)

---

## 子模块清单

### 4.1 项目发现服务 (Project Discovery Service)

#### T4.1 实现项目发现业务逻辑
**状态**: ✅ 已完成
**依赖**: T2.1 (数据模型), T3.2 (GitLab API)

**任务目标**:
- 从 GitLab 拉取项目列表
- 应用配置的过滤规则
- 创建或更新 SYNC_PROJECT 记录
- 保存源项目信息到 SOURCE_PROJECT_INFO
- 记录项目发现事件到 SYNC_EVENT
- 处理新增、更新、删除的项目
- 实现定时调度（Spring Scheduler）
- 防止任务重复执行（分布式锁）

**验收标准**:
- 正确发现所有符合条件的项目
- 过滤规则正确应用
- 数据库记录准确
- 增量更新正常（不重复创建）
- 事件记录完整
- 定时任务正常执行
- 支持手动触发

**测试要求**:
- 测试首次发现项目
- 测试增量发现
- 测试更新已存在项目
- 测试过滤规则
- 测试定时调度
- 测试分布式锁
- 测试大量项目场景

**提交**: `feat(discovery): implement project discovery service with scheduler`

---

### 4.2 目标项目管理服务 (Target Project Management Service)

#### T4.2 实现目标项目管理业务逻辑
**依赖**: T2.1 (数据模型), T3.2 (GitLab API)

**任务目标**:
- 按源项目路径创建对应的目标分组结构
- 创建目标项目（空项目）
- 保存目标项目信息到 TARGET_PROJECT_INFO
- 处理分组/项目已存在的情况
- 实现创建失败重试机制
- 更新目标项目状态
- 定期检查目标项目是否存在
- 检测项目被意外删除
- 支持批量创建和检查

**验收标准**:
- 目标分组结构与源一致
- 目标项目创建成功
- 状态记录准确
- 已存在时正确处理
- 失败后能重试
- 批量操作性能良好

**测试要求**:
- 测试创建单个项目
- 测试创建嵌套分组项目
- 测试处理已存在项目
- 测试重试机制
- 测试状态检查
- 测试批量创建

**提交**: `feat(target): implement target project management with retry`

---

### 4.3 Push Mirror 管理服务 (Push Mirror Management Service)

#### T4.3 实现 Push Mirror 配置和监控
**依赖**: T2.1 (数据模型), T3.2 (GitLab API), T4.2 (目标项目)

**任务目标**:
- **Mirror 配置**:
  - 为同步项目配置 Push Mirror
  - 构建 Mirror URL（包含认证 Token）
  - 调用 GitLab API 创建 Mirror
  - 保存 Mirror 配置到 PUSH_MIRROR_CONFIG
  - 触发首次同步
  - 处理配置失败和重试
  - 批量配置（并发控制）

- **Mirror 状态监控**:
  - 轮询查询 Mirror 状态
  - 检测状态变化
  - 更新 PUSH_MIRROR_CONFIG 状态
  - 记录同步事件到 SYNC_EVENT
  - 批量轮询优化性能
  - 更新连续失败计数
  - 定时调度轮询任务

**验收标准**:
- Mirror 配置成功
- Mirror URL 构建正确（Token 不泄露）
- 数据库记录准确
- 首次同步触发成功
- 失败能重试
- 批量配置支持 5-10 并发
- 状态轮询准确
- 定时轮询正常

**测试要求**:
- 测试 Mirror 配置流程
- 测试 URL 构建（Token 安全）
- 测试批量配置
- 测试并发控制
- 测试状态轮询
- 测试状态变化检测
- 测试定时调度

**提交**: `feat(mirror): implement mirror config and monitoring with scheduler`

---

### 4.4 事件管理服务 (Event Management Service)

#### T4.4 实现事件记录和查询
**依赖**: T2.1 (数据模型)

**任务目标**:
- 提供统一的事件记录接口
- 自动填充时间戳和上下文信息
- 支持批量记录事件
- 实现事件数据验证
- 优化事件插入性能
- 实现多维度事件查询（按项目、类型、时间范围）
- 实现事件统计（延迟、耗时、频率）
- 实现关联事件分析（Push → Sync 延迟）

**验收标准**:
- 事件记录准确
- 时间戳自动填充
- 批量记录高效
- 查询功能完整
- 统计数据准确
- 性能良好（高并发）

**测试要求**:
- 测试单个事件记录
- 测试批量事件记录
- 测试并发记录
- 测试多维度查询
- 测试统计计算
- 测试性能

**提交**: `feat(event): implement event recording and query service`

---

## 业务流程图

### 完整同步流程
```
1. 项目发现
   ├─ 从 GitLab 获取项目列表
   ├─ 应用过滤规则
   ├─ 保存到 SYNC_PROJECT 和 SOURCE_PROJECT_INFO
   └─ 记录发现事件

2. 目标项目创建
   ├─ 创建目标分组结构
   ├─ 创建目标空项目
   ├─ 保存到 TARGET_PROJECT_INFO
   └─ 记录创建事件

3. Mirror 配置
   ├─ 构建 Mirror URL
   ├─ 调用 GitLab API 创建 Mirror
   ├─ 保存到 PUSH_MIRROR_CONFIG
   ├─ 触发首次同步
   └─ 记录配置事件

4. Mirror 监控
   ├─ 定时轮询 Mirror 状态
   ├─ 检测状态变化
   ├─ 更新数据库
   └─ 记录同步事件
```

---

## 模块输出

- ✅ 项目发现服务（支持定时调度）
- ✅ 目标项目管理服务（创建 + 状态检查）
- ✅ Push Mirror 管理服务（配置 + 监控 + 调度）
- ✅ 事件管理服务（记录 + 查询 + 统计）

---

## 关键决策

1. **定时调度**: 使用 Spring Scheduler，支持动态调整间隔
2. **分布式锁**: 防止定时任务重复执行
3. **批量配置**: 支持 5-10 个并发，避免 API 限流
4. **状态轮询**: 每 1-2 分钟轮询一次 Mirror 状态
5. **事件记录**: 异步批量插入，提高性能
