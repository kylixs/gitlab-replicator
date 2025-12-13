# 模块 6: CLI 客户端 (CLI Client)

**目标**: 实现命令行工具，通过 REST API 与服务端交互，复用服务端的模型和工具类。

**预计时间**: Week 3 (2-3天)

**架构**: Shell 脚本 + Java Client JAR（依赖 common 模块）

**CLI 设计参考**: [PUSH_MIRROR_MVP_DESIGN.md - CLI 命令设计](../PUSH_MIRROR_MVP_DESIGN.md#-cli-命令设计)

---

## 任务清单

### T6.1 实现 CLI 基础框架
**依赖**: T5.2 (REST API)

**任务目标**:
- **Java Client 模块**:
  - 创建 cli-client 模块（依赖 common 模块）
  - 复用服务端模型类（实体、DTO）
  - 复用工具类（日期处理、JSON 处理等）
  - 实现 HTTP 客户端封装（调用 REST API）
  - 添加 Token 认证
  - 实现错误处理和友好提示
  - 实现超时处理

- **Shell 脚本框架**:
  - 创建主 Shell 脚本（gitlab-mirror）
  - 实现命令分发逻辑
  - 实现服务管理命令（start/stop/status/reload）
  - 实现 JAR 查找和加载逻辑
  - 实现进程管理（daemon 模式）

- **CLI 通用功能**:
  - 实现 ANSI 颜色美化输出
  - 实现表格格式化工具
  - 实现进度条显示
  - 实现配置文件管理（CLI 配置）

**验收标准**:
- HTTP 请求正常
- Token 认证生效
- 错误提示友好
- Shell 脚本正常执行
- 命令分发正确
- 服务启停正常
- 输出格式美观

**测试要求**:
- 测试 API 调用
- 测试认证
- 测试错误处理
- 测试服务启停
- 测试输出格式

**提交**: `feat(cli): add CLI client framework with shell script`

---

### T6.2 实现 CLI 命令
**依赖**: T6.1 (CLI 基础框架)

**任务目标**:

**服务管理命令**:
- `gitlab-mirror start` - 启动服务
- `gitlab-mirror stop` - 停止服务
- `gitlab-mirror restart` - 重启服务
- `gitlab-mirror status` - 查看服务状态
- `gitlab-mirror reload` - 重新加载配置

**项目管理命令**:
- `gitlab-mirror projects` - 项目列表
  - `--status active` - 按状态过滤
  - `--page 1 --size 20` - 分页
- `gitlab-mirror discover` - 手动触发项目发现

**Mirror 管理命令**:
- `gitlab-mirror mirrors` - Mirror 列表
  - `--status synced` - 按状态过滤
- `gitlab-mirror mirror <project>` - Mirror 详情
- `gitlab-mirror mirror <project> --setup` - 配置 Mirror
- `gitlab-mirror mirror <project> --check` - 一致性检查

**事件查询命令**:
- `gitlab-mirror events` - 事件列表
  - `--project <key>` - 按项目过滤
  - `--type mirror_sync` - 按类型过滤

**数据导出命令**:
- `gitlab-mirror export mirrors` - 导出 Mirror 状态
- `gitlab-mirror export events` - 导出事件数据
  - `--format json|csv` - 输出格式
  - `--output <file>` - 输出文件

**验收标准**:
- 所有命令功能正常
- 选项解析正确
- 输出格式清晰美观
- 错误提示友好
- 表格格式化工作正常
- 颜色使用合理

**测试要求**:
- 测试所有命令
- 测试选项解析
- 测试输出格式
- 测试错误场景
- 测试服务管理

**提交**: `feat(cli): add all CLI commands with formatted output`

---

## CLI 命令清单

### 服务管理
```bash
gitlab-mirror start         # 启动服务（daemon 模式）
gitlab-mirror stop          # 停止服务
gitlab-mirror restart       # 重启服务
gitlab-mirror status        # 查看服务状态
gitlab-mirror reload        # 重新加载配置
```

### 项目管理
```bash
# 查看项目列表
gitlab-mirror projects [OPTIONS]
  --status <status>         # 按状态过滤：active, failed, pending
  --page <n>                # 页码
  --size <n>                # 每页数量

# 手动触发项目发现
gitlab-mirror discover
```

### Mirror 管理
```bash
# 查看 Mirror 列表
gitlab-mirror mirrors [OPTIONS]
  --status <status>         # 按状态过滤：synced, failed, syncing

# 查看 Mirror 详情
gitlab-mirror mirror <project>

# 配置 Mirror
gitlab-mirror mirror <project> --setup

# 一致性检查
gitlab-mirror mirror <project> --check
```

### 事件查询
```bash
# 查看事件列表
gitlab-mirror events [OPTIONS]
  --project <key>           # 按项目过滤
  --type <type>             # 按事件类型过滤
  --start <date>            # 开始时间
  --end <date>              # 结束时间
  --page <n>                # 页码
```

### 数据导出
```bash
# 导出 Mirror 状态
gitlab-mirror export mirrors [OPTIONS]
  --format json|csv         # 输出格式
  --output <file>           # 输出文件（默认标准输出）

# 导出事件数据
gitlab-mirror export events [OPTIONS]
  --format json|csv
  --output <file>
```

---

## 输出示例

### 项目列表
```
┌─────────────────────┬──────────┬──────────┬─────────────────────┐
│ Project Key         │ Status   │ Method   │ Last Sync           │
├─────────────────────┼──────────┼──────────┼─────────────────────┤
│ group1/project1     │ active   │ mirror   │ 2025-01-15 10:30:00 │
│ group1/project2     │ syncing  │ mirror   │ 2025-01-15 10:28:15 │
│ group2/project3     │ failed   │ mirror   │ 2025-01-15 09:15:22 │
└─────────────────────┴──────────┴──────────┴─────────────────────┘

Total: 3 projects
```

### Mirror 详情
```
Project: group1/project1
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Status:              ✓ Synced
Last Update:         2025-01-15 10:30:00
Last Success:        2025-01-15 10:30:00
Consecutive Fails:   0
Mirror URL:          https://target-gitlab.com/***
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 模块输出

- ✅ CLI Java 客户端模块（复用服务端代码）
- ✅ Shell 脚本框架
- ✅ 服务管理命令
- ✅ 项目管理命令
- ✅ Mirror 管理命令
- ✅ 事件查询命令
- ✅ 数据导出命令
- ✅ 美化输出（表格、颜色）

---

## 关键决策

1. **架构设计**: Shell + Java JAR，便于复用服务端代码
2. **代码复用**: cli-client 模块依赖 common 模块，复用实体和工具类
3. **输出格式**: 使用表格格式，ANSI 颜色美化
4. **配置管理**: CLI 独立配置文件（API 地址、Token 等）
5. **错误处理**: 友好的错误提示，包含修复建议
