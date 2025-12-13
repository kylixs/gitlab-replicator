# GitLab 同步器项目文档

## 📚 文档导航

### 核心文档

| 文档 | 说明 | 适用场景 |
|------|------|---------|
| **[PUSH_MIRROR_MVP_FEATURES.md](PUSH_MIRROR_MVP_FEATURES.md)** | MVP 功能清单（Push Mirror 方案） | 快速了解 MVP 范围和功能点 |
| **[PUSH_MIRROR_MVP_DESIGN.md](PUSH_MIRROR_MVP_DESIGN.md)** | Push Mirror 方案详细设计 | 了解技术实现和关键流程 |
| **[REQUIREMENTS.md](REQUIREMENTS.md)** | 完整需求清单 | 了解长期规划和完整功能 |
| **[TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md)** | 完整技术设计 | 了解模块化架构和扩展方案 |

### 参考文档

| 文档 | 说明 |
|------|------|
| **[PUSH_MIRROR_ANALYSIS.md](PUSH_MIRROR_ANALYSIS.md)** | Push Mirror 可行性分析 |
| **[PULL_MIRROR_ANALYSIS.md](PULL_MIRROR_ANALYSIS.md)** | Pull Mirror 功能分析 |
| **[DATA_CONSISTENCY.md](DATA_CONSISTENCY.md)** | 数据一致性快速参考 |
| **[MONITORING_LOGGING.md](MONITORING_LOGGING.md)** | 监控与日志快速参考 |
| **[CLONE_PUSH_OPTIMIZATION.md](CLONE_PUSH_OPTIMIZATION.md)** | Clone & Push 优化方案 |

---

## 🎯 项目概述

**目标**：开发 GitLab 项目同步工具，实现从源 GitLab 到目标 GitLab 的自动同步

**MVP 方案**：基于 **Push Mirror** 的批量配置和管理工具

---

## 🚀 快速开始

### MVP 方案选择

**默认：Push Mirror 方案**

✅ **优势**：
- 利用 GitLab 原生功能，稳定可靠
- 自动持续同步（1-5 分钟延迟）
- 不占用本地存储
- 开发周期短（4 周）

⚠️ **前提条件**：
- 需要源 GitLab 管理员权限
- 仅同步 Git 仓库（不含 Issues/MR/Wiki）

📖 **详细文档**：[PUSH_MIRROR_MVP_DESIGN.md](PUSH_MIRROR_MVP_DESIGN.md)

---

## 📋 MVP 功能范围

### P0 功能（核心必需）

1. **配置管理**：YAML 配置文件、连接测试
2. **项目发现**：获取源项目列表、过滤规则
3. **目标准备**：创建分组和空项目
4. **Push Mirror 配置**：批量配置、首次同步触发
5. **Mirror 管理**：健康检查、手动触发同步
6. **状态管理**：SQLite 存储、状态查询
7. **日志记录**：结构化 JSON 日志
8. **错误处理**：重试机制、API 限流处理
9. **进度监控**：实时进度显示
10. **报告生成**：配置报告、状态报告
11. **CLI 命令**：run、status、retry、cleanup
12. **并发控制**：批量配置、并发监控

完整功能清单见：[PUSH_MIRROR_MVP_FEATURES.md](PUSH_MIRROR_MVP_FEATURES.md)

---

## 🔄 核心流程

### 1. 整体执行流程

```
加载配置 → 测试连接 → 获取项目列表 → 应用过滤
→ 创建目标分组 → 创建目标项目 → 批量配置 Push Mirror
→ 触发首次同步 → 监控同步进度 → 生成报告
```

### 2. Push Mirror 配置流程

```
遍历项目 → 构建目标 URL → 调用 API 创建 Mirror
→ 保存 Mirror ID → 触发首次同步 → 更新状态
```

详细流程图见：[PUSH_MIRROR_MVP_DESIGN.md - 关键处理流程](PUSH_MIRROR_MVP_DESIGN.md#关键处理流程)

---

## 🛠️ 技术栈

**推荐：Python**

```
核心库：
- python-gitlab: GitLab API 客户端
- click: CLI 框架
- sqlalchemy: ORM (SQLite)
- pyyaml: 配置文件解析
- loguru: 日志库
- rich: 进度条和彩色输出
```

---

## 📅 开发计划

| 阶段 | 时间 | 内容 |
|------|------|------|
| **Week 1** | 基础框架 | 配置管理、API 客户端、项目发现、目标准备 |
| **Week 2** | Push Mirror 核心 | Mirror 配置、同步触发、状态监控、数据库 |
| **Week 3** | 错误处理和完善 | 重试机制、进度监控、状态查询、重试命令 |
| **Week 4** | 测试和文档 | 报告生成、集成测试、文档编写、性能优化 |

---

## 📊 验收标准

### 功能验收

- ✅ 批量配置 Push Mirror（至少 10 个项目）
- ✅ 监控首次同步进度
- ✅ 查询 Mirror 状态和生成报告
- ✅ 重试失败的 Mirror

### 性能验收

- ✅ 处理至少 100 个项目
- ✅ 支持 5-10 个并发配置
- ✅ 配置单个 Mirror < 5 秒

---

## 🎯 后续版本规划

### V2.0: 完整同步方案

**技术方案**：Clone & Push + API 同步

**新增功能**：
- Issues 同步（含评论）
- Merge Requests 同步（含评论、审批）
- Wiki 同步
- 用户映射
- 一致性巡检
- Web UI

**开发周期**：6-8 周

详细设计见：[TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md)

---

## 🔑 关键决策

### 为什么选择 Push Mirror 作为 MVP？

1. **快速验证**：4 周即可上线，快速验证需求
2. **稳定可靠**：利用 GitLab 原生功能
3. **低维护成本**：配置后自动持续同步
4. **满足核心需求**：Git 仓库同步是首要需求

### Push Mirror 的局限性

- ❌ 无法同步 Issues/MR/Wiki（后续版本解决）
- ❌ 无法同步 Git LFS 对象（需手动处理）
- ⚠️ 需要源 GitLab 管理员权限
- ⚠️ 依赖 GitLab 调度（1-5 分钟延迟）

---

## 📖 使用示例

### 1. 配置文件 (config.yml)

```yaml
source:
  url: https://source-gitlab.com
  token: ${SOURCE_GITLAB_TOKEN}

target:
  url: https://target-gitlab.com
  token: ${TARGET_GITLAB_TOKEN}

sync:
  groups:
    - path: "group1"
      include_subgroups: true

  filters:
    exclude_archived: true
    exclude_empty: true
```

### 2. CLI 命令

```bash
# 测试连接
push-mirror-sync test-connection

# 发现项目（预览）
push-mirror-sync discover

# 执行配置 Mirror
push-mirror-sync run --config config.yml

# 查看状态
push-mirror-sync status

# 重试失败项目
push-mirror-sync retry --all-failed

# 清理 Mirror
push-mirror-sync cleanup --confirm
```

---

## 🤝 贡献指南

（待补充）

---

## 📄 License

（待定）

---

**最后更新**: 2025-12-13
**文档版本**: v1.0
**项目状态**: 设计阶段
