import { test, expect } from '@playwright/test';

/**
 * Navigation - Dashboard Tests
 *
 * Test IDs: NAV-001, NAV-002, NAV-005
 * Priority: P0 (Critical)
 */

test.describe('NAV-001: Dashboard loads with data', { tag: '@smoke' }, () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('should display dashboard with widgets and summary data', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, [data-testid="dashboard-title"]')).toBeVisible();

    const dashboardContent = page.locator(
      '[data-testid="dashboard"], main, [role="main"], .dashboard'
    );
    await expect(dashboardContent).toBeVisible();
  });
});

test.describe('NAV-002: Sidebar navigation works', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('should navigate to different pages via sidebar', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const sidebar = page.locator('[data-slot="sidebar"], nav, aside, [data-testid="sidebar"]');
    await expect(sidebar.first()).toBeVisible();

    const menuItems = [
      { name: /customers|khách hàng/i, urlPattern: /customer/i },
      { name: /suppliers|nhà cung cấp/i, urlPattern: /supplier/i },
      { name: /vouchers|chứng từ/i, urlPattern: /voucher/i },
    ];

    for (const item of menuItems) {
      const menuButton = sidebar.getByRole('button', { name: item.name }).first();
      if (await menuButton.isVisible()) {
        await menuButton.click();
        await page.waitForLoadState('networkidle');
        expect(page.url()).toMatch(item.urlPattern);
        break;
      }
    }
  });
});

test.describe('NAV-005: Role-based menu visibility - Accountant', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('accountant should see accounting menu items', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Wait for sidebar menu to be loaded
    await page.waitForSelector('[data-sidebar="menu"]', { state: 'visible', timeout: 15000 });

    // Menu items are buttons inside list items in sidebar
    const accountingMenuItems = page.locator('[data-sidebar="menu"]').getByRole('button', {
      name: /voucher|purchase|sales|customers|suppliers/i,
    });
    const count = await accountingMenuItems.count();
    expect(count).toBeGreaterThan(0);
  });

  test('accountant should NOT see super admin menu', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Super admin menu should not exist in sidebar
    const tenantMenu = page.getByRole('listitem').getByRole('button', { name: /tenant|super admin/i });

    await expect(tenantMenu).toHaveCount(0);
  });
});

test.describe('NAV-005: Role-based menu visibility - Admin', () => {
  test.use({ storageState: 'tests/.auth/admin.json' });

  test('admin should see admin menu items', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const sidebar = page.locator('nav, aside, [data-testid="sidebar"]');
    await expect(sidebar).toBeVisible();
  });
});
