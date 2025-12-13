# 模块 8: 部署和文档 (Deployment & Documentation)

**目标**: 完成项目构建、部署配置和文档编写。

**预计时间**: Week 4 (1-2天)

---

## 任务清单

### T8.1 构建和部署配置
**依赖**: 所有核心模块

**任务目标**:

**构建配置**:
- 配置 Maven/Gradle 多模块打包
- 配置 Spring Boot 打包插件
- 配置 CLI 客户端 JAR 打包
- 配置资源文件打包（Shell 脚本、配置模板）
- 生成可执行文件

**Docker 部署**:
- 编写 Dockerfile（服务端）
- 配置 Docker Compose（MySQL + 应用）
- 配置环境变量映射
- 配置数据卷挂载（配置、日志、数据）
- 配置健康检查

**Shell 脚本部署**:
- 编写安装脚本（install.sh）
- 配置环境变量检查
- 配置依赖检查（Java、MySQL）
- 实现自动创建配置文件
- 实现服务注册（systemd）

**验收标准**:
- JAR 打包成功
- CLI 脚本可执行
- Docker 镜像构建成功
- Docker Compose 启动正常
- Shell 安装脚本工作正常
- 服务健康检查通过

**测试要求**:
- 测试多模块打包
- 测试 Docker 部署
- 测试配置文件加载
- 测试服务启停
- 测试数据持久化

**提交**: `build: add build and deployment configuration`

---

### T8.2 编写项目文档
**依赖**: T8.1 (部署配置)

**任务目标**:

**用户文档**:
- README.md（项目介绍、快速开始、架构说明）
- INSTALL.md（详细安装步骤、环境要求）
- CONFIGURATION.md（配置说明、配置项参考）
- CLI.md（CLI 命令使用手册）
- API.md（API 接口文档）

**开发文档**:
- DEVELOPMENT.md（开发环境搭建、代码规范）
- ARCHITECTURE.md（架构设计、模块说明）
- DATABASE.md（数据库设计、表结构说明）
- TESTING.md（测试策略、测试运行）

**运维文档**:
- DEPLOYMENT.md（部署指南、配置调优）
- MONITORING.md（监控指标、日志分析）
- TROUBLESHOOTING.md（常见问题、故障排查）

**验收标准**:
- 文档内容完整准确
- 示例代码可运行
- 配置说明清晰
- 包含常见问题解答
- 格式统一规范

**测试要求**:
- 验证文档中的命令和示例
- 检查链接有效性
- 确认配置参数正确

**提交**: `docs: add comprehensive project documentation`

---

## 部署方式

### 1. Docker 部署（推荐）
```bash
# 使用 Docker Compose 一键部署
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 2. Shell 脚本部署
```bash
# 安装服务
./install.sh

# 启动服务
gitlab-mirror start

# 查看状态
gitlab-mirror status
```

### 3. 手动部署
```bash
# 编译打包
mvn clean package

# 运行服务端
java -jar server/target/gitlab-mirror-server.jar

# 使用 CLI
./gitlab-mirror <command>
```

---

## 配置文件模板

### application.yml
```yaml
# GitLab 源配置
gitlab:
  source:
    url: https://source-gitlab.com
    token: ${GITLAB_SOURCE_TOKEN}
  target:
    url: https://target-gitlab.com
    token: ${GITLAB_TARGET_TOKEN}

# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gitlab_mirror
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

# 调度配置
scheduler:
  discovery:
    cron: "0 */30 * * * *"  # 每 30 分钟
  mirror-poll:
    cron: "0 */2 * * * *"   # 每 2 分钟
```

---

## 模块输出

- ✅ Maven/Gradle 构建配置
- ✅ Docker 部署配置
- ✅ Shell 安装脚本
- ✅ 完整的用户文档
- ✅ 完整的开发文档
- ✅ 完整的运维文档

---

## 关键决策

1. **部署方式**: 提供 Docker 和 Shell 两种部署方式
2. **配置管理**: 使用环境变量覆盖敏感配置
3. **文档结构**: 按用户、开发、运维分类
4. **健康检查**: Docker 容器健康检查确保服务可用
5. **日志收集**: 配置日志卷挂载便于查看和分析
