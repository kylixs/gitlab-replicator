#!/bin/bash

echo "=========================================="
echo "GitLab Mirror 监控系统验证"
echo "=========================================="
echo ""

# 1. 检查容器状态
echo "1. 检查Docker容器状态"
echo "-------------------------------------------"
docker ps --filter "name=gitlab-mirror-prometheus" --format "Prometheus: {{.Status}}"
docker ps --filter "name=gitlab-mirror-alertmanager" --format "Alertmanager: {{.Status}}"
docker ps --filter "name=gitlab-mirror-grafana" --format "Grafana: {{.Status}}"
echo ""

# 2. 检查Prometheus
echo "2. 检查Prometheus"
echo "-------------------------------------------"
PROM_HEALTH=$(curl -s http://localhost:9090/-/healthy)
if [ "$PROM_HEALTH" = "Prometheus is Healthy." ]; then
    echo "✓ Prometheus健康检查: OK"
else
    echo "✗ Prometheus健康检查失败"
fi

# 检查Prometheus targets
TARGETS=$(curl -s http://localhost:9090/api/v1/targets | python3 -c "import sys, json; d=json.load(sys.stdin); print(len([t for t in d['data']['activeTargets'] if t['health']=='up']))")
echo "✓ Prometheus活跃目标: $TARGETS"
echo ""

# 3. 检查Alertmanager
echo "3. 检查Alertmanager"
echo "-------------------------------------------"
AM_STATUS=$(curl -s http://localhost:9093/-/healthy)
if [ "$AM_STATUS" = "OK" ]; then
    echo "✓ Alertmanager健康检查: OK"
else
    echo "✗ Alertmanager健康检查失败"
fi
echo ""

# 4. 检查Grafana
echo "4. 检查Grafana"
echo "-------------------------------------------"
GRAFANA_HEALTH=$(curl -s http://localhost:3001/api/health | python3 -c "import sys, json; d=json.load(sys.stdin); print(d['database'])")
if [ "$GRAFANA_HEALTH" = "ok" ]; then
    echo "✓ Grafana健康检查: OK"
else
    echo "✗ Grafana健康检查失败"
fi

# 检查dashboard文件
DASHBOARD_FILE=$(docker exec gitlab-mirror-grafana ls /var/lib/grafana/dashboards/gitlab-mirror-overview.json 2>/dev/null)
if [ -n "$DASHBOARD_FILE" ]; then
    echo "✓ Dashboard文件已挂载"
    DASHBOARD_PANELS=$(docker exec gitlab-mirror-grafana cat /var/lib/grafana/dashboards/gitlab-mirror-overview.json | python3 -c "import sys, json; d=json.load(sys.stdin); print(len(d['panels']))")
    echo "  - 包含 $DASHBOARD_PANELS 个面板"
else
    echo "✗ Dashboard文件未找到"
fi

# 检查provisioning配置
PROV_FILE=$(docker exec gitlab-mirror-grafana ls /etc/grafana/provisioning/dashboards/dashboard.yml 2>/dev/null)
if [ -n "$PROV_FILE" ]; then
    echo "✓ Dashboard provisioning配置已加载"
else
    echo "✗ Dashboard provisioning配置未找到"
fi
echo ""

# 5. 检查GitLab Mirror指标
echo "5. 检查GitLab Mirror指标"
echo "-------------------------------------------"
METRICS_COUNT=$(curl -s http://localhost:9999/actuator/prometheus | grep "^# TYPE gitlab_mirror" | wc -l | tr -d ' ')
echo "✓ GitLab Mirror指标类型数: $METRICS_COUNT"

# 检查关键指标
echo "  关键指标检查:"
for metric in "gitlab_mirror_projects_by_status" "gitlab_mirror_delayed_projects" "gitlab_mirror_sync_events_total"; do
    COUNT=$(curl -s http://localhost:9999/actuator/prometheus | grep -c "^$metric")
    if [ $COUNT -gt 0 ]; then
        echo "    ✓ $metric"
    else
        echo "    ✗ $metric (未找到)"
    fi
done
echo ""

# 6. 访问URL
echo "6. 访问URL"
echo "-------------------------------------------"
echo "Prometheus: http://localhost:9090"
echo "Alertmanager: http://localhost:9093"
echo "Grafana: http://localhost:3001"
echo ""
echo "Grafana登录:"
echo "  用户名: admin"
echo "  密码: admin"
echo ""
echo "Dashboard路径:"
echo "  Dashboards -> GitLab Mirror -> GitLab Mirror 监控总览"
echo ""

echo "=========================================="
echo "验证完成"
echo "=========================================="
