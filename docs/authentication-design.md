# GitLab Mirror 登录认证方案设计

## 📋 概述

本文档描述 GitLab Mirror Web UI 的登录认证方案设计。采用基于挑战-响应的认证机制，确保密码安全性，同时保持实现简单。

**设计原则**：
- ✅ 密码不明文传输
- ✅ 服务端不保存明文或可逆加密的密码
- ✅ 防重放攻击（基于时间窗口的挑战码）
- ✅ 暂不实现角色授权（所有登录用户权限相同）
- ✅ 使用标准加密算法（SHA-256）

---

## 🔐 认证流程

### 整体流程图

```
┌─────────┐                                    ┌─────────┐
│ 前端    │                                    │ 后端    │
└────┬────┘                                    └────┬────┘
     │                                              │
     │ 1. 请求挑战码                                │
     ├─────────────────────────────────────────────>│
     │    GET /api/auth/challenge                   │
     │                                              │
     │ 2. 返回挑战码 + 过期时间                     │
     │<─────────────────────────────────────────────┤
     │    { challenge, expiresAt }                  │
     │                                              │
     │ 3. 计算登录Hash                              │
     │    hash = SHA256(username + password + challenge)
     │                                              │
     │ 4. 提交登录                                  │
     ├─────────────────────────────────────────────>│
     │    POST /api/auth/login                      │
     │    { username, challenge, hash }             │
     │                                              │
     │                                              │ 5. 验证挑战码有效性
     │                                              │    - 检查是否过期（30秒）
     │                                              │    - 检查是否已使用
     │                                              │
     │                                              │ 6. 验证登录Hash
     │                                              │    计算期望Hash:
     │                                              │    expected = SHA256(
     │                                              │      username +
     │                                              │      storedPasswordHash +
     │                                              │      challenge
     │                                              │    )
     │                                              │    比较: hash == expected
     │                                              │
     │ 7. 返回Token                                 │
     │<─────────────────────────────────────────────┤
     │    { token, expiresAt }                      │
     │                                              │
     │ 8. 后续API请求                               │
     ├─────────────────────────────────────────────>│
     │    Header: Authorization: Bearer <token>     │
     │                                              │
```

---

## 💾 数据模型

### 用户表 (users)

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(64) NOT NULL COMMENT '密码Hash (SHA256)',
    salt VARCHAR(32) NOT NULL COMMENT '盐值',
    display_name VARCHAR(100) COMMENT '显示名称',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) COMMENT='用户表';
```

**字段说明**：
- `password_hash`: SHA256(salt + 原始密码)，64位十六进制字符串
- `salt`: 随机生成的32位十六进制字符串
- `enabled`: 账户启用状态，预留字段

### 挑战码表 (auth_challenges)

```sql
CREATE TABLE auth_challenges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    challenge VARCHAR(64) NOT NULL UNIQUE COMMENT '挑战码',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    used TINYINT(1) DEFAULT 0 COMMENT '是否已使用',
    used_at TIMESTAMP NULL COMMENT '使用时间',
    INDEX idx_challenge (challenge),
    INDEX idx_expires_at (expires_at)
) COMMENT='认证挑战码表';
```

**字段说明**：
- `challenge`: UUID v4格式的挑战码
- `expires_at`: 过期时间（创建时间 + 30秒）
- `used`: 标记是否已使用（防止重放攻击）

### 会话Token表 (auth_tokens)

```sql
CREATE TABLE auth_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(64) NOT NULL UNIQUE COMMENT 'Token',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    last_used_at TIMESTAMP NULL COMMENT '最后使用时间',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
) COMMENT='认证Token表';
```

**字段说明**：
- `token`: UUID v4格式的会话Token
- `expires_at`: Token过期时间（默认7天）
- `last_used_at`: 最后使用时间（可用于刷新Token）

---

## 🔑 密码存储方案

### 初始密码设置流程

```javascript
// 服务端生成用户账户
function createUser(username, rawPassword) {
    // 1. 生成随机盐值
    const salt = generateRandomHex(32);  // 32字节十六进制

    // 2. 计算密码Hash
    const passwordHash = SHA256(salt + rawPassword);

    // 3. 存储到数据库
    INSERT INTO users (username, password_hash, salt)
    VALUES (username, passwordHash, salt);
}
```

**安全性**：
- ✅ 原始密码不存储
- ✅ 使用随机盐值（每个用户唯一）
- ✅ 即使数据库泄露，也无法反推原始密码

---

## 🛡️ 登录验证流程

### 前端实现

```typescript
// 1. 获取挑战码
async function getChallenge(): Promise<Challenge> {
    const response = await fetch('/api/auth/challenge');
    return response.json();
    // 返回: { challenge: "uuid-v4", expiresAt: "2025-12-25T12:00:30Z" }
}

// 2. 计算登录Hash
function calculateLoginHash(username: string, password: string, challenge: string): string {
    // 计算: SHA256(username + password + challenge)
    const combined = username + password + challenge;
    return SHA256(combined);  // 使用crypto-js或Web Crypto API
}

// 3. 提交登录
async function login(username: string, password: string) {
    // 获取新的挑战码
    const { challenge } = await getChallenge();

    // 计算登录Hash
    const loginHash = calculateLoginHash(username, password, challenge);

    // 提交登录
    const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            username,
            challenge,
            hash: loginHash
        })
    });

    const { token, expiresAt } = await response.json();

    // 存储Token到localStorage
    localStorage.setItem('auth_token', token);
    localStorage.setItem('auth_expires', expiresAt);

    return token;
}
```

### 后端验证逻辑

```java
// 1. 验证挑战码
public boolean validateChallenge(String challenge) {
    AuthChallenge ch = challengeRepository.findByChallenge(challenge);

    if (ch == null) {
        return false;  // 挑战码不存在
    }

    if (ch.isUsed()) {
        return false;  // 已被使用（防重放）
    }

    if (ch.getExpiresAt().isBefore(Instant.now())) {
        return false;  // 已过期
    }

    // 标记为已使用
    ch.setUsed(true);
    ch.setUsedAt(Instant.now());
    challengeRepository.save(ch);

    return true;
}

// 2. 验证登录Hash
public boolean validateLogin(String username, String challenge, String clientHash) {
    // 查询用户
    User user = userRepository.findByUsername(username);
    if (user == null || !user.isEnabled()) {
        return false;
    }

    // 计算期望的Hash
    // expected = SHA256(username + storedPasswordHash + challenge)
    String expectedHash = DigestUtils.sha256Hex(
        username + user.getPasswordHash() + challenge
    );

    // 比较Hash
    return expectedHash.equals(clientHash);
}

// 3. 生成Token
public String generateToken(Long userId) {
    String token = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

    AuthToken authToken = new AuthToken();
    authToken.setToken(token);
    authToken.setUserId(userId);
    authToken.setExpiresAt(expiresAt);
    tokenRepository.save(authToken);

    return token;
}
```

---

## 🔒 安全特性

### 1. 防止密码泄露

- ✅ **客户端**: 密码仅在计算Hash时使用，不发送到服务器
- ✅ **传输层**: 只传输Hash值，即使被截获也无法反推密码
- ✅ **服务端**: 只存储 `SHA256(salt + password)`，不保存原始密码

### 2. 防止重放攻击

- ✅ **一次性挑战码**: 每次登录获取新的挑战码
- ✅ **时间窗口**: 挑战码30秒内有效
- ✅ **单次使用**: 挑战码使用后立即标记，不可重复使用

### 3. Hash计算安全

**前端计算公式**：
```
loginHash = SHA256(username + password + challenge)
```

**后端验证公式**：
```
expectedHash = SHA256(username + storedPasswordHash + challenge)
其中: storedPasswordHash = SHA256(salt + password)
```

**为什么安全**：
- 即使攻击者获取了 `loginHash`，也无法反推 `password`
- 即使攻击者获取了 `storedPasswordHash`，也无法直接登录（缺少 `challenge`）
- 挑战码每次不同，即使重放 `loginHash` 也会因挑战码失效而拒绝

### 4. Token管理

- ✅ **Token格式**: UUID v4（随机、不可预测）
- ✅ **过期时间**: 7天（可配置）
- ✅ **自动清理**: 定时任务清理过期Token和挑战码

---

## 📡 API接口定义

### 1. 获取挑战码

**请求**：
```http
GET /api/auth/challenge
```

**响应**：
```json
{
  "success": true,
  "data": {
    "challenge": "550e8400-e29b-41d4-a716-446655440000",
    "expiresAt": "2025-12-25T12:00:30Z"
  }
}
```

### 2. 登录

**请求**：
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "challenge": "550e8400-e29b-41d4-a716-446655440000",
  "hash": "a1b2c3d4e5f6..."
}
```

**成功响应**：
```json
{
  "success": true,
  "data": {
    "token": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "expiresAt": "2026-01-01T12:00:00Z",
    "user": {
      "username": "admin",
      "displayName": "Administrator"
    }
  }
}
```

**失败响应**：
```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "用户名或密码错误"
  }
}
```

### 3. 登出

**请求**：
```http
POST /api/auth/logout
Authorization: Bearer 7c9e6679-7425-40de-944b-e07fc1f90ae7
```

**响应**：
```json
{
  "success": true,
  "message": "已登出"
}
```

### 4. 验证Token

**请求**：
```http
GET /api/auth/verify
Authorization: Bearer 7c9e6679-7425-40de-944b-e07fc1f90ae7
```

**响应**：
```json
{
  "success": true,
  "data": {
    "valid": true,
    "expiresAt": "2026-01-01T12:00:00Z",
    "user": {
      "username": "admin",
      "displayName": "Administrator"
    }
  }
}
```

---

## 🚀 实施步骤

### Phase 1: 数据库和实体（1天）

1. 创建数据库表（SQL脚本）
2. 创建JPA实体类
   - `User.java`
   - `AuthChallenge.java`
   - `AuthToken.java`
3. 创建Repository接口

### Phase 2: 后端API（2天）

1. 实现认证服务
   - `AuthenticationService.java`
   - 挑战码生成和验证
   - 登录Hash验证
   - Token生成和管理
2. 实现认证控制器
   - `AuthController.java`
   - 4个API端点
3. 修改Token过滤器
   - 支持Token验证
   - 白名单：`/api/auth/**`, `/actuator/**`
4. 定时任务
   - 清理过期挑战码（每分钟）
   - 清理过期Token（每小时）

### Phase 3: 前端实现（1天）

1. 创建登录页面
   - `Login.vue`
   - 用户名/密码输入
   - 集成crypto-js进行Hash计算
2. 实现认证逻辑
   - `auth.ts` - 认证API客户端
   - `useAuth.ts` - 认证状态管理
3. 路由守卫
   - 未登录重定向到登录页
   - 登录后重定向到Dashboard
4. 全局请求拦截器
   - 自动添加 `Authorization` Header
   - Token过期处理

### Phase 4: 初始化和测试（0.5天）

1. 数据库初始化脚本
   - 创建默认管理员账户
   - 用户名: `admin`
   - 默认密码: `Admin@123`（首次登录后强制修改）
2. 集成测试
3. 安全性测试

---

## 🛠️ 技术栈

### 后端
- **加密算法**: Apache Commons Codec (SHA-256)
- **UUID生成**: `java.util.UUID`
- **定时任务**: Spring `@Scheduled`

### 前端
- **加密库**: `crypto-js` 或 Web Crypto API
- **状态管理**: Vue Composition API
- **HTTP客户端**: Axios拦截器

---

## 📋 默认账户

系统初始化时创建默认管理员账户：

| 字段 | 值 |
|------|-----|
| 用户名 | `admin` |
| 默认密码 | `Admin@123` |
| 显示名称 | `Administrator` |
| 状态 | 启用 |

**首次登录后建议立即修改密码**（后续可实现强制修改密码功能）。

---

## 🔄 未来扩展

### 可选功能（暂不实现）

1. **角色权限管理**
   - 添加 `roles` 和 `permissions` 表
   - 基于角色的访问控制（RBAC）

2. **多因素认证（MFA）**
   - TOTP (Google Authenticator)
   - 短信验证码

3. **密码策略**
   - 密码复杂度要求
   - 密码过期策略
   - 密码历史（防止重复使用）

4. **登录审计**
   - 登录日志记录
   - 失败登录锁定
   - 异常登录检测

5. **Token刷新机制**
   - Refresh Token
   - 滑动过期时间

6. **OAuth2/SAML集成**
   - 支持第三方登录
   - 企业SSO

---

## 📝 注意事项

### 安全建议

1. ✅ **HTTPS必须**: 生产环境必须使用HTTPS
2. ✅ **定期清理**: 定时清理过期的挑战码和Token
3. ✅ **日志审计**: 记录所有认证相关操作
4. ✅ **限流保护**: 防止暴力破解（可使用Guava RateLimiter）
5. ✅ **SQL注入防护**: 使用参数化查询

### 开发环境

- 可以配置更长的挑战码有效期（方便调试）
- 可以禁用HTTPS要求
- 可以添加调试日志

### 生产环境

- 挑战码有效期: 30秒（严格）
- Token有效期: 7天
- 必须启用HTTPS
- 限制登录失败次数（如: 5次/10分钟）

---

## 📚 参考资料

- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [RFC 4122 - UUID](https://tools.ietf.org/html/rfc4122)
- [SHA-256 Hash Algorithm](https://en.wikipedia.org/wiki/SHA-2)

---

**文档版本**: v1.0
**创建日期**: 2025-12-25
**最后更新**: 2025-12-25
**作者**: GitLab Mirror Team
