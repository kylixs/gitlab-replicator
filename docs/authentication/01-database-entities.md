# 模块 1: 数据库和实体 (Database & Entities)

**状态**: ⏸️ 待处理 (Pending)

**目标**: 创建SCRAM-SHA-256认证所需的数据库表和MyBatis-Plus实体。

**预计时间**: 1天

---

## 参考文档

- [认证系统设计文档](../authentication-design.md)
  - [数据模型](../authentication-design.md#3-数据模型)
  - [密码存储方案](../authentication-design.md#2-密码存储方案)

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

### T1.1 创建数据库迁移脚本
**状态**: ⏸️ 待处理 (Pending)
**依赖**: 无

**任务目标**:
创建3张认证相关数据库表

**SQL脚本路径**: `sql/migrations/00X_create_auth_tables.sql`

**表结构设计**:

1. **users 表** - 用户表
   - `id` BIGINT PK 自增
   - `username` VARCHAR(50) UK 用户名
   - `stored_key` VARCHAR(64) SCRAM存储密钥
   - `salt` VARCHAR(32) 盐值（16字节十六进制）
   - `iterations` INT PBKDF2迭代次数（默认4096）
   - `display_name` VARCHAR(100) 显示名称
   - `enabled` TINYINT(1) 是否启用
   - `created_at` TIMESTAMP
   - `updated_at` TIMESTAMP
   - 索引：`idx_username`

2. **auth_tokens 表** - 认证Token表
   - `id` BIGINT PK 自增
   - `token` VARCHAR(64) UK Token值
   - `user_id` BIGINT FK 用户ID
   - `created_at` TIMESTAMP
   - `expires_at` TIMESTAMP 过期时间
   - `last_used_at` TIMESTAMP 最后使用时间
   - 外键：`user_id` → `users.id` ON DELETE CASCADE
   - 索引：`idx_token`, `idx_user_id`, `idx_expires_at`

3. **login_audit_log 表** - 登录审计日志
   - `id` BIGINT PK 自增
   - `username` VARCHAR(50)
   - `ip_address` VARCHAR(45)
   - `user_agent` TEXT
   - `login_result` ENUM('SUCCESS', 'FAILURE', 'LOCKED', 'RATE_LIMITED')
   - `failure_reason` VARCHAR(100)
   - `created_at` TIMESTAMP
   - 索引：`idx_username`, `idx_ip`, `idx_created_at`

**关键点**:
- MySQL 8.0+兼容
- 使用TIMESTAMP存储时间
- 适当的索引优化查询性能

**验收标准**:
- SQL脚本无语法错误
- 可在开发环境成功执行
- 所有表、索引、外键创建成功
- 验证表结构

**提交**: `feat(auth): add database migration for authentication tables`

---

### T1.2 创建MyBatis-Plus实体类
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T1.1

**任务目标**:
创建对应数据库表的实体类和内存数据类

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/entity/User.java`
- `server/src/main/java/com/gitlab/mirror/server/entity/AuthToken.java`
- `server/src/main/java/com/gitlab/mirror/server/entity/LoginAuditLog.java`
- `server/src/main/java/com/gitlab/mirror/server/service/auth/model/ChallengeInfo.java`

**实体设计要点**:

1. **User 实体**
   - 使用 `@TableName("users")`
   - 使用 `@TableId(type = IdType.AUTO)`
   - 使用 `@TableField` 映射字段
   - 关键字段：`storedKey`, `salt`, `iterations`

2. **AuthToken 实体**
   - Token格式：UUID v4
   - 关联 `user_id` 字段
   - 自动更新 `last_used_at`

3. **LoginAuditLog 实体**
   - ENUM类型字段处理
   - 索引字段：`username`, `ip_address`

4. **ChallengeInfo 内存数据类**
   - 非数据库实体（无@TableName）
   - 用于内存存储挑战码
   - 字段：`username`, `createdAt`, `expiresAt`, `used`
   - 使用 `@Data` 和 `@Builder`

**关键点**:
- 使用Lombok减少样板代码
- 字段命名遵循驼峰命名
- 使用 `@TableField(fill = FieldFill.INSERT)` 自动填充时间

**验收标准**:
- 编译通过
- 字段映射正确
- Spring Boot启动无错误

**提交**: `feat(auth): add MyBatis-Plus entities for authentication`

---

### T1.3 创建Mapper接口
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T1.2

**任务目标**:
创建MyBatis-Plus Mapper接口

**文件路径**:
- `server/src/main/java/com/gitlab/mirror/server/mapper/UserMapper.java`
- `server/src/main/java/com/gitlab/mirror/server/mapper/AuthTokenMapper.java`
- `server/src/main/java/com/gitlab/mirror/server/mapper/LoginAuditLogMapper.java`

**Mapper设计**:

1. **UserMapper**
   - 继承 `BaseMapper<User>`
   - 方法：`selectByUsername(String username)`

2. **AuthTokenMapper**
   - 继承 `BaseMapper<AuthToken>`
   - 方法：
     - `selectByToken(String token)`
     - `deleteExpiredTokens(Instant now)` - 删除过期Token
     - `updateLastUsedAt(String token, Instant now)` - 更新最后使用时间

3. **LoginAuditLogMapper**
   - 继承 `BaseMapper<LoginAuditLog>`
   - 方法：
     - `selectByUsername(String username, int limit)`
     - `selectByIpAddress(String ip, int limit)`
     - `deleteOldRecords(Instant before)` - 清理旧记录

**关键点**:
- 使用 `@Mapper` 注解
- 复杂查询使用 `@Select` 注解或XML映射文件
- 删除/更新操作使用 `@Update` 或 `@Delete` 注解

**验收标准**:
- 所有Mapper方法正确
- 可执行基本CRUD操作
- 自定义查询正常工作
- 编写并通过单元测试验证所有Mapper方法, 验证实体映射正确

**提交**: `feat(auth): add MyBatis-Plus mappers for authentication`

---

### T1.4 创建默认管理员初始化
**状态**: ⏸️ 待处理 (Pending)
**依赖**: T1.1, T1.2, T1.3

**任务目标**:
在Spring Boot启动时创建默认管理员账户

**文件路径**: `server/src/main/java/com/gitlab/mirror/server/config/DatabaseInitializer.java`

**核心逻辑**:
1. 实现 `CommandLineRunner` 接口
2. 检查 `admin` 用户是否存在
3. 如果不存在，使用SCRAM算法创建：
   - 生成随机Salt（16字节）
   - 使用PBKDF2计算SaltedPassword
   - 计算ClientKey和StoredKey
   - 插入数据库

**默认账户**:
- 用户名: `admin`
- 密码: `Admin@123`
- 显示名称: `Administrator`
- 状态: 启用

**关键点**:
- 使用 `ScramUtils` 工具类计算StoredKey
- 幂等性：重启不会重复创建
- 记录日志提醒修改默认密码

**验收标准**:
- 首次启动自动创建admin用户
- StoredKey计算正确
- 可以使用默认密码登录
- 编写并通过单元测试验证初始化逻辑

**提交**: `feat(auth): add default admin user initialization`

---

## 模块验收

**验收检查项**:
1. 数据库表结构正确，索引生效
2. 实体类与数据库表映射正确
3. Mapper接口可执行CRUD操作
4. 默认admin用户创建成功

**完成标志**: 所有任务状态为 ✅，模块状态更新为 ✅ 已完成
