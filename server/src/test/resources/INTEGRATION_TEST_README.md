# GitLab API 集成测试指南

**注意：集成测试使用目标GitLab（Target GitLab）进行测试，端口为9000。**

## 前提条件

1. Docker 和 Docker Compose 已安装
2. 至少 4GB 可用内存
3. 端口 9000 未被占用

## 步骤

### 1. 启动 GitLab 目标服务器

```bash
cd /Users/gongdewei/.docker
docker-compose up -d gitlab-target
```

### 2. 等待 GitLab 启动完成

GitLab 首次启动需要 5-10 分钟。检查状态：

```bash
docker logs -f gitlab-target
```

等待看到类似以下日志：
```
gitlab-target | * Running on http://0.0.0.0:9000/
```

或者访问: http://localhost:9000/-/health

### 3. 获取初始 root 密码

```bash
docker exec -it gitlab-target cat /etc/gitlab/initial_root_password
```

### 4. 登录 GitLab

- 访问: http://localhost:9000
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
export TARGET_GITLAB_TOKEN="your-token-here"
export TARGET_GITLAB_URL="http://localhost:9000"
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
4. **testGroupLifecycle**: 创建测试分组并验证（测试结束后自动删除）
5. **testProjectLifecycle**: 创建测试项目并验证（测试结束后自动删除）

**注意**: 测试会在 GitLab 中创建测试数据，测试结束后会自动清理。

## 清理测试数据

如果测试异常退出导致数据未清理，可运行清理脚本：

```bash
./scripts/cleanup-test-data.sh
```

该脚本会清理：
- 分组: `test-integration-group-*`
- 分组: `test-integration-projects`
- 项目: 所有包含 `test-project` 的项目

## 停止 GitLab

```bash
cd /Users/gongdewei/.docker
docker-compose stop gitlab-target
```

完全删除（包括数据）：
```bash
docker-compose down gitlab-target -v
```

## 故障排除

### GitLab 启动失败
- 检查端口是否被占用: `lsof -i :9000`
- 检查内存是否足够: `docker stats`
- 查看日志: `docker logs -f gitlab-target`

### 集成测试被跳过
- 确认环境变量已设置: `echo $TARGET_GITLAB_TOKEN`
- 确认 GitLab 可访问: `curl http://localhost:9000/-/health`
- 查看测试日志了解详细信息

### 连接超时
- 增加超时时间（在 application-test.yml 中）
- 检查防火墙设置
