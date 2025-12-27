# GitLab Mirror Prometheus Monitoring

## 概述

完整的Prometheus + Alertmanager + Grafana监控解决方案，用于监控GitLab Mirror服务的同步状态、性能和健康度。

## 功能特性

### 监控指标

1. **同步事件指标**
   - `gitlab_mirror_sync_events_total` - 同步事件总数（按类型和状态）
   - `gitlab_mirror_sync_duration_seconds` - 同步耗时分布
   - `gitlab_mirror_sync_delay_seconds` - 同步延时分布

2. **项目状态指标**
   - `gitlab_mirror_projects_by_status` - 各状态项目数量
   - `gitlab_mirror_delayed_projects` - 延时项目数量
   - `gitlab_mirror_max_delay_seconds` - 项目最大延时
   - `gitlab_mirror_last_sync_timestamp` - 项目最后同步时间
   - `gitlab_mirror_consecutive_failures` - 项目连续失败次数

3. **分支变化指标**
   - `gitlab_mirror_branch_changes_total` - 分支变化总数
   - `gitlab_mirror_project_branches` - 项目分支数量分布

4. **API调用指标**
   - `gitlab_mirror_api_calls_total` - GitLab API调用总数

### 告警规则

#### Critical级别
- **HighSyncFailureRate** - 同步失败率 > 30%
- **ManyDelayedProjects** - 延时超过1天的项目 > 10个
- **NoSyncActivity** - 系统停止同步活动
- **ManyFailedProjects** - 失败状态项目 > 5个

#### Warning级别
- **SyncPerformanceDegradation** - P95同步耗时 > 5分钟
- **IncreasedMissingProjects** - Missing项目增加
- **FrequentTaskBlocking** - 任务频繁阻塞
- **ConsecutiveFailures** - 项目连续失败 >= 5次
- **IncreasedDelayedProjects** - 延时项目数量增加

#### Info级别
- **HighBranchChangeRate** - 分支变化率较高
- **UnbalancedProjectStatus** - 失败项目占比 > 10%

## 快速开始

### 1. 启动监控栈

```bash
cd monitoring
docker-compose up -d
```

这将启动以下服务：
- **Prometheus** - http://localhost:9090
- **Alertmanager** - http://localhost:9093
- **Grafana** - http://localhost:3001 (admin/admin)

### 2. 配置GitLab Mirror服务

确保服务已启用Actuator和Prometheus端点（已在application.yml中配置）：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### 3. 验证指标采集

访问 http://localhost:9999/actuator/prometheus 查看暴露的指标。

访问 http://localhost:9090/targets 确认Prometheus能够成功抓取指标。

### 4. 配置告警通知

编辑 `alertmanager/alertmanager.yml` 配置告警接收器：

#### Email通知

```yaml
global:
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_from: 'gitlab-mirror-alerts@example.com'
  smtp_auth_username: 'your-email@example.com'
  smtp_auth_password: 'your-app-password'

receivers:
  - name: 'critical'
    email_configs:
      - to: 'oncall@example.com'
```

#### Slack通知

```yaml
receivers:
  - name: 'critical'
    slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK_URL'
        channel: '#alerts-critical'
```

#### Webhook通知

```yaml
receivers:
  - name: 'critical'
    webhook_configs:
      - url: 'http://your-server:8080/webhook/alerts/critical'
```

重启Alertmanager使配置生效：

```bash
docker-compose restart alertmanager
```

### 5. 查看Grafana仪表盘

1. 访问 http://localhost:3001
2. 使用默认凭据登录: admin / admin
3. 仪表盘会自动加载（通过provisioning配置）
4. 在左侧菜单选择 "Dashboards" -> "GitLab Mirror" 文件夹
5. 打开 "GitLab Mirror 监控总览" 仪表盘

## 架构说明

```
┌─────────────────────────────────┐
│  GitLab Mirror Service (Port 9999) │
│  /actuator/prometheus           │
└──────────┬──────────────────────┘
           │ Pull metrics (15s)
           ▼
┌─────────────────────────────────┐
│  Prometheus (Port 9090)          │
│  - Scrape metrics                │
│  - Evaluate alert rules          │
│  - Store time-series data        │
└──────────┬──────────────────────┘
           │
    ┌──────┴───────┐
    │              │
    ▼              ▼
┌─────────┐  ┌────────────┐
│ Grafana │  │Alertmanager│
│ (3001)  │  │   (9093)   │
└─────────┘  └────────────┘
```

## 常用操作

### 查看所有告警

```bash
# Prometheus web UI
open http://localhost:9090/alerts

# Alertmanager web UI
open http://localhost:9093/#/alerts
```

### 手动触发告警规则评估

```bash
# Reload Prometheus configuration
curl -X POST http://localhost:9090/-/reload

# Reload Alertmanager configuration
curl -X POST http://localhost:9093/-/reload
```

### 查看指标数据

```bash
# 查看所有项目状态
curl -s http://localhost:9999/actuator/prometheus | grep gitlab_mirror_projects_by_status

# 查看延时项目
curl -s http://localhost:9999/actuator/prometheus | grep gitlab_mirror_delayed_projects

# 查看同步事件
curl -s http://localhost:9999/actuator/prometheus | grep gitlab_mirror_sync_events_total
```

### PromQL查询示例

#### 全局查询

```promql
# 最近1小时同步失败率
rate(gitlab_mirror_sync_events_total{status="failed"}[1h])
/
rate(gitlab_mirror_sync_events_total[1h])

# P95同步耗时
histogram_quantile(0.95, rate(gitlab_mirror_sync_duration_seconds_bucket[5m]))

# 延时超过1天的项目数量
gitlab_mirror_delayed_projects{delay_level="1d"}

# 各状态项目分布
sum by (status) (gitlab_mirror_projects_by_status)

# Top 10最久未同步的项目
topk(10, time() - gitlab_mirror_last_sync_timestamp)

# Top 10连续失败最多的项目
topk(10, gitlab_mirror_consecutive_failures)
```

#### 项目级查询

```promql
# 特定项目的同步成功率（最近1小时）
rate(gitlab_mirror_project_sync_events_total{project_key="ai/test-app",status="success"}[1h])
/
rate(gitlab_mirror_project_sync_events_total{project_key="ai/test-app"}[1h])

# 各项目按延时排序（Top 10）
topk(10, gitlab_mirror_project_delay_seconds)

# 各项目的分支创建趋势（最近24小时）
sum by (project_key) (increase(gitlab_mirror_project_branch_changes_total{operation="created"}[24h]))

# 各项目的commit推送统计（最近1天）
topk(20, increase(gitlab_mirror_project_commit_changes_total[1d]))

# Webhook触发的同步成功率（各项目）
sum by (project_key) (rate(gitlab_mirror_project_sync_events_total{category="webhook",status="success"}[1h]))
/
sum by (project_key) (rate(gitlab_mirror_project_sync_events_total{category="webhook"}[1h]))

# Pull任务失败的项目
sum by (project_key) (increase(gitlab_mirror_project_sync_tasks_total{task_type="pull",status="failed"}[1h])) > 0

# 最活跃的项目（按分支变更）
topk(10,
  sum by (project_key) (increase(gitlab_mirror_project_branch_changes_total[24h]))
)

# 各项目的同步任务类型分布
sum by (project_key, task_type) (increase(gitlab_mirror_project_sync_tasks_total[24h]))
```

## 告警处理流程

### Critical: HighSyncFailureRate

**症状**: 同步失败率超过30%

**可能原因**:
1. GitLab源服务器不可用
2. 目标服务器不可用或空间不足
3. 网络连接问题
4. 认证token过期

**处理步骤**:
1. 检查GitLab源和目标服务器状态
2. 查看最近的错误日志: `tail -100 logs/gitlab-mirror.log | grep ERROR`
3. 检查网络连接
4. 验证API token是否有效

### Critical: ManyDelayedProjects

**症状**: 大量项目延时超过1天

**可能原因**:
1. 同步任务队列阻塞
2. 系统资源不足
3. 定时任务未正常运行

**处理步骤**:
1. 检查系统资源使用: CPU、内存、磁盘
2. 查看定时任务日志
3. 手动触发增量扫描: `./cli/bin/gitlab-mirror scan incremental`
4. 重启服务

### Warning: SyncPerformanceDegradation

**症状**: 同步耗时增加

**可能原因**:
1. 项目规模增大（分支/提交数增加）
2. 网络延迟增加
3. GitLab服务器负载高

**处理步骤**:
1. 分析慢查询
2. 优化同步策略
3. 增加并发配置
4. 考虑分批同步

## 性能调优

### 降低指标基数

如果项目数量很多（> 1000），考虑：

1. **限制project_key标签**: 只为top项目记录详细指标
2. **调整采集频率**: 增加MetricsScheduler的执行间隔
3. **使用聚合**: 对某些指标只记录聚合值

### 优化存储

```yaml
# prometheus.yml
global:
  scrape_interval: 30s  # 从15s增加到30s

command:
  - '--storage.tsdb.retention.time=15d'  # 保留15天
  - '--storage.tsdb.retention.size=10GB'  # 限制大小
```

## 故障排查

### Prometheus无法抓取指标

1. 检查服务是否启动: `curl http://localhost:9999/actuator/prometheus`
2. 检查网络连接: `docker network inspect gitlab_mirror_network`
3. 查看Prometheus日志: `docker logs gitlab-mirror-prometheus`

### 告警未触发

1. 检查告警规则: http://localhost:9090/alerts
2. 验证PromQL查询: http://localhost:9090/graph
3. 查看Alertmanager状态: http://localhost:9093/#/status

### Grafana显示No Data

1. 验证数据源配置
2. 检查时间范围
3. 确认Prometheus有数据

## 维护建议

1. **定期检查**: 每周review告警规则和阈值
2. **日志分析**: 定期分析告警历史，调整阈值减少噪音
3. **容量规划**: 监控Prometheus存储使用情况
4. **文档更新**: 记录常见问题和解决方案
5. **演练**: 定期进行告警响应演练

## Grafana仪表盘说明

### 仪表盘概览

**GitLab Mirror 监控总览** 提供完整的系统监控视图，包含以下部分：

#### 1. 项目概览 (Project Overview)
- **总项目数**: 当前系统管理的总项目数量
- **活跃项目**: 处于active状态的项目数
- **失败项目**: 处于failed状态的项目数（超过1个变红）
- **延时>1h项目**: 同步延时超过1小时的项目数
- **延时>1天项目**: 同步延时超过1天的项目数
- **同步事件频率**: 实时同步事件速率趋势图

#### 2. 项目状态分布 (Status Distribution)
- **项目状态分布**: 饼图展示各状态项目占比（active/failed/pending/missing等）
- **延时项目分布**: 柱状图展示不同延时级别的项目数量（1h/6h/1d/3d/7d）

#### 3. 同步事件趋势 (Sync Event Trends)
- **同步事件频率 (按状态)**: 展示success/failed/pending事件的速率趋势
- **同步事件频率 (按类型)**: 展示各类型事件（sync_finished/sync_failed/task_blocked等）的速率趋势

#### 4. 同步性能 (Sync Performance)
- **同步耗时分布**: P50/P95/P99百分位数耗时趋势（阈值：黄色300s，红色600s）
- **分支变更频率**: 按变更类型（created/updated/deleted）展示分支变化速率

#### 5. 告警与异常 (Alerts & Anomalies)
- **同步失败率 (1小时)**: 最近1小时的失败率趋势（阈值：黄色10%，红色30%）
- **连续失败次数 Top 10**: 展示连续失败次数最多的10个项目

#### 6. 项目详情 (Project Details)
- **延时最大项目 Top 20**: 表格展示延时最严重的20个项目及其延时秒数
- **最久未同步项目 Top 20**: 表格展示最长时间未同步的20个项目

### 仪表盘特性

- **自动刷新**: 默认30秒刷新一次
- **时间范围**: 默认显示最近1小时数据，可自定义
- **交互式**: 支持鼠标悬停查看详细数据，点击图例筛选系列
- **响应式**: 自动适配不同屏幕尺寸
- **颜色编码**: 使用阈值颜色标识异常状态（绿色正常，黄色警告，红色严重）

### 手动导入仪表盘

如果自动加载未生效，可以手动导入：

1. 访问 http://localhost:3001
2. 点击左侧菜单 "+" -> "Import dashboard"
3. 点击 "Upload JSON file"
4. 选择 `monitoring/grafana/dashboards/gitlab-mirror-overview.json`
5. 选择 Prometheus 数据源
6. 点击 "Import"

### 自定义仪表盘

仪表盘已配置为可编辑（`allowUiUpdates: true`），你可以：

1. 添加新面板展示其他指标
2. 调整阈值和颜色
3. 修改图表类型
4. 创建新的仪表盘

修改后的仪表盘会保存在Grafana数据库中，但原始JSON文件保持不变。

## 参考资料

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Alertmanager Documentation](https://prometheus.io/docs/alerting/latest/alertmanager/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/dashboards/)
- [Micrometer Documentation](https://micrometer.io/docs)
