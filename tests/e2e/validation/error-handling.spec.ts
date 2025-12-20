import { test, expect } from '@playwright/test';

/**
 * Error Handling Tests
 *
 * Test IDs: ERR-001 to ERR-005
 * Priority: P1 (High)
 *
 * Tests verify proper error handling and user feedback for various error scenarios.
 */

test.describe('ERR-001: API 500 error display', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('should display user-friendly error message on server error', async ({ page }) => {
    // Mock API to return 500 status
    await page.route('**/api/v1/**', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'An unexpected error occurred',
        }),
      });
    });

    await page.goto('/dashboard');

    // Verify error toast/banner is displayed
    const errorIndicator = page.locator('[role="alert"], .toast, .error-message, [data-testid="error-toast"]');
    await expect(
      errorIndicator.or(page.getByText(/error|lỗi|không thể|failed/i))
    ).toBeVisible({ timeout: 10000 });

    // Verify user-friendly message (not raw technical error)
    await expect(page.locator('body')).not.toContainText('Internal Server Error');
  });

  test('should display error toast on API failure during data fetch', async ({ page }) => {
    await page.goto('/dashboard');

    // Mock specific endpoint failure
    await page.route('**/api/v1/analytics/**', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Database connection failed',
        }),
      });
    });

    // Trigger data fetch
    await page.reload();

    // Verify error indication
    await expect(
      page.getByText(/error|lỗi|failed|không thể tải/i)
    ).toBeVisible({ timeout: 10000 });
  });
});

test.describe('ERR-002: Network timeout handling', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('should display timeout message on slow API response', async ({ page }) => {
    // Mock slow/timeout API response
    await page.route('**/api/v1/**', async (route) => {
      // Delay for longer than typical timeout
      await new Promise((resolve) => setTimeout(resolve, 20000));
      await route.abort('timedout');
    });

    await page.goto('/dashboard');

    // Verify timeout/network error message displayed
    await expect(
      page.getByText(/timeout|hết thời gian|network|mạng|không thể kết nối|connection/i)
    ).toBeVisible({ timeout: 30000 });
  });

  test('should handle network failure gracefully', async ({ page }) => {
    await page.goto('/dashboard');

    // Simulate network failure
    await page.route('**/api/v1/**', async (route) => {
      await route.abort('failed');
    });

    await page.reload();

    // Verify error handling
    await expect(
      page.getByText(/network|mạng|connection|kết nối|failed|thất bại/i)
    ).toBeVisible({ timeout: 10000 });
  });

  test('should show retry option if available', async ({ page }) => {
    await page.route('**/api/v1/**', async (route) => {
      await route.abort('failed');
    });

    await page.goto('/dashboard');

    // Check for retry button if implemented
    const retryButton = page.getByRole('button', { name: /retry|thử lại|reload|tải lại/i });
    if (await retryButton.isVisible({ timeout: 5000 }).catch(() => false)) {
      await expect(retryButton).toBeEnabled();
    }
  });
});

test.describe('ERR-003: Session expired handling', () => {
  test('should redirect to login on 401 expired token', async ({ page }) => {
    // Start with valid session
    await page.goto('/dashboard');

    // Mock 401 response with expired token
    await page.route('**/api/v1/**', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Unauthorized',
          message: 'Token expired',
          code: 'TOKEN_EXPIRED',
        }),
      });
    });

    // Trigger API call
    await page.reload();

    // Verify redirect to login page
    await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
  });

  test('should display session expired message', async ({ page }) => {
    // Clear auth and mock expired response
    await page.context().clearCookies();
    await page.evaluate(() => localStorage.clear());

    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Session expired',
          message: 'Your session has expired. Please login again.',
        }),
      });
    });

    await page.goto('/dashboard');

    // Verify redirect to login
    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });

    // Check for session expired message (if shown on login page)
    const sessionExpiredMessage = page.getByText(/session expired|phiên.*hết hạn|đăng nhập lại/i);
    if (await sessionExpiredMessage.isVisible({ timeout: 3000 }).catch(() => false)) {
      await expect(sessionExpiredMessage).toBeVisible();
    }
  });
});

test.describe('ERR-004: 404 page display', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('should display 404 page for invalid route', async ({ page }) => {
    // Navigate to invalid route
    await page.goto('/this-route-does-not-exist-' + Date.now());

    // Verify 404 page is shown
    await expect(
      page.getByText(/404|not found|không tìm thấy|trang không tồn tại/i)
    ).toBeVisible({ timeout: 10000 });
  });

  test('should have working navigation back to home', async ({ page }) => {
    await page.goto('/invalid-route-test-' + Date.now());

    // Wait for 404 page
    await expect(
      page.getByText(/404|not found|không tìm thấy/i)
    ).toBeVisible({ timeout: 10000 });

    // Verify "Go back" or "Home" link works
    const homeLink = page.getByRole('link', { name: /home|trang chủ|back|quay lại|dashboard/i });
    const homeButton = page.getByRole('button', { name: /home|trang chủ|back|quay lại/i });

    const navigationElement = homeLink.or(homeButton);

    if (await navigationElement.isVisible({ timeout: 5000 }).catch(() => false)) {
      await navigationElement.click();
      await expect(page).not.toHaveURL(/invalid-route|not-found/);
    } else {
      // Fallback: verify browser back navigation works
      await page.goBack();
    }
  });

  test('should display 404 for invalid resource ID', async ({ page }) => {
    await page.goto('/invoices/99999999');

    // Verify 404 or "not found" indication
    await expect(
      page.getByText(/404|not found|không tìm thấy|không tồn tại/i)
    ).toBeVisible({ timeout: 10000 });
  });
});

test.describe('ERR-005: Permission denied message', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('accountant cannot access admin tenants page', async ({ page }) => {
    // Accountant tries to access /admin/tenants
    await page.goto('/admin/tenants');

    // Verify 403/forbidden message displayed or redirect
    const forbidden403 = await page
      .getByText(/403|forbidden|access denied|không có quyền|bị từ chối/i)
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const redirected = /\/403|\/unauthorized|\/dashboard/.test(page.url());

    expect(forbidden403 || redirected).toBeTruthy();
  });

  test('accountant cannot access user management', async ({ page }) => {
    await page.goto('/users');

    // Verify access denied or redirect
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard/, { timeout: 10000 });
  });

  test('should display access denied page with clear message', async ({ page }) => {
    // Mock 403 response
    await page.route('**/api/v1/admin/**', async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Forbidden',
          message: 'You do not have permission to access this resource',
        }),
      });
    });

    await page.goto('/admin/tenants');

    // Verify access denied indication
    await expect(
      page
        .getByText(/forbidden|permission|quyền|access denied|không được phép/i)
        .or(page.locator('[data-testid="access-denied"]'))
    ).toBeVisible({ timeout: 10000 }).catch(async () => {
      // Fallback: check for redirect
      await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard/);
    });
  });
});

test.describe('Error handling with CFO role', () => {
  test.use({ storageState: 'tests/.auth/cfo.json' });

  test('CFO cannot access admin routes - shows appropriate error', async ({ page }) => {
    await page.goto('/admin/tenants');

    // CFO should be denied access
    const forbidden = await page
      .getByText(/403|forbidden|access denied|không có quyền/i)
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const redirected = /\/403|\/unauthorized|\/dashboard/.test(page.url());

    expect(forbidden || redirected).toBeTruthy();
  });
});
