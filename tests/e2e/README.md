# Long-Running E2E Simulation Test

This test simulates real user operations on GitLab over an extended period (up to 3 days) to test the GitLab Mirror synchronization system under realistic conditions.

## Test Scenarios

The simulation performs the following operations with realistic intervals:

1. **Commit Code Changes** (every 15-45 minutes)
   - Creates commits on main branch or feature branches
   - Simulates active development

2. **Create Feature Branches** (every 1-3 hours)
   - Creates new feature branches from main
   - Limits to max 10 open branches

3. **Create Tags** (every 2-6 hours)
   - Creates version tags (v1.0.0, v1.0.1, etc.)
   - Simulates release tagging

4. **Create and Merge MRs** (every 1.5-4 hours)
   - Creates merge requests from feature branches
   - Automatically merges after 5 seconds
   - Keeps branch after merge for cleanup testing

5. **Delete Merged Branches** (every 3-6 hours)
   - **Important**: Only deletes branches that were merged at least **2 hours ago**
   - Simulates branch cleanup after merge
   - Deletes up to 3 branches at a time

## Configuration

Edit `CONFIG` in `long-running-simulation.ts`:

```typescript
const CONFIG = {
  MAX_DURATION_HOURS: 72,      // Total duration (3 days)

  GROUP_PATH: 'test-group',     // GitLab group
  PROJECT_NAME: 'long-running-test',

  // Operation intervals (minutes)
  COMMIT_INTERVAL_MIN: 15,
  COMMIT_INTERVAL_MAX: 45,

  BRANCH_INTERVAL_MIN: 60,
  BRANCH_INTERVAL_MAX: 180,

  TAG_INTERVAL_MIN: 120,
  TAG_INTERVAL_MAX: 360,

  MR_INTERVAL_MIN: 90,
  MR_INTERVAL_MAX: 240,

  CLEANUP_INTERVAL_MIN: 180,
  CLEANUP_INTERVAL_MAX: 360,

  // Cleanup rules
  MIN_HOURS_BEFORE_DELETE: 2,  // Wait at least 2 hours before deleting

  // Limits
  MAX_OPEN_BRANCHES: 10,
  MAX_MERGED_BRANCHES: 5,
}
```

## Prerequisites

1. Source GitLab instance running at `http://localhost:8000`
2. Test group created (default: `test-group`)
3. Environment variables set in `.env`:
   ```bash
   SOURCE_GITLAB_URL=http://localhost:8000
   SOURCE_GITLAB_TOKEN=glpat-...
   ```

## Usage

### Start the simulation

```bash
# Install dependencies (if needed)
npm install

# Compile TypeScript
npx tsc tests/e2e/long-running-simulation.ts --esModuleInterop --resolveJsonModule --skipLibCheck

# Run the simulation
npm run test:e2e:long-running

# Or run directly with ts-node
npx ts-node tests/e2e/long-running-simulation.ts
```

### Run in background

```bash
# Start in background with nohup
nohup npm run test:e2e:long-running > logs/simulation.log 2>&1 &

# View logs in real-time
tail -f logs/simulation.log

# Stop the simulation
kill $(pgrep -f long-running-simulation)
```

### Using the run script

```bash
# Start simulation
./tests/e2e/run-simulation.sh start

# Stop simulation
./tests/e2e/run-simulation.sh stop

# View status
./tests/e2e/run-simulation.sh status

# View logs
./tests/e2e/run-simulation.sh logs
```

## Monitoring

The simulation prints status summaries every 30 minutes:

```
================================================================================
STATUS SUMMARY - Elapsed: 2.5h (150m)
================================================================================
Total Operations: 47
Open Branches: 8
Merged Branches: 3
Tags Created: 2
Merge Requests: 5
Errors: 0
================================================================================
```

## Graceful Shutdown

The simulation handles SIGINT and SIGTERM signals:

```bash
# Press Ctrl+C to stop gracefully
# Or send signal
kill -SIGTERM <pid>
```

## Logs

Each operation is logged with:
- Timestamp
- Elapsed time
- Operation type
- Result (success/failure)

Example:
```
[2025-12-28T01:00:00.000Z] [INFO] [Elapsed: 150m] ✓ Created commit abc123f on branch 'feature/test-1735344000000'
[2025-12-28T01:05:00.000Z] [INFO] [Elapsed: 155m] ✓ Created branch 'feature/test-1735344300000' (Total: 9)
[2025-12-28T03:10:00.000Z] [INFO] [Elapsed: 280m] Found 2 branches eligible for deletion (>= 2.0h old)
[2025-12-28T03:10:01.000Z] [INFO] [Elapsed: 280m] ✓ Deleted merged branch 'feature/test-1735337200000' (age: 2.1h)
```

## Testing the Mirror System

While the simulation runs, monitor the GitLab Mirror system:

1. **Check synchronization status**
   ```bash
   curl http://localhost:9999/api/sync/projects | jq
   ```

2. **View dashboard statistics**
   ```bash
   # Open web UI
   open http://localhost:3000/dashboard

   # Or check API
   curl http://localhost:9999/api/dashboard/trend?range=7d | jq
   ```

3. **Verify target GitLab**
   - Open http://localhost:9000
   - Check that branches, commits, and tags are synced
   - Verify deleted branches are also removed

## Expected Behavior

Over the 3-day run:
- **~200-300 commits** created
- **~30-50 branches** created
- **~15-25 tags** created
- **~20-35 MRs** created and merged
- **~15-30 branches** deleted (after 2h wait)

The GitLab Mirror system should:
- ✓ Sync all commits to target
- ✓ Sync all branches (including new ones)
- ✓ Sync all tags
- ✓ Sync branch deletions
- ✓ Update dashboard statistics in real-time

## Troubleshooting

**Simulation stops unexpectedly**
- Check logs for errors
- Verify GitLab instances are running
- Check network connectivity

**Too many branches**
- Reduce `MAX_OPEN_BRANCHES` in config
- Decrease `MIN_HOURS_BEFORE_DELETE` (but keep >= 2)

**GitLab API rate limiting**
- Increase operation intervals
- Check GitLab instance capacity

**Branches not deleted after 2 hours**
- Check cleanup logs
- Verify branch merge timestamps
- Ensure cleanup interval is running

## Notes

- The simulation is idempotent - safe to restart
- Uses existing project if found
- Gracefully handles errors and continues
- All operations are logged for debugging
- Status printed every 30 minutes
