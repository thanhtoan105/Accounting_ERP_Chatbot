import { test, expect } from '@playwright/test'

/**
 * Aging Reports E2E Tests
 *
 * Test IDs from e2e-test-plan.md:
 * - RPT-006: AP aging report
 * - RPT-007: AR aging report
 * - RPT-008: AR statements
 */

test.describe('Aging Reports', () => {
  test.use({ storageState: 'tests/.auth/cfo.json' })

  test('RPT-006: AP aging report', async ({ page }) => {
    await page.goto('/ap-aging')

    await expect(page.locator('h1, [data-testid="page-title"]')).toContainText(
      /AP|Payable|Aging|Phải trả|Tuổi nợ/i,
    )

    await expect(page.locator('table, [data-testid="ap-aging-table"]')).toBeVisible({
      timeout: 15000,
    })

    const headers = page.locator('thead th')
    await expect(headers.filter({ hasText: /Supplier|Vendor|Nhà cung cấp/i })).toBeVisible()
    await expect(headers.filter({ hasText: /Current|Hiện tại/i })).toBeVisible()

    const agingBuckets = ['1-30', '31-60', '61-90', '90+', 'Over 90']
    let foundBucket = false
    for (const bucket of agingBuckets) {
      const bucketHeader = headers.filter({ hasText: new RegExp(bucket, 'i') })
      if ((await bucketHeader.count()) > 0) {
        foundBucket = true
        break
      }
    }
    expect(foundBucket).toBeTruthy()

    const totalRow = page.locator('tr').filter({ hasText: /Total|Tổng/i })
    if ((await totalRow.count()) > 0) {
      await expect(totalRow.first()).toBeVisible()
    }

    const asOfDate = page.locator(
      '[data-testid="as-of-date"], input[name="asOfDate"], button:has-text("As of"), [data-testid="date-filter"]',
    )
    if ((await asOfDate.count()) > 0) {
      await expect(asOfDate.first()).toBeVisible()
    }
  })

  test('RPT-007: AR aging report', async ({ page }) => {
    await page.goto('/ar-aging')

    await expect(page.locator('h1, [data-testid="page-title"]')).toContainText(
      /AR|Receivable|Aging|Phải thu|Tuổi nợ/i,
    )

    await expect(page.locator('table, [data-testid="ar-aging-table"]')).toBeVisible({
      timeout: 15000,
    })

    const headers = page.locator('thead th')
    await expect(headers.filter({ hasText: /Customer|Khách hàng/i })).toBeVisible()
    await expect(headers.filter({ hasText: /Current|Hiện tại/i })).toBeVisible()

    const agingBuckets = ['1-30', '31-60', '61-90', '90+', 'Over 90']
    let foundBucket = false
    for (const bucket of agingBuckets) {
      const bucketHeader = headers.filter({ hasText: new RegExp(bucket, 'i') })
      if ((await bucketHeader.count()) > 0) {
        foundBucket = true
        break
      }
    }
    expect(foundBucket).toBeTruthy()

    const totalRow = page.locator('tr').filter({ hasText: /Total|Tổng/i })
    if ((await totalRow.count()) > 0) {
      await expect(totalRow.first()).toBeVisible()
    }

    const exportButton = page.locator(
      'button:has-text("Export"), button:has-text("Xuất"), [data-testid="export-button"]',
    )
    if ((await exportButton.count()) > 0) {
      await exportButton.first().click()

      const exportOptions = page.locator('[role="menuitem"], [role="option"]')
      await expect(exportOptions.first()).toBeVisible({ timeout: 5000 })

      await page.keyboard.press('Escape')
    }

    const customerRow = page.locator('tbody tr').first()
    if ((await customerRow.count()) > 0) {
      const agingCell = customerRow.locator('td').nth(2)
      if ((await agingCell.count()) > 0) {
        await agingCell.click()

        const drillDown = page.locator(
          '[role="dialog"], [data-testid="drill-down"], .drill-down-panel, [data-state="open"]',
        )
        if ((await drillDown.count()) > 0) {
          await expect(drillDown).toBeVisible({ timeout: 5000 })
          await page.keyboard.press('Escape')
        }
      }
    }
  })

  test('RPT-008: AR statements', async ({ page }) => {
    await page.goto('/ar-statements')

    await expect(page.locator('h1, [data-testid="page-title"]')).toContainText(
      /Statement|AR|Customer|Sao kê|Khách hàng/i,
    )

    const customerSelector = page.locator(
      '[data-testid="customer-select"], button:has-text("Customer"), button:has-text("Khách hàng"), select[name="customer"], input[placeholder*="Customer"], input[placeholder*="customer"]',
    )

    if ((await customerSelector.count()) > 0) {
      await customerSelector.first().click()

      const customerOptions = page.locator('[role="option"], [role="menuitem"]')
      if ((await customerOptions.count()) > 0) {
        await customerOptions.first().click()
        await page.waitForLoadState('networkidle')
      }
    }

    const periodSelector = page.locator(
      '[data-testid="period-select"], button:has-text("Period"), input[type="date"], button[id="period"]',
    )
    if ((await periodSelector.count()) > 0) {
      await expect(periodSelector.first()).toBeVisible()
    }

    const generateButton = page.locator(
      'button:has-text("Generate"), button:has-text("Run"), button:has-text("Tạo"), button:has-text("View"), [data-testid="generate-statement"]',
    )
    if ((await generateButton.count()) > 0) {
      await generateButton.first().click()
      await page.waitForLoadState('networkidle')
    }

    const statementContent = page.locator(
      'table, [data-testid="statement-content"], .statement-preview, [data-testid="statement-table"]',
    )
    await expect(statementContent).toBeVisible({ timeout: 15000 })

    const printButton = page.locator(
      'button:has-text("Print"), button:has-text("In"), [data-testid="print-button"]',
    )
    if ((await printButton.count()) > 0) {
      await expect(printButton.first()).toBeVisible()
    }

    const emailButton = page.locator(
      'button:has-text("Email"), button:has-text("Send"), button:has-text("Gửi"), [data-testid="email-button"]',
    )
    if ((await emailButton.count()) > 0) {
      await expect(emailButton.first()).toBeVisible()
    }

    const exportButton = page.locator(
      'button:has-text("Export"), button:has-text("Download"), button:has-text("Xuất"), [data-testid="export-button"]',
    )
    if ((await exportButton.count()) > 0) {
      await expect(exportButton.first()).toBeVisible()
    }
  })
})
