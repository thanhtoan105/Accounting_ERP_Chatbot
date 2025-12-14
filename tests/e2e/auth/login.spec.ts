import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../../auth.global-setup';

/**
 * Authentication - Login Tests
 *
 * Test IDs: AUTH-001, AUTH-002, AUTH-003
 * Priority: P0 (Critical)
 */

test.describe('AUTH-001: Login with valid credentials', { tag: '@smoke' }, () => {
  test('should login successfully and redirect to dashboard', async ({ page }) => {
    await page.goto('/login');
    await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

    await page.fill('[data-testid="email-input"]', TEST_USERS.accountant.email);
    await page.fill('[data-testid="password-input"]', TEST_USERS.accountant.password);
    await page.click('[data-testid="login-button"]');

    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });

    const currentUrl = page.url();
    expect(currentUrl).not.toContain('/login');
  });
});

test.describe('AUTH-002: Login with invalid credentials', () => {
  test('should display error for wrong password', async ({ page }) => {
    await page.goto('/login');
    await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

    await page.fill('[data-testid="email-input"]', TEST_USERS.accountant.email);
    await page.fill('[data-testid="password-input"]', 'WrongPassword123!');
    await page.click('[data-testid="login-button"]');

    await expect(page.getByText(/invalid|incorrect|wrong|sai/i)).toBeVisible({ timeout: 10000 });
  });
});

test.describe('AUTH-003: Login with non-existent email', () => {
  test('should display error for non-existent user', async ({ page }) => {
    await page.goto('/login');
    await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

    await page.fill('[data-testid="email-input"]', 'nonexistent@test.com');
    await page.fill('[data-testid="password-input"]', 'SomePassword123!');
    await page.click('[data-testid="login-button"]');

    await expect(page.getByText(/invalid|not found|incorrect|không tồn tại|sai/i)).toBeVisible({
      timeout: 10000,
    });
  });
});
