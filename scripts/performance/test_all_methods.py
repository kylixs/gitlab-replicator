#!/usr/bin/env python3
"""
Comprehensive performance comparison for fetching GitLab project commit info

Compares 5 different methods:
1. git ls-remote (one by one)
2. GitLab REST API - 2 calls per project (get project + get branch)
3. GitLab REST API - 1 call per project (direct branch query)
4. GitLab GraphQL API - batch query (all projects in one request)
5. GitLab GraphQL API - optimized batch (with statistics)
"""

import subprocess
import time
import requests
import statistics
from typing import List, Tuple, Dict
import json


# Configuration
SOURCE_GITLAB_URL = "http://localhost:8000"
SOURCE_GITLAB_TOKEN = "glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq"

# Test projects
TEST_PROJECTS = [
    {"id": "1", "path": "devops/gitlab-mirror", "branch": "main"},
    {"id": "16", "path": "arch/test-spring-app1", "branch": "master"},
    {"id": "17", "path": "ai/test-node-app2", "branch": "master"},
]

NUM_RUNS = 5


def test_git_ls_remote_batch() -> Tuple[List[float], Dict]:
    """Test git ls-remote for multiple projects (sequential)"""
    times = []
    results = {}

    for run in range(NUM_RUNS):
        start = time.time()

        for project in TEST_PROJECTS:
            git_url = f"http://root:{SOURCE_GITLAB_TOKEN}@localhost:8000/{project['path']}.git"
            try:
                result = subprocess.run(
                    ["git", "ls-remote", git_url, "HEAD"],
                    capture_output=True,
                    text=True,
                    timeout=5
                )
                if result.returncode == 0 and result.stdout:
                    commit_sha = result.stdout.split()[0]
                    results[project['id']] = commit_sha
            except subprocess.TimeoutExpired:
                pass

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, results


def test_rest_api_2calls_batch() -> Tuple[List[float], Dict]:
    """Test REST API with 2 calls per project (sequential)"""
    times = []
    results = {}
    headers = {"PRIVATE-TOKEN": SOURCE_GITLAB_TOKEN}

    for run in range(NUM_RUNS):
        start = time.time()

        for project in TEST_PROJECTS:
            # Call 1: Get project
            resp1 = requests.get(
                f"{SOURCE_GITLAB_URL}/api/v4/projects/{project['id']}",
                headers=headers
            )

            if resp1.status_code == 200:
                default_branch = resp1.json().get('default_branch')

                # Call 2: Get branch
                resp2 = requests.get(
                    f"{SOURCE_GITLAB_URL}/api/v4/projects/{project['id']}/repository/branches/{default_branch}",
                    headers=headers
                )

                if resp2.status_code == 200:
                    commit_sha = resp2.json().get('commit', {}).get('id')
                    results[project['id']] = commit_sha

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, results


def test_rest_api_1call_batch() -> Tuple[List[float], Dict]:
    """Test REST API with 1 call per project (sequential, optimized)"""
    times = []
    results = {}
    headers = {"PRIVATE-TOKEN": SOURCE_GITLAB_TOKEN}

    for run in range(NUM_RUNS):
        start = time.time()

        for project in TEST_PROJECTS:
            # Single call: Direct branch query
            resp = requests.get(
                f"{SOURCE_GITLAB_URL}/api/v4/projects/{project['id']}/repository/branches/{project['branch']}",
                headers=headers
            )

            if resp.status_code == 200:
                commit_sha = resp.json().get('commit', {}).get('id')
                results[project['id']] = commit_sha

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, results


def test_graphql_batch() -> Tuple[List[float], Dict]:
    """Test GraphQL API - batch query all projects at once"""
    times = []
    results = {}
    headers = {
        "PRIVATE-TOKEN": SOURCE_GITLAB_TOKEN,
        "Content-Type": "application/json"
    }

    # Build GraphQL query for multiple projects
    # Note: GraphQL in GitLab requires specific query structure
    query = """
    query {
      projects {
        nodes {
          id
          fullPath
          repository {
            rootRef
            tree {
              lastCommit {
                sha
              }
            }
          }
        }
      }
    }
    """

    for run in range(NUM_RUNS):
        start = time.time()

        try:
            resp = requests.post(
                f"{SOURCE_GITLAB_URL}/api/graphql",
                headers=headers,
                json={"query": query}
            )

            if resp.status_code == 200:
                data = resp.json()
                if 'data' in data and 'projects' in data['data']:
                    # Only populate results on first successful run
                    if not results:
                        for project_node in data['data']['projects']['nodes']:
                            # Extract commit SHA from each project
                            if project_node.get('repository') and project_node['repository'].get('tree'):
                                commit = project_node['repository']['tree'].get('lastCommit')
                                if commit:
                                    # Match by path
                                    for test_proj in TEST_PROJECTS:
                                        if project_node['fullPath'] == test_proj['path']:
                                            results[test_proj['id']] = commit.get('sha')
        except Exception as e:
            print(f"    GraphQL error: {e}")

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, results


def test_graphql_paginated() -> Tuple[List[float], Dict]:
    """Test GraphQL API - query by IDs (scalable, realistic for production)"""
    times = []
    results = {}
    headers = {
        "PRIVATE-TOKEN": SOURCE_GITLAB_TOKEN,
        "Content-Type": "application/json"
    }

    # Build GraphQL query using project IDs (most realistic for production)
    # In real scenario, we get project IDs from database first
    # Note: GitLab GraphQL uses "gid://gitlab/Project/ID" format
    query = """
    query($ids: [ID!]) {
      projects(ids: $ids) {
        nodes {
          id
          fullPath
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
            repositorySize
            commitCount
            storageSize
          }
          lastActivityAt
        }
      }
    }
    """

    # Build list of project global IDs (GitLab format)
    project_gids = [f"gid://gitlab/Project/{proj['id']}" for proj in TEST_PROJECTS]

    for run in range(NUM_RUNS):
        start = time.time()

        try:
            resp = requests.post(
                f"{SOURCE_GITLAB_URL}/api/graphql",
                headers=headers,
                json={
                    "query": query,
                    "variables": {"ids": project_gids}
                }
            )

            if resp.status_code == 200:
                data = resp.json()
                # Debug on first run
                if run == 0:
                    import json
                    print(f"\n    DEBUG: Full response: {json.dumps(data)[:500]}")
                    print(f"\n    DEBUG: Response has data? {'data' in data}")
                    if 'data' in data:
                        print(f"    DEBUG: Data has projects? {'projects' in data['data']}")
                        if 'projects' in data['data']:
                            print(f"    DEBUG: Number of nodes: {len(data['data']['projects']['nodes'])}")
                if 'data' in data and 'projects' in data['data']:
                    if not results:
                        for project_node in data['data']['projects']['nodes']:
                            # Match by gitlab ID
                            for test_proj in TEST_PROJECTS:
                                expected_gid = f"gid://gitlab/Project/{test_proj['id']}"
                                if project_node['id'] == expected_gid:
                                    repo = project_node.get('repository')
                                    if repo:
                                        tree = repo.get('tree')
                                        if tree and tree.get('lastCommit'):
                                            results[test_proj['id']] = {
                                                'sha': tree['lastCommit'].get('sha'),
                                                'committedDate': tree['lastCommit'].get('committedDate'),
                                                'rootRef': repo.get('rootRef'),
                                                'commitCount': project_node.get('statistics', {}).get('commitCount') if project_node.get('statistics') else None,
                                                'repositorySize': project_node.get('statistics', {}).get('repositorySize') if project_node.get('statistics') else None,
                                                'lastActivityAt': project_node.get('lastActivityAt')
                                            }
        except Exception as e:
            import traceback
            print(f"    GraphQL by IDs error: {e}")
            traceback.print_exc()

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, results


def test_graphql_optimized() -> Tuple[List[float], Dict]:
    """Test GraphQL API - optimized with specific project IDs and statistics"""
    times = []
    results = {}
    headers = {
        "PRIVATE-TOKEN": SOURCE_GITLAB_TOKEN,
        "Content-Type": "application/json"
    }

    # Build optimized GraphQL query with project-specific queries
    # Using aliases to query specific projects
    query = """
    query {
      project1: project(fullPath: "devops/gitlab-mirror") {
        id
        fullPath
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
          repositorySize
          commitCount
          storageSize
        }
        lastActivityAt
      }
      project2: project(fullPath: "arch/test-spring-app1") {
        id
        fullPath
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
          repositorySize
          commitCount
          storageSize
        }
        lastActivityAt
      }
      project3: project(fullPath: "ai/test-node-app2") {
        id
        fullPath
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
          repositorySize
          commitCount
          storageSize
        }
        lastActivityAt
      }
    }
    """

    for run in range(NUM_RUNS):
        start = time.time()

        try:
            resp = requests.post(
                f"{SOURCE_GITLAB_URL}/api/graphql",
                headers=headers,
                json={"query": query}
            )

            if resp.status_code == 200:
                data = resp.json()
                # Debug: print response on first run
                if run == 0:
                    import json
                    print(f"\n    DEBUG: GraphQL Aliases response has data? {'data' in data}")
                    if 'data' in data:
                        print(f"    DEBUG: Has project1? {'project1' in data['data']}")
                        if 'project1' in data['data']:
                            print(f"    DEBUG: project1 type: {type(data['data']['project1'])}")
                            if data['data']['project1']:
                                print(f"    DEBUG: project1 has repository? {'repository' in data['data']['project1']}")
                if 'data' in data:
                    # Extract data from each aliased query (only save on first successful run)
                    if not results:
                        for i, test_proj in enumerate(TEST_PROJECTS, 1):
                            project_data = data['data'].get(f'project{i}')
                            if project_data:
                                repo = project_data.get('repository')
                                if repo:
                                    tree = repo.get('tree')
                                    if tree:
                                        last_commit = tree.get('lastCommit')
                                        if last_commit:
                                            results[test_proj['id']] = {
                                                'sha': last_commit.get('sha'),
                                                'committedDate': last_commit.get('committedDate'),
                                                'rootRef': repo.get('rootRef'),
                                                'commitCount': project_data.get('statistics', {}).get('commitCount') if project_data.get('statistics') else None,
                                                'repositorySize': project_data.get('statistics', {}).get('repositorySize') if project_data.get('statistics') else None,
                                                'lastActivityAt': project_data.get('lastActivityAt')
                                            }
        except Exception as e:
            import traceback
            print(f"    GraphQL optimized error: {e}")
            traceback.print_exc()

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, results


def validate_results(method_name: str, results: Dict) -> bool:
    """Validate query results meet requirements"""
    if not results:
        print(f"  ❌ FAILED: No results returned")
        return False

    expected_count = len(TEST_PROJECTS)
    if len(results) != expected_count:
        print(f"  ❌ FAILED: Expected {expected_count} projects, got {len(results)}")
        return False

    # Validate each project has required data
    all_valid = True
    for proj in TEST_PROJECTS:
        proj_id = proj['id']
        if proj_id not in results:
            print(f"  ❌ FAILED: Missing project {proj_id} ({proj['path']})")
            all_valid = False
            continue

        result = results[proj_id]
        # For dict results (GraphQL), check SHA field
        if isinstance(result, dict):
            sha = result.get('sha')
            if not sha or len(sha) != 40:
                print(f"  ❌ FAILED: Project {proj_id} invalid SHA: {sha}")
                all_valid = False
        # For string results (git/REST), check directly
        else:
            if not result or len(result) != 40:
                print(f"  ❌ FAILED: Project {proj_id} invalid SHA: {result}")
                all_valid = False

    if all_valid:
        print(f"  ✅ PASSED: All {expected_count} projects returned with valid SHAs")

    return all_valid


def print_stats(name: str, times: List[float], results: Dict):
    """Print statistics for a test"""
    if not times:
        print(f"\n{name}: No successful runs")
        return None

    avg = statistics.mean(times)
    median = statistics.median(times)
    stdev = statistics.stdev(times) if len(times) > 1 else 0
    min_time = min(times)
    max_time = max(times)

    print(f"\n{name}:")
    print(f"  Projects: {len(TEST_PROJECTS)}")
    print(f"  Runs:     {len(times)}/{NUM_RUNS}")
    print(f"  Average:  {avg:7.1f}ms")
    print(f"  Median:   {median:7.1f}ms")
    print(f"  Std Dev:  {stdev:7.1f}ms")
    print(f"  Min:      {min_time:7.1f}ms")
    print(f"  Max:      {max_time:7.1f}ms")

    # Validate results
    print(f"\n  Results Validation:")
    is_valid = validate_results(name, results)

    # Show detailed results for each project
    if results:
        print(f"\n  Detailed Results:")
        for proj in TEST_PROJECTS:
            proj_id = proj['id']
            proj_path = proj['path']

            if proj_id not in results:
                print(f"    [{proj_id}] {proj_path:30s} ❌ MISSING")
                continue

            result = results[proj_id]

            if isinstance(result, dict):
                # GraphQL results with rich data
                sha = result.get('sha', 'N/A')
                commit_date = result.get('committedDate', 'N/A')
                commit_count = result.get('commitCount', 'N/A')
                repo_size = result.get('repositorySize', 'N/A')
                root_ref = result.get('rootRef', 'N/A')

                # Format commit count (convert float to int)
                commit_count_str = str(int(commit_count)) if commit_count != 'N/A' and commit_count is not None else 'N/A'
                # Format repository size
                repo_size_str = f"{int(repo_size/1024)}KB" if repo_size != 'N/A' and repo_size is not None else 'N/A'

                print(f"    [{proj_id}] {proj_path:30s} ✅ SHA={sha[:12]}... "
                      f"Branch={root_ref} Commits={commit_count_str} Size={repo_size_str}")
            else:
                # Simple SHA results (git/REST)
                sha = result if result else 'N/A'
                print(f"    [{proj_id}] {proj_path:30s} ✅ SHA={sha[:12]}...")

    return avg


def main():
    print("=" * 80)
    print("Comprehensive GitLab API Performance Comparison")
    print("=" * 80)
    print(f"\nConfiguration:")
    print(f"  GitLab URL:    {SOURCE_GITLAB_URL}")
    print(f"  Test Projects: {len(TEST_PROJECTS)}")
    print(f"  Test Runs:     {NUM_RUNS}")
    print(f"\nProjects:")
    for p in TEST_PROJECTS:
        print(f"  - {p['path']} (ID: {p['id']}, branch: {p['branch']})")

    results = {}

    # Test 1: git ls-remote
    print("\n" + "=" * 80)
    print("1. git ls-remote (sequential, one by one)")
    print("=" * 80)
    git_times, git_results = test_git_ls_remote_batch()
    results['git'] = print_stats("git ls-remote", git_times, git_results)

    # Test 2: REST API (2 calls)
    print("\n" + "=" * 80)
    print("2. GitLab REST API - 2 calls per project (get project + branch)")
    print("=" * 80)
    rest2_times, rest2_results = test_rest_api_2calls_batch()
    results['rest2'] = print_stats("REST API (2 calls/project)", rest2_times, rest2_results)

    # Test 3: REST API (1 call)
    print("\n" + "=" * 80)
    print("3. GitLab REST API - 1 call per project (direct branch query)")
    print("=" * 80)
    rest1_times, rest1_results = test_rest_api_1call_batch()
    results['rest1'] = print_stats("REST API (1 call/project)", rest1_times, rest1_results)

    # Test 4: GraphQL batch
    print("\n" + "=" * 80)
    print("4. GitLab GraphQL API - batch query (all projects in one request)")
    print("=" * 80)
    gql_times, gql_results = test_graphql_batch()
    results['graphql'] = print_stats("GraphQL Batch", gql_times, gql_results)

    # Test 5: GraphQL by IDs (scalable)
    print("\n" + "=" * 80)
    print("5. GitLab GraphQL API - query by IDs (scalable, production-ready)")
    print("=" * 80)
    gql_pag_times, gql_pag_results = test_graphql_paginated()
    results['graphql_pag'] = print_stats("GraphQL by IDs", gql_pag_times, gql_pag_results)

    # Test 6: GraphQL optimized (aliases - NOT scalable for 100+ projects)
    print("\n" + "=" * 80)
    print("6. GitLab GraphQL API - aliases (NOT scalable - for small datasets only)")
    print("=" * 80)
    gql_opt_times, gql_opt_results = test_graphql_optimized()
    results['graphql_opt'] = print_stats("GraphQL Aliases (not scalable)", gql_opt_times, gql_opt_results)

    # Summary
    print("\n" + "=" * 80)
    print("PERFORMANCE SUMMARY")
    print("=" * 80)

    print(f"\n{'Method':<40} {'Avg Time':>12} {'Per Project':>15} {'vs Baseline':>15}")
    print("-" * 80)

    baseline = results['git']
    for method_name, method_key in [
        ("git ls-remote (sequential)", 'git'),
        ("REST API (2 calls/project)", 'rest2'),
        ("REST API (1 call/project)", 'rest1'),
        ("GraphQL Batch (1 call total)", 'graphql'),
        ("GraphQL Paginated (scalable)", 'graphql_pag'),
        ("GraphQL Aliases (not scalable)", 'graphql_opt'),
    ]:
        avg = results.get(method_key)
        if avg:
            per_project = avg / len(TEST_PROJECTS)
            if baseline and baseline > 0:
                improvement = ((baseline - avg) / baseline) * 100
                print(f"{method_name:<40} {avg:>10.1f}ms {per_project:>13.1f}ms {improvement:>+13.1f}%")
            else:
                print(f"{method_name:<40} {avg:>10.1f}ms {per_project:>13.1f}ms {'N/A':>15}")

    # Extrapolation to 14 projects
    print("\n" + "=" * 80)
    print("EXTRAPOLATION TO 14 PROJECTS")
    print("=" * 80)

    print(f"\n{'Method':<40} {'Est. Time':>12} {'Savings':>15}")
    print("-" * 80)

    git_14 = results['git'] / len(TEST_PROJECTS) * 14 if results['git'] else 0

    for method_name, method_key in [
        ("git ls-remote (sequential)", 'git'),
        ("REST API (2 calls/project)", 'rest2'),
        ("REST API (1 call/project)", 'rest1'),
        ("GraphQL Batch (1 call total)", 'graphql'),
        ("GraphQL Paginated (scalable)", 'graphql_pag'),
        ("GraphQL Aliases (not scalable)", 'graphql_opt'),
    ]:
        avg = results.get(method_key)
        if avg:
            # For sequential methods, scale linearly
            if method_key in ['git', 'rest2', 'rest1']:
                est_14 = avg / len(TEST_PROJECTS) * 14
            # For batch methods, assume constant overhead + small per-project cost
            else:
                est_14 = avg + (avg * 0.1 * (14 - len(TEST_PROJECTS)) / len(TEST_PROJECTS))

            savings = git_14 - est_14
            print(f"{method_name:<40} {est_14:>10.0f}ms {savings:>+13.0f}ms")

    # Recommendations
    print("\n" + "=" * 80)
    print("RECOMMENDATIONS")
    print("=" * 80)

    fastest = min(results.items(), key=lambda x: x[1] if x[1] else float('inf'))

    print(f"\n✅ Fastest method: {fastest[0].upper()} ({fastest[1]:.1f}ms for {len(TEST_PROJECTS)} projects)")

    print("\n💡 Key Findings:")
    if results.get('graphql_opt'):
        improvement_vs_git = ((results['git'] - results['graphql_opt']) / results['git']) * 100
        print(f"  • GraphQL Optimized is {improvement_vs_git:.1f}% faster than git ls-remote")

        est_git_14 = results['git'] / len(TEST_PROJECTS) * 14
        est_gql_14 = results['graphql_opt'] + (results['graphql_opt'] * 0.1 * 11 / 3)
        print(f"  • For 14 projects: GraphQL ~{est_gql_14:.0f}ms vs git ~{est_git_14:.0f}ms")
        print(f"  • Saves ~{est_git_14 - est_gql_14:.0f}ms ({(est_git_14 - est_gql_14)/1000:.2f}s)")

    if results.get('graphql') and results.get('rest1'):
        if results['graphql'] < results['rest1']:
            improvement = ((results['rest1'] - results['graphql']) / results['rest1']) * 100
            print(f"  • GraphQL batch is {improvement:.1f}% faster than REST API (1 call/project)")
            print(f"  • GraphQL makes 1 API call vs {len(TEST_PROJECTS)} REST calls")

    print("\n📊 API Call Comparison for 14 projects:")
    print(f"  • git ls-remote:         14 calls (sequential)")
    print(f"  • REST API (2 calls):    28 calls (sequential)")
    print(f"  • REST API (1 call):     14 calls (sequential)")
    print(f"  • GraphQL Batch:         1 call (batch)")
    print(f"  • GraphQL Paginated:     1 call (with variables, scalable)")
    print(f"  • GraphQL Aliases:       1 call (for 14 projects)")

    print("\n💡 For 100+ projects - Batching Strategy:")
    print(f"  • REST API (1 call):     100 calls (can parallel 10 threads = ~10 batches)")
    print(f"  • GraphQL Aliases:       5 batches × 20 projects = 5 calls")
    print(f"  • GraphQL Paginated:     1 call (if API supports filtering by IDs)")
    print(f"")
    print(f"  Recommended: GraphQL Aliases with batching (20 projects per call)")
    print(f"  Fallback: Parallel REST API (1 call/project) with thread pool")

    print("\n" + "=" * 80)


if __name__ == "__main__":
    main()
