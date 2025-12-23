# GitLab Mirror Web UI 需求清单

## 1. 概述

### 1.1 目标
为 GitLab Mirror 系统开发 Web 管理界面，用于监控同步状态、管理项目配置、查看同步历史。

### 1.2 技术栈建议
- **前端**: Vue 3 + TypeScript + Element Plus/Ant Design Vue
- **状态管理**: Pinia
- **图表**: ECharts
- **HTTP**: Axios
- **构建**: Vite

---

## 2. 页面需求

### 2.1 Dashboard (仪表盘)

**功能**: 系统整体运行状况概览

**数据展示**:
- 统计卡片: 总项目数、已同步数、同步中数、暂停数、失败数
- 同步状态分布图: 饼图/柱状图
- 同步延时 Top 10: 延时最严重的项目列表
- 最近活动时间线: 最近 20 条同步事件

**操作**:
- 触发全量扫描
- 触发增量扫描

---

### 2.2 Projects (项目列表)

**功能**: 管理所有镜像项目

**表格列**:
- 项目路径
- 同步状态 (6种: Synced/Syncing/Outdated/Paused/Failed/Pending)
- 差异 (分支: +3/-1/~2, Commit: +125)
- 同步延时 (智能时间显示 + 颜色编码)
- 同步方式 (Push Mirror/Pull Sync)
- 最后同步时间
- 更新时间
- 操作按钮

**筛选器**:
- 按 Group
- 按同步状态
- 按同步方式
- 按延时范围
- 搜索框

**排序**:
- 延时
- 更新时间
- 项目名
- 最后同步时间

**操作**:
- 批量: 同步/暂停/恢复/删除
- 单个: 查看详情/同步/暂停/恢复/删除
- 新增项目
- 导出列表

---

### 2.3 Project Detail (项目详情)

**Tab 1: Overview (概览)**
- 同步状态卡片
- Source/Target 项目信息 (两栏布局)
- 差异统计卡片 (分支/Commit/仓库大小/延时)
- 同步配置

**Tab 2: Branches (分支对比)**
- 统计卡片: 已同步/待同步/目标缺失/目标多余
- 分支对比表格
- 分支详情展开行
- 筛选器: 按状态、搜索分支名、仅保护分支

**Tab 3: Sync Events (同步事件)**
- 事件时间线表格
- 点击展开查看详情: JSON 格式，根据事件类型包含不同数据
- 筛选器: 事件类型、状态、日期范围
- 操作: 查看详情、查看日志、重试

**顶部操作栏**:
- 返回列表
- 立即同步
- 暂停/恢复
- 删除配置

---

### 2.4 Sync Events (同步事件)

**功能**: 查看所有项目的同步事件历史

**统计卡片**:
- 今日事件总数
- 今日成功数
- 今日失败数
- 今日平均耗时

**事件列表表格**:
- 时间
- 源/目标项目
- 事件类型
- 状态
- 详情
- 耗时
- 操作: 查看详情、查看日志、重试

**事件详情**:
- 根据事件类型显示不同的 JSON 数据
- 包含分支列表（最多10个）、统计信息、错误信息、Webhook上下文等

**筛选器**:
- 按项目
- 按事件类型
- 按状态
- 按日期范围
- 搜索

**操作**:
- 导出事件列表

---

### 2.5 Configuration (全局配置)

**功能**: 管理系统级配置

**配置模块**:

**GitLab Instances**:
- Source GitLab: URL, Token, 连接测试
- Target GitLab: URL, Token, 连接测试

**定时扫描配置 (Scheduled Scan)**:
- 增量扫描间隔 (毫秒，默认 300000 = 5分钟)
- 全量扫描时间 (Cron 表达式，默认每天凌晨2点)
- 启用定时扫描开关

**Sync Settings (同步设置)**:
- 同步间隔 (秒，默认 300)
- 并发同步数量 (默认 5)

**Default Sync Rules (默认规则)**:
- 默认同步方式 (Push Mirror/Pull Sync)
- 排除规则 (归档项目、空仓库、自定义正则)

**Thresholds (阈值)**:
- 延时告警阈值 - 警告 (小时，默认 1)
- 延时告警阈值 - 严重 (小时，默认 24)
- 失败重试次数 (默认 3)
- 超时时间 (秒，默认 300)

**操作**:
- 测试连接
- 保存配置

---

## 3. 数据库表扩展需求

### 3.1 sync_project 表新增字段

```sql
ALTER TABLE sync_project ADD COLUMN last_sync_at TIMESTAMP NULL COMMENT '最后同步成功时间';
```

> 注: 项目级别的配置（同步频率、触发方式、保护分支等）暂不需要，使用全局配置即可。

### 3.2 sync_event 表

```sql
CREATE TABLE IF NOT EXISTS sync_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sync_project_id BIGINT NOT NULL,
  event_type VARCHAR(50) NOT NULL COMMENT '事件类型',
  status VARCHAR(20) NOT NULL COMMENT '状态: success/failure/running',
  message TEXT COMMENT '事件消息',
  details TEXT COMMENT '详细日志',
  duration_ms BIGINT COMMENT '耗时(毫秒)',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_project_id (sync_project_id),
  INDEX idx_event_type (event_type),
  INDEX idx_status (status),
  INDEX idx_created_at (created_at)
);
```

### 3.3 sync_event 表字段说明

**event_type 字段**: 支持以下事件类型
- `incremental_sync`: 增量同步
- `full_sync`: 全量同步
- `manual_sync`: 手动同步
- `webhook_sync`: Webhook触发的同步
- `status_change`: 状态变更
- `discovery`: 项目发现

**details 字段 (TEXT)**:
存储 JSON 格式的事件详情，包含：
- 同步成功: trigger, branches (最多10个), summary, webhook (可选)
- 同步失败: trigger, error (类型、消息、详情)
- 状态变更: changes (from, to, reason, operator)
- 项目发现: trigger, discovered (newProjects, projects)

---

## 4. 后端 API 需求

### 4.1 Dashboard API
- `GET /api/dashboard/stats` - 统计数据
- `GET /api/dashboard/status-distribution` - 状态分布
- `GET /api/dashboard/top-delayed-projects` - 延时 Top10
- `GET /api/dashboard/recent-events` - 最近活动

### 4.2 Projects API
- `GET /api/sync/projects` - 项目列表 (支持筛选、搜索、排序、分页)
- `GET /api/sync/projects/groups` - 获取所有 Group
- `GET /api/sync/projects/{id}` - 项目详情
- `GET /api/sync/projects/{id}/overview` - 项目概览
- `POST /api/sync/projects/{id}/trigger-sync` - 触发同步
- `POST /api/sync/projects/{id}/pause` - 暂停
- `POST /api/sync/projects/{id}/resume` - 恢复
- `POST /api/sync/projects/{id}/delete` - 删除
- `POST /api/sync/projects/batch-sync` - 批量同步
- `POST /api/sync/projects/batch-pause` - 批量暂停
- `POST /api/sync/projects/batch-resume` - 批量恢复
- `POST /api/sync/projects/batch-delete` - 批量删除

### 4.3 Branches API
- `GET /api/sync/projects/{id}/branches` - 分支对比

### 4.4 Sync Events API
- `GET /api/sync/events/stats` - 事件统计
- `GET /api/sync/events` - 事件列表 (支持筛选、搜索、分页)
- `GET /api/sync/events/{id}/details` - 事件详情 (包含分支列表)
- `GET /api/sync/events/{id}/logs` - 查看日志

### 4.5 Configuration API
- `GET /api/config/all` - 获取所有配置
- `POST /api/config/all` - 更新配置
- `POST /api/config/test-connection` - 测试 GitLab 连接


### 4.7 Scan API
- `POST /api/sync/scan?type=incremental|full` - 触发扫描

---

## 5. UI/UX 规范

**状态颜色**: 成功(绿)、同步中(蓝)、警告(黄)、暂停(灰)、失败(红)

**延时颜色**: < 1小时(绿)、1-24小时(黄)、> 24小时(红)、实时(蓝)

**交互规范**:
- 按钮操作有 Loading 状态
- 危险操作二次确认
- 操作结果 Toast 提示

---

## 6. 实施阶段

**Phase 1: 核心功能** (优先级高)
- Dashboard
- Projects 列表
- Project Detail (Overview/Branches)
- 基础 API

**Phase 2: 事件管理** (优先级中)
- Sync Events
- Project Detail (Sync Events Tab)
- 事件 API

**Phase 3: 配置管理** (优先级中)
- Configuration
- 配置 API


---

## 7. 关键功能说明

### 7.1 同步状态 (6 种)
- **Synced** (已同步): 源目标完全一致，无差异
- **Syncing** (同步中): 正在执行同步任务
- **Outdated** (有差异): 存在分支或Commit差异，待同步
- **Paused** (暂停): 用户手动暂停同步
- **Failed** (失败): 最近一次同步失败
- **Pending** (待处理): 新发现的项目，未配置同步

### 7.2 差异计算

**分支差异** (格式: `+X/-Y/~Z`):
- `+X`: 源GitLab有X个新分支（目标没有）
- `-Y`: 源GitLab删除Y个分支（目标仍有）
- `~Z`: Z个分支存在Commit差异（分支存在但commit不一致）

**Commit差异** (格式: `+N commits`):
- 统计源GitLab领先目标GitLab的commit总数
- 仅计算源领先的commit，不计算目标领先的

**同步延时** (格式: 智能时间显示):
- **计算公式**: `source.last_activity_at - target.last_activity_at`
- **智能显示**:
  - < 1分钟: "刚刚"
  - < 1小时: "X分钟"
  - < 24小时: "X小时"
  - >= 24小时: "X天"
- **颜色编码**:
  - < 1小时: 绿色（正常）
  - 1-24小时: 黄色（警告）
  - > 24小时: 红色（严重）
  - 实时同步: 蓝色（特殊）

### 7.3 同步事件类型
- **incremental_sync** (增量同步): 定时扫描触发的增量同步
- **full_sync** (全量同步): 定时扫描触发的全量同步
- **manual_sync** (手动同步): 用户在Web界面点击"立即同步"触发
- **webhook_sync** (Webhook同步): 收到GitLab Webhook推送触发的同步
- **status_change** (状态变更): 项目状态变更事件（暂停/恢复/删除等）
- **discovery** (项目发现): 新项目发现事件

---

**文档版本**: v2.2
**创建日期**: 2025-12-23
**最后更新**: 2025-12-23
**作者**: GitLab Mirror Team

**更新历史**:
- v2.2 (2025-12-23): 删除过于细节的设计内容，保留核心需求清单
- v2.1 (2025-12-23): 补充事件类型，增加 webhook_sync/manual_sync/discovery 等事件
- v2.0 (2025-12-23): 简化为需求清单，移除详细设计
- v1.0 (2025-12-23): 初始版本
