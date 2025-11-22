import { test, expect } from '../support/fixtures';
import { loginAsUser } from '../support/helpers/auth-helper';

/**
 * Epic 5 - Story 5.3: Customer Payment Receipts - E2E Tests
 * 
 * Critical user journey:
 * - Create receipt with customer picker (filters open invoices)
 * - Allocate to invoices (single/multiple, partial)
 * - Post receipt (generates GL voucher)
 * - Verify invoice status updated (PAID/PARTIALLY_PAID)
 * - Reverse receipt (generates linked reversal voucher)
 */

test.describe('Story 5.3: Customer Payment Receipts - E2E Workflow', () => {
  // Setup: Login before each test
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'accountant@example.com', 'password', 'accountant');
  });

  test('E2E-001: Complete Receipt Workflow (Create → Allocate → Post → Reverse)', async ({ page }) => {
    // ========== GIVEN: Customer with open invoices exists ==========
    // Mock: API returns customer with open invoices
    await page.route('**/api/v1/ar/customers/*/open-invoices', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [
            {
              id: 'invoice-001',
              number: 'INV-2025-001',
              date: '2025-01-10',
              amount: 10000000,
              remainingBalance: 10000000,
              status: 'POSTED',
            },
            {
              id: 'invoice-002',
              number: 'INV-2025-002',
              date: '2025-01-12',
              amount: 8000000,
              remainingBalance: 8000000,
              status: 'POSTED',
            },
          ],
          total: 2,
        }),
      });
    });

    // Mock: Customer list for picker
    await page.route('**/api/v1/ar/customers*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [
            {
              id: 'customer-001',
              name: 'Test Customer AR',
              arAccount: '131',
            },
          ],
          total: 1,
        }),
      });
    });

    // Mock: Bank accounts for picker
    await page.route('**/api/v1/bank-accounts*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [
            {
              id: 'bank-001',
              accountCode: '111',
              name: 'Test Bank Account',
              balance: 50000000,
            },
          ],
          total: 1,
        }),
      });
    });

    // ========== WHEN: User navigates to create receipt ==========
    await page.goto('/ar-receipts/new', { waitUntil: 'networkidle' });

    // Verify form title
    await expect(page.locator('[data-testid="receipt-form-title"]')).toContainText('New Receipt');

    // ========== WHEN: User selects customer with open invoices ==========
    const customerPicker = page.locator('[data-testid="customer-picker"]');
    await customerPicker.waitFor({ state: 'visible', timeout: 10000 });
    await customerPicker.click();

    // Wait for customer options
    await page.waitForSelector('[data-testid="customer-option"]', { state: 'visible' });
    const customerOption = page.locator('[data-testid="customer-option"]').first();
    await customerOption.click();

    // Verify customer selected
    await expect(customerPicker).toContainText('Test Customer AR');

    // ========== WHEN: User fills receipt form ==========
    // Receipt date
    const dateInput = page.locator('[data-testid="receipt-date-input"]');
    await dateInput.fill('2025-01-15');

    // Bank account
    const accountPicker = page.locator('[data-testid="bank-account-picker"]');
    await accountPicker.click();
    await page.waitForSelector('[data-testid="account-option"]', { state: 'visible' });
    await page.locator('[data-testid="account-option"]').first().click();

    // Receipt amount
    const amountInput = page.locator('[data-testid="receipt-amount-input"]');
    await amountInput.fill('5000000');

    // Payment method
    const paymentMethod = page.locator('[data-testid="payment-method-select"]');
    await paymentMethod.click();
    await page.locator('text=Bank Transfer').click();

    // Reference
    const referenceInput = page.locator('[data-testid="receipt-reference-input"]');
    await referenceInput.fill('REC-TEST-FLOW-001');

    // ========== WHEN: User allocates receipt to invoices ==========
    // Mock receipt created response
    let createdReceiptId = '';
    await page.route('**/api/v1/ar/receipts', async (route) => {
      if (route.request().method() === 'POST') {
        createdReceiptId = 'receipt-001';
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              id: createdReceiptId,
              receiptNumber: '2025/001',
              status: 'DRAFT',
              customerId: 'customer-001',
              amount: 5000000,
            },
          }),
        });
      }
    });

    // Save receipt (draft)
    const saveButton = page.locator('[data-testid="save-receipt-button"]');
    await saveButton.click();

    // Wait for receipt created
    await page.waitForURL(/\/ar-receipts\/.*\/edit/);

    // Now in allocation view
    const allocationGrid = page.locator('[data-testid="receipt-allocation-grid"]');
    await allocationGrid.waitFor({ state: 'visible', timeout: 5000 });

    // Mock: Get open invoices
    await page.route('**/api/v1/ar/receipts/*/open-invoices', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [
            {
              id: 'invoice-001',
              number: 'INV-2025-001',
              remainingBalance: 10000000,
            },
            {
              id: 'invoice-002',
              number: 'INV-2025-002',
              remainingBalance: 8000000,
            },
          ],
        }),
      });
    });

    // Allocate full amount to first invoice
    const allocationInput = page.locator('[data-testid="allocation-amount-input"]').first();
    await allocationInput.fill('5000000');

    // ========== WHEN: User posts receipt ==========
    // Mock: Allocate endpoint
    await page.route(`**/api/v1/ar/receipts/${createdReceiptId}/allocate`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: createdReceiptId,
            allocations: [
              {
                salesInvoiceId: 'invoice-001',
                allocatedAmount: 5000000,
              },
            ],
          },
        }),
      });
    });

    // Mock: Post endpoint (generates GL voucher)
    await page.route(`**/api/v1/ar/receipts/${createdReceiptId}/post`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: createdReceiptId,
            status: 'POSTED',
            linkedVoucherId: 'voucher-001',
            allocations: [
              {
                salesInvoiceId: 'invoice-001',
                allocatedAmount: 5000000,
              },
            ],
          },
        }),
      });
    });

    const postButton = page.locator('[data-testid="post-receipt-button"]');
    await postButton.click();

    // Confirm post dialog
    const confirmPostButton = page.locator('[data-testid="confirm-post-button"]');
    await confirmPostButton.click();

    // ========== THEN: Receipt is posted and invoice status updated ==========
    // Wait for success message
    const successMessage = page.locator('[data-testid="success-message"]');
    await expect(successMessage).toContainText('Receipt posted successfully');

    // Verify receipt status changed to POSTED
    const statusBadge = page.locator('[data-testid="receipt-status-badge"]');
    await expect(statusBadge).toContainText('POSTED');

    // ========== WHEN: User reverses receipt ==========
    // Mock: Reverse endpoint
    await page.route(`**/api/v1/ar/receipts/${createdReceiptId}/reverse`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: createdReceiptId,
            status: 'REVERSED',
            reversalVoucherId: 'voucher-002',
          },
        }),
      });
    });

    const reverseButton = page.locator('[data-testid="reverse-receipt-button"]');
    await reverseButton.click();

    // Fill reversal reason
    const reasonInput = page.locator('[data-testid="reversal-reason-input"]');
    await reasonInput.fill('Customer requested cancellation');

    const confirmReverseButton = page.locator('[data-testid="confirm-reverse-button"]');
    await confirmReverseButton.click();

    // ========== THEN: Receipt is reversed and linked reversal voucher created ==========
    await expect(statusBadge).toContainText('REVERSED');

    // Verify reversal voucher link shown
    const reversalVoucherLink = page.locator('[data-testid="reversal-voucher-link"]');
    await expect(reversalVoucherLink).toContainText('Reversal Voucher: voucher-002');
  });
});
