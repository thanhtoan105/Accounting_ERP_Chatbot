import { faker } from '@faker-js/faker';
import type { Customer } from './customer-factory';

/**
 * Sales Invoice Factory
 * 
 * Creates test sales invoice data with sensible defaults and explicit overrides.
 * Uses faker for dynamic values that prevent collisions in parallel execution.
 */
export type SalesInvoiceLine = {
  id?: number;
  lineNumber: number;
  accountId: number;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  vatRate: 0 | 5 | 10 | 'EXEMPT';
  vatAmount: number;
  costCenterId?: number;
  itemId?: number;
};

export type SalesInvoice = {
  id?: number;
  companyId?: number;
  customerId: number;
  invoiceNumber: string;
  invoiceDate: string; // ISO date string
  dueDate: string; // ISO date string
  reference: string;
  description: string;
  status?: 'DRAFT' | 'PENDING_APPROVAL' | 'POSTED' | 'REJECTED' | 'PAID' | 'PARTIALLY_PAID';
  totalAmount: number;
  vatAmount: number;
  createdById?: number;
  approvedById?: number;
  isSensitive?: boolean; // Approval workflow: sensitive invoices require approval regardless of amount
  rejectionReason?: string; // Approval workflow: reason for rejection
  lines: SalesInvoiceLine[];
  createdAt?: string;
  updatedAt?: string;
};

export class SalesInvoiceFactory {
  private createdInvoices: number[] = [];

  /**
   * Create a sales invoice line with sensible defaults
   */
  createInvoiceLine(overrides: Partial<SalesInvoiceLine> = {}): SalesInvoiceLine {
    const quantity = overrides.quantity ?? faker.number.float({ min: 1, max: 100, fractionDigits: 2 });
    const unitPrice = overrides.unitPrice ?? faker.number.float({ min: 1000, max: 1000000, fractionDigits: 0 });
    const amount = quantity * unitPrice;
    const vatRate = overrides.vatRate ?? (faker.helpers.arrayElement([0, 5, 10]) as 0 | 5 | 10);
    const vatAmount = vatRate === 'EXEMPT' ? 0 : (amount * vatRate) / 100;

    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      lineNumber: overrides.lineNumber ?? 1,
      accountId: overrides.accountId ?? 5111, // Default revenue account
      description: overrides.description ?? faker.commerce.productDescription(),
      quantity,
      unitPrice,
      amount,
      vatRate,
      vatAmount,
      costCenterId: overrides.costCenterId,
      itemId: overrides.itemId,
    };
  }

  /**
   * Create a sales invoice with sensible defaults
   */
  createSalesInvoice(overrides: Partial<SalesInvoice> = {}): SalesInvoice {
    const id = overrides.id ?? faker.number.int({ min: 1, max: 999999 });
    const invoiceDate = overrides.invoiceDate ?? new Date().toISOString().split('T')[0];
    const dueDate = overrides.dueDate ?? new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    
    const lines = overrides.lines ?? [this.createInvoiceLine({ lineNumber: 1 })];
    const totalAmount = overrides.totalAmount ?? lines.reduce((sum, line) => sum + line.amount, 0);
    const vatAmount = overrides.vatAmount ?? lines.reduce((sum, line) => sum + line.vatAmount, 0);
    
    this.createdInvoices.push(id);

    return {
      id,
      companyId: overrides.companyId ?? 1,
      customerId: overrides.customerId ?? faker.number.int({ min: 1, max: 100 }),
      invoiceNumber: overrides.invoiceNumber ?? `SI-${faker.string.numeric({ length: 6 })}`,
      invoiceDate,
      dueDate,
      reference: overrides.reference ?? faker.string.alphanumeric({ length: 10, casing: 'upper' }),
      description: overrides.description ?? faker.commerce.productName(),
      status: overrides.status ?? 'DRAFT',
      totalAmount,
      vatAmount,
      createdById: overrides.createdById ?? 1,
      approvedById: overrides.approvedById,
      isSensitive: overrides.isSensitive ?? false,
      rejectionReason: overrides.rejectionReason,
      lines,
      createdAt: overrides.createdAt ?? new Date().toISOString(),
      updatedAt: overrides.updatedAt ?? new Date().toISOString(),
    };
  }

  /**
   * Create a draft sales invoice
   */
  createDraftInvoice(overrides: Partial<SalesInvoice> = {}): SalesInvoice {
    return this.createSalesInvoice({
      ...overrides,
      status: 'DRAFT',
    });
  }

  /**
   * Create a sales invoice pending approval
   */
  createPendingApprovalInvoice(overrides: Partial<SalesInvoice> = {}): SalesInvoice {
    return this.createSalesInvoice({
      ...overrides,
      status: 'PENDING_APPROVAL',
    });
  }

  /**
   * Create a posted sales invoice
   */
  createPostedInvoice(overrides: Partial<SalesInvoice> = {}): SalesInvoice {
    return this.createSalesInvoice({
      ...overrides,
      status: 'POSTED',
      approvedById: overrides.approvedById ?? 2, // Different from creator
    });
  }

  /**
   * Create a high-value sales invoice above approval threshold (>100M VND)
   */
  createHighValueInvoice(overrides: Partial<SalesInvoice> = {}): SalesInvoice {
    const line = this.createInvoiceLine({
      quantity: 1,
      unitPrice: 120000000, // 120M VND
    });

    return this.createSalesInvoice({
      ...overrides,
      lines: [line],
      totalAmount: line.amount,
      vatAmount: line.vatAmount,
    });
  }

  /**
   * Create a low-value sales invoice below approval threshold (<100M VND)
   */
  createLowValueInvoice(overrides: Partial<SalesInvoice> = {}): SalesInvoice {
    const line = this.createInvoiceLine({
      quantity: 1,
      unitPrice: 50000000, // 50M VND
    });

    return this.createSalesInvoice({
      ...overrides,
      lines: [line],
      totalAmount: line.amount,
      vatAmount: line.vatAmount,
    });
  }

  /**
   * Returns list of created invoice IDs for cleanup
   */
  getCreatedInvoiceIds(): number[] {
    return [...this.createdInvoices];
  }

  /**
   * Clear the tracking list (used after cleanup)
   */
  reset(): void {
    this.createdInvoices = [];
  }
}
