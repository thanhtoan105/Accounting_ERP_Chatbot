import { faker } from '@faker-js/faker';

/**
 * Customer Factory
 * 
 * Creates test customer data with sensible defaults and explicit overrides.
 * Uses faker for dynamic values that prevent collisions in parallel execution.
 */
export type Customer = {
  id?: number;
  companyId?: number;
  customerCode: string;
  customerName: string;
  taxId?: string;
  address?: string;
  phone?: string;
  email?: string;
  contactPerson?: string;
  paymentTermDays: number;
  creditLimit?: number;
  isActive: boolean;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
};

export class CustomerFactory {
  private createdCustomers: number[] = [];

  /**
   * Create a customer with sensible defaults
   */
  createCustomer(overrides: Partial<Customer> = {}): Customer {
    const id = overrides.id ?? faker.number.int({ min: 1, max: 999999 });
    const customerName = overrides.customerName ?? faker.company.name();
    
    this.createdCustomers.push(id);

    return {
      id,
      companyId: overrides.companyId ?? 1,
      customerCode: overrides.customerCode ?? faker.string.alphanumeric({ length: 8, casing: 'upper' }),
      customerName,
      taxId: overrides.taxId ?? faker.string.numeric({ length: 10 }),
      address: overrides.address ?? faker.location.streetAddress({ useFullAddress: true }),
      phone: overrides.phone ?? faker.phone.number(),
      email: overrides.email ?? faker.internet.email({ provider: 'customer.vn' }),
      contactPerson: overrides.contactPerson ?? faker.person.fullName(),
      paymentTermDays: overrides.paymentTermDays ?? faker.helpers.arrayElement([15, 30, 45, 60]),
      creditLimit: overrides.creditLimit ?? faker.number.int({ min: 10000000, max: 500000000 }),
      isActive: overrides.isActive ?? true,
      notes: overrides.notes,
      createdAt: overrides.createdAt ?? new Date().toISOString(),
      updatedAt: overrides.updatedAt ?? new Date().toISOString(),
    };
  }

  /**
   * Returns list of created customer IDs for cleanup
   */
  getCreatedCustomerIds(): number[] {
    return [...this.createdCustomers];
  }

  /**
   * Clear the tracking list (used after cleanup)
   */
  reset(): void {
    this.createdCustomers = [];
  }
}
