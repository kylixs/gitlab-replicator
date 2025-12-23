# Branch Sync Issue Analysis

## Issue Description

**Project**: `/ai/test-android-app-3`
**Problem**: New branches are not syncing to the target GitLab instance
**Date**: 2025-12-22

## Root Cause Analysis

### 1. GitLab Push Mirror Behavior

GitLab push mirrors have specific behaviors regarding branch synchronization:

- **Automatic Sync**: Push mirrors sync automatically when:
  - New commits are pushed to existing branches
  - New branches are created AND pushed to the source repository

- **NOT Automatic**: Push mirrors do NOT automatically sync when:
  - Branches exist in the source repository but were created before the mirror was configured
  - Branches are created locally but not pushed
  - The mirror sync needs to be manually triggered

### 2. Current Configuration

From `PushMirrorManagementService.java:137`:
```java
RemoteMirror mirror = sourceGitLabApiClient.createMirror(
    sourceInfo.getGitlabProjectId(),
    mirrorUrlWithToken,
    false  // Don't restrict to protected branches only
);
```

The configuration is correct: `only_protected_branches: false` means ALL branches should be synced.

### 3. Possible Causes

#### Cause 1: Pre-existing Branches
If branches were created in the source repository BEFORE the push mirror was configured, they won't be automatically synced. GitLab only syncs branches that are pushed AFTER the mirror is created.

#### Cause 2: Mirror Not Triggered
GitLab push mirrors need to be triggered either:
- Automatically (when new commits are pushed)
- Manually via API call

If no commits have been pushed to the new branches, the mirror won't sync them.

#### Cause 3: Mirror Update Status
The mirror might be in a failed state or not enabled.

### 4. Available Solutions

#### Solution 1: Manual Trigger (Immediate)
Use the existing CLI command to manually trigger mirror sync:

```bash
gitlab-mirror sync ai/test-android-app-3
```

This calls the API endpoint that triggers:
```java
sourceGitLabApiClient.triggerMirrorSync(sourceInfo.getGitlabProjectId(), config.getGitlabMirrorId());
```

Which makes a POST request to:
```
/api/v4/projects/{projectId}/remote_mirrors/{mirrorId}/sync
```

#### Solution 2: Check Mirror Status
Query the mirror status to see if there are any errors:

```bash
gitlab-mirror mirror ai/test-android-app-3
```

This will show:
- Mirror enabled status
- Last update status
- Last error (if any)
- Last update time

#### Solution 3: Automatic Periodic Sync
Implement a scheduled job that periodically triggers sync for all active mirrors. This would ensure branches get synced even if no new commits are pushed.

## Recommended Actions

### Step 1: Verify Mirror Configuration

Check the mirror status for the affected project:

```bash
# Using API
curl -H "Authorization: Bearer ${GITLAB_MIRROR_API_KEY}" \
  http://localhost:9999/api/mirrors?project=ai/test-android-app-3
```

Expected response should show:
- `enabled: true`
- `onlyProtectedBranches: false`
- Last update status

### Step 2: Manual Trigger

Immediately trigger sync for the project:

```bash
# Using API
curl -X POST -H "Authorization: Bearer ${GITLAB_MIRROR_API_KEY}" \
  "http://localhost:9999/api/mirror/sync?project=ai/test-android-app-3"
```

### Step 3: Verify Sync Result

Check the mirror status again to see if branches were synced:

```bash
# Check source branches
curl -H "PRIVATE-TOKEN: ${SOURCE_GITLAB_TOKEN}" \
  http://localhost:8000/api/v4/projects/ai%2Ftest-android-app-3/repository/branches

# Check target branches
curl -H "PRIVATE-TOKEN: ${TARGET_GITLAB_TOKEN}" \
  http://localhost:9000/api/v4/projects/ai%2Ftest-android-app-3/repository/branches
```

### Step 4: Implement Periodic Sync (If Needed)

If manual triggers are required frequently, consider adding a scheduled task that periodically triggers sync for all active mirrors. This could be added to `MirrorCompensationScheduler.java`.

## Implementation Plan

### Short-term Fix (Immediate)
1. Build the CLI client
2. Use `gitlab-mirror sync ai/test-android-app-3` to trigger immediate sync
3. Verify branches are synced

### Long-term Enhancement (Optional)
Add configuration option for periodic forced sync:

```yaml
sync:
  push_mirror:
    periodic_trigger:
      enabled: true
      cron: "0 0 3 * * ?"  # Trigger daily at 3 AM
```

This would ensure all branches stay in sync even without new commits.

## Notes

- GitLab's push mirror is designed to sync on push events
- Pre-existing branches require manual trigger
- The `only_protected_branches: false` setting is correct
- No code changes needed - this is expected GitLab behavior

## References

- GitLab API: https://docs.gitlab.com/ee/api/remote_mirrors.html
- Push Mirror Documentation: https://docs.gitlab.com/ee/user/project/repository/mirror/push.html
- `server/src/main/java/com/gitlab/mirror/server/service/PushMirrorManagementService.java:452`
- `server/src/main/java/com/gitlab/mirror/server/client/GitLabApiClient.java:282`
