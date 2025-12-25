import { test, expect } from '@playwright/test'

test.describe('Projects Page', () => {
  test.beforeEach(async ({ page }) => {
    // Login first
    await page.goto('/login')
    await page.getByLabel('Username').fill('admin')
    await page.getByLabel('Password').fill('Admin@123')
    await page.getByRole('button', { name: /Sign In/i }).click()

    // Wait for login to complete (same as login.spec.ts)
    await page.waitForTimeout(2000)

    // Wait for redirect to complete
    await page.waitForURL(/\/(dashboard)?/, { timeout: 5000 })

    // Check if login was successful by checking localStorage
    const token = await page.evaluate(() => localStorage.getItem('auth_token'))
    console.log('Token after login:', token ? 'EXISTS' : 'MISSING')
    expect(token).toBeTruthy()
  })

  test('should navigate to projects page and display project list', async ({ page }) => {
    // Check token before navigation
    const tokenBefore = await page.evaluate(() => localStorage.getItem('auth_token'))
    console.log('Token before navigating to /projects:', tokenBefore ? 'EXISTS' : 'MISSING')

    // Listen for API requests and responses
    const apiRequests: any[] = []
    const apiResponses: any[] = []

    page.on('request', request => {
      if (request.url().includes('/api/')) {
        const headers = request.headers()
        apiRequests.push({
          url: request.url(),
          method: request.method(),
          hasAuthHeader: !!headers['authorization']
        })
        console.log('→ Request:', request.method(), request.url(),
                    headers['authorization'] ? 'WITH AUTH' : 'NO AUTH')
      }
    })

    page.on('response', async response => {
      if (response.url().includes('/api/')) {
        const status = response.status()
        let body = null
        try {
          body = await response.text()
        } catch (e) {
          // Ignore errors reading response body
        }
        apiResponses.push({
          url: response.url(),
          status: status,
          body: body
        })
        console.log('← Response:', status, response.url())
        if (status >= 400 && body) {
          console.log('  ❌ Error Body:', body.substring(0, 500))
        }
      }
    })

    // Listen for console messages
    const consoleErrors: string[] = []
    const consoleWarnings: string[] = []
    page.on('console', msg => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text())
        console.log('Console Error:', msg.text())
      } else if (msg.type() === 'warning') {
        consoleWarnings.push(msg.text())
      } else if (msg.type() === 'log' && msg.text().includes('auth')) {
        console.log('Console Log:', msg.text())
      }
    })

    // Navigate to projects page
    console.log('Navigating to /projects...')
    await page.goto('/projects')

    // Wait a bit for API calls
    await page.waitForTimeout(3000)

    // Check token after navigation
    const tokenAfter = await page.evaluate(() => localStorage.getItem('auth_token'))
    console.log('Token after navigating to /projects:', tokenAfter ? 'EXISTS' : 'MISSING')

    // Check current URL
    const currentUrl = page.url()
    console.log('Current URL:', currentUrl)

    // Take screenshot
    await page.screenshot({ path: 'tests/screenshots/projects-page.png', fullPage: true })

    // Check for error alerts
    const errorAlert = page.locator('.el-alert--error, .el-message--error')
    const hasError = await errorAlert.count() > 0

    if (hasError) {
      console.log('❌ Error alert found on page')
      const errorText = await errorAlert.first().textContent()
      console.log('Error message:', errorText)
    }

    // Log all API requests and responses
    console.log('\n=== API Summary ===')
    console.log('Total requests:', apiRequests.length)
    console.log('Total responses:', apiResponses.length)

    apiResponses.forEach((resp, idx) => {
      console.log(`\nResponse ${idx + 1}:`)
      console.log('  URL:', resp.url)
      console.log('  Status:', resp.status)
      if (resp.status >= 400) {
        console.log('  ❌ Error response body:', resp.body)
      }
    })

    // Log console errors
    if (consoleErrors.length > 0) {
      console.log('\n=== Console Errors ===')
      consoleErrors.forEach((err, idx) => {
        console.log(`${idx + 1}. ${err}`)
      })
    }

    // Report findings
    console.log('\n=== Analysis ===')
    console.log('Redirected to login?', currentUrl.includes('/login'))
    console.log('Token persisted?', !!tokenAfter)
    if (currentUrl.includes('/login')) {
      console.log('Status: ERROR - Redirected back to login page (authentication failed)')
    } else if (hasError) {
      console.log('Status: ERROR - Page shows error alert')
    } else if (consoleErrors.length > 0) {
      console.log('Status: WARNING - Console errors detected')
    } else if (apiResponses.some(r => r.status >= 400)) {
      console.log('Status: ERROR - API calls failed')
    } else {
      console.log('Status: OK - No obvious errors detected')
    }
  })

  test('should check project API endpoints', async ({ page }) => {
    // Intercept API calls to see what's being requested
    let projectsApiCalled = false
    let groupsApiCalled = false
    let projectsResponse: any = null
    let groupsResponse: any = null

    page.on('response', async response => {
      const url = response.url()

      if (url.includes('/api/projects/list') || url.includes('/api/projects?')) {
        projectsApiCalled = true
        projectsResponse = {
          status: response.status(),
          url: url,
          body: await response.text().catch(() => null)
        }
        console.log('Projects API response:', projectsResponse.status)
        console.log('Projects API body:', projectsResponse.body?.substring(0, 200))
      }

      if (url.includes('/api/projects/groups') || url.includes('/api/groups')) {
        groupsApiCalled = true
        groupsResponse = {
          status: response.status(),
          url: url,
          body: await response.text().catch(() => null)
        }
        console.log('Groups API response:', groupsResponse.status)
        console.log('Groups API body:', groupsResponse.body?.substring(0, 200))
      }
    })

    // Navigate to projects page
    await page.goto('/projects')
    await page.waitForTimeout(2000)

    console.log('\n=== API Call Summary ===')
    console.log('Projects API called:', projectsApiCalled)
    console.log('Groups API called:', groupsApiCalled)

    if (projectsResponse) {
      console.log('Projects API status:', projectsResponse.status)
      if (projectsResponse.status >= 400) {
        console.log('❌ Projects API failed with status:', projectsResponse.status)
        console.log('Response body:', projectsResponse.body)
      }
    }

    if (groupsResponse) {
      console.log('Groups API status:', groupsResponse.status)
      if (groupsResponse.status >= 400) {
        console.log('❌ Groups API failed with status:', groupsResponse.status)
        console.log('Response body:', groupsResponse.body)
      }
    }
  })

  test('should clear cache for selected projects', async ({ page }) => {
    // Navigate to projects page
    await page.goto('/projects')
    await page.waitForTimeout(2000)

    // Wait for table to load
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // Select the first two projects using checkboxes
    const checkboxes = page.locator('.el-table .el-checkbox__input')
    const count = await checkboxes.count()
    console.log(`Found ${count} checkboxes`)

    // Click first 2 checkboxes (skip the header checkbox at index 0)
    if (count > 2) {
      await checkboxes.nth(1).click()
      await checkboxes.nth(2).click()
      await page.waitForTimeout(500)

      // Verify batch bar appears
      const batchBar = page.locator('.batch-bar')
      await expect(batchBar).toBeVisible()

      // Check selected count
      const selectedInfo = page.locator('.batch-info')
      const selectedText = await selectedInfo.textContent()
      console.log('Selected projects:', selectedText)

      // Listen for API calls
      let clearCacheApiCalled = false
      let clearCacheResponse: any = null

      page.on('response', async response => {
        const url = response.url()
        if (url.includes('/api/sync/projects/batch-clear-cache')) {
          clearCacheApiCalled = true
          clearCacheResponse = {
            status: response.status(),
            body: await response.json().catch(() => null)
          }
          console.log('Clear cache API response:', clearCacheResponse)
        }
      })

      // Click "Clear Cache" button
      const clearCacheBtn = page.locator('.batch-actions').getByRole('button', { name: /Clear Cache/i })
      await expect(clearCacheBtn).toBeVisible()
      await clearCacheBtn.click()

      // Wait for confirmation dialog
      await page.waitForSelector('.el-message-box', { timeout: 3000 })

      // Click OK button
      const confirmBtn = page.locator('.el-message-box').getByRole('button', { name: /OK/i })
      await confirmBtn.click()

      // Wait for API call and response
      await page.waitForTimeout(2000)

      // Take screenshot
      await page.screenshot({ path: 'tests/screenshots/clear-cache-result.png', fullPage: true })

      // Verify API was called
      console.log('Clear cache API called:', clearCacheApiCalled)
      if (clearCacheResponse) {
        console.log('Response status:', clearCacheResponse.status)
        console.log('Response body:', JSON.stringify(clearCacheResponse.body, null, 2))

        // Verify response structure
        expect(clearCacheResponse.status).toBe(200)
        expect(clearCacheResponse.body).toHaveProperty('success')
        expect(clearCacheResponse.body).toHaveProperty('data')
      }

      // Check for success/warning message
      const successMsg = page.locator('.el-message--success, .el-message--warning')
      const hasMessage = await successMsg.count() > 0
      if (hasMessage) {
        const msgText = await successMsg.first().textContent()
        console.log('Success/Warning message:', msgText)
      }
    } else {
      console.log('Not enough projects to test batch clear cache')
    }
  })

  test('should trigger immediate sync for a project', async ({ page }) => {
    // Navigate to projects page
    await page.goto('/projects')
    await page.waitForTimeout(2000)

    // Wait for table to load
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // Select one project using checkbox
    const checkboxes = page.locator('.el-table .el-checkbox__input')
    const count = await checkboxes.count()
    console.log(`Found ${count} checkboxes`)

    if (count > 1) {
      // Click first project checkbox (index 1, skip header at index 0)
      await checkboxes.nth(1).click()
      await page.waitForTimeout(500)

      // Verify batch bar appears
      const batchBar = page.locator('.batch-bar')
      await expect(batchBar).toBeVisible()

      // Listen for API calls
      let batchSyncApiCalled = false
      let batchSyncResponse: any = null

      page.on('response', async response => {
        const url = response.url()
        if (url.includes('/api/sync/projects/batch-sync')) {
          batchSyncApiCalled = true
          batchSyncResponse = {
            status: response.status(),
            body: await response.json().catch(() => null)
          }
          console.log('Batch sync API response:', batchSyncResponse)
        }
      })

      // Click "Sync" button
      const syncBtn = page.locator('.batch-actions').getByRole('button', { name: /^Sync$/i })
      await expect(syncBtn).toBeVisible()
      await syncBtn.click()

      // Wait for confirmation dialog
      await page.waitForSelector('.el-message-box', { timeout: 3000 })

      // Click OK button
      const confirmBtn = page.locator('.el-message-box').getByRole('button', { name: /OK/i })
      await confirmBtn.click()

      // Wait for API call and response
      await page.waitForTimeout(2000)

      // Take screenshot
      await page.screenshot({ path: 'tests/screenshots/batch-sync-result.png', fullPage: true })

      // Verify API was called
      console.log('Batch sync API called:', batchSyncApiCalled)
      expect(batchSyncApiCalled).toBe(true)

      if (batchSyncResponse) {
        console.log('Response status:', batchSyncResponse.status)
        console.log('Response body:', JSON.stringify(batchSyncResponse.body, null, 2))

        // Verify response structure
        expect(batchSyncResponse.status).toBe(200)
        expect(batchSyncResponse.body).toHaveProperty('success')
        expect(batchSyncResponse.body).toHaveProperty('data')

        if (batchSyncResponse.body.success) {
          expect(batchSyncResponse.body.data.success).toBeGreaterThan(0)
          console.log('✅ Sync task successfully triggered for', batchSyncResponse.body.data.success, 'project(s)')
        } else {
          console.log('❌ Sync task failed:', batchSyncResponse.body.message)
        }
      }

      // Check for success message
      const successMsg = page.locator('.el-message--success')
      const hasMessage = await successMsg.count() > 0
      if (hasMessage) {
        const msgText = await successMsg.first().textContent()
        console.log('Success message:', msgText)
      }

      // Wait for scheduler to pick up the task (scheduler runs every 10 seconds)
      console.log('Waiting 15 seconds for scheduler to pick up and execute the task...')
      await page.waitForTimeout(15000)

      // Reload page to check if sync status has been updated
      await page.reload()
      await page.waitForTimeout(2000)

      // Take screenshot after waiting
      await page.screenshot({ path: 'tests/screenshots/batch-sync-after-wait.png', fullPage: true })

      console.log('✅ Test completed - Check server logs to verify sync execution')
    } else {
      console.log('Not enough projects to test batch sync')
    }
  })
})
