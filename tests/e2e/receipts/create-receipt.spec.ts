import { test, expect } from '@playwright/test';
import { ReceiptListPage, ReceiptFormPage } from '../../pages/ReceiptsPage';

/**
 * Receipts Module (AR) - Create/Post/Reverse Tests
 *
 * Test IDs: REC-001, REC-004, REC-005
 * Priority: P0 (Smoke) + P1 (Core workflows)
 */

test.describe('Receipts Module - Create/Post/Reverse', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  let listPage: ReceiptListPage;
  let formPage: ReceiptFormPage;

  test.beforeEach(async ({ page }) => {
    listPage = new ReceiptListPage(page);
    formPage = new ReceiptFormPage(page);
    await listPage.navigate();
  });

  test('REC-001: Create receipt @smoke', async ({ page }) => {
    await listPage.clickNewReceipt();

    await formPage.waitForLoaded();
    await formPage.selectCustomer('Test Customer');
    await formPage.setAmount(5000000);
    await formPage.setReference(`REF-${Date.now()}`);
    await formPage.selectPaymentMethod('BANK_TRANSFER');

    await formPage.saveDraft();

    const receiptNumber = await formPage.getReceiptNumber();
    expect(receiptNumber).toBeTruthy();
    expect(receiptNumber).toMatch(/REC-/);

    await formPage.expectStatus('DRAFT');
  });

  test('REC-004: Post receipt', async ({ page }) => {
    await listPage.filterByStatus('DRAFT');
    await page.waitForLoadState('networkidle');

    const firstDraftRow = page.locator('table tbody tr').first();
    await expect(firstDraftRow).toBeVisible();

    const receiptNumber = await firstDraftRow.locator('td').first().textContent();
    await firstDraftRow.click();
    await page.waitForURL(/\/accounting\/receipts\/\d+/);

    await formPage.waitForLoaded();
    await formPage.expectStatus('DRAFT');

    await formPage.post();

    await formPage.expectStatus('POSTED');

    await listPage.navigate();
    await listPage.filterByStatus('POSTED');
    await listPage.expectReceiptInList(receiptNumber!.trim());
  });

  test('REC-005: Reverse receipt', async ({ page }) => {
    await listPage.filterByStatus('POSTED');
    await page.waitForLoadState('networkidle');

    const firstPostedRow = page.locator('table tbody tr').first();
    await expect(firstPostedRow).toBeVisible();

    const receiptNumber = await firstPostedRow.locator('td').first().textContent();
    await firstPostedRow.click();
    await page.waitForURL(/\/accounting\/receipts\/\d+/);

    await formPage.waitForLoaded();
    await formPage.expectStatus('POSTED');

    const reverseButton = page.getByRole('button', { name: /Reverse/i });
    await reverseButton.click();

    const confirmDialog = page.locator('[role="alertdialog"]');
    if (await confirmDialog.isVisible()) {
      await confirmDialog.getByRole('button', { name: /confirm|yes|đồng ý/i }).click();
    }

    await formPage.expectSuccessToast();
    await formPage.expectStatus('REVERSED');

    await listPage.navigate();
    await listPage.filterByStatus('REVERSED');
    await listPage.expectReceiptInList(receiptNumber!.trim());
  });
});
