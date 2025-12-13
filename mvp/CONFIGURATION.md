# 配置文件格式及说明

本文档描述 GitLab Mirror 工具的配置文件格式和详细说明。

---

## 📂 配置文件路径

**默认路径**: `/etc/gitlab-mirror/config.yml`

**环境变量**: `GITLAB_MIRROR_CONFIG`

**优先级**: 环境变量 > 默认路径

---

## 📋 完整配置示例

```yaml
# GitLab 源和目标配置
source:
  url: https://source.gitlab.com
  token: ${SOURCE_GITLAB_TOKEN}  # 环境变量

target:
  url: https://target.gitlab.com
  token: ${TARGET_GITLAB_TOKEN}  # 环境变量

# 同步配置（动态拉取）
sync:
  # 包含的分组路径（支持通配符）
  include_groups:
    - "group1/**"           # 包含 group1 及所有子分组
    - "group2/subgroup"     # 仅包含特定子分组
    - "group3"              # 包含 group3（不含子分组）

  # 排除的分组路径
  exclude_groups:
    - "group1/archived/**"
    - "*/test-*"            # 排除所有 test- 开头的分组

  # 项目过滤规则
  filters:
    exclude_archived: true  # 排除归档项目
    exclude_empty: true     # 排除空仓库
    min_activity_days: 30   # 排除超过30天无活动的项目（可选）

  # Push Mirror 配置选项
  mirror:
    enabled: true

# 定时任务配置
scheduler:
  # 项目发现任务（拉取新项目）
  project_discovery:
    enabled: true
    interval: 300  # 间隔时间（秒），默认 5 分钟

  # Mirror 状态轮询任务
  mirror_polling:
    enabled: true
    interval: 30   # 间隔时间（秒），默认 30 秒

  # 一致性检查任务（可选）
  consistency_check:
    enabled: false
    interval: 3600  # 间隔时间（秒），默认 1 小时

# Webhook 配置（可选）
webhook:
  enabled: false
  port: 9000
  path: /webhooks/gitlab
  secret: ${WEBHOOK_SECRET}  # Webhook 验证密钥

# 数据库配置
database:
  type: sqlite
  path: /var/lib/gitlab-mirror/data.db
  backup:
    enabled: true
    interval: 86400  # 每天备份
    keep_days: 7     # 保留 7 天

# 日志配置
logging:
  level: INFO  # DEBUG, INFO, WARN, ERROR
  file: /var/log/gitlab-mirror/service.log
  format: json  # json 或 text
  rotation:
    max_size: 100  # MB
    max_files: 10

# API 服务配置
api:
  host: 0.0.0.0
  port: 8080
  auth:
    enabled: true
    tokens:
      - ${API_TOKEN_1}  # CLI 使用的 API Token
      - ${API_TOKEN_2}  # 可选的第二个 Token

# 性能配置
performance:
  project_discovery_concurrency: 5   # 项目发现并发数
  mirror_setup_concurrency: 10       # Mirror 配置并发数
  mirror_polling_batch_size: 50      # Mirror 轮询批次大小
  api_rate_limit_delay: 0.1          # API 限流延迟（秒）
```

---

## 🔧 最小配置示例

```yaml
# 最小配置（仅必需字段）
source:
  url: https://source.gitlab.com
  token: ${SOURCE_GITLAB_TOKEN}

target:
  url: https://target.gitlab.com
  token: ${TARGET_GITLAB_TOKEN}

sync:
  include_groups:
    - "**"  # 包含所有分组
```

---

## 📖 配置项详细说明

### 1. GitLab 配置

#### source / target

**source.url** (必需)
- 类型: String
- 说明: 源 GitLab 实例的 URL
- 示例: `https://source.gitlab.com`

**source.token** (必需)
- 类型: String
- 说明: 源 GitLab 访问 Token
- 权限要求: `api`, `read_repository`, `write_repository`
- 支持环境变量: `${SOURCE_GITLAB_TOKEN}`

**target.url** (必需)
- 类型: String
- 说明: 目标 GitLab 实例的 URL
- 示例: `https://target.gitlab.com`

**target.token** (必需)
- 类型: String
- 说明: 目标 GitLab 访问 Token
- 权限要求: `api`, `read_repository`, `write_repository`
- 支持环境变量: `${TARGET_GITLAB_TOKEN}`

---

### 2. 同步配置

#### sync.include_groups

- 类型: Array[String]
- 说明: 包含的分组路径列表
- 支持通配符:
  - `**`: 递归包含所有子分组
  - `*`: 匹配单层路径
- 示例:
  ```yaml
  include_groups:
    - "group1/**"        # group1 及所有子分组
    - "group2/subgroup"  # 仅特定子分组
    - "group3"           # 仅 group3（不含子分组）
  ```

#### sync.exclude_groups

- 类型: Array[String]
- 说明: 排除的分组路径列表
- 优先级: 高于 `include_groups`
- 示例:
  ```yaml
  exclude_groups:
    - "group1/archived/**"  # 排除归档分组
    - "*/test-*"            # 排除所有 test- 开头的分组
  ```

#### sync.filters

**exclude_archived**
- 类型: Boolean
- 默认值: `true`
- 说明: 是否排除归档项目

**exclude_empty**
- 类型: Boolean
- 默认值: `true`
- 说明: 是否排除空仓库

**min_activity_days**
- 类型: Integer
- 默认值: `null`（不限制）
- 说明: 排除超过指定天数无活动的项目
- 示例: `30` (排除超过30天无活动的项目)

#### sync.mirror

**enabled**
- 类型: Boolean
- 默认值: `true`
- 说明: 是否启用 Push Mirror 同步

---

### 3. 定时任务配置

#### scheduler.project_discovery

**enabled**
- 类型: Boolean
- 默认值: `true`
- 说明: 是否启用项目发现定时任务

**interval**
- 类型: Integer
- 单位: 秒
- 默认值: `300`（5分钟）
- 说明: 项目发现任务执行间隔
- 建议值: 300-1800（5-30分钟）

#### scheduler.mirror_polling

**enabled**
- 类型: Boolean
- 默认值: `true`
- 说明: 是否启用 Mirror 状态轮询任务

**interval**
- 类型: Integer
- 单位: 秒
- 默认值: `30`
- 说明: Mirror 轮询任务执行间隔
- 建议值: 30-120（30秒-2分钟）

#### scheduler.consistency_check

**enabled**
- 类型: Boolean
- 默认值: `false`
- 说明: 是否启用一致性检查任务（可选）

**interval**
- 类型: Integer
- 单位: 秒
- 默认值: `3600`（1小时）
- 说明: 一致性检查任务执行间隔

---

### 4. Webhook 配置（可选）

#### webhook.enabled

- 类型: Boolean
- 默认值: `false`
- 说明: 是否启用 Webhook 接收

#### webhook.port

- 类型: Integer
- 默认值: `9000`
- 说明: Webhook 服务监听端口

#### webhook.path

- 类型: String
- 默认值: `/webhooks/gitlab`
- 说明: Webhook 接收路径

#### webhook.secret

- 类型: String
- 说明: Webhook 验证密钥
- 支持环境变量: `${WEBHOOK_SECRET}`

---

### 5. 数据库配置

#### database.type

- 类型: String
- 默认值: `sqlite`
- 支持值: `sqlite`, `mysql`（MVP 仅支持 MySQL）

#### database.path

- 类型: String
- 说明: 数据库文件路径（SQLite）或连接字符串（MySQL）
- 示例:
  - SQLite: `/var/lib/gitlab-mirror/data.db`
  - MySQL: `jdbc:mysql://localhost:3306/gitlab_mirror`

#### database.backup

**enabled**
- 类型: Boolean
- 默认值: `true`
- 说明: 是否启用数据库自动备份

**interval**
- 类型: Integer
- 单位: 秒
- 默认值: `86400`（1天）
- 说明: 备份执行间隔

**keep_days**
- 类型: Integer
- 默认值: `7`
- 说明: 备份保留天数

---

### 6. 日志配置

#### logging.level

- 类型: String
- 默认值: `INFO`
- 支持值: `DEBUG`, `INFO`, `WARN`, `ERROR`
- 说明: 日志级别

#### logging.file

- 类型: String
- 默认值: `/var/log/gitlab-mirror/service.log`
- 说明: 日志文件路径

#### logging.format

- 类型: String
- 默认值: `json`
- 支持值: `json`, `text`
- 说明: 日志输出格式

#### logging.rotation

**max_size**
- 类型: Integer
- 单位: MB
- 默认值: `100`
- 说明: 单个日志文件最大大小

**max_files**
- 类型: Integer
- 默认值: `10`
- 说明: 保留的日志文件数量

---

### 7. API 服务配置

#### api.host

- 类型: String
- 默认值: `0.0.0.0`
- 说明: API 服务监听地址

#### api.port

- 类型: Integer
- 默认值: `8080`
- 说明: API 服务监听端口

#### api.auth

**enabled**
- 类型: Boolean
- 默认值: `true`
- 说明: 是否启用 API Token 认证

**tokens**
- 类型: Array[String]
- 说明: 有效的 API Token 列表
- 支持环境变量: `${API_TOKEN_1}`
- 示例:
  ```yaml
  tokens:
    - ${API_TOKEN_1}
    - ${API_TOKEN_2}
  ```

---

### 8. 性能配置

#### performance.project_discovery_concurrency

- 类型: Integer
- 默认值: `5`
- 说明: 项目发现并发数
- 建议值: 5-10

#### performance.mirror_setup_concurrency

- 类型: Integer
- 默认值: `10`
- 说明: Mirror 配置并发数
- 建议值: 5-10

#### performance.mirror_polling_batch_size

- 类型: Integer
- 默认值: `50`
- 说明: Mirror 轮询批次大小
- 建议值: 50-100

#### performance.api_rate_limit_delay

- 类型: Float
- 单位: 秒
- 默认值: `0.1`
- 说明: API 调用间隔延迟（避免限流）
- 建议值: 0.1-0.5

---

## 🔒 环境变量支持

配置文件支持通过环境变量替换敏感信息：

**语法**: `${ENV_VAR_NAME}`

**示例**:
```yaml
source:
  token: ${SOURCE_GITLAB_TOKEN}

target:
  token: ${TARGET_GITLAB_TOKEN}

api:
  auth:
    tokens:
      - ${API_TOKEN}
```

**设置环境变量**:
```bash
export SOURCE_GITLAB_TOKEN="glpat-xxxxx"
export TARGET_GITLAB_TOKEN="glpat-yyyyy"
export API_TOKEN="your-api-token"
```

---

## ✅ 配置验证

启动服务时会自动验证配置：

- **必需字段检查**: 确保所有必需配置项存在
- **格式验证**: 验证 URL、数值等格式正确
- **连通性测试**: 测试 GitLab 连接和 Token 有效性
- **权限检查**: 验证 Token 是否具有必需的权限

**验证失败**: 服务将拒绝启动并输出详细错误信息

---

## 📝 配置示例场景

### 场景 1: 同步所有项目

```yaml
source:
  url: https://source.gitlab.com
  token: ${SOURCE_GITLAB_TOKEN}

target:
  url: https://target.gitlab.com
  token: ${TARGET_GITLAB_TOKEN}

sync:
  include_groups:
    - "**"  # 包含所有分组
```

### 场景 2: 仅同步特定分组

```yaml
sync:
  include_groups:
    - "production/**"
    - "staging/critical-apps"
  exclude_groups:
    - "*/archived/**"
  filters:
    exclude_archived: true
    exclude_empty: true
```

### 场景 3: 高性能配置

```yaml
performance:
  project_discovery_concurrency: 10
  mirror_setup_concurrency: 10
  mirror_polling_batch_size: 100
  api_rate_limit_delay: 0.05

scheduler:
  project_discovery:
    interval: 600  # 10分钟
  mirror_polling:
    interval: 60   # 1分钟
```

---

## 🔄 配置热重载

支持运行时重新加载配置：

```bash
# 方式 1: CLI 命令
gitlab-mirror reload

# 方式 2: API 调用
curl -X POST http://localhost:8080/api/reload \
  -H "Authorization: Bearer ${API_TOKEN}"
```

**重载范围**:
- ✅ 同步规则（include_groups, exclude_groups, filters）
- ✅ 定时任务间隔
- ✅ 性能配置
- ❌ 数据库配置（需重启）
- ❌ API 服务配置（需重启）

---

## 📚 相关文档

- [安装指南](./08-deployment.md#配置文件模板)
- [CLI 命令](./06-cli-client.md)
- [API 文档](./05-rest-api.md)

---

**最后更新**: 2025-12-13
