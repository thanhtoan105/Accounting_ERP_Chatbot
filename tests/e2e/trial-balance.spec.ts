import { test, expect } from '../support/fixtures'
import { setupMockAuth } from '../support/helpers/auth-helper'

/**
 * Epic 7 - Story 7.1: Trial Balance MVP E2E Tests
 *
 * Critical user journeys:
 * - View trial balance for selected period
 * - Period selection with localStorage persistence
 * - Search/filter accounts by code or name
 * - Export trial balance to Excel
 * - Imbalance warning banner display
 * - Pagination controls
 * - i18n language switching
 */

// ============================================================================
// Mock Data Factories
// ============================================================================

interface MockPeriod {
  id: string
  periodName: string
  startDate: string
  endDate: string
  fiscalYear: number
  periodNumber: number
  status: string
}

interface MockTrialBalanceAccount {
  accountId: number
  accountCode: string
  accountName: string
  openingDebit: number
  openingCredit: number
  periodDebit: number
  periodCredit: number
  closingDebit: number
  closingCredit: number
}

interface MockTrialBalanceResponse {
  period: MockPeriod
  companyName: string
  generatedAt: string
  accounts: MockTrialBalanceAccount[]
  totalOpeningDebit: number
  totalOpeningCredit: number
  totalPeriodDebit: number
  totalPeriodCredit: number
  totalClosingDebit: number
  totalClosingCredit: number
  isBalanced: boolean
}

function createMockPeriod(overrides: Partial<MockPeriod> = {}): MockPeriod {
  return {
    id: 'period-2025-01',
    periodName: 'January 2025',
    startDate: '2025-01-01',
    endDate: '2025-01-31',
    fiscalYear: 2025,
    periodNumber: 1,
    status: 'OPEN',
    ...overrides,
  }
}

function createMockAccount(overrides: Partial<MockTrialBalanceAccount> = {}): MockTrialBalanceAccount {
  return {
    accountId: Math.floor(Math.random() * 1000),
    accountCode: '111',
    accountName: 'Tiền mặt',
    openingDebit: 100000000,
    openingCredit: 0,
    periodDebit: 50000000,
    periodCredit: 30000000,
    closingDebit: 120000000,
    closingCredit: 0,
    ...overrides,
  }
}

function createMockTrialBalance(
  accounts: MockTrialBalanceAccount[],
  isBalanced: boolean = true,
  period: MockPeriod = createMockPeriod(),
): MockTrialBalanceResponse {
  const totalOpeningDebit = accounts.reduce((sum, a) => sum + a.openingDebit, 0)
  const totalOpeningCredit = accounts.reduce((sum, a) => sum + a.openingCredit, 0)
  const totalPeriodDebit = accounts.reduce((sum, a) => sum + a.periodDebit, 0)
  const totalPeriodCredit = accounts.reduce((sum, a) => sum + a.periodCredit, 0)
  const totalClosingDebit = accounts.reduce((sum, a) => sum + a.closingDebit, 0)
  const totalClosingCredit = accounts.reduce((sum, a) => sum + a.closingCredit, 0)

  return {
    period,
    companyName: 'Test Company LLC',
    generatedAt: new Date().toISOString(),
    accounts,
    totalOpeningDebit,
    totalOpeningCredit,
    totalPeriodDebit,
    totalPeriodCredit,
    totalClosingDebit,
    totalClosingCredit,
    isBalanced,
  }
}

// ============================================================================
// Test Suite
// ============================================================================

test.describe('Story 7.1: Trial Balance MVP', () => {
  test.beforeEach(async ({ page }) => {
    // Setup mock authentication as chief accountant (required role per AC7.1-MVP-07)
    await setupMockAuth(page, 'chief_accountant@example.com', 'chief_accountant', 1)
  })

  test('E2E-TB-001: Display trial balance with account data', async ({ page }) => {
    // ========== GIVEN: Trial balance data exists for the period ==========
    const mockPeriods = [
      createMockPeriod({ id: 'period-2025-01', periodName: 'January 2025' }),
      createMockPeriod({ id: 'period-2025-02', periodName: 'February 2025', periodNumber: 2 }),
    ]

    const mockAccounts = [
      createMockAccount({
        accountId: 1,
        accountCode: '111',
        accountName: 'Tiền mặt',
        openingDebit: 100000000,
        closingDebit: 120000000,
      }),
      createMockAccount({
        accountId: 2,
        accountCode: '112',
        accountName: 'Tiền gửi ngân hàng',
        openingDebit: 500000000,
        closingDebit: 480000000,
      }),
      createMockAccount({
        accountId: 3,
        accountCode: '331',
        accountName: 'Phải trả người bán',
        openingCredit: 200000000,
        closingCredit: 200000000,
        openingDebit: 0,
        closingDebit: 0,
      }),
    ]

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockTrialBalance(mockAccounts)),
      })
    })

    // ========== WHEN: User navigates to trial balance page ==========
    await page.goto('/accounting/reports/trial-balance')

    // ========== THEN: Trial balance is displayed in table ==========
    await expect(page.locator('table')).toBeVisible()
    await expect(page.locator('tbody tr')).toHaveCount(4) // 3 accounts + 1 total row

    // ========== THEN: Header shows correct title ==========
    await expect(page.locator('h1')).toContainText('Trial Balance')
  })

  test('E2E-TB-002: Display imbalance warning banner when Dr ≠ Cr', async ({ page }) => {
    // ========== GIVEN: Trial balance is out of balance ==========
    const mockPeriods = [createMockPeriod()]

    // Create imbalanced accounts (Dr > Cr)
    const imbalancedAccounts = [
      createMockAccount({
        accountId: 1,
        accountCode: '111',
        accountName: 'Tiền mặt',
        closingDebit: 200000000,
        closingCredit: 0,
      }),
      createMockAccount({
        accountId: 2,
        accountCode: '331',
        accountName: 'Phải trả người bán',
        closingDebit: 0,
        closingCredit: 100000000,
      }),
    ]

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockTrialBalance(imbalancedAccounts, false)),
      })
    })

    // ========== WHEN: User views trial balance ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    // ========== THEN: Warning banner is displayed ==========
    await expect(page.locator('[role="alert"]')).toBeVisible()
    await expect(page.locator('[role="alert"]')).toContainText('Warning')
  })

  test('E2E-TB-003: Period selection persists in localStorage', async ({ page }) => {
    // ========== GIVEN: Multiple periods available ==========
    const mockPeriods = [
      createMockPeriod({ id: 'period-2025-01', periodName: 'January 2025' }),
      createMockPeriod({ id: 'period-2025-02', periodName: 'February 2025', periodNumber: 2 }),
      createMockPeriod({ id: 'period-2025-03', periodName: 'March 2025', periodNumber: 3 }),
    ]

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockTrialBalance([createMockAccount()])),
      })
    })

    // ========== WHEN: User selects a different period ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    // Open period dropdown and select February
    const periodSelect = page.locator('button[id="period"]')
    await periodSelect.click()

    const februaryOption = page.locator('[role="option"]:has-text("February 2025")')
    await februaryOption.click()

    // Wait for data to reload
    await page.waitForResponse((resp) => resp.url().includes('/reports/trial-balance'))

    // ========== THEN: Selection is saved to localStorage ==========
    const savedPeriod = await page.evaluate(() => localStorage.getItem('trialBalance_lastPeriod'))
    expect(savedPeriod).toBe('period-2025-02')

    // ========== WHEN: User refreshes the page ==========
    await page.reload()
    await expect(page.locator('table')).toBeVisible()

    // ========== THEN: Saved period is restored ==========
    await expect(periodSelect).toContainText('February 2025')
  })

  test('E2E-TB-004: Search/filter accounts by code or name', async ({ page }) => {
    // ========== GIVEN: Multiple accounts in trial balance ==========
    const mockPeriods = [createMockPeriod()]
    const mockAccounts = [
      createMockAccount({ accountId: 1, accountCode: '111', accountName: 'Tiền mặt' }),
      createMockAccount({ accountId: 2, accountCode: '112', accountName: 'Tiền gửi ngân hàng' }),
      createMockAccount({ accountId: 3, accountCode: '131', accountName: 'Phải thu khách hàng' }),
      createMockAccount({ accountId: 4, accountCode: '331', accountName: 'Phải trả người bán' }),
    ]

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockTrialBalance(mockAccounts)),
      })
    })

    // ========== WHEN: User searches for "11" ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    const searchInput = page.locator('input#search')
    await searchInput.fill('11')

    // ========== THEN: Only matching accounts are displayed ==========
    // Should show 111 and 112 (+ total row = 3 rows)
    await expect(page.locator('tbody tr')).toHaveCount(3)
    await expect(page.locator('tbody tr:has-text("111")')).toBeVisible()
    await expect(page.locator('tbody tr:has-text("112")')).toBeVisible()

    // ========== WHEN: User searches for "Phải thu" ==========
    await searchInput.fill('Phải thu')

    // ========== THEN: Only accounts with matching name are shown ==========
    await expect(page.locator('tbody tr')).toHaveCount(2) // 1 account + total
    await expect(page.locator('tbody tr:has-text("131")')).toBeVisible()
  })

  test('E2E-TB-005: Export trial balance to Excel', async ({ page }) => {
    // ========== GIVEN: Trial balance data exists ==========
    const mockPeriods = [createMockPeriod()]
    const mockAccounts = [createMockAccount()]

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance?periodId=*', async (route) => {
      if (!route.request().url().includes('export')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockTrialBalance(mockAccounts)),
        })
      }
    })

    let exportCalled = false

    await page.route('**/api/v1/reports/trial-balance/export*', async (route) => {
      exportCalled = true
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        body: Buffer.from('fake excel content'),
        headers: {
          'Content-Disposition': 'attachment; filename="trial-balance.xlsx"',
        },
      })
    })

    // ========== WHEN: User clicks Export Excel button ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    const exportButton = page.locator('button:has-text("Export")')
    await exportButton.click()

    // Wait for export request
    await page.waitForResponse((resp) => resp.url().includes('/export'))

    // ========== THEN: Export API is called ==========
    expect(exportCalled).toBe(true)
  })

  test('E2E-TB-006: Pagination - change page and page size', async ({ page }) => {
    // ========== GIVEN: Many accounts in trial balance ==========
    const mockPeriods = [createMockPeriod()]
    const mockAccounts = Array.from({ length: 100 }, (_, i) =>
      createMockAccount({
        accountId: i + 1,
        accountCode: String(100 + i).padStart(3, '0'),
        accountName: `Account ${i + 1}`,
      }),
    )

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockTrialBalance(mockAccounts)),
      })
    })

    // ========== WHEN: User views trial balance ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    // Default page size is 50
    // Should show 50 accounts + 1 total row = 51 rows initially
    await expect(page.locator('tbody tr')).toHaveCount(51)

    // ========== WHEN: User clicks Next button ==========
    const nextButton = page.locator('button:has-text("Next")')
    await nextButton.click()

    // ========== THEN: Page 2 is displayed ==========
    await expect(page.locator('text=Page 2 of')).toBeVisible()

    // ========== WHEN: User changes page size to 10 ==========
    const pageSizeSelect = page.locator('button#pageSize')
    await pageSizeSelect.click()

    const option10 = page.locator('[role="option"]:has-text("10")')
    await option10.click()

    // ========== THEN: Only 10 accounts + total row are shown ==========
    await expect(page.locator('tbody tr')).toHaveCount(11)
  })

  test('E2E-TB-007: Refresh button reloads data', async ({ page }) => {
    // ========== GIVEN: Trial balance is displayed ==========
    const mockPeriods = [createMockPeriod()]
    let requestCount = 0

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      requestCount++
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockTrialBalance([createMockAccount()])),
      })
    })

    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    const initialRequestCount = requestCount

    // ========== WHEN: User clicks Refresh button ==========
    const refreshButton = page.locator('button:has-text("Refresh")')
    await refreshButton.click()

    await page.waitForResponse((resp) => resp.url().includes('/reports/trial-balance'))

    // ========== THEN: Data is reloaded ==========
    expect(requestCount).toBeGreaterThan(initialRequestCount)
  })

  test('E2E-TB-008: i18n - labels change when language switches', async ({ page }) => {
    // ========== GIVEN: Trial balance is displayed ==========
    const mockPeriods = [createMockPeriod()]

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockTrialBalance([createMockAccount()])),
      })
    })

    // Start with English (default)
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    // ========== THEN: English labels are displayed ==========
    await expect(page.locator('h1')).toContainText('Trial Balance')
    await expect(page.locator('th:has-text("Account Code")')).toBeVisible()

    // ========== WHEN: User switches to Vietnamese ==========
    // Note: This assumes there's a language switcher in the app
    // The actual implementation may vary
    await page.evaluate(() => {
      localStorage.setItem('i18nextLng', 'vi')
    })
    await page.reload()
    await expect(page.locator('table')).toBeVisible()

    // ========== THEN: Vietnamese labels are displayed ==========
    await expect(page.locator('h1')).toContainText('Bảng Cân đối Phát sinh')
    await expect(page.locator('th:has-text("Mã TK")')).toBeVisible()
  })

  test('E2E-TB-009: Empty state when no accounts for period', async ({ page }) => {
    // ========== GIVEN: No accounts in trial balance ==========
    const mockPeriods = [createMockPeriod()]

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockTrialBalance([])),
      })
    })

    // ========== WHEN: User views trial balance ==========
    await page.goto('/accounting/reports/trial-balance')

    // ========== THEN: Empty state message is displayed ==========
    await expect(page.locator('text=No accounts found')).toBeVisible()
  })

  test('E2E-TB-010: Error handling when API fails', async ({ page }) => {
    // ========== GIVEN: API returns error ==========
    const mockPeriods = [createMockPeriod()]

    await page.route('**/api/v1/periods/open*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/v1/periods/current', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods[0]),
      })
    })

    await page.route('**/api/v1/reports/trial-balance*', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Internal server error' }),
      })
    })

    // ========== WHEN: User views trial balance ==========
    await page.goto('/accounting/reports/trial-balance')

    // ========== THEN: Error toast is shown ==========
    await expect(page.locator('[data-sonner-toast]')).toBeVisible()
  })
})
