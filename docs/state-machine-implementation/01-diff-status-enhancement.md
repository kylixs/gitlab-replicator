# 模块 1: 差异状态增强 (Diff Status Enhancement)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 实现差异状态计算逻辑，支持 synced/outdated/ahead/diverged/unknown/target_missing/source_missing 七种状态，为项目同步提供精确的数据差异判断。

**预计时间**: 2-3天

---

## 参考文档

- [状态机设计文档](../state-machine-design.md)
  - [差异状态定义](../state-machine-design.md#31-状态定义)
  - [计算逻辑](../state-machine-design.md#32-计算逻辑)
  - [差异检测维度](../state-machine-design.md#33-差异检测维度)
  - [差异数据结构](../state-machine-design.md#34-差异数据结构)

---

## ⚠️ 重要提醒：任务状态管理规范

**【必须】在开始处理下面的每个子任务前及后需要修改其任务状态：**

1. **开始任务前**：将任务状态从 `⏸️ 待处理 (Pending)` 修改为 `🔄 进行中 (In Progress)`
2. **完成任务后**：将任务状态修改为 `✅ 已完成 (Completed)` 或 `❌ 失败 (Failed)`
3. **更新位置**：在本文档对应任务的 `**状态**:` 行进行修改

**状态标记说明**：
- `⏸️ 待处理 (Pending)` - 任务未开始
- `🔄 进行中 (In Progress)` - 任务正在处理中
- `✅ 已完成 (Completed)` - 任务成功完成，测试通过
- `❌ 失败 (Failed)` - 任务失败，需要修复
- `⚠️ 阻塞 (Blocked)` - 任务被依赖阻塞

---

## 任务清单

### T1.1 扩展现有差异状态枚举
**状态**: ✅ 已完成 (Completed)
**依赖**: 无

**任务目标**:
扩展现有的 `ProjectDiff.SyncStatus` 和 `BranchComparison.BranchSyncStatus` 枚举，添加 AHEAD 和 DIVERGED 状态。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/service/monitor/model/ProjectDiff.java`
- `server/src/main/java/com/gitlab/mirror/server/service/monitor/model/BranchComparison.java`

**核心改造**:

1. **扩展 ProjectDiff.SyncStatus 枚举**
   - 保留现有：`SYNCED`, `OUTDATED`, `PENDING`, `FAILED`, `INCONSISTENT`
   - 新增：`AHEAD` - 目标领先于源
   - 新增：`DIVERGED` - 源和目标历史分裂
   - 新增：`SOURCE_MISSING` - 源项目不存在

2. **扩展 BranchComparison.BranchSyncStatus 枚举**
   - 保留现有：`SYNCED`, `OUTDATED`, `MISSING_IN_TARGET`, `EXTRA_IN_TARGET`
   - 新增：`AHEAD` - 目标领先于源
   - 新增：`DIVERGED` - 历史分裂

3. **扩展 BranchComparison 数据结构**
   - 添加 `sourceCommittedAt` - 源提交时间
   - 添加 `targetCommittedAt` - 目标提交时间
   - 添加 `commitTimeDiffSeconds` - 提交时间差异
   - 添加 `isProtected` - 是否保护分支

**关键点**:
- 保持现有代码的兼容性，不破坏现有逻辑
- 枚举值添加文档注释说明用途
- BranchComparison 添加时间相关字段用于判断领先/落后
- 优先级：`DIVERGED` > `AHEAD` > `OUTDATED` > `SYNCED`

**验收标准**:
- 现有枚举成功扩展，新增状态值
- BranchComparison 包含时间字段
- 现有单元测试仍然通过
- 编写并通过单元测试验证新增枚举值

**提交**: `feat(diff): extend diff status enums with AHEAD and DIVERGED`

---

### T1.2 增强 DiffCalculator 的分支比对逻辑
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T1.1

**任务目标**:
增强现有 DiffCalculator 的 compareBranches 方法，添加基于提交时间的领先/落后/分裂判断逻辑。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/service/monitor/DiffCalculator.java`

**核心改造**:

1. **改造 compareBranchesFromSnapshots() 方法**
   - 从 ProjectBranchSnapshot 中读取 `committed_at` 字段
   - 将时间信息添加到 BranchComparison 对象
   - 根据时间判断分支状态（而不仅仅基于 SHA）

2. **改造 compareBranchMaps() 方法**
   - 当 SHA 不同时，比较 `committed_at` 时间戳
   - 若 `target_time > source_time` → `AHEAD`
   - 若 `source_time > target_time` → `OUTDATED`
   - 若时间差 < 1小时且 SHA 不同 → `DIVERGED`（简化检测）

3. **新增 detectDivergence() 辅助方法**
   - 输入：sourceBranch, targetBranch（包含时间信息）
   - 逻辑：时间差 < 1小时 且 SHA 不同 → 可能分裂
   - 返回：boolean

**关键点**:
- 保持现有方法签名和返回值不变
- 从数据库快照中读取 `committed_at` 字段（ProjectBranchSnapshot 已有）
- 分裂检测使用启发式算法（时间阈值）
- 添加日志记录分支比对的详细信息

**验收标准**:
- DiffCalculator 正确识别 AHEAD 和 DIVERGED 状态
- 基于时间的判断逻辑准确
- 现有测试 DiffCalculatorTest 仍然通过
- 新增测试用例覆盖 AHEAD 和 DIVERGED 场景
- 编写并通过单元测试验证增强后的分支比对逻辑

**提交**: `refactor(diff): enhance branch comparison with time-based status detection`

---

### T1.3 增强 DiffCalculator 的项目级状态判断
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T1.2

**任务目标**:
改造现有 DiffCalculator 的 determineSyncStatus 方法，根据分支比对结果判断项目级 AHEAD 和 DIVERGED 状态。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/service/monitor/DiffCalculator.java`
- `server/src/main/java/com/gitlab/mirror/server/service/monitor/model/DiffDetails.java`

**核心改造**:

1. **改造 determineSyncStatus() 方法**
   - 保留现有逻辑：检查 source/target 是否存在
   - 新增：检查分支比对结果（branchComparisons）
   - 优先级：`SOURCE_MISSING` > `PENDING` > `DIVERGED` > `AHEAD` > `INCONSISTENT` > `OUTDATED` > `SYNCED`
   - 若存在任意 DIVERGED 分支 → `DIVERGED`
   - 若存在任意 AHEAD 分支 → `AHEAD`

2. **扩展 DiffDetails 数据结构**
   - 添加 `aheadBranchCount` 字段
   - 添加 `divergedBranchCount` 字段
   - 更新 `BranchComparisonSummary` 包含新统计

3. **改造 buildBranchSummary() 方法**
   - 统计 AHEAD 状态的分支数量
   - 统计 DIVERGED 状态的分支数量
   - 更新 summary 对象

**关键点**:
- 保持现有 API 签名不变，确保兼容性
- 状态优先级严格按照设计文档
- 添加详细日志记录状态判断依据
- 处理 branchComparisons 为空的情况

**验收标准**:
- determineSyncStatus 正确识别 7 种状态
- 分支统计包含 ahead 和 diverged 计数
- 状态优先级逻辑正确
- 现有测试用例仍然通过
- 新增测试用例覆盖 AHEAD 和 DIVERGED 场景
- 编写并通过单元测试验证项目级状态判断

**提交**: `refactor(diff): enhance project-level status determination`

---

### T1.4 更新项目概览 API 展示新差异状态
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T1.3

**任务目标**:
更新现有的 ProjectOverviewDTO 和相关 API，展示新增的差异状态（AHEAD/DIVERGED）和分支统计。

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/controller/dto/ProjectOverviewDTO.java`
- `server/src/main/java/com/gitlab/mirror/server/service/ProjectListService.java`
- `server/src/main/java/com/gitlab/mirror/server/controller/SyncController.java`

**核心改造**:

1. **ProjectOverviewDTO 扩展**
   - 添加 `aheadBranchCount` 字段（领先分支数）
   - 添加 `divergedBranchCount` 字段（分裂分支数）
   - 确保 `diffStatus` 字段支持新的枚举值
   - 添加 `branchComparisons` 字段（可选，用于详情展示）

2. **ProjectListService 改造**
   - 在 `buildProjectOverview()` 方法中调用 `DiffCalculator.calculateDiff(id, true)`
   - 从 ProjectDiff 的 branchSummary 提取统计数据
   - 将新增字段映射到 DTO

3. **SyncController 无需改造**
   - 现有端点 `GET /api/sync/projects/{id}/overview` 自动支持新字段
   - 确保返回的 JSON 包含新增字段

**关键点**:
- 保持现有 API 响应格式兼容，只是新增字段
- DiffCalculator 调用时启用 `includeDetailedBranches = true`
- 处理 ProjectDiff 为 null 的边界情况
- 添加日志记录差异状态

**验收标准**:
- `GET /api/sync/projects/{id}/overview` 返回包含新字段的数据
- diffStatus 正确显示 AHEAD/DIVERGED 状态
- 分支统计数据准确（包含 ahead 和 diverged 计数）
- 现有 API 测试仍然通过
- 新增测试验证新字段的返回
- 编写并通过集成测试验证 API 数据

**提交**: `feat(api): add AHEAD and DIVERGED status to project overview`

---

### T1.5 前端展示差异状态
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T1.4

**任务目标**:
在前端项目列表和详情页面展示差异状态，使用颜色和图标区分不同状态。

**文件路径**:
- `web-ui/src/components/project-detail/DiffStatusBadge.vue`
- `web-ui/src/components/project-detail/OverviewTab.vue`
- `web-ui/src/views/Projects.vue`

**核心功能**:

1. **DiffStatusBadge 组件**
   - 接收 `diffStatus` 属性
   - 根据状态显示不同颜色和图标
   - synced → 🟢 绿色
   - outdated → 🟡 黄色
   - ahead → 🟠 橙色
   - diverged → 🔴 红色
   - unknown → ⚪ 灰色

2. **OverviewTab 增强**
   - 显示分支统计信息（同步/落后/领先/分裂分支数）
   - 显示默认分支差异状态
   - 显示目标延迟时间（delaySeconds）
   - 添加"查看分支详情"链接

3. **Projects 列表页增强**
   - 在项目卡片中显示差异状态徽章
   - 添加差异状态过滤器
   - diverged 状态显示警告提示

**关键点**:
- 使用 Element Plus 的 Badge/Tag 组件
- 差异状态使用统一的颜色规范
- 分裂状态显示特殊警告图标和提示文字
- 响应式设计，移动端适配

**验收标准**:
- 项目列表正确显示差异状态徽章
- 项目详情页展示完整差异信息
- 分裂状态有明显的视觉警告
- 差异状态过滤器功能正常
- UI 美观且符合设计规范
- 编写并通过 E2E 测试验证差异状态展示

**提交**: `feat(ui): display diff status in project list and overview`

---

## 模块验收

**验收检查项**:
1. 差异状态枚举和数据模型完整且文档清晰
2. 分支比对逻辑正确识别 6 种分支状态
3. 项目差异计算准确，优先级逻辑正确
4. API 返回的差异数据准确且性能可接受（<500ms）
5. 前端正确展示差异状态，UI 美观易用
6. 所有单元测试和集成测试通过

**完成标志**: 所有任务状态为 ✅，模块状态更新为 ✅ 已完成

---
