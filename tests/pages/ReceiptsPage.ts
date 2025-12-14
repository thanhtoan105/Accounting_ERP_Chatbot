import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export type ReceiptStatus = 'DRAFT' | 'POSTED' | 'REVERSED';

export interface AllocationData {
  invoiceNumber: string;
  allocatedAmount: number;
}

export class ReceiptListPage extends BasePage {
  readonly path = '/accounting/receipts';

  private readonly receiptsTable: Locator;
  private readonly newReceiptButton: Locator;
  private readonly searchInput: Locator;
  private readonly statusFilter: Locator;
  private readonly refreshButton: Locator;
  private readonly dateFromPicker: Locator;
  private readonly dateToPicker: Locator;

  constructor(page: Page) {
    super(page);
    this.receiptsTable = page.locator('table');
    this.newReceiptButton = page.getByRole('button', { name: /New Receipt/i });
    this.searchInput = page.getByPlaceholder(/Search receipts/i);
    this.statusFilter = page.locator('button[role="combobox"]').filter({ hasText: /Status|All Status/i });
    this.refreshButton = page.getByRole('button').filter({ has: page.locator('svg.lucide-refresh-cw') });
    this.dateFromPicker = page.getByPlaceholder(/From date/i);
    this.dateToPicker = page.getByPlaceholder(/To date/i);
  }

  async waitForLoaded(): Promise<void> {
    await this.receiptsTable.waitFor({ state: 'visible' });
    await this.page.waitForLoadState('networkidle');
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async clickNewReceipt(): Promise<void> {
    await this.newReceiptButton.click();
    await this.page.waitForSelector('[role="dialog"], [data-state="open"]');
  }

  async openReceipt(receiptNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: receiptNumber });
    await row.click();
    await this.page.waitForURL(/\/accounting\/receipts\/\d+/);
  }

  async filterByStatus(status: 'all' | ReceiptStatus): Promise<void> {
    await this.statusFilter.click();
    const statusLabels: Record<string, RegExp> = {
      all: /all status/i,
      DRAFT: /draft/i,
      POSTED: /posted/i,
      REVERSED: /reversed/i,
    };
    await this.page.getByRole('option', { name: statusLabels[status] }).click();
    await this.page.waitForLoadState('networkidle');
  }

  async filterByCustomer(customerName: string): Promise<void> {
    await this.searchInput.fill(customerName);
    await this.page.waitForTimeout(400);
    await this.page.waitForLoadState('networkidle');
  }

  async searchReceipts(searchText: string): Promise<void> {
    await this.searchInput.fill(searchText);
    await this.page.waitForTimeout(400);
    await this.page.waitForLoadState('networkidle');
  }

  async expectReceiptInList(receiptNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: receiptNumber });
    await expect(row).toBeVisible();
  }

  async expectReceiptNotInList(receiptNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: receiptNumber });
    await expect(row).toBeHidden();
  }

  async refresh(): Promise<void> {
    await this.refreshButton.click();
    await this.page.waitForLoadState('networkidle');
  }

  async getReceiptRowCount(): Promise<number> {
    return this.getTableRowCount();
  }
}

export class ReceiptFormPage extends BasePage {
  readonly path = '/accounting/receipts/new';

  private readonly receiptForm: Locator;
  private readonly customerPicker: Locator;
  private readonly receiptDateButton: Locator;
  private readonly cashAccountSelect: Locator;
  private readonly bankAccountSelect: Locator;
  private readonly amountInput: Locator;
  private readonly referenceInput: Locator;
  private readonly paymentMethodSelect: Locator;
  private readonly payeeInput: Locator;
  private readonly standaloneSwitch: Locator;
  private readonly saveDraftButton: Locator;
  private readonly postButton: Locator;
  private readonly backButton: Locator;
  private readonly autoAllocateButton: Locator;
  private readonly allocationGrid: Locator;

  constructor(page: Page) {
    super(page);
    this.receiptForm = page.locator('form');
    this.customerPicker = page.locator('[data-testid="customer-picker"]').or(
      page.locator('label:has-text("Customer")').locator('..').locator('button, input').first()
    );
    this.receiptDateButton = page.locator('label:has-text("Receipt Date")').locator('..').locator('button').first();
    this.cashAccountSelect = page.locator('label:has-text("Cash Account")').locator('..').locator('button[role="combobox"]');
    this.bankAccountSelect = page.locator('label:has-text("Bank Account")').locator('..').locator('button[role="combobox"]');
    this.amountInput = page.locator('label:has-text("Amount")').locator('..').locator('input');
    this.referenceInput = page.locator('input[placeholder*="Reference" i]');
    this.paymentMethodSelect = page.locator('label:has-text("Payment Method")').locator('..').locator('button[role="combobox"]');
    this.payeeInput = page.locator('input[placeholder*="Customer name" i]');
    this.standaloneSwitch = page.locator('button[role="switch"]');
    this.saveDraftButton = page.getByRole('button', { name: /Save Draft/i });
    this.postButton = page.getByRole('button', { name: /Post Receipt/i });
    this.backButton = page.locator('button').filter({ has: page.locator('svg.lucide-arrow-left') });
    this.autoAllocateButton = page.getByRole('button', { name: /Auto-Allocate/i });
    this.allocationGrid = page.locator('table').filter({ hasText: /Invoice|Allocated/i });
  }

  async waitForLoaded(): Promise<void> {
    await this.receiptForm.waitFor({ state: 'visible' });
    await this.page.waitForLoadState('networkidle');
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async selectCustomer(customerName: string): Promise<void> {
    await this.customerPicker.click();
    await this.page.getByRole('option', { name: new RegExp(customerName, 'i') }).click();
    await this.page.waitForLoadState('networkidle');
  }

  async selectCashAccount(accountName: string): Promise<void> {
    await this.cashAccountSelect.click();
    await this.page.getByRole('option', { name: new RegExp(accountName, 'i') }).click();
  }

  async selectBankAccount(accountName: string): Promise<void> {
    await this.bankAccountSelect.click();
    await this.page.getByRole('option', { name: new RegExp(accountName, 'i') }).click();
  }

  async setReceiptDate(date: string): Promise<void> {
    await this.receiptDateButton.click();
    const [year, month, day] = date.split('-').map(Number);
    const calendar = this.page.locator('[role="grid"]');
    await calendar.waitFor({ state: 'visible' });
    await this.page.getByRole('gridcell', { name: String(day), exact: true }).click();
  }

  async setAmount(amount: number): Promise<void> {
    await this.amountInput.fill(amount.toString());
  }

  async setReference(ref: string): Promise<void> {
    await this.referenceInput.fill(ref);
  }

  async setPayee(payee: string): Promise<void> {
    await this.payeeInput.fill(payee);
  }

  async selectPaymentMethod(method: 'CASH' | 'BANK_TRANSFER' | 'CHECK' | 'OTHER'): Promise<void> {
    await this.paymentMethodSelect.click();
    const methodLabels: Record<string, string> = {
      CASH: 'Cash',
      BANK_TRANSFER: 'Bank Transfer',
      CHECK: 'Check',
      OTHER: 'Other',
    };
    await this.page.getByRole('option', { name: methodLabels[method] }).click();
  }

  async toggleStandalone(enabled: boolean): Promise<void> {
    const isChecked = await this.standaloneSwitch.getAttribute('data-state') === 'checked';
    if (isChecked !== enabled) {
      await this.standaloneSwitch.click();
    }
  }

  async allocateToInvoice(invoiceNumber: string, amount?: number): Promise<void> {
    const row = this.allocationGrid.locator('tbody tr', { hasText: invoiceNumber });
    await expect(row).toBeVisible();
    if (amount !== undefined) {
      const input = row.locator('input[type="text"], input[type="number"]');
      await input.fill(amount.toString());
    }
  }

  async autoAllocateFIFO(): Promise<void> {
    await this.autoAllocateButton.click();
    await this.page.waitForLoadState('networkidle');
  }

  async getUnallocatedAmount(): Promise<number> {
    const unallocatedText = await this.page.locator('text=/Unallocated/i').locator('..').textContent();
    const match = unallocatedText?.match(/[\d,]+/);
    return match ? parseInt(match[0].replace(/,/g, '')) : 0;
  }

  async getAllocatedInvoices(): Promise<AllocationData[]> {
    const rows = this.allocationGrid.locator('tbody tr');
    const count = await rows.count();
    const allocations: AllocationData[] = [];

    for (let i = 0; i < count; i++) {
      const row = rows.nth(i);
      const invoiceNumber = await row.locator('td').nth(0).textContent();
      const allocatedInput = row.locator('input[type="text"], input[type="number"]');
      const allocatedText = await allocatedInput.inputValue().catch(() => '0');
      const allocatedAmount = parseInt(allocatedText?.replace(/[^\d]/g, '') || '0');
      if (invoiceNumber && allocatedAmount > 0) {
        allocations.push({ invoiceNumber: invoiceNumber.trim(), allocatedAmount });
      }
    }

    return allocations;
  }

  async saveDraft(): Promise<void> {
    await this.saveDraftButton.click();
    await this.expectSuccessToast();
  }

  async post(): Promise<void> {
    await this.postButton.click();
    await this.expectSuccessToast();
  }

  async goBack(): Promise<void> {
    await this.backButton.click();
  }

  async getReceiptNumber(): Promise<string | null> {
    const header = this.page.locator('text=/REC-/');
    if (await header.isVisible()) {
      return (await header.textContent())?.trim() || null;
    }
    return null;
  }

  async expectStatus(status: ReceiptStatus): Promise<void> {
    const badge = this.page.locator('[class*="badge"]', { hasText: new RegExp(status, 'i') });
    await expect(badge).toBeVisible();
  }
}
