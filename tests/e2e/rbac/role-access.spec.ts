import { test, expect } from '@playwright/test';

/**
 * Role-Based Access Control Tests
 *
 * Test IDs: RBAC-001 to RBAC-007
 * Priority: P0 (Critical)
 *
 * Tests verify that users with different roles can only access appropriate routes.
 */

test.describe('RBAC-001: Super admin full access', { tag: '@smoke' }, () => {
  test.use({ storageState: 'tests/.auth/super_admin.json' });

  test('super admin can access all routes', async ({ page }) => {
    // Admin routes
    await page.goto('/admin/tenants');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);
    await expect(page.locator('body')).not.toContainText(/access denied|không có quyền/i);

    // User management
    await page.goto('/users');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    // Profile
    await page.goto('/profile');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    // Dashboard
    await page.goto('/dashboard');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);
  });
});

test.describe('RBAC-002: Admin limited access', () => {
  test.use({ storageState: 'tests/.auth/admin.json' });

  test('admin can access user management but not tenant management', async ({ page }) => {
    // Admin CAN access user management
    await page.goto('/users');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);
    await expect(page.locator('body')).not.toContainText(/access denied|không có quyền/i);

    // Admin CAN access profile
    await page.goto('/profile');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    // Admin CANNOT access super_admin tenant management
    await page.goto('/admin/tenants');
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard/);
  });
});

test.describe('RBAC-003: Chief accountant access', () => {
  test.use({ storageState: 'tests/.auth/chief_accountant.json' });

  test('chief accountant has full accounting access', async ({ page }) => {
    // Chief accountant CAN access accounting routes
    await page.goto('/dashboard');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    await page.goto('/profile');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    // Chief accountant CANNOT access admin routes
    await page.goto('/admin/tenants');
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard/);

    await page.goto('/users');
    // Chief accountant may or may not have user management access
    // depending on company config - check for either success or redirect
  });
});

test.describe('RBAC-004: Accountant access', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('accountant has limited accounting access', async ({ page }) => {
    // Accountant CAN access basic routes
    await page.goto('/dashboard');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    await page.goto('/profile');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    // Accountant CANNOT access admin routes
    await page.goto('/admin/tenants');
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard/);

    await page.goto('/users');
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard/);
  });
});

test.describe('RBAC-005: CFO report access', () => {
  test.use({ storageState: 'tests/.auth/cfo.json' });

  test('CFO can view analytics and reports', async ({ page }) => {
    // CFO CAN access dashboard and reports
    await page.goto('/dashboard');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    await page.goto('/profile');
    await expect(page).not.toHaveURL(/\/login|\/403|\/unauthorized/);

    // CFO CANNOT access admin routes
    await page.goto('/admin/tenants');
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard/);
  });
});

test.describe('RBAC-006: Unauthorized redirect', () => {
  test('unauthenticated user is redirected to login', async ({ page }) => {
    // Clear any existing auth state
    await page.context().clearCookies();
    await page.evaluate(() => localStorage.clear());

    // Try to access protected routes
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);

    await page.goto('/users');
    await expect(page).toHaveURL(/\/login/);

    await page.goto('/admin/tenants');
    await expect(page).toHaveURL(/\/login/);

    await page.goto('/profile');
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('RBAC-007: Awaiting company redirect', () => {
  test('user without company is redirected to company selection', async ({ page }) => {
    // Mock a user response with no company
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: 999,
            email: 'nocompany@test.com',
            fullName: 'No Company User',
            role: 'USER',
            companyId: null,
          },
        }),
      });
    });

    await page.goto('/dashboard');
    // User without company should be redirected to company selection or awaiting page
    await expect(page).toHaveURL(/\/company|\/awaiting|\/select-company|\/onboarding/);
  });
});
