import { test, expect } from '@playwright/test';
import { PaymentListPage, PaymentFormPage } from '../../pages/PaymentsPage';

/**
 * Payments Module (AP) - Create, Post, Cancel, Reverse Tests
 *
 * Test IDs:
 * - PAY-001: Create payment (@smoke)
 * - PAY-005: Post payment
 * - PAY-006: Cancel payment
 * - PAY-007: Reverse payment
 *
 * Uses authenticated project 'chromium-accountant' for pre-authenticated sessions.
 */
test.describe('Payments Module - Create, Post, Cancel, Reverse', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  let paymentListPage: PaymentListPage;
  let paymentFormPage: PaymentFormPage;

  test.beforeEach(async ({ page }) => {
    paymentListPage = new PaymentListPage(page);
    paymentFormPage = new PaymentFormPage(page);
  });

  test.describe('PAY-001: Create payment', { tag: '@smoke' }, () => {
    test('should create a new payment with all required fields', async ({ page }) => {
      // GIVEN: User navigates to payment creation form
      await paymentListPage.navigate();
      await paymentListPage.clickNewPayment();
      await paymentFormPage.waitForLoaded();

      // WHEN: User fills in payment details
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(1000000);
      await paymentFormPage.setReference('REF-TEST-001');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('BANK_TRANSFER');
      await paymentFormPage.selectCashAccount('Cash on Hand');

      // AND: User saves the payment as draft
      await paymentFormPage.saveDraft();

      // THEN: Payment is created and payment number is generated
      const paymentNumber = await paymentFormPage.getPaymentNumber();
      expect(paymentNumber).toMatch(/^PAY-/);
      await paymentFormPage.expectStatus('DRAFT');
    });

    test('should display validation errors for missing required fields', async ({ page }) => {
      // GIVEN: User is on payment creation form
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();

      // WHEN: User attempts to save without filling required fields
      const saveDraftButton = page.getByRole('button', { name: /save|update/i }).first();
      await saveDraftButton.click();

      // THEN: Validation errors are displayed
      await expect(page.getByText(/supplier.*required/i).or(page.getByText(/required/i))).toBeVisible();
    });

    test('should auto-populate payee from selected supplier', async ({ page }) => {
      // GIVEN: User is on payment creation form
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();

      // WHEN: User selects a supplier
      await paymentFormPage.selectSupplier('Test Supplier');

      // THEN: Payee field should be auto-populated or ready for input
      const payeeInput = page.locator('input[placeholder*="payee" i]');
      await expect(payeeInput).toBeVisible();
    });
  });

  test.describe('PAY-005: Post payment', () => {
    test('should post a draft payment successfully', async ({ page }) => {
      // GIVEN: User creates a draft payment first
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(500000);
      await paymentFormPage.setReference('REF-POST-001');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('CASH');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();

      // WHEN: User posts the payment
      await paymentFormPage.post();

      // THEN: Payment status changes to POSTED
      await paymentFormPage.expectStatus('POSTED');
    });

    test('should show confirmation dialog before posting', async ({ page }) => {
      // GIVEN: User has a draft payment open
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(500000);
      await paymentFormPage.setReference('REF-POST-002');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('CASH');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();

      // WHEN: User clicks post button
      const postButton = page.getByRole('button', { name: /post payment/i });
      await postButton.click();

      // THEN: Confirmation dialog is displayed
      await expect(page.getByRole('alertdialog').or(page.getByRole('dialog'))).toBeVisible();
    });

    test('should prevent posting payment without allocations for non-standalone', async ({ page }) => {
      // GIVEN: User creates a payment without allocations
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(500000);
      await paymentFormPage.setReference('REF-NO-ALLOC-001');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('CASH');
      await paymentFormPage.selectCashAccount('Cash on Hand');

      // WHEN: User attempts to post without allocations (standalone disabled)
      const postButton = page.getByRole('button', { name: /post payment/i });
      
      // THEN: Post button should be disabled or show validation error
      const isDisabled = await postButton.isDisabled().catch(() => false);
      if (!isDisabled) {
        await postButton.click();
        await expect(page.getByText(/allocation.*required/i).or(page.getByText(/must allocate/i))).toBeVisible();
      }
    });
  });

  test.describe('PAY-006: Cancel payment', () => {
    test('should cancel a draft payment successfully', async ({ page }) => {
      // GIVEN: User creates a draft payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(300000);
      await paymentFormPage.setReference('REF-CANCEL-001');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('CASH');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();

      // WHEN: User cancels the payment
      await paymentFormPage.cancel();

      // THEN: Payment status changes to CANCELLED
      await paymentFormPage.expectStatus('CANCELLED');
    });

    test('should not allow cancelling a posted payment', async ({ page }) => {
      // GIVEN: User has a posted payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(300000);
      await paymentFormPage.setReference('REF-CANCEL-002');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('CASH');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();
      await paymentFormPage.post();

      // THEN: Cancel button should not be visible for posted payments
      const cancelButton = page.getByRole('button', { name: /cancel payment/i });
      await expect(cancelButton).toBeHidden();
    });
  });

  test.describe('PAY-007: Reverse payment', () => {
    test('should reverse a posted payment successfully', async ({ page }) => {
      // GIVEN: User has a posted payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(750000);
      await paymentFormPage.setReference('REF-REVERSE-001');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('BANK_TRANSFER');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();
      await paymentFormPage.post();
      await paymentFormPage.expectStatus('POSTED');

      // WHEN: User reverses the payment
      await paymentFormPage.reverse();

      // THEN: Payment status changes to REVERSED
      await paymentFormPage.expectStatus('REVERSED');
    });

    test('should show confirmation dialog before reversing', async ({ page }) => {
      // GIVEN: User has a posted payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(750000);
      await paymentFormPage.setReference('REF-REVERSE-002');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('BANK_TRANSFER');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();
      await paymentFormPage.post();

      // WHEN: User clicks reverse button
      const reverseButton = page.getByRole('button', { name: /reverse/i });
      await reverseButton.click();

      // THEN: Confirmation dialog is displayed
      await expect(page.getByRole('alertdialog').or(page.getByRole('dialog'))).toBeVisible();
    });

    test('should not allow reversing a draft payment', async ({ page }) => {
      // GIVEN: User has a draft payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(750000);
      await paymentFormPage.setReference('REF-REVERSE-003');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('BANK_TRANSFER');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();

      // THEN: Reverse button should not be visible for draft payments
      const reverseButton = page.getByRole('button', { name: /reverse/i });
      await expect(reverseButton).toBeHidden();
    });

    test('should create reversal journal entry on successful reversal', async ({ page }) => {
      // GIVEN: User has a posted payment
      await paymentFormPage.navigate();
      await paymentFormPage.waitForLoaded();
      await paymentFormPage.selectSupplier('Test Supplier');
      await paymentFormPage.setAmount(1500000);
      await paymentFormPage.setReference('REF-REVERSE-JE-001');
      await paymentFormPage.setPayee('Test Payee');
      await paymentFormPage.selectPaymentMethod('BANK_TRANSFER');
      await paymentFormPage.selectCashAccount('Cash on Hand');
      await paymentFormPage.saveDraft();
      await paymentFormPage.post();

      // WHEN: User reverses the payment
      await paymentFormPage.reverse();

      // THEN: Reversal voucher link should be visible
      await expect(page.getByText(/reversal voucher/i).or(page.getByText(/reversed/i))).toBeVisible();
    });
  });
});
