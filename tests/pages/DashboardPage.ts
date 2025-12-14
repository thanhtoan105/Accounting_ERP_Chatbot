import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export class DashboardPage extends BasePage {
  readonly path = '/';

  private readonly dashboardTitle = () => this.page.locator('h1').filter({ hasText: /dashboard/i });
  private readonly welcomeMessage = () => this.page.locator('p').filter({ hasText: /welcome/i });
  private readonly arOverdueTiles = () => this.page.locator('[data-testid="ar-overdue-tiles"]');
  private readonly sidebar = () => this.page.locator('nav, [role="navigation"]');

  constructor(page: Page) {
    super(page);
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.dashboardTitle()).toBeVisible({ timeout: 10000 });
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
  }

  async expectWelcomeMessage(userName?: string): Promise<void> {
    await expect(this.welcomeMessage()).toBeVisible();
    if (userName) {
      await expect(this.welcomeMessage()).toContainText(userName);
    }
  }

  async expectWidgetsLoaded(): Promise<void> {
    await expect(this.dashboardTitle()).toBeVisible();
    await this.page.waitForLoadState('networkidle');
  }

  async expectAROverdueTilesVisible(): Promise<void> {
    await expect(this.arOverdueTiles()).toBeVisible();
  }

  async navigateToSection(sectionName: string): Promise<void> {
    const navLink = this.sidebar().getByRole('link', { name: new RegExp(sectionName, 'i') });
    await navLink.click();
  }

  async navigateToVouchers(): Promise<void> {
    await this.navigateToSection('vouchers');
  }

  async navigateToChartOfAccounts(): Promise<void> {
    await this.navigateToSection('chart of accounts');
  }

  async navigateToReports(): Promise<void> {
    await this.navigateToSection('reports');
  }

  async getUserDisplayName(): Promise<string> {
    const welcomeText = await this.welcomeMessage().textContent();
    return welcomeText || '';
  }
}
