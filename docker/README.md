# Docker 开发环境说明

本目录包含 GitLab Mirror 项目的完整 Docker 开发环境配置。

---

## 📂 目录结构

```
docker/
├── README.md                    # 本文件
├── mysql/                       # MySQL 配置
│   ├── conf/                    # MySQL 配置文件
│   │   └── custom.cnf           # 自定义配置
│   └── init/                    # 数据库初始化脚本
│       └── 01-init.sql          # 初始化 SQL
├── gitlab-source/               # 源 GitLab 实例
│   ├── docker-compose.yml       # 源 GitLab Docker Compose
│   └── README.md                # 源 GitLab 使用说明
└── gitlab-target/               # 目标 GitLab 实例
    ├── docker-compose.yml       # 目标 GitLab Docker Compose
    └── README.md                # 目标 GitLab 使用说明
```

---

## 🚀 快速启动

### 方案 1: 仅启动基础开发环境（推荐初期开发）

```bash
# 在项目根目录
docker-compose up -d

# 包含的服务：
# - MySQL 8.0 (端口 3306)
# - Redis 7 (端口 6379)
```

### 方案 2: 启动完整测试环境

```bash
# 1. 启动基础开发环境
docker-compose up -d

# 2. 启动源 GitLab
cd docker/gitlab-source
docker-compose up -d

# 3. 启动目标 GitLab
cd ../gitlab-target
docker-compose up -d

# 4. 等待 GitLab 实例启动完成（约 5-10 分钟）
```

---

## 📋 服务列表

### 基础开发环境

| 服务 | 端口 | 用户名 | 密码 | 说明 |
|------|------|--------|------|------|
| MySQL | 3306 | gitlab_mirror | mirror_pass_123 | 应用数据库 |
| Redis | 6379 | - | redis_pass_123 | 缓存和分布式锁 |

**MySQL Root 密码**: `root_password_123`

### GitLab 实例

| 实例 | HTTP | SSH | 用户名 | 密码 | 说明 |
|------|------|-----|--------|------|------|
| 源 GitLab | 8000 | 2222 | root | GitLabSource123! | 源代码仓库 |
| 目标 GitLab | 9000 | 2223 | root | GitLabTarget123! | 同步目标仓库 |

---

## 🔧 配置说明

### MySQL 配置

**位置**: `docker/mysql/conf/custom.cnf`

**主要配置**:
- 字符集: UTF-8MB4
- 最大连接数: 500
- InnoDB 缓冲池: 512MB
- 慢查询日志: 启用（2秒）
- 时区: Asia/Shanghai

### GitLab 配置

**资源优化**（开发环境）:
- PostgreSQL shared_buffers: 256MB
- Unicorn workers: 2
- Sidekiq concurrency: 10
- 禁用功能: Prometheus、Container Registry、GitLab Pages

---

## 📝 使用步骤

### 1. 启动基础环境

```bash
# 在项目根目录
docker-compose up -d

# 验证 MySQL 连接
docker exec -it gitlab-mirror-mysql mysql -ugitlab_mirror -pmirror_pass_123 -e "SELECT VERSION();"

# 验证 Redis 连接
docker exec -it gitlab-mirror-redis redis-cli -a redis_pass_123 ping
```

### 2. 配置 GitLab 实例（可选）

详细步骤请参考：
- [源 GitLab 配置](./gitlab-source/README.md)
- [目标 GitLab 配置](./gitlab-target/README.md)

### 3. 配置应用

在 `src/main/resources/application.yml` 中配置数据库连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gitlab_mirror
    username: gitlab_mirror
    password: mirror_pass_123

  redis:
    host: localhost
    port: 6379
    password: redis_pass_123
```

---

## 🛠️ 常用命令

### 查看服务状态

```bash
# 基础环境
docker-compose ps

# 源 GitLab
cd docker/gitlab-source && docker-compose ps

# 目标 GitLab
cd docker/gitlab-target && docker-compose ps
```

### 查看日志

```bash
# 基础环境
docker-compose logs -f

# 特定服务
docker-compose logs -f mysql
docker-compose logs -f redis

# GitLab 日志
cd docker/gitlab-source && docker-compose logs -f
cd docker/gitlab-target && docker-compose logs -f
```

### 停止服务

```bash
# 停止基础环境
docker-compose down

# 停止 GitLab 实例
cd docker/gitlab-source && docker-compose down
cd docker/gitlab-target && docker-compose down

# 停止所有服务
docker-compose down
cd docker/gitlab-source && docker-compose down
cd docker/gitlab-target && docker-compose down
```

### 清理数据

```bash
# 删除所有数据卷（谨慎操作）
docker-compose down -v
cd docker/gitlab-source && docker-compose down -v
cd docker/gitlab-target && docker-compose down -v
```

---

## 💡 开发建议

### 初期开发阶段

仅启动基础环境（MySQL + Redis），使用 Mock 数据进行开发：

```bash
docker-compose up -d
```

**优点**:
- 资源占用小（约 500MB 内存）
- 启动快速（几秒钟）
- 适合单元测试和集成测试

### 集成测试阶段

启动完整环境（包括源和目标 GitLab）：

```bash
docker-compose up -d
cd docker/gitlab-source && docker-compose up -d
cd docker/gitlab-target && docker-compose up -d
```

**注意**:
- 资源占用大（约 8GB 内存）
- 首次启动慢（约 10 分钟）
- 适合端到端测试

---

## 🔍 故障排查

### 1. 端口冲突

检查端口占用：
```bash
# macOS/Linux
lsof -i :3306
lsof -i :6379
lsof -i :8080
lsof -i :9080

# 修改端口映射
# 在 docker-compose.yml 中修改 ports 配置
```

### 2. 内存不足

```bash
# 查看 Docker 内存使用
docker stats

# 优化方案：
# 1. 仅启动必要的服务
# 2. 减少 GitLab worker 配置
# 3. 增加 Docker Desktop 内存限制
```

### 3. 数据库连接失败

```bash
# 检查 MySQL 容器状态
docker-compose ps mysql

# 查看 MySQL 日志
docker-compose logs mysql

# 测试连接
docker exec -it gitlab-mirror-mysql mysql -uroot -proot_password_123
```

### 4. GitLab 无法访问

```bash
# 检查容器状态
cd docker/gitlab-source && docker-compose ps

# 查看健康检查
docker inspect gitlab-source | grep -A 10 Health

# 等待初始化完成
docker-compose logs -f | grep "gitlab Reconfigured"
```

---

## 📊 资源需求

### 最小配置（仅基础环境）

- CPU: 2 核
- 内存: 2GB
- 磁盘: 5GB

### 推荐配置（完整环境）

- CPU: 4 核
- 内存: 8GB
- 磁盘: 20GB

---

## 🔗 相关文档

- [MySQL 官方文档](https://dev.mysql.com/doc/)
- [Redis 官方文档](https://redis.io/documentation)
- [GitLab Docker 文档](https://docs.gitlab.com/ee/install/docker.html)
- [GitLab API 文档](https://docs.gitlab.com/ee/api/)

---

**最后更新**: 2025-12-13
