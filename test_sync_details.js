const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext();
  const page = await context.newPage();

  // Listen to API responses
  page.on('response', async response => {
    if (response.url().includes('/api/sync/projects/') && response.url().includes('/result')) {
      console.log('\n=== API Response ===');
      console.log('URL:', response.url());
      try {
        const data = await response.json();
        console.log('Response Data:', JSON.stringify(data, null, 2));
      } catch (e) {
        console.log('Failed to parse response:', e.message);
      }
    }
  });

  // Navigate to the projects page
  await page.goto('http://localhost:3000');
  
  // Wait for login or projects page to load
  await page.waitForTimeout(2000);
  
  // Check if we need to login
  const loginButton = await page.$('button:has-text("Login")');
  if (loginButton) {
    console.log('Login page detected, please login manually...');
    await page.waitForTimeout(10000);
  }

  // Wait for projects table to load
  await page.waitForSelector('.el-table', { timeout: 10000 });
  console.log('\n=== Projects page loaded ===');

  // Find the ai/test-rails-5 project row
  const projectRow = await page.locator('text=ai/test-rails-5').first();
  if (await projectRow.count() > 0) {
    console.log('\n=== Found ai/test-rails-5 project ===');
    
    // Find and click the Sync status tag in the same row
    const row = projectRow.locator('xpath=ancestor::tr');
    const syncTag = row.locator('.el-tag').first();
    
    console.log('\n=== Clicking Sync status tag ===');
    await syncTag.click();
    
    // Wait for dialog to appear
    await page.waitForSelector('.el-dialog', { timeout: 5000 });
    console.log('\n=== Sync Details Dialog opened ===');
    
    // Extract dialog content
    await page.waitForTimeout(1000);
    const dialogContent = await page.locator('.el-dialog .sync-details').textContent();
    console.log('\n=== Dialog Content ===');
    console.log(dialogContent);
    
    // Extract specific fields
    const lastSyncAt = await page.locator('.el-descriptions-item:has-text("Last Sync At")').locator('.el-descriptions-item__content').textContent();
    console.log('\n=== Last Sync At (displayed in UI) ===');
    console.log(lastSyncAt.trim());
    
    await page.waitForTimeout(3000);
  } else {
    console.log('Project ai/test-rails-5 not found');
  }

  await browser.close();
})();
