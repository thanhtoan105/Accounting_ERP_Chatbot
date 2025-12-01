import { faker } from '@faker-js/faker';
import { createInvoice, Invoice, InvoiceLineItem } from './invoice.factory';

/**
 * VAT Factory - Creates test data for VAT-related scenarios
 * Used for VAT rate override, VAT corrections, and VAT reporting tests
 */

export interface VATCorrection {
  id?: string;
  invoiceId: string;
  lineItemId?: string;
  oldVATAmount: number;
  newVATAmount: number;
  variance: number;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  correctedBy?: string;
  correctedAt?: string;
  approvedBy?: string;
  approvedAt?: string;
}

export interface OutputVATReportRow {
  invoiceNumber: string;
  invoiceDate: string;
  customerName: string;
  customerTaxCode: string;
  revenue0pct: number;
  revenue5pct: number;
  revenue10pct: number;
  revenueExempt: number;
  total_vat_collected: number;
}

/**
 * Create VAT correction with optional overrides
 */
export const createVATCorrection = (overrides: Partial<VATCorrection> = {}): VATCorrection => {
  const oldVATAmount = faker.number.int({ min: 10000, max: 1000000 });
  const newVATAmount = oldVATAmount + faker.number.int({ min: -50000, max: 50000 });
  const variance = Math.abs(newVATAmount - oldVATAmount);

  return {
    id: faker.string.uuid(),
    invoiceId: faker.string.uuid(),
    oldVATAmount,
    newVATAmount,
    variance,
    reason: faker.lorem.sentence({ min: 10, max: 50 }),
    status: 'PENDING',
    correctedBy: faker.string.uuid(),
    correctedAt: new Date().toISOString(),
    ...overrides,
  };
};

/**
 * Create multiple VAT corrections
 */
export const createVATCorrections = (count: number, overrides: Partial<VATCorrection> = {}): VATCorrection[] => {
  return Array.from({ length: count }, () => createVATCorrection(overrides));
};

/**
 * Create VAT correction requiring approval (variance > threshold)
 */
export const createVATCorrectionRequiringApproval = (
  threshold: number = 10000000,
  overrides: Partial<VATCorrection> = {}
): VATCorrection => {
  const oldVATAmount = faker.number.int({ min: 1000000, max: 5000000 });
  const newVATAmount = oldVATAmount + threshold + faker.number.int({ min: 1, max: 1000000 });
  const variance = newVATAmount - oldVATAmount;

  return createVATCorrection({
    oldVATAmount,
    newVATAmount,
    variance,
    status: 'PENDING',
    ...overrides,
  });
};

/**
 * Create invoice with VAT rate override (different from company default)
 */
export const createInvoiceWithVATOverride = (
  companyDefaultRate: number = 10,
  overrideRate: 0 | 5 | 10 | 'exempt' = 5,
  overrides: Partial<Invoice> = {}
): Invoice => {
  const invoice = createInvoice(overrides);
  const lineItems: InvoiceLineItem[] = invoice.lineItems.map((item) => ({
    ...item,
    vatRate: overrideRate,
  }));

  // Recalculate totals with override rate
  const subtotal = lineItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
  const totalVAT = lineItems.reduce((sum, item) => {
    if (item.vatRate === 'exempt') return sum;
    return sum + (item.quantity * item.unitPrice * (item.vatRate as number)) / 100;
  }, 0);

  return {
    ...invoice,
    lineItems,
    subtotal,
    totalVAT,
    grandTotal: subtotal + totalVAT,
  };
};

/**
 * Create invoice with VAT rounding scenario (amounts that require rounding to nearest 100 VND)
 */
export const createInvoiceWithVATRounding = (overrides: Partial<Invoice> = {}): Invoice => {
  // Use amounts that result in VAT requiring rounding
  // Example: 333,333 * 10% = 33,333.30 → rounds to 33,300
  const lineItems: InvoiceLineItem[] = [
    {
      description: 'Service with rounding',
      quantity: 1,
      unitPrice: 333333,
      vatRate: 10,
      revenueAccount: '511001',
    },
  ];

  const subtotal = lineItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
  // VAT rounded to nearest 100 VND: 33,333.30 → 33,300
  const totalVAT = 33300;

  return {
    ...createInvoice(overrides),
    lineItems,
    subtotal,
    totalVAT,
    grandTotal: subtotal + totalVAT,
  };
};

/**
 * Create invoice with VAT variance (header VAT ≠ sum of line VAT)
 */
export const createInvoiceWithVATVariance = (
  variance: number,
  overrides: Partial<Invoice> = {}
): Invoice => {
  const invoice = createInvoice(overrides);
  const calculatedVAT = invoice.totalVAT;
  const headerVAT = calculatedVAT + variance;

  return {
    ...invoice,
    totalVAT: headerVAT,
    grandTotal: invoice.subtotal + headerVAT,
  };
};

/**
 * Create output VAT report row
 */
export const createOutputVATReportRow = (overrides: Partial<OutputVATReportRow> = {}): OutputVATReportRow => {
  const revenue10pct = faker.number.int({ min: 100000, max: 10000000 });
  const revenue5pct = faker.number.int({ min: 0, max: 5000000 });
  const revenue0pct = faker.number.int({ min: 0, max: 3000000 });
  const revenueExempt = faker.number.int({ min: 0, max: 2000000 });

  const total_vat_collected = revenue10pct * 0.1 + revenue5pct * 0.05;

  return {
    invoiceNumber: `INV-${faker.number.int({ min: 1, max: 9999 })}`,
    invoiceDate: faker.date.recent({ days: 30 }).toISOString().split('T')[0],
    customerName: faker.company.name(),
    customerTaxCode: faker.string.numeric(10),
    revenue0pct,
    revenue5pct,
    revenue10pct,
    revenueExempt,
    total_vat_collected: Math.round(total_vat_collected),
    ...overrides,
  };
};

/**
 * Create multiple output VAT report rows
 */
export const createOutputVATReportRows = (
  count: number,
  overrides: Partial<OutputVATReportRow> = {}
): OutputVATReportRow[] => {
  return Array.from({ length: count }, () => createOutputVATReportRow(overrides));
};

/**
 * Create credit note referencing original invoice
 */
export const createCreditNote = (originalInvoiceId: string, overrides: Partial<Invoice> = {}): Invoice => {
  const originalInvoice = createInvoice();
  const creditNote = createInvoice({
    ...overrides,
    invoiceNumber: `CN-${originalInvoice.invoiceNumber}`,
  });

  // Invert amounts for credit note
  const lineItems: InvoiceLineItem[] = creditNote.lineItems.map((item) => ({
    ...item,
    quantity: -item.quantity, // Negative quantity
  }));

  const subtotal = lineItems.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
  const totalVAT = lineItems.reduce((sum, item) => {
    if (item.vatRate === 'exempt') return sum;
    return sum + (item.quantity * item.unitPrice * (item.vatRate as number)) / 100;
  }, 0);

  return {
    ...creditNote,
    lineItems,
    subtotal: Math.abs(subtotal), // Store as positive but will be inverted in GL
    totalVAT: Math.abs(totalVAT),
    grandTotal: Math.abs(subtotal + totalVAT),
    status: 'Draft',
  };
};

