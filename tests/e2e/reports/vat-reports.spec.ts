import { test, expect } from '@playwright/test'

/**
 * VAT Reports E2E Tests
 *
 * Test IDs from e2e-test-plan.md:
 * - RPT-003: VAT report run
 * - RPT-004: VAT report export
 * - RPT-005: VAT corrections
 */

test.describe('VAT Reports', () => {
  test.use({ storageState: 'tests/.auth/chief_accountant.json' })

  test('RPT-003: VAT report run', async ({ page }) => {
    await page.goto('/vat/reports')

    await expect(
      page.locator('h1, [data-testid="page-title"]').filter({ hasText: /VAT|Thuế GTGT/i }),
    ).toBeVisible({ timeout: 10000 })

    const periodSelector = page.locator(
      '[data-testid="period-select"], button:has-text("Period"), button:has-text("Kỳ"), select#period, button[id="period"]',
    )

    if ((await periodSelector.count()) > 0) {
      await periodSelector.first().click()

      const periodOptions = page.locator('[role="option"], [role="menuitem"], option')
      if ((await periodOptions.count()) > 0) {
        await periodOptions.first().click()
      }
    }

    const runButton = page.locator(
      'button:has-text("Run"), button:has-text("Generate"), button:has-text("Tạo báo cáo"), button:has-text("Chạy"), [data-testid="run-report"]',
    )

    if ((await runButton.count()) > 0) {
      await runButton.first().click()
      await page.waitForLoadState('networkidle')
    }

    await expect(
      page.locator(
        'table, [data-testid="vat-report-content"], .report-content, [data-testid="report-table"]',
      ),
    ).toBeVisible({ timeout: 15000 })

    const reportContent = page.locator(
      'table tbody tr, [data-testid="vat-summary"], .vat-summary, [data-testid="report-data"]',
    )
    await expect(reportContent.first()).toBeVisible({ timeout: 10000 })
  })

  test('RPT-004: VAT report export', async ({ page }) => {
    await page.goto('/vat/reports')

    await page.waitForLoadState('networkidle')

    const periodSelector = page.locator(
      '[data-testid="period-select"], button:has-text("Period"), button:has-text("Kỳ"), button[id="period"]',
    )

    if ((await periodSelector.count()) > 0) {
      await periodSelector.first().click()
      const periodOptions = page.locator('[role="option"], [role="menuitem"]')
      if ((await periodOptions.count()) > 0) {
        await periodOptions.first().click()
      }
    }

    const runButton = page.locator(
      'button:has-text("Run"), button:has-text("Generate"), button:has-text("Tạo báo cáo"), [data-testid="run-report"]',
    )
    if ((await runButton.count()) > 0) {
      await runButton.first().click()
      await page.waitForLoadState('networkidle')
    }

    await expect(
      page.locator('table, [data-testid="vat-report-content"], .report-content'),
    ).toBeVisible({ timeout: 15000 })

    const exportButton = page.locator(
      'button:has-text("Export"), button:has-text("Xuất"), [data-testid="export-button"]',
    )
    await expect(exportButton.first()).toBeVisible()
    await exportButton.first().click()

    const exportOption = page.locator(
      '[role="menuitem"]:has-text("Excel"), [role="menuitem"]:has-text("PDF"), [role="menuitem"]:has-text("XML"), button:has-text("Excel")',
    )

    const downloadPromise = page.waitForEvent('download', { timeout: 30000 })

    if ((await exportOption.count()) > 0) {
      await exportOption.first().click()
    }

    const download = await downloadPromise
    const filename = download.suggestedFilename()
    expect(filename).toBeTruthy()
    expect(filename.toLowerCase()).toMatch(/\.(xlsx?|pdf|xml|csv)$|vat/)
  })

  test('RPT-005: VAT corrections', async ({ page }) => {
    await page.goto('/vat/corrections')

    await expect(page.locator('h1, [data-testid="page-title"]')).toContainText(
      /VAT|Thuế|Correction|Điều chỉnh/i,
    )

    const newCorrectionButton = page.locator(
      'button:has-text("New"), button:has-text("Add"), button:has-text("Create"), button:has-text("Tạo"), button:has-text("Thêm"), [data-testid="new-correction"]',
    )

    if ((await newCorrectionButton.count()) > 0) {
      await newCorrectionButton.first().click()

      await expect(
        page.locator(
          '[role="dialog"], form, [data-testid="correction-form"], .modal, .drawer, [data-state="open"]',
        ),
      ).toBeVisible({ timeout: 5000 })

      const periodField = page.locator(
        '[data-testid="correction-period"], input[name="period"], select[name="period"], button:has-text("Period")',
      )
      if ((await periodField.count()) > 0) {
        if ((await periodField.first().getAttribute('type')) === 'text') {
          await periodField.first().fill('2025-01')
        } else {
          await periodField.first().click()
          const options = page.locator('[role="option"]')
          if ((await options.count()) > 0) {
            await options.first().click()
          }
        }
      }

      const reasonField = page.locator(
        '[data-testid="correction-reason"], input[name="reason"], textarea[name="reason"], textarea[name="description"]',
      )
      if ((await reasonField.count()) > 0) {
        await reasonField.first().fill('Test VAT correction - E2E test')
      }

      const amountField = page.locator(
        '[data-testid="correction-amount"], input[name="amount"], input[type="number"]',
      )
      if ((await amountField.count()) > 0) {
        await amountField.first().fill('1000000')
      }

      const cancelButton = page.locator(
        'button:has-text("Cancel"), button:has-text("Hủy"), button:has-text("Close")',
      )
      if ((await cancelButton.count()) > 0) {
        await cancelButton.first().click()
      }
    }

    await expect(
      page.locator('table, [data-testid="corrections-list"], .corrections-list'),
    ).toBeVisible({ timeout: 10000 })
  })
})
