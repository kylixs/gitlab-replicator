# 模块 2: 后端服务实现 (Backend Services)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 实现SCRAM-SHA-256认证核心服务、防暴力破解和审计日志功能。

**预计时间**: 2.5天

---

## 参考文档

- [认证系统设计文档](../authentication-design.md)
  - [认证流程](../authentication-design.md#1-认证流程概述)
  - [密码存储方案](../authentication-design.md#2-密码存储方案)
  - [SCRAM-SHA-256算法细节](../authentication-design.md#21-scram-sha-256-算法)
  - [防暴力破解保护](../authentication-design.md#4-防暴力破解保护)
  - [审计日志](../authentication-design.md#6-审计日志)

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

### T2.1 SCRAM工具类实现
**状态**: ⏸️ 待处理 (Pending)
**依赖**: 模块1

**任务目标**:
实现SCRAM-SHA-256算法的加密工具类

**文件路径**: `server/src/main/java/com/gitlab/mirror/server/service/auth/ScramUtils.java`

**核心方法**:
1. **calculateStoredKey(password, saltHex, iterations)** - 计算StoredKey
   - PBKDF2密钥派生
   - HMAC-SHA256计算ClientKey
   - SHA-256计算StoredKey

2. **verifyClientProof(username, challenge, clientProof, storedKey)** - 验证ClientProof
   - 计算AuthMessage
   - 计算ClientSignature
   - XOR恢复ClientKey
   - 验证SHA256(ClientKey) == StoredKey

3. **辅助方法**:
   - `pbkdf2()` - PBKDF2WithHmacSHA256
   - `hmacSha256()` - HMAC-SHA256计算
   - `sha256()` - SHA-256哈希
   - `xor()` - 字节数组异或
   - `generateSalt()` - 生成16字节随机盐

**关键点**:
- 使用 `javax.crypto` 标准库
- PBKDF2迭代次数默认4096
- 所有加密操作异常处理
- 使用Apache Commons Codec进行Hex编解码

**验收标准**:
- SCRAM算法实现正确
- 与RFC 7677标准向量对比通过
- 性能测试：4096次迭代<100ms
- 编写并通过单元测试验证所有加密方法

**提交**: `feat(auth): add SCRAM-SHA-256 utility class`

---

### T2.2 防暴力破解服务
**状态**: ⏸️ 待处理 (Pending)
**依赖**: 模块1

**任务目标**:
实现基于Caffeine Cache的防暴力破解限流服务

**文件路径**: `server/src/main/java/com/gitlab/mirror/server/service/auth/BruteForceProtectionService.java`

**核心功能**:
1. **IP级别限流**
   - 使用Caffeine Cache存储失败计数
   - Key: `login_fail:ip:{ip}`
   - 10分钟窗口，20次失败上限

2. **账户级别限流**
   - Key: `login_fail:user:{username}`
   - 10分钟窗口，10次失败上限

3. **指数退避锁定**
   - 失败次数 → 锁定时长
   - 公式: `2^(failCount-2)` 秒
   - 最大锁定时长：300秒（5分钟）

**核心方法**:
- `checkLoginAllowed(username, ip)` - 返回锁定秒数（0表示允许）
- `recordLoginFailure(username, ip)` - 记录失败
- `resetFailureCount(username, ip)` - 成功登录后重置
- `getFailureCount(username)` - 获取失败次数
- `calculateExponentialBackoff(failCount)` - 计算退避时间

**配置类**: `AuthProperties.java`
- 使用 `@ConfigurationProperties(prefix = "auth")`
- 可配置限流参数和退避策略

**关键点**:
- Caffeine Cache自动过期（10分钟）
- AtomicInteger保证线程安全
- 支持配置化调整参数

**验收标准**:
- IP限流正确工作
- 账户限流正确工作
- 指数退避计算准确
- 成功登录后计数重置
- 编写并通过单元测试验证限流逻辑

**提交**: `feat(auth): add brute-force protection service`

---

### T2.3 审计日志服务
**状态**: ⏸️ 待处理 (Pending)
**依赖**: 模块1

**任务目标**:
实现异步审计日志记录服务

**文件路径**: `server/src/main/java/com/gitlab/mirror/server/service/auth/LoginAuditService.java`

**核心功能**:
1. **异步记录登录事件**
   - 使用 `@Async` 注解
   - 记录类型：SUCCESS, FAILURE, LOCKED, RATE_LIMITED
   - 不影响主流程性能

2. **审计日志方法**:
   - `recordLoginSuccess(username, ip, userAgent)`
   - `recordLoginFailure(username, ip, userAgent, reason)`
   - `recordAccountLocked(username, ip, userAgent)`
   - `recordRateLimited(username, ip, userAgent)`

3. **查询方法**:
   - `getUserLoginHistory(username, limit)` - 用户登录历史
   - `getIpLoginHistory(ip, limit)` - IP登录历史

4. **清理方法**:
   - `cleanupOldLogs(retentionDays)` - 清理过期日志（默认90天）

**异步配置**: `AsyncConfig.java`
- 配置审计专用线程池
- 核心线程：2
- 最大线程：5
- 队列容量：100

**关键点**:
- 使用 `@EnableAsync` 启用异步
- 审计失败不应影响主流程
- 使用 `@Transactional` 保证清理操作原子性

**验收标准**:
- 异步写入不阻塞登录流程
- 所有登录事件正确记录
- 查询功能正常
- 清理功能正常
- 编写并通过单元测试验证审计日志功能

**提交**: `feat(auth): add login audit service with async logging`

---

### T2.4 认证服务核心实现
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T2.1, T2.2, T2.3

**任务目标**:
实现完整的SCRAM认证服务，集成防暴力破解和审计日志

**文件路径**: `server/src/main/java/com/gitlab/mirror/server/service/auth/AuthenticationService.java`

**核心功能**:

1. **generateChallenge(username)** - 生成挑战码
   - 查询用户，返回Salt和迭代次数
   - 生成UUID挑战码
   - 存储到内存ConcurrentHashMap
   - 设置30秒过期时间

2. **login(request, ip, userAgent)** - 登录验证
   - 检查防暴力破解限制
   - 验证挑战码有效性
   - 使用SCRAM验证ClientProof
   - 生成UUID Token
   - 重置失败计数
   - 记录审计日志

3. **validateToken(token)** - Token验证
   - 查询Token是否存在
   - 检查是否过期
   - 更新last_used_at
   - 返回用户信息

4. **logout(token)** - 登出
   - 删除Token记录

5. **定时任务**:
   - `cleanupExpiredChallenges()` - 每分钟清理过期挑战码
   - `cleanupExpiredTokens()` - 每小时清理过期Token

**挑战码内存存储**:
- 使用 `ConcurrentHashMap<String, ChallengeInfo>`
- 定时清理过期挑战码
- 单次使用标记

**异常处理**:
- `AuthenticationException` - 认证失败
- `AccountLockedException` - 账户锁定（带锁定时间和失败次数）

**关键点**:
- 使用 `@Scheduled` 注解定时任务
- 使用 `@Transactional` 保证数据一致性
- 所有密码验证失败统一返回"用户名或密码错误"

**验收标准**:
- 完整登录流程正确
- 防暴力破解集成正确
- 审计日志记录完整
- 定时清理任务正常
- 编写并通过单元测试验证认证服务核心逻辑

**提交**: `feat(auth): add authentication service with SCRAM and brute-force protection`

---

### T2.5 Token过滤器改造（兼容性）
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T2.4

**任务目标**:
改造现有Token过滤器，兼容API Key和用户Token两种认证方式

**文件路径**: `server/src/main/java/com/gitlab/mirror/server/api/security/TokenAuthenticationFilter.java`

**核心改造**:

1. **白名单路径**:
   - `/actuator/**`
   - `/swagger/**`, `/v3/api-docs/**`
   - `/api/status`
   - `/api/auth/**` - 认证接口免Token

2. **Token验证逻辑（两种方式）**:
   - 优先验证API Key（向后兼容）
   - 如果不是API Key，查询auth_tokens表验证
   - 检查Token是否过期
   - 更新last_used_at字段

3. **用户上下文设置**:
   - 将用户信息设置到Request Attribute
   - `request.setAttribute("currentUser", user)`

4. **IP提取**:
   - 考虑代理头：X-Forwarded-For, X-Real-IP
   - 处理多IP情况（取第一个）

**兼容性设计**:
- API Key验证通过时创建虚拟admin用户
- 保持现有API Key功能不受影响
- 新用户Token验证独立实现

**关键点**:
- 使用 `OncePerRequestFilter` 确保每个请求只过滤一次
- 未认证返回401状态码
- 白名单路径直接放行

**验收标准**:
- API Key验证仍然有效
- 用户Token验证正确
- 白名单路径放行
- 过期Token被拒绝
- last_used_at正确更新
- 编写并通过单元测试验证Token过滤器

**提交**: `refactor(auth): enhance token filter with user authentication support`

---

## 模块验收

**验收检查项**:
1. SCRAM算法实现正确，与标准向量对比通过
2. 防暴力破解限流正确工作
3. 审计日志异步记录，不阻塞主流程
4. 完整登录流程端到端测试通过
5. Token过滤器兼容API Key和用户Token

**完成标志**: 所有任务状态为 ✅，模块状态更新为 ✅ 已完成
