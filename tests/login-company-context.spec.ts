import { test, expect } from '@playwright/test';

/**
 * Test login flow and company context header fix.
 * Verifies that after login, company-scoped pages work without
 * "Missing company context (X-Company-Id header required)" error.
 */
test.describe('Login and Company Context', () => {
  test('should login and access company-scoped pages without error', async ({ page }) => {
    // Navigate to login page (use full URL to ensure correct server)
    await page.goto('http://localhost:5173/login');
    await page.waitForLoadState('networkidle');
    
    // Wait for React to hydrate
    await page.waitForTimeout(2000);
    
    // Take screenshot of login page
    await page.screenshot({ path: 'test-results/01_login_page.png' });
    
    // Fill login form
    await page.fill('[data-testid="email-input"]', 'user1@demo-01.demo.local');
    await page.fill('[data-testid="password-input"]', 'password');
    
    // Take screenshot before submit
    await page.screenshot({ path: 'test-results/02_login_filled.png' });
    
    // Click login button
    await page.click('[data-testid="login-button"]');
    
    // Wait for navigation after login (success toast shows for 1.5s before redirect)
    await page.waitForTimeout(2000);
    await page.waitForLoadState('networkidle');
    
    // Take screenshot after login
    await page.screenshot({ path: 'test-results/03_after_login.png' });
    
    // Verify redirected from login page
    expect(page.url()).not.toContain('/login');
    
    // Test navigating to UserManagement page
    await page.goto('http://localhost:5173/admin/users');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    
    await page.screenshot({ path: 'test-results/04_user_management.png' });
    
    // Check for error messages
    const content1 = await page.content();
    expect(content1).not.toContain('Missing company context');
    expect(content1).not.toContain('X-Company-Id header required');
    
    // Test VoucherList page
    await page.goto('http://localhost:5173/vouchers');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    
    await page.screenshot({ path: 'test-results/05_vouchers.png' });
    
    const content2 = await page.content();
    expect(content2).not.toContain('Missing company context');
    expect(content2).not.toContain('X-Company-Id header required');
    
    // Test Customers page
    await page.goto('http://localhost:5173/customers');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    
    await page.screenshot({ path: 'test-results/06_customers.png' });
    
    const content3 = await page.content();
    expect(content3).not.toContain('Missing company context');
    expect(content3).not.toContain('X-Company-Id header required');
  });
});
