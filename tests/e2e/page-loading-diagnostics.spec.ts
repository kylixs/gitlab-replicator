import { test, expect, Page } from '@playwright/test'

/**
 * Page Loading Diagnostics Test
 *
 * Comprehensive test to diagnose loading issues including:
 * - Network errors (failed requests, 4xx/5xx responses)
 * - Console errors and warnings
 * - API response validation
 * - Page rendering verification
 */

interface NetworkRequest {
  url: string
  method: string
  status: number
  statusText: string
  timing: number
}

interface ConsoleMessage {
  type: string
  text: string
  location?: string
}

class PageDiagnostics {
  private networkErrors: NetworkRequest[] = []
  private consoleMessages: ConsoleMessage[] = []
  private apiResponses: Map<string, any> = new Map()

  constructor(private page: Page) {}

  async setup() {
    // Monitor network requests
    this.page.on('response', async (response) => {
      const url = response.url()
      const status = response.status()

      // Record failed requests
      if (status >= 400) {
        this.networkErrors.push({
          url,
          method: response.request().method(),
          status,
          statusText: response.statusText(),
          timing: 0
        })
      }

      // Capture API responses for validation
      if (url.includes('/api/')) {
        try {
          const data = await response.json()
          this.apiResponses.set(url, {
            status,
            data,
            url
          })
        } catch (e) {
          // Not JSON, skip
        }
      }
    })

    // Monitor console messages
    this.page.on('console', (msg) => {
      const type = msg.type()
      const text = msg.text()

      // Only record errors and warnings
      if (type === 'error' || type === 'warning') {
        this.consoleMessages.push({
          type,
          text,
          location: msg.location()?.url
        })
      }
    })

    // Monitor page errors
    this.page.on('pageerror', (error) => {
      this.consoleMessages.push({
        type: 'pageerror',
        text: error.message
      })
    })
  }

  getNetworkErrors(): NetworkRequest[] {
    return this.networkErrors
  }

  getConsoleMessages(): ConsoleMessage[] {
    return this.consoleMessages
  }

  getApiResponses(): Map<string, any> {
    return this.apiResponses
  }

  printReport() {
    console.log('\n' + '='.repeat(80))
    console.log('PAGE DIAGNOSTICS REPORT')
    console.log('='.repeat(80))

    // Network Errors
    console.log('\n### NETWORK ERRORS ###')
    if (this.networkErrors.length === 0) {
      console.log('✅ No network errors detected')
    } else {
      console.log(`❌ Found ${this.networkErrors.length} network error(s):`)
      this.networkErrors.forEach((err, i) => {
        console.log(`\n  ${i + 1}. ${err.method} ${err.url}`)
        console.log(`     Status: ${err.status} ${err.statusText}`)
      })
    }

    // Console Messages
    console.log('\n### CONSOLE MESSAGES ###')
    if (this.consoleMessages.length === 0) {
      console.log('✅ No console errors/warnings detected')
    } else {
      console.log(`⚠️  Found ${this.consoleMessages.length} console message(s):`)
      this.consoleMessages.forEach((msg, i) => {
        console.log(`\n  ${i + 1}. [${msg.type.toUpperCase()}] ${msg.text}`)
        if (msg.location) {
          console.log(`     Location: ${msg.location}`)
        }
      })
    }

    // API Responses
    console.log('\n### API RESPONSES ###')
    if (this.apiResponses.size === 0) {
      console.log('⚠️  No API responses captured')
    } else {
      console.log(`✅ Captured ${this.apiResponses.size} API response(s):`)
      this.apiResponses.forEach((resp, url) => {
        const shortUrl = url.replace('http://localhost:9999', '')
        console.log(`\n  ${shortUrl}`)
        console.log(`    Status: ${resp.status}`)
        console.log(`    Success: ${resp.data.success}`)
        if (!resp.data.success && resp.data.error) {
          console.log(`    Error: ${JSON.stringify(resp.data.error)}`)
        }
      })
    }

    console.log('\n' + '='.repeat(80) + '\n')
  }
}

test.describe('Page Loading Diagnostics', () => {
  let diagnostics: PageDiagnostics

  test.beforeEach(async ({ page }) => {
    diagnostics = new PageDiagnostics(page)
    await diagnostics.setup()

    // Login
    await page.goto('http://localhost:3000/login')
    await page.getByLabel('Username').fill('admin')
    await page.getByLabel('Password').fill('Admin@123')
    await page.getByRole('button', { name: /Sign In/i }).click()
    await page.waitForURL(/\/(dashboard)?/, { timeout: 10000 })
  })

  test('Dashboard - Check loading errors', async ({ page }) => {
    console.log('\n🔍 Testing Dashboard Page...\n')

    // Wait for page to stabilize
    await page.waitForTimeout(3000)

    // Print diagnostics report
    diagnostics.printReport()

    // Assertions
    const networkErrors = diagnostics.getNetworkErrors()
    const consoleMessages = diagnostics.getConsoleMessages()

    // Check for critical errors
    const criticalConsoleErrors = consoleMessages.filter(
      msg => msg.type === 'error' || msg.type === 'pageerror'
    )

    expect(networkErrors.length,
      `Found ${networkErrors.length} network errors`).toBe(0)

    expect(criticalConsoleErrors.length,
      `Found ${criticalConsoleErrors.length} console errors`).toBe(0)

    // Verify key API responses
    const apiResponses = diagnostics.getApiResponses()
    const statsResponse = Array.from(apiResponses.entries())
      .find(([url]) => url.includes('/api/dashboard/stats'))

    expect(statsResponse, 'Dashboard stats API should be called').toBeDefined()
    if (statsResponse) {
      expect(statsResponse[1].data.success, 'Stats API should succeed').toBe(true)
    }
  })

  test('Projects Page - Check loading errors', async ({ page }) => {
    console.log('\n🔍 Testing Projects Page...\n')

    // Navigate to projects
    await page.click('a[href="/projects"]')
    await page.waitForURL('**/projects')
    await page.waitForTimeout(3000)

    // Print diagnostics report
    diagnostics.printReport()

    // Assertions
    const networkErrors = diagnostics.getNetworkErrors()
    const consoleMessages = diagnostics.getConsoleMessages()

    const criticalConsoleErrors = consoleMessages.filter(
      msg => msg.type === 'error' || msg.type === 'pageerror'
    )

    expect(networkErrors.length,
      `Found ${networkErrors.length} network errors`).toBe(0)

    expect(criticalConsoleErrors.length,
      `Found ${criticalConsoleErrors.length} console errors`).toBe(0)

    // Verify projects API was called
    const apiResponses = diagnostics.getApiResponses()
    const projectsResponse = Array.from(apiResponses.entries())
      .find(([url]) => url.includes('/api/projects'))

    expect(projectsResponse, 'Projects API should be called').toBeDefined()
    if (projectsResponse) {
      expect(projectsResponse[1].data.success, 'Projects API should succeed').toBe(true)
    }
  })

  test('Sync Results Page - Check loading errors', async ({ page }) => {
    console.log('\n🔍 Testing Sync Results Page...\n')

    // Navigate to sync results
    await page.click('a[href="/sync-results"]')
    await page.waitForURL('**/sync-results')
    await page.waitForTimeout(3000)

    // Print diagnostics report
    diagnostics.printReport()

    // Assertions
    const networkErrors = diagnostics.getNetworkErrors()
    const consoleMessages = diagnostics.getConsoleMessages()

    const criticalConsoleErrors = consoleMessages.filter(
      msg => msg.type === 'error' || msg.type === 'pageerror'
    )

    expect(networkErrors.length,
      `Found ${networkErrors.length} network errors`).toBe(0)

    expect(criticalConsoleErrors.length,
      `Found ${criticalConsoleErrors.length} console errors`).toBe(0)
  })

  test('Check all API endpoints health', async ({ page }) => {
    console.log('\n🔍 Testing All API Endpoints Health...\n')

    const endpoints = [
      '/api/dashboard/stats',
      '/api/dashboard/status-distribution',
      '/api/dashboard/delayed-projects',
      '/api/dashboard/recent-events',
      '/api/projects?page=1&size=20',
      '/api/sync/results?page=1&size=20'
    ]

    const results: any[] = []

    for (const endpoint of endpoints) {
      try {
        const response = await page.request.get(`http://localhost:9999${endpoint}`)
        const data = await response.json()

        results.push({
          endpoint,
          status: response.status(),
          success: data.success,
          error: data.error
        })
      } catch (error) {
        results.push({
          endpoint,
          status: 0,
          success: false,
          error: String(error)
        })
      }
    }

    console.log('\n### API ENDPOINTS HEALTH CHECK ###\n')
    results.forEach(result => {
      const icon = result.success ? '✅' : '❌'
      console.log(`${icon} ${result.endpoint}`)
      console.log(`   Status: ${result.status}, Success: ${result.success}`)
      if (result.error) {
        console.log(`   Error: ${JSON.stringify(result.error)}`)
      }
    })

    // All endpoints should succeed
    const failedEndpoints = results.filter(r => !r.success)
    expect(failedEndpoints.length,
      `${failedEndpoints.length} endpoints failed: ${failedEndpoints.map(f => f.endpoint).join(', ')}`
    ).toBe(0)
  })

  test('Performance - Check page load times', async ({ page }) => {
    console.log('\n⏱️  Testing Page Load Performance...\n')

    const pages = [
      { name: 'Dashboard', url: 'http://localhost:3000/' },
      { name: 'Projects', url: 'http://localhost:3000/projects' },
      { name: 'Sync Results', url: 'http://localhost:3000/sync-results' }
    ]

    for (const pageInfo of pages) {
      const startTime = Date.now()
      await page.goto(pageInfo.url)
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      const loadTime = Date.now() - startTime

      console.log(`${pageInfo.name}: ${loadTime}ms`)

      // Warn if load time is slow
      if (loadTime > 5000) {
        console.log(`  ⚠️  Slow load time detected!`)
      }
    }

    console.log()
  })
})
