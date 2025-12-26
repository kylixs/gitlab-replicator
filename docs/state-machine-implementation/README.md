# 状态机实现任务清单

本目录包含状态机功能的完整实现任务清单，分为 3 个独立模块。

---

## 模块概览

### 模块 1: 差异状态增强 (Diff Status Enhancement)
**文件**: [01-diff-status-enhancement.md](./01-diff-status-enhancement.md)
**状态**: ⏸️ 待处理 (Pending)
**预计时间**: 2-3天

**目标**: 增强现有 DiffCalculator，支持 7 种差异状态（synced/outdated/ahead/diverged/unknown/target_missing/source_missing），为项目同步提供精确的数据差异判断。

**任务列表**:
- T1.1 扩展现有差异状态枚举（基于 ProjectDiff 和 BranchComparison）
- T1.2 增强 DiffCalculator 的分支比对逻辑（添加时间判断）
- T1.3 增强 DiffCalculator 的项目级状态判断（支持 AHEAD/DIVERGED）
- T1.4 更新项目概览 API 展示新差异状态
- T1.5 前端展示差异状态

---

### 模块 2: 项目状态机 (Project State Machine)
**文件**: [02-project-state-machine.md](./02-project-state-machine.md)
**状态**: ⏸️ 待处理 (Pending)
**预计时间**: 1.5-2天

**目标**: 完善现有 ProjectStateMachine，统一项目状态定义，支持完整生命周期管理。

**任务列表**:
- T2.1 完善现有 ProjectStateMachine 状态定义
- T2.2 清理 SyncProject 实体的过时状态常量
- T2.3 集成状态机到同步流程（基于现有代码改造）
- T2.4 添加项目状态管理 API
- T2.5 前端集成项目状态管理功能

---

### 模块 3: 任务状态机 (Task State Machine)
**文件**: [03-task-state-machine.md](./03-task-state-machine.md)
**状态**: ⏸️ 待处理 (Pending)
**预计时间**: 1.5-2天

**目标**: 创建 TaskStateMachine，增强现有任务状态管理，添加 BLOCKED 和 SCHEDULED 状态。

**任务列表**:
- T3.1 创建 TaskStateMachine 服务类（参考 ProjectStateMachine）
- T3.2 统一 SyncTask 状态常量和添加 BLOCKED 状态
- T3.3 改造调度器使用任务状态机（基于现有 UnifiedSyncScheduler）
- T3.4 改造执行器使用任务状态机（基于现有 PullSyncExecutorService）
- T3.5 添加任务状态管理 API
- T3.6 前端展示任务状态和操作

---

## 模块依赖关系

```
模块 1 (差异状态)
    ↓ (无强依赖，可并行)
模块 2 (项目状态机)
    ↓ (无强依赖，可并行)
模块 3 (任务状态机)
```

**说明**:
- 三个模块之间无强依赖关系，理论上可以并行开发
- 建议按顺序实施，便于集成测试和验证
- 每个模块内的任务有明确的依赖关系，必须按顺序执行

---

## 实施计划

### Week 1: 差异状态增强
- Day 1-2: T1.1 - T1.3（后端核心逻辑）
- Day 3: T1.4（API 集成）
- Day 4: T1.5（前端展示）
- Day 5: 测试和修复

### Week 2: 项目状态机 + 任务状态机
- Day 1-2: T2.1 - T2.3（项目状态机核心）
- Day 3: T3.1 - T3.2（任务状态机核心）
- Day 4: T2.4, T3.3 - T3.4（API 和集成）
- Day 5: T2.5, T3.5 - T3.6（前端集成）

### Week 3: 集成测试和优化
- Day 1-2: 编写集成测试和 E2E 测试
- Day 3: 性能优化和代码审查
- Day 4: 文档更新
- Day 5: 上线准备和验收

**总预计时间**: 5-6 天（基于现有代码改造，不含测试和优化）

## 重要说明

本任务清单基于项目现有代码进行改造，而非从零实现：

**现有代码基础**:
- ✅ `DiffCalculator` - 已实现基础差异计算
- ✅ `ProjectStateMachine` - 已实现基础项目状态机
- ✅ `ProjectDiff` / `BranchComparison` - 已有数据模型
- ✅ `SyncProject` / `SyncTask` - 实体已包含状态字段

**改造策略**:
1. **扩展而非重写** - 在现有类上添加新功能
2. **保持兼容** - 不破坏现有 API 和数据结构
3. **渐进增强** - 逐步添加新状态和逻辑
4. **清理过时** - 移除不再使用的状态常量

---

## 验收标准

### 功能性验收
- [ ] 差异状态计算准确，支持 7 种状态
- [ ] 项目状态机支持 6 种状态和所有转换规则
- [ ] 任务状态机支持 4 种状态和调度/执行流程
- [ ] API 完整，支持状态查询和管理操作
- [ ] 前端正确展示状态并提供管理功能

### 质量验收
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 所有集成测试通过
- [ ] E2E 测试覆盖核心流程
- [ ] 代码符合规范，通过 SonarQube 检查
- [ ] 文档完整（API 文档、设计文档、操作手册）

### 性能验收
- [ ] 差异状态计算耗时 < 500ms
- [ ] API 响应时间 < 200ms (P95)
- [ ] 调度器延迟 < 1分钟
- [ ] 前端页面加载时间 < 2秒

---

## 参考文档

- [状态机设计文档](../state-machine-design.md) - 完整设计方案
- [任务清单模板](../task-template.md) - 任务编写规范
- [数据库设计](../../PUSH_MIRROR_MVP_DESIGN.md#核心实体及关系) - 数据模型
- [API 设计](../../PUSH_MIRROR_MVP_DESIGN.md#rest-api-设计) - API 规范

---

## 注意事项

### 数据库迁移
- 所有数据库变更必须使用迁移脚本
- 迁移脚本必须支持幂等执行
- 生产环境迁移前需在测试环境验证

### 状态转换安全
- 所有状态转换必须在事务中执行
- 使用乐观锁防止并发冲突
- 状态转换失败不应影响业务流程

### 测试策略
- 每个任务完成后立即编写单元测试
- 模块完成后编写集成测试
- 所有模块完成后编写 E2E 测试

### 日志和监控
- 所有状态转换必须记录日志
- 关键操作记录审计日志
- 添加监控指标（Prometheus/Grafana）

---

**文档版本**: v1.0
**创建时间**: 2025-12-26
**最后更新**: 2025-12-26
**维护者**: GitLab Mirror Team
