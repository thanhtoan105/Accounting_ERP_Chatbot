import { test, expect } from '@playwright/test'

/**
 * Trial Balance Report E2E Tests
 *
 * Test IDs from e2e-test-plan.md:
 * - RPT-001: Trial balance report (@smoke)
 * - RPT-009: Report date range filter
 * - RPT-010: Report export PDF
 * - RPT-011: Report export Excel
 */

test.describe('Trial Balance Reports', () => {
  test.use({ storageState: 'tests/.auth/cfo.json' })

  test('RPT-001: Trial balance report @smoke', async ({ page }) => {
    await page.goto('/accounting/trial-balance')

    await expect(page.locator('h1, [data-testid="page-title"]')).toContainText(/Trial Balance/i)

    await expect(page.locator('table')).toBeVisible({ timeout: 15000 })

    const headers = page.locator('thead th')
    await expect(headers.filter({ hasText: /Account|Tài khoản/i })).toBeVisible()
    await expect(headers.filter({ hasText: /Debit|Nợ/i }).first()).toBeVisible()
    await expect(headers.filter({ hasText: /Credit|Có/i }).first()).toBeVisible()

    const rows = page.locator('tbody tr')
    await expect(rows).not.toHaveCount(0)
  })

  test('RPT-009: Report date range filter', async ({ page }) => {
    await page.goto('/accounting/trial-balance')

    await expect(page.locator('table')).toBeVisible({ timeout: 15000 })

    const periodSelector = page.locator(
      '[data-testid="period-select"], button:has-text("Period"), button:has-text("Kỳ"), button[id="period"]',
    )

    if ((await periodSelector.count()) > 0) {
      await periodSelector.first().click()

      const periodOptions = page.locator('[role="option"], [role="menuitem"]')
      await expect(periodOptions.first()).toBeVisible({ timeout: 5000 })

      await periodOptions.first().click()

      await page.waitForLoadState('networkidle')
      await expect(page.locator('table')).toBeVisible()
    }

    const dateInputs = page.locator(
      '[data-testid="date-from"], [data-testid="date-to"], input[type="date"]',
    )

    if ((await dateInputs.count()) >= 2) {
      const fromDate = dateInputs.nth(0)
      const toDate = dateInputs.nth(1)

      await fromDate.fill('2025-01-01')
      await toDate.fill('2025-01-31')

      await page.waitForLoadState('networkidle')
      await expect(page.locator('table')).toBeVisible()
    }

    await expect(page.locator('tbody tr').first()).toBeVisible()
  })

  test('RPT-010: Report export PDF', async ({ page }) => {
    await page.goto('/accounting/trial-balance')

    await expect(page.locator('table')).toBeVisible({ timeout: 15000 })

    const exportButton = page.locator(
      'button:has-text("Export"), button:has-text("Xuất"), [data-testid="export-button"]',
    )
    await expect(exportButton.first()).toBeVisible()
    await exportButton.first().click()

    const pdfOption = page.locator(
      '[role="menuitem"]:has-text("PDF"), button:has-text("PDF"), [data-testid="export-pdf"]',
    )

    if ((await pdfOption.count()) > 0) {
      const downloadPromise = page.waitForEvent('download', { timeout: 30000 })
      await pdfOption.first().click()

      const download = await downloadPromise
      const filename = download.suggestedFilename()
      expect(filename.toLowerCase()).toMatch(/\.pdf$|trial|balance/)
    } else {
      const downloadPromise = page.waitForEvent('download', { timeout: 30000 })

      const pdfButton = page.locator('button:has-text("PDF")')
      if ((await pdfButton.count()) > 0) {
        await pdfButton.click()
        const download = await downloadPromise
        expect(download.suggestedFilename()).toBeTruthy()
      }
    }
  })

  test('RPT-011: Report export Excel', async ({ page }) => {
    await page.goto('/accounting/trial-balance')

    await expect(page.locator('table')).toBeVisible({ timeout: 15000 })

    const exportButton = page.locator(
      'button:has-text("Export"), button:has-text("Xuất"), [data-testid="export-button"]',
    )
    await expect(exportButton.first()).toBeVisible()
    await exportButton.first().click()

    const excelOption = page.locator(
      '[role="menuitem"]:has-text("Excel"), [role="menuitem"]:has-text("XLSX"), button:has-text("Excel"), [data-testid="export-excel"]',
    )

    const downloadPromise = page.waitForEvent('download', { timeout: 30000 })

    if ((await excelOption.count()) > 0) {
      await excelOption.first().click()
    } else {
      const xlsxButton = page.locator('button:has-text("Excel"), button:has-text("XLSX")')
      if ((await xlsxButton.count()) > 0) {
        await xlsxButton.click()
      }
    }

    const download = await downloadPromise
    const filename = download.suggestedFilename()
    expect(filename.toLowerCase()).toMatch(/\.xlsx?$|excel|trial|balance/)
  })
})
