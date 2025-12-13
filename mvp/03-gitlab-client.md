# 模块 3: GitLab API 客户端 (GitLab Client)

**目标**: 封装 GitLab API 调用，提供项目发现、分组/项目管理、Push Mirror 管理等功能。

**预计时间**: Week 1 (2-3天)

---

## 任务清单

### T3.1 实现 GitLab API 基础客户端
**依赖**: T1.2 (配置管理)

**任务目标**:
- 封装 HTTP 客户端（RestTemplate/WebClient）
- 实现 GitLab API 认证（Token）
- 实现连接测试功能
- 实现 API 限流处理（429 错误自动等待）
- 实现请求重试机制（指数退避）
- 实现请求/响应日志记录

**验收标准**:
- 成功调用 GitLab API
- Token 认证正常
- 限流后自动重试
- 网络错误自动重试（最多 3 次）
- 请求日志完整

**测试要求**:
- 测试 API 认证
- 测试连接测试
- 测试限流处理（Mock 429 响应）
- 测试重试机制
- 测试超时处理

**提交**: `feat(gitlab): add base API client with retry and rate limit`

---

### T3.2 实现 GitLab API 功能封装
**依赖**: T3.1

**任务目标**:
- **项目发现 API**:
  - 获取分组列表（支持递归）
  - 获取项目列表（支持分页）
  - 获取项目详细信息
  - 根据规则过滤项目（归档、空仓库、活跃度）

- **分组/项目管理 API**:
  - 创建分组（支持嵌套）
  - 创建项目
  - 检查分组/项目是否存在
  - 处理已存在的分组/项目

- **Push Mirror 管理 API**:
  - 创建 Remote Mirror
  - 获取 Mirror 状态
  - 触发 Mirror 同步
  - 更新 Mirror 配置
  - 删除 Mirror

- **仓库一致性检查 API**:
  - 获取仓库分支数量
  - 获取默认分支最后 commit
  - 对比源和目标仓库一致性
  - 生成一致性检查报告

**验收标准**:
- 所有 API 功能正常
- 分页处理正确
- 过滤规则生效
- Mirror 操作成功
- 一致性检查准确

**测试要求**:
- 使用 Mock Server 测试所有 API
- 测试分页处理
- 测试过滤逻辑
- 测试 Mirror 操作
- 测试一致性检查
- 测试大量项目场景

**提交**: `feat(gitlab): add project, group, mirror and consistency APIs`

---

## API 功能清单

### 项目发现 API
```java
// 获取分组列表
List<GitLabGroup> getGroups(String parentPath, boolean recursive);

// 获取项目列表（分页）
Page<GitLabProject> getProjects(String groupPath, int page, int pageSize);

// 获取项目详情
GitLabProject getProject(String projectPath);

// 过滤项目
List<GitLabProject> filterProjects(List<GitLabProject> projects, FilterRules rules);
```

### 分组/项目管理 API
```java
// 创建分组
GitLabGroup createGroup(String path, String name, String parentPath);

// 创建项目
GitLabProject createProject(String path, String name, String groupPath);

// 检查存在性
boolean groupExists(String groupPath);
boolean projectExists(String projectPath);
```

### Push Mirror 管理 API
```java
// 创建 Mirror
RemoteMirror createMirror(String projectId, String mirrorUrl, String token);

// 获取 Mirror 状态
RemoteMirror getMirror(String projectId, String mirrorId);

// 触发同步
void triggerMirrorSync(String projectId, String mirrorId);

// 删除 Mirror
void deleteMirror(String projectId, String mirrorId);
```

### 仓库一致性检查 API
```java
// 获取仓库信息
RepositoryInfo getRepositoryInfo(String projectPath);

// 一致性检查
ConsistencyReport checkConsistency(String sourceProject, String targetProject);
```

---

## 模块输出

- ✅ GitLab API 基础客户端（认证、限流、重试）
- ✅ 项目发现 API
- ✅ 分组/项目管理 API
- ✅ Push Mirror 管理 API
- ✅ 仓库一致性检查 API

---

## 关键决策

1. **HTTP 客户端**: 使用 RestTemplate 或 WebClient
2. **限流处理**: 429 错误自动等待 Retry-After 时间
3. **重试机制**: 指数退避（1s, 2s, 4s），最多重试 3 次
4. **分页处理**: 自动处理 GitLab 分页，返回完整列表
5. **Token 安全**: Token 不记录在日志中
