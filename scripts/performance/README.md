# Performance Test Scripts

This directory contains performance comparison scripts for different methods of fetching GitLab project information.

## Test Scripts

### 1. test-git-vs-api.sh
Bash script comparing git ls-remote vs GitLab REST API performance.

**Usage:**
```bash
./test-git-vs-api.sh
```

**Methods tested:**
- git ls-remote (Git protocol)
- GitLab API - 2 calls (get project + get branch)
- GitLab API - 1 call (direct branch query)

### 2. test_git_vs_api.py
Python script with detailed statistics for git vs API comparison.

**Requirements:**
```bash
pip install requests
```

**Usage:**
```bash
python3 test_git_vs_api.py
```

### 3. test_all_methods.py
Comprehensive performance comparison of 6 different methods.

**Requirements:**
```bash
pip install requests
```

**Usage:**
```bash
python3 test_all_methods.py
```

**Methods tested:**
1. git ls-remote (sequential)
2. REST API - 2 calls per project
3. REST API - 1 call per project
4. GraphQL API - batch query
5. GraphQL API - by IDs (recommended)
6. GraphQL API - aliases

## Test Results

Detailed analysis and recommendations can be found in:
- `docs/analysis/api-methods-comparison.md`

## Configuration

All scripts use the following default configuration:
- GitLab URL: http://localhost:8000
- Token: Set in `.env` file as `SOURCE_GITLAB_TOKEN`
- Test projects: devops/gitlab-mirror, arch/test-spring-app1, ai/test-node-app2

Modify the configuration variables at the top of each script to test against different GitLab instances or projects.
