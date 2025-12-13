# 目标 GitLab 实例

用于测试 GitLab Mirror 同步工具的目标 GitLab 实例。

---

## 快速启动

```bash
# 启动 GitLab
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止 GitLab
docker-compose down

# 完全清理（包括数据）
docker-compose down -v
```

---

## 访问信息

- **Web 访问**: http://localhost:9000
- **SSH 访问**: ssh://git@localhost:2223
- **初始用户**: `root`
- **初始密码**: `GitLabTarget123!`

**首次启动**: GitLab 首次启动需要 5-10 分钟完成初始化，请耐心等待。

---

## 主机名配置（可选）

为了更好的体验，建议在本地 hosts 文件中添加：

```bash
# macOS/Linux: /etc/hosts
# Windows: C:\Windows\System32\drivers\etc\hosts

127.0.0.1 gitlab-target.local
```

配置后可以通过 http://gitlab-target.local:9000 访问。

---

## 创建访问 Token

### 1. 创建 Personal Access Token

1. 登录 GitLab: http://localhost:9000
2. 点击右上角头像 → Preferences
3. 左侧菜单选择 "Access Tokens"
4. 创建新 Token:
   - Name: `gitlab-mirror-target`
   - Scopes: 勾选 `api`, `read_repository`, `write_repository`
   - 点击 "Create personal access token"
5. 复制生成的 Token（后续无法再次查看）

### 2. 验证 Token

```bash
export GITLAB_TARGET_TOKEN="your-token-here"
export GITLAB_TARGET_URL="http://localhost:9000"

# 测试 API 访问
curl -H "PRIVATE-TOKEN: $GITLAB_TARGET_TOKEN" "$GITLAB_TARGET_URL/api/v4/user"
```

---

## 与源 GitLab 配合使用

### 完整测试环境启动顺序

```bash
# 1. 启动源 GitLab
cd docker/gitlab-source
docker-compose up -d

# 2. 启动目标 GitLab
cd ../gitlab-target
docker-compose up -d

# 3. 等待两个实例完全启动（约 5-10 分钟）
# 可以分别查看日志确认启动状态
docker-compose logs -f

# 4. 启动项目开发环境（MySQL + Redis）
cd ../..
docker-compose up -d
```

### 配置 GitLab Mirror 工具

在 `config/application.yml` 中配置源和目标 GitLab：

```yaml
source:
  url: http://localhost:8000
  token: ${SOURCE_GITLAB_TOKEN}  # 从源 GitLab 创建的 Token

target:
  url: http://localhost:9000
  token: ${TARGET_GITLAB_TOKEN}  # 从目标 GitLab 创建的 Token
```

---

## 测试 Push Mirror

### 手动创建 Push Mirror 测试

```bash
# 环境变量
export SOURCE_GITLAB_TOKEN="your-source-token"
export TARGET_GITLAB_TOKEN="your-target-token"
export SOURCE_URL="http://localhost:8000"
export TARGET_URL="http://localhost:9000"

# 1. 在源 GitLab 获取项目 ID
SOURCE_PROJECT_ID=2  # 替换为实际的项目 ID

# 2. 在目标 GitLab 创建空项目
curl -X POST "$TARGET_URL/api/v4/projects" \
  -H "PRIVATE-TOKEN: $TARGET_GITLAB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mirrored-project",
    "path": "mirrored-project",
    "visibility": "private"
  }'

# 3. 获取目标项目 ID
TARGET_PROJECT_ID=2  # 从上面的响应中获取

# 4. 在源项目配置 Push Mirror
curl -X POST "$SOURCE_URL/api/v4/projects/$SOURCE_PROJECT_ID/remote_mirrors" \
  -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"url\": \"http://oauth2:$TARGET_GITLAB_TOKEN@gitlab-target.local:9000/root/mirrored-project.git\",
    \"enabled\": true
  }"

# 5. 查看 Mirror 状态
curl -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" \
  "$SOURCE_URL/api/v4/projects/$SOURCE_PROJECT_ID/remote_mirrors"
```

---

## 性能说明

此配置为开发测试环境，已调整以下参数以减少资源占用：

- PostgreSQL shared_buffers: 256MB
- Unicorn worker_processes: 2
- Sidekiq max_concurrency: 10
- 禁用了 Prometheus 监控
- 禁用了 Container Registry
- 禁用了 GitLab Pages

**运行两个 GitLab 实例的硬件要求**:
- CPU: 4 核
- 内存: 8GB
- 磁盘: 20GB

---

## 常用命令

```bash
# 查看 GitLab 状态
docker exec -it gitlab-target gitlab-ctl status

# 重启 GitLab 服务
docker exec -it gitlab-target gitlab-ctl restart

# 查看 GitLab 配置
docker exec -it gitlab-target gitlab-rake gitlab:env:info

# 进入 GitLab Rails 控制台（高级）
docker exec -it gitlab-target gitlab-rails console

# 查看日志
docker exec -it gitlab-target tail -f /var/log/gitlab/gitlab-rails/production.log
```

---

## 端口说明

为避免与源 GitLab 冲突，目标 GitLab 使用不同的端口：

| 服务 | 源 GitLab | 目标 GitLab |
|------|----------|-----------|
| HTTP | 8080 | 9080 |
| SSH  | 2222 | 2223 |

---

## 故障排查

### 1. 端口冲突

如果端口 9000 或 2223 已被占用，可以修改 `docker-compose.yml` 中的端口映射：

```yaml
ports:
  - "19000:9000"  # 修改为其他端口
  - "12223:22"
```

### 2. 内存不足

如果同时运行源和目标 GitLab 内存不足，可以：

- 仅启动必要的实例进行测试
- 减少 worker 和 concurrency 配置
- 增加系统内存

### 3. 网络问题

确保源和目标 GitLab 能够互相访问：

```bash
# 在源 GitLab 容器中测试连接目标
docker exec -it gitlab-source ping gitlab-target.local

# 在目标 GitLab 容器中测试连接源
docker exec -it gitlab-target ping gitlab-source.local
```

---

## 清理数据

```bash
# 停止并删除容器（保留数据卷）
docker-compose down

# 完全清理（删除所有数据）
docker-compose down -v

# 删除镜像
docker rmi gitlab/gitlab-ce:latest
```
