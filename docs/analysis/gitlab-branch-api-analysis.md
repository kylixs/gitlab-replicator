# GitLab 分支 API 分析与实现方案

## 问题背景

需要记录源和目标项目的：
1. 分支数量
2. 具体分支列表
3. 每个分支的最后提交记录（SHA、时间、作者等）

用于精确对比源和目标项目的同步状态。

## GitLab API 能力分析

### 1. List Repository Branches API

**端点**: `GET /api/v4/projects/:id/repository/branches`

**文档**: https://docs.gitlab.com/ee/api/branches.html#list-repository-branches

**返回数据** (单个分支):
```json
{
  "name": "main",
  "commit": {
    "id": "7b5c3cc8be40ee161ae89a06bba6229da1032a0c",
    "short_id": "7b5c3cc",
    "created_at": "2012-06-28T03:44:20-07:00",
    "parent_ids": ["4ad91d3c1144c406e50c7b33bae684bd6837faf8"],
    "title": "add projects api",
    "message": "add projects api",
    "author_name": "John Smith",
    "author_email": "john@example.com",
    "authored_date": "2012-06-27T05:51:39-07:00",
    "committer_name": "John Smith",
    "committer_email": "john@example.com",
    "committed_date": "2012-06-28T03:44:20-07:00",
    "trailers": {},
    "web_url": "https://gitlab.example.com/thedude/gitlab-foss/-/commit/7b5c3cc8"
  },
  "merged": false,
  "protected": true,
  "developers_can_push": false,
  "developers_can_merge": false,
  "can_push": true,
  "default": true,
  "web_url": "https://gitlab.example.com/thedude/gitlab-foss/-/tree/main"
}
```

**关键字段**:
- `name`: 分支名称
- `commit.id`: 提交 SHA (完整)
- `commit.committed_date`: 提交时间 (ISO 8601 格式)
- `commit.author_name`: 作者名
- `commit.author_email`: 作者邮箱
- `commit.message`: 提交消息
- `protected`: 是否受保护
- `default`: 是否默认分支
- `merged`: 是否已合并

**查询参数**:
- `per_page`: 每页数量 (默认 20，最大 100)
- `page`: 页码
- `search`: 搜索分支名

**特点**:
✅ **一次性获取所有需要的信息**:
- 分支列表 ✅
- 最后提交 SHA ✅
- 提交时间 ✅
- 提交作者 ✅
- 提交消息 ✅
- 是否受保护 ✅

❌ **局限性**:
- 需要分页获取（大项目可能有数百个分支）
- 每个请求最多返回 100 个分支

## 当前实现状态

### 已有代码

**GitLabApiClient.java**:
```java
public List<RepositoryBranch> getBranches(Long projectId) {
    String path = "/api/v4/projects/" + projectId + "/repository/branches";
    RepositoryBranch[] branches = client.get(path, RepositoryBranch[].class);
    return branches != null ? List.of(branches) : new ArrayList<>();
}
```

**问题**:
1. ❌ 没有分页支持 - 只能获取前 20 个分支（GitLab 默认）
2. ❌ RepositoryBranch 模型定义不完整

### RepositoryBranch 模型问题

**当前定义**:
```java
@Data
public class RepositoryBranch {
    private String name;
    private Commit commit;
    private Boolean merged;

    @Data
    public static class Commit {
        private String id;
        private String message;
        private String authorName;
        private String authorEmail;
        private String committedDate;  // ❌ String 类型，应该是 OffsetDateTime
    }
}
```

**缺少字段**:
- ❌ `protected`: 是否受保护分支
- ❌ `default`: 是否默认分支
- ❌ `webUrl`: 分支 Web URL

**错误字段类型**:
- ❌ `committedDate`: 应该是 `OffsetDateTime`，不是 `String`

## 实现方案

### 方案 1: 修复现有模型 + 添加分页支持（推荐）

#### 步骤 1: 修复 RepositoryBranch 模型

```java
@Data
public class RepositoryBranch {
    private String name;
    private Commit commit;
    private Boolean merged;
    private Boolean protected;     // ✅ 新增
    @JsonProperty("default")
    private Boolean isDefault;     // ✅ 新增 (default 是 Java 关键字，用 JsonProperty)
    private String webUrl;         // ✅ 新增

    @Data
    public static class Commit {
        private String id;
        private String message;
        private String authorName;
        private String authorEmail;
        private OffsetDateTime committedDate;  // ✅ 修复类型
        private OffsetDateTime authoredDate;   // ✅ 新增
        private String committerName;          // ✅ 新增
        private String committerEmail;         // ✅ 新增
    }
}
```

#### 步骤 2: 添加分页获取方法

```java
// GitLabApiClient.java
public List<RepositoryBranch> getAllBranches(Long projectId) {
    List<RepositoryBranch> allBranches = new ArrayList<>();
    int page = 1;
    int perPage = 100;  // 最大值

    while (true) {
        String path = String.format("/api/v4/projects/%d/repository/branches?per_page=%d&page=%d",
            projectId, perPage, page);
        RepositoryBranch[] branches = client.get(path, RepositoryBranch[].class);

        if (branches == null || branches.length == 0) {
            break;
        }

        allBranches.addAll(List.of(branches));

        // 如果返回数量少于 perPage，说明是最后一页
        if (branches.length < perPage) {
            break;
        }

        page++;
    }

    return allBranches;
}
```

#### 步骤 3: 集成到 Monitor 服务

```java
// UpdateProjectDataService.java (Monitor 服务中)
private void updateProjectBranchSnapshot(Long syncProjectId, Long gitlabProjectId, String projectType) {
    // 获取所有分支（带分页）
    List<RepositoryBranch> branches = (projectType.equals("source"))
        ? sourceGitLabApiClient.getAllBranches(gitlabProjectId)
        : targetGitLabApiClient.getAllBranches(gitlabProjectId);

    // 更新数据库快照
    branchSnapshotService.updateBranchSnapshot(syncProjectId, projectType, branches, defaultBranch);
}
```

### 方案 2: 使用 GraphQL API（可选，更高级）

GitLab 也提供 GraphQL API，可以更灵活地查询数据。

**优点**:
- 一次请求获取更多数据
- 减少 API 调用次数
- 可以精确控制返回字段

**缺点**:
- 需要学习 GraphQL 查询语法
- 需要修改现有的 HTTP 客户端代码
- 复杂度较高

**暂不推荐使用**，因为 REST API 已经足够满足需求。

## 数据库设计

### project_branch_snapshot 表（已设计）

```sql
CREATE TABLE `project_branch_snapshot` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `sync_project_id` BIGINT(20) NOT NULL,
  `project_type` VARCHAR(50) NOT NULL COMMENT 'source/target',
  `branch_name` VARCHAR(255) NOT NULL,
  `commit_sha` VARCHAR(255) NOT NULL,
  `commit_message` TEXT DEFAULT NULL,
  `commit_author` VARCHAR(255) DEFAULT NULL,
  `committed_at` DATETIME DEFAULT NULL,
  `is_default` TINYINT(1) DEFAULT 0,
  `is_protected` TINYINT(1) DEFAULT 0,
  `snapshot_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_branch` (`sync_project_id`, `project_type`, `branch_name`)
);
```

**存储策略**:
1. 每次扫描项目时，更新分支快照
2. 删除旧快照 → 插入新快照（全量替换）
3. 使用 UNIQUE KEY 防止重复

## 更新时机

### 触发分支快照更新的场景

1. **项目发现时** (Discovery)
   - 新发现项目 → 立即拉取源项目分支快照

2. **项目扫描时** (Monitor Scan)
   - 定期扫描 → 更新源和目标项目的分支快照
   - 频率: 每次 scan 都更新（增量 scan 或全量 scan）

3. **同步完成后** (Pull Sync / Push Mirror)
   - Pull Sync 完成 → 更新目标项目分支快照
   - Push Mirror 状态更新 → 更新目标项目分支快照

4. **手动触发**
   - CLI 命令: `gitlab-mirror scan`
   - API 调用: `POST /api/sync/scan`

### 实现位置

#### UpdateProjectDataService.java

```java
private void updateSourceProjectData(Long syncProjectId, Map<String, Object> projectData) {
    // ... 现有逻辑 ...

    // ✅ 新增：更新分支快照
    if (sourceInfo.getGitlabProjectId() != null) {
        try {
            branchSnapshotService.updateSourceBranchSnapshot(
                syncProjectId,
                sourceInfo.getGitlabProjectId(),
                sourceInfo.getDefaultBranch()
            );
        } catch (Exception e) {
            log.warn("Failed to update source branch snapshot: {}", syncProjectId, e);
        }
    }
}

private void updateTargetProjectData(Long syncProjectId, Map<String, Object> projectData) {
    // ... 现有逻辑 ...

    // ✅ 新增：更新分支快照
    if (targetInfo.getGitlabProjectId() != null) {
        try {
            branchSnapshotService.updateTargetBranchSnapshot(
                syncProjectId,
                targetInfo.getGitlabProjectId(),
                targetInfo.getDefaultBranch()
            );
        } catch (Exception e) {
            log.warn("Failed to update target branch snapshot: {}", syncProjectId, e);
        }
    }
}
```

## 性能考虑

### 大项目分支数量

假设项目有 500 个分支：
- 分页请求: 500 / 100 = 5 次 API 调用
- 每次调用: ~200ms
- 总耗时: ~1 秒

### 批量更新

假设有 100 个项目需要更新分支快照：
- 串行处理: 100 秒
- 并行处理 (10 并发): 10 秒

**优化建议**:
1. 使用线程池并行处理多个项目
2. 对于分支数量少的项目，优先处理
3. 添加缓存，避免短时间内重复查询

## 数据对比逻辑

### DiffCalculator 改进

```java
// 使用数据库快照对比
private List<BranchComparison> compareBranches(Long syncProjectId) {
    // 从数据库读取快照
    List<ProjectBranchSnapshot> sourceSnapshots =
        branchSnapshotService.getBranchSnapshots(syncProjectId, "source");
    List<ProjectBranchSnapshot> targetSnapshots =
        branchSnapshotService.getBranchSnapshots(syncProjectId, "target");

    // 对比逻辑
    return compareBranchSnapshots(sourceSnapshots, targetSnapshots);
}
```

**优势**:
- ✅ 不需要每次都调用 GitLab API
- ✅ 对比速度快（内存操作）
- ✅ 支持历史对比（可以看到分支变化趋势）

## 实现优先级

### 阶段 1: 修复基础模型（立即）

1. ✅ 修复 `RepositoryBranch` 模型
2. ✅ 添加 `getAllBranches()` 分页方法
3. ✅ 测试 API 调用

### 阶段 2: 集成到 Monitor（本周）

1. ✅ 在 `UpdateProjectDataService` 中调用分支快照更新
2. ✅ 测试分支快照存储
3. ✅ 验证数据库记录

### 阶段 3: 完善对比逻辑（下周）

1. 优化 `DiffCalculator` 使用数据库快照
2. 添加详细的分支对比报告
3. CLI 命令展示分支差异

## 总结

### GitLab API 能力

✅ **GitLab Branches API 完全满足需求**:
- 一次性获取分支列表和最后提交信息
- 包含所有必要字段（SHA、时间、作者、消息）
- 支持分页查询大量分支

### 实现方案

✅ **推荐方案: 修复模型 + 分页 + 数据库快照**:
1. 修复 `RepositoryBranch` 模型定义
2. 添加分页获取所有分支
3. 存储到 `project_branch_snapshot` 表
4. Monitor 服务定期更新快照
5. Diff 服务使用快照对比

### 关键优势

- ✅ 精确对比：记录每个分支的提交 SHA 和时间
- ✅ 性能优化：数据库查询比 API 调用快
- ✅ 可追溯：保留快照时间，支持历史分析
- ✅ 容错性：API 失败不影响已有数据
