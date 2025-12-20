import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export class AnalyticsPage extends BasePage {
	readonly path = '/analytics';

	private readonly pageTitle = () =>
		this.page.locator('h1').filter({ hasText: /analytics|dashboard/i });
	private readonly dateRangeFrom = () =>
		this.page.locator('[data-testid="date-from"], input[name="from"]');
	private readonly dateRangeTo = () =>
		this.page.locator('[data-testid="date-to"], input[name="to"]');
	private readonly periodSelector = () =>
		this.page.locator(
			'[data-testid="period-select"], button[id="period"], [data-testid="period-dropdown"]',
		);
	private readonly widgets = () =>
		this.page.locator(
			'[data-testid^="widget-"], .dashboard-widget, [class*="widget"]',
		);
	private readonly charts = () =>
		this.page.locator(
			'[data-testid^="chart-"], .recharts-wrapper, canvas, svg.chart',
		);

	constructor(page: Page) {
		super(page);
	}

	async waitForLoaded(): Promise<void> {
		await this.page.waitForLoadState('networkidle');
		await expect(this.pageTitle()).toBeVisible({ timeout: 15000 });
	}

	async navigate(): Promise<void> {
		await this.goto(this.path);
		await this.waitForLoaded();
	}

	async expectWidgetsLoaded(): Promise<void> {
		await this.page.waitForLoadState('networkidle');

		const widgetContainer = this.page.locator(
			'[data-testid="analytics-dashboard"], [data-testid="dashboard-widgets"], main, [role="main"]',
		);
		await expect(widgetContainer.first()).toBeVisible({ timeout: 10000 });

		const widgetElements = this.widgets();
		const widgetCount = await widgetElements.count();

		if (widgetCount === 0) {
			const cards = this.page.locator(
				'[class*="card"], [data-testid*="card"], .stat-card, .metric-card',
			);
			await expect(cards.first()).toBeVisible({ timeout: 10000 });
		} else {
			await expect(widgetElements.first()).toBeVisible();
		}
	}

	async selectDateRange(from: string, to: string): Promise<void> {
		const fromInput = this.dateRangeFrom();
		const toInput = this.dateRangeTo();

		if ((await fromInput.count()) > 0) {
			await fromInput.fill(from);
			await toInput.fill(to);
			await this.page.waitForLoadState('networkidle');
		} else {
			const datePickerTrigger = this.page.locator(
				'[data-testid="date-range-picker"], button:has-text("Date"), button:has-text("Ngày")',
			);
			if ((await datePickerTrigger.count()) > 0) {
				await datePickerTrigger.first().click();
				await this.page
					.getByRole('textbox', { name: /from|start|từ/i })
					.fill(from);
				await this.page.getByRole('textbox', { name: /to|end|đến/i }).fill(to);
				await this.page.getByRole('button', { name: /apply|áp dụng/i }).click();
				await this.page.waitForLoadState('networkidle');
			}
		}
	}

	async selectPeriod(
		period: 'week' | 'month' | 'quarter' | 'year',
	): Promise<void> {
		const periodMap: Record<string, RegExp> = {
			week: /week|tuần/i,
			month: /month|tháng/i,
			quarter: /quarter|quý/i,
			year: /year|năm/i,
		};

		const selector = this.periodSelector();
		if ((await selector.count()) > 0) {
			await selector.first().click();
			await this.page.getByRole('option', { name: periodMap[period] }).click();
			await this.page.waitForLoadState('networkidle');
		} else {
			const periodButton = this.page.getByRole('button', {
				name: periodMap[period],
			});
			if ((await periodButton.count()) > 0) {
				await periodButton.click();
				await this.page.waitForLoadState('networkidle');
			}
		}
	}

	async getWidgetValue(widgetName: string): Promise<string> {
		const widget = this.page.locator(
			`[data-testid="widget-${widgetName}"], [data-widget="${widgetName}"]`,
		);
		if ((await widget.count()) > 0) {
			const valueElement = widget.locator(
				'[data-testid="widget-value"], .widget-value, .metric-value',
			);
			return (await valueElement.textContent()) ?? '';
		}

		const widgetByText = this.page
			.locator(`[class*="widget"], [class*="card"]`)
			.filter({ hasText: widgetName });
		if ((await widgetByText.count()) > 0) {
			const value = widgetByText.locator('span, p, .value').first();
			return (await value.textContent()) ?? '';
		}

		return '';
	}

	async hoverChart(chartId: string, dataPoint: number): Promise<void> {
		const chart = this.page.locator(
			`[data-testid="chart-${chartId}"], [data-chart="${chartId}"], #${chartId}`,
		);
		if ((await chart.count()) > 0) {
			const chartArea = chart.locator('.recharts-surface, canvas, svg').first();
			const box = await chartArea.boundingBox();
			if (box) {
				const x = box.x + (box.width / 10) * dataPoint;
				const y = box.y + box.height / 2;
				await this.page.mouse.move(x, y);
				await this.page.waitForTimeout(500);
			}
		}
	}

	async clickChartElement(chartId: string): Promise<void> {
		const chart = this.page.locator(
			`[data-testid="chart-${chartId}"], [data-chart="${chartId}"], #${chartId}`,
		);
		if ((await chart.count()) > 0) {
			const clickableElement = chart
				.locator(
					'.recharts-bar-rectangle, .recharts-pie-sector, .recharts-line-dot, path[class*="chart"]',
				)
				.first();
			if ((await clickableElement.count()) > 0) {
				await clickableElement.click();
			} else {
				await chart.click();
			}
			await this.page.waitForTimeout(300);
		}
	}

	async expectChartVisible(chartId: string): Promise<void> {
		const chart = this.page.locator(
			`[data-testid="chart-${chartId}"], [data-chart="${chartId}"], #${chartId}`,
		);
		await expect(chart.first()).toBeVisible({ timeout: 10000 });
	}

	async expectTooltipVisible(): Promise<void> {
		const tooltip = this.page.locator(
			'.recharts-tooltip-wrapper, [role="tooltip"], .chart-tooltip',
		);
		await expect(tooltip.first()).toBeVisible({ timeout: 5000 });
	}

	async expectAccessDenied(): Promise<void> {
		const accessDenied = this.page.locator(
			'[data-testid="access-denied"], [class*="forbidden"], [class*="unauthorized"]',
		);
		const errorMessage = this.page.locator(
			'text=/access denied|không có quyền|forbidden|403/i',
		);
		const redirectToLogin = this.page.locator(
			'input[type="password"], [data-testid="login-form"]',
		);

		await expect(
			accessDenied.or(errorMessage).or(redirectToLogin).first(),
		).toBeVisible({ timeout: 10000 });
	}
}
