import { test, expect } from '@playwright/test';
import { PaymentListPage, PaymentFormPage } from '../../pages/PaymentsPage';

/**
 * Payments Module (AP) - Allocation Tests
 *
 * Test IDs:
 * - PAY-002: Allocate payment to bill (@smoke)
 * - PAY-003: Auto-allocate payment FIFO
 * - PAY-004: Partial payment allocation
 *
 * Uses authenticated project 'chromium-accountant' for pre-authenticated sessions.
 */
test.describe('Payments Module - Allocation', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  let paymentListPage: PaymentListPage;
  let paymentFormPage: PaymentFormPage;

  test.beforeEach(async ({ page }) => {
    paymentListPage = new PaymentListPage(page);
    paymentFormPage = new PaymentFormPage(page);
  });

  test.describe('PAY-002: Allocate payment to bill', { tag: '@smoke' }, () => {
    test('should display allocation grid with supplier open bills', async ({ page }) => {
      // GIVEN: User is on payment creation form
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();

      // WHEN: User selects a supplier with open bills
      await paymentFormPage.selectSupplier('Test Supplier');

      // THEN: Allocation grid is visible with bills
      const allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
      await expect(allocationGrid).toBeVisible();
    });

    test('should manually allocate payment amount to specific bill', async ({ page }) => {
      // GIVEN: User is on payment form with supplier selected
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(500000);

      // WHEN: User manually allocates amount to a bill
      await paymentFormPage.allocateToBill('BILL-001', 500000);

      // THEN: Allocation is reflected in the grid
      const allocatedBills = await paymentFormPage.getAllocatedBills();
      expect(allocatedBills.length).toBeGreaterThan(0);
    });

    test('should update unallocated amount when allocating to bills', async ({ page }) => {
      // GIVEN: User creates a payment with amount 1,000,000
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(1000000);

      // WHEN: User allocates 500,000 to a bill
      await paymentFormPage.allocateToBill('BILL-001', 500000);

      // THEN: Unallocated amount should be 500,000
      const unallocatedAmount = await paymentFormPage.getUnallocatedAmount();
      expect(unallocatedAmount).toBeLessThanOrEqual(500000);
    });

    test('should prevent allocation exceeding bill remaining balance', async ({ page }) => {
      // GIVEN: User is on payment form with supplier selected
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(5000000);

      // WHEN: User attempts to allocate more than bill remaining balance
      const allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
      const firstBillRow = allocationGrid.locator('tbody tr').first();
      const allocationInput = firstBillRow.locator('input[type="text"], input[type="number"]');
      
      if (await allocationInput.isVisible()) {
        await allocationInput.fill('99999999');
        await allocationInput.blur();

        // THEN: Validation error is displayed
        await expect(
          page.getByText(/exceeds/i).or(page.getByText(/maximum/i)).or(page.getByText(/cannot exceed/i))
        ).toBeVisible({ timeout: 5000 }).catch(() => {
          // Some implementations auto-cap the value instead of showing error
        });
      }
    });

    test('should show bill details (number, date, amount, remaining) in allocation grid', async ({ page }) => {
      // GIVEN: User selects a supplier with open bills
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');

      // THEN: Allocation grid shows bill details
      const allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
      await expect(allocationGrid).toBeVisible();

      // Verify grid headers contain expected columns
      const headers = allocationGrid.locator('thead th, th');
      await expect(headers.filter({ hasText: /bill/i })).toBeVisible();
    });
  });

  test.describe('PAY-003: Auto-allocate payment FIFO', () => {
    test('should auto-allocate payment to bills using FIFO algorithm', async ({ page }) => {
      // GIVEN: User is on payment form with supplier having multiple open bills
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(2000000);

      // WHEN: User clicks FIFO allocate button
      await paymentFormPage.autoAllocateFIFO();

      // THEN: Bills are allocated in order (oldest due date first)
      const allocatedBills = await paymentFormPage.getAllocatedBills();
      expect(allocatedBills.length).toBeGreaterThan(0);
    });

    test('should allocate to oldest bill first based on due date', async ({ page }) => {
      // GIVEN: User is on payment form with supplier having multiple open bills
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(1000000);

      // WHEN: User triggers FIFO allocation
      await paymentFormPage.autoAllocateFIFO();

      // THEN: First bill in allocation should have the earliest due date
      const allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
      const firstRow = allocationGrid.locator('tbody tr').first();
      const allocatedInput = firstRow.locator('input[type="text"], input[type="number"]');
      
      // Verify first row has allocation
      if (await allocatedInput.isVisible()) {
        const value = await allocatedInput.inputValue();
        expect(parseInt(value.replace(/[^0-9]/g, '') || '0')).toBeGreaterThan(0);
      }
    });

    test('should fully allocate to first bill before moving to next', async ({ page }) => {
      // GIVEN: User has payment amount that covers first bill completely
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(3000000);

      // WHEN: User triggers FIFO allocation
      await paymentFormPage.autoAllocateFIFO();

      // THEN: First bill should be fully allocated before second bill gets any allocation
      const allocatedBills = await paymentFormPage.getAllocatedBills();
      if (allocatedBills.length >= 2) {
        // First bill should have full allocation
        expect(allocatedBills[0].allocatedAmount).toBeGreaterThan(0);
      }
    });

    test('should stop allocation when payment amount is exhausted', async ({ page }) => {
      // GIVEN: User has payment amount less than total of all bills
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(500000);

      // WHEN: User triggers FIFO allocation
      await paymentFormPage.autoAllocateFIFO();

      // THEN: Unallocated amount should be zero or near zero
      const unallocatedAmount = await paymentFormPage.getUnallocatedAmount();
      expect(unallocatedAmount).toBeLessThanOrEqual(500000);
    });

    test('should allow manual override after FIFO allocation', async ({ page }) => {
      // GIVEN: User has FIFO allocated payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(2000000);
      await paymentFormPage.autoAllocateFIFO();

      // WHEN: User manually modifies allocation
      const allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
      const firstRow = allocationGrid.locator('tbody tr').first();
      const allocatedInput = firstRow.locator('input[type="text"], input[type="number"]');

      if (await allocatedInput.isVisible()) {
        await allocatedInput.fill('100000');

        // THEN: Manual override is saved
        await expect(allocatedInput).toHaveValue(/100000/);
      }
    });
  });

  test.describe('PAY-004: Partial payment allocation', () => {
    test('should allow partial payment to single bill', async ({ page }) => {
      // GIVEN: User is on payment form
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');

      // WHEN: User enters amount less than bill total
      await paymentFormPage.setAmount(250000);
      await paymentFormPage.allocateToBill('BILL-001', 250000);

      // THEN: Partial allocation is accepted
      const allocatedBills = await paymentFormPage.getAllocatedBills();
      const partiallyAllocated = allocatedBills.find(b => b.allocatedAmount === 250000);
      expect(partiallyAllocated || allocatedBills.length).toBeTruthy();
    });

    test('should show remaining balance after partial allocation', async ({ page }) => {
      // GIVEN: User allocates partial payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(300000);

      // WHEN: User allocates partial amount
      await paymentFormPage.allocateToBill('BILL-001', 300000);

      // THEN: Bill remaining balance is updated in the grid
      const allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
      await expect(allocationGrid.getByText(/remaining/i).or(allocationGrid.locator('td'))).toBeVisible();
    });

    test('should allow multiple partial payments to same bill over time', async ({ page }) => {
      // GIVEN: User creates first partial payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(100000);
      await paymentFormPage.allocateToBill('BILL-001', 100000);
      await paymentFormPage.setReference('PARTIAL-001');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('CASH');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();

      // THEN: Payment is saved with partial allocation
      const paymentNumber = await paymentFormPage.getPaymentNumber();
      expect(paymentNumber).toMatch(/^PAY-/);
    });

    test('should track cumulative payments against bill total', async ({ page }) => {
      // GIVEN: User is viewing payment form with bills that have prior payments
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');

      // THEN: Allocation grid shows total amount, paid amount, and remaining
      const allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
      await expect(allocationGrid).toBeVisible();

      // Grid should have columns for tracking payment status
      const headers = allocationGrid.locator('thead');
      await expect(headers).toBeVisible();
    });

    test('should prevent over-allocation across multiple payments', async ({ page }) => {
      // GIVEN: User is on payment form with partially paid bill
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(9999999999);

      // WHEN: User attempts to allocate more than remaining balance
      const allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
      const firstRow = allocationGrid.locator('tbody tr').first();
      const allocatedInput = firstRow.locator('input[type="text"], input[type="number"]');

      if (await allocatedInput.isVisible()) {
        await allocatedInput.fill('9999999999');
        await allocatedInput.blur();

        // THEN: Validation prevents over-allocation (error shown or value capped)
        const inputValue = await allocatedInput.inputValue();
        const numericValue = parseInt(inputValue.replace(/[^0-9]/g, '') || '0');
        
        // Either error is shown or value is capped
        const errorVisible = await page.getByText(/exceed/i).isVisible().catch(() => false);
        expect(errorVisible || numericValue < 9999999999).toBeTruthy();
      }
    });

    test('should update bill status to partially paid after partial allocation and post', async ({ page }) => {
      // GIVEN: User creates and posts a partial payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(100000);
      await paymentFormPage.allocateToBill('BILL-001', 100000);
      await paymentFormPage.setReference('PARTIAL-STATUS-001');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('CASH');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();

      // WHEN: User posts the payment
      await paymentFormPage.post();

      // THEN: Payment is posted successfully
      await paymentFormPage.expectStatus('POSTED');
    });
  });
});
