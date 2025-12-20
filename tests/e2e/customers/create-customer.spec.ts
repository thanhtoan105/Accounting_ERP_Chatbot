import { test, expect } from '@playwright/test';
import { CustomersPage, CustomerData } from '../../pages/CustomersPage';

/**
 * Customer Management - Create Customer Tests
 *
 * Test IDs: CUST-001, CUST-002, CUST-008
 * Priority: P0 (Smoke) + P1 (Validation)
 */

test.describe('Customer Management - Create Customer', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  let customersPage: CustomersPage;

  test.beforeEach(async ({ page }) => {
    customersPage = new CustomersPage(page);
    await customersPage.navigate();
  });

  test('CUST-001: Create customer with valid data @smoke', async ({ page }) => {
    const customerData: CustomerData = {
      name: `Test Customer ${Date.now()}`,
      email: 'customer@example.com',
      phone: '0901234567',
      address: '123 Test Street, District 1, HCMC',
      taxCode: '1234567890',
    };

    await customersPage.createCustomer(customerData);

    await customersPage.expectCustomerInList(customerData.name);
  });

  test('CUST-002: Create customer - validation errors', async ({ page }) => {
    await page.getByRole('button', { name: /add customer/i }).click();
    await customersPage.waitForModal();

    await page.getByRole('button', { name: /create customer/i }).click();

    const nameInput = page.locator('#name');
    await expect(nameInput).toHaveAttribute('aria-invalid', 'true');

    const errorMessage = page.locator('[data-error], .text-destructive, [role="alert"]');
    await expect(errorMessage.first()).toBeVisible();
  });

  test('CUST-008: Customer tax code validation', async ({ page }) => {
    await page.getByRole('button', { name: /add customer/i }).click();
    await customersPage.waitForModal();

    await page.locator('#name').fill('Tax Validation Customer');
    await page.locator('#taxCode').fill('123');

    await page.getByRole('button', { name: /create customer/i }).click();

    const taxCodeError = page.locator('#taxCode ~ [data-error], #taxCode + [data-error]');
    const formError = page.locator('[data-error], .text-destructive').filter({ hasText: /tax|mã số thuế/i });

    await expect(taxCodeError.or(formError).first()).toBeVisible();

    await page.locator('#taxCode').fill('1234567890');
    await page.getByRole('button', { name: /create customer/i }).click();

    await customersPage.expectSuccessToast('Customer created successfully');
  });
});
