# Long-Running E2E Simulation Examples

## Quick Start

### 1. Verify Environment

```bash
# Check GitLab instances are running
curl -s http://localhost:8000/api/v4/version | jq
curl -s http://localhost:9000/api/v4/version | jq

# Check test group exists
curl -s http://localhost:8000/api/v4/groups/test-group \
  -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" | jq

# If not exists, create it
curl -s http://localhost:8000/api/v4/groups \
  -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Group", "path": "test-group"}' | jq
```

### 2. Quick Test (6 minutes)

```bash
# Run quick test to verify everything works
npx ts-node tests/e2e/quick-test.ts

# Expected output:
# ================================================================================
# QUICK TEST MODE - Running for ~6 minutes
# ================================================================================
#
# [2025-12-28T...] [INFO] [Elapsed: 0m] Initializing test project...
# [2025-12-28T...] [INFO] [Elapsed: 0m] Project initialized: test-group/long-running-test (ID: 123)
# [2025-12-28T...] [INFO] [Elapsed: 0m] Starting operation scenarios...
# [2025-12-28T...] [INFO] [Elapsed: 0m] ✓ Created commit abc123f on branch 'main'
# [2025-12-28T...] [INFO] [Elapsed: 1m] ✓ Created branch 'feature/test-1735344300000'
# ...
```

### 3. Full Test (3 days)

```bash
# Start full simulation
./tests/e2e/run-simulation.sh start

# Output:
# ℹ Starting long-running E2E simulation...
# ℹ Log file: /path/to/logs/simulation.log
# ✓ Simulation started successfully (PID: 12345)
# ℹ View logs: tail -f /path/to/logs/simulation.log
# ℹ Stop simulation: ./tests/e2e/run-simulation.sh stop
```

## Monitoring the Simulation

### View Real-Time Logs

```bash
# Follow logs
./tests/e2e/run-simulation.sh logs

# Or use tail directly
tail -f logs/simulation.log
```

### Check Status

```bash
./tests/e2e/run-simulation.sh status

# Output:
# ℹ Simulation Status
#
# ✓ Status: Running (PID: 12345)
#
# Process Info:
#   PID  PPID USER     %CPU %MEM     ELAPSED CMD
# 12345     1 user      0.5  0.3    02:15:30 node ...
#
# Recent Log (last 20 lines):
# -----------------------------------------------------------
# [2025-12-28T...] [INFO] [Elapsed: 135m] ✓ Created commit ...
# [2025-12-28T...] [INFO] [Elapsed: 140m] ✓ Created branch ...
# ...
```

### View Statistics

```bash
./tests/e2e/run-simulation.sh summary

# Output:
# ℹ Simulation Summary
#
# Operations:
#   Commits:   47
#   Branches:  12
#   Tags:      3
#   MRs:       8
#   Merged:    8
#   Deleted:   5
#
# Errors:    0
#
# Last Status Summary:
# -----------------------------------------------------------
# STATUS SUMMARY - Elapsed: 2.5h (150m)
# Total Operations: 75
# Open Branches: 7
# Merged Branches: 3
# Tags Created: 3
# Merge Requests: 8
# Errors: 0
# -----------------------------------------------------------
```

## Monitoring GitLab Mirror System

### Check Sync Status

```bash
# Get project sync status
curl -s "http://localhost:9999/api/sync/projects?projectKey=test-group/long-running-test" \
  -H "Authorization: Bearer $GITLAB_MIRROR_API_KEY" | jq

# Example output:
# {
#   "success": true,
#   "data": {
#     "items": [
#       {
#         "id": 123,
#         "projectKey": "test-group/long-running-test",
#         "syncStatus": "active",
#         "lastSyncAt": "2025-12-28T01:30:00Z",
#         "delaySeconds": 5,
#         "delayFormatted": "5s"
#       }
#     ]
#   }
# }
```

### Check Sync Events

```bash
# Get recent sync events
curl -s "http://localhost:9999/api/sync/events?projectKey=test-group/long-running-test&limit=10" \
  -H "Authorization: Bearer $GITLAB_MIRROR_API_KEY" | jq

# Example output shows commits, branches, tags being synced
```

### View Dashboard

```bash
# Open web UI
open http://localhost:3000/dashboard

# Check 7-day trend (should show increasing activity)
curl -s "http://localhost:9999/api/dashboard/trend?range=7d" \
  -H "Authorization: Bearer $GITLAB_MIRROR_API_KEY" | jq
```

### Verify Target GitLab

```bash
# Check branches on target
curl -s "http://localhost:9000/api/v4/projects/test-group%2Flong-running-test/repository/branches" \
  -H "PRIVATE-TOKEN: $TARGET_GITLAB_TOKEN" | jq '. | length'

# Check tags on target
curl -s "http://localhost:9000/api/v4/projects/test-group%2Flong-running-test/repository/tags" \
  -H "PRIVATE-TOKEN: $TARGET_GITLAB_TOKEN" | jq '. | length'

# Compare with source
curl -s "http://localhost:8000/api/v4/projects/test-group%2Flong-running-test/repository/branches" \
  -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" | jq '. | length'
```

## Example Timeline

Here's what happens during a typical run:

### Hour 1: Initial Setup

```
[00:00] ✓ Project initialized
[00:01] ✓ Created commit on 'main'
[00:05] ✓ Created branch 'feature/test-1'
[00:20] ✓ Created commit on 'main'
[00:30] ✓ Created tag 'v1.0.0'
[00:45] ✓ Created commit on 'feature/test-1'
```

### Hour 2: MR Activity

```
[01:00] ✓ Created branch 'feature/test-2'
[01:15] ✓ Created MR !1: feature/test-1 → main
[01:15] ✓ Merged MR !1
[01:30] ✓ Created commit on 'main'
[01:45] ✓ Created tag 'v1.0.1'
```

### Hour 3-4: Branch Cleanup

```
[03:15] ℹ Found 1 branches eligible for deletion (>= 2.0h old)
[03:15] ✓ Deleted merged branch 'feature/test-1' (age: 2.0h)
[03:30] ✓ Created branch 'feature/test-3'
[03:45] ✓ Created MR !2: feature/test-2 → main
[03:45] ✓ Merged MR !2
```

### Continuous Operation

This pattern continues for 3 days, creating a realistic workload:
- ~200-300 commits
- ~30-50 branches created
- ~20-35 MRs merged
- ~15-30 branches deleted
- ~15-25 tags

## Stopping the Simulation

### Graceful Stop

```bash
# Stop using script (recommended)
./tests/e2e/run-simulation.sh stop

# Or send SIGTERM directly
kill -TERM $(cat logs/simulation.pid)

# Or press Ctrl+C if running in foreground
```

### Force Stop

```bash
# If graceful stop fails
kill -9 $(cat logs/simulation.pid)
rm logs/simulation.pid
```

## Cleanup

### Remove Test Project

```bash
# Delete test project from source GitLab
PROJECT_ID=$(curl -s "http://localhost:8000/api/v4/projects/test-group%2Flong-running-test" \
  -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" | jq -r '.id')

curl -X DELETE "http://localhost:8000/api/v4/projects/$PROJECT_ID" \
  -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN"

# Delete from target GitLab
PROJECT_ID=$(curl -s "http://localhost:9000/api/v4/projects/test-group%2Flong-running-test" \
  -H "PRIVATE-TOKEN: $TARGET_GITLAB_TOKEN" | jq -r '.id')

curl -X DELETE "http://localhost:9000/api/v4/projects/$PROJECT_ID" \
  -H "PRIVATE-TOKEN: $TARGET_GITLAB_TOKEN"
```

### Clear Logs

```bash
rm logs/simulation.log
rm logs/simulation.pid
```

## Troubleshooting

### Simulation Won't Start

```bash
# Check if already running
./tests/e2e/run-simulation.sh status

# Check dependencies
npx ts-node --version
npx tsc --version

# Install if needed
npm install
```

### Too Many Errors

```bash
# Check error types
grep "\[ERROR\]" logs/simulation.log | tail -20

# Common issues:
# - GitLab instance down: Check docker-compose
# - Network issues: Check connectivity
# - API rate limit: Increase intervals in config
```

### Branches Not Being Deleted

```bash
# Check merged branch timestamps
grep "Merged MR" logs/simulation.log | tail -5

# Check cleanup attempts
grep "eligible for deletion" logs/simulation.log

# Verify MIN_HOURS_BEFORE_DELETE is set correctly (default: 2)
```

### Performance Issues

```bash
# Reduce operation frequency
# Edit CONFIG in long-running-simulation.ts:
# - Increase COMMIT_INTERVAL_MIN/MAX
# - Increase BRANCH_INTERVAL_MIN/MAX
# - Reduce MAX_OPEN_BRANCHES

# Check GitLab resource usage
docker stats gitlab-source
docker stats gitlab-target
```
