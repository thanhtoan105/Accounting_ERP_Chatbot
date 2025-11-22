import { faker } from '@faker-js/faker';

/**
 * Invoice Factory - Creates valid test invoice objects
 * Used for seeding test data and API mocking
 */

export interface InvoiceLineItem {
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: 0 | 5 | 10 | 'exempt';
  revenueAccount: string;
}

export interface Invoice {
  id?: string;
  invoiceNumber: string;
  customerId: string;
  customerName: string;
  date: string;
  dueDate: string;
  referenceText?: string;
  status: 'Draft' | 'PendingApproval' | 'Posted' | 'Rejected';
  lineItems: InvoiceLineItem[];
  currency: 'VND';
  subtotal: number;
  totalVAT: number;
  grandTotal: number;
  attachments?: string[];
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
}

/**
 * Create a single invoice with optional overrides
 */
export const createInvoice = (overrides: Partial<Invoice> = {}): Invoice => {
  const date = faker.date.soon({ days: 7 });
  const dueDate = new Date(date);
  dueDate.setDate(dueDate.getDate() + 30);

  const lineItems: InvoiceLineItem[] = [
    {
      description: faker.commerce.productName(),
      quantity: faker.number.int({ min: 1, max: 10 }),
      unitPrice: faker.number.int({ min: 100000, max: 5000000 }),
      vatRate: 10,
      revenueAccount: '511001',
    },
  ];

  const subtotal = lineItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
  const totalVAT = lineItems.reduce((sum, item) => {
    if (item.vatRate === 'exempt') return sum;
    return sum + (item.quantity * item.unitPrice * (item.vatRate as number)) / 100;
  }, 0);

  return {
    id: faker.string.uuid(),
    invoiceNumber: `INV-${Date.now()}-${faker.number.int({ min: 1, max: 999 })}`,
    customerId: faker.string.uuid(),
    customerName: faker.company.name(),
    date: date.toISOString().split('T')[0],
    dueDate: dueDate.toISOString().split('T')[0],
    referenceText: faker.lorem.word(),
    status: 'Draft',
    lineItems,
    currency: 'VND',
    subtotal,
    totalVAT,
    grandTotal: subtotal + totalVAT,
    createdAt: new Date().toISOString(),
    createdBy: faker.string.uuid(),
    ...overrides,
  };
};

/**
 * Create multiple invoices
 */
export const createInvoices = (count: number, overrides: Partial<Invoice> = {}): Invoice[] => {
  return Array.from({ length: count }, () => createInvoice(overrides));
};

/**
 * Create invoice with specific VAT rates (for VAT testing)
 */
export const createInvoiceWithVATRates = (
  rates: Array<0 | 5 | 10 | 'exempt'> = [0, 5, 10, 'exempt'],
  overrides: Partial<Invoice> = {}
): Invoice => {
  const lineItems: InvoiceLineItem[] = rates.map((rate) => ({
    description: `Item - VAT ${rate}%`,
    quantity: 1,
    unitPrice: 100000,
    vatRate: rate,
    revenueAccount: '511001',
  }));

  const subtotal = lineItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
  const totalVAT = lineItems.reduce((sum, item) => {
    if (item.vatRate === 'exempt') return sum;
    return sum + (item.quantity * item.unitPrice * (item.vatRate as number)) / 100;
  }, 0);

  return {
    ...createInvoice(overrides),
    lineItems,
    subtotal,
    totalVAT,
    grandTotal: subtotal + totalVAT,
  };
};

/**
 * Create invoice for specific customer
 */
export const createInvoiceForCustomer = (
  customerId: string,
  customerName: string,
  overrides: Partial<Invoice> = {}
): Invoice => {
  return createInvoice({
    customerId,
    customerName,
    ...overrides,
  });
};

/**
 * Create bulk invoices for load testing
 */
export const createInvoicesBulk = (
  count: number,
  options: {
    customerIds?: string[];
    dateRange?: { start: Date; end: Date };
  } = {}
): Invoice[] => {
  return Array.from({ length: count }, (_, i) => {
    let date = faker.date.soon({ days: 30 });
    if (options.dateRange) {
      date = faker.date.between({
        from: options.dateRange.start,
        to: options.dateRange.end,
      });
    }

    const customerId = options.customerIds
      ? options.customerIds[i % options.customerIds.length]
      : faker.string.uuid();

    const lineQuantity = faker.number.int({ min: 1, max: 5 });
    const lineItems: InvoiceLineItem[] = Array.from({ length: lineQuantity }, () => ({
      description: faker.commerce.productName(),
      quantity: faker.number.int({ min: 1, max: 10 }),
      unitPrice: faker.number.int({ min: 100000, max: 5000000 }),
      vatRate: faker.helpers.arrayElement([0, 5, 10, 'exempt'] as const),
      revenueAccount: '511001',
    }));

    const subtotal = lineItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
    const totalVAT = lineItems.reduce((sum, item) => {
      if (item.vatRate === 'exempt') return sum;
      return sum + (item.quantity * item.unitPrice * (item.vatRate as number)) / 100;
    }, 0);

    return {
      id: faker.string.uuid(),
      invoiceNumber: `INV-${customerId}-${i + 1}`,
      customerId,
      customerName: faker.company.name(),
      date: date.toISOString().split('T')[0],
      dueDate: new Date(date.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      status: faker.helpers.arrayElement(['Draft', 'Posted'] as const),
      lineItems,
      currency: 'VND',
      subtotal,
      totalVAT,
      grandTotal: subtotal + totalVAT,
    };
  });
};
