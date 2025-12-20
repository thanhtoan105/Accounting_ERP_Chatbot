import { test, expect } from '../support/fixtures'
import { loginAsUser } from '../support/helpers/auth-helper'
import {
  createBankReconciliation,
  createStatementImportResult,
  createStatementImportWithErrors,
  createTestCSVContent,
  createInvalidCSVContent,
  createBankStatementFormat,
} from '../support/factories/bank-reconciliation.factory'

/**
 * Epic 6 - Story 6.5: Bank Reconciliation - Statement Import E2E Tests
 *
 * Critical user journeys:
 * - Upload bank statement (CSV/Excel) with file validation
 * - Map columns with auto-detection and saved profiles
 * - Preview parsed data with validation warnings
 * - Import statement with duplicate detection
 * - Handle import errors with downloadable error report
 */

test.describe('Story 6.5: Bank Reconciliation - Statement Import Flow', () => {
  // Setup: Login before each test
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'accountant@example.com', 'password', 'accountant')
  })

  test('E2E-IMPORT-001: Upload CSV file and auto-detect columns', async ({ page }) => {
    // ========== GIVEN: Bank reconciliation exists ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-001',
      status: 'IN_PROGRESS',
    })

    const testFormat = createBankStatementFormat()

    // Mock: Reconciliation detail
    await page.route('**/api/v1/bank-reconciliations/recon-001*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: testReconciliation,
        }),
      })
    })

    // ========== WHEN: User navigates to reconciliation detail ==========
    await page.goto('/accounting/bank-reconciliation/recon-001', { waitUntil: 'networkidle' })

    // ========== WHEN: User clicks Import Statement button ==========
    const importButton = page.locator('[data-testid="import-statement-button"]')
    await importButton.waitFor({ state: 'visible', timeout: 10000 })
    await importButton.click()

    // ========== THEN: Import dialog opens on Step 1 (Upload) ==========
    await expect(page.locator('[data-testid="statement-import-dialog"]')).toBeVisible()
    await expect(page.locator('[data-testid="import-step-indicator-1"]')).toHaveClass(/active/)

    // Network-first: Intercept column analysis BEFORE file upload
    const analyzePromise = page.waitForResponse(
      (resp) => resp.url().includes('/import/analyze') && resp.status() === 200,
    )

    // Mock: Column analysis response
    await page.route('**/api/v1/bank-reconciliations/recon-001/import/analyze*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            suggestions: {
              dateColumn: 'Transaction Date',
              descriptionColumn: 'Description',
              referenceColumn: 'Reference Number',
              debitColumn: 'Debit',
              creditColumn: 'Credit',
              balanceColumn: 'Balance',
            },
            headers: [
              'Transaction Date',
              'Description',
              'Reference Number',
              'Debit',
              'Credit',
              'Balance',
            ],
            sampleRows: [
              {
                'Transaction Date': '15/01/2025',
                Description: 'Customer Payment',
                'Reference Number': 'TXN-001',
                Debit: '0',
                Credit: '50000000',
                Balance: '500000000',
              },
            ],
            savedFormat: testFormat,
          },
        }),
      })
    })

    // ========== WHEN: User uploads CSV file ==========
    const csvContent = createTestCSVContent(10)
    const fileInput = page.locator('[data-testid="statement-file-input"]')

    await fileInput.setInputFiles({
      name: 'statement-jan-2025.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent),
    })

    // Wait for column analysis
    await analyzePromise

    // ========== THEN: Dialog advances to Step 2 (Mapping) with auto-detected columns ==========
    await expect(page.locator('[data-testid="import-step-indicator-2"]')).toHaveClass(/active/)

    // Verify auto-detected columns are displayed
    await expect(page.locator('[data-testid="date-column-select"]')).toHaveValue(
      'Transaction Date',
    )
    await expect(page.locator('[data-testid="description-column-select"]')).toHaveValue(
      'Description',
    )
    await expect(page.locator('[data-testid="debit-column-select"]')).toHaveValue('Debit')
    await expect(page.locator('[data-testid="credit-column-select"]')).toHaveValue('Credit')

    // ========== THEN: Saved profile indicator shows format was loaded ==========
    await expect(page.locator('[data-testid="saved-profile-indicator"]')).toContainText(
      'Vietcombank Standard Format',
    )
  })

  test('E2E-IMPORT-002: Complete import flow with preview and confirmation', async ({ page }) => {
    // ========== GIVEN: Reconciliation exists ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-002' })
    const importResult = createStatementImportResult({
      reconciliationId: 'recon-002',
      totalRows: 10,
      successCount: 10,
    })

    await page.route('**/api/v1/bank-reconciliations/recon-002*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // Mock: Column analysis
    await page.route('**/api/v1/bank-reconciliations/recon-002/import/analyze*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            suggestions: {
              dateColumn: 'Transaction Date',
              descriptionColumn: 'Description',
              debitColumn: 'Debit',
              creditColumn: 'Credit',
            },
            headers: ['Transaction Date', 'Description', 'Debit', 'Credit', 'Balance'],
            sampleRows: [],
          },
        }),
      })
    })

    // Network-first: Intercept import BEFORE submission
    const importPromise = page.waitForResponse(
      (resp) => resp.url().includes('/import') && !resp.url().includes('/analyze') && resp.status() === 200,
    )

    // Mock: Import response
    await page.route('**/api/v1/bank-reconciliations/recon-002/import*', async (route) => {
      // Skip analyze endpoint
      if (route.request().url().includes('/analyze')) {
        return
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: importResult }),
      })
    })

    // ========== WHEN: User goes through import wizard ==========
    await page.goto('/accounting/bank-reconciliation/recon-002')
    await page.locator('[data-testid="import-statement-button"]').click()

    // Step 1: Upload file
    const csvContent = createTestCSVContent(10)
    await page
      .locator('[data-testid="statement-file-input"]')
      .setInputFiles({
        name: 'statement.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(csvContent),
      })

    // Step 2: Column mapping - click Next to proceed to preview
    await page.waitForSelector('[data-testid="import-next-button"]', { state: 'visible' })
    await page.locator('[data-testid="import-next-button"]').click()

    // Step 3: Preview - verify preview shows mapping summary
    await expect(page.locator('[data-testid="import-step-indicator-3"]')).toHaveClass(/active/)
    await expect(page.locator('[data-testid="mapping-summary"]')).toBeVisible()
    await expect(page.locator('[data-testid="file-info"]')).toContainText('statement.csv')

    // Click Import to submit
    await page.locator('[data-testid="import-submit-button"]').click()

    // Wait for import response
    await importPromise

    // Step 4: Result - verify success statistics
    await expect(page.locator('[data-testid="import-step-indicator-4"]')).toHaveClass(/active/)
    await expect(page.locator('[data-testid="import-success-count"]')).toContainText('10')
    await expect(page.locator('[data-testid="import-error-count"]')).toContainText('0')

    // ========== THEN: Success badge is shown ==========
    await expect(page.locator('[data-testid="import-result-badge"]')).toHaveClass(/success/)
  })

  test('E2E-IMPORT-003: Import with errors shows error table and download button', async ({
    page,
  }) => {
    // ========== GIVEN: Reconciliation exists ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-003' })
    const importResultWithErrors = createStatementImportWithErrors(3)

    await page.route('**/api/v1/bank-reconciliations/recon-003*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    await page.route('**/api/v1/bank-reconciliations/recon-003/import/analyze*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            suggestions: { dateColumn: 'Date', descriptionColumn: 'Desc' },
            headers: ['Date', 'Desc', 'Amount'],
            sampleRows: [],
          },
        }),
      })
    })

    // Mock: Import response with errors
    await page.route('**/api/v1/bank-reconciliations/recon-003/import', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: importResultWithErrors }),
      })
    })

    // ========== WHEN: User imports invalid CSV ==========
    await page.goto('/accounting/bank-reconciliation/recon-003')
    await page.locator('[data-testid="import-statement-button"]').click()

    const invalidCSV = createInvalidCSVContent()
    await page
      .locator('[data-testid="statement-file-input"]')
      .setInputFiles({
        name: 'invalid.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(invalidCSV),
      })

    await page.locator('[data-testid="import-next-button"]').click()
    await page.locator('[data-testid="import-submit-button"]').click()

    // ========== THEN: Error statistics are displayed ==========
    await expect(page.locator('[data-testid="import-error-count"]')).toContainText('3')
    await expect(page.locator('[data-testid="import-result-badge"]')).toHaveClass(/error/)

    // ========== THEN: Error table shows first 10 errors ==========
    await expect(page.locator('[data-testid="import-errors-table"]')).toBeVisible()
    const errorRows = page.locator('[data-testid="error-row"]')
    await expect(errorRows).toHaveCount(3)

    // Verify error row structure
    const firstError = errorRows.first()
    await expect(firstError.locator('[data-testid="error-row-number"]')).toBeVisible()
    await expect(firstError.locator('[data-testid="error-field"]')).toBeVisible()
    await expect(firstError.locator('[data-testid="error-message"]')).toContainText('Invalid')

    // ========== THEN: Download error report button is available ==========
    await expect(page.locator('[data-testid="download-error-report-button"]')).toBeVisible()
  })

  test('E2E-IMPORT-004: Duplicate file detection shows warning', async ({ page }) => {
    // ========== GIVEN: Reconciliation exists ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-004' })
    const duplicateImportResult = createStatementImportResult({
      isDuplicate: true,
      duplicateReconciliationId: 'recon-duplicate-001',
    })

    await page.route('**/api/v1/bank-reconciliations/recon-004*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    await page.route('**/api/v1/bank-reconciliations/recon-004/import/analyze*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            suggestions: { dateColumn: 'Date' },
            headers: ['Date', 'Amount'],
            sampleRows: [],
          },
        }),
      })
    })

    // Mock: Import response with duplicate flag
    await page.route('**/api/v1/bank-reconciliations/recon-004/import', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: duplicateImportResult }),
      })
    })

    // ========== WHEN: User imports duplicate file ==========
    await page.goto('/accounting/bank-reconciliation/recon-004')
    await page.locator('[data-testid="import-statement-button"]').click()

    const csvContent = createTestCSVContent(5)
    await page
      .locator('[data-testid="statement-file-input"]')
      .setInputFiles({
        name: 'duplicate.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(csvContent),
      })

    await page.locator('[data-testid="import-next-button"]').click()
    await page.locator('[data-testid="import-submit-button"]').click()

    // ========== THEN: Duplicate warning is displayed ==========
    await expect(page.locator('[data-testid="duplicate-warning"]')).toBeVisible()
    await expect(page.locator('[data-testid="duplicate-warning"]')).toContainText(
      'This file has already been imported',
    )
    await expect(page.locator('[data-testid="duplicate-reconciliation-link"]')).toHaveAttribute(
      'href',
      '/accounting/bank-reconciliation/recon-duplicate-001',
    )
  })

  test('E2E-IMPORT-005: Save column mapping as reusable profile', async ({ page }) => {
    // ========== GIVEN: Reconciliation exists ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-005' })

    await page.route('**/api/v1/bank-reconciliations/recon-005*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    await page.route('**/api/v1/bank-reconciliations/recon-005/import/analyze*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            suggestions: { dateColumn: 'TXN_DATE', descriptionColumn: 'MEMO' },
            headers: ['TXN_DATE', 'MEMO', 'DR', 'CR'],
            sampleRows: [],
            savedFormat: null, // No saved format
          },
        }),
      })
    })

    const importResult = createStatementImportResult({ reconciliationId: 'recon-005' })

    // Mock: Import with save profile option
    await page.route('**/api/v1/bank-reconciliations/recon-005/import*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: importResult }),
      })
    })

    // ========== WHEN: User goes through import and enables "Save as profile" ==========
    await page.goto('/accounting/bank-reconciliation/recon-005')
    await page.locator('[data-testid="import-statement-button"]').click()

    const csvContent = createTestCSVContent(5)
    await page
      .locator('[data-testid="statement-file-input"]')
      .setInputFiles({
        name: 'custom-bank.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(csvContent),
      })

    // Step 2: Map columns manually and check "Save as profile"
    await page.locator('[data-testid="date-column-select"]').selectOption('TXN_DATE')
    await page.locator('[data-testid="description-column-select"]').selectOption('MEMO')

    const saveProfileCheckbox = page.locator('[data-testid="save-profile-checkbox"]')
    await saveProfileCheckbox.check()

    await page.locator('[data-testid="import-next-button"]').click()

    // Step 3: Preview - submit import
    await page.locator('[data-testid="import-submit-button"]').click()

    // ========== THEN: Success message indicates profile was saved ==========
    await expect(page.locator('[data-testid="import-success-message"]')).toContainText(
      'Column mapping saved',
    )
  })

  test('E2E-IMPORT-006: File size validation rejects files over 10MB', async ({ page }) => {
    // ========== GIVEN: Reconciliation exists ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-006' })

    await page.route('**/api/v1/bank-reconciliations/recon-006*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // ========== WHEN: User tries to upload file >10MB ==========
    await page.goto('/accounting/bank-reconciliation/recon-006')
    await page.locator('[data-testid="import-statement-button"]').click()

    // Create large CSV content (>10MB)
    const largeCSV = 'A'.repeat(11 * 1024 * 1024) // 11MB
    await page
      .locator('[data-testid="statement-file-input"]')
      .setInputFiles({
        name: 'large.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(largeCSV),
      })

    // ========== THEN: Validation error is displayed ==========
    await expect(page.locator('[data-testid="file-size-error"]')).toBeVisible()
    await expect(page.locator('[data-testid="file-size-error"]')).toContainText(
      'File size exceeds 10MB',
    )
  })
})
