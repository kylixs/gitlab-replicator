# T7: 集成测试和文档

**状态**: ✅ 已完成 (Completed)
**依赖**: T6 - 定时调度器
**预计时间**: 2天

---

## 任务目标

- 编写端到端集成测试
- 配置 Grafana 监控面板
- 配置 Prometheus 告警规则
- 编写使用文档
- 性能测试和优化

---

## 子任务

### T7.1 端到端集成测试
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 创建集成测试类 `UnifiedMonitorIntegrationTest`
- 测试完整扫描流程:
  1. 启动测试环境（TestContainers: MySQL, Redis, GitLab）
  2. 初始化测试数据（源项目、目标项目）
  3. 触发增量扫描
  4. 验证项目表更新
  5. 验证差异计算
  6. 验证告警创建
  7. 验证 Redis 缓存
  8. 验证 Prometheus 指标
- 测试全量对账流程
- 测试告警自动解决
- 测试 CLI 命令
- 测试 API 端点
- 清理测试数据

**验收标准**:
- 所有集成测试通过
- 测试覆盖完整流程
- 测试环境自动启动/停止
- 测试数据自动清理

---

### T7.2 性能测试
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 测试批量查询性能
  - 1000个项目批量查询 <30秒
  - 并发查询 5个 <10秒
- 测试差异计算性能
  - 1000个项目差异计算 <5秒
- 测试 Redis 缓存性能
  - 1000个缓存写入 <2秒
  - 缓存读取 <100ms
- 测试 Prometheus 指标导出性能
  - 指标导出响应时间 <100ms
- 测试增量扫描性能
  - 无变更项目跳过 <1秒
  - 70-90% 项目无变更场景下总耗时 <15秒
- 使用 JMeter 或 Gatling 进行压力测试

**性能目标**:
| 操作 | 目标耗时 | 说明 |
|------|---------|------|
| 增量扫描（1000项目，90%无变更） | <15秒 | git ls-remote 检测 |
| 全量对账（1000项目） | <60秒 | 完整对比 |
| 差异计算（1000项目） | <5秒 | 内存计算 |
| Prometheus导出 | <100ms | 指标获取 |

**验收标准**:
- 性能达到目标
- 压力测试通过
- 无内存泄漏
- 无并发问题

---

### T7.3 Grafana 监控面板配置
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 创建 Grafana Dashboard JSON 配置文件
- 配置面板1：系统级监控
  - 项目总数趋势（时间序列）
  - 同步状态分布（饼图）
  - 活跃告警数（柱状图）
  - 扫描耗时趋势（时间序列）
- 配置面板2：项目级监控
  - Top 10 Commit差异项目（表格）
  - 项目同步延迟分布（时间序列）
  - 项目大小 Top 10（柱状图）
- 配置面板3：告警趋势
  - 告警触发趋势（时间序列）
  - 按严重级别分组（堆叠柱状图）
  - 同步异常数量趋势（时间序列）
- 配置面板4：性能监控
  - API调用频率（时间序列）
  - 项目发现统计（new/updated）
- 配置 PromQL 查询
- 配置告警阈值线
- 配置变量和模板

**验收标准**:
- Dashboard 导入成功
- 所有面板正确显示
- PromQL 查询正确
- 图表类型合适
- 刷新间隔合理

---

### T7.4 Prometheus 告警规则配置
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 创建 Prometheus 告警规则文件 `gitlab_mirror_alerts.yml`
- 配置系统级告警:
  - `GitLabMirrorSyncAnomaliesHigh` - 同步异常数量过多
  - `GitLabMirrorSyncFailedHigh` - 同步失败过多
  - `GitLabMirrorCriticalAlerts` - 存在Critical告警
  - `GitLabMirrorScanSlow` - 扫描耗时过长
- 配置项目级告警:
  - `GitLabMirrorProjectCommitDiffHigh` - 项目commit差异过大
  - `GitLabMirrorProjectSyncDelayHigh` - 项目同步延迟过长
  - `GitLabMirrorProjectSizeDiffHigh` - 项目大小差异过大
- 配置 AlertManager 路由
- 配置通知渠道（邮件/企业微信/钉钉）

**验收标准**:
- 告警规则正确加载
- 告警正确触发
- 通知正确发送
- 告警标签正确

---

### T7.5 使用文档编写
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 编写用户手册 `docs/unified-monitor/USER_GUIDE.md`:
  - 功能介绍
  - 快速开始
  - API 使用指南
  - CLI 使用指南
  - Grafana 面板使用
  - 告警配置说明
  - 常见问题解答
- 编写运维手册 `docs/unified-monitor/OPS_GUIDE.md`:
  - 部署指南
  - 配置说明
  - 性能调优
  - 监控指标说明
  - 故障排查
- 编写开发文档 `docs/unified-monitor/DEV_GUIDE.md`:
  - 架构说明
  - 模块划分
  - 扩展开发
  - 测试指南
- 更新主 README.md

**验收标准**:
- 文档结构清晰
- 内容完整准确
- 示例代码可运行
- 截图清晰

---

## 提交信息

```
feat(monitor): add integration tests, grafana dashboard and documentation
```

---

## 参考文档

- [UNIFIED_PROJECT_MONITOR_DESIGN.md](../UNIFIED_PROJECT_MONITOR_DESIGN.md)
- Grafana Dashboard 最佳实践
- Prometheus 告警规则编写指南
