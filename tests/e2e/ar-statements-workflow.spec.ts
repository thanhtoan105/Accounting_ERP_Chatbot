import { test, expect } from '../support/fixtures';
import { loginAsUser } from '../support/helpers/auth-helper';
import { createStatementSummary, createStatementDetailed } from '../support/factories/statement.factory';

/**
 * Epic 5 - Story 5.5: Customer Statement & Reconciliation - E2E Tests
 * 
 * Critical user journeys:
 * - Generate and view customer statement (summary/detailed)
 * - Export statement (PDF/Excel)
 * - Send statement to customer via email
 * - Import customer reconciliation CSV
 * - View and resolve disputes
 * - View statement history and regenerate
 * - Batch export multiple statements
 */

test.describe('Story 5.5: Customer Statement & Reconciliation - E2E Workflow', () => {
  // Setup: Login before each test
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'accountant@example.com', 'password', 'accountant');
  });

  test('E2E-001: Generate and View Summary Statement', async ({ page }) => {
    // ========== GIVEN: Customer with invoices exists ==========
    const testCustomer = {
      id: 'customer-001',
      name: 'Test Customer AR',
      code: 'CUST-001',
      address: '123 Test Street',
      taxCode: '1234567890',
    };

    const statementSummary = createStatementSummary({
      customerId: testCustomer.id,
      customerName: testCustomer.name,
      customerAddress: testCustomer.address,
      customerTaxCode: testCustomer.taxCode,
    });

    // Network-first: Intercept BEFORE navigation
    const statementPromise = page.waitForResponse(
      (resp) => resp.url().includes('/ar-statements/') && resp.status() === 200,
    );

    // Mock: Customer list
    await page.route('**/api/v1/ar/customers*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [testCustomer],
          total: 1,
        }),
      });
    });

    // Mock: Statement summary response
    await page.route(`**/api/v1/ar-statements/${testCustomer.id}?format=SUMMARY*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: statementSummary,
        }),
      });
    });

    // ========== WHEN: User navigates to statement page ==========
    await page.goto('/ar-statements', { waitUntil: 'networkidle' });

    // ========== WHEN: User selects customer ==========
    const customerPicker = page.locator('[data-testid="customer-picker"]');
    await customerPicker.waitFor({ state: 'visible', timeout: 10000 });
    await customerPicker.click();

    await page.waitForSelector('[data-testid="customer-option"]', { state: 'visible' });
    await page.locator('[data-testid="customer-option"]').first().click();

    // ========== WHEN: User selects Summary format ==========
    const formatToggle = page.locator('[data-testid="format-toggle"]');
    await formatToggle.click();
    await page.locator('text=Summary').click();

    // ========== WHEN: User clicks Generate Statement ==========
    const generateButton = page.locator('[data-testid="generate-statement-button"]');
    await generateButton.click();

    // Wait for statement response
    await statementPromise;

    // ========== THEN: Statement is displayed with customer header ==========
    await expect(page.locator('[data-testid="statement-customer-name"]')).toContainText(testCustomer.name);
    await expect(page.locator('[data-testid="statement-customer-address"]')).toContainText(testCustomer.address);
    await expect(page.locator('[data-testid="statement-customer-tax-code"]')).toContainText(testCustomer.taxCode);

    // ========== THEN: Statement rows are displayed ==========
    await expect(page.locator('[data-testid="statement-row"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="statement-invoice-number"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="statement-invoice-amount"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="statement-balance"]').first()).toBeVisible();

    // ========== THEN: Totals row is displayed ==========
    await expect(page.locator('[data-testid="statement-totals"]')).toBeVisible();
    await expect(page.locator('[data-testid="total-invoices"]')).toBeVisible();
    await expect(page.locator('[data-testid="total-paid"]')).toBeVisible();
    await expect(page.locator('[data-testid="total-outstanding"]')).toBeVisible();
  });

  test('E2E-002: Export Statement as PDF', async ({ page }) => {
    // ========== GIVEN: Statement is generated ==========
    const testCustomer = { id: 'customer-001', name: 'Test Customer AR', code: 'CUST-001' };

    // Mock: Customer list
    await page.route('**/api/v1/ar/customers*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [testCustomer],
          total: 1,
        }),
      });
    });

    // Mock: Statement response
    await page.route(`**/api/v1/ar-statements/${testCustomer.id}*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: createStatementSummary({ customerId: testCustomer.id }),
        }),
      });
    });

    // Network-first: Intercept export BEFORE action
    const exportPromise = page.waitForResponse(
      (resp) => resp.url().includes('/export') && resp.status() === 200,
    );

    // Mock: PDF export response
    await page.route(`**/api/v1/ar-statements/${testCustomer.id}/export?format=PDF*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('PDF content'), // Mock PDF content
        headers: {
          'Content-Disposition': 'attachment; filename="Statement_CUST-001_2025-01-15.pdf"',
        },
      });
    });

    // ========== WHEN: User navigates to statement page ==========
    await page.goto('/ar-statements', { waitUntil: 'networkidle' });

    // Select customer and generate statement (simplified)
    await page.locator('[data-testid="customer-picker"]').click();
    await page.locator('[data-testid="customer-option"]').first().click();
    await page.locator('[data-testid="generate-statement-button"]').click();

    // Wait for statement to load
    await page.waitForSelector('[data-testid="statement-row"]', { state: 'visible' });

    // ========== WHEN: User clicks Export PDF ==========
    const exportButton = page.locator('[data-testid="export-pdf-button"]');
    await exportButton.click();

    // Wait for export response
    await exportPromise;

    // ========== THEN: PDF file is downloaded ==========
    // Playwright will handle download automatically
    // Implementation will verify download occurred
  });

  test('E2E-003: Send Statement to Customer via Email', async ({ page }) => {
    // ========== GIVEN: Statement is generated ==========
    const testCustomer = { id: 'customer-001', name: 'Test Customer AR' };
    const recipientEmail = 'customer@example.com';

    // Mock: Customer list
    await page.route('**/api/v1/ar/customers*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [testCustomer],
          total: 1,
        }),
      });
    });

    // Mock: Statement response
    await page.route(`**/api/v1/ar-statements/${testCustomer.id}*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: createStatementSummary({ customerId: testCustomer.id }),
        }),
      });
    });

    // Network-first: Intercept send BEFORE action
    const sendPromise = page.waitForResponse(
      (resp) => resp.url().includes('/send') && resp.status() === 202,
    );

    // Mock: Send statement response
    await page.route(`**/api/v1/ar-statements/${testCustomer.id}/send*`, async (route) => {
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            deliveryId: 'delivery-001',
            status: 'SENT',
            recipientEmail,
            sentAt: new Date().toISOString(),
          },
        }),
      });
    });

    // ========== WHEN: User navigates to statement page ==========
    await page.goto('/ar-statements', { waitUntil: 'networkidle' });

    // Select customer and generate statement
    await page.locator('[data-testid="customer-picker"]').click();
    await page.locator('[data-testid="customer-option"]').first().click();
    await page.locator('[data-testid="generate-statement-button"]').click();
    await page.waitForSelector('[data-testid="statement-row"]', { state: 'visible' });

    // ========== WHEN: User clicks Send to Customer ==========
    const sendButton = page.locator('[data-testid="send-statement-button"]');
    await sendButton.click();

    // ========== WHEN: User enters email and confirms ==========
    const emailInput = page.locator('[data-testid="recipient-email-input"]');
    await emailInput.fill(recipientEmail);

    const confirmButton = page.locator('[data-testid="confirm-send-button"]');
    await confirmButton.click();

    // Wait for send response
    await sendPromise;

    // ========== THEN: Success message is displayed ==========
    await expect(page.locator('[data-testid="send-success-message"]')).toBeVisible();
    await expect(page.locator('[data-testid="send-success-message"]')).toContainText('Statement sent successfully');
  });

  test('E2E-004: Import Customer Reconciliation CSV', async ({ page }) => {
    // ========== GIVEN: Customer exists ==========
    const testCustomer = { id: 'customer-001', name: 'Test Customer AR' };

    // Mock: Customer list
    await page.route('**/api/v1/ar/customers*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [testCustomer],
          total: 1,
        }),
      });
    });

    // Network-first: Intercept import BEFORE action
    const importPromise = page.waitForResponse(
      (resp) => resp.url().includes('/import-reconciliation') && resp.status() === 200,
    );

    // Mock: Reconciliation import response
    await page.route(`**/api/v1/ar-statements/${testCustomer.id}/import-reconciliation*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            reconciliationId: 'recon-001',
            matchedCount: 2,
            mismatchCount: 1,
            mismatches: [
              {
                invoiceNumber: 'INV-001',
                systemAmount: 10000000,
                customerAmount: 10500000,
                variance: 500000,
                varianceType: 'SIGNIFICANT',
              },
            ],
          },
        }),
      });
    });

    // ========== WHEN: User navigates to reconciliation page ==========
    await page.goto('/ar-statements/reconciliation', { waitUntil: 'networkidle' });

    // ========== WHEN: User selects customer ==========
    await page.locator('[data-testid="customer-picker"]').click();
    await page.locator('[data-testid="customer-option"]').first().click();

    // ========== WHEN: User uploads CSV file ==========
    const fileInput = page.locator('[data-testid="reconciliation-file-input"]');
    const csvContent = `InvoiceNumber,CustomerAmount,CustomerPayment,Notes
INV-001,10000000,5000000,Partial payment
INV-002,8000000,8000000,Full payment`;

    await fileInput.setInputFiles({
      name: 'reconciliation.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent),
    });

    // ========== WHEN: User clicks Import ==========
    const importButton = page.locator('[data-testid="import-reconciliation-button"]');
    await importButton.click();

    // Wait for import response
    await importPromise;

    // ========== THEN: Import results are displayed ==========
    await expect(page.locator('[data-testid="import-results"]')).toBeVisible();
    await expect(page.locator('[data-testid="matched-count"]')).toContainText('2');
    await expect(page.locator('[data-testid="mismatch-count"]')).toContainText('1');

    // ========== THEN: Mismatches table is displayed ==========
    await expect(page.locator('[data-testid="mismatch-row"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="mismatch-invoice-number"]').first()).toContainText('INV-001');
    await expect(page.locator('[data-testid="mismatch-variance"]').first()).toContainText('500,000');
  });

  test('E2E-005: View and Resolve Dispute', async ({ page }) => {
    // ========== GIVEN: Disputes exist ==========
    const testDispute = {
      id: 'dispute-001',
      invoiceNumber: 'INV-001',
      systemAmount: 10000000,
      customerAmount: 10500000,
      variance: 500000,
      varianceType: 'SIGNIFICANT',
      status: 'OPEN',
    };

    // Network-first: Intercept disputes list BEFORE navigation
    const disputesPromise = page.waitForResponse(
      (resp) => resp.url().includes('/disputes') && resp.status() === 200,
    );

    // Mock: Disputes list response
    await page.route('**/api/v1/ar-statements/disputes*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [testDispute],
          total: 1,
        }),
      });
    });

    // ========== WHEN: User navigates to disputes page ==========
    await page.goto('/ar-statements/disputes', { waitUntil: 'networkidle' });

    // Wait for disputes response
    await disputesPromise;

    // ========== THEN: Disputes grid is displayed ==========
    await expect(page.locator('[data-testid="disputes-grid"]')).toBeVisible();
    await expect(page.locator('[data-testid="dispute-row"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="dispute-invoice-number"]').first()).toContainText('INV-001');
    await expect(page.locator('[data-testid="dispute-status"]').first()).toContainText('OPEN');

    // ========== WHEN: User clicks on dispute to view details ==========
    await page.locator('[data-testid="dispute-row"]').first().click();

    // ========== THEN: Dispute detail dialog is displayed ==========
    await expect(page.locator('[data-testid="dispute-detail-dialog"]')).toBeVisible();
    await expect(page.locator('[data-testid="dispute-variance"]')).toContainText('500,000');
    await expect(page.locator('[data-testid="dispute-variance-type"]')).toContainText('SIGNIFICANT');

    // Network-first: Intercept resolve BEFORE action
    const resolvePromise = page.waitForResponse(
      (resp) => resp.url().includes('/resolve') && resp.status() === 200,
    );

    // Mock: Resolve dispute response
    await page.route(`**/api/v1/ar-statements/disputes/${testDispute.id}/resolve*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testDispute,
            status: 'RESOLVED',
            resolutionNotes: 'Resolved after customer verification',
            resolvedAt: new Date().toISOString(),
          },
        }),
      });
    });

    // ========== WHEN: User adds resolution notes and resolves ==========
    const resolutionNotesInput = page.locator('[data-testid="resolution-notes-input"]');
    await resolutionNotesInput.fill('Resolved after customer verification');

    const resolveButton = page.locator('[data-testid="resolve-dispute-button"]');
    await resolveButton.click();

    // Wait for resolve response
    await resolvePromise;

    // ========== THEN: Dispute status is updated to RESOLVED ==========
    await expect(page.locator('[data-testid="dispute-status"]').first()).toContainText('RESOLVED');
  });

  test('E2E-006: View Statement History and Regenerate', async ({ page }) => {
    // ========== GIVEN: Statement history exists ==========
    const testHistory = {
      id: 'history-001',
      statementNumber: 'STMT-2025-001',
      customerId: 'customer-001',
      generatedAt: '2025-01-15T10:00:00Z',
      format: 'SUMMARY',
      exportCount: 2,
      sentCount: 1,
    };

    // Network-first: Intercept history BEFORE navigation
    const historyPromise = page.waitForResponse(
      (resp) => resp.url().includes('/history') && resp.status() === 200,
    );

    // Mock: Statement history response
    await page.route('**/api/v1/ar-statements/customer-001/history*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [testHistory],
          total: 1,
        }),
      });
    });

    // ========== WHEN: User navigates to statement history ==========
    await page.goto('/ar-statements/history?customerId=customer-001', { waitUntil: 'networkidle' });

    // Wait for history response
    await historyPromise;

    // ========== THEN: History table is displayed ==========
    await expect(page.locator('[data-testid="statement-history-table"]')).toBeVisible();
    await expect(page.locator('[data-testid="history-row"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="history-statement-number"]').first()).toContainText('STMT-2025-001');
    await expect(page.locator('[data-testid="history-export-count"]').first()).toContainText('2');
    await expect(page.locator('[data-testid="history-sent-count"]').first()).toContainText('1');

    // Network-first: Intercept regenerate BEFORE action
    const regeneratePromise = page.waitForResponse(
      (resp) => resp.url().includes('/regenerate') && resp.status() === 200,
    );

    // Mock: Regenerate statement response
    await page.route(`**/api/v1/ar-statements/history/${testHistory.id}/regenerate*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: createStatementSummary({ customerId: testHistory.customerId }),
        }),
      });
    });

    // ========== WHEN: User clicks Regenerate ==========
    const regenerateButton = page.locator('[data-testid="regenerate-statement-button"]').first();
    await regenerateButton.click();

    // Wait for regenerate response
    await regeneratePromise;

    // ========== THEN: Statement is regenerated and displayed ==========
    await expect(page.locator('[data-testid="statement-row"]').first()).toBeVisible();
  });

  test('E2E-007: Batch Export Multiple Statements', async ({ page }) => {
    // ========== GIVEN: Multiple customers exist ==========
    const testCustomers = [
      { id: 'customer-001', name: 'Customer A', code: 'CUST-001' },
      { id: 'customer-002', name: 'Customer B', code: 'CUST-002' },
      { id: 'customer-003', name: 'Customer C', code: 'CUST-003' },
    ];

    // Mock: Customer list
    await page.route('**/api/v1/ar/customers*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: testCustomers,
          total: 3,
        }),
      });
    });

    // Network-first: Intercept batch export BEFORE action
    const batchExportPromise = page.waitForResponse(
      (resp) => resp.url().includes('/batch-export') && resp.status() === 200,
    );

    // Mock: Batch export response
    await page.route('**/api/v1/ar-statements/batch-export*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/zip',
        body: Buffer.from('ZIP content'), // Mock ZIP content
        headers: {
          'Content-Disposition': 'attachment; filename="Statements_2025-01-15.zip"',
        },
      });
    });

    // ========== WHEN: User navigates to batch export page ==========
    await page.goto('/ar-statements/batch-export', { waitUntil: 'networkidle' });

    // ========== WHEN: User selects multiple customers ==========
    for (const customer of testCustomers) {
      const customerCheckbox = page.locator(`[data-testid="customer-checkbox-${customer.id}"]`);
      await customerCheckbox.check();
    }

    // ========== WHEN: User selects format and clicks Generate ZIP ==========
    const formatSelect = page.locator('[data-testid="batch-export-format-select"]');
    await formatSelect.selectOption('PDF');

    const generateButton = page.locator('[data-testid="generate-batch-export-button"]');
    await generateButton.click();

    // Wait for batch export response
    await batchExportPromise;

    // ========== THEN: ZIP file is downloaded ==========
    // Playwright will handle download automatically
    // Implementation will verify download occurred
  });
});






























