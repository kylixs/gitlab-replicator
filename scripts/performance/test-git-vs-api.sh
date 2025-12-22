#!/bin/bash

# Test script to compare performance of git ls-remote vs GitLab API

echo "=== Git ls-remote vs GitLab API Performance Comparison ==="
echo ""

# Configuration
SOURCE_GITLAB_URL="http://localhost:8000"
SOURCE_GITLAB_TOKEN="glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq"
PROJECT_PATH="devops/gitlab-mirror"
PROJECT_ID="1"

# Test git ls-remote
echo "1. Testing git ls-remote..."
echo "   Command: git ls-remote http://root:${SOURCE_GITLAB_TOKEN}@localhost:8000/${PROJECT_PATH}.git HEAD"
echo ""

# Run git ls-remote 5 times and calculate average
total_time=0
for i in {1..5}; do
    start=$(python3 -c 'import time; print(int(time.time() * 1000))')
    result=$(git ls-remote "http://root:${SOURCE_GITLAB_TOKEN}@localhost:8000/${PROJECT_PATH}.git" HEAD 2>&1)
    end=$(python3 -c 'import time; print(int(time.time() * 1000))')
    duration=$((end - start))
    total_time=$((total_time + duration))

    if [ $i -eq 1 ]; then
        commit_sha=$(echo "$result" | awk '{print $1}')
        echo "   Result: $commit_sha"
        echo ""
    fi
    echo "   Run $i: ${duration}ms"
done
avg_git=$((total_time / 5))
echo "   Average: ${avg_git}ms"
echo ""

# Test GitLab API - Get default branch
echo "2. Testing GitLab API (2 API calls - project + branch)..."
echo "   API 1: GET /api/v4/projects/${PROJECT_ID}"
echo "   API 2: GET /api/v4/projects/${PROJECT_ID}/repository/branches/{default_branch}"
echo ""

total_time=0
for i in {1..5}; do
    start=$(python3 -c 'import time; print(int(time.time() * 1000))')

    # First API call - get project to find default branch
    project_response=$(curl -s -H "PRIVATE-TOKEN: ${SOURCE_GITLAB_TOKEN}" \
        "${SOURCE_GITLAB_URL}/api/v4/projects/${PROJECT_ID}")
    default_branch=$(echo "$project_response" | jq -r '.default_branch')

    # Second API call - get branch details
    branch_response=$(curl -s -H "PRIVATE-TOKEN: ${SOURCE_GITLAB_TOKEN}" \
        "${SOURCE_GITLAB_URL}/api/v4/projects/${PROJECT_ID}/repository/branches/${default_branch}")

    end=$(python3 -c 'import time; print(int(time.time() * 1000))')
    duration=$((end - start))
    total_time=$((total_time + duration))

    if [ $i -eq 1 ]; then
        api_commit_sha=$(echo "$branch_response" | jq -r '.commit.id')
        echo "   Result: $api_commit_sha"
        echo ""
    fi
    echo "   Run $i: ${duration}ms"
done
avg_api_2calls=$((total_time / 5))
echo "   Average: ${avg_api_2calls}ms"
echo ""

# Test GitLab API - Direct branch query (optimized)
echo "3. Testing GitLab API (1 API call - direct branch query)..."
echo "   API: GET /api/v4/projects/${PROJECT_ID}/repository/branches/main"
echo ""

total_time=0
for i in {1..5}; do
    start=$(python3 -c 'import time; print(int(time.time() * 1000))')

    # Single API call - directly query known default branch
    branch_response=$(curl -s -H "PRIVATE-TOKEN: ${SOURCE_GITLAB_TOKEN}" \
        "${SOURCE_GITLAB_URL}/api/v4/projects/${PROJECT_ID}/repository/branches/main")

    end=$(python3 -c 'import time; print(int(time.time() * 1000))')
    duration=$((end - start))
    total_time=$((total_time + duration))

    if [ $i -eq 1 ]; then
        api_commit_sha=$(echo "$branch_response" | jq -r '.commit.id')
        echo "   Result: $api_commit_sha"
        echo ""
    fi
    echo "   Run $i: ${duration}ms"
done
avg_api_1call=$((total_time / 5))
echo "   Average: ${avg_api_1call}ms"
echo ""

# Summary
echo "=== SUMMARY ==="
echo "git ls-remote:              ${avg_git}ms"
echo "GitLab API (2 calls):       ${avg_api_2calls}ms"
echo "GitLab API (1 call):        ${avg_api_1call}ms"
echo ""

# Calculate improvements
improvement_1=$((avg_api_2calls - avg_git))
improvement_2=$((avg_api_2calls - avg_api_1call))
improvement_3=$((avg_api_1call - avg_git))

echo "Performance Comparison:"
if [ $avg_git -lt $avg_api_2calls ]; then
    percent=$(echo "scale=1; ($improvement_1 * 100.0) / $avg_api_2calls" | bc)
    echo "  git ls-remote is ${improvement_1}ms faster than API (2 calls) - ${percent}% improvement"
else
    improvement_1=$((avg_git - avg_api_2calls))
    percent=$(echo "scale=1; ($improvement_1 * 100.0) / $avg_git" | bc)
    echo "  GitLab API (2 calls) is ${improvement_1}ms faster than git ls-remote - ${percent}% improvement"
fi

if [ $avg_api_1call -lt $avg_api_2calls ]; then
    percent=$(echo "scale=1; ($improvement_2 * 100.0) / $avg_api_2calls" | bc)
    echo "  GitLab API (1 call) is ${improvement_2}ms faster than API (2 calls) - ${percent}% improvement"
fi

if [ $avg_git -lt $avg_api_1call ]; then
    percent=$(echo "scale=1; ($improvement_3 * 100.0) / $avg_api_1call" | bc)
    echo "  git ls-remote is ${improvement_3}ms faster than API (1 call) - ${percent}% improvement"
else
    improvement_3=$((avg_api_1call - avg_git))
    percent=$(echo "scale=1; ($improvement_3 * 100.0) / $avg_git" | bc)
    echo "  GitLab API (1 call) is ${improvement_3}ms faster than git ls-remote - ${percent}% improvement"
fi

echo ""
echo "Recommendation:"
if [ $avg_git -lt $avg_api_1call ]; then
    echo "  ✅ Use git ls-remote for better performance"
    echo "  For 14 projects: $((avg_git * 14))ms vs $((avg_api_1call * 14))ms API (saves $((improvement_3 * 14))ms)"
else
    echo "  ✅ Use GitLab API (1 call) for better performance"
fi
