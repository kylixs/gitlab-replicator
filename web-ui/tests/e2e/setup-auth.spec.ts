import { test as setup } from '@playwright/test';

const authFile = 'tests/.auth/user.json';

setup('authenticate', async ({ page }) => {
  // Login
  await page.goto('http://localhost:3000/login');
  await page.getByLabel('Username').fill('admin');
  await page.getByLabel('Password').fill('Admin@123');
  await page.getByRole('button', { name: /Sign In/i }).click();

  // Wait for login to complete
  await page.waitForTimeout(2000);
  await page.waitForURL(/\/(dashboard)?/, { timeout: 5000 });

  // Save signed-in state to 'authFile'
  await page.context().storageState({ path: authFile });
});
