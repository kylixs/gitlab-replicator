# GitLab Mirror Web 启动指南

## 📋 前置要求

1. **Java 17+**
2. **Maven 3.6+**
3. **MySQL 8.0** (通过Docker运行)
4. **环境配置** (.env文件)

## 🚀 快速启动（开发模式 - 推荐）

### 一键启动开发环境

```bash
# 启动前端开发服务器 + 后端API服务器
./start-dev.sh
```

这个脚本会：
- ✅ 自动检查数据库连接
- ✅ 启动后端Spring Boot服务器（端口9999）
- ✅ 启动前端Vite开发服务器（端口3000，支持热更新）
- ✅ 配置API代理（前端自动代理到后端）

启动后访问：
- **前端开发服务器**: http://localhost:3000 （支持热更新）
- **后端API**: http://localhost:9999/api

### 停止开发环境

```bash
./stop-dev.sh
```

---

## 📦 生产模式启动

### 1. 启动数据库

```bash
# 启动MySQL数据库
docker-compose up -d

# 验证数据库是否启动成功
docker ps | grep gitlab-mirror-mysql
```

### 2. 配置环境变量

确保项目根目录下有 `.env` 文件（从 `.env.example` 复制）：

```bash
# 如果没有.env文件，从示例复制
cp .env.example .env
```

`.env` 文件应包含：

```properties
# Database Configuration
DB_HOST=localhost
DB_PORT=3306
DB_NAME=gitlab_mirror
DB_USERNAME=gitlab_mirror
DB_PASSWORD=mirror_pass_123

# Source GitLab
SOURCE_GITLAB_URL=http://localhost:8000
SOURCE_GITLAB_TOKEN=glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq

# Target GitLab
TARGET_GITLAB_URL=http://localhost:9000
TARGET_GITLAB_TOKEN=glpat-b2nrFAAy9q2SozZr3Dm0N286MQp1OjEH.01.0w0t2khzm

# API Server
API_PORT=9999
```

### 3. 启动Web服务器

#### 方式一：使用启动脚本（推荐）

```bash
./server/bin/start.sh
```

#### 方式二：使用Maven命令

```bash
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 方式三：先编译再运行

```bash
# 编译整个项目
mvn clean package -DskipTests

# 运行服务器
java -jar server/target/server-1.0.0-SNAPSHOT.jar
```

### 4. 访问Web界面

服务器启动成功后，访问以下地址：

- **Web UI**: http://localhost:9999
- **API文档**: http://localhost:9999/api
- **健康检查**: http://localhost:9999/actuator/health

## 📱 Web UI 功能

### 1. Dashboard (仪表盘)
- 访问: http://localhost:9999/
- 功能: 系统整体运行状况、统计数据、状态分布图、延时监控

### 2. Projects (项目列表)
- 访问: http://localhost:9999/projects
- 功能: 项目列表、筛选、搜索、排序、批量操作、CSV导出

### 3. Project Detail (项目详情)
- 访问: http://localhost:9999/projects/{id}
- 功能:
  - Overview Tab: 项目概览、差异统计、Source/Target信息
  - Branches Tab: 分支对比、筛选
  - Events Tab: 同步事件历史

### 4. Sync Events (同步事件)
- 访问: http://localhost:9999/events
- 功能: 事件历史、筛选、搜索、CSV导出、详情查看

### 5. Configuration (全局配置)
- 访问: http://localhost:9999/configuration
- 功能: GitLab实例配置、连接测试、调度设置、同步规则

## 🔧 开发模式（详细说明）

### 推荐方式：使用开发启动脚本

```bash
./start-dev.sh
```

**优势**：
- ✅ 前端代码修改后自动热更新，无需重新编译
- ✅ 后端和前端同时启动
- ✅ 自动配置API代理
- ✅ 日志集中管理

### 手动启动（如需要单独调试）

#### 只启动前端开发服务器

```bash
cd web-ui
npm install
npm run dev
```

访问: http://localhost:3000

#### 只启动后端服务器

```bash
cd server
mvn spring-boot:run
```

访问: http://localhost:9999

### 查看日志

```bash
# 后端日志
tail -f logs/backend.log

# 前端日志
tail -f logs/frontend.log

# 同时查看两者
tail -f logs/backend.log logs/frontend.log
```

## 📊 API端点

所有API端点都在 `/api` 路径下：

- `GET /api/dashboard/stats` - Dashboard统计数据
- `GET /api/sync/projects` - 项目列表
- `GET /api/sync/projects/{id}/overview` - 项目概览
- `GET /api/sync/branches` - 分支对比
- `GET /api/sync/events` - 同步事件
- `GET /api/config/all` - 全局配置
- `POST /api/sync/scan` - 触发扫描

## ❗ 常见问题

### 1. 端口已被占用

如果9999端口已被占用，可以修改 `.env` 文件：

```properties
API_PORT=8080  # 改为其他端口
```

### 2. 数据库连接失败

确保MySQL容器正在运行：

```bash
docker-compose up -d
docker logs gitlab-mirror-mysql
```

### 3. GitLab连接失败

确保GitLab实例正在运行：

```bash
# 检查source GitLab
curl http://localhost:8000

# 检查target GitLab
curl http://localhost:9000
```

### 4. 编译失败

清理并重新编译：

```bash
mvn clean install -DskipTests
```

## 🛑 停止服务

### 停止Web服务器

在运行终端按 `Ctrl+C`

### 停止数据库

```bash
docker-compose down
```

## 📝 日志

服务器日志位置：

- **控制台输出**: 实时显示
- **日志文件**: `server/logs/gitlab-mirror-service.log`

查看日志：

```bash
tail -f server/logs/gitlab-mirror-service.log
```

## 🔄 重启服务

```bash
# 停止服务 (Ctrl+C)
# 然后重新启动
./server/bin/start.sh
```

## 📚 更多信息

- [项目设计文档](./PUSH_MIRROR_MVP_DESIGN.md)
- [Web UI开发文档](./docs/web-ui/README.md)
- [API文档](./docs/web-ui-requirements.md)

---

**注意**: 首次启动可能需要几分钟来初始化数据库和加载依赖。
