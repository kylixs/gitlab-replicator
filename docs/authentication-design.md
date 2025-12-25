# GitLab Mirror 登录认证方案设计

## 📋 概述

本文档描述 GitLab Mirror Web UI 的登录认证方案设计。采用基于 **SCRAM-SHA-256** (Salted Challenge Response Authentication Mechanism) 的简化版本，参考业界最佳实践，确保密码安全性。

**设计原则**：
- ✅ 密码不明文传输
- ✅ 服务端不保存明文或可逆加密的密码
- ✅ 使用 PBKDF2 进行密钥派生（抗暴力破解）
- ✅ 防重放攻击（基于时间窗口的挑战码）
- ✅ 挑战码存储在内存中（不使用数据库）
- ✅ 参考业界标准 SCRAM 认证机制
- ✅ 暂不实现角色授权（所有登录用户权限相同）

**参考标准**：
- RFC 5802: Salted Challenge Response Authentication Mechanism (SCRAM)
- RFC 7677: SCRAM-SHA-256 and SCRAM-SHA-256-PLUS
- PBKDF2 (RFC 2898)

---

## 🔐 认证流程（基于 SCRAM 简化版）

### 整体流程图

```
┌─────────┐                                    ┌─────────┐
│ 前端    │                                    │ 后端    │
└────┬────┘                                    └────┬────┘
     │                                              │
     │ 1. 请求挑战码 + Salt                         │
     ├─────────────────────────────────────────────>│
     │    POST /api/auth/challenge                  │
     │    { username }                              │
     │                                              │ - 查询用户获取salt
     │                                              │ - 生成随机challenge
     │                                              │ - 存入内存(Map)
     │                                              │
     │ 2. 返回挑战码 + Salt + 迭代次数              │
     │<─────────────────────────────────────────────┤
     │    { challenge, salt, iterations, expiresAt }│
     │                                              │
     │ 3. 前端计算 ClientProof                      │
     │    saltedPassword = PBKDF2(password, salt, iterations)
     │    clientKey = HMAC-SHA256(saltedPassword, "Client Key")
     │    storedKey = SHA256(clientKey)             │
     │    authMessage = username + challenge        │
     │    clientSignature = HMAC-SHA256(storedKey, authMessage)
     │    clientProof = XOR(clientKey, clientSignature)
     │                                              │
     │ 4. 提交登录                                  │
     ├─────────────────────────────────────────────>│
     │    POST /api/auth/login                      │
     │    { username, challenge, clientProof }      │
     │                                              │
     │                                              │ 5. 验证挑战码有效性
     │                                              │    - 从内存检查是否存在
     │                                              │    - 检查是否过期（30秒）
     │                                              │    - 检查是否已使用
     │                                              │
     │                                              │ 6. 验证 ClientProof
     │                                              │    从数据库获取storedKey
     │                                              │    计算 authMessage
     │                                              │    计算 clientSignature
     │                                              │    恢复 clientKey = XOR(clientProof, clientSignature)
     │                                              │    验证 SHA256(clientKey) == storedKey
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

### 关键改进点

**相比原方案的优势**：
1. ✅ **Salt 安全传输** - Salt 不是秘密，可以安全传输给前端
2. ✅ **PBKDF2 密钥派生** - 使用迭代哈希（默认4096次），大幅增强抗暴力破解能力
3. ✅ **XOR 混淆** - 使用 HMAC 和 XOR 操作，即使截获也无法反推密码
4. ✅ **内存存储挑战码** - 无需数据库，性能更好，自动过期清理
5. ✅ **标准 SCRAM 机制** - 参考 PostgreSQL、MongoDB 等数据库的认证方式

---

## 💾 数据模型

### 用户表 (users)

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    stored_key VARCHAR(64) NOT NULL COMMENT 'StoredKey (SHA256(ClientKey))',
    salt VARCHAR(32) NOT NULL COMMENT '盐值 (16字节十六进制)',
    iterations INT DEFAULT 4096 COMMENT 'PBKDF2迭代次数',
    display_name VARCHAR(100) COMMENT '显示名称',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) COMMENT='用户表';
```

**字段说明（SCRAM 方式）**：
- `stored_key`: SHA256(ClientKey)，用于验证客户端身份
  - ClientKey = HMAC-SHA256(SaltedPassword, "Client Key")
  - SaltedPassword = PBKDF2(password, salt, iterations)
- `salt`: 随机生成的盐值（16字节，32位十六进制字符串）
- `iterations`: PBKDF2 迭代次数（默认 4096，可调整以适应性能需求）
- `enabled`: 账户启用状态

**为什么不存储密码Hash？**
- SCRAM 机制中，服务端只需存储 StoredKey
- StoredKey 由 SaltedPassword 派生，无法反推原始密码
- 即使数据库泄露，攻击者无法直接使用 StoredKey 登录

### 挑战码存储（内存）

**不使用数据库表**，改为内存存储（ConcurrentHashMap）：

```java
// 挑战码数据结构
class ChallengeInfo {
    String username;
    Instant createdAt;
    Instant expiresAt;
    boolean used;
}

// 内存存储
ConcurrentHashMap<String, ChallengeInfo> challengeStore;
```

**优势**：
- ✅ 性能更好（无数据库IO）
- ✅ 自动过期（定时清理或检查时清理）
- ✅ 无需数据库表和索引
- ✅ 挑战码本身是临时数据，无需持久化

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

## 🔑 密码存储方案（SCRAM-SHA-256）

### 用户创建流程

```java
// 服务端创建用户账户
public void createUser(String username, String rawPassword) {
    // 1. 生成随机盐值（16字节）
    byte[] salt = new byte[16];
    SecureRandom random = new SecureRandom();
    random.nextBytes(salt);
    String saltHex = Hex.encodeHexString(salt);  // 转为十六进制字符串

    // 2. PBKDF2 密钥派生
    int iterations = 4096;
    SecretKeySpec saltedPassword = PBKDF2(
        rawPassword,
        salt,
        iterations,
        256  // 输出长度：256位
    );

    // 3. 计算 ClientKey
    byte[] clientKey = HMAC_SHA256(saltedPassword, "Client Key");

    // 4. 计算 StoredKey
    byte[] storedKey = SHA256(clientKey);
    String storedKeyHex = Hex.encodeHexString(storedKey);

    // 5. 存储到数据库
    User user = new User();
    user.setUsername(username);
    user.setStoredKey(storedKeyHex);
    user.setSalt(saltHex);
    user.setIterations(iterations);
    userRepository.save(user);
}
```

**SCRAM 计算链**：
```
原始密码
   ↓ PBKDF2(password, salt, iterations)
SaltedPassword (256位密钥)
   ↓ HMAC-SHA256(SaltedPassword, "Client Key")
ClientKey (32字节)
   ↓ SHA256(ClientKey)
StoredKey (32字节) → 存储到数据库
```

**安全性优势**：
- ✅ **PBKDF2 迭代** - 4096 次迭代大幅增加暴力破解成本
- ✅ **多层派生** - StoredKey 经过 3 次不可逆变换，无法反推密码
- ✅ **随机盐值** - 每个用户唯一，防止彩虹表攻击
- ✅ **标准算法** - 使用 RFC 标准，经过广泛验证

---

## 🛡️ 登录验证流程（SCRAM-SHA-256）

### 前端实现

```typescript
import CryptoJS from 'crypto-js';

// 辅助函数：PBKDF2
function pbkdf2(password: string, saltHex: string, iterations: number): CryptoJS.lib.WordArray {
    const salt = CryptoJS.enc.Hex.parse(saltHex);
    return CryptoJS.PBKDF2(password, salt, {
        keySize: 256 / 32,  // 8个32位字 = 256位
        iterations: iterations,
        hasher: CryptoJS.algo.SHA256
    });
}

// 辅助函数：XOR 操作
function xor(a: CryptoJS.lib.WordArray, b: CryptoJS.lib.WordArray): string {
    const aBytes = a.words;
    const bBytes = b.words;
    const result = [];
    for (let i = 0; i < aBytes.length; i++) {
        result.push(aBytes[i] ^ bBytes[i]);
    }
    return CryptoJS.lib.WordArray.create(result).toString(CryptoJS.enc.Hex);
}

// 1. 获取挑战码和Salt
async function getChallenge(username: string): Promise<ChallengeResponse> {
    const response = await fetch('/api/auth/challenge', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username })
    });
    return response.json();
    // 返回: { challenge, salt, iterations, expiresAt }
}

// 2. 计算 ClientProof
function calculateClientProof(
    username: string,
    password: string,
    challenge: string,
    saltHex: string,
    iterations: number
): string {
    // Step 1: SaltedPassword = PBKDF2(password, salt, iterations)
    const saltedPassword = pbkdf2(password, saltHex, iterations);

    // Step 2: ClientKey = HMAC-SHA256(SaltedPassword, "Client Key")
    const clientKey = CryptoJS.HmacSHA256("Client Key", saltedPassword);

    // Step 3: StoredKey = SHA256(ClientKey)
    const storedKey = CryptoJS.SHA256(clientKey.toString(CryptoJS.enc.Hex));

    // Step 4: AuthMessage = username + ":" + challenge
    const authMessage = `${username}:${challenge}`;

    // Step 5: ClientSignature = HMAC-SHA256(StoredKey, AuthMessage)
    const clientSignature = CryptoJS.HmacSHA256(authMessage, storedKey);

    // Step 6: ClientProof = XOR(ClientKey, ClientSignature)
    const clientProof = xor(clientKey, clientSignature);

    return clientProof;
}

// 3. 登录
async function login(username: string, password: string) {
    // 获取挑战码和Salt
    const { challenge, salt, iterations, expiresAt } = await getChallenge(username);

    // 计算 ClientProof
    const clientProof = calculateClientProof(username, password, challenge, salt, iterations);

    // 提交登录
    const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            username,
            challenge,
            clientProof
        })
    });

    const { token, expiresAt: tokenExpires } = await response.json();

    // 存储Token
    localStorage.setItem('auth_token', token);
    localStorage.setItem('auth_expires', tokenExpires);

    return token;
}
```

### 后端验证逻辑

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

// 挑战码内存存储
private ConcurrentHashMap<String, ChallengeInfo> challengeStore = new ConcurrentHashMap<>();

// 1. 生成挑战码（返回 Salt）
public ChallengeResponse generateChallenge(String username) {
    // 查询用户，获取Salt
    User user = userRepository.findByUsername(username);
    if (user == null || !user.isEnabled()) {
        throw new AuthenticationException("用户不存在或已禁用");
    }

    // 生成随机挑战码
    String challenge = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(30);

    // 存储到内存
    ChallengeInfo info = new ChallengeInfo();
    info.setUsername(username);
    info.setCreatedAt(now);
    info.setExpiresAt(expiresAt);
    info.setUsed(false);
    challengeStore.put(challenge, info);

    // 返回挑战码、Salt、迭代次数
    return new ChallengeResponse(
        challenge,
        user.getSalt(),
        user.getIterations(),
        expiresAt
    );
}

// 2. 验证挑战码
public boolean validateChallenge(String challenge, String username) {
    ChallengeInfo info = challengeStore.get(challenge);

    if (info == null) {
        return false;  // 挑战码不存在
    }

    if (!info.getUsername().equals(username)) {
        return false;  // 用户名不匹配
    }

    if (info.isUsed()) {
        return false;  // 已被使用（防重放）
    }

    if (info.getExpiresAt().isBefore(Instant.now())) {
        challengeStore.remove(challenge);  // 清理过期挑战码
        return false;  // 已过期
    }

    // 标记为已使用
    info.setUsed(true);

    return true;
}

// 3. 验证 ClientProof (SCRAM-SHA-256)
public boolean validateClientProof(String username, String challenge, String clientProofHex)
        throws Exception {
    // 查询用户
    User user = userRepository.findByUsername(username);
    if (user == null || !user.isEnabled()) {
        return false;
    }

    // 获取 StoredKey
    byte[] storedKey = Hex.decodeHex(user.getStoredKey());

    // 计算 AuthMessage
    String authMessage = username + ":" + challenge;

    // 计算 ClientSignature = HMAC-SHA256(StoredKey, AuthMessage)
    Mac hmac = Mac.getInstance("HmacSHA256");
    SecretKeySpec keySpec = new SecretKeySpec(storedKey, "HmacSHA256");
    hmac.init(keySpec);
    byte[] clientSignature = hmac.doFinal(authMessage.getBytes(StandardCharsets.UTF_8));

    // 解码 ClientProof
    byte[] clientProof = Hex.decodeHex(clientProofHex);

    // 恢复 ClientKey = XOR(ClientProof, ClientSignature)
    byte[] clientKey = new byte[32];
    for (int i = 0; i < 32; i++) {
        clientKey[i] = (byte) (clientProof[i] ^ clientSignature[i]);
    }

    // 计算 SHA256(ClientKey)
    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    byte[] computedStoredKey = sha256.digest(clientKey);

    // 比较 StoredKey
    return MessageDigest.isEqual(storedKey, computedStoredKey);
}

// 4. 生成Token
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

// 5. 定时清理过期挑战码
@Scheduled(fixedDelay = 60000)  // 每分钟执行一次
public void cleanExpiredChallenges() {
    Instant now = Instant.now();
    challengeStore.entrySet().removeIf(entry ->
        entry.getValue().getExpiresAt().isBefore(now)
    );
}
```

**验证流程说明**：
1. 客户端发送 `ClientProof = XOR(ClientKey, ClientSignature)`
2. 服务端计算 `ClientSignature = HMAC-SHA256(StoredKey, AuthMessage)`
3. 服务端恢复 `ClientKey = XOR(ClientProof, ClientSignature)`
4. 服务端验证 `SHA256(ClientKey) == StoredKey`

**为什么安全？**
- 即使攻击者截获 ClientProof，也无法反推 ClientKey（需要知道 ClientSignature）
- ClientSignature 由 StoredKey 计算，而 StoredKey 存储在服务端
- 每次登录的 Challenge 不同，ClientSignature 也不同，无法重放

---

## 🔒 安全特性（SCRAM-SHA-256）

### 1. 防止密码泄露

- ✅ **客户端**: 密码仅用于 PBKDF2 计算，不发送到服务器
- ✅ **传输层**: 只传输 ClientProof（XOR 混淆后的值），无法反推密码
- ✅ **服务端**: 只存储 StoredKey = SHA256(ClientKey)，无法反推密码
- ✅ **PBKDF2 保护**: 4096 次迭代，即使暴力破解也需大量计算

### 2. 防止重放攻击

- ✅ **一次性挑战码**: 每次登录生成新的随机挑战码
- ✅ **时间窗口**: 挑战码 30 秒内有效
- ✅ **单次使用**: 挑战码使用后立即标记为已使用
- ✅ **内存存储**: 挑战码存储在内存中，服务重启自动失效

### 3. SCRAM 安全机制

**客户端计算链**：
```
原始密码
   ↓ PBKDF2(password, salt, 4096)
SaltedPassword
   ↓ HMAC-SHA256(SaltedPassword, "Client Key")
ClientKey
   ↓ SHA256(ClientKey)
StoredKey (用于验证)
   ↓ HMAC-SHA256(StoredKey, AuthMessage)
ClientSignature
   ↓ XOR(ClientKey, ClientSignature)
ClientProof → 发送给服务器
```

**服务端验证链**：
```
从数据库获取 StoredKey
   ↓ HMAC-SHA256(StoredKey, AuthMessage)
ClientSignature
   ↓ XOR(ClientProof, ClientSignature)
恢复 ClientKey
   ↓ SHA256(ClientKey)
计算 StoredKey
   ↓ 比较
验证成功/失败
```

**为什么安全**：
- ✅ **多层派生**: StoredKey 由密码经过 4 次不可逆变换得到
- ✅ **XOR 混淆**: ClientProof 无法直接反推 ClientKey
- ✅ **HMAC 保护**: 使用 HMAC-SHA256 确保消息完整性
- ✅ **Challenge 绑定**: 每次登录的 Challenge 不同，无法重放

### 4. Salt 安全性

**Salt 可以安全传输的原因**：
- Salt 本身不是秘密，其作用是防止彩虹表攻击
- 即使攻击者知道 Salt，仍需进行 4096 次 PBKDF2 迭代
- 无法从 Salt + StoredKey 反推原始密码

### 5. Token 管理

- ✅ **Token 格式**: UUID v4（随机、不可预测）
- ✅ **过期时间**: 7 天（可配置）
- ✅ **自动清理**: 定时任务清理过期 Token 和挑战码
- ✅ **数据库存储**: Token 持久化，支持跨服务器验证

### 6. 防暴力破解保护

#### 6.1 多层防护策略

采用**深度防御**原则，结合多种防护机制：

| 防护层级 | 机制 | 目的 |
|---------|------|-----|
| 第1层 | IP级别限流 | 防止单个IP大量尝试 |
| 第2层 | 账户级别限流 | 防止分布式暴力破解 |
| 第3层 | 指数退避锁定 | 逐步增加攻击成本 |
| 第4层 | 审计告警 | 检测异常行为 |

#### 6.2 指数退避机制（推荐）

相比固定锁定时间，**指数退避**更智能：

```
失败次数  →  锁定时长
   1-2    →  无锁定
   3      →  1秒
   4      →  2秒
   5      →  4秒
   6      →  8秒
   7      →  16秒
   8      →  32秒
   9      →  64秒
  10+     →  300秒 (5分钟，上限)
```

**优势**：
- ✅ 对正常用户友好（偶尔输错密码影响小）
- ✅ 对攻击者有效（持续失败会快速累积惩罚）
- ✅ 避免DoS攻击（不会永久锁定账户）
- ✅ 自动恢复（成功登录后重置计数）

#### 6.3 限流策略

**IP级别限流**（防止单点暴力破解）：
- 窗口时间：10分钟
- 限制次数：20次失败尝试
- 超限处理：返回 429 Too Many Requests，要求等待或CAPTCHA

**账户级别限流**（防止分布式暴力破解）：
- 窗口时间：10分钟
- 限制次数：10次失败尝试
- 超限处理：账户临时锁定（指数退避）

**实现方式**：
- 使用 **Caffeine Cache** 或 **Redis** 存储失败计数
- Key格式：`login_fail:ip:{ip}` 和 `login_fail:user:{username}`

#### 6.4 审计和告警

**记录失败登录事件**：
```java
CREATE TABLE login_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent TEXT,
    login_result ENUM('SUCCESS', 'FAILURE', 'LOCKED', 'RATE_LIMITED'),
    failure_reason VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_ip (ip_address),
    INDEX idx_created_at (created_at)
);
```

**告警条件**：
- 单个IP在5分钟内失败超过30次
- 单个账户在10分钟内从10个不同IP登录失败
- 检测到异常模式（如按字典顺序尝试）

**告警方式**：
- 写入日志文件
- 发送邮件通知管理员
- 集成到监控系统（Prometheus/Grafana）

#### 6.5 安全建议

**OWASP 最佳实践**：
- ✅ 失败次数与账户绑定，不与IP绑定（防止IP轮换）
- ✅ 使用指数退避而非固定锁定（避免DoS）
- ✅ 不要泄露用户是否存在（统一错误消息）
- ✅ 成功登录后重置失败计数
- ✅ 记录审计日志用于分析

**Microsoft 智能锁定参考**：
- 识别合法用户和攻击者
- 对合法用户友好（如从不同地点登录）
- 对攻击者严格限制

---

## 📡 API 接口定义（SCRAM-SHA-256）

### 1. 获取挑战码和 Salt

**请求**：
```http
POST /api/auth/challenge
Content-Type: application/json

{
  "username": "admin"
}
```

**成功响应**：
```json
{
  "success": true,
  "data": {
    "challenge": "550e8400-e29b-41d4-a716-446655440000",
    "salt": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
    "iterations": 4096,
    "expiresAt": "2025-12-25T12:00:30Z"
  }
}
```

**失败响应**（用户不存在）：
```json
{
  "success": false,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "用户不存在"
  }
}
```

**注意**：
- 为防止用户名枚举攻击，可以考虑对不存在的用户也返回随机 salt
- 生产环境建议添加频率限制

### 2. 登录（提交 ClientProof）

**请求**：
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "challenge": "550e8400-e29b-41d4-a716-446655440000",
  "clientProof": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
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

**错误码**：
- `INVALID_CREDENTIALS`: 用户名或密码错误（统一消息，不泄露用户是否存在）
- `CHALLENGE_EXPIRED`: 挑战码已过期
- `CHALLENGE_USED`: 挑战码已被使用
- `CHALLENGE_NOT_FOUND`: 挑战码不存在
- `USER_DISABLED`: 用户已被禁用
- `ACCOUNT_LOCKED`: 账户临时锁定（防暴力破解）
- `TOO_MANY_REQUESTS`: IP请求过于频繁（429状态码）

**锁定响应示例**：
```json
{
  "success": false,
  "error": {
    "code": "ACCOUNT_LOCKED",
    "message": "账户已临时锁定，请稍后再试",
    "retryAfter": 32,
    "failedAttempts": 7
  }
}
```

**频率限制响应**（429）：
```json
{
  "success": false,
  "error": {
    "code": "TOO_MANY_REQUESTS",
    "message": "请求过于频繁，请稍后再试",
    "retryAfter": 600
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

## 🚀 实施步骤（SCRAM-SHA-256）

### Phase 1: 数据库和实体（1天）

1. **创建数据库表**（SQL脚本）
   - `users` 表（包含 stored_key, salt, iterations 字段）
   - `auth_tokens` 表
   - ❌ 不创建 `auth_challenges` 表（使用内存存储）

2. **创建JPA实体类**
   - `User.java` - 用户实体（SCRAM字段）
   - `AuthToken.java` - Token实体
   - ~~`AuthChallenge.java`~~ - 不需要（内存存储）

3. **创建Java类**
   - `ChallengeInfo.java` - 挑战码信息（内存数据结构）
   - Repository接口：`UserRepository`, `AuthTokenRepository`

### Phase 2: 后端API实现（2.5天）

1. **实现 SCRAM 工具类**
   - `ScramUtils.java` - PBKDF2、HMAC-SHA256、XOR 等工具方法
   - 用户创建时的 StoredKey 计算
   - ClientProof 验证逻辑

2. **实现防暴力破解服务**
   - `BruteForceProtectionService.java`
   - 失败计数器（Caffeine Cache）
     - IP级别计数：`login_fail:ip:{ip}`
     - 账户级别计数：`login_fail:user:{username}`
   - 指数退避计算：`lockoutSeconds = Math.min(2^(failCount-2), 300)`
   - 成功登录后重置计数

3. **实现审计日志服务**
   - `LoginAuditService.java`
   - 记录所有登录尝试（成功/失败）
   - 异步写入数据库（避免影响性能）
   - 提供查询接口用于安全分析

4. **实现认证服务**
   - `AuthenticationService.java`
   - 挑战码生成和验证（内存存储 ConcurrentHashMap）
   - ClientProof 验证（SCRAM机制）
   - Token 生成和管理
   - 集成防暴力破解检查
   - 集成审计日志记录

5. **实现认证控制器**
   - `AuthController.java`
   - `POST /api/auth/challenge` - 获取挑战码和Salt
   - `POST /api/auth/login` - 验证ClientProof并返回Token
     - 登录前检查IP和账户限流
     - 登录失败时记录审计日志
     - 返回锁定信息（如果账户被锁定）
   - `POST /api/auth/logout` - 登出
   - `GET /api/auth/verify` - 验证Token

6. **修改Token过滤器**
   - 支持Token验证
   - 白名单：`/api/auth/**`, `/actuator/**`
   - 提取客户端IP（考虑代理头 X-Forwarded-For）

7. **定时任务**
   - 清理过期挑战码（从内存Map删除，每分钟执行）
   - 清理过期Token（从数据库删除，每小时执行）
   - 清理过期审计日志（保留90天，每天执行）

### Phase 3: 前端实现（2天）

1. **安装依赖**
   ```bash
   npm install crypto-js
   npm install --save-dev @types/crypto-js
   ```

2. **实现 SCRAM 工具类**
   - `scram.ts` - PBKDF2、HMAC、XOR 工具函数
   - ClientProof 计算逻辑

3. **创建登录页面（增强版）**
   - `Login.vue` - 用户名/密码输入表单
   - 集成 crypto-js 进行 SCRAM-SHA-256 计算
   - **防暴力破解UI**：
     - 显示账户锁定倒计时（如果被锁定）
     - 显示友好的错误提示（不泄露用户是否存在）
     - 禁用登录按钮（当账户被锁定时）

4. **实现认证逻辑**
   - `auth.ts` - 认证API客户端
     - 处理 `ACCOUNT_LOCKED` 错误（显示重试时间）
     - 处理 `TOO_MANY_REQUESTS` 错误（提示稍后重试）
   - `useAuth.ts` - 认证状态管理（Composition API）
     - 登录状态
     - 失败次数计数（本地显示，仅用于UI提示）
     - 锁定倒计时

5. **路由守卫**
   - 未登录重定向到登录页
   - 登录后重定向到Dashboard

6. **全局请求拦截器**
   - 自动添加 `Authorization: Bearer <token>` Header
   - Token过期处理（401响应 → 重定向登录）
   - 429响应处理（显示限流提示）

### Phase 4: 初始化和测试（1天）

1. **数据库初始化脚本**
   - 创建 `users` 表（SCRAM字段）
   - 创建 `auth_tokens` 表
   - 创建 `login_audit_log` 表
   - 创建默认管理员账户（使用SCRAM计算StoredKey）
     - 用户名: `admin`
     - 默认密码: `Admin@123`

2. **功能测试**
   - 测试完整SCRAM登录流程
   - 测试挑战码过期处理
   - 测试重放攻击防护
   - 测试Token生成和验证

3. **防暴力破解测试**
   - 测试IP级别限流（连续失败20次）
   - 测试账户级别限流（连续失败10次）
   - 测试指数退避锁定机制（失败3/4/5次的锁定时长）
   - 测试成功登录后计数重置
   - 测试审计日志记录

4. **安全性测试**
   - 验证密码无法反推（即使知道StoredKey）
   - 验证挑战码单次使用
   - 验证Token有效性和过期
   - 验证不泄露用户是否存在
   - 验证无法通过DoS攻击锁定账户（指数退避上限）

5. **性能测试**
   - 测试PBKDF2计算时间（4096次迭代，应<100ms）
   - 测试内存存储挑战码性能
   - 测试Caffeine Cache限流性能
   - 测试异步审计日志写入性能

---

## 🛠️ 技术栈

### 后端
- **SCRAM 算法**:
  - PBKDF2: `javax.crypto.SecretKeyFactory` (PBKDF2WithHmacSHA256)
  - HMAC-SHA256: `javax.crypto.Mac` (HmacSHA256)
  - SHA-256: `java.security.MessageDigest`
  - 编解码: Apache Commons Codec (Hex)
- **UUID 生成**: `java.util.UUID`
- **内存存储**:
  - 挑战码: `java.util.concurrent.ConcurrentHashMap`
  - 限流缓存: **Caffeine Cache** (高性能、带过期时间)
- **定时任务**: Spring `@Scheduled`
- **异步处理**: Spring `@Async` (审计日志异步写入)
- **依赖库**:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `commons-codec` (Hex编解码)
  - `com.github.ben-manes.caffeine:caffeine` (限流缓存)
  - `spring-boot-starter-security` (可选，用于密码编码器等工具)

**Maven依赖示例**:
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
</dependency>
```

### 前端
- **SCRAM 计算**: `crypto-js` (PBKDF2, HMAC-SHA256, SHA-256)
- **状态管理**: Vue Composition API
- **HTTP 客户端**: Axios 拦截器
- **依赖库**:
  - `crypto-js` (^4.2.0)
  - `@types/crypto-js` (开发依赖)

**NPM依赖示例**:
```json
{
  "dependencies": {
    "crypto-js": "^4.2.0"
  },
  "devDependencies": {
    "@types/crypto-js": "^4.2.1"
  }
}
```

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

## ⚙️ 配置参数

### 认证配置（application.yml）

```yaml
auth:
  # SCRAM 配置
  scram:
    iterations: 4096  # PBKDF2迭代次数（推荐4096-10000）
    salt-length: 16   # Salt长度（字节）

  # 挑战码配置
  challenge:
    ttl: 30  # 有效期（秒）
    cleanup-interval: 60000  # 清理间隔（毫秒）

  # Token配置
  token:
    ttl: 7  # 有效期（天）
    cleanup-interval: 3600000  # 清理间隔（毫秒，1小时）

  # 防暴力破解配置
  brute-force-protection:
    enabled: true

    # IP级别限流
    ip-rate-limit:
      window: 600  # 时间窗口（秒，10分钟）
      max-attempts: 20  # 最大失败次数

    # 账户级别限流
    account-rate-limit:
      window: 600  # 时间窗口（秒，10分钟）
      max-attempts: 10  # 最大失败次数

    # 指数退避锁定
    exponential-backoff:
      enabled: true
      start-after: 3  # 失败N次后开始锁定
      max-lockout: 300  # 最大锁定时长（秒，5分钟）

    # 审计日志
    audit-log:
      enabled: true
      async: true  # 异步写入
      retention-days: 90  # 保留天数
```

### 配置说明

| 配置项 | 默认值 | 说明 | 建议 |
|--------|-------|------|------|
| `scram.iterations` | 4096 | PBKDF2迭代次数 | 开发环境可降低到1024提升速度，生产环境4096-10000 |
| `challenge.ttl` | 30秒 | 挑战码有效期 | 生产环境保持30秒，开发环境可延长到300秒 |
| `token.ttl` | 7天 | Token有效期 | 根据业务需求调整，内部系统可延长到30天 |
| `ip-rate-limit.max-attempts` | 20 | IP失败次数限制 | 根据用户规模调整，大型系统可适当提高 |
| `account-rate-limit.max-attempts` | 10 | 账户失败次数限制 | 建议保持较低值，防止暴力破解 |
| `exponential-backoff.max-lockout` | 300秒 | 最大锁定时长 | 避免DoS攻击，不建议超过600秒 |

### 环境变量覆盖

```bash
# 开发环境 - 宽松配置
export AUTH_SCRAM_ITERATIONS=1024
export AUTH_CHALLENGE_TTL=300
export AUTH_BRUTE_FORCE_PROTECTION_ENABLED=false

# 生产环境 - 严格配置
export AUTH_SCRAM_ITERATIONS=4096
export AUTH_CHALLENGE_TTL=30
export AUTH_BRUTE_FORCE_PROTECTION_ENABLED=true
export AUTH_IP_RATE_LIMIT_MAX_ATTEMPTS=15
export AUTH_ACCOUNT_RATE_LIMIT_MAX_ATTEMPTS=5
```

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

### SCRAM 标准文档
- [RFC 5802 - Salted Challenge Response Authentication Mechanism (SCRAM)](https://datatracker.ietf.org/doc/html/rfc5802)
- [RFC 7677 - SCRAM-SHA-256 and SCRAM-SHA-256-PLUS](https://datatracker.ietf.org/doc/html/rfc7677)
- [RFC 2898 - PBKDF2](https://datatracker.ietf.org/doc/html/rfc2898)

### 业界实现参考
- [PostgreSQL SCRAM-SHA-256 Authentication](https://www.postgresql.org/docs/current/sasl-authentication.html)
- [MongoDB SCRAM Authentication](https://www.mongodb.com/docs/manual/core/security-scram/)
- [CockroachDB SASL/SCRAM](https://www.cockroachlabs.com/docs/stable/security-reference/scram-authentication)

### 安全最佳实践
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

### 技术规范
- [RFC 4122 - UUID](https://tools.ietf.org/html/rfc4122)
- [FIPS 180-4 - SHA-2 (包括SHA-256)](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.180-4.pdf)
- [HMAC - Keyed-Hashing for Message Authentication](https://tools.ietf.org/html/rfc2104)

---

**文档版本**: v2.0 (基于 SCRAM-SHA-256)
**创建日期**: 2025-12-25
**最后更新**: 2025-12-25
**更新说明**:
- ✅ 采用 SCRAM-SHA-256 标准认证机制
- ✅ 使用 PBKDF2 密钥派生（4096次迭代）
- ✅ 挑战码改为内存存储（ConcurrentHashMap）
- ✅ 修正 Hash 验证逻辑，确保前后端计算一致
- ✅ 参考 PostgreSQL、MongoDB 等数据库的实现
**作者**: GitLab Mirror Team
