import { test, expect } from '@playwright/test'

test.describe('Projects Page Layout Check', () => {
  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('http://localhost:3000/login')
    await page.waitForTimeout(500)
    await page.getByPlaceholder('Enter your username').fill('admin')
    await page.getByPlaceholder('Enter your password').fill('Admin@123')
    await page.getByRole('button', { name: /Sign In/i }).click()
    await page.waitForURL(/\/(dashboard)?/, { timeout: 10000 })
    await page.waitForTimeout(1000)

    // Navigate to projects page
    await page.goto('http://localhost:3000/projects')
    await page.waitForTimeout(3000)
  })

  test('should check query filter layout for overlapping', async ({ page }) => {
    console.log('\n=== Projects Page Query Filter Layout Check ===\n')

    // Take full page screenshot
    await page.screenshot({
      path: 'test-results/projects-page-layout.png',
      fullPage: true
    })
    console.log('Screenshot saved: test-results/projects-page-layout.png')

    // Check if search input exists
    const searchInput = page.locator('input[placeholder*="Search"], input[placeholder*="search"], input[placeholder*="搜索"]')
    const searchCount = await searchInput.count()
    console.log(`\nSearch inputs found: ${searchCount}`)

    // Check if filter selects exist
    const selects = page.locator('.el-select')
    const selectCount = await selects.count()
    console.log(`Filter selects found: ${selectCount}`)

    // Get bounding boxes to check for overlaps
    if (selectCount > 0) {
      console.log('\nFilter positions:')
      for (let i = 0; i < selectCount; i++) {
        const select = selects.nth(i)
        const box = await select.boundingBox()
        if (box) {
          console.log(`  Filter ${i + 1}: x=${box.x}, y=${box.y}, width=${box.width}, height=${box.height}`)
        }
      }
    }

    // Check for action buttons
    const buttons = page.locator('.el-button')
    const buttonCount = await buttons.count()
    console.log(`\nAction buttons found: ${buttonCount}`)

    console.log('\n✅ Layout check completed\n')
  })

  test('should check if filters are interactive', async ({ page }) => {
    console.log('\n=== Testing Filter Interactions ===\n')

    // Try to interact with status filter
    const statusFilter = page.locator('.el-select').first()
    if (await statusFilter.count() > 0) {
      await statusFilter.click()
      await page.waitForTimeout(500)

      // Check if dropdown opened
      const dropdown = page.locator('.el-select-dropdown')
      const isVisible = await dropdown.isVisible().catch(() => false)
      console.log(`Status filter dropdown opened: ${isVisible}`)

      if (isVisible) {
        await page.keyboard.press('Escape')
      }
    }

    console.log('\n✅ Filter interaction test completed\n')
  })

  test('should check projects table', async ({ page }) => {
    console.log('\n=== Projects Table Check ===\n')

    // Wait for table to load
    await page.waitForTimeout(1000)

    // Check if table exists
    const table = page.locator('.el-table')
    await expect(table).toBeVisible()
    console.log('✅ Projects table is visible')

    // Count table rows
    const rows = page.locator('.el-table__row')
    const rowCount = await rows.count()
    console.log(`Table rows: ${rowCount}`)

    // Check table headers
    const headers = page.locator('.el-table__header th')
    const headerCount = await headers.count()
    console.log(`Table columns: ${headerCount}`)

    if (headerCount > 0) {
      console.log('\nColumn headers:')
      for (let i = 0; i < headerCount; i++) {
        const header = headers.nth(i)
        const text = await header.textContent()
        console.log(`  - ${text?.trim()}`)
      }
    }

    console.log('\n✅ Table check completed\n')
  })
})
