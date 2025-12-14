import { test, expect } from '../../support/fixtures';
import { SalesInvoiceFormPage } from '../../pages/SalesInvoicesPage';

/**
 * Sales Invoice Credit Note E2E Tests
 *
 * Tests for credit note creation:
 * - INV-009: Create credit note against invoice
 *
 * Uses authenticated project 'chromium-accountant' for basic tests.
 */
test.describe('Sales Invoice Credit Notes', () => {
  test.use({ storageState: 'tests/.auth/accountant.json' });

  test('INV-009: Create credit note against invoice', async ({
    page,
    customerFactory,
    salesInvoiceFactory,
  }) => {
    const customer = customerFactory.createCustomer({ customerName: 'Credit Note Customer' });
    const postedInvoice = salesInvoiceFactory.createPostedInvoice({
      id: 200,
      customerId: customer.id!,
      invoiceNumber: 'SI-POSTED-001',
      totalAmount: 5000000,
      vatAmount: 500000,
    });

    const creditNote = {
      id: 201,
      originalInvoiceId: 200,
      invoiceNumber: 'CN-001',
      customerId: customer.id,
      invoiceDate: new Date().toISOString().split('T')[0],
      dueDate: new Date().toISOString().split('T')[0],
      reference: 'Credit for SI-POSTED-001',
      description: 'Credit note for returned goods',
      status: 'DRAFT',
      totalAmount: -2000000,
      vatAmount: -200000,
      lines: [
        {
          lineNumber: 1,
          accountId: 5111,
          description: 'Returned goods',
          quantity: -1,
          unitPrice: 2000000,
          amount: -2000000,
          vatRate: 10,
          vatAmount: -200000,
        },
      ],
    };

    await page.route('**/api/v1/sales-invoices/200', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: postedInvoice }),
      });
    });

    await page.route('**/api/v1/sales-invoices/200/credit-note', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: creditNote }),
        });
      } else if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ data: creditNote }),
        });
      }
    });

    const formPage = new SalesInvoiceFormPage(page);
    await formPage.navigateToInvoice('200');

    await formPage.expectStatus('POSTED');

    await formPage.createCreditNote();

    await expect(page).toHaveURL(/\/sales-invoices\/\d+\/credit-note/);

    await expect(page.getByText(/credit note|credit/i)).toBeVisible();

    await expect(page.getByText(/SI-POSTED-001/)).toBeVisible();

    const descriptionInput = page.locator('textarea[name="description"]').or(
      page.locator('label:has-text("Description")').locator('..').locator('textarea'),
    );
    if (await descriptionInput.isVisible()) {
      await descriptionInput.fill('Credit note for returned goods');
    }

    const saveButton = page.getByRole('button', { name: /save|create/i });
    await saveButton.click();

    await expect(page.locator('[role="status"], [data-sonner-toast]')).toContainText(
      /created|saved|success/i,
    );
  });
});
