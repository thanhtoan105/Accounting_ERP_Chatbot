import { test, expect } from '@playwright/test';

/**
 * Epic 5 - Story 5.1: Sales Invoice Entry, Edit, and Draft Management
 * 
 * As an accountant, I want to create and edit sales invoices with strict validations
 * and attachments, so that revenue recognition and receivables are accurate and auditable.
 */

test.describe('Story 5.1: Sales Invoice Entry', () => {
  // Shared fixture: Authenticate as accountant
  test.beforeEach(async ({ page }) => {
    // Setup: Login as accountant
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', 'accountant@test.example.com');
    await page.fill('[data-testid="password-input"]', 'Test@123456');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL('/dashboard');
  });

  test.describe('P0: Critical Tests', () => {
    test('AC1.1: should create invoice with all required fields (happy path)', async ({ page }) => {
      // GIVEN: User is on invoice creation form
      await page.goto('/invoices/new');
      await page.waitForSelector('[data-testid="invoice-form"]');

      // WHEN: User enters required invoice details
      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');
      await page.fill('[data-testid="due-date"]', '2025-02-15');
      await page.fill('[data-testid="reference-text"]', 'INV-2025-001');

      // Add line item
      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Professional Services');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '1000000');
      await page.selectOption('[data-testid="line-vat-rate"]', '10');

      // Save as draft
      await page.click('[data-testid="save-draft-button"]');

      // THEN: Invoice is saved and confirmation is shown
      await expect(page.locator('[data-testid="success-message"]')).toContainText('Invoice saved as draft');
      await expect(page.locator('[data-testid="invoice-status"]')).toHaveText('Draft');
      await expect(page.locator('[data-testid="invoice-id"]')).toContainText('INV-');
    });

    test('AC1.2: should auto-generate invoice number per customer/period', async ({ page }) => {
      // GIVEN: User creates invoice for specific customer in specific period
      await page.goto('/invoices/new');
      
      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      // Add minimum line item
      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Service');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');

      // WHEN: Form is saved
      await page.click('[data-testid="save-draft-button"]');

      // THEN: Invoice number follows pattern CUST-1-2025-001
      const invoiceNumber = await page.locator('[data-testid="invoice-number"]').textContent();
      await expect(invoiceNumber).toMatch(/^CUST-1-2025-\d{3}$/);
    });

    test('AC1.3: should prevent duplicate invoice (same customer + inv# + date)', async ({ page }) => {
      // GIVEN: An invoice already exists with specific customer + number + date
      // (Pre-created in test data setup)

      // WHEN: User attempts to create identical invoice
      await page.goto('/invoices/new');
      
      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');
      await page.fill('[data-testid="due-date"]', '2025-02-15');
      await page.fill('[data-testid="reference-text"]', 'DUPLICATE-INV');

      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Service');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');

      await page.click('[data-testid="save-draft-button"]');

      // THEN: Duplicate validation error is shown
      await expect(page.locator('[data-testid="error-message"]')).toContainText('already exists');
      await expect(page).toHaveURL(/\/invoices\/new/);
    });

    test('AC1.4: should reject date in closed period', async ({ page }) => {
      // GIVEN: User is on invoice form
      await page.goto('/invoices/new');

      // WHEN: User selects date in closed period (e.g., Dec 2024 if closed)
      await page.click('[data-testid="invoice-date"]');
      
      // Calendar picker should show closed dates as disabled
      const closedDateButton = page.locator('[data-testid="date-2024-12-15"]');
      const isDisabled = await closedDateButton.evaluate(el => el.hasAttribute('disabled'));

      // THEN: Closed period dates are disabled
      await expect(closedDateButton).toBeDisabled();
    });

    test('AC1.5: should validate required fields on save', async ({ page }) => {
      // GIVEN: User is on invoice form with empty fields
      await page.goto('/invoices/new');

      // WHEN: User attempts to save without required fields
      await page.click('[data-testid="save-draft-button"]');

      // THEN: Form shows validation errors for required fields
      await expect(page.locator('[data-testid="error-customer-required"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-date-required"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-line-items-required"]')).toBeVisible();
      await expect(page.locator('[data-testid="save-draft-button"]')).toBeDisabled();
    });

    test('AC1.6: should support VAT rate selection (0%, 5%, 10%, exempt)', async ({ page }) => {
      // GIVEN: User is creating invoice with line items
      await page.goto('/invoices/new');

      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      // WHEN: User adds line items with different VAT rates
      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Item 0% VAT');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');
      await page.selectOption('[data-testid="line-vat-rate"]', '0');

      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description-1"]', 'Item 10% VAT');
      await page.fill('[data-testid="line-quantity-1"]', '1');
      await page.fill('[data-testid="line-unit-price-1"]', '100000');
      await page.selectOption('[data-testid="line-vat-rate-1"]', '10');

      // THEN: VAT rates are selectable and totals recalculate
      await expect(page.locator('[data-testid="line-vat-rate"]')).toHaveValue('0');
      await expect(page.locator('[data-testid="line-vat-rate-1"]')).toHaveValue('10');
      
      // Verify total VAT = 10000 (0 + 10000)
      const totalVAT = await page.locator('[data-testid="total-vat"]').textContent();
      await expect(totalVAT).toContain('10,000');
    });

    test('AC1.7: should handle file attachments (drag/drop, preview, delete)', async ({ page }) => {
      // GIVEN: User is on invoice form
      await page.goto('/invoices/new');

      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      // WHEN: User uploads attachment via drag/drop
      const attachment = await page.locator('[data-testid="attachment-drop-zone"]');
      await attachment.dragAndDrop('[data-testid="file-input"]', { sourcePosition: { x: 1, y: 1 }, targetPosition: { x: 1, y: 1 } });
      
      // Alternative: use file input directly if drag/drop not supported
      await page.locator('[data-testid="file-input"]').setInputFiles('./tests/fixtures/sample-invoice.pdf');

      // THEN: File is shown in attachment list with preview and delete options
      await expect(page.locator('[data-testid="attachment-item"]')).toContainText('sample-invoice.pdf');
      await expect(page.locator('[data-testid="attachment-preview-button"]')).toBeEnabled();
      await expect(page.locator('[data-testid="attachment-delete-button"]')).toBeEnabled();

      // Test delete
      await page.click('[data-testid="attachment-delete-button"]');
      await expect(page.locator('[data-testid="attachment-item"]')).not.toBeVisible();
    });

    test('AC1.8: should support autosave with undo/redo', async ({ page }) => {
      // GIVEN: User is on invoice form
      await page.goto('/invoices/new');

      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      // WHEN: User enters data and waits for autosave
      await page.fill('[data-testid="reference-text"]', 'First Reference');
      await page.waitForTimeout(2000); // Wait for autosave

      // THEN: Autosave indicator shows success
      await expect(page.locator('[data-testid="autosave-status"]')).toContainText('Saved');

      // WHEN: User changes data and clicks undo
      await page.fill('[data-testid="reference-text"]', 'Changed Reference');
      await page.waitForTimeout(2000);

      await page.click('[data-testid="undo-button"]');

      // THEN: Previous value is restored
      const referenceValue = await page.inputValue('[data-testid="reference-text"]');
      await expect(referenceValue).toBe('First Reference');

      // WHEN: User clicks redo
      await page.click('[data-testid="redo-button"]');

      // THEN: Changed value is restored
      const redoValue = await page.inputValue('[data-testid="reference-text"]');
      await expect(redoValue).toBe('Changed Reference');
    });

    test('AC1.9: should support CSV/Excel import with atomicity', async ({ page }) => {
      // GIVEN: User is on invoice import page
      await page.goto('/invoices/import');

      // WHEN: User uploads valid CSV file with 10 invoices
      await page.locator('[data-testid="import-file-input"]').setInputFiles('./tests/fixtures/invoices-import.csv');

      await page.click('[data-testid="map-columns-button"]');
      // Auto-map columns if template matches
      await page.click('[data-testid="confirm-mapping-button"]');

      // Wait for import to complete
      await page.waitForSelector('[data-testid="import-complete-message"]');

      // THEN: All invoices are imported successfully
      const successMessage = await page.locator('[data-testid="import-complete-message"]').textContent();
      await expect(successMessage).toContain('10 invoices imported');

      // Verify invoices in list
      await page.goto('/invoices');
      const invoiceCount = await page.locator('[data-testid="invoice-row"]').count();
      await expect(invoiceCount).toBeGreaterThanOrEqual(10);
    });
  });

  test.describe('P1: High Priority Tests', () => {
    test('AC2.1: should restrict draft editing to creator/admin only', async ({ page, context }) => {
      // GIVEN: Accountant created a draft invoice
      await page.goto('/invoices/new');
      
      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');
      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Service');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');

      await page.click('[data-testid="save-draft-button"]');
      
      const invoiceId = await page.locator('[data-testid="invoice-id"]').textContent();

      // WHEN: Different user (Chief Accountant) tries to edit draft
      const newPage = await context.newPage();
      await newPage.goto('/login');
      await newPage.fill('[data-testid="email-input"]', 'chief@test.example.com');
      await newPage.fill('[data-testid="password-input"]', 'Test@123456');
      await newPage.click('[data-testid="login-button"]');
      await newPage.waitForURL('/dashboard');

      await newPage.goto(`/invoices/${invoiceId}/edit`);

      // THEN: Edit is blocked with permission error
      await expect(newPage.locator('[data-testid="error-message"]')).toContainText('do not have permission');
    });

    test('AC2.2: should support draft deletion with confirmation', async ({ page }) => {
      // GIVEN: Draft invoice exists
      await page.goto('/invoices/new');
      
      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');
      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Service');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');

      await page.click('[data-testid="save-draft-button"]');

      // WHEN: User clicks delete button
      await page.click('[data-testid="delete-invoice-button"]');

      // THEN: Confirmation dialog is shown
      await expect(page.locator('[data-testid="delete-confirmation-dialog"]')).toBeVisible();
      await expect(page.locator('[data-testid="delete-confirmation-message"]')).toContainText('This action cannot be undone');

      // User confirms deletion
      await page.click('[data-testid="confirm-delete-button"]');

      // THEN: Invoice is deleted and user redirected to invoice list
      await expect(page).toHaveURL(/\/invoices$/);
      await expect(page.locator('[data-testid="success-message"]')).toContainText('Invoice deleted');
    });

    test('AC2.3: should validate revenue account is leaf account only', async ({ page }) => {
      // GIVEN: User is adding line item
      await page.goto('/invoices/new');
      
      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Service');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');

      // WHEN: User selects parent revenue account (511) instead of leaf
      await page.click('[data-testid="line-revenue-account"]');
      await page.type('[data-testid="account-search"]', '511');
      
      const parentAccountOption = page.locator('[data-testid="account-option-parent"]');
      
      // THEN: Parent account option shows as disabled or warning
      await expect(parentAccountOption).toBeDisabled();
      await expect(page.locator('[data-testid="account-warning"]')).toContainText('parent account');
    });

    test('AC2.4: should enforce mandatory dimension validation', async ({ page }) => {
      // GIVEN: Cost center is mandatory for expense accounts
      await page.goto('/invoices/new');
      
      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Service');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');

      // WHEN: User tries to save without required dimension
      // (If cost center is required and not filled)
      await page.click('[data-testid="save-draft-button"]');

      // THEN: Validation error shows for missing dimension
      await expect(page.locator('[data-testid="error-cost-center-required"]')).toBeVisible();
    });

    test('AC2.5: should auto-calculate totals and validate', async ({ page }) => {
      // GIVEN: User is entering invoice with line items
      await page.goto('/invoices/new');
      
      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-quantity"]', '2');
      await page.fill('[data-testid="line-unit-price"]', '100000');
      await page.selectOption('[data-testid="line-vat-rate"]', '10');

      // WHEN: Form calculates totals
      await page.waitForTimeout(500); // Wait for calculation

      // THEN: Totals are calculated correctly
      // Subtotal = 2 * 100,000 = 200,000
      // VAT = 200,000 * 10% = 20,000
      // Total = 220,000
      const subtotal = await page.locator('[data-testid="subtotal"]').textContent();
      const vat = await page.locator('[data-testid="total-vat"]').textContent();
      const total = await page.locator('[data-testid="grand-total"]').textContent();

      await expect(subtotal).toContain('200,000');
      await expect(vat).toContain('20,000');
      await expect(total).toContain('220,000');
    });

    test('AC2.6: should validate attachment file type/size', async ({ page }) => {
      // GIVEN: User is uploading attachment
      await page.goto('/invoices/new');

      // WHEN: User attempts to upload oversized file (>10MB)
      // Mock file with size property
      const largeFile = new File(['x'.repeat(11 * 1024 * 1024)], 'large-file.pdf', { type: 'application/pdf' });
      
      await page.locator('[data-testid="file-input"]').setInputFiles([largeFile] as any);

      // THEN: File size validation error is shown
      await expect(page.locator('[data-testid="error-file-size"]')).toContainText('exceeds maximum');

      // WHEN: User attempts unsupported file type (.exe)
      const execFile = new File(['exec'], 'malware.exe', { type: 'application/x-msdownload' });
      await page.locator('[data-testid="file-input"]').setInputFiles([execFile] as any);

      // THEN: File type validation error is shown
      await expect(page.locator('[data-testid="error-file-type"]')).toBeVisible();
    });
  });

  test.describe('P2: Medium Priority Tests', () => {
    test('should search invoices by number, customer, date range', async ({ page }) => {
      // GIVEN: Multiple invoices exist
      await page.goto('/invoices');

      // WHEN: User searches by invoice number
      await page.fill('[data-testid="search-input"]', 'INV-2025-001');
      await page.click('[data-testid="search-button"]');

      // THEN: Only matching invoice is shown
      const invoiceRows = await page.locator('[data-testid="invoice-row"]').count();
      await expect(invoiceRows).toBe(1);
    });

    test('should export invoice to PDF', async ({ page }) => {
      // GIVEN: Draft invoice exists
      await page.goto('/invoices');
      await page.click('[data-testid="invoice-row-0"]');

      // WHEN: User clicks export to PDF
      const downloadPromise = page.waitForEvent('download');
      await page.click('[data-testid="export-pdf-button"]');
      const download = await downloadPromise;

      // THEN: PDF file is downloaded
      await expect(download.suggestedFilename()).toContain('invoice');
    });

    test('should show audit log with all changes', async ({ page }) => {
      // GIVEN: Invoice has been created and edited
      await page.goto('/invoices');
      await page.click('[data-testid="invoice-row-0"]');

      // WHEN: User opens audit log
      await page.click('[data-testid="audit-log-button"]');

      // THEN: Audit entries show create, edit, and other actions
      const auditEntries = await page.locator('[data-testid="audit-entry"]').count();
      await expect(auditEntries).toBeGreaterThan(0);
      
      const firstEntry = await page.locator('[data-testid="audit-entry-0"]').textContent();
      await expect(firstEntry).toContainText('created');
    });
  });
});
