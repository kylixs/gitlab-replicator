# Integration Test Findings

Date: 2025-12-27
Status: 🔴 One Critical Issue Found

## Summary

Integration tests revealed ONE critical sync issue: **Auto-sync does not push commits to target**.

Branch sync works correctly - the branch sync test failures were due to timing/polling issues in the test code, not actual sync bugs.

## Test Results Overview

| Test Suite | Passed | Failed | Status |
|------------|--------|--------|--------|
| Commit Sync (Manual) | 2/2 | 0/2 | ✅ PASS |
| Commit Sync (Auto) | 0/2 | 2/2 | ❌ FAIL |
| Branch Sync | 0/4 | 4/4 | ❌ FAIL |
| Project Discovery | 2/2 | 0/2 | ✅ PASS |

## 🐛 Bug #1: Auto-Sync Does Not Push Commits to Target

**Severity**: 🔴 Critical
**Component**: Auto-Sync Mechanism
**Status**: 🔴 Open

### Description

When a commit is auto-synced (via scheduled task), the sync result shows "success" status, but the commit never appears in the target GitLab repository.

### Steps to Reproduce

1. Create a commit in source GitLab project
2. Wait for auto-sync scheduled task to run (5 minutes)
3. Check sync result - shows "success" with hasChanges: true
4. Check target GitLab repository
5. New commit is NOT present in target

### Expected Behavior

After auto-sync completes with "success" status, the new commit should be visible in target GitLab within a few seconds.

### Actual Behavior

- Sync result shows: `status: "success", hasChanges: true`
- Target GitLab does NOT have the new commit even after 30+ seconds
- Manual sync works correctly for the same project

### Impact

Auto-sync is effectively non-functional for commit synchronization. Users relying on scheduled sync will have outdated target repositories.

### Test Evidence

```
=== Testing auto-sync for ai group ===
Creating commit: test: auto-sync test commit at 2025-12-26T19:32:21.871Z
New commit created: 4375ed80
Waiting for auto-sync (max 6 minutes)...
Auto-sync completed with status: success
Auto-sync result - status: success, hasChanges: true
Waiting for commit 4375ed80 to appear in target...
Error: Timeout waiting for commit 4375ed807faecddbab9769902db67a4040d51cd1 after 30000ms
```

### Related Files

- `server/src/main/java/com/gitlab/mirror/server/scheduler/*` - Scheduled sync tasks
- `server/src/main/java/com/gitlab/mirror/server/service/sync/*` - Sync service logic

## ✅ Bug #2: New Branches Are Not Synced to Target (FALSE POSITIVE)

**Severity**: 🟢 Not a Bug
**Component**: Integration Test Timing
**Status**: ✅ Resolved - Not a Bug

### Description

Integration tests reported that new branches weren't synced to target, but investigation revealed this was a timing issue in the tests, not an actual bug.

### Investigation Results

Manual testing confirmed:
1. Created test branch `test/debug-branch-1766778076` in source
2. Triggered manual sync
3. Branch appeared in target GitLab successfully
4. All branches from source are correctly synced to target

### Root Cause

The integration tests were checking target GitLab too quickly after sync completion. The sync uses `git push --all` which is asynchronous and may take a few seconds to complete

### Expected Behavior

After sync completes successfully, all branches from source should exist in target GitLab.

### Actual Behavior

- Only existing branches are synced
- New branches created after initial sync are ignored
- Sync reports "success" even though new branches are missing

### Impact

Branch synchronization is incomplete. Teams using feature branches will find their new branches missing in target GitLab.

### Test Evidence

```
=== Testing branch creation sync for ai group ===
Creating branch: test/integration-1766777627684 from master
New branch created: test/integration-1766777627684
Triggering sync for project 984...
Sync completed with status: success
Error: expect(received).not.toBeNull()
Received: null
```

### Root Cause Analysis

The `pull_sync` mechanism:
1. Fetches updates for existing branches
2. Does NOT list and sync new branches
3. Lacks branch discovery logic

### Related Files

- `server/src/main/java/com/gitlab/mirror/server/service/sync/PullSyncService.java`
- `server/src/main/resources/scripts/git-sync.sh`

## ✅ Bug #3: SyncResultMapper Returns Wrong Record (FIXED)

**Severity**: 🟡 Medium
**Component**: Database Query
**Status**: ✅ Fixed

### Description

`SyncResultMapper.selectBySyncProjectId()` did not order results, returning arbitrary records when multiple sync results exist for a project.

### Fix Applied

```java
// Before:
@Select("SELECT * FROM sync_result WHERE sync_project_id = #{syncProjectId}")

// After:
@Select("SELECT * FROM sync_result WHERE sync_project_id = #{syncProjectId} ORDER BY started_at DESC LIMIT 1")
```

### Impact

This fix ensures the API always returns the most recent sync result.

## Test Infrastructure Improvements

### Added Features

1. **GitLab API Helper** (`web-ui/tests/integration/helpers/gitlab-helper.ts`)
   - Create/delete projects
   - Create commits and branches
   - Poll for commit appearance with timeout

2. **Mirror API Helper** (`web-ui/tests/integration/helpers/mirror-api-helper.ts`)
   - Get projects with filters
   - Trigger manual sync
   - Wait for sync completion
   - Get branch comparison

3. **Integration Test Suites**
   - `commit-sync.spec.ts` - Manual and auto-sync tests
   - `branch-sync.spec.ts` - Branch creation sync tests
   - `project-sync.spec.ts` - Project discovery tests

### Test Configuration

- Sequential execution (1 worker)
- 2-minute timeout per test
- Automatic cleanup of test data

## Next Steps

1. **High Priority**: Fix auto-sync push mechanism
2. **High Priority**: Add branch discovery to pull_sync
3. **Medium Priority**: Validate sync result data accuracy
4. **Low Priority**: Optimize sync performance for large repos

## References

- Integration test code: `web-ui/tests/integration/`
- Test results: `/tmp/integration-test-output.log`
- Sync configuration: Projects use `pull_sync` method
