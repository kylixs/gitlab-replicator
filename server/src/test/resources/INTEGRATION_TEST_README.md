# GitLab API 集成测试指南

## 前提条件

1. Docker 和 Docker Compose 已安装
2. 至少 4GB 可用内存
3. 端口 8000 和 2222 未被占用

## 步骤

### 1. 启动 GitLab 源服务器

```bash
cd /Users/gongdewei/work/projects/gitlab-replica/docker/gitlab-source
docker-compose up -d
```

### 2. 等待 GitLab 启动完成

GitLab 首次启动需要 5-10 分钟。检查状态：

```bash
docker-compose logs -f gitlab-source
```

等待看到类似以下日志：
```
gitlab-source | * Running on http://0.0.0.0:8000/
```

或者访问: http://localhost:8000/-/health

### 3. 获取初始 root 密码

```bash
docker exec -it gitlab-source cat /etc/gitlab/initial_root_password
```

### 4. 登录 GitLab

- 访问: http://localhost:8000
- 用户名: `root`
- 密码: 从步骤3获取

### 5. 创建 Personal Access Token

1. 登录后，点击右上角头像 -> Settings
2. 左侧菜单选择 "Access Tokens"
3. 创建新 token:
   - Name: `integration-test`
   - Scopes: 选择 `api`, `read_api`, `read_repository`, `write_repository`
4. 点击 "Create personal access token"
5. 复制生成的 token

### 6. 设置环境变量

```bash
export SOURCE_GITLAB_TOKEN="your-token-here"
export SOURCE_GITLAB_URL="http://localhost:8000"
```

### 7. 运行集成测试

```bash
cd /Users/gongdewei/work/projects/gitlab-replica
mvn test -Dtest=GitLabApiClientIntegrationTest -rf :server
```

## 测试说明

集成测试会执行以下操作：

1. **testConnectionToGitLab**: 测试连接到 GitLab 服务器
2. **testGetGroups**: 获取所有分组
3. **testGetAllProjects**: 获取所有项目
4. **testGroupLifecycle**: 创建测试分组并验证（不会删除）
5. **testProjectLifecycle**: 创建测试项目并验证（不会删除）

**注意**: 测试会在 GitLab 中创建测试数据，不会自动删除。请在测试后手动清理。

## 清理测试数据

在 GitLab UI 中删除：
- 分组: `test-integration-group-*`
- 项目: `test-integration-projects/test-project-*`

## 停止 GitLab

```bash
cd /Users/gongdewei/work/projects/gitlab-replica/docker/gitlab-source
docker-compose down
```

保留数据（下次启动更快）：
```bash
docker-compose stop
```

## 故障排除

### GitLab 启动失败
- 检查端口是否被占用: `lsof -i :8000`
- 检查内存是否足够: `docker stats`
- 查看日志: `docker-compose logs gitlab-source`

### 集成测试被跳过
- 确认环境变量已设置: `echo $SOURCE_GITLAB_TOKEN`
- 确认 GitLab 可访问: `curl http://localhost:8000/-/health`
- 查看测试日志了解详细信息

### 连接超时
- 增加超时时间（在 application-test.yml 中）
- 检查防火墙设置
