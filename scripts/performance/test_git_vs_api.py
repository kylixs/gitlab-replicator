#!/usr/bin/env python3
"""
Performance comparison: git ls-remote vs GitLab REST API

This script compares the performance of different methods to get the latest commit SHA:
1. git ls-remote (Git protocol)
2. GitLab API - 2 calls (get project + get branch)
3. GitLab API - 1 call (direct branch query)
"""

import subprocess
import time
import requests
import statistics
from typing import List, Tuple


# Configuration
SOURCE_GITLAB_URL = "http://localhost:8000"
SOURCE_GITLAB_TOKEN = "glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq"
PROJECT_PATH = "devops/gitlab-mirror"
PROJECT_ID = "1"
DEFAULT_BRANCH = "main"
NUM_RUNS = 10


def test_git_ls_remote() -> Tuple[List[float], str]:
    """Test git ls-remote performance"""
    times = []
    commit_sha = None

    git_url = f"http://root:{SOURCE_GITLAB_TOKEN}@localhost:8000/{PROJECT_PATH}.git"

    for i in range(NUM_RUNS):
        start = time.time()
        try:
            result = subprocess.run(
                ["git", "ls-remote", git_url, "HEAD"],
                capture_output=True,
                text=True,
                timeout=10
            )
            if result.returncode == 0:
                commit_sha = result.stdout.split()[0] if result.stdout else None
        except subprocess.TimeoutExpired:
            print(f"   Warning: Run {i+1} timed out")
            continue

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, commit_sha


def test_gitlab_api_2calls() -> Tuple[List[float], str]:
    """Test GitLab API with 2 calls (get project + get branch)"""
    times = []
    commit_sha = None

    headers = {"PRIVATE-TOKEN": SOURCE_GITLAB_TOKEN}

    for i in range(NUM_RUNS):
        start = time.time()

        # First API call - get project
        resp1 = requests.get(
            f"{SOURCE_GITLAB_URL}/api/v4/projects/{PROJECT_ID}",
            headers=headers
        )

        if resp1.status_code == 200:
            default_branch = resp1.json().get('default_branch')

            # Second API call - get branch
            resp2 = requests.get(
                f"{SOURCE_GITLAB_URL}/api/v4/projects/{PROJECT_ID}/repository/branches/{default_branch}",
                headers=headers
            )

            if resp2.status_code == 200:
                commit_sha = resp2.json().get('commit', {}).get('id')

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, commit_sha


def test_gitlab_api_1call() -> Tuple[List[float], str]:
    """Test GitLab API with 1 call (direct branch query)"""
    times = []
    commit_sha = None

    headers = {"PRIVATE-TOKEN": SOURCE_GITLAB_TOKEN}

    for i in range(NUM_RUNS):
        start = time.time()

        # Single API call - direct branch query
        resp = requests.get(
            f"{SOURCE_GITLAB_URL}/api/v4/projects/{PROJECT_ID}/repository/branches/{DEFAULT_BRANCH}",
            headers=headers
        )

        if resp.status_code == 200:
            commit_sha = resp.json().get('commit', {}).get('id')

        end = time.time()
        duration_ms = (end - start) * 1000
        times.append(duration_ms)

    return times, commit_sha


def print_stats(name: str, times: List[float], commit_sha: str):
    """Print statistics for a test"""
    if not times:
        print(f"\n{name}: No successful runs")
        return

    avg = statistics.mean(times)
    median = statistics.median(times)
    stdev = statistics.stdev(times) if len(times) > 1 else 0
    min_time = min(times)
    max_time = max(times)

    print(f"\n{name}:")
    print(f"  Commit SHA: {commit_sha[:12]}..." if commit_sha else "  Commit SHA: None")
    print(f"  Runs: {len(times)}/{NUM_RUNS}")
    print(f"  Average:  {avg:6.1f}ms")
    print(f"  Median:   {median:6.1f}ms")
    print(f"  Std Dev:  {stdev:6.1f}ms")
    print(f"  Min:      {min_time:6.1f}ms")
    print(f"  Max:      {max_time:6.1f}ms")

    return avg


def main():
    print("=" * 70)
    print("Git ls-remote vs GitLab API Performance Comparison")
    print("=" * 70)
    print(f"\nConfiguration:")
    print(f"  GitLab URL:    {SOURCE_GITLAB_URL}")
    print(f"  Project:       {PROJECT_PATH}")
    print(f"  Default Branch: {DEFAULT_BRANCH}")
    print(f"  Test Runs:     {NUM_RUNS}")

    # Test 1: git ls-remote
    print("\n" + "-" * 70)
    print("1. Testing git ls-remote...")
    print("-" * 70)
    git_times, git_sha = test_git_ls_remote()
    git_avg = print_stats("git ls-remote", git_times, git_sha)

    # Test 2: GitLab API (2 calls)
    print("\n" + "-" * 70)
    print("2. Testing GitLab API (2 calls: project + branch)...")
    print("-" * 70)
    api2_times, api2_sha = test_gitlab_api_2calls()
    api2_avg = print_stats("GitLab API (2 calls)", api2_times, api2_sha)

    # Test 3: GitLab API (1 call)
    print("\n" + "-" * 70)
    print("3. Testing GitLab API (1 call: direct branch query)...")
    print("-" * 70)
    api1_times, api1_sha = test_gitlab_api_1call()
    api1_avg = print_stats("GitLab API (1 call)", api1_times, api1_sha)

    # Summary
    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)

    print(f"\n{'Method':<30} {'Avg Time':>12} {'vs git':>15} {'vs API(2)':>15}")
    print("-" * 70)
    print(f"{'git ls-remote':<30} {git_avg:>10.1f}ms {'baseline':>15} {'':<15}")

    if git_avg > 0:
        api2_vs_git = ((git_avg - api2_avg) / git_avg) * 100
        print(f"{'GitLab API (2 calls)':<30} {api2_avg:>10.1f}ms {api2_vs_git:>+13.1f}% {'baseline':>15}")

        api1_vs_git = ((git_avg - api1_avg) / git_avg) * 100
        api1_vs_api2 = ((api2_avg - api1_avg) / api2_avg) * 100
        print(f"{'GitLab API (1 call)':<30} {api1_avg:>10.1f}ms {api1_vs_git:>+13.1f}% {api1_vs_api2:>+13.1f}%")

    # Recommendation
    print("\n" + "=" * 70)
    print("RECOMMENDATION")
    print("=" * 70)

    fastest = min([(git_avg, "git ls-remote"), (api2_avg, "GitLab API (2 calls)"), (api1_avg, "GitLab API (1 call)")],
                  key=lambda x: x[0])

    print(f"\n✅ Fastest method: {fastest[1]} ({fastest[0]:.1f}ms)")

    # Calculate total time for 14 projects
    print(f"\n📊 Estimated time for 14 projects:")
    print(f"  git ls-remote:        {git_avg * 14:>8.0f}ms")
    print(f"  GitLab API (2 calls): {api2_avg * 14:>8.0f}ms (saves {(git_avg - api2_avg) * 14:>6.0f}ms)")
    print(f"  GitLab API (1 call):  {api1_avg * 14:>8.0f}ms (saves {(git_avg - api1_avg) * 14:>6.0f}ms)")

    print("\n💡 Key Findings:")
    if api1_avg < git_avg:
        improvement = ((git_avg - api1_avg) / git_avg) * 100
        print(f"  • GitLab API (1 call) is {improvement:.1f}% faster than git ls-remote")
        print(f"  • For 14 projects: saves {(git_avg - api1_avg) * 14:.0f}ms ({(git_avg - api1_avg) * 14 / 1000:.1f}s)")

    if api1_avg < api2_avg:
        improvement = ((api2_avg - api1_avg) / api2_avg) * 100
        print(f"  • Skipping branches list API saves {improvement:.1f}% time")

    print("\n" + "=" * 70)


if __name__ == "__main__":
    main()
