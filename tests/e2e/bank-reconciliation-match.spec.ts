import { test, expect } from '../support/fixtures'
import { loginAsUser } from '../support/helpers/auth-helper'
import {
  createBankReconciliation,
  createStatementLine,
  createUnmatchedStatementLine,
  createLedgerTransaction,
  createAutoMatchResult,
} from '../support/factories/bank-reconciliation.factory'

/**
 * Epic 6 - Story 6.5: Bank Reconciliation - Matching Flow E2E Tests
 *
 * Critical user journeys:
 * - Auto-match statement lines with ledger transactions
 * - Review auto-match suggestions with confidence scores
 * - Manual match selection with notes
 * - Unmatch previously matched lines
 * - Handle already-matched vouchers (double-match prevention)
 */

test.describe('Story 6.5: Bank Reconciliation - Matching Flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'accountant@example.com', 'password', 'accountant')
  })

  test('E2E-MATCH-001: Auto-match runs and applies high-confidence matches', async ({ page }) => {
    // ========== GIVEN: Reconciliation with unmatched lines ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-match-001',
      matchedLines: 0,
      unmatchedLines: 10,
    })

    const autoMatchResult = createAutoMatchResult({
      totalProcessed: 10,
      matchedCount: 8,
      unmatchedCount: 2,
      autoAppliedCount: 6,
      suggestedCount: 2,
    })

    await page.route('**/api/v1/bank-reconciliations/recon-match-001*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: testReconciliation }),
      })
    })

    // Network-first: Intercept auto-match BEFORE action
    const autoMatchPromise = page.waitForResponse(
      (resp) => resp.url().includes('/auto-match') && resp.status() === 200,
    )

    // Mock: Auto-match response
    await page.route('**/api/v1/bank-reconciliations/recon-match-001/auto-match*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: autoMatchResult }),
      })
    })

    // Mock: Updated reconciliation after auto-match
    await page.route('**/api/v1/bank-reconciliations/recon-match-001$', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            matchedLines: 8,
            unmatchedLines: 2,
          },
        }),
      })
    })

    // ========== WHEN: User navigates to reconciliation detail ==========
    await page.goto('/accounting/bank-reconciliation/recon-match-001', { waitUntil: 'networkidle' })

    // ========== WHEN: User clicks Auto Match button ==========
    const autoMatchButton = page.locator('[data-testid="auto-match-button"]')
    await autoMatchButton.waitFor({ state: 'visible', timeout: 10000 })
    await autoMatchButton.click()

    // Wait for auto-match response
    await autoMatchPromise

    // ========== THEN: Success toast shows match statistics ==========
    await expect(page.locator('[data-testid="auto-match-success-toast"]')).toBeVisible()
    await expect(page.locator('[data-testid="auto-match-success-toast"]')).toContainText(
      '8 matches found',
    )
    await expect(page.locator('[data-testid="auto-match-success-toast"]')).toContainText(
      '6 auto-applied',
    )

    // ========== THEN: Summary cards update to show matched lines ==========
    await expect(page.locator('[data-testid="matched-lines-count"]')).toContainText('8')
    await expect(page.locator('[data-testid="unmatched-lines-count"]')).toContainText('2')
  })

  test('E2E-MATCH-002: Review suggested matches with confidence scores', async ({ page }) => {
    // ========== GIVEN: Reconciliation with suggested matches ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-match-002' })

    const suggestedLine = createStatementLine({
      id: 'line-suggested-001',
      matchStatus: 'UNMATCHED',
      matchConfidence: 0.72,
      matchReason: 'Match: exact amount, date ±1 day, similar reference',
    })

    await page.route('**/api/v1/bank-reconciliations/recon-match-002*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            statementLines: [suggestedLine],
          },
        }),
      })
    })

    // ========== WHEN: User views reconciliation detail ==========
    await page.goto('/accounting/bank-reconciliation/recon-match-002')

    // ========== THEN: Suggested match row shows confidence badge ==========
    const suggestedRow = page.locator('[data-testid="statement-line-line-suggested-001"]')
    await expect(suggestedRow).toBeVisible()

    // Confidence score displayed with color coding
    const confidenceBadge = suggestedRow.locator('[data-testid="match-confidence-badge"]')
    await expect(confidenceBadge).toBeVisible()
    await expect(confidenceBadge).toContainText('72%')
    await expect(confidenceBadge).toHaveClass(/warning/) // Medium confidence = warning color

    // Match reason tooltip/hover text
    const matchReason = suggestedRow.locator('[data-testid="match-reason"]')
    await expect(matchReason).toContainText('exact amount, date ±1 day')

    // ========== THEN: Accept/Reject buttons are visible ==========
    await expect(suggestedRow.locator('[data-testid="accept-match-button"]')).toBeVisible()
    await expect(suggestedRow.locator('[data-testid="reject-match-button"]')).toBeVisible()
  })

  test('E2E-MATCH-003: Manual match - select ledger transaction and match', async ({ page }) => {
    // ========== GIVEN: Unmatched statement line and ledger transactions ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-match-003' })
    const unmatchedLine = createUnmatchedStatementLine({
      id: 'line-unmatched-003',
      description: 'Customer Payment',
      creditAmount: 50000000,
    })

    const ledgerTransactions = [
      createLedgerTransaction({
        voucherId: 'voucher-003',
        voucherNumber: 'RC-2025-003',
        description: 'Customer Payment - INV-2025-003',
        debitAmount: 50000000,
        isMatched: false,
      }),
      createLedgerTransaction({
        voucherId: 'voucher-004',
        voucherNumber: 'RC-2025-004',
        description: 'Another Payment',
        debitAmount: 30000000,
        isMatched: false,
      }),
    ]

    await page.route('**/api/v1/bank-reconciliations/recon-match-003*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            statementLines: [unmatchedLine],
          },
        }),
      })
    })

    // Mock: Ledger transactions endpoint
    await page.route('**/api/v1/bank-reconciliations/recon-match-003/ledger-transactions*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: ledgerTransactions,
        }),
      })
    })

    // Network-first: Intercept manual match BEFORE action
    const matchPromise = page.waitForResponse(
      (resp) => resp.url().includes('/match') && !resp.url().includes('/auto-match') && resp.status() === 200,
    )

    // Mock: Manual match response
    await page.route('**/api/v1/bank-reconciliations/recon-match-003/match*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...unmatchedLine,
            matchStatus: 'MATCHED',
            matchedVoucherId: 'voucher-003',
            matchedVoucherNumber: 'RC-2025-003',
            matchConfidence: 1.0,
            matchReason: 'Manual match by user',
          },
        }),
      })
    })

    // ========== WHEN: User views reconciliation detail ==========
    await page.goto('/accounting/bank-reconciliation/recon-match-003')

    // ========== WHEN: User selects unmatched line ==========
    const unmatchedRow = page.locator('[data-testid="statement-line-line-unmatched-003"]')
    await unmatchedRow.click()

    // ========== THEN: Ledger transactions panel shows available transactions ==========
    await expect(page.locator('[data-testid="ledger-transactions-panel"]')).toBeVisible()
    const ledgerRows = page.locator('[data-testid="ledger-transaction-row"]')
    await expect(ledgerRows).toHaveCount(2)

    // ========== WHEN: User selects matching ledger transaction ==========
    const matchingTransaction = page.locator('[data-testid="ledger-transaction-voucher-003"]')
    await matchingTransaction.click()

    // ========== WHEN: User clicks Match button ==========
    const matchButton = page.locator('[data-testid="manual-match-button"]')
    await matchButton.click()

    // Wait for match response
    await matchPromise

    // ========== THEN: Line is marked as matched ==========
    await expect(unmatchedRow.locator('[data-testid="match-status-badge"]')).toContainText('Matched')
    await expect(unmatchedRow.locator('[data-testid="matched-voucher-number"]')).toContainText(
      'RC-2025-003',
    )

    // ========== THEN: Success toast confirms match ==========
    await expect(page.locator('[data-testid="match-success-toast"]')).toContainText(
      'Successfully matched',
    )
  })

  test('E2E-MATCH-004: Manual match with notes field', async ({ page }) => {
    // ========== GIVEN: Unmatched line ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-match-004' })
    const unmatchedLine = createUnmatchedStatementLine({ id: 'line-004' })

    await page.route('**/api/v1/bank-reconciliations/recon-match-004*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            statementLines: [unmatchedLine],
          },
        }),
      })
    })

    await page.route('**/api/v1/bank-reconciliations/recon-match-004/ledger-transactions*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [createLedgerTransaction({ voucherId: 'voucher-004' })],
        }),
      })
    })

    // Mock: Match with notes
    let capturedNotes: string | undefined
    await page.route('**/api/v1/bank-reconciliations/recon-match-004/match*', async (route) => {
      const postData = route.request().postDataJSON()
      capturedNotes = postData?.notes

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...unmatchedLine,
            matchStatus: 'MATCHED',
            notes: postData?.notes,
          },
        }),
      })
    })

    // ========== WHEN: User performs manual match with notes ==========
    await page.goto('/accounting/bank-reconciliation/recon-match-004')

    await page.locator('[data-testid="statement-line-line-004"]').click()
    await page.locator('[data-testid="ledger-transaction-voucher-004"]').click()

    // Enter notes in match dialog
    const notesInput = page.locator('[data-testid="match-notes-input"]')
    await notesInput.fill('Matched after verifying with customer email')

    await page.locator('[data-testid="manual-match-button"]').click()

    // ========== THEN: Notes are saved with the match ==========
    await page.waitForTimeout(500) // Wait for request to complete
    expect(capturedNotes).toBe('Matched after verifying with customer email')
  })

  test('E2E-MATCH-005: Unmatch previously matched line', async ({ page }) => {
    // ========== GIVEN: Matched statement line ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-match-005' })
    const matchedLine = createStatementLine({
      id: 'line-matched-005',
      matchStatus: 'MATCHED',
      matchedVoucherId: 'voucher-005',
      matchedVoucherNumber: 'RC-2025-005',
    })

    await page.route('**/api/v1/bank-reconciliations/recon-match-005*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            statementLines: [matchedLine],
          },
        }),
      })
    })

    // Network-first: Intercept unmatch BEFORE action
    const unmatchPromise = page.waitForResponse(
      (resp) => resp.url().includes('/unmatch') && resp.status() === 200,
    )

    // Mock: Unmatch response
    await page.route('**/api/v1/bank-reconciliations/recon-match-005/lines/line-matched-005/unmatch*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...matchedLine,
            matchStatus: 'UNMATCHED',
            matchedVoucherId: null,
            matchedVoucherNumber: null,
            matchConfidence: null,
            matchReason: null,
          },
        }),
      })
    })

    // ========== WHEN: User views matched line ==========
    await page.goto('/accounting/bank-reconciliation/recon-match-005')

    const matchedRow = page.locator('[data-testid="statement-line-line-matched-005"]')
    await expect(matchedRow.locator('[data-testid="match-status-badge"]')).toContainText('Matched')

    // ========== WHEN: User clicks Unmatch button ==========
    const unmatchButton = matchedRow.locator('[data-testid="unmatch-button"]')
    await unmatchButton.click()

    // Confirm unmatch in dialog
    const confirmButton = page.locator('[data-testid="confirm-unmatch-button"]')
    await confirmButton.click()

    // Wait for unmatch response
    await unmatchPromise

    // ========== THEN: Line is reverted to unmatched status ==========
    await expect(matchedRow.locator('[data-testid="match-status-badge"]')).toContainText('Unmatched')
    await expect(matchedRow.locator('[data-testid="matched-voucher-number"]')).not.toBeVisible()
  })

  test('E2E-MATCH-006: Prevent double-matching (voucher already matched)', async ({ page }) => {
    // ========== GIVEN: Two unmatched lines, one voucher ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-match-006' })
    const line1 = createUnmatchedStatementLine({ id: 'line-006-1', lineNumber: 1 })
    const line2 = createUnmatchedStatementLine({ id: 'line-006-2', lineNumber: 2 })

    await page.route('**/api/v1/bank-reconciliations/recon-match-006*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            statementLines: [line1, line2],
          },
        }),
      })
    })

    const ledgerTransaction = createLedgerTransaction({
      voucherId: 'voucher-006',
      voucherNumber: 'RC-2025-006',
    })

    await page.route('**/api/v1/bank-reconciliations/recon-match-006/ledger-transactions*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: [ledgerTransaction],
        }),
      })
    })

    // Mock: First match succeeds
    await page.route('**/api/v1/bank-reconciliations/recon-match-006/match*', async (route) => {
      const postData = route.request().postDataJSON()

      // First match succeeds
      if (postData.lineId === 'line-006-1') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              ...line1,
              matchStatus: 'MATCHED',
              matchedVoucherId: 'voucher-006',
            },
          }),
        })
      }
      // Second match fails (voucher already matched)
      else if (postData.lineId === 'line-006-2') {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            error: {
              code: 'VOUCHER_ALREADY_MATCHED',
              message: 'Voucher RC-2025-006 is already matched to another statement line',
            },
          }),
        })
      }
    })

    // ========== WHEN: User matches first line successfully ==========
    await page.goto('/accounting/bank-reconciliation/recon-match-006')

    await page.locator('[data-testid="statement-line-line-006-1"]').click()
    await page.locator('[data-testid="ledger-transaction-voucher-006"]').click()
    await page.locator('[data-testid="manual-match-button"]').click()

    await page.waitForSelector('[data-testid="match-success-toast"]')

    // ========== WHEN: User tries to match second line to same voucher ==========
    await page.locator('[data-testid="statement-line-line-006-2"]').click()
    await page.locator('[data-testid="ledger-transaction-voucher-006"]').click()
    await page.locator('[data-testid="manual-match-button"]').click()

    // ========== THEN: Error toast shows double-match prevention message ==========
    await expect(page.locator('[data-testid="match-error-toast"]')).toBeVisible()
    await expect(page.locator('[data-testid="match-error-toast"]')).toContainText(
      'already matched',
    )
  })

  test('E2E-MATCH-007: Filter ledger transactions by date range', async ({ page }) => {
    // ========== GIVEN: Ledger transactions across multiple months ==========
    const testReconciliation = createBankReconciliation({
      id: 'recon-match-007',
      statementPeriodStart: '2025-01-01',
      statementPeriodEnd: '2025-01-31',
    })

    const ledgerTransactions = [
      createLedgerTransaction({ voucherId: 'v1', transactionDate: '2025-01-15' }), // In period
      createLedgerTransaction({ voucherId: 'v2', transactionDate: '2024-12-20' }), // Before period
      createLedgerTransaction({ voucherId: 'v3', transactionDate: '2025-02-05' }), // After period
    ]

    await page.route('**/api/v1/bank-reconciliations/recon-match-007*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            statementLines: [createUnmatchedStatementLine()],
          },
        }),
      })
    })

    await page.route('**/api/v1/bank-reconciliations/recon-match-007/ledger-transactions*', async (route) => {
      // Filter by date range query param
      const url = new URL(route.request().url())
      const dateFrom = url.searchParams.get('dateFrom')
      const dateTo = url.searchParams.get('dateTo')

      let filteredTransactions = ledgerTransactions

      if (dateFrom && dateTo) {
        filteredTransactions = ledgerTransactions.filter(
          (t) => t.transactionDate >= dateFrom && t.transactionDate <= dateTo,
        )
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: filteredTransactions,
        }),
      })
    })

    // ========== WHEN: User views ledger transactions ==========
    await page.goto('/accounting/bank-reconciliation/recon-match-007')

    // ========== THEN: Only transactions within period are shown by default ==========
    const ledgerRows = page.locator('[data-testid="ledger-transaction-row"]')
    await expect(ledgerRows).toHaveCount(1) // Only v1 in period
  })
})
