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

// ============================================================================
// Story 7.1 Full Implementation Tests - Drill-Down, PDF Export, Validation
// ============================================================================

test.describe('Story 7.1: Trial Balance Full Implementation', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page, 'chief_accountant@example.com', 'chief_accountant', 1)
  })

  // ========== Mock Data for Full Tests ==========
  interface MockDrillDownVoucher {
    id: string
    voucherNumber: string
    voucherDate: string
    description: string
    debit: number
    credit: number
    voucherType: string
    status: string
  }

  interface MockDrillDownResponse {
    vouchers: MockDrillDownVoucher[]
    totalAmount: number
    voucherCount: number
    page: number
    size: number
    total: number
    hasNext: boolean
  }

  function createMockDrillDownVoucher(overrides: Partial<MockDrillDownVoucher> = {}): MockDrillDownVoucher {
    return {
      id: `voucher-${Math.random().toString(36).substring(7)}`,
      voucherNumber: 'GV-001',
      voucherDate: '2025-01-15',
      description: 'Cash receipt from customer',
      debit: 1000000,
      credit: 0,
      voucherType: 'GENERAL',
      status: 'POSTED',
      ...overrides,
    }
  }

  function createMockDrillDownResponse(vouchers: MockDrillDownVoucher[]): MockDrillDownResponse {
    return {
      vouchers,
      totalAmount: vouchers.reduce((sum, v) => sum + v.debit + v.credit, 0),
      voucherCount: vouchers.length,
      page: 0,
      size: 20,
      total: vouchers.length,
      hasNext: false,
    }
  }

  // ========== AC7.1-04: Drill-Down Tests ==========

  test('E2E-TB-011: Click amount cell opens drill-down panel', async ({ page }) => {
    // ========== GIVEN: Trial balance with accounts ==========
    const mockPeriods = [createMockPeriod()]
    const mockAccounts = [
      createMockAccount({
        accountId: 111,
        accountCode: '111',
        accountName: 'Tiền mặt',
        periodDebit: 50000000,
        periodCredit: 30000000,
      }),
    ]

    const mockDrillDownVouchers = [
      createMockDrillDownVoucher({ voucherNumber: 'GV-001', debit: 25000000 }),
      createMockDrillDownVoucher({ voucherNumber: 'GV-002', debit: 25000000 }),
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

    await page.route('**/api/v1/reports/trial-balance?periodId=*', async (route) => {
      if (!route.request().url().includes('drill-down')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockTrialBalance(mockAccounts)),
        })
      }
    })

    await page.route('**/api/v1/reports/trial-balance/drill-down*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockDrillDownResponse(mockDrillDownVouchers)),
      })
    })

    // ========== WHEN: User navigates to trial balance ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    // ========== WHEN: User clicks on Period Debit amount cell ==========
    const debitCell = page.locator('tbody tr:first-child td:nth-child(5)') // Period Debit column
    await debitCell.click()

    // ========== THEN: Drill-down panel opens ==========
    await expect(page.locator('[role="dialog"], [data-state="open"]')).toBeVisible()
    await expect(page.locator('text=Drill-Down')).toBeVisible()

    // ========== THEN: Vouchers are displayed in the panel ==========
    await expect(page.locator('text=GV-001')).toBeVisible()
    await expect(page.locator('text=GV-002')).toBeVisible()
  })

  test('E2E-TB-012: Drill-down panel pagination works', async ({ page }) => {
    // ========== GIVEN: Many vouchers for drill-down ==========
    const mockPeriods = [createMockPeriod()]
    const mockAccounts = [
      createMockAccount({
        accountId: 111,
        accountCode: '111',
        accountName: 'Tiền mặt',
        periodDebit: 100000000,
      }),
    ]

    // Create 25 vouchers for pagination test
    const manyVouchers = Array.from({ length: 25 }, (_, i) =>
      createMockDrillDownVoucher({
        id: `voucher-${i}`,
        voucherNumber: `GV-${String(i + 1).padStart(3, '0')}`,
        debit: 4000000,
      }),
    )

    let currentPage = 0

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
      if (!route.request().url().includes('drill-down')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockTrialBalance(mockAccounts)),
        })
      }
    })

    await page.route('**/api/v1/reports/trial-balance/drill-down*', async (route) => {
      const url = new URL(route.request().url())
      currentPage = parseInt(url.searchParams.get('page') || '0')
      const pageSize = parseInt(url.searchParams.get('size') || '20')

      const startIdx = currentPage * pageSize
      const endIdx = Math.min(startIdx + pageSize, manyVouchers.length)
      const pageVouchers = manyVouchers.slice(startIdx, endIdx)

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          vouchers: pageVouchers,
          totalAmount: pageVouchers.reduce((sum, v) => sum + v.debit, 0),
          voucherCount: pageVouchers.length,
          page: currentPage,
          size: pageSize,
          total: manyVouchers.length,
          hasNext: endIdx < manyVouchers.length,
        }),
      })
    })

    // ========== WHEN: User opens drill-down panel ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    const debitCell = page.locator('tbody tr:first-child td:nth-child(5)')
    await debitCell.click()

    await expect(page.locator('[role="dialog"], [data-state="open"]')).toBeVisible()

    // ========== THEN: Page 1 vouchers are shown ==========
    await expect(page.locator('text=GV-001')).toBeVisible()
    await expect(page.locator('text=Page 1')).toBeVisible()

    // ========== WHEN: User clicks Next button ==========
    const nextButton = page.locator('[role="dialog"] button:has-text("Next"), [data-state="open"] button:has-text("Next")')
    await nextButton.click()

    // ========== THEN: Page 2 vouchers are shown ==========
    await expect(page.locator('text=Page 2')).toBeVisible()
    await expect(page.locator('text=GV-021')).toBeVisible()
  })

  test('E2E-TB-013: Click voucher row opens detail modal', async ({ page }) => {
    // ========== GIVEN: Drill-down panel is open ==========
    const mockPeriods = [createMockPeriod()]
    const mockAccounts = [
      createMockAccount({
        accountId: 111,
        accountCode: '111',
        accountName: 'Tiền mặt',
        periodDebit: 50000000,
      }),
    ]

    const voucherId = 'voucher-123'
    const mockDrillDownVouchers = [
      createMockDrillDownVoucher({ id: voucherId, voucherNumber: 'GV-001' }),
    ]

    const mockVoucherDetail = {
      id: voucherId,
      voucherNumber: 'GV-001',
      voucherDate: '2025-01-15',
      description: 'Cash receipt from customer ABC',
      status: 'POSTED',
      statusDisplay: 'Posted',
      voucherType: 'GENERAL',
      voucherTypeName: 'General Voucher',
      periodName: 'January 2025',
      lines: [
        { id: 'line-1', accountCode: '111', accountName: 'Tiền mặt', debit: 1000000, credit: 0 },
        { id: 'line-2', accountCode: '131', accountName: 'Phải thu khách hàng', debit: 0, credit: 1000000 },
      ],
    }

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
      if (!route.request().url().includes('drill-down')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockTrialBalance(mockAccounts)),
        })
      }
    })

    await page.route('**/api/v1/reports/trial-balance/drill-down*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockDrillDownResponse(mockDrillDownVouchers)),
      })
    })

    await page.route(`**/api/v1/vouchers/${voucherId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: mockVoucherDetail }),
      })
    })

    await page.route(`**/api/v1/vouchers/${voucherId}/attachments`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    // ========== WHEN: User opens drill-down and clicks voucher ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    const debitCell = page.locator('tbody tr:first-child td:nth-child(5)')
    await debitCell.click()

    await expect(page.locator('text=GV-001')).toBeVisible()

    // Click on the voucher row
    const voucherRow = page.locator('tr:has-text("GV-001")')
    await voucherRow.click()

    // ========== THEN: Voucher detail modal opens ==========
    await expect(page.locator('text=Chi tiết chứng từ')).toBeVisible()

    // ========== THEN: GL lines are displayed ==========
    await expect(page.locator('text=Tiền mặt')).toBeVisible()
    await expect(page.locator('text=Phải thu khách hàng')).toBeVisible()
  })

  // ========== AC7.1-06: PDF Export Tests ==========

  test('E2E-TB-014: Export to PDF with validation preflight', async ({ page }) => {
    // ========== GIVEN: Balanced trial balance ==========
    const mockPeriods = [createMockPeriod()]
    const mockAccounts = [
      createMockAccount({
        accountId: 1,
        accountCode: '111',
        accountName: 'Tiền mặt',
        closingDebit: 100000000,
      }),
      createMockAccount({
        accountId: 2,
        accountCode: '331',
        accountName: 'Phải trả người bán',
        closingCredit: 100000000,
        closingDebit: 0,
      }),
    ]

    let validateCalled = false
    let pdfExportCalled = false

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
      if (!route.request().url().includes('drill-down') && !route.request().url().includes('validate') && !route.request().url().includes('export')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockTrialBalance(mockAccounts, true)),
        })
      }
    })

    await page.route('**/api/v1/reports/trial-balance/validate', async (route) => {
      validateCalled = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ valid: true, errors: [] }),
      })
    })

    await page.route('**/api/v1/reports/trial-balance/export/pdf*', async (route) => {
      pdfExportCalled = true
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('fake pdf content'),
        headers: {
          'Content-Disposition': 'attachment; filename="trial-balance.pdf"',
          'X-Content-SHA256': 'abc123hash',
        },
      })
    })

    // ========== WHEN: User clicks Export PDF button ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    // Open export dropdown
    const exportButton = page.locator('button:has-text("Export")')
    await exportButton.click()

    // Click PDF option
    const pdfOption = page.locator('[role="menuitem"]:has-text("PDF")')
    await pdfOption.click()

    // Wait for validation and export
    await page.waitForResponse((resp) => resp.url().includes('/validate'))
    await page.waitForResponse((resp) => resp.url().includes('/export/pdf'))

    // ========== THEN: Validation was called before export ==========
    expect(validateCalled).toBe(true)
    expect(pdfExportCalled).toBe(true)

    // ========== THEN: Success toast is shown ==========
    await expect(page.locator('[data-sonner-toast]:has-text("success")')).toBeVisible({ timeout: 5000 })
  })

  // ========== AC7.1-09: Validation Preflight Tests ==========

  test('E2E-TB-015: PDF export blocked when GL is imbalanced', async ({ page }) => {
    // ========== GIVEN: Imbalanced trial balance ==========
    const mockPeriods = [createMockPeriod()]
    const imbalancedAccounts = [
      createMockAccount({
        accountId: 1,
        accountCode: '111',
        accountName: 'Tiền mặt',
        closingDebit: 200000000,
      }),
      createMockAccount({
        accountId: 2,
        accountCode: '331',
        accountName: 'Phải trả người bán',
        closingCredit: 100000000,
        closingDebit: 0,
      }),
    ]

    let pdfExportCalled = false

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
      if (!route.request().url().includes('validate') && !route.request().url().includes('export')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockTrialBalance(imbalancedAccounts, false)),
        })
      }
    })

    await page.route('**/api/v1/reports/trial-balance/validate', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          valid: false,
          errors: [
            {
              code: 'GL_IMBALANCE',
              message: 'Cannot export: Trial Balance out of balance',
              details: {
                totalDebit: 200000000,
                totalCredit: 100000000,
                difference: 100000000,
              },
              helpUrl: '/help/trial-balance-imbalance',
            },
          ],
        }),
      })
    })

    await page.route('**/api/v1/reports/trial-balance/export/pdf*', async (route) => {
      pdfExportCalled = true
      await route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        body: Buffer.from('fake pdf content'),
      })
    })

    // ========== WHEN: User clicks Export PDF button ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    // Open export dropdown
    const exportButton = page.locator('button:has-text("Export")')
    await exportButton.click()

    // Click PDF option
    const pdfOption = page.locator('[role="menuitem"]:has-text("PDF")')
    await pdfOption.click()

    // Wait for validation
    await page.waitForResponse((resp) => resp.url().includes('/validate'))

    // ========== THEN: PDF export is NOT called ==========
    expect(pdfExportCalled).toBe(false)

    // ========== THEN: Error toast is shown ==========
    await expect(page.locator('[data-sonner-toast]')).toBeVisible()
  })

  test('E2E-TB-016: Drill-down no vouchers message', async ({ page }) => {
    // ========== GIVEN: Account with no vouchers ==========
    const mockPeriods = [createMockPeriod()]
    const mockAccounts = [
      createMockAccount({
        accountId: 111,
        accountCode: '111',
        accountName: 'Tiền mặt',
        periodDebit: 50000000,
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

    await page.route('**/api/v1/reports/trial-balance?periodId=*', async (route) => {
      if (!route.request().url().includes('drill-down')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockTrialBalance(mockAccounts)),
        })
      }
    })

    await page.route('**/api/v1/reports/trial-balance/drill-down*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockDrillDownResponse([])),
      })
    })

    // ========== WHEN: User opens drill-down for account with no vouchers ==========
    await page.goto('/accounting/reports/trial-balance')
    await expect(page.locator('table')).toBeVisible()

    const debitCell = page.locator('tbody tr:first-child td:nth-child(5)')
    await debitCell.click()

    // ========== THEN: No vouchers message is displayed ==========
    await expect(page.locator('text=No vouchers found')).toBeVisible()
  })
})
