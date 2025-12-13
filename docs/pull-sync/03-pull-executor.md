# 模块 3: Pull 同步执行器 (Pull Sync Executor)

**状态**: ✅ 已完成 (Completed)

**目标**: 实现 Pull 同步的核心执行逻辑，包括 Git 命令封装和同步流程。

**预计时间**: 3-4天

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

### T3.1 Git 命令执行器
**状态**: ✅ 已完成
**依赖**: 模块1 - 数据模型扩展

**任务目标**:
- 创建 `GitCommandExecutor` 工具类
- 封装 Git 命令执行（clone, remote update, push, ls-remote）
- 实现进度捕获和超时控制
- 实现错误分类和重试逻辑
  - 参考 [PULL_SYNC_DESIGN.md - 错误处理与重试](../PULL_SYNC_DESIGN.md#流程-6-错误处理与重试)

**核心方法**:
```java
public class GitCommandExecutor {
    // git clone --mirror
    GitCloneResult cloneMirror(String sourceUrl, String localPath, String token);

    // git ls-remote
    String getRemoteHeadSha(String remoteUrl, String token);

    // git remote update --prune
    GitUpdateResult updateMirror(String localPath);

    // git push --mirror
    GitPushResult pushMirror(String localPath, String targetUrl, String token);

    // git fsck --quick
    boolean verifyRepository(String localPath);

    // 获取本地 HEAD SHA
    String getLocalHeadSha(String localPath);
}
```

**验收标准**:
- 所有 Git 命令正确执行
- 进度输出正确捕获
- 超时控制生效（30秒超时）
- 错误信息正确解析和分类
- 认证 token 正确处理（URL 中嵌入）

**测试要求**:
- 测试每个 Git 命令执行
- 测试超时控制
- 测试错误场景（网络错误、认证失败、冲突等）
- 测试进度捕获
- 测试大仓库处理

**提交**: `feat(pull): add GitCommandExecutor`

---

### T3.2 Pull 同步服务 - 首次同步
**状态**: ✅ 已完成
**依赖**: T3.1, 模块2 - 项目发现扩展

**任务目标**:
- 创建 `PullSyncExecutor` 服务类
- 实现首次同步逻辑（全量克隆）
- 目标项目自动创建
- 状态更新（pending → running → waiting）
  - 参考 [PULL_SYNC_DESIGN.md - 首次同步流程](../PULL_SYNC_DESIGN.md#流程-3-任务执行首次同步)

**核心方法**:
```java
public class PullSyncExecutor {
    // 执行首次同步
    void executeFirstSync(SyncTask task) {
        // 1. 更新状态: pending → running
        // 2. 检查/创建目标项目
        // 3. 检查磁盘空间
        // 4. git clone --mirror 源仓库
        // 5. git push --mirror 到目标
        // 6. 更新任务状态和结果
        // 7. 更新状态: running → waiting
        // 8. 计算 next_run_at
    }
}
```

**验收标准**:
- 首次同步成功完成
- 目标项目自动创建（包括分组）
- 本地仓库正确克隆
- 推送到目标成功
- 任务状态正确更新
- 磁盘空间检查生效

**测试要求**:
- 测试完整的首次同步流程
- 测试目标项目创建
- 测试磁盘空间不足场景
- 测试克隆失败场景
- 测试推送失败场景
- 测试事务回滚

**提交**: `feat(pull): implement first sync logic`

---

### T3.3 Pull 同步服务 - 增量同步
**状态**: ✅ 已完成
**依赖**: T3.2

**任务目标**:
- 实现增量同步逻辑
- git ls-remote 变更检测优化
- 无变更快速跳过
- 状态和结果更新
  - 参考 [PULL_SYNC_DESIGN.md - 增量同步流程](../PULL_SYNC_DESIGN.md#流程-4-任务执行增量同步)

**核心方法**:
```java
public class PullSyncExecutor {
    // 执行增量同步
    void executeIncrementalSync(SyncTask task) {
        // 1. 更新状态: pending → running
        // 2. 检查/创建目标项目
        // 3. 检查本地仓库是否存在
        // 4. git ls-remote 获取源 HEAD SHA
        // 5. 对比 SHA，无变更则跳过
        // 6. 有变更: git remote update + git push
        // 7. 更新任务状态和结果
        // 8. 更新状态: running → waiting
    }
}
```

**验收标准**:
- 变更检测正确（git ls-remote）
- 无变更项目快速跳过（<1秒）
- 有变更项目正确同步
- SHA 对比准确
- has_changes 字段正确更新
- 性能达标（70-90% 无变更跳过）

**测试要求**:
- 测试无变更场景（跳过）
- 测试有变更场景（同步）
- 测试性能（无变更 <1秒）
- 测试 SHA 对比逻辑
- 测试本地仓库不存在降级为首次同步

**提交**: `feat(pull): implement incremental sync with optimization`

---

### T3.4 错误处理和重试逻辑
**状态**: ✅ 已完成
**依赖**: T3.2, T3.3

**任务目标**:
- 实现错误分类（可重试/不可重试）
- 实现指数退避重试策略
- 实现连续失败自动禁用
- 错误信息详细记录

**错误分类**:
```java
public enum ErrorType {
    // 可重试
    NETWORK_TIMEOUT,
    DISK_FULL,
    CONFLICT,

    // 不可重试
    AUTH_FAILED,
    REPO_NOT_FOUND,
    UNKNOWN
}
```

**重试策略**:
```java
// 指数退避: delay = 5min × 2^retry_count
// 第1次失败: 5分钟后重试
// 第2次失败: 10分钟后重试
// 第3次失败: 20分钟后重试
// 第4次失败: 40分钟后重试
// 第5次失败: 80分钟后重试
// ≥5次失败: 自动禁用
```

**验收标准**:
- 错误正确分类
- 可重试错误自动重试
- 不可重试错误立即禁用
- 重试延迟时间正确
- 连续失败≥5次自动禁用
- 成功后 consecutive_failures 清零

**测试要求**:
- 测试各种错误类型分类
- 测试重试延迟计算
- 测试自动禁用逻辑
- 测试成功后重置失败计数
- 测试错误信息记录

**提交**: `feat(pull): add error handling and retry logic`

---

### T3.5 磁盘管理和清理
**状态**: ✅ 已完成
**依赖**: T3.3

**任务目标**:
- 实现磁盘空间检查
- 实现本地仓库清理（gc）
- 实现磁盘使用统计
- 实现仓库删除功能

**核心方法**:
```java
public class DiskManagementService {
    // 检查可用空间
    boolean checkAvailableSpace(long requiredBytes);

    // 清理仓库（git gc）
    long cleanupRepository(String localPath);

    // 删除仓库
    void deleteRepository(String localPath);

    // 计算磁盘使用
    DiskUsageStats calculateDiskUsage();

    // 获取仓库大小
    long getRepositorySize(String localPath);
}
```

**验收标准**:
- 磁盘空间检查准确
- git gc 正确执行并释放空间
- 仓库删除完整（包括目录）
- 磁盘使用统计准确
- 基于 SOURCE_PROJECT_INFO.repository_size 估算

**测试要求**:
- 测试磁盘空间检查
- 测试 git gc 执行和效果
- 测试仓库删除
- 测试磁盘使用统计
- 测试大仓库处理

**提交**: `feat(pull): add disk management service`

---

## 模块输出

- ✅ GitCommandExecutor 工具类
- ✅ PullSyncExecutor 服务类
- ✅ 首次同步和增量同步逻辑
- ✅ 变更检测优化（git ls-remote）
- ✅ 错误处理和重试机制
- ✅ 磁盘管理服务

---

## 关键决策

1. **Git 命令封装**: 使用 ProcessBuilder 执行 Git 命令，捕获输出和错误
2. **变更检测**: git ls-remote 快速检查，避免无意义的 fetch
3. **错误分类**: 基于 Git 错误输出特征分类（stderr 匹配）
4. **重试策略**: 指数退避，避免频繁重试
5. **磁盘估算**: 基于 repository_size 估算，实际占用可能略大

---

## 性能目标

| 场景 | 目标耗时 | 说明 |
|-----|---------|------|
| 无变更检测 | <1秒 | git ls-remote |
| 小变更同步 | 2-5秒 | 1-10 commits |
| 中变更同步 | 5-15秒 | 10-100 commits |
| 大变更同步 | 15-60秒 | >100 commits |
| 首次同步 | 视仓库大小 | 小:<1分钟, 中:1-3分钟, 大:3-10分钟 |

---

## 注意事项

1. **并发安全**: 确保同一项目不会并发执行同步
2. **超时控制**: Git 命令需要设置超时，避免长时间挂起
3. **认证处理**: Token 嵌入 URL 中，注意日志脱敏
4. **磁盘空间**: 同步前检查空间，避免磁盘满导致失败
5. **本地仓库**: 首次克隆失败需清理残留目录
6. **目标项目**: 每次同步前检查目标项目是否存在
