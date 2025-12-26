# Bug Fixes TODO

## 🔴 Critical Bugs (Must Fix)

### [ ] Bug #1: Auto-Sync Does Not Push Commits to Target

**Priority**: P0 - Critical
**Estimated Effort**: 4-8 hours
**Assignee**: TBD

**Tasks**:
- [ ] Investigate auto-sync scheduler code
  - [ ] Check `ScheduledSyncTask.java` execution logic
  - [ ] Verify sync service is called correctly
  - [ ] Add debug logging to track sync flow
- [ ] Compare auto-sync vs manual-sync code paths
  - [ ] Identify differences in execution
  - [ ] Check if push operation is missing in auto-sync
- [ ] Add integration test for auto-sync
  - [ ] Test should wait for scheduled task
  - [ ] Verify commit appears in target
- [ ] Fix the auto-sync mechanism
  - [ ] Ensure push to target is executed
  - [ ] Update sync result with correct commit SHA
- [ ] Verify fix with integration tests

**Files to Check**:
- `server/src/main/java/com/gitlab/mirror/server/scheduler/ScheduledSyncTask.java`
- `server/src/main/java/com/gitlab/mirror/server/service/sync/PullSyncService.java`
- `server/src/main/java/com/gitlab/mirror/server/service/sync/SyncExecutor.java`

**Success Criteria**:
- Auto-sync integration tests pass (2/2)
- New commits appear in target within 10 seconds of auto-sync completion
- Sync result records correct source commit SHA

---

### [ ] Bug #2: New Branches Are Not Synced to Target

**Priority**: P0 - Critical
**Estimated Effort**: 6-10 hours
**Assignee**: TBD

**Tasks**:
- [ ] Analyze current pull_sync implementation
  - [ ] Identify why only existing branches are synced
  - [ ] Check if git-sync.sh fetches all branches
- [ ] Design branch discovery mechanism
  - [ ] Fetch all branches from source GitLab
  - [ ] Compare with target GitLab branches
  - [ ] Identify new/missing branches
- [ ] Implement branch sync logic
  - [ ] Add branch listing to PullSyncService
  - [ ] Push all branches to target (not just tracked ones)
  - [ ] Update git-sync.sh if needed
- [ ] Update branch comparison logic
  - [ ] Ensure new branches are detected
  - [ ] Show proper sync status for new branches
- [ ] Add integration tests
  - [ ] Test new branch creation sync
  - [ ] Test commit on new branch sync
- [ ] Verify fix with all branch sync tests

**Files to Modify**:
- `server/src/main/java/com/gitlab/mirror/server/service/sync/PullSyncService.java`
- `server/src/main/resources/scripts/git-sync.sh`
- `server/src/main/java/com/gitlab/mirror/server/service/BranchSnapshotService.java`

**Success Criteria**:
- Branch sync integration tests pass (4/4)
- New branches appear in target after sync
- Branch comparison shows all branches (including new ones)
- git push includes `--all` or equivalent flag

**Technical Approach**:

Option 1: Modify git-sync.sh to push all branches
```bash
git push --all target-remote
```

Option 2: List branches via GitLab API and push each
```java
List<RepositoryBranch> sourceBranches = gitLabApiClient.getBranches(sourceProjectId);
for (RepositoryBranch branch : sourceBranches) {
    // Ensure branch exists in target
}
```

---

## ✅ Fixed Bugs

### [x] Bug #3: SyncResultMapper Returns Wrong Record

**Priority**: P1 - High
**Status**: ✅ Fixed in commit [pending]
**Fix Date**: 2025-12-27

**Changes Made**:
- Added `ORDER BY started_at DESC LIMIT 1` to `SyncResultMapper.selectBySyncProjectId()`
- Ensures the most recent sync result is always returned

**Files Modified**:
- `server/src/main/java/com/gitlab/mirror/server/mapper/SyncResultMapper.java`

---

## 🟡 Medium Priority (Should Fix)

### [ ] Validate Sync Result Data Accuracy

**Priority**: P2 - Medium
**Estimated Effort**: 2-4 hours

**Tasks**:
- [ ] Ensure `sourceCommitSha` is updated after sync
- [ ] Verify `targetCommitSha` is recorded correctly
- [ ] Add validation that sync result matches actual GitLab state

---

## 🟢 Low Priority (Nice to Have)

### [ ] Optimize Sync Performance for Large Repos

**Priority**: P3 - Low
**Estimated Effort**: TBD

**Tasks**:
- [ ] Profile sync performance
- [ ] Optimize git fetch/push operations
- [ ] Add progress indicators for long-running syncs

---

## Notes

- All integration tests are in `web-ui/tests/integration/`
- Run tests with: `npx playwright test --config=playwright.integration.config.ts`
- Bug details documented in `docs/bugs/integration-test-findings.md`
