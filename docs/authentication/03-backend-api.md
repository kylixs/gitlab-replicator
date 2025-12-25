# 模块 3: 后端API实现 (Backend API)

**状态**: ✅ 已完成 (Completed)

**目标**: 实现认证相关的RESTful API接口。

**预计时间**: 1天

---

## 参考文档

- [认证系统设计文档](../authentication-design.md)
  - [API接口定义](../authentication-design.md#5-api-接口定义)
  - [错误处理](../authentication-design.md#52-错误响应格式)

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

### T3.1 创建请求/响应DTO
**状态**: ✅ 已完成 (Completed)
**依赖**: 模块2

**任务目标**:
创建认证相关的数据传输对象（DTO）

**文件路径**: `common/src/main/java/com/gitlab/mirror/common/model/auth/`

**DTO列表**:

1. **ChallengeRequest** - 请求挑战码
   - `username` (必填, 3-50字符)

2. **ChallengeResponse** - 挑战码响应
   - `challenge` - UUID挑战码
   - `salt` - 盐值（十六进制）
   - `iterations` - PBKDF2迭代次数
   - `expiresAt` - 过期时间

3. **LoginRequest** - 登录请求
   - `username` (必填)
   - `challenge` (必填)
   - `clientProof` (必填, 64字符十六进制)

4. **LoginResponse** - 登录响应
   - `token` - UUID Token
   - `expiresAt` - Token过期时间
   - `user` - 用户信息对象

5. **UserInfo** - 用户信息
   - `username`
   - `displayName`

6. **TokenVerifyResponse** - Token验证响应
   - `valid` - 是否有效
   - `expiresAt` - 过期时间
   - `user` - 用户信息

7. **ApiResponse<T>** - 通用响应包装器
   - `success` - 成功标志
   - `data` - 数据对象
   - `error` - 错误信息对象
   - 工厂方法：`success(T data)`, `error(code, message)`, `accountLocked(...)`

8. **ApiError** - 错误信息
   - `code` - 错误代码
   - `message` - 错误消息
   - `retryAfter` - 重试时间（秒）
   - `failedAttempts` - 失败次数

**关键点**:
- 使用Bean Validation注解（@NotBlank, @Size等）
- 使用Lombok @Data和@Builder
- 支持JSON序列化/反序列化

**验收标准**:
- 所有DTO字段正确
- 验证注解生效
- JSON序列化正常
- 编写并通过单元测试验证DTO字段和验证规则

**提交**: `feat(auth): add authentication DTOs`

---

### T3.2 创建认证控制器
**状态**: ✅ 已完成 (Completed)
**依赖**: T3.1, 模块2

**任务目标**:
实现认证REST API控制器

**文件路径**: `server/src/main/java/com/gitlab/mirror/server/controller/AuthController.java`

**API端点列表**:

1. **POST /api/auth/challenge** - 获取挑战码
   - 请求：`ChallengeRequest`
   - 响应：`ApiResponse<ChallengeResponse>`
   - 状态码：200 / 404（用户不存在）

2. **POST /api/auth/login** - 登录
   - 请求：`LoginRequest`
   - 响应：`ApiResponse<LoginResponse>`
   - 状态码：200 / 401（认证失败） / 423（账户锁定）
   - 提取客户端IP和User-Agent

3. **POST /api/auth/logout** - 登出
   - 需要Bearer Token
   - 响应：`ApiResponse<Void>`
   - 状态码：200

4. **GET /api/auth/verify** - 验证Token
   - 需要Bearer Token
   - 响应：`ApiResponse<TokenVerifyResponse>`
   - 状态码：200 / 401（Token无效）

**核心方法**:
- `getClientIp(HttpServletRequest)` - 提取客户端IP（支持代理）
- `extractToken(HttpServletRequest)` - 提取Bearer Token

**异常处理**:
- `AccountLockedException` → 返回锁定信息和重试时间
- `AuthenticationException` → 返回统一错误消息

**关键点**:
- 使用 `@RestController` 和 `@RequestMapping`
- 使用 `@Valid` 验证请求
- 统一响应格式 `ApiResponse`
- 提取IP考虑X-Forwarded-For头

**验收标准**:
- 所有API端点正确响应
- 请求验证生效
- 异常处理正确
- HTTP状态码正确
- 编写并通过单元测试验证所有API端点

**提交**: `feat(auth): add authentication REST API controller`

---

### T3.3 全局异常处理器
**状态**: ✅ 已完成 (Completed)
**依赖**: T3.2

**任务目标**:
创建全局异常处理器，统一异常响应格式

**文件路径**: `server/src/main/java/com/gitlab/mirror/server/api/exception/GlobalExceptionHandler.java`

**处理的异常类型**:

1. **MethodArgumentNotValidException** - 验证异常
   - 返回400状态码
   - 错误代码：VALIDATION_ERROR
   - 收集所有验证错误消息

2. **AuthenticationException** - 认证异常
   - 返回401状态码
   - 错误代码：AUTHENTICATION_ERROR

3. **AccountLockedException** - 账户锁定异常
   - 返回423状态码
   - 错误代码：ACCOUNT_LOCKED
   - 包含retryAfter和failedAttempts

4. **RateLimitExceededException** - 限流异常
   - 返回429状态码
   - 错误代码：TOO_MANY_REQUESTS

5. **Exception** - 通用异常
   - 返回500状态码
   - 错误代码：INTERNAL_ERROR
   - 记录完整异常堆栈

**关键点**:
- 使用 `@RestControllerAdvice`
- 使用 `@ExceptionHandler` 处理特定异常
- 所有异常统一返回 `ApiResponse` 格式
- 记录适当的日志级别

**验收标准**:
- 所有异常正确捕获
- 响应格式统一
- HTTP状态码正确
- 日志记录完整
- 编写并通过单元测试验证异常处理器

**提交**: `feat(auth): add global exception handler`

---

### T3.4 Swagger API文档（可选）
**状态**: ✅ 已完成 (Completed)
**依赖**: T3.2

**任务目标**:
配置Swagger/OpenAPI生成交互式API文档

**配置文件**: `server/src/main/java/com/gitlab/mirror/server/config/SwaggerConfig.java`

**核心配置**:
- 使用 `@OpenAPIDefinition` 定义API信息
- 配置Bearer Token认证方案
- 配置API分组：Authentication

**Controller注解**:
- `@Tag(name = "Authentication")`
- `@Operation(summary = "...")`
- `@ApiResponses` 定义响应状态码

**访问地址**:
- Swagger UI: `http://localhost:9999/swagger-ui.html`
- OpenAPI JSON: `http://localhost:9999/v3/api-docs`

**关键点**:
- 添加springdoc-openapi依赖
- 配置API文档标题和版本
- 配置认证方案（Bearer Token）

**验收标准**:
- Swagger UI可访问
- API文档完整准确
- 可通过Swagger UI测试API
- 验证Swagger文档与实际API一致

**提交**: `docs(auth): add Swagger API documentation`

---

## 模块验收

**验收检查项**:
1. 所有API端点正确响应
2. 请求验证生效
3. 异常处理统一
4. Swagger文档可访问（如果实现）
5. 端到端测试通过

**完成标志**: 所有任务状态为 ✅，模块状态更新为 ✅ 已完成
