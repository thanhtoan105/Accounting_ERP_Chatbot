import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export interface BankAccountData {
  name: string;
  bankName: string;
  accountNumber: string;
  currency: string;
}

export class BankAccountsPage extends BasePage {
  readonly path = '/bank-accounts';

  constructor(page: Page) {
    super(page);
  }

  async waitForLoaded(): Promise<void> {
    await this.page.waitForSelector('table');
    await this.waitForPageReady();
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async createBankAccount(data: BankAccountData): Promise<void> {
    await this.page.getByRole('button', { name: /add bank account/i }).click();
    await this.waitForModal();
    await this.fillBankAccountForm(data);
    await this.page.getByRole('button', { name: /create|save/i }).click();
    await this.expectSuccessToast('Bank account created successfully');
    await expect(this.page.locator('[role="dialog"]')).toBeHidden();
  }

  async editBankAccount(name: string, data: Partial<BankAccountData>): Promise<void> {
    await this.openBankAccountActions(name);
    await this.page.getByRole('menuitem', { name: /edit/i }).click();
    await this.waitForModal();
    await this.fillBankAccountForm(data);
    await this.page.getByRole('button', { name: /update|save/i }).click();
    await this.expectSuccessToast('Bank account updated successfully');
    await expect(this.page.locator('[role="dialog"]')).toBeHidden();
  }

  async deleteBankAccount(name: string): Promise<void> {
    await this.openBankAccountActions(name);
    await this.page.getByRole('menuitem', { name: /delete/i }).click();
    await expect(this.page.locator('[role="alertdialog"]')).toBeVisible();
    await this.page
      .locator('[role="alertdialog"]')
      .getByRole('button', { name: /delete/i })
      .click();
    await this.expectSuccessToast('Bank account deleted successfully');
  }

  async expectBankAccountInList(name: string): Promise<void> {
    await expect(this.page.locator('table tbody').getByText(name)).toBeVisible();
  }

  async expectBankAccountNotInList(name: string): Promise<void> {
    await expect(this.page.locator('table tbody').getByText(name)).toBeHidden();
  }

  async validateAccountNumber(accountNumber: string): Promise<void> {
    const accountNumberInput = this.page.locator('#accountNumber');
    await accountNumberInput.fill(accountNumber);
    await accountNumberInput.blur();
    await this.page.waitForTimeout(300);
  }

  async expectValidationError(message: string): Promise<void> {
    await expect(this.page.getByText(message)).toBeVisible();
  }

  async searchBankAccount(query: string): Promise<void> {
    const searchInput = this.page.getByPlaceholder(/search/i);
    await searchInput.fill(query);
    await this.page.waitForTimeout(350);
  }

  async getBankAccountCount(): Promise<number> {
    const rows = this.page.locator('table tbody tr');
    return rows.count();
  }

  private async openBankAccountActions(name: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: name });
    await row.getByRole('button').click();
  }

  private async fillBankAccountForm(data: Partial<BankAccountData>): Promise<void> {
    if (data.name !== undefined) {
      await this.page.locator('#name').fill(data.name);
    }
    if (data.bankName !== undefined) {
      await this.page.locator('#bankName').fill(data.bankName);
    }
    if (data.accountNumber !== undefined) {
      await this.page.locator('#accountNumber').fill(data.accountNumber);
    }
    if (data.currency !== undefined) {
      await this.page.getByLabel(/currency/i).click();
      await this.page.getByRole('option', { name: data.currency }).click();
    }
  }
}
