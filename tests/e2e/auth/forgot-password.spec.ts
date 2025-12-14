import { test, expect } from '@playwright/test';

/**
 * Authentication - Forgot/Reset Password Tests
 *
 * Test IDs: AUTH-005, AUTH-006, AUTH-007
 * Priority: P1 (High)
 */

test.describe('AUTH-005: Forgot password request', () => {
  test('should send password reset email successfully', async ({ page }) => {
    await page.route('**/api/v1/auth/forgot-password', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Password reset email sent' }),
      });
    });

    await page.goto('/forgot-password');
    await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

    await page.fill('[data-testid="email-input"]', 'user@test.com');
    await page.click('[data-testid="submit-button"]');

    await expect(page.getByText(/email sent|check your email|đã gửi/i)).toBeVisible({
      timeout: 10000,
    });
  });

  test('should show error for invalid email format', async ({ page }) => {
    await page.goto('/forgot-password');
    await page.waitForSelector('[data-testid="email-input"]', { state: 'visible' });

    await page.fill('[data-testid="email-input"]', 'invalid-email');
    await page.click('[data-testid="submit-button"]');

    await expect(page.getByText(/invalid email|email không hợp lệ/i)).toBeVisible({
      timeout: 5000,
    });
  });
});

test.describe('AUTH-006: Reset password with valid token', () => {
  test('should reset password successfully and redirect to login', async ({ page }) => {
    await page.route('**/api/v1/auth/validate-reset-token*', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ valid: true, email: 'user@test.com' }),
      });
    });

    await page.route('**/api/v1/auth/reset-password', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Password reset successful' }),
      });
    });

    await page.goto('/reset-password?token=valid-test-token');
    await page.waitForSelector('[data-testid="password-input"]', { state: 'visible' });

    await page.fill('[data-testid="password-input"]', 'NewPassword@123');
    await page.fill('[data-testid="confirm-password-input"]', 'NewPassword@123');
    await page.click('[data-testid="submit-button"]');

    await expect(page.getByText(/password reset|success|thành công/i)).toBeVisible({
      timeout: 10000,
    });

    await page.waitForURL('**/login', { timeout: 10000 });
    expect(page.url()).toContain('/login');
  });

  test('should show error when passwords do not match', async ({ page }) => {
    await page.route('**/api/v1/auth/validate-reset-token*', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ valid: true, email: 'user@test.com' }),
      });
    });

    await page.goto('/reset-password?token=valid-test-token');
    await page.waitForSelector('[data-testid="password-input"]', { state: 'visible' });

    await page.fill('[data-testid="password-input"]', 'NewPassword@123');
    await page.fill('[data-testid="confirm-password-input"]', 'DifferentPassword@123');
    await page.click('[data-testid="submit-button"]');

    await expect(page.getByText(/passwords do not match|không khớp/i)).toBeVisible({
      timeout: 5000,
    });
  });
});

test.describe('AUTH-007: Reset password with invalid/expired token', () => {
  test('should show error for invalid token', async ({ page }) => {
    await page.route('**/api/v1/auth/validate-reset-token*', (route) => {
      route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Invalid token' }),
      });
    });

    await page.goto('/reset-password?token=invalid-token');

    await expect(page.getByText(/invalid|expired|hết hạn|không hợp lệ/i)).toBeVisible({
      timeout: 10000,
    });
  });

  test('should show error for expired token', async ({ page }) => {
    await page.route('**/api/v1/auth/validate-reset-token*', (route) => {
      route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Token expired' }),
      });
    });

    await page.goto('/reset-password?token=expired-token');

    await expect(page.getByText(/expired|invalid|hết hạn|không hợp lệ/i)).toBeVisible({
      timeout: 10000,
    });
  });

  test('should show error when no token provided', async ({ page }) => {
    await page.goto('/reset-password');

    await expect(page.getByText(/invalid|missing|token required|không hợp lệ/i)).toBeVisible({
      timeout: 10000,
    });
  });
});
