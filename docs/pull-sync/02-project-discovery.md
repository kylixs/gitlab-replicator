# 模块 2: 项目发现扩展 (Project Discovery Extension)

**状态**: ✅ 已完成 (Completed)

**目标**: 扩展项目发现功能，支持自动发现和初始化 pull_sync 类型项目。

**预计时间**: 1天

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

### T2.1 扩展 ProjectDiscoveryService 支持 Pull 同步
**状态**: ✅ 已完成
**依赖**: 模块1 - 数据模型扩展

**任务目标**:
- 扩展 `ProjectDiscoveryService.discoverProjects()` 方法
- 支持创建 pull_sync 类型项目
- 自动创建 PULL_SYNC_CONFIG 和 SYNC_TASK 记录
- 配置默认优先级和本地仓库路径
  - 参考 [PULL_SYNC_DESIGN.md - 项目发现与任务初始化](../PULL_SYNC_DESIGN.md#流程-1-项目发现与任务初始化)

**核心逻辑**:
- 查询源 GitLab 项目并应用过滤规则
- 为新项目创建: SYNC_PROJECT, SOURCE_PROJECT_INFO, PULL_SYNC_CONFIG, SYNC_TASK
- 默认配置: priority=normal, enabled=true, task_type=pull, status=waiting, next_run_at=NOW

**验收标准**:
- 支持指定同步方式（push_mirror/pull_sync）
- 自动创建完整的配置和任务记录
- 本地仓库路径自动生成（base-path + project-key）
- repository_size 正确填充
- 事务一致性保证

**测试要求**:
- 测试发现 pull_sync 项目
- 测试完整性（所有相关表都有记录）
- 测试默认值（priority, enabled, next_run_at）
- 测试事务回滚
- 测试幂等性（重复发现不重复创建）

**提交**: `feat(discovery): support pull_sync project discovery`

---

### T2.2 配置项目同步方式选择
**状态**: ✅ 已完成 (Completed)
**依赖**: T2.1

**任务目标**:
- 添加配置项支持选择默认同步方式
- 支持按分组配置不同的同步方式
- 更新配置类 `GitLabMirrorProperties`

**配置项**:
- default-sync-method: 默认同步方式 (push_mirror/pull_sync)
- sync-methods: 按分组配置同步方式（支持通配符匹配）

**验收标准**:
- 配置正确解析
- 支持通配符匹配分组路径
- 默认方式生效
- 优先级规则正确（具体分组 > 默认）

**测试要求**:
- 测试配置解析
- 测试分组路径匹配
- 测试默认方式
- 测试优先级规则

**提交**: `feat(config): add sync method selection config`

---

### T2.3 添加 Pull 配置初始化服务
**状态**: ✅ 已完成
**依赖**: T2.1

**任务目标**:
- 创建 `PullSyncConfigService` 服务类
- 实现配置初始化方法
- 实现优先级更新方法
- 实现启用/禁用方法

**核心方法**:
- initializeConfig() - 初始化 Pull 配置和本地仓库路径
- updatePriority() - 更新优先级
- setEnabled() - 启用/禁用
- getConfig() - 查询配置

**验收标准**:
- 初始化方法正确创建配置
- 本地仓库路径自动生成
- 优先级更新生效
- 启用/禁用状态更新正确

**测试要求**:
- 测试配置初始化
- 测试本地路径生成规则
- 测试优先级更新
- 测试启用/禁用
- 测试并发安全性

**提交**: `feat(pull): add PullSyncConfigService`

---

## 模块输出

- ✅ ProjectDiscoveryService 支持 pull_sync 类型
- ✅ 自动创建 PULL_SYNC_CONFIG 和 SYNC_TASK
- ✅ 配置文件支持选择同步方式
- ✅ PullSyncConfigService 服务类
- ✅ 完整的事务处理和幂等性保证

---

## 关键决策

1. **同步方式选择**: 支持全局默认 + 按分组配置
2. **自动初始化**: 项目发现时自动创建所有必需记录
3. **本地路径**: 自动生成 `base-path/project-key` 格式
4. **默认优先级**: 新项目默认 `normal` 优先级
5. **立即调度**: 新项目 `next_run_at=NOW`，立即可被调度

---

## 配置示例

- default-sync-method: pull_sync
- pull.local-repo.base-path: ~/.gitlab-sync/repos
- pull.default-priority: normal

---

## 注意事项

1. **事务一致性**: 确保创建 SYNC_PROJECT、CONFIG、TASK 在同一事务
2. **幂等性**: 重复发现同一项目不重复创建
3. **路径生成**: 确保本地路径不冲突（使用项目唯一标识）
4. **repository_size**: 从 GitLab API 获取并保存
5. **错误处理**: 创建失败时正确回滚事务
