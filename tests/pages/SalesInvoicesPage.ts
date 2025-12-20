import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './BasePage';

export interface LineItemData {
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: 'ZERO' | 'FIVE' | 'TEN' | 'EXEMPT';
  accountCode?: string;
}

export type SalesInvoiceStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'POSTED'
  | 'REJECTED'
  | 'PAID'
  | 'PARTIALLY_PAID';

/**
 * Page Object for the Sales Invoices List page (/sales-invoices)
 */
export class SalesInvoiceListPage extends BasePage {
  readonly path = '/sales-invoices';

  private readonly invoicesTable: Locator;
  private readonly createButton: Locator;
  private readonly refreshButton: Locator;
  private readonly searchInput: Locator;
  private readonly statusFilter: Locator;
  private readonly customerFilter: Locator;
  private readonly dateFromPicker: Locator;
  private readonly dateToPicker: Locator;

  constructor(page: Page) {
    super(page);
    this.invoicesTable = page.locator('table');
    this.createButton = page.getByRole('button', { name: /Create Sales Invoice/i });
    this.refreshButton = page.getByRole('button', { name: /Refresh/i });
    this.searchInput = page.getByPlaceholder(/Invoice number, reference/i);
    this.statusFilter = page.locator('label:has-text("Status")').locator('..').locator('button');
    this.customerFilter = page.getByPlaceholder(/Customer ID/i);
    this.dateFromPicker = page.locator('label:has-text("Date From")').locator('..').locator('button');
    this.dateToPicker = page.locator('label:has-text("Date To")').locator('..').locator('button');
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.invoicesTable).toBeVisible({ timeout: 10000 });
    await this.page.waitForLoadState('networkidle');
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async clickNewInvoice(): Promise<void> {
    await this.createButton.click();
    await this.page.waitForURL(/\/sales-invoices\/new/);
  }

  async openInvoice(invoiceNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: invoiceNumber });
    await row.click();
    await this.page.waitForURL(/\/sales-invoices\/\d+/);
  }

  async filterByStatus(
    status: 'all' | 'draft' | 'pending' | 'approved' | 'rejected' | 'paid' | 'partially_paid',
  ): Promise<void> {
    await this.statusFilter.click();
    const statusMap: Record<string, string> = {
      all: 'All Status',
      draft: 'Draft',
      pending: 'Pending Approval',
      approved: 'Posted',
      rejected: 'Rejected',
      paid: 'Paid',
      partially_paid: 'Partially Paid',
    };
    await this.page.getByRole('option', { name: statusMap[status] }).click();
    await this.page.waitForTimeout(300);
  }

  async filterByCustomer(customerId: string): Promise<void> {
    await this.customerFilter.fill(customerId);
    await this.page.waitForTimeout(300);
  }

  async searchInvoices(searchText: string): Promise<void> {
    await this.searchInput.fill(searchText);
    await this.page.waitForTimeout(300);
  }

  async refresh(): Promise<void> {
    await this.refreshButton.click();
    await this.waitForPageReady();
  }

  async expectInvoiceInList(invoiceNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: invoiceNumber });
    await expect(row).toBeVisible({ timeout: 10000 });
  }

  async expectInvoiceNotInList(invoiceNumber: string): Promise<void> {
    const row = this.page.locator('table tbody tr', { hasText: invoiceNumber });
    await expect(row).toBeHidden();
  }

  async getInvoiceCount(): Promise<number> {
    return this.getTableRowCount();
  }
}

/**
 * Page Object for the Sales Invoice Form page (/sales-invoices/new, /sales-invoices/:id)
 */
export class SalesInvoiceFormPage extends BasePage {
  readonly path = '/sales-invoices/new';

  private readonly invoiceForm: Locator;
  private readonly customerPicker: Locator;
  private readonly invoiceNumberInput: Locator;
  private readonly invoiceDateButton: Locator;
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
  private readonly createCreditNoteButton: Locator;

  constructor(page: Page) {
    super(page);
    this.invoiceForm = page.locator('form');
    this.customerPicker = page.locator('[data-testid="customer-picker"]').or(
      page.locator('label:has-text("Customer")').locator('..').locator('button').first(),
    );
    this.invoiceNumberInput = page.locator('input[name="invoiceNumber"]').or(
      page.locator('label:has-text("Invoice Number")').locator('..').locator('input'),
    );
    this.invoiceDateButton = page.locator('label:has-text("Invoice Date")').locator('..').locator('button');
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
    this.createCreditNoteButton = page.getByRole('menuitem', { name: /Create Credit Note/i });
  }

  async waitForLoaded(): Promise<void> {
    await expect(this.invoiceForm).toBeVisible({ timeout: 10000 });
    await this.page.waitForLoadState('networkidle');
  }

  async navigate(): Promise<void> {
    await this.goto(this.path);
    await this.waitForLoaded();
  }

  async navigateToInvoice(invoiceId: string): Promise<void> {
    await this.goto(`/sales-invoices/${invoiceId}`);
    await this.waitForLoaded();
  }

  async selectCustomer(customerName: string): Promise<void> {
    await this.customerPicker.click();
    await this.page.getByRole('option', { name: new RegExp(customerName, 'i') }).click();
  }

  async setInvoiceNumber(invoiceNumber: string): Promise<void> {
    await this.invoiceNumberInput.fill(invoiceNumber);
  }

  async setInvoiceDate(date: string): Promise<void> {
    await this.invoiceDateButton.click();
    await this.page.waitForSelector('[role="dialog"]');
    const [year, month, day] = date.split('-').map(Number);
    const dateToSelect = new Date(year, month - 1, day);
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

  async createCreditNote(): Promise<void> {
    const moreActionsButton = this.page.getByRole('button', { name: /more/i });
    if (await moreActionsButton.isVisible()) {
      await moreActionsButton.click();
    }
    await this.createCreditNoteButton.click();
    await this.page.waitForURL(/\/sales-invoices\/\d+\/credit-note/);
  }

  async expectStatus(status: SalesInvoiceStatus): Promise<void> {
    const statusBadge = this.page.locator('text="Status"').locator('..').locator('[class*="badge"]');
    await expect(statusBadge).toHaveText(status, { ignoreCase: true });
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
