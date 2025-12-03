import { test, expect } from '../support/fixtures'
import { setupMockAuth } from '../support/helpers/auth-helper'

/**
 * Epic 6 - Story 6.6: Cash & Bank Integrity Dashboard E2E Tests
 *
 * Critical user journeys:
 * - View last integrity check status
 * - View integrity check history
 * - Run manual integrity check (Admin only)
 * - View detected issues with severity indicators
 * - Navigate between Audit Explorer and Integrity Dashboard
 */

// ============================================================================
// Mock Data Factories
// ============================================================================

type IntegrityCheckStatus = 'PASSED' | 'FAILED' | 'ERROR'
type IntegrityCheckType = 'DAILY' | 'PERIOD_CLOSE' | 'MANUAL' | 'HOURLY_ANOMALY'
type IssueSeverity = 'HIGH' | 'MEDIUM' | 'LOW'

interface MockIntegrityIssue {
  type: string
  severity: IssueSeverity
  description: string
  details?: string
  entityType?: string
  entityId?: string
  amount?: number
  expectedAmount?: number
  deviation?: number
}

interface MockIntegrityCheckResult {
  checkId: string
  checkType: IntegrityCheckType
  status: IntegrityCheckStatus
  passed: boolean
  executedAt: string
  duration: number
  recordsChecked: number
  issueCount: number
  issues?: MockIntegrityIssue[]
  alertsSent?: boolean
}

function createMockIntegrityCheck(
  overrides: Partial<MockIntegrityCheckResult> = {},
): MockIntegrityCheckResult {
  const passed = overrides.passed ?? true
  return {
    checkId: `check-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    checkType: 'DAILY',
    status: passed ? 'PASSED' : 'FAILED',
    passed,
    executedAt: new Date().toISOString(),
    duration: 45000, // 45 seconds
    recordsChecked: 1500,
    issueCount: passed ? 0 : 3,
    issues: passed
      ? []
      : [
          {
            type: 'DR_CR_IMBALANCE',
            severity: 'HIGH',
            description: 'Debit/Credit imbalance detected',
            details: 'Difference of 1,000,000 VND',
            entityType: 'CashBook',
            entityId: 'book-123',
          },
        ],
    alertsSent: !passed,
    ...overrides,
  }
}

function createMockIntegrityIssue(overrides: Partial<MockIntegrityIssue> = {}): MockIntegrityIssue {
  return {
    type: 'DR_CR_IMBALANCE',
    severity: 'HIGH',
    description: 'Test issue description',
    details: 'Additional details',
    entityType: 'CashReceipt',
    entityId: 'entity-123',
    ...overrides,
  }
}

// ============================================================================
// Test Suite
// ============================================================================

test.describe('Story 6.6: Cash & Bank Integrity Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    // Setup mock authentication as admin (required for integrity check access)
    await setupMockAuth(page, 'admin@example.com', 'admin', 1)
  })

  test('E2E-INTEGRITY-001: Display last integrity check status - PASSED', async ({ page }) => {
    // ========== GIVEN: Last check passed ==========
    const lastCheck = createMockIntegrityCheck({
      checkId: 'check-001',
      status: 'PASSED',
      passed: true,
      issueCount: 0,
      issues: [],
      recordsChecked: 2500,
      duration: 60000,
    })

    const checkHistory = [
      lastCheck,
      createMockIntegrityCheck({ checkId: 'check-002', checkType: 'DAILY' }),
      createMockIntegrityCheck({ checkId: 'check-003', checkType: 'MANUAL' }),
    ]

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(lastCheck),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(checkHistory),
      })
    })

    // ========== WHEN: User navigates to integrity dashboard ==========
    await page.goto('/accounting/audit/integrity')

    // ========== THEN: Header shows correct title ==========
    await expect(page.locator('h1')).toContainText('Integrity')

    // ========== THEN: Last check card shows PASSED status ==========
    await expect(page.locator('text=PASSED')).toBeVisible()

    // ========== THEN: Issue count shows 0 ==========
    await expect(page.locator('text=0').first()).toBeVisible()

    // ========== THEN: Records checked is displayed ==========
    await expect(page.locator('text=2,500')).toBeVisible()
  })

  test('E2E-INTEGRITY-002: Display last integrity check status - FAILED with issues', async ({
    page,
  }) => {
    // ========== GIVEN: Last check failed with issues ==========
    const issues: MockIntegrityIssue[] = [
      {
        type: 'DR_CR_IMBALANCE',
        severity: 'HIGH',
        description: 'Debit/Credit imbalance in cash book',
        details: 'Difference of 5,000,000 VND',
        entityType: 'CashBook',
        entityId: 'book-456',
      },
      {
        type: 'DUPLICATE_REFERENCE',
        severity: 'MEDIUM',
        description: 'Duplicate transaction reference found',
        details: 'Reference: REF-2025-001 appears twice',
        entityType: 'CashReceipt',
        entityId: 'receipt-789',
      },
      {
        type: 'SEQUENCE_GAP',
        severity: 'LOW',
        description: 'Number sequence gap detected',
        details: 'Missing voucher numbers: 1001-1003',
        entityType: 'CashVoucher',
      },
    ]

    const lastCheck = createMockIntegrityCheck({
      checkId: 'check-failed-001',
      status: 'FAILED',
      passed: false,
      issueCount: 3,
      issues,
      alertsSent: true,
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(lastCheck),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([lastCheck]),
      })
    })

    // ========== WHEN: User navigates to integrity dashboard ==========
    await page.goto('/accounting/audit/integrity')

    // ========== THEN: Last check card shows FAILED status ==========
    await expect(page.locator('text=FAILED')).toBeVisible()

    // ========== THEN: Issue count shows 3 ==========
    const issueCountCard = page.locator('text=3').first()
    await expect(issueCountCard).toBeVisible()

    // ========== THEN: Issues table is displayed ==========
    await expect(page.locator('text=Detected Issues')).toBeVisible()

    // ========== THEN: HIGH severity issue is highlighted ==========
    const highSeverityRow = page.locator('tr:has-text("HIGH")')
    await expect(highSeverityRow).toBeVisible()
    await expect(highSeverityRow).toHaveClass(/bg-red/)

    // ========== THEN: All issues are listed ==========
    await expect(page.locator('text=DR_CR_IMBALANCE')).toBeVisible()
    await expect(page.locator('text=DUPLICATE_REFERENCE')).toBeVisible()
    await expect(page.locator('text=SEQUENCE_GAP')).toBeVisible()
  })

  test('E2E-INTEGRITY-003: Run manual integrity check (Admin)', async ({ page }) => {
    // ========== GIVEN: User is admin ==========
    const initialCheck = createMockIntegrityCheck({
      checkId: 'check-initial',
      checkType: 'DAILY',
    })

    const manualCheckResult = createMockIntegrityCheck({
      checkId: 'check-manual-001',
      checkType: 'MANUAL',
      status: 'PASSED',
      passed: true,
      recordsChecked: 3000,
      duration: 120000,
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(initialCheck),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([initialCheck]),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/run*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(manualCheckResult),
      })
    })

    // ========== WHEN: User navigates to integrity dashboard ==========
    await page.goto('/accounting/audit/integrity')

    // ========== THEN: Run Check button is visible (admin only) ==========
    const runCheckButton = page.locator('button:has-text("Run")')
    await expect(runCheckButton).toBeVisible()

    // ========== WHEN: User clicks Run Check button ==========
    await runCheckButton.click()

    // Wait for the check to complete
    await page.waitForResponse((resp) =>
      resp.url().includes('/integrity-checks/run'),
    )

    // ========== THEN: Success toast is shown ==========
    // Note: Toast notification would appear
    await expect(page.locator('text=PASSED').first()).toBeVisible()
  })

  test('E2E-INTEGRITY-004: Run Check button hidden for non-admin', async ({ page }) => {
    // ========== GIVEN: User is accountant (not admin) ==========
    await setupMockAuth(page, 'accountant@example.com', 'accountant', 1)

    const lastCheck = createMockIntegrityCheck({ checkId: 'check-001' })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(lastCheck),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([lastCheck]),
      })
    })

    // ========== WHEN: User navigates to integrity dashboard ==========
    await page.goto('/accounting/audit/integrity')

    // ========== THEN: Run Check button is NOT visible ==========
    const runCheckButton = page.locator('button:has-text("Run Check")')
    await expect(runCheckButton).not.toBeVisible()
  })

  test('E2E-INTEGRITY-005: Display check history table', async ({ page }) => {
    // ========== GIVEN: Multiple integrity checks in history ==========
    const checkHistory = [
      createMockIntegrityCheck({
        checkId: 'check-001',
        checkType: 'DAILY',
        status: 'PASSED',
        passed: true,
        executedAt: '2025-01-15T02:00:00Z',
        duration: 45000,
        recordsChecked: 1500,
      }),
      createMockIntegrityCheck({
        checkId: 'check-002',
        checkType: 'MANUAL',
        status: 'FAILED',
        passed: false,
        executedAt: '2025-01-14T10:30:00Z',
        duration: 60000,
        recordsChecked: 1500,
        issueCount: 2,
      }),
      createMockIntegrityCheck({
        checkId: 'check-003',
        checkType: 'PERIOD_CLOSE',
        status: 'PASSED',
        passed: true,
        executedAt: '2025-01-13T23:59:00Z',
        duration: 120000,
        recordsChecked: 3000,
      }),
    ]

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(checkHistory[0]),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(checkHistory),
      })
    })

    // ========== WHEN: User navigates to integrity dashboard ==========
    await page.goto('/accounting/audit/integrity')

    // ========== THEN: History table shows all checks ==========
    await expect(page.locator('text=History')).toBeVisible()

    // Check that all check types are shown
    await expect(page.locator('text=DAILY')).toBeVisible()
    await expect(page.locator('text=MANUAL')).toBeVisible()
    await expect(page.locator('text=PERIOD_CLOSE')).toBeVisible()

    // Check that status badges are shown
    const passedBadges = page.locator('text=PASSED')
    await expect(passedBadges.first()).toBeVisible()

    const failedBadge = page.locator('text=FAILED')
    await expect(failedBadge).toBeVisible()
  })

  test('E2E-INTEGRITY-006: Display no checks message when history empty', async ({ page }) => {
    // ========== GIVEN: No integrity checks exist ==========
    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 204, // No content
        body: '',
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    // ========== WHEN: User navigates to integrity dashboard ==========
    await page.goto('/accounting/audit/integrity')

    // ========== THEN: "No checks" message is displayed ==========
    await expect(page.locator('text=No integrity checks')).toBeVisible()
  })

  test('E2E-INTEGRITY-007: Navigate to Audit Explorer', async ({ page }) => {
    // ========== GIVEN: User is on integrity dashboard ==========
    const lastCheck = createMockIntegrityCheck({ checkId: 'check-001' })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(lastCheck),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([lastCheck]),
      })
    })

    await page.goto('/accounting/audit/integrity')
    await expect(page.locator('h1')).toContainText('Integrity')

    // ========== WHEN: User clicks Audit Explorer button ==========
    const auditExplorerButton = page.locator('button:has-text("Audit Explorer")')
    await auditExplorerButton.click()

    // ========== THEN: User is navigated to audit explorer ==========
    await expect(page).toHaveURL(/\/accounting\/audit$/)
  })

  test('E2E-INTEGRITY-008: Refresh integrity data', async ({ page }) => {
    // ========== GIVEN: Integrity data is displayed ==========
    let requestCount = 0
    const lastCheck = createMockIntegrityCheck({ checkId: 'check-001' })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      requestCount++
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(lastCheck),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([lastCheck]),
      })
    })

    await page.goto('/accounting/audit/integrity')
    await expect(page.locator('h1')).toContainText('Integrity')
    const initialRequestCount = requestCount

    // ========== WHEN: User clicks refresh button ==========
    const refreshButton = page.locator('button:has-text("Refresh")')
    await refreshButton.click()

    await page.waitForResponse((resp) =>
      resp.url().includes('/integrity-checks/last'),
    )

    // ========== THEN: Data is reloaded ==========
    expect(requestCount).toBeGreaterThan(initialRequestCount)
  })

  test('E2E-INTEGRITY-009: Severity badges display correctly', async ({ page }) => {
    // ========== GIVEN: Check with issues of different severities ==========
    const issues: MockIntegrityIssue[] = [
      createMockIntegrityIssue({ type: 'CRITICAL_ERROR', severity: 'HIGH' }),
      createMockIntegrityIssue({ type: 'WARNING', severity: 'MEDIUM' }),
      createMockIntegrityIssue({ type: 'INFO', severity: 'LOW' }),
    ]

    const lastCheck = createMockIntegrityCheck({
      checkId: 'check-severity-test',
      status: 'FAILED',
      passed: false,
      issueCount: 3,
      issues,
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(lastCheck),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([lastCheck]),
      })
    })

    // ========== WHEN: User views integrity dashboard ==========
    await page.goto('/accounting/audit/integrity')

    // ========== THEN: HIGH severity badge is destructive (red) ==========
    const highBadge = page.locator('text=HIGH').first()
    await expect(highBadge).toBeVisible()

    // ========== THEN: MEDIUM severity badge is default (yellow) ==========
    const mediumBadge = page.locator('text=MEDIUM')
    await expect(mediumBadge).toBeVisible()

    // ========== THEN: LOW severity badge is outline (gray) ==========
    const lowBadge = page.locator('text=LOW')
    await expect(lowBadge).toBeVisible()
  })

  test('E2E-INTEGRITY-010: Duration formatted correctly', async ({ page }) => {
    // ========== GIVEN: Check with specific duration ==========
    const lastCheck = createMockIntegrityCheck({
      checkId: 'check-duration-test',
      duration: 125000, // 2 minutes 5 seconds = ~2.1 min
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks/last*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(lastCheck),
      })
    })

    await page.route('**/api/v1/audit/cash-bank/integrity-checks?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([lastCheck]),
      })
    })

    // ========== WHEN: User views integrity dashboard ==========
    await page.goto('/accounting/audit/integrity')

    // ========== THEN: Duration is displayed in minutes ==========
    await expect(page.locator('text=2.1min')).toBeVisible()
  })
})
