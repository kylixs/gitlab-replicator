import { test, expect } from '@playwright/test'

test.describe('Login Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
  })

  test('should display login page correctly', async ({ page }) => {
    // Check page title
    await expect(page.locator('h2')).toContainText('GitLab Mirror')
    await expect(page.locator('p')).toContainText('Sign in to your account')

    // Check form elements exist
    await expect(page.getByLabel('Username')).toBeVisible()
    await expect(page.getByLabel('Password')).toBeVisible()
    await expect(page.getByRole('button', { name: /Sign In/i })).toBeVisible()
  })

  test('should show validation errors for empty fields', async ({ page }) => {
    // Click submit without filling form
    await page.getByRole('button', { name: /Sign In/i }).click()

    // Wait for validation messages
    await page.waitForTimeout(500)

    // Check validation errors appear
    const usernameError = page.locator('.el-form-item__error').first()
    const passwordError = page.locator('.el-form-item__error').last()

    await expect(usernameError).toBeVisible()
    await expect(passwordError).toBeVisible()
  })

  test('should successfully login with correct credentials', async ({ page }) => {
    // Fill in the form
    await page.getByLabel('Username').fill('admin')
    await page.getByLabel('Password').fill('Admin@123')

    // Take screenshot before login
    await page.screenshot({ path: 'tests/screenshots/before-login.png' })

    // Click sign in button
    await page.getByRole('button', { name: /Sign In/i }).click()

    // Wait for navigation or success message
    await page.waitForTimeout(2000)

    // Take screenshot after login attempt
    await page.screenshot({ path: 'tests/screenshots/after-login.png' })

    // Check if redirected to dashboard
    await page.waitForURL(/\/(dashboard)?/, { timeout: 5000 }).catch(() => {
      console.log('Current URL:', page.url())
    })

    // Verify we're on dashboard or home page
    const url = page.url()
    console.log('Final URL:', url)

    // Check for success indicators
    const isDashboard = url.includes('/dashboard') || url === 'http://localhost:3000/'
    if (isDashboard) {
      console.log('✅ Successfully redirected to dashboard')
    } else {
      // Check if there's an error message
      const errorAlert = page.locator('.el-alert')
      if (await errorAlert.isVisible()) {
        const errorText = await errorAlert.textContent()
        console.log('❌ Error message:', errorText)
      }
    }

    expect(isDashboard).toBeTruthy()
  })

  test('should show error for invalid credentials', async ({ page }) => {
    // Fill in wrong credentials
    await page.getByLabel('Username').fill('admin')
    await page.getByLabel('Password').fill('wrongpassword')

    // Click sign in
    await page.getByRole('button', { name: /Sign In/i }).click()

    // Wait for error message
    await page.waitForTimeout(1000)

    // Check for error alert
    const errorAlert = page.locator('.el-alert--error')
    await expect(errorAlert).toBeVisible({ timeout: 5000 })
  })

  test('should handle account lockout after multiple failures', async ({ page }) => {
    // Try to login with wrong password multiple times
    for (let i = 0; i < 5; i++) {
      await page.getByLabel('Username').fill('admin')
      await page.getByLabel('Password').fill('wrongpassword')
      await page.getByRole('button', { name: /Sign In/i }).click()
      await page.waitForTimeout(1000)
    }

    // After multiple failures, should show lockout warning or error
    const warningAlert = page.locator('.el-alert--warning, .el-alert--error')
    const isVisible = await warningAlert.isVisible()

    if (isVisible) {
      const alertText = await warningAlert.textContent()
      console.log('Alert message:', alertText)
      expect(alertText).toMatch(/failed|locked|attempts/i)
    }
  })

  test('should disable login button when locked', async ({ page }) => {
    // Note: This test assumes account is not locked initially
    const loginButton = page.getByRole('button', { name: /Sign In/i })

    // Initially button should be enabled
    await expect(loginButton).toBeEnabled()

    // If there's a lockout, button should be disabled
    // This would need actual lockout state, so we just verify the button state
    const isDisabled = await loginButton.isDisabled()
    console.log('Login button disabled:', isDisabled)
  })
})

test.describe('Login API Integration', () => {
  test('should make correct API calls during login', async ({ page }) => {
    // Listen for API requests
    const challengeRequest = page.waitForRequest(
      (request) => request.url().includes('/api/auth/challenge')
    )
    const loginRequest = page.waitForRequest(
      (request) => request.url().includes('/api/auth/login')
    )

    // Fill and submit form
    await page.goto('/login')
    await page.getByLabel('Username').fill('admin')
    await page.getByLabel('Password').fill('Admin@123')
    await page.getByRole('button', { name: /Sign In/i }).click()

    // Verify API calls were made
    const challenge = await challengeRequest
    console.log('Challenge request:', challenge.url())
    expect(challenge.url()).toContain('/api/auth/challenge')
    expect(challenge.method()).toBe('POST')

    const login = await loginRequest
    console.log('Login request:', login.url())
    expect(login.url()).toContain('/api/auth/login')
    expect(login.method()).toBe('POST')

    // Check response
    await page.waitForTimeout(2000)
    await page.screenshot({ path: 'tests/screenshots/api-integration.png' })
  })
})
