import { faker } from '@faker-js/faker';

/**
 * Customer Factory - Creates valid test customer objects for AR testing
 */

export interface Customer {
  id: string;
  code: string;
  name: string;
  taxCode: string;
  address: string;
  phone?: string;
  email?: string;
  arAccount: string; // Should be '131' for AR
  paymentTerms: number; // days
  creditLimit?: number;
  status: 'Active' | 'Inactive';
  createdAt?: string;
}

/**
 * Create a single customer with optional overrides
 */
export const createCustomer = (overrides: Partial<Customer> = {}): Customer => {
  return {
    id: faker.string.uuid(),
    code: `CUST-${faker.string.alphanumeric(6).toUpperCase()}`,
    name: faker.company.name(),
    taxCode: faker.string.numeric(10),
    address: faker.location.streetAddress(),
    phone: faker.phone.number('+84 9## ### ####'),
    email: faker.internet.email(),
    arAccount: '131',
    paymentTerms: 30, // Default net 30
    creditLimit: faker.number.int({ min: 10000000, max: 1000000000 }),
    status: 'Active',
    createdAt: new Date().toISOString(),
    ...overrides,
  };
};

/**
 * Create multiple customers
 */
export const createCustomers = (count: number, overrides: Partial<Customer> = {}): Customer[] => {
  return Array.from({ length: count }, () => createCustomer(overrides));
};

/**
 * Create customer with specific credit limit
 */
export const createCustomerWithCreditLimit = (creditLimit: number, overrides: Partial<Customer> = {}): Customer => {
  return createCustomer({
    creditLimit,
    ...overrides,
  });
};

/**
 * Create bulk customers for performance testing
 */
export const createCustomersBulk = (count: number): Customer[] => {
  return Array.from({ length: count }, (_, i) => ({
    id: faker.string.uuid(),
    code: `CUST-${String(i + 1).padStart(6, '0')}`,
    name: faker.company.name(),
    taxCode: faker.string.numeric(10),
    address: faker.location.streetAddress(),
    phone: faker.phone.number(),
    email: faker.internet.email(),
    arAccount: '131',
    paymentTerms: faker.number.int({ min: 15, max: 60 }),
    creditLimit: faker.number.int({ min: 10000000, max: 1000000000 }),
    status: 'Active',
    createdAt: new Date().toISOString(),
  }));
};
