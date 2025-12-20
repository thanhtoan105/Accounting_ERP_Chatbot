import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export type PaymentStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'POSTED' | 'CANCELLED' | 'REJECTED' | 'REVERSED';

export interface AllocatedBill {
  billNumber: string;
  allocatedAmount: number;
}

export class PaymentListPage extends BasePage {
  readonly path = '/payments';

  private readonly paymentsTable: Locator;
  private readonly newPaymentButton: Locator;
  private readonly searchInput: Locator;
  private readonly statusFilter: Locator;
  private readonly refreshButton: Locator;
  private readonly supplierIdInput: Locator;
  private readonly dateFromPicker: Locator;
  private readonly dateToPicker: Locator;

  constructor(page: Page) {
    super(page);
    this.paymentsTable = page.locator('table');
    this.newPaymentButton = page.getByRole('button', { name: /create payment/i });
    this.searchInput = page.getByPlaceholder(/search/i);
    this.statusFilter = page.locator('label:has-text("Status")').locator('..').locator('button[role="combobox"]');
    this.refreshButton = page.getByRole('button', { name: /refresh/i });
    this.supplierIdInput = page.locator('input[type="number"][placeholder*="Supplier"]');
    this.dateFromPicker = page.locator('label:has-text("Date From")').locator('..').locator('button');
    this.dateToPicker = page.locator('label:has-text("Date To")').locator('..').locator('button');
  }

  async waitForLoaded(): Promise<void> {
    await this.paymentsTable.waitFor({ state: 'visible' });
    await this.page.waitForLoadState('networkidle');
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async clickNewPayment(): Promise<void> {
    await this.newPaymentButton.click();
    await this.page.waitForURL(/\/payments\/new/);
  }

  async openPayment(paymentNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: paymentNumber });
    await row.click();
    await this.page.waitForURL(/\/payments\/\d+/);
  }

  async filterByStatus(status: 'all' | PaymentStatus): Promise<void> {
    await this.statusFilter.click();
    const statusLabels: Record<string, RegExp> = {
      all: /all status/i,
      DRAFT: /draft/i,
      PENDING_APPROVAL: /pending approval/i,
      POSTED: /posted/i,
      CANCELLED: /cancelled/i,
      REJECTED: /rejected/i,
      REVERSED: /reversed/i,
    };
    await this.page.getByRole('option', { name: statusLabels[status] }).click();
    await this.page.waitForLoadState('networkidle');
  }

  async filterBySupplier(supplierId: number): Promise<void> {
    await this.supplierIdInput.fill(supplierId.toString());
    await this.page.waitForLoadState('networkidle');
  }

  async searchPayments(searchText: string): Promise<void> {
    await this.searchInput.fill(searchText);
    await this.page.waitForTimeout(400);
    await this.page.waitForLoadState('networkidle');
  }

  async expectPaymentInList(paymentNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: paymentNumber });
    await expect(row).toBeVisible();
  }

  async expectPaymentNotInList(paymentNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: paymentNumber });
    await expect(row).toBeHidden();
  }

  async refresh(): Promise<void> {
    await this.refreshButton.click();
    await this.page.waitForLoadState('networkidle');
  }

  async getPaymentRowCount(): Promise<number> {
    return this.getTableRowCount();
  }
}

export class PaymentFormPage extends BasePage {
  readonly path = '/payments/new';

  private readonly paymentForm: Locator;
  private readonly supplierPicker: Locator;
  private readonly paymentDateButton: Locator;
  private readonly cashAccountSelect: Locator;
  private readonly bankAccountSelect: Locator;
  private readonly amountInput: Locator;
  private readonly referenceInput: Locator;
  private readonly paymentMethodSelect: Locator;
  private readonly payeeInput: Locator;
  private readonly standaloneSwitch: Locator;
  private readonly saveDraftButton: Locator;
  private readonly postButton: Locator;
  private readonly cancelPaymentButton: Locator;
  private readonly backButton: Locator;
  private readonly fifoAllocateButton: Locator;
  private readonly allocationGrid: Locator;

  constructor(page: Page) {
    super(page);
    this.paymentForm = page.locator('form');
    this.supplierPicker = page.locator('[data-testid="supplier-picker"]').or(
      page.locator('label:has-text("Supplier")').locator('..').locator('button, input').first()
    );
    this.paymentDateButton = page.locator('label:has-text("Payment Date")').locator('..').locator('button').first();
    this.cashAccountSelect = page.locator('label:has-text("Cash Account")').locator('..').locator('button[role="combobox"]');
    this.bankAccountSelect = page.locator('label:has-text("Bank Account")').locator('..').locator('button[role="combobox"]');
    this.amountInput = page.locator('input[type="number"]').filter({ hasText: '' }).or(
      page.locator('label:has-text("Amount")').locator('..').locator('input')
    );
    this.referenceInput = page.locator('input[placeholder*="reference" i]');
    this.paymentMethodSelect = page.locator('label:has-text("Payment Method")').locator('..').locator('button[role="combobox"]');
    this.payeeInput = page.locator('input[placeholder*="payee" i]');
    this.standaloneSwitch = page.locator('label:has-text("Standalone")').locator('..').locator('button[role="switch"]');
    this.saveDraftButton = page.getByRole('button', { name: /save|update/i }).first();
    this.postButton = page.getByRole('button', { name: /post payment/i });
    this.cancelPaymentButton = page.getByRole('button', { name: /cancel payment/i });
    this.backButton = page.locator('button').filter({ has: page.locator('svg.lucide-arrow-left') });
    this.fifoAllocateButton = page.getByRole('button', { name: /allocate fifo/i });
    this.allocationGrid = page.locator('table').filter({ hasText: /bill number/i });
  }

  async waitForLoaded(): Promise<void> {
    await this.paymentForm.waitFor({ state: 'visible' });
    await this.page.waitForLoadState('networkidle');
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async selectSupplier(supplierName: string): Promise<void> {
    await this.supplierPicker.click();
    await this.page.getByRole('option', { name: new RegExp(supplierName, 'i') }).click();
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

  async setPaymentDate(date: string): Promise<void> {
    await this.paymentDateButton.click();
    const [year, month, day] = date.split('-').map(Number);
    const calendar = this.page.locator('[role="grid"]');
    await calendar.waitFor({ state: 'visible' });
    await this.page.getByRole('gridcell', { name: String(day), exact: true }).click();
  }

  async setAmount(amount: number): Promise<void> {
    const input = this.page.locator('label:has-text("Amount")').locator('..').locator('input');
    await input.fill(amount.toString());
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

  async allocateToBill(billNumber: string, amount?: number): Promise<void> {
    const row = this.allocationGrid.locator('tbody tr', { hasText: billNumber });
    await expect(row).toBeVisible();
    if (amount !== undefined) {
      const input = row.locator('input[type="text"], input[type="number"]');
      await input.fill(amount.toString());
    }
  }

  async autoAllocateFIFO(): Promise<void> {
    await this.fifoAllocateButton.click();
    await this.page.waitForLoadState('networkidle');
  }

  async getUnallocatedAmount(): Promise<number> {
    const unallocatedText = await this.page.locator('text=/unallocated/i').locator('..').textContent();
    const match = unallocatedText?.match(/[\d,]+/);
    return match ? parseInt(match[0].replace(/,/g, '')) : 0;
  }

  async getAllocatedBills(): Promise<AllocatedBill[]> {
    const rows = this.allocationGrid.locator('tbody tr');
    const count = await rows.count();
    const bills: AllocatedBill[] = [];

    for (let i = 0; i < count; i++) {
      const row = rows.nth(i);
      const billNumber = await row.locator('td').nth(1).textContent();
      const allocatedText = await row.locator('td').nth(6).textContent();
      const allocatedAmount = parseInt(allocatedText?.replace(/[^\d]/g, '') || '0');
      if (billNumber && allocatedAmount > 0) {
        bills.push({ billNumber: billNumber.trim(), allocatedAmount });
      }
    }

    return bills;
  }

  async saveDraft(): Promise<void> {
    await this.saveDraftButton.click();
    await this.expectSuccessToast();
  }

  async post(): Promise<void> {
    await this.postButton.click();
    await this.waitForModal();
    await this.confirmDialog();
    await this.expectSuccessToast();
  }

  async cancel(): Promise<void> {
    await this.cancelPaymentButton.click();
    await this.expectSuccessToast();
  }

  async reverse(): Promise<void> {
    const reverseButton = this.page.getByRole('button', { name: /reverse/i });
    await reverseButton.click();
    await this.waitForModal();
    await this.confirmDialog();
    await this.expectSuccessToast();
  }

  async goBack(): Promise<void> {
    await this.backButton.click();
  }

  async getPaymentNumber(): Promise<string | null> {
    const header = this.page.locator('text=/Payment Number/i').locator('..').locator('text=/PAY-/');
    if (await header.isVisible()) {
      return (await header.textContent())?.trim() || null;
    }
    return null;
  }

  async expectStatus(status: PaymentStatus): Promise<void> {
    const badge = this.page.locator('[class*="badge"]', { hasText: new RegExp(status, 'i') });
    await expect(badge).toBeVisible();
  }
}
