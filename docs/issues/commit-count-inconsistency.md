# Commit Count Inconsistency Issue

## 问题描述

CLI diff命令显示源项目和目标项目的commit数量不一致，但实际上两边的commit SHA是匹配的。

## 问题表现

```bash
./scripts/gitlab-mirror diff
```

输出结果：
- devops/gitlab-mirror: Δ Cmt = -57 (源88, 目标145)
- ai/test-node-app2: Δ Cmt = N/A (源1, 目标NULL)
- arch/test-spring-app1: Δ Cmt = N/A (源1, 目标NULL)

## 调查结果

### 1. GitLab API验证

通过GraphQL直接查询GitLab，确认实际数据一致：

```bash
# 源GitLab查询devops/gitlab-mirror
curl -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
  -X POST http://localhost:8000/api/graphql \
  -d '{"query": "{ project(fullPath: \"devops/gitlab-mirror\") { statistics { commitCount } } }"}'
# 结果: commitCount = 88

# 目标GitLab查询devops/gitlab-mirror
curl -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
  -X POST http://localhost:9000/api/graphql \
  -d '{"query": "{ project(fullPath: \"devops/gitlab-mirror\") { statistics { commitCount } } }"}'
# 结果: commitCount = 88
```

同样的，ai/test-node-app2和arch/test-spring-app1在两边的commit count都是1。

### 2. 数据库状态

```sql
-- 源项目信息（正确）
SELECT sync_project_id, gitlab_project_id, path_with_namespace, commit_count
FROM source_project_info ORDER BY sync_project_id;

919  | 1  | devops/gitlab-mirror      | 88
920  | 17 | ai/test-node-app2         | 1
921  | 16 | arch/test-spring-app1     | 1

-- 目标项目信息（错误）
SELECT sync_project_id, gitlab_project_id, path_with_namespace, commit_count
FROM target_project_info ORDER BY sync_project_id;

919  | 2  | devops/gitlab-mirror      | 145   -- 错误：应该是88
920  | 3  | ai/test-node-app2         | NULL  -- 缺失：应该是1
921  | 4  | arch/test-spring-app1     | NULL  -- 缺失：应该是1
```

### 3. 根本原因

通过代码分析发现问题在于 **`UpdateProjectDataService.updateTargetProjects()`** 方法：

**文件**: `server/src/main/java/com/gitlab/mirror/server/service/monitor/UpdateProjectDataService.java:297-312`

```java
private void updateTargetProjectFields(
        TargetProjectInfo info,
        GitLabProject project,
        BatchQueryExecutor.ProjectDetails details) {

    // Update from project details
    if (details != null) {
        info.setLatestCommitSha(details.getLatestCommitSha());
        info.setCommitCount(details.getCommitCount());    // 只有details不为null时才更新
        info.setBranchCount(details.getBranchCount());
    }
    // ... 其他字段更新
}
```

**问题**：
1. 调用时传入的`projectDetails`参数为null或不包含目标项目的统计信息
2. 因此`commit_count`字段从未被设置
3. MyBatis-Plus在UPDATE时会跳过null字段，导致数据库值保持旧值或NULL

### 4. 调用链分析

```
UnifiedProjectMonitor.scan()
└─> updateProjectDataService.updateTargetProjects(targetProjects, targetDetailsMap)
    └─> targetDetailsMap来自:
        batchQueryExecutor.getProjectDetailsBatchOptimized(targetProjects, targetClient)
        └─> convertGraphQLToDetails(graphQLInfo)
            └─> details.setCommitCount(graphQLInfo.getCommitCount())
```

**嫌疑点**：
- `getProjectDetailsBatchOptimized()` 可能没有正确查询目标项目的统计信息
- GraphQL查询可能缺少`statistics { commitCount }`字段
- 数据映射过程中丢失了统计信息

## 解决方案

### 方案1：修复GraphQL查询（推荐）

检查并确保GraphQL批量查询包含完整的统计信息：

```java
// BatchQueryExecutor.java
String query = """
    {
      projects(first: 100) {
        nodes {
          id
          fullPath
          statistics {
            commitCount      # 确保包含此字段
            repositorySize
          }
          repository {
            rootRef
            tree {
              lastCommit {
                sha
                committedDate
              }
            }
          }
        }
      }
    }
    """;
```

### 方案2：添加fallback逻辑

如果GraphQL统计信息不可用，使用REST API获取：

```java
private void updateTargetProjectFields(...) {
    if (details != null) {
        info.setCommitCount(details.getCommitCount());
    } else if (project.getStatistics() != null) {
        // Fallback: use statistics from REST API
        info.setCommitCount(project.getStatistics().getCommitCount());
    }
}
```

### 方案3：临时修复数据库

```sql
-- 手动修复当前错误数据
UPDATE target_project_info SET commit_count = 88 WHERE gitlab_project_id = 2;
UPDATE target_project_info SET commit_count = 1 WHERE gitlab_project_id = 3;
UPDATE target_project_info SET commit_count = 1 WHERE gitlab_project_id = 4;
```

## 后续行动

1. ✅ 添加调试日志到`updateTargetProjectFields()`
2. ⏳ 触发完整扫描并查看日志确认`details`是否为null
3. ⏳ 修复GraphQL查询或添加fallback逻辑
4. ⏳ 验证修复后扫描能正确更新commit count
5. ⏳ 添加单元测试确保统计信息正确传递

## 相关文件

- `server/src/main/java/com/gitlab/mirror/server/service/monitor/UpdateProjectDataService.java:297`
- `server/src/main/java/com/gitlab/mirror/server/service/monitor/BatchQueryExecutor.java:399`
- `server/src/main/java/com/gitlab/mirror/server/client/graphql/GraphQLProjectInfo.java:87`

## 影响范围

- ❌ CLI diff命令显示错误的commit count差异
- ❌ 监控系统可能基于错误数据产生INCONSISTENT状态
- ✅ 实际同步不受影响（基于commit SHA判断）
