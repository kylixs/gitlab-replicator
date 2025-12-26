# Git 同步策略分析

## 概述

本文档分析 GitLab 项目同步中 Git clone/push 策略的选择，解决 `--mirror` 导致的配置冲突问题。

## 核心问题

### 问题描述

使用 `git clone --mirror` 克隆仓库后，增量同步时执行 `git push --all` 会报错：

```
fatal: options '--all' and '--mirror' cannot be used together
```

### 问题原因

当使用 `--mirror` 克隆后，Git 自动设置 `remote.origin.mirror = true`。之后执行 `git push --all` 时：

1. Git 检测到 `mirror = true` 配置
2. 自动应用 `--mirror` 选项
3. 与显式的 `--all` 冲突
4. 报错退出

## Git Clone 模式对比

### `git clone --mirror`

创建一个**完整的镜像仓库**，包含源仓库的所有内容。

**包含的引用**：
1. **所有分支** (refs/heads/*)
2. **所有标签** (refs/tags/*)
3. **所有远程分支** (refs/remotes/*)
4. **所有 notes** (refs/notes/*)
5. **所有其他引用**:
   - refs/pull/* (GitHub Pull Requests)
   - refs/merge-requests/* (GitLab Merge Requests)
   - refs/pipelines/* (GitLab CI/CD)
   - refs/environments/* (GitLab Environments)

**特点**：
- 创建**裸仓库** (bare repository，没有工作目录)
- 所有分支直接存储在 `refs/heads/` 下
- 自动设置 `remote.origin.mirror = true`
- 自动设置 `remote.origin.fetch = +refs/*:refs/*`

**优点**：
- ✅ **完整克隆**：获取所有分支、标签、notes 等引用
- ✅ **裸仓库**：节省磁盘空间（没有工作目录）
- ✅ **简单高效**：一条命令获取所有内容

**缺点**：
- ❌ **包含特殊引用**：refs/merge-requests/*, refs/pipelines/* 等
- ❌ **mirror 配置冲突**：后续无法使用 --all/--tags
- ❌ **需要额外配置**：必须手动禁用 mirror 模式

### `git clone --bare`

创建一个**裸仓库**，包含正常的分支和标签。

**包含的引用**：
1. **所有分支** (refs/heads/*)
2. **所有标签** (refs/tags/*)

**特点**：
- 创建**裸仓库** (bare repository)
- 默认 fetch 配置：`+refs/heads/*:refs/heads/*`
- **不会**设置 `remote.origin.mirror = true`

**优点**：
- ✅ 裸仓库，节省空间
- ✅ 获取所有分支和标签
- ✅ **没有 mirror 配置**，不会冲突
- ✅ 只克隆正常的 refs，不包含特殊引用

**缺点**：
- ⚠️ 需要手动配置 fetch tags（`git config --add remote.origin.fetch '+refs/tags/*:refs/tags/*'`）
- ⚠️ 不包含 refs/notes/* 等特殊引用（通常不需要）

### `git clone`（普通克隆）

创建一个**工作仓库**，包含工作目录。

**不适合用于同步**：
- ❌ 包含工作目录，浪费磁盘空间
- ❌ 需要额外步骤转换为裸仓库
- ❌ 步骤复杂，效率低

## GitLab 项目同步需求分析

### 需要同步的内容

1. ✅ **所有分支** (refs/heads/*) - **必需**
2. ✅ **所有标签** (refs/tags/*) - **必需**

### 不需要同步的内容

3. ❌ **MR 引用** (refs/merge-requests/*) - GitLab 不允许推送，由 GitLab 管理
4. ❌ **Pipeline 引用** (refs/pipelines/*) - GitLab 不允许推送，由 GitLab 管理
5. ❌ **Environment 引用** (refs/environments/*) - GitLab 不允许推送
6. ❌ **Notes** (refs/notes/*) - 很少使用，通常不需要

### 结论

**`--bare` 足够满足 GitLab 同步需求！**

## Git Push 模式对比

### `git push --mirror`

推送**所有本地引用**到远程，并删除远程多余的引用。

**行为**：
- 推送所有 refs/* (branches, tags, notes, 等)
- **删除**目标端存在但源端不存在的引用
- 等同于：`git push --all --tags --force + 删除远程多余引用`

**问题**：
- ❌ 推送 GitLab 内部引用会被拒绝
- ❌ 与 `--all`/`--tags` 冲突

### `git push --all`

只推送**所有分支** (refs/heads/*)。

**特点**：
- ✅ 只推送分支，不推送 tags
- ✅ 不会删除远程分支
- ⚠️ 需要配合 `--tags` 推送标签

### `git push --tags`

只推送**所有标签** (refs/tags/*)。

**特点**：
- ✅ 只推送标签，不推送分支
- ✅ 不会删除远程标签
- ⚠️ 需要配合 `--all` 推送分支

### `git push --all` + `git push --tags`

分别推送分支和标签。

**优点**：
- ✅ 完整同步所有分支和标签
- ✅ 避免推送 GitLab 内部引用
- ✅ 与任何 clone 模式兼容
- ✅ 使用 `--force` 可以强制覆盖

## 解决方案对比

### 方案A：`git clone --bare`（推荐 ✅）

**首次同步**：
```bash
# 使用 --bare 代替 --mirror
git clone --bare SOURCE_URL LOCAL_PATH
cd LOCAL_PATH

# 配置 fetch 所有标签（bare 默认已配置 refs/heads/*）
git config --add remote.origin.fetch '+refs/tags/*:refs/tags/*'

# 设置推送目标
git remote set-url --push origin TARGET_URL

# 推送所有分支和标签
git push --all origin --force
git push --tags origin --force
```

**增量同步**：
```bash
cd LOCAL_PATH

# 更新所有分支和标签
git fetch origin --prune

# 推送到目标
git remote set-url --push origin TARGET_URL
git push --all origin --force
git push --tags origin --force
```

**优点**：
- ✅ 从一开始就避免 mirror 配置
- ✅ 满足所有同步需求（分支 + 标签）
- ✅ 不克隆不需要的特殊引用
- ✅ 更简洁，无需额外配置
- ✅ 无配置冲突问题

**缺点**：
- ⚠️ 需要手动配置 fetch tags（一次性操作）

### 方案B：`git clone --mirror` + 立即禁用

**首次同步**：
```bash
git clone --mirror SOURCE_URL LOCAL_PATH
cd LOCAL_PATH

# 立即禁用 mirror 模式
git config remote.origin.mirror false

# 设置推送目标
git remote set-url --push origin TARGET_URL

# 推送所有分支和标签
git push --all origin --force
git push --tags origin --force
```

**增量同步**：
```bash
cd LOCAL_PATH

# 更新所有分支和标签
git fetch origin --prune

# 推送到目标
git remote set-url --push origin TARGET_URL
git push --all origin --force
git push --tags origin --force
```

**优点**：
- ✅ 简单，一条命令克隆
- ✅ 获取所有内容（包括 notes 等）
- ✅ 禁用后可正常使用 --all/--tags

**缺点**：
- ⚠️ 需要额外步骤禁用 mirror
- ⚠️ 克隆了特殊引用（浪费空间和时间）

### 方案C：统一使用 `--mirror`

**问题**：
- ❌ 推送 GitLab 内部引用会被拒绝
- ❌ 不适用于 GitLab 同步场景

### 方案对比总结

| 方案 | 克隆速度 | 磁盘占用 | 复杂度 | 配置冲突 | 推荐度 |
|------|---------|---------|--------|----------|--------|
| --bare | 快 | 小 | 低 | **无** | ✅ **推荐** |
| --mirror + 禁用 | 快 | 中（包含无用引用） | 中 | 需手动禁用 | ⚠️ 可用 |
| --mirror | 快 | 中 | 低 | **有冲突** | ❌ 不推荐 |

## 推荐的最终方案

### 方案：使用 `--bare` 替代 `--mirror`

**理由**：

1. ✅ **满足所有同步需求**：branches + tags 足够
2. ✅ **避免配置冲突**：不设置 `mirror = true`
3. ✅ **不克隆无用引用**：节省空间和时间
4. ✅ **更简洁**：无需额外禁用步骤
5. ✅ **性能相当**：clone 速度和磁盘占用差异可忽略

**唯一丢失的内容**（都不需要）：

- refs/notes/* (很少使用)
- refs/pull/*, refs/merge-requests/* (GitLab 不允许推送，无用)
- refs/pipelines/*, refs/environments/* (GitLab 不允许推送，无用)

## GitLab 同步限制

### GitLab 拒绝的引用

GitLab 不允许直接推送某些内部引用：

```bash
# ❌ 会失败
git push --mirror gitlab.com:user/repo.git

# 错误示例：
remote: GitLab: You are not allowed to push code to protected branches on this project.
remote: error: deny updating a hidden ref
```

**被拒绝的引用**：
- `refs/merge-requests/*` - MR 由 GitLab 管理
- `refs/pipelines/*` - Pipeline 由 GitLab 管理
- `refs/environments/*` - Environment 由 GitLab 管理
- `refs/keep-around/*` - GitLab 内部引用

### 正确的推送方式

```bash
# ✅ 正确：只推送 branches 和 tags
git push --all origin --force
git push --tags origin --force
```

## 同步删除的分支/标签

### 使用 --prune 选项

```bash
# 推送并删除远程不存在的分支
git push --prune origin +refs/heads/*:refs/heads/*

# 推送并删除远程不存在的标签
git push --prune origin +refs/tags/*:refs/tags/*
```

### 手动删除

```bash
# 删除目标端不存在于源端的分支
git push origin --delete $(git ls-remote --heads origin | \
  grep -v -f <(git for-each-ref --format='%(refname:short)' refs/heads) | \
  awk '{print $2}' | sed 's|refs/heads/||')
```

## 实际应用场景

### ✅ 适合使用 --mirror 的场景

1. **完整备份到自己的 Git 服务器**（非 GitLab/GitHub）
   ```bash
   git clone --mirror github.com:user/repo.git backup.git
   cd backup.git
   git push --mirror my-git-server:backup/repo.git
   ```

2. **创建本地 mirror 加速克隆**
   ```bash
   git clone --mirror upstream/large-repo.git local-mirror.git
   # 其他人可以从 local-mirror.git 快速克隆
   ```

### ❌ 不适合使用 --mirror 的场景

1. **同步到 GitLab/GitHub**（会被拒绝）
   ```bash
   # ❌ 失败
   git push --mirror gitlab.com:user/repo.git
   ```

2. **持续增量同步**（第二次会冲突）
   ```bash
   # 第一次 OK
   git clone --mirror source
   git push --mirror target

   # 第二次冲突 ❌
   git fetch origin
   git push --all origin  # fatal: --all and --mirror...
   ```

## 总结

**最佳实践**：GitLab 项目同步使用 `git clone --bare` + `git push --all/--tags`

- 首次同步：`git clone --bare` → 配置 fetch tags → `git push --all/--tags`
- 增量同步：`git fetch --prune` → `git push --all/--tags --force`
- 删除同步：使用 `git push --prune` 或手动删除

**避免使用** `git clone --mirror`，除非：
- 需要完整备份所有引用（包括 notes）
- 目标是自己的 Git 服务器（非 GitLab/GitHub）
- 只进行一次性完整镜像
