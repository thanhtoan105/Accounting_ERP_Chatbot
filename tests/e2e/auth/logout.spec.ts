import { test, expect } from '@playwright/test';

/**
 * Authentication - Logout Tests
 *
 * Test ID: AUTH-004
 * Priority: P0 (Critical)
 */

test.describe('AUTH-004: Logout from application', { tag: '@smoke' }, () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('should logout and redirect to login page', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="user-menu-button"]');
    await page.click('[data-testid="logout-button"]');

    await page.waitForURL(/\/login/, { timeout: 10000 });
    expect(page.url()).toContain('/login');

    await page.goto('/');
    await page.waitForURL(/\/login/, { timeout: 10000 });
    expect(page.url()).toContain('/login');
  });
});
