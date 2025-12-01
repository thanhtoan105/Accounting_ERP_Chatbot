import { faker } from '@faker-js/faker';

/**
 * Statement Factory - Creates valid test statement objects for AR Statement testing
 */

export interface StatementRow {
  invoiceNumber: string;
  invoiceDate: string;
  invoiceAmount: number;
  amountPaid: number;
  balance: number;
  runningBalance: number;
  subRows?: StatementSubRow[];
}

export interface StatementSubRow {
  type: 'RECEIPT' | 'CREDIT' | 'ADJUSTMENT';
  date: string;
  amount: number;
  reference: string;
}

export interface StatementSummary {
  customerId: string;
  customerName: string;
  customerAddress: string;
  customerTaxCode: string;
  asOfDate: string;
  rows: StatementRow[];
  totals: {
    totalInvoices: number;
    totalPaid: number;
    totalOutstanding: number;
  };
  statementHash?: string;
  generatedAt?: string;
}

export interface StatementDetailed {
  customerId: string;
  customerName: string;
  customerAddress: string;
  customerTaxCode: string;
  asOfDate: string;
  rows: StatementRow[];
  totals: {
    totalInvoices: number;
    totalPaid: number;
    totalOutstanding: number;
  };
  statementHash?: string;
  generatedAt?: string;
}

export interface ReconciliationRow {
  invoiceNumber: string;
  customerAmount: number;
  customerPayment: number;
  notes?: string;
}

export interface DisputeLog {
  id: string;
  reconciliationId: string;
  invoiceNumber: string;
  systemAmount: number;
  customerAmount: number;
  variance: number;
  varianceType: 'SIGNIFICANT' | 'ROUNDING';
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED';
  createdAt: string;
  resolvedAt?: string;
  resolutionNotes?: string;
}

export interface StatementHistory {
  id: string;
  statementNumber: string;
  customerId: string;
  generatedAt: string;
  generatedBy: string;
  format: 'SUMMARY' | 'DETAILED';
  exportCount: number;
  sentCount: number;
}

export interface StatementDelivery {
  id: string;
  statementHistoryId: string;
  recipientEmail: string;
  status: 'SENT' | 'DELIVERED' | 'FAILED';
  sentAt: string;
  deliveredAt?: string;
}

/**
 * Create a statement row
 */
export const createStatementRow = (overrides: Partial<StatementRow> = {}): StatementRow => {
  const invoiceAmount = faker.number.int({ min: 1000000, max: 50000000 });
  const amountPaid = faker.number.int({ min: 0, max: invoiceAmount });
  const balance = invoiceAmount - amountPaid;

  return {
    invoiceNumber: `INV-${faker.string.numeric(6)}`,
    invoiceDate: faker.date.past({ years: 1 }).toISOString().split('T')[0],
    invoiceAmount,
    amountPaid,
    balance,
    runningBalance: balance,
    ...overrides,
  };
};

/**
 * Create a statement sub-row (receipt/credit/adjustment)
 */
export const createStatementSubRow = (overrides: Partial<StatementSubRow> = {}): StatementSubRow => {
  return {
    type: faker.helpers.arrayElement(['RECEIPT', 'CREDIT', 'ADJUSTMENT']),
    date: faker.date.past({ years: 1 }).toISOString().split('T')[0],
    amount: faker.number.int({ min: 100000, max: 10000000 }),
    reference: `REF-${faker.string.alphanumeric(8)}`,
    ...overrides,
  };
};

/**
 * Create a summary statement
 */
export const createStatementSummary = (overrides: Partial<StatementSummary> = {}): StatementSummary => {
  const rows = Array.from({ length: faker.number.int({ min: 1, max: 10 }) }, () => createStatementRow());
  
  // Calculate running balances
  let runningBalance = 0;
  rows.forEach((row) => {
    runningBalance += row.balance;
    row.runningBalance = runningBalance;
  });

  const totalInvoices = rows.reduce((sum, row) => sum + row.invoiceAmount, 0);
  const totalPaid = rows.reduce((sum, row) => sum + row.amountPaid, 0);
  const totalOutstanding = rows.reduce((sum, row) => sum + row.balance, 0);

  return {
    customerId: faker.string.uuid(),
    customerName: faker.company.name(),
    customerAddress: faker.location.streetAddress(),
    customerTaxCode: faker.string.numeric(10),
    asOfDate: new Date().toISOString().split('T')[0],
    rows,
    totals: {
      totalInvoices,
      totalPaid,
      totalOutstanding,
    },
    statementHash: faker.string.alphanumeric(64),
    generatedAt: new Date().toISOString(),
    ...overrides,
  };
};

/**
 * Create a detailed statement with sub-rows
 */
export const createStatementDetailed = (overrides: Partial<StatementDetailed> = {}): StatementDetailed => {
  const rows = Array.from({ length: faker.number.int({ min: 1, max: 5 }) }, () => {
    const row = createStatementRow();
    // Add sub-rows for receipts/credits
    const subRowCount = faker.number.int({ min: 0, max: 3 });
    row.subRows = Array.from({ length: subRowCount }, () => createStatementSubRow());
    return row;
  });

  // Calculate running balances including sub-rows
  let runningBalance = 0;
  rows.forEach((row) => {
    runningBalance += row.balance;
    row.runningBalance = runningBalance;
    
    // Update running balance for sub-rows
    if (row.subRows) {
      row.subRows.forEach((subRow) => {
        if (subRow.type === 'RECEIPT' || subRow.type === 'CREDIT') {
          runningBalance -= subRow.amount;
        } else if (subRow.type === 'ADJUSTMENT') {
          runningBalance += subRow.amount;
        }
      });
    }
  });

  const totalInvoices = rows.reduce((sum, row) => sum + row.invoiceAmount, 0);
  const totalPaid = rows.reduce((sum, row) => sum + row.amountPaid, 0);
  const totalOutstanding = rows.reduce((sum, row) => sum + row.balance, 0);

  return {
    customerId: faker.string.uuid(),
    customerName: faker.company.name(),
    customerAddress: faker.location.streetAddress(),
    customerTaxCode: faker.string.numeric(10),
    asOfDate: new Date().toISOString().split('T')[0],
    rows,
    totals: {
      totalInvoices,
      totalPaid,
      totalOutstanding,
    },
    statementHash: faker.string.alphanumeric(64),
    generatedAt: new Date().toISOString(),
    ...overrides,
  };
};

/**
 * Create a reconciliation row
 */
export const createReconciliationRow = (overrides: Partial<ReconciliationRow> = {}): ReconciliationRow => {
  return {
    invoiceNumber: `INV-${faker.string.numeric(6)}`,
    customerAmount: faker.number.int({ min: 1000000, max: 50000000 }),
    customerPayment: faker.number.int({ min: 0, max: 50000000 }),
    notes: faker.lorem.sentence(),
    ...overrides,
  };
};

/**
 * Create a dispute log
 */
export const createDisputeLog = (overrides: Partial<DisputeLog> = {}): DisputeLog => {
  const systemAmount = faker.number.int({ min: 1000000, max: 50000000 });
  const customerAmount = systemAmount + faker.number.int({ min: -1000000, max: 1000000 });
  const variance = Math.abs(systemAmount - customerAmount);
  const varianceType = variance > 1000 ? 'SIGNIFICANT' : 'ROUNDING';

  return {
    id: faker.string.uuid(),
    reconciliationId: faker.string.uuid(),
    invoiceNumber: `INV-${faker.string.numeric(6)}`,
    systemAmount,
    customerAmount,
    variance,
    varianceType,
    status: faker.helpers.arrayElement(['OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED']),
    createdAt: new Date().toISOString(),
    ...overrides,
  };
};

/**
 * Create statement history
 */
export const createStatementHistory = (overrides: Partial<StatementHistory> = {}): StatementHistory => {
  return {
    id: faker.string.uuid(),
    statementNumber: `STMT-${new Date().getFullYear()}-${faker.string.numeric(3)}`,
    customerId: faker.string.uuid(),
    generatedAt: new Date().toISOString(),
    generatedBy: faker.person.fullName(),
    format: faker.helpers.arrayElement(['SUMMARY', 'DETAILED']),
    exportCount: faker.number.int({ min: 0, max: 10 }),
    sentCount: faker.number.int({ min: 0, max: 5 }),
    ...overrides,
  };
};

/**
 * Create statement delivery
 */
export const createStatementDelivery = (overrides: Partial<StatementDelivery> = {}): StatementDelivery => {
  return {
    id: faker.string.uuid(),
    statementHistoryId: faker.string.uuid(),
    recipientEmail: faker.internet.email(),
    status: faker.helpers.arrayElement(['SENT', 'DELIVERED', 'FAILED']),
    sentAt: new Date().toISOString(),
    deliveredAt: faker.helpers.maybe(() => new Date().toISOString(), { probability: 0.5 }),
    ...overrides,
  };
};

