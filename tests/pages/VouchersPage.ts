import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export class VoucherListPage extends BasePage {
  readonly path = '/vouchers';

  private readonly pageTitle = () => this.page.locator('h1').filter({ hasText: /vouchers/i });
  private readonly createVoucherButton = () => this.page.getByRole('button', { name: /create voucher/i });
  private readonly refreshButton = () => this.page.getByRole('button', { name: /refresh/i });
  private readonly searchInput = () => this.page.getByPlaceholder(/voucher number|description/i);
  private readonly statusSelect = () => this.page.locator('button').filter({ hasText: /all status|draft|posted|unposted/i }).first();
  private readonly voucherTable = () => this.page.locator('table');
  private readonly tableRows = () => this.page.locator('table tbody tr');
  private readonly draftBadge = () => this.page.locator('text=Draft:').first();
  private readonly postedBadge = () => this.page.locator('text=Posted:').first();
  private readonly unpostedBadge = () => this.page.locator('text=Unposted:').first();

  constructor(page: Page) {
    super(page);
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.pageTitle()).toBeVisible();
    await expect(this.voucherTable()).toBeVisible();
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
  }

  async clickNewVoucher(): Promise<void> {
    await this.createVoucherButton().click();
    await expect(this.page).toHaveURL(/\/vouchers\/new/);
  }

  async filterByStatus(status: 'all' | 'draft' | 'posted' | 'unposted'): Promise<void> {
    await this.statusSelect().click();
    const optionText = status === 'all' ? 'All Status' : status.charAt(0).toUpperCase() + status.slice(1);
    await this.page.getByRole('option', { name: optionText }).click();
    await this.page.waitForLoadState('networkidle');
  }

  async searchVouchers(searchText: string): Promise<void> {
    await this.searchInput().fill(searchText);
    await this.page.waitForTimeout(300);
    await this.page.waitForLoadState('networkidle');
  }

  async clearSearch(): Promise<void> {
    await this.searchInput().clear();
    await this.page.waitForTimeout(300);
  }

  async refreshList(): Promise<void> {
    await this.refreshButton().click();
    await this.page.waitForLoadState('networkidle');
  }

  async getVoucherCount(): Promise<number> {
    return await this.tableRows().count();
  }

  async clickVoucherRow(index: number): Promise<void> {
    await this.tableRows().nth(index).click();
  }

  async openVoucherByNumber(voucherNumber: string): Promise<void> {
    await this.page.getByRole('cell', { name: voucherNumber }).click();
  }

  async deleteVoucher(voucherNumber: string, reason: string): Promise<void> {
    const row = this.page.locator('tr', { has: this.page.locator(`text=${voucherNumber}`) });
    await row.locator('button').filter({ has: this.page.locator('svg') }).last().click();
    await this.page.getByRole('menuitem', { name: /delete/i }).click();
    await this.page.locator('#delete-reason').fill(reason);
    await this.page.getByRole('button', { name: /delete/i }).click();
  }

  async expectEmptyState(): Promise<void> {
    await expect(this.page.getByText(/no vouchers found/i)).toBeVisible();
  }

  async getStatusCounts(): Promise<{ draft: number; posted: number; unposted: number }> {
    const draftText = await this.draftBadge().textContent();
    const postedText = await this.postedBadge().textContent();
    const unpostedText = await this.unpostedBadge().textContent();

    return {
      draft: parseInt(draftText?.match(/\d+/)?.[0] || '0'),
      posted: parseInt(postedText?.match(/\d+/)?.[0] || '0'),
      unposted: parseInt(unpostedText?.match(/\d+/)?.[0] || '0'),
    };
  }
}

export class VoucherFormPage extends BasePage {
  readonly path = '/vouchers/new';

  private readonly pageTitle = () => this.page.locator('h1, h2').filter({ hasText: /voucher|create|edit/i }).first();
  private readonly voucherDateButton = () => this.page.getByRole('button', { name: /select date|\d{2}\/\d{2}\/\d{4}/i });
  private readonly descriptionInput = () => this.page.locator('textarea').first();
  private readonly saveDraftButton = () => this.page.getByRole('button', { name: /save draft/i });
  private readonly postButton = () => this.page.getByRole('button', { name: /^post$/i });
  private readonly unpostButton = () => this.page.getByRole('button', { name: /unpost/i });
  private readonly reverseButton = () => this.page.getByRole('button', { name: /reverse/i });
  private readonly templateButton = () => this.page.getByRole('button', { name: /template/i });
  private readonly entryLinesCard = () => this.page.locator('text=Entry lines').first();
  private readonly attachmentsCard = () => this.page.locator('text=Attachments').first();

  constructor(page: Page) {
    super(page);
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.saveDraftButton()).toBeVisible({ timeout: 10000 });
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
  }

  async navigateToEdit(voucherId: string): Promise<void> {
    await this.goto(`/vouchers/${voucherId}`);
  }

  async selectVoucherDate(date: Date): Promise<void> {
    await this.voucherDateButton().click();
    const day = date.getDate().toString();
    await this.page.getByRole('gridcell', { name: day, exact: true }).click();
  }

  async fillDescription(description: string): Promise<void> {
    await this.descriptionInput().fill(description);
  }

  async selectVoucherType(typeName: string): Promise<void> {
    await this.templateButton().click();
    await this.page.getByRole('option', { name: new RegExp(typeName, 'i') }).click();
  }

  async addLine(debitAccount: string, creditAccount: string, amount: number, description?: string): Promise<void> {
    const lastRow = this.page.locator('[data-testid="voucher-line-row"]').last();

    const debitCell = lastRow.locator('[data-testid="debit-account-cell"]');
    await debitCell.click();
    await this.page.getByRole('option', { name: new RegExp(debitAccount, 'i') }).click();

    const creditCell = lastRow.locator('[data-testid="credit-account-cell"]');
    await creditCell.click();
    await this.page.getByRole('option', { name: new RegExp(creditAccount, 'i') }).click();

    const amountCell = lastRow.locator('[data-testid="amount-cell"] input');
    await amountCell.fill(amount.toString());

    if (description) {
      const descCell = lastRow.locator('[data-testid="description-cell"] input');
      await descCell.fill(description);
    }
  }

  async saveDraft(): Promise<void> {
    await this.saveDraftButton().click();
    await this.expectSuccessToast();
  }

  async post(): Promise<void> {
    await this.postButton().click();
    await this.expectSuccessToast();
  }

  async unpost(reason: string): Promise<void> {
    await this.unpostButton().click();
    await this.page.locator('#unpost-reason').fill(reason);
    await this.page.getByRole('button', { name: /xác nhận|confirm/i }).click();
    await this.expectSuccessToast();
  }

  async reverse(description: string, reason: string): Promise<void> {
    await this.reverseButton().click();
    await this.page.locator('#reverse-description').fill(description);
    await this.page.locator('#reverse-reason').fill(reason);
    await this.page.getByRole('button', { name: /xác nhận|confirm/i }).click();
    await this.expectSuccessToast();
  }

  async expectValidationErrors(): Promise<void> {
    await expect(this.page.locator('[data-slot="alert"][data-variant="destructive"], .text-destructive')).toBeVisible();
  }

  async expectStatus(status: 'draft' | 'posted' | 'unposted'): Promise<void> {
    const badge = this.page.locator('[data-slot="badge"]', { hasText: new RegExp(status, 'i') });
    await expect(badge).toBeVisible();
  }

  async getVoucherNumber(): Promise<string> {
    const voucherNumberElement = this.page.locator('text=/[A-Z]+-\\d+/').first();
    return (await voucherNumberElement.textContent()) || '';
  }

  async uploadAttachment(filePath: string): Promise<void> {
    const fileInput = this.page.locator('input[type="file"]');
    await fileInput.setInputFiles(filePath);
  }

  async expectDraftAutoSaved(): Promise<void> {
    await expect(this.page.getByText(/draft saved|saving/i)).toBeVisible();
  }
}
