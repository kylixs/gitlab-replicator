import { test } from '@playwright/test'

test('capture auto-refresh button area', async ({ page }) => {
  // Login - reuse from setup-auth
  await page.goto('http://localhost:3000/login')
  await page.getByLabel('Username').fill('admin')
  await page.getByLabel('Password').fill('Admin@123')
  await page.getByRole('button', { name: /Sign In/i }).click()

  // Wait for navigation to complete
  await page.waitForTimeout(2000)
  await page.waitForURL(/\/(dashboard)?/, { timeout: 5000 })

  // Navigate to projects page to see the header clearly
  await page.goto('http://localhost:3000/projects')
  await page.waitForTimeout(1000)

  // Take full page screenshot
  await page.screenshot({
    path: 'tests/screenshots/full-page-with-header.png',
    fullPage: true
  })

  // Capture header area specifically
  const header = page.locator('.header')
  await header.screenshot({
    path: 'tests/screenshots/header-area.png'
  })

  // Capture just the auto-refresh button area
  const refreshButton = page.locator('.header-actions').first()
  await refreshButton.screenshot({
    path: 'tests/screenshots/auto-refresh-button-area.png'
  })

  console.log('✅ Screenshots captured:')
  console.log('   - tests/screenshots/full-page-with-header.png')
  console.log('   - tests/screenshots/header-area.png')
  console.log('   - tests/screenshots/auto-refresh-button-area.png')
})
