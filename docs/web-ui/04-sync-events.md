# Web UI 模块 4: Sync Events (同步事件)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 实现同步事件页面，查看所有项目的同步历史，支持筛选、搜索和导出。

**预计时间**: 2-3天

**相关文档**:
- [Web UI 需求清单](../web-ui-requirements.md#24-sync-events-同步事件)

---

## ⚠️ 重要提醒：任务状态管理规范

**【必须】在开始处理下面的每个子任务前及后需要修改其任务状态：**

1. **开始任务前**：将任务状态从 `⏸️ 待处理 (Pending)` 修改为 `🔄 进行中 (In Progress)`
2. **完成任务后**：将任务状态修改为 `✅ 已完成 (Completed)` 或 `❌ 失败 (Failed)`
3. **更新位置**：在本文档对应任务的 `**状态**:` 行进行修改

---

## 任务清单

### T4.1 后端 API - 事件统计
**状态**: 🔄 进行中 (In Progress)
**依赖**: 无

**任务目标**:
- 创建事件统计 API
- 实现今日事件统计
- 实现成功/失败统计
- 实现平均耗时统计

**API 端点**:
```java
GET /api/sync/events/stats?date=2025-12-23
响应: {
  "totalEvents": 1234,
  "successEvents": 1200,
  "failedEvents": 34,
  "avgDurationMs": 12500
}
```

**验收标准**:
- 统计数据准确
- 日期参数正确处理
- 响应时间 < 200ms

**测试要求**:
- 单元测试
- 日期边界测试

**提交**: `feat(web-ui): add sync events statistics API`

---

### T4.2 后端 API - 事件列表扩展
**状态**: ✅ 已完成 (Completed)
**依赖**: 无

**任务目标**:
- 扩展事件列表 API
- 实现项目筛选
- 实现事件类型筛选
- 实现状态筛选
- 实现日期范围筛选
- 实现搜索功能

**API 端点**:
```java
GET /api/sync/events?projectId=&eventType=&status=&startDate=&endDate=&search=&page=1&size=20
响应: {
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,
        "syncProjectId": 123,
        "projectKey": "ai/test-rails-5",
        "eventType": "incremental_sync",
        "status": "success",
        "message": "同步3分支, +25 commits",
        "durationMs": 15000,
        "createdAt": "2025-12-23T14:30:25"
      }
    ],
    "total": 1234,
    "page": 1,
    "size": 20
  }
}
```

**验收标准**:
- 筛选功能正确
- 搜索功能正确
- 分页正确
- 排序正确（按时间降序）

**测试要求**:
- 筛选测试
- 搜索测试
- 分页测试

**提交**: `feat(web-ui): extend sync events list API`

---

### T4.3 后端 API - 事件详情
**状态**: ✅ 已完成 (Completed)
**依赖**: 无

**任务目标**:
- 创建事件详情 API
- 返回 JSON 格式的详情数据
- 支持不同事件类型

**API 端点**:
```java
GET /api/sync/events/{id}/details
响应: {
  "success": true,
  "data": {
    "event": { 事件基本信息 },
    "details": {
      // 同步成功: branches数组 + summary
      // 同步失败: error对象
      // 状态变更: changes对象
    }
  }
}
```

**验收标准**:
- 详情数据完整
- JSON 格式正确
- 不同事件类型正确返回

**测试要求**:
- 测试不同事件类型
- JSON 格式验证

**提交**: `feat(web-ui): add sync event details API`

---

### T4.4 后端 API - 导出功能
**状态**: ⏸️ 待处理
**依赖**: T4.2

**任务目标**:
- 创建导出 API
- 实现 CSV 格式导出
- 支持筛选条件导出

**API 端点**:
```java
GET /api/sync/events/export?format=csv&filters=...
响应: CSV 文件下载
```

**验收标准**:
- CSV 格式正确
- 包含所有列
- 筛选条件生效

**测试要求**:
- CSV 格式验证
- 筛选测试

**提交**: `feat(web-ui): add events export API`

---

### T4.5 前端页面 - 事件页面布局
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.1, T4.2

**任务目标**:
- 创建 SyncEvents 页面
- 实现统计卡片
- 实现筛选器
- 实现事件表格

**组件结构**:
```
views/SyncEvents.vue
├─ components/
│  ├─ EventStats.vue         # 事件统计卡片
│  ├─ EventFilter.vue        # 筛选器
│  ├─ EventTable.vue         # 事件表格
│  ├─ EventDetailDrawer.vue  # 详情抽屉
│  └─ EventRow.vue           # 表格行
```

**验收标准**:
- 页面布局合理
- 统计卡片显示正确
- 筛选器位置正确
- 表格显示正确

**测试要求**:
- 组件测试
- 布局测试

**提交**: `feat(web-ui): add sync events page layout`

---

### T4.6 前端功能 - 统计卡片
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.5

**任务目标**:
- 实现今日统计卡片
- 实现数据获取
- 实现自动刷新

**功能需求**:
- 4个统计卡片（总数、成功、失败、平均耗时）
- 颜色编码
- 每30秒自动刷新

**验收标准**:
- 统计数据正确显示
- 颜色标识准确
- 自动刷新生效

**测试要求**:
- 数据获取测试
- 刷新测试

**提交**: `feat(web-ui): implement event statistics cards`

---

### T4.7 前端功能 - 事件表格
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.5

**任务目标**:
- 实现事件表格数据绑定
- 实现事件类型图标
- 实现状态徽章
- 实现分页

**表格列**:
- 时间
- 源/目标项目
- 事件类型（图标）
- 状态（徽章）
- 详情
- 耗时
- 操作按钮

**验收标准**:
- 表格正确显示
- 图标和徽章正确
- 分页功能正常

**测试要求**:
- 数据渲染测试
- 分页测试

**提交**: `feat(web-ui): implement events table`

---

### T4.8 前端功能 - 事件详情
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.3, T4.7

**任务目标**:
- 实现事件详情展开
- 实现 JSON 格式化显示
- 实现分支列表显示
- 实现错误信息显示

**功能需求**:
- 点击"查看详情"展开
- JSON 美化显示
- 分支列表表格
- 错误信息高亮

**验收标准**:
- 展开功能正常
- JSON 格式美观
- 不同事件类型正确显示

**测试要求**:
- 展开测试
- 格式化测试

**提交**: `feat(web-ui): add event details display`

---

### T4.9 前端功能 - 筛选和搜索
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.5

**任务目标**:
- 实现项目筛选
- 实现事件类型筛选
- 实现状态筛选
- 实现日期范围筛选
- 实现搜索框

**功能需求**:
- 项目多选下拉框
- 事件类型多选
- 状态多选
- 日期范围选择器
- 搜索框（项目名/事件详情）

**验收标准**:
- 筛选器正确工作
- 筛选条件联合生效
- 搜索功能正确

**测试要求**:
- 筛选测试
- 搜索测试

**提交**: `feat(web-ui): add events filters and search`

---

### T4.10 前端功能 - 导出
**状态**: ✅ 已完成 (Completed)
**依赖**: T4.4, T4.7

**任务目标**:
- 实现导出按钮
- 实现 CSV 下载
- 实现当前筛选结果导出

**功能需求**:
- 导出按钮
- 下载 CSV 文件
- 包含当前筛选的数据

**验收标准**:
- 导出功能正确
- CSV 格式正确
- 筛选结果正确导出

**测试要求**:
- 导出功能测试
- CSV 格式验证

**提交**: `feat(web-ui): add events export`

---

## 模块输出

- ✅ 事件统计 API
- ✅ 事件列表 API 扩展
- ✅ 事件详情 API
- ✅ 导出 API
- ✅ SyncEvents 页面和子组件
- ✅ 统计卡片
- ✅ 事件表格
- ✅ 事件详情展示
- ✅ 筛选和搜索
- ✅ 导出功能

---

## 关键决策

1. **事件详情**: 使用 JSON 格式存储，前端美化显示
2. **筛选器**: 支持多维度筛选（项目、类型、状态、日期）
3. **导出**: CSV 格式，包含所有可见列
4. **刷新**: 统计数据每30秒刷新，事件列表手动刷新

---

## 事件类型说明

支持以下事件类型:
- **incremental_sync**: 增量同步 (定时扫描触发)
- **full_sync**: 全量同步 (定时扫描触发)
- **manual_sync**: 手动同步 (Web界面点击触发)
- **webhook_sync**: Webhook同步 (GitLab Webhook推送触发)
- **status_change**: 状态变更 (暂停/恢复/删除等)
- **discovery**: 项目发现 (新项目发现)

---

## JSON 格式规范

**增量同步成功事件** (incremental_sync):
```json
{
  "trigger": "scheduled",
  "branches": [
    {
      "branchName": "master",
      "commitSha": "a1b2c3d4...",
      "commitMessage": "feat: add feature",
      "committedAt": "2025-12-23T14:25:00"
    }
  ],
  "summary": {
    "branchCount": 3,
    "commitCount": 25
  }
}
```

**手动同步成功事件** (manual_sync):
```json
{
  "trigger": "manual",
  "triggerBy": "admin",
  "branches": [...],
  "summary": {
    "branchCount": 5,
    "commitCount": 120
  }
}
```

**Webhook触发同步事件** (webhook_sync):
```json
{
  "trigger": "webhook",
  "webhook": {
    "event": "push",
    "ref": "refs/heads/main",
    "commits": [
      {
        "id": "a1b2c3d4...",
        "message": "Update README",
        "timestamp": "2025-12-23T14:25:00"
      }
    ],
    "pusher": {
      "name": "John Doe",
      "email": "john@example.com"
    }
  },
  "branches": [...],
  "summary": {
    "branchCount": 1,
    "commitCount": 1
  }
}
```

**同步失败事件**:
```json
{
  "trigger": "scheduled",
  "error": {
    "errorType": "GitCommandError",
    "errorMessage": "fatal: unable to access repository",
    "errorDetails": "Connection timed out after 300s"
  }
}
```

**状态变更事件** (status_change):
```json
{
  "changes": {
    "from": "active",
    "to": "paused",
    "reason": "Manual pause by user",
    "operator": "admin"
  }
}
```

**项目发现事件** (discovery):
```json
{
  "trigger": "scheduled",
  "discovered": {
    "newProjects": 5,
    "projects": [
      "group1/project1",
      "group1/project2"
    ]
  }
}
```

---

## 注意事项

1. **性能**: 事件数据量可能很大，注意分页和索引
2. **JSON 显示**: 使用第三方库美化 JSON
3. **日期范围**: 默认显示最近7天
4. **导出限制**: 最多导出 10000 条记录
5. **权限**: 导出功能可能需要权限控制（后续）
