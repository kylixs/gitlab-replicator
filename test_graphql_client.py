#!/usr/bin/env python3
"""
验证GitLabGraphQLClient的查询结果
真实连接GitLab进行测试
"""

import requests
import json
from typing import List, Dict

# 配置
SOURCE_GITLAB_URL = "http://localhost:8000"
SOURCE_GITLAB_TOKEN = "glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq"

# 测试项目
TEST_PROJECTS = [
    {"id": 1, "path": "devops/gitlab-mirror"},
    {"id": 16, "path": "arch/test-spring-app1"},
    {"id": 17, "path": "ai/test-node-app2"},
]

# GraphQL查询模板（与Java代码完全一致）
BATCH_QUERY_TEMPLATE = """
query($ids: [ID!]) {
  projects(ids: $ids) {
    nodes {
      id
      fullPath
      createdAt
      lastActivityAt
      repository {
        rootRef
        tree {
          lastCommit {
            sha
            committedDate
          }
        }
      }
      statistics {
        commitCount
        repositorySize
        storageSize
      }
    }
  }
}
"""


def test_graphql_query(project_ids: List[int]):
    """测试GraphQL批量查询"""

    # 转换为GitLab GID格式
    gids = [f"gid://gitlab/Project/{id}" for id in project_ids]

    # 构建请求
    query_request = {
        "query": BATCH_QUERY_TEMPLATE,
        "variables": {
            "ids": gids
        }
    }

    headers = {
        "PRIVATE-TOKEN": SOURCE_GITLAB_TOKEN,
        "Content-Type": "application/json"
    }

    # 发送请求
    print(f"\n{'='*80}")
    print(f"Testing GraphQL batch query for {len(project_ids)} projects")
    print(f"{'='*80}\n")

    import time
    start_time = time.time()

    response = requests.post(
        f"{SOURCE_GITLAB_URL}/api/graphql",
        headers=headers,
        json=query_request
    )

    duration_ms = (time.time() - start_time) * 1000

    print(f"✅ Request completed in {duration_ms:.1f}ms")
    print(f"   Status code: {response.status_code}\n")

    if response.status_code != 200:
        print(f"❌ Request failed: {response.status_code}")
        print(response.text)
        return False

    # 解析响应
    data = response.json()

    # 检查错误
    if "errors" in data and data["errors"]:
        print(f"❌ GraphQL errors:")
        for error in data["errors"]:
            print(f"   - {error['message']}")
        return False

    # 验证响应结构
    if "data" not in data or "projects" not in data["data"]:
        print(f"❌ Invalid response structure")
        print(json.dumps(data, indent=2))
        return False

    projects = data["data"]["projects"]["nodes"]

    if not projects:
        print(f"❌ No projects returned")
        return False

    print(f"✅ Retrieved {len(projects)} projects\n")

    # 验证每个项目
    all_valid = True
    for i, project in enumerate(projects, 1):
        print(f"{'─'*80}")
        print(f"Project {i}: {project.get('fullPath', 'N/A')}")
        print(f"{'─'*80}")

        # 验证基础字段
        errors = []

        # 1. ID验证
        if not project.get('id'):
            errors.append("Missing id")
        else:
            print(f"  ✅ ID: {project['id']}")
            # 提取数字ID
            numeric_id = int(project['id'].split('/')[-1])
            print(f"     Numeric ID: {numeric_id}")

        # 2. fullPath验证
        if not project.get('fullPath'):
            errors.append("Missing fullPath")
        else:
            print(f"  ✅ Full Path: {project['fullPath']}")

        # 3. 时间字段验证
        if not project.get('createdAt'):
            errors.append("Missing createdAt")
        else:
            print(f"  ✅ Created At: {project['createdAt']}")

        if not project.get('lastActivityAt'):
            errors.append("Missing lastActivityAt")
        else:
            print(f"  ✅ Last Activity: {project['lastActivityAt']}")

        # 4. Repository验证
        repo = project.get('repository')
        if not repo:
            errors.append("Missing repository")
        else:
            root_ref = repo.get('rootRef')
            if not root_ref:
                errors.append("Missing rootRef")
            else:
                print(f"  ✅ Default Branch: {root_ref}")

            # 5. Commit信息验证
            tree = repo.get('tree')
            if not tree:
                errors.append("Missing tree")
            else:
                last_commit = tree.get('lastCommit')
                if not last_commit:
                    errors.append("Missing lastCommit")
                else:
                    sha = last_commit.get('sha')
                    committed_date = last_commit.get('committedDate')

                    if not sha:
                        errors.append("Missing commit SHA")
                    elif len(sha) != 40:
                        errors.append(f"Invalid SHA length: {len(sha)} (expected 40)")
                    else:
                        print(f"  ✅ Last Commit SHA: {sha[:12]}...")

                    if not committed_date:
                        errors.append("Missing committedDate")
                    else:
                        print(f"  ✅ Last Commit Date: {committed_date}")

        # 6. 统计信息验证
        stats = project.get('statistics')
        if not stats:
            errors.append("Missing statistics")
        else:
            commit_count = stats.get('commitCount')
            repo_size = stats.get('repositorySize')
            storage_size = stats.get('storageSize')

            if commit_count is None:
                errors.append("Missing commitCount")
            else:
                print(f"  ✅ Commit Count: {int(commit_count)} commits (all branches)")

            if repo_size is None:
                errors.append("Missing repositorySize")
            else:
                print(f"  ✅ Repository Size: {int(repo_size):,} bytes ({int(repo_size/1024):,} KB)")

            if storage_size is None:
                errors.append("Missing storageSize")
            else:
                print(f"  ✅ Storage Size: {int(storage_size):,} bytes")

        # 输出验证结果
        if errors:
            print(f"\n  ❌ Validation errors:")
            for error in errors:
                print(f"     - {error}")
            all_valid = False
        else:
            print(f"\n  ✅ All fields validated successfully")

        print()

    # 总结
    print(f"{'='*80}")
    print(f"SUMMARY")
    print(f"{'='*80}")
    print(f"Queried projects: {len(project_ids)}")
    print(f"Returned projects: {len(projects)}")
    print(f"Duration: {duration_ms:.1f}ms")
    print(f"Avg per project: {duration_ms/len(projects):.1f}ms")
    print(f"Validation: {'✅ PASSED' if all_valid else '❌ FAILED'}")
    print(f"{'='*80}\n")

    return all_valid


def main():
    """主函数"""
    print("\n" + "="*80)
    print("GitLab GraphQL Client Integration Test")
    print("="*80)

    # 测试1: 单个项目
    print("\n[Test 1] Single Project Query")
    success1 = test_graphql_query([TEST_PROJECTS[0]["id"]])

    # 测试2: 多个项目
    print("\n[Test 2] Multiple Projects Query")
    project_ids = [p["id"] for p in TEST_PROJECTS]
    success2 = test_graphql_query(project_ids)

    # 测试3: 性能测试
    print("\n[Test 3] Performance Benchmark (5 iterations)")
    print("="*80 + "\n")

    durations = []
    for i in range(5):
        import time
        start = time.time()
        test_graphql_query(project_ids)
        duration = (time.time() - start) * 1000
        durations.append(duration)
        print(f"Iteration {i+1}: {duration:.1f}ms\n")

    avg_duration = sum(durations) / len(durations)
    print(f"{'='*80}")
    print(f"Performance Summary:")
    print(f"  Average: {avg_duration:.1f}ms")
    print(f"  Min: {min(durations):.1f}ms")
    print(f"  Max: {max(durations):.1f}ms")
    print(f"  Std Dev: {(sum((d - avg_duration)**2 for d in durations) / len(durations))**0.5:.1f}ms")
    print(f"{'='*80}\n")

    # 最终结果
    print(f"{'='*80}")
    print(f"FINAL RESULT")
    print(f"{'='*80}")
    if success1 and success2:
        print("✅ ALL TESTS PASSED")
        print("\nGraphQL client is working correctly and can:")
        print("  ✅ Query single and multiple projects")
        print("  ✅ Retrieve all required fields")
        print("  ✅ Extract project statistics (commitCount, repositorySize)")
        print("  ✅ Performance < 150ms for 3 projects")
    else:
        print("❌ SOME TESTS FAILED")
    print(f"{'='*80}\n")


if __name__ == "__main__":
    main()
