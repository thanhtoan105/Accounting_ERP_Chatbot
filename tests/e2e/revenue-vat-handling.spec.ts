import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../auth.global-setup';

/**
 * Epic 5 - Story 5.6: Revenue & VAT Handling
 * 
 * As an accountant/auditor,
 * I want correct revenue and output VAT accounting and reporting,
 * so that statutory filings and financial statements are accurate.
 */

test.describe('Story 5.6: Revenue & VAT Handling - E2E Tests', () => {
  // Shared fixture: Authenticate as accountant
  test.beforeEach(async ({ page }) => {
    // Setup: Login as accountant
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', TEST_USERS.accountant.email);
    await page.fill('[data-testid="password-input"]', TEST_USERS.accountant.password);
    await page.click('[data-testid="login-button"]');
    await page.waitForURL('/dashboard');
  });

  test.describe('AC-VAT-001: VAT Rate Override Warning', () => {
    test('should display warning when overriding default VAT rate', async ({ page }) => {
      // GIVEN: User is creating invoice with line item
      await page.goto('/invoices/new');
      await page.waitForSelector('[data-testid="invoice-form"]');

      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');
      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Professional Services');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '1000000');

      // WHEN: User overrides default VAT rate (company default is 10%, user selects 5%)
      await page.selectOption('[data-testid="line-vat-rate"]', '5');

      // THEN: Warning dialog is displayed
      await expect(page.locator('[data-testid="vat-override-warning-dialog"]')).toBeVisible();
      await expect(page.locator('[data-testid="vat-override-warning-message"]')).toContainText(
        'You are overriding the default VAT rate from 10% to 5%'
      );
      await expect(page.locator('[data-testid="vat-override-warning-message"]')).toContainText(
        'Ensure this is correct per customer agreement'
      );

      // WHEN: User confirms override
      await page.click('[data-testid="confirm-vat-override-button"]');

      // THEN: Warning dialog closes and VAT rate is saved
      await expect(page.locator('[data-testid="vat-override-warning-dialog"]')).not.toBeVisible();
      await expect(page.locator('[data-testid="line-vat-rate"]')).toHaveValue('5');

      // THEN: Warning badge is displayed on line item
      await expect(page.locator('[data-testid="vat-override-badge"]')).toBeVisible();
    });

    test('should require confirmation before saving VAT rate override', async ({ page }) => {
      // GIVEN: User is creating invoice with line item
      await page.goto('/invoices/new');
      await page.waitForSelector('[data-testid="invoice-form"]');

      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');
      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Service');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');

      // WHEN: User overrides VAT rate and cancels warning
      await page.selectOption('[data-testid="line-vat-rate"]', '0');
      await expect(page.locator('[data-testid="vat-override-warning-dialog"]')).toBeVisible();
      await page.click('[data-testid="cancel-vat-override-button"]');

      // THEN: VAT rate reverts to default
      await expect(page.locator('[data-testid="line-vat-rate"]')).toHaveValue('10');
      await expect(page.locator('[data-testid="vat-override-badge"]')).not.toBeVisible();
    });
  });

  test.describe('AC-VAT-003: VAT Totals Validation', () => {
    test('should display VAT totals summary and block post if variance ≥ 1000 VND', async ({ page }) => {
      // GIVEN: Invoice with line items exists
      await page.goto('/invoices/new');
      await page.waitForSelector('[data-testid="invoice-form"]');

      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      // Add line items with VAT
      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Item 1');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');
      await page.selectOption('[data-testid="line-vat-rate"]', '10');

      // WHEN: Header VAT is manually set to mismatch (variance ≥ 1000 VND)
      await page.fill('[data-testid="header-vat-input"]', '20000'); // Should be 10000

      // THEN: Error message is displayed and post button is disabled
      await expect(page.locator('[data-testid="vat-variance-error"]')).toBeVisible();
      await expect(page.locator('[data-testid="vat-variance-error"]')).toContainText(
        'VAT rounding variance: 10000 VND'
      );
      await expect(page.locator('[data-testid="post-invoice-button"]')).toBeDisabled();
    });

    test('should show warning but allow post if variance < 1000 VND', async ({ page }) => {
      // GIVEN: Invoice with line items exists
      await page.goto('/invoices/new');
      await page.waitForSelector('[data-testid="invoice-form"]');

      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Item 1');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '100000');
      await page.selectOption('[data-testid="line-vat-rate"]', '10');

      // WHEN: Header VAT has small variance (< 1000 VND)
      await page.fill('[data-testid="header-vat-input"]', '10050'); // Variance: 50 VND

      // THEN: Warning message is displayed but post button remains enabled
      await expect(page.locator('[data-testid="vat-variance-warning"]')).toBeVisible();
      await expect(page.locator('[data-testid="vat-variance-warning"]')).toContainText(
        'VAT rounding variance: 50 VND'
      );
      await expect(page.locator('[data-testid="post-invoice-button"]')).toBeEnabled();
    });

    test('should display GL split preview before posting', async ({ page }) => {
      // GIVEN: Invoice with line items ready to post
      await page.goto('/invoices/new');
      await page.waitForSelector('[data-testid="invoice-form"]');

      await page.click('[data-testid="customer-picker"]');
      await page.type('[data-testid="customer-search"]', 'Test Customer 1');
      await page.click('[data-testid="customer-option-0"]');

      await page.fill('[data-testid="invoice-date"]', '2025-01-15');

      await page.click('[data-testid="add-line-item"]');
      await page.fill('[data-testid="line-description"]', 'Service');
      await page.fill('[data-testid="line-quantity"]', '1');
      await page.fill('[data-testid="line-unit-price"]', '1000000');
      await page.selectOption('[data-testid="line-vat-rate"]', '10');
      await page.fill('[data-testid="line-revenue-account"]', '511001');

      // WHEN: User views GL split preview
      await page.click('[data-testid="show-gl-split-preview-button"]');

      // THEN: GL split preview shows Dr 131, Cr 511001, Cr 3331
      await expect(page.locator('[data-testid="gl-split-preview"]')).toBeVisible();
      await expect(page.locator('[data-testid="gl-entry-131-debit"]')).toContainText('1,100,000');
      await expect(page.locator('[data-testid="gl-entry-511001-credit"]')).toContainText('1,000,000');
      await expect(page.locator('[data-testid="gl-entry-3331-credit"]')).toContainText('100,000');
    });
  });

  test.describe('AC-VAT-004: Credit Note Creation', () => {
    test('should create credit note referencing original invoice', async ({ page }) => {
      // GIVEN: Posted invoice exists
      // (Pre-created in test data setup - invoice ID: INV-001)

      // WHEN: User navigates to credit note creation
      await page.goto('/invoices');
      await page.click('[data-testid="invoice-row-INV-001"]');
      await page.click('[data-testid="create-credit-note-button"]');

      // THEN: Credit note form is displayed with original invoice reference
      await expect(page.locator('[data-testid="credit-note-form"]')).toBeVisible();
      await expect(page.locator('[data-testid="original-invoice-reference"]')).toContainText('INV-001');
      await expect(page.locator('[data-testid="original-invoice-link"]')).toBeVisible();

      // WHEN: User confirms credit note creation
      await page.click('[data-testid="confirm-credit-note-button"]');

      // THEN: Credit note is created with inverted amounts
      await expect(page.locator('[data-testid="success-message"]')).toContainText('Credit note created');
      await expect(page.locator('[data-testid="credit-note-status"]')).toContainText('Draft');

      // THEN: Credit note shows negative amounts
      const creditNoteTotal = await page.locator('[data-testid="credit-note-total"]').textContent();
      await expect(creditNoteTotal).toContain('-');
    });

    test('should display audit cross-reference to original invoice', async ({ page }) => {
      // GIVEN: Credit note exists referencing original invoice
      // (Pre-created in test data setup)

      // WHEN: User views credit note details
      await page.goto('/invoices/CN-001');
      await page.click('[data-testid="audit-trail-button"]');

      // THEN: Audit trail shows cross-reference to original invoice
      await expect(page.locator('[data-testid="audit-entry-credit-note"]')).toBeVisible();
      await expect(page.locator('[data-testid="audit-original-invoice-id"]')).toContainText('INV-001');
      await expect(page.locator('[data-testid="audit-original-invoice-link"]')).toBeVisible();
    });
  });

  test.describe('AC-VAT-005: Output VAT Report', () => {
    test('should generate output VAT report with filters', async ({ page }) => {
      // GIVEN: Posted invoices exist for period 2025-01
      // (Pre-created in test data setup)

      // WHEN: User navigates to VAT report page
      await page.goto('/reports/vat-output');
      await page.waitForSelector('[data-testid="vat-report-form"]');

      // WHEN: User sets filters and generates report
      await page.fill('[data-testid="period-selector"]', '2025-01');
      await page.click('[data-testid="generate-report-button"]');

      // THEN: Report table is displayed with invoice details
      await expect(page.locator('[data-testid="vat-report-table"]')).toBeVisible();
      await expect(page.locator('[data-testid="report-row-0"]')).toBeVisible();

      // THEN: Report shows required columns
      await expect(page.locator('[data-testid="column-invoice-number"]')).toBeVisible();
      await expect(page.locator('[data-testid="column-invoice-date"]')).toBeVisible();
      await expect(page.locator('[data-testid="column-customer-name"]')).toBeVisible();
      await expect(page.locator('[data-testid="column-customer-tax-code"]')).toBeVisible();
      await expect(page.locator('[data-testid="column-revenue-0pct"]')).toBeVisible();
      await expect(page.locator('[data-testid="column-revenue-5pct"]')).toBeVisible();
      await expect(page.locator('[data-testid="column-revenue-10pct"]')).toBeVisible();
      await expect(page.locator('[data-testid="column-revenue-exempt"]')).toBeVisible();
      await expect(page.locator('[data-testid="column-total-vat-collected"]')).toBeVisible();

      // THEN: Summary totals row is displayed
      await expect(page.locator('[data-testid="report-summary-totals"]')).toBeVisible();
    });

    test('should export VAT report to Excel in ND123 format', async ({ page }) => {
      // GIVEN: VAT report is generated
      await page.goto('/reports/vat-output');
      await page.fill('[data-testid="period-selector"]', '2025-01');
      await page.click('[data-testid="generate-report-button"]');
      await page.waitForSelector('[data-testid="vat-report-table"]');

      // WHEN: User clicks export to Excel
      const downloadPromise = page.waitForEvent('download');
      await page.click('[data-testid="export-excel-button"]');
      const download = await downloadPromise;

      // THEN: Excel file is downloaded in ND123 format
      await expect(download.suggestedFilename()).toMatch(/vat-report.*\.xlsx$/);
      
      // Verify file content (would need to read file in real test)
      // File should contain: Invoice Number, Invoice Date, Customer Name, Customer Tax Code,
      // Revenue (0%), Revenue (5%), Revenue (10%), Revenue (Exempt), Total VAT Collected
    });
  });

  test.describe('AC-VAT-006: VAT Correction Management', () => {
    test('should create VAT correction with reason and diff preview', async ({ page }) => {
      // GIVEN: Posted invoice exists
      await page.goto('/invoices/INV-001');
      await page.click('[data-testid="create-vat-correction-button"]');

      // THEN: VAT correction dialog is displayed
      await expect(page.locator('[data-testid="vat-correction-dialog"]')).toBeVisible();
      await expect(page.locator('[data-testid="current-vat-amount"]')).toBeVisible();

      // WHEN: User enters correction details
      await page.fill('[data-testid="new-vat-amount"]', '150000');
      await page.fill('[data-testid="correction-reason"]', 'Customer agreement specifies different VAT rate');

      // THEN: Diff preview is displayed
      await expect(page.locator('[data-testid="vat-diff-preview"]')).toBeVisible();
      await expect(page.locator('[data-testid="old-vat-amount"]')).toContainText('100,000');
      await expect(page.locator('[data-testid="new-vat-amount"]')).toContainText('150,000');
      await expect(page.locator('[data-testid="vat-variance"]')).toContainText('50,000');

      // WHEN: User submits correction
      await page.click('[data-testid="submit-correction-button"]');

      // THEN: Correction is created (status depends on variance threshold)
      await expect(page.locator('[data-testid="success-message"]')).toContainText('VAT correction created');
    });

    test('should require approval for corrections above threshold', async ({ page }) => {
      // GIVEN: User (Chief Accountant) views pending correction
      await page.goto('/invoices/corrections');
      await page.click('[data-testid="correction-row-0"]');

      // WHEN: Correction variance > 10M VND threshold
      await expect(page.locator('[data-testid="correction-status"]')).toContainText('Pending Approval');
      await expect(page.locator('[data-testid="approve-correction-button"]')).toBeVisible();

      // WHEN: Chief Accountant approves correction
      await page.click('[data-testid="approve-correction-button"]');
      await page.fill('[data-testid="approval-notes"]', 'Approved per customer agreement');
      await page.click('[data-testid="confirm-approval-button"]');

      // THEN: Correction is approved and applied
      await expect(page.locator('[data-testid="success-message"]')).toContainText('Correction approved');
      await expect(page.locator('[data-testid="correction-status"]')).toContainText('Approved');
    });
  });
});

