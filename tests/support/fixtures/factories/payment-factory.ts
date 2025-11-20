import { faker } from '@faker-js/faker';
import type { PurchaseBill } from './purchase-bill-factory';

/**
 * Payment Factory
 * 
 * Creates test payment data with sensible defaults and explicit overrides.
 * Uses faker for dynamic values that prevent collisions in parallel execution.
 */
export type PaymentAllocation = {
  id?: number;
  paymentId?: number;
  purchaseBillId: number;
  allocatedAmount: number;
  allocationOrder: number; // For FIFO tracking
  createdAt?: string;
};

export type APPayment = {
  id?: number;
  companyId?: number;
  supplierId: number;
  paymentNumber: string; // Auto-generated, unique per company/year
  paymentDate: string; // ISO date string
  dueDate?: string; // ISO date string
  cashAccountId?: number;
  bankAccountId?: number;
  payee: string;
  amount: number;
  reference: string;
  paymentMethod: 'CASH' | 'BANK_TRANSFER' | 'CHECK' | 'OTHER';
  paymentProofUrl?: string;
  isStandalone: boolean;
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'POSTED' | 'CANCELLED';
  createdById?: number;
  approvedById?: number;
  linkedVoucherId?: number;
  allocations?: PaymentAllocation[];
  createdAt?: string;
  updatedAt?: string;
  postedAt?: string;
};

export class PaymentFactory {
  private createdPayments: number[] = [];

  /**
   * Create a payment allocation with sensible defaults
   */
  createAllocation(overrides: Partial<PaymentAllocation> = {}): PaymentAllocation {
    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      paymentId: overrides.paymentId,
      purchaseBillId: overrides.purchaseBillId ?? faker.number.int({ min: 1, max: 999999 }),
      allocatedAmount: overrides.allocatedAmount ?? faker.number.float({ min: 1000, max: 10000000, fractionDigits: 0 }),
      allocationOrder: overrides.allocationOrder ?? 1,
      createdAt: new Date().toISOString(),
      ...overrides,
    };
  }

  /**
   * Create a payment object with sensible defaults
   * @param overrides - Partial payment data to override defaults
   * @returns Payment object ready for API seeding
   */
  createPayment(overrides: Partial<APPayment> = {}): APPayment {
    const paymentDate = overrides.paymentDate ?? faker.date.recent({ days: 30 }).toISOString().split('T')[0];
    const year = new Date(paymentDate).getFullYear();
    const paymentNumber = overrides.paymentNumber ?? `PAY-${year}-${faker.string.numeric({ length: 6 })}`;

    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      companyId: faker.number.int({ min: 1, max: 100 }),
      supplierId: overrides.supplierId ?? faker.number.int({ min: 1, max: 1000 }),
      paymentNumber,
      paymentDate,
      dueDate: overrides.dueDate,
      cashAccountId: overrides.cashAccountId,
      bankAccountId: overrides.bankAccountId,
      payee: overrides.payee ?? faker.person.fullName(),
      amount: overrides.amount ?? faker.number.float({ min: 1000, max: 50000000, fractionDigits: 0 }),
      reference: overrides.reference ?? faker.string.alphanumeric({ length: 10 }).toUpperCase(),
      paymentMethod: overrides.paymentMethod ?? faker.helpers.arrayElement(['CASH', 'BANK_TRANSFER', 'CHECK', 'OTHER']),
      paymentProofUrl: overrides.paymentProofUrl,
      isStandalone: overrides.isStandalone ?? false,
      status: overrides.status ?? 'DRAFT',
      createdById: overrides.createdById,
      approvedById: overrides.approvedById,
      linkedVoucherId: overrides.linkedVoucherId,
      allocations: overrides.allocations,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      postedAt: overrides.postedAt,
      ...overrides,
    };
  }

  /**
   * Create a draft payment (convenience method)
   */
  createDraftPayment(overrides: Partial<APPayment> = {}): APPayment {
    return this.createPayment({ status: 'DRAFT', ...overrides });
  }

  /**
   * Create a payment linked to bills with FIFO allocations
   * @param bills - Array of purchase bills to allocate payment to
   * @param paymentAmount - Total payment amount (defaults to sum of bill remaining balances)
   */
  createPaymentWithAllocations(
    bills: PurchaseBill[],
    paymentAmount?: number,
    overrides: Partial<APPayment> = {}
  ): APPayment {
    // Sort bills by due date ASC for FIFO
    const sortedBills = [...bills].sort((a, b) => 
      new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime()
    );

    const totalRemaining = sortedBills.reduce((sum, bill) => sum + (bill.totalAmount - (bill.totalAmount - (bill.totalAmount * 0.1))), 0);
    const amount = paymentAmount ?? totalRemaining;

    // Allocate payment to bills in FIFO order
    const allocations: PaymentAllocation[] = [];
    let remainingAmount = amount;
    let order = 1;

    for (const bill of sortedBills) {
      if (remainingAmount <= 0) break;
      
      const billRemaining = bill.totalAmount - (bill.totalAmount * 0.1); // Simplified remaining balance
      const allocated = Math.min(remainingAmount, billRemaining);
      
      allocations.push(this.createAllocation({
        purchaseBillId: bill.id!,
        allocatedAmount: allocated,
        allocationOrder: order++,
      }));
      
      remainingAmount -= allocated;
    }

    return this.createPayment({
      supplierId: bills[0]?.supplierId ?? overrides.supplierId ?? faker.number.int({ min: 1, max: 1000 }),
      amount,
      allocations,
      isStandalone: false,
      ...overrides,
    });
  }

  /**
   * Create a standalone payment (advance payment, no bill allocation)
   */
  createStandalonePayment(overrides: Partial<APPayment> = {}): APPayment {
    return this.createPayment({
      isStandalone: true,
      allocations: [],
      ...overrides,
    });
  }

  /**
   * Create a payment that exceeds approval threshold
   * @param threshold - Approval threshold in VND (default: 20M VND)
   */
  createPaymentAboveThreshold(threshold: number = 20_000_000, overrides: Partial<APPayment> = {}): APPayment {
    return this.createPayment({
      status: 'PENDING_APPROVAL',
      amount: threshold + 1_000_000,
      ...overrides,
    });
  }

  /**
   * Create a payment below approval threshold
   * @param threshold - Approval threshold in VND (default: 20M VND)
   */
  createPaymentBelowThreshold(threshold: number = 20_000_000, overrides: Partial<APPayment> = {}): APPayment {
    return this.createPayment({
      status: 'DRAFT',
      amount: threshold - 1_000_000,
      ...overrides,
    });
  }

  /**
   * Create a posted payment (convenience method)
   */
  createPostedPayment(overrides: Partial<APPayment> = {}): APPayment {
    return this.createPayment({
      status: 'POSTED',
      postedAt: new Date().toISOString(),
      ...overrides,
    });
  }

  /**
   * Track a created payment ID for cleanup
   */
  trackPayment(paymentId: number): void {
    this.createdPayments.push(paymentId);
  }

  /**
   * Cleanup all tracked payments via API
   * Call this in fixture teardown for automatic cleanup
   */
  async cleanup(apiRequest?: (params: {
    method: 'DELETE';
    url: string;
  }) => Promise<void>): Promise<void> {
    if (!apiRequest) {
      console.warn('PaymentFactory.cleanup() called without apiRequest - skipping cleanup');
      return;
    }

    for (const paymentId of this.createdPayments) {
      try {
        await apiRequest({
          method: 'DELETE',
          url: `/api/v1/ap-payments/${paymentId}`,
        });
      } catch (error) {
        console.warn(`Failed to cleanup payment ${paymentId}:`, error);
      }
    }
    this.createdPayments = [];
  }

  /**
   * Get list of tracked payment IDs
   */
  getTrackedPayments(): number[] {
    return [...this.createdPayments];
  }
}


