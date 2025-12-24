# Web UI 模块 5: Configuration (全局配置)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 实现全局配置页面，管理 GitLab 实例连接、定时扫描、同步设置和阈值。

**预计时间**: 2-3天

**相关文档**:
- [Web UI 需求清单](../web-ui-requirements.md#25-configuration-全局配置)

---

## ⚠️ 重要提醒：任务状态管理规范

**【必须】在开始处理下面的每个子任务前及后需要修改其任务状态：**

1. **开始任务前**：将任务状态从 `⏸️ 待处理 (Pending)` 修改为 `🔄 进行中 (In Progress)`
2. **完成任务后**：将任务状态修改为 `✅ 已完成 (Completed)` 或 `❌ 失败 (Failed)`
3. **更新位置**：在本文档对应任务的 `**状态**:` 行进行修改

---

## 任务清单

### T5.1 后端 API - 配置管理
**状态**: ✅ 已完成 (Completed)
**依赖**: 无

**任务目标**:
- 创建 `ConfigController`
- 实现获取所有配置 API
- 实现更新配置 API
- 实现测试连接 API

**API 端点**:
```java
GET /api/config/all
响应: {
  "success": true,
  "data": {
    "gitlab": {
      "source": {
        "url": "http://localhost:8000",
        "token": "glpat-****"
      },
      "target": {
        "url": "http://localhost:9000",
        "token": "glpat-****"
      }
    },
    "scanSettings": {
      "incrementalInterval": 300000,
      "fullScanCron": "0 0 2 * * ?",
      "enabled": true
    },
    "syncSettings": {
      "syncInterval": 300,
      "concurrency": 5
    },
    "defaultSyncRules": {
      "method": "pull_sync",
      "excludeArchived": true,
      "excludeEmpty": true,
      "excludePattern": "^temp/.*"
    },
    "thresholds": {
      "delayWarningHours": 1,
      "delayCriticalHours": 24,
      "maxRetryAttempts": 3,
      "timeoutSeconds": 300
    }
  }
}

POST /api/config/all
请求体: 同上格式
响应: { "success": true, "message": "配置已保存" }

POST /api/config/test-connection?type=source|target
响应: {
  "success": true,
  "data": {
    "connected": true,
    "version": "16.11.0",
    "latencyMs": 50
  }
}

```

**配置存储**:
- 使用 Spring Boot 配置管理
- 支持环境变量覆盖
- 重要配置加密存储（Token）

**验收标准**:
- 所有 API 正确返回
- 配置正确保存和读取
- 连接测试准确
- Token 遮蔽显示

**测试要求**:
- 单元测试所有 API
- 测试配置更新
- 测试连接测试

**提交**: `feat(web-ui): add configuration management API`

---

### T5.2 前端页面 - 配置页面布局
**状态**: ⏸️ 待处理
**依赖**: T5.1

**任务目标**:
- 创建 Configuration 页面
- 实现配置表单布局
- 实现分组显示
- 实现操作按钮区

**组件结构**:
```
views/Configuration.vue
├─ components/
│  ├─ GitLabConfig.vue       # GitLab 实例配置
│  ├─ ScanConfig.vue         # 定时扫描配置
│  ├─ SyncSettings.vue       # 同步设置
│  ├─ SyncRules.vue          # 同步规则
│  ├─ Thresholds.vue         # 阈值配置
│  └─ ConnectionTest.vue     # 连接测试组件
```

**验收标准**:
- 页面布局合理
- 配置分组清晰
- 表单显示正确
- 操作按钮位置正确

**测试要求**:
- 组件测试
- 布局测试

**提交**: `feat(web-ui): add configuration page layout`

---

### T5.3 前端功能 - GitLab 实例配置
**状态**: ⏸️ 待处理
**依赖**: T5.2

**任务目标**:
- 实现 Source GitLab 配置表单
- 实现 Target GitLab 配置表单
- 实现连接测试功能
- 实现 Token 遮蔽显示

**功能需求**:
- URL 输入框
- Token 输入框（遮蔽显示）
- 连接测试按钮
- 测试结果显示（版本、延时）

**验收标准**:
- 表单正确显示
- Token 遮蔽生效
- 连接测试正常
- 测试结果准确

**测试要求**:
- 表单验证测试
- 连接测试测试

**提交**: `feat(web-ui): implement GitLab instance config`

---

### T5.4 前端功能 - 定时扫描配置
**状态**: ⏸️ 待处理
**依赖**: T5.2

**任务目标**:
- 实现定时扫描配置表单
- 实现 Cron 表达式输入
- 实现 Cron 可视化配置（可选）
- 实现开关控制

**功能需求**:
- 增量扫描间隔（毫秒）
- 全量扫描时间（Cron 表达式）
- 启用定时扫描开关
- Cron 表达式验证

**验收标准**:
- 间隔时间验证正确
- Cron 表达式验证正确
- 开关控制生效
- 可视化配置正常（如果实现）

**测试要求**:
- 间隔验证测试
- Cron 验证测试
- 表单测试

**提交**: `feat(web-ui): implement scheduled scan config`

---

### T5.5 前端功能 - 同步设置和规则
**状态**: ⏸️ 待处理
**依赖**: T5.2

**任务目标**:
- 实现同步设置表单
- 实现默认规则表单
- 实现输入验证

**功能需求**:
- 同步间隔（秒）
- 并发数量
- 默认同步方式
- 排除规则（正则）

**验收标准**:
- 输入验证正确
- 正则表达式验证
- 默认值正确

**测试要求**:
- 输入验证测试
- 正则验证测试

**提交**: `feat(web-ui): implement sync settings and rules`

---

### T5.6 前端功能 - 阈值配置
**状态**: ⏸️ 待处理
**依赖**: T5.2

**任务目标**:
- 实现阈值配置表单
- 实现输入验证
- 实现单位提示

**功能需求**:
- 延时告警阈值（警告/严重）
- 失败重试次数
- 超时时间
- 单位显示（小时/秒）

**验收标准**:
- 输入验证正确
- 单位显示清晰
- 默认值合理

**测试要求**:
- 输入验证测试
- 表单测试

**提交**: `feat(web-ui): implement thresholds config`

---

### T5.7 前端功能 - 配置保存
**状态**: ⏸️ 待处理
**依赖**: T5.3, T5.4, T5.5, T5.6

**任务目标**:
- 实现保存配置功能
- 实现表单验证
- 实现操作反馈

**功能需求**:
- 保存按钮（表单验证）
- 保存成功/失败提示
- 表单脏检测
- Loading 状态

**验收标准**:
- 保存功能正常
- 验证生效
- 提示信息准确
- Loading 状态正确

**测试要求**:
- 保存测试
- 验证测试
- 错误处理测试

**提交**: `feat(web-ui): add config save functionality`

---

## 模块输出

- ✅ ConfigController (后端)
- ✅ Configuration 页面和子组件 (前端)
- ✅ GitLab 实例配置
- ✅ 定时扫描配置
- ✅ 同步设置和规则
- ✅ 阈值配置
- ✅ 保存功能

---

## 关键决策

1. **配置存储**: 使用 Spring Boot 配置，支持环境变量
2. **Token 安全**: 加密存储，前端遮蔽显示
3. **连接测试**: 调用 GitLab API `/api/v4/version`
4. **Cron 表达式**: 使用第三方库验证和可视化（可选）

---

## 配置默认值

**GitLab Instances**:
- Source URL: `${SOURCE_GITLAB_URL}`
- Target URL: `${TARGET_GITLAB_URL}`

**定时扫描 (Scheduled Scan)**:
- 增量扫描间隔: `300000` 毫秒 (5分钟)
- 全量扫描时间: `0 0 2 * * ?` (每天凌晨2点)
- 启用定时扫描: `true`

**Sync Settings**:
- 同步间隔: `300` 秒
- 并发数: `5`

**Default Sync Rules**:
- 同步方式: `pull_sync`
- 排除归档: `true`
- 排除空仓库: `true`
- 排除规则: `^temp/.*`

**Thresholds**:
- 延时警告: `1` 小时
- 延时严重: `24` 小时
- 重试次数: `3`
- 超时时间: `300` 秒

---

## 注意事项

1. **Token 安全**: 前端遮蔽显示，后端加密存储
2. **配置验证**: 保存前验证所有输入
3. **连接测试**: 测试前验证 URL 和 Token
4. **Cron 验证**: 使用库验证 Cron 表达式
5. **重启提示**: 部分配置修改需要重启服务（提示用户）
6. **环境变量**: 优先使用环境变量配置，Web界面配置为覆盖
