import { test, expect } from '@playwright/test';
import { PurchaseBillListPage, PurchaseBillFormPage, LineItemData } from '../../pages/PurchaseBillsPage';

test.describe('Purchase Bills - Create Bill', () => {
  let listPage: PurchaseBillListPage;
  let formPage: PurchaseBillFormPage;

  test.beforeEach(async ({ page }) => {
    listPage = new PurchaseBillListPage(page);
    formPage = new PurchaseBillFormPage(page);
  });

  test('BILL-001: Create purchase bill draft @smoke', async ({ page }) => {
    await listPage.navigate();
    await listPage.clickNewBill();

    await formPage.selectSupplier('Test Supplier');
    await formPage.setBillNumber(`BILL-${Date.now()}`);
    await formPage.setReference('PO-2025-001');
    await formPage.setDescription('Office supplies purchase');

    const lineItem: LineItemData = {
      description: 'Office supplies',
      quantity: 10,
      unitPrice: 50000,
      vatRate: 'TEN',
    };
    await formPage.addLineItem(lineItem);

    await formPage.saveDraft();

    await formPage.expectStatus('DRAFT');
    await expect(page.getByText(/saved|success/i)).toBeVisible();
  });

  test('BILL-002: Create bill - supplier required', async ({ page }) => {
    await formPage.navigate();

    await formPage.setBillNumber(`BILL-${Date.now()}`);
    await formPage.setReference('PO-2025-002');

    const lineItem: LineItemData = {
      description: 'Test item',
      quantity: 1,
      unitPrice: 100000,
      vatRate: 'TEN',
    };
    await formPage.addLineItem(lineItem);

    await formPage.saveDraft();

    await formPage.expectValidationError('supplier');
    await expect(page).toHaveURL(/\/purchase-bills\/new/);
  });

  test('BILL-003: Add line items to bill with VAT', async ({ page }) => {
    await listPage.navigate();
    await listPage.clickNewBill();

    await formPage.selectSupplier('Test Supplier');
    await formPage.setBillNumber(`BILL-VAT-${Date.now()}`);

    const lineItem1: LineItemData = {
      description: 'Item with 10% VAT',
      quantity: 2,
      unitPrice: 100000,
      vatRate: 'TEN',
    };
    await formPage.addLineItem(lineItem1);

    const lineItem2: LineItemData = {
      description: 'Item with 5% VAT',
      quantity: 5,
      unitPrice: 50000,
      vatRate: 'FIVE',
    };
    await formPage.addLineItem(lineItem2);

    const lineItem3: LineItemData = {
      description: 'Item with 0% VAT',
      quantity: 1,
      unitPrice: 200000,
      vatRate: 'ZERO',
    };
    await formPage.addLineItem(lineItem3);

    const subtotal = await formPage.getSubtotal();
    const vatAmount = await formPage.getVatAmount();
    const total = await formPage.getTotal();

    expect(subtotal).toBeTruthy();
    expect(vatAmount).toBeTruthy();
    expect(total).toBeTruthy();

    await formPage.saveDraft();
    await formPage.expectStatus('DRAFT');
  });
});
