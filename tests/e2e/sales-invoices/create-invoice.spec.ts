import { test, expect } from '../../support/fixtures';
import { SalesInvoiceListPage, SalesInvoiceFormPage } from '../../pages/SalesInvoicesPage';

/**
 * Sales Invoice Creation E2E Tests
 *
 * Tests for creating sales invoices:
 * - INV-001: Create sales invoice draft (@smoke)
 * - INV-002: Create invoice - customer required
 * - INV-003: Add line items to invoice with VAT calculation
 *
 * Uses authenticated project 'chromium-accountant' for basic tests.
 */
test.describe('Sales Invoice Creation', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('INV-001: Create sales invoice draft @smoke', async ({
    page,
    customerFactory,
    salesInvoiceFactory,
  }) => {
    const customer = customerFactory.createCustomer({ customerName: 'Test Customer VN' });
    const invoice = salesInvoiceFactory.createDraftInvoice({
      customerId: customer.id!,
      invoiceNumber: 'SI-DRAFT-001',
    });

    await page.route('**/api/v1/customers*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: [customer], pagination: { total: 1 } }),
      });
    });

    await page.route('**/api/v1/sales-invoices', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ data: invoice }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: [], pagination: { total: 0 } }),
        });
      }
    });

    const listPage = new SalesInvoiceListPage(page);
    await listPage.navigate();

    await listPage.clickNewInvoice();

    const formPage = new SalesInvoiceFormPage(page);
    await formPage.waitForLoaded();

    await formPage.selectCustomer('Test Customer VN');
    await formPage.setReference('REF-001');
    await formPage.setDescription('Test invoice draft');

    await formPage.addLineItem({
      description: 'Consulting services',
      quantity: 10,
      unitPrice: 1000000,
      vatRate: 'TEN',
    });

    await formPage.saveDraft();

    await expect(page.locator('[role="status"], [data-sonner-toast]')).toContainText(/saved|success/i);
  });

  test('INV-002: Create invoice - customer required', async ({ page }) => {
    await page.route('**/api/v1/sales-invoices', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: [], pagination: { total: 0 } }),
        });
      }
    });

    const listPage = new SalesInvoiceListPage(page);
    await listPage.navigate();

    await listPage.clickNewInvoice();

    const formPage = new SalesInvoiceFormPage(page);
    await formPage.waitForLoaded();

    await formPage.setReference('REF-NO-CUSTOMER');
    await formPage.setDescription('Invoice without customer');

    await formPage.addLineItem({
      description: 'Test item',
      quantity: 1,
      unitPrice: 100000,
      vatRate: 'TEN',
    });

    await formPage.saveDraft();

    await formPage.expectValidationError('customer');
  });

  test('INV-003: Add line items to invoice with VAT calculation', async ({
    page,
    customerFactory,
    salesInvoiceFactory,
  }) => {
    const customer = customerFactory.createCustomer({ customerName: 'VAT Test Customer' });

    const line1 = salesInvoiceFactory.createInvoiceLine({
      lineNumber: 1,
      description: 'Product A',
      quantity: 2,
      unitPrice: 1000000,
      vatRate: 10,
    });

    const line2 = salesInvoiceFactory.createInvoiceLine({
      lineNumber: 2,
      description: 'Product B',
      quantity: 3,
      unitPrice: 500000,
      vatRate: 5,
    });

    const invoice = salesInvoiceFactory.createDraftInvoice({
      customerId: customer.id!,
      invoiceNumber: 'SI-VAT-001',
      lines: [line1, line2],
    });

    await page.route('**/api/v1/customers*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: [customer], pagination: { total: 1 } }),
      });
    });

    await page.route('**/api/v1/sales-invoices', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ data: invoice }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: [], pagination: { total: 0 } }),
        });
      }
    });

    const listPage = new SalesInvoiceListPage(page);
    await listPage.navigate();

    await listPage.clickNewInvoice();

    const formPage = new SalesInvoiceFormPage(page);
    await formPage.waitForLoaded();

    await formPage.selectCustomer('VAT Test Customer');

    await formPage.addLineItem({
      description: 'Product A',
      quantity: 2,
      unitPrice: 1000000,
      vatRate: 'TEN',
    });

    await formPage.addLineItem({
      description: 'Product B',
      quantity: 3,
      unitPrice: 500000,
      vatRate: 'FIVE',
    });

    const subtotal = await formPage.getSubtotal();
    const vatAmount = await formPage.getVatAmount();
    const total = await formPage.getTotal();

    expect(subtotal).toContain('3,500,000');
    expect(vatAmount).toContain('275,000');
    expect(total).toContain('3,775,000');

    await formPage.saveDraft();

    await expect(page.locator('[role="status"], [data-sonner-toast]')).toContainText(/saved|success/i);
  });
});
