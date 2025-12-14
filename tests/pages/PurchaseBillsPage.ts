import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export interface LineItemData {
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: 'ZERO' | 'FIVE' | 'TEN' | 'EXEMPT';
  accountCode?: string;
}

export type PurchaseBillStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'POSTED'
  | 'REJECTED'
  | 'PAID'
  | 'PARTIALLY_PAID';

export class PurchaseBillListPage extends BasePage {
  readonly path = '/purchase-bills';

  private readonly billsTable: Locator;
  private readonly createButton: Locator;
  private readonly refreshButton: Locator;
  private readonly searchInput: Locator;
  private readonly statusFilter: Locator;
  private readonly supplierIdInput: Locator;
  private readonly dateFromPicker: Locator;
  private readonly dateToPicker: Locator;

  constructor(page: Page) {
    super(page);
    this.billsTable = page.locator('table');
    this.createButton = page.getByRole('button', { name: /Create Purchase Bill/i });
    this.refreshButton = page.getByRole('button', { name: /Refresh/i });
    this.searchInput = page.getByPlaceholder(/Bill number, reference/i);
    this.statusFilter = page.locator('label:has-text("Status")').locator('..').locator('button');
    this.supplierIdInput = page.locator('input[type="number"][placeholder*="Supplier"]');
    this.dateFromPicker = page.locator('label:has-text("Date From")').locator('..').locator('button');
    this.dateToPicker = page.locator('label:has-text("Date To")').locator('..').locator('button');
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.billsTable).toBeVisible({ timeout: 10000 });
    await this.page.waitForLoadState('networkidle');
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async clickNewBill(): Promise<void> {
    await this.createButton.click();
    await this.page.waitForURL(/\/purchase-bills\/new/);
  }

  async openBill(billNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: billNumber });
    await row.click();
    await this.page.waitForURL(/\/purchase-bills\/\d+/);
  }

  async filterByStatus(
    status: 'all' | 'draft' | 'pending' | 'posted' | 'rejected' | 'paid' | 'partially_paid',
  ): Promise<void> {
    await this.statusFilter.click();
    const statusMap: Record<string, string> = {
      all: 'All Status',
      draft: 'Draft',
      pending: 'Pending Approval',
      posted: 'Posted',
      rejected: 'Rejected',
      paid: 'Paid',
      partially_paid: 'Partially Paid',
    };
    await this.page.getByRole('option', { name: statusMap[status] }).click();
    await this.page.waitForTimeout(300);
  }

  async filterBySupplier(supplierId: number): Promise<void> {
    await this.supplierIdInput.fill(supplierId.toString());
    await this.page.waitForTimeout(300);
  }

  async searchBills(searchText: string): Promise<void> {
    await this.searchInput.fill(searchText);
    await this.page.waitForTimeout(300);
  }

  async refresh(): Promise<void> {
    await this.refreshButton.click();
    await this.waitForPageReady();
  }

  async expectBillInList(billNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: billNumber });
    await expect(row).toBeVisible({ timeout: 10000 });
  }

  async expectBillNotInList(billNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: billNumber });
    await expect(row).toBeHidden();
  }

  async getBillCount(): Promise<number> {
    return this.getTableRowCount();
  }
}

export class PurchaseBillFormPage extends BasePage {
  readonly path = '/purchase-bills/new';

  private readonly billForm: Locator;
  private readonly supplierPicker: Locator;
  private readonly billNumberInput: Locator;
  private readonly billDateButton: Locator;
  private readonly dueDateButton: Locator;
  private readonly referenceInput: Locator;
  private readonly descriptionTextarea: Locator;
  private readonly lineItemsCard: Locator;
  private readonly summaryCard: Locator;
  private readonly saveButton: Locator;
  private readonly submitForApprovalButton: Locator;
  private readonly approveButton: Locator;
  private readonly rejectButton: Locator;
  private readonly cancelButton: Locator;
  private readonly validateButton: Locator;

  constructor(page: Page) {
    super(page);
    this.billForm = page.locator('form');
    this.supplierPicker = page.locator('[data-testid="supplier-picker"]').or(
      page.locator('label:has-text("Supplier")').locator('..').locator('button').first(),
    );
    this.billNumberInput = page.locator('input[name="billNumber"]').or(
      page.locator('label:has-text("Bill Number")').locator('..').locator('input'),
    );
    this.billDateButton = page.locator('label:has-text("Bill Date")').locator('..').locator('button');
    this.dueDateButton = page.locator('label:has-text("Due Date")').locator('..').locator('button');
    this.referenceInput = page.locator('input[name="reference"]').or(
      page.locator('label:has-text("Reference")').locator('..').locator('input'),
    );
    this.descriptionTextarea = page.locator('textarea[name="description"]').or(
      page.locator('label:has-text("Description")').locator('..').locator('textarea'),
    );
    this.lineItemsCard = page.locator('text="Line Items"').locator('..').locator('..');
    this.summaryCard = page.locator('text="Summary"').locator('..').locator('..');
    this.saveButton = page.getByRole('button', { name: /^Save$|^Update$/i });
    this.submitForApprovalButton = page.getByRole('button', { name: /Submit for Approval/i });
    this.approveButton = page.getByRole('button', { name: /^Approve$/i });
    this.rejectButton = page.getByRole('button', { name: /^Reject$/i });
    this.cancelButton = page.getByRole('button', { name: /Cancel/i });
    this.validateButton = page.getByRole('button', { name: /Validate/i });
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.billForm).toBeVisible({ timeout: 10000 });
    await this.page.waitForLoadState('networkidle');
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async navigateToBill(billId: string): Promise<void> {
    await this.goto(`/purchase-bills/${billId}`);
    await this.waitForLoaded();
  }

  async selectSupplier(supplierName: string): Promise<void> {
    await this.supplierPicker.click();
    await this.page.getByRole('option', { name: new RegExp(supplierName, 'i') }).click();
  }

  async setBillNumber(billNumber: string): Promise<void> {
    await this.billNumberInput.fill(billNumber);
  }

  async setBillDate(date: string): Promise<void> {
    await this.billDateButton.click();
    await this.page.waitForSelector('[role="dialog"]');
    const [year, month, day] = date.split('-').map(Number);
    await this.page.getByRole('gridcell', { name: String(day), exact: true }).click();
  }

  async setDueDate(date: string): Promise<void> {
    await this.dueDateButton.click();
    await this.page.waitForSelector('[role="dialog"]');
    const [year, month, day] = date.split('-').map(Number);
    await this.page.getByRole('gridcell', { name: String(day), exact: true }).click();
  }

  async setReference(reference: string): Promise<void> {
    await this.referenceInput.fill(reference);
  }

  async setDescription(description: string): Promise<void> {
    await this.descriptionTextarea.fill(description);
  }

  async addLineItem(data: LineItemData): Promise<void> {
    const addLineButton = this.page.getByRole('button', { name: /Add Line|Add Row|\+/i });
    if (await addLineButton.isVisible()) {
      await addLineButton.click();
    }

    const lastRow = this.page.locator('table tbody tr').last();

    if (data.accountCode) {
      const accountCell = lastRow.locator('td').nth(0);
      await accountCell.click();
      await this.page.getByRole('option', { name: new RegExp(data.accountCode, 'i') }).click();
    }

    const descriptionInput = lastRow.locator('input[placeholder*="description" i], textarea').first();
    if (await descriptionInput.isVisible()) {
      await descriptionInput.fill(data.description);
    }

    const quantityInput = lastRow.locator('input[type="number"]').first();
    if (await quantityInput.isVisible()) {
      await quantityInput.fill(String(data.quantity));
    }

    const unitPriceInput = lastRow.locator('input[type="number"]').nth(1);
    if (await unitPriceInput.isVisible()) {
      await unitPriceInput.fill(String(data.unitPrice));
    }

    const vatRateSelect = lastRow.locator('select, [role="combobox"]').last();
    if (await vatRateSelect.isVisible()) {
      await vatRateSelect.click();
      const vatRateLabels: Record<string, string> = {
        ZERO: '0%',
        FIVE: '5%',
        TEN: '10%',
        EXEMPT: 'Exempt',
      };
      await this.page.getByRole('option', { name: vatRateLabels[data.vatRate] }).click();
    }
  }

  async removeLineItem(index: number): Promise<void> {
    const row = this.page.locator('table tbody tr').nth(index);
    const deleteButton = row.getByRole('button', { name: /delete|remove|trash/i });
    await deleteButton.click();
  }

  async getSubtotal(): Promise<string> {
    const subtotalElement = this.summaryCard.locator('text="Total Amount"').locator('..').locator('div').last();
    return (await subtotalElement.textContent()) ?? '';
  }

  async getVatAmount(): Promise<string> {
    const vatElement = this.summaryCard.locator('text="Total VAT"').locator('..').locator('div').last();
    return (await vatElement.textContent()) ?? '';
  }

  async getTotal(): Promise<string> {
    const totalElement = this.summaryCard.locator('text="Grand Total"').locator('..').locator('div').last();
    return (await totalElement.textContent()) ?? '';
  }

  async saveDraft(): Promise<void> {
    await this.saveButton.click();
    await this.waitForPageReady();
  }

  async submit(): Promise<void> {
    await this.submitForApprovalButton.click();
    await this.waitForPageReady();
  }

  async approve(): Promise<void> {
    await this.approveButton.click();
    await this.waitForModal();
    await this.confirmDialog();
    await this.waitForPageReady();
  }

  async reject(reason: string): Promise<void> {
    await this.rejectButton.click();
    await this.waitForModal();
    const reasonInput = this.page.locator('[role="dialog"]').locator('textarea');
    await reasonInput.fill(reason);
    await this.confirmDialog();
    await this.waitForPageReady();
  }

  async validate(): Promise<void> {
    await this.validateButton.click();
    await this.waitForPageReady();
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
  }

  async expectStatus(status: PurchaseBillStatus): Promise<void> {
    const statusBadge = this.page.locator('[class*="badge"]', { hasText: new RegExp(status, 'i') });
    await expect(statusBadge).toBeVisible();
  }

  async expectValidationError(fieldName: string): Promise<void> {
    const errorMessage = await this.getFormError(fieldName);
    expect(errorMessage).not.toBe('');
  }

  async expectVatWarning(): Promise<void> {
    const vatAlert = this.page.locator('[role="alert"]', { hasText: /VAT/i });
    await expect(vatAlert).toBeVisible();
  }
}
