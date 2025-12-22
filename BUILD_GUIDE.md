# GitLab Mirror - Build Guide

## 📦 构建发布包

### 快速构建

```bash
# 完整构建（包含测试）
./build.sh

# 跳过测试
./build.sh --skip-tests

# 清理后构建
./build.sh --clean --skip-tests
```

构建产物位于 `target/` 目录：
- `gitlab-mirror-1.0.0-SNAPSHOT-dist.tar.gz`
- `gitlab-mirror-1.0.0-SNAPSHOT-dist.zip`

### 构建要求

- **Java**: 17 or higher
- **Maven**: 3.6.0 or higher
- **Git**: For version information

### 构建流程

1. **编译打包** - Maven package
2. **生成版本信息** - 从 Git 获取版本号、commit hash
3. **Assembly打包** - 按照 `assembly.xml` 组装发布结构
4. **创建归档文件** - 生成 tar.gz 和 zip 格式

## 📁 发布包结构

```
gitlab-mirror-1.0.0/
├── server/                           # 服务端
│   ├── bin/                         # 管理脚本
│   │   ├── start.sh                # 启动
│   │   ├── stop.sh                 # 停止
│   │   ├── restart.sh              # 重启
│   │   └── status.sh               # 状态查询
│   ├── lib/
│   │   └── gitlab-mirror-server.jar
│   ├── conf/
│   │   ├── application.yml
│   │   ├── application-prod.yml
│   │   └── logback-spring.xml
│   └── logs/                        # 日志目录（空）
│
├── cli/                              # 客户端
│   ├── bin/
│   │   └── gitlab-mirror           # CLI启动脚本
│   └── lib/
│       └── gitlab-mirror-cli.jar
│
├── conf/                             # 全局配置
│   ├── .env.example                # 环境变量模板
│   └── .env                        # 实际配置（安装时生成）
│
├── sql/                              # 数据库脚本
│   └── schema.sql
│
├── scripts/                          # 工具脚本
│   ├── install.sh                  # 安装脚本
│   ├── uninstall.sh                # 卸载脚本
│   ├── backup.sh                   # 备份脚本
│   ├── restore.sh                  # 恢复脚本
│   └── health-check.sh             # 健康检查
│
├── systemd/
│   └── gitlab-mirror-server.service
│
├── docs/                             # 文档
│   ├── README.md
│   ├── INSTALL.md
│   └── CONFIGURATION.md
│
├── LICENSE
├── README.md
└── VERSION
```

## 🔧 Assembly 配置

### assembly.xml 关键配置

```xml
<assembly>
  <!-- 基础目录：gitlab-mirror-${version} -->
  <baseDirectory>gitlab-mirror-${project.version}</baseDirectory>

  <!-- 格式：tar.gz 和 zip -->
  <formats>
    <format>tar.gz</format>
    <format>zip</format>
  </formats>

  <!-- 文件集合 -->
  <fileSets>
    <!-- Server JAR -->
    <fileSet>
      <directory>server/target</directory>
      <outputDirectory>server/lib</outputDirectory>
      <includes>
        <include>gitlab-mirror-server-*.jar</include>
      </includes>
    </fileSet>

    <!-- CLI JAR -->
    <fileSet>
      <directory>cli-client/target</directory>
      <outputDirectory>cli/lib</outputDirectory>
      <includes>
        <include>gitlab-mirror-cli-*.jar</include>
      </includes>
    </fileSet>

    <!-- 脚本文件（设置为可执行） -->
    <fileSet>
      <directory>distribution/server/bin</directory>
      <outputDirectory>server/bin</outputDirectory>
      <fileMode>0755</fileMode>
      <lineEnding>unix</lineEnding>
    </fileSet>
  </fileSets>
</assembly>
```

## 📝 版本信息

版本信息在构建时自动生成（`VERSION` 文件）：

```
VERSION=1.0.0-SNAPSHOT
BUILD_DATE=2025-01-22 14:30:00
BUILD_NUMBER=local
GIT_COMMIT=78ccd10
GIT_BRANCH=main
```

## 🚀 部署流程

### 1. 构建发布包

```bash
./build.sh --skip-tests
```

### 2. 上传到服务器

```bash
scp target/gitlab-mirror-1.0.0-SNAPSHOT-dist.tar.gz user@server:/tmp/
```

### 3. 在服务器上安装

```bash
# 解压
cd /tmp
tar -xzf gitlab-mirror-1.0.0-SNAPSHOT-dist.tar.gz
cd gitlab-mirror-1.0.0-SNAPSHOT

# 运行安装脚本
sudo ./scripts/install.sh

# 配置
sudo vi /opt/gitlab-mirror/conf/.env

# 启动服务
sudo systemctl start gitlab-mirror-server
sudo systemctl enable gitlab-mirror-server
```

### 4. 验证

```bash
# 检查服务状态
sudo systemctl status gitlab-mirror-server

# 运行健康检查
sudo /opt/gitlab-mirror/scripts/health-check.sh

# 测试 CLI
gitlab-mirror projects
```

## 🔄 持续集成

### GitHub Actions 示例

```yaml
name: Build and Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build with Maven
        run: ./build.sh --skip-tests

      - name: Upload artifacts
        uses: actions/upload-artifact@v3
        with:
          name: distribution
          path: target/*.tar.gz

      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: target/*.tar.gz
```

### Jenkins Pipeline 示例

```groovy
pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'Maven3'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './build.sh --skip-tests'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/*.tar.gz,target/*.zip',
                                fingerprint: true
            }
        }

        stage('Deploy to Test') {
            when {
                branch 'develop'
            }
            steps {
                sh '''
                    scp target/*.tar.gz deploy@test-server:/tmp/
                    ssh deploy@test-server "cd /tmp && tar -xzf *.tar.gz && sudo ./gitlab-mirror-*/scripts/install.sh"
                '''
            }
        }
    }
}
```

## 🧪 本地测试

### 测试构建过程

```bash
# 1. 清理环境
mvn clean

# 2. 执行构建
./build.sh --skip-tests

# 3. 验证产物
ls -lh target/*.tar.gz
tar -tzf target/gitlab-mirror-*.tar.gz | head -20

# 4. 解压测试
cd /tmp
tar -xzf /path/to/gitlab-mirror-*.tar.gz
cd gitlab-mirror-*
tree -L 2
```

### 测试脚本

```bash
# 测试服务端脚本
cd /tmp/gitlab-mirror-*/server/bin
./start.sh
sleep 5
./status.sh
./stop.sh

# 测试安装脚本（需要 root）
sudo /tmp/gitlab-mirror-*/scripts/install.sh
```

## 📊 构建优化

### Maven 构建参数

```bash
# 并行构建（4线程）
mvn -T 4 package

# 离线模式（使用本地仓库）
mvn -o package

# 跳过测试
mvn -DskipTests package

# 跳过 Javadoc
mvn -Dmaven.javadoc.skip=true package
```

### 构建缓存

```bash
# 使用 Maven Daemon 加速
./mvnw package

# 或者使用 Gradle（未来）
./gradlew build
```

## 🔍 故障排查

### 构建失败

```bash
# 查看详细日志
mvn package -X

# 清理并重新构建
mvn clean package -U
```

### Assembly 失败

```bash
# 验证 assembly.xml
mvn assembly:help -Ddetail=true

# 测试 assembly（不生成）
mvn assembly:assembly -DdryRun=true
```

### JAR 文件找不到

```bash
# 检查子模块构建
mvn package -pl server,cli-client

# 验证 JAR 存在
ls -l server/target/*.jar
ls -l cli-client/target/*.jar
```

## 📚 相关文档

- [部署文档](distribution/docs/INSTALL.md) - 详细安装步骤
- [配置文档](distribution/docs/CONFIGURATION.md) - 配置说明
- [README](distribution/docs/README.md) - 快速开始
- [CLAUDE.md](CLAUDE.md) - 项目开发指南
