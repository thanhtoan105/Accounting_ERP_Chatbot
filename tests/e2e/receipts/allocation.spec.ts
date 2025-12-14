import { test, expect } from '@playwright/test';
import { ReceiptListPage, ReceiptFormPage } from '../../pages/ReceiptsPage';

/**
 * Receipts Module (AR) - Allocation Tests
 *
 * Test IDs: REC-002, REC-003
 * Priority: P0 (Smoke) + P1 (Core workflows)
 */

test.describe('Receipts Module - Invoice Allocation', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  let listPage: ReceiptListPage;
  let formPage: ReceiptFormPage;

  test.beforeEach(async ({ page }) => {
    listPage = new ReceiptListPage(page);
    formPage = new ReceiptFormPage(page);
    await listPage.navigate();
  });

  test('REC-002: Allocate receipt to invoice @smoke', async ({ page }) => {
    await listPage.clickNewReceipt();
    await formPage.waitForLoaded();

    await formPage.selectCustomer('Test Customer');
    await formPage.setAmount(10000000);
    await formPage.setReference(`REF-ALLOC-${Date.now()}`);
    await formPage.selectPaymentMethod('BANK_TRANSFER');

    await formPage.toggleStandalone(false);

    const allocationGrid = page.locator('table').filter({ hasText: /Invoice|Allocated/i });
    await expect(allocationGrid).toBeVisible();

    const firstInvoiceRow = allocationGrid.locator('tbody tr').first();
    await expect(firstInvoiceRow).toBeVisible();

    const invoiceNumber = await firstInvoiceRow.locator('td').first().textContent();
    const allocatedInput = firstInvoiceRow.locator('input[type="text"], input[type="number"]');
    await allocatedInput.fill('10000000');

    await formPage.saveDraft();

    const allocations = await formPage.getAllocatedInvoices();
    expect(allocations.length).toBeGreaterThan(0);
    expect(allocations[0].invoiceNumber).toBe(invoiceNumber?.trim());
    expect(allocations[0].allocatedAmount).toBe(10000000);

    const unallocated = await formPage.getUnallocatedAmount();
    expect(unallocated).toBe(0);
  });

  test('REC-003: Partial receipt allocation', async ({ page }) => {
    await listPage.clickNewReceipt();
    await formPage.waitForLoaded();

    await formPage.selectCustomer('Test Customer');
    const receiptAmount = 5000000;
    await formPage.setAmount(receiptAmount);
    await formPage.setReference(`REF-PARTIAL-${Date.now()}`);
    await formPage.selectPaymentMethod('CASH');

    await formPage.toggleStandalone(false);

    const allocationGrid = page.locator('table').filter({ hasText: /Invoice|Allocated/i });
    await expect(allocationGrid).toBeVisible();

    const invoiceRows = allocationGrid.locator('tbody tr');
    const rowCount = await invoiceRows.count();
    expect(rowCount).toBeGreaterThan(0);

    const firstRow = invoiceRows.nth(0);
    const firstInvoiceAmount = 3000000;
    await firstRow.locator('input[type="text"], input[type="number"]').fill(firstInvoiceAmount.toString());

    if (rowCount > 1) {
      const secondRow = invoiceRows.nth(1);
      const secondInvoiceAmount = 2000000;
      await secondRow.locator('input[type="text"], input[type="number"]').fill(secondInvoiceAmount.toString());
    }

    await formPage.saveDraft();

    const allocations = await formPage.getAllocatedInvoices();
    const totalAllocated = allocations.reduce((sum, a) => sum + a.allocatedAmount, 0);
    expect(totalAllocated).toBeLessThanOrEqual(receiptAmount);

    const unallocated = await formPage.getUnallocatedAmount();
    expect(unallocated).toBe(receiptAmount - totalAllocated);

    await formPage.expectStatus('DRAFT');
  });
});
