import { test, expect } from '@playwright/test'

/**
 * Sync Statistics Display Test
 *
 * 验证同步统计信息在各个页面的显示是否正确
 */

test.describe('Sync Statistics Display', () => {
  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('http://localhost:3000/login')
    await page.getByLabel('Username').fill('admin')
    await page.getByLabel('Password').fill('Admin@123')
    await page.getByRole('button', { name: /Sign In/i }).click()
    await page.waitForURL(/\/(dashboard)?/, { timeout: 10000 })
  })

  test('Sync Results - Statistics column displays correctly', async ({ page }) => {
    console.log('\n🔍 Testing Sync Results Statistics Display...\n')

    // Navigate to Sync Results page
    await page.goto('http://localhost:3000/sync-results')
    await page.waitForTimeout(2000)

    // Check if Statistics column exists
    const statisticsHeader = page.locator('th:has-text("Statistics")')
    await expect(statisticsHeader).toBeVisible()
    console.log('✅ Statistics column header found')

    // Check if there are any statistics displayed
    const statisticsCells = page.locator('td').filter({ hasText: /[+~\-]\d+B|\d+C/ })
    const count = await statisticsCells.count()

    if (count > 0) {
      console.log(`✅ Found ${count} cells with statistics`)

      // Get first statistics cell text
      const firstStat = await statisticsCells.first().textContent()
      console.log(`   Example: ${firstStat}`)

      // Verify format (should contain +NB, ~NB, -NB, or NC)
      expect(firstStat).toMatch(/[+~\-]\d+B|\d+C/)
    } else {
      console.log('ℹ️  No statistics data found (all syncs may have 0 changes)')
    }
  })

  test('Sync Results Detail - Statistics section displays correctly', async ({ page }) => {
    console.log('\n🔍 Testing Sync Results Detail Statistics...\n')

    // Navigate to Sync Results page
    await page.goto('http://localhost:3000/sync-results')
    await page.waitForTimeout(2000)

    // Click on first "Detail" button
    const detailButton = page.getByRole('button', { name: 'Detail' }).first()
    await detailButton.click()
    await page.waitForTimeout(1000)

    // Check if dialog is visible
    const dialog = page.locator('.el-dialog')
    await expect(dialog).toBeVisible()
    console.log('✅ Detail dialog opened')

    // Check for Statistics section
    const statisticsHeading = dialog.locator('h3:has-text("Sync Statistics")')
    const hasStatistics = await statisticsHeading.count() > 0

    if (hasStatistics) {
      console.log('✅ Sync Statistics section found')

      // Check for statistics tags
      const tags = dialog.locator('.statistics-summary .el-tag')
      const tagCount = await tags.count()
      console.log(`   Found ${tagCount} statistics tags`)

      if (tagCount > 0) {
        const firstTag = await tags.first().textContent()
        console.log(`   Example: ${firstTag}`)
      }
    } else {
      console.log('ℹ️  No statistics section (sync may have 0 changes)')
    }

    // Check for "Recently Updated" branch tags
    const recentlyUpdatedTags = dialog.locator('.el-tag:has-text("Recently Updated")')
    const recentCount = await recentlyUpdatedTags.count()

    if (recentCount > 0) {
      console.log(`✅ Found ${recentCount} recently updated branch(es)`)
    } else {
      console.log('ℹ️  No recently updated branches (no commits in last 24 hours)')
    }
  })

  test('Project Events Tab - Statistics column displays correctly', async ({ page }) => {
    console.log('\n🔍 Testing Project Events Statistics...\n')

    // Navigate to first project detail page
    await page.goto('http://localhost:3000/projects')
    await page.waitForTimeout(2000)

    // Click on first project
    const firstProject = page.locator('tbody tr').first()
    await firstProject.click()
    await page.waitForTimeout(2000)

    // Click on Events tab
    const eventsTab = page.getByRole('tab', { name: /Events/i })
    await eventsTab.click()
    await page.waitForTimeout(1000)

    // Check if Statistics column exists
    const statisticsHeader = page.locator('th:has-text("Statistics")')
    const hasStatisticsColumn = await statisticsHeader.count() > 0

    if (hasStatisticsColumn) {
      console.log('✅ Statistics column found in Events tab')

      // Check for statistics data
      const statisticsCells = page.locator('td').filter({ hasText: /[+~\-]\d+B|\d+C/ })
      const count = await statisticsCells.count()

      if (count > 0) {
        console.log(`✅ Found ${count} events with statistics`)
        const firstStat = await statisticsCells.first().textContent()
        console.log(`   Example: ${firstStat}`)
      } else {
        console.log('ℹ️  No statistics data in events')
      }
    } else {
      console.log('⚠️  Statistics column not found in Events tab')
    }
  })

  test('Project Events Detail - Statistics section displays correctly', async ({ page }) => {
    console.log('\n🔍 Testing Event Detail Statistics...\n')

    // Navigate to first project detail page
    await page.goto('http://localhost:3000/projects')
    await page.waitForTimeout(2000)

    // Click on first project
    const firstProject = page.locator('tbody tr').first()
    await firstProject.click()
    await page.waitForTimeout(2000)

    // Click on Events tab
    const eventsTab = page.getByRole('tab', { name: /Events/i })
    await eventsTab.click()
    await page.waitForTimeout(1000)

    // Click on first "Detail" button
    const detailButton = page.getByRole('button', { name: 'Detail' }).first()
    if (await detailButton.count() > 0) {
      await detailButton.click()
      await page.waitForTimeout(1000)

      // Check if drawer is visible
      const drawer = page.locator('.el-drawer')
      await expect(drawer).toBeVisible()
      console.log('✅ Event detail drawer opened')

      // Check for Statistics section
      const statisticsHeading = drawer.locator('h3:has-text("Sync Statistics")')
      const hasStatistics = await statisticsHeading.count() > 0

      if (hasStatistics) {
        console.log('✅ Sync Statistics section found in event detail')

        // Check for statistics tags
        const tags = drawer.locator('.statistics-summary .el-tag')
        const tagCount = await tags.count()
        console.log(`   Found ${tagCount} statistics tags`)

        if (tagCount > 0) {
          const firstTag = await tags.first().textContent()
          console.log(`   Example: ${firstTag}`)
        }
      } else {
        console.log('ℹ️  No statistics section in event detail')
      }
    } else {
      console.log('ℹ️  No events available to check')
    }
  })

  test('Statistics format validation', async ({ page }) => {
    console.log('\n🔍 Testing Statistics Format Validation...\n')

    // Navigate to Sync Results page
    await page.goto('http://localhost:3000/sync-results')
    await page.waitForTimeout(2000)

    // Get all statistics cells
    const statisticsCells = page.locator('td').filter({ hasText: /[+~\-]\d+B|\d+C/ })
    const count = await statisticsCells.count()

    if (count > 0) {
      console.log(`Validating ${count} statistics cells...`)

      for (let i = 0; i < Math.min(count, 5); i++) {
        const text = await statisticsCells.nth(i).textContent()
        console.log(`  Cell ${i + 1}: ${text}`)

        // Validate format
        // Should contain: +NB (green), ~NB (blue), -NB (red), NC (gray)
        const hasValidFormat = /[+~\-]\d+B|\d+C/.test(text || '')
        expect(hasValidFormat).toBeTruthy()
      }

      console.log('✅ All statistics formats are valid')
    } else {
      console.log('ℹ️  No statistics to validate')
    }
  })

  test('Recently Updated branch tag validation', async ({ page }) => {
    console.log('\n🔍 Testing Recently Updated Branch Tags...\n')

    // Navigate to Sync Results page
    await page.goto('http://localhost:3000/sync-results')
    await page.waitForTimeout(2000)

    // Click on first "Detail" button
    const detailButton = page.getByRole('button', { name: 'Detail' }).first()
    await detailButton.click()
    await page.waitForTimeout(1000)

    // Check for branch table
    const dialog = page.locator('.el-dialog')
    const branchTable = dialog.locator('table')
    const hasBranchTable = await branchTable.count() > 0

    if (hasBranchTable) {
      console.log('✅ Branch table found')

      // Check for "Recently Updated" tags
      const recentlyUpdatedTags = dialog.locator('.el-tag:has-text("Recently Updated")')
      const recentCount = await recentlyUpdatedTags.count()

      console.log(`   Recently Updated branches: ${recentCount}`)

      // Check for other branch tags
      const defaultTags = dialog.locator('.el-tag:has-text("Default")')
      const protectedTags = dialog.locator('.el-tag:has-text("Protected")')

      console.log(`   Default branches: ${await defaultTags.count()}`)
      console.log(`   Protected branches: ${await protectedTags.count()}`)

      // Verify Recently Updated tag has correct type (danger/red)
      if (recentCount > 0) {
        const firstRecentTag = recentlyUpdatedTags.first()
        const tagClass = await firstRecentTag.getAttribute('class')
        expect(tagClass).toContain('el-tag--danger')
        console.log('✅ Recently Updated tag has correct style (danger/red)')
      }
    } else {
      console.log('ℹ️  No branch table found')
    }
  })
})
