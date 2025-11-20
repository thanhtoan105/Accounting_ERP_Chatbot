export type PurchaseBillStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'POSTED'
  | 'REJECTED'
  | 'PAID'
  | 'PARTIALLY_PAID'

export type VatRate = 'ZERO' | 'FIVE' | 'TEN' | 'EXEMPT'

export interface PurchaseBillListDTO {
  id: string
  billNumber: string
  billDate: string
  dueDate: string
  supplierName: string | null
  supplierCode: string | null
  reference: string
  status: PurchaseBillStatus
  totalAmount: number
  vatAmount: number
  createdByName: string
  approvedByName: string | null
  attachmentCount: number
}

export interface PurchaseBillLineDTO {
  lineNumber?: number
  accountId: number
  description: string
  quantity?: number
  unitPrice?: number
  amount: number
  vatRate?: VatRate
  vatAmount?: number
  costCenterId?: number | null
  itemId?: number | null
}

export interface PurchaseBillDTO {
  id: string
  companyId: number
  supplierId: number
  supplierName: string | null
  supplierCode: string | null
  billNumber: string
  billDate: string
  dueDate: string
  reference: string
  description: string | null
  status: PurchaseBillStatus
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
  lines: PurchaseBillLineDTO[]
}

export interface PurchaseBillCreateRequest {
  id?: string
  supplierId: number
  billNumber: string
  billDate: string // ISO (YYYY-MM-DD)
  dueDate: string // ISO (YYYY-MM-DD)
  reference: string
  description?: string | null
  status?: PurchaseBillStatus
  lines: PurchaseBillLineDTO[]
}

export interface PurchaseBillQueryParams {
  page?: number
  size?: number
  supplier?: number
  status?: PurchaseBillStatus
  dateFrom?: string
  dateTo?: string
  search?: string
  sort?: string[]
}

export interface PurchaseBillValidationResult {
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
