import { test, expect } from '@playwright/test'

test('check branch comparison data', async ({ page }) => {
  let apiResponse: any = null

  // Listen to console logs
  page.on('console', msg => {
    console.log('Browser Console:', msg.text())
  })

  // Intercept API response for branch comparison
  page.on('response', async response => {
    if (response.url().includes('/api/sync/branches')) {
      console.log('\n=== Branch Comparison API Response ===')
      console.log('URL:', response.url())
      try {
        const data = await response.json()
        apiResponse = data
        console.log('API Response Data:', JSON.stringify(data, null, 2))
      } catch (e) {
        console.log('Failed to parse response')
      }
    }
  })

  // Login
  await page.goto('http://localhost:3000/login')
  await page.getByLabel('Username').fill('admin')
  await page.getByLabel('Password').fill('Admin@123')
  await page.getByRole('button', { name: /Sign In/i }).click()

  // Wait for navigation to complete
  await page.waitForTimeout(2000)
  await page.waitForURL(/\/(dashboard)?/, { timeout: 5000 })

  // Navigate to projects page
  await page.goto('http://localhost:3000/projects')
  await page.waitForTimeout(1000)

  console.log('\n=== Projects page loaded ===')

  // Find the ai/test-rails-5 project
  const projectCell = page.locator('text=ai/test-rails-5').first()
  await projectCell.waitFor({ timeout: 10000 })

  console.log('\n=== Found ai/test-rails-5 project ===')

  // Click on the project to view details
  await projectCell.click()

  // Wait for project detail page to load
  await page.waitForTimeout(2000)

  console.log('\n=== Project detail page loaded ===')

  // Click on Branches tab
  const branchesTab = page.locator('text=Branches').first()
  await branchesTab.click()

  console.log('\n=== Clicked Branches tab ===')

  // Wait for branch comparison data to load
  await page.waitForTimeout(3000)

  // Take screenshot
  await page.screenshot({
    path: 'tests/screenshots/branch-comparison.png',
    fullPage: true
  })
  console.log('\n✅ Screenshot saved to tests/screenshots/branch-comparison.png')

  // Check if API response has branch data
  if (apiResponse && apiResponse.success && apiResponse.data) {
    console.log('\n=== Branch Comparison Data ===')
    const branches = apiResponse.data.branches || []
    console.log(`Total branches: ${branches.length}`)

    // Find feature/test-branch-1
    const testBranch = branches.find((b: any) => b.branchName === 'feature/test-branch-1')
    if (testBranch) {
      console.log('\n=== feature/test-branch-1 Data ===')
      console.log('Branch Name:', testBranch.branchName)
      console.log('Sync Status:', testBranch.syncStatus)
      console.log('Source Commit ID:', testBranch.sourceCommitId)
      console.log('Source Commit Short:', testBranch.sourceCommitShort)
      console.log('Source Last Commit At:', testBranch.sourceLastCommitAt)
      console.log('Source Commit Author:', testBranch.sourceCommitAuthor)
      console.log('Source Commit Message:', testBranch.sourceCommitMessage)
      console.log('Target Commit ID:', testBranch.targetCommitId)
      console.log('Target Last Commit At:', testBranch.targetLastCommitAt)
      console.log('Target Commit Author:', testBranch.targetCommitAuthor)
      console.log('Commit Diff:', testBranch.commitDiff)
    } else {
      console.log('\n❌ feature/test-branch-1 not found in API response')
    }

    // Check UI display
    console.log('\n=== Checking UI Display ===')
    const branchRow = page.locator('tr').filter({ hasText: 'feature/test-branch-1' })
    const rowCount = await branchRow.count()
    console.log(`Found ${rowCount} rows with feature/test-branch-1`)

    if (rowCount > 0) {
      // Expand the row to see details
      const expandButton = branchRow.locator('.el-table__expand-icon').first()
      await expandButton.click()
      await page.waitForTimeout(1000)

      // Take screenshot of expanded row
      await page.screenshot({
        path: 'tests/screenshots/branch-comparison-expanded.png',
        fullPage: true
      })
      console.log('\n✅ Screenshot saved to tests/screenshots/branch-comparison-expanded.png')

      // Extract expanded details
      const expandedContent = await page.locator('.branch-details').textContent()
      console.log('\n=== Expanded Branch Details (UI) ===')
      console.log(expandedContent)
    }
  } else {
    console.log('\n❌ No API response data')
  }
})
