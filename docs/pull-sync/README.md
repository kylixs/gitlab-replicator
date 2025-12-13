# Pull 同步方案 - 任务清单

> 基于 Clone & Push 策略的 Pull 同步方案开发任务清单

---

## 📋 模块概览

本开发任务按以下 6 个模块组织，基于 Push Mirror MVP 基础之上扩展：

| 序号 | 模块 | 文件 | 预计时间 | 核心内容 |
|-----|------|------|---------|---------|
| 1 | 数据模型扩展 | [01-data-model.md](./01-data-model.md) | 1-2天 | PULL_SYNC_CONFIG、SYNC_TASK 表 |
| 2 | 项目发现扩展 | [02-project-discovery.md](./02-project-discovery.md) | 1天 | 支持 pull_sync 类型项目自动发现 |
| 3 | Pull 同步执行器 | [03-pull-executor.md](./03-pull-executor.md) | 3-4天 | Git 命令封装、同步执行逻辑 |
| 4 | 统一任务调度器 | [04-scheduler.md](./04-scheduler.md) | 2-3天 | 统一调度 Push/Pull 任务 |
| 5 | Webhook 准实时同步 | [05-webhook.md](./05-webhook.md) | 1-2天 | 接收 GitLab Push 事件，触发同步 |
| 6 | REST API 和 CLI | [06-api-cli.md](./06-api-cli.md) | 2-3天 | Pull 同步 API 和命令 |

**总预计时间**: 2-3 周（10-15 个工作日）

---

## 🎯 核心特性

### 与 Push Mirror 的区别

| 特性 | Push Mirror | Pull 同步 |
|-----|-------------|----------|
| 同步方式 | 源 GitLab 自动推送 | 本地克隆 + 推送到目标 |
| 权限要求 | 需管理员权限 | 仅需项目访问权限 |
| 调度控制 | 依赖 GitLab | 完全可控（优先级、峰谷） |
| 任务模型 | 轮询 Mirror 状态 | 直接执行同步 |
| 实时性 | 秒级 | 准实时（Webhook + 定时） |

### 核心优势

1. **统一任务表** - Push 和 Pull 使用同一个 SYNC_TASK 表，1个项目=1条任务记录
2. **状态循环** - waiting → pending → running → waiting，避免任务表膨胀
3. **全局配置** - 高峰/低峰、并发数、间隔等全局配置
4. **准实时同步** - Webhook + 定时调度（防抖2分钟）
5. **优先级调度** - critical/high/normal/low 四级优先级
6. **变更检测优化** - git ls-remote 快速检测，70-90% 无变更跳过

---

## 🗄️ 新增数据表

| 表名 | 说明 | 关系 |
|-----|------|------|
| PULL_SYNC_CONFIG | Pull 同步配置 | 1:1 SYNC_PROJECT |
| SYNC_TASK | 统一同步任务表 | 1:1 SYNC_PROJECT |

**注意**: SYNC_TASK 表统一管理 Push 和 Pull 任务，通过 `task_type` 字段区分。

---

## 🔄 业务流程

```
1. 项目发现（扩展）
   ├─ 发现 pull_sync 类型项目
   ├─ 创建 PULL_SYNC_CONFIG
   └─ 创建 SYNC_TASK（task_type=pull）

2. 任务调度（统一调度器）
   ├─ 每分钟触发
   ├─ 查询 waiting 状态任务
   ├─ 按优先级排序，峰谷并发控制
   └─ 更新状态：waiting → pending

3. Pull 同步执行
   ├─ 检查/创建目标项目
   ├─ git ls-remote 检查变更
   ├─ git clone/update 本地仓库
   ├─ git push 推送到目标
   └─ 更新状态：running → waiting

4. Webhook 准实时同步
   ├─ 接收源 GitLab Push 事件
   ├─ 自动初始化未配置项目
   ├─ 防抖检查（2分钟）
   └─ 更新 next_run_at=NOW 触发立即调度
```

---

## 🎨 CLI 命令示例

```bash
# Pull 同步任务管理
gitlab-mirror tasks list --type pull
gitlab-mirror tasks show <task-id>
gitlab-mirror tasks trigger <project-key>

# Pull 配置管理
gitlab-mirror pull-sync config <project-key>
gitlab-mirror pull-sync priority <project-key> <critical|high|normal|low>

# 进度和统计
gitlab-mirror pull-sync progress
gitlab-mirror pull-sync stats

# 磁盘管理
gitlab-mirror pull-sync disk usage
gitlab-mirror pull-sync disk cleanup <project-key>
```

---

## 📊 验收标准

### 功能验收
- ✅ 自动发现并初始化 pull_sync 项目
- ✅ 统一调度器正常调度 Push/Pull 任务
- ✅ Pull 同步执行成功（首次 + 增量）
- ✅ Webhook 准实时同步生效
- ✅ 优先级和峰谷调度正常
- ✅ 变更检测优化生效（跳过无变更项目）
- ✅ 错误重试和自动禁用

### 性能验收
- ✅ 支持 200+ 项目同步
- ✅ 无变更项目检测 <1秒
- ✅ 高峰期并发控制生效（3个）
- ✅ Webhook 响应时间 <100ms

---

## 🚀 快速开始

### 1. 前置条件
- ✅ Push Mirror MVP 已完成
- ✅ 数据库、配置、日志等基础设施就绪
- ✅ GitLab 客户端、项目发现、目标管理等服务可复用

### 2. 任务执行顺序

**Week 1**: 数据模型扩展 → 项目发现扩展 → Pull 同步执行器（开始）
**Week 2**: Pull 同步执行器（完成）→ 统一任务调度器
**Week 3**: Webhook 准实时同步 → REST API 和 CLI

### 3. 关键依赖

- **数据模型**: SYNC_TASK 表是核心，需先创建
- **项目发现**: 扩展 ProjectDiscoveryService 支持 pull_sync
- **调度器**: 统一调度 Push 和 Pull，需适配现有 Push Mirror 轮询
- **执行器**: 依赖 TargetProjectManagementService 创建目标项目

---

## 📝 任务统计

- **总模块数**: 6 个
- **总任务数**: ~15 个主任务
- **新增实体**: 2 个（PULL_SYNC_CONFIG, SYNC_TASK）
- **新增 API 端点**: ~10 个
- **新增 CLI 命令**: ~8 个

---

## 🔗 相关文档

### 设计文档
- [Pull 同步方案设计](../PULL_SYNC_DESIGN.md) - Pull 同步的完整设计（系统架构、数据模型、流程设计）
- [Push Mirror MVP 设计](../PUSH_MIRROR_MVP_DESIGN.md) - Push Mirror 方案设计（可复用部分）

### Push Mirror MVP 任务清单
- [MVP 任务清单](../mvp/README.md) - Push Mirror 基础任务
- [基础设施](../mvp/01-infrastructure.md) - 可复用
- [GitLab 客户端](../mvp/03-gitlab-client.md) - 可复用
- [业务服务层](../mvp/04-business-services.md) - 部分复用

---

## ⚠️ 重要说明

1. **渐进式开发**: 基于 Push Mirror MVP，逐步添加 Pull 同步能力
2. **向后兼容**: 不影响现有 Push Mirror 功能
3. **统一任务表**: Push 和 Pull 任务统一管理，1个项目=1条记录
4. **配置分离**: 全局配置 vs 项目配置，简化管理
5. **准实时**: Webhook + 定时调度结合，平衡实时性和性能

---

## 📈 里程碑

### Milestone 1: 数据模型和项目发现（3-4天）
- 完成 PULL_SYNC_CONFIG 和 SYNC_TASK 表创建
- 扩展项目发现支持 pull_sync 类型

### Milestone 2: Pull 同步执行（5-7天）
- 完成 Git 命令封装
- 实现首次同步和增量同步
- 变更检测优化

### Milestone 3: 统一调度和 Webhook（3-4天）
- 统一调度器调度 Push/Pull 任务
- Webhook 准实时同步

### Milestone 4: API 和 CLI（2-3天）
- REST API 接口
- CLI 命令实现

---

*本任务清单基于 Pull 同步方案设计，在 Push Mirror MVP 基础上扩展，聚焦 Pull 同步核心功能。*
