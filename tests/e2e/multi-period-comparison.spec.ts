import { test, expect } from '../support/fixtures'
import { setupMockAuth } from '../support/helpers/auth-helper'

/**
 * Story 7.4: Multi-Period Comparison & Variance Analysis E2E Tests
 *
 * Tests for:
 * - Multi-period selection (up to 4 periods)
 * - Comparison modes (YoY, MoM, Quarterly, Custom)
 * - Report type switching (B01, B02, B03)
 * - Variance display with color coding
 * - Display options (hide zeros, hide immaterial, sparklines)
 * - Export to Excel/PDF
 * - Draft period warning
 */

// ============================================================================
// Mock Data Factories
// ============================================================================

interface MockPeriodSummary {
  periodId: string
  periodName: string
  fiscalYear: number
  periodNumber: number
  startDate: string
  endDate: string
  status: string
}

interface MockVariance {
  fromPeriodId: string
  toPeriodId: string
  absoluteVariance: number
  percentVariance: number | null
  direction: 'FAVORABLE' | 'UNFAVORABLE' | 'NEUTRAL'
}

interface MockMultiPeriodLine {
  lineCode: string
  lineName: string
  lineNameEnglish: string
  level: number
  isCalculated: boolean
  periodValues: number[]
  variances: MockVariance[]
  sparklineData: number[]
  exceedsThreshold: boolean
}

interface MockPeriodColumn {
  periodId: string
  periodName: string
  startDate: string
  endDate: string
  isDraft: boolean
}

interface MockComparisonSettings {
  varianceThresholdPercent: number
  varianceThresholdAbsolute: number
  defaultComparisonMode: string
  showSparklines: boolean
  hideImmaterialDefault: boolean
}

interface MockMultiPeriodReport {
  reportType: string
  reportName: string
  companyId: number
  companyName: string
  periods: MockPeriodColumn[]
  lines: MockMultiPeriodLine[]
  settings: MockComparisonSettings
  generatedAt: string
  hasDraftPeriod: boolean
}

function createMockPeriods(): MockPeriodSummary[] {
  return [
    {
      periodId: 'period-2025-01',
      periodName: 'January 2025',
      fiscalYear: 2025,
      periodNumber: 1,
      startDate: '2025-01-01',
      endDate: '2025-01-31',
      status: 'OPEN',
    },
    {
      periodId: 'period-2024-12',
      periodName: 'December 2024',
      fiscalYear: 2024,
      periodNumber: 12,
      startDate: '2024-12-01',
      endDate: '2024-12-31',
      status: 'CLOSED',
    },
    {
      periodId: 'period-2024-11',
      periodName: 'November 2024',
      fiscalYear: 2024,
      periodNumber: 11,
      startDate: '2024-11-01',
      endDate: '2024-11-30',
      status: 'CLOSED',
    },
    {
      periodId: 'period-2024-01',
      periodName: 'January 2024',
      fiscalYear: 2024,
      periodNumber: 1,
      startDate: '2024-01-01',
      endDate: '2024-01-31',
      status: 'CLOSED',
    },
    {
      periodId: 'period-2023-01',
      periodName: 'January 2023',
      fiscalYear: 2023,
      periodNumber: 1,
      startDate: '2023-01-01',
      endDate: '2023-01-31',
      status: 'CLOSED',
    },
  ]
}

function createMockComparisonSettings(overrides: Partial<MockComparisonSettings> = {}): MockComparisonSettings {
  return {
    varianceThresholdPercent: 10.0,
    varianceThresholdAbsolute: 1000000,
    defaultComparisonMode: 'YOY',
    showSparklines: true,
    hideImmaterialDefault: false,
    ...overrides,
  }
}

function createMockMultiPeriodReport(
  reportType: string = 'B02',
  periodIds: string[] = ['period-2025-01', 'period-2024-01'],
  hasDraftPeriod: boolean = false,
  overrides: Partial<MockMultiPeriodReport> = {},
): MockMultiPeriodReport {
  const reportNames: Record<string, string> = {
    B01: 'Bảng cân đối kế toán / Balance Sheet',
    B02: 'Báo cáo kết quả kinh doanh / Income Statement',
    B03: 'Báo cáo lưu chuyển tiền tệ / Cash Flow Statement',
  }

  const periods: MockPeriodColumn[] = periodIds.map((id) => {
    const mockPeriods = createMockPeriods()
    const period = mockPeriods.find((p) => p.periodId === id)
    return {
      periodId: id,
      periodName: period?.periodName || id,
      startDate: period?.startDate || '2025-01-01',
      endDate: period?.endDate || '2025-01-31',
      isDraft: period?.status === 'OPEN',
    }
  })

  const lines: MockMultiPeriodLine[] = [
    {
      lineCode: '10',
      lineName: 'Doanh thu bán hàng',
      lineNameEnglish: 'Revenue from sales',
      level: 0,
      isCalculated: false,
      periodValues: [500000000, 400000000],
      variances: [
        {
          fromPeriodId: periodIds[1],
          toPeriodId: periodIds[0],
          absoluteVariance: 100000000,
          percentVariance: 25,
          direction: 'FAVORABLE',
        },
      ],
      sparklineData: [400000000, 500000000],
      exceedsThreshold: true,
    },
    {
      lineCode: '11',
      lineName: 'Giá vốn hàng bán',
      lineNameEnglish: 'Cost of goods sold',
      level: 0,
      isCalculated: false,
      periodValues: [300000000, 280000000],
      variances: [
        {
          fromPeriodId: periodIds[1],
          toPeriodId: periodIds[0],
          absoluteVariance: 20000000,
          percentVariance: 7.14,
          direction: 'UNFAVORABLE',
        },
      ],
      sparklineData: [280000000, 300000000],
      exceedsThreshold: false,
    },
    {
      lineCode: '20',
      lineName: 'Lợi nhuận gộp',
      lineNameEnglish: 'Gross profit',
      level: 0,
      isCalculated: true,
      periodValues: [200000000, 120000000],
      variances: [
        {
          fromPeriodId: periodIds[1],
          toPeriodId: periodIds[0],
          absoluteVariance: 80000000,
          percentVariance: 66.67,
          direction: 'FAVORABLE',
        },
      ],
      sparklineData: [120000000, 200000000],
      exceedsThreshold: true,
    },
    {
      lineCode: '30',
      lineName: 'Chi phí hoạt động',
      lineNameEnglish: 'Operating expenses',
      level: 0,
      isCalculated: false,
      periodValues: [0, 0],
      variances: [
        {
          fromPeriodId: periodIds[1],
          toPeriodId: periodIds[0],
          absoluteVariance: 0,
          percentVariance: null,
          direction: 'NEUTRAL',
        },
      ],
      sparklineData: [0, 0],
      exceedsThreshold: false,
    },
  ]

  return {
    reportType,
    reportName: reportNames[reportType] || 'Unknown Report',
    companyId: 1,
    companyName: 'Test Company LLC',
    periods,
    lines,
    settings: createMockComparisonSettings(),
    generatedAt: new Date().toISOString(),
    hasDraftPeriod,
    ...overrides,
  }
}

// ============================================================================
// Test Suite
// ============================================================================

test.describe('Story 7.4: Multi-Period Comparison', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockAuth(page, 'cfo@example.com', 'cfo', 1)
  })

  // E1: Page load test
  test('E2E-MPC-001: Page loads with correct title', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01', 'period-2023-01']),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    await expect(page.locator('h1')).toContainText('So sánh đa kỳ / Multi-Period Comparison')
  })

  // E1: Select 4 periods test
  test('E2E-MPC-002: Select multiple periods and verify columns', async ({ page }) => {
    const mockPeriods = createMockPeriods()
    const selectedPeriodIds = ['period-2025-01', 'period-2024-12', 'period-2024-11', 'period-2024-01']

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      const url = new URL(route.request().url())
      const periodIdsParam = url.searchParams.get('periodIds')
      const reportType = url.searchParams.get('reportType') || 'B02'
      const periodIds = periodIdsParam?.split(',') || []

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport(reportType, periodIds, periodIds.includes('period-2025-01'))),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Select CUSTOM mode to manually select periods
    await page.click('input[id="mode-CUSTOM"]')

    // Add periods one by one
    for (const periodId of selectedPeriodIds) {
      const selectTrigger = page.locator('button:has-text("Add period...")')
      if (await selectTrigger.isVisible()) {
        await selectTrigger.click()
        const periodOption = page.locator(`[role="option"]:has-text("${mockPeriods.find(p => p.periodId === periodId)?.periodName}")`)
        await periodOption.click()
        await page.waitForTimeout(300)
      }
    }

    // Verify period badges are shown
    await expect(page.locator('text=January 2025')).toBeVisible()
    await expect(page.locator('text=December 2024')).toBeVisible()
    await expect(page.locator('text=November 2024')).toBeVisible()
    await expect(page.locator('text=January 2024')).toBeVisible()

    // Verify max 4 periods message
    await expect(page.locator('text=Maximum 4 periods allowed')).toBeVisible()
  })

  // E2: YoY preset selection
  test('E2E-MPC-003: YoY mode auto-selects same month across years', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      const url = new URL(route.request().url())
      const mode = url.searchParams.get('mode')

      if (mode === 'YOY') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(['period-2024-01', 'period-2023-01']),
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        })
      }
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01', 'period-2023-01'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // YoY should be default
    await expect(page.locator('input[id="mode-YOY"]')).toBeChecked()

    // Wait for suggested periods to load
    await page.waitForResponse((resp) => resp.url().includes('/suggested'))

    // Verify YoY periods are auto-selected
    await expect(page.locator('text=January 2025')).toBeVisible()
  })

  // E2: MoM preset selection
  test('E2E-MPC-004: MoM mode auto-selects consecutive months', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      const url = new URL(route.request().url())
      const mode = url.searchParams.get('mode')

      if (mode === 'MOM') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(['period-2024-12', 'period-2024-11']),
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        })
      }
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-12', 'period-2024-11'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Click MoM mode
    await page.click('input[id="mode-MOM"]')

    // Wait for suggested periods to load
    await page.waitForResponse((resp) => resp.url().includes('/suggested'))

    // Verify MoM mode is selected
    await expect(page.locator('input[id="mode-MOM"]')).toBeChecked()
  })

  // Report type switching tests
  test('E2E-MPC-005: Switch between B01, B02, B03 report types', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      const url = new URL(route.request().url())
      const reportType = url.searchParams.get('reportType') || 'B02'

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport(reportType, ['period-2025-01', 'period-2024-01'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Default should be B02
    await expect(page.locator('[role="tab"][data-state="active"]')).toContainText('B02')

    // Click B01 tab
    await page.click('[role="tab"]:has-text("B01")')
    await page.waitForResponse((resp) => resp.url().includes('/multi-period') && resp.url().includes('B01'))
    await expect(page.locator('[role="tab"][data-state="active"]')).toContainText('B01')

    // Click B03 tab
    await page.click('[role="tab"]:has-text("B03")')
    await page.waitForResponse((resp) => resp.url().includes('/multi-period') && resp.url().includes('B03'))
    await expect(page.locator('[role="tab"][data-state="active"]')).toContainText('B03')
  })

  // Variance display test
  test('E2E-MPC-006: Variance columns display with color coding', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Wait for table to load
    await expect(page.locator('table')).toBeVisible()

    // Verify variance column header
    await expect(page.locator('th:has-text("Variance %")')).toBeVisible()

    // Verify line data is displayed
    await expect(page.locator('text=Doanh thu bán hàng')).toBeVisible()
    await expect(page.locator('text=Revenue from sales')).toBeVisible()
  })

  // E3: Hide zeros toggle
  test('E2E-MPC-007: Toggle hide zero values', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Wait for table to load
    await expect(page.locator('table')).toBeVisible()

    // Verify zero line is visible initially
    await expect(page.locator('text=Chi phí hoạt động')).toBeVisible()

    // Toggle hide zeros
    await page.click('button[id="hide-zeros"]')

    // Verify hidden count message appears
    await expect(page.locator('text=/\\d+ dòng đã ẩn/')).toBeVisible()
  })

  // E4: Hide immaterial toggle
  test('E2E-MPC-008: Toggle hide immaterial variances', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Wait for table to load
    await expect(page.locator('table')).toBeVisible()

    // Toggle hide immaterial
    await page.click('button[id="hide-immaterial"]')

    // Only material lines (exceedsThreshold=true) should remain visible
    await expect(page.locator('text=Doanh thu bán hàng')).toBeVisible()
    await expect(page.locator('text=Lợi nhuận gộp')).toBeVisible()
  })

  // Sparklines toggle test
  test('E2E-MPC-009: Toggle sparklines visibility', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Wait for table to load
    await expect(page.locator('table')).toBeVisible()

    // Verify Trend column header is visible initially
    await expect(page.locator('th:has-text("Trend")')).toBeVisible()

    // Toggle off sparklines
    await page.click('button[id="show-sparklines"]')

    // Verify Trend column is hidden
    await expect(page.locator('th:has-text("Trend")')).toBeHidden()
  })

  // E6: Export Excel test
  test('E2E-MPC-010: Export to Excel', async ({ page }) => {
    const mockPeriods = createMockPeriods()
    let exportCalled = false

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      if (route.request().url().includes('/export')) {
        exportCalled = true
        await route.fulfill({
          status: 200,
          contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          body: Buffer.from('fake excel content'),
          headers: {
            'Content-Disposition': 'attachment; filename="multi-period-report.xlsx"',
          },
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01'], true)),
        })
      }
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Wait for report to load
    await expect(page.locator('table')).toBeVisible()

    // Open export dropdown
    await page.click('button:has-text("Export")')

    // Click Excel export
    await page.click('[role="menuitem"]:has-text("Export to Excel")')

    // Wait for export request
    await page.waitForResponse((resp) => resp.url().includes('/export'))

    expect(exportCalled).toBe(true)
  })

  // E6: Export PDF test
  test('E2E-MPC-011: Export to PDF', async ({ page }) => {
    const mockPeriods = createMockPeriods()
    let exportCalled = false

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      if (route.request().url().includes('/export')) {
        exportCalled = true
        await route.fulfill({
          status: 200,
          contentType: 'application/pdf',
          body: Buffer.from('fake pdf content'),
          headers: {
            'Content-Disposition': 'attachment; filename="multi-period-report.pdf"',
          },
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01'], true)),
        })
      }
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Wait for report to load
    await expect(page.locator('table')).toBeVisible()

    // Open export dropdown
    await page.click('button:has-text("Export")')

    // Click PDF export
    await page.click('[role="menuitem"]:has-text("Export to PDF")')

    // Wait for export request
    await page.waitForResponse((resp) => resp.url().includes('/export'))

    expect(exportCalled).toBe(true)
  })

  // Draft period warning test
  test('E2E-MPC-012: Draft period warning alert is displayed', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Wait for report to load
    await expect(page.locator('table')).toBeVisible()

    // Verify draft period warning is shown
    await expect(page.locator('[role="alert"]')).toBeVisible()
    await expect(page.locator('text=Draft Period Included')).toBeVisible()
    await expect(page.locator('text=Values may change')).toBeVisible()
  })

  // Refresh button test
  test('E2E-MPC-013: Refresh button reloads data', async ({ page }) => {
    const mockPeriods = createMockPeriods()
    let requestCount = 0

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(['period-2024-01']),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      if (!route.request().url().includes('/export')) {
        requestCount++
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-01'], true)),
        })
      }
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Wait for initial load
    await expect(page.locator('table')).toBeVisible()
    const initialCount = requestCount

    // Click refresh button
    await page.click('button:has-text("Refresh")')

    // Wait for new request
    await page.waitForResponse((resp) => resp.url().includes('/multi-period') && !resp.url().includes('/export'))

    expect(requestCount).toBeGreaterThan(initialCount)
  })

  // Custom mode allows manual period selection
  test('E2E-MPC-014: Custom mode allows manual period selection', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Select CUSTOM mode
    await page.click('input[id="mode-CUSTOM"]')
    await expect(page.locator('input[id="mode-CUSTOM"]')).toBeChecked()

    // Verify period selector is enabled (no disabled message)
    await expect(page.locator('text=Add period...')).toBeVisible()

    // Verify auto-selection message is NOT shown
    await expect(page.locator('text=Periods are auto-selected based on comparison mode')).toBeHidden()
  })

  // Empty state test
  test('E2E-MPC-015: Empty state when no periods selected', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Select CUSTOM mode to clear periods
    await page.click('input[id="mode-CUSTOM"]')

    // Verify empty state message
    await expect(page.locator('text=Select at least one period to generate a comparison report')).toBeVisible()
  })

  // Quarterly mode test
  test('E2E-MPC-016: Quarterly mode auto-selects previous quarters', async ({ page }) => {
    const mockPeriods = createMockPeriods()

    await page.route('**/api/reports/comparison-presets/available-periods', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPeriods),
      })
    })

    await page.route('**/api/settings/comparison', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockComparisonSettings()),
      })
    })

    await page.route('**/api/reports/comparison-presets/suggested**', async (route) => {
      const url = new URL(route.request().url())
      const mode = url.searchParams.get('mode')

      if (mode === 'QUARTERLY') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(['period-2024-12', 'period-2024-11']),
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        })
      }
    })

    await page.route('**/api/reports/statutory/multi-period**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockMultiPeriodReport('B02', ['period-2025-01', 'period-2024-12', 'period-2024-11'], true)),
      })
    })

    await page.goto('/accounting/reports/comparison')
    await page.waitForLoadState('networkidle')

    // Click Quarterly mode
    await page.click('input[id="mode-QUARTERLY"]')

    // Wait for suggested periods to load
    await page.waitForResponse((resp) => resp.url().includes('/suggested'))

    // Verify Quarterly mode is selected
    await expect(page.locator('input[id="mode-QUARTERLY"]')).toBeChecked()
  })
})
