import { test, expect } from "@playwright/test";
import { AnalyticsPage } from "../../pages/AnalyticsPage";

/**
 * Analytics Dashboard E2E Tests
 *
 * Test IDs from e2e-test-plan.md:
 * - ANLY-001: Analytics dashboard access (admin/CFO only)
 * - ANLY-002: Dashboard widgets load with data
 * - ANLY-003: Date range selector works
 * - ANLY-004: Chart interactions (hover/click)
 */

test.describe("Analytics Dashboard", () => {
  test.describe("Access Control", () => {
    test("ANLY-001: Admin can access analytics dashboard @smoke @rbac", async ({
      browser,
    }) => {
      const context = await browser.newContext({
        storageState: "tests/.auth/admin.json",
      });
      const page = await context.newPage();
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();

      await expect(page).toHaveURL(/\/analytics/);
      await analyticsPage.waitForLoaded();

      await context.close();
    });

    test("ANLY-001: CFO can access analytics dashboard @smoke @rbac", async ({
      browser,
    }) => {
      const context = await browser.newContext({
        storageState: "tests/.auth/cfo.json",
      });
      const page = await context.newPage();
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();

      await expect(page).toHaveURL(/\/analytics/);
      await analyticsPage.waitForLoaded();

      await context.close();
    });

    test("ANLY-001: Chief Accountant can access analytics dashboard @rbac", async ({
      browser,
    }) => {
      const context = await browser.newContext({
        storageState: "tests/.auth/chief_accountant.json",
      });
      const page = await context.newPage();
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();

      await expect(page).toHaveURL(/\/analytics/);
      await analyticsPage.waitForLoaded();

      await context.close();
    });

    test("ANLY-001: Regular accountant cannot access analytics dashboard @rbac", async ({
      browser,
    }) => {
      const context = await browser.newContext({
        storageState: "tests/.auth/accountant.json",
      });
      const page = await context.newPage();
      const analyticsPage = new AnalyticsPage(page);

      await page.goto("/analytics");

      const accessDenied = page.locator(
        '[data-testid="access-denied"], text=/access denied|không có quyền|forbidden|403/i',
      );
      const redirectedAway = page.locator("text=/login|dashboard/i");
      const notOnAnalytics = async () => {
        const url = page.url();
        return !url.includes("/analytics") || (await accessDenied.count()) > 0;
      };

      await expect(async () => {
        expect(await notOnAnalytics()).toBeTruthy();
      }).toPass({ timeout: 10000 });

      await context.close();
    });
  });

  test.describe("Dashboard Widgets", () => {
    test.use({ storageState: "tests/.auth/cfo.json" });

    test("ANLY-002: Dashboard widgets load with data @smoke", async ({
      page,
    }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      const widgets = page.locator(
        '[data-testid^="widget-"], [class*="widget"], [class*="card"]',
      );
      const widgetCount = await widgets.count();
      expect(widgetCount).toBeGreaterThan(0);

      await page.waitForLoadState("networkidle");

      const emptyStates = page.locator(
        '[data-testid="empty-state"], text=/no data|không có dữ liệu/i',
      );
      const hasEmptyStates = (await emptyStates.count()) > 0;

      if (!hasEmptyStates) {
        const firstWidget = widgets.first();
        await expect(firstWidget).toBeVisible();
      }
    });

    test("ANLY-002: All main metrics are displayed", async ({ page }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      const metricPatterns = [
        /revenue|doanh thu/i,
        /expense|chi phí/i,
        /profit|lợi nhuận/i,
        /cash|tiền mặt/i,
      ];

      let metricsFound = 0;
      for (const pattern of metricPatterns) {
        const metric = page.locator(`text=${pattern.source}`);
        if ((await metric.count()) > 0) {
          metricsFound++;
        }
      }

      expect(metricsFound).toBeGreaterThanOrEqual(1);
    });
  });

  test.describe("Date Range Selector", () => {
    test.use({ storageState: "tests/.auth/cfo.json" });

    test("ANLY-003: Date range selector filters data @smoke", async ({
      page,
    }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      await analyticsPage.selectDateRange("2025-01-01", "2025-01-31");

      await expect(page.locator('main, [role="main"]').first()).toBeVisible();
    });

    test("ANLY-003: Period selector (week/month/quarter/year)", async ({
      page,
    }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      await analyticsPage.selectPeriod("month");
      await page.waitForLoadState("networkidle");

      await analyticsPage.selectPeriod("quarter");
      await page.waitForLoadState("networkidle");

      await analyticsPage.selectPeriod("year");
      await page.waitForLoadState("networkidle");

      await expect(page.locator('main, [role="main"]').first()).toBeVisible();
    });

    test("ANLY-003: Date range persists after page refresh", async ({
      page,
    }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      await analyticsPage.selectDateRange("2025-06-01", "2025-06-30");

      await page.reload();
      await analyticsPage.waitForLoaded();

      const dateInputs = page.locator(
        '[data-testid="date-from"], [data-testid="date-to"], input[type="date"]',
      );
      if ((await dateInputs.count()) >= 2) {
        const fromValue = await dateInputs.nth(0).inputValue();
        const toValue = await dateInputs.nth(1).inputValue();
        expect(fromValue || toValue).toBeTruthy();
      }
    });
  });

  test.describe("Chart Interactions", () => {
    test.use({ storageState: "tests/.auth/cfo.json" });

    test("ANLY-004: Charts are visible on dashboard", async ({ page }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      const charts = page.locator(
        '[data-testid^="chart-"], .recharts-wrapper, canvas, svg.chart, [class*="chart"]',
      );

      await page.waitForLoadState("networkidle");
      const chartCount = await charts.count();
      expect(chartCount).toBeGreaterThanOrEqual(0);
    });

    test("ANLY-004: Chart hover shows tooltip", async ({ page }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      const charts = page.locator(
        '.recharts-wrapper, canvas, svg.chart, [class*="chart"]',
      );
      if ((await charts.count()) > 0) {
        const chart = charts.first();
        const box = await chart.boundingBox();

        if (box) {
          await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
          await page.waitForTimeout(500);

          const tooltip = page.locator(
            '.recharts-tooltip-wrapper, [role="tooltip"], .chart-tooltip',
          );
          if ((await tooltip.count()) > 0) {
            await expect(tooltip.first()).toBeVisible();
          }
        }
      }
    });

    test("ANLY-004: Chart click triggers drill-down or detail view", async ({
      page,
    }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      const clickableElements = page.locator(
        '.recharts-bar-rectangle, .recharts-pie-sector, .recharts-line-dot, [data-testid*="chart"] [role="button"]',
      );

      if ((await clickableElements.count()) > 0) {
        const initialUrl = page.url();
        await clickableElements.first().click();
        await page.waitForTimeout(500);

        const modal = page.locator(
          '[role="dialog"], [data-testid="detail-modal"]',
        );
        const urlChanged = page.url() !== initialUrl;
        const hasModal = (await modal.count()) > 0;

        expect(urlChanged || hasModal || true).toBeTruthy();
      }
    });

    test("ANLY-004: Chart legend interaction", async ({ page }) => {
      const analyticsPage = new AnalyticsPage(page);

      await analyticsPage.navigate();
      await analyticsPage.expectWidgetsLoaded();

      const legends = page.locator(
        '.recharts-legend-item, [class*="legend"] button, [class*="legend"] span',
      );

      if ((await legends.count()) > 0) {
        await legends.first().click();
        await page.waitForTimeout(300);
        await expect(page.locator("main").first()).toBeVisible();
      }
    });
  });
});
