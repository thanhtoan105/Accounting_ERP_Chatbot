import { faker } from '@faker-js/faker';
import type { Supplier } from './supplier-factory';

/**
 * Purchase Bill Factory
 * 
 * Creates test purchase bill data with sensible defaults and explicit overrides.
 * Uses faker for dynamic values that prevent collisions in parallel execution.
 */
export type PurchaseBillLine = {
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

export type PurchaseBill = {
  id?: number;
  companyId?: number;
  supplierId: number;
  billNumber: string;
  billDate: string; // ISO date string
  dueDate: string; // ISO date string
  reference: string;
  description: string;
  status?: 'DRAFT' | 'PENDING_APPROVAL' | 'POSTED' | 'REJECTED' | 'PAID' | 'PARTIALLY_PAID';
  totalAmount: number;
  vatAmount: number;
  createdById?: number;
  approvedById?: number;
  isSensitive?: boolean; // Approval workflow: sensitive bills require approval regardless of amount
  rejectionReason?: string; // Approval workflow: reason for rejection
  lines: PurchaseBillLine[];
  createdAt?: string;
  updatedAt?: string;
};

export class PurchaseBillFactory {
  private createdBills: number[] = [];

  /**
   * Create a purchase bill line with sensible defaults
   */
  createBillLine(overrides: Partial<PurchaseBillLine> = {}): PurchaseBillLine {
    const quantity = overrides.quantity ?? faker.number.float({ min: 1, max: 100, fractionDigits: 2 });
    const unitPrice = overrides.unitPrice ?? faker.number.float({ min: 1000, max: 1000000, fractionDigits: 0 });
    const amount = quantity * unitPrice;
    const vatRate = overrides.vatRate ?? (faker.helpers.arrayElement([0, 5, 10]) as 0 | 5 | 10);
    const vatAmount = vatRate === 'EXEMPT' ? 0 : (amount * vatRate) / 100;

    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      lineNumber: overrides.lineNumber ?? 1,
      accountId: overrides.accountId ?? faker.number.int({ min: 1000, max: 9999 }),
      description: overrides.description ?? faker.commerce.productDescription(),
      quantity,
      unitPrice,
      amount,
      vatRate,
      vatAmount,
      costCenterId: overrides.costCenterId,
      itemId: overrides.itemId,
      ...overrides,
    };
  }

  /**
   * Create a purchase bill object with sensible defaults
   * @param overrides - Partial purchase bill data to override defaults
   * @returns Purchase bill object ready for API seeding
   */
  createPurchaseBill(overrides: Partial<PurchaseBill> = {}): PurchaseBill {
    const billDate = overrides.billDate ?? faker.date.recent({ days: 30 }).toISOString().split('T')[0];
    const dueDate = overrides.dueDate ?? new Date(new Date(billDate).getTime() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    
    const lines = overrides.lines ?? [this.createBillLine()];
    const totalAmount = lines.reduce((sum, line) => sum + line.amount, 0);
    const vatAmount = lines.reduce((sum, line) => sum + line.vatAmount, 0);

    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      companyId: faker.number.int({ min: 1, max: 100 }),
      supplierId: overrides.supplierId ?? faker.number.int({ min: 1, max: 1000 }),
      billNumber: overrides.billNumber ?? `BILL-${faker.string.alphanumeric({ length: 6 }).toUpperCase()}`,
      billDate,
      dueDate,
      reference: overrides.reference ?? faker.string.alphanumeric({ length: 10 }).toUpperCase(),
      description: overrides.description ?? faker.commerce.productDescription(),
      status: overrides.status ?? 'DRAFT',
      totalAmount: overrides.totalAmount ?? totalAmount,
      vatAmount: overrides.vatAmount ?? vatAmount,
      createdById: overrides.createdById,
      approvedById: overrides.approvedById,
      lines,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides,
    };
  }

  /**
   * Create a draft purchase bill (convenience method)
   */
  createDraftBill(overrides: Partial<PurchaseBill> = {}): PurchaseBill {
    return this.createPurchaseBill({ status: 'DRAFT', ...overrides });
  }

  /**
   * Create a posted purchase bill (convenience method)
   */
  createPostedBill(overrides: Partial<PurchaseBill> = {}): PurchaseBill {
    return this.createPurchaseBill({ status: 'POSTED', ...overrides });
  }

  /**
   * Create a purchase bill pending approval (convenience method)
   */
  createPendingApprovalBill(overrides: Partial<PurchaseBill> = {}): PurchaseBill {
    return this.createPurchaseBill({ status: 'PENDING_APPROVAL', ...overrides });
  }

  /**
   * Create a purchase bill that exceeds approval threshold (convenience method)
   * @param threshold - Approval threshold in VND (default: 20M VND)
   */
  createBillAboveThreshold(threshold: number = 20_000_000, overrides: Partial<PurchaseBill> = {}): PurchaseBill {
    const lines = overrides.lines ?? [this.createBillLine({ amount: threshold + 1_000_000 })];
    const totalAmount = lines.reduce((sum, line) => sum + line.amount, 0);
    const vatAmount = lines.reduce((sum, line) => sum + line.vatAmount, 0);

    return this.createPurchaseBill({
      status: 'PENDING_APPROVAL',
      totalAmount,
      vatAmount,
      lines,
      ...overrides,
    });
  }

  /**
   * Create a purchase bill below approval threshold (convenience method)
   * @param threshold - Approval threshold in VND (default: 20M VND)
   */
  createBillBelowThreshold(threshold: number = 20_000_000, overrides: Partial<PurchaseBill> = {}): PurchaseBill {
    const lines = overrides.lines ?? [this.createBillLine({ amount: threshold - 1_000_000 })];
    const totalAmount = lines.reduce((sum, line) => sum + line.amount, 0);
    const vatAmount = lines.reduce((sum, line) => sum + line.vatAmount, 0);

    return this.createPurchaseBill({
      status: 'DRAFT',
      totalAmount,
      vatAmount,
      lines,
      ...overrides,
    });
  }

  /**
   * Create a rejected purchase bill (convenience method)
   */
  createRejectedBill(overrides: Partial<PurchaseBill> = {}): PurchaseBill {
    return this.createPurchaseBill({ status: 'REJECTED', ...overrides });
  }

  /**
   * Track a created bill ID for cleanup
   */
  trackBill(billId: number): void {
    this.createdBills.push(billId);
  }

  /**
   * Cleanup all tracked bills via API
   * Call this in fixture teardown for automatic cleanup
   */
  async cleanup(apiRequest?: (params: {
    method: 'DELETE';
    url: string;
  }) => Promise<void>): Promise<void> {
    if (!apiRequest) {
      console.warn('PurchaseBillFactory.cleanup() called without apiRequest - skipping cleanup');
      return;
    }

    for (const billId of this.createdBills) {
      try {
        await apiRequest({
          method: 'DELETE',
          url: `/api/v1/purchase-bills/${billId}`,
        });
      } catch (error) {
        console.warn(`Failed to cleanup bill ${billId}:`, error);
      }
    }
    this.createdBills = [];
  }

  /**
   * Get list of tracked bill IDs
   */
  getTrackedBills(): number[] {
    return [...this.createdBills];
  }
}

