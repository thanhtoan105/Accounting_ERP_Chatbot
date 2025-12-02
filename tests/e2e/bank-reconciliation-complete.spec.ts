import { test, expect } from '../support/fixtures'
import { loginAsUser } from '../support/helpers/auth-helper'
import {
  createBankReconciliation,
  createStatementLine,
  createPostedAdjustment,
} from '../support/factories/bank-reconciliation.factory'

/**
 * Epic 6 - Story 6.5: Bank Reconciliation - Complete Flow E2E Tests
 *
 * Critical user journeys:
 * - Complete reconciliation when all lines matched
 * - Balance validation before completion
 * - Reopen completed reconciliation
 * - Export reconciliation report (Excel/PDF)
 * - View reconciliation summary dashboard
 */

test.describe('Story 6.5: Bank Reconciliation - Complete Flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'accountant@example.com', 'password', 'accountant')
  })

  test('E2E-COMPLETE-001: Complete reconciliation when all lines matched', async ({ page }) => {
    // Login as Chief Accountant (required for completion)
    await loginAsUser(page, 'chief@example.com', 'password', 'chief_accountant')

    // ========== GIVEN: Reconciliation with all lines matched ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-complete-001',
      status: 'IN_PROGRESS',
      totalLines: 10,
      matchedLines: 10,
      unmatchedLines: 0,
      statementBalance: 500000000,
      ledgerBalance: 500000000,
      difference: 0,
    })

    await page.route('**/api/v1/bank-reconciliations/recon-complete-001*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // Network-first: Intercept complete BEFORE action
    const completePromise = page.waitForResponse(
      (resp) => resp.url().includes('/complete') && resp.status() === 200,
    )

    // Mock: Complete response
    await page.route('**/api/v1/bank-reconciliations/recon-complete-001/complete*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            status: 'COMPLETED',
            completedAt: new Date().toISOString(),
            completedById: 2,
            completedByName: 'Chief Accountant',
          },
        }),
      })
    })

    // ========== WHEN: User navigates to reconciliation detail ==========
    await page.goto('/accounting/bank-reconciliation/recon-complete-001')

    // ========== THEN: Summary shows all lines matched ==========
    await expect(page.locator('[data-testid="matched-lines-count"]')).toContainText('10')
    await expect(page.locator('[data-testid="unmatched-lines-count"]')).toContainText('0')
    await expect(page.locator('[data-testid="balance-difference"]')).toContainText('0')

    // ========== WHEN: User clicks Complete Reconciliation ==========
    const completeButton = page.locator('[data-testid="complete-reconciliation-button"]')
    await completeButton.waitFor({ state: 'visible', timeout: 10000 })
    await expect(completeButton).toBeEnabled() // Button should be enabled when all matched

    await completeButton.click()

    // Confirm completion in dialog
    const confirmButton = page.locator('[data-testid="confirm-complete-button"]')
    await confirmButton.click()

    // Wait for completion response
    await completePromise

    // ========== THEN: Status updates to COMPLETED ==========
    await expect(page.locator('[data-testid="reconciliation-status-badge"]')).toContainText(
      'Completed',
    )

    // ========== THEN: Completion metadata is displayed ==========
    await expect(page.locator('[data-testid="completed-by-name"]')).toContainText(
      'Chief Accountant',
    )
    await expect(page.locator('[data-testid="completed-at"]')).toBeVisible()

    // ========== THEN: Complete button is replaced with Reopen button ==========
    await expect(page.locator('[data-testid="complete-reconciliation-button"]')).not.toBeVisible()
    await expect(page.locator('[data-testid="reopen-reconciliation-button"]')).toBeVisible()
  })

  test('E2E-COMPLETE-002: Complete button disabled when unmatched lines exist', async ({ page }) => {
    // ========== GIVEN: Reconciliation with unmatched lines ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-complete-002',
      status: 'IN_PROGRESS',
      totalLines: 10,
      matchedLines: 8,
      unmatchedLines: 2,
    })

    await page.route('**/api/v1/bank-reconciliations/recon-complete-002*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // ========== WHEN: User views reconciliation with unmatched lines ==========
    await page.goto('/accounting/bank-reconciliation/recon-complete-002')

    // ========== THEN: Complete button is disabled ==========
    const completeButton = page.locator('[data-testid="complete-reconciliation-button"]')
    await expect(completeButton).toBeDisabled()

    // ========== THEN: Tooltip explains why completion is blocked ==========
    await completeButton.hover()
    await expect(page.locator('[data-testid="complete-disabled-tooltip"]')).toContainText(
      '2 unmatched lines',
    )
  })

  test('E2E-COMPLETE-003: Balance validation fails when balances do not reconcile', async ({
    page,
  }) => {
    // Login as Chief Accountant
    await loginAsUser(page, 'chief@example.com', 'password', 'chief_accountant')

    // ========== GIVEN: All lines matched but balances differ ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-complete-003',
      status: 'IN_PROGRESS',
      totalLines: 10,
      matchedLines: 10,
      unmatchedLines: 0,
      statementBalance: 500000000,
      ledgerBalance: 498000000, // 2M difference
      difference: 2000000,
    })

    await page.route('**/api/v1/bank-reconciliations/recon-complete-003*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // Mock: Complete fails with validation error
    await page.route('**/api/v1/bank-reconciliations/recon-complete-003/complete*', async (route) => {
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({
          error: {
            code: 'BALANCE_MISMATCH',
            message:
              'Cannot complete: Statement balance (500,000,000) does not match ledger balance (498,000,000). Difference: 2,000,000 VND',
          },
        }),
      })
    })

    // ========== WHEN: User tries to complete reconciliation ==========
    await page.goto('/accounting/bank-reconciliation/recon-complete-003')

    const completeButton = page.locator('[data-testid="complete-reconciliation-button"]')
    await completeButton.click()

    const confirmButton = page.locator('[data-testid="confirm-complete-button"]')
    await confirmButton.click()

    // ========== THEN: Error toast shows balance mismatch ==========
    await expect(page.locator('[data-testid="complete-error-toast"]')).toBeVisible()
    await expect(page.locator('[data-testid="complete-error-toast"]')).toContainText(
      'Balance mismatch',
    )
    await expect(page.locator('[data-testid="complete-error-toast"]')).toContainText('2,000,000')

    // ========== THEN: Status remains IN_PROGRESS ==========
    await expect(page.locator('[data-testid="reconciliation-status-badge"]')).toContainText(
      'In Progress',
    )
  })

  test('E2E-COMPLETE-004: Reopen completed reconciliation', async ({ page }) => {
    // Login as Chief Accountant
    await loginAsUser(page, 'chief@example.com', 'password', 'chief_accountant')

    // ========== GIVEN: Completed reconciliation ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-complete-004',
      status: 'COMPLETED',
      completedAt: '2025-01-31T15:00:00Z',
      completedById: 2,
      completedByName: 'Chief Accountant',
    })

    await page.route('**/api/v1/bank-reconciliations/recon-complete-004*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // Network-first: Intercept reopen BEFORE action
    const reopenPromise = page.waitForResponse(
      (resp) => resp.url().includes('/reopen') && resp.status() === 200,
    )

    // Mock: Reopen response
    await page.route('**/api/v1/bank-reconciliations/recon-complete-004/reopen*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            status: 'IN_PROGRESS',
            completedAt: null,
            completedById: null,
            completedByName: null,
          },
        }),
      })
    })

    // ========== WHEN: User views completed reconciliation ==========
    await page.goto('/accounting/bank-reconciliation/recon-complete-004')

    await expect(page.locator('[data-testid="reconciliation-status-badge"]')).toContainText(
      'Completed',
    )

    // ========== WHEN: User clicks Reopen ==========
    const reopenButton = page.locator('[data-testid="reopen-reconciliation-button"]')
    await reopenButton.click()

    // Confirm reopen
    const confirmButton = page.locator('[data-testid="confirm-reopen-button"]')
    await confirmButton.click()

    // Wait for reopen response
    await reopenPromise

    // ========== THEN: Status reverts to IN_PROGRESS ==========
    await expect(page.locator('[data-testid="reconciliation-status-badge"]')).toContainText(
      'In Progress',
    )

    // ========== THEN: Completion metadata is cleared ==========
    await expect(page.locator('[data-testid="completed-by-name"]')).not.toBeVisible()
  })

  test('E2E-COMPLETE-005: Export reconciliation as Excel', async ({ page }) => {
    // ========== GIVEN: Completed reconciliation ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-complete-005',
      status: 'COMPLETED',
    })

    await page.route('**/api/v1/bank-reconciliations/recon-complete-005*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // Network-first: Intercept export BEFORE action
    const exportPromise = page.waitForResponse(
      (resp) => resp.url().includes('/export/excel') && resp.status() === 200,
    )

    // Mock: Excel export response
    await page.route('**/api/v1/bank-reconciliations/recon-complete-005/export/excel*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType:
          'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        body: Buffer.from('EXCEL_CONTENT'), // Mock Excel binary
        headers: {
          'Content-Disposition':
            'attachment; filename="Reconciliation_recon-complete-005_2025-01-31.xlsx"',
        },
      })
    })

    // ========== WHEN: User clicks Export Excel ==========
    await page.goto('/accounting/bank-reconciliation/recon-complete-005')

    const exportExcelButton = page.locator('[data-testid="export-excel-button"]')
    await exportExcelButton.click()

    // Wait for export response
    await exportPromise

    // ========== THEN: File download is triggered ==========
    // Playwright handles download automatically
    // In real test, we would verify download event
  })

  test('E2E-COMPLETE-006: Export reconciliation as PDF', async ({ page }) => {
    // ========== GIVEN: Completed reconciliation ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-complete-006',
      status: 'COMPLETED',
    })

    await page.route('**/api/v1/bank-reconciliations/recon-complete-006*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // Mock: PDF export response
    await page.route('**/api/v1/bank-reconciliations/recon-complete-006/export/pdf*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('PDF_CONTENT'),
        headers: {
          'Content-Disposition':
            'attachment; filename="Reconciliation_recon-complete-006_2025-01-31.pdf"',
        },
      })
    })

    // ========== WHEN: User clicks Export PDF ==========
    await page.goto('/accounting/bank-reconciliation/recon-complete-006')

    const exportPdfButton = page.locator('[data-testid="export-pdf-button"]')
    await exportPdfButton.click()

    // Wait for response (PDF export may be placeholder)
    await page.waitForResponse(
      (resp) => resp.url().includes('/export/pdf') && resp.status() === 200,
    )
  })

  test('E2E-COMPLETE-007: View reconciliation summary dashboard', async ({ page }) => {
    // ========== GIVEN: Reconciliation with mixed status lines ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-complete-007',
      totalLines: 20,
      matchedLines: 15,
      unmatchedLines: 3,
      adjustmentRequiredLines: 2,
      matchedAmount: 750000000,
      unmatchedAmount: 50000000,
      statementBalance: 800000000,
      ledgerBalance: 750000000,
      difference: 50000000,
    })

    await page.route('**/api/v1/bank-reconciliations/recon-complete-007*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // ========== WHEN: User views reconciliation detail ==========
    await page.goto('/accounting/bank-reconciliation/recon-complete-007')

    // ========== THEN: Summary cards display key metrics ==========

    // Statement Balance Card
    const statementBalanceCard = page.locator('[data-testid="statement-balance-card"]')
    await expect(statementBalanceCard).toContainText('800,000,000')

    // Ledger Balance Card
    const ledgerBalanceCard = page.locator('[data-testid="ledger-balance-card"]')
    await expect(ledgerBalanceCard).toContainText('750,000,000')

    // Matched Total Card
    const matchedTotalCard = page.locator('[data-testid="matched-total-card"]')
    await expect(matchedTotalCard).toContainText('750,000,000')
    await expect(matchedTotalCard).toContainText('15') // Count

    // Unmatched Total Card
    const unmatchedTotalCard = page.locator('[data-testid="unmatched-total-card"]')
    await expect(unmatchedTotalCard).toContainText('50,000,000')
    await expect(unmatchedTotalCard).toContainText('3') // Count

    // Difference Card (highlighted if non-zero)
    const differenceCard = page.locator('[data-testid="difference-card"]')
    await expect(differenceCard).toContainText('50,000,000')
    await expect(differenceCard).toHaveClass(/border-destructive/) // Red border for difference
  })

  test('E2E-COMPLETE-008: Reconciliation list page filters and pagination', async ({ page }) => {
    // ========== GIVEN: Multiple reconciliations exist ==========
    const reconciliations = [
      createBankReconciliation({ id: 'recon-1', status: 'NOT_STARTED' }),
      createBankReconciliation({ id: 'recon-2', status: 'IN_PROGRESS' }),
      createBankReconciliation({ id: 'recon-3', status: 'COMPLETED' }),
    ]

    await page.route('**/api/v1/bank-reconciliations*', async (route) => {
      const url = new URL(route.request().url())
      const status = url.searchParams.get('status')

      let filteredReconciliations = reconciliations

      if (status) {
        filteredReconciliations = reconciliations.filter((r) => r.status === status)
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: filteredReconciliations.map((r) => ({
            id: r.id,
            bankAccountNumber: r.bankAccountNumber,
            bankName: r.bankName,
            statementPeriodStart: r.statementPeriodStart,
            statementPeriodEnd: r.statementPeriodEnd,
            status: r.status,
            totalLines: r.totalLines,
            matchedLines: r.matchedLines,
            unmatchedLines: r.unmatchedLines,
            updatedAt: r.updatedAt,
          })),
          meta: {
            total: filteredReconciliations.length,
            page: 0,
            size: 20,
          },
        }),
      })
    })

    // ========== WHEN: User navigates to reconciliation list ==========
    await page.goto('/accounting/bank-reconciliation')

    // ========== THEN: All reconciliations are displayed ==========
    const reconciliationRows = page.locator('[data-testid="reconciliation-row"]')
    await expect(reconciliationRows).toHaveCount(3)

    // ========== WHEN: User filters by status ==========
    const statusFilter = page.locator('[data-testid="status-filter-select"]')
    await statusFilter.selectOption('COMPLETED')

    // ========== THEN: Only completed reconciliations are shown ==========
    await expect(reconciliationRows).toHaveCount(1)
    await expect(reconciliationRows.first()).toContainText('Completed')
  })

  test('E2E-COMPLETE-009: Completed reconciliation updates bank account last reconciled date', async ({
    page,
  }) => {
    // Login as Chief Accountant
    await loginAsUser(page, 'chief@example.com', 'password', 'chief_accountant')

    // ========== GIVEN: Reconciliation ready to complete ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-complete-009',
      status: 'IN_PROGRESS',
      totalLines: 5,
      matchedLines: 5,
      unmatchedLines: 0,
      statementPeriodEnd: '2025-01-31',
    })

    await page.route('**/api/v1/bank-reconciliations/recon-complete-009*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // Mock: Complete response
    await page.route('**/api/v1/bank-reconciliations/recon-complete-009/complete*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            status: 'COMPLETED',
            completedAt: new Date().toISOString(),
          },
        }),
      })
    })

    // ========== WHEN: User completes reconciliation ==========
    await page.goto('/accounting/bank-reconciliation/recon-complete-009')

    const completeButton = page.locator('[data-testid="complete-reconciliation-button"]')
    await completeButton.click()

    const confirmButton = page.locator('[data-testid="confirm-complete-button"]')
    await confirmButton.click()

    // ========== THEN: Success message mentions bank account update ==========
    await expect(page.locator('[data-testid="complete-success-toast"]')).toContainText(
      'Bank account last reconciled date updated',
    )
  })
})
