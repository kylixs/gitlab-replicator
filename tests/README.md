# Tests

This directory contains all automated tests for the GitLab Mirror project.

## Directory Structure

```
tests/
├── e2e/                           # End-to-end UI tests
│   ├── login.spec.ts              # Login functionality
│   ├── projects.spec.ts           # Project list and search
│   ├── sync-details.spec.ts       # Sync details page
│   ├── branch-comparison.spec.ts  # Branch comparison view
│   ├── analyze-diff.spec.ts       # Diff analysis
│   └── webhook-integration.spec.ts # Webhook UI integration
├── integration/                   # Integration tests (API + sync)
│   ├── commit-sync.spec.ts        # Commit synchronization
│   ├── branch-sync.spec.ts        # Branch synchronization
│   ├── project-sync.spec.ts       # Project discovery
│   ├── webhook-sync.spec.ts       # Webhook sync triggering
│   ├── helpers/                   # Test helper utilities
│   └── README.md                  # Integration test docs
├── playwright.e2e.config.ts       # E2E test configuration
├── playwright.integration.config.ts # Integration test configuration
└── README.md                      # This file
```

## Test Types

### E2E Tests (`e2e/`)
- **Purpose**: Test web UI functionality from user perspective
- **Technology**: Playwright with Chromium browser
- **Scope**: Login, navigation, data display, user interactions
- **Speed**: Fast (UI interactions only, no waiting for sync)

### Integration Tests (`integration/`)
- **Purpose**: Test full sync workflow (GitLab API → Mirror Service → Target GitLab)
- **Technology**: Playwright (for API calls and assertions)
- **Scope**: Commit/branch/project sync, webhook triggering, data consistency
- **Speed**: Slower (waits for actual sync operations)

## Running Tests

### E2E Tests

```bash
# From web-ui directory
cd web-ui

# Run all E2E tests
npm run test:e2e

# Run with UI
npm run test:e2e:ui

# Debug mode
npm run test:e2e:debug
```

### Integration Tests

```bash
# From web-ui directory
cd web-ui

# Run all integration tests
npm run test:integration

# Run with UI
npm run test:integration:ui

# Debug mode
npm run test:integration:debug
```

### From Tests Directory

```bash
cd tests

# E2E tests
npx playwright test -c playwright.e2e.config.ts

# Integration tests
npx playwright test -c playwright.integration.config.ts

# Run specific test file
npx playwright test -c playwright.e2e.config.ts e2e/login.spec.ts
npx playwright test -c playwright.integration.config.ts integration/webhook-sync.spec.ts
```

## Prerequisites

### For E2E Tests
1. **Web UI running**: `http://localhost:3000`
   - Automatically started by playwright config if not running
2. **Backend API running**: `http://localhost:9999`
3. **Valid test account**: username=`admin`, password=`Admin@123`

### For Integration Tests
1. **Source GitLab**: `http://localhost:8000`
2. **Target GitLab**: `http://localhost:9000`
3. **Mirror Service**: `http://localhost:9999`
4. **Test data**: Projects in `ai` and `arch` groups

## Environment Variables

Tests use environment variables from `.env` file:

```bash
# Source GitLab
SOURCE_GITLAB_URL=http://localhost:8000
SOURCE_GITLAB_TOKEN=glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq

# Target GitLab
TARGET_GITLAB_URL=http://localhost:9000
TARGET_GITLAB_TOKEN=glpat-b2nrFAAy9q2SozZr3Dm0N286MQp1OjEH.01.0w0t2khzm

# Mirror Service
MIRROR_API_URL=http://localhost:9999/api
MIRROR_API_KEY=dev-api-key-12345
```

## Test Reports

Test results are saved to:
- **HTML Report**: `web-ui/playwright-report/`
- **Test Results**: `web-ui/test-results/`
- **Screenshots**: `tests/screenshots/` (only on failure)
- **Traces**: Available on retry (for debugging)

View HTML report:
```bash
cd web-ui
npx playwright show-report
```

## Writing Tests

### E2E Test Example

```typescript
import { test, expect } from '@playwright/test'

test('should display project list', async ({ page }) => {
  // Login
  await page.goto('/login')
  await page.getByLabel('Username').fill('admin')
  await page.getByLabel('Password').fill('Admin@123')
  await page.getByRole('button', { name: /Sign In/i }).click()

  // Navigate to projects
  await page.goto('/projects')

  // Verify project list
  const projectTable = page.locator('table')
  await expect(projectTable).toBeVisible()
})
```

### Integration Test Example

```typescript
import { test, expect } from '@playwright/test'
import { mirrorApi } from './helpers/mirror-api-helper'
import { sourceGitLab } from './helpers/gitlab-helper'

test('should sync new commit', async () => {
  // Create commit in source GitLab
  const commit = await sourceGitLab.createCommit(
    projectId,
    'master',
    'test commit'
  )

  // Trigger sync
  await mirrorApi.triggerSync(syncProjectId)

  // Wait for sync completion
  const result = await mirrorApi.waitForSync(syncProjectId, 60000)

  // Verify sync success
  expect(result.status).toBe('success')
})
```

## Best Practices

1. **Use helpers**: Import from `integration/helpers/` for common operations
2. **Clean up**: Delete test branches/projects after tests
3. **Unique names**: Use timestamps for test data names
4. **Wait properly**: Use Playwright's built-in waiting, avoid `setTimeout`
5. **Assertions**: Check both API responses and actual state
6. **Error handling**: Wrap API calls in try-catch for better error messages

## Troubleshooting

### Tests timeout
- Check all services are running
- Verify network connectivity
- Check service logs: `tail -f logs/gitlab-mirror.log`

### Authentication failures
- Verify test credentials are correct
- Check `.auth/` directory exists
- Re-run setup-auth test: `npx playwright test -c playwright.e2e.config.ts e2e/setup-auth.spec.ts`

### Sync not working
- Check GitLab API tokens are valid
- Verify push mirror is configured
- Check scheduled tasks are enabled

## CI/CD Integration

For CI/CD pipelines:

```yaml
test:
  script:
    # Start services
    - docker-compose up -d
    - ./scripts/wait-for-services.sh

    # Install dependencies
    - cd web-ui
    - npm install
    - npx playwright install chromium

    # Run tests
    - npm run test:e2e
    - npm run test:integration

  artifacts:
    when: always
    paths:
      - web-ui/playwright-report/
      - web-ui/test-results/
```

## Further Reading

- **E2E Tests**: See individual test files in `e2e/` for specific scenarios
- **Integration Tests**: See `integration/README.md` for detailed documentation
- **Playwright Docs**: https://playwright.dev/
