import { faker } from '@faker-js/faker';

/**
 * AR Aging Factory - Creates test data for AR aging reports
 * Used for seeding test data and API mocking
 */

export interface AgingBucket {
  current: number;
  days1To30: number;
  days31To60: number;
  days61To90: number;
  daysOver90: number;
  total: number;
}

export interface AgingReportEntry {
  customerId: number;
  customerName: string;
  customerCode: string;
  buckets: AgingBucket;
  totalOutstanding: number;
  hasOverdue: boolean;
  invoiceCount: number;
}

export interface DrillDownInvoice {
  invoiceId: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  outstandingAmount: number;
  amountPaid: number;
  daysOverdue: number;
  status: string;
  lastPaymentDate?: string;
}

/**
 * Create aging bucket data with optional overrides
 */
export const createAgingBucketData = (overrides: Partial<AgingBucket> = {}): AgingBucket => {
  const current = overrides.current ?? faker.number.int({ min: 0, max: 1000000 });
  const days1To30 = overrides.days1To30 ?? faker.number.int({ min: 0, max: 500000 });
  const days31To60 = overrides.days31To60 ?? faker.number.int({ min: 0, max: 300000 });
  const days61To90 = overrides.days61To90 ?? faker.number.int({ min: 0, max: 200000 });
  const daysOver90 = overrides.daysOver90 ?? faker.number.int({ min: 0, max: 100000 });

  return {
    current,
    days1To30,
    days31To60,
    days61To90,
    daysOver90,
    total: current + days1To30 + days31To60 + days61To90 + daysOver90,
    ...overrides,
  };
};

/**
 * Create aging report entry for a customer
 */
export const createAgingReportEntry = (overrides: Partial<AgingReportEntry> = {}): AgingReportEntry => {
  const buckets = overrides.buckets || createAgingBucketData();
  const hasOverdue = buckets.days1To30 > 0 || buckets.days31To60 > 0 || buckets.days61To90 > 0 || buckets.daysOver90 > 0;

  return {
    customerId: faker.number.int({ min: 1, max: 1000 }),
    customerName: faker.company.name(),
    customerCode: `CUST-${faker.string.alphanumeric(6).toUpperCase()}`,
    buckets,
    totalOutstanding: buckets.total,
    hasOverdue,
    invoiceCount: faker.number.int({ min: 1, max: 10 }),
    ...overrides,
  };
};

/**
 * Create invoice with specific days overdue
 */
export const createOverdueInvoice = (daysOverdue: number, overrides: Partial<DrillDownInvoice> = {}): DrillDownInvoice => {
  const asOfDate = new Date();
  const dueDate = new Date(asOfDate);
  dueDate.setDate(dueDate.getDate() - daysOverdue);

  const totalAmount = faker.number.int({ min: 100000, max: 5000000 });
  const amountPaid = overrides.amountPaid ?? (daysOverdue > 0 ? faker.number.int({ min: 0, max: totalAmount * 0.5 }) : 0);
  const outstandingAmount = totalAmount - amountPaid;

  return {
    invoiceId: faker.string.uuid(),
    invoiceNumber: `INV-${Date.now()}-${faker.number.int({ min: 1, max: 999 })}`,
    invoiceDate: new Date(dueDate.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    dueDate: dueDate.toISOString().split('T')[0],
    outstandingAmount,
    amountPaid,
    daysOverdue: Math.max(0, daysOverdue),
    status: 'POSTED',
    lastPaymentDate: amountPaid > 0 ? faker.date.recent({ days: 30 }).toISOString().split('T')[0] : undefined,
    ...overrides,
  };
};

/**
 * Create drill-down invoice list for a specific aging bucket
 */
export const createDrillDownInvoices = (
  bucketKey: 'DAYS_1_30' | 'DAYS_31_60' | 'DAYS_61_90' | 'DAYS_OVER_90',
  count: number = 3
): DrillDownInvoice[] => {
  const daysOverdueMap = {
    DAYS_1_30: () => faker.number.int({ min: 1, max: 30 }),
    DAYS_31_60: () => faker.number.int({ min: 31, max: 60 }),
    DAYS_61_90: () => faker.number.int({ min: 61, max: 90 }),
    DAYS_OVER_90: () => faker.number.int({ min: 91, max: 365 }),
  };

  return Array.from({ length: count }, () => createOverdueInvoice(daysOverdueMap[bucketKey]()));
};

/**
 * Create aging report with multiple customers
 */
export const createAgingReport = (customerCount: number = 5): AgingReportEntry[] => {
  return Array.from({ length: customerCount }, () => createAgingReportEntry());
};

/**
 * Create customer with overdue invoices in specific buckets
 */
export const createCustomerWithOverdue = (
  buckets: Partial<AgingBucket> = {},
  overrides: Partial<AgingReportEntry> = {}
): AgingReportEntry => {
  const defaultBuckets: AgingBucket = {
    current: 0,
    days1To30: 500000,
    days31To60: 300000,
    days61To90: 200000,
    daysOver90: 100000,
    total: 1100000,
  };

  return createAgingReportEntry({
    buckets: { ...defaultBuckets, ...buckets },
    hasOverdue: true,
    ...overrides,
  });
};

/**
 * Create aging report entry for boundary conditions (exactly 30, 60, 90 days)
 */
export const createBoundaryAgingEntry = (
  boundary: '30' | '60' | '90',
  overrides: Partial<AgingReportEntry> = {}
): AgingReportEntry => {
  const bucketMap = {
    '30': { days1To30: 500000, days31To60: 0, days61To90: 0, daysOver90: 0 },
    '60': { days1To30: 0, days31To60: 500000, days61To90: 0, daysOver90: 0 },
    '90': { days1To30: 0, days31To60: 0, days61To90: 500000, daysOver90: 0 },
  };

  return createAgingReportEntry({
    buckets: createAgingBucketData(bucketMap[boundary]),
    ...overrides,
  });
};

