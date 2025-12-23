# Pull Sync 新增分支不同步问题分析

## 问题描述

**同步方式**: Pull Sync
**现象**: 源 GitLab 添加新分支后，目标 GitLab 没有同步这些新分支
**影响范围**: 增量同步 (sync-incremental)

## 根本原因分析

### 当前实现

在 `server/src/main/resources/scripts/git-sync.sh` 的 `sync-incremental` 命令中：

```bash
sync-incremental)
    # ...
    cd "$LOCAL_PATH"

    # Update from source
    git remote update --prune    # 第84行

    # Set push URL
    git remote set-url --push origin "$TARGET_URL"

    # Push to target
    git push --mirror            # 第92行

    # ...
```

### 问题所在

**核心问题**: `git remote update --prune` 命令存在局限性

1. **命令行为**:
   - `git remote update` 会更新所有已配置的 remote
   - 对于 mirror 仓库，理论上应该获取所有远程分支
   - 但在某些 git 版本或配置下，可能不会正确跟踪新添加的分支

2. **与 sync-first 的对比**:
   ```bash
   sync-first)
       # Clone with --mirror (creates proper mirror config)
       git clone --mirror "$SOURCE_URL" "$LOCAL_PATH"

       # Push to target
       git push --mirror
   ```

   首次同步使用 `--mirror` 模式克隆，配置正确，所有分支都会被跟踪。

3. **潜在配置问题**:
   - Mirror 仓库需要正确的 `remote.<name>.fetch` 配置
   - 正确配置: `+refs/*:refs/*` (镜像所有引用)
   - 错误配置: `+refs/heads/*:refs/remotes/origin/*` (普通 fetch 配置)

## 验证步骤

### 1. 检查本地仓库配置

```bash
# 进入本地镜像仓库
cd ~/.gitlab-sync/repos/ai/test-android-app-3

# 查看 git 配置
git config --list | grep remote

# 期望看到:
# remote.origin.url=http://...
# remote.origin.fetch=+refs/*:refs/*
# remote.origin.mirror=true

# 如果看到的是:
# remote.origin.fetch=+refs/heads/*:refs/remotes/origin/*
# 那就是配置错误，不是真正的 mirror
```

### 2. 手动测试更新

```bash
cd ~/.gitlab-sync/repos/ai/test-android-app-3

# 测试 remote update
git remote update --prune

# 查看所有分支
git branch -a

# 测试 fetch
git fetch --all --prune

# 再次查看分支
git branch -a
```

### 3. 比对源和目标分支

```bash
# 列出源 GitLab 分支
curl -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" \
  "http://localhost:8000/api/v4/projects/ai%2Ftest-android-app-3/repository/branches" \
  | jq '.[].name'

# 列出目标 GitLab 分支
curl -H "PRIVATE-TOKEN: $TARGET_GITLAB_TOKEN" \
  "http://localhost:9000/api/v4/projects/ai%2Ftest-android-app-3/repository/branches" \
  | jq '.[].name'
```

## 解决方案

### 方案 1: 改进 sync-incremental 命令 (推荐)

修改 `git-sync.sh` 中的 `sync-incremental` 部分：

```bash
sync-incremental)
    SOURCE_URL="$1"
    TARGET_URL="$2"
    LOCAL_PATH="$3"

    cd "$LOCAL_PATH"

    log "Updating from source: $(mask_url "$SOURCE_URL")"

    # 方案 1a: 使用 fetch --mirror 而不是 remote update
    git fetch --mirror

    # 或者
    # 方案 1b: 先确保配置正确，再更新
    # git config remote.origin.mirror true
    # git config remote.origin.fetch '+refs/*:refs/*'
    # git remote update --prune

    log "Pushing to target: $(mask_url "$TARGET_URL")"

    # Set push URL
    git remote set-url --push origin "$TARGET_URL"

    # Push to target
    git push --mirror

    # Get final SHA
    FINAL_SHA=$(git rev-parse HEAD)
    echo "FINAL_SHA=$FINAL_SHA"

    log "Sync completed successfully"
    ;;
```

**关键变更**:
- 将 `git remote update --prune` 替换为 `git fetch --mirror`
- `git fetch --mirror` 会强制以镜像模式获取，确保所有远程引用都被同步

### 方案 2: 在首次同步后验证配置

在 `executeFirstSync()` 方法中，克隆后验证配置：

```java
// After clone-mirror, verify config
GitCommandExecutor.GitResult verifyResult = gitCommandExecutor.execute(
    "git config --get remote.origin.mirror",
    localRepoPath
);

if (!"true".equals(verifyResult.getOutput().trim())) {
    log.warn("Mirror config not set correctly, fixing...");
    gitCommandExecutor.execute(
        "git config remote.origin.mirror true",
        localRepoPath
    );
    gitCommandExecutor.execute(
        "git config remote.origin.fetch '+refs/*:refs/*'",
        localRepoPath
    );
}
```

### 方案 3: 定期强制重新克隆 (备选)

对于长期运行的镜像，可能会累积配置漂移。可以添加定期完全重新克隆的机制：

```java
// In PullSyncConfig entity
private LocalDateTime lastFullSyncAt;
private Integer daysSinceFullSync;

// In executeIncrementalSync()
if (config.daysSinceLastFullSync() > 30) {
    log.info("Last full sync was {} days ago, performing full sync",
        config.daysSinceLastFullSync());
    executeFirstSync(task, project, config);
    return;
}
```

## 推荐实施方案

### 立即修复 (方案 1)

1. 修改 `git-sync.sh` 的 `sync-incremental` 命令
2. 将 `git remote update --prune` 改为 `git fetch --mirror`
3. 测试验证

### 代码变更

```bash
# 文件: server/src/main/resources/scripts/git-sync.sh
# 行号: 84

# 修改前:
git remote update --prune

# 修改后:
git fetch --mirror
```

### 验证步骤

1. 构建并部署更新后的代码
2. 手动触发问题项目的增量同步:
   ```bash
   # 如果使用 CLI
   gitlab-mirror pull enable ai/test-android-app-3

   # 或直接调用调度器
   gitlab-mirror scheduler trigger --type=pull
   ```
3. 等待同步完成后，验证分支是否同步

## 影响范围评估

### 受影响的功能
- Pull Sync 的增量同步 (`sync-incremental`)
- 所有使用 Pull Sync 方式的项目

### 不受影响的功能
- Pull Sync 的首次同步 (`sync-first`) - 使用 `git clone --mirror`，配置正确
- Push Mirror 同步 - 使用 GitLab 原生功能
- Clone-Push 同步 - 不依赖本地镜像仓库

### 风险评估
- **风险等级**: 低
- **理由**:
  - `git fetch --mirror` 是标准 git 命令
  - 对于已正确配置的 mirror 仓库，行为与 `git remote update` 相同
  - 对于配置有问题的仓库，会修复问题
  - 不会影响已同步的数据

## 测试计划

### 单元测试
```java
@Test
void testIncrementalSyncWithNewBranches() {
    // 1. 执行首次同步
    // 2. 在源 GitLab 创建新分支
    // 3. 执行增量同步
    // 4. 验证新分支已同步到目标
}
```

### 集成测试

1. 准备测试项目
2. 配置为 Pull Sync
3. 执行首次同步
4. 在源 GitLab 添加新分支
5. 触发增量同步
6. 验证新分支出现在目标 GitLab

### 手动测试

使用 `test-branch-sync.sh` 脚本诊断和验证修复。

## 后续优化建议

1. **监控镜像仓库健康度**:
   - 定期检查本地镜像仓库配置
   - 检测配置漂移
   - 自动修复错误配置

2. **增强日志**:
   - 记录 fetch 前后的分支数量
   - 记录新增/删除的分支
   - 便于排查同步问题

3. **配置验证**:
   - 在每次增量同步前验证 mirror 配置
   - 如配置错误，自动修复或告警

## 参考资料

- Git Mirror 文档: https://git-scm.com/docs/git-clone#Documentation/git-clone.txt---mirror
- Git Fetch 文档: https://git-scm.com/docs/git-fetch
- Git Remote 文档: https://git-scm.com/docs/git-remote

## 相关文件

- `server/src/main/resources/scripts/git-sync.sh:72-99` - sync-incremental 命令
- `server/src/main/java/com/gitlab/mirror/server/service/PullSyncExecutorService.java:211-282` - executeIncrementalSync 方法
- `server/src/main/java/com/gitlab/mirror/server/executor/GitCommandExecutor.java:160-165` - syncIncremental 调用
