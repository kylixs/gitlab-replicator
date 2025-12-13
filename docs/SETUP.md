# GitLab Mirror 开发环境配置指南

本文档说明如何配置 GitLab Mirror 项目的开发环境。

---

## 📋 前置条件

- Docker 和 Docker Compose 已安装
- 至少 8GB 可用内存（运行完整环境）
- 至少 20GB 可用磁盘空间

---

## 🚀 快速启动

### 1. 启动基础开发环境（MySQL）

```bash
# 在项目根目录
docker-compose up -d

# 验证 MySQL 启动
docker ps | grep gitlab-mirror-mysql
```

### 2. 启动源 GitLab 实例

```bash
cd docker/gitlab-source
docker-compose up -d

# 查看启动日志
docker-compose logs -f
```

**等待 GitLab 初始化完成**（约 5-10 分钟），直到看到：
```
gitlab Reconfigured!
```

### 3. 启动目标 GitLab 实例

```bash
cd ../gitlab-target
docker-compose up -d

# 查看启动日志
docker-compose logs -f
```

同样等待初始化完成。

---

## 🔑 创建 GitLab Access Tokens

### 创建源 GitLab Token

1. **访问源 GitLab**
   - URL: http://localhost:8000
   - 用户名: `root`
   - 密码: `My2024@1213!`

   > **注意**: 初始密码已通过脚本重置为上述密码。如果登录失败，请参考文档末尾的"故障排查"部分。

2. **创建 Personal Access Token**
   - 点击右上角头像 → **Settings** (或 **Preferences**)
   - 左侧菜单选择 **Access Tokens**
   - 填写信息：
     - **Token name**: `gitlab-mirror-source`
     - **Expiration date**: 选择 1 年后的日期
     - **Scopes**: 勾选以下三项
       - ✅ `api` - 完整的 API 访问权限
       - ✅ `read_repository` - 读取仓库权限
       - ✅ `write_repository` - 写入仓库权限
   - 点击 **Create personal access token**
   - ⚠️ **立即复制生成的 Token**（只显示一次）

3. **保存 Token**
   ```bash
   # 示例 Token（请替换为实际生成的）
   glpat-xxxxxxxxxxxxxxxxxxxx
   ```

### 创建目标 GitLab Token

1. **访问目标 GitLab**
   - URL: http://localhost:9000
   - 用户名: `root`
   - 密码: `My2024@1213!`

2. **创建 Personal Access Token**（步骤同上）
   - **Token name**: `gitlab-mirror-target`
   - **Scopes**: `api`, `read_repository`, `write_repository`

3. **保存 Token**
   ```bash
   # 示例 Token（请替换为实际生成的）
   glpat-yyyyyyyyyyyyyyyyyyyy
   ```

---

## ⚙️ 配置环境变量

### 1. 复制环境变量模板

```bash
cp .env.example .env
```

### 2. 编辑 .env 文件

```bash
vi .env
```

### 3. 填入实际的 Token

```bash
# ==================== GitLab 源配置 ====================
SOURCE_GITLAB_URL=http://localhost:8000
SOURCE_GITLAB_TOKEN=glpat-xxxxxxxxxxxxxxxxxxxx  # 👈 替换为源 GitLab Token

# ==================== GitLab 目标配置 ====================
TARGET_GITLAB_URL=http://localhost:9000
TARGET_GITLAB_TOKEN=glpat-yyyyyyyyyyyyyyyyyyyy  # 👈 替换为目标 GitLab Token
```

**完整的 .env 配置示例**:

```bash
# ==================== 数据库配置 ====================
DB_HOST=localhost
DB_PORT=3306
DB_NAME=gitlab_mirror
DB_USERNAME=gitlab_mirror
DB_PASSWORD=mirror_pass_123

MYSQL_ROOT_PASSWORD=root_password_123

# ==================== Redis 配置 ====================
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis_pass_123

# ==================== GitLab 源配置 ====================
SOURCE_GITLAB_URL=http://localhost:8000
SOURCE_GITLAB_TOKEN=glpat-Abc123XyZ_SourceToken456

# ==================== GitLab 目标配置 ====================
TARGET_GITLAB_URL=http://localhost:9000
TARGET_GITLAB_TOKEN=glpat-Def789UvW_TargetToken012

# ==================== API 服务配置 ====================
API_HOST=0.0.0.0
API_PORT=8080
API_TOKEN_1=your-api-token-here

# ==================== Webhook 配置（可选）====================
WEBHOOK_SECRET=your-webhook-secret

# ==================== 日志配置 ====================
LOG_LEVEL=INFO
LOG_FILE=/var/log/gitlab-mirror/service.log

# ==================== 开发环境配置 ====================
SPRING_PROFILES_ACTIVE=dev
```

---

## ✅ 验证配置

### 1. 验证数据库连接

```bash
docker exec -it gitlab-mirror-mysql mysql -ugitlab_mirror -pmirror_pass_123 gitlab_mirror -e "SELECT 'Database OK' AS status;"
```

应该看到:
```
+-------------+
| status      |
+-------------+
| Database OK |
+-------------+
```

### 2. 验证源 GitLab Token

```bash
export SOURCE_GITLAB_TOKEN="glpat-xxxxxxxxxxxxxxxxxxxx"  # 替换为实际 Token

curl -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" \
  "http://localhost:8000/api/v4/user" | jq .
```

应该返回 root 用户信息（JSON 格式）。

### 3. 验证目标 GitLab Token

```bash
export TARGET_GITLAB_TOKEN="glpat-yyyyyyyyyyyyyyyyyyyy"  # 替换为实际 Token

curl -H "PRIVATE-TOKEN: $TARGET_GITLAB_TOKEN" \
  "http://localhost:9000/api/v4/user" | jq .
```

应该返回 root 用户信息（JSON 格式）。

---

## 🧪 创建测试数据

### 在源 GitLab 创建测试分组和项目

```bash
export SOURCE_GITLAB_TOKEN="your-source-token-here"

# 1. 创建测试分组
curl -X POST "http://localhost:8000/api/v4/groups" \
  -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Group",
    "path": "test-group",
    "visibility": "private"
  }' | jq .

# 2. 创建测试项目（假设分组 ID 为 2）
curl -X POST "http://localhost:8000/api/v4/projects" \
  -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Project",
    "path": "test-project",
    "namespace_id": 2,
    "visibility": "private",
    "initialize_with_readme": true
  }' | jq .
```

### 推送测试代码

```bash
# 创建本地仓库
mkdir test-repo && cd test-repo
git init
echo "# Test Project" > README.md
git add README.md
git commit -m "Initial commit"

# 添加远程仓库
git remote add origin http://root:GitLabSource123!@localhost:8000/test-group/test-project.git

# 推送代码
git push -u origin main
```

---

## 📚 配置文件说明

### Spring Boot 配置文件

创建 `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: gitlab-mirror

  # 数据库配置
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:gitlab_mirror}
    username: ${DB_USERNAME:gitlab_mirror}
    password: ${DB_PASSWORD:mirror_pass_123}
    driver-class-name: com.mysql.cj.jdbc.Driver

  # Redis 配置（可选）
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:redis_pass_123}

# GitLab 配置
gitlab:
  source:
    url: ${SOURCE_GITLAB_URL:http://localhost:8000}
    token: ${SOURCE_GITLAB_TOKEN}

  target:
    url: ${TARGET_GITLAB_URL:http://localhost:9000}
    token: ${TARGET_GITLAB_TOKEN}

# 日志配置
logging:
  level:
    root: ${LOG_LEVEL:INFO}
  file:
    name: ${LOG_FILE:/var/log/gitlab-mirror/service.log}
```

---

## 🔧 故障排查

### GitLab 无法访问

```bash
# 检查容器状态
docker ps | grep gitlab

# 查看日志
cd docker/gitlab-source
docker-compose logs -f

# 重启 GitLab
docker-compose restart
```

### Token 验证失败

1. 确认 Token 没有过期
2. 确认 Scopes 包含 `api`, `read_repository`, `write_repository`
3. 重新生成 Token

### 数据库连接失败

```bash
# 检查 MySQL 状态
docker ps | grep gitlab-mirror-mysql

# 测试连接
docker exec -it gitlab-mirror-mysql mysql -uroot -proot_password_123 -e "SHOW DATABASES;"
```

---

## 📝 下一步

配置完成后，可以开始开发：

1. 查看任务清单: [mvp/README.md](mvp/README.md)
2. 阅读技术设计: [PUSH_MIRROR_MVP_DESIGN.md](PUSH_MIRROR_MVP_DESIGN.md)
3. 开始模块 1: [mvp/01-infrastructure.md](mvp/01-infrastructure.md)

---

## 🆘 获取帮助

- [Docker 环境说明](docker/README.md)
- [源 GitLab 配置](docker/gitlab-source/README.md)
- [目标 GitLab 配置](docker/gitlab-target/README.md)
- [配置文件格式](mvp/CONFIGURATION.md)

---

**环境配置完成时间**: 约 15-20 分钟（包括等待 GitLab 初始化）
