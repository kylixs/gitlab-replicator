import { test, expect } from '@playwright/test'

test.describe('Dashboard Page Check', () => {
  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('http://localhost:3000/login')
    await page.getByLabel('Username').fill('admin')
    await page.getByLabel('Password').fill('Admin@123')
    await page.getByRole('button', { name: /Sign In/i }).click()
    await page.waitForURL(/\/(dashboard)?/, { timeout: 5000 })
    await page.waitForTimeout(2000)
  })

  test('should display dashboard statistics', async ({ page }) => {
    console.log('\n=== Dashboard Statistics Check ===\n')

    // Check Total Projects card
    const totalProjectsCard = page.locator('text=Total Projects').locator('..')
    await expect(totalProjectsCard).toBeVisible()
    const totalCount = await totalProjectsCard.locator('.stat-value').textContent()
    console.log(`Total Projects: ${totalCount}`)

    // Check Synced card
    const syncedCard = page.locator('text=Synced').locator('..')
    await expect(syncedCard).toBeVisible()
    const syncedCount = await syncedCard.locator('.stat-value').textContent()
    console.log(`Synced: ${syncedCount}`)

    // Check Syncing card
    const syncingCard = page.locator('text=Syncing').locator('..')
    await expect(syncingCard).toBeVisible()
    const syncingCount = await syncingCard.locator('.stat-value').textContent()
    console.log(`Syncing: ${syncingCount}`)

    // Check Paused card
    const pausedCard = page.locator('text=Paused').locator('..')
    await expect(pausedCard).toBeVisible()
    const pausedCount = await pausedCard.locator('.stat-value').textContent()
    console.log(`Paused: ${pausedCount}`)

    // Check Failed card
    const failedCard = page.locator('text=Failed').locator('..')
    await expect(failedCard).toBeVisible()
    const failedCount = await failedCard.locator('.stat-value').textContent()
    console.log(`Failed: ${failedCount}`)

    console.log('\n✅ All statistic cards are visible\n')
  })

  test('should display status distribution chart', async ({ page }) => {
    console.log('\n=== Status Distribution Chart Check ===\n')

    // Wait for chart to load
    await page.waitForTimeout(1000)

    // Check if chart container exists
    const chartContainer = page.locator('.status-chart')
    await expect(chartContainer).toBeVisible()

    console.log('✅ Status distribution chart is visible\n')
  })

  test('should display delayed projects table', async ({ page }) => {
    console.log('\n=== Delayed Projects Table Check ===\n')

    // Check if delayed table exists
    const delayedTable = page.locator('text=Top Delayed Projects').locator('..')
    await expect(delayedTable).toBeVisible()

    console.log('✅ Delayed projects table is visible\n')
  })

  test('should display activity timeline', async ({ page }) => {
    console.log('\n=== Activity Timeline Check ===\n')

    // Check if timeline exists
    const timeline = page.locator('text=Recent Activity').locator('..')
    await expect(timeline).toBeVisible()

    console.log('✅ Activity timeline is visible\n')
  })

  test('should have action buttons', async ({ page }) => {
    console.log('\n=== Action Buttons Check ===\n')

    // Check Incremental Scan button
    const incrementalBtn = page.getByRole('button', { name: /Incremental Scan/i })
    await expect(incrementalBtn).toBeVisible()
    console.log('✅ Incremental Scan button exists')

    // Check Full Scan button
    const fullScanBtn = page.getByRole('button', { name: /Full Scan/i })
    await expect(fullScanBtn).toBeVisible()
    console.log('✅ Full Scan button exists')

    console.log()
  })

  test('should take dashboard screenshot', async ({ page }) => {
    console.log('\n=== Taking Dashboard Screenshot ===\n')

    // Take full page screenshot
    await page.screenshot({
      path: 'test-results/dashboard-full.png',
      fullPage: true
    })

    console.log('✅ Screenshot saved to test-results/dashboard-full.png\n')
  })

  test('should check API response data', async ({ page }) => {
    console.log('\n=== Checking Dashboard API Responses ===\n')

    // Intercept API calls
    const statsPromise = page.waitForResponse(
      response => response.url().includes('/api/dashboard/stats') && response.status() === 200
    )
    const distributionPromise = page.waitForResponse(
      response => response.url().includes('/api/dashboard/status-distribution') && response.status() === 200
    )

    // Reload page to trigger API calls
    await page.reload()

    const statsResponse = await statsPromise
    const distributionResponse = await distributionPromise

    const statsData = await statsResponse.json()
    const distributionData = await distributionResponse.json()

    console.log('Stats API Response:')
    console.log(JSON.stringify(statsData, null, 2))

    console.log('\nStatus Distribution API Response:')
    console.log(JSON.stringify(distributionData, null, 2))

    // Verify response structure
    expect(statsData.success).toBe(true)
    expect(statsData.data).toHaveProperty('totalProjects')
    expect(statsData.data).toHaveProperty('statusCounts')

    expect(distributionData.success).toBe(true)
    expect(Array.isArray(distributionData.data)).toBe(true)

    console.log('\n✅ API responses are valid\n')
  })
})
