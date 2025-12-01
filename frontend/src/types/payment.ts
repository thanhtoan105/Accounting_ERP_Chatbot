export type PaymentStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'POSTED'
  | 'CANCELLED'
  | 'REJECTED'
  | 'REVERSED'

export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CHECK' | 'OTHER'

export interface APPaymentListDTO {
  id: string
  paymentNumber: string
  paymentDate: string
  supplierId: number
  supplierName: string | null
  supplierCode: string | null
  amount: number
  cashAccountId: number | null
  cashAccountName: string | null
  bankAccountId: number | null
  bankAccountName: string | null
  paymentMethod: PaymentMethod
  isStandalone: boolean
  status: PaymentStatus
  allocationCount: number
  linkedVoucherId: string | null
}

export interface PaymentAllocationDTO {
  id?: string
  paymentId?: string
  purchaseBillId: string
  purchaseBillNumber: string | null
  purchaseBillDate: string | null
  purchaseBillDueDate: string | null
  purchaseBillTotalAmount: number | null
  purchaseBillRemainingBalance: number | null
  allocatedAmount: number
  allocationOrder: number
  createdAt?: string
}

export interface PaymentAllocationRequest {
  purchaseBillId: string
  allocatedAmount: number
}

export interface APPaymentDTO {
  id: string
  companyId: number
  supplierId: number
  supplierName: string | null
  supplierCode: string | null
  paymentNumber: string
  paymentDate: string
  dueDate: string | null
  cashAccountId: number | null
  cashAccountName: string | null
  cashAccountNumber: string | null
  bankAccountId: number | null
  bankAccountName: string | null
  bankAccountNumber: string | null
  payee: string | null
  amount: number
  reference: string | null
  paymentMethod: PaymentMethod
  paymentProofUrl: string | null
  isStandalone: boolean
  status: PaymentStatus
  createdById: number
  createdByName: string
  approvedById: number | null
  approvedByName: string | null
  linkedVoucherId: string | null
  createdAt: string
  updatedAt: string
  postedAt: string | null
  allocations: PaymentAllocationDTO[]
}

export interface APPaymentCreateRequest {
  id?: string
  supplierId: number
  paymentDate: string // ISO (YYYY-MM-DD)
  dueDate?: string | null // ISO (YYYY-MM-DD)
  cashAccountId?: number | null
  bankAccountId?: number | null
  payee?: string | null
  amount: number
  reference?: string | null
  paymentMethod?: PaymentMethod
  paymentProofUrl?: string | null
  isStandalone?: boolean
  allocations?: PaymentAllocationRequest[]
}

export interface PaymentQueryParams {
  page?: number
  size?: number
  supplier?: number
  status?: PaymentStatus
  dateFrom?: string
  dateTo?: string
  search?: string
  standalone?: boolean
  sort?: string[]
}

export interface PaymentValidationResult {
  valid: boolean
  fieldErrors: Record<string, string>
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

// Re-export PurchaseBillDTO for convenience
export interface PurchaseBillDTO {
  id: string
  billNumber: string
  billDate: string
  dueDate: string
  supplierId: number
  supplierName: string | null
  supplierCode: string | null
  totalAmount: number
  remainingBalance?: number
  status: string
}
