# T6: 定时调度器

**状态**: ✅ 已完成 (Completed)
**依赖**: T5 - 监控模块
**预计时间**: 1天

---

## 任务目标

- 实现增量扫描定时任务（5分钟）
- 实现全量对账定时任务（每天）
- 实现告警自动解决定时任务
- 配置调度参数
- 实现调度日志记录

---

## 子任务

### T6.1 增量扫描调度器
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 创建 `MonitorScheduler` 调度器类
- 实现 `incrementalScan()` 方法
  - 使用 `@Scheduled(fixedDelay = 300000)` 注解（5分钟）
  - 或使用配置参数: `monitor.incremental-interval`
  - 调用 UnifiedProjectMonitor.scan("incremental")
  - 记录扫描开始/结束时间
  - 记录扫描结果统计
  - 异常处理和日志记录
- 实现调度锁（防止重复执行）
  - 使用 Redis 分布式锁
  - 锁超时时间: 10分钟
- 实现调度开关（支持动态启停）

**验收标准**:
- 定时任务正确触发
- 时间间隔准确
- 分布式锁生效
- 异常不影响下次调度
- 日志记录完整

---

### T6.2 全量对账调度器
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 实现 `fullScan()` 方法
  - 使用 `@Scheduled(cron = "0 0 2 * * ?")` 注解（每天凌晨2点）
  - 或使用配置参数: `monitor.full-scan-cron`
  - 调用 UnifiedProjectMonitor.scan("full")
  - 执行全量对账检查:
    - 检查新项目
    - 检查删除项目
    - 检查数据一致性
  - 生成对账报告
  - 记录对账结果
- 实现对账报告生成
  - 新增项目列表
  - 删除项目列表
  - 异常项目列表
  - 统计摘要
- 实现报告存储（数据库或文件）

**验收标准**:
- 定时任务正确触发
- Cron 表达式正确
- 全量对账完整
- 报告内容准确
- 报告正确存储

---

### T6.3 告警自动解决调度器
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 实现 `autoResolveAlerts()` 方法
  - 使用 `@Scheduled(fixedDelay = 600000)` 注解（10分钟）
  - 查询活跃告警（status = active）
  - 对每个告警检查问题是否已修复
  - 自动标记为 resolved
  - 记录解决日志
- 实现过期告警清理
  - 清理已解决超过30天的告警
  - 定期执行（每周一次）

**验收标准**:
- 定时任务正确触发
- 告警检查逻辑正确
- 自动解决准确
- 清理逻辑正确
- 日志记录完整

---

### T6.4 调度配置
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 在 `application.yml` 中添加调度配置:
  ```yaml
  gitlab:
    mirror:
      monitor:
        # 增量扫描间隔（毫秒）
        incremental-interval: 300000  # 5分钟
        # 全量对账 Cron
        full-scan-cron: "0 0 2 * * ?"  # 每天凌晨2点
        # 告警自动解决间隔（毫秒）
        auto-resolve-interval: 600000  # 10分钟
        # 调度开关
        scheduler:
          enabled: true
          incremental-enabled: true
          full-scan-enabled: true
          auto-resolve-enabled: true
  ```
- 实现配置类 `MonitorSchedulerProperties`
- 实现动态配置刷新（@RefreshScope）

**验收标准**:
- 配置正确加载
- 参数正确应用
- 动态刷新生效
- 默认值合理

---

### T6.5 单元测试
**状态**: ✅ 已完成 (Completed)

**任务内容**:
- 测试增量扫描调度
- 测试全量对账调度
- 测试告警自动解决
- 测试分布式锁
- 测试配置加载
- Mock 调度触发

**验收标准**:
- 所有测试通过
- 调度逻辑正确
- 分布式锁测试通过
- Mock 正确

---

## 提交信息

```
feat(monitor): implement schedulers for incremental scan and full reconciliation
```

---

## 参考文档

- [UNIFIED_PROJECT_MONITOR_DESIGN.md - 调度流程](../UNIFIED_PROJECT_MONITOR_DESIGN.md#🔄-关键处理流程)
- Spring @Scheduled 文档
