/**
 * Type definitions for supplier statement functionality
 */

export enum StatementType {
  SUMMARY = 'SUMMARY',
  DETAILED = 'DETAILED',
}

export enum ExportFormat {
  PDF = 'PDF',
  EXCEL = 'EXCEL',
}

export enum DisputeStatus {
  OPEN = 'OPEN',
  IN_PROGRESS = 'IN_PROGRESS',
  RESOLVED = 'RESOLVED',
  REJECTED = 'REJECTED',
}

export interface StatementLineItem {
  type: 'BILL' | 'PAYMENT'
  date: string
  reference: string
  description: string
  debit: number
  credit: number
  balance: number
  billId?: string
  billNumber?: string
  paymentId?: string
  paymentNumber?: string
}

export interface SupplierStatement {
  id: string
  supplierId: number
  supplierName: string
  supplierCode: string
  statementType: StatementType
  startDate: string
  endDate: string
  generationDate: string
  generatedByName: string
  format: ExportFormat
  openingBalance: number
  closingBalance: number
  totalDebits: number
  totalCredits: number
  items: StatementLineItem[]
}

export interface BillLineItem {
  accountCode: string
  accountName: string
  description: string
  quantity: number
  unitPrice: number
  amount: number
  vatAmount: number
}

export interface PaymentEvent {
  paymentId: string
  paymentNumber: string
  paymentDate: string
  amount: number
  allocatedAmount: number
  reference: string
}

export interface DetailedBillItem {
  billId: string
  billNumber: string
  billDate: string
  dueDate: string
  reference: string
  description: string
  totalAmount: number
  remainingBalance: number
  lineItems: BillLineItem[]
  paymentEvents: PaymentEvent[]
}

export interface DetailedStatement extends SupplierStatement {
  billDetails: DetailedBillItem[]
}

export interface SupplierStatementHistory {
  id: string
  supplierId: number
  supplierName: string
  supplierCode: string
  statementType: StatementType
  generationDate: string
  generatedByName: string
  format: ExportFormat
  hash: string
  sentDate?: string
  sentTo?: string[]
  viewCount: number
  downloadCount: number
  startDate: string
  endDate: string
}

export interface ReconciliationItem {
  billId?: string
  billNumber: string
  billDate: string
  supplierAmount: number
  systemAmount?: number
  variance?: number
  status: 'MATCHED' | 'MISMATCHED' | 'MISSING' | 'APPLIED'
  notes?: string
}

export interface ReconciliationResult {
  supplierId: number
  supplierName: string
  reconciliationDate: string
  totalItems: number
  matchedCount: number
  mismatchedCount: number
  missingCount: number
  appliedCount: number
  matched: ReconciliationItem[]
  mismatched: ReconciliationItem[]
  missing: ReconciliationItem[]
  applied: ReconciliationItem[]
}

export interface SupplierStatementDispute {
  id: string
  supplierId: number
  supplierName: string
  supplierCode: string
  billId?: string
  billNumber?: string
  disputeReason: string
  status: DisputeStatus
  resolutionNotes?: string
  createdByName: string
  createdAt: string
  resolvedByName?: string
  resolvedAt?: string
  disputedAmount?: number
  systemAmount?: number
  variance?: number
}

export interface GenerateStatementRequest {
  supplierId: number
  statementType: StatementType
  startDate: string
  endDate: string
  format: ExportFormat
}

export interface UpdateDisputeRequest {
  status: DisputeStatus
  resolutionNotes?: string
}

export interface PaginatedResponse<T> {
  content: T[]
  currentPage: number
  totalItems: number
  totalPages: number
}

export interface StatementFilterParams {
  page?: number
  size?: number
  supplier?: number
  startDate?: string
  endDate?: string
  statementType?: StatementType
  sort?: string[]
}

export interface DisputeFilterParams {
  page?: number
  size?: number
  supplier?: number
  status?: DisputeStatus
  sort?: string[]
}
