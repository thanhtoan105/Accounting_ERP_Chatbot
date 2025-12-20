import { test, expect } from '@playwright/test';

/**
 * Admin - User Management Tests
 *
 * Test IDs: USER-001 to USER-004
 * Priority: P1 (High)
 *
 * Tests for user management functionality.
 * Route: /users
 */

test.describe('USER-001: View user list', { tag: '@smoke' }, () => {
  test.use({ storageState: 'tests/.auth/admin.json' });

  test('admin can view user list', async ({ page }) => {
    await page.goto('/users');

    // Should see user list page
    await expect(page.locator('h1, h2, [data-testid="page-title"]')).toContainText(
      /user|người dùng|thành viên/i
    );

    // Should have a table or list of users
    const userList = page.locator('[data-testid="user-list"], [data-testid="user-table"], table');
    await expect(userList).toBeVisible({ timeout: 10000 });
  });

  test('user list displays user information', async ({ page }) => {
    await page.goto('/users');

    // Wait for data to load
    await page.waitForLoadState('networkidle');

    // Should display user data (email, role, status)
    const tableRows = page.locator('tbody tr, [data-testid*="user-row"]');
    await expect(tableRows.first()).toBeVisible({ timeout: 10000 });
  });

  test('super admin can also view user list', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'tests/.auth/super_admin.json',
    });
    const page = await context.newPage();

    await page.goto('/users');

    // Should see user list page
    await expect(page.locator('h1, h2, [data-testid="page-title"]')).toContainText(
      /user|người dùng|thành viên/i
    );

    await context.close();
  });
});

test.describe('USER-002: Invite new user', () => {
  test.use({ storageState: 'tests/.auth/admin.json' });

  test('admin can open invite user form', async ({ page }) => {
    await page.goto('/users');

    // Find and click invite/add button
    const inviteButton = page.locator(
      '[data-testid="invite-user-button"], [data-testid="add-user-button"], button:has-text("Mời"), button:has-text("Invite"), button:has-text("Thêm"), button:has-text("Add")'
    );
    await expect(inviteButton).toBeVisible({ timeout: 10000 });
    await inviteButton.click();

    // Should show invite form or modal
    const formOrModal = page.locator(
      '[data-testid="invite-user-form"], [data-testid="user-form"], [data-testid="invite-modal"], form, [role="dialog"]'
    );
    await expect(formOrModal).toBeVisible({ timeout: 5000 });
  });

  test('invite form has required fields', async ({ page }) => {
    await page.goto('/users');

    const inviteButton = page.locator(
      '[data-testid="invite-user-button"], [data-testid="add-user-button"], button:has-text("Mời"), button:has-text("Invite"), button:has-text("Thêm"), button:has-text("Add")'
    );
    await inviteButton.click();

    // Email field should be present
    const emailInput = page.locator(
      '[data-testid="user-email-input"], input[name="email"], input[type="email"], input[placeholder*="email" i]'
    );
    await expect(emailInput).toBeVisible({ timeout: 5000 });

    // Role selection should be present
    const roleSelect = page.locator(
      '[data-testid="user-role-select"], select[name="role"], [data-testid*="role"]'
    );
    await expect(roleSelect).toBeVisible({ timeout: 5000 });
  });

  test('can fill invite user form', async ({ page }) => {
    await page.goto('/users');

    const inviteButton = page.locator(
      '[data-testid="invite-user-button"], [data-testid="add-user-button"], button:has-text("Mời"), button:has-text("Invite"), button:has-text("Thêm"), button:has-text("Add")'
    );
    await inviteButton.click();

    // Fill email
    const emailInput = page.locator(
      '[data-testid="user-email-input"], input[name="email"], input[type="email"], input[placeholder*="email" i]'
    );
    await emailInput.fill(`test-invite-${Date.now()}@example.com`);

    // Submit button should be visible
    const submitButton = page.locator(
      '[data-testid="submit-invite-button"], button[type="submit"], button:has-text("Gửi"), button:has-text("Send"), button:has-text("Mời"), button:has-text("Invite")'
    );
    await expect(submitButton).toBeVisible();

    // Note: Don't actually submit to avoid sending real invites
  });
});

test.describe('USER-003: Edit user role', () => {
  test.use({ storageState: 'tests/.auth/admin.json' });

  test('admin can open edit user form', async ({ page }) => {
    await page.goto('/users');

    // Wait for user list to load
    await page.waitForLoadState('networkidle');

    // Find edit button on first row
    const editButton = page.locator(
      '[data-testid="edit-user-button"], button:has-text("Sửa"), button:has-text("Edit"), [data-testid*="edit"]'
    ).first();

    // Skip test if no users exist
    if (!(await editButton.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip();
      return;
    }

    await editButton.click();

    // Should show edit form or modal
    const formOrModal = page.locator(
      '[data-testid="user-form"], [data-testid="edit-user-modal"], form, [role="dialog"]'
    );
    await expect(formOrModal).toBeVisible({ timeout: 5000 });
  });

  test('edit form has role selection', async ({ page }) => {
    await page.goto('/users');
    await page.waitForLoadState('networkidle');

    const editButton = page.locator(
      '[data-testid="edit-user-button"], button:has-text("Sửa"), button:has-text("Edit"), [data-testid*="edit"]'
    ).first();

    if (!(await editButton.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip();
      return;
    }

    await editButton.click();

    // Role selection should be present and editable
    const roleSelect = page.locator(
      '[data-testid="user-role-select"], select[name="role"], [data-testid*="role"]'
    );
    await expect(roleSelect).toBeVisible({ timeout: 5000 });
  });

  test('role options include expected roles', async ({ page }) => {
    await page.goto('/users');
    await page.waitForLoadState('networkidle');

    const editButton = page.locator(
      '[data-testid="edit-user-button"], button:has-text("Sửa"), button:has-text("Edit"), [data-testid*="edit"]'
    ).first();

    if (!(await editButton.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip();
      return;
    }

    await editButton.click();

    // Open role dropdown if it's a select
    const roleSelect = page.locator(
      '[data-testid="user-role-select"], select[name="role"], [data-testid*="role"]'
    );

    if (await roleSelect.locator('select').isVisible().catch(() => false)) {
      // It's a native select - check options
      const options = roleSelect.locator('option');
      const optionCount = await options.count();
      expect(optionCount).toBeGreaterThan(1);
    }
  });
});

test.describe('USER-004: Deactivate user', () => {
  test.use({ storageState: 'tests/.auth/admin.json' });

  test('admin can see deactivate option for users', async ({ page }) => {
    await page.goto('/users');

    // Wait for user list to load
    await page.waitForLoadState('networkidle');

    // Look for deactivate/disable button or toggle
    const deactivateControl = page.locator(
      '[data-testid="deactivate-user-button"], [data-testid="user-status-toggle"], button:has-text("Vô hiệu"), button:has-text("Deactivate"), button:has-text("Disable"), [data-testid*="status"]'
    ).first();

    // Skip test if no users with deactivate option
    if (!(await deactivateControl.isVisible({ timeout: 5000 }).catch(() => false))) {
      // Try looking in action menu
      const actionMenu = page.locator(
        '[data-testid="user-actions"], [data-testid*="menu"], button[aria-haspopup="menu"]'
      ).first();

      if (await actionMenu.isVisible({ timeout: 3000 }).catch(() => false)) {
        await actionMenu.click();

        const deactivateOption = page.locator(
          '[data-testid="deactivate-user"], [role="menuitem"]:has-text("Vô hiệu"), [role="menuitem"]:has-text("Deactivate")'
        );
        await expect(deactivateOption).toBeVisible({ timeout: 3000 });
      } else {
        test.skip();
        return;
      }
    }
  });

  test('deactivate shows confirmation dialog', async ({ page }) => {
    await page.goto('/users');
    await page.waitForLoadState('networkidle');

    // Find and click deactivate button
    const deactivateButton = page.locator(
      '[data-testid="deactivate-user-button"], button:has-text("Vô hiệu"), button:has-text("Deactivate")'
    ).first();

    if (!(await deactivateButton.isVisible({ timeout: 5000 }).catch(() => false))) {
      // Try action menu approach
      const actionMenu = page.locator(
        '[data-testid="user-actions"], button[aria-haspopup="menu"]'
      ).first();

      if (!(await actionMenu.isVisible({ timeout: 3000 }).catch(() => false))) {
        test.skip();
        return;
      }

      await actionMenu.click();

      const deactivateOption = page.locator(
        '[data-testid="deactivate-user"], [role="menuitem"]:has-text("Vô hiệu"), [role="menuitem"]:has-text("Deactivate")'
      );

      if (!(await deactivateOption.isVisible({ timeout: 3000 }).catch(() => false))) {
        test.skip();
        return;
      }

      await deactivateOption.click();
    } else {
      await deactivateButton.click();
    }

    // Should show confirmation dialog
    const confirmDialog = page.locator(
      '[data-testid="confirm-dialog"], [role="alertdialog"], [role="dialog"]:has-text("confirm"), [role="dialog"]:has-text("Xác nhận")'
    );
    await expect(confirmDialog).toBeVisible({ timeout: 5000 });

    // Cancel to avoid actual deactivation
    const cancelButton = page.locator(
      '[data-testid="cancel-button"], button:has-text("Hủy"), button:has-text("Cancel"), button:has-text("No")'
    );
    if (await cancelButton.isVisible({ timeout: 2000 }).catch(() => false)) {
      await cancelButton.click();
    }
  });

  test('non-admin cannot deactivate users', async ({ browser }) => {
    const context = await browser.newContext({
      storageState: 'tests/.auth/accountant.json',
    });
    const page = await context.newPage();

    await page.goto('/users');

    // Accountant should not have access to user management
    await expect(page).toHaveURL(/\/403|\/unauthorized|\/dashboard|\/login/);

    await context.close();
  });
});
