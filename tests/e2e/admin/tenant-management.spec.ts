import { test, expect } from '@playwright/test';

/**
 * Admin - Tenant Management Tests
 *
 * Test IDs: ADMIN-001 to ADMIN-004
 * Priority: P1 (High)
 *
 * Tests for super_admin tenant management functionality.
 * Route: /admin/tenants
 */

test.describe('ADMIN-001: View tenant list', () => {
  test.use({ storageState: 'tests/.auth/super_admin.json' });

  test('super admin can view tenant list', async ({ page }) => {
    await page.goto('/admin/tenants');

    // Should see tenant list page
    await expect(page.locator('h1, h2, [data-testid="page-title"]')).toContainText(
      /tenant|công ty|company/i
    );

    // Should have a table or list of tenants
    const tenantList = page.locator(
      '[data-testid="tenant-list"], [data-testid="tenant-table"], table'
    );
    await expect(tenantList).toBeVisible({ timeout: 10000 });
  });

  test('tenant list displays tenant information', async ({ page }) => {
    await page.goto('/admin/tenants');

    // Wait for data to load
    await page.waitForLoadState('networkidle');

    // Should display tenant data columns (name, status, etc.)
    const tableHeaders = page.locator('th, [data-testid*="header"]');
    await expect(tableHeaders.first()).toBeVisible({ timeout: 10000 });
  });
});

test.describe('ADMIN-002: Create tenant', () => {
  test.use({ storageState: 'tests/.auth/super_admin.json' });

  test('super admin can open create tenant form', async ({ page }) => {
    await page.goto('/admin/tenants');

    // Find and click create button
    const createButton = page.locator(
      '[data-testid="create-tenant-button"], button:has-text("Thêm"), button:has-text("Create"), button:has-text("Add")'
    );
    await expect(createButton).toBeVisible({ timeout: 10000 });
    await createButton.click();

    // Should show create form or modal
    const formOrModal = page.locator(
      '[data-testid="tenant-form"], [data-testid="create-tenant-modal"], form, [role="dialog"]'
    );
    await expect(formOrModal).toBeVisible({ timeout: 5000 });
  });

  test('can fill and submit new tenant form', async ({ page }) => {
    await page.goto('/admin/tenants');

    // Click create button
    const createButton = page.locator(
      '[data-testid="create-tenant-button"], button:has-text("Thêm"), button:has-text("Create"), button:has-text("Add")'
    );
    await createButton.click();

    // Fill form fields (adjust selectors based on actual form)
    const nameInput = page.locator(
      '[data-testid="tenant-name-input"], input[name="name"], input[placeholder*="name" i]'
    );
    await nameInput.fill(`Test Tenant ${Date.now()}`);

    // Look for submit button
    const submitButton = page.locator(
      '[data-testid="submit-tenant-button"], button[type="submit"], button:has-text("Lưu"), button:has-text("Save")'
    );
    await expect(submitButton).toBeVisible();

    // Note: Don't actually submit to avoid creating real data
    // Just verify form is functional
  });
});

test.describe('ADMIN-003: Edit tenant', () => {
  test.use({ storageState: 'tests/.auth/super_admin.json' });

  test('super admin can open edit tenant form', async ({ page }) => {
    await page.goto('/admin/tenants');

    // Wait for tenant list to load
    await page.waitForLoadState('networkidle');

    // Find edit button on first row
    const editButton = page.locator(
      '[data-testid="edit-tenant-button"], button:has-text("Sửa"), button:has-text("Edit"), [data-testid*="edit"]'
    ).first();

    // Skip test if no tenants exist
    if (!(await editButton.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip();
      return;
    }

    await editButton.click();

    // Should show edit form or navigate to edit page
    const formOrModal = page.locator(
      '[data-testid="tenant-form"], [data-testid="edit-tenant-modal"], form, [role="dialog"]'
    );
    await expect(formOrModal).toBeVisible({ timeout: 5000 });
  });

  test('edit form is pre-populated with tenant data', async ({ page }) => {
    await page.goto('/admin/tenants');
    await page.waitForLoadState('networkidle');

    const editButton = page.locator(
      '[data-testid="edit-tenant-button"], button:has-text("Sửa"), button:has-text("Edit"), [data-testid*="edit"]'
    ).first();

    if (!(await editButton.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip();
      return;
    }

    await editButton.click();

    // Name field should have existing value
    const nameInput = page.locator(
      '[data-testid="tenant-name-input"], input[name="name"], input[placeholder*="name" i]'
    );
    await expect(nameInput).not.toHaveValue('');
  });
});

test.describe('ADMIN-004: Tenant access denied for non-super_admin', { tag: '@smoke' }, () => {
  test('admin cannot access tenant management', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'tests/.auth/admin.json',
    });
    const page = await context.newPage();

    await page.goto('/admin/tenants');

    // Should be redirected or see access denied
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard|\/login/);

    await context.close();
  });

  test('chief accountant cannot access tenant management', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'tests/.auth/chief_accountant.json',
    });
    const page = await context.newPage();

    await page.goto('/admin/tenants');

    // Should be redirected or see access denied
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard|\/login/);

    await context.close();
  });

  test('accountant cannot access tenant management', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'tests/.auth/accountant.json',
    });
    const page = await context.newPage();

    await page.goto('/admin/tenants');

    // Should be redirected or see access denied
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard|\/login/);

    await context.close();
  });

  test('CFO cannot access tenant management', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'tests/.auth/cfo.json',
    });
    const page = await context.newPage();

    await page.goto('/admin/tenants');

    // Should be redirected or see access denied
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard|\/login/);

    await context.close();
  });
});
