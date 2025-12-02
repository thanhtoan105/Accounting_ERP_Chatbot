import { test, expect } from '../support/fixtures'
import { setupMockAuth } from '../support/helpers/auth-helper'
import {
  createBankReconciliation,
  createUnmatchedStatementLine,
  createReconciliationAdjustment,
  createApprovedAdjustment,
  createPostedAdjustment,
} from '../support/factories/bank-reconciliation.factory'

/**
 * Epic 6 - Story 6.5: Bank Reconciliation - Adjustment Flow E2E Tests
 *
 * Critical user journeys:
 * - Create adjustment for unmatched bank fees/interest
 * - Pre-fill adjustment from statement line
 * - Approve/reject adjustments (Chief Accountant role)
 * - Post adjustment to create voucher
 * - View adjustment approval workflow status
 */

test.describe('Story 6.5: Bank Reconciliation - Adjustment Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Use mock auth for fully mocked E2E tests (no backend required)
    await setupMockAuth(page, 'accountant@example.com', 'accountant')
  })

  test('E2E-ADJ-001: Create adjustment from unmatched line with pre-fill', async ({ page }) => {
    // ========== GIVEN: Unmatched line (bank fee) ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-adj-001' })
    const unmatchedLine = createUnmatchedStatementLine({
      id: 'line-fee-001',
      description: 'Bank Service Fee - Monthly Maintenance',
      debitAmount: 200000,
      creditAmount: undefined,
    })

    // Mock: Create adjustment response
    const newAdjustment = createReconciliationAdjustment({
      statementLineId: 'line-fee-001',
      adjustmentType: 'BANK_FEE',
      amount: 200000,
      description: 'Bank Service Fee - Monthly Maintenance',
    })

    // Setup ALL route mocks BEFORE navigation
    await page.route('**/api/v1/bank-reconciliations/recon-adj-001*', async (route) => {
      // Handle both GET (page load) and other requests
      if (route.request().url().includes('/adjustments')) {
        // Let the more specific route handler below handle this
        await route.fallback()
        return
      }
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

    await page.route('**/api/v1/bank-reconciliations/recon-adj-001/adjustments*', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: newAdjustment }),
        })
      } else {
        await route.fallback()
      }
    })

    // ========== WHEN: User opens adjustment dialog for unmatched line ==========
    await page.goto('/accounting/bank-reconciliation/recon-adj-001', { waitUntil: 'networkidle' })

    const unmatchedRow = page.locator('[data-testid="statement-line-line-fee-001"]')
    const createAdjustmentButton = unmatchedRow.locator('[data-testid="create-adjustment-button"]')
    await createAdjustmentButton.click()

    // ========== THEN: Adjustment dialog opens with pre-filled values ==========
    await expect(page.locator('[data-testid="adjustment-dialog"]')).toBeVisible()

    // Pre-filled amount from statement line
    const amountInput = page.locator('[data-testid="adjustment-amount-input"]')
    await expect(amountInput).toHaveValue('200000')

    // Pre-filled description
    const descriptionInput = page.locator('[data-testid="adjustment-description-input"]')
    await expect(descriptionInput).toHaveValue(/Bank Service Fee/)

    // Auto-detected adjustment type (BANK_FEE from description keywords)
    const typeSelect = page.locator('[data-testid="adjustment-type-select"]')
    await expect(typeSelect).toHaveValue('BANK_FEE')

    // Default GL account for bank fees
    const accountInput = page.locator('[data-testid="adjustment-account-input"]')
    await expect(accountInput).toHaveValue('6425')

    // ========== WHEN: User submits adjustment ==========
    // Setup waitForResponse JUST BEFORE the click that triggers the API call
    const createAdjustmentPromise = page.waitForResponse(
      (resp) => resp.url().includes('/adjustments') && resp.request().method() === 'POST' && resp.status() === 200,
    )

    const submitButton = page.locator('[data-testid="submit-adjustment-button"]')
    await submitButton.click()

    // Wait for creation response
    await createAdjustmentPromise

    // ========== THEN: Success toast confirms creation ==========
    await expect(page.locator('[data-testid="adjustment-created-toast"]')).toBeVisible()
    await expect(page.locator('[data-testid="adjustment-created-toast"]')).toContainText(
      'Adjustment created',
    )

    // ========== THEN: Line status changes to ADJUSTMENT_REQUIRED ==========
    await expect(unmatchedRow.locator('[data-testid="match-status-badge"]')).toContainText(
      'Adjustment Required',
    )
  })

  test('E2E-ADJ-002: Approve adjustment as Chief Accountant', async ({ page }) => {
    // Login as Chief Accountant (override beforeEach setup)
    await setupMockAuth(page, 'chief@example.com', 'chief_accountant')

    // ========== GIVEN: Pending adjustment exists ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-adj-002' })
    const pendingAdjustment = createReconciliationAdjustment({
      id: 'adj-pending-001',
      status: 'PENDING',
    })

    // Mock: Approve response
    const approvedAdjustment = createApprovedAdjustment({
      id: 'adj-pending-001',
    })

    // Setup ALL route mocks BEFORE navigation
    await page.route('**/api/v1/bank-reconciliations/recon-adj-002*', async (route) => {
      if (route.request().url().includes('/approve')) {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            adjustments: [pendingAdjustment],
          },
        }),
      })
    })

    await page.route('**/api/v1/bank-reconciliations/recon-adj-002/adjustments/adj-pending-001/approve*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: approvedAdjustment }),
      })
    })

    // ========== WHEN: Chief Accountant views adjustment list ==========
    await page.goto('/accounting/bank-reconciliation/recon-adj-002', { waitUntil: 'networkidle' })

    const adjustmentRow = page.locator('[data-testid="adjustment-row-adj-pending-001"]')
    await expect(adjustmentRow.locator('[data-testid="adjustment-status-badge"]')).toContainText(
      'Pending',
    )

    // ========== WHEN: Chief clicks Approve ==========
    // Setup waitForResponse JUST BEFORE the click that triggers the API call
    const approvePromise = page.waitForResponse(
      (resp) => resp.url().includes('/approve') && resp.status() === 200,
    )

    const approveButton = adjustmentRow.locator('[data-testid="approve-adjustment-button"]')
    await approveButton.click()

    // Wait for approval response
    await approvePromise

    // ========== THEN: Status changes to APPROVED ==========
    await expect(adjustmentRow.locator('[data-testid="adjustment-status-badge"]')).toContainText(
      'Approved',
    )

    // ========== THEN: Post button becomes available ==========
    await expect(adjustmentRow.locator('[data-testid="post-adjustment-button"]')).toBeVisible()
  })

  test('E2E-ADJ-003: Reject adjustment with reason', async ({ page }) => {
    // Login as Chief Accountant
    await setupMockAuth(page, 'chief@example.com', 'chief_accountant')

    // ========== GIVEN: Pending adjustment ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-adj-003' })
    const pendingAdjustment = createReconciliationAdjustment({
      id: 'adj-pending-003',
    })

    // Setup ALL route mocks BEFORE navigation
    await page.route('**/api/v1/bank-reconciliations/recon-adj-003*', async (route) => {
      if (route.request().url().includes('/reject')) {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            adjustments: [pendingAdjustment],
          },
        }),
      })
    })

    // Mock: Reject response
    let capturedReason: string | undefined
    await page.route('**/api/v1/bank-reconciliations/recon-adj-003/adjustments/adj-pending-003/reject*', async (route) => {
      const postData = route.request().postDataJSON()
      capturedReason = postData?.reason

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...pendingAdjustment,
            status: 'REJECTED',
            rejectionReason: postData?.reason,
          },
        }),
      })
    })

    // ========== WHEN: Chief clicks Reject ==========
    await page.goto('/accounting/bank-reconciliation/recon-adj-003', { waitUntil: 'networkidle' })

    const adjustmentRow = page.locator('[data-testid="adjustment-row-adj-pending-003"]')
    const rejectButton = adjustmentRow.locator('[data-testid="reject-adjustment-button"]')
    await rejectButton.click()

    // ========== THEN: Rejection dialog prompts for reason ==========
    await expect(page.locator('[data-testid="reject-dialog"]')).toBeVisible()

    const reasonInput = page.locator('[data-testid="rejection-reason-input"]')
    await reasonInput.fill('Incorrect GL account - should be 6426')

    // Setup waitForResponse JUST BEFORE the click that triggers the API call
    const rejectPromise = page.waitForResponse(
      (resp) => resp.url().includes('/reject') && resp.status() === 200,
    )

    const confirmRejectButton = page.locator('[data-testid="confirm-reject-button"]')
    await confirmRejectButton.click()

    // Wait for reject response
    await rejectPromise

    // ========== THEN: Adjustment is rejected with reason ==========
    expect(capturedReason).toBe('Incorrect GL account - should be 6426')

    await expect(adjustmentRow.locator('[data-testid="adjustment-status-badge"]')).toContainText(
      'Rejected',
    )
  })

  test('E2E-ADJ-004: Post adjustment creates voucher and updates line status', async ({ page }) => {
    // Login as Chief Accountant
    await setupMockAuth(page, 'chief@example.com', 'chief_accountant')

    // ========== GIVEN: Approved adjustment ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-adj-004' })
    const approvedAdjustment = createApprovedAdjustment({
      id: 'adj-approved-004',
      statementLineId: 'line-004',
    })

    // Mock: Post response
    const postedAdjustment = createPostedAdjustment({
      id: 'adj-approved-004',
      voucherId: 'voucher-adj-004',
      voucherNumber: 'ADJ-2025-004',
    })

    // Setup ALL route mocks BEFORE navigation
    await page.route('**/api/v1/bank-reconciliations/recon-adj-004*', async (route) => {
      if (route.request().url().includes('/post')) {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            adjustments: [approvedAdjustment],
          },
        }),
      })
    })

    await page.route('**/api/v1/bank-reconciliations/recon-adj-004/adjustments/adj-approved-004/post*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: postedAdjustment }),
      })
    })

    // ========== WHEN: Chief clicks Post ==========
    await page.goto('/accounting/bank-reconciliation/recon-adj-004', { waitUntil: 'networkidle' })

    const adjustmentRow = page.locator('[data-testid="adjustment-row-adj-approved-004"]')
    await expect(adjustmentRow.locator('[data-testid="adjustment-status-badge"]')).toContainText(
      'Approved',
    )

    // Setup waitForResponse JUST BEFORE the click that triggers the API call
    const postPromise = page.waitForResponse(
      (resp) => resp.url().includes('/post') && resp.status() === 200,
    )

    const postButton = adjustmentRow.locator('[data-testid="post-adjustment-button"]')
    await postButton.click()

    // Wait for post response
    await postPromise

    // ========== THEN: Status changes to POSTED ==========
    await expect(adjustmentRow.locator('[data-testid="adjustment-status-badge"]')).toContainText(
      'Posted',
    )

    // ========== THEN: Voucher number is displayed ==========
    await expect(adjustmentRow.locator('[data-testid="adjustment-voucher-number"]')).toContainText(
      'ADJ-2025-004',
    )

    // ========== THEN: Voucher link is clickable ==========
    const voucherLink = adjustmentRow.locator('[data-testid="adjustment-voucher-link"]')
    await expect(voucherLink).toHaveAttribute('href', /voucher-adj-004/)
  })

  test('E2E-ADJ-005: Delete pending adjustment', async ({ page }) => {
    // ========== GIVEN: Pending adjustment ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-adj-005' })
    const pendingAdjustment = createReconciliationAdjustment({
      id: 'adj-pending-005',
    })

    // Setup ALL route mocks BEFORE navigation
    await page.route('**/api/v1/bank-reconciliations/recon-adj-005*', async (route) => {
      if (route.request().url().includes('/adjustments/adj-pending-005') && route.request().method() === 'DELETE') {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            adjustments: [pendingAdjustment],
          },
        }),
      })
    })

    // Mock: Delete response
    await page.route('**/api/v1/bank-reconciliations/recon-adj-005/adjustments/adj-pending-005*', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: { success: true } }),
        })
      } else {
        await route.fallback()
      }
    })

    // ========== WHEN: User clicks Delete ==========
    await page.goto('/accounting/bank-reconciliation/recon-adj-005', { waitUntil: 'networkidle' })

    const adjustmentRow = page.locator('[data-testid="adjustment-row-adj-pending-005"]')
    const deleteButton = adjustmentRow.locator('[data-testid="delete-adjustment-button"]')
    await deleteButton.click()

    // Confirm deletion
    // Setup waitForResponse JUST BEFORE the click that triggers the API call
    const deletePromise = page.waitForResponse(
      (resp) => resp.url().includes('/adjustments/adj-pending-005') && resp.request().method() === 'DELETE' && resp.status() === 200,
    )

    const confirmDeleteButton = page.locator('[data-testid="confirm-delete-adjustment-button"]')
    await confirmDeleteButton.click()

    // Wait for delete response
    await deletePromise

    // ========== THEN: Adjustment is removed from list ==========
    await expect(adjustmentRow).not.toBeVisible()
  })

  test('E2E-ADJ-006: Interest income adjustment with correct GL account', async ({ page }) => {
    // ========== GIVEN: Unmatched line (interest credit) ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-adj-006' })
    const interestLine = createUnmatchedStatementLine({
      id: 'line-interest-006',
      description: 'Interest Income - Savings Account',
      debitAmount: undefined,
      creditAmount: 500000,
    })

    // Setup ALL route mocks BEFORE navigation
    await page.route('**/api/v1/bank-reconciliations/recon-adj-006*', async (route) => {
      if (route.request().url().includes('/adjustments')) {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            statementLines: [interestLine],
          },
        }),
      })
    })

    // Mock: Create adjustment
    await page.route('**/api/v1/bank-reconciliations/recon-adj-006/adjustments*', async (route) => {
      if (route.request().method() === 'POST') {
        const postData = route.request().postDataJSON()
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: createReconciliationAdjustment({
              adjustmentType: postData.adjustmentType,
              accountCode: postData.accountCode,
            }),
          }),
        })
      } else {
        await route.fallback()
      }
    })

    // ========== WHEN: User creates interest income adjustment ==========
    await page.goto('/accounting/bank-reconciliation/recon-adj-006', { waitUntil: 'networkidle' })

    const interestRow = page.locator('[data-testid="statement-line-line-interest-006"]')
    await interestRow.locator('[data-testid="create-adjustment-button"]').click()

    // ========== THEN: Adjustment type auto-detects INTEREST_INCOME ==========
    const typeSelect = page.locator('[data-testid="adjustment-type-select"]')
    await expect(typeSelect).toHaveValue('INTEREST_INCOME')

    // ========== THEN: GL account defaults to 5158 (Interest Income) ==========
    const accountInput = page.locator('[data-testid="adjustment-account-input"]')
    await expect(accountInput).toHaveValue('5158')
  })

  test('E2E-ADJ-007: View adjustment approval workflow status', async ({ page }) => {
    // ========== GIVEN: Adjustments in various statuses ==========
    const testReconciliation = createBankReconciliation({ id: 'recon-adj-007' })

    const adjustments = [
      createReconciliationAdjustment({ id: 'adj-1', status: 'PENDING' }),
      createApprovedAdjustment({ id: 'adj-2', status: 'APPROVED' }),
      createPostedAdjustment({ id: 'adj-3', status: 'POSTED' }),
      createReconciliationAdjustment({
        id: 'adj-4',
        status: 'REJECTED',
        rejectionReason: 'Incorrect amount',
      }),
    ]

    // Setup route mock BEFORE navigation
    await page.route('**/api/v1/bank-reconciliations/recon-adj-007*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            ...testReconciliation,
            adjustments,
          },
        }),
      })
    })

    // ========== WHEN: User views adjustments list ==========
    await page.goto('/accounting/bank-reconciliation/recon-adj-007', { waitUntil: 'networkidle' })

    // ========== THEN: Each adjustment shows color-coded status badge ==========
    const pendingBadge = page.locator('[data-testid="adjustment-row-adj-1"] [data-testid="adjustment-status-badge"]')
    await expect(pendingBadge).toHaveClass(/warning/) // Yellow for pending

    const approvedBadge = page.locator('[data-testid="adjustment-row-adj-2"] [data-testid="adjustment-status-badge"]')
    await expect(approvedBadge).toHaveClass(/info/) // Blue for approved

    const postedBadge = page.locator('[data-testid="adjustment-row-adj-3"] [data-testid="adjustment-status-badge"]')
    await expect(postedBadge).toHaveClass(/success/) // Green for posted

    const rejectedBadge = page.locator('[data-testid="adjustment-row-adj-4"] [data-testid="adjustment-status-badge"]')
    await expect(rejectedBadge).toHaveClass(/destructive/) // Red for rejected

    // ========== THEN: Rejected adjustment shows reason ==========
    const rejectedRow = page.locator('[data-testid="adjustment-row-adj-4"]')
    await expect(rejectedRow.locator('[data-testid="rejection-reason"]')).toContainText(
      'Incorrect amount',
    )
  })
})
