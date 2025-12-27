# Integration Tests

This directory contains integration tests for GitLab Mirror synchronization functionality.

## Overview

The integration tests verify that:
1. **Commit Sync**: Commits made to source GitLab are synced to target GitLab
2. **Branch Sync**: New branches and commits on branches are synced correctly
3. **Project Sync**: New projects are discovered and synced automatically
4. **Auto Sync**: Scheduled synchronization works as expected

## Test Structure

```
tests/integration/
├── helpers/
│   ├── gitlab-helper.ts       # GitLab API utilities
│   └── mirror-api-helper.ts   # GitLab Mirror API utilities
├── commit-sync.spec.ts        # Commit synchronization tests
├── branch-sync.spec.ts        # Branch creation and sync tests
├── project-sync.spec.ts       # New project discovery tests
└── README.md                  # This file
```

## Prerequisites

1. **Running Services**:
   - Source GitLab: http://localhost:8000
   - Target GitLab: http://localhost:9000
   - GitLab Mirror Service: http://localhost:9999

2. **Environment Variables** (optional, defaults provided):
   ```bash
   export SOURCE_GITLAB_URL=http://localhost:8000
   export SOURCE_GITLAB_TOKEN=glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq
   export TARGET_GITLAB_URL=http://localhost:9000
   export TARGET_GITLAB_TOKEN=glpat-b2nrFAAy9q2SozZr3Dm0N286MQp1OjEH.01.0w0t2khzm
   export MIRROR_API_URL=http://localhost:9999/api
   export MIRROR_API_KEY=test-api-key
   ```

3. **Test Groups**:
   - Tests run against projects in `ai` and `arch` groups
   - Ensure these groups exist and have at least one project

## Running Tests

### Run All Integration Tests
```bash
# From web-ui directory
cd web-ui
npm run test:integration

# Or from tests directory
cd tests
npx playwright test -c playwright.integration.config.ts
```

### Run Specific Test Suite
```bash
# From web-ui directory
cd web-ui
npx playwright test -c ../tests/playwright.integration.config.ts ../tests/integration/commit-sync.spec.ts

# Or from tests directory
cd tests
npx playwright test -c playwright.integration.config.ts integration/commit-sync.spec.ts
npx playwright test -c playwright.integration.config.ts integration/branch-sync.spec.ts
npx playwright test -c playwright.integration.config.ts integration/project-sync.spec.ts
npx playwright test -c playwright.integration.config.ts integration/webhook-sync.spec.ts
```

### Run Tests with UI
```bash
cd web-ui
npm run test:integration:ui
```

### Run Tests in Debug Mode
```bash
cd web-ui
npm run test:integration:debug
```

### Run Specific Test
```bash
cd tests
npx playwright test -c playwright.integration.config.ts -g "should sync new commit in ai group"
```

## Test Scenarios

### 1. Commit Sync Tests (`commit-sync.spec.ts`)

**Manual Sync Test**:
- Creates a commit in source GitLab
- Triggers manual sync via API
- Verifies commit is synced to target GitLab
- Checks branch comparison shows synced status
- Validates commit author and timestamp

**Auto Sync Test**:
- Creates a commit in source GitLab
- Waits for scheduled sync (max 6 minutes)
- Verifies automatic synchronization

### 2. Branch Sync Tests (`branch-sync.spec.ts`)

**New Branch Test**:
- Creates a new branch in source GitLab
- Triggers sync
- Verifies branch is created in target GitLab
- Validates branch comparison data
- Cleans upt branch

**Commit to New Branch Test**:
- Creates a new branch
- Adds a commit to the branch
- Triggers sync
- Verifies both branch and commit are synced
- Cleans up test branch

### 3. Project Sync Tests (`project-sync.spec.ts`)

**New Project Discovery Test**:
- Creates a new project in source GitLab
- Waits for project discovery (max 6 minutes)
- Verifies project is synced to target GitLab
- Tests commit sync on new project
- Cleans up test project

**Project Discovery Test**:
- Lists all projects in ai and arch groups
- Verifies project metadata

**Sync Status Test**:
- Checks all projects have valid sync status
- Verifies sync results exist

## Test Helpers

### GitLabHelper

Provides utilities to interact with GitLab API:
- `getProject(projectPath)`: Get project by path
- `createProject(groupPath, projectName)`: Create new project
- `createCommit(projectId, branchName, message)`: Create commit
- `createBranch(projectId, branchName, ref)`: Create branch
- `getBranch(projectId, branchName)`: Get branch info
- `deleteBranch(projectId, branchName)`: Delete branch
- `deleteProject(projectId)`: Delete project

### MirrorApiHelper

Provides utilities to interact with GitLab Mirror API:
- `getProject(projectKey)`: Get sync project
- `getProjects(filters)`: List projects with filters
- `triggerSync(projectId)`: Trigger manual sync
- `getBranchComparison(syncProjectId)`: Get branch comparison
- `getSyncResult(projectId)`: Get sync result
- `waitForSync(projectId, timeout)`: Wait for sync completion
- `waitForBranchSync(syncProjectId, branchName)`: Wait for branch sync

## Expected Results

All tests should pass with the following validations:

✅ **Commit Sync**:
- Source and target commits match
- Commit author is preserved
- Commit timestamp is preserved
- Branch comparison shows "synced" status

✅ **Branch Sync**:
- New branches are created in target
- Branch commits are synced
- Branch metadata (ar, time) is correct

✅ **Project Sync**:
- New projects are discovered within 6 minutes
- Initial sync completes successfully
- All branches are synced
- Subsequent commits sync correctly

## Troubleshooting

### Tests Timeout
- Check if GitLab Mirror service is running
- Verify scheduled tasks are enabled
- Check logs: `tail -f logs/gitlab-mirror.log`

### Sync Failures
- Check GitLab API tokens are valid
- Verify network connectivity
- Check for errors in sync result

### Project Not Found
- Ensure test groups (ai, arch) exist
- Verify at least one project exists in each group
- Check project discovery is enabled

## Cleanup

Tests automatically clean up:
- Test branches are deleted after branch sync tests
- Test projects are deleted after project sync tests
- Test commits remain (they don't affect other tests)

To manually clean up test data:
```bash
# List test branches
git branch -r | grep test/integration

# List test projects
curl -H "PRIVATE-TOKEN: $SOURCE_GITLAB_TOKEN" \
  http://localhost:8000/api/v4/projects?search=test-project
```

## CI/CD Integration

To run tests in CI/CD pipeline:

```yaml
integration-tests:
  script:
    - docker-compose up -d
    - ./scripts/wait-for-services.sh
    - cd web-ui
    - npm install
    - npx playwright install
    - npx playwright test tests/integration/
  artifacts:
    when: always
    paths:
      - web-ui/test-results/
      - web-ui/playwright-report/
```

## Notes

- Tests are designed to be idempotent and can run multiple times
- Each test creates unique test data (timestamped names)
- Tests verify both API responses and actual GitLab state
- Auto-sync tests may take up to 6 minutes (scheduled task interval)
- Manual sync tests complete within 1-2 minutes
