import { test, expect } from '@playwright/test'

test('check sync details dialog data', async ({ page }) => {
  let apiResponse: any = null

  // Listen to console logs
  page.on('console', msg => {
    console.log('Browser Console:', msg.text())
  })

  // Intercept API response
  page.on('response', async response => {
    if (response.url().includes('/api/sync/projects/') && response.url().includes('/result')) {
      console.log('\n=== API Response ===')
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

  // Find the ai/test-rails-5 project row
  const projectCell = page.locator('text=ai/test-rails-5').first()
  await projectCell.waitFor({ timeout: 10000 })

  console.log('\n=== Found ai/test-rails-5 project ===')

  // Find the Sync status tag in the same row
  const row = projectCell.locator('xpath=ancestor::tr')

  // Wait a bit to ensure row is fully loaded
  await page.waitForTimeout(500)

  // Find all tags in the row and click the one in the Sync column
  const allTags = row.locator('.el-tag')
  const tagCount = await allTags.count()
  console.log(`Found ${tagCount} tags in the row`)

  // The Sync column should be the second tag (first is Status, second is Sync)
  let syncTag = null
  for (let i = 0; i < tagCount; i++) {
    const tag = allTags.nth(i)
    const text = await tag.textContent()
    console.log(`Tag ${i}: ${text}`)
    if (text && (text.includes('Success') || text.includes('Failed') || text.includes('Skipped'))) {
      syncTag = tag
      break
    }
  }

  if (!syncTag) {
    console.log('Could not find Sync status tag, using second tag')
    syncTag = allTags.nth(1)
  }

  console.log('\n=== Clicking Sync status tag ===')
  await syncTag.click()

  // Wait for dialog to appear
  await page.waitForSelector('.el-dialog', { timeout: 5000 })
  console.log('\n=== Sync Details Dialog opened ===')

  // Wait for API response
  await page.waitForTimeout(1000)

  // Take screenshot first
  await page.screenshot({
    path: 'tests/screenshots/sync-details-dialog.png',
    fullPage: true
  })
  console.log('\n✅ Screenshot saved to tests/screenshots/sync-details-dialog.png')

  // Extract all dialog text content
  const dialogText = await page.locator('.el-dialog').textContent()
  console.log('\n=== Dialog Full Text ===')
  console.log(dialogText)

  // Extract Last Sync At from dialog text
  const lastSyncAtMatch = dialogText?.match(/Last Sync At([^A-Z]+)/)
  const uiLastSyncAt = lastSyncAtMatch ? lastSyncAtMatch[1].trim() : 'Not found'

  console.log('\n=== Comparison ===')
  console.log('UI Display - Last Sync At:', uiLastSyncAt)

  if (apiResponse && apiResponse.success && apiResponse.data) {
    console.log('API Response - lastSyncAt:', apiResponse.data.lastSyncAt)
    console.log('API Response - startedAt:', apiResponse.data.startedAt)
    console.log('API Response - completedAt:', apiResponse.data.completedAt)
    console.log('API Response - syncStatus:', apiResponse.data.syncStatus)
    console.log('API Response - summary:', apiResponse.data.summary)

    // Check if times match (convert API time to local format for comparison)
    const apiTime = new Date(apiResponse.data.lastSyncAt).toLocaleString()
    console.log('\nAPI Time (formatted):', apiTime)
    console.log('UI Time:', uiLastSyncAt)
    console.log('Match:', apiTime === uiLastSyncAt ? '✅ YES' : '❌ NO')
  }
})
