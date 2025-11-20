import { faker } from '@faker-js/faker';

/**
 * Supplier Factory
 * 
 * Creates test supplier data with sensible defaults and explicit overrides.
 * Uses faker for dynamic values that prevent collisions in parallel execution.
 */
export type Supplier = {
  id?: number;
  companyId?: number;
  code?: string;
  name: string;
  taxCode?: string;
  address?: string;
  email?: string;
  phone?: string;
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export class SupplierFactory {
  private createdSuppliers: number[] = [];

  /**
   * Create a supplier object with sensible defaults
   * @param overrides - Partial supplier data to override defaults
   * @returns Supplier object ready for API seeding
   */
  createSupplier(overrides: Partial<Supplier> = {}): Supplier {
    return {
      id: faker.number.int({ min: 1, max: 999999 }),
      companyId: faker.number.int({ min: 1, max: 100 }),
      code: faker.string.alphanumeric({ length: 8 }).toUpperCase(),
      name: faker.company.name(),
      taxCode: faker.string.numeric({ length: 10 }),
      address: faker.location.streetAddress(),
      email: faker.internet.email(),
      phone: faker.phone.number('0#########'),
      active: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...overrides,
    };
  }

  /**
   * Create an inactive supplier (convenience method)
   */
  createInactiveSupplier(overrides: Partial<Supplier> = {}): Supplier {
    return this.createSupplier({ active: false, ...overrides });
  }

  /**
   * Track a created supplier ID for cleanup
   */
  trackSupplier(supplierId: number): void {
    this.createdSuppliers.push(supplierId);
  }

  /**
   * Cleanup all tracked suppliers via API
   * Call this in fixture teardown for automatic cleanup
   */
  async cleanup(apiRequest?: (params: {
    method: 'DELETE';
    url: string;
  }) => Promise<void>): Promise<void> {
    if (!apiRequest) {
      console.warn('SupplierFactory.cleanup() called without apiRequest - skipping cleanup');
      return;
    }

    for (const supplierId of this.createdSuppliers) {
      try {
        await apiRequest({
          method: 'DELETE',
          url: `/api/v1/suppliers/${supplierId}`,
        });
      } catch (error) {
        console.warn(`Failed to cleanup supplier ${supplierId}:`, error);
      }
    }
    this.createdSuppliers = [];
  }

  /**
   * Get list of tracked supplier IDs
   */
  getTrackedSuppliers(): number[] {
    return [...this.createdSuppliers];
  }
}

