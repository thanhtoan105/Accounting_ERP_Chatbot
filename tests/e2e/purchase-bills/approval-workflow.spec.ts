import { test, expect } from '@playwright/test';
import { PurchaseBillListPage, PurchaseBillFormPage, LineItemData } from '../../pages/PurchaseBillsPage';

test.describe('Purchase Bills - Approval Workflow', () => {
  test.describe('Accountant actions', () => {

    let listPage: PurchaseBillListPage;
    let formPage: PurchaseBillFormPage;

    test.beforeEach(async ({ page }) => {
      listPage = new PurchaseBillListPage(page);
      formPage = new PurchaseBillFormPage(page);
    });

    test('BILL-004: Submit bill for approval', async ({ page }) => {
      await listPage.navigate();
      await listPage.clickNewBill();

      await formPage.selectSupplier('Test Supplier');
      await formPage.setBillNumber(`BILL-SUBMIT-${Date.now()}`);
      await formPage.setReference('PO-2025-004');

      const lineItem: LineItemData = {
        description: 'Equipment purchase',
        quantity: 1,
        unitPrice: 500000,
        vatRate: 'TEN',
      };
      await formPage.addLineItem(lineItem);

      await formPage.saveDraft();
      await formPage.expectStatus('DRAFT');

      await formPage.submit();

      await formPage.expectStatus('PENDING_APPROVAL');
      await expect(page.getByText(/submitted|pending/i)).toBeVisible();
    });

    test('BILL-007: Edit draft bill', async ({ page }) => {
      await listPage.navigate();
      await listPage.clickNewBill();

      const billNumber = `BILL-EDIT-${Date.now()}`;
      await formPage.selectSupplier('Test Supplier');
      await formPage.setBillNumber(billNumber);
      await formPage.setReference('PO-2025-007');

      const lineItem: LineItemData = {
        description: 'Original item',
        quantity: 1,
        unitPrice: 100000,
        vatRate: 'TEN',
      };
      await formPage.addLineItem(lineItem);

      await formPage.saveDraft();
      await formPage.expectStatus('DRAFT');

      await formPage.setReference('PO-2025-007-UPDATED');
      await formPage.setDescription('Updated description');

      await formPage.saveDraft();

      await expect(page.getByText(/updated|saved/i)).toBeVisible();
      await formPage.expectStatus('DRAFT');
    });

    test('BILL-008: Delete draft bill', async ({ page }) => {
      await listPage.navigate();
      await listPage.clickNewBill();

      const billNumber = `BILL-DELETE-${Date.now()}`;
      await formPage.selectSupplier('Test Supplier');
      await formPage.setBillNumber(billNumber);
      await formPage.setReference('PO-2025-008');

      const lineItem: LineItemData = {
        description: 'Item to delete',
        quantity: 1,
        unitPrice: 100000,
        vatRate: 'TEN',
      };
      await formPage.addLineItem(lineItem);

      await formPage.saveDraft();
      await formPage.expectStatus('DRAFT');

      const deleteButton = page.getByRole('button', { name: /delete|remove/i });
      await deleteButton.click();

      const confirmButton = page.getByRole('button', { name: /confirm|yes/i });
      if (await confirmButton.isVisible()) {
        await confirmButton.click();
      }

      await expect(page).toHaveURL(/\/purchase-bills$/);
      await listPage.expectBillNotInList(billNumber);
    });
  });

  test.describe('Chief Accountant approval actions', () => {

    let listPage: PurchaseBillListPage;
    let formPage: PurchaseBillFormPage;

    test.beforeEach(async ({ page }) => {
      listPage = new PurchaseBillListPage(page);
      formPage = new PurchaseBillFormPage(page);
    });

    test('BILL-005: Approve purchase bill @smoke', async ({ page }) => {
      await listPage.navigate();
      await listPage.filterByStatus('pending');

      const pendingBillRow = page.locator('table tbody tr').first();
      const hasPendingBill = (await pendingBillRow.count()) > 0;

      if (hasPendingBill) {
        await pendingBillRow.click();
        await formPage.waitForLoaded();

        await formPage.approve();

        await formPage.expectStatus('POSTED');
        await expect(page.getByText(/approved|posted/i)).toBeVisible();
      } else {
        test.skip();
      }
    });

    test('BILL-006: Reject purchase bill with reason', async ({ page }) => {
      await listPage.navigate();
      await listPage.filterByStatus('pending');

      const pendingBillRow = page.locator('table tbody tr').first();
      const hasPendingBill = (await pendingBillRow.count()) > 0;

      if (hasPendingBill) {
        await pendingBillRow.click();
        await formPage.waitForLoaded();

        const rejectionReason = 'Missing supporting documentation. Please attach invoice scan.';
        await formPage.reject(rejectionReason);

        await formPage.expectStatus('REJECTED');
        await expect(page.getByText(/rejected/i)).toBeVisible();
      } else {
        test.skip();
      }
    });
  });
});
