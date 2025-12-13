# GitLab Mirror MVP - 任务清单

> 基于 Push Mirror 的 GitLab 项目同步工具 MVP 开发任务清单

---

## 📋 模块概览

本 MVP 按以下 8 个模块组织开发任务，每个模块涵盖从数据库到 API 的完整实现：

| 序号 | 模块 | 文件 | 预计时间 | 核心内容 |
|-----|------|------|---------|---------|
| 1 | 基础设施 | [01-infrastructure.md](./01-infrastructure.md) | Week 1 (3-4天) | 项目初始化、日志、配置、数据库连接 |
| 2 | 数据模型层 | [02-data-model.md](./02-data-model.md) | Week 1 (2天) | 5 个核心实体、Mapper、CRUD |
| 3 | GitLab 客户端 | [03-gitlab-client.md](./03-gitlab-client.md) | Week 2 (2天) | GitLab API 封装、认证、重试 |
| 4 | 业务服务层 | [04-business-services.md](./04-business-services.md) | Week 2 (5-6天) | 项目发现、目标管理、Mirror 配置、事件管理 |
| 5 | REST API 层 | [05-rest-api.md](./05-rest-api.md) | Week 3 (2-3天) | RESTful API、认证、统一响应 |
| 6 | CLI 客户端 | [06-cli-client.md](./06-cli-client.md) | Week 3 (2-3天) | Shell 脚本 + Java JAR、命令实现 |
| 7 | 测试和性能优化 | [07-testing.md](./07-testing.md) | Week 4 (3-4天) | 集成测试、性能优化、测试覆盖率 |
| 8 | 部署和文档 | [08-deployment.md](./08-deployment.md) | Week 4 (1-2天) | 构建配置、Docker 部署、文档编写 |

**总预计时间**: 4 周（20-24 个工作日）

---

## 🎯 核心技术栈

- **框架**: Spring Boot 3 + MyBatis-Plus
- **数据库**: MySQL 8.0+
- **构建工具**: Maven/Gradle
- **测试**: JUnit 5 + Mockito + TestContainers
- **部署**: Docker + Docker Compose
- **CLI**: Shell Script + Java JAR

---

## 📦 项目结构

```
gitlab-mirror/
├── server/              # 服务端模块
│   ├── entity/          # 实体类
│   ├── mapper/          # MyBatis Mapper
│   ├── service/         # 业务服务
│   ├── controller/      # REST API
│   └── scheduler/       # 定时任务
├── cli-client/          # CLI 客户端模块
│   ├── client/          # HTTP 客户端
│   └── command/         # 命令实现
├── common/              # 公共模块
│   ├── model/           # 共享模型
│   └── util/            # 工具类
└── scripts/             # Shell 脚本
    └── gitlab-mirror    # CLI 主脚本
```

---

## 🗄️ 核心数据表

| 表名 | 说明 | 依赖 |
|-----|------|------|
| SYNC_PROJECT | 同步项目配置（核心表） | 无 |
| SOURCE_PROJECT_INFO | 源项目信息 | SYNC_PROJECT |
| TARGET_PROJECT_INFO | 目标项目信息 | SYNC_PROJECT |
| PUSH_MIRROR_CONFIG | Push Mirror 配置 | SYNC_PROJECT |
| SYNC_EVENT | 同步事件记录 | SYNC_PROJECT |

**实体依赖关系**: 所有从表都依赖 SYNC_PROJECT 主表的 `project_key` 字段。

---

## 🔄 业务流程

```
1. 项目发现 (定时任务)
   ├─ 从源 GitLab 获取项目列表
   ├─ 应用过滤规则
   └─ 保存到 SYNC_PROJECT

2. 目标项目创建
   ├─ 创建目标分组结构
   └─ 创建空目标项目

3. Mirror 配置
   ├─ 构建 Mirror URL
   ├─ 调用 GitLab API 创建 Mirror
   └─ 触发首次同步

4. Mirror 监控 (定时任务)
   ├─ 轮询 Mirror 状态
   ├─ 检测状态变化
   └─ 记录同步事件
```

---

## 🎨 CLI 命令示例

```bash
# 服务管理
gitlab-mirror start
gitlab-mirror status

# 项目管理
gitlab-mirror projects --status active
gitlab-mirror discover

# Mirror 管理
gitlab-mirror mirrors --status synced
gitlab-mirror mirror group/project --setup

# 事件查询
gitlab-mirror events --project group/project
```

---

## 📊 验收标准

### 功能验收
- ✅ 自动发现源 GitLab 项目
- ✅ 自动创建目标项目和 Mirror 配置
- ✅ 定时轮询 Mirror 状态
- ✅ 完整的事件记录和查询
- ✅ REST API 正常工作
- ✅ CLI 命令完整可用

### 性能验收
- ✅ 支持管理 100+ 项目
- ✅ API 响应时间 < 200ms
- ✅ 支持 10+ 并发 Mirror 配置
- ✅ 测试覆盖率 > 80%

---

## 🚀 快速开始

### 1. 查看具体模块任务
点击上方表格中的文件链接，查看每个模块的详细任务清单。

### 2. 任务执行顺序
虽然按模块组织，但建议按以下顺序执行：

**Week 1**: 基础设施 → 数据模型层
**Week 2**: GitLab 客户端 → 业务服务层
**Week 3**: REST API 层 → CLI 客户端
**Week 4**: 测试和性能优化 → 部署和文档

### 3. 关键依赖
- **数据模型**: 必须按实体依赖顺序创建（SYNC_PROJECT 优先）
- **GitLab 客户端**: 业务服务层的前置依赖
- **REST API**: CLI 客户端的前置依赖

---

## 📝 任务统计

- **总模块数**: 8 个
- **总任务数**: 19 个主任务
- **代码提交数**: ~19 个功能提交
- **核心实体**: 5 个
- **API 端点**: ~15 个
- **CLI 命令**: ~10 个

---

## 🔗 相关文档

### 需求与设计文档
- [需求文档](../REQUIREMENTS.md) - 项目背景、目标用户、核心需求和功能范围
- [技术设计](../PUSH_MIRROR_MVP_DESIGN.md) - Push Mirror 方案的详细设计（系统架构、数据模型、API 设计、CLI 设计）

### 配置与参考
- [配置文件格式及说明](./CONFIGURATION.md) - 完整的配置文件格式、配置项说明和使用示例

### 关键设计章节引用
- **数据模型设计**: 参考 [PUSH_MIRROR_MVP_DESIGN.md - 核心实体及关系](../PUSH_MIRROR_MVP_DESIGN.md#-核心实体及关系)
- **业务流程设计**: 参考 [PUSH_MIRROR_MVP_DESIGN.md - 关键处理流程](../PUSH_MIRROR_MVP_DESIGN.md#-关键处理流程)
- **REST API 设计**: 参考 [PUSH_MIRROR_MVP_DESIGN.md - REST API 设计](../PUSH_MIRROR_MVP_DESIGN.md#-rest-api-设计)
- **CLI 命令设计**: 参考 [PUSH_MIRROR_MVP_DESIGN.md - CLI 命令设计](../PUSH_MIRROR_MVP_DESIGN.md#-cli-命令设计)
- **错误处理策略**: 参考 [PUSH_MIRROR_MVP_DESIGN.md - 错误处理](../PUSH_MIRROR_MVP_DESIGN.md#️-错误处理)
- **日志设计**: 参考 [PUSH_MIRROR_MVP_DESIGN.md - 日志设计](../PUSH_MIRROR_MVP_DESIGN.md#-日志设计)

---

## ⚠️ 重要说明

1. **模块化**: 每个模块相对独立，但存在依赖关系，请按顺序执行
2. **代码复用**: CLI 客户端复用 common 模块的实体和工具类
3. **测试优先**: 每个任务完成后立即编写测试
4. **提交规范**: 使用约定式提交格式（feat/fix/test/docs/chore）
5. **性能关注**: 关注批量操作性能和并发控制

---

*本任务清单基于 Push Mirror 方案的 MVP 版本，聚焦核心功能快速交付。*
