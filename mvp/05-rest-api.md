# 模块 5: REST API 层 (REST API Layer)

**目标**: 提供 RESTful API，支持项目管理、Mirror 管理、事件查询和统计。

**预计时间**: Week 3 (2-3天)

**API 设计参考**: [PUSH_MIRROR_MVP_DESIGN.md - REST API 设计](../PUSH_MIRROR_MVP_DESIGN.md#-rest-api-设计)

---

## 任务清单

### T5.1 实现 API 基础设施
**依赖**: T1.2 (配置管理)

**任务目标**:
- 使用 Spring Security 实现 Token 认证
- 解析 Authorization Header（Bearer Token）
- 验证 Token 有效性
- 实现认证失败响应（401/403）
- 支持多个 Token 管理
- 使用 @ControllerAdvice 实现统一异常处理
- 定义统一的响应格式（成功/失败）
- 配置 CORS 支持
- 配置 API 文档（OpenAPI/Swagger）

**验收标准**:
- Token 认证正常工作
- 无效 Token 被拒绝
- 异常处理统一
- 响应格式一致
- CORS 配置正确
- API 文档可访问

**测试要求**:
- 测试 Token 认证
- 测试各种异常场景
- 测试响应格式
- 测试 CORS

**提交**: `feat(api): add authentication and unified exception handling`

---

### T5.2 实现业务 API 端点
**依赖**: T5.1 (API 基础), T4.1-T4.4 (业务服务)

**任务目标**:

**服务状态 API**:
- `GET /api/status` - 服务状态
- `POST /api/reload` - 重新加载配置
- `GET /api/stats` - 整体统计

**项目管理 API**:
- `GET /api/projects` - 项目列表（支持过滤和分页）
- `GET /api/projects/{key}` - 项目详情
- `POST /api/projects/discover` - 手动触发发现
- `POST /api/projects/{key}/setup-target` - 创建目标项目
- `GET /api/projects/{key}/target` - 获取目标项目信息

**Mirror 管理 API**:
- `POST /api/projects/{key}/setup-mirror` - 配置 Mirror
- `GET /api/mirrors` - Mirror 列表（支持过滤和分页）
- `GET /api/mirrors/{key}/consistency` - 一致性检查
- `POST /api/mirrors/poll` - 手动触发轮询

**事件查询 API**:
- `GET /api/events` - 事件列表（支持多维度过滤和分页）

**验收标准**:
- 所有端点正常响应
- 过滤和分页生效
- 手动操作有效
- 认证保护生效
- 响应格式统一

**测试要求**:
- 测试所有端点
- 测试过滤条件
- 测试分页
- 测试认证保护
- 测试错误响应

**提交**: `feat(api): add all business API endpoints`

---

## API 端点清单

### 1. 服务状态 API
```
GET  /api/status          # 获取服务状态
POST /api/reload          # 重新加载配置
GET  /api/stats           # 获取整体统计
```

### 2. 项目管理 API
```
GET  /api/projects                    # 项目列表
  ?status=active                      # 按状态过滤
  &sync_method=push_mirror            # 按同步方式过滤
  &page=1&size=20                     # 分页

GET  /api/projects/{key}              # 项目详情
POST /api/projects/discover           # 手动触发项目发现
POST /api/projects/{key}/setup-target # 创建目标项目
GET  /api/projects/{key}/target       # 获取目标项目信息
POST /api/targets/check               # 批量检查目标项目状态
```

### 3. Mirror 管理 API
```
POST /api/projects/{key}/setup-mirror # 配置 Mirror
GET  /api/mirrors                     # Mirror 列表
  ?status=synced                      # 按状态过滤
  &page=1&size=20                     # 分页

GET  /api/mirrors/{key}/consistency   # 一致性检查
POST /api/mirrors/poll                # 手动触发轮询
```

### 4. 事件查询 API
```
GET  /api/events                      # 事件列表
  ?project_key=xxx                    # 按项目过滤
  &event_type=mirror_sync             # 按事件类型过滤
  &start_time=2025-01-01              # 按时间范围过滤
  &end_time=2025-01-31
  &page=1&size=50                     # 分页
```

---

## 响应格式

### 成功响应
```json
{
  "success": true,
  "data": { ... },
  "message": "操作成功"
}
```

### 错误响应
```json
{
  "success": false,
  "error": {
    "code": "PROJECT_NOT_FOUND",
    "message": "项目不存在",
    "details": "项目 key 'xxx' 不存在"
  }
}
```

### 分页响应
```json
{
  "success": true,
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "totalPages": 5
  }
}
```

---

## 模块输出

- ✅ Token 认证机制
- ✅ 统一异常处理和响应格式
- ✅ CORS 支持
- ✅ OpenAPI/Swagger 文档
- ✅ 所有业务 API 端点

---

## 关键决策

1. **认证方式**: Bearer Token（放在 Authorization Header）
2. **响应格式**: 统一的 success/error 格式，便于客户端处理
3. **分页**: 使用 page/size 参数，返回 total 和 totalPages
4. **过滤**: 支持多条件组合过滤
5. **API 文档**: 使用 Swagger 自动生成，支持在线测试
