/**
 * Type definitions for AR customer statement functionality
 */

export type StatementFormat = 'SUMMARY' | 'DETAILED'

export type ExportFormat = 'PDF' | 'EXCEL'

export type DisputeStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED'

export type VarianceType = 'SIGNIFICANT' | 'ROUNDING'

export interface StatementInvoice {
  invoiceId: string
  invoiceNumber: string
  invoiceDate: string
  invoiceAmount: number
  amountPaid: number
  balance: number
  runningBalance: number
}

export interface StatementTransaction {
  type: 'INVOICE' | 'RECEIPT' | 'CREDIT' | 'ADJUSTMENT'
  invoiceId?: string
  invoiceNumber?: string
  receiptId?: string
  receiptNumber?: string
  transactionDate: string
  reference?: string
  description?: string
  debit: number
  credit: number
  runningBalance: number
}

export interface ARStatementSummary {
  id: string
  customerId: number
  customerName: string
  customerCode: string
  customerAddress?: string
  customerTaxCode?: string
  format: StatementFormat
  asOfDate: string
  generatedAt?: string
  generatedByName?: string
  statementHash?: string
  totalInvoices: number
  totalPaid: number
  totalOutstanding: number
  invoices: StatementInvoice[]
}

export interface ARStatementDetailed extends ARStatementSummary {
  transactions: StatementTransaction[]
}

export interface ARStatementHistory {
  id: string
  customerId: number
  customerName?: string
  statementNumber?: string
  format: StatementFormat
  generatedAt: string
  generatedById?: number
  generatedByName?: string
  asOfDate: string
  exportCount: number
  sentCount: number
  statementHash?: string
}

export interface ReconciliationMismatch {
  invoiceId: string
  invoiceNumber: string
  systemAmount: number
  customerAmount: number
  variance: number
  varianceType: VarianceType
  notes?: string
}

export interface ReconciliationImportResult {
  reconciliationId: string
  customerId: number
  matchedCount: number
  mismatchCount: number
  mismatches: ReconciliationMismatch[]
}

export interface ARStatementDispute {
  id: string
  reconciliationId?: string
  invoiceId?: string
  invoiceNumber?: string
  systemAmount?: number
  customerAmount?: number
  variance?: number
  varianceType?: VarianceType
  notes?: string
  status: DisputeStatus
  resolvedAt?: string
  resolvedById?: number
  resolvedByName?: string
  resolutionNotes?: string
  createdAt: string
  updatedAt?: string
}

export interface GetStatementParams {
  customerId: number
  format?: StatementFormat
  asOfDate?: string
}

export interface ExportStatementParams {
  customerId: number
  format: ExportFormat
  statementFormat?: StatementFormat
  asOfDate?: string
}

export interface SendStatementParams {
  customerId: number
  email: string
  statementFormat?: StatementFormat
  asOfDate?: string
}

export interface StatementHistoryParams {
  customerId: number
  page?: number
  size?: number
}

export interface DisputeFilterParams {
  page?: number
  size?: number
  customerId?: number
  status?: DisputeStatus
  dateFrom?: string
  dateTo?: string
}

export interface ResolveDisputeRequest {
  resolutionNotes: string
}

