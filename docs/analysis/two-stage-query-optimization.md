# 两阶段查询优化技术方案

**创建时间**: 2025-12-15
**版本**: v1.0
**目标**: 优化scan性能，减少GitLab API调用次数和耗时

---

## 1. 方案概述

### 1.1 现状问题

**当前方案**（REST API逐个查询）：
- 14个项目需要28次API调用（2次/项目）
- 总耗时: ~19450ms
- Step 2（获取项目详情）占96.8%时间
- 性能瓶颈明显

**限制**：
- 无法获取累计commit数量
- 无法获取仓库大小统计
- API调用次数多，易触发rate limiting

### 1.2 优化目标

1. **减少API调用**: 从28次降至~3-5次
2. **降低响应时间**: Step 2从18829ms降至~200ms
3. **获取更多信息**: commitCount、repositorySize等统计数据
4. **可扩展性**: 支持100+项目批量查询

---

## 2. 两阶段查询策略

### 2.1 阶段1: GraphQL批量快速扫描

**目的**: 快速获取所有项目的基础信息，识别有变化的项目

**查询内容**:
```graphql
query($ids: [ID!]) {
  projects(ids: $ids) {
    nodes {
      id                          # 项目ID
      fullPath                    # 项目路径
      createdAt                   # 创建时间
      lastActivityAt              # 最后活动时间
      repository {
        rootRef                   # 默认分支名
        tree {
          lastCommit {
            sha                   # 最后commit SHA
            committedDate         # 最后commit时间
          }
        }
      }
      statistics {
        commitCount               # 累计commit数量
        repositorySize            # 仓库大小
        storageSize
      }
    }
  }
}
```

**性能**:
- 14个项目: ~148ms (1次API调用)
- 100个项目: ~500ms (5批，每批20项目)

**判断变化逻辑**:
```java
boolean hasChanged(GraphQLProjectInfo info, SourceProjectInfo dbRecord) {
    // 1. 最后commit SHA变化
    if (!Objects.equals(info.getLastCommitSha(), dbRecord.getLatestCommitSha())) {
        return true;
    }

    // 2. 最后活动时间变化（超过阈值）
    if (info.getLastActivityAt() != null && dbRecord.getUpdatedAt() != null) {
        long diffSeconds = Math.abs(Duration.between(
            info.getLastActivityAt(), dbRecord.getUpdatedAt()).getSeconds());
        if (diffSeconds > 60) {  // 1分钟阈值
            return true;
        }
    }

    return false;
}
```

**记录信息**:
- 更新时间: `lastActivityAt`
- Commit数量: `commitCount`
- 仓库大小: `repositorySize`
- 最后commit: `lastCommitSha`, `committedDate`

### 2.2 阶段2: 精细查询变化项目

**目的**: 对有变化的项目查询详细commit信息（作者、消息等）

**查询方式**: REST API或GraphQL单独查询

**REST API方案**:
```java
// GET /api/v4/projects/:id/repository/branches/:branch
{
  "commit": {
    "id": "40ebc2f225ba...",
    "title": "fix(monitor): resolve thread pool deadlock",
    "message": "完整commit消息...",
    "author_name": "张三",
    "author_email": "zhangsan@example.com",
    "authored_date": "2025-12-15T02:27:56+08:00",
    "committer_name": "张三",
    "committer_email": "zhangsan@example.com",
    "committed_date": "2025-12-15T02:27:56+08:00"
  }
}
```

**GraphQL方案**:
```graphql
query {
  project(fullPath: "devops/gitlab-mirror") {
    repository {
      tree {
        lastCommit {
          sha
          title                  # Commit标题
          message                # 完整消息
          author {
            name
            email
          }
          committedDate
        }
      }
    }
  }
}
```

**性能对比**:
- 假设14个项目中3个有变化
- REST API: 3次调用, ~30ms
- GraphQL: 1次调用(使用别名), ~20ms

**推荐**: REST API（逻辑简单，复杂度低）

---

## 3. 实现方案

### 3.1 核心类设计

#### 3.1.1 GraphQLProjectInfo
```java
@Data
public class GraphQLProjectInfo {
    private String id;  // gid://gitlab/Project/1
    private String fullPath;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastActivityAt;

    private Repository repository;
    private Statistics statistics;

    // 辅助方法
    public Long getProjectId();         // 提取数字ID
    public String getLastCommitSha();   // 获取SHA
    public OffsetDateTime getLastCommitDate();
    public Integer getCommitCount();    // 累计commit数
    public Long getRepositorySize();    // 仓库大小
}
```

#### 3.1.2 GitLabGraphQLClient
```java
@Component
public class GitLabGraphQLClient {
    /**
     * 批量查询项目基础信息
     * @param projectIds 项目ID列表
     * @return 项目信息列表
     */
    public List<GraphQLProjectInfo> batchQueryProjects(List<Long> projectIds);

    /**
     * 分批查询（避免单次过多）
     * @param projectIds 项目ID列表
     * @param batchSize 每批数量（建议20-50）
     * @return 所有项目信息
     */
    public List<GraphQLProjectInfo> batchQueryProjectsInChunks(
        List<Long> projectIds, int batchSize);
}
```

### 3.2 UnifiedProjectMonitor改造

**优化前流程**:
```
1. 查询源项目列表 (REST API, N次)
2. 获取项目详情 (REST API, 2N次) ← 瓶颈
3. 对比数据库
4. 更新数据库
```

**优化后流程**:
```
1. 查询源项目列表 (REST API, 1次, ~283ms)
2. 批量获取基础信息 (GraphQL, 1次, ~148ms) ← 新增
3. 对比数据库，识别变化项目
4. 精细查询变化项目详情 (REST API, M次, M≪N) ← 按需查询
5. 更新数据库
```

**代码结构**:
```java
public ScanResult scanSourceProjects(OffsetDateTime updatedAfter) {
    // Step 1: 查询源项目列表
    List<GitLabProject> sourceProjects =
        batchQueryExecutor.querySourceProjects(updatedAfter, 100);

    // Step 2: GraphQL批量获取基础信息
    List<Long> projectIds = sourceProjects.stream()
        .map(GitLabProject::getId)
        .collect(Collectors.toList());
    List<GraphQLProjectInfo> graphQLInfos =
        graphQLClient.batchQueryProjectsInChunks(projectIds, 30);

    // Step 3: 对比数据库，识别变化
    List<GraphQLProjectInfo> changedProjects =
        identifyChangedProjects(graphQLInfos);

    // Step 4: 对变化项目查询详细信息
    for (GraphQLProjectInfo info : changedProjects) {
        CommitDetail detail = fetchCommitDetail(info);
        // 更新数据库
        updateSourceProjectInfo(info, detail);
    }

    // Step 5: 批量更新基础信息（无变化的项目）
    batchUpdateBasicInfo(graphQLInfos, changedProjects);
}
```

---

## 4. 性能对比

### 4.1 14项目场景

| 指标 | 优化前 (REST) | 优化后 (GraphQL+REST) | 提升 |
|------|--------------|---------------------|------|
| API调用次数 | 28次 | 2-5次 | 82-93% ↓ |
| Step 2耗时 | 18829ms | ~200ms | 98.9% ↓ |
| 总耗时 | 19450ms | ~700ms | 96.4% ↓ |
| 获取信息 | 基础 | 基础+统计+详情 | - |

**详细分析**:
```
优化前:
  Step 1: Query source         283ms (1.5%)
  Step 2: Get project details  18829ms (96.8%)  ← 瓶颈
  Step 3-9: Other              338ms (1.7%)
  Total: 19450ms

优化后:
  Step 1: Query source         283ms (40.4%)
  Step 2: GraphQL batch        148ms (21.1%)   ← 新增
  Step 3: Identify changes     20ms (2.9%)     ← 新增
  Step 4: Fetch changed (3个)  30ms (4.3%)     ← 按需
  Step 5: Update DB            219ms (31.3%)
  Total: ~700ms
```

### 4.2 100项目场景

| 方案 | API调用 | 耗时 |
|------|--------|------|
| REST (优化前) | 200次 | ~135s |
| GraphQL批量 | 5次 | ~2.5s |
| **提升** | **97.5% ↓** | **98.1% ↓** |

**计算依据**:
- REST: 100项目 × 70ms/项目 × 2次调用 = 14000ms × 10倍并发 = ~135s
- GraphQL: 5批 × 20项目/批 × 100ms/批 = 500ms + 变化项目查询(~2s) = ~2.5s

---

## 5. 数据库Schema扩展

### 5.1 SOURCE_PROJECT_INFO新增字段

```sql
ALTER TABLE SOURCE_PROJECT_INFO
ADD COLUMN total_commit_count INT COMMENT '所有分支累计commit数量',
ADD COLUMN repository_size BIGINT COMMENT '仓库大小（字节）',
ADD COLUMN storage_size BIGINT COMMENT '存储大小（字节）',
ADD COLUMN last_commit_author VARCHAR(255) COMMENT '最后commit作者',
ADD COLUMN last_commit_email VARCHAR(255) COMMENT '最后commit邮箱',
ADD COLUMN last_commit_message TEXT COMMENT '最后commit消息',
ADD COLUMN last_commit_title VARCHAR(500) COMMENT '最后commit标题';
```

### 5.2 字段说明

| 字段 | 来源 | 阶段 | 说明 |
|------|------|------|------|
| `total_commit_count` | GraphQL | 1 | 所有分支累计commit数 |
| `repository_size` | GraphQL | 1 | 仓库大小（字节） |
| `last_commit_author` | REST/GraphQL | 2 | 最后commit作者（仅变化时查询） |
| `last_commit_message` | REST/GraphQL | 2 | 完整commit消息（仅变化时查询） |
| `last_commit_title` | REST/GraphQL | 2 | Commit标题（message首行） |

---

## 6. 配置选项

### 6.1 application.yml

```yaml
gitlab:
  graphql:
    enabled: true                    # 是否启用GraphQL批量查询
    batch-size: 30                   # 每批查询项目数（建议20-50）
    max-complexity: 300              # GitLab GraphQL复杂度限制

  scan:
    change-detection:
      enabled: true                  # 是否启用变化检测
      activity-threshold-seconds: 60 # 活动时间差异阈值（秒）

    detail-query:
      enabled: true                  # 是否查询变化项目详情
      method: rest                   # rest | graphql
```

### 6.2 Feature Toggle

```java
@ConditionalOnProperty(
    name = "gitlab.graphql.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class GraphQLOptimizationConfig {
    // GraphQL相关Bean配置
}
```

---

## 7. 错误处理

### 7.1 GraphQL查询失败

**策略**: 降级到REST API

```java
public List<GraphQLProjectInfo> batchQuery(List<Long> ids) {
    try {
        return graphQLClient.batchQueryProjects(ids);
    } catch (Exception e) {
        log.warn("GraphQL query failed, fallback to REST API: {}",
            e.getMessage());
        return fallbackToRestAPI(ids);
    }
}
```

### 7.2 部分项目查询失败

**策略**: 跳过失败项目，继续处理其他

```java
for (GraphQLProjectInfo info : graphQLInfos) {
    try {
        processProject(info);
    } catch (Exception e) {
        log.error("Failed to process project {}: {}",
            info.getFullPath(), e.getMessage());
        // 继续处理下一个项目
    }
}
```

---

## 8. 监控指标

### 8.1 性能指标

```java
@Timed(value = "gitlab.graphql.batch.query",
       description = "GraphQL batch query duration")
public List<GraphQLProjectInfo> batchQueryProjects(List<Long> ids) {
    // ...
}
```

**Metrics**:
- `gitlab.graphql.batch.query.duration`: GraphQL查询耗时
- `gitlab.graphql.batch.query.size`: 每批查询项目数
- `gitlab.scan.changed.projects.count`: 检测到变化的项目数
- `gitlab.scan.detail.query.count`: 详细查询次数

### 8.2 日志示例

```
[SCAN-PERF] === SCAN PERFORMANCE SUMMARY ===
[SCAN-PERF] Total Duration: 718ms
[SCAN-PERF] Step 1 (Query Source):        283ms (39.4%)
[SCAN-PERF] Step 2 (GraphQL Batch):       148ms (20.6%)  ← 新增
[SCAN-PERF] Step 3 (Identify Changes):    25ms (3.5%)    ← 新增
[SCAN-PERF] Step 4 (Query Changed 3):     42ms (5.8%)    ← 新增
[SCAN-PERF] Step 5 (Update DB):           220ms (30.7%)
[SCAN-PERF]
[SCAN-PERF] Projects scanned: 14
[SCAN-PERF] Projects changed: 3
[SCAN-PERF] API calls saved: 25 (28→3)
```

---

## 9. 实施计划

### 9.1 Phase 1: GraphQL客户端 (1天)

- [x] 创建GraphQLProjectInfo DTO
- [x] 创建GraphQLRequest/Response
- [x] 创建GitLabGraphQLClient
- [ ] 单元测试

### 9.2 Phase 2: 两阶段查询逻辑 (1天)

- [ ] 修改UnifiedProjectMonitor
- [ ] 实现变化检测逻辑
- [ ] 实现按需详细查询
- [ ] 集成测试

### 9.3 Phase 3: 数据库Schema (0.5天)

- [ ] 添加新字段
- [ ] 数据迁移脚本
- [ ] 更新Mapper

### 9.4 Phase 4: 测试验证 (0.5天)

- [ ] 性能测试
- [ ] 降级测试
- [ ] 压力测试

**总计**: 3天

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| GraphQL API变更 | 中 | 版本锁定、定期测试、REST降级 |
| 复杂度超限 | 中 | 分批查询、减小batch-size |
| 变化检测误判 | 低 | 调整阈值、记录日志 |
| 性能退化 | 低 | Feature toggle、灰度发布 |

---

## 11. 总结

**核心优势**:
1. ✅ **性能提升96%**: 19450ms → 700ms
2. ✅ **API调用减少93%**: 28次 → 2-5次
3. ✅ **信息更全面**: 新增commitCount、repositorySize等统计
4. ✅ **可扩展**: 支持100+项目批量查询
5. ✅ **按需查询**: 仅对变化项目查询详情，节省资源

**投入产出**:
- 开发时间: 3天
- 性能收益: 96%提升 (14项目场景)
- 扩展性: 支持10倍项目规模
- ROI: ⭐⭐⭐⭐⭐

**下一步**:
1. 完成GitLabGraphQLClient单元测试
2. 修改UnifiedProjectMonitor实现两阶段查询
3. 添加数据库字段和迁移脚本
4. 性能测试验证

---

**文档版本**: v1.0
**最后更新**: 2025-12-15
**维护人**: GitLab Mirror Team
