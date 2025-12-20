import { test, expect } from '../../support/fixtures';
import { SalesInvoiceListPage, SalesInvoiceFormPage } from '../../pages/SalesInvoicesPage';

/**
 * Sales Invoice Approval Workflow E2E Tests
 *
 * Tests for approval workflow:
 * - INV-004: Submit invoice for approval (@smoke)
 * - INV-005: Approve sales invoice (@smoke)
 * - INV-006: Reject sales invoice with reason
 * - INV-007: Edit draft invoice
 * - INV-008: Delete draft invoice
 *
 * Uses 'chromium-chief_accountant' for approval tests.
 * Uses 'chromium-accountant' for basic tests.
 */
test.describe('Sales Invoice Approval Workflow', () => {
  test.describe('Submit and Edit (Accountant)', () => {
    test.use({ storageState: 'tests/.auth/accountant.json' });

    test('INV-004: Submit invoice for approval @smoke', async ({
      page,
      customerFactory,
      salesInvoiceFactory,
    }) => {
      const customer = customerFactory.createCustomer({ customerName: 'Submit Test Customer' });
      const draftInvoice = salesInvoiceFactory.createDraftInvoice({
        id: 100,
        customerId: customer.id!,
        invoiceNumber: 'SI-SUBMIT-001',
        createdById: 1,
      });

      const pendingInvoice = {
        ...draftInvoice,
        status: 'PENDING_APPROVAL',
      };

      await page.route('**/api/v1/sales-invoices/100', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: draftInvoice }),
        });
      });

      await page.route('**/api/v1/sales-invoices/100/submit-for-approval', async (route) => {
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ data: pendingInvoice }),
          });
        }
      });

      const formPage = new SalesInvoiceFormPage(page);
      await formPage.navigateToInvoice('100');

      await formPage.expectStatus('DRAFT');

      await formPage.submit();

      await expect(
        page.locator('[data-testid="invoice-status"], [role="status"]').filter({ hasText: /pending|submitted|success/i }),
      ).toBeVisible({ timeout: 5000 });
    });

    test('INV-007: Edit draft invoice', async ({ page, customerFactory, salesInvoiceFactory }) => {
      const customer = customerFactory.createCustomer({ customerName: 'Edit Test Customer' });
      const draftInvoice = salesInvoiceFactory.createDraftInvoice({
        id: 101,
        customerId: customer.id!,
        invoiceNumber: 'SI-EDIT-001',
        description: 'Original description',
      });

      const updatedInvoice = {
        ...draftInvoice,
        description: 'Updated description',
        reference: 'REF-UPDATED',
      };

      await page.route('**/api/v1/sales-invoices/101', async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ data: draftInvoice }),
          });
        } else if (route.request().method() === 'PUT') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ data: updatedInvoice }),
          });
        }
      });

      const formPage = new SalesInvoiceFormPage(page);
      await formPage.navigateToInvoice('101');

      await formPage.expectStatus('DRAFT');

      await formPage.setDescription('Updated description');
      await formPage.setReference('REF-UPDATED');

      await formPage.saveDraft();

      await expect(page.locator('[role="status"], [data-sonner-toast]')).toContainText(/saved|updated|success/i);
    });

    test('INV-008: Delete draft invoice', async ({ page, customerFactory, salesInvoiceFactory }) => {
      const customer = customerFactory.createCustomer({ customerName: 'Delete Test Customer' });
      const draftInvoice = salesInvoiceFactory.createDraftInvoice({
        id: 102,
        customerId: customer.id!,
        invoiceNumber: 'SI-DELETE-001',
      });

      await page.route('**/api/v1/sales-invoices/102', async (route) => {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ data: draftInvoice }),
          });
        } else if (route.request().method() === 'DELETE') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ message: 'Invoice deleted successfully' }),
          });
        }
      });

      await page.route('**/api/v1/sales-invoices', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: [], pagination: { total: 0 } }),
        });
      });

      const formPage = new SalesInvoiceFormPage(page);
      await formPage.navigateToInvoice('102');

      await formPage.expectStatus('DRAFT');

      const deleteButton = page.getByRole('button', { name: /delete/i });
      await deleteButton.click();

      await page.waitForSelector('[role="dialog"]', { state: 'visible', timeout: 5000 });

      const confirmButton = page.locator('[role="dialog"]').getByRole('button', { name: /confirm|delete|yes/i });
      await confirmButton.click();

      await expect(page.locator('[role="dialog"]')).not.toBeVisible({ timeout: 3000 });

      await expect(page.locator('[role="status"], [data-sonner-toast]')).toContainText(/deleted|success/i);
    });
  });

  test.describe('Approve and Reject (Chief Accountant)', () => {
    test.use({ storageState: 'tests/.auth/chief_accountant.json' });

    test('INV-005: Approve sales invoice @smoke', async ({
      page,
      customerFactory,
      salesInvoiceFactory,
    }) => {
      const customer = customerFactory.createCustomer({ customerName: 'Approve Test Customer' });
      const pendingInvoice = salesInvoiceFactory.createPendingApprovalInvoice({
        id: 103,
        customerId: customer.id!,
        invoiceNumber: 'SI-APPROVE-001',
        createdById: 1,
      });

      const approvedInvoice = {
        ...pendingInvoice,
        status: 'POSTED',
        approvedById: 2,
      };

      await page.route('**/api/v1/sales-invoices/103', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: pendingInvoice }),
        });
      });

      await page.route('**/api/v1/sales-invoices/103/approve', async (route) => {
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ data: approvedInvoice }),
          });
        }
      });

      const formPage = new SalesInvoiceFormPage(page);
      await formPage.navigateToInvoice('103');

      await formPage.expectStatus('PENDING_APPROVAL');

      await formPage.approve();

      await expect(
        page.locator('[data-testid="invoice-status"], [role="status"]').filter({ hasText: /posted|approved|success/i }),
      ).toBeVisible({ timeout: 5000 });
    });

    test('INV-006: Reject sales invoice with reason', async ({
      page,
      customerFactory,
      salesInvoiceFactory,
    }) => {
      const customer = customerFactory.createCustomer({ customerName: 'Reject Test Customer' });
      const pendingInvoice = salesInvoiceFactory.createPendingApprovalInvoice({
        id: 104,
        customerId: customer.id!,
        invoiceNumber: 'SI-REJECT-001',
        createdById: 1,
      });

      const rejectedInvoice = {
        ...pendingInvoice,
        status: 'REJECTED',
        rejectionReason: 'Invoice amount does not match quote',
      };

      await page.route('**/api/v1/sales-invoices/104', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: pendingInvoice }),
        });
      });

      await page.route('**/api/v1/sales-invoices/104/reject', async (route) => {
        if (route.request().method() === 'POST') {
          const requestBody = await route.request().postDataJSON();

          if (!requestBody.reason) {
            await route.fulfill({
              status: 400,
              contentType: 'application/json',
              body: JSON.stringify({
                errors: { reason: 'Rejection reason is required' },
              }),
            });
          } else {
            await route.fulfill({
              status: 200,
              contentType: 'application/json',
              body: JSON.stringify({ data: rejectedInvoice }),
            });
          }
        }
      });

      const formPage = new SalesInvoiceFormPage(page);
      await formPage.navigateToInvoice('104');

      await formPage.expectStatus('PENDING_APPROVAL');

      await formPage.reject('Invoice amount does not match quote');

      await expect(
        page.locator('[data-testid="invoice-status"], [role="status"]').filter({ hasText: /rejected|cancelled/i }),
      ).toBeVisible({ timeout: 5000 });
    });
  });
});
