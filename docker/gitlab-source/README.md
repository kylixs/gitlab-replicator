# 源 GitLab 实例

用于测试 GitLab Mirror 同步工具的源 GitLab 实例。

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

- **Web 访问**: http://localhost:8000
- **SSH 访问**: ssh://git@localhost:2222
- **初始用户**: `root`
- **初始密码**: `GitLabSource123!`

**首次启动**: GitLab 首次启动需要 5-10 分钟完成初始化，请耐心等待。

---

## 主机名配置（可选）

为了更好的体验，建议在本地 hosts 文件中添加：

```bash
# macOS/Linux: /etc/hosts
# Windows: C:\Windows\System32\drivers\etc\hosts

127.0.0.1 gitlab-source.local
```

配置后可以通过 http://gitlab-source.local:8000 访问。

---

## 创建测试数据

### 1. 创建访问 Token

1. 登录 GitLab: http://localhost:8000
2. 点击右上角头像 → Preferences
3. 左侧菜单选择 "Access Tokens"
4. 创建新 Token:
   - Name: `gitlab-mirror-source`
   - Scopes: 勾选 `api`, `read_repository`, `write_repository`
   - 点击 "Create personal access token"
5. 复制生成的 Token（后续无法再次查看）

### 2. 创建测试分组和项目

```bash
# 方式 1: 通过 Web UI 创建
# 登录后点击 "New group" 创建分组
# 在分组下创建项目

# 方式 2: 通过 GitLab API 创建（需要先获取 Token）
export GITLAB_SOURCE_TOKEN="your-token-here"
export GITLAB_SOURCE_URL="http://localhost:8000"

# 创建分组
curl -X POST "$GITLAB_SOURCE_URL/api/v4/groups" \
  -H "PRIVATE-TOKEN: $GITLAB_SOURCE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-group",
    "path": "test-group",
    "visibility": "private"
  }'

# 创建项目
curl -X POST "$GITLAB_SOURCE_URL/api/v4/projects" \
  -H "PRIVATE-TOKEN: $GITLAB_SOURCE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-project",
    "path": "test-project",
    "namespace_id": 2,
    "visibility": "private"
  }'
```

### 3. 推送测试代码

```bash
# 初始化本地仓库
mkdir test-repo && cd test-repo
git init
echo "# Test Project" > README.md
git add README.md
git commit -m "Initial commit"

# 添加远程仓库（使用 SSH）
git remote add origin ssh://git@localhost:2222/test-group/test-project.git

# 或使用 HTTP（需要输入用户名和密码/Token）
git remote add origin http://localhost:8000/test-group/test-project.git

# 推送代码
git push -u origin main
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

**最低硬件要求**:
- CPU: 2 核
- 内存: 4GB
- 磁盘: 10GB

**生产环境**: 请参考 [GitLab 官方文档](https://docs.gitlab.com/ee/install/requirements.html) 进行配置。

---

## 常用命令

```bash
# 查看 GitLab 状态
docker exec -it gitlab-source gitlab-ctl status

# 重启 GitLab 服务
docker exec -it gitlab-source gitlab-ctl restart

# 查看 GitLab 配置
docker exec -it gitlab-source gitlab-rake gitlab:env:info

# 进入 GitLab Rails 控制台（高级）
docker exec -it gitlab-source gitlab-rails console

# 查看日志
docker exec -it gitlab-source tail -f /var/log/gitlab/gitlab-rails/production.log
```

---

## 故障排查

### 1. GitLab 启动失败

```bash
# 查看日志
docker-compose logs -f

# 检查容器状态
docker-compose ps

# 重启容器
docker-compose restart
```

### 2. 内存不足

如果内存不足，可以进一步减少资源配置：

```yaml
# 在 docker-compose.yml 中修改
unicorn['worker_processes'] = 1
sidekiq['max_concurrency'] = 5
```

### 3. 端口冲突

如果端口 8080 或 2222 已被占用，可以修改 `docker-compose.yml` 中的端口映射：

```yaml
ports:
  - "18080:8080"  # 修改为其他端口
  - "12222:22"
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
