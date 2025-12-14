# T3: 差异计算服务

**状态**: ⏸️ 待处理 (Pending)
**依赖**: T2 - 批量查询服务
**预计时间**: 2天

---

## 任务目标

- 实现 DiffCalculator 差异计算服务
- 内存计算源和目标项目差异
- 缓存差异结果到本地内存（使用ConcurrentHashMap）
- 实现告警阈值判定
- 支持差异结果查询

---

## 子任务

### T3.1 差异对象模型
**状态**: ⏸️ 待处理

**任务内容**:
- 创建 `ProjectDiff` 差异对象类
- 定义字段:
  - `projectKey` - 项目标识
  - `source` - 源项目快照（ProjectSnapshot）
  - `target` - 目标项目快照（ProjectSnapshot）
  - `diff` - 差异详情（DiffDetails）
  - `status` - 同步状态（synced/outdated/failed/inconsistent）
  - `checkedAt` - 检查时间
- 创建 `ProjectSnapshot` 快照对象:
  - `commitSha` - 提交SHA
  - `commitCount` - 提交数量
  - `branchCount` - 分支数量
  - `sizeBytes` - 仓库大小
  - `lastActivityAt` - 最后活动时间
- 创建 `DiffDetails` 差异详情对象:
  - `commitBehind` - commit 落后数量
  - `syncDelayMinutes` - 同步延迟（分钟）
  - `sizeDiffPercent` - 大小差异百分比
  - `branchDiff` - 分支数量差异

**验收标准**:
- 对象模型结构清晰
- 支持 JSON 序列化/反序列化
- 字段类型正确
- 包含必要的 getter/setter

---

### T3.2 差异计算逻辑
**状态**: ⏸️ 待处理

**任务内容**:
- 创建 `DiffCalculator` 服务类
- 实现 `calculateDiff()` 方法
  - 获取源项目信息（SOURCE_PROJECT_INFO）
  - 获取目标项目信息（TARGET_PROJECT_INFO）
  - 检查目标是否存在
  - 逐字段对比计算差异:
    - commit SHA 对比
    - 时间差异计算（分钟）
    - 大小差异计算（百分比）
    - 分支数量差异
- 实现 `determineSyncStatus()` 状态判定方法
  - `synced`: SHA 一致且延迟 <5分钟
  - `outdated`: SHA 不一致或延迟 >30分钟
  - `failed`: 目标不存在或有错误
  - `inconsistent`: 分支数量/大小差异过大
- 实现批量计算（支持多个项目）

**验收标准**:
- 差异计算准确
- 状态判定正确
- 百分比计算准确（避免除零）
- 时间差异正确（考虑时区）
- 批量计算高效

---

### T3.3 内存缓存
**状态**: ⏸️ 待处理

**任务内容**:
- 创建 `LocalCacheManager` 本地缓存管理类
  - 使用 `ConcurrentHashMap<String, CacheEntry<T>>` 存储数据
  - 支持 TTL 过期管理（后台线程定期清理）
- 实现 `cacheDiff()` 方法
  - Key: project_key
  - Value: ProjectDiff 对象
  - TTL: 15分钟
- 实现 `getCachedDiff()` 方法
  - 读取缓存，检查过期时间
  - 缓存不存在或已过期返回 null
- 实现 `cacheStats()` 方法
  - Key: "monitor:stats"
  - Value: 统计摘要对象
  - TTL: 5分钟
- 实现缓存批量写入
- 实现缓存失效策略（定期清理过期条目）
- 实现缓存统计（命中率、大小等）

**统计摘要内容**:
- `total_projects` - 项目总数
- `synced` - 同步成功数量
- `outdated` - 延迟数量
- `failed` - 失败数量
- `inconsistent` - 不一致数量
- `updated_at` - 更新时间

**验收标准**:
- 缓存读写线程安全
- TTL 自动过期生效
- 内存使用合理（防止内存泄漏）
- 批量操作高效
- 支持并发访问

---

### T3.4 告警阈值判定
**状态**: ⏸️ 待处理

**任务内容**:
- 实现 `evaluateThresholds()` 方法
- 定义阈值规则（从配置读取）:
  - `sync-delay-minutes`: 30（警告）
  - `critical-delay-hours`: 2（严重）
  - `commit-diff-alert`: 10（告警）
  - `size-diff-tolerance`: 5%（容忍度）
- 实现告警判定逻辑:
  - `sync_delay`: 延迟超过阈值
  - `commit_diff`: commit 差异超过阈值
  - `branch_diff`: 分支数量不一致
  - `size_diff`: 大小差异超过容忍度
  - `target_missing`: 目标项目不存在
- 返回需要告警的项目列表和告警类型
- 实现严重程度判定（critical/high/medium/low）

**验收标准**:
- 阈值规则正确应用
- 告警判定准确
- 严重程度分级正确
- 配置可动态调整
- 支持多条告警（一个项目多个问题）

---

### T3.5 单元测试
**状态**: ⏸️ 待处理

**任务内容**:
- 测试差异计算准确性
- 测试状态判定逻辑
- 测试本地缓存读写
- 测试缓存TTL过期逻辑
- 测试并发缓存访问
- 测试告警阈值判定
- 测试边界情况（除零、null 值等）
- 测试批量计算性能

**验收标准**:
- 所有测试通过
- 边界情况正确处理
- 性能达标（1000个项目差异计算 <5秒）

---

## 提交信息

```
feat(monitor): implement diff calculator with local cache and alert threshold
```

---

## 参考文档

- [UNIFIED_PROJECT_MONITOR_DESIGN.md - 差异计算](../UNIFIED_PROJECT_MONITOR_DESIGN.md#🔄-关键处理流程)
- [UNIFIED_PROJECT_MONITOR_DESIGN.md - 本地内存缓存](../UNIFIED_PROJECT_MONITOR_DESIGN.md#本地内存缓存结构)
