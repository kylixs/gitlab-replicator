# 模块 4: 前端实现 (Frontend Implementation)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 实现Vue3前端登录页面、SCRAM客户端计算和认证状态管理。

**预计时间**: 2天

---

## ⚠️ 重要提醒：任务状态管理规范

**【必须】在开始处理下面的每个子任务前及后需要修改其任务状态：**

1. **开始任务前**：将任务状态从 `⏸️ 待处理 (Pending)` 修改为 `🔄 进行中 (In Progress)`
2. **完成任务后**：将任务状态修改为 `✅ 已完成 (Completed)` 或 `❌ 失败 (Failed)`
3. **更新位置**：在本文档对应任务的 `**状态**:` 行进行修改

**状态标记说明**:
- `⏸️ 待处理 (Pending)` - 任务未开始
- `🔄 进行中 (In Progress)` - 任务正在处理中
- `✅ 已完成 (Completed)` - 任务成功完成，测试通过
- `❌ 失败 (Failed)` - 任务失败，需要修复
- `⚠️ 阻塞 (Blocked)` - 任务被依赖阻塞

---

## 任务清单

### T4.1 安装依赖和类型定义
**状态**: ⏸️ 待处理 (Pending)
**依赖**: 无

**任务目标**:
安装crypto-js和TypeScript类型定义

**执行命令**:
```bash
cd web-ui
npm install crypto-js
npm install --save-dev @types/crypto-js
```

**关键点**:
- crypto-js用于前端SCRAM算法计算
- 类型定义提供TypeScript支持

**验收标准**:
- 依赖安装成功
- TypeScript无类型错误

**提交**: `build(web-ui): add crypto-js dependency for authentication`

---

### T4.2 SCRAM工具类实现
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.1

**任务目标**:
实现前端SCRAM-SHA-256计算工具类

**文件路径**: `web-ui/src/utils/scram.ts`

**核心函数**:

1. **pbkdf2(password, saltHex, iterations)** - PBKDF2密钥派生
   - 使用 `CryptoJS.PBKDF2`
   - 参数：密码、盐值（十六进制）、迭代次数
   - 返回：WordArray (256位)

2. **calculateClientProof(username, password, challenge, saltHex, iterations)**
   - 步骤：
     1. SaltedPassword = PBKDF2(password, salt, iterations)
     2. ClientKey = HMAC-SHA256(SaltedPassword, "Client Key")
     3. StoredKey = SHA256(ClientKey)
     4. AuthMessage = username + ":" + challenge
     5. ClientSignature = HMAC-SHA256(StoredKey, AuthMessage)
     6. ClientProof = XOR(ClientKey, ClientSignature)
   - 返回：ClientProof（十六进制字符串）

3. **xor(a, b)** - 字节数组异或
   - 参数：两个WordArray
   - 返回：XOR结果（十六进制）

**关键点**:
- 使用 `CryptoJS.PBKDF2`, `CryptoJS.HmacSHA256`, `CryptoJS.SHA256`
- 正确处理Hex编解码
- XOR操作按字节进行

**验收标准**:
- SCRAM算法计算正确
- 与后端验证一致
- 单元测试通过

**提交**: `feat(web-ui): add SCRAM-SHA-256 client utility`

---

### T4.3 认证API客户端
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.2

**任务目标**:
创建认证API客户端封装

**文件路径**: `web-ui/src/api/auth.ts`

**API方法列表**:

1. **getChallenge(username)** - 获取挑战码
   - POST `/api/auth/challenge`
   - 返回：`{ challenge, salt, iterations, expiresAt }`

2. **login(username, password)** - 登录
   - 内部流程：
     1. 调用getChallenge获取挑战码和Salt
     2. 使用SCRAM工具计算ClientProof
     3. POST `/api/auth/login` 提交
   - 返回：`{ token, expiresAt, user }`
   - 自动存储Token到localStorage

3. **logout()** - 登出
   - POST `/api/auth/logout`
   - 清除localStorage中的Token

4. **verifyToken()** - 验证Token
   - GET `/api/auth/verify`
   - 返回：`{ valid, expiresAt, user }`

**错误处理**:
- 捕获HTTP错误
- 解析错误代码（ACCOUNT_LOCKED, INVALID_CREDENTIALS等）
- 返回友好错误消息

**关键点**:
- 使用Axios实例
- 统一错误处理
- Token自动管理（localStorage）

**验收标准**:
- 所有API方法正确
- 错误处理完善
- Token自动存储和清理

**提交**: `feat(web-ui): add authentication API client`

---

### T4.4 认证状态管理
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.3

**任务目标**:
使用Vue Composition API实现认证状态管理

**文件路径**: `web-ui/src/composables/useAuth.ts`

**状态管理**:
- `isAuthenticated` - 是否已登录（ref）
- `currentUser` - 当前用户信息（ref）
- `failureCount` - 失败次数（本地计数，仅UI提示）
- `lockoutSeconds` - 锁定剩余时间（ref）

**核心方法**:
1. **login(username, password)** - 登录
   - 调用API登录
   - 成功：设置isAuthenticated=true，存储用户信息
   - 失败：增加failureCount，显示错误

2. **logout()** - 登出
   - 调用API登出
   - 清除本地状态

3. **checkAuth()** - 检查登录状态
   - 检查localStorage中的Token
   - 验证Token是否有效
   - 初始化时调用

4. **startLockoutCountdown(seconds)** - 启动锁定倒计时
   - 使用setInterval每秒递减
   - 倒计时结束后自动清除

**关键点**:
- 使用 `ref` 和 `reactive` 管理状态
- 使用 `computed` 计算派生状态
- localStorage持久化Token

**验收标准**:
- 登录状态正确管理
- 用户信息正确存储
- 锁定倒计时正常工作

**提交**: `feat(web-ui): add authentication state management`

---

### T4.5 创建登录页面
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.4

**任务目标**:
创建登录页面UI组件

**文件路径**: `web-ui/src/views/Login.vue`

**页面功能**:
1. **表单字段**:
   - 用户名输入框
   - 密码输入框（隐藏显示）
   - 登录按钮

2. **状态显示**:
   - 账户锁定提示（显示倒计时）
   - 失败次数提示（失败3次后）
   - 错误消息提示

3. **交互逻辑**:
   - 提交表单调用`useAuth().login()`
   - 锁定时禁用登录按钮
   - 显示loading状态

4. **样式设计**:
   - 居中布局
   - 响应式设计
   - 使用Element Plus组件

**关键点**:
- 使用 `<script setup>` 语法
- 使用 `useAuth` composable
- 表单验证（非空、长度）
- 锁定倒计时UI反馈

**验收标准**:
- 登录功能正常
- 锁定提示正确显示
- UI友好美观

**提交**: `feat(web-ui): add login page component`

---

### T4.6 路由和守卫配置
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.5

**任务目标**:
配置Vue Router路由和认证守卫

**文件路径**: `web-ui/src/router/index.ts`

**路由配置**:
1. 添加登录路由：
   - 路径：`/login`
   - 组件：`Login.vue`
   - 元信息：`{ requiresAuth: false }`

2. 修改现有路由元信息：
   - Dashboard, Projects等：`{ requiresAuth: true }`

**路由守卫**:
```typescript
router.beforeEach(async (to, from, next) => {
  const { isAuthenticated, checkAuth } = useAuth()

  // 检查登录状态
  if (!isAuthenticated.value) {
    await checkAuth()
  }

  // 需要认证的路由
  if (to.meta.requiresAuth && !isAuthenticated.value) {
    next('/login')
  }
  // 已登录访问登录页，重定向到首页
  else if (to.path === '/login' && isAuthenticated.value) {
    next('/')
  }
  // 其他情况正常放行
  else {
    next()
  }
})
```

**关键点**:
- 使用 `beforeEach` 全局守卫
- 检查 `requiresAuth` 元信息
- 未登录重定向到登录页
- 已登录无法访问登录页

**验收标准**:
- 未登录访问受保护页面自动跳转登录
- 登录后自动跳转首页
- 登出后回到登录页

**提交**: `feat(web-ui): add authentication route guard`

---

### T4.7 Axios拦截器配置
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T4.4

**任务目标**:
配置Axios请求/响应拦截器自动处理Token

**文件路径**: `web-ui/src/api/client.ts`

**请求拦截器**:
```typescript
client.interceptors.request.use(config => {
  const token = localStorage.getItem('auth_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

**响应拦截器**:
```typescript
client.interceptors.response.use(
  response => response,
  error => {
    // 401 Token过期或无效
    if (error.response?.status === 401) {
      const { logout } = useAuth()
      logout()
      router.push('/login')
    }
    // 429 限流
    else if (error.response?.status === 429) {
      ElMessage.error('请求过于频繁，请稍后重试')
    }
    return Promise.reject(error)
  }
)
```

**关键点**:
- 自动添加Authorization头
- 401自动登出并跳转登录页
- 429显示限流提示
- 其他错误正常抛出

**验收标准**:
- Token自动附加到请求
- 401自动登出
- 429显示提示

**提交**: `feat(web-ui): add axios interceptors for authentication`

---

## 模块验收

**验收检查项**:
1. SCRAM客户端算法计算正确
2. 登录页面功能完整，UI美观
3. 路由守卫正确保护受保护页面
4. Token自动管理和过期处理
5. 账户锁定UI反馈正确
6. 端到端登录流程测试通过

**完成标志**: 所有任务状态为 ✅，模块状态更新为 ✅ 已完成
