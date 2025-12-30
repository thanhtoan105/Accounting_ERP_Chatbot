export type SalesInvoiceStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'POSTED'
  | 'REJECTED'
  | 'PAID'
  | 'PARTIALLY_PAID'

export type VatRate = 'ZERO' | 'FIVE' | 'TEN' | 'EXEMPT'

export interface SalesInvoiceListDTO {
  id: string
  invoiceNumber: string
  invoiceDate: string
  dueDate: string
  customerName: string | null
  customerCode: string | null
  reference: string
  status: SalesInvoiceStatus
  totalAmount: number
  vatAmount: number
  createdByName: string
  approvedByName: string | null
  attachmentCount: number
}

export interface SalesInvoiceLineDTO {
  lineNumber?: number
  accountId: number
  description: string
  quantity?: number
  unitPrice?: number
  amount: number
  vatRate?: VatRate
  vatAmount?: number
  itemId?: number | null
}

export interface SalesInvoiceDTO {
  id: string
  companyId: number
  customerId: number
  customerName: string | null
  customerCode: string | null
  invoiceNumber: string
  invoiceDate: string
  dueDate: string
  reference: string
  description: string | null
  status: SalesInvoiceStatus
  totalAmount: number
  vatAmount: number
  createdById: number
  createdByName: string
  approvedById: number | null
  approvedByName: string | null
  createdAt: string
  updatedAt: string
  attachmentCount: number
  postedVoucherId: string | null
  lines: SalesInvoiceLineDTO[]
}

export interface SalesInvoiceCreateRequest {
  id?: string
  customerId: number
  invoiceNumber: string
  invoiceDate: string // ISO (YYYY-MM-DD)
  dueDate: string // ISO (YYYY-MM-DD)
  reference: string
  description?: string | null
  status?: SalesInvoiceStatus
  lines: SalesInvoiceLineDTO[]
  originalInvoiceId?: string // For credit notes - references the original invoice
}

export interface SalesInvoiceQueryParams {
  page?: number
  size?: number
  customer?: number
  status?: SalesInvoiceStatus
  dateFrom?: string
  dateTo?: string
  search?: string
  sort?: string[]
}

export interface SalesInvoiceValidationResult {
  valid: boolean
  headerErrors: Record<string, string[]>
  lineErrors: Record<number, Record<string, string[]>>
}

export interface PaginatedResponse<T> {
  data: {
    content: T[]
    totalElements: number
    totalPages: number
  }
  meta: {
    page: number
    size: number
    totalElements: number
    totalPages: number
  }
}

export interface ImportRowError {
  rowNumber: number
  field: string
  message: string
}

export interface ImportResult {
  successCount: number
  skippedCount: number
  errorCount: number
  errors: ImportRowError[]
  errorReportId?: string
}
