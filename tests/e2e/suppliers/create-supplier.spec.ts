import { test, expect } from '@playwright/test';
import { SuppliersPage, SupplierData } from '../../pages/SuppliersPage';

/**
 * Supplier Management - Create Supplier Tests
 *
 * Test IDs: SUPP-001, SUPP-002
 * Priority: P0 (Smoke) + P1 (Validation)
 */

test.describe('Supplier Management - Create Supplier', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  let suppliersPage: SuppliersPage;

  test.beforeEach(async ({ page }) => {
    suppliersPage = new SuppliersPage(page);
    await suppliersPage.navigate();
  });

  test('SUPP-001: Create supplier with valid data @smoke', async ({ page }) => {
    const supplierData: SupplierData = {
      name: `Test Supplier ${Date.now()}`,
      email: 'supplier@example.com',
      phone: '0901234567',
      address: '456 Supplier Street, District 3, HCMC',
      taxCode: '0987654321',
    };

    await suppliersPage.createSupplier(supplierData);

    await suppliersPage.expectSupplierInList(supplierData.name);
  });

  test('SUPP-002: Create supplier - validation errors', async ({ page }) => {
    await page.getByRole('button', { name: /add supplier/i }).click();
    await suppliersPage.waitForModal();

    await page.getByRole('button', { name: /create supplier/i }).click();

    const nameInput = page.locator('#name');
    await expect(nameInput).toHaveAttribute('aria-invalid', 'true');

    const errorMessage = page.locator('[data-error], .text-destructive, [role="alert"]');
    await expect(errorMessage.first()).toBeVisible();
  });
});
