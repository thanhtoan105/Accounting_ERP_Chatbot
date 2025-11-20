import { test, expect } from '../support/fixtures';
import { loginAsUser } from '../support/helpers/auth-helper';

/**
 * Cash Payments E2E Tests
 * 
 * Tests critical user journeys for cash payment management:
 * - Payment creation with supplier picker (only suppliers with open bills)
 * - FIFO allocation to multiple bills
 * - Overpayment prevention
 * - Standalone payment (admin-only)
 * - Payment approval workflow integration
 * - Account balance validation
 * 
 * Pattern: Given-When-Then structure, network-first route interception,
 * data-testid selectors, one assertion per test
 * 
 * Story: 4.3 (Cash Payments - Linked to Bills, Standalone)
 */
test.describe('Cash Payments - Linked to Bills, Standalone', () => {
  // Setup: Login before each test
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'admin@example.com', 'password', 'accountant');
  });

  // AC#1: Payment form with supplier picker
  test.describe('4.3-E2E-001: Payment Form with Supplier Picker', () => {
    test('should display only suppliers with open/unpaid bills in supplier picker', async ({ 
      page, 
      supplierFactory, 
      purchaseBillFactory 
    }) => {
      // GIVEN: Supplier with open bills exists
      const supplier = supplierFactory.createSupplier();
      const openBill = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        status: 'POSTED' // Open bill (not fully paid)
      });

      // Intercept supplier API - only return suppliers with open bills
      await page.route('**/api/v1/ap-payments/suppliers/*/open-bills', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: [openBill],
            total: 1,
          }),
        });
      });

      await page.route('**/api/v1/suppliers*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: [supplier],
            total: 1,
            page: 0,
            size: 10,
            totalPages: 1,
          }),
        });
      });

      // Navigate to payment creation form
      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User opens supplier picker
      const supplierPicker = page.locator('[data-testid="supplier-picker"]');
      await supplierPicker.waitFor({ state: 'visible', timeout: 10000 });
      await supplierPicker.click();

      // Wait for supplier list to load
      await page.waitForSelector('[data-testid="supplier-option"]', { state: 'visible' });

      // THEN: Only suppliers with open bills are displayed
      const supplierOptions = page.locator('[data-testid="supplier-option"]');
      await expect(supplierOptions).toHaveCount(1);
      await expect(supplierOptions.first()).toContainText(supplier.name);
    });

    test('should not display suppliers without open bills in supplier picker', async ({ 
      page, 
      supplierFactory 
    }) => {
      // GIVEN: Supplier exists but has no open bills
      const supplier = supplierFactory.createSupplier();

      // Intercept API - supplier has no open bills
      await page.route('**/api/v1/ap-payments/suppliers/*/open-bills', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: [],
            total: 0,
          }),
        });
      });

      await page.route('**/api/v1/suppliers*', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: [supplier],
            total: 1,
            page: 0,
            size: 10,
            totalPages: 1,
          }),
        });
      });

      // Navigate to payment creation form
      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User opens supplier picker
      const supplierPicker = page.locator('[data-testid="supplier-picker"]');
      await supplierPicker.click();

      // THEN: Supplier is not displayed (no open bills)
      await expect(page.locator('[data-testid="supplier-option"]')).toHaveCount(0);
    });
  });

  // AC#2: Multiple bills allocation with FIFO
  test.describe('4.3-E2E-002: Multiple Bills Allocation with FIFO', () => {
    test('should automatically allocate payment to bills using FIFO algorithm', async ({ 
      page, 
      supplierFactory, 
      purchaseBillFactory,
      paymentFactory
    }) => {
      // GIVEN: Supplier with multiple open bills exists
      const supplier = supplierFactory.createSupplier();
      const bill1 = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        dueDate: '2024-01-15', // Oldest
        totalAmount: 1000000,
        status: 'POSTED'
      });
      const bill2 = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        dueDate: '2024-02-15', // Newer
        totalAmount: 2000000,
        status: 'POSTED'
      });

      // Intercept open bills API
      await page.route('**/api/v1/ap-payments/suppliers/*/open-bills', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: [bill1, bill2],
            total: 2,
          }),
        });
      });

      // Navigate to payment form
      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User selects supplier and enters payment amount
      const supplierPicker = page.locator('[data-testid="supplier-picker"]');
      await supplierPicker.click();
      await page.fill('[data-testid="supplier-search"]', supplier.name);
      await page.click(`[data-testid="supplier-option-${supplier.id}"]`);

      await page.fill('[data-testid="payment-amount"]', '2500000');

      // THEN: FIFO allocation preview shows oldest bill allocated first
      const allocationGrid = page.locator('[data-testid="payment-allocation-grid"]');
      await expect(allocationGrid).toBeVisible();

      // First allocation should be to bill1 (oldest due date)
      const firstAllocation = allocationGrid.locator('[data-testid="allocation-row"]').first();
      await expect(firstAllocation.locator('[data-testid="bill-number"]')).toContainText(bill1.billNumber);
      await expect(firstAllocation.locator('[data-testid="allocated-amount"]')).toHaveValue('1000000');
    });

    test('should allow manual override of FIFO allocation before posting', async ({ 
      page, 
      supplierFactory, 
      purchaseBillFactory
    }) => {
      // GIVEN: Supplier with multiple open bills and payment created
      const supplier = supplierFactory.createSupplier();
      const bill1 = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        dueDate: '2024-01-15',
        totalAmount: 1000000,
        status: 'POSTED'
      });
      const bill2 = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        dueDate: '2024-02-15',
        totalAmount: 2000000,
        status: 'POSTED'
      });

      await page.route('**/api/v1/ap-payments/suppliers/*/open-bills', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: [bill1, bill2],
            total: 2,
          }),
        });
      });

      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User manually modifies allocation amounts
      const supplierPicker = page.locator('[data-testid="supplier-picker"]');
      await supplierPicker.click();
      await page.fill('[data-testid="supplier-search"]', supplier.name);
      await page.click(`[data-testid="supplier-option-${supplier.id}"]`);

      await page.fill('[data-testid="payment-amount"]', '2500000');

      // Override FIFO allocation - allocate more to bill2
      const allocationGrid = page.locator('[data-testid="payment-allocation-grid"]');
      const secondAllocation = allocationGrid.locator('[data-testid="allocation-row"]').nth(1);
      await secondAllocation.locator('[data-testid="allocated-amount-input"]').fill('2000000');

      // THEN: Manual allocation is saved and displayed
      await expect(secondAllocation.locator('[data-testid="allocated-amount"]')).toHaveValue('2000000');
    });
  });

  // AC#3: Required fields validation
  test.describe('4.3-E2E-003: Required Fields Validation', () => {
    test('should require all mandatory fields before submission', async ({ page }) => {
      // GIVEN: User is on payment form
      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User attempts to submit without filling required fields
      const submitButton = page.locator('[data-testid="submit-payment-button"]');
      await submitButton.click();

      // THEN: Validation errors are displayed for all required fields
      await expect(page.locator('[data-testid="error-supplier"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-payment-date"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-account"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-payee"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-amount"]')).toBeVisible();
    });

    test('should require payment proof when payment amount exceeds threshold', async ({ 
      page, 
      supplierFactory,
      purchaseBillFactory
    }) => {
      // GIVEN: Payment amount exceeds approval threshold (20M VND)
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ supplierId: supplier.id! });

      await page.route('**/api/v1/ap-payments/suppliers/*/open-bills', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: [bill], total: 1 }),
        });
      });

      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User enters payment amount above threshold without proof
      const supplierPicker = page.locator('[data-testid="supplier-picker"]');
      await supplierPicker.click();
      await page.fill('[data-testid="supplier-search"]', supplier.name);
      await page.click(`[data-testid="supplier-option-${supplier.id}"]`);

      await page.fill('[data-testid="payment-amount"]', '25000000'); // Above 20M threshold

      const submitButton = page.locator('[data-testid="submit-payment-button"]');
      await submitButton.click();

      // THEN: Payment proof validation error is displayed
      await expect(page.locator('[data-testid="error-payment-proof"]')).toBeVisible();
    });
  });

  // AC#4: Overpayment prevention
  test.describe('4.3-E2E-004: Overpayment Prevention', () => {
    test('should prevent overpayment when allocated amount exceeds bill remaining balance', async ({ 
      page, 
      supplierFactory, 
      purchaseBillFactory
    }) => {
      // GIVEN: Supplier with bill having remaining balance of 1M VND
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ 
        supplierId: supplier.id!,
        totalAmount: 1000000,
        status: 'POSTED'
      });

      await page.route('**/api/v1/ap-payments/suppliers/*/open-bills', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: [{ ...bill, remainingBalance: 1000000 }],
            total: 1,
          }),
        });
      });

      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User attempts to allocate more than remaining balance
      const supplierPicker = page.locator('[data-testid="supplier-picker"]');
      await supplierPicker.click();
      await page.fill('[data-testid="supplier-search"]', supplier.name);
      await page.click(`[data-testid="supplier-option-${supplier.id}"]`);

      const allocationGrid = page.locator('[data-testid="payment-allocation-grid"]');
      const allocationInput = allocationGrid.locator('[data-testid="allocated-amount-input"]').first();
      await allocationInput.fill('1500000'); // Exceeds remaining balance

      // THEN: Overpayment validation error is displayed
      await expect(page.locator('[data-testid="error-overpayment"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-overpayment"]')).toContainText('exceeds remaining balance');
    });
  });

  // AC#5: Standalone payment (admin-only)
  test.describe('4.3-E2E-005: Standalone Payment', () => {
    test('should allow admin to create standalone payment with warning tag', async ({ page }) => {
      // GIVEN: Admin user is on payment form
      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: Admin enables standalone payment toggle
      const standaloneToggle = page.locator('[data-testid="standalone-payment-toggle"]');
      await standaloneToggle.click();

      // THEN: Warning tag is displayed
      await expect(page.locator('[data-testid="standalone-warning-tag"]')).toBeVisible();
      await expect(page.locator('[data-testid="standalone-warning-tag"]')).toContainText('Standalone Payment');
    });

    test('should not allow non-admin to create standalone payment', async ({ page }) => {
      // GIVEN: Non-admin user (accountant) is logged in
      await loginAsUser(page, 'accountant@example.com', 'password', 'accountant');
      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // THEN: Standalone payment toggle is not visible
      await expect(page.locator('[data-testid="standalone-payment-toggle"]')).not.toBeVisible();
    });
  });

  // AC#7: Payment approval workflow
  test.describe('4.3-E2E-007: Payment Approval Workflow', () => {
    test('should require approval for payments exceeding threshold', async ({ 
      page, 
      supplierFactory, 
      purchaseBillFactory
    }) => {
      // GIVEN: Payment amount exceeds approval threshold (20M VND)
      const supplier = supplierFactory.createSupplier();
      const bill = purchaseBillFactory.createPostedBill({ supplierId: supplier.id! });

      await page.route('**/api/v1/ap-payments/suppliers/*/open-bills', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: [bill], total: 1 }),
        });
      });

      await page.route('**/api/v1/ap-payments', async (route) => {
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({
              data: {
                id: 1,
                status: 'PENDING_APPROVAL',
                amount: 25000000,
              },
            }),
          });
        }
      });

      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User creates payment above threshold
      const supplierPicker = page.locator('[data-testid="supplier-picker"]');
      await supplierPicker.click();
      await page.fill('[data-testid="supplier-search"]', supplier.name);
      await page.click(`[data-testid="supplier-option-${supplier.id}"]`);

      await page.fill('[data-testid="payment-amount"]', '25000000');
      await page.fill('[data-testid="payment-proof"]', 'proof.pdf');
      await page.locator('[data-testid="submit-payment-button"]').click();

      // THEN: Payment status is PENDING_APPROVAL
      await expect(page.locator('[data-testid="payment-status"]')).toContainText('PENDING_APPROVAL');
    });
  });

  // AC#8: Account balance validation
  test.describe('4.3-E2E-008: Account Balance Validation', () => {
    test('should display account balance and calculate balance after payment', async ({ page }) => {
      // GIVEN: User selects cash/bank account with known balance
      await page.route('**/api/v1/accounts/*/balance', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            balance: 5000000,
          }),
        });
      });

      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User selects account and enters payment amount
      const accountPicker = page.locator('[data-testid="account-picker"]');
      await accountPicker.click();
      await page.click('[data-testid="account-option-1"]');

      await page.fill('[data-testid="payment-amount"]', '2000000');

      // THEN: Current balance and balance after payment are displayed
      await expect(page.locator('[data-testid="account-balance-current"]')).toContainText('5,000,000');
      await expect(page.locator('[data-testid="account-balance-after"]')).toContainText('3,000,000');
    });

    test('should display overdraft warning when payment exceeds balance', async ({ page }) => {
      // GIVEN: Account balance is 1M VND
      await page.route('**/api/v1/accounts/*/balance', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            balance: 1000000,
          }),
        });
      });

      await page.goto('/ap-payments/new', { waitUntil: 'networkidle' });

      // WHEN: User enters payment amount exceeding balance
      const accountPicker = page.locator('[data-testid="account-picker"]');
      await accountPicker.click();
      await page.click('[data-testid="account-option-1"]');

      await page.fill('[data-testid="payment-amount"]', '2000000');

      // THEN: Overdraft warning is displayed
      await expect(page.locator('[data-testid="overdraft-warning"]')).toBeVisible();
      await expect(page.locator('[data-testid="overdraft-warning"]')).toContainText('Insufficient balance');
    });
  });
});


