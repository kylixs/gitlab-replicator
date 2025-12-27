# Prometheus Monitoring Design for GitLab Mirror

## 1. 监控目标

### 1.1 核心监控场景

1. **同步失败率过高** - 最近1小时/24小时的失败率超过阈值
2. **同步延时过大** - 项目延时超过阈值的数量增加
3. **长时间未同步** - 项目长时间没有执行同步操作
4. **同步任务阻塞** - task_blocked事件频繁发生
5. **同步性能下降** - 平均同步耗时增加
6. **目标项目缺失** - missing状态的项目数量增加

### 1.2 次要监控场景

1. **分支变化率** - 监控分支创建/更新/删除的频率
2. **webhook响应异常** - webhook触发的同步失败率
3. **定时任务异常** - scheduled sync失败率
4. **系统资源** - JVM内存、线程池使用情况

## 2. Prometheus指标设计

### 2.1 Counter指标（累计计数）

#### 全局计数器

```
# 同步事件总数（按事件类型、状态分组）
gitlab_mirror_sync_events_total{event_type="sync_finished|sync_failed|task_blocked|...", status="success|failed", project_key="xxx"}

# 分支变化总数
gitlab_mirror_branch_changes_total{change_type="created|updated|deleted", project_key="xxx"}

# API调用总数
gitlab_mirror_api_calls_total{api_type="source|target", status="success|failed"}
```

#### 项目级计数器

```
# 项目同步事件统计（按项目、类别、状态）
gitlab_mirror_project_sync_events_total{project_key="xxx", category="webhook|scheduled|manual", status="success|failed|pending"}

# 项目同步任务执行统计（按项目、类型、状态）
gitlab_mirror_project_sync_tasks_total{project_key="xxx", task_type="pull|push|mirror_setup", status="success|failed|timeout"}

# 项目分支变更统计（按项目、操作类型）
gitlab_mirror_project_branch_changes_total{project_key="xxx", operation="created|updated|deleted"}

# 项目commit变更统计（按项目）
gitlab_mirror_project_commit_changes_total{project_key="xxx"}
```

### 2.2 Gauge指标（当前值）

#### 全局状态指标

```
# 各状态项目数量
gitlab_mirror_projects_by_status{status="active|pending|missing|failed|deleted"}

# 延时项目数量（按延时级别分组）
gitlab_mirror_delayed_projects{delay_level="1h|6h|1d|3d|7d"}
```

#### 项目级状态指标

```
# 项目同步延时（秒）- 每个项目的当前延时
gitlab_mirror_project_delay_seconds{project_key="xxx"}

# 项目最大延时（秒）- 历史最大延时
gitlab_mirror_max_delay_seconds{project_key="xxx"}

# 项目最后同步时间（Unix时间戳）
gitlab_mirror_last_sync_timestamp{project_key="xxx"}

# 项目连续失败次数
gitlab_mirror_consecutive_failures{project_key="xxx"}
```

### 2.3 Histogram指标（分布统计）

```
# 同步耗时分布
gitlab_mirror_sync_duration_seconds_bucket{le="5|10|30|60|120|300|600|+Inf"}
gitlab_mirror_sync_duration_seconds_sum
gitlab_mirror_sync_duration_seconds_count

# 分支数量分布
gitlab_mirror_project_branches_bucket{le="10|50|100|500|1000|+Inf"}
```

### 2.4 Summary指标（百分位数）

```
# 同步延时百分位数
gitlab_mirror_sync_delay_seconds{quantile="0.5|0.9|0.95|0.99"}
gitlab_mirror_sync_delay_seconds_sum
gitlab_mirror_sync_delay_seconds_count
```

## 3. 告警规则设计

### 3.1 Critical级别告警（立即处理）

```yaml
# 1. 同步失败率过高
- alert: HighSyncFailureRate
  expr: |
    (
      rate(gitlab_mirror_sync_events_total{status="failed"}[1h])
      /
      rate(gitlab_mirror_sync_events_total[1h])
    ) > 0.3
  for: 10m
  labels:
    severity: critical
  annotations:
    summary: "同步失败率过高"
    description: "最近1小时同步失败率 {{ $value | humanizePercentage }}，超过30%阈值"

# 2. 大量项目延时
- alert: ManyDelayedProjects
  expr: gitlab_mirror_delayed_projects{delay_level="1d"} > 10
  for: 30m
  labels:
    severity: critical
  annotations:
    summary: "大量项目同步延时超过1天"
    description: "{{ $value }}个项目延时超过1天"

# 3. 关键项目长时间未同步
- alert: CriticalProjectNotSynced
  expr: |
    (time() - gitlab_mirror_last_sync_timestamp{project_key=~"critical/.*"}) > 86400
  for: 1h
  labels:
    severity: critical
  annotations:
    summary: "关键项目 {{ $labels.project_key }} 超过24小时未同步"
    description: "最后同步时间: {{ $value | humanizeDuration }}"

# 4. 系统级同步停滞
- alert: NoSyncActivity
  expr: |
    rate(gitlab_mirror_sync_events_total[30m]) == 0
  for: 1h
  labels:
    severity: critical
  annotations:
    summary: "系统停止同步活动"
    description: "最近30分钟没有任何同步事件"
```

### 3.2 Warning级别告警（需要关注）

```yaml
# 1. 同步性能下降
- alert: SyncPerformanceDegradation
  expr: |
    histogram_quantile(0.95,
      rate(gitlab_mirror_sync_duration_seconds_bucket[1h])
    ) > 300
  for: 30m
  labels:
    severity: warning
  annotations:
    summary: "同步性能下降"
    description: "P95同步耗时 {{ $value }}秒，超过5分钟"

# 2. Missing项目增加
- alert: IncreasedMissingProjects
  expr: |
    increase(gitlab_mirror_projects_by_status{status="missing"}[1h]) > 5
  labels:
    severity: warning
  annotations:
    summary: "Missing状态项目增加"
    description: "最近1小时新增 {{ $value }} 个missing项目"

# 3. 任务频繁阻塞
- alert: FrequentTaskBlocking
  expr: |
    rate(gitlab_mirror_sync_events_total{event_type="task_blocked"}[1h]) > 0.1
  for: 15m
  labels:
    severity: warning
  annotations:
    summary: "同步任务频繁阻塞"
    description: "最近1小时task_blocked事件频率: {{ $value }}/s"

# 4. 连续失败项目
- alert: ConsecutiveFailures
  expr: gitlab_mirror_consecutive_failures >= 5
  labels:
    severity: warning
  annotations:
    summary: "项目 {{ $labels.project_key }} 连续失败"
    description: "连续失败次数: {{ $value }}"

# 5. Webhook同步失败率高
- alert: HighWebhookFailureRate
  expr: |
    (
      rate(gitlab_mirror_sync_events_total{event_type="webhook_sync",status="failed"}[1h])
      /
      rate(gitlab_mirror_sync_events_total{event_type="webhook_sync"}[1h])
    ) > 0.2
  for: 15m
  labels:
    severity: warning
  annotations:
    summary: "Webhook同步失败率高"
    description: "Webhook同步失败率 {{ $value | humanizePercentage }}"
```

### 3.3 Info级别告警（信息通知）

```yaml
# 1. 大量分支变化
- alert: HighBranchChangeRate
  expr: |
    rate(gitlab_mirror_branch_changes_total[1h]) > 100
  for: 30m
  labels:
    severity: info
  annotations:
    summary: "分支变化率较高"
    description: "最近1小时分支变化率: {{ $value }}/s"

# 2. 项目状态分布异常
- alert: UnbalancedProjectStatus
  expr: |
    gitlab_mirror_projects_by_status{status="failed"}
    /
    sum(gitlab_mirror_projects_by_status) > 0.1
  labels:
    severity: info
  annotations:
    summary: "失败状态项目占比较高"
    description: "失败项目占比: {{ $value | humanizePercentage }}"
```

## 4. 指标采集实现

### 4.1 MetricsCollector接口

```java
public interface MetricsCollector {
    // Counter指标
    void incrementSyncEvent(String eventType, String status, String projectKey);
    void incrementBranchChange(String changeType, String projectKey);
    void incrementApiCall(String apiType, String status);

    // Gauge指标
    void updateProjectsByStatus(Map<String, Long> statusCounts);
    void updateDelayedProjects(Map<String, Integer> delayLevels);
    void updateProjectMetrics(String projectKey, long delaySeconds,
                             long lastSyncTimestamp, int consecutiveFailures);

    // Histogram指标
    void recordSyncDuration(double seconds);
    void recordProjectBranches(int branchCount);

    // Summary指标
    void recordSyncDelay(double seconds);
}
```

### 4.2 定期采集任务

```java
@Scheduled(fixedRate = 60000) // 每分钟
public void collectMetrics() {
    // 1. 统计各状态项目数量
    Map<String, Long> statusCounts = projectMapper.countByStatus();
    metricsCollector.updateProjectsByStatus(statusCounts);

    // 2. 统计延时项目
    Map<String, Integer> delayedCounts = calculateDelayedProjects();
    metricsCollector.updateDelayedProjects(delayedCounts);

    // 3. 更新每个项目的指标
    List<ProjectMetric> metrics = projectMapper.selectProjectMetrics();
    for (ProjectMetric metric : metrics) {
        metricsCollector.updateProjectMetrics(
            metric.getProjectKey(),
            metric.getDelaySeconds(),
            metric.getLastSyncTimestamp(),
            metric.getConsecutiveFailures()
        );
    }
}
```

### 4.3 事件驱动采集

```java
@Async
public void onSyncEvent(SyncEvent event) {
    // 记录事件计数
    metricsCollector.incrementSyncEvent(
        event.getEventType(),
        event.getStatus(),
        event.getProjectKey()
    );

    // 记录同步耗时
    if (event.getDurationSeconds() != null) {
        metricsCollector.recordSyncDuration(event.getDurationSeconds());
    }

    // 记录分支变化
    if (event.getStatistics() != null) {
        SyncStatistics stats = event.getStatistics();
        if (stats.getBranchesCreated() > 0) {
            metricsCollector.incrementBranchChange("created", event.getProjectKey());
        }
        if (stats.getBranchesUpdated() > 0) {
            metricsCollector.incrementBranchChange("updated", event.getProjectKey());
        }
        if (stats.getBranchesDeleted() > 0) {
            metricsCollector.incrementBranchChange("deleted", event.getProjectKey());
        }
    }
}
```

## 5. Grafana仪表盘设计

### 5.1 Overview面板

- 总项目数、活跃项目数、失败项目数、Missing项目数
- 最近24小时同步成功率趋势
- 最近24小时同步事件分布（饼图）
- Top 10延时项目

### 5.2 Sync Performance面板

- 同步耗时P50/P95/P99趋势
- 同步QPS趋势
- 同步成功率vs失败率对比
- 各事件类型趋势（sync_finished, sync_failed, task_blocked等）

### 5.3 Project Health面板

- 延时项目分布（1h/6h/1d/3d/7d）
- 各状态项目数量趋势
- 连续失败Top项目列表
- 最久未同步Top项目列表

### 5.4 Branch Changes面板

- 分支变化趋势（created/updated/deleted）
- Top活跃项目（按分支变化量）
- 分支数量分布直方图

### 5.5 Alerts面板

- 当前告警列表
- 告警历史趋势
- 告警按严重级别分组统计

## 6. 部署架构

```
┌─────────────────────────────────────────────────────────────┐
│  GitLab Mirror Service                                       │
│  ┌────────────────────┐    ┌─────────────────────┐         │
│  │  MetricsCollector  │───▶│ /actuator/prometheus│         │
│  │  (Micrometer)      │    │  (HTTP Endpoint)     │         │
│  └────────────────────────┘ └─────────────────────┘         │
└───────────────────────────────────────┬─────────────────────┘
                                        │ Pull metrics
                                        ▼
┌─────────────────────────────────────────────────────────────┐
│  Prometheus Server                                           │
│  - Scrape interval: 15s                                      │
│  - Retention: 15d                                            │
│  - Storage: Local disk                                       │
└───────────────────────┬─────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
┌──────────────┐ ┌─────────────┐ ┌────────────────┐
│ Alertmanager │ │   Grafana   │ │  Prometheus    │
│              │ │             │ │  Pushgateway   │
│ - Email      │ │ - Dashboard │ │  (Optional)    │
│ - Slack      │ │ - Alerts    │ └────────────────┘
│ - Webhook    │ └─────────────┘
└──────────────┘
```

## 7. 配置示例

### 7.1 application.yml (Spring Boot)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info,metrics
  metrics:
    tags:
      application: gitlab-mirror
      environment: production
    export:
      prometheus:
        enabled: true
```

### 7.2 prometheus.yml

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'gitlab-mirror'
    static_configs:
      - targets: ['localhost:9999']
    metrics_path: '/actuator/prometheus'

rule_files:
  - 'alerts.yml'

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['localhost:9093']
```

### 7.3 alertmanager.yml

```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'critical'
    - match:
        severity: warning
      receiver: 'warning'

receivers:
  - name: 'default'
    email_configs:
      - to: 'team@example.com'

  - name: 'critical'
    email_configs:
      - to: 'oncall@example.com'
    slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK_URL'
        channel: '#alerts-critical'

  - name: 'warning'
    slack_configs:
      - api_url: 'YOUR_SLACK_WEBHOOK_URL'
        channel: '#alerts-warning'
```

## 8. 实施计划

1. **Phase 1: 基础指标** (Week 1)
   - 添加Micrometer依赖
   - 实现MetricsCollector
   - 暴露/actuator/prometheus端点
   - 基础Counter和Gauge指标

2. **Phase 2: Prometheus部署** (Week 1-2)
   - Docker Compose配置
   - Prometheus + Alertmanager部署
   - 基础告警规则

3. **Phase 3: 高级指标** (Week 2)
   - Histogram和Summary指标
   - 定期采集任务
   - 事件驱动指标

4. **Phase 4: 可视化** (Week 3)
   - Grafana仪表盘
   - 告警通知集成
   - 文档和运维手册

5. **Phase 5: 优化调优** (Week 4)
   - 性能优化
   - 告警规则调优
   - 减少误报

## 9. 维护建议

1. **指标清理**: 定期清理不活跃项目的label，避免高基数问题
2. **采样率调整**: 根据实际负载调整scrape_interval
3. **告警调优**: 根据运行情况调整阈值，减少噪音
4. **Dashboard更新**: 定期review和更新Grafana面板
5. **文档维护**: 记录告警处理步骤和troubleshooting指南
