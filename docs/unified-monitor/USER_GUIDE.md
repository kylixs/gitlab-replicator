# GitLab Mirror 统一项目监控 - 用户手册

**版本**: 1.0.0
**更新日期**: 2025-12-14

---

## 📖 目录

1. [功能介绍](#功能介绍)
2. [快速开始](#快速开始)
3. [CLI使用指南](#cli使用指南)
4. [API使用指南](#api使用指南)
5. [Grafana面板使用](#grafana面板使用)
6. [告警配置说明](#告警配置说明)
7. [常见问题解答](#常见问题解答)

---

## 功能介绍

### 核心功能

GitLab Mirror统一项目监控系统提供以下核心功能：

1. **自动项目发现**: 自动扫描源GitLab和目标GitLab的项目列表
2. **差异计算**: 对比源目标项目的commit数、分支数、仓库大小、最后活动时间等
3. **智能告警**: 基于阈值自动触发告警，支持严重级别分类
4. **指标导出**: 导出Prometheus指标，支持Grafana可视化
5. **自动化调度**: 增量扫描（5分钟）、全量对账（每天）、自动解决告警（10分钟）

### 双层指标体系

**系统级指标**:
- 项目总数
- 同步状态分布（synced/outdated/failed）
- 活跃告警数
- 扫描耗时

**项目级指标**:
- 各项目commit数量对比
- 各项目最后提交时间对比
- 各项目仓库大小对比
- 各项目分支数量对比

---

## 快速开始

### 前置条件

- Java 17+
- MySQL 8.0+
- 源GitLab和目标GitLab的访问Token
- （可选）Prometheus + Grafana

### 环境配置

1. **配置数据库连接** (`application.yml`):
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gitlab_mirror
    username: gitlab_mirror
    password: your_password
```

2. **配置GitLab连接** (`.env`):
```bash
SOURCE_GITLAB_URL=http://source-gitlab.com
SOURCE_GITLAB_TOKEN=glpat-xxx
TARGET_GITLAB_URL=http://target-gitlab.com
TARGET_GITLAB_TOKEN=glpat-yyy
```

3. **配置监控参数** (`application.yml`):
```yaml
gitlab:
  mirror:
    monitor:
      incremental-interval: 300000  # 5分钟
      full-scan-cron: "0 0 2 * * ?"  # 每天凌晨2点
      auto-resolve-interval: 600000  # 10分钟
      scheduler:
        enabled: true
        incremental-enabled: true
        full-scan-enabled: true
        auto-resolve-enabled: true
```

### 启动服务

```bash
# 启动Server
cd server
mvn spring-boot:run

# 验证服务
curl http://localhost:8080/actuator/health
```

---

## CLI使用指南

### 安装CLI

```bash
# 使用预编译的JAR
java -jar cli-client/target/cli-client-1.0.0-SNAPSHOT.jar help

# 或使用脚本（需先编译）
./scripts/gitlab-mirror help
```

### 监控相关命令

#### 1. 查看监控总览

```bash
gitlab-mirror monitor status
```

**输出示例**:
```
╔════════════════════════════════════════╗
║       Monitor Status Overview          ║
╠════════════════════════════════════════╣
║ 📊 Projects Summary                    ║
║   Total:        127                    ║
║   ✓ Synced:     118  (92.9%)           ║
║   ⟳ Outdated:   5    (3.9%)            ║
║   ✗ Failed:     2    (1.6%)            ║
╠════════════════════════════════════════╣
║ 🚨 Active Alerts   9                   ║
║   🔴 Critical:  1                       ║
║   🟠 High:      2                       ║
╚════════════════════════════════════════╝
```

#### 2. 查看告警列表

```bash
# 查看所有告警
gitlab-mirror monitor alerts

# 查看Critical告警
gitlab-mirror monitor alerts --severity=critical

# 查看活跃告警
gitlab-mirror monitor alerts --status=active
```

**输出示例**:
```
╔════════════════════════════════════════════════════════════╗
║                       Active Alerts                        ║
╠════════════════════════════════════════════════════════════╣
║ 🔴 Sync failed for project group1/project-a               ║
║   Severity: critical                                       ║
║   Triggered: 2025-12-14 10:30:00                          ║
╠════════════════════════════════════════════════════════════╣
║ Total: 9 alert(s)                                          ║
╚════════════════════════════════════════════════════════════╝
```

### 同步相关命令

#### 3. 触发扫描

```bash
# 触发增量扫描
gitlab-mirror scan --type=incremental

# 触发全量扫描
gitlab-mirror scan --type=full
```

#### 4. 查看项目列表

```bash
# 查看所有项目
gitlab-mirror projects

# 查看有差异的项目
gitlab-mirror projects --status=outdated
```

#### 5. 查看项目差异

```bash
gitlab-mirror diff --project=group1/project-a
```

---

## API使用指南

### 认证

所有API请求需要在Header中添加Token:
```http
Authorization: Bearer your_api_token
```

### 监控模块API

#### 1. 获取监控总览

```http
GET /api/monitor/status
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "summary": {
      "total_projects": 127,
      "synced": 118,
      "outdated": 5,
      "failed": 2
    },
    "alerts": {
      "active": 9,
      "critical": 1,
      "high": 2
    }
  }
}
```

#### 2. 获取告警列表

```http
GET /api/monitor/alerts?severity=critical&status=active&page=1&size=20
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "alerts": [
      {
        "id": 123,
        "sync_project_id": 456,
        "project_key": "group1/project-a",
        "alert_type": "sync_delay",
        "severity": "high",
        "status": "active",
        "title": "Sync delay for project group1/project-a",
        "description": "Sync delay: 180 minutes",
        "triggered_at": "2025-12-14T10:30:00",
        "metadata": {
          "sync_delay_minutes": 180,
          "threshold_minutes": 60
        }
      }
    ],
    "total": 9,
    "page": 1,
    "size": 20
  }
}
```

#### 3. 解决告警

```http
POST /api/monitor/alerts/123/resolve
```

**响应示例**:
```json
{
  "success": true,
  "message": "Alert resolved successfully"
}
```

#### 4. 静默告警

```http
POST /api/monitor/alerts/123/mute
Content-Type: application/json

{
  "duration_minutes": 60
}
```

### 同步模块API

#### 5. 触发扫描

```http
POST /api/sync/scan
Content-Type: application/json

{
  "type": "incremental"
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "scan_type": "incremental",
    "projects_scanned": 127,
    "projects_updated": 15,
    "changes_detected": 8,
    "duration_ms": 8500
  }
}
```

#### 6. 获取项目列表

```http
GET /api/sync/projects?status=outdated&page=1&size=20
```

#### 7. 获取项目差异

```http
GET /api/sync/projects/group1/project-a/diff
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "project_key": "group1/project-a",
    "status": "outdated",
    "diff": {
      "commit_ahead": 5,
      "commit_behind": 0,
      "sync_delay_minutes": 180,
      "size_diff_bytes": 1024000,
      "branch_diff": 2
    },
    "source": {
      "commit_count": 105,
      "branch_count": 12,
      "size_bytes": 15360000,
      "last_activity_at": "2025-12-14T10:00:00"
    },
    "target": {
      "commit_count": 100,
      "branch_count": 10,
      "size_bytes": 14336000,
      "last_activity_at": "2025-12-14T07:00:00"
    }
  }
}
```

### Prometheus指标端点

```http
GET /actuator/prometheus
```

---

## Grafana面板使用

### 导入Dashboard

1. 登录Grafana
2. 点击左侧菜单 "+" → "Import"
3. 上传 `grafana-dashboard.json` 文件
4. 选择Prometheus数据源
5. 点击"Import"

### 面板说明

#### 1. 项目总数趋势
- 显示项目总数的时间序列图
- 可以观察项目数量的增长趋势

#### 2. 同步状态分布
- 饼图显示synced/outdated/failed的分布
- 快速了解整体同步健康状况

#### 3. 活跃告警数
- 按严重级别显示活跃告警
- 柱状图展示critical/high/medium/low

#### 4. 扫描耗时趋势
- 监控扫描性能
- 超过15秒会触发告警

#### 5. Top 10 Commit差异项目
- 表格展示commit差异最大的10个项目
- 快速定位需要关注的项目

#### 6. 项目大小 Top 10
- 显示仓库大小最大的10个项目
- 用于容量规划

#### 7. 告警触发趋势
- 按严重级别展示告警触发频率
- 识别系统性问题

#### 8. API调用频率
- 监控对源/目标GitLab的API调用
- 避免触发速率限制

#### 9. 项目发现统计
- 显示新增和更新的项目数量
- 了解项目变更情况

---

## 告警配置说明

### 告警类型

| 告警类型 | 触发条件 | 严重级别 | 默认阈值 |
|---------|---------|---------|---------|
| sync_delay | 同步延迟过长 | MEDIUM | 60分钟 |
| commit_diff | Commit差异过大 | HIGH | 10个commits |
| size_diff | 仓库大小差异过大 | LOW | 10% |
| sync_failed | 同步失败 | CRITICAL | 立即触发 |

### 告警生命周期

1. **触发**: 当项目违反阈值时自动创建告警
2. **去重**: 同一项目同一类型60分钟内只触发一次
3. **自动解决**: 每10分钟检查，问题修复后自动标记为resolved
4. **手动解决**: 通过API或CLI手动解决
5. **静默**: 可以临时静默告警（1-1440分钟）
6. **清理**: 已解决超过30天的告警自动删除（每周一次）

### 配置告警阈值

修改 `DiffCalculator.java` 中的阈值常量:
```java
private static final int COMMIT_DIFF_THRESHOLD = 10;
private static final long SYNC_DELAY_THRESHOLD_MINUTES = 60;
private static final double SIZE_DIFF_THRESHOLD_PERCENT = 0.1;
```

### Prometheus告警规则

告警规则配置在 `prometheus-alerts.yml`:
- 系统级告警: 同步异常比例、扫描耗时等
- 项目级告警: 项目commit差异、同步延迟等
- 可用性告警: 服务下线、缓存满等
- 趋势告警: 项目数突增、告警频率异常等

---

## 常见问题解答

### Q1: 如何调整扫描频率？

A: 修改 `application.yml`:
```yaml
gitlab:
  mirror:
    monitor:
      incremental-interval: 180000  # 改为3分钟
      full-scan-cron: "0 0 1 * * ?"  # 改为每天凌晨1点
```

### Q2: 如何禁用某个调度器？

A: 修改 `application.yml`:
```yaml
gitlab:
  mirror:
    monitor:
      scheduler:
        incremental-enabled: false  # 禁用增量扫描
```

### Q3: 告警太多怎么办？

A:
1. 提高告警阈值（修改DiffCalculator.java）
2. 使用静默功能临时屏蔽告警
3. 检查是否有系统性问题导致大量告警

### Q4: 如何查看历史扫描记录？

A: 查询 `scan:stats:incremental` 和 `scan:stats:full` 缓存键，或通过Grafana查看历史趋势。

### Q5: Prometheus指标不更新？

A:
1. 检查 `/actuator/prometheus` 端点是否正常
2. 检查Prometheus配置的scrape间隔
3. 检查指标是否正确注册（查看日志）

### Q6: 差异计算不准确？

A:
1. 检查源/目标项目信息是否正确更新
2. 查看 `UPDATE_PROJECT_DATA_SERVICE` 日志
3. 手动触发全量扫描重新计算

### Q7: 如何备份监控数据？

A: 监控数据存储在MySQL的 `MONITOR_ALERT` 表和本地缓存。建议:
1. 定期备份MySQL数据库
2. 缓存数据是临时的，丢失后会自动重建

### Q8: 性能优化建议？

A:
1. 增大缓存TTL减少数据库查询
2. 调整批量查询的perPage参数
3. 使用增量查询而非全量查询
4. 在低峰期执行全量对账

---

## 技术支持

如有问题，请联系：
- 项目仓库: https://github.com/your-org/gitlab-mirror
- 问题反馈: https://github.com/your-org/gitlab-mirror/issues
- 文档地址: https://docs.your-org.com/gitlab-mirror

---

**文档版本**: v1.0.0
**最后更新**: 2025-12-14
**维护者**: GitLab Mirror Team
