import { test, expect } from '@playwright/test';

/**
 * Authentication - Invitation Tests
 *
 * Test IDs: AUTH-008, AUTH-009
 * Priority: P0 (Critical)
 */

test.describe('AUTH-008: Accept invitation with valid token', () => {
  test('should accept invitation and redirect to dashboard', async ({ page }) => {
    const validToken = 'valid-test-token';

    await page.route('**/api/invitations/validate/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          email: 'newuser@test.com',
          companyName: 'Test Company',
          role: 'ACCOUNTANT',
          expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        }),
      });
    });

    await page.route('**/api/invitations/accept/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'mock-access-token',
          user: {
            id: 1,
            email: 'newuser@test.com',
            fullName: 'New User',
            companyId: 1,
          },
        }),
      });
    });

    await page.goto(`/invite/${validToken}`);

    await expect(page.getByText('Test Company')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('newuser@test.com')).toBeVisible();

    await page.fill('input[type="text"]', 'New User');
    await page.fill('input[type="password"]', 'SecurePassword123!');
    await page.locator('input[type="password"]').nth(1).fill('SecurePassword123!');

    await page.click('button[type="submit"]');

    await page.waitForURL((url) => url.pathname === '/', { timeout: 15000 });
    expect(page.url()).not.toContain('/invite');
  });
});

test.describe('AUTH-009: Accept invitation with expired token', () => {
  test('should display expiration error for expired token', async ({ page }) => {
    const expiredToken = 'expired-token';

    await page.route('**/api/invitations/validate/*', async (route) => {
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({
          error: {
            message: 'Invitation has expired',
          },
        }),
      });
    });

    await page.goto(`/invite/${expiredToken}`);

    await expect(
      page.getByText(/expired|invalid|hết hạn|không hợp lệ/i)
    ).toBeVisible({ timeout: 10000 });

    const loginButton = page.getByRole('button', { name: /login|đăng nhập/i });
    await expect(loginButton).toBeVisible();
  });

  test('should display expiration warning when invitation is past expiry date', async ({ page }) => {
    const expiredToken = 'expired-date-token';

    await page.route('**/api/invitations/validate/*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          email: 'expired@test.com',
          companyName: 'Test Company',
          role: 'ACCOUNTANT',
          expiresAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
        }),
      });
    });

    await page.goto(`/invite/${expiredToken}`);

    await expect(page.getByRole('alert')).toBeVisible({ timeout: 10000 });

    const submitButton = page.getByRole('button', { name: /accept|create|tạo/i });
    await expect(submitButton).toBeDisabled();
  });
});
